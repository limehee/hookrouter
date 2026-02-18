package io.github.limehee.hookrouter.spring.metrics;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

public final class NoOpWebhookMetrics implements WebhookMetrics {

    public static final NoOpWebhookMetrics INSTANCE = new NoOpWebhookMetrics();

    private NoOpWebhookMetrics() {
    }

    @Override
    public void recordSendAttempt(String platform, String webhookKey, String typeId) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordSendSuccess(String platform, String webhookKey, String typeId, Duration duration) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordSendFailure(String platform, String webhookKey, String typeId, String reason, Duration duration) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordSendSkipped(String platform, String webhookKey, String typeId) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordSendRateLimited(String platform, String webhookKey, String typeId) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordSendBulkheadFull(String platform, String webhookKey, String typeId) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordRetry(String platform, String webhookKey, String typeId, int attemptNumber) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordDeadLetter(String platform, String webhookKey, String typeId, String reason) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordDeadLetterHandlerFailure(String platform, String webhookKey, String typeId) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordExternalRateLimitDetected(String platform, String webhookKey, String typeId,
        @Nullable Long retryAfterMillis) {
        // Intentionally empty - Null Object pattern
    }

    @Override
    public void recordAsyncCallerRuns() {
        // Intentionally empty - Null Object pattern
    }
}
