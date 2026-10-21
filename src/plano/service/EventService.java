package plano.service;

import plano.model.Event;
import plano.model.EventStatus;
import plano.model.Registration;
import plano.model.User;
import plano.repository.DataStore;
import plano.strategy.CompositeEventFilterStrategy;
import plano.strategy.EventFilterCriteria;
import plano.strategy.EventFilterStrategy;
import plano.strategy.EventSortStrategy;
import plano.util.ConsoleIO;
import plano.util.InputValidator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service managing event creation, updating, cancellation, status changes,
 * scheduling conflict checks, and Strategy-based sorting and filtering.
 */
public class EventService {
    private final DataStore dataStore;
    private final RegistrationService registrationService;
    private final EventFilterStrategy filterStrategy = new CompositeEventFilterStrategy();

    public EventService(DataStore dataStore) {
        this.dataStore = dataStore;
        this.registrationService = new RegistrationService(dataStore);
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
    public synchronized EventResult createEvent(User creator, String title, String description,
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
            return new EventResult(false, "Capacity must be between 1 and 50,000.", null);
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
     * Updates an existing event. Every field is validated first; the event is changed
     * only if all checks pass, so a rejected edit leaves the event exactly as it was.
     * Blank/null arguments (and capacity <= 0) keep the current value.
     * Raising the capacity offers the new seats to the waitlist.
     */
    public synchronized EventResult updateEvent(User requestingUser, String eventId,
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

        if (event.isCancelled()) {
            return new EventResult(false, "Cancelled events cannot be modified.", null);
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

        if (startDateTime != null && event.isUpcoming() && !InputValidator.isFuture(newStart)) {
            return new EventResult(false, "Rescheduled event start time must be in the future.", null);
        }

        if (startDateTime != null && !InputValidator.isWithinMaxHorizon(newStart)) {
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

        int newCapacity = event.getCapacity();
        if (capacity > 0) {
            int confirmedCount = dataStore.getRegistrationRepository().countConfirmedByEvent(eventId);
            if (capacity < confirmedCount) {
                return new EventResult(false, "Cannot reduce capacity to " + capacity +
                        ". There are already " + confirmedCount + " confirmed attendees.", null);
            }
            if (capacity > 50000) {
                return new EventResult(false, "Capacity exceeds maximum allowable limit (50,000).", null);
            }
            newCapacity = capacity;
        }

        // 2. Every check passed: apply all changes together.
        boolean capacityGrew = newCapacity > event.getCapacity();
        event.setTitle(newTitle);
        event.setDescription(newDescription);
        event.setCategory(newCategory);
        event.setVenue(newVenue);
        event.setStartDateTime(newStart);
        event.setEndDateTime(newEnd);
        event.setCapacity(newCapacity);

        List<Registration> promoted = capacityGrew
                ? registrationService.fillOpenSpots(event)
                : Collections.<Registration>emptyList();

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
    public synchronized EventResult cancelEvent(User requestingUser, String eventId) {
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
    public synchronized EventResult updateEventStatus(User requestingUser, String eventId, EventStatus newStatus) {
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
