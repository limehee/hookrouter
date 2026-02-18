package io.github.limehee.hookrouter.spring.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

public class MicrometerWebhookMetrics implements WebhookMetrics {

    private static final String METRIC_PREFIX = "hookrouter";
    private static final String TAG_PLATFORM = "platform";
    private static final String TAG_WEBHOOK_KEY = "webhookKey";
    private static final String TAG_TYPE_ID = "typeId";
    private static final String TAG_RESULT = "result";
    private static final String TAG_REASON = "reason";
    private final MeterRegistry meterRegistry;

    public MicrometerWebhookMetrics(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordSendAttempt(String platform, String webhookKey, String typeId) {
        meterRegistry.counter(METRIC_PREFIX + ".send.total", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY, webhookKey,
            TAG_TYPE_ID, typeId).increment();
    }

    @Override
    public void recordSendSuccess(String platform, String webhookKey, String typeId, Duration duration) {
        meterRegistry.counter(METRIC_PREFIX + ".send.success", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY, webhookKey,
            TAG_TYPE_ID, typeId).increment();
        recordSendDuration(platform, webhookKey, typeId, "success", duration);
    }

    @Override
    public void recordSendFailure(String platform, String webhookKey, String typeId, String reason, Duration duration) {
        meterRegistry.counter(METRIC_PREFIX + ".send.failure", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY, webhookKey,
            TAG_TYPE_ID, typeId, TAG_REASON, reason).increment();
        recordSendDuration(platform, webhookKey, typeId, "failure", duration);
    }

    @Override
    public void recordSendSkipped(String platform, String webhookKey, String typeId) {
        meterRegistry.counter(METRIC_PREFIX + ".send.skipped", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY, webhookKey,
            TAG_TYPE_ID, typeId).increment();
    }

    @Override
    public void recordSendRateLimited(String platform, String webhookKey, String typeId) {
        meterRegistry.counter(METRIC_PREFIX + ".send.rate-limited", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY, webhookKey,
            TAG_TYPE_ID, typeId).increment();
    }

    @Override
    public void recordSendBulkheadFull(String platform, String webhookKey, String typeId) {
        meterRegistry.counter(METRIC_PREFIX + ".send.bulkhead-full", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY,
            webhookKey, TAG_TYPE_ID, typeId).increment();
    }

    @Override
    public void recordRetry(String platform, String webhookKey, String typeId, int attemptNumber) {
        meterRegistry.counter(METRIC_PREFIX + ".retry.total", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY, webhookKey,
            TAG_TYPE_ID, typeId).increment();
    }

    @Override
    public void recordDeadLetter(String platform, String webhookKey, String typeId, String reason) {
        meterRegistry.counter(METRIC_PREFIX + ".dead-letter.total", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY, webhookKey,
            TAG_TYPE_ID, typeId, TAG_REASON, reason).increment();
    }

    @Override
    public void recordDeadLetterHandlerFailure(String platform, String webhookKey, String typeId) {
        meterRegistry.counter(METRIC_PREFIX + ".dead-letter.handler-failure", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY,
            webhookKey, TAG_TYPE_ID, typeId).increment();
    }

    @Override
    public void recordExternalRateLimitDetected(String platform, String webhookKey, String typeId,
        @Nullable Long retryAfterMillis) {
        meterRegistry.counter(METRIC_PREFIX + ".external-rate-limit.detected", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY,
            webhookKey, TAG_TYPE_ID, typeId).increment();
        if (retryAfterMillis != null && retryAfterMillis > 0) {
            meterRegistry.summary(METRIC_PREFIX + ".external-rate-limit.retry-after", TAG_PLATFORM, platform,
                TAG_WEBHOOK_KEY, webhookKey).record(retryAfterMillis);
        }
    }

    @Override
    public void recordAsyncCallerRuns() {
        meterRegistry.counter(METRIC_PREFIX + ".async.caller-runs.count").increment();
    }

    private void recordSendDuration(String platform, String webhookKey, String typeId, String result,
        Duration duration) {
        meterRegistry.timer(METRIC_PREFIX + ".send.duration", TAG_PLATFORM, platform, TAG_WEBHOOK_KEY, webhookKey,
            TAG_TYPE_ID, typeId, TAG_RESULT, result).record(duration.toMillis(), TimeUnit.MILLISECONDS);
    }
}
