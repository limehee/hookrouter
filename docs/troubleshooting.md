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

## Publish/signing failures

Check:

- Central Portal user token is valid (token username/password)
- token values are mapped to `OSSRH_USERNAME`, `OSSRH_PASSWORD` secrets
- `SIGNING_KEY`, `SIGNING_PASSWORD`
- version/tag consistency

## Published but not searchable yet

Check:

- Central deployment state is `PUBLISHED`
- repository URL resolves artifact path directly (for example `repo1.maven.org`)
- allow indexing propagation delay before Maven Central search UI reflects the new coordinates
