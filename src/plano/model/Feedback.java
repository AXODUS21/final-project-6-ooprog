package plano.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an attendee's review and rating for a completed event.
 * The IDs never change; the rating and comment change only through {@link #revise}.
 */
public class Feedback {
    private final String feedbackId;
    private final String eventId;
    private final String userId;
    private int rating; // 1 to 5
    private String comment;
    private LocalDateTime submittedAt;

    /** Creates a new review submitted now. Ratings outside 1-5 are clamped into range. */
    public Feedback(String feedbackId, String eventId, String userId, int rating, String comment) {
        this(feedbackId, eventId, userId, rating, comment, LocalDateTime.now());
    }

    /** Restores a review exactly as it was saved. */
    private Feedback(String feedbackId, String eventId, String userId, int rating, String comment,
                     LocalDateTime submittedAt) {
        this.feedbackId = feedbackId;
        this.eventId = eventId;
        this.userId = userId;
        this.rating = clampRating(rating);
        this.comment = comment;
        this.submittedAt = submittedAt;
    }

    private static int clampRating(int rating) {
        return Math.max(1, Math.min(5, rating));
    }

    public String getFeedbackId() {
        return feedbackId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getUserId() {
        return userId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    /** Plain-ASCII rating bar, e.g. "****-" for 4 out of 5. */
    public String getStarsDisplay() {
        StringBuilder sb = new StringBuilder(5);
        for (int i = 1; i <= 5; i++) {
            sb.append(i <= rating ? '*' : '-');
        }
        return sb.toString();
    }

    /** Replaces the rating and comment when an attendee updates their review. */
    public void revise(int newRating, String newComment, LocalDateTime revisedAt) {
        this.rating = clampRating(newRating);
        this.comment = newComment;
        this.submittedAt = revisedAt;
    }

    // ==================== PERSISTENCE ====================

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("feedbackId", feedbackId);
        map.put("eventId", eventId);
        map.put("userId", userId);
        map.put("rating", rating);
        map.put("comment", comment);
        map.put("submittedAt", submittedAt != null ? submittedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        return map;
    }

    public static Feedback fromMap(Map<String, Object> map) {
        if (map == null) return null;

        int rating = 0;
        Object rObj = map.get("rating");
        if (rObj instanceof Number) {
            rating = ((Number) rObj).intValue();
        } else if (rObj != null) {
            rating = Integer.parseInt(rObj.toString());
        }

        LocalDateTime submittedAt = LocalDateTime.now();
        String sa = (String) map.get("submittedAt");
        if (sa != null && !sa.trim().isEmpty()) {
            submittedAt = LocalDateTime.parse(sa, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        return new Feedback(
                (String) map.get("feedbackId"),
                (String) map.get("eventId"),
                (String) map.get("userId"),
                rating,
                (String) map.get("comment"),
                submittedAt);
    }
}
