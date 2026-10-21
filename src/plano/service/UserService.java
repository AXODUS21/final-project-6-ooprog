package plano.service;

import plano.model.Event;
import plano.model.Role;
import plano.model.User;
import plano.repository.DataStore;
import plano.util.InputValidator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing user accounts, roles, activations, and profile updates.
 */
public class UserService {
    private final DataStore dataStore;
    private final RegistrationService registrationService;

    public UserService(DataStore dataStore) {
        this.dataStore = dataStore;
        this.registrationService = new RegistrationService(dataStore);
    }

    public List<User> getAllUsers() {
        return dataStore.getUserRepository().findAll();
    }

    /**
     * Deactivates an account. Organizers with events that have not ended yet cannot be deactivated.
     * The user's registrations for events that have not started are cancelled and their seats
     * are offered to the waitlist.
     */
    public synchronized OperationResult deactivateUser(User adminUser, String targetUserId) {
        if (adminUser == null || !adminUser.isAdmin()) {
            return new OperationResult(false, "Access denied. Only Administrators can deactivate users.");
        }
        if (adminUser.getUserId().equals(targetUserId)) {
            return new OperationResult(false, "You cannot deactivate your own administrative account.");
        }

        Optional<User> targetOpt = dataStore.getUserRepository().findById(targetUserId);
        if (!targetOpt.isPresent()) {
            return new OperationResult(false, "User not found with ID: " + targetUserId);
        }

        User target = targetOpt.get();
        if (!target.isActive()) {
            return new OperationResult(false, "User '" + target.getUsername() + "' is already deactivated.");
        }

        if (target.isOrganizer()) {
            LocalDateTime now = LocalDateTime.now();
            long activeCount = 0;
            for (Event e : dataStore.getEventRepository().findByOrganizer(targetUserId)) {
                if (!e.isCancelled() && !e.isCompleted() && !e.hasEnded(now)) {
                    activeCount++;
                }
            }
            if (activeCount > 0) {
                return new OperationResult(false, "Cannot deactivate Organizer '" + target.getUsername() +
                        "': They currently have " + activeCount + " active upcoming event(s). Please cancel or reassign their events first.");
            }
        }

        target.deactivate();
        dataStore.saveUsers();

        RegistrationService.ReleaseResult released = registrationService.releaseUpcomingRegistrations(targetUserId);
        String msg = "User '" + target.getUsername() + "' has been deactivated.";
        if (released.released > 0) {
            msg += " Their " + released.released + " registration(s) for upcoming events were cancelled." +
                    registrationService.describePromotions(released.promoted);
        }
        return new OperationResult(true, msg);
    }

    public synchronized OperationResult reactivateUser(User adminUser, String targetUserId) {
        if (adminUser == null || !adminUser.isAdmin()) {
            return new OperationResult(false, "Access denied. Only Administrators can reactivate users.");
        }

        Optional<User> targetOpt = dataStore.getUserRepository().findById(targetUserId);
        if (!targetOpt.isPresent()) {
            return new OperationResult(false, "User not found with ID: " + targetUserId);
        }

        User target = targetOpt.get();
        if (target.isActive()) {
            return new OperationResult(false, "User '" + target.getUsername() + "' is already active.");
        }
        target.reactivate();
        dataStore.saveUsers();
        return new OperationResult(true, "User '" + target.getUsername() + "' has been reactivated.");
    }

    public synchronized OperationResult promoteToOrganizer(User adminUser, String targetUserId) {
        if (adminUser == null || !adminUser.isAdmin()) {
            return new OperationResult(false, "Access denied. Only Administrators can promote users.");
        }

        Optional<User> targetOpt = dataStore.getUserRepository().findById(targetUserId);
        if (!targetOpt.isPresent()) {
            return new OperationResult(false, "User not found with ID: " + targetUserId);
        }

        User target = targetOpt.get();
        if (target.getRole() == Role.ORGANIZER) {
            return new OperationResult(false, "User '" + target.getUsername() + "' is already an Organizer.");
        }
        if (target.isAdmin()) {
            return new OperationResult(false, "User is already an Administrator.");
        }

        target.promoteToOrganizer();
        dataStore.saveUsers();
        return new OperationResult(true, "User '" + target.getUsername() + "' has been promoted to Event Organizer.");
    }

    /**
     * Updates profile details. Blank arguments keep the current value. Every field is validated
     * first, so a rejected update changes nothing.
     */
    public synchronized OperationResult updateProfile(String userId, String fullName, String email, String newPassword) {
        Optional<User> userOpt = dataStore.getUserRepository().findById(userId);
        if (!userOpt.isPresent()) {
            return new OperationResult(false, "User not found.");
        }

        User user = userOpt.get();

        String newName = null;
        if (InputValidator.isNotEmpty(fullName)) {
            if (!InputValidator.isValidFullName(fullName)) {
                return new OperationResult(false, "Invalid full name format (2-50 characters, letters and spaces only).");
            }
            newName = fullName.trim();
        }

        String newEmail = null;
        if (InputValidator.isNotEmpty(email)) {
            if (!InputValidator.isValidEmail(email)) {
                return new OperationResult(false, "Invalid email address format.");
            }
            Optional<User> existingEmailUser = dataStore.getUserRepository().findByEmail(email.trim());
            if (existingEmailUser.isPresent() && !existingEmailUser.get().getUserId().equals(userId)) {
                return new OperationResult(false, "Email address is already in use by another account.");
            }
            newEmail = email.trim();
        }

        String password = null;
        if (InputValidator.isNotEmpty(newPassword)) {
            if (!InputValidator.isValidPassword(newPassword)) {
                return new OperationResult(false, "Password must be between 6 and 64 characters long, contain at least one letter and one number, and cannot consist solely of whitespace.");
            }
            password = newPassword;
        }

        if (newName != null) {
            user.changeFullName(newName);
        }
        if (newEmail != null) {
            user.changeEmail(newEmail);
        }
        if (password != null) {
            user.changePassword(password);
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
