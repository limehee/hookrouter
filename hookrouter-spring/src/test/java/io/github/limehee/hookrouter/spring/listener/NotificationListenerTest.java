package io.github.limehee.hookrouter.spring.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.core.registry.FormatterRegistry;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterProcessor;
import io.github.limehee.hookrouter.spring.dispatcher.WebhookDispatcher;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    private NotificationListener notificationListener;

    @Mock
    private RoutingPolicy routingPolicy;

    @Mock
    private FormatterRegistry formatterRegistry;

    @Mock
    private WebhookSender slackSender;

    @Mock
    private WebhookDispatcher dispatcher;

    @Mock
    private DeadLetterProcessor deadLetterProcessor;

    @BeforeEach
    void setUp() {
        lenient().when(slackSender.platform()).thenReturn("slack");
        notificationListener = new NotificationListener(
            routingPolicy,
            formatterRegistry,
            List.of(slackSender),
            dispatcher,
            deadLetterProcessor
        );
    }

    private Notification<TestContext> createNotification(String typeId) {
        return Notification.of(typeId, "general", new TestContext("test-data"));
    }

    private RoutingTarget createRoutingTarget(String platform, String webhookKey, String webhookUrl) {
        return new RoutingTarget(platform, webhookKey, webhookUrl);
    }

    // === Helper Methods ===

    @SuppressWarnings("unchecked")
    private WebhookFormatter<?, ?> createFormatter(String platform, String typeId) {
        WebhookFormatter<?, ?> formatter = mock(WebhookFormatter.class);
        lenient().doReturn(FormatterKey.of(platform, typeId)).when(formatter).key();
        lenient().doReturn(Object.class).when(formatter).contextClass();
        return formatter;
    }

    private record TestContext(String data) {

    }

    @Nested
    class HandleNotificationTest {

        @Nested
        class RoutingTest {

            @Test
            void shouldNotInvokeUnexpectedInteractions() {
                // Given
                Notification<TestContext> notification = createNotification("test-type");
                given(routingPolicy.resolve("test-type", "general"))
                    .willReturn(Collections.emptyList());

                // When
                notificationListener.handleNotification(notification);

                // Then
                verify(formatterRegistry, never()).getOrFallback(any(), any());
                verify(dispatcher, never()).dispatch(any(), any(), any(), any());
            }

            @Test
            void shouldInvokeExpectedInteractions() {
                // Given
                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");

                given(routingPolicy.resolve("test-type", "general"))
                    .willReturn(List.of(target));
                given(formatterRegistry.getOrFallback("slack", "test-type")).willReturn(null);

                // When
                notificationListener.handleNotification(notification);

                // Then
                verify(formatterRegistry).getOrFallback("slack", "test-type");
            }

            @Test
            void shouldInvokeExpectedInteractionsWhenMultipleTargetsAreResolved() {
                // Given
                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget slackTarget = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                RoutingTarget discordTarget = createRoutingTarget("discord", "discord-key", "https://discord.com/test");

                given(routingPolicy.resolve("test-type", "general"))
                    .willReturn(List.of(slackTarget, discordTarget));

                given(formatterRegistry.getOrFallback("slack", "test-type")).willReturn(null);
                given(formatterRegistry.getOrFallback("discord", "test-type")).willReturn(null);

                // When
                notificationListener.handleNotification(notification);

                // Then
                verify(formatterRegistry).getOrFallback("slack", "test-type");
                verify(formatterRegistry).getOrFallback("discord", "test-type");
            }
        }

        @Nested
        class FormatterTest {

            @Test
            void shouldNotInvokeUnexpectedInteractionsWhenFormatterIsMissing() {
                // Given
                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");

                given(routingPolicy.resolve("test-type", "general"))
                    .willReturn(List.of(target));
                given(formatterRegistry.getOrFallback("slack", "test-type")).willReturn(null);

                // When
                notificationListener.handleNotification(notification);

                // Then
                verify(dispatcher, never()).dispatch(any(), any(), any(), any());
                verify(deadLetterProcessor).processFormatterNotFound(notification, target);
            }

            @Test
            void shouldNotInvokeUnexpectedInteractionsWhenFormatterReturnsNullPayload() {
                // Given
                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                WebhookFormatter<?, ?> formatter = createFormatter("slack", "test-type");

                given(routingPolicy.resolve("test-type", "general"))
                    .willReturn(List.of(target));
                doReturn(formatter).when(formatterRegistry).getOrFallback("slack", "test-type");
                doReturn(null).when(formatter).format(any());

                // When
                notificationListener.handleNotification(notification);

                // Then
                verify(dispatcher, never()).dispatch(any(), any(), any(), any());
                verify(deadLetterProcessor).processPayloadCreationFailed(
                    eq(notification), eq(target), eq("Formatter returned null payload"));
            }

            @Test
            void shouldInvokeExpectedInteractionsWhenFormatterProducesPayload() {
                // Given
                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                WebhookFormatter<?, ?> formatter = createFormatter("slack", "test-type");
                Map<String, Object> payload = Map.of("text", "Hello, World!");

                given(routingPolicy.resolve("test-type", "general"))
                    .willReturn(List.of(target));
                doReturn(formatter).when(formatterRegistry).getOrFallback("slack", "test-type");
                doReturn(payload).when(formatter).format(any());
                doReturn(Object.class).when(formatter).contextClass();

                // When
                notificationListener.handleNotification(notification);

                // Then
                verify(dispatcher).dispatch(eq(notification), eq(target), eq(slackSender), eq(payload));
            }
        }

        @Nested
        class SenderSelectionTest {

            @Test
            void shouldNotInvokeUnexpectedInteractionsWhenSenderIsNotRegistered() {
                // Given
                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget target = createRoutingTarget("unknown", "unknown-key", "https://unknown.com/test");
                WebhookFormatter<?, ?> formatter = createFormatter("unknown", "test-type");
                Map<String, Object> payload = Map.of("text", "Hello");

                given(routingPolicy.resolve("test-type", "general"))
                    .willReturn(List.of(target));
                doReturn(formatter).when(formatterRegistry).getOrFallback("unknown", "test-type");
                doReturn(payload).when(formatter).format(any());
                doReturn(Object.class).when(formatter).contextClass();

                // When
                notificationListener.handleNotification(notification);

                // Then
                verify(dispatcher, never()).dispatch(any(), any(), any(), any());
                verify(deadLetterProcessor).processSenderNotFound(notification, target, payload);
            }
        }

        @Nested
        class ExceptionHandlingTest {

            @Test
            void shouldInvokeExpectedInteractionsWhenMultiplePlatformsAreResolved() {
                // Given
                WebhookSender discordSender = mock(WebhookSender.class);
                lenient().when(discordSender.platform()).thenReturn("discord");

                notificationListener = new NotificationListener(
                    routingPolicy,
                    formatterRegistry,
                    List.of(slackSender, discordSender),
                    dispatcher,
                    deadLetterProcessor
                );

                Notification<TestContext> notification = createNotification("test-type");
                RoutingTarget slackTarget = createRoutingTarget("slack", "slack-key", "https://hooks.slack.com/test");
                RoutingTarget discordTarget = createRoutingTarget("discord", "discord-key", "https://discord.com/test");

                WebhookFormatter<?, ?> slackFormatter = createFormatter("slack", "test-type");
                WebhookFormatter<?, ?> discordFormatter = createFormatter("discord", "test-type");

                Map<String, Object> slackPayload = Map.of("text", "Slack");
                Map<String, Object> discordPayload = Map.of("content", "Discord");

                given(routingPolicy.resolve("test-type", "general"))
                    .willReturn(List.of(slackTarget, discordTarget));
                doReturn(slackFormatter).when(formatterRegistry).getOrFallback("slack", "test-type");
                doReturn(discordFormatter).when(formatterRegistry).getOrFallback("discord", "test-type");
                doReturn(slackPayload).when(slackFormatter).format(any());
                doReturn(Object.class).when(slackFormatter).contextClass();
                doReturn(discordPayload).when(discordFormatter).format(any());
                doReturn(Object.class).when(discordFormatter).contextClass();

                // When
                notificationListener.handleNotification(notification);

                verify(dispatcher).dispatch(eq(notification), eq(slackTarget), eq(slackSender), eq(slackPayload));
                verify(dispatcher).dispatch(eq(notification), eq(discordTarget), eq(discordSender), eq(discordPayload));
            }
        }
    }

    @Nested
    class IntegrationScenarioTest {

        @Test
        void shouldInvokeExpectedInteractionsForEndToEndSingleTargetFlow() {
            // Given
            Notification<TestContext> notification = createNotification("order.created");
            RoutingTarget target = createRoutingTarget("slack", "order-webhook",
                "https://hooks.slack.com/services/xxx");
            WebhookFormatter<?, ?> formatter = createFormatter("slack", "order.created");
            Map<String, Object> payload = Map.of("text", "New order created!");

            given(routingPolicy.resolve("order.created", "general"))
                .willReturn(List.of(target));
            doReturn(formatter).when(formatterRegistry).getOrFallback("slack", "order.created");
            doReturn(payload).when(formatter).format(any());
            doReturn(Object.class).when(formatter).contextClass();

            // When
            notificationListener.handleNotification(notification);

            // Then
            verify(routingPolicy).resolve("order.created", "general");
            verify(formatterRegistry).getOrFallback("slack", "order.created");
            verify(formatter).format(any());
            verify(dispatcher).dispatch(notification, target, slackSender, payload);
        }

        @Test
        void shouldInvokeExpectedInteractionsForEndToEndMultipleTargetFlow() {
            // Given
            Notification<TestContext> notification = createNotification("test-type");
            RoutingTarget target1 = createRoutingTarget("slack", "channel-1", "https://hooks.slack.com/1");
            RoutingTarget target2 = createRoutingTarget("slack", "channel-2", "https://hooks.slack.com/2");

            WebhookFormatter<?, ?> formatter = createFormatter("slack", "test-type");
            Map<String, Object> payload = Map.of("text", "Hello");

            given(routingPolicy.resolve("test-type", "general"))
                .willReturn(List.of(target1, target2));
            doReturn(formatter).when(formatterRegistry).getOrFallback("slack", "test-type");
            doReturn(payload).when(formatter).format(any());
            doReturn(Object.class).when(formatter).contextClass();

            // When
            notificationListener.handleNotification(notification);

            // Then
            verify(dispatcher, times(2)).dispatch(eq(notification), any(), eq(slackSender), eq(payload));
        }
    }
}
