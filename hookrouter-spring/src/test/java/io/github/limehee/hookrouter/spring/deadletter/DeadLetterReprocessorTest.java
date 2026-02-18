package io.github.limehee.hookrouter.spring.deadletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterReprocessor.ReprocessResult;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterReprocessor.ReprocessStatus;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterReprocessor.ReprocessSummary;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.DeadLetterStatus;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.StoredDeadLetter;
import io.github.limehee.hookrouter.spring.listener.NotificationProcessingGateway;
import io.github.limehee.hookrouter.spring.listener.NotificationProcessingGateway.ProcessingResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeadLetterReprocessorTest {

    private DeadLetterReprocessor reprocessor;

    @Mock
    private DeadLetterStore store;

    @Mock
    private NotificationProcessingGateway notificationProcessor;

    private static Notification<?> anyNotification() {
        return any();
    }

    @BeforeEach
    void setUp() {
        reprocessor = new DeadLetterReprocessor(store, notificationProcessor);
        lenient().when(notificationProcessor.process(anyNotification())).thenReturn(ProcessingResult.ok());
    }

    private StoredDeadLetter createStoredDeadLetter(String id, int retryCount, int maxRetries) {
        Notification<String> notification = Notification.<String>builder("TEST_TYPE")
            .category("general")
            .context("test payload")
            .build();

        DeadLetter deadLetter = DeadLetter.of(
            notification,
            "slack",
            "test-channel",
            "https://hooks.slack.com/services/test",
            "payload",
            FailureReason.MAX_RETRIES_EXCEEDED,
            "Test error",
            3
        );

        return new StoredDeadLetter(
            id,
            deadLetter,
            DeadLetterStatus.PENDING,
            retryCount,
            maxRetries,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    @Nested
    class ReprocessByIdTest {

        @Test
        void shouldMatchExpectedResultId() {
            // Given
            String id = "test-id";
            StoredDeadLetter storedDeadLetter = createStoredDeadLetter(id, 0, 3);

            given(store.findById(id)).willReturn(Optional.of(storedDeadLetter));
            given(store.updateStatus(eq(id), any(DeadLetterStatus.class))).willReturn(true);

            // When
            ReprocessResult result = reprocessor.reprocessById(id);

            // Then
            assertThat(result.id()).isEqualTo(id);
            assertThat(result.status()).isEqualTo(ReprocessStatus.SUCCESS);
            assertThat(result.isSuccess()).isTrue();

            verify(store).updateStatus(id, DeadLetterStatus.PROCESSING);
            verify(notificationProcessor).process(anyNotification());
            verify(store).updateStatus(id, DeadLetterStatus.RESOLVED);
        }

        @Test
        void shouldMatchExpectedResultIdWhenDeadLetterIsNotFound() {
            // Given
            String id = "non-existent-id";
            given(store.findById(id)).willReturn(Optional.empty());

            // When
            ReprocessResult result = reprocessor.reprocessById(id);

            // Then
            assertThat(result.id()).isEqualTo(id);
            assertThat(result.status()).isEqualTo(ReprocessStatus.NOT_FOUND);
            assertThat(result.errorMessage()).isEqualTo("Dead letter not found");

            verify(notificationProcessor, never()).process(anyNotification());
        }

        @Test
        void shouldMatchExpectedResultIdWhenMaxRetriesAreExceeded() {
            // Given
            String id = "test-id";
            StoredDeadLetter storedDeadLetter = createStoredDeadLetter(id, 3, 3);

            given(store.findById(id)).willReturn(Optional.of(storedDeadLetter));
            given(store.updateStatus(eq(id), any(DeadLetterStatus.class))).willReturn(true);

            // When
            ReprocessResult result = reprocessor.reprocessById(id);

            // Then
            assertThat(result.id()).isEqualTo(id);
            assertThat(result.status()).isEqualTo(ReprocessStatus.ABANDONED);
            assertThat(result.errorMessage()).isEqualTo("Max retries exceeded");

            verify(store).updateStatus(id, DeadLetterStatus.ABANDONED);
            verify(notificationProcessor, never()).process(anyNotification());
        }

        @Test
        void shouldMatchExpectedResultIdWhenPublisherThrowsDuringReprocess() {
            // Given
            String id = "test-id";
            StoredDeadLetter storedDeadLetter = createStoredDeadLetter(id, 0, 3);

            given(store.findById(id)).willReturn(Optional.of(storedDeadLetter));
            given(store.updateStatus(eq(id), any(DeadLetterStatus.class))).willReturn(true);
            willThrow(new RuntimeException("Connection failed")).given(notificationProcessor).process(anyNotification());

            // When
            ReprocessResult result = reprocessor.reprocessById(id);

            // Then
            assertThat(result.id()).isEqualTo(id);
            assertThat(result.status()).isEqualTo(ReprocessStatus.FAILED);
            assertThat(result.errorMessage()).isEqualTo("Connection failed");

            verify(store).updateStatus(id, DeadLetterStatus.PROCESSING);
            verify(store).updateRetryInfo(eq(id), eq(1), any(Instant.class), eq("Connection failed"));
        }

        @Test
        void shouldReturnFailedWhenNotificationProcessorReportsFailure() {
            // Given
            String id = "test-id";
            StoredDeadLetter storedDeadLetter = createStoredDeadLetter(id, 0, 3);

            given(store.findById(id)).willReturn(Optional.of(storedDeadLetter));
            given(store.updateStatus(eq(id), any(DeadLetterStatus.class))).willReturn(true);
            given(notificationProcessor.process(anyNotification()))
                .willReturn(ProcessingResult.failed("delivery failed"));

            // When
            ReprocessResult result = reprocessor.reprocessById(id);

            // Then
            assertThat(result.id()).isEqualTo(id);
            assertThat(result.status()).isEqualTo(ReprocessStatus.FAILED);
            assertThat(result.errorMessage()).isEqualTo("delivery failed");
            verify(store).updateRetryInfo(eq(id), eq(1), any(Instant.class), eq("delivery failed"));
        }
    }

    @Nested
    class ReprocessPendingTest {

        @Test
        void shouldMatchExpectedSummarySuccessCount() {
            // Given
            StoredDeadLetter stored1 = createStoredDeadLetter("id-1", 0, 3);
            StoredDeadLetter stored2 = createStoredDeadLetter("id-2", 0, 3);

            given(store.findReadyForReprocess(10)).willReturn(List.of(stored1, stored2));
            given(store.updateStatus(anyString(), any(DeadLetterStatus.class))).willReturn(true);

            // When
            ReprocessSummary summary = reprocessor.reprocessPending(10);

            // Then
            assertThat(summary.successCount()).isEqualTo(2);
            assertThat(summary.failedCount()).isZero();
            assertThat(summary.skippedCount()).isZero();
            assertThat(summary.totalCount()).isEqualTo(2);
        }

        @Test
        void shouldMatchExpectedSummarySuccessCountWhenFindReadyForReprocessIsPositive() {
            // Given
            StoredDeadLetter stored1 = createStoredDeadLetter("id-1", 0, 3);
            StoredDeadLetter stored2 = createStoredDeadLetter("id-2", 0, 3);

            given(store.findReadyForReprocess(10)).willReturn(List.of(stored1, stored2));
            given(store.updateStatus(anyString(), any(DeadLetterStatus.class))).willReturn(true);
            given(store.updateRetryInfo(anyString(), anyInt(), any(Instant.class), anyString())).willReturn(true);

            willThrow(new RuntimeException("Connection failed"))
                .willReturn(ProcessingResult.ok())
                .given(notificationProcessor).process(anyNotification());

            // When
            ReprocessSummary summary = reprocessor.reprocessPending(10);

            // Then

            assertThat(summary.successCount()).isEqualTo(1);
            assertThat(summary.failedCount()).isEqualTo(1);
            assertThat(summary.skippedCount()).isZero();
        }

        @Test
        void shouldMatchExpectedSummarySuccessCountWhenFindReadyForReprocessIsPositiveWithFindReadyForReprocess() {
            // Given
            StoredDeadLetter stored1 = createStoredDeadLetter("id-1", 3, 3); // canRetry = false

            given(store.findReadyForReprocess(10)).willReturn(List.of(stored1));
            given(store.updateStatus(anyString(), any(DeadLetterStatus.class))).willReturn(true);

            // When
            ReprocessSummary summary = reprocessor.reprocessPending(10);

            // Then
            assertThat(summary.successCount()).isZero();
            assertThat(summary.failedCount()).isZero();
            assertThat(summary.skippedCount()).isEqualTo(1);
        }

        @Test
        void shouldVerifyExpectedSummarySuccessCount() {
            // Given
            given(store.findReadyForReprocess(10)).willReturn(List.of());

            // When
            ReprocessSummary summary = reprocessor.reprocessPending(10);

            // Then
            assertThat(summary.successCount()).isZero();
            assertThat(summary.failedCount()).isZero();
            assertThat(summary.skippedCount()).isZero();
            assertThat(summary.totalCount()).isZero();
        }
    }

    @Nested
    class ReprocessResultTest {

        @Test
        void shouldReturnNullResultId() {
            // When
            ReprocessResult result = ReprocessResult.success("test-id");

            // Then
            assertThat(result.id()).isEqualTo("test-id");
            assertThat(result.status()).isEqualTo(ReprocessStatus.SUCCESS);
            assertThat(result.errorMessage()).isNull();
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void shouldMatchExpectedResultIdWhenFailed() {
            // When
            ReprocessResult result = ReprocessResult.failed("test-id", "Connection failed");

            // Then
            assertThat(result.id()).isEqualTo("test-id");
            assertThat(result.status()).isEqualTo(ReprocessStatus.FAILED);
            assertThat(result.errorMessage()).isEqualTo("Connection failed");
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void shouldMatchExpectedResultIdWhenAbandoned() {
            // When
            ReprocessResult result = ReprocessResult.abandoned("test-id");

            // Then
            assertThat(result.id()).isEqualTo("test-id");
            assertThat(result.status()).isEqualTo(ReprocessStatus.ABANDONED);
            assertThat(result.errorMessage()).isEqualTo("Max retries exceeded");
        }

        @Test
        void shouldMatchExpectedResultIdWhenNotFound() {
            // When
            ReprocessResult result = ReprocessResult.notFound("test-id");

            // Then
            assertThat(result.id()).isEqualTo("test-id");
            assertThat(result.status()).isEqualTo(ReprocessStatus.NOT_FOUND);
            assertThat(result.errorMessage()).isEqualTo("Dead letter not found");
        }
    }

    @Nested
    class ReprocessSummaryTest {

        @Test
        void shouldMatchExpectedSummaryTotalCount() {
            // When
            ReprocessSummary summary = new ReprocessSummary(5, 2, 1);

            // Then
            assertThat(summary.totalCount()).isEqualTo(8);
        }

        @Test
        void shouldMatchExpectedSummaryToString() {
            // When
            ReprocessSummary summary = new ReprocessSummary(5, 2, 1);

            // Then
            assertThat(summary.toString())
                .isEqualTo("ReprocessSummary[success=5, failed=2, skipped=1, total=8]");
        }
    }

    @Nested
    class ReprocessStatusTest {

        @Test
        void shouldContainExpectedValues() {
            // When
            ReprocessStatus[] values = ReprocessStatus.values();

            // Then
            assertThat(values).hasSize(4);
            assertThat(values).containsExactlyInAnyOrder(
                ReprocessStatus.SUCCESS,
                ReprocessStatus.FAILED,
                ReprocessStatus.ABANDONED,
                ReprocessStatus.NOT_FOUND
            );
        }
    }

    @Nested
    class ConstructorTest {

        @Test
        void shouldMatchExpectedDELAYMS() {
            // Then
            assertThat(DeadLetterReprocessor.DEFAULT_INITIAL_DELAY_MS).isEqualTo(60_000L);
            assertThat(DeadLetterReprocessor.DEFAULT_MAX_DELAY_MS).isEqualTo(3_600_000L);
            assertThat(DeadLetterReprocessor.DEFAULT_MULTIPLIER).isEqualTo(2.0);
        }

        @Test
        void shouldReturnNotNullCustomReprocessor() {
            // When
            DeadLetterReprocessor customReprocessor = new DeadLetterReprocessor(
                store, notificationProcessor, 30_000L, 1_800_000L, 1.5
            );

            // Then
            assertThat(customReprocessor).isNotNull();
        }
    }

    @Nested
    class ExponentialBackoffTest {

        @Test
        void shouldMatchExpectedResultStatus() {

            long maxDelayMs = 3_600_000L;
            DeadLetterReprocessor customReprocessor = new DeadLetterReprocessor(
                store, notificationProcessor, 60_000L, maxDelayMs, 2.0
            );

            String id = "overflow-test-id";
            StoredDeadLetter storedDeadLetter = createStoredDeadLetterWithRetryCount(id, 99, 200);

            given(store.findById(id)).willReturn(Optional.of(storedDeadLetter));
            given(store.updateStatus(eq(id), any(DeadLetterStatus.class))).willReturn(true);
            given(store.updateRetryInfo(anyString(), anyInt(), any(Instant.class), anyString())).willReturn(true);
            willThrow(new RuntimeException("Connection failed")).given(notificationProcessor).process(anyNotification());

            Instant before = Instant.now();

            // When
            ReprocessResult result = customReprocessor.reprocessById(id);

            // Then
            assertThat(result.status()).isEqualTo(ReprocessStatus.FAILED);

            verify(store).updateRetryInfo(eq(id), eq(100), argThat((Instant nextRetryAt) -> {

                Instant expectedMax = before.plusMillis(maxDelayMs + 1000);
                return nextRetryAt.isBefore(expectedMax) && nextRetryAt.isAfter(before);
            }), eq("Connection failed"));
        }

        private StoredDeadLetter createStoredDeadLetterWithRetryCount(String id, int retryCount, int maxRetries) {
            Notification<String> notification = Notification.<String>builder("TEST_TYPE")
                .category("general")
                .context("test payload")
                .build();

            DeadLetter deadLetter = DeadLetter.of(
                notification,
                "slack",
                "test-channel",
                "https://hooks.slack.com/services/test",
                "payload",
                FailureReason.MAX_RETRIES_EXCEEDED,
                "Test error",
                3
            );

            return new StoredDeadLetter(
                id,
                deadLetter,
                DeadLetterStatus.PENDING,
                retryCount,
                maxRetries,
                null,
                null,
                Instant.now(),
                Instant.now()
            );
        }
    }

    @Nested
    class StuckProcessingRecoveryTest {

        @Test
        void shouldMatchExpectedResultStatusWhenFindById() {
            // Given
            String id = "test-id";
            StoredDeadLetter storedDeadLetter = createStoredDeadLetter(id, 0, 3);
            StoredDeadLetter processingState = new StoredDeadLetter(
                id,
                storedDeadLetter.deadLetter(),
                DeadLetterStatus.PROCESSING,
                0,
                3,
                null,
                null,
                Instant.now(),
                Instant.now()
            );

            given(store.findById(id)).willReturn(Optional.of(storedDeadLetter))
                .willReturn(Optional.of(processingState));
            given(store.updateStatus(eq(id), any(DeadLetterStatus.class))).willReturn(true);
            given(store.updateRetryInfo(anyString(), anyInt(), any(Instant.class), anyString())).willReturn(true);
            willThrow(new RuntimeException("Connection failed")).given(notificationProcessor).process(anyNotification());

            // When
            ReprocessResult result = reprocessor.reprocessById(id);

            // Then
            assertThat(result.status()).isEqualTo(ReprocessStatus.FAILED);

            verify(store).updateStatus(id, DeadLetterStatus.PROCESSING);
            verify(store).updateRetryInfo(eq(id), eq(1), any(Instant.class), eq("Connection failed"));

        }

        @Test
        void shouldInvokeExpectedInteractions() {
            // Given
            String id = "test-id";
            StoredDeadLetter storedDeadLetter = createStoredDeadLetter(id, 0, 3);
            StoredDeadLetter processingState = new StoredDeadLetter(
                id,
                storedDeadLetter.deadLetter(),
                DeadLetterStatus.PROCESSING,
                0,
                3,
                null,
                null,
                Instant.now(),
                Instant.now()
            );

            given(store.findById(id))
                .willReturn(Optional.of(storedDeadLetter))
                .willReturn(Optional.of(processingState));
            given(store.updateStatus(anyString(), any(DeadLetterStatus.class))).willReturn(true);

            willThrow(new OutOfMemoryError("Heap space")).given(notificationProcessor).process(anyNotification());

            // When & Then
            try {
                reprocessor.reprocessById(id);
            } catch (OutOfMemoryError e) {

            }

            verify(store).updateStatus(id, DeadLetterStatus.PROCESSING);
            verify(store).updateStatus(id, DeadLetterStatus.PENDING);

        }

        @Test
        void shouldMatchExpectedResultStatusWhenRecoverySucceedsAfterStuckProcessing() {
            // Given
            String id = "test-id";
            StoredDeadLetter storedDeadLetter = createStoredDeadLetter(id, 0, 3);

            given(store.findById(id)).willReturn(Optional.of(storedDeadLetter));
            given(store.updateStatus(eq(id), any(DeadLetterStatus.class))).willReturn(true);

            // When
            ReprocessResult result = reprocessor.reprocessById(id);

            // Then
            assertThat(result.status()).isEqualTo(ReprocessStatus.SUCCESS);

            verify(store).updateStatus(id, DeadLetterStatus.PROCESSING);
            verify(store).updateStatus(id, DeadLetterStatus.RESOLVED);
            verify(notificationProcessor).process(anyNotification());

        }

        @Test
        void shouldMatchExpectedResultStatusWhenStuckProcessingStatePersists() {
            // Given
            String id = "test-id";
            StoredDeadLetter storedDeadLetter = createStoredDeadLetter(id, 0, 3);
            StoredDeadLetter processingState = new StoredDeadLetter(
                id,
                storedDeadLetter.deadLetter(),
                DeadLetterStatus.PROCESSING,
                0,
                3,
                null,
                null,
                Instant.now(),
                Instant.now()
            );

            given(store.findById(id)).willReturn(Optional.of(storedDeadLetter))
                .willReturn(Optional.of(processingState));
            given(store.updateStatus(eq(id), any(DeadLetterStatus.class))).willReturn(true);
            given(store.updateRetryInfo(anyString(), anyInt(), any(Instant.class), anyString())).willReturn(true);

            willThrow(new RuntimeException(new InterruptedException("Thread interrupted")))
                .given(notificationProcessor).process(anyNotification());

            // When
            ReprocessResult result = reprocessor.reprocessById(id);

            // Then
            assertThat(result.status()).isEqualTo(ReprocessStatus.FAILED);

            verify(store).updateStatus(id, DeadLetterStatus.PROCESSING);
            verify(store).updateRetryInfo(anyString(), anyInt(), any(Instant.class), anyString());
        }
    }
}
