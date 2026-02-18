package io.github.limehee.hookrouter.spring.actuator;

import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.DeadLetterStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public class WebhookHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Set<String> monitoredWebhookKeys;
    @Nullable
    private final DeadLetterStore deadLetterStore;

    public WebhookHealthIndicator(
        final CircuitBreakerRegistry circuitBreakerRegistry,
        final Set<String> monitoredWebhookKeys,
        @Nullable final DeadLetterStore deadLetterStore
    ) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.monitoredWebhookKeys = monitoredWebhookKeys;
        this.deadLetterStore = deadLetterStore;
    }

    @Override
    public Health health() {
        CircuitBreakerSummary summary = buildCircuitBreakerSummary();
        Map<String, Object> deadLetterDetails = buildDeadLetterDetails();
        Health.Builder builder = determineHealthStatus(summary.openCount(), summary.halfOpenCount()).withDetail(
            "circuitBreaker", summary.details());
        if (!deadLetterDetails.isEmpty()) {
            builder.withDetail("deadLetter", deadLetterDetails);
        }
        return builder.build();
    }

    private CircuitBreakerSummary buildCircuitBreakerSummary() {
        Map<String, Object> details = new HashMap<>();
        details.put("enabled", true);
        Map<String, String> circuitStates = new HashMap<>();
        int openCount = 0;
        int halfOpenCount = 0;
        int closedCount = 0;
        for (String webhookKey : monitoredWebhookKeys) {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(webhookKey);
            State state = circuitBreaker.getState();
            circuitStates.put(webhookKey, state.name());
            switch (state) {
                case OPEN, FORCED_OPEN -> openCount++;
                case HALF_OPEN -> halfOpenCount++;
                case CLOSED, DISABLED, METRICS_ONLY -> closedCount++;
            }
        }
        details.put("circuits", circuitStates);
        details.put("openCount", openCount);
        details.put("halfOpenCount", halfOpenCount);
        details.put("closedCount", closedCount);
        return new CircuitBreakerSummary(openCount, halfOpenCount, closedCount, details);
    }

    private Map<String, Object> buildDeadLetterDetails() {
        if (deadLetterStore == null) {
            return Map.of();
        }
        Map<String, Object> details = new HashMap<>();
        details.put("pending", deadLetterStore.countByStatus(DeadLetterStatus.PENDING));
        details.put("processing", deadLetterStore.countByStatus(DeadLetterStatus.PROCESSING));
        details.put("resolved", deadLetterStore.countByStatus(DeadLetterStatus.RESOLVED));
        details.put("abandoned", deadLetterStore.countByStatus(DeadLetterStatus.ABANDONED));
        return details;
    }

    private Health.Builder determineHealthStatus(int openCount, int halfOpenCount) {
        if (monitoredWebhookKeys.isEmpty()) {
            return Health.up();
        }
        if (openCount == monitoredWebhookKeys.size()) {
            return Health.down();
        }
        if (openCount > 0 || halfOpenCount > 0) {
            return Health.status("DEGRADED");
        }
        return Health.up();
    }

    private record CircuitBreakerSummary(
        int openCount,
        int halfOpenCount,
        int closedCount,
        Map<String, Object> details
    ) {

    }
}
