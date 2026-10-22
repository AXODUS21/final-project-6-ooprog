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

    /**
     * Reads a saved status name, in any letter case.
     *
     * @throws IllegalArgumentException if the name is missing or is not a registration status
     */
    public static RegistrationStatus fromString(String val) {
        if (val != null) {
            for (RegistrationStatus status : values()) {
                if (status.name().equalsIgnoreCase(val.trim())) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("unknown registration status '" + val + "'");
    }
}
