package io.github.limehee.hookrouter.spring.resilience.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.github.limehee.hookrouter.spring.metrics.WebhookMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimitEventListenerTest {

    private RateLimiterRegistry rateLimiterRegistry;
    private RateLimitEventListener listener;

    @Mock
    private WebhookMetrics webhookMetrics;

    @BeforeEach
    void setUp() {
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
            .limitForPeriod(50)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ZERO)
            .build();
        rateLimiterRegistry = RateLimiterRegistry.of(defaultConfig);
        listener = new RateLimitEventListener(rateLimiterRegistry, webhookMetrics);
    }

    @Nested
    class OnRateLimitDetectedTest {

        @Test
        void shouldBeLessThanOrEqualToExpectedAdjustedLimiterMetrics() {
            // Given
            String webhookKey = "test-channel";
            RateLimitDetectedEvent event = RateLimitDetectedEvent.of(
                "slack", webhookKey, "https://hooks.slack.com/xxx",
                "demo.test.event", 30000L, "Too Many Requests"
            );

            RateLimiter initialLimiter = rateLimiterRegistry.rateLimiter(webhookKey);
            int initialPermissions = initialLimiter.getMetrics().getAvailablePermissions();

            // When
            listener.onRateLimitDetected(event);

            // Then
            RateLimiter adjustedLimiter = rateLimiterRegistry.rateLimiter(webhookKey);

            assertThat(adjustedLimiter.getMetrics().getAvailablePermissions())
                .isLessThanOrEqualTo(1);

            verify(webhookMetrics).recordExternalRateLimitDetected(
                "slack", webhookKey, "demo.test.event", 30000L);
        }

        @Test
        void shouldBeLessThanOrEqualToExpectedAdjustedLimiterMetricsWhenOfHasUrlLikeValue() {
            // Given
            String webhookKey = "no-retry-after-channel";
            RateLimitDetectedEvent event = RateLimitDetectedEvent.of(
                "slack", webhookKey, "https://hooks.slack.com/xxx",
                "demo.test.event", null, "Too Many Requests"
            );

            // When
            listener.onRateLimitDetected(event);

            // Then
            RateLimiter adjustedLimiter = rateLimiterRegistry.rateLimiter(webhookKey);

            assertThat(adjustedLimiter.getMetrics().getAvailablePermissions())
                .isLessThanOrEqualTo(1);

            verify(webhookMetrics).recordExternalRateLimitDetected(
                "slack", webhookKey, "demo.test.event", null);
        }

        @Test
        void shouldMatchExpectedLimiter1Metrics() {
            // Given
            String webhookKey1 = "channel-1";
            String webhookKey2 = "channel-2";

            RateLimitDetectedEvent event1 = RateLimitDetectedEvent.of(
                "slack", webhookKey1, "https://hooks.slack.com/1",
                "demo.test.event", 10000L, "Too Many Requests"
            );

            RateLimiter limiter2Before = rateLimiterRegistry.rateLimiter(webhookKey2);
            int permissionsBefore = limiter2Before.getMetrics().getAvailablePermissions();

            listener.onRateLimitDetected(event1);

            // Then
            RateLimiter limiter1 = rateLimiterRegistry.rateLimiter(webhookKey1);
            RateLimiter limiter2After = rateLimiterRegistry.rateLimiter(webhookKey2);

            assertThat(limiter1.getMetrics().getAvailablePermissions())
                .isLessThanOrEqualTo(1);

            assertThat(limiter2After.getMetrics().getAvailablePermissions())
                .isEqualTo(permissionsBefore);

            verify(webhookMetrics).recordExternalRateLimitDetected(
                "slack", webhookKey1, "demo.test.event", 10000L);
        }
    }
}
