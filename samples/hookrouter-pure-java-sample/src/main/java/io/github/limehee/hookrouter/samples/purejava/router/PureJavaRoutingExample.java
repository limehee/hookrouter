package io.github.limehee.hookrouter.samples.purejava.router;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.registry.FormatterRegistry;
import java.util.List;
import java.util.Map;

public final class PureJavaRoutingExample {

    private PureJavaRoutingExample() {
    }

    public static int runOnce() {
        MappingBasedRoutingPolicy routingPolicy = new MappingBasedRoutingPolicy(
            Map.of(
                "order.failed", List.of(RoutingTarget.of("slack", "critical", "https://example.test/slack/critical"))
            ),
            Map.of(
                "ops", List.of(RoutingTarget.of("discord", "ops", "https://example.test/discord/ops"))
            ),
            List.of(RoutingTarget.of("slack", "general", "https://example.test/slack/general"))
        );

        FormatterRegistry formatterRegistry = new FormatterRegistry();
        formatterRegistry.register(fallbackFormatter("slack"));
        formatterRegistry.register(fallbackFormatter("discord"));

        RecordingWebhookSender slackSender = new RecordingWebhookSender("slack");
        RecordingWebhookSender discordSender = new RecordingWebhookSender("discord");

        PureJavaWebhookRouter router = new PureJavaWebhookRouter(
            routingPolicy,
            formatterRegistry,
            Map.of("slack", slackSender, "discord", discordSender)
        );

        Notification<String> notification = Notification
            .<String>builder("order.failed")
            .category("ops")
            .context("payment timeout")
            .build();

        return router.dispatch(notification);
    }

    private static WebhookFormatter<Object, Map<String, Object>> fallbackFormatter(String platform) {
        return new WebhookFormatter<>() {
            @Override
            public FormatterKey key() {
                return FormatterKey.fallback(platform);
            }

            @Override
            public Class<Object> contextClass() {
                return Object.class;
            }

            @Override
            public Map<String, Object> format(Notification<Object> notification) {
                return Map.of(
                    "platform", platform,
                    "typeId", notification.getTypeId(),
                    "category", notification.getCategory(),
                    "context", String.valueOf(notification.getContext())
                );
            }
        };
    }
}
