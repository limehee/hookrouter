package io.github.limehee.hookrouter.spring.resilience.event;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.springframework.context.ApplicationEventPublisher;

public class CircuitBreakerEventListener implements RegistryEventConsumer<CircuitBreaker> {

    private final ApplicationEventPublisher eventPublisher;

    public CircuitBreakerEventListener(final ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
        CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
        String webhookKey = circuitBreaker.getName();

        circuitBreaker.getEventPublisher().onStateTransition(event -> handleStateTransition(webhookKey, event));
    }

    @Override
    public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemovedEvent) {
        String webhookKey = entryRemovedEvent.getRemovedEntry().getName();
    }

    @Override
    public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
        String webhookKey = entryReplacedEvent.getNewEntry().getName();

        entryReplacedEvent.getNewEntry().getEventPublisher()
            .onStateTransition(event -> handleStateTransition(webhookKey, event));
    }

    private void handleStateTransition(String webhookKey, CircuitBreakerOnStateTransitionEvent event) {
        State fromState = event.getStateTransition().getFromState();
        State toState = event.getStateTransition().getToState();
        publishSpringEvent(webhookKey, fromState, toState);
    }

    private void publishSpringEvent(String webhookKey, State fromState, State toState) {
        CircuitBreakerStateChangedEvent springEvent = new CircuitBreakerStateChangedEvent(this, webhookKey, fromState,
            toState);
        eventPublisher.publishEvent(springEvent);
    }
}
