package io.github.limehee.hookrouter.spring.resilience.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CircuitBreakerStateChangedEventTest {

    private CircuitBreakerStateChangedEvent createEvent(State fromState, State toState) {
        return new CircuitBreakerStateChangedEvent(this, "test-hookrouter", fromState, toState);
    }

    private CircuitBreakerStateChangedEvent createEvent(String webhookKey, State fromState, State toState) {
        return new CircuitBreakerStateChangedEvent(this, webhookKey, fromState, toState);
    }

    @Nested
    class ConstructorTest {

        @Test
        void shouldMatchExpectedEventSource() {
            // Given
            Object source = new Object();
            String webhookKey = "slack-test-channel";
            State fromState = State.CLOSED;
            State toState = State.OPEN;

            // When
            Instant beforeCreation = Instant.now();
            CircuitBreakerStateChangedEvent event = new CircuitBreakerStateChangedEvent(
                source, webhookKey, fromState, toState
            );
            Instant afterCreation = Instant.now();

            // Then
            assertThat(event.getSource()).isSameAs(source);
            assertThat(event.getWebhookKey()).isEqualTo(webhookKey);
            assertThat(event.getFromState()).isEqualTo(fromState);
            assertThat(event.getToState()).isEqualTo(toState);
            assertThat(event.getEventTimestamp())
                .isAfterOrEqualTo(beforeCreation)
                .isBeforeOrEqualTo(afterCreation);
        }
    }

    @Nested
    class IsCircuitOpenedTest {

        @Test
        void shouldBeTrueResult() {
            // Given
            CircuitBreakerStateChangedEvent event = createEvent(State.CLOSED, State.OPEN);

            // When
            boolean result = event.isCircuitOpened();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void shouldBeFalseHALFOPEN() {
            // When & Then
            assertThat(createEvent(State.OPEN, State.HALF_OPEN).isCircuitOpened()).isFalse();
            assertThat(createEvent(State.HALF_OPEN, State.CLOSED).isCircuitOpened()).isFalse();
            assertThat(createEvent(State.CLOSED, State.HALF_OPEN).isCircuitOpened()).isFalse();
        }
    }

    @Nested
    class IsCircuitClosedTest {

        @Test
        void shouldBeTrueResultWhenTransitionIsHalfOpenToClosed() {
            // Given
            CircuitBreakerStateChangedEvent event = createEvent(State.HALF_OPEN, State.CLOSED);

            // When
            boolean result = event.isCircuitClosed();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void shouldBeFalseStateOPEN() {
            // When & Then
            assertThat(createEvent(State.CLOSED, State.OPEN).isCircuitClosed()).isFalse();
            assertThat(createEvent(State.OPEN, State.HALF_OPEN).isCircuitClosed()).isFalse();
            assertThat(createEvent(State.HALF_OPEN, State.OPEN).isCircuitClosed()).isFalse();
        }
    }

    @Nested
    class IsCircuitHalfOpenTest {

        @Test
        void shouldBeTrueResultWhenTransitionIsOpenToHalfOpen() {
            // Given
            CircuitBreakerStateChangedEvent event = createEvent(State.OPEN, State.HALF_OPEN);

            // When
            boolean result = event.isCircuitHalfOpen();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void shouldBeFalseStateOPENWhenTransitionDoesNotEnterHalfOpen() {
            // When & Then
            assertThat(createEvent(State.CLOSED, State.OPEN).isCircuitHalfOpen()).isFalse();
            assertThat(createEvent(State.HALF_OPEN, State.CLOSED).isCircuitHalfOpen()).isFalse();
            assertThat(createEvent(State.HALF_OPEN, State.OPEN).isCircuitHalfOpen()).isFalse();
        }
    }

    @Nested
    class ToStringTest {

        @Test
        void shouldContainExpectedResult() {
            // Given
            CircuitBreakerStateChangedEvent event = createEvent(
                "slack-error-channel", State.CLOSED, State.OPEN
            );

            // When
            String result = event.toString();

            // Then
            assertThat(result)
                .contains("webhookKey=slack-error-channel")
                .contains("fromState=CLOSED")
                .contains("toState=OPEN")
                .contains("eventTimestamp=");
        }
    }

    @Nested
    class StateTransitionScenarioTest {

        @Test
        void shouldBeTrueEventCircuitOpened() {
            // Given
            CircuitBreakerStateChangedEvent event = createEvent(State.CLOSED, State.OPEN);

            // Then
            assertThat(event.isCircuitOpened()).isTrue();
            assertThat(event.isCircuitClosed()).isFalse();
            assertThat(event.isCircuitHalfOpen()).isFalse();
        }

        @Test
        void shouldBeTrueEventCircuitOpenedWhenTransitionIsOpenToHalfOpen() {
            // Given
            CircuitBreakerStateChangedEvent event = createEvent(State.OPEN, State.HALF_OPEN);

            // Then
            assertThat(event.isCircuitOpened()).isFalse();
            assertThat(event.isCircuitClosed()).isFalse();
            assertThat(event.isCircuitHalfOpen()).isTrue();
        }

        @Test
        void shouldBeTrueEventCircuitOpenedWhenTransitionIsHalfOpenToClosed() {
            // Given
            CircuitBreakerStateChangedEvent event = createEvent(State.HALF_OPEN, State.CLOSED);

            // Then
            assertThat(event.isCircuitOpened()).isFalse();
            assertThat(event.isCircuitClosed()).isTrue();
            assertThat(event.isCircuitHalfOpen()).isFalse();
        }

        @Test
        void shouldBeTrueEventCircuitOpenedWhenTransitionReturnsToOpenFromHalfOpen() {
            // Given
            CircuitBreakerStateChangedEvent event = createEvent(State.HALF_OPEN, State.OPEN);

            // Then
            assertThat(event.isCircuitOpened()).isTrue();
            assertThat(event.isCircuitClosed()).isFalse();
            assertThat(event.isCircuitHalfOpen()).isFalse();
        }
    }
}
