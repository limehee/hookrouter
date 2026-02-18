package io.github.limehee.hookrouter.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.spring.config.WebhookAutoConfiguration;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import io.github.limehee.hookrouter.spring.publisher.NotificationPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class WebhookResilienceIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
        .withUserConfiguration(TestConfig.class)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    private static Notification<TestContext> testNotification(String value) {
        return Notification.of("integration.event", "general", new TestContext(value));
    }

    private static String[] retryEnabledProperties() {
        return new String[]{
            "hookrouter.default-mappings[0].platform=slack",
            "hookrouter.default-mappings[0].webhook=alerts",
            "hookrouter.platforms.slack.endpoints.alerts.url=https://hooks.slack.com/integration",
            "hookrouter.async.core-pool-size=1",
            "hookrouter.async.max-pool-size=1",
            "hookrouter.async.queue-capacity=10",
            "hookrouter.retry.enabled=true",
            "hookrouter.retry.max-attempts=3",
            "hookrouter.retry.initial-delay=1",
            "hookrouter.retry.max-delay=10",
            "hookrouter.retry.multiplier=1.0",
            "hookrouter.retry.jitter-factor=0.0",
            "hookrouter.timeout.enabled=false",
            "hookrouter.rate-limiter.enabled=false",
            "hookrouter.bulkhead.enabled=false",
            "hookrouter.circuit-breaker.enabled=false"
        };
    }

    private static String[] timeoutRetryProperties() {
        return new String[]{
            "hookrouter.default-mappings[0].platform=slack",
            "hookrouter.default-mappings[0].webhook=alerts",
            "hookrouter.platforms.slack.endpoints.alerts.url=https://hooks.slack.com/integration",
            "hookrouter.async.core-pool-size=1",
            "hookrouter.async.max-pool-size=1",
            "hookrouter.async.queue-capacity=10",
            "hookrouter.retry.enabled=true",
            "hookrouter.retry.max-attempts=1",
            "hookrouter.retry.initial-delay=1",
            "hookrouter.retry.max-delay=10",
            "hookrouter.retry.multiplier=1.0",
            "hookrouter.retry.jitter-factor=0.0",
            "hookrouter.timeout.enabled=true",
            "hookrouter.timeout.duration=20",
            "hookrouter.rate-limiter.enabled=false",
            "hookrouter.bulkhead.enabled=false",
            "hookrouter.circuit-breaker.enabled=false"
        };
    }

    private static String[] rateLimitCooldownProperties() {
        return new String[]{
            "hookrouter.default-mappings[0].platform=slack",
            "hookrouter.default-mappings[0].webhook=alerts",
            "hookrouter.platforms.slack.endpoints.alerts.url=https://hooks.slack.com/integration",
            "hookrouter.async.core-pool-size=1",
            "hookrouter.async.max-pool-size=1",
            "hookrouter.async.queue-capacity=10",
            "hookrouter.retry.enabled=false",
            "hookrouter.timeout.enabled=false",
            "hookrouter.rate-limiter.enabled=true",
            "hookrouter.rate-limiter.limit-for-period=10",
            "hookrouter.rate-limiter.limit-refresh-period=60000",
            "hookrouter.rate-limiter.timeout-duration=0",
            "hookrouter.bulkhead.enabled=false",
            "hookrouter.circuit-breaker.enabled=false"
        };
    }

    private static double counter(MeterRegistry meterRegistry, String metricName, String... tags) {
        Counter counter = meterRegistry.find(metricName).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void retryableFailureShouldBeRetriedUntilSuccess() {
        AtomicInteger sendCount = new AtomicInteger(0);
        List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();

        contextRunner
            .withBean(DeadLetterHandler.class, () -> deadLetters::add)
            .withBean(WebhookSender.class, () -> new WebhookSender() {
                @Override
                public String platform() {
                    return "slack";
                }

                @Override
                public SendResult send(String webhookUrl, Object payload) {
                    int current = sendCount.incrementAndGet();
                    if (current < 3) {
                        return SendResult.failure(503, "temporary failure", true);
                    }
                    return SendResult.success(200);
                }
            })
            .withPropertyValues(retryEnabledProperties())
            .run(context -> {
                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
                MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

                publisher.publish(testNotification("retry-success"));

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertThat(sendCount.get()).isEqualTo(3);
                    assertThat(deadLetters).isEmpty();
                });

                assertThat(counter(meterRegistry, "hookrouter.retry.total",
                    "platform", "slack",
                    "webhookKey", "alerts",
                    "typeId", "integration.event"))
                    .isGreaterThanOrEqualTo(2.0);

                assertThat(counter(meterRegistry, "hookrouter.send.success",
                    "platform", "slack",
                    "webhookKey", "alerts",
                    "typeId", "integration.event"))
                    .isEqualTo(1.0);
            });
    }

    @Test
    void timeoutShouldCreateDeadLetterAfterRetryExhaustion() {
        AtomicInteger sendCount = new AtomicInteger(0);
        List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();

        contextRunner
            .withBean(DeadLetterHandler.class, () -> deadLetters::add)
            .withBean(WebhookSender.class, () -> new WebhookSender() {
                @Override
                public String platform() {
                    return "slack";
                }

                @Override
                public SendResult send(String webhookUrl, Object payload) {
                    sendCount.incrementAndGet();
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return SendResult.success(200);
                }
            })
            .withPropertyValues(timeoutRetryProperties())
            .run(context -> {
                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);

                publisher.publish(testNotification("timeout"));

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertThat(deadLetters).hasSize(1);
                });

                DeadLetter deadLetter = deadLetters.getFirst();
                assertThat(deadLetter.reason()).isEqualTo(FailureReason.MAX_RETRIES_EXCEEDED);
                assertThat(deadLetter.attemptCount()).isEqualTo(2);
                assertThat(sendCount.get()).isEqualTo(2);
            });
    }

    @Test
    void rateLimitedResponseShouldTriggerCooldownAndBlockImmediateNextSend() {
        AtomicInteger sendCount = new AtomicInteger(0);
        List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();

        contextRunner
            .withBean(DeadLetterHandler.class, () -> deadLetters::add)
            .withBean(WebhookSender.class, () -> new WebhookSender() {
                @Override
                public String platform() {
                    return "slack";
                }

                @Override
                public SendResult send(String webhookUrl, Object payload) {
                    int current = sendCount.incrementAndGet();
                    if (current == 1) {
                        return SendResult.rateLimited("upstream rate limited", 1_000L);
                    }
                    return SendResult.success(200);
                }
            })
            .withPropertyValues(rateLimitCooldownProperties())
            .run(context -> {
                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
                MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

                publisher.publish(testNotification("first"));

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertThat(sendCount.get()).isEqualTo(1);
                    assertThat(deadLetters).hasSize(1);
                });

                publisher.publish(testNotification("second"));

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertThat(sendCount.get()).isEqualTo(1);
                    assertThat(deadLetters).hasSize(2);
                });

                assertThat(deadLetters.get(0).reason()).isEqualTo(FailureReason.MAX_RETRIES_EXCEEDED);
                assertThat(deadLetters.get(1).reason()).isEqualTo(FailureReason.RATE_LIMITED);

                assertThat(counter(meterRegistry, "hookrouter.external-rate-limit.detected",
                    "platform", "slack",
                    "webhookKey", "alerts",
                    "typeId", "integration.event"))
                    .isGreaterThanOrEqualTo(1.0);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        NotificationTypeDefinition integrationTypeDefinition() {
            return NotificationTypeDefinition.builder()
                .typeId("integration.event")
                .title("Integration Event")
                .defaultMessage("Integration message")
                .category("general")
                .build();
        }

        @Bean
        WebhookFormatter<TestContext, Map<String, Object>> integrationFormatter() {
            return new WebhookFormatter<>() {
                @Override
                public FormatterKey key() {
                    return FormatterKey.of("slack", "integration.event");
                }

                @Override
                public Class<TestContext> contextClass() {
                    return TestContext.class;
                }

                @Override
                public Map<String, Object> format(Notification<TestContext> notification) {
                    return Map.of("text", "integration:" + notification.getContext().value());
                }
            };
        }
    }

    private record TestContext(String value) {

    }
}
