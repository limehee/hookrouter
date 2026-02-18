package io.github.limehee.hookrouter.spring.resilience;

import static io.github.limehee.hookrouter.spring.support.ClampUtils.clampDouble;
import static io.github.limehee.hookrouter.spring.support.ClampUtils.clampInt;
import static io.github.limehee.hookrouter.spring.support.ClampUtils.clampLong;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RetryProperties;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

public final class WebhookRetryFactory {

    private WebhookRetryFactory() {
    }

    public static RetryRegistry createRegistry(RetryProperties properties) {
        return RetryRegistry.of(createConfig(properties));
    }

    public static RetryConfig createConfig(RetryProperties properties) {
        int maxAttempts = clampInt(properties.getMaxAttempts(), 1, Integer.MAX_VALUE);
        long initialDelay = clampLong(properties.getInitialDelay(), 1L, Long.MAX_VALUE);
        long maxDelay = clampLong(properties.getMaxDelay(), initialDelay, Long.MAX_VALUE);
        double multiplier = Math.max(properties.getMultiplier(), 1.0);
        double jitterFactor = clampDouble(properties.getJitterFactor(), 0.0, 1.0);
        IntervalFunction intervalFunction = IntervalFunction.ofExponentialRandomBackoff(initialDelay, multiplier,
            jitterFactor, maxDelay);
        return RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .intervalFunction(intervalFunction)
            .retryOnException(throwable -> throwable instanceof WebhookSendRetryableException)
            .build();
    }

    public static final class WebhookSendRetryableException extends RuntimeException {

        public WebhookSendRetryableException(String message) {
            super(message, null, false, false);
        }
    }
}
