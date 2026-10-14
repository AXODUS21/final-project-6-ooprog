package plano.model;

/**
 * Status of an Event.
 */
public enum EventStatus {
    UPCOMING("Upcoming"),
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String displayName;

    EventStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static EventStatus fromString(String val) {
        if (val == null) return UPCOMING;
        try {
            return EventStatus.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UPCOMING;
        }
    }
}
