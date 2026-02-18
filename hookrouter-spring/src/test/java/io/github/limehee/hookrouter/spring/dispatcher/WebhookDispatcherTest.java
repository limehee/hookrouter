package io.github.limehee.hookrouter.spring.dispatcher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.core.port.WebhookSender.SendResult;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.BulkheadProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.CircuitBreakerProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RateLimiterProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.RetryProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.TimeoutProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigResolver;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterProcessor;
import io.github.limehee.hookrouter.spring.metrics.WebhookMetrics;
import io.github.limehee.hookrouter.spring.resilience.WebhookRetryFactory;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    private WebhookDispatcher dispatcher;

    @Mock
    private WebhookSender slackSender;

    @Mock
    private WebhookMetrics metrics;

    @Mock
    private DeadLetterProcessor deadLetterProcessor;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WebhookConfigProperties configProperties;
    private WebhookConfigResolver configResolver;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private TimeLimiterRegistry timeLimiterRegistry;
    private RateLimiterRegistry rateLimiterRegistry;
    private BulkheadRegistry bulkheadRegistry;

    @BeforeEach
    void setUp() {
        lenient().when(slackSender.platform()).thenReturn("slack");

        configProperties = createDefaultConfigProperties();
        configResolver = new WebhookConfigResolver(configProperties);

        circuitBreakerRegistry = createCircuitBreakerRegistry();
        retryRegistry = WebhookRetryFactory.createRegistry(configProperties.getRetry());
        timeLimiterRegistry = createTimeLimiterRegistry();
        rateLimiterRegistry = createRateLimiterRegistry();
        bulkheadRegistry = createBulkheadRegistry();

        dispatcher = createDispatcher();
    }

    private WebhookConfigProperties createDefaultConfigProperties() {
        WebhookConfigProperties props = new WebhookConfigProperties();
        props.setRetry(createDefaultRetryProperties());
        props.setTimeout(createDefaultTimeoutProperties());
        props.setRateLimiter(createDefaultRateLimiterProperties());
        props.setBulkhead(createDefaultBulkheadProperties());
        props.setCircuitBreaker(createDefaultCircuitBreakerProperties());
        return props;
    }

    private WebhookDispatcher createDispatcher() {
        configResolver = new WebhookConfigResolver(configProperties);
        return new WebhookDispatcher(
            configResolver,
            circuitBreakerRegistry,
            retryRegistry,
            timeLimiterRegistry,
            rateLimiterRegistry,
            bulkheadRegistry,
            metrics,
            deadLetterProcessor,
            eventPublisher
        );
    }

    private RetryProperties createDefaultRetryProperties() {
        RetryProperties props = new RetryProperties();
        props.setEnabled(true);
        props.setMaxAttempts(3);
        props.setInitialDelay(10);
        props.setMaxDelay(100);
        props.setMultiplier(2.0);
        props.setJitterFactor(0.0);
        return props;
    }

    // === Helper Methods ===

    private TimeoutProperties createDefaultTimeoutProperties() {
        TimeoutProperties props = new TimeoutProperties();
        props.setEnabled(false);
        props.setDuration(5000);
        return props;
    }

    private RateLimiterProperties createDefaultRateLimiterProperties() {
        RateLimiterProperties props = new RateLimiterProperties();
        props.setEnabled(false);
        props.setLimitForPeriod(50);
        props.setLimitRefreshPeriod(1000);
        props.setTimeoutDuration(0);
        return props;
    }

    private BulkheadProperties createDefaultBulkheadProperties() {
        BulkheadProperties props = new BulkheadProperties();
        props.setEnabled(false);
        props.setMaxConcurrentCalls(25);
        props.setMaxWaitDuration(0);
        return props;
    }

    private CircuitBreakerProperties createDefaultCircuitBreakerProperties() {
        CircuitBreakerProperties props = new CircuitBreakerProperties();
        props.setEnabled(true);
        props.setFailureThreshold(3);
        props.setFailureRateThreshold(50.0f);
        props.setWaitDuration(1000L);
        props.setSuccessThreshold(2);
        return props;
    }

    private CircuitBreakerRegistry createCircuitBreakerRegistry() {
        CircuitBreakerProperties cbProps = configProperties.getCircuitBreaker();
        int failureThreshold = Math.max(cbProps.getFailureThreshold(), 1);
        int successThreshold = Math.max(cbProps.getSuccessThreshold(), 1);
        long waitDurationMillis = Math.max(cbProps.getWaitDuration(), 1L);
        float failureRateThreshold = cbProps.getFailureRateThreshold();

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(failureThreshold)
            .minimumNumberOfCalls(failureThreshold)
            .failureRateThreshold(failureRateThreshold)
            .waitDurationInOpenState(Duration.ofMillis(waitDurationMillis))
            .permittedNumberOfCallsInHalfOpenState(successThreshold)
            .build();

        return CircuitBreakerRegistry.of(config);
    }

    private TimeLimiterRegistry createTimeLimiterRegistry() {
        long durationMillis = Math.max(configProperties.getTimeout().getDuration(), 1L);
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(durationMillis))
            .cancelRunningFuture(true)
            .build();
        return TimeLimiterRegistry.of(config);
    }

    private RateLimiterRegistry createRateLimiterRegistry() {
        RateLimiterProperties rlProps = configProperties.getRateLimiter();
        int limitForPeriod = Math.max(rlProps.getLimitForPeriod(), 1);
        long refreshPeriodMs = Math.max(rlProps.getLimitRefreshPeriod(), 1L);
        long timeoutMs = Math.max(rlProps.getTimeoutDuration(), 0L);

        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(limitForPeriod)
            .limitRefreshPeriod(Duration.ofMillis(refreshPeriodMs))
            .timeoutDuration(Duration.ofMillis(timeoutMs))
            .build();
        return RateLimiterRegistry.of(config);
    }

    private BulkheadRegistry createBulkheadRegistry() {
        BulkheadProperties bhProps = configProperties.getBulkhead();
        int maxConcurrentCalls = Math.max(bhProps.getMaxConcurrentCalls(), 1);
        long maxWaitDurationMs = Math.max(bhProps.getMaxWaitDuration(), 0L);

        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(maxConcurrentCalls)
            .maxWaitDuration(Duration.ofMillis(maxWaitDurationMs))
            .build();
        return BulkheadRegistry.of(config);
    }

    private Notification<TestContext> createNotification(String typeId) {
        return Notification.of(typeId, "general", new TestContext("test-data"));
    }

    private RoutingTarget createRoutingTarget(String platform, String webhookKey, String webhookUrl) {
        return new RoutingTarget(platform, webhookKey, webhookUrl);
    }

    private record TestContext(String data) {

    }

    @Nested
    class DispatchTest {

        @Nested
        class SuccessScenarioTest {

            @Test
            void shouldNotInvokeUnexpectedInteractions() {
                // Given
                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                given(slackSender.send(anyString(), any())).willReturn(SendResult.success(200));

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                // Then
                verify(slackSender).send("https://hooks.slack.com/test", payload);
                verify(metrics).recordSendAttempt("slack", "slack-key", "test-type");
                verify(metrics).recordSendSuccess(eq("slack"), eq("slack-key"), eq("test-type"), any(Duration.class));
                verify(deadLetterProcessor, never()).processSendFailure(any(), any(), any(), any(), any(Integer.class));
            }
        }

        @Nested
        class FailureScenarioTest {

            @Test
            void shouldInvokeExpectedInteractions() {
                // Given
                configProperties.getRetry().setEnabled(false);
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                SendResult failureResult = SendResult.failure(500, "Internal Server Error", false);
                given(slackSender.send(anyString(), any())).willReturn(failureResult);

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                // Then
                verify(deadLetterProcessor).processSendFailure(
                    eq(notification), eq(target), eq(payload), eq(failureResult), eq(1));
                verify(metrics).recordSendFailure(
                    eq("slack"), eq("slack-key"), eq("test-type"), eq("Internal Server Error"), any(Duration.class));
            }
        }

        @Nested
        class RateLimiterTest {

            @Test
            void shouldInvokeExpectedInteractionsWhenEnabledIsFalse() {
                // Given
                configProperties.getRateLimiter().setEnabled(false);
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                given(slackSender.send(anyString(), any())).willReturn(SendResult.success(200));

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                // Then
                verify(slackSender).send(anyString(), any());
            }

            @Test
            void shouldInvokeExpectedInteractionsWhenEnabledIsTrue() {
                // Given
                configProperties.getRateLimiter().setEnabled(true);
                configProperties.getRateLimiter().setLimitForPeriod(1);
                configProperties.getRateLimiter().setLimitRefreshPeriod(60000);
                configProperties.getRateLimiter().setTimeoutDuration(0);

                rateLimiterRegistry = createRateLimiterRegistry();
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                given(slackSender.send(anyString(), any())).willReturn(SendResult.success(200));

                dispatcher.dispatch(notification, target, slackSender, payload);

                dispatcher.dispatch(notification, target, slackSender, payload);

                verify(slackSender, times(1)).send(anyString(), any());
                verify(metrics).recordSendRateLimited("slack", "slack-key", "test-type");
                verify(deadLetterProcessor).processRateLimited(notification, target, payload);
            }
        }

        @Nested
        class BulkheadTest {

            @Test
            void shouldInvokeExpectedInteractionsWhenEnabledIsFalse() {
                // Given
                configProperties.getBulkhead().setEnabled(false);
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                given(slackSender.send(anyString(), any())).willReturn(SendResult.success(200));

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                // Then
                verify(slackSender).send(anyString(), any());
            }

            @Test
            void shouldNotInvokeUnexpectedInteractionsWhenEnabledIsTrue() {

                configProperties.getBulkhead().setEnabled(true);
                configProperties.getBulkhead().setMaxConcurrentCalls(1);
                configProperties.getBulkhead().setMaxWaitDuration(0);

                bulkheadRegistry = createBulkheadRegistry();
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "bulkhead-test-key",
                    "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                given(slackSender.send(anyString(), any())).willReturn(SendResult.success(200));

                dispatcher.dispatch(notification, target, slackSender, payload);

                dispatcher.dispatch(notification, target, slackSender, payload);

                verify(slackSender, times(2)).send(anyString(), any());
                verify(metrics, times(2)).recordSendSuccess(eq("slack"), eq("bulkhead-test-key"), eq("test-type"),
                    any(Duration.class));

                verify(metrics, never()).recordSendBulkheadFull(anyString(), anyString(), anyString());
            }

            @Test
            void shouldNotInvokeUnexpectedInteractionsWhenEnabledIsTrueWithEnabledTrue() {

                configProperties.getBulkhead().setEnabled(true);
                configProperties.getBulkhead().setMaxConcurrentCalls(1);
                configProperties.getBulkhead().setMaxWaitDuration(0);
                configProperties.getRetry().setEnabled(false);

                bulkheadRegistry = createBulkheadRegistry();
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "bulkhead-exception-key",
                    "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                given(slackSender.send(anyString(), any()))
                    .willThrow(new RuntimeException("Connection refused"))
                    .willReturn(SendResult.success(200));

                dispatcher.dispatch(notification, target, slackSender, payload);

                dispatcher.dispatch(notification, target, slackSender, payload);

                verify(slackSender, times(2)).send(anyString(), any());

                verify(metrics, never()).recordSendBulkheadFull(anyString(), anyString(), anyString());
            }

            @Test
            void shouldNotInvokeUnexpectedInteractionsWhenBulkheadPermissionCannotBeAcquired() {

                configProperties.getBulkhead().setEnabled(true);
                configProperties.getBulkhead().setMaxConcurrentCalls(1);
                configProperties.getBulkhead().setMaxWaitDuration(0);

                bulkheadRegistry = createBulkheadRegistry();
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "bulkhead-full-key",
                    "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                var bulkhead = bulkheadRegistry.bulkhead("bulkhead-full-key");
                bulkhead.tryAcquirePermission();

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                verify(slackSender, never()).send(anyString(), any());
                verify(metrics).recordSendBulkheadFull("slack", "bulkhead-full-key", "test-type");
                verify(deadLetterProcessor).processBulkheadFull(notification, target, payload);
            }
        }

        @Nested
        class CircuitBreakerTest {

            @Test
            void shouldInvokeExpectedInteractionsWhenEnabledIsFalse() {
                // Given
                configProperties.getCircuitBreaker().setEnabled(false);
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                given(slackSender.send(anyString(), any())).willReturn(SendResult.success(200));

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                // Then
                verify(slackSender).send(anyString(), any());
            }

            @Test
            void shouldNotInvokeUnexpectedInteractionsWhenEnabledIsTrue() {
                // Given
                configProperties.getCircuitBreaker().setEnabled(true);
                configProperties.getCircuitBreaker().setFailureThreshold(2);
                configProperties.getCircuitBreaker().setFailureRateThreshold(50.0f);

                circuitBreakerRegistry = createCircuitBreakerRegistry();
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("slack-key");
                circuitBreaker.transitionToOpenState();

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                verify(slackSender, never()).send(anyString(), any());
                verify(metrics).recordSendSkipped("slack", "slack-key", "test-type");

                verify(deadLetterProcessor, never()).processRateLimited(any(), any(), any());
            }
        }

        @Nested
        class RetryTest {

            @Test
            void shouldInvokeExpectedInteractionsWhenEnabledIsFalse() {
                // Given
                configProperties.getRetry().setEnabled(false);
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                SendResult failureResult = SendResult.failure(500, "Internal Server Error", true);
                given(slackSender.send(anyString(), any())).willReturn(failureResult);

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                verify(slackSender, times(1)).send(anyString(), any());
            }

            @Test
            void shouldInvokeExpectedInteractionsWhenEnabledIsTrue() {
                // Given
                configProperties.getRetry().setEnabled(true);
                configProperties.getRetry().setMaxAttempts(2);
                configProperties.getRetry().setInitialDelay(1);
                configProperties.getRetry().setJitterFactor(0.0);
                retryRegistry = WebhookRetryFactory.createRegistry(configProperties.getRetry());
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                given(slackSender.send(anyString(), any()))
                    .willReturn(SendResult.failure(503, "Service Unavailable", true))
                    .willReturn(SendResult.success(200));

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                verify(slackSender, times(2)).send(anyString(), any());
                verify(metrics).recordSendSuccess(eq("slack"), eq("slack-key"), eq("test-type"), any(Duration.class));
            }

            @Test
            void shouldInvokeExpectedInteractionsWhenEnabledIsTrueWithEnabledTrue() {
                // Given
                configProperties.getRetry().setEnabled(true);
                configProperties.getRetry().setMaxAttempts(3);
                retryRegistry = WebhookRetryFactory.createRegistry(configProperties.getRetry());
                dispatcher = createDispatcher();

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                Map<String, Object> payload = Map.of("text", "Hello");

                SendResult nonRetryableFailure = SendResult.failure(400, "Bad Request", false);
                given(slackSender.send(anyString(), any())).willReturn(nonRetryableFailure);

                // When
                dispatcher.dispatch(notification, target, slackSender, payload);

                verify(slackSender, times(1)).send(anyString(), any());
                verify(deadLetterProcessor).processSendFailure(
                    eq(notification), eq(target), eq(payload), eq(nonRetryableFailure), eq(1));
            }
        }
    }
}
