package io.github.limehee.hookrouter.spring.resilience;

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
        int maxAttempts = Math.clamp(properties.getMaxAttempts(), 1, Integer.MAX_VALUE);
        long initialDelay = Math.clamp(properties.getInitialDelay(), 1L, Long.MAX_VALUE);
        long maxDelay = Math.clamp(properties.getMaxDelay(), initialDelay, Long.MAX_VALUE);
        double multiplier = Math.max(properties.getMultiplier(), 1.0);
        double jitterFactor = Math.clamp(properties.getJitterFactor(), 0.0, 1.0);
        IntervalFunction intervalFunction = IntervalFunction.ofExponentialRandomBackoff(initialDelay, multiplier,
            jitterFactor, maxDelay);
        return
            // Exponential backoff + Jitter
            RetryConfig.custom().maxAttempts(1 + maxAttempts).intervalFunction(intervalFunction)
                .retryOnException(throwable -> throwable instanceof WebhookSendRetryableException).build();
    }

    public static final class WebhookSendRetryableException extends RuntimeException {

        public WebhookSendRetryableException(String message) {
            super(message, null, false, false);
        }
    }
}
