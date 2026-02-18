package io.github.limehee.hookrouter.spring.resilience.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateLimitDetectedEventTest {

    @Nested
    class OfTest {

        @Test
        void shouldReturnNotNullEventPlatform() {
            // When
            RateLimitDetectedEvent event = RateLimitDetectedEvent.of(
                "slack",
                "error-channel",
                "https://hooks.slack.com/xxx",
                "demo.server.error",
                60000L,
                "Too Many Requests"
            );

            // Then
            assertThat(event.platform()).isEqualTo("slack");
            assertThat(event.webhookKey()).isEqualTo("error-channel");
            assertThat(event.webhookUrl()).isEqualTo("https://hooks.slack.com/xxx");
            assertThat(event.typeId()).isEqualTo("demo.server.error");
            assertThat(event.retryAfterMillis()).isEqualTo(60000L);
            assertThat(event.errorMessage()).isEqualTo("Too Many Requests");
            assertThat(event.timestamp()).isNotNull();
        }

        @Test
        void shouldReturnNullEventPlatform() {
            // When
            RateLimitDetectedEvent event = RateLimitDetectedEvent.of(
                "discord",
                "alerts",
                "https://discord.com/api/webhooks/xxx",
                "demo.order.created",
                null,
                "Rate Limited"
            );

            // Then
            assertThat(event.platform()).isEqualTo("discord");
            assertThat(event.retryAfterMillis()).isNull();
        }
    }

    @Nested
    class HasRetryAfterTest {

        @Test
        void shouldBeTrueEventRetryAfter() {
            // Given
            RateLimitDetectedEvent event = RateLimitDetectedEvent.of(
                "slack", "channel", "url", "typeId", 30000L, "error"
            );

            // When & Then
            assertThat(event.hasRetryAfter()).isTrue();
        }

        @Test
        void shouldBeFalseEventRetryAfter() {
            // Given
            RateLimitDetectedEvent event = RateLimitDetectedEvent.of(
                "slack", "channel", "url", "typeId", null, "error"
            );

            // When & Then
            assertThat(event.hasRetryAfter()).isFalse();
        }

        @Test
        void shouldBeFalseEventRetryAfterUsingFactoryMethod() {
            // Given
            RateLimitDetectedEvent event = RateLimitDetectedEvent.of(
                "slack", "channel", "url", "typeId", 0L, "error"
            );

            // When & Then
            assertThat(event.hasRetryAfter()).isFalse();
        }

        @Test
        void shouldBeFalseEventRetryAfterWhenRetryAfterIsNegative() {
            // Given
            RateLimitDetectedEvent event = RateLimitDetectedEvent.of(
                "slack", "channel", "url", "typeId", -1L, "error"
            );

            // When & Then
            assertThat(event.hasRetryAfter()).isFalse();
        }
    }
}
