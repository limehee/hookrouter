# Extension Points

Primary extension interfaces:

- `WebhookSender`: platform delivery implementation
- `WebhookFormatter<T, R>`: domain context to platform payload mapping
- `RoutingPolicy`: custom routing strategy
- `DeadLetterHandler` / `DeadLetterStore`: failure handling strategy
- `WebhookMetrics`: metric collection strategy

Recommendation:

- Implement contracts in `hookrouter-core`
- Register implementations as Spring beans in application modules
