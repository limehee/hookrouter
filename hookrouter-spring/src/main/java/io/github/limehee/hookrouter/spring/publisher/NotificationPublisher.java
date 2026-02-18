package io.github.limehee.hookrouter.spring.publisher;

import io.github.limehee.hookrouter.core.domain.Notification;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

public class NotificationPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public NotificationPublisher(final ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public <T> void publish(Notification<T> notification) {
        eventPublisher.publishEvent(notification);
    }

    public int publishAll(Collection<? extends Notification<?>> notifications) {
        return publishAll(notifications, BatchPublishOptions.DEFAULT).publishedCount();
    }

    public BatchPublishResult publishAll(Collection<? extends Notification<?>> notifications,
        BatchPublishOptions options) {
        if (notifications == null || notifications.isEmpty()) {
            return BatchPublishResult.empty();
        }
        int totalCount = notifications.size();
        int chunkSize = options.getChunkSize();

        if (totalCount <= chunkSize && !options.hasDelay()) {
            int publishedCount = publishChunk(notifications);
            return BatchPublishResult.of(publishedCount, 1, totalCount);
        }

        List<? extends Notification<?>> notificationList = toList(notifications);
        int chunkCount = (totalCount + chunkSize - 1) / chunkSize;
        int publishedCount = 0;
        for (int i = 0; i < chunkCount; i++) {
            int fromIndex = i * chunkSize;
            int toIndex = Math.min(fromIndex + chunkSize, totalCount);
            List<? extends Notification<?>> chunk = notificationList.subList(fromIndex, toIndex);
            publishedCount += publishChunk(chunk);

            if (i < chunkCount - 1 && options.hasDelay()) {
                if (!sleepBetweenChunks(options)) {

                    return BatchPublishResult.of(publishedCount, i + 1, totalCount);
                }
            }
        }
        return BatchPublishResult.of(publishedCount, chunkCount, totalCount);
    }

    private int publishChunk(Collection<? extends Notification<?>> chunk) {
        int count = 0;
        for (Notification<?> notification : chunk) {
            if (notification != null) {
                publish(notification);
                count++;
            }
        }
        return count;
    }

    private boolean sleepBetweenChunks(BatchPublishOptions options) {
        Duration delay = options.getDelayBetweenChunks();
        if (delay == null) {
            return true;
        }
        try {
            Thread.sleep(delay.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private List<? extends Notification<?>> toList(Collection<? extends Notification<?>> collection) {
        if (collection instanceof List) {
            return (List<? extends Notification<?>>) collection;
        }
        return new ArrayList<>(collection);
    }
}
