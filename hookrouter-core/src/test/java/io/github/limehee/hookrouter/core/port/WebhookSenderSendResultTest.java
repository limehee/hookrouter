package io.github.limehee.hookrouter.core.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.core.port.WebhookSender.SendResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookSenderSendResultTest {

    @Nested
    class SuccessTest {

        @Test
        void shouldReturnNullResultSuccess() {
            // When
            SendResult result = SendResult.success(200);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.errorMessage()).isNull();
            assertThat(result.retryable()).isFalse();
            assertThat(result.retryAfterMillis()).isNull();
            assertThat(result.isRateLimited()).isFalse();
        }

        @Test
        void shouldMatchExpectedResult201Success() {
            // When
            SendResult result201 = SendResult.success(201);
            SendResult result204 = SendResult.success(204);

            // Then
            assertThat(result201.success()).isTrue();
            assertThat(result201.statusCode()).isEqualTo(201);
            assertThat(result204.success()).isTrue();
            assertThat(result204.statusCode()).isEqualTo(204);
        }
    }

    @Nested
    class FailureTest {

        @Test
        void shouldMatchExpectedResultSuccess() {
            // When
            SendResult result = SendResult.failure(400, "Bad Request", false);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(400);
            assertThat(result.errorMessage()).isEqualTo("Bad Request");
            assertThat(result.retryable()).isFalse();
        }

        @Test
        void shouldMatchExpectedResultSuccessWhenFailure() {
            // When
            SendResult result = SendResult.failure(503, "Service Unavailable", true);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(503);
            assertThat(result.errorMessage()).isEqualTo("Service Unavailable");
            assertThat(result.retryable()).isTrue();
        }
    }

    @Nested
    class NetworkErrorTest {

        @Test
        void shouldMatchExpectedResultSuccessWhenNetworkError() {
            // When
            SendResult result = SendResult.networkError("Connection refused");

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(-1);
            assertThat(result.errorMessage()).isEqualTo("Connection refused");
            assertThat(result.retryable()).isTrue();
        }

        @Test
        void shouldBeTrueResultRetryable() {
            // When
            SendResult result = SendResult.networkError("Timeout");

            // Then
            assertThat(result.retryable()).isTrue();
        }
    }

    @Nested
    class RateLimitedTest {

        @Test
        void shouldMatchExpectedResultSuccessWhenRateLimited() {
            // When
            SendResult result = SendResult.rateLimited("Too Many Requests", 60000L);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(429);
            assertThat(result.errorMessage()).isEqualTo("Too Many Requests");
            assertThat(result.retryable()).isTrue();
            assertThat(result.retryAfterMillis()).isEqualTo(60000L);
            assertThat(result.isRateLimited()).isTrue();
        }

        @Test
        void shouldReturnNullResultSuccessWhenRateLimited() {
            // When
            SendResult result = SendResult.rateLimited("Too Many Requests", null);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(429);
            assertThat(result.retryAfterMillis()).isNull();
            assertThat(result.isRateLimited()).isTrue();
        }

        @Test
        void shouldBeTrueResultRetryableWhenRateLimited() {
            // When
            SendResult result = SendResult.rateLimited("Rate limited", 30000L);

            // Then
            assertThat(result.retryable()).isTrue();
        }
    }

    @Nested
    class IsRateLimitedTest {

        @Test
        void shouldBeTrueResultRateLimited() {
            // When
            SendResult result = SendResult.failure(429, "Too Many Requests", true);

            // Then
            assertThat(result.isRateLimited()).isTrue();
        }

        @Test
        void shouldBeFalseSuccessRateLimited() {
            // When
            SendResult success = SendResult.success(200);
            SendResult failure400 = SendResult.failure(400, "Bad Request", false);
            SendResult failure500 = SendResult.failure(500, "Internal Server Error", true);

            // Then
            assertThat(success.isRateLimited()).isFalse();
            assertThat(failure400.isRateLimited()).isFalse();
            assertThat(failure500.isRateLimited()).isFalse();
        }
    }

    @Nested
    class EqualsHashCodeTest {

        @Test
        void shouldMatchExpectedResult1() {
            // Given
            SendResult result1 = SendResult.success(200);
            SendResult result2 = SendResult.success(200);

            // When & Then
            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        void shouldVerifyExpectedSuccess() {
            // Given
            SendResult success = SendResult.success(200);
            SendResult failure = SendResult.failure(400, "Error", false);

            // When & Then
            assertThat(success).isNotEqualTo(failure);
        }
    }
}
