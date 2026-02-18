package io.github.limehee.hookrouter.samples.purejava.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.registry.FormatterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PureJavaWebhookRouterTest {

    private RecordingWebhookSender slackSender;
    private RecordingWebhookSender discordSender;
    private PureJavaWebhookRouter router;

    @BeforeEach
    void setUp() {
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

        slackSender = new RecordingWebhookSender("slack");
        discordSender = new RecordingWebhookSender("discord");

        router = new PureJavaWebhookRouter(
            routingPolicy,
            formatterRegistry,
            Map.of("slack", slackSender, "discord", discordSender)
        );
    }

    @Test
    void typeMappingHasHighestPriority() {
        Notification<String> notification = Notification
            .<String>builder("order.failed")
            .category("ops")
            .context("payment timeout")
            .build();

        int sentCount = router.dispatch(notification);

        assertEquals(1, sentCount);
        assertEquals(1, slackSender.calls().size());
        assertEquals("https://example.test/slack/critical", slackSender.calls().get(0).webhookUrl());
        assertTrue(discordSender.calls().isEmpty());
    }

    @Test
    void categoryMappingIsUsedWhenTypeMappingDoesNotExist() {
        Notification<String> notification = Notification
            .<String>builder("inventory.low")
            .category("ops")
            .context("sku=SKU-9")
            .build();

        int sentCount = router.dispatch(notification);

        assertEquals(1, sentCount);
        assertEquals(1, discordSender.calls().size());
        assertEquals("https://example.test/discord/ops", discordSender.calls().get(0).webhookUrl());
        assertTrue(slackSender.calls().isEmpty());
    }

    @Test
    void defaultMappingIsUsedWhenNoTypeOrCategoryMappingExists() {
        Notification<String> notification = Notification
            .<String>builder("user.signup.completed")
            .category("growth")
            .context("new user joined")
            .build();

        int sentCount = router.dispatch(notification);

        assertEquals(1, sentCount);
        assertEquals(1, slackSender.calls().size());
        assertEquals("https://example.test/slack/general", slackSender.calls().get(0).webhookUrl());
        assertTrue(discordSender.calls().isEmpty());
    }

    private WebhookFormatter<Object, Map<String, Object>> fallbackFormatter(String platform) {
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
