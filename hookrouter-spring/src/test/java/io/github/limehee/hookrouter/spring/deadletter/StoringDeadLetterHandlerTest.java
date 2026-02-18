package io.github.limehee.hookrouter.spring.deadletter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.DeadLetterStatus;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.StoredDeadLetter;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoringDeadLetterHandlerTest {

    @InjectMocks
    private StoringDeadLetterHandler handler;

    @Mock
    private DeadLetterStore store;

    private DeadLetter createDeadLetter() {
        return createDeadLetterWithReason(FailureReason.MAX_RETRIES_EXCEEDED);
    }

    private DeadLetter createDeadLetterWithReason(FailureReason reason) {
        Notification<String> notification = Notification.<String>builder("TEST_TYPE")
            .category("general")
            .context("test payload")
            .build();

        return DeadLetter.of(
            notification,
            "slack",
            "test-channel",
            "https://hooks.slack.com/services/test",
            "payload",
            reason,
            "Test error",
            3
        );
    }

    private DeadLetter createDeadLetterForPlatform(String platform) {
        Notification<String> notification = Notification.<String>builder("TEST_TYPE")
            .category("general")
            .context("test payload")
            .build();

        return DeadLetter.of(
            notification,
            platform,
            "test-channel",
            "https://hooks.example.com/test",
            "payload",
            FailureReason.MAX_RETRIES_EXCEEDED,
            "Test error",
            3
        );
    }

    private StoredDeadLetter createStoredDeadLetter(DeadLetter deadLetter) {
        return new StoredDeadLetter(
            "test-id",
            deadLetter,
            DeadLetterStatus.PENDING,
            0,
            3,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    @Nested
    class HandleTest {

        @Test
        void shouldInvokeExpectedInteractions() {
            // Given
            DeadLetter deadLetter = createDeadLetter();
            StoredDeadLetter storedDeadLetter = createStoredDeadLetter(deadLetter);

            given(store.save(any(DeadLetter.class))).willReturn(storedDeadLetter);

            // When
            handler.handle(deadLetter);

            // Then
            verify(store).save(deadLetter);
        }

        @Test
        void shouldInvokeExpectedInteractionsWhenSave() {
            // Given
            for (FailureReason reason : FailureReason.values()) {
                DeadLetter deadLetter = createDeadLetterWithReason(reason);
                StoredDeadLetter storedDeadLetter = createStoredDeadLetter(deadLetter);

                given(store.save(any(DeadLetter.class))).willReturn(storedDeadLetter);

                // When
                handler.handle(deadLetter);

                // Then
                verify(store).save(deadLetter);
            }
        }

        @Test
        void shouldInvokeExpectedInteractionsWhenSavingDeadLettersFromMultiplePlatforms() {
            // Given
            String[] platforms = {"slack", "discord", "teams", "custom"};

            for (String platform : platforms) {
                DeadLetter deadLetter = createDeadLetterForPlatform(platform);
                StoredDeadLetter storedDeadLetter = createStoredDeadLetter(deadLetter);

                given(store.save(any(DeadLetter.class))).willReturn(storedDeadLetter);

                // When
                handler.handle(deadLetter);

                // Then
                verify(store).save(deadLetter);
            }
        }
    }
}
