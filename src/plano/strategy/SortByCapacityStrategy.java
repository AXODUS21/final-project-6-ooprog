package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Sorts events by highest capacity first.
 */
public class SortByCapacityStrategy implements EventSortStrategy {

    @Override
    public String getName() {
        return "Capacity (Largest Venue / Hall)";
    }

    @Override
    public String getDescription() {
        return "Events ordered by highest venue capacity first.";
    }

    @Override
    public List<Event> sort(List<Event> events, RegistrationRepository regRepo) {
        List<Event> sorted = new ArrayList<>(events);
        sorted.sort((e1, e2) -> Integer.compare(e2.getCapacity(), e1.getCapacity()));
        return sorted;
    }
}
