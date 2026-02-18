package io.github.limehee.hookrouter.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.limehee.hookrouter.core.exception.InvalidWebhookArgumentException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private record TestContext(String message) {

    }

    @Nested
    class BuilderTest {

        @Test
        void shouldMatchExpectedNotificationTypeId() {
            // Given
            Instant occurredAt = Instant.parse("2025-01-20T10:00:00Z");
            TestContext context = new TestContext("test message");

            // When
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .occurredAt(occurredAt)
                .context(context)
                .meta("requestId", "req-123")
                .meta("actorId", 42)
                .build();

            // Then
            assertThat(notification.getTypeId()).isEqualTo("demo.test.event");
            assertThat(notification.getCategory()).isEqualTo("general");
            assertThat(notification.getOccurredAt()).isEqualTo(occurredAt);
            assertThat(notification.getContext()).isEqualTo(context);
            assertThat(notification.getMeta()).hasSize(2);
            assertThat(notification.getMetaValue("requestId")).isEqualTo("req-123");
            assertThat(notification.getMetaValue("actorId")).isEqualTo(42);
        }

        @Test
        void shouldBeWithinExpectedTimeRangeNotificationGetOccurredAt() {
            // Given
            Instant before = Instant.now();
            TestContext context = new TestContext("test");

            // When
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .context(context)
                .build();

            // Then
            Instant after = Instant.now();
            assertThat(notification.getOccurredAt()).isAfterOrEqualTo(before);
            assertThat(notification.getOccurredAt()).isBeforeOrEqualTo(after);
        }

        @Test
        void shouldContainExpectedNotificationMeta() {
            // Given
            Map<String, Object> metaMap = Map.of(
                "key1", "value1",
                "key2", 123
            );
            TestContext context = new TestContext("test");

            // When
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .context(context)
                .meta(metaMap)
                .build();

            // Then
            assertThat(notification.getMeta()).containsAllEntriesOf(metaMap);
        }

        @Test
        void shouldBeEmptyNotificationMeta() {
            // When
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .context(new TestContext("test"))
                .build();

            // Then
            assertThat(notification.getMeta()).isEmpty();
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenInvalidInput() {
            // When & Then
            assertThatThrownBy(() -> Notification.<TestContext>builder(null)
                .category("general")
                .context(new TestContext("test"))
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenContextIsMissing() {
            // When & Then
            assertThatThrownBy(() -> Notification.<TestContext>builder("   ")
                .category("general")
                .context(new TestContext("test"))
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenInvalidInputAndContext() {
            // When & Then
            assertThatThrownBy(() -> Notification.<TestContext>builder("demo.test.event")
                .context(new TestContext("test"))
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenInvalidInputAndCategory() {
            // When & Then
            assertThatThrownBy(() -> Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }
    }

    @Nested
    class OfTest {

        @Test
        void shouldReturnNotNullNotificationTypeId() {
            // Given
            TestContext context = new TestContext("Simple creation");

            // When
            Notification<TestContext> notification = Notification.of(
                "demo.test.simple",
                "general",
                context
            );

            // Then
            assertThat(notification.getTypeId()).isEqualTo("demo.test.simple");
            assertThat(notification.getCategory()).isEqualTo("general");
            assertThat(notification.getContext()).isEqualTo(context);
            assertThat(notification.getOccurredAt()).isNotNull();
            assertThat(notification.getMeta()).isEmpty();
        }
    }

    @Nested
    class GetMetaTest {

        @Test
        void shouldThrowUnsupportedOperationExceptionWhenInvalidInput() {
            // Given
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .context(new TestContext("test"))
                .meta("key", "value")
                .build();

            // When & Then
            Map<String, Object> meta = notification.getMeta();
            assertThatThrownBy(() -> meta.put("newKey", "newValue"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldReturnNullResult() {
            // Given
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .context(new TestContext("test"))
                .build();

            // When
            Object result = notification.getMetaValue("nonExistentKey");

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    class ToStringTest {

        @Test
        void shouldContainExpectedResult() {
            // Given
            TestContext context = new TestContext("test");
            Notification<TestContext> notification = Notification.<TestContext>builder("demo.test.event")
                .category("general")
                .context(context)
                .meta("requestId", "123")
                .build();

            // When
            String result = notification.toString();

            // Then
            assertThat(result).contains("demo.test.event");
            assertThat(result).contains("general");
            assertThat(result).contains("requestId");
            assertThat(result).contains("123");
        }
    }
}
