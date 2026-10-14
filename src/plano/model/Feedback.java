package plano.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an attendee's review and rating for a completed event.
 */
public class Feedback {
    private String feedbackId;
    private String eventId;
    private String userId;
    private int rating; // 1 to 5
    private String comment;
    private LocalDateTime submittedAt;

    public Feedback() {
        this.submittedAt = LocalDateTime.now();
    }

    public Feedback(String feedbackId, String eventId, String userId, int rating, String comment) {
        this.feedbackId = feedbackId;
        this.eventId = eventId;
        this.userId = userId;
        this.rating = Math.max(1, Math.min(5, rating));
        this.comment = comment;
        this.submittedAt = LocalDateTime.now();
    }

    public String getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(String feedbackId) {
        this.feedbackId = feedbackId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = Math.max(1, Math.min(5, rating));
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getStarsDisplay() {
        return "★".repeat(rating) + "☆".repeat(5 - rating);
    }

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
        Feedback fb = new Feedback();
        fb.setFeedbackId((String) map.get("feedbackId"));
        fb.setEventId((String) map.get("eventId"));
        fb.setUserId((String) map.get("userId"));

        Object rObj = map.get("rating");
        if (rObj instanceof Number) {
            fb.setRating(((Number) rObj).intValue());
        } else if (rObj != null) {
            fb.setRating(Integer.parseInt(rObj.toString()));
        }

        fb.setComment((String) map.get("comment"));

        String sa = (String) map.get("submittedAt");
        if (sa != null && !sa.isBlank()) {
            fb.setSubmittedAt(LocalDateTime.parse(sa, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        return fb;
    }
}
