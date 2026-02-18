# Getting Started

## Requirements

- Java 17+
- Gradle Wrapper (`./gradlew`)

## Modules

- `hookrouter-core`: domain model, registries, and contracts
- `hookrouter-spring`: Spring runtime pipeline, configuration, resilience, dead-letter, metrics

## Dependencies (Maven)

```xml
<dependencies>
  <dependency>
    <groupId>io.github.limehee</groupId>
    <artifactId>hookrouter-core</artifactId>
    <version>${hookrouter.version}</version>
  </dependency>
  <dependency>
    <groupId>io.github.limehee</groupId>
    <artifactId>hookrouter-spring</artifactId>
    <version>${hookrouter.version}</version>
  </dependency>
</dependencies>
```

## Dependencies (Gradle)

Groovy DSL:

```groovy
dependencies {
    implementation 'io.github.limehee:hookrouter-core:<version>'
    implementation 'io.github.limehee:hookrouter-spring:<version>'
}
```

Kotlin DSL:

```kotlin
dependencies {
    implementation("io.github.limehee:hookrouter-core:<version>")
    implementation("io.github.limehee:hookrouter-spring:<version>")
}
```

## Minimal `application.yml`

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

## What this config means

- One platform (`slack`) is configured.
- One endpoint (`general`) is configured for that platform.
- If no type/category mapping matches, notifications are routed to `slack/general`.

## Next step

- Add explicit routing rules by type/category: see [`configuration-reference.md`](configuration-reference.md)
- See complete Spring setup and per-webhook resilience examples: [`spring-boot-guide.md`](spring-boot-guide.md)
