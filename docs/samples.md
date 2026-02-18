# Samples

This project includes runnable sample modules under `samples/`.

## 1. Spring Mapping Sample

Path: `samples/hookrouter-spring-mapping-sample`

Purpose:

- Demonstrates mapping precedence in Spring runtime:
  - `type-mappings`
  - `category-mappings`
  - `default-mappings`
- Uses `NotificationPublisher` -> `NotificationListener` -> `WebhookDispatcher` pipeline
- Includes integration tests that verify actual dispatch target URLs per mapping case

Run tests:

```bash
./gradlew -p samples/hookrouter-spring-mapping-sample test
```

## 2. Pure Java Sample

Path: `samples/hookrouter-pure-java-sample`

Purpose:

- Demonstrates mapping precedence without Spring
- Uses only `hookrouter-core` contracts with a custom `MappingBasedRoutingPolicy`
- Includes tests that verify routing and sender dispatch behavior in pure Java

Run tests:

```bash
./gradlew -p samples/hookrouter-pure-java-sample test
```

## 3. Slack Adapter Sample

Path: `samples/hookrouter-adapters-slack`

Purpose:

- Demonstrates platform adapter extension pattern for Slack sender/formatter

Run tests:

```bash
./gradlew -p samples/hookrouter-adapters-slack test
```
