package io.github.limehee.hookrouter.spring.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BatchPublishResultTest {

    @Nested
    class FactoryMethodTest {

        @Test
        void shouldVerifyExpectedResultPublishedCount() {
            // When
            BatchPublishResult result = BatchPublishResult.empty();

            // Then
            assertThat(result.publishedCount()).isZero();
            assertThat(result.chunkCount()).isZero();
            assertThat(result.totalCount()).isZero();
        }

        @Test
        void shouldMatchExpectedResultPublishedCount() {
            // When
            BatchPublishResult result = BatchPublishResult.of(10, 2, 12);

            // Then
            assertThat(result.publishedCount()).isEqualTo(10);
            assertThat(result.chunkCount()).isEqualTo(2);
            assertThat(result.totalCount()).isEqualTo(12);
        }

        @Test
        void shouldMatchExpectedResultPublishedCountWhenInputIsPositive() {
            // When
            BatchPublishResult result = new BatchPublishResult(5, 1, 5);

            // Then
            assertThat(result.publishedCount()).isEqualTo(5);
            assertThat(result.chunkCount()).isEqualTo(1);
            assertThat(result.totalCount()).isEqualTo(5);
        }
    }

    @Nested
    class SkippedCountTest {

        @Test
        void shouldVerifyExpectedSkippedCount() {
            // Given
            BatchPublishResult result = BatchPublishResult.of(10, 2, 10);

            // When
            int skippedCount = result.skippedCount();

            // Then
            assertThat(skippedCount).isZero();
        }

        @Test
        void shouldMatchExpectedSkippedCount() {
            // Given
            BatchPublishResult result = BatchPublishResult.of(8, 2, 10);

            // When
            int skippedCount = result.skippedCount();

            // Then
            assertThat(skippedCount).isEqualTo(2);
        }

        @Test
        void shouldVerifyExpectedSkippedCountWhenResultIsEmpty() {
            // Given
            BatchPublishResult result = BatchPublishResult.empty();

            // When
            int skippedCount = result.skippedCount();

            // Then
            assertThat(skippedCount).isZero();
        }
    }

    @Nested
    class IsFullyPublishedTest {

        @Test
        void shouldBeTrueFullyPublished() {
            // Given
            BatchPublishResult result = BatchPublishResult.of(10, 2, 10);

            // When
            boolean fullyPublished = result.isFullyPublished();

            // Then
            assertThat(fullyPublished).isTrue();
        }

        @Test
        void shouldBeFalseFullyPublished() {
            // Given
            BatchPublishResult result = BatchPublishResult.of(8, 2, 10);

            // When
            boolean fullyPublished = result.isFullyPublished();

            // Then
            assertThat(fullyPublished).isFalse();
        }

        @Test
        void shouldBeTrueFullyPublishedWhenResultIsEmpty() {
            // Given
            BatchPublishResult result = BatchPublishResult.empty();

            // When
            boolean fullyPublished = result.isFullyPublished();

            // Then
            assertThat(fullyPublished).isTrue();
        }
    }

    @Nested
    class RecordEqualityTest {

        @Test
        void shouldMatchExpectedResult1() {
            // Given
            BatchPublishResult result1 = BatchPublishResult.of(10, 2, 12);
            BatchPublishResult result2 = BatchPublishResult.of(10, 2, 12);

            // Then
            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        void shouldVerifyExpectedResult1() {
            // Given
            BatchPublishResult result1 = BatchPublishResult.of(10, 2, 12);
            BatchPublishResult result2 = BatchPublishResult.of(10, 2, 10);

            // Then
            assertThat(result1).isNotEqualTo(result2);
        }
    }
}
