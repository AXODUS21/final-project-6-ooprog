package plano.model;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a registered user account in the PlanO system.
 * The ID, username and creation time never change. The password is never exposed:
 * other classes can only check a candidate password with {@link #passwordMatches}.
 */
public class User {
    private final String userId;
    private final String username;
    private String password;
    private String fullName;
    private String email;
    private Role role;
    private boolean active;
    private final LocalDateTime createdAt;

    /** Creates a new, active account. */
    public User(String userId, String username, String password, String fullName, String email, Role role) {
        this(userId, username, password, fullName, email, role, true, LocalDateTime.now());
    }

    /** Restores an account exactly as it was saved. */
    private User(String userId, String username, String password, String fullName, String email,
                 Role role, boolean active, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.role = role != null ? role : Role.ATTENDEE;
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isOrganizer() {
        return role == Role.ORGANIZER;
    }

    public boolean isAttendee() {
        return role == Role.ATTENDEE;
    }

    // ==================== BEHAVIOR ====================

    /** Checks a login attempt without revealing the stored password. */
    public boolean passwordMatches(String candidate) {
        return password != null && password.equals(candidate);
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
    }

    public void changeFullName(String newFullName) {
        this.fullName = newFullName;
    }

    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    /**
     * Gives an attendee the Organizer role.
     *
     * @throws IllegalStateException if the user is not an attendee
     */
    public void promoteToOrganizer() {
        if (role != Role.ATTENDEE) {
            throw new IllegalStateException("Only attendees can be promoted to Event Organizer.");
        }
        this.role = Role.ORGANIZER;
    }

    // ==================== PERSISTENCE ====================

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", userId);
        map.put("username", username);
        map.put("password", password);
        map.put("fullName", fullName);
        map.put("email", email);
        map.put("role", role.name());
        map.put("active", active);
        map.put("createdAt", SavedFields.formatDate(createdAt));
        return map;
    }

    /**
     * Restores a saved account.
     *
     * @throws RuntimeException if the role, active flag or creation time cannot be read
     */
    public static User fromMap(Map<String, Object> map) {
        if (map == null) return null;
        LocalDateTime createdAt = SavedFields.parseDate((String) map.get("createdAt"));
        return new User(
                (String) map.get("userId"),
                (String) map.get("username"),
                (String) map.get("password"),
                (String) map.get("fullName"),
                (String) map.get("email"),
                Role.fromString((String) map.get("role")),
                SavedFields.requireBoolean(map, "active"),
                createdAt != null ? createdAt : LocalDateTime.now());
    }
}
