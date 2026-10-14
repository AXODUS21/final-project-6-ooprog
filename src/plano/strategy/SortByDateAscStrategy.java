package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts events chronologically by start date and time (earliest first).
 */
public class SortByDateAscStrategy implements EventSortStrategy {

    @Override
    public String getName() {
        return "Date (Earliest First)";
    }

    @Override
    public String getDescription() {
        return "Upcoming events ordered chronologically from earliest to latest.";
    }

    @Override
    public List<Event> sort(List<Event> events, RegistrationRepository regRepo) {
        List<Event> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(
                Event::getStartDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        return sorted;
    }
}
