# Architecture

```mermaid
flowchart LR
    A[Business Service] --> B[NotificationPublisher]
    B --> C[Spring Event]
    C --> D[NotificationListener]
    D --> E[RoutingPolicy]
    D --> F[FormatterRegistry]
    D --> G[WebhookDispatcher]
    G --> H[Resilience Layer]
    H --> I[WebhookSender]
    G --> J[DeadLetterProcessor]
```

Separation of concerns:

- `core`: stable contracts and domain model
- `spring`: runtime pipeline and operational features
- `adapter`: platform-specific sender/formatter implementations
