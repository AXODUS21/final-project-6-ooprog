package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts events by popularity (highest registration count first).
 */
public class SortByPopularityStrategy implements EventSortStrategy {

    @Override
    public String getName() {
        return "Popularity (Most Registrations)";
    }

    @Override
    public String getDescription() {
        return "Trending events ordered by highest attendee registration count first.";
    }

    @Override
    public List<Event> sort(List<Event> events, RegistrationRepository regRepo) {
        List<Event> sorted = new ArrayList<>(events);
        if (regRepo == null) {
            return sorted;
        }

        sorted.sort((e1, e2) -> {
            int count1 = regRepo.countConfirmedByEvent(e1.getEventId());
            int count2 = regRepo.countConfirmedByEvent(e2.getEventId());
            // descending count
            int cmp = Integer.compare(count2, count1);
            if (cmp != 0) return cmp;
            // secondary sort: capacity descending
            return Integer.compare(e2.getCapacity(), e1.getCapacity());
        });
        return sorted;
    }
}
