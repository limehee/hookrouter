# Testing and Quality

Recommended test layers:

- Unit tests: core model, registries, formatter/sender logic
- Integration tests: Spring auto-configuration, retry, timeout, and rate-limit cooldown flow
- End-to-end tests: full Spring Boot context with dead-letter persistence/reprocessing and circuit-breaker open/skip behavior
- Consumer smoke tests: published artifact usability in external projects

Commands:

```bash
./gradlew test
./gradlew :hookrouter-spring:integrationTest
./gradlew :hookrouter-spring:e2eTest
./gradlew check
./scripts/verify-consumer-smoke.sh
```

API compatibility:

```bash
./gradlew apiCompat -PapiBaselineVersion=<released-version>
```
