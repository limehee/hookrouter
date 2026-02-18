package io.github.limehee.hookrouter.spring.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.limehee.hookrouter.core.domain.Notification;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @InjectMocks
    private NotificationPublisher notificationPublisher;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private record TestContext(String message) {

    }

    private record OrderContext(Integer orderId, String description) {

    }

    @Nested
    class PublishTest {

        @Test
        void shouldInvokeExpectedInteractions() {
            // Given
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .context(new TestContext("test message"))
                .build();

            // When
            notificationPublisher.publish(notification);

            // Then
            verify(eventPublisher).publishEvent(notification);
        }

        @Test
        void shouldMatchExpectedCapturedTypeId() {
            // Given
            OrderContext orderContext = new OrderContext(12345, "Order created");
            Notification<OrderContext> notification = Notification.<OrderContext>builder("demo.order.created")
                .category("general")
                .context(orderContext)
                .meta("orderId", 12345)
                .build();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Notification<OrderContext>> captor =
                ArgumentCaptor.forClass(Notification.class);

            // When
            notificationPublisher.publish(notification);

            // Then
            verify(eventPublisher).publishEvent(captor.capture());
            Notification<OrderContext> captured = captor.getValue();
            assertThat(captured.getTypeId()).isEqualTo("demo.order.created");
            assertThat(captured.getCategory()).isEqualTo("general");
            assertThat(captured.getContext().orderId()).isEqualTo(12345);
        }
    }

    @Nested
    class PublishAllTest {

        @Test
        void shouldMatchExpectedCount() {
            // Given
            Notification<TestContext> notification1 = Notification.<TestContext>builder("demo.test.event1")
                .category("general")
                .context(new TestContext("message1"))
                .build();
            Notification<OrderContext> notification2 = Notification.<OrderContext>builder("demo.order.created")
                .category("general")
                .context(new OrderContext(123, "Order created"))
                .build();
            List<Notification<?>> notifications = List.of(notification1, notification2);

            // When
            int count = notificationPublisher.publishAll(notifications);

            // Then
            assertThat(count).isEqualTo(2);
            verify(eventPublisher).publishEvent(notification1);
            verify(eventPublisher).publishEvent(notification2);
        }

        @Test
        void shouldNotInvokeUnexpectedInteractions() {
            // Given
            List<Notification<?>> notifications = Collections.emptyList();

            // When
            int count = notificationPublisher.publishAll(notifications);

            // Then
            assertThat(count).isZero();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldNotInvokeUnexpectedInteractionsWhenPublishAllIsNull() {
            // When
            int count = notificationPublisher.publishAll(null);

            // Then
            assertThat(count).isZero();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldMatchExpectedCountWhenCategory() {
            // Given
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .context(new TestContext("message"))
                .build();
            List<Notification<?>> notifications = Arrays.asList(notification, null, null);

            // When
            int count = notificationPublisher.publishAll(notifications);

            // Then
            assertThat(count).isEqualTo(1);
            verify(eventPublisher).publishEvent(notification);
        }

        @Test
        void shouldMatchExpectedCountUsingFactoryMethod() {
            // Given
            List<Notification<?>> notifications = List.of(
                Notification.<TestContext>builder("demo.test.1")
                    .category("general")
                    .context(new TestContext("1"))
                    .build(),
                Notification.<TestContext>builder("demo.test.2")
                    .category("general")
                    .context(new TestContext("2"))
                    .build(),
                Notification.<TestContext>builder("demo.test.3")
                    .category("general")
                    .context(new TestContext("3"))
                    .build()
            );

            // When
            int count = notificationPublisher.publishAll(notifications);

            // Then
            assertThat(count).isEqualTo(3);
        }
    }

    @Nested
    class PublishAllWithOptionsTest {

        @Test
        void shouldMatchExpectedResultPublishedCount() {
            // Given
            List<Notification<?>> notifications = createNotifications(10);
            BatchPublishOptions options = BatchPublishOptions.builder()
                .chunkSize(3)
                .build();

            // When
            BatchPublishResult result = notificationPublisher.publishAll(notifications, options);

            // Then
            assertThat(result.publishedCount()).isEqualTo(10);
            assertThat(result.chunkCount()).isEqualTo(4); // 3 + 3 + 3 + 1
            assertThat(result.totalCount()).isEqualTo(10);
            assertThat(result.isFullyPublished()).isTrue();
        }

        @Test
        void shouldMatchExpectedResultPublishedCountWhenChunkSizeIsPositive() {
            // Given
            List<Notification<?>> notifications = createNotifications(5);
            BatchPublishOptions options = BatchPublishOptions.builder()
                .chunkSize(100)
                .build();

            // When
            BatchPublishResult result = notificationPublisher.publishAll(notifications, options);

            // Then
            assertThat(result.publishedCount()).isEqualTo(5);
            assertThat(result.chunkCount()).isEqualTo(1);
        }

        @Test
        void shouldMatchExpectedResultPublishedCountWhenChunkSizeIsPositiveAndIsGreaterThanOrEqualTo() {
            // Given
            List<Notification<?>> notifications = createNotifications(6);
            BatchPublishOptions options = BatchPublishOptions.builder()
                .chunkSize(2)
                .delayBetweenChunks(Duration.ofMillis(10))
                .build();

            // When
            long startTime = System.currentTimeMillis();
            BatchPublishResult result = notificationPublisher.publishAll(notifications, options);
            long elapsedTime = System.currentTimeMillis() - startTime;

            // Then
            assertThat(result.publishedCount()).isEqualTo(6);
            assertThat(result.chunkCount()).isEqualTo(3); // 2 + 2 + 2

            assertThat(elapsedTime).isGreaterThanOrEqualTo(20);
        }

        @Test
        void shouldMatchExpectedResultPublishedCountWhenCategory() {
            // Given
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .context(new TestContext("message"))
                .build();
            List<Notification<?>> notifications = new ArrayList<>();
            notifications.add(notification);
            notifications.add(null);
            notifications.add(null);

            BatchPublishOptions options = BatchPublishOptions.builder()
                .chunkSize(10)
                .build();

            // When
            BatchPublishResult result = notificationPublisher.publishAll(notifications, options);

            // Then
            assertThat(result.publishedCount()).isEqualTo(1);
            assertThat(result.totalCount()).isEqualTo(3);
            assertThat(result.skippedCount()).isEqualTo(2);
            assertThat(result.isFullyPublished()).isFalse();
        }

        @Test
        void shouldVerifyExpectedResultPublishedCount() {
            // Given
            BatchPublishOptions options = BatchPublishOptions.builder().build();

            // When
            BatchPublishResult result = notificationPublisher.publishAll(Collections.emptyList(), options);

            // Then
            assertThat(result.publishedCount()).isZero();
            assertThat(result.chunkCount()).isZero();
            assertThat(result.totalCount()).isZero();
        }

        @Test
        void shouldMatchExpectedResultPublishedCountWhenPublishAll() {
            // Given
            List<Notification<?>> notifications = createNotifications(50);

            // When
            BatchPublishResult result = notificationPublisher.publishAll(notifications, BatchPublishOptions.DEFAULT);

            // Then
            assertThat(result.publishedCount()).isEqualTo(50);
            assertThat(result.chunkCount()).isEqualTo(1); // 50 < DEFAULT_CHUNK_SIZE(100)
        }

        @Test
        void shouldMatchExpectedResultTotalCount() {
            // Given
            LinkedHashSet<Notification<?>> notifications = new LinkedHashSet<>(createNotifications(5));
            BatchPublishOptions options = BatchPublishOptions.builder()
                .chunkSize(2)
                .build();

            // When
            BatchPublishResult result = notificationPublisher.publishAll(notifications, options);

            // Then
            assertThat(result.totalCount()).isEqualTo(5);
            assertThat(result.publishedCount()).isEqualTo(5);
            assertThat(result.chunkCount()).isEqualTo(3);
            assertThat(result.isFullyPublished()).isTrue();
        }

        @Test
        void shouldMatchExpectedResultTotalCountWhenChunkSizeIsPositive() {
            // Given
            List<Notification<?>> notifications = createNotifications(6);
            BatchPublishOptions options = BatchPublishOptions.builder()
                .chunkSize(3)
                .build();

            // When
            BatchPublishResult result = notificationPublisher.publishAll(notifications, options);

            // Then
            assertThat(result.totalCount()).isEqualTo(6);
            assertThat(result.publishedCount()).isEqualTo(6);
            assertThat(result.chunkCount()).isEqualTo(2);
        }

        private List<Notification<?>> createNotifications(int count) {
            List<Notification<?>> notifications = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                notifications.add(Notification.<TestContext>builder("demo.test.event." + i)
                    .category("general")
                    .context(new TestContext("message " + i))
                    .build());
            }
            return notifications;
        }
    }

}
