package io.github.limehee.hookrouter.core.exception;

import java.io.Serial;

public class InvalidWebhookArgumentException extends WebhookException {

    @Serial
    private static final long serialVersionUID = -5138352932709373310L;

    public InvalidWebhookArgumentException(String message) {
        super(message);
    }

    public InvalidWebhookArgumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
