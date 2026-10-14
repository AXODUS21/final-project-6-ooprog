package plano.repository;

import plano.model.Feedback;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory repository for event feedback and reviews.
 */
public class FeedbackRepository {
    private final Map<String, Feedback> feedbacks = new LinkedHashMap<>();

    public void add(Feedback fb) {
        if (fb != null && fb.getFeedbackId() != null) {
            feedbacks.put(fb.getFeedbackId(), fb);
        }
    }

    public Optional<Feedback> findById(String id) {
        return Optional.ofNullable(feedbacks.get(id));
    }

    public List<Feedback> findAll() {
        return new ArrayList<>(feedbacks.values());
    }

    public List<Feedback> findByEvent(String eventId) {
        if (eventId == null) return Collections.emptyList();
        return feedbacks.values().stream()
                .filter(fb -> eventId.equals(fb.getEventId()))
                .sorted(Comparator.comparing(Feedback::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public List<Feedback> findByUser(String userId) {
        if (userId == null) return Collections.emptyList();
        return feedbacks.values().stream()
                .filter(fb -> userId.equals(fb.getUserId()))
                .collect(Collectors.toList());
    }

    public Optional<Feedback> findByEventAndUser(String eventId, String userId) {
        if (eventId == null || userId == null) return Optional.empty();
        return feedbacks.values().stream()
                .filter(fb -> eventId.equals(fb.getEventId()) && userId.equals(fb.getUserId()))
                .findFirst();
    }

    public double getAverageRatingForEvent(String eventId) {
        List<Feedback> list = findByEvent(eventId);
        if (list.isEmpty()) return 0.0;
        double sum = list.stream().mapToInt(Feedback::getRating).sum();
        return sum / list.size();
    }

    public boolean delete(String id) {
        return feedbacks.remove(id) != null;
    }

    public void clear() {
        feedbacks.clear();
    }

    public int size() {
        return feedbacks.size();
    }
}
