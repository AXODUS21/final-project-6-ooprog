package plano.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an event category (e.g. Conference, Seminar, Workshop, Meetup, Community).
 */
public class Category {
    private String categoryId;
    private String name;
    private String description;

    public Category() {}

    public Category(String categoryId, String name, String description) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
        Category c = new Category();
        c.setCategoryId((String) map.get("categoryId"));
        c.setName((String) map.get("name"));
        c.setDescription((String) map.get("description"));
        return c;
    }

    @Override
    public String toString() {
        return name;
    }
}
