package io.github.limehee.hookrouter.spring.resilience;

import java.util.Objects;

public final class ResilienceResourceKey {

    private static final String SEPARATOR = ":";

    private ResilienceResourceKey() {
    }

    public static String of(String platform, String webhookKey) {
        return Objects.requireNonNull(platform, "platform must not be null")
            + SEPARATOR
            + Objects.requireNonNull(webhookKey, "webhookKey must not be null");
    }
}
