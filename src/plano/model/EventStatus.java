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

    /**
     * Reads a saved status name, in any letter case.
     *
     * @throws IllegalArgumentException if the name is missing or is not an event status
     */
    public static EventStatus fromString(String val) {
        if (val != null) {
            for (EventStatus status : values()) {
                if (status.name().equalsIgnoreCase(val.trim())) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("unknown event status '" + val + "'");
    }
}
