package plano.service;

import plano.model.Role;
import plano.model.User;
import plano.repository.DataStore;
import plano.util.InputValidator;

import java.util.Optional;

/**
 * Authentication service handling user login, registration, and active session.
 */
public class AuthService {
    private final DataStore dataStore;
    private User currentUser;

    public AuthService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void logout() {
        this.currentUser = null;
    }

    public synchronized LoginResult login(String username, String password) {
        if (!InputValidator.isNotEmpty(username) || !InputValidator.isNotEmpty(password)) {
            return new LoginResult(false, "Username and password cannot be empty.", null);
        }

        Optional<User> userOpt = dataStore.getUserRepository().findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            return new LoginResult(false, "Invalid username or password.", null);
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            return new LoginResult(false, "Your account has been deactivated by an Administrator.", null);
        }

        if (!user.getPassword().equals(password)) {
            return new LoginResult(false, "Invalid username or password.", null);
        }

        this.currentUser = user;
        return new LoginResult(true, "Login successful. Welcome back, " + user.getFullName() + "!", user);
    }

    public synchronized RegisterResult register(String username, String password, String fullName, String email, Role role) {
        if (!InputValidator.isNotEmpty(username)) {
            return new RegisterResult(false, "Username cannot be empty.", null);
        }
        if (username.trim().length() < 3) {
            return new RegisterResult(false, "Username must be at least 3 characters long.", null);
        }
        if (dataStore.getUserRepository().existsByUsername(username.trim())) {
            return new RegisterResult(false, "Username '" + username + "' is already taken. Please choose another.", null);
        }

        if (!InputValidator.isNotEmpty(password) || password.length() < 4) {
            return new RegisterResult(false, "Password must be at least 4 characters long.", null);
        }

        if (!InputValidator.isNotEmpty(fullName)) {
            return new RegisterResult(false, "Full name cannot be empty.", null);
        }

        if (!InputValidator.isValidEmail(email)) {
            return new RegisterResult(false, "Invalid email address format.", null);
        }

        if (dataStore.getUserRepository().existsByEmail(email.trim())) {
            return new RegisterResult(false, "Email '" + email + "' is already registered to an account.", null);
        }

        String userId = dataStore.nextUserId();
        User newUser = new User(userId, username.trim(), password, fullName.trim(), email.trim(), role != null ? role : Role.ATTENDEE);
        dataStore.getUserRepository().add(newUser);
        dataStore.saveUsers();

        return new RegisterResult(true, "Registration successful! You can now log in.", newUser);
    }

    public static class LoginResult {
        public final boolean success;
        public final String message;
        public final User user;

        public LoginResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }
    }

    public static class RegisterResult {
        public final boolean success;
        public final String message;
        public final User user;

        public RegisterResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }
    }
}
