package plano.service;

import plano.model.Role;
import plano.model.User;
import plano.repository.DataStore;
import plano.util.InputValidator;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing user accounts, roles, activations, and profile updates.
 */
public class UserService {
    private final DataStore dataStore;

    public UserService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public List<User> getAllUsers() {
        return dataStore.getUserRepository().findAll();
    }

    public Optional<User> getUserById(String userId) {
        return dataStore.getUserRepository().findById(userId);
    }

    public synchronized OperationResult deactivateUser(User adminUser, String targetUserId) {
        if (adminUser == null || !adminUser.isAdmin()) {
            return new OperationResult(false, "Access denied. Only Administrators can deactivate users.");
        }
        if (adminUser.getUserId().equals(targetUserId)) {
            return new OperationResult(false, "You cannot deactivate your own administrative account.");
        }

        Optional<User> targetOpt = dataStore.getUserRepository().findById(targetUserId);
        if (targetOpt.isEmpty()) {
            return new OperationResult(false, "User not found with ID: " + targetUserId);
        }

        User target = targetOpt.get();
        target.setActive(false);
        dataStore.saveUsers();
        return new OperationResult(true, "User '" + target.getUsername() + "' has been deactivated.");
    }

    public synchronized OperationResult reactivateUser(User adminUser, String targetUserId) {
        if (adminUser == null || !adminUser.isAdmin()) {
            return new OperationResult(false, "Access denied. Only Administrators can reactivate users.");
        }

        Optional<User> targetOpt = dataStore.getUserRepository().findById(targetUserId);
        if (targetOpt.isEmpty()) {
            return new OperationResult(false, "User not found with ID: " + targetUserId);
        }

        User target = targetOpt.get();
        target.setActive(true);
        dataStore.saveUsers();
        return new OperationResult(true, "User '" + target.getUsername() + "' has been reactivated.");
    }

    public synchronized OperationResult promoteToOrganizer(User adminUser, String targetUserId) {
        if (adminUser == null || !adminUser.isAdmin()) {
            return new OperationResult(false, "Access denied. Only Administrators can promote users.");
        }

        Optional<User> targetOpt = dataStore.getUserRepository().findById(targetUserId);
        if (targetOpt.isEmpty()) {
            return new OperationResult(false, "User not found with ID: " + targetUserId);
        }

        User target = targetOpt.get();
        if (target.getRole() == Role.ORGANIZER) {
            return new OperationResult(false, "User '" + target.getUsername() + "' is already an Organizer.");
        }
        if (target.isAdmin()) {
            return new OperationResult(false, "User is already an Administrator.");
        }

        target.setRole(Role.ORGANIZER);
        dataStore.saveUsers();
        return new OperationResult(true, "User '" + target.getUsername() + "' has been promoted to Event Organizer.");
    }

    public synchronized OperationResult updateProfile(String userId, String fullName, String email, String newPassword) {
        Optional<User> userOpt = dataStore.getUserRepository().findById(userId);
        if (userOpt.isEmpty()) {
            return new OperationResult(false, "User not found.");
        }

        User user = userOpt.get();

        if (InputValidator.isNotEmpty(fullName)) {
            if (!InputValidator.isValidFullName(fullName)) {
                return new OperationResult(false, "Invalid full name format (2-50 characters, letters and spaces only).");
            }
            user.setFullName(fullName.trim());
        }

        if (InputValidator.isNotEmpty(email)) {
            if (!InputValidator.isValidEmail(email)) {
                return new OperationResult(false, "Invalid email address format.");
            }
            Optional<User> existingEmailUser = dataStore.getUserRepository().findByEmail(email.trim());
            if (existingEmailUser.isPresent() && !existingEmailUser.get().getUserId().equals(userId)) {
                return new OperationResult(false, "Email address is already in use by another account.");
            }
            user.setEmail(email.trim());
        }

        if (InputValidator.isNotEmpty(newPassword)) {
            if (!InputValidator.isValidPassword(newPassword)) {
                return new OperationResult(false, "Password must be between 4 and 64 characters long and cannot consist solely of whitespace.");
            }
            user.setPassword(newPassword);
        }

        dataStore.saveUsers();
        return new OperationResult(true, "Profile updated successfully.");
    }

    public static class OperationResult {
        public final boolean success;
        public final String message;

        public OperationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
