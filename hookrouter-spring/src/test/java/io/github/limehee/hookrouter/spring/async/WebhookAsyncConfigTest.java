package io.github.limehee.hookrouter.spring.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.AsyncProperties;
import io.github.limehee.hookrouter.spring.metrics.WebhookMetrics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class WebhookAsyncConfigTest {

    private static final String VIRTUAL_THREADS_PROPERTY = "spring.threads.virtual.enabled";

    @InjectMocks
    private WebhookAsyncConfig config;

    @Mock
    private WebhookConfigProperties configProperties;

    @Mock
    private Environment environment;

    @Mock
    private ObjectProvider<WebhookMetrics> webhookMetricsProvider;

    @Mock
    private WebhookMetrics webhookMetrics;

    private AsyncProperties createDefaultAsyncProperties() {
        AsyncProperties async = new AsyncProperties();
        async.setCorePoolSize(2);
        async.setMaxPoolSize(10);
        async.setQueueCapacity(100);
        async.setThreadNamePrefix("hookrouter-");
        async.setAwaitTerminationSeconds(30);
        return async;
    }

    private boolean isVirtualThreadSupported() {
        try {
            Thread.class.getMethod("ofVirtual");
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    @Nested
    class PlatformThreadExecutorTest {

        @Test
        void shouldReturnNotNullExecutor() {
            // Given
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(false);
            given(configProperties.getAsync()).willReturn(createDefaultAsyncProperties());

            // When
            Executor executor = config.webhookTaskExecutor();

            // Then
            assertThat(executor).isNotNull();
            assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        }

        @Test
        void shouldMatchExpectedExecutorCorePoolSize() {
            // Given
            AsyncProperties async = createDefaultAsyncProperties();
            async.setCorePoolSize(4);
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(false);
            given(configProperties.getAsync()).willReturn(async);

            // When
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.webhookTaskExecutor();

            // Then
            assertThat(executor.getCorePoolSize()).isEqualTo(4);
        }

        @Test
        void shouldMatchExpectedExecutorMaxPoolSize() {
            // Given
            AsyncProperties async = createDefaultAsyncProperties();
            async.setMaxPoolSize(20);
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(false);
            given(configProperties.getAsync()).willReturn(async);

            // When
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.webhookTaskExecutor();

            // Then
            assertThat(executor.getMaxPoolSize()).isEqualTo(20);
        }

        @Test
        void shouldMatchExpectedExecutorThreadNamePrefix() {
            // Given
            AsyncProperties async = createDefaultAsyncProperties();
            async.setThreadNamePrefix("custom-hookrouter-");
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(false);
            given(configProperties.getAsync()).willReturn(async);

            // When
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.webhookTaskExecutor();

            // Then
            assertThat(executor.getThreadNamePrefix()).isEqualTo("custom-hookrouter-");
        }

        @Test
        void shouldMatchExpectedExecutorQueueCapacity() {
            // Given
            AsyncProperties async = createDefaultAsyncProperties();
            async.setQueueCapacity(200);
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(false);
            given(configProperties.getAsync()).willReturn(async);

            // When
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.webhookTaskExecutor();

            // Then
            assertThat(executor.getQueueCapacity()).isEqualTo(200);
        }

        @Test
        void shouldMatchExpectedExecutorCorePoolSizeWhenExecutorPropertyIsApplied() {
            // Given
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(false);
            given(configProperties.getAsync()).willReturn(createDefaultAsyncProperties());

            // When
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.webhookTaskExecutor();

            // Then
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(10);
            assertThat(executor.getQueueCapacity()).isEqualTo(100);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("hookrouter-");
        }

        @Test
        void shouldUseCallerRunsPolicyForRejectedExecution() {
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(false);
            given(configProperties.getAsync()).willReturn(createDefaultAsyncProperties());

            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.webhookTaskExecutor();

            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler()).isNotNull();
        }

        @Test
        void shouldRunTaskInCallerThreadWhenPoolIsSaturated() throws Exception {
            AsyncProperties async = createDefaultAsyncProperties();
            async.setCorePoolSize(1);
            async.setMaxPoolSize(1);
            async.setQueueCapacity(0);
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(false);
            given(configProperties.getAsync()).willReturn(async);

            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.webhookTaskExecutor();
            CountDownLatch firstTaskStarted = new CountDownLatch(1);
            CountDownLatch releaseFirstTask = new CountDownLatch(1);

            executor.execute(() -> {
                firstTaskStarted.countDown();
                try {
                    releaseFirstTask.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertThat(firstTaskStarted.await(1, TimeUnit.SECONDS)).isTrue();
            String callerThreadName = Thread.currentThread().getName();
            AtomicReference<String> secondTaskThreadName = new AtomicReference<>();

            executor.execute(() -> secondTaskThreadName.set(Thread.currentThread().getName()));
            releaseFirstTask.countDown();

            assertThat(secondTaskThreadName.get()).isEqualTo(callerThreadName);
            executor.shutdown();
        }

        @Test
        void shouldRecordCallerRunsMetricWhenPoolIsSaturated() throws Exception {
            AsyncProperties async = createDefaultAsyncProperties();
            async.setCorePoolSize(1);
            async.setMaxPoolSize(1);
            async.setQueueCapacity(0);
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(false);
            given(configProperties.getAsync()).willReturn(async);
            given(webhookMetricsProvider.getIfAvailable()).willReturn(webhookMetrics);

            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.webhookTaskExecutor();
            CountDownLatch firstTaskStarted = new CountDownLatch(1);
            CountDownLatch releaseFirstTask = new CountDownLatch(1);

            executor.execute(() -> {
                firstTaskStarted.countDown();
                try {
                    releaseFirstTask.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertThat(firstTaskStarted.await(1, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {
            });
            releaseFirstTask.countDown();

            verify(webhookMetrics).recordAsyncCallerRuns();
            executor.shutdown();
        }
    }

    @Nested
    class VirtualThreadExecutorTest {

        @Test
        void shouldReturnNotNullExecutorWhenExecutorPropertyIsApplied() {
            // Given
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(true);
            given(configProperties.getAsync()).willReturn(createDefaultAsyncProperties());

            // When
            Executor executor = config.webhookTaskExecutor();

            // Then
            assertThat(executor).isNotNull();
            if (isVirtualThreadSupported()) {
                assertThat(executor).isInstanceOf(SimpleAsyncTaskExecutor.class);
            } else {
                assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
            }
        }

        @Test
        void shouldMatchExpectedExecutorThreadNamePrefixWhenThreadNamePrefix() {
            // Given
            AsyncProperties async = createDefaultAsyncProperties();
            async.setThreadNamePrefix("virtual-hookrouter-");
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(true);
            given(configProperties.getAsync()).willReturn(async);

            // When
            Executor executor = config.webhookTaskExecutor();

            // Then
            if (isVirtualThreadSupported()) {
                assertThat(((SimpleAsyncTaskExecutor) executor).getThreadNamePrefix()).isEqualTo("virtual-hookrouter-");
            } else {
                assertThat(((ThreadPoolTaskExecutor) executor).getThreadNamePrefix()).isEqualTo("virtual-hookrouter-");
            }
        }

        @Test
        void shouldReturnNotNullExecutorWhenAwaitTerminationSecondsIsPositive() {
            // Given
            AsyncProperties async = createDefaultAsyncProperties();
            async.setAwaitTerminationSeconds(60);
            given(environment.getProperty(VIRTUAL_THREADS_PROPERTY, Boolean.class, false))
                .willReturn(true);
            given(configProperties.getAsync()).willReturn(async);

            // When
            Executor executor = config.webhookTaskExecutor();

            // Then

            assertThat(executor).isNotNull();
            if (isVirtualThreadSupported()) {
                assertThat(((SimpleAsyncTaskExecutor) executor).isActive()).isTrue();
            } else {
                assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
            }
        }
    }
}
