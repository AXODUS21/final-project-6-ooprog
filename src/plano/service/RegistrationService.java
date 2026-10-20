package plano.service;

import plano.model.*;
import plano.repository.DataStore;
import plano.util.ConsoleIO;
import plano.util.InputValidator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service managing registrations, capacity enforcement, FIFO waitlists,
 * automatic waitlist promotion upon cancellation, and attendee check-ins.
 */
public class RegistrationService {
    private final DataStore dataStore;

    public RegistrationService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public List<Registration> getRegistrationsForEvent(String eventId) {
        return dataStore.getRegistrationRepository().findByEvent(eventId);
    }

    public List<Registration> getRegistrationsForUser(String userId) {
        return dataStore.getRegistrationRepository().findByUser(userId);
    }

    public int getConfirmedCount(String eventId) {
        return dataStore.getRegistrationRepository().countConfirmedByEvent(eventId);
    }

    public int getWaitlistCount(String eventId) {
        return dataStore.getRegistrationRepository().countWaitlistedByEvent(eventId);
    }

    public int getCheckedInCount(String eventId) {
        return dataStore.getRegistrationRepository().countCheckedInByEvent(eventId);
    }

    public int getWaitlistPosition(String eventId, String userId) {
        List<Registration> waitlist = dataStore.getRegistrationRepository().findWaitlistedByEvent(eventId);
        for (int i = 0; i < waitlist.size(); i++) {
            if (waitlist.get(i).getUserId().equals(userId)) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Registers a user for an event, automatically placing them in Confirmed status
     * or Waitlisted status depending on capacity limits.
     */
    public synchronized RegistrationResult register(User user, String eventId) {
        if (user == null || !user.isActive()) {
            return new RegistrationResult(false, "User account is inactive or invalid.", null, null);
        }

        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (eventOpt.isEmpty()) {
            return new RegistrationResult(false, "Event not found with ID: " + eventId, null, null);
        }

        Event event = eventOpt.get();
        if (event.isCancelled()) {
            return new RegistrationResult(false, "Cannot register: This event has been cancelled by the organizer.", null, null);
        }

        if (event.isCompleted()) {
            return new RegistrationResult(false, "Cannot register: This event has already concluded.", null, null);
        }

        if (event.getEndDateTime() != null && event.getEndDateTime().isBefore(LocalDateTime.now())) {
            return new RegistrationResult(false, "Cannot register: This event has already concluded.", null, null);
        }

        if (event.getOrganizerId().equals(user.getUserId())) {
            return new RegistrationResult(false, "Organizers cannot register as attendees for their own events.", null, null);
        }

        // Check for Attendee Schedule Conflict with other confirmed registrations
        List<Registration> userRegs = dataStore.getRegistrationRepository().findByUser(user.getUserId());
        for (Registration ur : userRegs) {
            if (ur.isConfirmed() || ur.isCheckedIn()) {
                Optional<Event> otherEventOpt = dataStore.getEventRepository().findById(ur.getEventId());
                if (otherEventOpt.isPresent()) {
                    Event other = otherEventOpt.get();
                    if (!other.isCancelled() && !other.getEventId().equals(eventId) &&
                            InputValidator.doIntervalsOverlap(event.getStartDateTime(), event.getEndDateTime(),
                                    other.getStartDateTime(), other.getEndDateTime())) {
                        return new RegistrationResult(false,
                                "Schedule Conflict: You are already confirmed for '" + other.getTitle() +
                                "' during an overlapping time window.", null, null);
                    }
                }
            }
        }

        // Check duplicate registration
        Optional<Registration> existingOpt = dataStore.getRegistrationRepository().findByEventAndUser(eventId, user.getUserId());
        if (existingOpt.isPresent()) {
            Registration reg = existingOpt.get();
            if (reg.isConfirmed()) {
                return new RegistrationResult(false, "You are already registered and confirmed for this event.", reg, null);
            } else if (reg.isWaitlisted()) {
                int pos = getWaitlistPosition(eventId, user.getUserId());
                return new RegistrationResult(false, "You are already on the waitlist for this event at position #" + pos + ".", reg, null);
            } else if (reg.isCheckedIn()) {
                return new RegistrationResult(false, "You have already checked in for this event.", reg, null);
            }
        }

        // Check Capacity
        int confirmedCount = dataStore.getRegistrationRepository().countConfirmedByEvent(eventId);
        RegistrationStatus assignedStatus;
        String message;

        if (confirmedCount < event.getCapacity()) {
            assignedStatus = RegistrationStatus.CONFIRMED;
            int remaining = event.getCapacity() - (confirmedCount + 1);
            message = "Registration successful! You have a CONFIRMED spot for '" + event.getTitle() + "'." +
                    (remaining > 0 ? " (" + remaining + " spots remaining)" : " (Event is now at full capacity)");
        } else {
            assignedStatus = RegistrationStatus.WAITLISTED;
            int waitlistPos = dataStore.getRegistrationRepository().countWaitlistedByEvent(eventId) + 1;
            message = "Event is currently at maximum capacity (" + event.getCapacity() + "/" + event.getCapacity() +
                    "). You have been added to the WAITLIST at position #" + waitlistPos +
                    ". If an attendee cancels, you will be automatically promoted!";
        }

        String regId = dataStore.nextRegistrationId();
        Registration newReg = new Registration(regId, eventId, user.getUserId(), assignedStatus);
        dataStore.getRegistrationRepository().add(newReg);
        dataStore.saveRegistrations();

        return new RegistrationResult(true, message, newReg, null);
    }

    /**
     * Cancels an existing registration and automatically promotes the next waitlisted
     * user in FIFO order if a spot becomes available.
     */
    public synchronized CancellationResult cancelRegistration(User requestingUser, String registrationId) {
        Optional<Registration> regOpt = dataStore.getRegistrationRepository().findById(registrationId);
        if (regOpt.isEmpty()) {
            return new CancellationResult(false, "Registration record not found.", null, null);
        }

        Registration reg = regOpt.get();

        // Check ownership or admin privilege
        if (requestingUser == null || (!requestingUser.isAdmin() && !reg.getUserId().equals(requestingUser.getUserId()))) {
            return new CancellationResult(false, "Access denied. You can only cancel your own registrations.", null, null);
        }

        if (reg.isCancelled()) {
            return new CancellationResult(false, "This registration is already cancelled.", null, null);
        }

        RegistrationStatus previousStatus = reg.getStatus();
        reg.setStatus(RegistrationStatus.CANCELLED);

        User promotedUser = null;
        Registration promotedReg = null;

        // Auto-promote from Waitlist if the cancelling user occupied a confirmed spot
        if (previousStatus == RegistrationStatus.CONFIRMED || previousStatus == RegistrationStatus.CHECKED_IN) {
            List<Registration> waitlist = dataStore.getRegistrationRepository().findWaitlistedByEvent(reg.getEventId());
            if (!waitlist.isEmpty()) {
                promotedReg = waitlist.get(0); // FIFO: first person on waitlist
                promotedReg.setStatus(RegistrationStatus.CONFIRMED);
                promotedUser = dataStore.getUserRepository().findById(promotedReg.getUserId()).orElse(null);
            }
        }

        dataStore.saveRegistrations();

        String msg = "Registration cancelled successfully.";
        if (promotedReg != null && promotedUser != null) {
            msg += "\n[Waitlist Auto-Promotion]: " + promotedUser.getFullName() +
                    " (" + promotedUser.getUsername() + ") has been automatically promoted from the waitlist to CONFIRMED status!";
        }

        return new CancellationResult(true, msg, promotedReg, promotedUser);
    }

    /**
     * Checks in an attendee on event day.
     */
    public synchronized CheckInResult checkInAttendee(User requestingUser, String eventId, String attendeeUserId) {
        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (eventOpt.isEmpty()) {
            return new CheckInResult(false, "Event not found.", null);
        }

        Event event = eventOpt.get();

        // Permission check: Admin or the owning Organizer
        if (requestingUser == null || (!requestingUser.isAdmin() && !event.getOrganizerId().equals(requestingUser.getUserId()))) {
            return new CheckInResult(false, "Access denied. Only the event Organizer or Administrator can perform check-ins.", null);
        }

        if (event.isCancelled()) {
            return new CheckInResult(false, "Cannot check in: Event has been cancelled.", null);
        }

        Optional<Registration> regOpt = dataStore.getRegistrationRepository().findByEventAndUser(eventId, attendeeUserId);
        if (regOpt.isEmpty()) {
            return new CheckInResult(false, "No active registration found for this user on this event.", null);
        }

        Registration reg = regOpt.get();
        if (reg.isCheckedIn()) {
            return new CheckInResult(false, "Attendee is already checked in (Checked in at: " +
                    reg.getCheckedInAt().format(ConsoleIO.DISPLAY_FORMATTER) + ").", reg);
        }

        if (reg.isWaitlisted()) {
            return new CheckInResult(false, "Cannot check in: Attendee is on the WAITLIST and does not have a confirmed spot.", reg);
        }

        if (reg.isCancelled()) {
            return new CheckInResult(false, "Cannot check in: Attendee's registration is cancelled.", reg);
        }

        reg.setStatus(RegistrationStatus.CHECKED_IN);
        reg.setCheckedInAt(LocalDateTime.now());
        dataStore.saveRegistrations();

        Optional<User> attendeeUser = dataStore.getUserRepository().findById(attendeeUserId);
        String attendeeName = attendeeUser.map(User::getFullName).orElse(attendeeUserId);

        return new CheckInResult(true, "Successfully checked in " + attendeeName + " for '" + event.getTitle() + "'!", reg);
    }

    public static class RegistrationResult {
        public final boolean success;
        public final String message;
        public final Registration registration;
        public final User promotedUser;

        public RegistrationResult(boolean success, String message, Registration registration, User promotedUser) {
            this.success = success;
            this.message = message;
            this.registration = registration;
            this.promotedUser = promotedUser;
        }
    }

    public static class CancellationResult {
        public final boolean success;
        public final String message;
        public final Registration promotedRegistration;
        public final User promotedUser;

        public CancellationResult(boolean success, String message, Registration promotedRegistration, User promotedUser) {
            this.success = success;
            this.message = message;
            this.promotedRegistration = promotedRegistration;
            this.promotedUser = promotedUser;
        }
    }

    public static class CheckInResult {
        public final boolean success;
        public final String message;
        public final Registration registration;

        public CheckInResult(boolean success, String message, Registration registration) {
            this.success = success;
            this.message = message;
            this.registration = registration;
        }
    }
}
