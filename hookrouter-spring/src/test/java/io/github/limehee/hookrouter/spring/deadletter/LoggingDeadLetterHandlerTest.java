package io.github.limehee.hookrouter.spring.deadletter;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggingDeadLetterHandlerTest {

    private final LoggingDeadLetterHandler handler = new LoggingDeadLetterHandler();

    @Test
    void shouldNotThrowExceptionDuringExecution() {
        DeadLetter deadLetter = createDeadLetter(FailureReason.MAX_RETRIES_EXCEEDED, "timeout", 3);

        assertThatCode(() -> handler.handle(deadLetter)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowExceptionDuringExecutionWhenHandle() {
        for (FailureReason reason : FailureReason.values()) {
            DeadLetter deadLetter = createDeadLetter(reason, null, 1);
            assertThatCode(() -> handler.handle(deadLetter)).doesNotThrowAnyException();
        }
    }

    private DeadLetter createDeadLetter(FailureReason reason, String errorMessage, int attemptCount) {
        Notification<String> notification = Notification.<String>builder("TEST_TYPE")
            .category("general")
            .context("test payload")
            .build();

        return new DeadLetter(
            notification,
            "slack",
            "test-channel",
            "https://hooks.slack.com/services/test",
            "payload",
            reason,
            errorMessage,
            attemptCount,
            Instant.now()
        );
    }
}
