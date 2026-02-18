package io.github.limehee.hookrouter.core.domain;

public interface WebhookFormatter<T, R> {

    FormatterKey key();

    Class<T> contextClass();

    R format(Notification<T> notification);

    default boolean isFallback() {
        return key().isFallback();
    }

    default String platform() {
        return key().platform();
    }
}
