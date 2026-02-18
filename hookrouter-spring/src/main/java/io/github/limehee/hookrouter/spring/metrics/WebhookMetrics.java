package io.github.limehee.hookrouter.spring.metrics;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

public interface WebhookMetrics {

    void recordSendAttempt(String platform, String webhookKey, String typeId);

    void recordSendSuccess(String platform, String webhookKey, String typeId, Duration duration);

    void recordSendFailure(String platform, String webhookKey, String typeId, String reason, Duration duration);

    void recordSendSkipped(String platform, String webhookKey, String typeId);

    void recordSendRateLimited(String platform, String webhookKey, String typeId);

    void recordSendBulkheadFull(String platform, String webhookKey, String typeId);

    void recordRetry(String platform, String webhookKey, String typeId, int attemptNumber);

    void recordDeadLetter(String platform, String webhookKey, String typeId, String reason);

    void recordDeadLetterHandlerFailure(String platform, String webhookKey, String typeId);

    void recordExternalRateLimitDetected(String platform, String webhookKey, String typeId,
        @Nullable Long retryAfterMillis);
}
