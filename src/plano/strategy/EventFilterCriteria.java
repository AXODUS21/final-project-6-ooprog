package plano.strategy;

import plano.model.EventStatus;

import java.time.LocalDateTime;

/**
 * Encapsulates search and filter criteria for events.
 */
public class EventFilterCriteria {
    private String textQuery;
    private String category;
    private String venue;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private EventStatus status;
    private boolean availableOnly; // only events with open capacity spots

    public EventFilterCriteria() {}

    public String getTextQuery() {
        return textQuery;
    }

    public void setTextQuery(String textQuery) {
        this.textQuery = textQuery;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public LocalDateTime getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDateTime fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDateTime getToDate() {
        return toDate;
    }

    public void setToDate(LocalDateTime toDate) {
        this.toDate = toDate;
    }

    public boolean setDateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            return false;
        }
        this.fromDate = from;
        this.toDate = to;
        return true;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public boolean isAvailableOnly() {
        return availableOnly;
    }

    public void setAvailableOnly(boolean availableOnly) {
        this.availableOnly = availableOnly;
    }

    public boolean hasActiveFilters() {
        return (textQuery != null && !textQuery.isBlank()) ||
                (category != null && !category.isBlank()) ||
                (venue != null && !venue.isBlank()) ||
                fromDate != null ||
                toDate != null ||
                status != null ||
                availableOnly;
    }

    public void reset() {
        textQuery = null;
        category = null;
        venue = null;
        fromDate = null;
        toDate = null;
        status = null;
        availableOnly = false;
    }
}
