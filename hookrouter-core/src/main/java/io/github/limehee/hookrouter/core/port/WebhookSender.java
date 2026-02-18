package io.github.limehee.hookrouter.core.port;

import org.jspecify.annotations.Nullable;

public interface WebhookSender {

    String platform();

    SendResult send(String webhookUrl, Object payload);

    record SendResult(
        boolean success,
        int statusCode,
        @Nullable String errorMessage,
        boolean retryable,
        @Nullable Long retryAfterMillis
    ) {

        public static final int HTTP_TOO_MANY_REQUESTS = 429;

        public static SendResult success(int statusCode) {
            return new SendResult(true, statusCode, null, false, null);
        }

        public static SendResult failure(int statusCode, String errorMessage, boolean retryable) {
            return new SendResult(false, statusCode, errorMessage, retryable, null);
        }

        public static SendResult rateLimited(String errorMessage, @Nullable Long retryAfterMillis) {
            return new SendResult(false, HTTP_TOO_MANY_REQUESTS, errorMessage, true, retryAfterMillis);
        }

        public static SendResult networkError(String errorMessage) {
            return new SendResult(false, -1, errorMessage, true, null);
        }

        public boolean isRateLimited() {
            return statusCode == HTTP_TOO_MANY_REQUESTS;
        }
    }
}
