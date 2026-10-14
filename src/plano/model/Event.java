package plano.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an event created by an Organizer or Admin.
 */
public class Event {
    private String eventId;
    private String title;
    private String description;
    private String organizerId;
    private String category;
    private String venue;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private int capacity;
    private EventStatus status;
    private LocalDateTime createdAt;

    public Event() {
        this.status = EventStatus.UPCOMING;
        this.createdAt = LocalDateTime.now();
    }

    public Event(String eventId, String title, String description, String organizerId,
                 String category, String venue, LocalDateTime startDateTime,
                 LocalDateTime endDateTime, int capacity) {
        this.eventId = eventId;
        this.title = title;
        this.description = description;
        this.organizerId = organizerId;
        this.category = category;
        this.venue = venue;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.capacity = capacity;
        this.status = EventStatus.UPCOMING;
        this.createdAt = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
        Event ev = new Event();
        ev.setEventId((String) map.get("eventId"));
        ev.setTitle((String) map.get("title"));
        ev.setDescription((String) map.get("description"));
        ev.setOrganizerId((String) map.get("organizerId"));
        ev.setCategory((String) map.get("category"));
        ev.setVenue((String) map.get("venue"));

        String sdt = (String) map.get("startDateTime");
        if (sdt != null && !sdt.isBlank()) {
            ev.setStartDateTime(LocalDateTime.parse(sdt, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        String edt = (String) map.get("endDateTime");
        if (edt != null && !edt.isBlank()) {
            ev.setEndDateTime(LocalDateTime.parse(edt, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        Object capObj = map.get("capacity");
        if (capObj instanceof Number) {
            ev.setCapacity(((Number) capObj).intValue());
        } else if (capObj != null) {
            ev.setCapacity(Integer.parseInt(capObj.toString()));
        }

        ev.setStatus(EventStatus.fromString((String) map.get("status")));

        String ca = (String) map.get("createdAt");
        if (ca != null && !ca.isBlank()) {
            ev.setCreatedAt(LocalDateTime.parse(ca, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        return ev;
    }
}
