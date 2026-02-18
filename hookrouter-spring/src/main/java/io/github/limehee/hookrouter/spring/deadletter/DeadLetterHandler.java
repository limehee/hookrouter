package io.github.limehee.hookrouter.spring.deadletter;

import io.github.limehee.hookrouter.core.domain.Notification;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface DeadLetterHandler {

    void handle(DeadLetter deadLetter);

    enum FailureReason {

        MAX_RETRIES_EXCEEDED,
        NON_RETRYABLE_ERROR,
        CIRCUIT_OPEN,
        RATE_LIMITED,
        BULKHEAD_FULL,
        EXCEPTION,
        FORMATTER_NOT_FOUND,
        PAYLOAD_CREATION_FAILED,
        SENDER_NOT_FOUND
    }

    record DeadLetter(
        Notification<?> notification,
        String platform,
        String webhookKey,
        String webhookUrl,
        @Nullable Object payload,
        FailureReason reason,
        @Nullable String errorMessage,
        int attemptCount,
        Instant timestamp
    ) {

        public static DeadLetter of(
            Notification<?> notification,
            String platform,
            String webhookKey,
            String webhookUrl,
            @Nullable Object payload,
            FailureReason reason,
            @Nullable String errorMessage,
            int attemptCount
        ) {
            return new DeadLetter(
                notification,
                platform,
                webhookKey,
                webhookUrl,
                payload,
                reason,
                errorMessage,
                attemptCount,
                Instant.now()
            );
        }
    }
}
