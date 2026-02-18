package io.github.limehee.hookrouter.core.exception;

import java.io.Serial;

public class NotificationTypeNotFoundException extends WebhookException {

    @Serial
    private static final long serialVersionUID = 3226124827745430267L;

    public NotificationTypeNotFoundException(String typeId) {
        super(String.format(
            "Notification type '%s' is not registered. "
                + "Please ensure the type is properly registered in NotificationTypeRegistry.",
            typeId
        ));
    }
}
