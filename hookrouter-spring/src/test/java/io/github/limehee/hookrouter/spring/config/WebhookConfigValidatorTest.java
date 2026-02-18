package io.github.limehee.hookrouter.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformConfig;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformMapping;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookConfigValidatorTest {

    private WebhookConfigProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WebhookConfigProperties();
    }

    @Nested
    class ValidateTest {

        @Test
        void shouldNotThrowExceptionForValidConfiguration() {
            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInput() {
            // Given
            properties.getRetry().setEnabled(false);
            properties.getRetry().setMaxAttempts(-1);
            properties.getTimeout().setDuration(0);
            properties.getCircuitBreaker().setFailureThreshold(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .satisfies(ex -> {
                    WebhookConfigValidationException validationEx = (WebhookConfigValidationException) ex;
                    assertThat(validationEx.getValidationErrors()).hasSize(3);
                    assertThat(validationEx.getValidationErrors())
                        .anyMatch(error -> error.contains("retry.maxAttempts"))
                        .anyMatch(error -> error.contains("timeout.duration"))
                        .anyMatch(error -> error.contains("circuitBreaker.failureThreshold"));
                });
        }
    }

    @Nested
    class AsyncPropertiesValidationTest {

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetCorePoolSizeIsZero() {
            // Given
            properties.getAsync().setCorePoolSize(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("async.corePoolSize must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetMaxPoolSizeIsZero() {
            // Given
            properties.getAsync().setMaxPoolSize(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("async.maxPoolSize must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetCorePoolSizeIsPositive() {
            // Given
            properties.getAsync().setCorePoolSize(20);
            properties.getAsync().setMaxPoolSize(10);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("async.corePoolSize (20) must be <= async.maxPoolSize (10)");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetQueueCapacityIsZero() {
            // Given
            properties.getAsync().setQueueCapacity(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("async.queueCapacity must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetAwaitTerminationSecondsIsNegative() {
            // Given
            properties.getAsync().setAwaitTerminationSeconds(-1);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("async.awaitTerminationSeconds must be >= 0");
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenAwaitTerminationSecondsIsZero() {
            // Given
            properties.getAsync().setAwaitTerminationSeconds(0);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetThreadNamePrefixIsBlank() {
            // Given
            properties.getAsync().setThreadNamePrefix("");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("async.threadNamePrefix must not be blank");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenThreadNamePrefixIsWhitespace() {
            // Given
            properties.getAsync().setThreadNamePrefix("   ");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("async.threadNamePrefix must not be blank");
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenCorePoolSizeIsPositive() {
            // Given
            properties.getAsync().setCorePoolSize(5);
            properties.getAsync().setMaxPoolSize(5);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class RetryPropertiesValidationTest {

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetMaxAttemptsIsZero() {
            // Given
            properties.getRetry().setMaxAttempts(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("retry.maxAttempts must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetMaxAttemptsIsNegative() {
            // Given
            properties.getRetry().setMaxAttempts(-1);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("retry.maxAttempts must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetInitialDelayIsZero() {
            // Given
            properties.getRetry().setInitialDelay(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("retry.initialDelay must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetMaxDelayIsZero() {
            // Given
            properties.getRetry().setMaxDelay(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("retry.maxDelay must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetInitialDelayIsPositive() {
            // Given
            properties.getRetry().setInitialDelay(5000);
            properties.getRetry().setMaxDelay(1000);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("retry.maxDelay (1000) must be >= retry.initialDelay (5000)");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenMultiplierIsLessThanOne() {
            // Given
            properties.getRetry().setMultiplier(0.5);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("retry.multiplier must be >= 1.0");
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenJitterFactorIsZero() {
            // Given
            properties.getRetry().setJitterFactor(0.0);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenJitterFactorIsOne() {
            // Given
            properties.getRetry().setJitterFactor(1.0);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenJitterFactorIsNegative() {
            // Given
            properties.getRetry().setJitterFactor(-0.1);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("retry.jitterFactor must be between 0.0 and 1.0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenJitterFactorExceedsOne() {
            // Given
            properties.getRetry().setJitterFactor(1.5);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("retry.jitterFactor must be between 0.0 and 1.0");
        }
    }

    @Nested
    class TimeoutPropertiesValidationTest {

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetDurationIsZero() {
            // Given
            properties.getTimeout().setDuration(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("timeout.duration must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetDurationIsNegative() {
            // Given
            properties.getTimeout().setDuration(-1000);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("timeout.duration must be > 0");
        }
    }

    @Nested
    class RateLimiterPropertiesValidationTest {

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetLimitForPeriodIsZero() {
            // Given
            properties.getRateLimiter().setLimitForPeriod(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("rateLimiter.limitForPeriod must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetLimitRefreshPeriodIsZero() {
            // Given
            properties.getRateLimiter().setLimitRefreshPeriod(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("rateLimiter.limitRefreshPeriod must be > 0");
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenTimeoutDurationIsZero() {
            // Given
            properties.getRateLimiter().setTimeoutDuration(0);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetTimeoutDurationIsNegative() {
            // Given
            properties.getRateLimiter().setTimeoutDuration(-1);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("rateLimiter.timeoutDuration must be >= 0");
        }
    }

    @Nested
    class BulkheadPropertiesValidationTest {

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetMaxConcurrentCallsIsZero() {
            // Given
            properties.getBulkhead().setMaxConcurrentCalls(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("bulkhead.maxConcurrentCalls must be > 0");
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenMaxWaitDurationIsZero() {
            // Given
            properties.getBulkhead().setMaxWaitDuration(0);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetMaxWaitDurationIsNegative() {
            // Given
            properties.getBulkhead().setMaxWaitDuration(-1);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("bulkhead.maxWaitDuration must be >= 0");
        }
    }

    @Nested
    class CircuitBreakerPropertiesValidationTest {

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetFailureThresholdIsZero() {
            // Given
            properties.getCircuitBreaker().setFailureThreshold(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("circuitBreaker.failureThreshold must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetFailureRateThresholdIsZero() {
            // Given
            properties.getCircuitBreaker().setFailureRateThreshold(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("circuitBreaker.failureRateThreshold must be between 0 and 100");
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenFailureRateThresholdIsPositive() {
            // Given
            properties.getCircuitBreaker().setFailureRateThreshold(100);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetFailureRateThresholdIsPositive() {
            // Given
            properties.getCircuitBreaker().setFailureRateThreshold(101);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("circuitBreaker.failureRateThreshold must be between 0 and 100");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetWaitDurationIsZero() {
            // Given
            properties.getCircuitBreaker().setWaitDuration(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("circuitBreaker.waitDuration must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetSuccessThresholdIsZero() {
            // Given
            properties.getCircuitBreaker().setSuccessThreshold(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("circuitBreaker.successThreshold must be > 0");
        }
    }

    @Nested
    class DeadLetterPropertiesValidationTest {

        @Test
        void shouldNotThrowExceptionWhenDeadLetterConfigIsValid() {
            // Given
            properties.getDeadLetter().setSchedulerInterval(60000);
            properties.getDeadLetter().setSchedulerBatchSize(50);
            properties.getDeadLetter().setMaxRetries(3);
            properties.getDeadLetter().setSchedulerEnabled(true);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetSchedulerIntervalIsZero() {
            // Given
            properties.getDeadLetter().setSchedulerInterval(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("deadLetter.schedulerInterval must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetSchedulerIntervalIsNegative() {
            // Given
            properties.getDeadLetter().setSchedulerInterval(-1000);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("deadLetter.schedulerInterval must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetSchedulerBatchSizeIsZero() {
            // Given
            properties.getDeadLetter().setSchedulerBatchSize(0);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("deadLetter.schedulerBatchSize must be > 0");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetSchedulerBatchSizeIsNegative() {
            // Given
            properties.getDeadLetter().setSchedulerBatchSize(-1);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("deadLetter.schedulerBatchSize must be > 0");
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenSchedulerEnabledIsTrue() {
            // Given
            properties.getDeadLetter().setSchedulerEnabled(true);
            properties.getDeadLetter().setSchedulerInterval(30_000L);
            properties.getDeadLetter().setSchedulerBatchSize(100);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class WebhookConfigValidationExceptionTest {

        @Test
        void shouldMatchExpectedExceptionMessage() {
            // When
            WebhookConfigValidationException exception =
                new WebhookConfigValidationException("Test error");

            // Then
            assertThat(exception.getMessage()).isEqualTo("Test error");
            assertThat(exception.getValidationErrors()).containsExactly("Test error");
        }

        @Test
        void shouldMatchExpectedExceptionMessageUsingFactoryMethod() {
            // Given
            List<String> errors = List.of("error1", "error2");

            // When
            WebhookConfigValidationException exception =
                new WebhookConfigValidationException("Summary", errors);

            // Then
            assertThat(exception.getMessage()).isEqualTo("Summary");
            assertThat(exception.getValidationErrors()).containsExactly("error1", "error2");
        }

        @Test
        void shouldThrowUnsupportedOperationExceptionWhenInvalidInput() {
            // Given
            WebhookConfigValidationException exception =
                new WebhookConfigValidationException("Summary", List.of("error1"));

            // When & Then
            assertThatThrownBy(() -> exception.getValidationErrors().add("new error"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class UrlValidationTest {

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenSlackGeneralEndpointUrlUsesHttpsUrl() {
            // Given
            addEndpoint("slack", "general", "https://hooks.slack.com/services/T00/B00/xxx");

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenSlackGeneralEndpointUrlUsesHttpUrl() {
            // Given
            addEndpoint("slack", "general", "http://localhost:8080/webhook");

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSlackGeneralEndpointUrlIsNull() {
            // Given
            addEndpoint("slack", "general", null);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("platforms.slack.endpoints.general")
                .hasMessageContaining("URL is missing or blank");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSlackGeneralEndpointUrlIsBlank() {
            // Given
            addEndpoint("slack", "general", "");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("platforms.slack.endpoints.general")
                .hasMessageContaining("URL is missing or blank");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSlackGeneralEndpointUrlHasUrlLikeValue() {
            // Given
            addEndpoint("slack", "general", "hooks.slack.com/services/T00/B00/xxx");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("platforms.slack.endpoints.general")
                .hasMessageContaining("must have a scheme");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSlackGeneralEndpointUrlUsesCustomScheme() {
            // Given
            addEndpoint("slack", "general", "ftp://example.com/webhook");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("platforms.slack.endpoints.general")
                .hasMessageContaining("must use http or https scheme");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSlackGeneralEndpointUrlUsesHttpsUrl() {
            // Given
            addEndpoint("slack", "general", "https:///path/to/webhook");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("platforms.slack.endpoints.general")
                .hasMessageContaining("must have a valid host");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSlackGeneralEndpointUrlUsesHttpsUrlWithSlackGeneralEndpointHttpsUrl() {
            // Given
            addEndpoint("slack", "general", "https://[invalid-host/webhook");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("platforms.slack.endpoints.general")
                .hasMessageContaining("invalid");
        }

        private void addEndpoint(String platform, String webhookKey, String url) {
            PlatformConfig config = properties.getPlatforms()
                .computeIfAbsent(platform, k -> new PlatformConfig());
            WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();
            endpointConfig.setUrl(url);
            config.getEndpoints().put(webhookKey, endpointConfig);
        }
    }

    @Nested
    class MappingValidationTest {

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenSlackErrorChannelEndpointUrlUsesHttpsUrl() {
            // Given
            addEndpoint("slack", "error-channel", "https://hooks.slack.com/xxx");
            addTypeMapping("demo.server.error", "slack", "error-channel");

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenSlackGeneralChannelEndpointUrlUsesHttpsUrl() {
            // Given
            addEndpoint("slack", "general-channel", "https://hooks.slack.com/xxx");
            addCategoryMapping("GENERAL", "slack", "general-channel");

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenSlackGeneralChannelEndpointUrlUsesHttpsUrlWithSlackGeneralChannelEndpointHttpsUrl() {
            // Given
            addEndpoint("slack", "general-channel", "https://hooks.slack.com/xxx");
            addDefaultMapping("slack", "general-channel");

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenTypeMappingReferencesUnknownPlatform() {
            // Given
            addTypeMapping("demo.server.error", "nonexistent", "channel");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("type-mappings[demo.server.error]")
                .hasMessageContaining("references non-existent platform: nonexistent");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSlackExistingChannelEndpointUrlUsesHttpsUrl() {
            // Given
            addEndpoint("slack", "existing-channel", "https://hooks.slack.com/xxx");
            addTypeMapping("demo.server.error", "slack", "nonexistent-channel");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("type-mappings[demo.server.error]")
                .hasMessageContaining("references non-existent webhook: slack.nonexistent-channel");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenCategoryMappingReferencesUnknownPlatform() {
            // Given
            addCategoryMapping("GENERAL", "nonexistent", "channel");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("category-mappings[GENERAL]")
                .hasMessageContaining("references non-existent platform: nonexistent");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenDefaultMappingReferencesUnknownWebhook() {
            // Given
            addEndpoint("slack", "existing-channel", "https://hooks.slack.com/xxx");
            addDefaultMapping("slack", "nonexistent-channel");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("default-mappings[0]")
                .hasMessageContaining("references non-existent webhook: slack.nonexistent-channel");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenTypeMappingPlatformIsNull() {
            // Given
            addTypeMapping("demo.server.error", null, "channel");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("type-mappings[demo.server.error]")
                .hasMessageContaining("platform is missing or blank");
        }

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSlackChannelEndpointUrlUsesHttpsUrl() {
            // Given
            addEndpoint("slack", "channel", "https://hooks.slack.com/xxx");
            addDefaultMapping("slack", "");

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("default-mappings[0]")
                .hasMessageContaining("webhook is missing or blank");
        }

        private void addEndpoint(String platform, String webhookKey, String url) {
            PlatformConfig config = properties.getPlatforms()
                .computeIfAbsent(platform, k -> new PlatformConfig());
            WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();
            endpointConfig.setUrl(url);
            config.getEndpoints().put(webhookKey, endpointConfig);
        }

        private void addTypeMapping(String typeId, String platform, String webhook) {
            PlatformMapping mapping = new PlatformMapping();
            mapping.setPlatform(platform);
            mapping.setWebhook(webhook);
            properties.getTypeMappings()
                .computeIfAbsent(typeId, k -> new ArrayList<>())
                .add(mapping);
        }

        private void addCategoryMapping(String category, String platform, String webhook) {
            PlatformMapping mapping = new PlatformMapping();
            mapping.setPlatform(platform);
            mapping.setWebhook(webhook);
            properties.getCategoryMappings()
                .computeIfAbsent(category, k -> new ArrayList<>())
                .add(mapping);
        }

        private void addDefaultMapping(String platform, String webhook) {
            PlatformMapping mapping = new PlatformMapping();
            mapping.setPlatform(platform);
            mapping.setWebhook(webhook);
            List<PlatformMapping> mappings = new ArrayList<>(properties.getDefaultMappings());
            mappings.add(mapping);
            properties.setDefaultMappings(mappings);
        }
    }

    @Nested
    class CrossConfigurationValidationTest {

        @Test
        void shouldThrowWebhookConfigValidationExceptionWhenInvalidInputAndSetEnabledIsTrue() {
            // Given
            properties.getTimeout().setEnabled(true);
            properties.getTimeout().setDuration(5000);
            properties.getRetry().setEnabled(true);
            properties.getRetry().setMaxDelay(10000);

            // When & Then
            assertThatThrownBy(() -> WebhookConfigValidator.validate(properties))
                .isInstanceOf(WebhookConfigValidationException.class)
                .hasMessageContaining("timeout.duration (5000ms) must be >= retry.maxDelay (10000ms)");
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenEnabledIsTrue() {
            // Given
            properties.getTimeout().setEnabled(true);
            properties.getTimeout().setDuration(10000);
            properties.getRetry().setEnabled(true);
            properties.getRetry().setMaxDelay(10000);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenEnabledIsTrueWithEnabledTrue() {
            // Given
            properties.getTimeout().setEnabled(true);
            properties.getTimeout().setDuration(15000);
            properties.getRetry().setEnabled(true);
            properties.getRetry().setMaxDelay(10000);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenEnabledIsFalse() {
            // Given
            properties.getTimeout().setEnabled(false);
            properties.getTimeout().setDuration(5000);
            properties.getRetry().setEnabled(true);
            properties.getRetry().setMaxDelay(10000);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenTimeoutEnabledAndRetryDisabled() {
            // Given
            properties.getTimeout().setEnabled(true);
            properties.getTimeout().setDuration(5000);
            properties.getRetry().setEnabled(false);
            properties.getRetry().setMaxDelay(10000);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionForValidConfigurationWhenEnabledIsFalseWithEnabledFalse() {
            // Given
            properties.getTimeout().setEnabled(false);
            properties.getTimeout().setDuration(5000);
            properties.getRetry().setEnabled(false);
            properties.getRetry().setMaxDelay(10000);

            // When & Then
            assertThatCode(() -> WebhookConfigValidator.validate(properties))
                .doesNotThrowAnyException();
        }
    }
}
