package io.github.limehee.hookrouter.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.BulkheadOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.CircuitBreakerOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.RateLimiterOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.RetryOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.TimeoutOverride;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookEndpointConfigTest {

    private WebhookEndpointConfig config;

    @BeforeEach
    void setUp() {
        config = new WebhookEndpointConfig();
    }

    @Nested
    class BasicFieldsTest {

        @Test
        void shouldReturnNullConfigUrl() {
            // Then
            assertThat(config.getUrl()).isNull();
            assertThat(config.getRetry()).isNull();
            assertThat(config.getTimeout()).isNull();
            assertThat(config.getCircuitBreaker()).isNull();
            assertThat(config.getRateLimiter()).isNull();
            assertThat(config.getBulkhead()).isNull();
        }

        @Test
        void shouldMatchExpectedConfigUrl() {
            // Given
            String url = "https://hooks.slack.com/services/test";

            // When
            config.setUrl(url);

            // Then
            assertThat(config.getUrl()).isEqualTo(url);
        }
    }

    @Nested
    class RetryOverrideTest {

        @Test
        void shouldReturnNullRetryEnabled() {
            // Given
            RetryOverride retry = new RetryOverride();

            // Then
            assertThat(retry.getEnabled()).isNull();
            assertThat(retry.getMaxAttempts()).isNull();
            assertThat(retry.getInitialDelay()).isNull();
            assertThat(retry.getMaxDelay()).isNull();
            assertThat(retry.getMultiplier()).isNull();
            assertThat(retry.getJitterFactor()).isNull();
        }

        @Test
        void shouldReturnNotNullConfigRetry() {
            // Given
            RetryOverride retry = new RetryOverride();

            // When
            retry.setEnabled(true);
            retry.setMaxAttempts(5);
            retry.setInitialDelay(2000L);
            retry.setMaxDelay(30000L);
            retry.setMultiplier(2.0);
            retry.setJitterFactor(0.5);

            config.setRetry(retry);

            // Then
            assertThat(config.getRetry()).isNotNull();
            assertThat(config.getRetry().getEnabled()).isTrue();
            assertThat(config.getRetry().getMaxAttempts()).isEqualTo(5);
            assertThat(config.getRetry().getInitialDelay()).isEqualTo(2000L);
            assertThat(config.getRetry().getMaxDelay()).isEqualTo(30000L);
            assertThat(config.getRetry().getMultiplier()).isEqualTo(2.0);
            assertThat(config.getRetry().getJitterFactor()).isEqualTo(0.5);
        }

        @Test
        void shouldReturnNullConfigRetry() {
            // Given
            RetryOverride retry = new RetryOverride();

            // When
            retry.setMaxAttempts(10);

            config.setRetry(retry);

            // Then
            assertThat(config.getRetry().getMaxAttempts()).isEqualTo(10);
            assertThat(config.getRetry().getEnabled()).isNull();
            assertThat(config.getRetry().getInitialDelay()).isNull();
        }
    }

    @Nested
    class TimeoutOverrideTest {

        @Test
        void shouldReturnNullTimeoutEnabled() {
            // Given
            TimeoutOverride timeout = new TimeoutOverride();

            // Then
            assertThat(timeout.getEnabled()).isNull();
            assertThat(timeout.getDuration()).isNull();
        }

        @Test
        void shouldReturnNotNullConfigTimeout() {
            // Given
            TimeoutOverride timeout = new TimeoutOverride();

            // When
            timeout.setEnabled(false);
            timeout.setDuration(15000L);

            config.setTimeout(timeout);

            // Then
            assertThat(config.getTimeout()).isNotNull();
            assertThat(config.getTimeout().getEnabled()).isFalse();
            assertThat(config.getTimeout().getDuration()).isEqualTo(15000L);
        }
    }

    @Nested
    class CircuitBreakerOverrideTest {

        @Test
        void shouldReturnNullCircuitBreakerEnabled() {
            // Given
            CircuitBreakerOverride circuitBreaker = new CircuitBreakerOverride();

            // Then
            assertThat(circuitBreaker.getEnabled()).isNull();
            assertThat(circuitBreaker.getFailureThreshold()).isNull();
            assertThat(circuitBreaker.getFailureRateThreshold()).isNull();
            assertThat(circuitBreaker.getWaitDuration()).isNull();
            assertThat(circuitBreaker.getSuccessThreshold()).isNull();
        }

        @Test
        void shouldReturnNotNullConfigCircuitBreaker() {
            // Given
            CircuitBreakerOverride circuitBreaker = new CircuitBreakerOverride();

            // When
            circuitBreaker.setEnabled(true);
            circuitBreaker.setFailureThreshold(10);
            circuitBreaker.setFailureRateThreshold(60.0f);
            circuitBreaker.setWaitDuration(120000L);
            circuitBreaker.setSuccessThreshold(3);

            config.setCircuitBreaker(circuitBreaker);

            // Then
            assertThat(config.getCircuitBreaker()).isNotNull();
            assertThat(config.getCircuitBreaker().getEnabled()).isTrue();
            assertThat(config.getCircuitBreaker().getFailureThreshold()).isEqualTo(10);
            assertThat(config.getCircuitBreaker().getFailureRateThreshold()).isEqualTo(60.0f);
            assertThat(config.getCircuitBreaker().getWaitDuration()).isEqualTo(120000L);
            assertThat(config.getCircuitBreaker().getSuccessThreshold()).isEqualTo(3);
        }
    }

    @Nested
    class RateLimiterOverrideTest {

        @Test
        void shouldReturnNullRateLimiterEnabled() {
            // Given
            RateLimiterOverride rateLimiter = new RateLimiterOverride();

            // Then
            assertThat(rateLimiter.getEnabled()).isNull();
            assertThat(rateLimiter.getLimitForPeriod()).isNull();
            assertThat(rateLimiter.getLimitRefreshPeriod()).isNull();
            assertThat(rateLimiter.getTimeoutDuration()).isNull();
        }

        @Test
        void shouldReturnNotNullConfigRateLimiter() {
            // Given
            RateLimiterOverride rateLimiter = new RateLimiterOverride();

            // When
            rateLimiter.setEnabled(true);
            rateLimiter.setLimitForPeriod(100);
            rateLimiter.setLimitRefreshPeriod(60000L);
            rateLimiter.setTimeoutDuration(5000L);

            config.setRateLimiter(rateLimiter);

            // Then
            assertThat(config.getRateLimiter()).isNotNull();
            assertThat(config.getRateLimiter().getEnabled()).isTrue();
            assertThat(config.getRateLimiter().getLimitForPeriod()).isEqualTo(100);
            assertThat(config.getRateLimiter().getLimitRefreshPeriod()).isEqualTo(60000L);
            assertThat(config.getRateLimiter().getTimeoutDuration()).isEqualTo(5000L);
        }
    }

    @Nested
    class BulkheadOverrideTest {

        @Test
        void shouldReturnNullBulkheadEnabled() {
            // Given
            BulkheadOverride bulkhead = new BulkheadOverride();

            // Then
            assertThat(bulkhead.getEnabled()).isNull();
            assertThat(bulkhead.getMaxConcurrentCalls()).isNull();
            assertThat(bulkhead.getMaxWaitDuration()).isNull();
        }

        @Test
        void shouldReturnNotNullConfigBulkhead() {
            // Given
            BulkheadOverride bulkhead = new BulkheadOverride();

            // When
            bulkhead.setEnabled(true);
            bulkhead.setMaxConcurrentCalls(50);
            bulkhead.setMaxWaitDuration(10000L);

            config.setBulkhead(bulkhead);

            // Then
            assertThat(config.getBulkhead()).isNotNull();
            assertThat(config.getBulkhead().getEnabled()).isTrue();
            assertThat(config.getBulkhead().getMaxConcurrentCalls()).isEqualTo(50);
            assertThat(config.getBulkhead().getMaxWaitDuration()).isEqualTo(10000L);
        }
    }

    @Nested
    class CombinedConfigTest {

        @Test
        void shouldMatchExpectedConfigUrlWhenUrlUsesHttpsUrl() {
            // Given
            config.setUrl("https://hooks.slack.com/services/test");

            RetryOverride retry = new RetryOverride();
            retry.setMaxAttempts(5);
            config.setRetry(retry);

            TimeoutOverride timeout = new TimeoutOverride();
            timeout.setDuration(10000L);
            config.setTimeout(timeout);

            CircuitBreakerOverride circuitBreaker = new CircuitBreakerOverride();
            circuitBreaker.setFailureThreshold(10);
            config.setCircuitBreaker(circuitBreaker);

            RateLimiterOverride rateLimiter = new RateLimiterOverride();
            rateLimiter.setLimitForPeriod(50);
            config.setRateLimiter(rateLimiter);

            BulkheadOverride bulkhead = new BulkheadOverride();
            bulkhead.setMaxConcurrentCalls(25);
            config.setBulkhead(bulkhead);

            // Then
            assertThat(config.getUrl()).isEqualTo("https://hooks.slack.com/services/test");
            assertThat(config.getRetry().getMaxAttempts()).isEqualTo(5);
            assertThat(config.getTimeout().getDuration()).isEqualTo(10000L);
            assertThat(config.getCircuitBreaker().getFailureThreshold()).isEqualTo(10);
            assertThat(config.getRateLimiter().getLimitForPeriod()).isEqualTo(50);
            assertThat(config.getBulkhead().getMaxConcurrentCalls()).isEqualTo(25);
        }
    }
}
