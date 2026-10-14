package plano.repository;

import plano.model.Event;
import plano.model.EventStatus;
import plano.util.InputValidator;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory repository for events.
 */
public class EventRepository {
    private final Map<String, Event> events = new LinkedHashMap<>();

    public void add(Event event) {
        if (event != null && event.getEventId() != null) {
            events.put(event.getEventId(), event);
        }
    }

    public Optional<Event> findById(String id) {
        return Optional.ofNullable(events.get(id));
    }

    public List<Event> findAll() {
        return new ArrayList<>(events.values());
    }

    public List<Event> findByOrganizer(String organizerId) {
        if (organizerId == null) return Collections.emptyList();
        return events.values().stream()
                .filter(e -> organizerId.equals(e.getOrganizerId()))
                .collect(Collectors.toList());
    }

    public List<Event> findByCategory(String category) {
        if (category == null) return Collections.emptyList();
        return events.values().stream()
                .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                .collect(Collectors.toList());
    }

    public List<Event> findByStatus(EventStatus status) {
        return events.values().stream()
                .filter(e -> e.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * Finds any active (non-cancelled) event scheduled at the same venue
     * whose time interval overlaps with [start, end].
     * Excludes excludeEventId if modifying an existing event.
     */
    public List<Event> findConflictingEvents(String venue, LocalDateTime start, LocalDateTime end, String excludeEventId) {
        if (venue == null || start == null || end == null) {
            return Collections.emptyList();
        }

        return events.values().stream()
                .filter(e -> !e.isCancelled())
                .filter(e -> excludeEventId == null || !e.getEventId().equals(excludeEventId))
                .filter(e -> InputValidator.isVenueMatch(e.getVenue(), venue))
                .filter(e -> InputValidator.doIntervalsOverlap(start, end, e.getStartDateTime(), e.getEndDateTime()))
                .collect(Collectors.toList());
    }

    public boolean delete(String id) {
        return events.remove(id) != null;
    }

    public void clear() {
        events.clear();
    }

    public int size() {
        return events.size();
    }
}
