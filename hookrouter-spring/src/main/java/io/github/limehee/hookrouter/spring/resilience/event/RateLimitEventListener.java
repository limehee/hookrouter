package io.github.limehee.hookrouter.spring.resilience.event;

import io.github.limehee.hookrouter.spring.metrics.WebhookMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.springframework.context.event.EventListener;

public class RateLimitEventListener {

    private static final long DEFAULT_COOLDOWN_MILLIS = 60000L;

    private static final int COOLDOWN_LIMIT_FOR_PERIOD = 1;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final WebhookMetrics webhookMetrics;

    public RateLimitEventListener(final RateLimiterRegistry rateLimiterRegistry, final WebhookMetrics webhookMetrics) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.webhookMetrics = webhookMetrics;
    }

    @EventListener
    public void onRateLimitDetected(RateLimitDetectedEvent event) {
        recordMetrics(event);
        adjustRateLimiter(event);
    }

    protected void recordMetrics(RateLimitDetectedEvent event) {
        webhookMetrics.recordExternalRateLimitDetected(event.platform(), event.webhookKey(), event.typeId(),
            event.retryAfterMillis());
    }

    protected void adjustRateLimiter(RateLimitDetectedEvent event) {
        Long retryAfter = event.retryAfterMillis();
        long cooldownMillis = (retryAfter != null && retryAfter > 0) ? retryAfter : DEFAULT_COOLDOWN_MILLIS;
        RateLimiterConfig cooldownConfig = RateLimiterConfig.custom().limitForPeriod(COOLDOWN_LIMIT_FOR_PERIOD)
            .limitRefreshPeriod(Duration.ofMillis(cooldownMillis)).timeoutDuration(Duration.ZERO).build();
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(event.webhookKey(), cooldownConfig);

        rateLimiter.drainPermissions();
    }
}
