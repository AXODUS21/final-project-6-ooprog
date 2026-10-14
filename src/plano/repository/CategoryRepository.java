package plano.repository;

import plano.model.Category;

import java.util.*;

/**
 * In-memory repository for event categories.
 */
public class CategoryRepository {
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public void add(Category cat) {
        if (cat != null && cat.getCategoryId() != null) {
            categories.put(cat.getCategoryId(), cat);
        }
    }

    public Optional<Category> findById(String id) {
        return Optional.ofNullable(categories.get(id));
    }

    public Optional<Category> findByName(String name) {
        if (name == null) return Optional.empty();
        return categories.values().stream()
                .filter(c -> name.equalsIgnoreCase(c.getName()))
                .findFirst();
    }

    public List<Category> findAll() {
        return new ArrayList<>(categories.values());
    }

    public boolean existsByName(String name) {
        return findByName(name).isPresent();
    }

    public boolean delete(String id) {
        return categories.remove(id) != null;
    }

    public void clear() {
        categories.clear();
    }

    public int size() {
        return categories.size();
    }
}
