package io.github.limehee.hookrouter.spring.deadletter;

import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.DeadLetterStatus;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.StoredDeadLetter;
import io.github.limehee.hookrouter.spring.listener.NotificationProcessingGateway;
import io.github.limehee.hookrouter.spring.listener.NotificationProcessingGateway.ProcessingResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class DeadLetterReprocessor {

    public static final long DEFAULT_INITIAL_DELAY_MS = 60000L;
    public static final long DEFAULT_MAX_DELAY_MS = 3600000L;
    public static final double DEFAULT_MULTIPLIER = 2.0;
    private final DeadLetterStore store;
    private final NotificationProcessingGateway notificationProcessor;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;

    public DeadLetterReprocessor(DeadLetterStore store, NotificationProcessingGateway notificationProcessor) {
        this(store, notificationProcessor, DEFAULT_INITIAL_DELAY_MS, DEFAULT_MAX_DELAY_MS, DEFAULT_MULTIPLIER);
    }

    public DeadLetterReprocessor(DeadLetterStore store, NotificationProcessingGateway notificationProcessor,
        long initialDelayMs,
        long maxDelayMs, double multiplier) {
        this.store = store;
        this.notificationProcessor = notificationProcessor;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
    }

    public ReprocessResult reprocessById(String id) {
        return store.findById(id).map(this::reprocess).orElse(ReprocessResult.notFound(id));
    }

    public ReprocessSummary reprocessPending(int limit) {
        List<StoredDeadLetter> deadLetters = store.findReadyForReprocess(limit);
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        for (StoredDeadLetter deadLetter : deadLetters) {
            ReprocessResult result = reprocess(deadLetter);
            switch (result.status()) {
                case SUCCESS -> successCount++;
                case FAILED -> failedCount++;
                case ABANDONED, NOT_FOUND -> skippedCount++;
            }
        }
        return new ReprocessSummary(successCount, failedCount, skippedCount);
    }

    private ReprocessResult reprocess(StoredDeadLetter storedDeadLetter) {
        String id = storedDeadLetter.id();

        if (!storedDeadLetter.canRetry()) {
            store.updateStatus(id, DeadLetterStatus.ABANDONED);
            return ReprocessResult.abandoned(id);
        }

        store.updateStatus(id, DeadLetterStatus.PROCESSING);
        boolean success = false;
        try {

            ProcessingResult processingResult = notificationProcessor.process(storedDeadLetter.deadLetter().notification());
            if (!processingResult.success()) {
                String errorMessage = processingResult.errorMessage() != null
                    ? processingResult.errorMessage()
                    : "Reprocess delivery failed";
                int newRetryCount = storedDeadLetter.retryCount() + 1;
                Instant nextRetryAt = calculateNextRetryTime(newRetryCount);
                store.updateRetryInfo(id, newRetryCount, nextRetryAt, errorMessage);
                return ReprocessResult.failed(id, errorMessage);
            }

            store.updateStatus(id, DeadLetterStatus.RESOLVED);
            success = true;
            return ReprocessResult.success(id);
        } catch (Exception e) {

            int newRetryCount = storedDeadLetter.retryCount() + 1;
            Instant nextRetryAt = calculateNextRetryTime(newRetryCount);
            store.updateRetryInfo(id, newRetryCount, nextRetryAt, e.getMessage());
            return ReprocessResult.failed(id, e.getMessage());
        } finally {
            if (!success) {

                store.findById(id).ifPresent(current -> {
                    if (current.status() == DeadLetterStatus.PROCESSING) {
                        store.updateStatus(id, DeadLetterStatus.PENDING);
                    }
                });
            }
        }
    }

    private Instant calculateNextRetryTime(int retryCount) {

        double rawDelay = initialDelayMs * Math.pow(multiplier, retryCount - 1);

        long delay;
        if (Double.isInfinite(rawDelay) || Double.isNaN(rawDelay) || rawDelay >= maxDelayMs) {
            delay = maxDelayMs;
        } else {
            delay = (long) rawDelay;
        }
        return Instant.now().plus(Duration.ofMillis(delay));
    }

    public enum ReprocessStatus {

        SUCCESS,
        FAILED,
        ABANDONED,
        NOT_FOUND;
    }

    public record ReprocessResult(String id, ReprocessStatus status, @Nullable String errorMessage) {

        public static ReprocessResult success(String id) {
            return new ReprocessResult(id, ReprocessStatus.SUCCESS, null);
        }

        public static ReprocessResult failed(String id, @Nullable String errorMessage) {
            return new ReprocessResult(id, ReprocessStatus.FAILED, errorMessage);
        }

        public static ReprocessResult abandoned(String id) {
            return new ReprocessResult(id, ReprocessStatus.ABANDONED, "Max retries exceeded");
        }

        public static ReprocessResult notFound(String id) {
            return new ReprocessResult(id, ReprocessStatus.NOT_FOUND, "Dead letter not found");
        }

        public boolean isSuccess() {
            return status == ReprocessStatus.SUCCESS;
        }
    }

    public record ReprocessSummary(int successCount, int failedCount, int skippedCount) {

        public int totalCount() {
            return successCount + failedCount + skippedCount;
        }

        @Override
        public String toString() {
            return String.format("ReprocessSummary[success=%d, failed=%d, skipped=%d, total=%d]", successCount,
                failedCount, skippedCount, totalCount());
        }
    }
}
