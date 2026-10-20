package plano.service;

import plano.model.Event;
import plano.model.EventStatus;
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
import java.util.List;
import java.util.Optional;

/**
 * Service managing event creation, updating, deletion, scheduling conflict checks,
 * and Strategy-based sorting and filtering.
 */
public class EventService {
    private final DataStore dataStore;
    private final EventFilterStrategy filterStrategy = new CompositeEventFilterStrategy();

    public EventService(DataStore dataStore) {
        this.dataStore = dataStore;
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
            Event conflict = conflicts.get(0);
            String msg = "Scheduling Conflict Detected! Venue '" + venue + "' is already booked for '" +
                    conflict.getTitle() + "' (" + conflict.getEventId() + ") from " +
                    conflict.getStartDateTime().format(ConsoleIO.DATE_TIME_FORMATTER) + " to " +
                    conflict.getEndDateTime().format(ConsoleIO.DATE_TIME_FORMATTER) + ".";
            return new EventResult(false, msg, null);
        }

        String eventId = dataStore.nextEventId();
        Event event = new Event(eventId, title.trim(), description != null ? description.trim() : "",
                creator.getUserId(), category.trim(), venue.trim(), startDateTime, endDateTime, capacity);

        dataStore.getEventRepository().add(event);
        dataStore.saveEvents();

        return new EventResult(true, "Event '" + title + "' created successfully with ID: " + eventId, event);
    }

    /**
     * Updates an existing event, checking permissions and re-validating scheduling conflicts.
     */
    public synchronized EventResult updateEvent(User requestingUser, String eventId,
                                                String title, String description,
                                                String category, String venue,
                                                LocalDateTime startDateTime, LocalDateTime endDateTime,
                                                int capacity, EventStatus status) {
        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (eventOpt.isEmpty()) {
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

        if (InputValidator.isNotEmpty(title)) {
            if (!InputValidator.isValidEventTitle(title)) {
                return new EventResult(false, "Event title must be between 3 and 100 characters.", null);
            }
            event.setTitle(title.trim());
        }

        if (description != null) {
            event.setDescription(description.trim());
        }

        if (InputValidator.isNotEmpty(category)) {
            if (!dataStore.getCategoryRepository().existsByName(category.trim())) {
                return new EventResult(false, "Category '" + category + "' does not exist.", null);
            }
            event.setCategory(category.trim());
        }

        String newVenue = InputValidator.isNotEmpty(venue) ? venue.trim() : event.getVenue();
        if (InputValidator.isNotEmpty(venue) && !InputValidator.isValidVenue(newVenue)) {
            return new EventResult(false, "Event venue/location must be between 2 and 100 characters.", null);
        }

        LocalDateTime newStart = startDateTime != null ? startDateTime : event.getStartDateTime();
        LocalDateTime newEnd = endDateTime != null ? endDateTime : event.getEndDateTime();

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
            Event conflict = conflicts.get(0);
            String msg = "Scheduling Conflict Detected! Venue '" + newVenue + "' is already booked for '" +
                    conflict.getTitle() + "' (" + conflict.getEventId() + ") from " +
                    conflict.getStartDateTime().format(ConsoleIO.DATE_TIME_FORMATTER) + " to " +
                    conflict.getEndDateTime().format(ConsoleIO.DATE_TIME_FORMATTER) + ".";
            return new EventResult(false, msg, null);
        }

        event.setVenue(newVenue);
        event.setStartDateTime(newStart);
        event.setEndDateTime(newEnd);

        if (capacity > 0) {
            int confirmedCount = dataStore.getRegistrationRepository().countConfirmedByEvent(eventId);
            if (capacity < confirmedCount) {
                return new EventResult(false, "Cannot reduce capacity to " + capacity +
                        ". There are already " + confirmedCount + " confirmed attendees.", null);
            }
            if (capacity > 50000) {
                return new EventResult(false, "Capacity exceeds maximum allowable limit (50,000).", null);
            }
            event.setCapacity(capacity);
        }

        if (status != null) {
            event.setStatus(status);
        }

        dataStore.saveEvents();
        return new EventResult(true, "Event '" + event.getTitle() + "' updated successfully.", event);
    }

    /**
     * Cancels an event. Admin can cancel any; Organizer can only cancel their own.
     */
    public synchronized EventResult cancelEvent(User requestingUser, String eventId) {
        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (eventOpt.isEmpty()) {
            return new EventResult(false, "Event not found with ID: " + eventId, null);
        }

        Event event = eventOpt.get();

        if (requestingUser == null || (!requestingUser.isAdmin() && !event.getOrganizerId().equals(requestingUser.getUserId()))) {
            return new EventResult(false, "Access denied. You can only cancel your own events.", null);
        }

        if (event.isCancelled()) {
            return new EventResult(false, "This event is already cancelled.", null);
        }

        event.setStatus(EventStatus.CANCELLED);
        dataStore.saveEvents();

        return new EventResult(true, "Event '" + event.getTitle() + "' has been cancelled.", event);
    }

    /**
     * Changes event status directly (e.g. to ONGOING or COMPLETED).
     */
    public synchronized EventResult updateEventStatus(User requestingUser, String eventId, EventStatus newStatus) {
        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (eventOpt.isEmpty()) {
            return new EventResult(false, "Event not found.", null);
        }

        Event event = eventOpt.get();
        if (requestingUser == null || (!requestingUser.isAdmin() && !event.getOrganizerId().equals(requestingUser.getUserId()))) {
            return new EventResult(false, "Access denied.", null);
        }

        if (event.isCancelled()) {
            return new EventResult(false, "Cancelled events cannot be modified or reactivated.", null);
        }

        if (event.isCompleted() && newStatus != EventStatus.COMPLETED) {
            return new EventResult(false, "Completed events cannot be reopened or changed to " + newStatus.getDisplayName() + ".", null);
        }

        if (newStatus == EventStatus.COMPLETED && event.getStartDateTime().isAfter(LocalDateTime.now())) {
            return new EventResult(false, "Cannot mark event as COMPLETED before its scheduled start time (" +
                    event.getStartDateTime().format(ConsoleIO.DATE_TIME_FORMATTER) + ").", null);
        }

        event.setStatus(newStatus);
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
