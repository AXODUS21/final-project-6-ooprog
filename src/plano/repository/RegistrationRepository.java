package plano.repository;

import plano.model.Registration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory repository for event registrations with indexed querying.
 */
public class RegistrationRepository {
    private final Map<String, Registration> registrations = new LinkedHashMap<>();

    public void add(Registration reg) {
        if (reg != null && reg.getRegistrationId() != null) {
            registrations.put(reg.getRegistrationId(), reg);
        }
    }

    public Optional<Registration> findById(String id) {
        return Optional.ofNullable(registrations.get(id));
    }

    public List<Registration> findAll() {
        return new ArrayList<>(registrations.values());
    }

    public List<Registration> findByEvent(String eventId) {
        if (eventId == null) return Collections.emptyList();
        return registrations.values().stream()
                .filter(r -> eventId.equals(r.getEventId()))
                .collect(Collectors.toList());
    }

    public List<Registration> findByUser(String userId) {
        if (userId == null) return Collections.emptyList();
        return registrations.values().stream()
                .filter(r -> userId.equals(r.getUserId()))
                .sorted(Comparator.comparing(Registration::getRegisteredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /** Returns the user's registration for the event that is not cancelled, if there is one. */
    public Optional<Registration> findByEventAndUser(String eventId, String userId) {
        if (eventId == null || userId == null) return Optional.empty();
        return registrations.values().stream()
                .filter(r -> eventId.equals(r.getEventId()) && userId.equals(r.getUserId()))
                .filter(r -> !r.isCancelled())
                .findFirst();
    }

    /** The event's waitlist in the order people joined it (first in, first out). */
    public List<Registration> findWaitlistedByEvent(String eventId) {
        return findByEvent(eventId).stream()
                .filter(Registration::isWaitlisted)
                .sorted(Comparator.comparing(Registration::getRegisteredAt))
                .collect(Collectors.toList());
    }

    /** Seats taken at the event: confirmed and checked-in registrations (see {@link Registration#holdsSeat()}). */
    public int countSeatsTakenByEvent(String eventId) {
        return (int) findByEvent(eventId).stream()
                .filter(Registration::holdsSeat)
                .count();
    }

    public int countWaitlistedByEvent(String eventId) {
        return (int) findByEvent(eventId).stream()
                .filter(Registration::isWaitlisted)
                .count();
    }

    public int countCheckedInByEvent(String eventId) {
        return (int) findByEvent(eventId).stream()
                .filter(Registration::isCheckedIn)
                .count();
    }

    public boolean delete(String id) {
        return registrations.remove(id) != null;
    }

    public void clear() {
        registrations.clear();
    }

    public int size() {
        return registrations.size();
    }
}
