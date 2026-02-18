package io.github.limehee.hookrouter.spring.deadletter;

import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface DeadLetterStore {

    StoredDeadLetter save(DeadLetter deadLetter);

    Optional<StoredDeadLetter> findById(String id);

    List<StoredDeadLetter> findByStatus(DeadLetterStatus status);

    List<StoredDeadLetter> findReadyForReprocess(int limit);

    boolean updateStatus(String id, DeadLetterStatus status);

    boolean updateRetryInfo(String id, int retryCount, @Nullable Instant nextRetryAt,
        @Nullable String lastErrorMessage);

    boolean delete(String id);

    int deleteOlderThan(Instant before);

    long countByStatus(DeadLetterStatus status);

    enum DeadLetterStatus {

        PENDING,

        PROCESSING,

        RESOLVED,

        ABANDONED
    }

    record StoredDeadLetter(
        String id,
        DeadLetter deadLetter,
        DeadLetterStatus status,
        int retryCount,
        int maxRetries,
        @Nullable Instant nextRetryAt,
        @Nullable String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
    ) {

        public boolean canRetry() {
            return retryCount < maxRetries;
        }

        public String typeId() {
            return deadLetter.notification().getTypeId();
        }

        public String platform() {
            return deadLetter.platform();
        }

        public String webhookKey() {
            return deadLetter.webhookKey();
        }

        public FailureReason failureReason() {
            return deadLetter.reason();
        }
    }
}
