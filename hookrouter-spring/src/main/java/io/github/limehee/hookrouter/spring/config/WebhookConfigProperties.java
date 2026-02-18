package io.github.limehee.hookrouter.spring.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hookrouter")
public class WebhookConfigProperties {

    private AsyncProperties async = new AsyncProperties();
    private RetryProperties retry = new RetryProperties();
    private TimeoutProperties timeout = new TimeoutProperties();
    private RateLimiterProperties rateLimiter = new RateLimiterProperties();
    private BulkheadProperties bulkhead = new BulkheadProperties();
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    private DeadLetterProperties deadLetter = new DeadLetterProperties();
    private Map<String, PlatformConfig> platforms = new HashMap<>();
    private Map<String, List<PlatformMapping>> categoryMappings = new HashMap<>();
    private Map<String, List<PlatformMapping>> typeMappings = new HashMap<>();
    private List<PlatformMapping> defaultMappings = List.of();

    @Nullable
    public String getWebhookUrl(String platform, String webhookKey) {
        PlatformConfig platformConfig = platforms.get(platform);
        if (platformConfig == null) {
            return null;
        }
        return platformConfig.getWebhookUrl(webhookKey);
    }

    @Nullable
    public WebhookEndpointConfig getEndpointConfig(String platform, String webhookKey) {
        PlatformConfig platformConfig = platforms.get(platform);
        if (platformConfig == null) {
            return null;
        }
        return platformConfig.getEndpoints().get(webhookKey);
    }

    public AsyncProperties getAsync() {
        return this.async;
    }

    public void setAsync(final AsyncProperties async) {
        this.async = async;
    }

    public RetryProperties getRetry() {
        return this.retry;
    }

    public void setRetry(final RetryProperties retry) {
        this.retry = retry;
    }

    public TimeoutProperties getTimeout() {
        return this.timeout;
    }

    public void setTimeout(final TimeoutProperties timeout) {
        this.timeout = timeout;
    }

    public RateLimiterProperties getRateLimiter() {
        return this.rateLimiter;
    }

    public void setRateLimiter(final RateLimiterProperties rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public BulkheadProperties getBulkhead() {
        return this.bulkhead;
    }

    public void setBulkhead(final BulkheadProperties bulkhead) {
        this.bulkhead = bulkhead;
    }

    public CircuitBreakerProperties getCircuitBreaker() {
        return this.circuitBreaker;
    }

    public void setCircuitBreaker(final CircuitBreakerProperties circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public DeadLetterProperties getDeadLetter() {
        return this.deadLetter;
    }

    public void setDeadLetter(final DeadLetterProperties deadLetter) {
        this.deadLetter = deadLetter;
    }

    public Map<String, PlatformConfig> getPlatforms() {
        return this.platforms;
    }

    public void setPlatforms(final Map<String, PlatformConfig> platforms) {
        this.platforms = platforms;
    }

    public Map<String, List<PlatformMapping>> getCategoryMappings() {
        return this.categoryMappings;
    }

    public void setCategoryMappings(final Map<String, List<PlatformMapping>> categoryMappings) {
        this.categoryMappings = categoryMappings;
    }

    public Map<String, List<PlatformMapping>> getTypeMappings() {
        return this.typeMappings;
    }

    public void setTypeMappings(final Map<String, List<PlatformMapping>> typeMappings) {
        this.typeMappings = typeMappings;
    }

    public List<PlatformMapping> getDefaultMappings() {
        return this.defaultMappings;
    }

    public void setDefaultMappings(final List<PlatformMapping> defaultMappings) {
        this.defaultMappings = defaultMappings;
    }

    @Override
    public String toString() {
        return "WebhookConfigProperties(async=" + this.getAsync() + ", retry=" + this.getRetry() + ", timeout="
            + this.getTimeout() + ", rateLimiter=" + this.getRateLimiter() + ", bulkhead=" + this.getBulkhead()
            + ", circuitBreaker=" + this.getCircuitBreaker() + ", deadLetter=" + this.getDeadLetter() + ", platforms="
            + this.getPlatforms() + ", categoryMappings=" + this.getCategoryMappings() + ", typeMappings="
            + this.getTypeMappings() + ", defaultMappings=" + this.getDefaultMappings() + ")";
    }

    public static class AsyncProperties {

        private int corePoolSize = 2;
        private int maxPoolSize = 10;
        private int queueCapacity = 100;
        private String threadNamePrefix = "hookrouter-";
        private int awaitTerminationSeconds = 30;

        public int getCorePoolSize() {
            return this.corePoolSize;
        }

        public void setCorePoolSize(final int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return this.maxPoolSize;
        }

        public void setMaxPoolSize(final int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return this.queueCapacity;
        }

        public void setQueueCapacity(final int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return this.threadNamePrefix;
        }

        public void setThreadNamePrefix(final String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        public int getAwaitTerminationSeconds() {
            return this.awaitTerminationSeconds;
        }

        public void setAwaitTerminationSeconds(final int awaitTerminationSeconds) {
            this.awaitTerminationSeconds = awaitTerminationSeconds;
        }

        @Override
        public String toString() {
            return "WebhookConfigProperties.AsyncProperties(corePoolSize=" + this.getCorePoolSize() + ", maxPoolSize="
                + this.getMaxPoolSize() + ", queueCapacity=" + this.getQueueCapacity() + ", threadNamePrefix="
                + this.getThreadNamePrefix() + ", awaitTerminationSeconds=" + this.getAwaitTerminationSeconds() + ")";
        }
    }

    public static class RetryProperties {

        private static final Random RANDOM = new Random();
        private boolean enabled = true;
        private int maxAttempts = 3;
        private long initialDelay = 1000;
        private long maxDelay = 10000;
        private double multiplier = 2.0;
        private double jitterFactor = 0.1;

        public long calculateDelay(final int attemptNumber) {
            double baseDelay = initialDelay * Math.pow(multiplier, attemptNumber);
            long cappedDelay = Math.min((long) baseDelay, maxDelay);
            if (jitterFactor <= 0) {
                return cappedDelay;
            }
            double jitterRange = cappedDelay * jitterFactor;
            double jitter = (RANDOM.nextDouble() * 2 - 1) * jitterRange;
            long finalDelay = cappedDelay + (long) jitter;
            return Math.max(0, finalDelay);
        }

        public long calculateBaseDelay(final int attemptNumber) {
            double baseDelay = initialDelay * Math.pow(multiplier, attemptNumber);
            return Math.min((long) baseDelay, maxDelay);
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return this.maxAttempts;
        }

        public void setMaxAttempts(final int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialDelay() {
            return this.initialDelay;
        }

        public void setInitialDelay(final long initialDelay) {
            this.initialDelay = initialDelay;
        }

        public long getMaxDelay() {
            return this.maxDelay;
        }

        public void setMaxDelay(final long maxDelay) {
            this.maxDelay = maxDelay;
        }

        public double getMultiplier() {
            return this.multiplier;
        }

        public void setMultiplier(final double multiplier) {
            this.multiplier = multiplier;
        }

        public double getJitterFactor() {
            return this.jitterFactor;
        }

        public void setJitterFactor(final double jitterFactor) {
            this.jitterFactor = jitterFactor;
        }

        @Override
        public String toString() {
            return "WebhookConfigProperties.RetryProperties(enabled=" + this.isEnabled() + ", maxAttempts="
                + this.getMaxAttempts() + ", initialDelay=" + this.getInitialDelay() + ", maxDelay="
                + this.getMaxDelay() + ", multiplier=" + this.getMultiplier() + ", jitterFactor="
                + this.getJitterFactor() + ")";
        }
    }

    public static class TimeoutProperties {

        private boolean enabled = true;
        private long duration = 15000;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public long getDuration() {
            return this.duration;
        }

        public void setDuration(final long duration) {
            this.duration = duration;
        }

        @Override
        public String toString() {
            return "WebhookConfigProperties.TimeoutProperties(enabled=" + this.isEnabled() + ", duration="
                + this.getDuration() + ")";
        }
    }

    public static class RateLimiterProperties {

        private boolean enabled = false;
        private int limitForPeriod = 50;
        private long limitRefreshPeriod = 1000;
        private long timeoutDuration = 5000;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public int getLimitForPeriod() {
            return this.limitForPeriod;
        }

        public void setLimitForPeriod(final int limitForPeriod) {
            this.limitForPeriod = limitForPeriod;
        }

        public long getLimitRefreshPeriod() {
            return this.limitRefreshPeriod;
        }

        public void setLimitRefreshPeriod(final long limitRefreshPeriod) {
            this.limitRefreshPeriod = limitRefreshPeriod;
        }

        public long getTimeoutDuration() {
            return this.timeoutDuration;
        }

        public void setTimeoutDuration(final long timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }

        @Override
        public String toString() {
            return "WebhookConfigProperties.RateLimiterProperties(enabled=" + this.isEnabled() + ", limitForPeriod="
                + this.getLimitForPeriod() + ", limitRefreshPeriod=" + this.getLimitRefreshPeriod()
                + ", timeoutDuration=" + this.getTimeoutDuration() + ")";
        }
    }

    public static class BulkheadProperties {

        private boolean enabled = false;
        private int maxConcurrentCalls = 25;
        private long maxWaitDuration = 0;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxConcurrentCalls() {
            return this.maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(final int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }

        public long getMaxWaitDuration() {
            return this.maxWaitDuration;
        }

        public void setMaxWaitDuration(final long maxWaitDuration) {
            this.maxWaitDuration = maxWaitDuration;
        }

        @Override
        public String toString() {
            return "WebhookConfigProperties.BulkheadProperties(enabled=" + this.isEnabled() + ", maxConcurrentCalls="
                + this.getMaxConcurrentCalls() + ", maxWaitDuration=" + this.getMaxWaitDuration() + ")";
        }
    }

    public static class CircuitBreakerProperties {

        private boolean enabled = true;
        private int failureThreshold = 5;
        private float failureRateThreshold = 100.0F;
        private long waitDuration = 60000;
        private int successThreshold = 2;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public int getFailureThreshold() {
            return this.failureThreshold;
        }

        public void setFailureThreshold(final int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public float getFailureRateThreshold() {
            return this.failureRateThreshold;
        }

        public void setFailureRateThreshold(final float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public long getWaitDuration() {
            return this.waitDuration;
        }

        public void setWaitDuration(final long waitDuration) {
            this.waitDuration = waitDuration;
        }

        public int getSuccessThreshold() {
            return this.successThreshold;
        }

        public void setSuccessThreshold(final int successThreshold) {
            this.successThreshold = successThreshold;
        }

        @Override
        public String toString() {
            return "WebhookConfigProperties.CircuitBreakerProperties(enabled=" + this.isEnabled()
                + ", failureThreshold=" + this.getFailureThreshold() + ", failureRateThreshold="
                + this.getFailureRateThreshold() + ", waitDuration=" + this.getWaitDuration() + ", successThreshold="
                + this.getSuccessThreshold() + ")";
        }
    }

    public static class DeadLetterProperties {

        private boolean enabled = true;
        private int maxRetries = 3;
        private boolean schedulerEnabled = false;
        private long schedulerInterval = 60000L;
        private int schedulerBatchSize = 50;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRetries() {
            return this.maxRetries;
        }

        public void setMaxRetries(final int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public boolean isSchedulerEnabled() {
            return this.schedulerEnabled;
        }

        public void setSchedulerEnabled(final boolean schedulerEnabled) {
            this.schedulerEnabled = schedulerEnabled;
        }

        public long getSchedulerInterval() {
            return this.schedulerInterval;
        }

        public void setSchedulerInterval(final long schedulerInterval) {
            this.schedulerInterval = schedulerInterval;
        }

        public int getSchedulerBatchSize() {
            return this.schedulerBatchSize;
        }

        public void setSchedulerBatchSize(final int schedulerBatchSize) {
            this.schedulerBatchSize = schedulerBatchSize;
        }

        @Override
        public String toString() {
            return "WebhookConfigProperties.DeadLetterProperties(enabled=" + this.isEnabled() + ", maxRetries="
                + this.getMaxRetries() + ", schedulerEnabled=" + this.isSchedulerEnabled() + ", schedulerInterval="
                + this.getSchedulerInterval() + ", schedulerBatchSize=" + this.getSchedulerBatchSize() + ")";
        }
    }

    public static class PlatformConfig {

        private Map<String, WebhookEndpointConfig> endpoints = new HashMap<>();

        @Nullable
        public String getWebhookUrl(final String webhookKey) {
            WebhookEndpointConfig endpointConfig = endpoints.get(webhookKey);
            if (endpointConfig != null) {
                return endpointConfig.getUrl();
            }
            return null;
        }

        public Map<String, WebhookEndpointConfig> getEndpoints() {
            return this.endpoints;
        }

        public void setEndpoints(final Map<String, WebhookEndpointConfig> endpoints) {
            this.endpoints = endpoints;
        }

        @Override
        public String toString() {
            return "WebhookConfigProperties.PlatformConfig(endpoints=" + this.getEndpoints() + ")";
        }
    }

    public static class PlatformMapping {

        @Nullable
        private String platform;
        @Nullable
        private String webhook;
        private boolean enabled = true;

        @Nullable
        public String getPlatform() {
            return this.platform;
        }

        public void setPlatform(@Nullable final String platform) {
            this.platform = platform;
        }

        @Nullable
        public String getWebhook() {
            return this.webhook;
        }

        public void setWebhook(@Nullable final String webhook) {
            this.webhook = webhook;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return "WebhookConfigProperties.PlatformMapping(platform=" + this.getPlatform() + ", webhook="
                + this.getWebhook() + ", enabled=" + this.isEnabled() + ")";
        }
    }
}
