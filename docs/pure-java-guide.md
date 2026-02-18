# Pure Java Guide

`hookrouter-core` works without Spring, but there is no `application.yml` binding.
In pure Java, you configure routing and webhook targets directly in code.

## 1. What you must wire manually

- Notification type metadata (`NotificationTypeRegistry`)
- Routing rules (`type -> category -> default` priority)
- Webhook target URLs (`RoutingTarget`)
- Payload formatter per platform/type (`FormatterRegistry`)
- Sender per platform (`WebhookSender`)

## 2. YAML-style routing, in pure Java

Spring YAML style:

```yaml
hookrouter:
  type-mappings:
    "order.failed":
      - platform: "slack"
        webhook: "critical"
  category-mappings:
    "ops":
      - platform: "discord"
        webhook: "ops"
  default-mappings:
    - platform: "slack"
      webhook: "general"
```

Equivalent pure Java routing table with explicit webhook URLs:

```java
Map<String, List<RoutingTarget>> typeMappings = Map.of(
    "order.failed",
    List.of(RoutingTarget.of("slack", "critical", "https://example.test/slack/critical"))
);

Map<String, List<RoutingTarget>> categoryMappings = Map.of(
    "ops",
    List.of(RoutingTarget.of("discord", "ops", "https://example.test/discord/ops"))
);

List<RoutingTarget> defaultMappings = List.of(
    RoutingTarget.of("slack", "general", "https://example.test/slack/general")
);
```

## 3. Routing policy implementation (type > category > default)

```java
import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import java.util.List;
import java.util.Map;

public final class MappingBasedRoutingPolicy implements RoutingPolicy {
    private final Map<String, List<RoutingTarget>> typeMappings;
    private final Map<String, List<RoutingTarget>> categoryMappings;
    private final List<RoutingTarget> defaultMappings;

    public MappingBasedRoutingPolicy(
        Map<String, List<RoutingTarget>> typeMappings,
        Map<String, List<RoutingTarget>> categoryMappings,
        List<RoutingTarget> defaultMappings
    ) {
        this.typeMappings = typeMappings;
        this.categoryMappings = categoryMappings;
        this.defaultMappings = defaultMappings;
    }

    @Override
    public List<RoutingTarget> resolve(String typeId, String category) {
        List<RoutingTarget> byType = typeMappings.get(typeId);
        if (byType != null && !byType.isEmpty()) {
            return byType;
        }
        List<RoutingTarget> byCategory = categoryMappings.get(category);
        if (byCategory != null && !byCategory.isEmpty()) {
            return byCategory;
        }
        return defaultMappings;
    }
}
```

## 4. Full bootstrap example

This example shows category/type registration, webhook URL mapping, formatting, and dispatch.

```java
import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.core.registry.FormatterRegistry;
import io.github.limehee.hookrouter.core.registry.NotificationTypeRegistry;
import java.util.List;
import java.util.Map;

NotificationTypeRegistry typeRegistry = new NotificationTypeRegistry();
typeRegistry.register(NotificationTypeDefinition.builder()
    .typeId("order.failed")
    .title("Order Failed")
    .defaultMessage("Order processing failed")
    .category("ops")
    .build());

Map<String, List<RoutingTarget>> typeMappings = Map.of(
    "order.failed",
    List.of(RoutingTarget.of("slack", "critical", "https://example.test/slack/critical"))
);
Map<String, List<RoutingTarget>> categoryMappings = Map.of(
    "ops",
    List.of(RoutingTarget.of("discord", "ops", "https://example.test/discord/ops"))
);
List<RoutingTarget> defaultMappings = List.of(
    RoutingTarget.of("slack", "general", "https://example.test/slack/general")
);

RoutingPolicy routingPolicy = new MappingBasedRoutingPolicy(
    typeMappings,
    categoryMappings,
    defaultMappings
);

FormatterRegistry formatterRegistry = new FormatterRegistry();
formatterRegistry.register(new WebhookFormatter<>() {
    @Override
    public FormatterKey key() {
        return FormatterKey.fallback("slack");
    }

    @Override
    public Class<Object> contextClass() {
        return Object.class;
    }

    @Override
    public Map<String, Object> format(Notification<Object> notification) {
        return Map.of(
            "text",
            "[" + notification.getCategory() + "] " + notification.getTypeId() + ": " + notification.getContext()
        );
    }
});
formatterRegistry.register(new WebhookFormatter<>() {
    @Override
    public FormatterKey key() {
        return FormatterKey.fallback("discord");
    }

    @Override
    public Class<Object> contextClass() {
        return Object.class;
    }

    @Override
    public Map<String, Object> format(Notification<Object> notification) {
        return Map.of(
            "content",
            "[" + notification.getCategory() + "] " + notification.getTypeId() + ": " + notification.getContext()
        );
    }
});

WebhookSender slackSender = new WebhookSender() {
    @Override
    public String platform() {
        return "slack";
    }

    @Override
    public SendResult send(String webhookUrl, Object payload) {
        System.out.println("POST " + webhookUrl + " payload=" + payload);
        return SendResult.success(200);
    }
};
WebhookSender discordSender = new WebhookSender() {
    @Override
    public String platform() {
        return "discord";
    }

    @Override
    public SendResult send(String webhookUrl, Object payload) {
        System.out.println("POST " + webhookUrl + " payload=" + payload);
        return SendResult.success(200);
    }
};
Map<String, WebhookSender> senders = Map.of(
    slackSender.platform(), slackSender,
    discordSender.platform(), discordSender
);

Notification<String> notification = Notification.<String>builder("order.failed")
    .category("ops")
    .context("payment timeout")
    .build();

if (!typeRegistry.contains(notification.getTypeId())) {
    throw new IllegalArgumentException("Unknown notification type: " + notification.getTypeId());
}

for (RoutingTarget target : routingPolicy.resolve(notification.getTypeId(), notification.getCategory())) {
    WebhookFormatter<?, ?> formatter = formatterRegistry.getOrFallback(target.platform(), notification.getTypeId());
    if (formatter == null) {
        continue;
    }
    WebhookSender sender = senders.get(target.platform());
    if (sender == null) {
        continue;
    }
    @SuppressWarnings("unchecked")
    WebhookFormatter<Object, Object> castedFormatter = (WebhookFormatter<Object, Object>) formatter;
    @SuppressWarnings("unchecked")
    Notification<Object> castedNotification = (Notification<Object>) notification;
    Object payload = castedFormatter.format(castedNotification);
    sender.send(target.webhookUrl(), payload);
}
```

## 5. Runnable sample

For complete runnable code and tests, see:

- `samples/hookrouter-pure-java-sample/src/main/java/io/github/limehee/hookrouter/samples/purejava/router/PureJavaRoutingExample.java`
- `samples/hookrouter-pure-java-sample/src/test/java/io/github/limehee/hookrouter/samples/purejava/router/PureJavaWebhookRouterTest.java`
