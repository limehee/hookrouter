package io.github.limehee.hookrouter.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.spring.async.WebhookAsyncConfig;
import io.github.limehee.hookrouter.spring.config.WebhookAutoConfiguration;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.publisher.NotificationPublisher;
import io.github.limehee.hookrouter.spring.resilience.ResilienceResourceKey;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class WebhookPipelineIntegrationTest {

    private String[] baseProperties() {
        return new String[]{
            "hookrouter.default-mappings[0].platform=slack",
            "hookrouter.default-mappings[0].webhook=test-channel",
            "hookrouter.platforms.slack.endpoints.test-channel.url=https://hooks.slack.com/test",
            "hookrouter.retry.enabled=true",
            "hookrouter.retry.max-attempts=3",
            "hookrouter.retry.initial-delay=10",
            "hookrouter.retry.multiplier=1.0",
            "hookrouter.circuit-breaker.enabled=true",
            "hookrouter.circuit-breaker.failure-threshold=3",
            "hookrouter.circuit-breaker.wait-duration=60000",
            "hookrouter.timeout.enabled=false",
            "hookrouter.rate-limiter.enabled=false",
            "hookrouter.bulkhead.enabled=false"
        };
    }

    private Notification<TestContext> createNotification() {
        return Notification.of("test.notification", "general", new TestContext("test-data"));
    }

    record TestContext(String data) {

    }

    @Configuration(proxyBeanMethods = false)
    static class BaseTestConfig {

        @Bean
        NotificationTypeDefinition testNotificationTypeDefinition() {
            return NotificationTypeDefinition.builder()
                .typeId("test.notification")
                .title("Test notification")
                .defaultMessage("Test default message")
                .category("general")
                .build();
        }

        @Bean
        WebhookFormatter<TestContext, Map<String, Object>> testSlackFormatter() {
            return new WebhookFormatter<>() {
                @Override
                public FormatterKey key() {
                    return FormatterKey.of("slack", "test.notification");
                }

                @Override
                public Class<TestContext> contextClass() {
                    return TestContext.class;
                }

                @Override
                public Map<String, Object> format(Notification<TestContext> notification) {
                    return Map.of("text", "Test message: " + notification.getContext().data());
                }
            };
        }
    }

    @Nested
    class SuccessfulSendTest {

        @Test
        void shouldContainExpectedSentUrls() {
            // Given
            List<String> sentUrls = new CopyOnWriteArrayList<>();
            List<Object> sentPayloads = new CopyOnWriteArrayList<>();

            ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
                .withUserConfiguration(WebhookAsyncConfig.class, BaseTestConfig.class)
                .withBean(WebhookSender.class, () -> new WebhookSender() {
                    @Override
                    public String platform() {
                        return "slack";
                    }

                    @Override
                    public SendResult send(String webhookUrl, Object payload) {
                        sentUrls.add(webhookUrl);
                        sentPayloads.add(payload);
                        return SendResult.success(200);
                    }
                })
                .withPropertyValues(baseProperties());

            // When & Then
            contextRunner.run(context -> {
                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
                publisher.publish(createNotification());

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertThat(sentUrls).contains("https://hooks.slack.com/test");
                    assertThat(sentPayloads).isNotEmpty();
                });
            });
        }

        @Test
        void shouldMatchExpectedSentPayloads() {
            // Given
            List<Object> sentPayloads = new CopyOnWriteArrayList<>();

            ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
                .withUserConfiguration(WebhookAsyncConfig.class, BaseTestConfig.class)
                .withBean(WebhookSender.class, () -> new WebhookSender() {
                    @Override
                    public String platform() {
                        return "slack";
                    }

                    @Override
                    public SendResult send(String webhookUrl, Object payload) {
                        sentPayloads.add(payload);
                        return SendResult.success(200);
                    }
                })
                .withPropertyValues(baseProperties());

            // When & Then
            contextRunner.run(context -> {
                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
                publisher.publish(createNotification());

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertThat(sentPayloads).isNotEmpty();

                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) sentPayloads.get(0);
                    assertThat(payload).containsKey("text");
                    assertThat(payload.get("text")).isEqualTo("Test message: test-data");
                });
            });
        }
    }

    @Nested
    class RetryAndDeadLetterTest {

        @Test
        void shouldMatchExpectedAttemptCount() {

            AtomicInteger attemptCount = new AtomicInteger(0);

            ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
                .withUserConfiguration(WebhookAsyncConfig.class, BaseTestConfig.class)
                .withBean(WebhookSender.class, () -> new WebhookSender() {
                    @Override
                    public String platform() {
                        return "slack";
                    }

                    @Override
                    public SendResult send(String webhookUrl, Object payload) {
                        int attempt = attemptCount.incrementAndGet();
                        if (attempt < 3) {
                            return SendResult.failure(503, "Service Unavailable", true);
                        }
                        return SendResult.success(200);
                    }
                })
                .withPropertyValues(baseProperties());

            // When & Then
            contextRunner.run(context -> {
                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
                publisher.publish(createNotification());

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(attemptCount.get()).isEqualTo(3)
                );
            });
        }

        @Test
        void shouldMatchExpectedAttemptCountWhenConfiguration() {

            AtomicInteger attemptCount = new AtomicInteger(0);
            List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();

            ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
                .withUserConfiguration(WebhookAsyncConfig.class, BaseTestConfig.class)
                .withBean(WebhookSender.class, () -> new WebhookSender() {
                    @Override
                    public String platform() {
                        return "slack";
                    }

                    @Override
                    public SendResult send(String webhookUrl, Object payload) {
                        attemptCount.incrementAndGet();
                        return SendResult.failure(503, "Service Unavailable", true);
                    }
                })
                .withBean(DeadLetterHandler.class, () -> deadLetters::add)
                .withPropertyValues(baseProperties());

            // When & Then
            contextRunner.run(context -> {
                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
                publisher.publish(createNotification());

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {

                    assertThat(attemptCount.get()).isEqualTo(4);

                    assertThat(deadLetters).hasSize(1);
                    DeadLetter deadLetter = deadLetters.get(0);
                    assertThat(deadLetter.platform()).isEqualTo("slack");
                    assertThat(deadLetter.webhookKey()).isEqualTo("test-channel");
                });
            });
        }

        @Test
        void shouldMatchExpectedAttemptCountWhenConfigurationAndHasSize() {

            AtomicInteger attemptCount = new AtomicInteger(0);
            List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();

            ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
                .withUserConfiguration(WebhookAsyncConfig.class, BaseTestConfig.class)
                .withBean(WebhookSender.class, () -> new WebhookSender() {
                    @Override
                    public String platform() {
                        return "slack";
                    }

                    @Override
                    public SendResult send(String webhookUrl, Object payload) {
                        attemptCount.incrementAndGet();
                        return SendResult.failure(400, "Bad Request", false);
                    }
                })
                .withBean(DeadLetterHandler.class, () -> deadLetters::add)
                .withPropertyValues(baseProperties());

            // When & Then
            contextRunner.run(context -> {
                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
                publisher.publish(createNotification());

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {

                    assertThat(attemptCount.get()).isEqualTo(1);

                    assertThat(deadLetters).hasSize(1);
                });
            });
        }
    }

    @Nested
    class CircuitBreakerTest {

        @Test
        void shouldMatchExpectedCircuitBreakerState() {

            List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();

            ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
                .withUserConfiguration(WebhookAsyncConfig.class, BaseTestConfig.class)
                .withBean(WebhookSender.class, () -> new WebhookSender() {
                    @Override
                    public String platform() {
                        return "slack";
                    }

                    @Override
                    public SendResult send(String webhookUrl, Object payload) {
                        return SendResult.failure(500, "Internal Server Error", false);
                    }
                })
                .withBean(DeadLetterHandler.class, () -> deadLetters::add)
                .withPropertyValues(baseProperties());

            // When & Then
            contextRunner.run(context -> {
                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
                CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
                CircuitBreaker circuitBreaker = registry.circuitBreaker(
                    ResilienceResourceKey.of("slack", "test-channel")
                );
                circuitBreaker.reset();

                for (int i = 0; i < 5; i++) {
                    publisher.publish(createNotification());
                }

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN)
                );
            });
        }

        @Test
        void shouldMatchExpectedSendCount() {
            // Given
            AtomicInteger sendCount = new AtomicInteger(0);

            ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
                .withUserConfiguration(WebhookAsyncConfig.class, BaseTestConfig.class)
                .withBean(WebhookSender.class, () -> new WebhookSender() {
                    @Override
                    public String platform() {
                        return "slack";
                    }

                    @Override
                    public SendResult send(String webhookUrl, Object payload) {
                        sendCount.incrementAndGet();
                        return SendResult.success(200);
                    }
                })
                .withPropertyValues(baseProperties());

            // When & Then
            contextRunner.run(context -> {
                CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
                CircuitBreaker circuitBreaker = registry.circuitBreaker(
                    ResilienceResourceKey.of("slack", "test-channel")
                );
                circuitBreaker.reset();
                circuitBreaker.transitionToOpenState();

                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
                publisher.publish(createNotification());

                await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(sendCount.get()).isEqualTo(0)
                );
            });
        }
    }

    @Nested
    class ConcurrentPublishTest {

        @Test
        void shouldCompleteAsyncProcessing() {
            // Given
            List<Object> sentPayloads = Collections.synchronizedList(new ArrayList<>());

            ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class))
                .withUserConfiguration(WebhookAsyncConfig.class, BaseTestConfig.class)
                .withBean(WebhookSender.class, () -> new WebhookSender() {
                    @Override
                    public String platform() {
                        return "slack";
                    }

                    @Override
                    public SendResult send(String webhookUrl, Object payload) {
                        sentPayloads.add(payload);
                        return SendResult.success(200);
                    }
                })
                .withPropertyValues(baseProperties());

            // When & Then
            contextRunner.run(context -> {
                CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
                CircuitBreaker circuitBreaker = registry.circuitBreaker(
                    ResilienceResourceKey.of("slack", "test-channel")
                );
                circuitBreaker.reset();

                NotificationPublisher publisher = context.getBean(NotificationPublisher.class);

                int notificationCount = 10;
                for (int i = 0; i < notificationCount; i++) {
                    publisher.publish(createNotification());
                }

                await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(sentPayloads).hasSizeGreaterThanOrEqualTo(notificationCount)
                );
            });
        }
    }
}
