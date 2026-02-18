# Performance

Important tuning keys:

- `hookrouter.async.core-pool-size`
- `hookrouter.async.max-pool-size`
- `hookrouter.async.queue-capacity`
- `hookrouter.retry.*`
- `hookrouter.timeout.*`
- `hookrouter.rate-limiter.*`
- `hookrouter.bulkhead.*`

Operational advice:

- tune per-endpoint overrides for critical channels
- monitor retry/failure trends and dead-letter volume
- adjust timeout and rate limits incrementally
- if async executor is saturated, work runs on caller thread (`CallerRunsPolicy`), so monitor request latency under peak load
