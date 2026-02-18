package io.github.limehee.hookrouter.spring.config;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.BulkheadProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.CircuitBreakerProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RateLimiterProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RetryProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.TimeoutProperties;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.BulkheadOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.CircuitBreakerOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.RateLimiterOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.RetryOverride;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig.TimeoutOverride;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public class WebhookConfigResolver {

    private final WebhookConfigProperties globalProperties;
    private final Map<CacheKey, ResolvedConfig> cache = new ConcurrentHashMap<>();

    public WebhookConfigResolver(WebhookConfigProperties globalProperties) {
        this.globalProperties = globalProperties;
    }

    public RetryProperties resolveRetryProperties(String platform, String webhookKey) {
        return getOrCreateResolvedConfig(platform, webhookKey).retry();
    }

    public TimeoutProperties resolveTimeoutProperties(String platform, String webhookKey) {
        return getOrCreateResolvedConfig(platform, webhookKey).timeout();
    }

    public CircuitBreakerProperties resolveCircuitBreakerProperties(String platform, String webhookKey) {
        return getOrCreateResolvedConfig(platform, webhookKey).circuitBreaker();
    }

    public RateLimiterProperties resolveRateLimiterProperties(String platform, String webhookKey) {
        return getOrCreateResolvedConfig(platform, webhookKey).rateLimiter();
    }

    public BulkheadProperties resolveBulkheadProperties(String platform, String webhookKey) {
        return getOrCreateResolvedConfig(platform, webhookKey).bulkhead();
    }

    private ResolvedConfig getOrCreateResolvedConfig(String platform, String webhookKey) {
        CacheKey cacheKey = new CacheKey(platform, webhookKey);
        return cache.computeIfAbsent(cacheKey, key -> createResolvedConfig(platform, webhookKey));
    }

    private ResolvedConfig createResolvedConfig(String platform, String webhookKey) {
        WebhookEndpointConfig endpointConfig = getEndpointConfig(platform, webhookKey);
        RetryProperties retry = mergeRetryProperties(endpointConfig);
        TimeoutProperties timeout = mergeTimeoutProperties(endpointConfig);
        CircuitBreakerProperties circuitBreaker = mergeCircuitBreakerProperties(endpointConfig);
        RateLimiterProperties rateLimiter = mergeRateLimiterProperties(endpointConfig);
        BulkheadProperties bulkhead = mergeBulkheadProperties(endpointConfig);
        return new ResolvedConfig(retry, timeout, circuitBreaker, rateLimiter, bulkhead);
    }

    @Nullable
    private WebhookEndpointConfig getEndpointConfig(String platform, String webhookKey) {
        WebhookConfigProperties.PlatformConfig platformConfig = globalProperties.getPlatforms().get(platform);
        if (platformConfig == null) {
            return null;
        }
        return platformConfig.getEndpoints().get(webhookKey);
    }

    private RetryProperties mergeRetryProperties(@Nullable WebhookEndpointConfig endpointConfig) {
        RetryProperties global = globalProperties.getRetry();
        if (endpointConfig == null || endpointConfig.getRetry() == null) {
            return global;
        }
        RetryOverride override = endpointConfig.getRetry();
        RetryProperties merged = new RetryProperties();
        merged.setEnabled(coalesce(override.getEnabled(), global.isEnabled()));
        merged.setMaxAttempts(coalesce(override.getMaxAttempts(), global.getMaxAttempts()));
        merged.setInitialDelay(coalesce(override.getInitialDelay(), global.getInitialDelay()));
        merged.setMaxDelay(coalesce(override.getMaxDelay(), global.getMaxDelay()));
        merged.setMultiplier(coalesce(override.getMultiplier(), global.getMultiplier()));
        merged.setJitterFactor(coalesce(override.getJitterFactor(), global.getJitterFactor()));
        return merged;
    }

    private TimeoutProperties mergeTimeoutProperties(@Nullable WebhookEndpointConfig endpointConfig) {
        TimeoutProperties global = globalProperties.getTimeout();
        if (endpointConfig == null || endpointConfig.getTimeout() == null) {
            return global;
        }
        TimeoutOverride override = endpointConfig.getTimeout();
        TimeoutProperties merged = new TimeoutProperties();
        merged.setEnabled(coalesce(override.getEnabled(), global.isEnabled()));
        merged.setDuration(coalesce(override.getDuration(), global.getDuration()));
        return merged;
    }

    private CircuitBreakerProperties mergeCircuitBreakerProperties(@Nullable WebhookEndpointConfig endpointConfig) {
        CircuitBreakerProperties global = globalProperties.getCircuitBreaker();
        if (endpointConfig == null || endpointConfig.getCircuitBreaker() == null) {
            return global;
        }
        CircuitBreakerOverride override = endpointConfig.getCircuitBreaker();
        CircuitBreakerProperties merged = new CircuitBreakerProperties();
        merged.setEnabled(coalesce(override.getEnabled(), global.isEnabled()));
        merged.setFailureThreshold(coalesce(override.getFailureThreshold(), global.getFailureThreshold()));
        merged.setFailureRateThreshold(coalesce(override.getFailureRateThreshold(), global.getFailureRateThreshold()));
        merged.setWaitDuration(coalesce(override.getWaitDuration(), global.getWaitDuration()));
        merged.setSuccessThreshold(coalesce(override.getSuccessThreshold(), global.getSuccessThreshold()));
        return merged;
    }

    private RateLimiterProperties mergeRateLimiterProperties(@Nullable WebhookEndpointConfig endpointConfig) {
        RateLimiterProperties global = globalProperties.getRateLimiter();
        if (endpointConfig == null || endpointConfig.getRateLimiter() == null) {
            return global;
        }
        RateLimiterOverride override = endpointConfig.getRateLimiter();
        RateLimiterProperties merged = new RateLimiterProperties();
        merged.setEnabled(coalesce(override.getEnabled(), global.isEnabled()));
        merged.setLimitForPeriod(coalesce(override.getLimitForPeriod(), global.getLimitForPeriod()));
        merged.setLimitRefreshPeriod(coalesce(override.getLimitRefreshPeriod(), global.getLimitRefreshPeriod()));
        merged.setTimeoutDuration(coalesce(override.getTimeoutDuration(), global.getTimeoutDuration()));
        return merged;
    }

    private BulkheadProperties mergeBulkheadProperties(@Nullable WebhookEndpointConfig endpointConfig) {
        BulkheadProperties global = globalProperties.getBulkhead();
        if (endpointConfig == null || endpointConfig.getBulkhead() == null) {
            return global;
        }
        BulkheadOverride override = endpointConfig.getBulkhead();
        BulkheadProperties merged = new BulkheadProperties();
        merged.setEnabled(coalesce(override.getEnabled(), global.isEnabled()));
        merged.setMaxConcurrentCalls(coalesce(override.getMaxConcurrentCalls(), global.getMaxConcurrentCalls()));
        merged.setMaxWaitDuration(coalesce(override.getMaxWaitDuration(), global.getMaxWaitDuration()));
        return merged;
    }

    private <T> T coalesce(@Nullable T override, T defaultValue) {
        return override != null ? override : defaultValue;
    }

    private record CacheKey(String platform, String webhookKey) {

    }

    private record ResolvedConfig(
        RetryProperties retry,
        TimeoutProperties timeout,
        CircuitBreakerProperties circuitBreaker,
        RateLimiterProperties rateLimiter,
        BulkheadProperties bulkhead
    ) {

    }
}
