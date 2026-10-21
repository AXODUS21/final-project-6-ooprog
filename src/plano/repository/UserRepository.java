package plano.repository;

import plano.model.User;

import java.util.*;

/**
 * In-memory repository for user accounts.
 */
public class UserRepository {
    private final Map<String, User> users = new LinkedHashMap<>();

    public void add(User user) {
        if (user != null && user.getUserId() != null) {
            users.put(user.getUserId(), user);
        }
    }

    public Optional<User> findById(String id) {
        return Optional.ofNullable(users.get(id));
    }

    public Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        return users.values().stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .findFirst();
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return users.values().stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .findFirst();
    }

    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    public boolean delete(String id) {
        return users.remove(id) != null;
    }

    public void clear() {
        users.clear();
    }

    public int size() {
        return users.size();
    }
}
