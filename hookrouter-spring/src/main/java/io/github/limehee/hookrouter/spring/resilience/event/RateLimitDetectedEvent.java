package io.github.limehee.hookrouter.spring.resilience.event;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record RateLimitDetectedEvent(
    String platform,
    String webhookKey,
    String webhookUrl,
    String typeId,
    @Nullable Long retryAfterMillis,
    String errorMessage,
    Instant timestamp
) {

    public static RateLimitDetectedEvent of(
        String platform,
        String webhookKey,
        String webhookUrl,
        String typeId,
        @Nullable Long retryAfterMillis,
        String errorMessage
    ) {
        return new RateLimitDetectedEvent(
            platform, webhookKey, webhookUrl, typeId,
            retryAfterMillis, errorMessage, Instant.now()
        );
    }

    public boolean hasRetryAfter() {
        return retryAfterMillis != null && retryAfterMillis > 0;
    }
}
