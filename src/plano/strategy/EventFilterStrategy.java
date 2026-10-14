package plano.strategy;

import plano.model.Event;
import plano.repository.RegistrationRepository;

import java.util.List;

/**
 * Strategy interface for filtering events based on various conditions.
 */
public interface EventFilterStrategy {
    String getName();
    List<Event> filter(List<Event> events, EventFilterCriteria criteria, RegistrationRepository regRepo);
}
