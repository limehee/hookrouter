package io.github.limehee.hookrouter.spring.deadletter;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class DeadLetterScheduler implements InitializingBean, DisposableBean {

    private final DeadLetterReprocessor reprocessor;
    private final WebhookConfigProperties properties;
    private final ScheduledExecutorService scheduler;
    @Nullable
    @SuppressWarnings("unused")
    private ScheduledFuture<?> scheduledTask;

    public DeadLetterScheduler(DeadLetterReprocessor reprocessor, WebhookConfigProperties properties) {
        this.reprocessor = reprocessor;
        this.properties = properties;
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "dead-letter-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void afterPropertiesSet() {
        var deadLetterProps = properties.getDeadLetter();
        long interval = deadLetterProps.getSchedulerInterval();
        this.scheduledTask = scheduler.scheduleWithFixedDelay(this::reprocessDeadLetters, interval, interval,
            TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();

                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                }
            } else {
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void reprocessDeadLetters() {
        try {
            int batchSize = properties.getDeadLetter().getSchedulerBatchSize();
            reprocessor.reprocessPending(batchSize);
        } catch (Exception e) {
        }
    }
}
