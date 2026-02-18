package io.github.limehee.hookrouter.samples.springmapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.samples.springmapping.context.GenericContext;
import io.github.limehee.hookrouter.samples.springmapping.context.InventoryAlertContext;
import io.github.limehee.hookrouter.samples.springmapping.context.OrderFailedContext;
import io.github.limehee.hookrouter.samples.springmapping.sender.RecordingWebhookSender;
import io.github.limehee.hookrouter.spring.publisher.NotificationPublisher;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = MappingSampleApplication.class)
class SpringMappingSampleIntegrationTest {

    @Autowired
    private NotificationPublisher notificationPublisher;

    @Autowired
    @Qualifier("slackWebhookSender")
    private RecordingWebhookSender slackSender;

    @Autowired
    @Qualifier("discordWebhookSender")
    private RecordingWebhookSender discordSender;

    private static void awaitCalls(
        RecordingWebhookSender sender,
        int expectedCount,
        Duration timeout
    ) {
        long timeoutNanos = timeout.toNanos();
        long start = System.nanoTime();
        while (System.nanoTime() - start < timeoutNanos) {
            if (sender.calls().size() >= expectedCount) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread interrupted while waiting for webhook calls", e);
            }
        }
        fail("Timed out waiting for webhook calls. expected=" + expectedCount + ", actual=" + sender.calls().size());
    }

    @BeforeEach
    void setUp() {
        slackSender.clear();
        discordSender.clear();
    }

    @Test
    void typeMappingHasHighestPriorityOverCategoryAndDefault() {
        Notification<OrderFailedContext> notification = Notification
            .<OrderFailedContext>builder("order.failed")
            .category("ops")
            .context(new OrderFailedContext("order-100", "payment timeout"))
            .build();

        notificationPublisher.publish(notification);

        awaitCalls(slackSender, 1, Duration.ofSeconds(3));

        assertThat(slackSender.calls()).hasSize(1);
        assertThat(slackSender.calls().getFirst().webhookUrl())
            .isEqualTo("https://example.test/slack/critical");
        assertThat(discordSender.calls()).isEmpty();
    }

    @Test
    void categoryMappingIsUsedWhenTypeMappingDoesNotExist() {
        Notification<InventoryAlertContext> notification = Notification
            .<InventoryAlertContext>builder("inventory.low")
            .category("ops")
            .context(new InventoryAlertContext("SKU-9", 2))
            .build();

        notificationPublisher.publish(notification);

        awaitCalls(discordSender, 1, Duration.ofSeconds(3));

        assertThat(discordSender.calls()).hasSize(1);
        assertThat(discordSender.calls().getFirst().webhookUrl())
            .isEqualTo("https://example.test/discord/ops");
        assertThat(slackSender.calls()).isEmpty();
    }

    @Test
    void defaultMappingIsUsedWhenNoTypeOrCategoryMappingExists() {
        Notification<GenericContext> notification = Notification
            .<GenericContext>builder("user.signup.completed")
            .category("growth")
            .context(new GenericContext("new user joined"))
            .build();

        notificationPublisher.publish(notification);

        awaitCalls(slackSender, 1, Duration.ofSeconds(3));

        assertThat(slackSender.calls()).hasSize(1);
        assertThat(slackSender.calls().getFirst().webhookUrl())
            .isEqualTo("https://example.test/slack/general");
        assertThat(discordSender.calls()).isEmpty();
    }
}
