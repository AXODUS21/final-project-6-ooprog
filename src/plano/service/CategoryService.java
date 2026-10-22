package plano.service;

import plano.model.Category;
import plano.model.User;
import plano.repository.DataStore;
import plano.util.InputValidator;

import java.util.List;
import java.util.Optional;

/**
 * Service for the event categories that Administrators manage.
 * Events refer to their category by name, so a category is added or removed, never renamed,
 * and a category that any event uses cannot be removed.
 */
public class CategoryService {
    private final DataStore dataStore;

    public CategoryService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public List<Category> getAllCategories() {
        return dataStore.getCategoryRepository().findAll();
    }

    /**
     * Checks a name for a new category: 2 to 50 characters and not already used (ignoring letter case).
     * The menu calls this before asking for a description; {@link #addCategory} checks it again.
     */
    public OperationResult checkNewCategoryName(String name) {
        if (!InputValidator.isValidCategoryName(name)) {
            return new OperationResult(false, "Category name must be between 2 and 50 characters long.");
        }
        if (dataStore.getCategoryRepository().existsByName(name.trim())) {
            return new OperationResult(false, "Category '" + name.trim() + "' already exists.");
        }
        return new OperationResult(true, "Category name '" + name.trim() + "' is available.");
    }

    public OperationResult addCategory(User adminUser, String name, String description) {
        if (adminUser == null || !adminUser.isAdmin()) {
            return new OperationResult(false, "Access denied. Only Administrators can manage categories.");
        }
        OperationResult nameCheck = checkNewCategoryName(name);
        if (!nameCheck.success) {
            return nameCheck;
        }

        String categoryId = dataStore.nextCategoryId();
        dataStore.getCategoryRepository().add(
                new Category(categoryId, name.trim(), description != null ? description.trim() : ""));
        dataStore.saveCategories();
        return new OperationResult(true, "Category '" + name.trim() + "' added successfully.");
    }

    public OperationResult removeCategory(User adminUser, String categoryId) {
        if (adminUser == null || !adminUser.isAdmin()) {
            return new OperationResult(false, "Access denied. Only Administrators can manage categories.");
        }
        Optional<Category> categoryOpt = dataStore.getCategoryRepository().findById(categoryId);
        if (!categoryOpt.isPresent()) {
            return new OperationResult(false, "Category not found with ID: " + categoryId);
        }

        Category category = categoryOpt.get();
        long eventsUsingIt = dataStore.getEventRepository().findAll().stream()
                .filter(e -> category.getName().equalsIgnoreCase(e.getCategory()))
                .count();
        if (eventsUsingIt > 0) {
            return new OperationResult(false, "Cannot delete category '" + category.getName() + "': " +
                    eventsUsingIt + " event(s) are currently assigned to it.");
        }

        dataStore.getCategoryRepository().delete(categoryId);
        dataStore.saveCategories();
        return new OperationResult(true, "Category '" + category.getName() + "' removed successfully.");
    }
}
