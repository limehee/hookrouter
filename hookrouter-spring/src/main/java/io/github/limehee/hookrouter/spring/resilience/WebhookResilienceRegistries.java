package io.github.limehee.hookrouter.spring.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.Objects;

public record WebhookResilienceRegistries(
    CircuitBreakerRegistry circuitBreakerRegistry,
    RetryRegistry retryRegistry,
    TimeLimiterRegistry timeLimiterRegistry,
    RateLimiterRegistry rateLimiterRegistry,
    BulkheadRegistry bulkheadRegistry
) {

    public WebhookResilienceRegistries {
        Objects.requireNonNull(circuitBreakerRegistry, "circuitBreakerRegistry must not be null");
        Objects.requireNonNull(retryRegistry, "retryRegistry must not be null");
        Objects.requireNonNull(timeLimiterRegistry, "timeLimiterRegistry must not be null");
        Objects.requireNonNull(rateLimiterRegistry, "rateLimiterRegistry must not be null");
        Objects.requireNonNull(bulkheadRegistry, "bulkheadRegistry must not be null");
    }
}
