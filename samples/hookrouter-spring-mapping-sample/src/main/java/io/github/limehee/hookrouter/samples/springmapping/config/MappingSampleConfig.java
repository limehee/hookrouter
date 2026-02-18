package io.github.limehee.hookrouter.samples.springmapping.config;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.samples.springmapping.context.OrderFailedContext;
import io.github.limehee.hookrouter.samples.springmapping.sender.RecordingWebhookSender;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MappingSampleConfig {

    @Bean
    NotificationTypeDefinition orderFailedTypeDefinition() {
        return NotificationTypeDefinition.builder()
            .typeId("order.failed")
            .title("Order Failed")
            .defaultMessage("Order processing failed")
            .category("ops")
            .build();
    }

    @Bean
    NotificationTypeDefinition inventoryLowTypeDefinition() {
        return NotificationTypeDefinition.builder()
            .typeId("inventory.low")
            .title("Inventory Low")
            .defaultMessage("Inventory is running low")
            .category("ops")
            .build();
    }

    @Bean
    NotificationTypeDefinition signupCompletedTypeDefinition() {
        return NotificationTypeDefinition.builder()
            .typeId("user.signup.completed")
            .title("User Signup Completed")
            .defaultMessage("A user completed signup")
            .category("growth")
            .build();
    }

    @Bean
    WebhookFormatter<OrderFailedContext, Map<String, Object>> slackOrderFailedFormatter() {
        return new WebhookFormatter<>() {
            @Override
            public FormatterKey key() {
                return FormatterKey.of("slack", "order.failed");
            }

            @Override
            public Class<OrderFailedContext> contextClass() {
                return OrderFailedContext.class;
            }

            @Override
            public Map<String, Object> format(Notification<OrderFailedContext> notification) {
                OrderFailedContext context = notification.getContext();
                return Map.of(
                    "channel", "#order-alerts",
                    "typeId", notification.getTypeId(),
                    "orderId", context.orderId(),
                    "reason", context.reason()
                );
            }
        };
    }

    @Bean
    WebhookFormatter<Object, Map<String, Object>> slackFallbackFormatter() {
        return new WebhookFormatter<>() {
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
                    "channel", "#general-alerts",
                    "typeId", notification.getTypeId(),
                    "category", notification.getCategory(),
                    "context", String.valueOf(notification.getContext())
                );
            }
        };
    }

    @Bean
    WebhookFormatter<Object, Map<String, Object>> discordFallbackFormatter() {
        return new WebhookFormatter<>() {
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
                    "channel", "ops",
                    "typeId", notification.getTypeId(),
                    "category", notification.getCategory(),
                    "context", String.valueOf(notification.getContext())
                );
            }
        };
    }

    @Bean
    RecordingWebhookSender slackWebhookSender() {
        return new RecordingWebhookSender("slack");
    }

    @Bean
    RecordingWebhookSender discordWebhookSender() {
        return new RecordingWebhookSender("discord");
    }
}
