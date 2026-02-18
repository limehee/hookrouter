package io.github.limehee.hookrouter.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.registry.FormatterRegistry;
import io.github.limehee.hookrouter.core.registry.NotificationTypeRegistry;
import io.github.limehee.hookrouter.spring.config.WebhookAutoConfiguration;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigResolver;
import io.github.limehee.hookrouter.spring.config.WebhookConfigValidationException;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterProcessor;
import io.github.limehee.hookrouter.spring.deadletter.LoggingDeadLetterHandler;
import io.github.limehee.hookrouter.spring.dispatcher.WebhookDispatcher;
import io.github.limehee.hookrouter.spring.listener.NotificationListener;
import io.github.limehee.hookrouter.spring.metrics.MicrometerWebhookMetrics;
import io.github.limehee.hookrouter.spring.metrics.WebhookMetrics;
import io.github.limehee.hookrouter.spring.publisher.NotificationPublisher;
import io.github.limehee.hookrouter.spring.resilience.event.CircuitBreakerEventListener;
import io.github.limehee.hookrouter.spring.routing.ConfigBasedRoutingPolicy;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class WebhookAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class));

    private String[] baseProperties() {
        return new String[]{
            "hookrouter.default-mappings[0].platform=slack",
            "hookrouter.default-mappings[0].webhook=general-channel",
            "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test"
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Nested
    class AutoConfigurationActivationTest {

        @Test
        void shouldVerifyExpectedContext() {
            contextRunner
                .withPropertyValues(baseProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(WebhookConfigProperties.class);
                    assertThat(context).hasSingleBean(NotificationPublisher.class);
                    assertThat(context).hasSingleBean(NotificationListener.class);
                    assertThat(context).hasSingleBean(RoutingPolicy.class);
                    assertThat(context).hasSingleBean(FormatterRegistry.class);
                    assertThat(context).hasSingleBean(NotificationTypeRegistry.class);
                });
        }

        @Test
        void shouldVerifyExpectedContextWhenApplicationContextStarts() {
            contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WebhookConfigProperties.class);
                    assertThat(context).doesNotHaveBean(NotificationListener.class);
                });
        }
    }

    @Nested
    class Resilience4jRegistryBeanTest {

        @Test
        void shouldVerifyExpectedContextWhenPropertyOverridesAreApplied() {
            contextRunner
                .withPropertyValues(baseProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
                    assertThat(context).hasSingleBean(RetryRegistry.class);
                    assertThat(context).hasSingleBean(TimeLimiterRegistry.class);
                    assertThat(context).hasSingleBean(RateLimiterRegistry.class);
                    assertThat(context).hasSingleBean(BulkheadRegistry.class);
                    assertThat(context).hasSingleBean(CircuitBreakerEventListener.class);
                });
        }

        @Test
        void shouldMatchExpectedPropertiesCircuitBreaker() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.circuit-breaker.enabled=true",
                    "hookrouter.circuit-breaker.failure-threshold=10",
                    "hookrouter.circuit-breaker.wait-duration=30000"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);
                    assertThat(properties.getCircuitBreaker().isEnabled()).isTrue();
                    assertThat(properties.getCircuitBreaker().getFailureThreshold()).isEqualTo(10);
                    assertThat(properties.getCircuitBreaker().getWaitDuration()).isEqualTo(30000);
                });
        }

        @Test
        void shouldMatchExpectedPropertiesRetry() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.retry.enabled=true",
                    "hookrouter.retry.max-attempts=5",
                    "hookrouter.retry.initial-delay=2000",
                    "hookrouter.retry.multiplier=3.0"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);
                    assertThat(properties.getRetry().isEnabled()).isTrue();
                    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
                    assertThat(properties.getRetry().getInitialDelay()).isEqualTo(2000);
                    assertThat(properties.getRetry().getMultiplier()).isEqualTo(3.0);
                });
        }

        @Test
        void shouldMatchExpectedPropertiesTimeout() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.timeout.enabled=true",
                    "hookrouter.timeout.duration=15000"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);
                    assertThat(properties.getTimeout().isEnabled()).isTrue();
                    assertThat(properties.getTimeout().getDuration()).isEqualTo(15000);
                });
        }

        @Test
        void shouldMatchExpectedPropertiesRateLimiter() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.rate-limiter.enabled=true",
                    "hookrouter.rate-limiter.limit-for-period=100",
                    "hookrouter.rate-limiter.limit-refresh-period=5000"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);
                    assertThat(properties.getRateLimiter().isEnabled()).isTrue();
                    assertThat(properties.getRateLimiter().getLimitForPeriod()).isEqualTo(100);
                    assertThat(properties.getRateLimiter().getLimitRefreshPeriod()).isEqualTo(5000);
                });
        }

        @Test
        void shouldMatchExpectedPropertiesBulkhead() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.bulkhead.enabled=true",
                    "hookrouter.async.max-pool-size=50",
                    "hookrouter.bulkhead.max-concurrent-calls=50",
                    "hookrouter.bulkhead.max-wait-duration=1000"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);
                    assertThat(properties.getBulkhead().isEnabled()).isTrue();
                    assertThat(properties.getBulkhead().getMaxConcurrentCalls()).isEqualTo(50);
                    assertThat(properties.getBulkhead().getMaxWaitDuration()).isEqualTo(1000);
                });
        }
    }

    @Nested
    class ConfigValidationTest {

        @Test
        void shouldBeExpectedTypeContext() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.retry.max-attempts=0"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                        .isInstanceOf(BeanCreationException.class)
                        .rootCause()
                        .isInstanceOf(WebhookConfigValidationException.class);
                });
        }

        @Test
        void shouldBeExpectedTypeContextWhenPropertyValuesHasUrlLikeValue() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.timeout.duration=0"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                        .isInstanceOf(BeanCreationException.class)
                        .rootCause()
                        .isInstanceOf(WebhookConfigValidationException.class);
                });
        }

        @Test
        void shouldBeExpectedTypeContextWhenPropertyValuesHasUrlLikeValueAndHasFailed() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.circuit-breaker.wait-duration=-1000"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                        .isInstanceOf(BeanCreationException.class)
                        .rootCause()
                        .isInstanceOf(WebhookConfigValidationException.class);
                });
        }
    }

    @Nested
    class DefaultBeanCreationTest {

        @Test
        void shouldBeExpectedTypeContextWhenPropertyOverridesAreApplied() {
            contextRunner
                .withPropertyValues(baseProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(RoutingPolicy.class);
                    RoutingPolicy policy = context.getBean(RoutingPolicy.class);
                    assertThat(policy).isInstanceOf(ConfigBasedRoutingPolicy.class);
                });
        }

        @Test
        void shouldBeExpectedTypeContextWhenPropertyOverridesAreAppliedAndHasSingleBean() {
            contextRunner
                .withPropertyValues(baseProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(DeadLetterHandler.class);
                    assertThat(context.getBean(DeadLetterHandler.class))
                        .isInstanceOf(LoggingDeadLetterHandler.class);
                });
        }

        @Test
        void shouldVerifyExpectedContextWhenPropertyValuesHasUrlLikeValue() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.dead-letter.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DeadLetterHandler.class);
                });
        }

        @Test
        void shouldVerifyExpectedContextWhenPropertyValuesHasUrlLikeValueAndDoesNotHaveBean() {

            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general-channel",
                    "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test",
                    "hookrouter.dead-letter.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DeadLetterHandler.class);
                    assertThat(context).hasSingleBean(DeadLetterProcessor.class);
                    assertThat(context).hasSingleBean(WebhookDispatcher.class);
                });
        }
    }

    @Nested
    class PerWebhookConfigurationTest {

        @Test
        void shouldVerifyExpectedContextWhenPropertyOverridesAreAppliedAndHasSingleBean() {
            contextRunner
                .withPropertyValues(baseProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(WebhookConfigResolver.class);
                });
        }

        @Test
        void shouldReturnNotNullEndpointConfig() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=error-channel",
                    "hookrouter.platforms.slack.endpoints.error-channel.url=https://hooks.slack.com/error",
                    "hookrouter.platforms.slack.endpoints.error-channel.retry.max-attempts=10",
                    "hookrouter.platforms.slack.endpoints.error-channel.timeout.duration=30000"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);
                    var endpointConfig = properties.getPlatforms().get("slack").getEndpoints().get("error-channel");

                    assertThat(endpointConfig).isNotNull();
                    assertThat(endpointConfig.getUrl()).isEqualTo("https://hooks.slack.com/error");
                    assertThat(endpointConfig.getRetry()).isNotNull();
                    assertThat(endpointConfig.getRetry().getMaxAttempts()).isEqualTo(10);
                    assertThat(endpointConfig.getTimeout()).isNotNull();
                    assertThat(endpointConfig.getTimeout().getDuration()).isEqualTo(30000L);
                });
        }

        @Test
        void shouldMatchExpectedRetryPropsMaxAttempts() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=error-channel",
                    "hookrouter.retry.max-attempts=3",
                    "hookrouter.retry.initial-delay=1000",
                    "hookrouter.platforms.slack.endpoints.error-channel.url=https://hooks.slack.com/error",
                    "hookrouter.platforms.slack.endpoints.error-channel.retry.max-attempts=10"
                )
                .run(context -> {
                    WebhookConfigResolver resolver = context.getBean(WebhookConfigResolver.class);

                    var retryProps = resolver.resolveRetryProperties("slack", "error-channel");
                    assertThat(retryProps.getMaxAttempts()).isEqualTo(10);
                    assertThat(retryProps.getInitialDelay()).isEqualTo(1000);

                    var defaultRetryProps = resolver.resolveRetryProperties("slack", "nonexistent");
                    assertThat(defaultRetryProps.getMaxAttempts()).isEqualTo(3);
                    assertThat(defaultRetryProps.getInitialDelay()).isEqualTo(1000);
                });
        }

        @Test
        void shouldMatchExpectedUrl() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=channel",
                    "hookrouter.platforms.slack.endpoints.channel.url=https://hooks.slack.com/channel"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);
                    String url = properties.getWebhookUrl("slack", "channel");

                    assertThat(url).isEqualTo("https://hooks.slack.com/channel");
                });
        }
    }

    @Nested
    class MetricsIntegrationTest {

        @Test
        void shouldVerifyExpectedContextWhenPropertyOverridesAreAppliedAndDoesNotHaveBean() {

            contextRunner
                .withPropertyValues(baseProperties())
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WebhookMetrics.class);
                });
        }

        @Test
        void shouldBeExpectedTypeContextWhenPropertyOverridesAreAppliedAndHasSingleBean() {
            contextRunner
                .withPropertyValues(baseProperties())
                .withUserConfiguration(MeterRegistryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(WebhookMetrics.class);
                    assertThat(context.getBean(WebhookMetrics.class))
                        .isInstanceOf(MicrometerWebhookMetrics.class);
                });
        }

        @Test
        void shouldVerifyExpectedContextWhenPropertyOverridesAreAppliedAndHasSingleBean() {

            contextRunner
                .withPropertyValues(baseProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(WebhookDispatcher.class);
                    assertThat(context).hasSingleBean(DeadLetterProcessor.class);
                });
        }
    }

    @Nested
    class PlatformConfigurationTest {

        @Test
        void shouldMatchExpectedPropertiesPlatforms() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general",
                    "hookrouter.platforms.slack.endpoints.general.url=https://hooks.slack.com/general",
                    "hookrouter.platforms.slack.endpoints.error.url=https://hooks.slack.com/error",
                    "hookrouter.platforms.discord.endpoints.general.url=https://discord.com/general"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);

                    assertThat(properties.getPlatforms()).hasSize(2);
                    assertThat(properties.getWebhookUrl("slack", "general"))
                        .isEqualTo("https://hooks.slack.com/general");
                    assertThat(properties.getWebhookUrl("slack", "error"))
                        .isEqualTo("https://hooks.slack.com/error");
                    assertThat(properties.getWebhookUrl("discord", "general"))
                        .isEqualTo("https://discord.com/general");
                });
        }

        @Test
        void shouldMatchExpectedPropertiesCategoryMappings() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general",
                    "hookrouter.category-mappings.ERROR[0].platform=slack",
                    "hookrouter.category-mappings.ERROR[0].webhook=error",
                    "hookrouter.platforms.slack.endpoints.general.url=https://hooks.slack.com/general",
                    "hookrouter.platforms.slack.endpoints.error.url=https://hooks.slack.com/error"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);

                    assertThat(properties.getCategoryMappings()).containsKey("ERROR");
                    assertThat(properties.getCategoryMappings().get("ERROR")).hasSize(1);
                    assertThat(properties.getCategoryMappings().get("ERROR").get(0).getPlatform())
                        .isEqualTo("slack");
                    assertThat(properties.getCategoryMappings().get("ERROR").get(0).getWebhook())
                        .isEqualTo("error");
                });
        }

        @Test
        void shouldMatchExpectedPropertiesTypeMappings() {
            contextRunner
                .withPropertyValues(
                    "hookrouter.default-mappings[0].platform=slack",
                    "hookrouter.default-mappings[0].webhook=general",
                    "hookrouter.type-mappings.demo.server.error[0].platform=slack",
                    "hookrouter.type-mappings.demo.server.error[0].webhook=error",
                    "hookrouter.platforms.slack.endpoints.general.url=https://hooks.slack.com/general",
                    "hookrouter.platforms.slack.endpoints.error.url=https://hooks.slack.com/error"
                )
                .run(context -> {
                    WebhookConfigProperties properties = context.getBean(WebhookConfigProperties.class);

                    assertThat(properties.getTypeMappings()).containsKey("demo.server.error");
                    assertThat(properties.getTypeMappings().get("demo.server.error")).hasSize(1);
                    assertThat(properties.getTypeMappings().get("demo.server.error").get(0).getPlatform())
                        .isEqualTo("slack");
                });
        }
    }
}
