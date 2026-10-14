package plano.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an attendee's registration for an event.
 */
public class Registration {
    private String registrationId;
    private String eventId;
    private String userId;
    private RegistrationStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime checkedInAt;

    public Registration() {
        this.status = RegistrationStatus.CONFIRMED;
        this.registeredAt = LocalDateTime.now();
    }

    public Registration(String registrationId, String eventId, String userId, RegistrationStatus status) {
        this.registrationId = registrationId;
        this.eventId = eventId;
        this.userId = userId;
        this.status = status != null ? status : RegistrationStatus.CONFIRMED;
        this.registeredAt = LocalDateTime.now();
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public boolean isConfirmed() {
        return status == RegistrationStatus.CONFIRMED;
    }

    public boolean isWaitlisted() {
        return status == RegistrationStatus.WAITLISTED;
    }

    public boolean isCheckedIn() {
        return status == RegistrationStatus.CHECKED_IN;
    }

    public boolean isCancelled() {
        return status == RegistrationStatus.CANCELLED;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("registrationId", registrationId);
        map.put("eventId", eventId);
        map.put("userId", userId);
        map.put("status", status != null ? status.name() : RegistrationStatus.CONFIRMED.name());
        map.put("registeredAt", registeredAt != null ? registeredAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        map.put("checkedInAt", checkedInAt != null ? checkedInAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        return map;
    }

    public static Registration fromMap(Map<String, Object> map) {
        if (map == null) return null;
        Registration reg = new Registration();
        reg.setRegistrationId((String) map.get("registrationId"));
        reg.setEventId((String) map.get("eventId"));
        reg.setUserId((String) map.get("userId"));
        reg.setStatus(RegistrationStatus.fromString((String) map.get("status")));

        String ra = (String) map.get("registeredAt");
        if (ra != null && !ra.isBlank()) {
            reg.setRegisteredAt(LocalDateTime.parse(ra, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        String ca = (String) map.get("checkedInAt");
        if (ca != null && !ca.isBlank()) {
            reg.setCheckedInAt(LocalDateTime.parse(ca, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        return reg;
    }
}
