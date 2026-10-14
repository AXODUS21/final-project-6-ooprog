package plano.model;

/**
 * User roles supported by PlanO.
 */
public enum Role {
    ADMIN("Administrator"),
    ORGANIZER("Event Organizer"),
    ATTENDEE("Regular User / Attendee");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Role fromString(String val) {
        if (val == null) return ATTENDEE;
        try {
            return Role.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ATTENDEE;
        }
    }
}
