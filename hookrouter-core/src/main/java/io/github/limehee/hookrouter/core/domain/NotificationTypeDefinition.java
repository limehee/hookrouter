package io.github.limehee.hookrouter.core.domain;

import io.github.limehee.hookrouter.core.exception.InvalidWebhookArgumentException;
import org.jspecify.annotations.Nullable;

public record NotificationTypeDefinition(
    String typeId,
    String title,
    String defaultMessage,
    String category,
    @Nullable String schemaVersion
) {

    public NotificationTypeDefinition {
        if (!hasText(typeId)) {
            throw new InvalidWebhookArgumentException("typeId must not be null or blank");
        }
        if (!hasText(title)) {
            throw new InvalidWebhookArgumentException("title must not be null or blank");
        }
        if (!hasText(defaultMessage)) {
            throw new InvalidWebhookArgumentException("defaultMessage must not be null or blank");
        }
        if (!hasText(category)) {
            throw new InvalidWebhookArgumentException("category must not be null or blank");
        }
    }

    public NotificationTypeDefinition(String typeId, String title, String defaultMessage, String category) {
        this(typeId, title, defaultMessage, category, null);
    }

    public static NotificationTypeDefinition.Builder builder() {
        return new NotificationTypeDefinition.Builder();
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    public static class Builder {

        private String typeId;
        private String title;
        private String defaultMessage;
        private String category;
        private String schemaVersion;

        Builder() {
        }

        public NotificationTypeDefinition.Builder typeId(final String typeId) {
            this.typeId = typeId;
            return this;
        }

        public NotificationTypeDefinition.Builder title(final String title) {
            this.title = title;
            return this;
        }

        public NotificationTypeDefinition.Builder defaultMessage(final String defaultMessage) {
            this.defaultMessage = defaultMessage;
            return this;
        }

        public NotificationTypeDefinition.Builder category(final String category) {
            this.category = category;
            return this;
        }

        public NotificationTypeDefinition.Builder schemaVersion(@Nullable final String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public NotificationTypeDefinition build() {
            return new NotificationTypeDefinition(this.typeId, this.title, this.defaultMessage, this.category,
                this.schemaVersion);
        }

        @Override
        public String toString() {
            return "NotificationTypeDefinition.Builder(typeId=" + this.typeId + ", title=" + this.title
                + ", defaultMessage=" + this.defaultMessage + ", category=" + this.category + ", schemaVersion="
                + this.schemaVersion + ")";
        }
    }
}
