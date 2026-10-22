package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.List;

/**
 * Strategy interface for sorting events at runtime.
 */
public interface EventSortStrategy {
    /** Short name shown in the menus, e.g. "Date (Earliest First)". */
    String getName();

    /** One sentence shown next to the name in the sort menu. */
    String getDescription();

    /** Returns a sorted copy; the list passed in is not changed. */
    List<Event> sort(List<Event> events, RegistrationRepository regRepo);
}
