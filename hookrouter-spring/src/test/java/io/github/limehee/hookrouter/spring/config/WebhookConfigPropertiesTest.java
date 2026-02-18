package io.github.limehee.hookrouter.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.CircuitBreakerProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.DeadLetterProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformConfig;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformMapping;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RetryProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookConfigPropertiesTest {

    private WebhookConfigProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WebhookConfigProperties();
    }

    @Nested
    class PlatformConfigTest {

        @Test
        void shouldMatchExpectedConfigEndpoints() {
            // Given
            PlatformConfig config = new PlatformConfig();
            WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();
            endpointConfig.setUrl("https://hooks.slack.com/extended");

            // When
            config.getEndpoints().put("error-channel", endpointConfig);

            // Then
            assertThat(config.getEndpoints()).hasSize(1);
            assertThat(config.getEndpoints().get("error-channel").getUrl())
                .isEqualTo("https://hooks.slack.com/extended");
        }

        @Test
        void shouldBeEmptyConfigEndpoints() {
            // Given
            PlatformConfig config = new PlatformConfig();

            // Then
            assertThat(config.getEndpoints()).isEmpty();
        }

        @Test
        void shouldMatchExpectedUrl() {
            // Given
            PlatformConfig config = new PlatformConfig();

            WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();
            endpointConfig.setUrl("https://hooks.slack.com/test");
            config.getEndpoints().put("channel", endpointConfig);

            // When
            String url = config.getWebhookUrl("channel");

            // Then
            assertThat(url).isEqualTo("https://hooks.slack.com/test");
        }

        @Test
        void shouldReturnNullUrl() {
            // Given
            PlatformConfig config = new PlatformConfig();

            // When
            String url = config.getWebhookUrl("non-existent");

            // Then
            assertThat(url).isNull();
        }

        @Test
        void shouldReturnNullUrlWhenPut() {
            // Given
            PlatformConfig config = new PlatformConfig();

            WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();

            config.getEndpoints().put("channel", endpointConfig);

            // When
            String url = config.getWebhookUrl("channel");

            // Then
            assertThat(url).isNull();
        }
    }

    @Nested
    class PlatformMappingTest {

        @Test
        void shouldMatchExpectedMappingPlatform() {
            // Given
            PlatformMapping mapping = new PlatformMapping();

            // When
            mapping.setPlatform("slack");
            mapping.setWebhook("error-channel");

            // Then
            assertThat(mapping.getPlatform()).isEqualTo("slack");
            assertThat(mapping.getWebhook()).isEqualTo("error-channel");
        }

        @Test
        void shouldReturnNullMappingPlatform() {
            // Given
            PlatformMapping mapping = new PlatformMapping();

            // Then
            assertThat(mapping.getPlatform()).isNull();
            assertThat(mapping.getWebhook()).isNull();
        }
    }

    @Nested
    class GetWebhookUrlTest {

        @Test
        void shouldMatchExpectedUrlWhenUrlUsesHttpsUrl() {
            // Given
            PlatformConfig slackConfig = new PlatformConfig();
            WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();
            endpointConfig.setUrl("https://hooks.slack.com/error");
            slackConfig.getEndpoints().put("error-channel", endpointConfig);

            Map<String, PlatformConfig> platforms = new HashMap<>();
            platforms.put("slack", slackConfig);
            properties.setPlatforms(platforms);

            // When
            String url = properties.getWebhookUrl("slack", "error-channel");

            // Then
            assertThat(url).isEqualTo("https://hooks.slack.com/error");
        }

        @Test
        void shouldReturnNullUrlWhenWebhookUrl() {
            // When
            String url = properties.getWebhookUrl("unknown", "channel");

            // Then
            assertThat(url).isNull();
        }

        @Test
        void shouldReturnNullUrlWhenUrlUsesHttpsUrl() {
            // Given
            PlatformConfig slackConfig = new PlatformConfig();
            WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();
            endpointConfig.setUrl("https://hooks.slack.com/error");
            slackConfig.getEndpoints().put("error-channel", endpointConfig);

            Map<String, PlatformConfig> platforms = new HashMap<>();
            platforms.put("slack", slackConfig);
            properties.setPlatforms(platforms);

            // When
            String url = properties.getWebhookUrl("slack", "unknown-channel");

            // Then
            assertThat(url).isNull();
        }

        @Test
        void shouldMatchExpectedUrlWhenUrlUsesHttpsUrlAndIsEqualTo() {
            // Given
            PlatformConfig slackConfig = new PlatformConfig();
            WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();
            endpointConfig.setUrl("https://hooks.slack.com/extended");
            slackConfig.getEndpoints().put("extended-channel", endpointConfig);

            properties.getPlatforms().put("slack", slackConfig);

            // When
            String url = properties.getWebhookUrl("slack", "extended-channel");

            // Then
            assertThat(url).isEqualTo("https://hooks.slack.com/extended");
        }
    }

    @Nested
    class GetEndpointConfigTest {

        @Test
        void shouldReturnNotNullResult() {
            // Given
            PlatformConfig slackConfig = new PlatformConfig();
            WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();
            endpointConfig.setUrl("https://hooks.slack.com/test");

            WebhookEndpointConfig.RetryOverride retryOverride = new WebhookEndpointConfig.RetryOverride();
            retryOverride.setMaxAttempts(10);
            endpointConfig.setRetry(retryOverride);

            slackConfig.getEndpoints().put("error-channel", endpointConfig);
            properties.getPlatforms().put("slack", slackConfig);

            // When
            WebhookEndpointConfig result = properties.getEndpointConfig("slack", "error-channel");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUrl()).isEqualTo("https://hooks.slack.com/test");
            assertThat(result.getRetry()).isNotNull();
            assertThat(result.getRetry().getMaxAttempts()).isEqualTo(10);
        }

        @Test
        void shouldReturnNullResult() {
            // When
            WebhookEndpointConfig result = properties.getEndpointConfig("non-existent", "channel");

            // Then
            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullResultWhenPut() {
            // Given
            PlatformConfig slackConfig = new PlatformConfig();
            properties.getPlatforms().put("slack", slackConfig);

            // When
            WebhookEndpointConfig result = properties.getEndpointConfig("slack", "non-existent");

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    class GetterSetterTest {

        @Test
        void shouldContainExpectedPropertiesCategoryMappings() {
            // Given
            PlatformMapping mapping = new PlatformMapping();
            mapping.setPlatform("slack");
            mapping.setWebhook("general-channel");

            Map<String, List<PlatformMapping>> categoryMappings = new HashMap<>();
            categoryMappings.put("GENERAL", List.of(mapping));

            // When
            properties.setCategoryMappings(categoryMappings);

            // Then
            assertThat(properties.getCategoryMappings()).containsKey("GENERAL");
            assertThat(properties.getCategoryMappings().get("GENERAL")).hasSize(1);
        }

        @Test
        void shouldContainExpectedPropertiesTypeMappings() {
            // Given
            PlatformMapping mapping = new PlatformMapping();
            mapping.setPlatform("slack");
            mapping.setWebhook("error-channel");

            Map<String, List<PlatformMapping>> typeMappings = new HashMap<>();
            typeMappings.put("demo.server.error", List.of(mapping));

            // When
            properties.setTypeMappings(typeMappings);

            // Then
            assertThat(properties.getTypeMappings()).containsKey("demo.server.error");
        }

        @Test
        void shouldMatchExpectedPropertiesDefaultMappings() {
            // Given
            PlatformMapping mapping = new PlatformMapping();
            mapping.setPlatform("slack");
            mapping.setWebhook("default-channel");

            // When
            properties.setDefaultMappings(List.of(mapping));

            // Then
            assertThat(properties.getDefaultMappings()).hasSize(1);
            assertThat(properties.getDefaultMappings().get(0).getPlatform()).isEqualTo("slack");
        }

        @Test
        void shouldBeEmptyPropertiesDefaultMappings() {
            // Then
            assertThat(properties.getDefaultMappings()).isEmpty();
        }

        @Test
        void shouldMatchExpectedPropertiesRetry() {
            // Given
            RetryProperties retry = new RetryProperties();
            retry.setEnabled(false);
            retry.setMaxAttempts(5);

            // When
            properties.setRetry(retry);

            // Then
            assertThat(properties.getRetry().isEnabled()).isFalse();
            assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
        }
    }

    @Nested
    class RetryPropertiesTest {

        @Test
        void shouldMatchExpectedRetryEnabled() {
            // Given
            RetryProperties retry = new RetryProperties();

            // Then
            assertThat(retry.isEnabled()).isTrue();
            assertThat(retry.getMaxAttempts()).isEqualTo(3);
            assertThat(retry.getInitialDelay()).isEqualTo(1000L);
            assertThat(retry.getMaxDelay()).isEqualTo(10000L);
            assertThat(retry.getMultiplier()).isEqualTo(2.0);
            assertThat(retry.getJitterFactor()).isEqualTo(0.1);
        }

        @Test
        void shouldMatchExpectedRetryEnabledWhenEnabledIsFalse() {
            // Given
            RetryProperties retry = new RetryProperties();

            // When
            retry.setEnabled(false);
            retry.setMaxAttempts(5);
            retry.setInitialDelay(2000L);
            retry.setMaxDelay(30000L);
            retry.setMultiplier(3.0);
            retry.setJitterFactor(0.2);

            // Then
            assertThat(retry.isEnabled()).isFalse();
            assertThat(retry.getMaxAttempts()).isEqualTo(5);
            assertThat(retry.getInitialDelay()).isEqualTo(2000L);
            assertThat(retry.getMaxDelay()).isEqualTo(30000L);
            assertThat(retry.getMultiplier()).isEqualTo(3.0);
            assertThat(retry.getJitterFactor()).isEqualTo(0.2);
        }

        @Test
        void shouldMatchExpectedRetryCalculateBaseDelay() {
            // Given
            RetryProperties retry = new RetryProperties();
            retry.setInitialDelay(1000);
            retry.setMultiplier(2.0);
            retry.setMaxDelay(10000);

            // attempt 0: 1000 * 2^0 = 1000
            assertThat(retry.calculateBaseDelay(0)).isEqualTo(1000L);
            // attempt 1: 1000 * 2^1 = 2000
            assertThat(retry.calculateBaseDelay(1)).isEqualTo(2000L);
            // attempt 2: 1000 * 2^2 = 4000
            assertThat(retry.calculateBaseDelay(2)).isEqualTo(4000L);
            // attempt 3: 1000 * 2^3 = 8000
            assertThat(retry.calculateBaseDelay(3)).isEqualTo(8000L);
        }

        @Test
        void shouldMatchExpectedRetryCalculateBaseDelayWhenInitialDelayIsPositive() {
            // Given
            RetryProperties retry = new RetryProperties();
            retry.setInitialDelay(1000);
            retry.setMultiplier(2.0);
            retry.setMaxDelay(5000);

            // Then
            // attempt 0: min(1000, 5000) = 1000
            assertThat(retry.calculateBaseDelay(0)).isEqualTo(1000L);
            // attempt 1: min(2000, 5000) = 2000
            assertThat(retry.calculateBaseDelay(1)).isEqualTo(2000L);
            // attempt 2: min(4000, 5000) = 4000
            assertThat(retry.calculateBaseDelay(2)).isEqualTo(4000L);
            // attempt 3: min(8000, 5000) = 5000 (capped)
            assertThat(retry.calculateBaseDelay(3)).isEqualTo(5000L);
            // attempt 10: min(very large, 5000) = 5000 (capped)
            assertThat(retry.calculateBaseDelay(10)).isEqualTo(5000L);
        }

        @Test
        void shouldMatchExpectedRetryCalculateBaseDelayWhenInitialDelayIsPositiveWithInitialDelayPositive() {
            // Given
            RetryProperties retry = new RetryProperties();
            retry.setInitialDelay(1000);
            retry.setMultiplier(1.0);
            retry.setMaxDelay(10000);

            // Then
            assertThat(retry.calculateBaseDelay(0)).isEqualTo(1000L);
            assertThat(retry.calculateBaseDelay(1)).isEqualTo(1000L);
            assertThat(retry.calculateBaseDelay(5)).isEqualTo(1000L);
        }

        @Test
        void shouldMatchExpectedRetryCalculateDelay() {
            // Given
            RetryProperties retry = new RetryProperties();
            retry.setInitialDelay(1000);
            retry.setMultiplier(2.0);
            retry.setMaxDelay(10000);
            retry.setJitterFactor(0);

            assertThat(retry.calculateDelay(0)).isEqualTo(1000L);
            assertThat(retry.calculateDelay(1)).isEqualTo(2000L);
            assertThat(retry.calculateDelay(2)).isEqualTo(4000L);
        }

        @Test
        void shouldBeWithinExpectedRangeDelay() {
            // Given
            RetryProperties retry = new RetryProperties();
            retry.setInitialDelay(1000);
            retry.setMultiplier(2.0);
            retry.setMaxDelay(10000);
            retry.setJitterFactor(0.1);  // ±10% jitter

            // baseDelay for attempt 1 = 2000, jitter ±10% = [1800, 2200]
            for (int i = 0; i < 100; i++) {
                long delay = retry.calculateDelay(1);
                assertThat(delay).isBetween(1800L, 2200L);
            }
        }

        @Test
        void shouldBeGreaterThanOrEqualToExpectedDelay() {
            // Given
            RetryProperties retry = new RetryProperties();
            retry.setInitialDelay(100);
            retry.setMultiplier(1.0);
            retry.setMaxDelay(10000);
            retry.setJitterFactor(0.5);

            for (int i = 0; i < 100; i++) {
                long delay = retry.calculateDelay(0);
                assertThat(delay).isGreaterThanOrEqualTo(0L);
            }
        }
    }

    @Nested
    class CircuitBreakerPropertiesTest {

        @Test
        void shouldMatchExpectedCbEnabled() {
            // Given
            CircuitBreakerProperties cb = new CircuitBreakerProperties();

            // Then
            assertThat(cb.isEnabled()).isTrue();
            assertThat(cb.getFailureThreshold()).isEqualTo(5);
            assertThat(cb.getWaitDuration()).isEqualTo(60000L);
            assertThat(cb.getSuccessThreshold()).isEqualTo(2);
        }

        @Test
        void shouldMatchExpectedCbEnabledWhenEnabledIsFalse() {
            // Given
            CircuitBreakerProperties cb = new CircuitBreakerProperties();

            // When
            cb.setEnabled(false);
            cb.setFailureThreshold(10);
            cb.setWaitDuration(120000L);
            cb.setSuccessThreshold(5);

            // Then
            assertThat(cb.isEnabled()).isFalse();
            assertThat(cb.getFailureThreshold()).isEqualTo(10);
            assertThat(cb.getWaitDuration()).isEqualTo(120000L);
            assertThat(cb.getSuccessThreshold()).isEqualTo(5);
        }

        @Test
        void shouldMatchExpectedPropertiesCircuitBreaker() {
            // Given
            CircuitBreakerProperties cb = new CircuitBreakerProperties();
            cb.setEnabled(false);
            cb.setFailureThreshold(7);

            // When
            properties.setCircuitBreaker(cb);

            // Then
            assertThat(properties.getCircuitBreaker().isEnabled()).isFalse();
            assertThat(properties.getCircuitBreaker().getFailureThreshold()).isEqualTo(7);
        }
    }

    @Nested
    class DeadLetterPropertiesTest {

        @Test
        void shouldBeTrueDlEnabled() {
            // Given
            DeadLetterProperties dl = new DeadLetterProperties();

            // Then
            assertThat(dl.isEnabled()).isTrue();
        }

        @Test
        void shouldBeFalseDlEnabled() {
            // Given
            DeadLetterProperties dl = new DeadLetterProperties();

            // When
            dl.setEnabled(false);

            // Then
            assertThat(dl.isEnabled()).isFalse();
        }

        @Test
        void shouldBeFalsePropertiesDeadLetter() {
            // Given
            DeadLetterProperties dl = new DeadLetterProperties();
            dl.setEnabled(false);

            // When
            properties.setDeadLetter(dl);

            // Then
            assertThat(properties.getDeadLetter().isEnabled()).isFalse();
        }

        @Test
        void shouldReturnNotNullPropertiesDeadLetter() {
            // Then
            assertThat(properties.getDeadLetter()).isNotNull();
            assertThat(properties.getDeadLetter().isEnabled()).isTrue();
        }
    }
}
