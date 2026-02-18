package io.github.limehee.hookrouter.core.exception;

import java.io.Serial;

public class DuplicateFallbackFormatterException extends WebhookException {

    @Serial
    private static final long serialVersionUID = -2985714444425117181L;

    public DuplicateFallbackFormatterException(String platform) {
        super(String.format(
            "Duplicate fallback formatter registration detected for platform '%s'. "
                + "Each platform can have at most one fallback formatter.",
            platform
        ));
    }
}
