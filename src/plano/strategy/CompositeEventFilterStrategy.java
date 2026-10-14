package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite filtering strategy that applies text search, category,
 * location/venue, date range, and capacity availability constraints.
 */
public class CompositeEventFilterStrategy implements EventFilterStrategy {

    @Override
    public String getName() {
        return "Multi-Criteria Filter";
    }

    @Override
    public List<Event> filter(List<Event> events, EventFilterCriteria criteria, RegistrationRepository regRepo) {
        if (criteria == null || !criteria.hasActiveFilters()) {
            return new ArrayList<>(events);
        }

        List<Event> filtered = new ArrayList<>();
        for (Event ev : events) {
            // Text query match
            if (criteria.getTextQuery() != null && !criteria.getTextQuery().isBlank()) {
                String q = criteria.getTextQuery().toLowerCase();
                boolean matchesTitle = ev.getTitle() != null && ev.getTitle().toLowerCase().contains(q);
                boolean matchesDesc = ev.getDescription() != null && ev.getDescription().toLowerCase().contains(q);
                boolean matchesVenue = ev.getVenue() != null && ev.getVenue().toLowerCase().contains(q);
                if (!matchesTitle && !matchesDesc && !matchesVenue) {
                    continue;
                }
            }

            // Category match
            if (criteria.getCategory() != null && !criteria.getCategory().isBlank()) {
                if (ev.getCategory() == null || !ev.getCategory().equalsIgnoreCase(criteria.getCategory())) {
                    continue;
                }
            }

            // Venue match
            if (criteria.getVenue() != null && !criteria.getVenue().isBlank()) {
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

            // Available spots only
            if (criteria.isAvailableOnly() && regRepo != null) {
                int confirmed = regRepo.countConfirmedByEvent(ev.getEventId());
                if (confirmed >= ev.getCapacity()) {
                    continue;
                }
            }

            filtered.add(ev);
        }

        return filtered;
    }
}
