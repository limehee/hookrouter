package io.github.limehee.hookrouter.spring.resilience.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerEventListenerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CircuitBreakerEventListener eventListener;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        eventListener = new CircuitBreakerEventListener(eventPublisher);
        circuitBreakerRegistry = createCircuitBreakerRegistry();
    }

    private CircuitBreakerRegistry createCircuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(1)
            .build();

        return CircuitBreakerRegistry.of(config, List.of(eventListener));
    }

    @Nested
    class RegistrationTest {

        @Test
        void shouldReturnNotNullCircuitBreaker() {
            // When
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("test-hookrouter");

            assertThat(circuitBreaker).isNotNull();
            assertThat(circuitBreaker.getName()).isEqualTo("test-hookrouter");
        }
    }

    @Nested
    class StateTransitionTest {

        @Test
        void shouldMatchExpectedCircuitBreakerState() {
            // Given
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("test-hookrouter");
            assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);

            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("test error"));
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("test error"));

            assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);

            ArgumentCaptor<CircuitBreakerStateChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(CircuitBreakerStateChangedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            CircuitBreakerStateChangedEvent event = eventCaptor.getValue();
            assertThat(event.getWebhookKey()).isEqualTo("test-hookrouter");
            assertThat(event.getFromState()).isEqualTo(State.CLOSED);
            assertThat(event.getToState()).isEqualTo(State.OPEN);
            assertThat(event.isCircuitOpened()).isTrue();
        }

        @Test
        void shouldMatchExpectedCircuitBreakerStateWhenCircuitBreaker() throws InterruptedException {

            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("test-hookrouter-2");
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("test error"));
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("test error"));
            assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);

            Thread.sleep(150);
            circuitBreaker.tryAcquirePermission();

            assertThat(circuitBreaker.getState()).isEqualTo(State.HALF_OPEN);

            ArgumentCaptor<CircuitBreakerStateChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(CircuitBreakerStateChangedEvent.class);
            verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

            List<CircuitBreakerStateChangedEvent> events = eventCaptor.getAllValues();
            CircuitBreakerStateChangedEvent halfOpenEvent = events.get(1);
            assertThat(halfOpenEvent.getFromState()).isEqualTo(State.OPEN);
            assertThat(halfOpenEvent.getToState()).isEqualTo(State.HALF_OPEN);
            assertThat(halfOpenEvent.isCircuitHalfOpen()).isTrue();
        }

        @Test
        void shouldMatchExpectedCircuitBreakerStateWhenHalfOpenRecoversToClosed() throws InterruptedException {

            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("test-hookrouter-3");
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("test error"));
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("test error"));
            Thread.sleep(150);
            circuitBreaker.tryAcquirePermission();
            assertThat(circuitBreaker.getState()).isEqualTo(State.HALF_OPEN);

            circuitBreaker.onSuccess(0, TimeUnit.MILLISECONDS);

            assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);

            ArgumentCaptor<CircuitBreakerStateChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(CircuitBreakerStateChangedEvent.class);
            verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());

            List<CircuitBreakerStateChangedEvent> events = eventCaptor.getAllValues();
            CircuitBreakerStateChangedEvent closedEvent = events.get(2);
            assertThat(closedEvent.getFromState()).isEqualTo(State.HALF_OPEN);
            assertThat(closedEvent.getToState()).isEqualTo(State.CLOSED);
            assertThat(closedEvent.isCircuitClosed()).isTrue();
        }
    }

    @Nested
    class EventTest {

        @Test
        void shouldReturnNotNullEventSource() {
            // Given
            Object source = new Object();

            // When
            CircuitBreakerStateChangedEvent event =
                new CircuitBreakerStateChangedEvent(source, "my-webhook", State.CLOSED, State.OPEN);

            // Then
            assertThat(event.getSource()).isEqualTo(source);
            assertThat(event.getWebhookKey()).isEqualTo("my-webhook");
            assertThat(event.getFromState()).isEqualTo(State.CLOSED);
            assertThat(event.getToState()).isEqualTo(State.OPEN);
            assertThat(event.getEventTimestamp()).isNotNull();
            assertThat(event.isCircuitOpened()).isTrue();
            assertThat(event.isCircuitClosed()).isFalse();
            assertThat(event.isCircuitHalfOpen()).isFalse();
        }

        @Test
        void shouldContainExpectedToString() {
            // Given
            CircuitBreakerStateChangedEvent event =
                new CircuitBreakerStateChangedEvent(this, "test-key", State.OPEN, State.HALF_OPEN);

            // When
            String toString = event.toString();

            // Then
            assertThat(toString).contains("test-key");
            assertThat(toString).contains("OPEN");
            assertThat(toString).contains("HALF_OPEN");
        }
    }
}
