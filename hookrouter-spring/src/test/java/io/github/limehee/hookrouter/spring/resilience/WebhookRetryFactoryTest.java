package io.github.limehee.hookrouter.spring.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RetryProperties;
import io.github.limehee.hookrouter.spring.resilience.WebhookRetryFactory.WebhookSendRetryableException;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookRetryFactoryTest {

    private static long delayOf(RetryConfig config, int attempt) {
        return config.<Object>getIntervalBiFunction().apply(attempt, Either.right(null));
    }

    @Nested
    class CreateRegistryTest {

        @Test
        void shouldReturnNotNullRegistry() {
            // Given
            RetryProperties properties = new RetryProperties();

            // When
            RetryRegistry registry = WebhookRetryFactory.createRegistry(properties);

            // Then
            assertThat(registry).isNotNull();
            Retry retry = registry.retry("test");
            assertThat(retry).isNotNull();
        }

        @Test
        void shouldReturnNotNullRegistryWhenMaxAttemptsIsPositive() {
            // Given
            RetryProperties properties = new RetryProperties();
            properties.setMaxAttempts(5);
            properties.setInitialDelay(500);
            properties.setMaxDelay(5000);
            properties.setMultiplier(1.5);
            properties.setJitterFactor(0.2);

            // When
            RetryRegistry registry = WebhookRetryFactory.createRegistry(properties);

            // Then
            assertThat(registry).isNotNull();
            Retry retry = registry.retry("test");
            RetryConfig config = retry.getRetryConfig();

            assertThat(config.getMaxAttempts()).isEqualTo(5);
        }
    }

    @Nested
    class CreateConfigTest {

        @Test
        void shouldMatchExpectedConfigMaxAttempts() {
            // Given
            RetryProperties properties = new RetryProperties();
            properties.setMaxAttempts(0);

            // When
            RetryConfig config = WebhookRetryFactory.createConfig(properties);

            // Then

            assertThat(config.getMaxAttempts()).isEqualTo(1);
        }

        @Test
        void shouldThrowWebhookSendRetryableExceptionWhenInvalidInput() {
            // Given
            RetryProperties properties = new RetryProperties();
            properties.setMaxAttempts(3);
            properties.setInitialDelay(1);
            RetryRegistry registry = WebhookRetryFactory.createRegistry(properties);
            Retry retry = registry.retry("test");

            AtomicInteger counter = new AtomicInteger(0);

            assertThatThrownBy(() -> retry.executeRunnable(() -> {
                counter.incrementAndGet();
                throw new WebhookSendRetryableException("retryable error");
            })).isInstanceOf(WebhookSendRetryableException.class);

            assertThat(counter.get()).isEqualTo(3);
        }

        @Test
        void shouldThrowRuntimeExceptionWhenInvalidInput() {
            // Given
            RetryProperties properties = new RetryProperties();
            properties.setMaxAttempts(3);
            properties.setInitialDelay(1);
            RetryRegistry registry = WebhookRetryFactory.createRegistry(properties);
            Retry retry = registry.retry("test");

            AtomicInteger counter = new AtomicInteger(0);

            assertThatThrownBy(() -> retry.executeRunnable(() -> {
                counter.incrementAndGet();
                throw new RuntimeException("non-retryable error");
            })).isInstanceOf(RuntimeException.class);

            assertThat(counter.get()).isEqualTo(1);
        }
    }

    @Nested
    class ExponentialBackoffTest {

        @Test
        void shouldMatchExpectedDelayOfConfig() {
            // Given
            RetryProperties properties = new RetryProperties();
            properties.setMaxAttempts(3);
            properties.setInitialDelay(100);
            properties.setMultiplier(2.0);
            properties.setJitterFactor(0.0);
            properties.setMaxDelay(10000);

            RetryConfig config = WebhookRetryFactory.createConfig(properties);

            // When & Then

            assertThat(delayOf(config, 1)).isEqualTo(100);

            assertThat(delayOf(config, 2)).isEqualTo(200);

            assertThat(delayOf(config, 3)).isEqualTo(400);
        }

        @Test
        void shouldMatchExpectedDelayOfConfigWhenMaxAttemptsIsPositive() {
            // Given
            RetryProperties properties = new RetryProperties();
            properties.setMaxAttempts(10);
            properties.setInitialDelay(100);
            properties.setMultiplier(2.0);
            properties.setJitterFactor(0.0);
            properties.setMaxDelay(500);

            RetryConfig config = WebhookRetryFactory.createConfig(properties);

            // When & Then
            // attemptNumber 1: 100 * 2^0 = 100
            assertThat(delayOf(config, 1)).isEqualTo(100);
            // attemptNumber 2: 100 * 2^1 = 200
            assertThat(delayOf(config, 2)).isEqualTo(200);
            // attemptNumber 3: 100 * 2^2 = 400
            assertThat(delayOf(config, 3)).isEqualTo(400);

            assertThat(delayOf(config, 4)).isEqualTo(500);

            assertThat(delayOf(config, 5)).isEqualTo(500);
        }

        @Test
        void shouldBeGreaterThanOrEqualToExpectedMinDelay() {
            // Given
            RetryProperties properties = new RetryProperties();
            properties.setMaxAttempts(3);
            properties.setInitialDelay(1000);
            properties.setMultiplier(2.0);
            properties.setJitterFactor(0.1);  // ±10% Jitter
            properties.setMaxDelay(10000);

            RetryConfig config = WebhookRetryFactory.createConfig(properties);

            // When

            long minDelay = Long.MAX_VALUE;
            long maxDelay = Long.MIN_VALUE;
            for (int i = 0; i < 100; i++) {
                long delay = delayOf(config, 1);
                minDelay = Math.min(minDelay, delay);
                maxDelay = Math.max(maxDelay, delay);
            }

            // Then
            // baseDelay = 1000, jitterRange = 100 (10%)

            assertThat(minDelay).isGreaterThanOrEqualTo(900);
            assertThat(maxDelay).isLessThanOrEqualTo(1100);

            assertThat(minDelay).isNotEqualTo(maxDelay);
        }
    }

    @Nested
    class WebhookSendRetryableExceptionTest {

        @Test
        void shouldReturnNullExceptionMessage() {
            // When
            WebhookSendRetryableException exception = new WebhookSendRetryableException("test error");

            // Then
            assertThat(exception.getMessage()).isEqualTo("test error");
            assertThat(exception.getCause()).isNull();
        }
    }
}
