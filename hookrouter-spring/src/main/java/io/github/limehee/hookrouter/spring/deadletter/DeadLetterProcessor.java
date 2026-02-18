package io.github.limehee.hookrouter.spring.deadletter;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.port.WebhookSender.SendResult;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import io.github.limehee.hookrouter.spring.metrics.WebhookMetrics;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class DeadLetterProcessor {

    private static final System.Logger LOGGER = System.getLogger(DeadLetterProcessor.class.getName());
    private final DeadLetterHandler deadLetterHandler;
    private final WebhookMetrics metrics;

    public DeadLetterProcessor(final DeadLetterHandler deadLetterHandler, final WebhookMetrics metrics) {
        this.deadLetterHandler = deadLetterHandler;
        this.metrics = metrics;
    }

    public <T> void processRateLimited(Notification<T> notification, RoutingTarget target, Object payload) {
        process(notification, target, payload, FailureReason.RATE_LIMITED,
            "Rate limit exceeded: request not permitted within timeout duration", 0);
    }

    public <T> void processBulkheadFull(Notification<T> notification, RoutingTarget target, Object payload) {
        process(notification, target, payload, FailureReason.BULKHEAD_FULL,
            "Bulkhead full: max concurrent calls exceeded", 0);
    }

    public <T> void processSendFailure(Notification<T> notification, RoutingTarget target, Object payload,
        SendResult result, int attemptCount) {
        FailureReason reason =
            result.retryable() ? FailureReason.MAX_RETRIES_EXCEEDED : FailureReason.NON_RETRYABLE_ERROR;
        String errorMessage = Objects.requireNonNullElse(result.errorMessage(), "No error message available");
        process(notification, target, payload, reason, errorMessage, attemptCount);
    }

    public <T> void processException(Notification<T> notification, RoutingTarget target, @Nullable Object payload,
        Exception exception) {
        String errorMessage = Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName());
        process(notification, target, payload != null ? payload : "payload_not_created", FailureReason.EXCEPTION,
            errorMessage, 1);
    }

    public <T> void processFormatterNotFound(Notification<T> notification, RoutingTarget target) {
        String errorMessage = String.format("No formatter found for platform=%s, typeId=%s", target.platform(),
            notification.getTypeId());
        process(notification, target, "payload_not_created", FailureReason.FORMATTER_NOT_FOUND, errorMessage, 0);
    }

    public <T> void processPayloadCreationFailed(Notification<T> notification, RoutingTarget target,
        @Nullable String errorMessage) {
        String message = errorMessage != null ? errorMessage
            : String.format("Payload creation failed for platform=%s, typeId=%s", target.platform(),
                notification.getTypeId());
        process(notification, target, "payload_not_created", FailureReason.PAYLOAD_CREATION_FAILED, message, 0);
    }

    public <T> void processSenderNotFound(Notification<T> notification, RoutingTarget target, Object payload) {
        String errorMessage = String.format("No sender found for platform=%s", target.platform());
        process(notification, target, payload, FailureReason.SENDER_NOT_FOUND, errorMessage, 0);
    }

    private <T> void process(Notification<T> notification, RoutingTarget target, Object payload, FailureReason reason,
        @Nullable String errorMessage, int attemptCount) {
        DeadLetter deadLetter = DeadLetter.of(notification, target.platform(), target.webhookKey(), target.webhookUrl(),
            payload, reason, errorMessage, attemptCount);
        handle(deadLetter, target, notification.getTypeId());
    }

    private void handle(DeadLetter deadLetter, RoutingTarget target, String typeId) {
        boolean handlerSuccess = false;
        try {
            deadLetterHandler.handle(deadLetter);
            handlerSuccess = true;
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Dead-letter handler failed for typeId=" + typeId + ", platform=" + target.platform()
                    + ", webhookKey=" + target.webhookKey(),
                e);
        } finally {
            recordDeadLetterMetric(target, typeId, deadLetter.reason(), handlerSuccess);
        }
    }

    private void recordDeadLetterMetric(RoutingTarget target, String typeId, FailureReason reason,
        boolean handlerSuccess) {
        try {
            metrics.recordDeadLetter(target.platform(), target.webhookKey(), typeId, reason.name());
            if (!handlerSuccess) {
                metrics.recordDeadLetterHandlerFailure(target.platform(), target.webhookKey(), typeId);
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Dead-letter metric recording failed for typeId=" + typeId + ", platform=" + target.platform()
                    + ", webhookKey=" + target.webhookKey() + ", reason=" + reason.name(),
                e);
        }
    }
}
