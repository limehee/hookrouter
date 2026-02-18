# Contributing Guide

Thanks for contributing to `hookrouter`.

## Scope

This project provides webhook routing and notification components for Spring Boot:
- core domain/registry/contracts module
- Spring integration module (routing, dispatch, resilience, dead-letter, metrics)
- platform adapters as external or sample modules

## Before You Start

1. Check existing issues and pull requests to avoid duplicate work.
2. For non-trivial changes, open an issue first and discuss the approach.
3. Keep changes focused and small when possible.

## Development Setup

Requirements:
- JDK 21+
- Gradle wrapper (`./gradlew`)

Common commands:

```bash
./gradlew test
./gradlew check
./scripts/verify-consumer-smoke.sh
```

## Coding Guidelines

- Follow existing module boundaries and abstraction style.
- Keep `hookrouter-core` free from framework-specific logic where possible.
- Add or update tests for:
  - success paths
  - failure paths
  - exception/edge branches
- Keep public API behavior backward compatible unless a breaking change is intentional and documented.

## Commit and PR Guidelines

- Write clear commit messages describing intent.
- Update docs when behavior/configuration changes.

Before opening a PR, make sure:

1. Tests pass locally.
2. New behavior is covered by tests.
3. Documentation is updated.
4. The PR description explains motivation, approach, and trade-offs.

## Reporting Security Issues

Do not open public issues for sensitive security vulnerabilities.
Share details privately with maintainers first.
