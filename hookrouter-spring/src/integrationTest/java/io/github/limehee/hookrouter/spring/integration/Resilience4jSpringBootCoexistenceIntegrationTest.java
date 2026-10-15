package io.github.limehee.hookrouter.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.spring.config.WebhookAutoConfiguration;
import io.github.limehee.hookrouter.spring.dispatcher.WebhookDispatcher;
import io.github.limehee.hookrouter.spring.dispatcher.WebhookDispatcher.DispatchResult;
import io.github.limehee.hookrouter.spring.resilience.ResilienceResourceKey;
import io.github.limehee.hookrouter.spring.resilience.WebhookResilienceRegistries;
import io.github.limehee.hookrouter.spring.resilience.event.CircuitBreakerStateChangedEvent;
import io.github.limehee.hookrouter.spring.resilience.event.RateLimitDetectedEvent;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

class Resilience4jSpringBootCoexistenceIntegrationTest {

    private static final String APP_INSTANCE = "app";
    private static final String WEBHOOK_URL = "https://hooks.slack.com/integration";
    private static final String WEBHOOK_RESILIENCE_KEY = ResilienceResourceKey.of("slack", "alerts");

    private static final String[] HOOKROUTER_PROPERTIES = {
        "hookrouter.default-mappings[0].platform=slack",
        "hookrouter.default-mappings[0].webhook=alerts",
        "hookrouter.platforms.slack.endpoints.alerts.url=" + WEBHOOK_URL,
        "hookrouter.bulkhead.max-concurrent-calls=3",
        "hookrouter.rate-limiter.limit-for-period=50",
        "hookrouter.retry.max-attempts=2",
        "hookrouter.circuit-breaker.failure-threshold=4",
        "hookrouter.timeout.duration=6500"
    };

    private static final String[] RESILIENCE4J_PROPERTIES = {
        "resilience4j.bulkhead.instances.app.max-concurrent-calls=1",
        "resilience4j.bulkhead.instances.app.max-wait-duration=0ms",
        "resilience4j.ratelimiter.instances.app.limit-for-period=2",
        "resilience4j.ratelimiter.instances.app.limit-refresh-period=1s",
        "resilience4j.ratelimiter.instances.app.timeout-duration=0ms",
        "resilience4j.retry.instances.app.max-attempts=4",
        "resilience4j.retry.instances.app.wait-duration=10ms",
        "resilience4j.retry.instances.app.retry-exceptions[0]=java.io.IOException",
        "resilience4j.circuitbreaker.instances.app.sliding-window-size=7",
        "resilience4j.timelimiter.instances.app.timeout-duration=1234ms"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            WebhookAutoConfiguration.class,
            BulkheadAutoConfiguration.class,
            RateLimiterAutoConfiguration.class,
            RetryAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class,
            TimeLimiterAutoConfiguration.class
        ))
        .withPropertyValues(HOOKROUTER_PROPERTIES)
        .withPropertyValues(RESILIENCE4J_PROPERTIES);

    private static DispatchResult dispatchOnce(ApplicationContext context) {
        return context.getBean(WebhookDispatcher.class).dispatch(
            Notification.of("integration.event", "general", "context"),
            RoutingTarget.of("slack", "alerts", WEBHOOK_URL),
            new SuccessfulSender(),
            Map.of("text", "integration")
        );
    }

    @Test
    void resilience4jRegistryBeansShouldBeDefinedByResilience4jAutoConfiguration() {
        Map<Class<?>, Class<?>> expectedOrigins = Map.of(
            BulkheadRegistry.class, BulkheadAutoConfiguration.class,
            RateLimiterRegistry.class, RateLimiterAutoConfiguration.class,
            RetryRegistry.class, RetryAutoConfiguration.class,
            CircuitBreakerRegistry.class, CircuitBreakerAutoConfiguration.class,
            TimeLimiterRegistry.class, TimeLimiterAutoConfiguration.class
        );

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            expectedOrigins.forEach((registryType, autoConfiguration) -> {
                assertThat(context).hasSingleBean(registryType);
                String beanName = context.getBeanNamesForType(registryType)[0];
                assertThat(context.getBeanFactory().getBeanDefinition(beanName).getFactoryBeanName())
                    .as("factory of %s", registryType.getSimpleName())
                    .isEqualTo(autoConfiguration.getName());
            });
        });
    }

    @Test
    void applicationBulkheadInstanceShouldUseResilience4jProperties() {
        contextRunner.run(context -> {
            BulkheadRegistry registry = context.getBean(BulkheadRegistry.class);

            assertThat(registry.bulkhead(APP_INSTANCE).getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(1);
        });
    }

    @Test
    void applicationRateLimiterInstanceShouldUseResilience4jProperties() {
        contextRunner.run(context -> {
            RateLimiterRegistry registry = context.getBean(RateLimiterRegistry.class);

            assertThat(registry.rateLimiter(APP_INSTANCE).getRateLimiterConfig().getLimitForPeriod()).isEqualTo(2);
        });
    }

    @Test
    void applicationRetryInstanceShouldUseResilience4jProperties() {
        contextRunner.run(context -> {
            RetryConfig config = context.getBean(RetryRegistry.class).retry(APP_INSTANCE).getRetryConfig();

            assertThat(config.getMaxAttempts()).isEqualTo(4);
            assertThat(config.getExceptionPredicate().test(new IOException("connection reset"))).isTrue();
        });
    }

    @Test
    void applicationCircuitBreakerInstanceShouldUseResilience4jProperties() {
        contextRunner.run(context -> {
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);

            assertThat(registry.circuitBreaker(APP_INSTANCE).getCircuitBreakerConfig().getSlidingWindowSize())
                .isEqualTo(7);
        });
    }

    @Test
    void applicationTimeLimiterInstanceShouldUseResilience4jProperties() {
        contextRunner.run(context -> {
            TimeLimiterRegistry registry = context.getBean(TimeLimiterRegistry.class);

            assertThat(registry.timeLimiter(APP_INSTANCE).getTimeLimiterConfig().getTimeoutDuration())
                .isEqualTo(Duration.ofMillis(1234));
        });
    }

    @Test
    void applicationCircuitBreakerTransitionShouldNotPublishHookrouterEvent() {
        contextRunner
            .withBean(CircuitBreakerStateChangedEventCollector.class)
            .run(context -> {
                CircuitBreakerStateChangedEventCollector collector =
                    context.getBean(CircuitBreakerStateChangedEventCollector.class);

                context.getBean(CircuitBreakerRegistry.class).circuitBreaker(APP_INSTANCE).transitionToOpenState();

                assertThat(collector.events()).isEmpty();
            });
    }

    @Test
    void hookrouterDispatcherShouldApplyHookrouterPolicyInItsOwnRegistries() {
        contextRunner.run(context -> {
            assertThat(dispatchOnce(context).success()).isTrue();

            WebhookResilienceRegistries registries = context.getBean(WebhookResilienceRegistries.class);
            assertThat(registries.bulkheadRegistry().find(WEBHOOK_RESILIENCE_KEY)).hasValueSatisfying(
                bulkhead -> assertThat(bulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(3));
            assertThat(registries.rateLimiterRegistry().find(WEBHOOK_RESILIENCE_KEY)).hasValueSatisfying(
                rateLimiter -> assertThat(rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(50));
            assertThat(registries.retryRegistry().find(WEBHOOK_RESILIENCE_KEY)).hasValueSatisfying(
                retry -> assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(2));
            assertThat(registries.circuitBreakerRegistry().find(WEBHOOK_RESILIENCE_KEY)).hasValueSatisfying(
                circuitBreaker -> assertThat(circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize())
                    .isEqualTo(4));
            assertThat(registries.timeLimiterRegistry().find(WEBHOOK_RESILIENCE_KEY)).hasValueSatisfying(
                timeLimiter -> assertThat(timeLimiter.getTimeLimiterConfig().getTimeoutDuration())
                    .isEqualTo(Duration.ofMillis(6500)));

            assertThat(context.getBean(BulkheadRegistry.class).find(WEBHOOK_RESILIENCE_KEY)).isEmpty();
            assertThat(context.getBean(RateLimiterRegistry.class).find(WEBHOOK_RESILIENCE_KEY)).isEmpty();
            assertThat(context.getBean(RetryRegistry.class).find(WEBHOOK_RESILIENCE_KEY)).isEmpty();
            assertThat(context.getBean(CircuitBreakerRegistry.class).find(WEBHOOK_RESILIENCE_KEY)).isEmpty();
            assertThat(context.getBean(TimeLimiterRegistry.class).find(WEBHOOK_RESILIENCE_KEY)).isEmpty();
        });
    }

    @Test
    void rateLimitCooldownShouldOnlyAdjustHookrouterRateLimiter() {
        contextRunner.run(context -> {
            context.publishEvent(RateLimitDetectedEvent.of(
                "slack", "alerts", WEBHOOK_URL, "integration.event", 5000L, "rate limited"));

            WebhookResilienceRegistries registries = context.getBean(WebhookResilienceRegistries.class);
            assertThat(registries.rateLimiterRegistry().find(WEBHOOK_RESILIENCE_KEY)).hasValueSatisfying(
                rateLimiter -> assertThat(rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(1));
            assertThat(context.getBean(RateLimiterRegistry.class).find(WEBHOOK_RESILIENCE_KEY)).isEmpty();
        });
    }

    @Test
    void hookrouterCircuitBreakerTransitionShouldStillPublishHookrouterEvent() {
        contextRunner
            .withBean(CircuitBreakerStateChangedEventCollector.class)
            .run(context -> {
                CircuitBreakerStateChangedEventCollector collector =
                    context.getBean(CircuitBreakerStateChangedEventCollector.class);

                context.getBean(WebhookResilienceRegistries.class).circuitBreakerRegistry()
                    .circuitBreaker(WEBHOOK_RESILIENCE_KEY).transitionToOpenState();

                assertThat(collector.events()).singleElement().satisfies(event -> {
                    assertThat(event.getWebhookKey()).isEqualTo(WEBHOOK_RESILIENCE_KEY);
                    assertThat(event.isCircuitOpened()).isTrue();
                });
            });
    }

    @Test
    void hookrouterShouldWorkWithoutResilience4jAutoConfiguration() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
            .withPropertyValues(HOOKROUTER_PROPERTIES)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(BulkheadRegistry.class);
                assertThat(context).doesNotHaveBean(RateLimiterRegistry.class);
                assertThat(context).doesNotHaveBean(RetryRegistry.class);
                assertThat(context).doesNotHaveBean(CircuitBreakerRegistry.class);
                assertThat(context).doesNotHaveBean(TimeLimiterRegistry.class);

                assertThat(dispatchOnce(context).success()).isTrue();

                assertThat(context.getBean(WebhookResilienceRegistries.class).bulkheadRegistry()
                    .find(WEBHOOK_RESILIENCE_KEY)).hasValueSatisfying(
                    bulkhead -> assertThat(bulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(3));
            });
    }

    static class CircuitBreakerStateChangedEventCollector
        implements ApplicationListener<CircuitBreakerStateChangedEvent> {

        private final List<CircuitBreakerStateChangedEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(CircuitBreakerStateChangedEvent event) {
            events.add(event);
        }

        List<CircuitBreakerStateChangedEvent> events() {
            return events;
        }
    }

    private static final class SuccessfulSender implements WebhookSender {

        @Override
        public String platform() {
            return "slack";
        }

        @Override
        public SendResult send(String webhookUrl, Object payload) {
            return SendResult.success(200);
        }
    }
}
