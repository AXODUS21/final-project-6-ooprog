package plano.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an event created by an Organizer or Admin.
 * The ID, organizer and creation time never change. The event owns its status lifecycle:
 * statuses only move forward (Upcoming -> Ongoing -> Completed, or to Cancelled)
 * and must agree with the schedule.
 */
public class Event {
    private static final DateTimeFormatter MESSAGE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String eventId;
    private String title;
    private String description;
    private final String organizerId;
    private String category;
    private String venue;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private int capacity;
    private EventStatus status;
    private final LocalDateTime createdAt;

    /** Creates a new, upcoming event. */
    public Event(String eventId, String title, String description, String organizerId,
                 String category, String venue, LocalDateTime startDateTime,
                 LocalDateTime endDateTime, int capacity) {
        this(eventId, title, description, organizerId, category, venue, startDateTime, endDateTime,
                capacity, EventStatus.UPCOMING, LocalDateTime.now());
    }

    /** Restores an event exactly as it was saved. */
    private Event(String eventId, String title, String description, String organizerId,
                  String category, String venue, LocalDateTime startDateTime, LocalDateTime endDateTime,
                  int capacity, EventStatus status, LocalDateTime createdAt) {
        this.eventId = eventId;
        this.title = title;
        this.description = description;
        this.organizerId = organizerId;
        this.category = category;
        this.venue = venue;
        this.startDateTime = toWholeMinute(startDateTime);
        this.endDateTime = toWholeMinute(endDateTime);
        this.capacity = capacity;
        this.status = status;
        this.createdAt = createdAt;
    }

    /**
     * Event times are kept to whole minutes, so an event that starts exactly
     * when another one ends is never treated as overlapping it.
     */
    public static LocalDateTime toWholeMinute(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.truncatedTo(ChronoUnit.MINUTES);
    }

    public String getEventId() {
        return eventId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public String getCategory() {
        return category;
    }

    public String getVenue() {
        return venue;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public int getCapacity() {
        return capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isCancelled() {
        return status == EventStatus.CANCELLED;
    }

    public boolean isCompleted() {
        return status == EventStatus.COMPLETED;
    }

    public boolean isUpcoming() {
        return status == EventStatus.UPCOMING;
    }

    public boolean isOngoing() {
        return status == EventStatus.ONGOING;
    }

    public boolean hasStarted(LocalDateTime now) {
        return startDateTime != null && !now.isBefore(startDateTime);
    }

    public boolean hasEnded(LocalDateTime now) {
        return endDateTime != null && !now.isBefore(endDateTime);
    }

    /**
     * True for an event that is neither cancelled nor completed but whose end time has passed,
     * meaning its organizer still has to mark it Completed.
     */
    public boolean isAwaitingCompletion(LocalDateTime now) {
        return (isUpcoming() || isOngoing()) && hasEnded(now);
    }

    /**
     * Check-in opens on the day the event starts and stays open afterwards,
     * so attendance can still be recorded once the event is over.
     */
    public boolean isCheckInOpen(LocalDateTime now) {
        return !isCancelled() && startDateTime != null
                && !now.toLocalDate().isBefore(startDateTime.toLocalDate());
    }

    // ==================== BEHAVIOR ====================

    /**
     * Replaces all editable details in one step. Callers validate the new values
     * first (see EventService), so an event is never left half-edited.
     */
    public void updateDetails(String title, String description, String category, String venue,
                              LocalDateTime startDateTime, LocalDateTime endDateTime, int capacity) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.venue = venue;
        this.startDateTime = toWholeMinute(startDateTime);
        this.endDateTime = toWholeMinute(endDateTime);
        this.capacity = capacity;
    }

    /**
     * Cancels the event.
     *
     * @throws IllegalStateException if the event is already cancelled or completed
     */
    public void cancel() {
        if (isCancelled()) {
            throw new IllegalStateException("This event is already cancelled.");
        }
        if (isCompleted()) {
            throw new IllegalStateException("Completed events cannot be cancelled.");
        }
        status = EventStatus.CANCELLED;
    }

    /**
     * Moves the event forward to a new status.
     *
     * @throws IllegalStateException with a user-facing reason if the change is not allowed
     */
    public void changeStatus(EventStatus target, LocalDateTime now) {
        if (target == null) {
            throw new IllegalArgumentException("A target status is required.");
        }
        if (target == EventStatus.CANCELLED) {
            cancel();
            return;
        }
        if (isCancelled()) {
            throw new IllegalStateException("Cancelled events cannot be modified or reactivated.");
        }
        if (isCompleted()) {
            throw new IllegalStateException("Completed events cannot be reopened or changed.");
        }
        if (target == status) {
            throw new IllegalStateException("This event is already " + status.getDisplayName() + ".");
        }
        if (target == EventStatus.UPCOMING) {
            throw new IllegalStateException("An event cannot move back to Upcoming once it is Ongoing.");
        }
        if (target == EventStatus.ONGOING) {
            if (now.toLocalDate().isBefore(startDateTime.toLocalDate())) {
                throw new IllegalStateException("An event can only be marked Ongoing on its start date ("
                        + startDateTime.toLocalDate() + ") or later.");
            }
            if (hasEnded(now)) {
                throw new IllegalStateException("This event already ended at "
                        + endDateTime.format(MESSAGE_FORMAT) + ". Mark it Completed instead.");
            }
        }
        if (target == EventStatus.COMPLETED && !hasStarted(now)) {
            throw new IllegalStateException("Cannot mark event as Completed before its scheduled start time ("
                    + startDateTime.format(MESSAGE_FORMAT) + ").");
        }
        status = target;
    }

    // ==================== PERSISTENCE ====================

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", eventId);
        map.put("title", title);
        map.put("description", description);
        map.put("organizerId", organizerId);
        map.put("category", category);
        map.put("venue", venue);
        map.put("startDateTime", startDateTime != null ? startDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        map.put("endDateTime", endDateTime != null ? endDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        map.put("capacity", capacity);
        map.put("status", status != null ? status.name() : EventStatus.UPCOMING.name());
        map.put("createdAt", createdAt != null ? createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        return map;
    }

    public static Event fromMap(Map<String, Object> map) {
        if (map == null) return null;

        int capacity = 0;
        Object capObj = map.get("capacity");
        if (capObj instanceof Number) {
            capacity = ((Number) capObj).intValue();
        } else if (capObj != null) {
            capacity = Integer.parseInt(capObj.toString());
        }

        LocalDateTime createdAt = parseDateTime((String) map.get("createdAt"));
        return new Event(
                (String) map.get("eventId"),
                (String) map.get("title"),
                (String) map.get("description"),
                (String) map.get("organizerId"),
                (String) map.get("category"),
                (String) map.get("venue"),
                parseDateTime((String) map.get("startDateTime")),
                parseDateTime((String) map.get("endDateTime")),
                capacity,
                EventStatus.fromString((String) map.get("status")),
                createdAt != null ? createdAt : LocalDateTime.now());
    }

    private static LocalDateTime parseDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
