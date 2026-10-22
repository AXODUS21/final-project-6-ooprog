package plano.service;

import plano.model.Event;
import plano.model.EventStatus;
import plano.model.Registration;
import plano.model.User;
import plano.repository.DataStore;
import plano.strategy.EventFilterCriteria;
import plano.strategy.EventFilterStrategy;
import plano.strategy.EventSortStrategy;
import plano.strategy.MultiCriteriaFilterStrategy;
import plano.util.ConsoleIO;
import plano.util.InputValidator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing event creation, updating, cancellation, status changes,
 * scheduling conflict checks, and Strategy-based sorting and filtering.
 */
public class EventService {
    private final DataStore dataStore;
    private final RegistrationService registrationService;
    private final EventFilterStrategy filterStrategy = new MultiCriteriaFilterStrategy();

    /**
     * @param registrationService the application's shared registration service, used to fill the
     *                            waitlist, cancel registrations and check attendees' schedules
     */
    public EventService(DataStore dataStore, RegistrationService registrationService) {
        this.dataStore = dataStore;
        this.registrationService = registrationService;
    }

    public List<Event> getAllEvents() {
        return dataStore.getEventRepository().findAll();
    }

    public Optional<Event> getEventById(String eventId) {
        return dataStore.getEventRepository().findById(eventId);
    }

    public List<Event> getEventsByOrganizer(String organizerId) {
        return dataStore.getEventRepository().findByOrganizer(organizerId);
    }

    /**
     * Creates a new event with scheduling conflict detection and field validation.
     */
    public EventResult createEvent(User creator, String title, String description,
                                                String category, String venue,
                                                LocalDateTime startDateTime, LocalDateTime endDateTime,
                                                int capacity) {
        if (creator == null || (!creator.isOrganizer() && !creator.isAdmin())) {
            return new EventResult(false, "Access denied. Only Organizers or Administrators can create events.", null);
        }

        startDateTime = Event.toWholeMinute(startDateTime);
        endDateTime = Event.toWholeMinute(endDateTime);

        if (!InputValidator.isValidEventTitle(title)) {
            return new EventResult(false, "Event title must be between 3 and 100 characters.", null);
        }

        if (!InputValidator.isValidVenue(venue)) {
            return new EventResult(false, "Event venue/location must be between 2 and 100 characters.", null);
        }

        if (!InputValidator.isNotEmpty(category) || !dataStore.getCategoryRepository().existsByName(category.trim())) {
            return new EventResult(false, "Category '" + category + "' does not exist in the system. Please select a valid category.", null);
        }

        if (!InputValidator.isValidCapacity(capacity)) {
            return new EventResult(false, capacityRangeMessage(), null);
        }

        if (!InputValidator.isFuture(startDateTime)) {
            return new EventResult(false, "Event start time must be in the future.", null);
        }

        if (!InputValidator.isWithinMaxHorizon(startDateTime)) {
            return new EventResult(false, "Event cannot be scheduled more than " +
                    InputValidator.MAX_EVENT_HORIZON_YEARS + " years in advance.", null);
        }

        if (!InputValidator.isValidTimeInterval(startDateTime, endDateTime)) {
            return new EventResult(false, "Event end time must be after the start time.", null);
        }

        if (!InputValidator.isValidEventDuration(startDateTime, endDateTime)) {
            return new EventResult(false, "Event duration must be at least 15 minutes and cannot exceed 30 days.", null);
        }

        // Check duplicate event for same organizer at the same time and venue
        List<Event> myEvents = dataStore.getEventRepository().findByOrganizer(creator.getUserId());
        for (Event e : myEvents) {
            if (!e.isCancelled() && e.getTitle().equalsIgnoreCase(title.trim()) &&
                    InputValidator.isVenueMatch(e.getVenue(), venue) &&
                    InputValidator.doIntervalsOverlap(startDateTime, endDateTime, e.getStartDateTime(), e.getEndDateTime())) {
                return new EventResult(false, "Duplicate Event: You already have an event titled '" + e.getTitle() +
                        "' scheduled at this venue during this time.", null);
            }
        }

        // Check for Scheduling Conflict at the same venue
        List<Event> conflicts = dataStore.getEventRepository().findConflictingEvents(venue, startDateTime, endDateTime, null);
        if (!conflicts.isEmpty()) {
            return new EventResult(false, conflictMessage(venue, conflicts.get(0)), null);
        }

        String eventId = dataStore.nextEventId();
        Event event = new Event(eventId, title.trim(), description != null ? description.trim() : "",
                creator.getUserId(), category.trim(), venue.trim(), startDateTime, endDateTime, capacity);

        dataStore.getEventRepository().add(event);
        dataStore.saveEvents();

        return new EventResult(true, "Event '" + title + "' created successfully with ID: " + eventId, event);
    }

    /**
     * Updates an Upcoming or Ongoing event. Every field is validated first; the event is changed
     * only if all checks pass, so a rejected edit leaves the event exactly as it was.
     * Blank/null arguments (and capacity <= 0) keep the current value.
     * The start time cannot move once the event has started, and new times are refused if they
     * would double-book anyone holding a seat. Raising the capacity offers the new seats to the waitlist.
     */
    public EventResult updateEvent(User requestingUser, String eventId,
                                   String title, String description,
                                   String category, String venue,
                                   LocalDateTime startDateTime, LocalDateTime endDateTime,
                                   int capacity) {
        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (!eventOpt.isPresent()) {
            return new EventResult(false, "Event not found with ID: " + eventId, null);
        }

        Event event = eventOpt.get();

        // Check permissions: Admin can edit any; Organizer only their own
        if (requestingUser == null || (!requestingUser.isAdmin() && !event.getOrganizerId().equals(requestingUser.getUserId()))) {
            return new EventResult(false, "Access denied. You can only edit your own events.", null);
        }

        // The event decides whether it can still be edited (Completed and Cancelled events cannot).
        try {
            event.checkEditable();
        } catch (IllegalStateException e) {
            return new EventResult(false, e.getMessage(), null);
        }

        // 1. Work out and validate the new values without touching the event.
        String newTitle = event.getTitle();
        if (InputValidator.isNotEmpty(title)) {
            if (!InputValidator.isValidEventTitle(title)) {
                return new EventResult(false, "Event title must be between 3 and 100 characters.", null);
            }
            newTitle = title.trim();
        }

        String newDescription = description != null ? description.trim() : event.getDescription();

        String newCategory = event.getCategory();
        if (InputValidator.isNotEmpty(category)) {
            if (!dataStore.getCategoryRepository().existsByName(category.trim())) {
                return new EventResult(false, "Category '" + category + "' does not exist.", null);
            }
            newCategory = category.trim();
        }

        String newVenue = event.getVenue();
        if (InputValidator.isNotEmpty(venue)) {
            if (!InputValidator.isValidVenue(venue)) {
                return new EventResult(false, "Event venue/location must be between 2 and 100 characters.", null);
            }
            newVenue = venue.trim();
        }

        LocalDateTime newStart = startDateTime != null ? Event.toWholeMinute(startDateTime) : event.getStartDateTime();
        LocalDateTime newEnd = endDateTime != null ? Event.toWholeMinute(endDateTime) : event.getEndDateTime();
        boolean startMoved = !Objects.equals(newStart, event.getStartDateTime());
        boolean timesChanged = startMoved || !Objects.equals(newEnd, event.getEndDateTime());

        if (startMoved && (event.isOngoing() || event.hasStarted(LocalDateTime.now()))) {
            return new EventResult(false, "The start time cannot change once the event has started.", null);
        }

        if (startMoved && !InputValidator.isFuture(newStart)) {
            return new EventResult(false, "Rescheduled event start time must be in the future.", null);
        }

        if (startMoved && !InputValidator.isWithinMaxHorizon(newStart)) {
            return new EventResult(false, "Event cannot be scheduled more than " +
                    InputValidator.MAX_EVENT_HORIZON_YEARS + " years in advance.", null);
        }

        if (!InputValidator.isValidTimeInterval(newStart, newEnd)) {
            return new EventResult(false, "Event end time must be after the start time.", null);
        }

        if (!InputValidator.isValidEventDuration(newStart, newEnd)) {
            return new EventResult(false, "Event duration must be at least 15 minutes and cannot exceed 30 days.", null);
        }

        // Check for Scheduling Conflict (excluding current event)
        List<Event> conflicts = dataStore.getEventRepository().findConflictingEvents(newVenue, newStart, newEnd, eventId);
        if (!conflicts.isEmpty()) {
            return new EventResult(false, conflictMessage(newVenue, conflicts.get(0)), null);
        }

        // Moving the event must not double-book the people who already hold a seat at it
        if (timesChanged) {
            int clashes = registrationService.countSeatHolderClashes(event, newStart, newEnd);
            if (clashes > 0) {
                return new EventResult(false, "Cannot move the event to this time: " + clashes +
                        " attendee(s) holding a seat already have another event during it.", null);
            }
        }

        int newCapacity = event.getCapacity();
        if (capacity > 0) {
            if (!InputValidator.isValidCapacity(capacity)) {
                return new EventResult(false, capacityRangeMessage(), null);
            }
            int seatsTaken = dataStore.getRegistrationRepository().countSeatsTakenByEvent(eventId);
            if (capacity < seatsTaken) {
                return new EventResult(false, "Cannot reduce capacity to " + capacity +
                        ". " + seatsTaken + " seats are already taken.", null);
            }
            newCapacity = capacity;
        }

        // 2. Every check passed: apply all changes together.
        boolean capacityGrew = newCapacity > event.getCapacity();
        event.updateDetails(newTitle, newDescription, newCategory, newVenue, newStart, newEnd, newCapacity);

        List<Registration> promoted = capacityGrew
                ? registrationService.fillOpenSpots(event)
                : Collections.emptyList();

        dataStore.saveEvents();
        if (!promoted.isEmpty()) {
            dataStore.saveRegistrations();
        }
        return new EventResult(true, "Event '" + event.getTitle() + "' updated successfully." +
                registrationService.describePromotions(promoted), event);
    }

    /**
     * Cancels an event and its confirmed and waitlisted registrations.
     * Admin can cancel any event; an Organizer only their own. Completed events cannot be cancelled.
     */
    public EventResult cancelEvent(User requestingUser, String eventId) {
        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (!eventOpt.isPresent()) {
            return new EventResult(false, "Event not found with ID: " + eventId, null);
        }

        Event event = eventOpt.get();

        if (requestingUser == null || (!requestingUser.isAdmin() && !event.getOrganizerId().equals(requestingUser.getUserId()))) {
            return new EventResult(false, "Access denied. You can only cancel your own events.", null);
        }

        try {
            event.cancel();
        } catch (IllegalStateException e) {
            return new EventResult(false, e.getMessage(), null);
        }

        int cancelledRegistrations = registrationService.cancelActiveRegistrationsForEvent(eventId);
        dataStore.saveEvents();
        dataStore.saveRegistrations();

        String msg = "Event '" + event.getTitle() + "' has been cancelled.";
        if (cancelledRegistrations > 0) {
            msg += " " + cancelledRegistrations + " registration(s) were cancelled with it.";
        }
        return new EventResult(true, msg, event);
    }

    /**
     * Moves an event forward to ONGOING or COMPLETED. The event itself decides
     * whether the change is allowed (see {@link Event#changeStatus}).
     */
    public EventResult updateEventStatus(User requestingUser, String eventId, EventStatus newStatus) {
        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (!eventOpt.isPresent()) {
            return new EventResult(false, "Event not found.", null);
        }

        Event event = eventOpt.get();
        if (requestingUser == null || (!requestingUser.isAdmin() && !event.getOrganizerId().equals(requestingUser.getUserId()))) {
            return new EventResult(false, "Access denied.", null);
        }

        if (newStatus == EventStatus.CANCELLED) {
            return cancelEvent(requestingUser, eventId);
        }

        try {
            event.changeStatus(newStatus, LocalDateTime.now());
        } catch (IllegalStateException e) {
            return new EventResult(false, e.getMessage(), null);
        }

        dataStore.saveEvents();
        return new EventResult(true, "Event status changed to " + newStatus.getDisplayName() + ".", event);
    }

    /**
     * Executes filtering using the Strategy pattern.
     */
    public List<Event> filterEvents(List<Event> events, EventFilterCriteria criteria) {
        return filterStrategy.filter(events, criteria, dataStore.getRegistrationRepository());
    }

    /**
     * Executes sorting using the provided Strategy pattern implementation.
     */
    public List<Event> sortEvents(List<Event> events, EventSortStrategy sortStrategy) {
        if (sortStrategy == null) {
            return new ArrayList<>(events);
        }
        return sortStrategy.sort(events, dataStore.getRegistrationRepository());
    }

    private static String capacityRangeMessage() {
        return String.format(Locale.US, "Capacity must be between 1 and %,d.", InputValidator.MAX_CAPACITY);
    }

    private static String conflictMessage(String venue, Event conflict) {
        return "Scheduling Conflict Detected! Venue '" + venue + "' is already booked for '" +
                conflict.getTitle() + "' (" + conflict.getEventId() + ") from " +
                conflict.getStartDateTime().format(ConsoleIO.DATE_TIME_FORMATTER) + " to " +
                conflict.getEndDateTime().format(ConsoleIO.DATE_TIME_FORMATTER) + ".";
    }

    public static class EventResult {
        public final boolean success;
        public final String message;
        public final Event event;

        public EventResult(boolean success, String message, Event event) {
            this.success = success;
            this.message = message;
            this.event = event;
        }
    }
}
