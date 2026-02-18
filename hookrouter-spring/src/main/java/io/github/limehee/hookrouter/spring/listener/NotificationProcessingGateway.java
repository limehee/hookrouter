package io.github.limehee.hookrouter.spring.listener;

import io.github.limehee.hookrouter.core.domain.Notification;
import org.jspecify.annotations.Nullable;

public interface NotificationProcessingGateway {

    <T> ProcessingResult process(Notification<T> notification);

    record ProcessingResult(boolean success, @Nullable String errorMessage) {

        public static ProcessingResult ok() {
            return new ProcessingResult(true, null);
        }

        public static ProcessingResult failed(String errorMessage) {
            return new ProcessingResult(false, errorMessage);
        }
    }
}
