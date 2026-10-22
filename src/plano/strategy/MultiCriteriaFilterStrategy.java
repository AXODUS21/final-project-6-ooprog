package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtering strategy that checks every criterion that is set (text search, category,
 * venue, date range, status and open seats); an event is kept only if it passes all of them.
 */
public class MultiCriteriaFilterStrategy implements EventFilterStrategy {

    @Override
    public List<Event> filter(List<Event> events, EventFilterCriteria criteria, RegistrationRepository regRepo) {
        if (criteria == null || !criteria.hasActiveFilters()) {
            return new ArrayList<>(events);
        }

        List<Event> filtered = new ArrayList<>();
        for (Event ev : events) {
            // Text query match
            if (criteria.getTextQuery() != null && !criteria.getTextQuery().trim().isEmpty()) {
                String q = criteria.getTextQuery().toLowerCase();
                boolean matchesTitle = ev.getTitle() != null && ev.getTitle().toLowerCase().contains(q);
                boolean matchesDesc = ev.getDescription() != null && ev.getDescription().toLowerCase().contains(q);
                boolean matchesVenue = ev.getVenue() != null && ev.getVenue().toLowerCase().contains(q);
                if (!matchesTitle && !matchesDesc && !matchesVenue) {
                    continue;
                }
            }

            // Category match
            if (criteria.getCategory() != null && !criteria.getCategory().trim().isEmpty()) {
                if (ev.getCategory() == null || !ev.getCategory().equalsIgnoreCase(criteria.getCategory())) {
                    continue;
                }
            }

            // Venue match
            if (criteria.getVenue() != null && !criteria.getVenue().trim().isEmpty()) {
                if (ev.getVenue() == null || !ev.getVenue().toLowerCase().contains(criteria.getVenue().toLowerCase())) {
                    continue;
                }
            }

            // Date range match
            if (criteria.getFromDate() != null) {
                if (ev.getStartDateTime() == null || ev.getStartDateTime().isBefore(criteria.getFromDate())) {
                    continue;
                }
            }
            if (criteria.getToDate() != null) {
                if (ev.getStartDateTime() == null || ev.getStartDateTime().isAfter(criteria.getToDate())) {
                    continue;
                }
            }

            // Status match
            if (criteria.getStatus() != null) {
                if (ev.getStatus() != criteria.getStatus()) {
                    continue;
                }
            }

            // Open seats only
            if (criteria.isAvailableOnly() && regRepo != null) {
                if (regRepo.countSeatsTakenByEvent(ev.getEventId()) >= ev.getCapacity()) {
                    continue;
                }
            }

            filtered.add(ev);
        }

        return filtered;
    }
}
