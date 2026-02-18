package io.github.limehee.hookrouter.spring.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NoOpWebhookMetricsTest {

    @Nested
    class InstanceTest {

        @Test
        void shouldBeExpectedTypeNoOpWebhookMetricsINSTANCE() {
            // When & Then
            assertThat(NoOpWebhookMetrics.INSTANCE).isInstanceOf(NoOpWebhookMetrics.class);
            assertThat(NoOpWebhookMetrics.INSTANCE).isInstanceOf(WebhookMetrics.class);
        }
    }

    @Nested
    class RecordMethodsTest {

        @Test
        void shouldNotThrowExceptionDuringExecution() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordSendAttempt("slack", "hookrouter-key", "test-type")
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordSendSuccess() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordSendSuccess("slack", "hookrouter-key", "test-type",
                    Duration.ofMillis(100))
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordSendFailure() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordSendFailure("slack", "hookrouter-key", "test-type", "error",
                    Duration.ofMillis(100))
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordSendSkipped() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordSendSkipped("slack", "hookrouter-key", "test-type")
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordSendRateLimited() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordSendRateLimited("slack", "hookrouter-key", "test-type")
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordSendBulkheadFull() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordSendBulkheadFull("slack", "hookrouter-key", "test-type")
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordRetry() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordRetry("slack", "hookrouter-key", "test-type", 1)
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordDeadLetter() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordDeadLetter("slack", "hookrouter-key", "test-type", "RATE_LIMITED")
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordDeadLetterHandlerFailure() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordDeadLetterHandlerFailure("slack", "hookrouter-key", "test-type")
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordExternalRateLimitDetected() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordExternalRateLimitDetected("slack", "hookrouter-key", "test-type",
                    60000L)
            ).doesNotThrowAnyException();

            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordExternalRateLimitDetected("slack", "hookrouter-key", "test-type",
                    null)
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenRecordSendAttempt() {
            // When & Then
            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordSendAttempt(null, null, null)
            ).doesNotThrowAnyException();

            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordSendSuccess(null, null, null, null)
            ).doesNotThrowAnyException();

            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordDeadLetter(null, null, null, null)
            ).doesNotThrowAnyException();

            assertThatCode(() ->
                NoOpWebhookMetrics.INSTANCE.recordAsyncCallerRuns()
            ).doesNotThrowAnyException();
        }
    }
}
