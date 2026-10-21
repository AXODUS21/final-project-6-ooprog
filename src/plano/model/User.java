package plano.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a registered user account in the PlanO system.
 */
public class User {
    private String userId;
    private String username;
    private String password;
    private String fullName;
    private String email;
    private Role role;
    private boolean active;
    private LocalDateTime createdAt;

    public User() {
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public User(String userId, String username, String password, String fullName, String email, Role role) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.role = role != null ? role : Role.ATTENDEE;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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

    // Persistence mapping
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", userId);
        map.put("username", username);
        map.put("password", password);
        map.put("fullName", fullName);
        map.put("email", email);
        map.put("role", role != null ? role.name() : Role.ATTENDEE.name());
        map.put("active", active);
        map.put("createdAt", createdAt != null ? createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        return map;
    }

    public static User fromMap(Map<String, Object> map) {
        if (map == null) return null;
        User user = new User();
        user.setUserId((String) map.get("userId"));
        user.setUsername((String) map.get("username"));
        user.setPassword((String) map.get("password"));
        user.setFullName((String) map.get("fullName"));
        user.setEmail((String) map.get("email"));
        user.setRole(Role.fromString((String) map.get("role")));
        Object act = map.get("active");
        user.setActive(act instanceof Boolean ? (Boolean) act : Boolean.parseBoolean(String.valueOf(act)));
        String ca = (String) map.get("createdAt");
        if (ca != null && !ca.trim().isEmpty()) {
            try {
                user.setCreatedAt(LocalDateTime.parse(ca, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (Exception e) {
                user.setCreatedAt(LocalDateTime.now());
            }
        }
        return user;
    }
}
