package io.github.limehee.hookrouter.spring.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MicrometerWebhookMetricsTest {

    private MeterRegistry meterRegistry;
    private MicrometerWebhookMetrics webhookMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        webhookMetrics = new MicrometerWebhookMetrics(meterRegistry);
    }

    @Nested
    class RecordSendAttemptTest {

        @Test
        void shouldReturnNotNullCounter() {
            // When
            webhookMetrics.recordSendAttempt("slack", "test-channel", "ORDER_CREATED");

            // Then
            Counter counter = meterRegistry.find("hookrouter.send.total")
                .tag("platform", "slack")
                .tag("webhookKey", "test-channel")
                .tag("typeId", "ORDER_CREATED")
                .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void shouldReturnNotNullCounterWhenRecordSendAttempt() {
            // When
            webhookMetrics.recordSendAttempt("slack", "test-channel", "ORDER_CREATED");
            webhookMetrics.recordSendAttempt("slack", "test-channel", "ORDER_CREATED");
            webhookMetrics.recordSendAttempt("slack", "test-channel", "ORDER_CREATED");

            // Then
            Counter counter = meterRegistry.find("hookrouter.send.total")
                .tag("platform", "slack")
                .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        }
    }

    @Nested
    class RecordSendSuccessTest {

        @Test
        void shouldReturnNotNullCounterWhenRecordSendSuccess() {
            // When
            webhookMetrics.recordSendSuccess("slack", "test-channel", "ORDER_CREATED", Duration.ofMillis(150));

            // Then
            Counter counter = meterRegistry.find("hookrouter.send.success")
                .tag("platform", "slack")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);

            Timer timer = meterRegistry.find("hookrouter.send.duration")
                .tag("result", "success")
                .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    @Nested
    class RecordSendFailureTest {

        @Test
        void shouldReturnNotNullCounterWhenRecordSendFailure() {
            // When
            webhookMetrics.recordSendFailure("slack", "test-channel", "ORDER_CREATED", "timeout",
                Duration.ofMillis(5000));

            // Then
            Counter counter = meterRegistry.find("hookrouter.send.failure")
                .tag("platform", "slack")
                .tag("reason", "timeout")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);

            Timer timer = meterRegistry.find("hookrouter.send.duration")
                .tag("result", "failure")
                .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    @Nested
    class RecordSendSkippedTest {

        @Test
        void shouldReturnNotNullCounterWhenRecordSendSkipped() {
            // When
            webhookMetrics.recordSendSkipped("slack", "test-channel", "ORDER_CREATED");

            // Then
            Counter counter = meterRegistry.find("hookrouter.send.skipped")
                .tag("platform", "slack")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    class RecordSendRateLimitedTest {

        @Test
        void shouldReturnNotNullCounterWhenRecordSendRateLimited() {
            // When
            webhookMetrics.recordSendRateLimited("slack", "test-channel", "ORDER_CREATED");

            // Then
            Counter counter = meterRegistry.find("hookrouter.send.rate-limited")
                .tag("platform", "slack")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    class RecordSendBulkheadFullTest {

        @Test
        void shouldReturnNotNullCounterWhenRecordSendBulkheadFull() {
            // When
            webhookMetrics.recordSendBulkheadFull("slack", "test-channel", "ORDER_CREATED");

            // Then
            Counter counter = meterRegistry.find("hookrouter.send.bulkhead-full")
                .tag("platform", "slack")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    class RecordRetryTest {

        @Test
        void shouldReturnNotNullCounterWhenRecordRetry() {
            // When
            webhookMetrics.recordRetry("slack", "test-channel", "ORDER_CREATED", 1);
            webhookMetrics.recordRetry("slack", "test-channel", "ORDER_CREATED", 2);

            // Then
            Counter counter = meterRegistry.find("hookrouter.retry.total")
                .tag("platform", "slack")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(2.0);
        }
    }

    @Nested
    class RecordDeadLetterTest {

        @Test
        void shouldReturnNotNullCounterWhenRecordDeadLetter() {
            // When
            webhookMetrics.recordDeadLetter("slack", "test-channel", "ORDER_CREATED", "MAX_RETRIES_EXCEEDED");

            // Then
            Counter counter = meterRegistry.find("hookrouter.dead-letter.total")
                .tag("platform", "slack")
                .tag("reason", "MAX_RETRIES_EXCEEDED")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    class RecordDeadLetterHandlerFailureTest {

        @Test
        void shouldReturnNotNullCounterWhenRecordDeadLetterHandlerFailure() {
            // When
            webhookMetrics.recordDeadLetterHandlerFailure("slack", "test-channel", "ORDER_CREATED");

            // Then
            Counter counter = meterRegistry.find("hookrouter.dead-letter.handler-failure")
                .tag("platform", "slack")
                .tag("webhookKey", "test-channel")
                .tag("typeId", "ORDER_CREATED")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void shouldReturnNotNullCounterWhenRecordDeadLetterHandlerFailureAndIsNotNull() {
            // When
            webhookMetrics.recordDeadLetterHandlerFailure("slack", "test-channel", "ORDER_CREATED");
            webhookMetrics.recordDeadLetterHandlerFailure("slack", "test-channel", "ORDER_CREATED");

            // Then
            Counter counter = meterRegistry.find("hookrouter.dead-letter.handler-failure")
                .tag("platform", "slack")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(2.0);
        }
    }

    @Nested
    class RecordExternalRateLimitDetectedTest {

        @Test
        void shouldReturnNotNullCounterWhenRecordExternalRateLimitDetected() {
            // When
            webhookMetrics.recordExternalRateLimitDetected("slack", "test-channel", "ORDER_CREATED", 30000L);

            // Then
            Counter counter = meterRegistry.find("hookrouter.external-rate-limit.detected")
                .tag("platform", "slack")
                .tag("webhookKey", "test-channel")
                .tag("typeId", "ORDER_CREATED")
                .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void shouldReturnNotNullSummary() {
            // When
            webhookMetrics.recordExternalRateLimitDetected("slack", "test-channel", "ORDER_CREATED", 60000L);

            // Then
            DistributionSummary summary = meterRegistry.find("hookrouter.external-rate-limit.retry-after")
                .tag("platform", "slack")
                .tag("webhookKey", "test-channel")
                .summary();

            assertThat(summary).isNotNull();
            assertThat(summary.count()).isEqualTo(1);
            assertThat(summary.totalAmount()).isEqualTo(60000.0);
        }

        @Test
        void shouldReturnNotNullCounterWhenRecordExternalRateLimitDetectedAndIsNotNull() {
            // When
            webhookMetrics.recordExternalRateLimitDetected("slack", "test-channel", "ORDER_CREATED", null);

            // Then

            Counter counter = meterRegistry.find("hookrouter.external-rate-limit.detected")
                .tag("platform", "slack")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);

            DistributionSummary summary = meterRegistry.find("hookrouter.external-rate-limit.retry-after")
                .summary();
            assertThat(summary).isNull();
        }

        @Test
        void shouldReturnNotNullCounterWhenRecordExternalRateLimitDetectedAndIsNotNullWithRecordExternalRateLimitDetected() {
            // When
            webhookMetrics.recordExternalRateLimitDetected("slack", "test-channel", "ORDER_CREATED", 0L);
            webhookMetrics.recordExternalRateLimitDetected("slack", "test-channel", "ORDER_CREATED", -1L);

            // Then

            Counter counter = meterRegistry.find("hookrouter.external-rate-limit.detected")
                .tag("platform", "slack")
                .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(2.0);

            DistributionSummary summary = meterRegistry.find("hookrouter.external-rate-limit.retry-after")
                .summary();
            assertThat(summary).isNull();
        }
    }

    @Nested
    class TagVerificationTest {

        @Test
        void shouldReturnNotNullMeterRegistryFind() {
            // Given
            String platform = "discord";
            String webhookKey = "alert-channel";
            String typeId = "SYSTEM_ALERT";

            // When
            webhookMetrics.recordSendAttempt(platform, webhookKey, typeId);
            webhookMetrics.recordSendSuccess(platform, webhookKey, typeId, Duration.ofMillis(100));
            webhookMetrics.recordSendFailure(platform, webhookKey, typeId, "error", Duration.ofMillis(200));
            webhookMetrics.recordSendSkipped(platform, webhookKey, typeId);
            webhookMetrics.recordRetry(platform, webhookKey, typeId, 1);
            webhookMetrics.recordDeadLetter(platform, webhookKey, typeId, "CIRCUIT_OPEN");
            webhookMetrics.recordDeadLetterHandlerFailure(platform, webhookKey, typeId);
            webhookMetrics.recordExternalRateLimitDetected(platform, webhookKey, typeId, 30000L);

            // Then
            assertThat(meterRegistry.find("hookrouter.send.total")
                .tag("platform", platform)
                .tag("webhookKey", webhookKey)
                .tag("typeId", typeId)
                .counter()).isNotNull();

            assertThat(meterRegistry.find("hookrouter.send.success")
                .tag("platform", platform)
                .tag("webhookKey", webhookKey)
                .tag("typeId", typeId)
                .counter()).isNotNull();

            assertThat(meterRegistry.find("hookrouter.send.failure")
                .tag("platform", platform)
                .tag("webhookKey", webhookKey)
                .tag("typeId", typeId)
                .tag("reason", "error")
                .counter()).isNotNull();

            assertThat(meterRegistry.find("hookrouter.send.skipped")
                .tag("platform", platform)
                .tag("webhookKey", webhookKey)
                .tag("typeId", typeId)
                .counter()).isNotNull();

            assertThat(meterRegistry.find("hookrouter.retry.total")
                .tag("platform", platform)
                .tag("webhookKey", webhookKey)
                .tag("typeId", typeId)
                .counter()).isNotNull();

            assertThat(meterRegistry.find("hookrouter.dead-letter.total")
                .tag("platform", platform)
                .tag("webhookKey", webhookKey)
                .tag("typeId", typeId)
                .tag("reason", "CIRCUIT_OPEN")
                .counter()).isNotNull();

            assertThat(meterRegistry.find("hookrouter.dead-letter.handler-failure")
                .tag("platform", platform)
                .tag("webhookKey", webhookKey)
                .tag("typeId", typeId)
                .counter()).isNotNull();

            assertThat(meterRegistry.find("hookrouter.external-rate-limit.detected")
                .tag("platform", platform)
                .tag("webhookKey", webhookKey)
                .tag("typeId", typeId)
                .counter()).isNotNull();

            assertThat(meterRegistry.find("hookrouter.external-rate-limit.retry-after")
                .tag("platform", platform)
                .tag("webhookKey", webhookKey)
                .summary()).isNotNull();
        }
    }
}
