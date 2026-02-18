package io.github.limehee.hookrouter.spring.deadletter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.port.WebhookSender.SendResult;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import io.github.limehee.hookrouter.spring.metrics.WebhookMetrics;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeadLetterProcessorTest {

    private DeadLetterProcessor processor;

    @Mock
    private DeadLetterHandler deadLetterHandler;

    @Mock
    private WebhookMetrics metrics;

    @BeforeEach
    void setUp() {
        processor = new DeadLetterProcessor(deadLetterHandler, metrics);
    }

    private Notification<TestContext> createNotification(String typeId) {
        return Notification.of(typeId, "general", new TestContext("test-data"));
    }

    private RoutingTarget createRoutingTarget(String platform, String webhookKey, String webhookUrl) {
        return new RoutingTarget(platform, webhookKey, webhookUrl);
    }

    private record TestContext(String data) {

    }

    @Nested
    class ProcessRateLimitedTest {

        @Test
        void shouldMatchExpectedDeadLetterNotification() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            // When
            processor.processRateLimited(notification, target, payload);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.notification().getTypeId()).isEqualTo("test-type");
            assertThat(deadLetter.platform()).isEqualTo("slack");
            assertThat(deadLetter.webhookKey()).isEqualTo("slack-key");
            assertThat(deadLetter.webhookUrl()).isEqualTo("https://hooks.slack.com/test");
            assertThat(deadLetter.payload()).isEqualTo(payload);
            assertThat(deadLetter.reason()).isEqualTo(FailureReason.RATE_LIMITED);
            assertThat(deadLetter.errorMessage()).contains("Rate limit exceeded");
            assertThat(deadLetter.attemptCount()).isEqualTo(0);
        }

        @Test
        void shouldInvokeExpectedInteractions() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            // When
            processor.processRateLimited(notification, target, payload);

            // Then
            verify(metrics).recordDeadLetter("slack", "slack-key", "test-type", "RATE_LIMITED");
        }
    }

    @Nested
    class ProcessBulkheadFullTest {

        @Test
        void shouldMatchExpectedDeadLetterReason() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            // When
            processor.processBulkheadFull(notification, target, payload);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.reason()).isEqualTo(FailureReason.BULKHEAD_FULL);
            assertThat(deadLetter.errorMessage()).contains("Bulkhead full");
            assertThat(deadLetter.attemptCount()).isEqualTo(0);
        }

        @Test
        void shouldInvokeExpectedInteractionsUsingFactoryMethod() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            // When
            processor.processBulkheadFull(notification, target, payload);

            // Then
            verify(metrics).recordDeadLetter("slack", "slack-key", "test-type", "BULKHEAD_FULL");
        }
    }

    @Nested
    class ProcessSendFailureTest {

        @Test
        void shouldMatchExpectedDeadLetterReasonUsingFactoryMethod() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");
            SendResult result = SendResult.failure(503, "Service Unavailable", true);

            // When
            processor.processSendFailure(notification, target, payload, result, 3);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.reason()).isEqualTo(FailureReason.MAX_RETRIES_EXCEEDED);
            assertThat(deadLetter.errorMessage()).isEqualTo("Service Unavailable");
            assertThat(deadLetter.attemptCount()).isEqualTo(3);
        }

        @Test
        void shouldMatchExpectedDeadLetterReasonWhenFailureIsNonRetryable() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");
            SendResult result = SendResult.failure(400, "Bad Request", false);

            // When
            processor.processSendFailure(notification, target, payload, result, 1);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.reason()).isEqualTo(FailureReason.NON_RETRYABLE_ERROR);
            assertThat(deadLetter.errorMessage()).isEqualTo("Bad Request");
            assertThat(deadLetter.attemptCount()).isEqualTo(1);
        }

        @Test
        void shouldInvokeExpectedInteractionsUsingFactoryMethod() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");
            SendResult result = SendResult.failure(500, "Internal Server Error", true);

            // When
            processor.processSendFailure(notification, target, payload, result, 3);

            // Then
            verify(metrics).recordDeadLetter("slack", "slack-key", "test-type", "MAX_RETRIES_EXCEEDED");
        }
    }

    // === Helper Methods ===

    @Nested
    class ProcessExceptionTest {

        @Test
        void shouldMatchExpectedDeadLetterReasonUsingFactoryMethod() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");
            Exception exception = new RuntimeException("Connection timeout");

            // When
            processor.processException(notification, target, payload, exception);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.reason()).isEqualTo(FailureReason.EXCEPTION);
            assertThat(deadLetter.errorMessage()).isEqualTo("Connection timeout");
            assertThat(deadLetter.attemptCount()).isEqualTo(1);
        }

        @Test
        void shouldMatchExpectedDeadLetterPayload() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Exception exception = new RuntimeException("Formatter error");

            // When
            processor.processException(notification, target, null, exception);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.payload()).isEqualTo("payload_not_created");
        }

        @Test
        void shouldInvokeExpectedInteractionsUsingFactoryMethod() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");
            Exception exception = new RuntimeException("Connection timeout");

            // When
            processor.processException(notification, target, payload, exception);

            // Then
            verify(metrics).recordDeadLetter("slack", "slack-key", "test-type", "EXCEPTION");
        }
    }

    @Nested
    class ProcessFormatterNotFoundTest {

        @Test
        void shouldMatchExpectedDeadLetterNotificationWhenProcessFormatterNotFound() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");

            // When
            processor.processFormatterNotFound(notification, target);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.notification().getTypeId()).isEqualTo("test-type");
            assertThat(deadLetter.platform()).isEqualTo("slack");
            assertThat(deadLetter.webhookKey()).isEqualTo("slack-key");
            assertThat(deadLetter.reason()).isEqualTo(FailureReason.FORMATTER_NOT_FOUND);
            assertThat(deadLetter.errorMessage()).contains("No formatter found");
            assertThat(deadLetter.errorMessage()).contains("slack");
            assertThat(deadLetter.errorMessage()).contains("test-type");
            assertThat(deadLetter.payload()).isEqualTo("payload_not_created");
            assertThat(deadLetter.attemptCount()).isEqualTo(0);
        }

        @Test
        void shouldInvokeExpectedInteractionsWhenProcessFormatterNotFound() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");

            // When
            processor.processFormatterNotFound(notification, target);

            // Then
            verify(metrics).recordDeadLetter("slack", "slack-key", "test-type", "FORMATTER_NOT_FOUND");
        }
    }

    @Nested
    class ProcessPayloadCreationFailedTest {

        @Test
        void shouldMatchExpectedDeadLetterReasonWhenProcessPayloadCreationFailed() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            String errorMessage = "Formatter returned null payload";

            // When
            processor.processPayloadCreationFailed(notification, target, errorMessage);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.reason()).isEqualTo(FailureReason.PAYLOAD_CREATION_FAILED);
            assertThat(deadLetter.errorMessage()).isEqualTo(errorMessage);
            assertThat(deadLetter.payload()).isEqualTo("payload_not_created");
            assertThat(deadLetter.attemptCount()).isEqualTo(0);
        }

        @Test
        void shouldContainExpectedDeadLetterErrorMessage() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");

            // When
            processor.processPayloadCreationFailed(notification, target, null);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.errorMessage()).contains("Payload creation failed");
            assertThat(deadLetter.errorMessage()).contains("slack");
            assertThat(deadLetter.errorMessage()).contains("test-type");
        }

        @Test
        void shouldInvokeExpectedInteractionsWhenProcessPayloadCreationFailed() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");

            // When
            processor.processPayloadCreationFailed(notification, target, "error");

            // Then
            verify(metrics).recordDeadLetter("slack", "slack-key", "test-type", "PAYLOAD_CREATION_FAILED");
        }
    }

    @Nested
    class ProcessSenderNotFoundTest {

        @Test
        void shouldMatchExpectedDeadLetterReasonUsingFactoryMethod() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("unknown", "unknown-key", "https://unknown.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            // When
            processor.processSenderNotFound(notification, target, payload);

            // Then
            ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
            verify(deadLetterHandler).handle(captor.capture());

            DeadLetter deadLetter = captor.getValue();
            assertThat(deadLetter.reason()).isEqualTo(FailureReason.SENDER_NOT_FOUND);
            assertThat(deadLetter.errorMessage()).contains("No sender found");
            assertThat(deadLetter.errorMessage()).contains("unknown");
            assertThat(deadLetter.payload()).isEqualTo(payload);
            assertThat(deadLetter.attemptCount()).isEqualTo(0);
        }

        @Test
        void shouldInvokeExpectedInteractionsUsingFactoryMethod() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("unknown", "unknown-key", "https://unknown.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            // When
            processor.processSenderNotFound(notification, target, payload);

            // Then
            verify(metrics).recordDeadLetter("unknown", "unknown-key", "test-type", "SENDER_NOT_FOUND");
        }
    }

    @Nested
    class ExceptionHandlingTest {

        @Test
        void shouldInvokeExpectedInteractionsUsingFactoryMethod() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            doThrow(new RuntimeException("Handler error")).when(deadLetterHandler).handle(any());

            processor.processRateLimited(notification, target, payload);

            verify(deadLetterHandler).handle(any());
        }

        @Test
        void shouldInvokeExpectedInteractionsWhenDeadLetterHandlerFailsDuringRateLimit() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            doThrow(new RuntimeException("Handler error")).when(deadLetterHandler).handle(any());

            // When
            processor.processRateLimited(notification, target, payload);

            verify(metrics).recordDeadLetter("slack", "slack-key", "test-type", "RATE_LIMITED");
        }

        @Test
        void shouldInvokeExpectedInteractionsWhenDeadLetterHandlerFailsDuringBulkheadFull() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            doThrow(new RuntimeException("Handler error")).when(deadLetterHandler).handle(any());

            // When
            processor.processBulkheadFull(notification, target, payload);

            verify(metrics).recordDeadLetter("slack", "slack-key", "test-type", "BULKHEAD_FULL");
            verify(metrics).recordDeadLetterHandlerFailure("slack", "slack-key", "test-type");
        }

        @Test
        void shouldNotInvokeUnexpectedInteractions() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            // When
            processor.processRateLimited(notification, target, payload);

            verify(metrics).recordDeadLetter("slack", "slack-key", "test-type", "RATE_LIMITED");
            verify(metrics, never()).recordDeadLetterHandlerFailure(any(), any(), any());
        }

        @Test
        void shouldNotThrowWhenMetricsRecordingFails() {
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
            Map<String, Object> payload = Map.of("text", "Hello");

            doThrow(new RuntimeException("metric error")).when(metrics)
                .recordDeadLetter("slack", "slack-key", "test-type", "RATE_LIMITED");

            assertDoesNotThrow(() -> processor.processRateLimited(notification, target, payload));
            verify(deadLetterHandler).handle(any());
        }
    }
}
