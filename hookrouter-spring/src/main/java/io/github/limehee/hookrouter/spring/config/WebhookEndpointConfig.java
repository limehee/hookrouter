package io.github.limehee.hookrouter.spring.config;

import org.jspecify.annotations.Nullable;

public class WebhookEndpointConfig {

    @Nullable
    private String url;

    @Nullable
    private RetryOverride retry;

    @Nullable
    private TimeoutOverride timeout;

    @Nullable
    private CircuitBreakerOverride circuitBreaker;

    @Nullable
    private RateLimiterOverride rateLimiter;

    @Nullable
    private BulkheadOverride bulkhead;

    @Nullable
    public String getUrl() {
        return this.url;
    }

    public void setUrl(@Nullable final String url) {
        this.url = url;
    }

    @Nullable
    public RetryOverride getRetry() {
        return this.retry;
    }

    public void setRetry(@Nullable final RetryOverride retry) {
        this.retry = retry;
    }

    @Nullable
    public TimeoutOverride getTimeout() {
        return this.timeout;
    }

    public void setTimeout(@Nullable final TimeoutOverride timeout) {
        this.timeout = timeout;
    }

    @Nullable
    public CircuitBreakerOverride getCircuitBreaker() {
        return this.circuitBreaker;
    }

    public void setCircuitBreaker(@Nullable final CircuitBreakerOverride circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Nullable
    public RateLimiterOverride getRateLimiter() {
        return this.rateLimiter;
    }

    public void setRateLimiter(@Nullable final RateLimiterOverride rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Nullable
    public BulkheadOverride getBulkhead() {
        return this.bulkhead;
    }

    public void setBulkhead(@Nullable final BulkheadOverride bulkhead) {
        this.bulkhead = bulkhead;
    }

    @Override
    public String toString() {
        return "WebhookEndpointConfig(url=" + this.getUrl() + ", retry=" + this.getRetry() + ", timeout="
            + this.getTimeout() + ", circuitBreaker=" + this.getCircuitBreaker() + ", rateLimiter="
            + this.getRateLimiter() + ", bulkhead=" + this.getBulkhead() + ")";
    }

    public static class RetryOverride {

        @Nullable
        private Boolean enabled;
        @Nullable
        private Integer maxAttempts;
        @Nullable
        private Long initialDelay;
        @Nullable
        private Long maxDelay;
        @Nullable
        private Double multiplier;
        @Nullable
        private Double jitterFactor;

        @Nullable
        public Boolean getEnabled() {
            return this.enabled;
        }

        public void setEnabled(@Nullable final Boolean enabled) {
            this.enabled = enabled;
        }

        @Nullable
        public Integer getMaxAttempts() {
            return this.maxAttempts;
        }

        public void setMaxAttempts(@Nullable final Integer maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        @Nullable
        public Long getInitialDelay() {
            return this.initialDelay;
        }

        public void setInitialDelay(@Nullable final Long initialDelay) {
            this.initialDelay = initialDelay;
        }

        @Nullable
        public Long getMaxDelay() {
            return this.maxDelay;
        }

        public void setMaxDelay(@Nullable final Long maxDelay) {
            this.maxDelay = maxDelay;
        }

        @Nullable
        public Double getMultiplier() {
            return this.multiplier;
        }

        public void setMultiplier(@Nullable final Double multiplier) {
            this.multiplier = multiplier;
        }

        @Nullable
        public Double getJitterFactor() {
            return this.jitterFactor;
        }

        public void setJitterFactor(@Nullable final Double jitterFactor) {
            this.jitterFactor = jitterFactor;
        }

        @Override
        public String toString() {
            return "WebhookEndpointConfig.RetryOverride(enabled=" + this.getEnabled() + ", maxAttempts="
                + this.getMaxAttempts() + ", initialDelay=" + this.getInitialDelay() + ", maxDelay="
                + this.getMaxDelay() + ", multiplier=" + this.getMultiplier() + ", jitterFactor="
                + this.getJitterFactor() + ")";
        }
    }

    public static class TimeoutOverride {

        @Nullable
        private Boolean enabled;
        @Nullable
        private Long duration;

        @Nullable
        public Boolean getEnabled() {
            return this.enabled;
        }

        public void setEnabled(@Nullable final Boolean enabled) {
            this.enabled = enabled;
        }

        @Nullable
        public Long getDuration() {
            return this.duration;
        }

        public void setDuration(@Nullable final Long duration) {
            this.duration = duration;
        }

        @Override
        public String toString() {
            return "WebhookEndpointConfig.TimeoutOverride(enabled=" + this.getEnabled() + ", duration="
                + this.getDuration() + ")";
        }
    }

    public static class CircuitBreakerOverride {

        @Nullable
        private Boolean enabled;
        @Nullable
        private Integer failureThreshold;
        @Nullable
        private Float failureRateThreshold;
        @Nullable
        private Long waitDuration;
        @Nullable
        private Integer successThreshold;

        @Nullable
        public Boolean getEnabled() {
            return this.enabled;
        }

        public void setEnabled(@Nullable final Boolean enabled) {
            this.enabled = enabled;
        }

        @Nullable
        public Integer getFailureThreshold() {
            return this.failureThreshold;
        }

        public void setFailureThreshold(@Nullable final Integer failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        @Nullable
        public Float getFailureRateThreshold() {
            return this.failureRateThreshold;
        }

        public void setFailureRateThreshold(@Nullable final Float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        @Nullable
        public Long getWaitDuration() {
            return this.waitDuration;
        }

        public void setWaitDuration(@Nullable final Long waitDuration) {
            this.waitDuration = waitDuration;
        }

        @Nullable
        public Integer getSuccessThreshold() {
            return this.successThreshold;
        }

        public void setSuccessThreshold(@Nullable final Integer successThreshold) {
            this.successThreshold = successThreshold;
        }

        @Override
        public String toString() {
            return "WebhookEndpointConfig.CircuitBreakerOverride(enabled=" + this.getEnabled() + ", failureThreshold="
                + this.getFailureThreshold() + ", failureRateThreshold=" + this.getFailureRateThreshold()
                + ", waitDuration=" + this.getWaitDuration() + ", successThreshold=" + this.getSuccessThreshold() + ")";
        }
    }

    public static class RateLimiterOverride {

        @Nullable
        private Boolean enabled;
        @Nullable
        private Integer limitForPeriod;
        @Nullable
        private Long limitRefreshPeriod;
        @Nullable
        private Long timeoutDuration;

        @Nullable
        public Boolean getEnabled() {
            return this.enabled;
        }

        public void setEnabled(@Nullable final Boolean enabled) {
            this.enabled = enabled;
        }

        @Nullable
        public Integer getLimitForPeriod() {
            return this.limitForPeriod;
        }

        public void setLimitForPeriod(@Nullable final Integer limitForPeriod) {
            this.limitForPeriod = limitForPeriod;
        }

        @Nullable
        public Long getLimitRefreshPeriod() {
            return this.limitRefreshPeriod;
        }

        public void setLimitRefreshPeriod(@Nullable final Long limitRefreshPeriod) {
            this.limitRefreshPeriod = limitRefreshPeriod;
        }

        @Nullable
        public Long getTimeoutDuration() {
            return this.timeoutDuration;
        }

        public void setTimeoutDuration(@Nullable final Long timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }

        @Override
        public String toString() {
            return "WebhookEndpointConfig.RateLimiterOverride(enabled=" + this.getEnabled() + ", limitForPeriod="
                + this.getLimitForPeriod() + ", limitRefreshPeriod=" + this.getLimitRefreshPeriod()
                + ", timeoutDuration=" + this.getTimeoutDuration() + ")";
        }
    }

    public static class BulkheadOverride {

        @Nullable
        private Boolean enabled;
        @Nullable
        private Integer maxConcurrentCalls;
        @Nullable
        private Long maxWaitDuration;

        @Nullable
        public Boolean getEnabled() {
            return this.enabled;
        }

        public void setEnabled(@Nullable final Boolean enabled) {
            this.enabled = enabled;
        }

        @Nullable
        public Integer getMaxConcurrentCalls() {
            return this.maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(@Nullable final Integer maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }

        @Nullable
        public Long getMaxWaitDuration() {
            return this.maxWaitDuration;
        }

        public void setMaxWaitDuration(@Nullable final Long maxWaitDuration) {
            this.maxWaitDuration = maxWaitDuration;
        }

        @Override
        public String toString() {
            return "WebhookEndpointConfig.BulkheadOverride(enabled=" + this.getEnabled() + ", maxConcurrentCalls="
                + this.getMaxConcurrentCalls() + ", maxWaitDuration=" + this.getMaxWaitDuration() + ")";
        }
    }
}
