package io.github.limehee.hookrouter.core.registry;

import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.exception.DuplicateNotificationTypeException;
import io.github.limehee.hookrouter.core.exception.NotificationTypeNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public class NotificationTypeRegistry {

    private final Map<String, NotificationTypeDefinition> registry = new ConcurrentHashMap<>();

    public void register(NotificationTypeDefinition definition) {
        String typeId = definition.typeId();
        NotificationTypeDefinition existing = registry.putIfAbsent(typeId, definition);
        if (existing != null) {
            throw new DuplicateNotificationTypeException(typeId);
        }
    }

    public void registerAll(Collection<NotificationTypeDefinition> definitions) {
        for (NotificationTypeDefinition definition : definitions) {
            register(definition);
        }
    }

    @Nullable
    public NotificationTypeDefinition find(String typeId) {
        return registry.get(typeId);
    }

    public NotificationTypeDefinition get(String typeId) {
        NotificationTypeDefinition definition = registry.get(typeId);
        if (definition == null) {
            throw new NotificationTypeNotFoundException(typeId);
        }
        return definition;
    }

    public boolean contains(String typeId) {
        return registry.containsKey(typeId);
    }

    public List<NotificationTypeDefinition> list() {
        return List.copyOf(registry.values());
    }

    public Collection<String> listTypeIds() {
        return Collections.unmodifiableCollection(registry.keySet());
    }

    public int size() {
        return registry.size();
    }

    public boolean isEmpty() {
        return registry.isEmpty();
    }
}
