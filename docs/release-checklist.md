# Release Checklist

## Pre-release

- [ ] `./gradlew check` passes
- [ ] `./scripts/verify-consumer-smoke.sh` passes
- [ ] `./gradlew apiCompat -PapiBaselineVersion=<previous>` passes

## Versioning

- [ ] update `version` in `gradle.properties`
- [ ] create and push `v<version>` tag

## CI/CD

- [ ] publish workflow passes
- [ ] Central finalize and publication state are confirmed

## Post-release

- [ ] update docs/examples to released version
- [ ] bump to next development version (`-SNAPSHOT`)
