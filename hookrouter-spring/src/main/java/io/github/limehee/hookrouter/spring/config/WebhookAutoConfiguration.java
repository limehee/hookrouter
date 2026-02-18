package io.github.limehee.hookrouter.spring.config;

import static io.github.limehee.hookrouter.spring.support.ClampUtils.clampFloat;
import static io.github.limehee.hookrouter.spring.support.ClampUtils.clampInt;
import static io.github.limehee.hookrouter.spring.support.ClampUtils.clampLong;

import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.core.registry.FormatterRegistry;
import io.github.limehee.hookrouter.core.registry.NotificationTypeRegistry;
import io.github.limehee.hookrouter.spring.actuator.WebhookHealthIndicator;
import io.github.limehee.hookrouter.spring.async.WebhookAsyncConfig;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterProcessor;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterReprocessor;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterScheduler;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore;
import io.github.limehee.hookrouter.spring.deadletter.LoggingDeadLetterHandler;
import io.github.limehee.hookrouter.spring.deadletter.NoOpDeadLetterHandler;
import io.github.limehee.hookrouter.spring.deadletter.StoringDeadLetterHandler;
import io.github.limehee.hookrouter.spring.dispatcher.WebhookDispatcher;
import io.github.limehee.hookrouter.spring.listener.NotificationListener;
import io.github.limehee.hookrouter.spring.metrics.MicrometerWebhookMetrics;
import io.github.limehee.hookrouter.spring.metrics.NoOpWebhookMetrics;
import io.github.limehee.hookrouter.spring.metrics.WebhookMetrics;
import io.github.limehee.hookrouter.spring.publisher.NotificationPublisher;
import io.github.limehee.hookrouter.spring.resilience.ResilienceResourceKey;
import io.github.limehee.hookrouter.spring.resilience.WebhookRetryFactory;
import io.github.limehee.hookrouter.spring.resilience.event.CircuitBreakerEventListener;
import io.github.limehee.hookrouter.spring.resilience.event.RateLimitEventListener;
import io.github.limehee.hookrouter.spring.routing.ConfigBasedRoutingPolicy;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(prefix = "hookrouter.default-mappings[0]", name = "platform")
@EnableConfigurationProperties(WebhookConfigProperties.class)
@Import(WebhookAsyncConfig.class)
public class WebhookAutoConfiguration {

    @Bean
    public Boolean webhookConfigValidation(WebhookConfigProperties properties) {
        WebhookConfigValidator.validate(properties);
        return Boolean.TRUE;
    }

    @Bean
    public NotificationTypeRegistry notificationTypeRegistry(ObjectProvider<NotificationTypeDefinition> definitions) {
        NotificationTypeRegistry registry = new NotificationTypeRegistry();
        definitions.orderedStream().forEach(definition -> {
            registry.register(definition);
        });
        return registry;
    }

    @Bean
    public FormatterRegistry formatterRegistry(ObjectProvider<WebhookFormatter<?, ?>> formatters) {
        FormatterRegistry registry = new FormatterRegistry();
        formatters.orderedStream().forEach(formatter -> {
            registry.register(formatter);
        });
        return registry;
    }

    @Bean
    public NotificationPublisher notificationPublisher(ApplicationEventPublisher eventPublisher) {
        return new NotificationPublisher(eventPublisher);
    }

    @Bean
    public CircuitBreakerEventListener circuitBreakerEventListener(ApplicationEventPublisher eventPublisher) {
        return new CircuitBreakerEventListener(eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitEventListener.class)
    public RateLimitEventListener rateLimitEventListener(RateLimiterRegistry rateLimiterRegistry,
        ObjectProvider<WebhookMetrics> metricsProvider) {
        WebhookMetrics metrics = metricsProvider.getIfAvailable(() -> NoOpWebhookMetrics.INSTANCE);
        return new RateLimitEventListener(rateLimiterRegistry, metrics);
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(WebhookConfigProperties properties,
        CircuitBreakerEventListener eventListener) {
        WebhookConfigProperties.CircuitBreakerProperties cbProps = properties.getCircuitBreaker();
        int failureThreshold = clampInt(cbProps.getFailureThreshold(), 1, Integer.MAX_VALUE);
        int successThreshold = clampInt(cbProps.getSuccessThreshold(), 1, Integer.MAX_VALUE);
        long waitDurationMillis = clampLong(cbProps.getWaitDuration(), 1L, Long.MAX_VALUE);
        float failureRateThreshold = clampFloat(cbProps.getFailureRateThreshold(), 0.0F, 100.0F);
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED).slidingWindowSize(failureThreshold)
            .minimumNumberOfCalls(failureThreshold).failureRateThreshold(failureRateThreshold)
            .waitDurationInOpenState(Duration.ofMillis(waitDurationMillis))
            .permittedNumberOfCallsInHalfOpenState(successThreshold).build();
        return CircuitBreakerRegistry.of(config, List.of(eventListener));
    }

    @Bean
    public RetryRegistry retryRegistry(WebhookConfigProperties properties) {
        WebhookConfigProperties.RetryProperties retryProperties = properties.getRetry();
        return WebhookRetryFactory.createRegistry(retryProperties);
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry(WebhookConfigProperties properties) {
        WebhookConfigProperties.TimeoutProperties timeoutProps = properties.getTimeout();
        long durationMillis = clampLong(timeoutProps.getDuration(), 1L, Long.MAX_VALUE);
        TimeLimiterConfig config = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(durationMillis))
            .cancelRunningFuture(true).build();
        return TimeLimiterRegistry.of(config);
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(WebhookConfigProperties properties) {
        WebhookConfigProperties.RateLimiterProperties rlProps = properties.getRateLimiter();
        int limitForPeriod = Math.max(rlProps.getLimitForPeriod(), 1);
        long refreshPeriodMs = Math.max(rlProps.getLimitRefreshPeriod(), 1L);
        long timeoutMs = Math.max(rlProps.getTimeoutDuration(), 0L);
        RateLimiterConfig config = RateLimiterConfig.custom().limitForPeriod(limitForPeriod)
            .limitRefreshPeriod(Duration.ofMillis(refreshPeriodMs)).timeoutDuration(Duration.ofMillis(timeoutMs))
            .build();
        return RateLimiterRegistry.of(config);
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry(WebhookConfigProperties properties) {
        WebhookConfigProperties.BulkheadProperties bhProps = properties.getBulkhead();
        int maxConcurrentCalls = Math.max(bhProps.getMaxConcurrentCalls(), 1);
        long maxWaitDurationMs = Math.max(bhProps.getMaxWaitDuration(), 0L);
        BulkheadConfig config = BulkheadConfig.custom().maxConcurrentCalls(maxConcurrentCalls)
            .maxWaitDuration(Duration.ofMillis(maxWaitDurationMs)).build();
        return BulkheadRegistry.of(config);
    }

    @Bean
    public NotificationListener notificationListener(RoutingPolicy routingPolicy, FormatterRegistry formatterRegistry,
        ObjectProvider<WebhookSender> senders, WebhookDispatcher dispatcher, DeadLetterProcessor deadLetterProcessor) {
        List<WebhookSender> senderList = senders.orderedStream().toList();
        return new NotificationListener(routingPolicy, formatterRegistry, senderList, dispatcher, deadLetterProcessor);
    }

    @Bean
    @ConditionalOnMissingBean(RoutingPolicy.class)
    public RoutingPolicy routingPolicy(WebhookConfigProperties properties) {
        return new ConfigBasedRoutingPolicy(properties);
    }

    @Bean
    public WebhookConfigResolver webhookConfigResolver(WebhookConfigProperties properties) {
        return new WebhookConfigResolver(properties);
    }

    @Bean
    @ConditionalOnBean(DeadLetterStore.class)
    @ConditionalOnMissingBean(DeadLetterHandler.class)
    @ConditionalOnProperty(prefix = "hookrouter.dead-letter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DeadLetterHandler storingDeadLetterHandler(DeadLetterStore store) {
        return new StoringDeadLetterHandler(store);
    }

    @Bean
    @ConditionalOnMissingBean({DeadLetterHandler.class, DeadLetterStore.class})
    @ConditionalOnProperty(prefix = "hookrouter.dead-letter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DeadLetterHandler defaultDeadLetterHandler() {
        return new LoggingDeadLetterHandler();
    }

    @Bean
    @ConditionalOnBean(DeadLetterStore.class)
    public DeadLetterReprocessor deadLetterReprocessor(DeadLetterStore store, NotificationPublisher publisher) {
        return new DeadLetterReprocessor(store, publisher);
    }

    @Bean
    @ConditionalOnBean(DeadLetterReprocessor.class)
    @ConditionalOnProperty(prefix = "hookrouter.dead-letter", name = "scheduler-enabled", havingValue = "true")
    public DeadLetterScheduler deadLetterScheduler(DeadLetterReprocessor reprocessor,
        WebhookConfigProperties properties) {
        return new DeadLetterScheduler(reprocessor, properties);
    }

    @Bean
    public DeadLetterProcessor deadLetterProcessor(ObjectProvider<DeadLetterHandler> deadLetterProvider,
        ObjectProvider<WebhookMetrics> metricsProvider) {
        DeadLetterHandler deadLetterHandler = deadLetterProvider.getIfAvailable(() -> NoOpDeadLetterHandler.INSTANCE);
        WebhookMetrics metrics = metricsProvider.getIfAvailable(() -> NoOpWebhookMetrics.INSTANCE);
        return new DeadLetterProcessor(deadLetterHandler, metrics);
    }

    @Bean
    public WebhookDispatcher webhookDispatcher(WebhookConfigResolver configResolver,
        CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry,
        TimeLimiterRegistry timeLimiterRegistry, RateLimiterRegistry rateLimiterRegistry,
        BulkheadRegistry bulkheadRegistry, ObjectProvider<WebhookMetrics> metricsProvider,
        DeadLetterProcessor deadLetterProcessor, ApplicationEventPublisher eventPublisher) {
        WebhookMetrics metrics = metricsProvider.getIfAvailable(() -> NoOpWebhookMetrics.INSTANCE);
        return new WebhookDispatcher(configResolver, circuitBreakerRegistry, retryRegistry, timeLimiterRegistry,
            rateLimiterRegistry, bulkheadRegistry, metrics, deadLetterProcessor, eventPublisher);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(WebhookMetrics.class)
        public WebhookMetrics webhookMetrics(MeterRegistry meterRegistry) {
            return new MicrometerWebhookMetrics(meterRegistry);
        }

        @Bean
        public Boolean resilience4jMetricsBinding(MeterRegistry meterRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry, RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry) {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry).bindTo(meterRegistry);
            TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry);
            TaggedTimeLimiterMetrics.ofTimeLimiterRegistry(timeLimiterRegistry).bindTo(meterRegistry);
            TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry).bindTo(meterRegistry);
            TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheadRegistry).bindTo(meterRegistry);
            return Boolean.TRUE;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class ActuatorConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "webhookHealthIndicator")
        public HealthIndicator webhookHealthIndicator(CircuitBreakerRegistry circuitBreakerRegistry,
            WebhookConfigProperties properties, Optional<DeadLetterStore> deadLetterStore) {

            Set<String> webhookKeys = properties.getPlatforms().entrySet().stream().flatMap(entry -> entry.getValue()
                .getEndpoints().keySet().stream().map(webhookKey -> ResilienceResourceKey.of(entry.getKey(), webhookKey)))
                .collect(Collectors.toSet());
            return new WebhookHealthIndicator(circuitBreakerRegistry, webhookKeys, deadLetterStore.orElse(null));
        }
    }

}
