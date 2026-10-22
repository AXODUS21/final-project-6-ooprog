package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.List;

/**
 * Strategy interface for filtering events based on various conditions.
 */
public interface EventFilterStrategy {
    /** Returns the events that match the criteria, in their original order; null criteria keeps all. */
    List<Event> filter(List<Event> events, EventFilterCriteria criteria, RegistrationRepository regRepo);
}
