# Spring Boot Guide

## Activation Conditions

Auto-configuration is activated when:

- `hookrouter-spring` dependency is present
- `hookrouter.default-mappings[0].platform` is present

## Required Beans

- `NotificationTypeDefinition`
- `WebhookFormatter`
- `WebhookSender`

## 1. Minimal Boot Config

```yaml
hookrouter:
  platforms:
    slack:
      endpoints:
        general:
          url: "https://hooks.slack.com/services/..."
  default-mappings:
    - platform: "slack"
      webhook: "general"
```

## 2. Routing Priority with YAML

Routing precedence:

1. `type-mappings`
2. `category-mappings`
3. `default-mappings`

Example:

```yaml
hookrouter:
  platforms:
    slack:
      endpoints:
        critical:
          url: "https://hooks.slack.com/services/..."
        general:
          url: "https://hooks.slack.com/services/..."
    discord:
      endpoints:
        ops:
          url: "https://discord.com/api/webhooks/..."

  type-mappings:
    "order.failed":
      - platform: "slack"
        webhook: "critical"

  category-mappings:
    "ops":
      - platform: "discord"
        webhook: "ops"

  default-mappings:
    - platform: "slack"
      webhook: "general"
```

Behavior:

- `order.failed` -> always `slack/critical`
- other `ops` category notifications -> `discord/ops`
- everything else -> `slack/general`

## 3. Global Resilience Config

```yaml
hookrouter:
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
    limit-for-period: 10
    limit-refresh-period: 1000
    timeout-duration: 0
  bulkhead:
    enabled: true
    max-concurrent-calls: 20
    max-wait-duration: 0
```

## 4. Per-Webhook Override Config

Use endpoint-level override when one webhook needs stricter policy than global defaults.

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
          circuit-breaker:
            enabled: true
            failure-threshold: 5
            failure-rate-threshold: 40
            wait-duration: 60000
            success-threshold: 2
          rate-limiter:
            enabled: true
            limit-for-period: 5
            limit-refresh-period: 1000
            timeout-duration: 0
          bulkhead:
            enabled: true
            max-concurrent-calls: 5
            max-wait-duration: 0
```

## 5. Async and Dead Letter Config

```yaml
hookrouter:
  async:
    thread-name-prefix: "hookrouter-"
    core-pool-size: 4
    max-pool-size: 16
    queue-capacity: 1000
    await-termination-seconds: 30

  dead-letter:
    enabled: true
    max-retries: 3
    scheduler-enabled: false
    scheduler-interval: 60000
    scheduler-batch-size: 50
```

To persist dead letters and enable replay, register a `DeadLetterStore` bean:

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
        return new InMemoryDeadLetterStore(
            InMemoryDeadLetterStore.DEFAULT_MAX_SIZE,
            Math.max(properties.getDeadLetter().getMaxRetries(), 1)
        );
    }
}
```

Without a `DeadLetterStore` bean, `hookrouter` falls back to logging dead-letter events only.
For full usage patterns (manual replay, scheduler behavior, status lifecycle), see [`dead-letter-guide.md`](dead-letter-guide.md).

## 6. Recommended Production Baseline

```yaml
hookrouter:
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
```

## 7. Profile-specific Example

```yaml
# application-prod.yml
hookrouter:
  retry:
    max-attempts: 4
    max-delay: 1500
  timeout:
    duration: 1500
  dead-letter:
    enabled: true
```

```yaml
# application-local.yml
hookrouter:
  retry:
    enabled: false
  circuit-breaker:
    enabled: false
  rate-limiter:
    enabled: false
  bulkhead:
    enabled: false
```
