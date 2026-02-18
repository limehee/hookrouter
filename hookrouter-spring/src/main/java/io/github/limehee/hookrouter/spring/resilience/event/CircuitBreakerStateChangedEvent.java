package io.github.limehee.hookrouter.spring.resilience.event;

import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import java.time.Instant;
import org.springframework.context.ApplicationEvent;

public class CircuitBreakerStateChangedEvent extends ApplicationEvent {

    private final String webhookKey;
    private final State fromState;
    private final State toState;
    private final Instant eventTimestamp;

    public CircuitBreakerStateChangedEvent(Object source, String webhookKey, State fromState, State toState) {
        super(source);
        this.webhookKey = webhookKey;
        this.fromState = fromState;
        this.toState = toState;
        this.eventTimestamp = Instant.now();
    }

    public boolean isCircuitOpened() {
        return toState == State.OPEN;
    }

    public boolean isCircuitClosed() {
        return toState == State.CLOSED;
    }

    public boolean isCircuitHalfOpen() {
        return toState == State.HALF_OPEN;
    }

    public String getWebhookKey() {
        return this.webhookKey;
    }

    public State getFromState() {
        return this.fromState;
    }

    public State getToState() {
        return this.toState;
    }

    public Instant getEventTimestamp() {
        return this.eventTimestamp;
    }

    @Override
    public String toString() {
        return "CircuitBreakerStateChangedEvent(webhookKey=" + this.getWebhookKey() + ", fromState="
            + this.getFromState() + ", toState=" + this.getToState() + ", eventTimestamp=" + this.getEventTimestamp()
            + ")";
    }
}
