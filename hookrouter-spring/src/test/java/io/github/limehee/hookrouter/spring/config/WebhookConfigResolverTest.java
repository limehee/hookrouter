package io.github.limehee.hookrouter.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.BulkheadProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.CircuitBreakerProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformConfig;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RateLimiterProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RetryProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.TimeoutProperties;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.BulkheadOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.CircuitBreakerOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.RateLimiterOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.RetryOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.TimeoutOverride;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookConfigResolverTest {

    private WebhookConfigProperties properties;
    private WebhookConfigResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new WebhookConfigProperties();
        resolver = new WebhookConfigResolver(properties);
    }

    private WebhookEndpointConfig createEndpointConfig() {
        WebhookEndpointConfig config = new WebhookEndpointConfig();
        config.setUrl("https://test.hookrouter.url");
        return config;
    }

    private void addEndpointConfig(String platform, String webhookKey, WebhookEndpointConfig endpointConfig) {
        Map<String, PlatformConfig> platforms = properties.getPlatforms();
        PlatformConfig platformConfig = platforms.computeIfAbsent(platform, k -> new PlatformConfig());
        platformConfig.getEndpoints().put(webhookKey, endpointConfig);
    }

    @Nested
    class ResolveRetryPropertiesTest {

        @Test
        void shouldMatchExpectedResultMaxAttempts() {
            // Given
            properties.getRetry().setMaxAttempts(5);
            properties.getRetry().setInitialDelay(2000);

            // When
            RetryProperties result = resolver.resolveRetryProperties("slack", "general-channel");

            // Then
            assertThat(result.getMaxAttempts()).isEqualTo(5);
            assertThat(result.getInitialDelay()).isEqualTo(2000);
        }

        @Test
        void shouldMatchExpectedResultMaxAttemptsWhenMaxAttemptsIsPositive() {
            // Given
            properties.getRetry().setMaxAttempts(3);
            properties.getRetry().setInitialDelay(1000);

            WebhookEndpointConfig endpointConfig = createEndpointConfig();
            RetryOverride retryOverride = new RetryOverride();
            retryOverride.setMaxAttempts(10);
            endpointConfig.setRetry(retryOverride);

            addEndpointConfig("slack", "error-channel", endpointConfig);

            // When
            RetryProperties result = resolver.resolveRetryProperties("slack", "error-channel");

            // Then
            assertThat(result.getMaxAttempts()).isEqualTo(10);
            assertThat(result.getInitialDelay()).isEqualTo(1000);
        }

        @Test
        void shouldMatchExpectedResultMaxAttemptsWhenMaxAttemptsIsPositiveWithMaxAttemptsPositive() {
            // Given
            properties.getRetry().setMaxAttempts(3);
            properties.getRetry().setInitialDelay(1000);
            properties.getRetry().setMaxDelay(10000);
            properties.getRetry().setMultiplier(2.0);
            properties.getRetry().setJitterFactor(0.1);

            WebhookEndpointConfig endpointConfig = createEndpointConfig();
            RetryOverride retryOverride = new RetryOverride();
            retryOverride.setMaxAttempts(5);
            retryOverride.setMultiplier(3.0);
            endpointConfig.setRetry(retryOverride);

            addEndpointConfig("slack", "test-channel", endpointConfig);

            // When
            RetryProperties result = resolver.resolveRetryProperties("slack", "test-channel");

            // Then
            assertThat(result.getMaxAttempts()).isEqualTo(5);
            assertThat(result.getInitialDelay()).isEqualTo(1000);
            assertThat(result.getMaxDelay()).isEqualTo(10000);
            assertThat(result.getMultiplier()).isEqualTo(3.0);
            assertThat(result.getJitterFactor()).isEqualTo(0.1);
        }
    }

    @Nested
    class ResolveTimeoutPropertiesTest {

        @Test
        void shouldMatchExpectedResultDuration() {
            // Given
            properties.getTimeout().setDuration(5000);

            // When
            TimeoutProperties result = resolver.resolveTimeoutProperties("slack", "general-channel");

            // Then
            assertThat(result.getDuration()).isEqualTo(5000);
        }

        @Test
        void shouldMatchExpectedResultDurationWhenDurationIsPositive() {
            // Given
            properties.getTimeout().setDuration(5000);

            WebhookEndpointConfig endpointConfig = createEndpointConfig();
            TimeoutOverride timeoutOverride = new TimeoutOverride();
            timeoutOverride.setDuration(15000L);
            endpointConfig.setTimeout(timeoutOverride);

            addEndpointConfig("slack", "slow-service", endpointConfig);

            // When
            TimeoutProperties result = resolver.resolveTimeoutProperties("slack", "slow-service");

            // Then
            assertThat(result.getDuration()).isEqualTo(15000);
        }

        @Test
        void shouldMatchExpectedResultEnabled() {
            // Given
            properties.getTimeout().setEnabled(true);
            properties.getTimeout().setDuration(5000);

            WebhookEndpointConfig endpointConfig = createEndpointConfig();
            TimeoutOverride timeoutOverride = new TimeoutOverride();
            timeoutOverride.setEnabled(false);
            endpointConfig.setTimeout(timeoutOverride);

            addEndpointConfig("slack", "no-timeout", endpointConfig);

            // When
            TimeoutProperties result = resolver.resolveTimeoutProperties("slack", "no-timeout");

            // Then
            assertThat(result.isEnabled()).isFalse();
            assertThat(result.getDuration()).isEqualTo(5000);
        }
    }

    @Nested
    class ResolveCircuitBreakerPropertiesTest {

        @Test
        void shouldMatchExpectedResultFailureThreshold() {
            // Given
            properties.getCircuitBreaker().setFailureThreshold(5);
            properties.getCircuitBreaker().setWaitDuration(60000);

            // When
            CircuitBreakerProperties result = resolver.resolveCircuitBreakerProperties("slack", "general-channel");

            // Then
            assertThat(result.getFailureThreshold()).isEqualTo(5);
            assertThat(result.getWaitDuration()).isEqualTo(60000);
        }

        @Test
        void shouldMatchExpectedResultFailureThresholdWhenFailureThresholdIsPositive() {
            // Given
            properties.getCircuitBreaker().setFailureThreshold(5);
            properties.getCircuitBreaker().setWaitDuration(60000);
            properties.getCircuitBreaker().setSuccessThreshold(2);

            WebhookEndpointConfig endpointConfig = createEndpointConfig();
            CircuitBreakerOverride cbOverride = new CircuitBreakerOverride();
            cbOverride.setFailureThreshold(10);
            cbOverride.setWaitDuration(120000L);
            endpointConfig.setCircuitBreaker(cbOverride);

            addEndpointConfig("slack", "unstable-service", endpointConfig);

            // When
            CircuitBreakerProperties result = resolver.resolveCircuitBreakerProperties("slack", "unstable-service");

            // Then
            assertThat(result.getFailureThreshold()).isEqualTo(10);
            assertThat(result.getWaitDuration()).isEqualTo(120000);
            assertThat(result.getSuccessThreshold()).isEqualTo(2);
        }
    }

    @Nested
    class ResolveRateLimiterPropertiesTest {

        @Test
        void shouldMatchExpectedResultLimitForPeriod() {
            // Given
            properties.getRateLimiter().setLimitForPeriod(50);

            // When
            RateLimiterProperties result = resolver.resolveRateLimiterProperties("slack", "general-channel");

            // Then
            assertThat(result.getLimitForPeriod()).isEqualTo(50);
        }

        @Test
        void shouldMatchExpectedResultEnabledWhenEnabledIsFalse() {
            // Given
            properties.getRateLimiter().setEnabled(false);
            properties.getRateLimiter().setLimitForPeriod(50);

            WebhookEndpointConfig endpointConfig = createEndpointConfig();
            RateLimiterOverride rlOverride = new RateLimiterOverride();
            rlOverride.setEnabled(true);
            rlOverride.setLimitForPeriod(10);
            endpointConfig.setRateLimiter(rlOverride);

            addEndpointConfig("slack", "rate-limited", endpointConfig);

            // When
            RateLimiterProperties result = resolver.resolveRateLimiterProperties("slack", "rate-limited");

            // Then
            assertThat(result.isEnabled()).isTrue();
            assertThat(result.getLimitForPeriod()).isEqualTo(10);
        }
    }

    @Nested
    class ResolveBulkheadPropertiesTest {

        @Test
        void shouldMatchExpectedResultMaxConcurrentCalls() {
            // Given
            properties.getBulkhead().setMaxConcurrentCalls(25);

            // When
            BulkheadProperties result = resolver.resolveBulkheadProperties("slack", "general-channel");

            // Then
            assertThat(result.getMaxConcurrentCalls()).isEqualTo(25);
        }

        @Test
        void shouldMatchExpectedResultEnabledWhenEnabledIsFalse() {
            // Given
            properties.getBulkhead().setEnabled(false);
            properties.getBulkhead().setMaxConcurrentCalls(25);

            WebhookEndpointConfig endpointConfig = createEndpointConfig();
            BulkheadOverride bhOverride = new BulkheadOverride();
            bhOverride.setEnabled(true);
            bhOverride.setMaxConcurrentCalls(5);
            endpointConfig.setBulkhead(bhOverride);

            addEndpointConfig("slack", "limited-concurrency", endpointConfig);

            // When
            BulkheadProperties result = resolver.resolveBulkheadProperties("slack", "limited-concurrency");

            // Then
            assertThat(result.isEnabled()).isTrue();
            assertThat(result.getMaxConcurrentCalls()).isEqualTo(5);
        }
    }

    @Nested
    class CachingTest {

        @Test
        void shouldVerifyExpectedResult1() {
            // Given
            properties.getRetry().setMaxAttempts(3);

            // When
            RetryProperties result1 = resolver.resolveRetryProperties("slack", "general-channel");
            RetryProperties result2 = resolver.resolveRetryProperties("slack", "general-channel");

            // Then
            assertThat(result1).isSameAs(result2);
        }

        @Test
        void shouldMatchExpectedResult1() {
            // Given
            properties.getRetry().setMaxAttempts(3);

            WebhookEndpointConfig endpointConfig = createEndpointConfig();
            RetryOverride retryOverride = new RetryOverride();
            retryOverride.setMaxAttempts(10);
            endpointConfig.setRetry(retryOverride);
            addEndpointConfig("slack", "error-channel", endpointConfig);

            // When
            RetryProperties result1 = resolver.resolveRetryProperties("slack", "general-channel");
            RetryProperties result2 = resolver.resolveRetryProperties("slack", "error-channel");

            // Then
            assertThat(result1).isNotSameAs(result2);
            assertThat(result1.getMaxAttempts()).isEqualTo(3);
            assertThat(result2.getMaxAttempts()).isEqualTo(10);
        }
    }

    @Nested
    class NonExistentPlatformTest {

        @Test
        void shouldMatchExpectedResultMaxAttemptsWhenMaxAttemptsIsPositive() {
            // Given
            properties.getRetry().setMaxAttempts(3);

            // When
            RetryProperties result = resolver.resolveRetryProperties("non-existent", "channel");

            // Then
            assertThat(result.getMaxAttempts()).isEqualTo(3);
        }
    }
}
