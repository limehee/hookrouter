# Dead Letter Guide

This guide explains how dead-letter processing works in `hookrouter-spring`, how to enable persistent storage, and how to reprocess failed notifications safely.

## 1. Runtime behavior by bean/config combination

| Condition                                                        | DeadLetterHandler used                 | Storage                   | Reprocess support             |
|------------------------------------------------------------------|----------------------------------------|---------------------------|-------------------------------|
| `hookrouter.dead-letter.enabled=false`                           | no dead-letter handler bean is created | no                        | no                            |
| `enabled=true`, custom `DeadLetterHandler` bean exists           | custom handler                         | depends on custom handler | depends on custom handler     |
| `enabled=true`, `DeadLetterStore` bean exists, no custom handler | `StoringDeadLetterHandler`             | yes (`DeadLetterStore`)   | yes (`DeadLetterReprocessor`) |
| `enabled=true`, no `DeadLetterStore`, no custom handler          | `LoggingDeadLetterHandler`             | no                        | no                            |

Notes:

- `DeadLetterReprocessor` is auto-created only when a `DeadLetterStore` bean exists.
- `DeadLetterScheduler` is auto-created only when:
  - `DeadLetterReprocessor` exists
  - `hookrouter.dead-letter.scheduler-enabled=true`
- Reprocess marks an item `RESOLVED` only when replay delivery actually succeeds.

## 2. Configuration keys

```yaml
hookrouter:
  dead-letter:
    enabled: true
    max-retries: 3
    scheduler-enabled: false
    scheduler-interval: 60000
    scheduler-batch-size: 50
```

Key meanings:

- `enabled`: enables dead-letter handling path.
- `max-retries`: desired maximum reprocess retries.
- `scheduler-enabled`: enables periodic reprocessing.
- `scheduler-interval`: reprocess interval in milliseconds.
- `scheduler-batch-size`: max items per scheduler run.

## 3. Minimal persistent setup (Spring)

If you want actual storage and replay, register a `DeadLetterStore` bean.

```java
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore;
import io.github.limehee.hookrouter.spring.deadletter.InMemoryDeadLetterStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeadLetterConfig {

    @Bean
    public DeadLetterStore deadLetterStore(WebhookConfigProperties properties) {
        int maxRetries = Math.max(properties.getDeadLetter().getMaxRetries(), 1);
        return new InMemoryDeadLetterStore(InMemoryDeadLetterStore.DEFAULT_MAX_SIZE, maxRetries);
    }
}
```

Why this matters:

- `InMemoryDeadLetterStore` constructor controls `maxRetries`.
- Wiring it from `hookrouter.dead-letter.max-retries` keeps runtime behavior aligned with YAML.
- When in-memory storage reaches capacity, it evicts oldest `RESOLVED`/`ABANDONED` first, then oldest remaining entry.

## 4. Automatic reprocessing

Enable scheduler to replay pending entries automatically:

```yaml
hookrouter:
  dead-letter:
    enabled: true
    scheduler-enabled: true
    scheduler-interval: 60000
    scheduler-batch-size: 100
```

## 5. Manual reprocessing API/service

You can expose manual replay controls from your application service layer:

```java
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterReprocessor;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.DeadLetterStatus;
import org.springframework.stereotype.Service;

@Service
public class DeadLetterOperationsService {

    private final DeadLetterStore store;
    private final DeadLetterReprocessor reprocessor;

    public DeadLetterOperationsService(DeadLetterStore store, DeadLetterReprocessor reprocessor) {
        this.store = store;
        this.reprocessor = reprocessor;
    }

    public long pendingCount() {
        return store.countByStatus(DeadLetterStatus.PENDING);
    }

    public DeadLetterReprocessor.ReprocessSummary reprocessPending(int batchSize) {
        return reprocessor.reprocessPending(batchSize);
    }

    public DeadLetterReprocessor.ReprocessResult reprocessById(String id) {
        return reprocessor.reprocessById(id);
    }
}
```

## 6. Failure reasons captured in dead letters

`DeadLetterHandler.FailureReason` values:

- `MAX_RETRIES_EXCEEDED`
- `NON_RETRYABLE_ERROR`
- `CIRCUIT_OPEN`
- `RATE_LIMITED`
- `BULKHEAD_FULL`
- `EXCEPTION`
- `FORMATTER_NOT_FOUND`
- `PAYLOAD_CREATION_FAILED`
- `SENDER_NOT_FOUND`

## 7. Operational checklist

- For production, do not rely on logging-only mode; register a persistent `DeadLetterStore`.
- Expose dead-letter status metrics or API by status (`PENDING`, `PROCESSING`, `RESOLVED`, `ABANDONED`).
- Configure scheduler interval and batch size based on downstream webhook capacity.
- Add retention cleanup for old resolved/abandoned entries in persistent storage.
