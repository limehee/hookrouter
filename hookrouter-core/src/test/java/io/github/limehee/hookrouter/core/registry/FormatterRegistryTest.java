package io.github.limehee.hookrouter.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.exception.DuplicateFallbackFormatterException;
import io.github.limehee.hookrouter.core.exception.DuplicateFormatterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FormatterRegistryTest {

    private FormatterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new FormatterRegistry();
    }

    private WebhookFormatter<String, String> createFormatter(String platform, String typeId) {
        return new WebhookFormatter<>() {
            @Override
            public FormatterKey key() {
                return FormatterKey.of(platform, typeId);
            }

            @Override
            public Class<String> contextClass() {
                return String.class;
            }

            @Override
            public String format(Notification<String> notification) {
                return "formatted: " + notification.getContext();
            }
        };
    }

    private WebhookFormatter<Object, String> createFallbackFormatter(String platform) {
        return new WebhookFormatter<>() {
            @Override
            public FormatterKey key() {
                return FormatterKey.fallback(platform);
            }

            @Override
            public Class<Object> contextClass() {
                return Object.class;
            }

            @Override
            public String format(Notification<Object> notification) {
                return "fallback: " + notification.getContext();
            }
        };
    }

    @Nested
    class RegisterTest {

        @Test
        void shouldMatchExpectedRegistryContains() {
            // Given
            WebhookFormatter<String, String> formatter = createFormatter("slack", "demo.server.error");

            // When
            registry.register(formatter);

            // Then
            assertThat(registry.contains("slack", "demo.server.error")).isTrue();
            assertThat(registry.size()).isEqualTo(1);
        }

        @Test
        void shouldMatchExpectedRegistryFallback() {
            // Given
            WebhookFormatter<Object, String> fallback = createFallbackFormatter("slack");

            // When
            registry.register(fallback);

            // Then
            assertThat(registry.hasFallback("slack")).isTrue();
            assertThat(registry.size()).isEqualTo(1);
        }

        @Test
        void shouldThrowDuplicateFormatterExceptionWhenInvalidInput() {
            // Given
            WebhookFormatter<String, String> formatter1 = createFormatter("slack", "demo.server.error");
            WebhookFormatter<String, String> formatter2 = createFormatter("slack", "demo.server.error");
            registry.register(formatter1);

            // When & Then
            assertThatThrownBy(() -> registry.register(formatter2))
                .isInstanceOf(DuplicateFormatterException.class)
                .hasMessageContaining("slack")
                .hasMessageContaining("demo.server.error");
        }

        @Test
        void shouldThrowDuplicateFallbackFormatterExceptionWhenInvalidInput() {
            // Given
            WebhookFormatter<Object, String> fallback1 = createFallbackFormatter("slack");
            WebhookFormatter<Object, String> fallback2 = createFallbackFormatter("slack");
            registry.register(fallback1);

            // When & Then
            assertThatThrownBy(() -> registry.register(fallback2))
                .isInstanceOf(DuplicateFallbackFormatterException.class)
                .hasMessageContaining("slack");
        }

        @Test
        void shouldMatchExpectedRegistryContainsWhenRegister() {
            // Given
            WebhookFormatter<String, String> slackFormatter = createFormatter("slack", "demo.server.error");
            WebhookFormatter<String, String> discordFormatter = createFormatter("discord", "demo.server.error");

            // When
            registry.register(slackFormatter);
            registry.register(discordFormatter);

            // Then
            assertThat(registry.contains("slack", "demo.server.error")).isTrue();
            assertThat(registry.contains("discord", "demo.server.error")).isTrue();
            assertThat(registry.size()).isEqualTo(2);
        }
    }

    @Nested
    class GetTest {

        @Test
        void shouldReturnNotNullResult() {
            // Given
            WebhookFormatter<String, String> formatter = createFormatter("slack", "demo.server.error");
            registry.register(formatter);

            // When
            var result = registry.get("slack", "demo.server.error");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.key()).isEqualTo(FormatterKey.of("slack", "demo.server.error"));
        }

        @Test
        void shouldReturnNotNullResultWhenRegister() {
            // Given
            WebhookFormatter<Object, String> fallback = createFallbackFormatter("slack");
            registry.register(fallback);

            // When
            var result = registry.getOrFallback("slack", "demo.non.existent");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isFallback()).isTrue();
        }

        @Test
        void shouldReturnNotNullResultWhenRegisterAndIsNotNull() {
            // Given
            WebhookFormatter<String, String> specific = createFormatter("slack", "demo.server.error");
            WebhookFormatter<Object, String> fallback = createFallbackFormatter("slack");
            registry.register(specific);
            registry.register(fallback);

            // When
            var result = registry.getOrFallback("slack", "demo.server.error");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isFallback()).isFalse();
            assertThat(result.key().typeId()).isEqualTo("demo.server.error");
        }

        @Test
        void shouldReturnNullResult() {
            // When
            var result = registry.getOrFallback("slack", "demo.non.existent");

            // Then
            assertThat(result).isNull();
        }
    }
}
