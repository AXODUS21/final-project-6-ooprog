package plano.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an event category (e.g. Conference, Seminar, Workshop, Meetup, Community).
 * Categories are immutable: events refer to them by name, so a category is added or removed, never edited.
 */
public class Category {
    private final String categoryId;
    private final String name;
    private final String description;

    public Category(String categoryId, String name, String description) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("categoryId", categoryId);
        map.put("name", name);
        map.put("description", description);
        return map;
    }

    public static Category fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new Category(
                (String) map.get("categoryId"),
                (String) map.get("name"),
                (String) map.get("description"));
    }

    @Override
    public String toString() {
        return name;
    }
}
