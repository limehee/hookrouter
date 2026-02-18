package io.github.limehee.hookrouter.spring.async;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.AsyncProperties;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class WebhookAsyncConfig {

    private static final String VIRTUAL_THREADS_ENABLED_PROPERTY = "spring.threads.virtual.enabled";
    private final WebhookConfigProperties configProperties;
    private final Environment environment;

    public WebhookAsyncConfig(final WebhookConfigProperties configProperties, final Environment environment) {
        this.configProperties = configProperties;
        this.environment = environment;
    }

    @Bean(name = "webhookTaskExecutor")
    public Executor webhookTaskExecutor() {
        boolean virtualThreadsEnabled = environment.getProperty(VIRTUAL_THREADS_ENABLED_PROPERTY, Boolean.class, false);
        if (virtualThreadsEnabled && isVirtualThreadSupported()) {
            try {
                return createVirtualThreadExecutor();
            } catch (UnsupportedOperationException ignored) {
                return createPlatformThreadExecutor();
            }
        }
        return createPlatformThreadExecutor();
    }

    private Executor createVirtualThreadExecutor() {
        AsyncProperties async = configProperties.getAsync();
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setThreadNamePrefix(async.getThreadNamePrefix());
        executor.setTaskTerminationTimeout(async.getAwaitTerminationSeconds() * 1000L);
        return executor;
    }

    private Executor createPlatformThreadExecutor() {
        AsyncProperties async = configProperties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix(async.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(createRejectedExecutionHandler());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(async.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler createRejectedExecutionHandler() {
        return (Runnable r, ThreadPoolExecutor e) -> {
        };
    }

    private boolean isVirtualThreadSupported() {
        try {
            Thread.class.getMethod("ofVirtual");
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }
}
