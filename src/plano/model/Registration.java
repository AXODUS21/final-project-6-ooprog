package plano.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an attendee's registration for an event.
 * The IDs and registration time never change; the status only changes through
 * {@link #promote()}, {@link #checkIn(LocalDateTime)} and {@link #cancel()}.
 */
public class Registration {
    private static final DateTimeFormatter MESSAGE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String registrationId;
    private final String eventId;
    private final String userId;
    private RegistrationStatus status;
    private final LocalDateTime registeredAt;
    private LocalDateTime checkedInAt;

    /**
     * Creates a new registration, which starts either CONFIRMED (a seat was free)
     * or WAITLISTED (the event was full). A null status means CONFIRMED.
     *
     * @throws IllegalArgumentException for any other starting status
     */
    public Registration(String registrationId, String eventId, String userId, RegistrationStatus initialStatus) {
        this(registrationId, eventId, userId,
                initialStatus != null ? initialStatus : RegistrationStatus.CONFIRMED,
                LocalDateTime.now(), null);
        if (status != RegistrationStatus.CONFIRMED && status != RegistrationStatus.WAITLISTED) {
            throw new IllegalArgumentException("A new registration must start as CONFIRMED or WAITLISTED.");
        }
    }

    /** Restores a registration exactly as it was saved. */
    private Registration(String registrationId, String eventId, String userId, RegistrationStatus status,
                         LocalDateTime registeredAt, LocalDateTime checkedInAt) {
        this.registrationId = registrationId;
        this.eventId = eventId;
        this.userId = userId;
        this.status = status;
        this.registeredAt = registeredAt;
        this.checkedInAt = checkedInAt;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getUserId() {
        return userId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
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

    /** True while the registration occupies one of the event's seats. */
    public boolean holdsSeat() {
        return isConfirmed() || isCheckedIn();
    }

    // ==================== BEHAVIOR ====================

    /**
     * Moves a waitlisted registration into a confirmed seat.
     *
     * @throws IllegalStateException if the registration is not waitlisted
     */
    public void promote() {
        if (!isWaitlisted()) {
            throw new IllegalStateException("Only waitlisted registrations can be promoted.");
        }
        status = RegistrationStatus.CONFIRMED;
    }

    /**
     * Marks a confirmed attendee as present.
     *
     * @throws IllegalStateException with a user-facing reason if the attendee cannot be checked in
     */
    public void checkIn(LocalDateTime now) {
        if (isCheckedIn()) {
            String when = checkedInAt != null ? checkedInAt.format(MESSAGE_FORMAT) : "an unknown time";
            throw new IllegalStateException("Attendee is already checked in (checked in at " + when + ").");
        }
        if (isWaitlisted()) {
            throw new IllegalStateException("Cannot check in: attendee is on the WAITLIST and does not have a confirmed spot.");
        }
        if (isCancelled()) {
            throw new IllegalStateException("Cannot check in: attendee's registration is cancelled.");
        }
        status = RegistrationStatus.CHECKED_IN;
        checkedInAt = now;
    }

    /**
     * Cancels the registration.
     *
     * @throws IllegalStateException if it is already cancelled
     */
    public void cancel() {
        if (isCancelled()) {
            throw new IllegalStateException("This registration is already cancelled.");
        }
        status = RegistrationStatus.CANCELLED;
    }

    // ==================== PERSISTENCE ====================

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
        LocalDateTime registeredAt = parseDateTime((String) map.get("registeredAt"));
        return new Registration(
                (String) map.get("registrationId"),
                (String) map.get("eventId"),
                (String) map.get("userId"),
                RegistrationStatus.fromString((String) map.get("status")),
                registeredAt != null ? registeredAt : LocalDateTime.now(),
                parseDateTime((String) map.get("checkedInAt")));
    }

    private static LocalDateTime parseDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
