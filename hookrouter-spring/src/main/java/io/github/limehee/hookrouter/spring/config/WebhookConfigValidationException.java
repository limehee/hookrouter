package io.github.limehee.hookrouter.spring.config;

import java.util.Collections;
import java.util.List;

public class WebhookConfigValidationException extends RuntimeException {

    private final List<String> validationErrors;

    public WebhookConfigValidationException(String message) {
        super(message);
        this.validationErrors = List.of(message);
    }

    public WebhookConfigValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = Collections.unmodifiableList(validationErrors);
    }

    public List<String> getValidationErrors() {
        return this.validationErrors;
    }
}
