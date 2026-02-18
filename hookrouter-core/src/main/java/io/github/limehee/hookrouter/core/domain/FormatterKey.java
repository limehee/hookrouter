package io.github.limehee.hookrouter.core.domain;

import org.jspecify.annotations.Nullable;

public record FormatterKey(
    String platform,
    @Nullable String typeId
) {

    public static FormatterKey of(String platform, String typeId) {
        return new FormatterKey(platform, typeId);
    }

    public static FormatterKey fallback(String platform) {
        return new FormatterKey(platform, null);
    }

    public boolean isFallback() {
        return typeId == null;
    }

    @Override
    public String toString() {
        return "FormatterKey{platform='" + platform + "', typeId='" + (typeId != null ? typeId : "FALLBACK") + "'}";
    }
}
