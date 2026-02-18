package io.github.limehee.hookrouter.spring.deadletter;

import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public class InMemoryDeadLetterStore implements DeadLetterStore {

    public static final int DEFAULT_MAX_SIZE = 10000;

    public static final int DEFAULT_MAX_RETRIES = 3;

    private static final long PROCESSING_TIMEOUT_MS = 5 * 60 * 1000L;
    private final Map<String, StoredDeadLetter> store = new ConcurrentHashMap<>();
    private final int maxSize;
    private final int maxRetries;

    public InMemoryDeadLetterStore() {
        this(DEFAULT_MAX_SIZE, DEFAULT_MAX_RETRIES);
    }

    public InMemoryDeadLetterStore(int maxSize, int maxRetries) {
        this.maxSize = maxSize;
        this.maxRetries = maxRetries;
    }

    @Override
    public synchronized StoredDeadLetter save(DeadLetter deadLetter) {
        if (store.size() >= maxSize) {
            ensureCapacity();
        }
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        StoredDeadLetter stored = new StoredDeadLetter(id, deadLetter, DeadLetterStatus.PENDING, 0, maxRetries, now,
            null, now, now);
        store.put(id, stored);
        return stored;
    }

    @Override
    public Optional<StoredDeadLetter> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<StoredDeadLetter> findByStatus(DeadLetterStatus status) {
        return store.values().stream().filter(dl -> dl.status() == status).toList();
    }

    @Override
    public List<StoredDeadLetter> findReadyForReprocess(int limit) {
        Instant now = Instant.now();
        Instant staleThreshold = now.minusMillis(PROCESSING_TIMEOUT_MS);
        return

            store.values().stream().filter(dl -> {
                if (dl.status() == DeadLetterStatus.PENDING) {
                    return dl.nextRetryAt() == null || !dl.nextRetryAt().isAfter(now);
                }
                if (dl.status() == DeadLetterStatus.PROCESSING) {
                    boolean isStale = dl.updatedAt().isBefore(staleThreshold);
                    if (isStale) {
                        updateStatus(dl.id(), DeadLetterStatus.PENDING);
                        return true;
                    }
                }
                return false;
            }).limit(limit).toList();
    }

    @Override
    public boolean updateStatus(String id, DeadLetterStatus status) {
        StoredDeadLetter result = store.computeIfPresent(id,
            (key, existing) -> new StoredDeadLetter(existing.id(), existing.deadLetter(), status, existing.retryCount(),
                existing.maxRetries(), existing.nextRetryAt(), existing.lastErrorMessage(), existing.createdAt(),
                Instant.now()));
        return result != null;
    }

    @Override
    public boolean updateRetryInfo(String id, int retryCount, @Nullable Instant nextRetryAt,
        @Nullable String lastErrorMessage) {
        StoredDeadLetter result = store.computeIfPresent(id, (key, existing) -> {
            DeadLetterStatus newStatus =
                retryCount >= existing.maxRetries() ? DeadLetterStatus.ABANDONED : DeadLetterStatus.PENDING;
            return new StoredDeadLetter(existing.id(), existing.deadLetter(), newStatus, retryCount,
                existing.maxRetries(), nextRetryAt, lastErrorMessage, existing.createdAt(), Instant.now());
        });
        return result != null;
    }

    @Override
    public boolean delete(String id) {
        return store.remove(id) != null;
    }

    @Override
    public int deleteOlderThan(Instant before) {
        List<String> toDelete = store.entrySet().stream().filter(e -> e.getValue().createdAt().isBefore(before))
            .map(Map.Entry::getKey).toList();
        toDelete.forEach(store::remove);
        return toDelete.size();
    }

    @Override
    public long countByStatus(DeadLetterStatus status) {
        return store.values().stream().filter(dl -> dl.status() == status).count();
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    private void ensureCapacity() {
        boolean evicted = evictOldestByStatus(DeadLetterStatus.RESOLVED);
        if (!evicted) {
            evicted = evictOldestByStatus(DeadLetterStatus.ABANDONED);
        }
        if (!evicted) {
            evicted = evictOldestAny();
        }
        if (!evicted) {
            throw new IllegalStateException("Failed to evict dead letter entry while store is full");
        }
    }

    private boolean evictOldestByStatus(DeadLetterStatus status) {

        List<Map.Entry<String, StoredDeadLetter>> candidates = store.entrySet().stream()
            .filter(e -> e.getValue().status() == status).toList();
        return candidates.stream().min(Comparator.comparing(e -> e.getValue().createdAt())).map(Map.Entry::getKey)
            .map(key -> store.remove(key) != null).orElse(false);
    }

    private boolean evictOldestAny() {
        return store.entrySet().stream().min(Comparator.comparing(e -> e.getValue().createdAt())).map(Map.Entry::getKey)
            .map(key -> store.remove(key) != null).orElse(false);
    }
}
