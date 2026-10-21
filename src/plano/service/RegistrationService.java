package plano.service;

import plano.model.*;
import plano.repository.DataStore;
import plano.util.InputValidator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service managing registrations, capacity enforcement, FIFO waitlists,
 * automatic waitlist promotion, and attendee check-ins.
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
        if (!eventOpt.isPresent()) {
            return new RegistrationResult(false, "Event not found with ID: " + eventId, null, null);
        }

        Event event = eventOpt.get();
        LocalDateTime now = LocalDateTime.now();
        if (event.isCancelled()) {
            return new RegistrationResult(false, "Cannot register: This event has been cancelled by the organizer.", null, null);
        }

        if (event.isCompleted() || event.hasEnded(now)) {
            return new RegistrationResult(false, "Cannot register: This event has already concluded.", null, null);
        }

        if (event.isOngoing() || event.hasStarted(now)) {
            return new RegistrationResult(false, "Cannot register: Registration is closed because the event has already started.", null, null);
        }

        if (event.getOrganizerId().equals(user.getUserId())) {
            return new RegistrationResult(false, "Organizers cannot register as attendees for their own events.", null, null);
        }

        Event conflict = findScheduleConflict(user.getUserId(), event);
        if (conflict != null) {
            return new RegistrationResult(false,
                    "Schedule Conflict: You are already confirmed for '" + conflict.getTitle() +
                    "' during an overlapping time window.", null, null);
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
            int waitlistCount = dataStore.getRegistrationRepository().countWaitlistedByEvent(eventId);
            int maxWaitlist = Math.max(50, event.getCapacity() * 2);
            if (waitlistCount >= maxWaitlist) {
                return new RegistrationResult(false, "The waitlist for this event is currently full (maximum " + maxWaitlist + " spots reached).", null, null);
            }
            assignedStatus = RegistrationStatus.WAITLISTED;
            int waitlistPos = waitlistCount + 1;
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
     * Cancels an existing registration. If it held a seat, the seat is offered
     * to the waitlist in FIFO order.
     */
    public synchronized CancellationResult cancelRegistration(User requestingUser, String registrationId) {
        Optional<Registration> regOpt = dataStore.getRegistrationRepository().findById(registrationId);
        if (!regOpt.isPresent()) {
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

        Optional<Event> eventOpt = dataStore.getEventRepository().findById(reg.getEventId());
        if (eventOpt.isPresent()) {
            Event ev = eventOpt.get();
            if (ev.isOngoing() || ev.isCompleted()) {
                return new CancellationResult(false, "Cannot cancel registration: The event is already ongoing or completed.", null, null);
            }
            if (ev.hasStarted(LocalDateTime.now())) {
                return new CancellationResult(false, "Cannot cancel registration: The event has already started.", null, null);
            }
        }

        boolean freedSeat = reg.holdsSeat();
        reg.cancel();

        List<Registration> promoted = freedSeat && eventOpt.isPresent()
                ? fillOpenSpots(eventOpt.get())
                : Collections.<Registration>emptyList();

        dataStore.saveRegistrations();

        Registration firstPromoted = promoted.isEmpty() ? null : promoted.get(0);
        User firstPromotedUser = firstPromoted == null ? null
                : dataStore.getUserRepository().findById(firstPromoted.getUserId()).orElse(null);
        String msg = "Registration cancelled successfully." + describePromotions(promoted);
        return new CancellationResult(true, msg, firstPromoted, firstPromotedUser);
    }

    /**
     * Checks in an attendee. Check-in opens on the event day.
     */
    public synchronized CheckInResult checkInAttendee(User requestingUser, String eventId, String attendeeUserId) {
        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (!eventOpt.isPresent()) {
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

        LocalDateTime now = LocalDateTime.now();
        if (!event.isCheckInOpen(now)) {
            return new CheckInResult(false, "Check-in opens on the event day (" +
                    event.getStartDateTime().toLocalDate() + ").", null);
        }

        Optional<Registration> regOpt = dataStore.getRegistrationRepository().findByEventAndUser(eventId, attendeeUserId);
        if (!regOpt.isPresent()) {
            return new CheckInResult(false, "No active registration found for this user on this event.", null);
        }

        Registration reg = regOpt.get();
        try {
            reg.checkIn(now);
        } catch (IllegalStateException e) {
            return new CheckInResult(false, e.getMessage(), reg);
        }
        dataStore.saveRegistrations();

        Optional<User> attendeeUser = dataStore.getUserRepository().findById(attendeeUserId);
        String attendeeName = attendeeUser.map(User::getFullName).orElse(attendeeUserId);

        return new CheckInResult(true, "Successfully checked in " + attendeeName + " for '" + event.getTitle() + "'!", reg);
    }

    // ==================== SHARED WAITLIST AND CANCELLATION RULES ====================

    /**
     * Fills open seats from the waitlist in FIFO order. A waitlisted attendee is skipped and stays
     * waitlisted when the seat would clash with another event they are confirmed for, or when their
     * account is deactivated. Nothing is promoted for events that are cancelled, completed or started.
     * Does not save; the caller saves registrations.
     */
    List<Registration> fillOpenSpots(Event event) {
        List<Registration> promoted = new ArrayList<>();
        if (event.isCancelled() || event.isCompleted() || event.hasStarted(LocalDateTime.now())) {
            return promoted;
        }

        int openSeats = event.getCapacity() - dataStore.getRegistrationRepository().countConfirmedByEvent(event.getEventId());
        for (Registration waiting : dataStore.getRegistrationRepository().findWaitlistedByEvent(event.getEventId())) {
            if (openSeats <= 0) {
                break;
            }
            Optional<User> waitingUser = dataStore.getUserRepository().findById(waiting.getUserId());
            if (!waitingUser.isPresent() || !waitingUser.get().isActive()) {
                continue;
            }
            if (findScheduleConflict(waiting.getUserId(), event) != null) {
                continue;
            }
            waiting.promote();
            promoted.add(waiting);
            openSeats--;
        }
        return promoted;
    }

    /** Builds the "[Waitlist Auto-Promotion]" lines shown after seats are filled. */
    String describePromotions(List<Registration> promoted) {
        StringBuilder sb = new StringBuilder();
        for (Registration r : promoted) {
            Optional<User> u = dataStore.getUserRepository().findById(r.getUserId());
            String who = u.isPresent() ? u.get().getFullName() + " (" + u.get().getUsername() + ")" : r.getUserId();
            sb.append("\n[Waitlist Auto-Promotion]: ").append(who)
                    .append(" has been automatically promoted from the waitlist to CONFIRMED status!");
        }
        return sb.toString();
    }

    /**
     * Cancels every confirmed and waitlisted registration of a cancelled event.
     * Check-ins are kept as attendance history. Does not save; the caller saves registrations.
     *
     * @return how many registrations were cancelled
     */
    int cancelActiveRegistrationsForEvent(String eventId) {
        int cancelled = 0;
        for (Registration r : dataStore.getRegistrationRepository().findByEvent(eventId)) {
            if (r.isConfirmed() || r.isWaitlisted()) {
                r.cancel();
                cancelled++;
            }
        }
        return cancelled;
    }

    /**
     * Cancels a user's confirmed and waitlisted registrations for events that have not started,
     * then offers each freed seat to the waitlist. Used when an account is deactivated. Saves registrations.
     */
    ReleaseResult releaseUpcomingRegistrations(String userId) {
        LocalDateTime now = LocalDateTime.now();
        int released = 0;
        List<Registration> promoted = new ArrayList<>();
        for (Registration r : dataStore.getRegistrationRepository().findByUser(userId)) {
            if (!(r.isConfirmed() || r.isWaitlisted())) {
                continue;
            }
            Optional<Event> ev = dataStore.getEventRepository().findById(r.getEventId());
            if (!ev.isPresent() || ev.get().isCancelled() || ev.get().isCompleted() || ev.get().hasStarted(now)) {
                continue;
            }
            boolean freedSeat = r.holdsSeat();
            r.cancel();
            released++;
            if (freedSeat) {
                promoted.addAll(fillOpenSpots(ev.get()));
            }
        }
        if (released > 0) {
            dataStore.saveRegistrations();
        }
        return new ReleaseResult(released, promoted);
    }

    /** Returns another event the user holds a seat for that overlaps the given event, or null. */
    private Event findScheduleConflict(String userId, Event event) {
        for (Registration ur : dataStore.getRegistrationRepository().findByUser(userId)) {
            if (!ur.holdsSeat()) {
                continue;
            }
            Optional<Event> otherOpt = dataStore.getEventRepository().findById(ur.getEventId());
            if (otherOpt.isPresent()) {
                Event other = otherOpt.get();
                if (!other.isCancelled() && !other.getEventId().equals(event.getEventId()) &&
                        InputValidator.doIntervalsOverlap(event.getStartDateTime(), event.getEndDateTime(),
                                other.getStartDateTime(), other.getEndDateTime())) {
                    return other;
                }
            }
        }
        return null;
    }

    // ==================== RESULT CARRIERS ====================

    static class ReleaseResult {
        final int released;
        final List<Registration> promoted;

        ReleaseResult(int released, List<Registration> promoted) {
            this.released = released;
            this.promoted = promoted;
        }
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
