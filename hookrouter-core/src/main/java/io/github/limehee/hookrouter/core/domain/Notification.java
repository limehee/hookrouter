package io.github.limehee.hookrouter.core.domain;

import io.github.limehee.hookrouter.core.exception.InvalidWebhookArgumentException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class Notification<T> {

    private final String typeId;
    private final String category;
    private final Instant occurredAt;
    private final T context;
    private final Map<String, Object> meta;

    private Notification(String typeId, String category, Instant occurredAt, T context, Map<String, Object> meta) {
        this.typeId = typeId;
        this.category = category;
        this.occurredAt = occurredAt;
        this.context = context;
        this.meta = Map.copyOf(meta);
    }

    public static <T> Builder<T> builder(String typeId) {
        return new Builder<>(typeId);
    }

    public static <T> Notification<T> of(String typeId, String category, T context) {
        return new Builder<T>(typeId).category(category).context(context).build();
    }

    @Nullable
    public Object getMetaValue(String key) {
        return meta.get(key);
    }

    public String getTypeId() {
        return this.typeId;
    }

    public String getCategory() {
        return this.category;
    }

    public Instant getOccurredAt() {
        return this.occurredAt;
    }

    public T getContext() {
        return this.context;
    }

    public Map<String, Object> getMeta() {
        return this.meta;
    }

    @Override
    public String toString() {
        return "Notification(typeId=" + this.getTypeId() + ", category=" + this.getCategory() + ", occurredAt="
            + this.getOccurredAt() + ", context=" + this.getContext() + ", meta=" + this.getMeta() + ")";
    }

    public static final class Builder<T> {

        private final String typeId;
        private final Map<String, Object> meta = new HashMap<>();
        @Nullable
        private String category;
        @Nullable
        private Instant occurredAt;
        @Nullable
        private T context;

        private Builder(String typeId) {
            this.typeId = typeId;
        }

        private static boolean hasText(@Nullable String value) {
            return value != null && !value.isBlank();
        }

        public Builder<T> category(String category) {
            this.category = category;
            return this;
        }

        public Builder<T> occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder<T> context(T context) {
            this.context = context;
            return this;
        }

        public Builder<T> meta(String key, Object value) {
            if (!hasText(key)) {
                throw new InvalidWebhookArgumentException("meta key must not be null or blank");
            }
            this.meta.put(key, value);
            return this;
        }

        public Builder<T> meta(@Nullable Map<String, Object> metaMap) {
            if (metaMap == null || metaMap.isEmpty()) {
                return this;
            }

            for (String key : metaMap.keySet()) {
                if (!hasText(key)) {
                    throw new InvalidWebhookArgumentException("meta key must not be null or blank");
                }
            }
            this.meta.putAll(metaMap);
            return this;
        }

        public Notification<T> build() {
            if (!hasText(typeId)) {
                throw new InvalidWebhookArgumentException("typeId must not be null or blank");
            }
            if (!hasText(category)) {
                throw new InvalidWebhookArgumentException("category must not be null or blank");
            }
            if (context == null) {
                throw new InvalidWebhookArgumentException("context must not be null");
            }
            return new Notification<>(typeId, category, occurredAt != null ? occurredAt : Instant.now(), context, meta);
        }
    }
}
