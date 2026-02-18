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

- `OSSRH_USERNAME`, `OSSRH_PASSWORD`
- `SIGNING_KEY`, `SIGNING_PASSWORD`
- version/tag consistency
