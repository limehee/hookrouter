# Configuration Reference

Root prefix: `hookrouter.*`

## 1. Routing Keys

- `hookrouter.platforms.<platform>.endpoints.<webhookKey>.url`
- `hookrouter.type-mappings`
- `hookrouter.category-mappings`
- `hookrouter.default-mappings`

Routing priority:

1. `type-mappings`
2. `category-mappings`
3. `default-mappings`

## 2. Resilience Keys

- `hookrouter.retry.*`
- `hookrouter.timeout.*`
- `hookrouter.circuit-breaker.*`
- `hookrouter.rate-limiter.*`
- `hookrouter.bulkhead.*`

## 3. Runtime/Operations Keys

- `hookrouter.dead-letter.*`
- `hookrouter.async.*`

## 4. Full Example (`application.yml`)

```yaml
hookrouter:
  platforms:
    slack:
      endpoints:
        general:
          url: "https://hooks.slack.com/services/..."
        critical:
          url: "https://hooks.slack.com/services/..."
          retry:
            enabled: true
            max-attempts: 5
            initial-delay: 500
            max-delay: 1000
            multiplier: 2.0
            jitter-factor: 0.1
          timeout:
            enabled: true
            duration: 1500
    discord:
      endpoints:
        ops:
          url: "https://discord.com/api/webhooks/..."

  type-mappings:
    "order.failed":
      - platform: "slack"
        webhook: "critical"
    "billing.refund.failed":
      - platform: "discord"
        webhook: "ops"

  category-mappings:
    "ops":
      - platform: "discord"
        webhook: "ops"

  default-mappings:
    - platform: "slack"
      webhook: "general"

  retry:
    enabled: true
    max-attempts: 3
    initial-delay: 300
    max-delay: 5000
    multiplier: 2.0
    jitter-factor: 0.1

  timeout:
    enabled: true
    duration: 5000

  circuit-breaker:
    enabled: true
    failure-threshold: 10
    failure-rate-threshold: 50
    wait-duration: 30000
    success-threshold: 3

  rate-limiter:
    enabled: true
    limit-for-period: 20
    limit-refresh-period: 1000
    timeout-duration: 0

  bulkhead:
    enabled: true
    max-concurrent-calls: 50
    max-wait-duration: 0

  dead-letter:
    enabled: true
    max-retries: 3
    scheduler-enabled: false
    scheduler-interval: 60000
    scheduler-batch-size: 50

  async:
    thread-name-prefix: "hookrouter-"
    core-pool-size: 4
    max-pool-size: 16
    queue-capacity: 1000
    await-termination-seconds: 30
```

## 5. Environment Variable Mapping Example

```bash
HOOKROUTER_RETRY_ENABLED=true
HOOKROUTER_RETRY_MAX_ATTEMPTS=3
HOOKROUTER_TIMEOUT_DURATION=5000
HOOKROUTER_CIRCUIT_BREAKER_ENABLED=true
HOOKROUTER_RATE_LIMITER_LIMIT_FOR_PERIOD=20
HOOKROUTER_BULKHEAD_MAX_CONCURRENT_CALLS=50
```

## 6. Recommended Patterns

- Keep `default-mappings` as a safe fallback.
- Put highest-critical routes into `type-mappings`.
- Use per-webhook override for hot/critical endpoints only.
- Use `dead-letter.enabled=true` in production.
- Register a `DeadLetterStore` bean if you need persistence/replay (otherwise dead letters are log-only fallback).

## 7. Dead-letter activation notes

- `dead-letter.enabled=true` does not automatically persist failed events by itself.
- Persistence and replay are active when a `DeadLetterStore` bean is present.
- Scheduled replay additionally requires `dead-letter.scheduler-enabled=true`.
- For end-to-end setup, see [`dead-letter-guide.md`](dead-letter-guide.md).

## 8. IDE Auto-completion and Hints

`hookrouter-spring` provides configuration metadata through:

- `spring-boot-configuration-processor`
- `META-INF/additional-spring-configuration-metadata.json`

In IntelliJ and Spring-aware tooling, this enables:

- auto-completion for `hookrouter.*` keys
- inline descriptions for core keys
- common value suggestions for selected keys

For map-based dynamic keys such as `hookrouter.platforms.<platform>.endpoints.<webhookKey>.*`,
the IDE can suggest the static prefix, but `<platform>` and `<webhookKey>` are user-defined and
therefore not fully enumerated.
