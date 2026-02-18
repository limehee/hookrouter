package io.github.limehee.hookrouter.spring.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.DeadLetterStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class WebhookHealthIndicatorTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private DeadLetterStore deadLetterStore;

    private WebhookHealthIndicator healthIndicator;

    @Nested
    class CircuitBreakerHealthTest {

        @Test
        void shouldMatchExpectedHealthStatus() {
            // Given
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, Set.of(), null);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsKey("circuitBreaker");

            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(details.get("enabled")).isEqualTo(true);
            assertThat(details.get("openCount")).isEqualTo(0);
            assertThat(details.get("halfOpenCount")).isEqualTo(0);
            assertThat(details.get("closedCount")).isEqualTo(0);
        }

        @Test
        void shouldMatchExpectedHealthStatusUsingFactoryMethod() {
            // Given
            Set<String> webhookKeys = Set.of("channel-1", "channel-2");
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, webhookKeys, null);

            CircuitBreaker cb1 = createMockCircuitBreaker(State.CLOSED);
            CircuitBreaker cb2 = createMockCircuitBreaker(State.CLOSED);
            given(circuitBreakerRegistry.circuitBreaker("channel-1")).willReturn(cb1);
            given(circuitBreakerRegistry.circuitBreaker("channel-2")).willReturn(cb2);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);

            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(details.get("openCount")).isEqualTo(0);
            assertThat(details.get("halfOpenCount")).isEqualTo(0);
            assertThat(details.get("closedCount")).isEqualTo(2);

            @SuppressWarnings("unchecked")
            Map<String, String> circuits = (Map<String, String>) details.get("circuits");
            assertThat(circuits.get("channel-1")).isEqualTo("CLOSED");
            assertThat(circuits.get("channel-2")).isEqualTo("CLOSED");
        }

        @Test
        void shouldMatchExpectedHealthStatusWhenAllCircuitBreakersAreOpen() {
            // Given
            Set<String> webhookKeys = Set.of("channel-1", "channel-2");
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, webhookKeys, null);

            CircuitBreaker cb1 = createMockCircuitBreaker(State.OPEN);
            CircuitBreaker cb2 = createMockCircuitBreaker(State.OPEN);
            given(circuitBreakerRegistry.circuitBreaker("channel-1")).willReturn(cb1);
            given(circuitBreakerRegistry.circuitBreaker("channel-2")).willReturn(cb2);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);

            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(details.get("openCount")).isEqualTo(2);
            assertThat(details.get("halfOpenCount")).isEqualTo(0);
            assertThat(details.get("closedCount")).isEqualTo(0);
        }

        @Test
        void shouldMatchExpectedHealthStatusUsingFactoryMethodAndGetCode() {
            // Given
            Set<String> webhookKeys = Set.of("channel-1", "channel-2");
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, webhookKeys, null);

            CircuitBreaker cb1 = createMockCircuitBreaker(State.CLOSED);
            CircuitBreaker cb2 = createMockCircuitBreaker(State.OPEN);
            given(circuitBreakerRegistry.circuitBreaker("channel-1")).willReturn(cb1);
            given(circuitBreakerRegistry.circuitBreaker("channel-2")).willReturn(cb2);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");

            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(details.get("openCount")).isEqualTo(1);
            assertThat(details.get("halfOpenCount")).isEqualTo(0);
            assertThat(details.get("closedCount")).isEqualTo(1);
        }

        @Test
        void shouldMatchExpectedHealthStatusCodeWhenHalfOpenCircuitExists() {
            // Given
            Set<String> webhookKeys = Set.of("channel-1", "channel-2");
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, webhookKeys, null);

            CircuitBreaker cb1 = createMockCircuitBreaker(State.CLOSED);
            CircuitBreaker cb2 = createMockCircuitBreaker(State.HALF_OPEN);
            given(circuitBreakerRegistry.circuitBreaker("channel-1")).willReturn(cb1);
            given(circuitBreakerRegistry.circuitBreaker("channel-2")).willReturn(cb2);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");

            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(details.get("openCount")).isEqualTo(0);
            assertThat(details.get("halfOpenCount")).isEqualTo(1);
            assertThat(details.get("closedCount")).isEqualTo(1);
        }

        @Test
        void shouldMatchExpectedHealthStatusWhenCircuitBreakerIsForcedOpen() {
            // Given
            Set<String> webhookKeys = Set.of("channel-1");
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, webhookKeys, null);

            CircuitBreaker cb1 = createMockCircuitBreaker(State.FORCED_OPEN);
            given(circuitBreakerRegistry.circuitBreaker("channel-1")).willReturn(cb1);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);

            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(details.get("openCount")).isEqualTo(1);
        }

        @Test
        void shouldMatchExpectedHealthStatusUsingFactoryMethodWithBlankValue() {
            // Given
            Set<String> webhookKeys = Set.of("channel-1");
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, webhookKeys, null);

            CircuitBreaker cb1 = createMockCircuitBreaker(State.DISABLED);
            given(circuitBreakerRegistry.circuitBreaker("channel-1")).willReturn(cb1);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);

            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(details.get("closedCount")).isEqualTo(1);
        }

        @Test
        void shouldMatchExpectedHealthStatusUsingFactoryMethodWithNullValue() {
            // Given
            Set<String> webhookKeys = Set.of("channel-1");
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, webhookKeys, null);

            CircuitBreaker cb1 = createMockCircuitBreaker(State.METRICS_ONLY);
            given(circuitBreakerRegistry.circuitBreaker("channel-1")).willReturn(cb1);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);

            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(details.get("closedCount")).isEqualTo(1);
        }

        @Test
        void shouldMatchExpectedHealthStatusWhenOpenAndHalfOpenStatesCoexist() {
            // Given
            Set<String> webhookKeys = Set.of("channel-1", "channel-2", "channel-3", "channel-4");
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, webhookKeys, null);

            CircuitBreaker cb1 = createMockCircuitBreaker(State.CLOSED);
            CircuitBreaker cb2 = createMockCircuitBreaker(State.OPEN);
            CircuitBreaker cb3 = createMockCircuitBreaker(State.HALF_OPEN);
            CircuitBreaker cb4 = createMockCircuitBreaker(State.CLOSED);
            given(circuitBreakerRegistry.circuitBreaker("channel-1")).willReturn(cb1);
            given(circuitBreakerRegistry.circuitBreaker("channel-2")).willReturn(cb2);
            given(circuitBreakerRegistry.circuitBreaker("channel-3")).willReturn(cb3);
            given(circuitBreakerRegistry.circuitBreaker("channel-4")).willReturn(cb4);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");

            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(details.get("openCount")).isEqualTo(1);
            assertThat(details.get("halfOpenCount")).isEqualTo(1);
            assertThat(details.get("closedCount")).isEqualTo(2);

            @SuppressWarnings("unchecked")
            Map<String, String> circuits = (Map<String, String>) details.get("circuits");
            assertThat(circuits).hasSize(4);
        }

        private CircuitBreaker createMockCircuitBreaker(State state) {
            CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
            given(circuitBreaker.getState()).willReturn(state);
            return circuitBreaker;
        }
    }

    @Nested
    class DeadLetterHealthTest {

        @Test
        void shouldVerifyExpectedHealthGetDetails() {
            // Given
            healthIndicator = new WebhookHealthIndicator(circuitBreakerRegistry, Set.of(), null);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).doesNotContainKey("deadLetter");
        }

        @Test
        void shouldMatchExpectedHealthDetails() {
            // Given
            healthIndicator = new WebhookHealthIndicator(
                circuitBreakerRegistry, Set.of(), deadLetterStore);

            given(deadLetterStore.countByStatus(DeadLetterStatus.PENDING)).willReturn(5L);
            given(deadLetterStore.countByStatus(DeadLetterStatus.PROCESSING)).willReturn(1L);
            given(deadLetterStore.countByStatus(DeadLetterStatus.RESOLVED)).willReturn(100L);
            given(deadLetterStore.countByStatus(DeadLetterStatus.ABANDONED)).willReturn(2L);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("deadLetter");

            @SuppressWarnings("unchecked")
            Map<String, Object> deadLetterDetails =
                (Map<String, Object>) health.getDetails().get("deadLetter");
            assertThat(deadLetterDetails.get("pending")).isEqualTo(5L);
            assertThat(deadLetterDetails.get("processing")).isEqualTo(1L);
            assertThat(deadLetterDetails.get("resolved")).isEqualTo(100L);
            assertThat(deadLetterDetails.get("abandoned")).isEqualTo(2L);
        }

        @Test
        void shouldMatchExpectedHealthStatusUsingFactoryMethod() {
            // Given
            Set<String> webhookKeys = Set.of("channel-1");
            healthIndicator = new WebhookHealthIndicator(
                circuitBreakerRegistry, webhookKeys, deadLetterStore);

            CircuitBreaker cb1 = mock(CircuitBreaker.class);
            given(cb1.getState()).willReturn(State.CLOSED);
            given(circuitBreakerRegistry.circuitBreaker("channel-1")).willReturn(cb1);

            given(deadLetterStore.countByStatus(DeadLetterStatus.PENDING)).willReturn(3L);
            given(deadLetterStore.countByStatus(DeadLetterStatus.PROCESSING)).willReturn(0L);
            given(deadLetterStore.countByStatus(DeadLetterStatus.RESOLVED)).willReturn(50L);
            given(deadLetterStore.countByStatus(DeadLetterStatus.ABANDONED)).willReturn(1L);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsKey("circuitBreaker");
            assertThat(health.getDetails()).containsKey("deadLetter");

            @SuppressWarnings("unchecked")
            Map<String, Object> circuitDetails =
                (Map<String, Object>) health.getDetails().get("circuitBreaker");
            assertThat(circuitDetails.get("closedCount")).isEqualTo(1);

            @SuppressWarnings("unchecked")
            Map<String, Object> deadLetterDetails =
                (Map<String, Object>) health.getDetails().get("deadLetter");
            assertThat(deadLetterDetails.get("pending")).isEqualTo(3L);
        }
    }
}
