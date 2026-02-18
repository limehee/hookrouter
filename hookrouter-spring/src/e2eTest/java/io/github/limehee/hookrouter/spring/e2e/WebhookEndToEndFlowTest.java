package io.github.limehee.hookrouter.spring.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.spring.config.WebhookAutoConfiguration;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterReprocessor;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.DeadLetterStatus;
import io.github.limehee.hookrouter.spring.deadletter.InMemoryDeadLetterStore;
import io.github.limehee.hookrouter.spring.publisher.NotificationPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

class WebhookEndToEndFlowTest {

    private static ConfigurableApplicationContext startContext(Map<String, Object> properties) {
        SpringApplication application = new SpringApplication(E2eApp.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(properties);
        return application.run();
    }

    private static Map<String, Object> reprocessProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hookrouter.default-mappings[0].platform", "slack");
        properties.put("hookrouter.default-mappings[0].webhook", "alerts");
        properties.put("hookrouter.platforms.slack.endpoints.alerts.url", "https://hooks.slack.com/e2e");
        properties.put("hookrouter.async.core-pool-size", "1");
        properties.put("hookrouter.async.max-pool-size", "1");
        properties.put("hookrouter.async.queue-capacity", "10");
        properties.put("hookrouter.dead-letter.enabled", "true");
        properties.put("hookrouter.retry.enabled", "true");
        properties.put("hookrouter.retry.max-attempts", "1");
        properties.put("hookrouter.retry.initial-delay", "1");
        properties.put("hookrouter.retry.max-delay", "10");
        properties.put("hookrouter.retry.multiplier", "1.0");
        properties.put("hookrouter.retry.jitter-factor", "0.0");
        properties.put("hookrouter.timeout.enabled", "false");
        properties.put("hookrouter.rate-limiter.enabled", "false");
        properties.put("hookrouter.bulkhead.enabled", "false");
        properties.put("hookrouter.circuit-breaker.enabled", "false");
        return properties;
    }

    private static Map<String, Object> circuitBreakerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hookrouter.default-mappings[0].platform", "slack");
        properties.put("hookrouter.default-mappings[0].webhook", "alerts");
        properties.put("hookrouter.platforms.slack.endpoints.alerts.url", "https://hooks.slack.com/e2e");
        properties.put("hookrouter.async.core-pool-size", "1");
        properties.put("hookrouter.async.max-pool-size", "1");
        properties.put("hookrouter.async.queue-capacity", "10");
        properties.put("hookrouter.dead-letter.enabled", "true");
        properties.put("hookrouter.retry.enabled", "false");
        properties.put("hookrouter.timeout.enabled", "false");
        properties.put("hookrouter.rate-limiter.enabled", "false");
        properties.put("hookrouter.bulkhead.enabled", "false");
        properties.put("hookrouter.circuit-breaker.enabled", "true");
        properties.put("hookrouter.circuit-breaker.failure-threshold", "2");
        properties.put("hookrouter.circuit-breaker.failure-rate-threshold", "50");
        properties.put("hookrouter.circuit-breaker.success-threshold", "1");
        properties.put("hookrouter.circuit-breaker.wait-duration", "600000");
        return properties;
    }

    private static Notification<E2eContext> notification(String value) {
        return Notification.of("e2e.event", "general", new E2eContext(value));
    }

    private static double counter(MeterRegistry meterRegistry, String metricName, String... tags) {
        Counter counter = meterRegistry.find(metricName).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void deadLetterShouldBeStoredAndReprocessedSuccessfully() {
        try (ConfigurableApplicationContext context = startContext(reprocessProperties())) {
            StatefulWebhookSender sender = context.getBean(StatefulWebhookSender.class);
            NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
            DeadLetterStore store = context.getBean(DeadLetterStore.class);
            DeadLetterReprocessor reprocessor = context.getBean(DeadLetterReprocessor.class);

            sender.setMode(SenderMode.RETRYABLE_FAILURE);
            publisher.publish(notification("needs-reprocess"));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(sender.sendCount()).isEqualTo(2);
                assertThat(store.countByStatus(DeadLetterStatus.PENDING)).isEqualTo(1);
            });

            List<DeadLetterStore.StoredDeadLetter> pending = store.findByStatus(DeadLetterStatus.PENDING);
            assertThat(pending).hasSize(1);

            sender.setMode(SenderMode.SUCCESS);
            DeadLetterReprocessor.ReprocessResult result = reprocessor.reprocessById(pending.get(0).id());
            assertThat(result.isSuccess()).isTrue();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(store.countByStatus(DeadLetterStatus.RESOLVED)).isEqualTo(1);
            });
        }
    }

    @Test
    void circuitBreakerShouldOpenAndSkipLaterRequests() {
        try (ConfigurableApplicationContext context = startContext(circuitBreakerProperties())) {
            StatefulWebhookSender sender = context.getBean(StatefulWebhookSender.class);
            NotificationPublisher publisher = context.getBean(NotificationPublisher.class);
            CircuitBreakerRegistry circuitBreakerRegistry = context.getBean(CircuitBreakerRegistry.class);
            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

            sender.setMode(SenderMode.NON_RETRYABLE_FAILURE);

            publisher.publish(notification("first"));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(sender.sendCount()).isEqualTo(1);
            });

            publisher.publish(notification("second"));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(sender.sendCount()).isEqualTo(2);
            });

            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("alerts");
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            });

            publisher.publish(notification("third"));
            await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                assertThat(sender.sendCount()).isEqualTo(2);
            });

            assertThat(counter(meterRegistry, "hookrouter.send.skipped",
                "platform", "slack",
                "webhookKey", "alerts",
                "typeId", "e2e.event"))
                .isGreaterThanOrEqualTo(1.0);
        }
    }

    private enum SenderMode {
        SUCCESS,
        RETRYABLE_FAILURE,
        NON_RETRYABLE_FAILURE
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration(WebhookAutoConfiguration.class)
    static class E2eApp {

        @Bean
        NotificationTypeDefinition e2eNotificationTypeDefinition() {
            return NotificationTypeDefinition.builder()
                .typeId("e2e.event")
                .title("E2E Event")
                .defaultMessage("E2E default message")
                .category("general")
                .build();
        }

        @Bean
        WebhookFormatter<E2eContext, Map<String, Object>> e2eFormatter() {
            return new WebhookFormatter<>() {
                @Override
                public FormatterKey key() {
                    return FormatterKey.of("slack", "e2e.event");
                }

                @Override
                public Class<E2eContext> contextClass() {
                    return E2eContext.class;
                }

                @Override
                public Map<String, Object> format(Notification<E2eContext> notification) {
                    return Map.of("text", "e2e:" + notification.getContext().value());
                }
            };
        }

        @Bean
        StatefulWebhookSender statefulWebhookSender() {
            return new StatefulWebhookSender();
        }

        @Bean
        InMemoryDeadLetterStore deadLetterStore() {
            return new InMemoryDeadLetterStore();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private record E2eContext(String value) {

    }

    static final class StatefulWebhookSender implements WebhookSender {

        private final AtomicReference<SenderMode> mode = new AtomicReference<>(SenderMode.SUCCESS);
        private final AtomicInteger sendCount = new AtomicInteger(0);

        @Override
        public String platform() {
            return "slack";
        }

        @Override
        public SendResult send(String webhookUrl, Object payload) {
            sendCount.incrementAndGet();
            return switch (mode.get()) {
                case SUCCESS -> SendResult.success(200);
                case RETRYABLE_FAILURE -> SendResult.failure(503, "temporary unavailable", true);
                case NON_RETRYABLE_FAILURE -> SendResult.failure(400, "bad request", false);
            };
        }

        int sendCount() {
            return sendCount.get();
        }

        void setMode(SenderMode senderMode) {
            this.mode.set(senderMode);
        }
    }
}
