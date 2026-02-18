# Module Guides

This document consolidates module-level guides that were previously maintained in each module README.

## 1. `hookrouter-core`

`hookrouter-core` contains framework-agnostic domain model and contracts.

### Responsibilities

- Notification domain model: `Notification<T>`, `NotificationTypeDefinition`
- Formatter contracts: `WebhookFormatter`, `FormatterKey`
- Routing/sender contracts: `RoutingPolicy`, `RoutingTarget`, `WebhookSender`
- Registries: `NotificationTypeRegistry`, `FormatterRegistry`
- Domain-specific fail-fast exceptions

### Design Principles

- Keep core independent from Spring runtime concerns
- Fail fast on duplicate type/formatter registration
- Keep extension points explicit and predictable

### Basic Usage

Register notification type:

```java
NotificationTypeRegistry typeRegistry = new NotificationTypeRegistry();

typeRegistry.register(NotificationTypeDefinition.builder()
    .typeId("demo.server.error")
    .title("Server Error")
    .defaultMessage("An error occurred")
    .category("general")
    .build());
```

Create notification:

```java
Notification<String> n = Notification
    .<String>builder("demo.server.error")
    .category("general")
    .context("timeout")
    .meta("requestId", "req-1")
    .build();
```

### Formatter Selection

`FormatterRegistry#getOrFallback(platform, typeId)` resolution order:

1. exact `(platform, typeId)` formatter
2. fallback formatter for `platform`
3. `null` when not found

## 2. `hookrouter-spring`

`hookrouter-spring` integrates `hookrouter-core` with Spring Boot runtime.

### Responsibilities

- Auto-configuration and property binding
- Event-based flow (`NotificationPublisher` -> `NotificationListener`)
- Config-driven routing (`ConfigBasedRoutingPolicy`)
- Dispatch with resilience policies
- Dead-letter handling and reprocessing
- Metrics and health indicator integration

### Auto-configuration Activation

Activated when:

- `hookrouter-spring` dependency is present
- route mapping configuration is provided (typically `hookrouter.default-mappings`)

### Minimal Configuration

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

### Required Extension Beans

- `NotificationTypeDefinition`
- `WebhookFormatter`
- `WebhookSender`

### Resilience/Dead-letter

Dispatcher supports:

- retry
- timeout
- circuit breaker
- rate limiter
- bulkhead

Failures can be persisted and reprocessed through dead-letter components.

## 3. `samples/hookrouter-adapters-slack` (Sample)

This module is a sample Slack adapter implementation for extension/reference.

### Demonstrates

- `WebhookSender` implementation for Slack webhooks
- fallback formatter mapping generic notifications to Slack payload
- Spring auto-configuration wiring for adapter beans

### Scope

This is a sample/extension module and is not part of the default root build.

### Main Components

- `SlackWebhookSender`
- `GenericSlackFallbackFormatter`
- `SlackPayload`
- `SlackAdapterAutoConfiguration`

### Production Guidance

For production, keep adapter implementations in dedicated repositories or separately versioned modules.
