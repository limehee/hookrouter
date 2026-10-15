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

- `apiBaselineVersion` must be a version published to Maven Central; the baseline jars are downloaded from there.
  The check fails if the baseline resolves to this build's own jar instead.
- CI compares against the newest `v*` tag, or the one before it when the commit under test is that tagged release.
- The publish workflow compares the tag being released against the previous release tag.
