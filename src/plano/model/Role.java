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

    /**
     * Reads a saved role name, in any letter case.
     *
     * @throws IllegalArgumentException if the name is missing or is not a role
     */
    public static Role fromString(String val) {
        if (val != null) {
            for (Role role : values()) {
                if (role.name().equalsIgnoreCase(val.trim())) {
                    return role;
                }
            }
        }
        throw new IllegalArgumentException("unknown role '" + val + "'");
    }
}
