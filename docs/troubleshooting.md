# Troubleshooting

## Auto-configuration not activated

Check:

- `hookrouter.default-mappings[0].platform` exists
- required beans are registered (`WebhookSender`, `WebhookFormatter`, `NotificationTypeDefinition`)

## Notification published but not delivered

Check:

- routing rules and key matching
- formatter key (`platform`, `typeId`) alignment
- sender platform alignment
- dead-letter store/handler behavior

## Context startup fails with `WebhookConfigValidationException`

Check all validation groups:

- Async:
  - `async.core-pool-size > 0`
  - `async.max-pool-size > 0`
  - `async.core-pool-size <= async.max-pool-size`
  - `async.queue-capacity > 0`
  - `async.await-termination-seconds >= 0`
  - `async.thread-name-prefix` is not blank
- Retry:
  - `retry.max-attempts > 0`
  - `retry.initial-delay > 0`
  - `retry.max-delay > 0`
  - `retry.max-delay >= retry.initial-delay`
  - `retry.multiplier >= 1.0`
  - `retry.jitter-factor` is in `[0.0, 1.0]`
- Timeout:
  - `timeout.duration > 0`
- Rate limiter:
  - `rate-limiter.limit-for-period > 0`
  - `rate-limiter.limit-refresh-period > 0`
  - `rate-limiter.timeout-duration >= 0`
- Bulkhead:
  - `bulkhead.max-concurrent-calls > 0`
  - `bulkhead.max-wait-duration >= 0`
- Circuit breaker:
  - `circuit-breaker.failure-threshold > 0`
  - `circuit-breaker.failure-rate-threshold` is in `(0, 100]`
  - `circuit-breaker.wait-duration > 0`
  - `circuit-breaker.success-threshold > 0`
- Dead letter:
  - `dead-letter.scheduler-interval > 0`
  - `dead-letter.scheduler-batch-size > 0`
- Mapping/platform references:
  - every mapped `platform` exists under `hookrouter.platforms`
  - every mapped `webhook` exists under that platform endpoints
  - endpoint URL uses `http` or `https` and has a valid host

Cross-field constraints:

- `timeout.duration >= retry.max-delay` when timeout and retry are both enabled
- `bulkhead.max-concurrent-calls <= async.max-pool-size` when bulkhead is enabled
- `rate-limiter.timeout-duration <= timeout.duration` when rate limiter and timeout are both enabled
- `bulkhead.max-wait-duration <= timeout.duration` when bulkhead and timeout are both enabled

Tips:

- check `WebhookConfigValidationException.getValidationErrors()` for all failing keys
- compare your YAML against [`configuration-reference.md`](configuration-reference.md)

## Publish/signing failures

Check:

- Central Portal user token is valid (token username/password)
- Central token username/password are mapped to `OSSRH_USERNAME`, `OSSRH_PASSWORD` secrets
- `SIGNING_KEY`, `SIGNING_PASSWORD`
- version/tag consistency

## Published but not searchable yet

Check:

- Central deployment state is `PUBLISHED`
- repository URL resolves artifact path directly (for example `repo1.maven.org`)
- allow indexing propagation delay before Maven Central search UI reflects the new coordinates
