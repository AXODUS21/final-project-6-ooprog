package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts events alphabetically by title (A to Z).
 */
public class SortByTitleStrategy implements EventSortStrategy {

    @Override
    public String getName() {
        return "Alphabetical (A - Z)";
    }

    @Override
    public String getDescription() {
        return "Events in A to Z order by title.";
    }

    @Override
    public List<Event> sort(List<Event> events, RegistrationRepository regRepo) {
        List<Event> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(
                e -> e.getTitle() != null ? e.getTitle().toLowerCase() : "",
                Comparator.naturalOrder()
        ));
        return sorted;
    }
}
