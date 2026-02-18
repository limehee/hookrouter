package io.github.limehee.hookrouter.spring.dispatcher;

import static io.github.limehee.hookrouter.spring.support.ClampUtils.clampFloat;
import static io.github.limehee.hookrouter.spring.support.ClampUtils.clampInt;
import static io.github.limehee.hookrouter.spring.support.ClampUtils.clampLong;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.core.port.WebhookSender.SendResult;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.BulkheadProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.CircuitBreakerProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RateLimiterProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RetryProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.TimeoutProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigResolver;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterProcessor;
import io.github.limehee.hookrouter.spring.metrics.WebhookMetrics;
import io.github.limehee.hookrouter.spring.resilience.ResilienceResourceKey;
import io.github.limehee.hookrouter.spring.resilience.WebhookRetryFactory;
import io.github.limehee.hookrouter.spring.resilience.WebhookRetryFactory.WebhookSendRetryableException;
import io.github.limehee.hookrouter.spring.resilience.event.RateLimitDetectedEvent;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;

public class WebhookDispatcher {

    private final WebhookConfigResolver configResolver;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final WebhookMetrics metrics;
    private final DeadLetterProcessor deadLetterProcessor;
    private final ApplicationEventPublisher eventPublisher;

    public WebhookDispatcher(
        WebhookConfigResolver configResolver,
        CircuitBreakerRegistry circuitBreakerRegistry,
        RetryRegistry retryRegistry,
        TimeLimiterRegistry timeLimiterRegistry,
        RateLimiterRegistry rateLimiterRegistry,
        BulkheadRegistry bulkheadRegistry,
        WebhookMetrics metrics,
        DeadLetterProcessor deadLetterProcessor,
        ApplicationEventPublisher eventPublisher
    ) {
        this.configResolver = configResolver;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.metrics = metrics;
        this.deadLetterProcessor = deadLetterProcessor;
        this.eventPublisher = eventPublisher;
    }

    public <T> void dispatch(
        Notification<T> notification,
        RoutingTarget target,
        WebhookSender sender,
        Object payload
    ) {
        String typeId = notification.getTypeId();
        String platform = target.platform();
        String webhookKey = target.webhookKey();
        String resilienceKey = ResilienceResourceKey.of(platform, webhookKey);
        Instant startTime = Instant.now();

        RateLimiterProperties rateLimiterProps =
            configResolver.resolveRateLimiterProperties(platform, webhookKey);
        BulkheadProperties bulkheadProps =
            configResolver.resolveBulkheadProperties(platform, webhookKey);
        CircuitBreakerProperties circuitBreakerProps =
            configResolver.resolveCircuitBreakerProperties(platform, webhookKey);
        RetryProperties retryProps =
            configResolver.resolveRetryProperties(platform, webhookKey);
        TimeoutProperties timeoutProps =
            configResolver.resolveTimeoutProperties(platform, webhookKey);

        Bulkhead bulkhead = null;
        boolean bulkheadPermissionAcquired = false;

        try {
            if (!acquireRateLimiterPermission(resilienceKey, rateLimiterProps)) {
                metrics.recordSendRateLimited(platform, webhookKey, typeId);
                deadLetterProcessor.processRateLimited(notification, target, payload);
                return;
            }

            bulkhead = getBulkhead(resilienceKey, bulkheadProps);
            if (bulkhead != null) {
                if (!bulkhead.tryAcquirePermission()) {
                    metrics.recordSendBulkheadFull(platform, webhookKey, typeId);
                    deadLetterProcessor.processBulkheadFull(notification, target, payload);
                    return;
                }
                bulkheadPermissionAcquired = true;
            }

            CircuitBreaker circuitBreaker = getCircuitBreaker(resilienceKey, circuitBreakerProps);
            if (circuitBreaker != null && !circuitBreaker.tryAcquirePermission()) {
                metrics.recordSendSkipped(platform, webhookKey, typeId);
                return;
            }

            metrics.recordSendAttempt(platform, webhookKey, typeId);

            SendResultWithAttempts resultWithAttempts = sendWithRetry(
                sender,
                target,
                payload,
                typeId,
                retryProps,
                timeoutProps,
                resilienceKey
            );
            SendResult result = resultWithAttempts.result();
            int attemptCount = resultWithAttempts.attemptCount();
            Duration duration = Duration.between(startTime, Instant.now());

            handleResult(
                notification,
                target,
                payload,
                result,
                attemptCount,
                duration,
                circuitBreaker
            );
        } catch (Exception e) {
            handleException(
                notification,
                target,
                payload,
                e,
                resilienceKey,
                webhookKey,
                platform,
                typeId,
                startTime,
                circuitBreakerProps
            );
        } finally {
            if (bulkheadPermissionAcquired && bulkhead != null) {
                bulkhead.onComplete();
            }
        }
    }

    private boolean acquireRateLimiterPermission(String resilienceKey, RateLimiterProperties props) {
        if (!props.isEnabled()) {
            return true;
        }

        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(Math.max(props.getLimitForPeriod(), 1))
            .limitRefreshPeriod(Duration.ofMillis(Math.max(props.getLimitRefreshPeriod(), 1L)))
            .timeoutDuration(Duration.ofMillis(Math.max(props.getTimeoutDuration(), 0L)))
            .build();

        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(resilienceKey, config);

        try {
            return rateLimiter.acquirePermission();
        } catch (RequestNotPermitted e) {
            return false;
        }
    }

    @Nullable
    private Bulkhead getBulkhead(String resilienceKey, BulkheadProperties props) {
        if (!props.isEnabled()) {
            return null;
        }

        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(Math.max(props.getMaxConcurrentCalls(), 1))
            .maxWaitDuration(Duration.ofMillis(Math.max(props.getMaxWaitDuration(), 0L)))
            .build();

        return bulkheadRegistry.bulkhead(resilienceKey, config);
    }

    @Nullable
    private CircuitBreaker getCircuitBreaker(String resilienceKey, CircuitBreakerProperties props) {
        if (!props.isEnabled()) {
            return null;
        }

        int failureThreshold = clampInt(props.getFailureThreshold(), 1, Integer.MAX_VALUE);
        int successThreshold = clampInt(props.getSuccessThreshold(), 1, Integer.MAX_VALUE);
        long waitDurationMillis = clampLong(props.getWaitDuration(), 1L, Long.MAX_VALUE);
        float failureRateThreshold = clampFloat(props.getFailureRateThreshold(), 0.0F, 100.0F);

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(failureThreshold)
            .minimumNumberOfCalls(failureThreshold)
            .failureRateThreshold(failureRateThreshold)
            .waitDurationInOpenState(Duration.ofMillis(waitDurationMillis))
            .permittedNumberOfCallsInHalfOpenState(successThreshold)
            .build();

        return circuitBreakerRegistry.circuitBreaker(resilienceKey, config);
    }

    private <T> void handleResult(
        Notification<T> notification,
        RoutingTarget target,
        Object payload,
        SendResult result,
        int attemptCount,
        Duration duration,
        @Nullable CircuitBreaker circuitBreaker
    ) {
        String platform = target.platform();
        String webhookKey = target.webhookKey();
        String typeId = notification.getTypeId();

        if (result.success()) {
            if (circuitBreaker != null) {
                circuitBreaker.onSuccess(0, TimeUnit.MILLISECONDS);
            }
            metrics.recordSendSuccess(platform, webhookKey, typeId, duration);
            return;
        }

        if (circuitBreaker != null) {
            circuitBreaker.onError(
                0,
                TimeUnit.MILLISECONDS,
                new WebhookSendFailureException(result.errorMessage())
            );
            if (circuitBreaker.getState() == State.HALF_OPEN) {
                circuitBreaker.transitionToOpenState();
            }
        }

        if (result.isRateLimited()) {
            publishRateLimitDetectedEvent(target, typeId, result);
        }

        metrics.recordSendFailure(
            platform,
            webhookKey,
            typeId,
            Objects.requireNonNullElse(result.errorMessage(), "unknown error"),
            duration
        );
        deadLetterProcessor.processSendFailure(notification, target, payload, result, attemptCount);
    }

    private <T> void handleException(
        Notification<T> notification,
        RoutingTarget target,
        Object payload,
        Exception exception,
        String resilienceKey,
        String webhookKey,
        String platform,
        String typeId,
        Instant startTime,
        CircuitBreakerProperties circuitBreakerProps
    ) {
        if (circuitBreakerProps.isEnabled()) {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(resilienceKey);
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, exception);
            if (circuitBreaker.getState() == State.HALF_OPEN) {
                circuitBreaker.transitionToOpenState();
            }
        }

        Duration duration = Duration.between(startTime, Instant.now());
        metrics.recordSendFailure(
            platform,
            webhookKey,
            typeId,
            "exception: " + exception.getMessage(),
            duration
        );
        deadLetterProcessor.processException(notification, target, payload, exception);
    }

    private void publishRateLimitDetectedEvent(
        RoutingTarget target,
        String typeId,
        SendResult result
    ) {
        RateLimitDetectedEvent event = RateLimitDetectedEvent.of(
            target.platform(),
            target.webhookKey(),
            target.webhookUrl(),
            typeId,
            result.retryAfterMillis(),
            Objects.requireNonNullElse(result.errorMessage(), "rate limited")
        );
        eventPublisher.publishEvent(event);
    }

    private SendResultWithAttempts sendWithRetry(
        WebhookSender sender,
        RoutingTarget target,
        Object payload,
        String typeId,
        RetryProperties retryProps,
        TimeoutProperties timeoutProps,
        String resilienceKey
    ) {
        if (!retryProps.isEnabled()) {
            SendResult result = sendWithTimeout(sender, target.webhookUrl(), payload, resilienceKey, timeoutProps);
            return new SendResultWithAttempts(result, 1);
        }

        Retry retry = retryRegistry.retry(resilienceKey, WebhookRetryFactory.createConfig(retryProps));
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicReference<SendResult> lastResult = new AtomicReference<>();

        try {
            SendResult result = retry.executeSupplier(() -> {
                int currentAttempt = attemptCount.incrementAndGet();
                if (currentAttempt > 1) {
                    metrics.recordRetry(target.platform(), target.webhookKey(), typeId, currentAttempt - 1);
                }
                SendResult sendResult = sendWithTimeout(
                    sender,
                    target.webhookUrl(),
                    payload,
                    resilienceKey,
                    timeoutProps
                );
                lastResult.set(sendResult);

                if (sendResult.success()) {
                    return sendResult;
                }
                if (sendResult.retryable()) {
                    throw new WebhookSendRetryableException(
                        Objects.requireNonNullElse(sendResult.errorMessage(), "retryable error")
                    );
                }
                return sendResult;
            });
            return new SendResultWithAttempts(result, attemptCount.get());
        } catch (WebhookSendRetryableException e) {
            SendResult finalResult = lastResult.get();
            if (finalResult == null) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "unknown error";
                finalResult = SendResult.failure(0, errorMessage, true);
            }
            return new SendResultWithAttempts(finalResult, attemptCount.get());
        }
    }

    private SendResult sendWithTimeout(
        WebhookSender sender,
        String webhookUrl,
        Object payload,
        String resilienceKey,
        TimeoutProperties timeoutProps
    ) {
        if (!timeoutProps.isEnabled()) {
            return sender.send(webhookUrl, payload);
        }

        long durationMillis = clampLong(timeoutProps.getDuration(), 1L, Long.MAX_VALUE);
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(durationMillis))
            .cancelRunningFuture(true)
            .build();
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(resilienceKey, config);

        try {
            return timeLimiter.executeFutureSupplier(
                () -> CompletableFuture.supplyAsync(() -> sender.send(webhookUrl, payload))
            );
        } catch (TimeoutException e) {
            return SendResult.failure(
                0,
                "timeout: request exceeded " + timeoutProps.getDuration() + "ms",
                true
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                return SendResult.failure(
                    0,
                    "timeout: request exceeded " + timeoutProps.getDuration() + "ms",
                    true
                );
            }
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return SendResult.failure(0, "interrupted: request was cancelled", false);
            }
            String message = cause != null ? cause.getMessage() : e.getMessage();
            return SendResult.failure(0, "execution error: " + message, true);
        } catch (Exception e) {
            return SendResult.failure(0, "error: " + e.getMessage(), true);
        }
    }

    private record SendResultWithAttempts(SendResult result, int attemptCount) {

    }

    private static final class WebhookSendFailureException extends RuntimeException {

        WebhookSendFailureException(@Nullable String message) {
            super(message, null, false, false);
        }
    }

}
