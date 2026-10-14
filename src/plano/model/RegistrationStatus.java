package plano.model;

/**
 * Status of an Event Registration.
 */
public enum RegistrationStatus {
    CONFIRMED("Confirmed"),
    WAITLISTED("Waitlisted"),
    CANCELLED("Cancelled"),
    CHECKED_IN("Checked In");

    private final String displayName;

    RegistrationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RegistrationStatus fromString(String val) {
        if (val == null) return CONFIRMED;
        try {
            return RegistrationStatus.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CONFIRMED;
        }
    }
}
