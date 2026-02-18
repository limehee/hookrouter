# Release Checklist

## Pre-release

- [ ] `./gradlew check` passes
- [ ] `./scripts/verify-consumer-smoke.sh` passes
- [ ] `./gradlew apiCompat -PapiBaselineVersion=<previous>` passes
- [ ] publishing secrets are configured (`OSSRH_USERNAME`, `OSSRH_PASSWORD`, `SIGNING_KEY`, `SIGNING_PASSWORD`)

## Versioning

- [ ] update `version` in `gradle.properties`
- [ ] create and push `v<version>` tag

## CI/CD

- [ ] publish workflow passes
- [ ] Central finalize and publication state are confirmed
- [ ] artifact path is reachable from `repo1.maven.org`
- [ ] Central search indexing propagation is verified

## Post-release

- [ ] update docs/examples to released version
- [ ] bump to next development version (`-SNAPSHOT`)
