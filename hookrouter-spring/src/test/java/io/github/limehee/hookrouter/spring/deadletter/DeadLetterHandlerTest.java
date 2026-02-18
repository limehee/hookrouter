package io.github.limehee.hookrouter.spring.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeadLetterHandlerTest {

    private record TestPayload(String message) {

    }

    @Nested
    class FailureReasonTest {

        @Test
        void shouldContainExpectedValues() {
            // When
            FailureReason[] values = FailureReason.values();

            // Then
            assertThat(values).hasSize(9);
            assertThat(values).containsExactlyInAnyOrder(
                FailureReason.MAX_RETRIES_EXCEEDED,
                FailureReason.NON_RETRYABLE_ERROR,
                FailureReason.CIRCUIT_OPEN,
                FailureReason.RATE_LIMITED,
                FailureReason.BULKHEAD_FULL,
                FailureReason.EXCEPTION,
                FailureReason.FORMATTER_NOT_FOUND,
                FailureReason.PAYLOAD_CREATION_FAILED,
                FailureReason.SENDER_NOT_FOUND
            );
        }

        @Test
        void shouldMatchExpectedFailureReasonValueOf() {
            // When & Then
            assertThat(FailureReason.valueOf("MAX_RETRIES_EXCEEDED")).isEqualTo(FailureReason.MAX_RETRIES_EXCEEDED);
            assertThat(FailureReason.valueOf("NON_RETRYABLE_ERROR")).isEqualTo(FailureReason.NON_RETRYABLE_ERROR);
            assertThat(FailureReason.valueOf("CIRCUIT_OPEN")).isEqualTo(FailureReason.CIRCUIT_OPEN);
            assertThat(FailureReason.valueOf("RATE_LIMITED")).isEqualTo(FailureReason.RATE_LIMITED);
            assertThat(FailureReason.valueOf("BULKHEAD_FULL")).isEqualTo(FailureReason.BULKHEAD_FULL);
            assertThat(FailureReason.valueOf("EXCEPTION")).isEqualTo(FailureReason.EXCEPTION);
            assertThat(FailureReason.valueOf("FORMATTER_NOT_FOUND")).isEqualTo(FailureReason.FORMATTER_NOT_FOUND);
            assertThat(FailureReason.valueOf("PAYLOAD_CREATION_FAILED")).isEqualTo(
                FailureReason.PAYLOAD_CREATION_FAILED);
            assertThat(FailureReason.valueOf("SENDER_NOT_FOUND")).isEqualTo(FailureReason.SENDER_NOT_FOUND);
        }
    }

    @Nested
    class DeadLetterRecordTest {

        @Test
        void shouldMatchExpectedDeadLetterNotification() {
            // Given
            Notification<String> notification = createTestNotification();
            String platform = "slack";
            String webhookKey = "test-channel";
            String webhookUrl = "https://hooks.slack.com/services/test";
            Object payload = new TestPayload("message");
            FailureReason reason = FailureReason.MAX_RETRIES_EXCEEDED;
            String errorMessage = "Connection timeout";
            int attemptCount = 3;

            // When
            Instant beforeCreation = Instant.now();
            DeadLetter deadLetter = DeadLetter.of(
                notification, platform, webhookKey, webhookUrl,
                payload, reason, errorMessage, attemptCount
            );
            Instant afterCreation = Instant.now();

            // Then
            assertThat(deadLetter.notification()).isSameAs(notification);
            assertThat(deadLetter.platform()).isEqualTo(platform);
            assertThat(deadLetter.webhookKey()).isEqualTo(webhookKey);
            assertThat(deadLetter.webhookUrl()).isEqualTo(webhookUrl);
            assertThat(deadLetter.payload()).isEqualTo(payload);
            assertThat(deadLetter.reason()).isEqualTo(reason);
            assertThat(deadLetter.errorMessage()).isEqualTo(errorMessage);
            assertThat(deadLetter.attemptCount()).isEqualTo(attemptCount);
            assertThat(deadLetter.timestamp())
                .isAfterOrEqualTo(beforeCreation)
                .isBeforeOrEqualTo(afterCreation);
        }

        @Test
        void shouldReturnNullDeadLetterPayload() {
            // Given
            Notification<String> notification = createTestNotification();

            // When
            DeadLetter deadLetter = DeadLetter.of(
                notification, "slack", "test-channel", "https://hooks.slack.com/services/test",
                null, FailureReason.NON_RETRYABLE_ERROR, "Bad request", 1
            );

            // Then
            assertThat(deadLetter.payload()).isNull();
        }

        @Test
        void shouldReturnNullDeadLetterErrorMessage() {
            // Given
            Notification<String> notification = createTestNotification();

            // When
            DeadLetter deadLetter = DeadLetter.of(
                notification, "slack", "test-channel", "https://hooks.slack.com/services/test",
                new TestPayload("message"), FailureReason.CIRCUIT_OPEN, null, 0
            );

            // Then
            assertThat(deadLetter.errorMessage()).isNull();
        }

        @Test
        void shouldMatchExpectedDeadLetter1() {
            // Given
            Notification<String> notification = createTestNotification();
            Instant timestamp = Instant.now();
            Object payload = new TestPayload("message");

            DeadLetter deadLetter1 = new DeadLetter(
                notification, "slack", "test-channel", "https://hooks.slack.com/services/test",
                payload, FailureReason.MAX_RETRIES_EXCEEDED, "Error", 3, timestamp
            );
            DeadLetter deadLetter2 = new DeadLetter(
                notification, "slack", "test-channel", "https://hooks.slack.com/services/test",
                payload, FailureReason.MAX_RETRIES_EXCEEDED, "Error", 3, timestamp
            );

            // When & Then
            assertThat(deadLetter1).isEqualTo(deadLetter2);
            assertThat(deadLetter1.hashCode()).isEqualTo(deadLetter2.hashCode());
        }

        @Test
        void shouldVerifyExpectedDeadLetter1() {
            // Given
            Notification<String> notification = createTestNotification();
            Instant timestamp = Instant.now();

            DeadLetter deadLetter1 = new DeadLetter(
                notification, "slack", "test-channel", "https://hooks.slack.com/services/test",
                null, FailureReason.MAX_RETRIES_EXCEEDED, "Error", 3, timestamp
            );
            DeadLetter deadLetter2 = new DeadLetter(
                notification, "slack", "test-channel", "https://hooks.slack.com/services/test",
                null, FailureReason.CIRCUIT_OPEN, "Error", 3, timestamp
            );

            // When & Then
            assertThat(deadLetter1).isNotEqualTo(deadLetter2);
        }

        private Notification<String> createTestNotification() {
            return Notification.<String>builder("TEST_TYPE")
                .category("general")
                .context("test payload")
                .build();
        }
    }
}
