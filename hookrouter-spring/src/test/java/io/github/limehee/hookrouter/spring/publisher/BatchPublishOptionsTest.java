package io.github.limehee.hookrouter.spring.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BatchPublishOptionsTest {

    @Nested
    class BuilderTest {

        @Test
        void shouldReturnNullOptionsChunkSize() {
            // When
            BatchPublishOptions options = BatchPublishOptions.builder().build();

            // Then
            assertThat(options.getChunkSize()).isEqualTo(BatchPublishOptions.DEFAULT_CHUNK_SIZE);
            assertThat(options.getDelayBetweenChunks()).isNull();
            assertThat(options.hasDelay()).isFalse();
        }

        @Test
        void shouldMatchExpectedOptionsChunkSize() {
            // When
            BatchPublishOptions options = BatchPublishOptions.builder()
                .chunkSize(50)
                .build();

            // Then
            assertThat(options.getChunkSize()).isEqualTo(50);
        }

        @Test
        void shouldMatchExpectedOptionsDelayBetweenChunks() {
            // When
            BatchPublishOptions options = BatchPublishOptions.builder()
                .delayBetweenChunks(Duration.ofMillis(100))
                .build();

            // Then
            assertThat(options.getDelayBetweenChunks()).isEqualTo(Duration.ofMillis(100));
            assertThat(options.hasDelay()).isTrue();
        }

        @Test
        void shouldMatchExpectedOptionsChunkSizeWhenChunkSizeIsPositive() {
            // When
            BatchPublishOptions options = BatchPublishOptions.builder()
                .chunkSize(25)
                .delayBetweenChunks(Duration.ofMillis(50))
                .build();

            // Then
            assertThat(options.getChunkSize()).isEqualTo(25);
            assertThat(options.getDelayBetweenChunks()).isEqualTo(Duration.ofMillis(50));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionWhenInvalidInput() {
            // When & Then
            assertThatThrownBy(() -> BatchPublishOptions.builder().chunkSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1");
        }

        @Test
        void shouldThrowIllegalArgumentExceptionWhenInvalidInputAndChunkSizeIsNegative() {
            // When & Then
            assertThatThrownBy(() -> BatchPublishOptions.builder().chunkSize(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1");
        }
    }

    @Nested
    class HasDelayTest {

        @Test
        void shouldBeFalseOptionsDelay() {
            // Given
            BatchPublishOptions options = BatchPublishOptions.builder()
                .delayBetweenChunks(null)
                .build();

            // Then
            assertThat(options.hasDelay()).isFalse();
        }

        @Test
        void shouldBeFalseOptionsDelayWhenDelayBetweenChunks() {
            // Given
            BatchPublishOptions options = BatchPublishOptions.builder()
                .delayBetweenChunks(Duration.ZERO)
                .build();

            // Then
            assertThat(options.hasDelay()).isFalse();
        }

        @Test
        void shouldBeFalseOptionsDelayWhenDelayBetweenChunksIsNegative() {
            // Given
            BatchPublishOptions options = BatchPublishOptions.builder()
                .delayBetweenChunks(Duration.ofMillis(-100))
                .build();

            // Then
            assertThat(options.hasDelay()).isFalse();
        }

        @Test
        void shouldBeTrueOptionsDelay() {
            // Given
            BatchPublishOptions options = BatchPublishOptions.builder()
                .delayBetweenChunks(Duration.ofMillis(1))
                .build();

            // Then
            assertThat(options.hasDelay()).isTrue();
        }
    }

    @Nested
    class DefaultConstantTest {

        @Test
        void shouldReturnNullDEFAULTChunkSize() {
            // Then
            assertThat(BatchPublishOptions.DEFAULT.getChunkSize())
                .isEqualTo(BatchPublishOptions.DEFAULT_CHUNK_SIZE);
            assertThat(BatchPublishOptions.DEFAULT.getDelayBetweenChunks()).isNull();
            assertThat(BatchPublishOptions.DEFAULT.hasDelay()).isFalse();
        }
    }
}
