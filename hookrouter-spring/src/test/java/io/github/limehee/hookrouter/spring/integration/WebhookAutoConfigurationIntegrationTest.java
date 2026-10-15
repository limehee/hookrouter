package io.github.limehee.hookrouter.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.registry.FormatterRegistry;
import io.github.limehee.hookrouter.core.registry.NotificationTypeRegistry;
import io.github.limehee.hookrouter.spring.actuator.WebhookHealthIndicator;
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
import io.github.limehee.hookrouter.spring.resilience.ResilienceResourceKey;
import io.github.limehee.hookrouter.spring.resilience.WebhookResilienceRegistries;
import io.github.limehee.hookrouter.spring.resilience.event.CircuitBreakerEventListener;
import io.github.limehee.hookrouter.spring.routing.ConfigBasedRoutingPolicy;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.contributor.HealthContributorAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;

class WebhookAutoConfigurationIntegrationTest {

    static class NonEnumerableMapPropertySource extends PropertySource<Map<String, Object>> {

        NonEnumerableMapPropertySource(String name, Map<String, Object> source) {
            super(name, source);
        }

        @Override
        public Object getProperty(String name) {
            return source.get(name);
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class));

    private String[] baseProperties() {
        return new String[]{
            "hookrouter.default-mappings[0].platform=slack",
            "hookrouter.default-mappings[0].webhook=general-channel",
            "hookrouter.platforms.slack.endpoints.general-channel.url=https://hooks.slack.com/test"
        };
    }

    private String[] typeMappingOnlyProperties() {
        return new String[]{
            "hookrouter.type-mappings.demo.server.error[0].platform=slack",
            "hookrouter.type-mappings.demo.server.error[0].webhook=error-channel",
            "hookrouter.platforms.slack.endpoints.error-channel.url=https://hooks.slack.com/error"
        };
    }

    private String[] typeMappingBracketKeyProperties() {
        return new String[]{
            "hookrouter.type-mappings[demo.server.error][0].platform=slack",
            "hookrouter.type-mappings[demo.server.error][0].webhook=error-channel",
            "hookrouter.platforms.slack.endpoints.error-channel.url=https://hooks.slack.com/error"
        };
    }

    private String[] categoryMappingOnlyProperties() {
        return new String[]{
            "hookrouter.category-mappings.critical[0].platform=slack",
            "hookrouter.category-mappings.critical[0].webhook=critical-channel",
            "hookrouter.platforms.slack.endpoints.critical-channel.url=https://hooks.slack.com/critical"
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
        void shouldVerifyExpectedContextWhenOnlyTypeMappingsAreConfigured() {
            contextRunner
                .withPropertyValues(typeMappingOnlyProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(WebhookConfigProperties.class);
                    assertThat(context).hasSingleBean(NotificationListener.class);
                    assertThat(context).hasSingleBean(RoutingPolicy.class);
                });
        }

        @Test
        void shouldVerifyExpectedContextWhenTypeMappingsUseBracketKeyStyle() {
            contextRunner
                .withPropertyValues(typeMappingBracketKeyProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(WebhookConfigProperties.class);
                    assertThat(context).hasSingleBean(NotificationListener.class);
                    assertThat(context).hasSingleBean(RoutingPolicy.class);
                });
        }

        @Test
        void shouldVerifyExpectedContextWhenOnlyCategoryMappingsAreConfigured() {
            contextRunner
                .withPropertyValues(categoryMappingOnlyProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(WebhookConfigProperties.class);
                    assertThat(context).hasSingleBean(NotificationListener.class);
                    assertThat(context).hasSingleBean(RoutingPolicy.class);
                });
        }

        @Test
        void shouldVerifyExpectedContextWhenRoutingMappingsComeFromNonEnumerablePropertySource() {
            contextRunner
                .withInitializer(context -> {
                    Map<String, Object> properties = Map.of(
                        "hookrouter.type-mappings[demo.server.error][0].platform", "slack",
                        "hookrouter.type-mappings[demo.server.error][0].webhook", "error-channel",
                        "hookrouter.platforms.slack.endpoints.error-channel.url", "https://hooks.slack.com/error"
                    );
                    PropertySource<?> nonEnumerable = new NonEnumerableMapPropertySource("nonEnumerableMappings",
                        properties);
                    context.getEnvironment().getPropertySources().addFirst(nonEnumerable);
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(WebhookConfigProperties.class);
                    assertThat(context).hasSingleBean(NotificationListener.class);
                    assertThat(context).hasSingleBean(RoutingPolicy.class);
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
        void shouldKeepResilience4jRegistriesOutOfApplicationContext() {
            contextRunner
                .withPropertyValues(baseProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(WebhookResilienceRegistries.class);
                    assertThat(context).doesNotHaveBean(CircuitBreakerRegistry.class);
                    assertThat(context).doesNotHaveBean(RetryRegistry.class);
                    assertThat(context).doesNotHaveBean(TimeLimiterRegistry.class);
                    assertThat(context).doesNotHaveBean(RateLimiterRegistry.class);
                    assertThat(context).doesNotHaveBean(BulkheadRegistry.class);
                    assertThat(context).doesNotHaveBean(CircuitBreakerEventListener.class);
                });
        }

        @Test
        void shouldBuildHookrouterRegistriesFromHookrouterProperties() {
            contextRunner
                .withPropertyValues(baseProperties())
                .withPropertyValues(
                    "hookrouter.circuit-breaker.failure-threshold=4",
                    "hookrouter.retry.max-attempts=2",
                    "hookrouter.timeout.duration=6500",
                    "hookrouter.rate-limiter.limit-for-period=50",
                    "hookrouter.bulkhead.max-concurrent-calls=3"
                )
                .run(context -> {
                    WebhookResilienceRegistries registries = context.getBean(WebhookResilienceRegistries.class);

                    assertThat(registries.circuitBreakerRegistry().getDefaultConfig().getSlidingWindowSize())
                        .isEqualTo(4);
                    assertThat(registries.retryRegistry().getDefaultConfig().getMaxAttempts()).isEqualTo(2);
                    assertThat(registries.timeLimiterRegistry().getDefaultConfig().getTimeoutDuration())
                        .isEqualTo(Duration.ofMillis(6500));
                    assertThat(registries.rateLimiterRegistry().getDefaultConfig().getLimitForPeriod())
                        .isEqualTo(50);
                    assertThat(registries.bulkheadRegistry().getDefaultConfig().getMaxConcurrentCalls())
                        .isEqualTo(3);
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

        @Test
        void shouldBindHookrouterResilience4jMetrics() {
            contextRunner
                .withPropertyValues(baseProperties())
                .withUserConfiguration(MeterRegistryConfiguration.class)
                .run(context -> {
                    MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
                    String resilienceKey = ResilienceResourceKey.of("slack", "general-channel");

                    context.getBean(WebhookResilienceRegistries.class).bulkheadRegistry().bulkhead(resilienceKey);

                    assertThat(meterRegistry.find("resilience4j.bulkhead.max.allowed.concurrent.calls")
                        .tag("name", resilienceKey).gauge()).isNotNull();
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

    @Nested
    class HealthIndicatorTest {

        private final ApplicationContextRunner healthEndpointRunner = contextRunner
            .withConfiguration(AutoConfigurations.of(
                HealthContributorAutoConfiguration.class,
                HealthContributorRegistryAutoConfiguration.class,
                HealthEndpointAutoConfiguration.class
            ));

        @Test
        void shouldNotRegisterWebhookHealthIndicatorByDefault() {
            contextRunner
                .withPropertyValues(baseProperties())
                .run(context -> assertThat(context).doesNotHaveBean("webhookHealthIndicator"));
        }

        @Test
        void shouldNotRegisterWebhookHealthIndicatorWhenDisabled() {
            contextRunner
                .withPropertyValues(baseProperties())
                .withPropertyValues("management.health.webhook.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("webhookHealthIndicator"));
        }

        @Test
        void shouldRegisterWebhookHealthIndicatorWhenEnabled() {
            contextRunner
                .withPropertyValues(baseProperties())
                .withPropertyValues("management.health.webhook.enabled=true")
                .run(context -> {
                    assertThat(context).hasBean("webhookHealthIndicator");
                    HealthIndicator indicator = context.getBean("webhookHealthIndicator", HealthIndicator.class);
                    assertThat(indicator).isInstanceOf(WebhookHealthIndicator.class);
                    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

                    context.getBean(WebhookResilienceRegistries.class).circuitBreakerRegistry()
                        .circuitBreaker(ResilienceResourceKey.of("slack", "general-channel")).transitionToOpenState();

                    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
                });
        }

        @Test
        void shouldBackOffWhenUserDefinesWebhookHealthIndicator() {
            HealthIndicator userIndicator = () -> Health.up().withDetail("source", "user").build();

            contextRunner
                .withPropertyValues(baseProperties())
                .withPropertyValues("management.health.webhook.enabled=true")
                .withBean("webhookHealthIndicator", HealthIndicator.class, () -> userIndicator)
                .run(context -> assertThat(context.getBean("webhookHealthIndicator")).isSameAs(userIndicator));
        }

        @Test
        void shouldExposeWebhookComponentInHealthEndpoint() {
            healthEndpointRunner
                .withPropertyValues(baseProperties())
                .withPropertyValues(
                    "hookrouter.platforms.slack.endpoints.ops-channel.url=https://hooks.slack.com/ops",
                    "management.health.webhook.enabled=true"
                )
                .run(context -> {
                    HealthEndpoint healthEndpoint = context.getBean(HealthEndpoint.class);
                    CircuitBreakerRegistry registry = context.getBean(WebhookResilienceRegistries.class)
                        .circuitBreakerRegistry();

                    registry.circuitBreaker(ResilienceResourceKey.of("slack", "general-channel"))
                        .transitionToOpenState();

                    CompositeHealthDescriptor partiallyOpen = (CompositeHealthDescriptor) healthEndpoint.health();
                    assertThat(partiallyOpen.getComponents().get("webhook").getStatus())
                        .isEqualTo(new Status("DEGRADED"));
                    assertThat(partiallyOpen.getStatus()).isEqualTo(Status.UP);

                    registry.circuitBreaker(ResilienceResourceKey.of("slack", "ops-channel")).transitionToOpenState();

                    CompositeHealthDescriptor allOpen = (CompositeHealthDescriptor) healthEndpoint.health();
                    assertThat(allOpen.getComponents().get("webhook").getStatus()).isEqualTo(Status.DOWN);
                    assertThat(allOpen.getStatus()).isEqualTo(Status.DOWN);
                });
        }
    }
}
