package plano.service;

import plano.model.*;
import plano.repository.DataStore;
import plano.util.InputValidator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service managing attendee post-event feedback and ratings.
 */
public class FeedbackService {
    private final DataStore dataStore;

    public FeedbackService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public List<Feedback> getFeedbackForEvent(String eventId) {
        return dataStore.getFeedbackRepository().findByEvent(eventId);
    }

    public Optional<Feedback> getUserFeedbackForEvent(String eventId, String userId) {
        return dataStore.getFeedbackRepository().findByEventAndUser(eventId, userId);
    }

    public double getAverageRating(String eventId) {
        return dataStore.getFeedbackRepository().getAverageRatingForEvent(eventId);
    }

    /**
     * Submits or updates feedback for a completed event.
     */
    public synchronized FeedbackResult submitFeedback(User attendee, String eventId, int rating, String comment) {
        if (attendee == null) {
            return new FeedbackResult(false, "Invalid user session.", null);
        }

        Optional<Event> eventOpt = dataStore.getEventRepository().findById(eventId);
        if (eventOpt.isEmpty()) {
            return new FeedbackResult(false, "Event not found.", null);
        }

        Event event = eventOpt.get();
        if (event.getStatus() != EventStatus.COMPLETED) {
            return new FeedbackResult(false, "Feedback can only be submitted after the event has been marked as COMPLETED.", null);
        }

        // Validate user was not the organizer of this event
        if (event.getOrganizerId().equals(attendee.getUserId())) {
            return new FeedbackResult(false, "Organizers cannot submit reviews for their own events.", null);
        }

        // Validate user was registered or attended
        Optional<Registration> regOpt = dataStore.getRegistrationRepository().findByEventAndUser(eventId, attendee.getUserId());
        if (regOpt.isEmpty() || regOpt.get().isWaitlisted() || regOpt.get().isCancelled()) {
            return new FeedbackResult(false, "Only confirmed attendees can submit feedback for this event.", null);
        }

        if (rating < 1 || rating > 5) {
            return new FeedbackResult(false, "Rating must be between 1 and 5 stars.", null);
        }

        if (!InputValidator.isValidComment(comment)) {
            return new FeedbackResult(false, "Please provide a valid review comment (3 - 1,000 characters).", null);
        }

        // Check if updating existing feedback
        Optional<Feedback> existingOpt = dataStore.getFeedbackRepository().findByEventAndUser(eventId, attendee.getUserId());
        Feedback feedback;
        if (existingOpt.isPresent()) {
            feedback = existingOpt.get();
            feedback.setRating(rating);
            feedback.setComment(comment.trim());
            feedback.setSubmittedAt(LocalDateTime.now());
        } else {
            String fbId = dataStore.nextFeedbackId();
            feedback = new Feedback(fbId, eventId, attendee.getUserId(), rating, comment.trim());
            dataStore.getFeedbackRepository().add(feedback);
        }

        dataStore.saveFeedbacks();
        return new FeedbackResult(true, "Thank you! Your feedback has been submitted successfully.", feedback);
    }

    public static class FeedbackResult {
        public final boolean success;
        public final String message;
        public final Feedback feedback;

        public FeedbackResult(boolean success, String message, Feedback feedback) {
            this.success = success;
            this.message = message;
            this.feedback = feedback;
        }
    }
}
