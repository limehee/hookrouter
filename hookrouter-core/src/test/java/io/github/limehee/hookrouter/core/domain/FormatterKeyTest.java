package io.github.limehee.hookrouter.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FormatterKeyTest {

    @Nested
    class OfTest {

        @Test
        void shouldMatchExpectedKeyPlatform() {
            // When
            FormatterKey key = FormatterKey.of("slack", "demo.server.error");

            // Then
            assertThat(key.platform()).isEqualTo("slack");
            assertThat(key.typeId()).isEqualTo("demo.server.error");
            assertThat(key.isFallback()).isFalse();
        }
    }

    @Nested
    class FallbackTest {

        @Test
        void shouldReturnNullKeyPlatform() {
            // When
            FormatterKey key = FormatterKey.fallback("slack");

            // Then
            assertThat(key.platform()).isEqualTo("slack");
            assertThat(key.typeId()).isNull();
            assertThat(key.isFallback()).isTrue();
        }
    }

    @Nested
    class EqualsTest {

        @Test
        void shouldMatchExpectedKey1() {
            // Given
            FormatterKey key1 = FormatterKey.of("slack", "demo.server.error");
            FormatterKey key2 = FormatterKey.of("slack", "demo.server.error");

            // Then
            assertThat(key1).isEqualTo(key2);
            assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
        }

        @Test
        void shouldMatchExpectedKey1WhenFallback() {
            // Given
            FormatterKey key1 = FormatterKey.fallback("slack");
            FormatterKey key2 = FormatterKey.fallback("slack");

            // Then
            assertThat(key1).isEqualTo(key2);
            assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
        }

        @Test
        void shouldVerifyExpectedSlackKey() {
            // Given
            FormatterKey slackKey = FormatterKey.of("slack", "demo.server.error");
            FormatterKey discordKey = FormatterKey.of("discord", "demo.server.error");

            // Then
            assertThat(slackKey).isNotEqualTo(discordKey);
        }

        @Test
        void shouldVerifyExpectedErrorKey() {
            // Given
            FormatterKey errorKey = FormatterKey.of("slack", "demo.server.error");
            FormatterKey startKey = FormatterKey.of("slack", "demo.server.start");

            // Then
            assertThat(errorKey).isNotEqualTo(startKey);
        }

        @Test
        void shouldVerifyExpectedSpecificKey() {
            // Given
            FormatterKey specificKey = FormatterKey.of("slack", "demo.server.error");
            FormatterKey fallbackKey = FormatterKey.fallback("slack");

            // Then
            assertThat(specificKey).isNotEqualTo(fallbackKey);
        }

        @Test
        void shouldBeFalseKeyEquals() {
            // Given
            FormatterKey key = FormatterKey.of("slack", "demo.server.error");

            // Then
            assertThat(key.equals(null)).isFalse();
        }

        @Test
        void shouldBeTrueEqualsKey() {
            // Given
            FormatterKey key = FormatterKey.of("slack", "demo.server.error");

            // Then
            assertThat(key.equals(key)).isTrue();
        }

        @Test
        void shouldBeFalseKeyEqualsUsingFactoryMethod() {
            // Given
            FormatterKey key = FormatterKey.of("slack", "demo.server.error");

            // Then
            assertThat(key.equals("not a FormatterKey")).isFalse();
        }
    }

    @Nested
    class ToStringTest {

        @Test
        void shouldContainExpectedResult() {
            // Given
            FormatterKey key = FormatterKey.of("slack", "demo.server.error");

            // When
            String result = key.toString();

            // Then
            assertThat(result).contains("slack");
            assertThat(result).contains("demo.server.error");
        }

        @Test
        void shouldContainExpectedResultWhenFallback() {
            // Given
            FormatterKey key = FormatterKey.fallback("slack");

            // When
            String result = key.toString();

            // Then
            assertThat(result).contains("slack");
            assertThat(result).contains("FALLBACK");
        }
    }
}
