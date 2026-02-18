package io.github.limehee.hookrouter.spring.config;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.AsyncProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.BulkheadProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.CircuitBreakerProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.DeadLetterProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformConfig;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformMapping;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RateLimiterProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RetryProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.TimeoutProperties;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class WebhookConfigValidator {

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    private WebhookConfigValidator() {
    }

    public static void validate(WebhookConfigProperties properties) {
        List<String> errors = new ArrayList<>();

        validateAsyncProperties(properties.getAsync(), errors);

        validateRetryProperties(properties.getRetry(), errors);
        validateTimeoutProperties(properties.getTimeout(), errors);
        validateRateLimiterProperties(properties.getRateLimiter(), errors);
        validateBulkheadProperties(properties.getBulkhead(), errors);
        validateCircuitBreakerProperties(properties.getCircuitBreaker(), errors);

        validateDeadLetterProperties(properties.getDeadLetter(), errors);

        validatePlatformUrls(properties.getPlatforms(), errors);
        validateMappings(properties, errors);

        validateCrossConfiguration(properties, errors);
        if (!errors.isEmpty()) {
            String errorMessage = String.join("; ", errors);
            throw new WebhookConfigValidationException(errorMessage, errors);
        }
    }

    private static void validateAsyncProperties(AsyncProperties async, List<String> errors) {
        if (async.getCorePoolSize() <= 0) {
            errors.add("async.corePoolSize must be > 0, but was: " + async.getCorePoolSize());
        }
        if (async.getMaxPoolSize() <= 0) {
            errors.add("async.maxPoolSize must be > 0, but was: " + async.getMaxPoolSize());
        }
        if (async.getCorePoolSize() > async.getMaxPoolSize()) {
            errors.add("async.corePoolSize (" + async.getCorePoolSize() + ") must be <= async.maxPoolSize ("
                + async.getMaxPoolSize() + ")");
        }
        if (async.getQueueCapacity() <= 0) {
            errors.add("async.queueCapacity must be > 0, but was: " + async.getQueueCapacity());
        }
        if (async.getAwaitTerminationSeconds() < 0) {
            errors.add("async.awaitTerminationSeconds must be >= 0, but was: " + async.getAwaitTerminationSeconds());
        }
        if (!StringUtils.hasText(async.getThreadNamePrefix())) {
            errors.add("async.threadNamePrefix must not be blank");
        }
    }

    private static void validateRetryProperties(RetryProperties retry, List<String> errors) {
        if (retry.getMaxAttempts() <= 0) {
            errors.add("retry.maxAttempts must be > 0, but was: " + retry.getMaxAttempts());
        }
        if (retry.getInitialDelay() <= 0) {
            errors.add("retry.initialDelay must be > 0, but was: " + retry.getInitialDelay());
        }
        if (retry.getMaxDelay() <= 0) {
            errors.add("retry.maxDelay must be > 0, but was: " + retry.getMaxDelay());
        }
        if (retry.getMaxDelay() < retry.getInitialDelay()) {
            errors.add(
                "retry.maxDelay (" + retry.getMaxDelay() + ") must be >= retry.initialDelay (" + retry.getInitialDelay()
                    + ")");
        }
        if (retry.getMultiplier() < 1.0) {
            errors.add("retry.multiplier must be >= 1.0, but was: " + retry.getMultiplier());
        }
        if (retry.getJitterFactor() < 0.0 || retry.getJitterFactor() > 1.0) {
            errors.add("retry.jitterFactor must be between 0.0 and 1.0, but was: " + retry.getJitterFactor());
        }
    }

    private static void validateTimeoutProperties(TimeoutProperties timeout, List<String> errors) {
        if (timeout.getDuration() <= 0) {
            errors.add("timeout.duration must be > 0, but was: " + timeout.getDuration());
        }
    }

    private static void validateRateLimiterProperties(RateLimiterProperties rateLimiter, List<String> errors) {
        if (rateLimiter.getLimitForPeriod() <= 0) {
            errors.add("rateLimiter.limitForPeriod must be > 0, but was: " + rateLimiter.getLimitForPeriod());
        }
        if (rateLimiter.getLimitRefreshPeriod() <= 0) {
            errors.add("rateLimiter.limitRefreshPeriod must be > 0, but was: " + rateLimiter.getLimitRefreshPeriod());
        }
        if (rateLimiter.getTimeoutDuration() < 0) {
            errors.add("rateLimiter.timeoutDuration must be >= 0, but was: " + rateLimiter.getTimeoutDuration());
        }
    }

    private static void validateBulkheadProperties(BulkheadProperties bulkhead, List<String> errors) {
        if (bulkhead.getMaxConcurrentCalls() <= 0) {
            errors.add("bulkhead.maxConcurrentCalls must be > 0, but was: " + bulkhead.getMaxConcurrentCalls());
        }
        if (bulkhead.getMaxWaitDuration() < 0) {
            errors.add("bulkhead.maxWaitDuration must be >= 0, but was: " + bulkhead.getMaxWaitDuration());
        }
    }

    private static void validateCircuitBreakerProperties(CircuitBreakerProperties circuitBreaker, List<String> errors) {
        if (circuitBreaker.getFailureThreshold() <= 0) {
            errors.add("circuitBreaker.failureThreshold must be > 0, but was: " + circuitBreaker.getFailureThreshold());
        }
        if (circuitBreaker.getFailureRateThreshold() <= 0 || circuitBreaker.getFailureRateThreshold() > 100) {
            errors.add("circuitBreaker.failureRateThreshold must be between 0 and 100 (exclusive of 0), but was: "
                + circuitBreaker.getFailureRateThreshold());
        }
        if (circuitBreaker.getWaitDuration() <= 0) {
            errors.add("circuitBreaker.waitDuration must be > 0, but was: " + circuitBreaker.getWaitDuration());
        }
        if (circuitBreaker.getSuccessThreshold() <= 0) {
            errors.add("circuitBreaker.successThreshold must be > 0, but was: " + circuitBreaker.getSuccessThreshold());
        }
    }

    private static void validateDeadLetterProperties(DeadLetterProperties deadLetter, List<String> errors) {
        if (deadLetter.getSchedulerInterval() <= 0) {
            errors.add("deadLetter.schedulerInterval must be > 0, but was: " + deadLetter.getSchedulerInterval());
        }
        if (deadLetter.getSchedulerBatchSize() <= 0) {
            errors.add("deadLetter.schedulerBatchSize must be > 0, but was: " + deadLetter.getSchedulerBatchSize());
        }
    }

    private static void validatePlatformUrls(Map<String, PlatformConfig> platforms, List<String> errors) {
        for (Map.Entry<String, PlatformConfig> platformEntry : platforms.entrySet()) {
            String platform = platformEntry.getKey();
            PlatformConfig config = platformEntry.getValue();

            for (Map.Entry<String, WebhookEndpointConfig> endpointEntry : config.getEndpoints().entrySet()) {
                String webhookKey = endpointEntry.getKey();
                WebhookEndpointConfig endpointConfig = endpointEntry.getValue();
                String url = endpointConfig.getUrl();
                if (!StringUtils.hasText(url)) {
                    errors.add(formatUrlPath(platform, webhookKey) + " URL is missing or blank");
                } else {
                    validateUrl(url, formatUrlPath(platform, webhookKey), errors);
                }
            }
        }
    }

    private static void validateUrl(String url, String location, List<String> errors) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                errors.add(location + " URL must have a scheme (http or https): " + url);
                return;
            }
            if (!HTTP_SCHEME.equalsIgnoreCase(scheme) && !HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
                errors.add(location + " URL must use http or https scheme, but was: " + scheme);
                return;
            }

            uri.toURL();
            if (!StringUtils.hasText(uri.getHost())) {
                errors.add(location + " URL must have a valid host: " + url);
            }
        } catch (URISyntaxException e) {
            errors.add(location + " URL has invalid syntax: " + url + " (" + e.getMessage() + ")");
        } catch (MalformedURLException e) {
            errors.add(location + " URL is malformed: " + url + " (" + e.getMessage() + ")");
        }
    }

    private static void validateMappings(WebhookConfigProperties properties, List<String> errors) {

        for (Map.Entry<String, List<PlatformMapping>> entry : properties.getTypeMappings().entrySet()) {
            String typeId = entry.getKey();
            for (PlatformMapping mapping : entry.getValue()) {
                validateMappingReference(properties, mapping, "type-mappings[" + typeId + "]", errors);
            }
        }

        for (Map.Entry<String, List<PlatformMapping>> entry : properties.getCategoryMappings().entrySet()) {
            String category = entry.getKey();
            for (PlatformMapping mapping : entry.getValue()) {
                validateMappingReference(properties, mapping, "category-mappings[" + category + "]", errors);
            }
        }

        int index = 0;
        for (PlatformMapping mapping : properties.getDefaultMappings()) {
            validateMappingReference(properties, mapping, "default-mappings[" + index + "]", errors);
            index++;
        }
    }

    private static void validateMappingReference(WebhookConfigProperties properties, PlatformMapping mapping,
        String location, List<String> errors) {
        String platform = mapping.getPlatform();
        String webhook = mapping.getWebhook();
        if (!StringUtils.hasText(platform)) {
            errors.add(location + " platform is missing or blank");
            return;
        }
        if (!StringUtils.hasText(webhook)) {
            errors.add(location + " webhook is missing or blank");
            return;
        }
        PlatformConfig platformConfig = properties.getPlatforms().get(platform);
        if (platformConfig == null) {
            errors.add(location + " references non-existent platform: " + platform);
            return;
        }

        if (!platformConfig.getEndpoints().containsKey(webhook)) {
            errors.add(location + " references non-existent webhook: " + platform + "." + webhook);
        }
    }

    private static String formatUrlPath(String platform, String webhookKey) {
        return "platforms." + platform + ".endpoints." + webhookKey;
    }

    private static void validateCrossConfiguration(WebhookConfigProperties properties, List<String> errors) {
        RetryProperties retry = properties.getRetry();
        TimeoutProperties timeout = properties.getTimeout();
        AsyncProperties async = properties.getAsync();
        BulkheadProperties bulkhead = properties.getBulkhead();
        RateLimiterProperties rateLimiter = properties.getRateLimiter();

        if (timeout.isEnabled() && retry.isEnabled()) {
            if (timeout.getDuration() < retry.getMaxDelay()) {
                errors.add("timeout.duration (" + timeout.getDuration() + "ms) must be >= retry.maxDelay ("
                    + retry.getMaxDelay() + "ms). Current configuration makes retry effectively impossible "
                    + "because timeout will occur during retry wait periods");
            }
        }

        if (bulkhead.isEnabled()) {
            if (bulkhead.getMaxConcurrentCalls() > async.getMaxPoolSize()) {
                errors.add("bulkhead.maxConcurrentCalls (" + bulkhead.getMaxConcurrentCalls()
                    + ") must be <= async.maxPoolSize (" + async.getMaxPoolSize()
                    + "). Current configuration allows more concurrent dispatch slots than async worker capacity");
            }
        }

        if (rateLimiter.isEnabled() && timeout.isEnabled()) {
            if (rateLimiter.getTimeoutDuration() > timeout.getDuration()) {
                errors.add("rateLimiter.timeoutDuration (" + rateLimiter.getTimeoutDuration()
                    + "ms) must be <= timeout.duration (" + timeout.getDuration()
                    + "ms). Waiting for rate limiter permission longer than request timeout is ineffective");
            }
        }

        if (bulkhead.isEnabled() && timeout.isEnabled()) {
            if (bulkhead.getMaxWaitDuration() > timeout.getDuration()) {
                errors.add("bulkhead.maxWaitDuration (" + bulkhead.getMaxWaitDuration()
                    + "ms) must be <= timeout.duration (" + timeout.getDuration()
                    + "ms). Waiting for bulkhead permission longer than request timeout is ineffective");
            }
        }
    }
}
