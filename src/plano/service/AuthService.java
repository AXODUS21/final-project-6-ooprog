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
        if (dataStore.getUserRepository().size() == 0) {
            return new LoginResult(false, "No registered accounts exist in the database. Please register an account first.", null);
        }

        if (!InputValidator.isNotEmpty(username) || !InputValidator.isNotEmpty(password)) {
            return new LoginResult(false, "Username and password cannot be empty.", null);
        }

        Optional<User> userOpt = dataStore.getUserRepository().findByUsername(username.trim());
        if (!userOpt.isPresent()) {
            return new LoginResult(false, "Invalid username or password.", null);
        }

        // Check the password first, so a wrong guess never reveals whether the account is deactivated.
        User user = userOpt.get();
        if (!user.passwordMatches(password)) {
            return new LoginResult(false, "Invalid username or password.", null);
        }

        if (!user.isActive()) {
            return new LoginResult(false, "Your account has been deactivated by an Administrator.", null);
        }

        this.currentUser = user;
        return new LoginResult(true, "Login successful. Welcome back, " + user.getFullName() + "!", user);
    }

    /**
     * Public sign-up. Always creates an Attendee account; Organizers are promoted by an Administrator.
     */
    public synchronized RegisterResult register(String username, String password, String fullName, String email) {
        if (!InputValidator.isValidUsername(username)) {
            return new RegisterResult(false, "Username must be 3-30 characters long and contain only letters, numbers, underscores, dots, or hyphens.", null);
        }
        if (dataStore.getUserRepository().existsByUsername(username.trim())) {
            return new RegisterResult(false, "Username '" + username + "' is already taken. Please choose another.", null);
        }

        if (!InputValidator.isValidPassword(password)) {
            return new RegisterResult(false, "Password must be between 6 and 64 characters long, contain at least one letter and one number, and cannot consist solely of whitespace.", null);
        }

        if (!InputValidator.isValidFullName(fullName)) {
            return new RegisterResult(false, "Please enter a valid full name (2-50 characters, letters and spaces only).", null);
        }

        if (!InputValidator.isValidEmail(email)) {
            return new RegisterResult(false, "Invalid email address format.", null);
        }

        if (dataStore.getUserRepository().existsByEmail(email.trim())) {
            return new RegisterResult(false, "Email '" + email + "' is already registered to an account.", null);
        }

        String userId = dataStore.nextUserId();
        User newUser = new User(userId, username.trim(), password, fullName.trim(), email.trim(), Role.ATTENDEE);
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
