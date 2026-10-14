package plano.repository;

import plano.model.Registration;
import plano.model.RegistrationStatus;

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

    public Optional<Registration> findByEventAndUser(String eventId, String userId) {
        if (eventId == null || userId == null) return Optional.empty();
        return registrations.values().stream()
                .filter(r -> eventId.equals(r.getEventId()) && userId.equals(r.getUserId()))
                .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED)
                .findFirst();
    }

    public Optional<Registration> findAnyByEventAndUser(String eventId, String userId) {
        if (eventId == null || userId == null) return Optional.empty();
        return registrations.values().stream()
                .filter(r -> eventId.equals(r.getEventId()) && userId.equals(r.getUserId()))
                .findFirst();
    }

    public List<Registration> findConfirmedByEvent(String eventId) {
        return findByEvent(eventId).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED || r.getStatus() == RegistrationStatus.CHECKED_IN)
                .collect(Collectors.toList());
    }

    public List<Registration> findWaitlistedByEvent(String eventId) {
        return findByEvent(eventId).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED)
                .sorted(Comparator.comparing(Registration::getRegisteredAt))
                .collect(Collectors.toList());
    }

    public List<Registration> findCheckedInByEvent(String eventId) {
        return findByEvent(eventId).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CHECKED_IN)
                .collect(Collectors.toList());
    }

    public int countConfirmedByEvent(String eventId) {
        return (int) findByEvent(eventId).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED || r.getStatus() == RegistrationStatus.CHECKED_IN)
                .count();
    }

    public int countWaitlistedByEvent(String eventId) {
        return (int) findByEvent(eventId).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED)
                .count();
    }

    public int countCheckedInByEvent(String eventId) {
        return (int) findByEvent(eventId).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CHECKED_IN)
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
