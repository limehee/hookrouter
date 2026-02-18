package io.github.limehee.hookrouter.core.exception;

import java.io.Serial;

public class DuplicateNotificationTypeException extends WebhookException {

    @Serial
    private static final long serialVersionUID = -470446999712093248L;

    public DuplicateNotificationTypeException(String typeId) {
        super(String.format(
            "Duplicate notification type registration detected for typeId '%s'. "
                + "Each typeId must be unique across all modules.",
            typeId
        ));
    }
}
