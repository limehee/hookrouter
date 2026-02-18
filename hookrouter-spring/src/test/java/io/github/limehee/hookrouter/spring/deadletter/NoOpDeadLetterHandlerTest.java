package io.github.limehee.hookrouter.spring.deadletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NoOpDeadLetterHandlerTest {

    private DeadLetter createDeadLetter() {
        Notification<TestContext> notification = Notification.of(
            "test-type",
            "general",
            new TestContext("test-data")
        );

        return DeadLetter.of(
            notification,
            "slack",
            "slack-key",
            "https://hooks.slack.com/test",
            Map.of("text", "Hello"),
            FailureReason.RATE_LIMITED,
            "Test error message",
            1
        );
    }

    private record TestContext(String data) {

    }

    @Nested
    class InstanceTest {

        @Test
        void shouldReturnNotNullNoOpDeadLetterHandlerINSTANCE() {
            // When & Then
            assertThat(NoOpDeadLetterHandler.INSTANCE).isNotNull();
        }

        @Test
        void shouldBeExpectedTypeNoOpDeadLetterHandlerINSTANCE() {
            // When & Then
            assertThat(NoOpDeadLetterHandler.INSTANCE).isInstanceOf(NoOpDeadLetterHandler.class);
            assertThat(NoOpDeadLetterHandler.INSTANCE).isInstanceOf(DeadLetterHandler.class);
        }
    }

    @Nested
    class HandleTest {

        @Test
        void shouldNotThrowExceptionDuringExecution() {
            // Given
            DeadLetter deadLetter = createDeadLetter();

            // When & Then
            assertThatCode(() -> NoOpDeadLetterHandler.INSTANCE.handle(deadLetter))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenHandleIsNull() {
            // When & Then
            assertThatCode(() -> NoOpDeadLetterHandler.INSTANCE.handle(null))
                .doesNotThrowAnyException();
        }
    }
}
