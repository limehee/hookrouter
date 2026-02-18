package io.github.limehee.hookrouter.core.exception;

import java.io.Serial;

public abstract class WebhookException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -7320543382781909215L;

    protected WebhookException(String message) {
        super(message);
    }

    protected WebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
