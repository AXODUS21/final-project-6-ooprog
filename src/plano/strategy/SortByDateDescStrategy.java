package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts events reverse-chronologically by start date and time (latest first).
 */
public class SortByDateDescStrategy implements EventSortStrategy {

    @Override
    public String getName() {
        return "Date (Latest First)";
    }

    @Override
    public String getDescription() {
        return "Events in order of start time, latest first.";
    }

    @Override
    public List<Event> sort(List<Event> events, RegistrationRepository regRepo) {
        List<Event> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(
                Event::getStartDateTime,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        return sorted;
    }
}
