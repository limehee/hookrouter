# Spring Boot Guide

## Activation Conditions

Auto-configuration is activated when:

- `hookrouter-spring` dependency is present
- at least one routing mapping is configured:
  - `hookrouter.type-mappings`
  - `hookrouter.category-mappings`
  - `hookrouter.default-mappings`

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

Resolution behavior:

- A higher-priority mapping is used only when it resolves to at least one enabled and valid target.
- If a higher-priority mapping exists but resolves to no valid targets, routing falls back to the next level.

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

For full validation rules (single-field and cross-field), see
[`configuration-reference.md`](configuration-reference.md) and
[`troubleshooting.md`](troubleshooting.md).

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
    max-concurrent-calls: 10
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

When the async pool is saturated, `hookrouter` uses `CallerRunsPolicy`.
Tasks are not dropped; the caller thread executes them instead.

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
At startup, `hookrouter` logs a warning in this mode to highlight that persistence/replay is not active.
For full usage patterns (manual replay, scheduler behavior, status lifecycle), see [`dead-letter-guide.md`](dead-letter-guide.md).

## 6. Recommended Production Baseline

Runtime defaults are intentionally safe for both local and production-like environments, but this section remains
a stricter production baseline recommendation.

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
    limit-for-period: 20  # runtime default: 200
    limit-refresh-period: 1000
    timeout-duration: 0
  bulkhead:
    enabled: true
    max-concurrent-calls: 10
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

## 8. Using hookrouter alongside resilience4j-spring-boot

`hookrouter` keeps its Resilience4j registries private. It registers a single `WebhookResilienceRegistries` bean
and does not register `CircuitBreakerRegistry`, `RetryRegistry`, `TimeLimiterRegistry`, `RateLimiterRegistry`, or
`BulkheadRegistry` beans. When `resilience4j-spring-boot` is on the classpath, its auto-configuration therefore
creates the application's registries from `resilience4j.*` as usual.

- `hookrouter.*` controls webhook delivery only.
- `resilience4j.*` controls the application's own instances (`@Bulkhead`, `@RateLimiter`, `@Retry`, ...) only.
- `CircuitBreakerStateChangedEvent` is published for hookrouter webhook circuit breakers only.

To inspect hookrouter's own instances (keyed as `<platform>:<webhook>`), inject `WebhookResilienceRegistries`:

```java
import io.github.limehee.hookrouter.spring.resilience.ResilienceResourceKey;
import io.github.limehee.hookrouter.spring.resilience.WebhookResilienceRegistries;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Component;

@Component
public class WebhookCircuitInspector {

    private final WebhookResilienceRegistries registries;

    public WebhookCircuitInspector(WebhookResilienceRegistries registries) {
        this.registries = registries;
    }

    public CircuitBreaker.State state(String platform, String webhook) {
        return registries.circuitBreakerRegistry()
            .find(ResilienceResourceKey.of(platform, webhook))
            .map(CircuitBreaker::getState)
            .orElse(CircuitBreaker.State.CLOSED);
    }
}
```

### Migrating from 0.4.x

Up to 0.4.x, `hookrouter` registered the five registries above as application-wide beans. With
`resilience4j-spring-boot` present, that made its registry auto-configuration back off, so the application's
`resilience4j.*` settings were silently replaced by `hookrouter.*` values.

- Code that injected `CircuitBreakerRegistry` (or another registry type) to reach hookrouter's instances must inject
  `WebhookResilienceRegistries` instead. Without `resilience4j-spring-boot`, such an injection point now fails at
  startup; with it, it now receives the application's registry, which does not contain hookrouter's instances.
- A custom `RateLimitEventListener` bean must be built with `WebhookResilienceRegistries#rateLimiterRegistry()`.
- `CircuitBreakerEventListener` is no longer a bean.
- Application instances now use their `resilience4j.*` values (or Resilience4j defaults) instead of `hookrouter.*`
  values. Review those settings; for example, stricter bulkhead or rate-limiter values start rejecting calls.
- Resilience4j actuator endpoints and health indicators no longer list hookrouter's instances. When a
  `MeterRegistry` is present, `hookrouter` still binds Resilience4j meters for its own instances.
