package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.List;

/**
 * Strategy interface for sorting events at runtime.
 */
public interface EventSortStrategy {
    String getName();
    String getDescription();
    List<Event> sort(List<Event> events, RegistrationRepository regRepo);
}
