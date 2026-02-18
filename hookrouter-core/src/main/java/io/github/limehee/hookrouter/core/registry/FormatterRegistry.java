package io.github.limehee.hookrouter.core.registry;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.exception.DuplicateFallbackFormatterException;
import io.github.limehee.hookrouter.core.exception.DuplicateFormatterException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public class FormatterRegistry {

    private final Map<FormatterKey, WebhookFormatter<?, ?>> formatters = new ConcurrentHashMap<>();
    private final Map<String, WebhookFormatter<?, ?>> fallbackFormatters = new ConcurrentHashMap<>();

    public void register(WebhookFormatter<?, ?> formatter) {
        FormatterKey key = formatter.key();

        if (key.isFallback()) {
            registerFallback(formatter);
        } else {
            registerSpecific(formatter);
        }
    }

    public void registerAll(Collection<? extends WebhookFormatter<?, ?>> formatterList) {
        for (WebhookFormatter<?, ?> formatter : formatterList) {
            register(formatter);
        }
    }

    @Nullable
    public WebhookFormatter<?, ?> get(String platform, String typeId) {
        return formatters.get(FormatterKey.of(platform, typeId));
    }

    @Nullable
    public WebhookFormatter<?, ?> getOrFallback(String platform, String typeId) {
        WebhookFormatter<?, ?> specific = get(platform, typeId);
        if (specific == null) {
            return getFallback(platform);
        }
        return specific;
    }

    @Nullable
    public WebhookFormatter<?, ?> getFallback(String platform) {
        return fallbackFormatters.get(platform);
    }

    public boolean contains(String platform, String typeId) {
        return formatters.containsKey(FormatterKey.of(platform, typeId));
    }

    public boolean hasFallback(String platform) {
        return fallbackFormatters.containsKey(platform);
    }

    public Collection<FormatterKey> listKeys() {
        return Collections.unmodifiableCollection(formatters.keySet());
    }

    public Collection<String> listFallbackPlatforms() {
        return Collections.unmodifiableCollection(fallbackFormatters.keySet());
    }

    public int size() {
        return formatters.size() + fallbackFormatters.size();
    }

    private void registerSpecific(WebhookFormatter<?, ?> formatter) {
        FormatterKey key = formatter.key();
        WebhookFormatter<?, ?> existing = formatters.putIfAbsent(key, formatter);
        if (existing != null) {
            throw new DuplicateFormatterException(key.platform(), key.typeId());
        }
    }

    private void registerFallback(WebhookFormatter<?, ?> formatter) {
        String platform = formatter.platform();
        WebhookFormatter<?, ?> existing = fallbackFormatters.putIfAbsent(platform, formatter);
        if (existing != null) {
            throw new DuplicateFallbackFormatterException(platform);
        }
    }
}
