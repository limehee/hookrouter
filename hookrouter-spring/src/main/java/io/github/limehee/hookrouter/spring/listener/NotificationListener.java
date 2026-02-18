package io.github.limehee.hookrouter.spring.listener;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.core.registry.FormatterRegistry;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterProcessor;
import io.github.limehee.hookrouter.spring.dispatcher.WebhookDispatcher;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public class NotificationListener {

    private final RoutingPolicy routingPolicy;
    private final FormatterRegistry formatterRegistry;
    private final Map<String, WebhookSender> senderMap;
    private final WebhookDispatcher dispatcher;
    private final DeadLetterProcessor deadLetterProcessor;

    public NotificationListener(RoutingPolicy routingPolicy, FormatterRegistry formatterRegistry,
        List<WebhookSender> senders, WebhookDispatcher dispatcher, DeadLetterProcessor deadLetterProcessor) {
        this.routingPolicy = routingPolicy;
        this.formatterRegistry = formatterRegistry;
        this.senderMap = senders.stream().collect(Collectors.toMap(WebhookSender::platform, Function.identity()));
        this.dispatcher = dispatcher;
        this.deadLetterProcessor = deadLetterProcessor;
    }

    @Async("webhookTaskExecutor")
    @EventListener
    public <T> void handleNotification(Notification<T> notification) {
        String typeId = notification.getTypeId();
        try {

            List<RoutingTarget> targets = routingPolicy.resolve(typeId, notification.getCategory());
            if (targets.isEmpty()) {
                return;
            }

            for (RoutingTarget target : targets) {
                dispatchToTarget(notification, target);
            }
        } catch (Exception e) {
        }
    }

    private <T> void dispatchToTarget(Notification<T> notification, RoutingTarget target) {
        String typeId = notification.getTypeId();
        String platform = target.platform();

        WebhookFormatter<?, ?> formatter = formatterRegistry.getOrFallback(platform, typeId);
        if (formatter == null) {
            deadLetterProcessor.processFormatterNotFound(notification, target);
            return;
        }

        Object payload = formatPayload(notification, formatter);
        if (payload == null) {
            deadLetterProcessor.processPayloadCreationFailed(notification, target, "Formatter returned null payload");
            return;
        }

        WebhookSender sender = senderMap.get(platform);
        if (sender == null) {
            deadLetterProcessor.processSenderNotFound(notification, target, payload);
            return;
        }

        dispatcher.dispatch(notification, target, sender, payload);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> Object formatPayload(Notification<T> notification, WebhookFormatter<?, ?> formatter) {
        try {

            Class<?> contextClass = formatter.contextClass();
            Object context = notification.getContext();

            if (!contextClass.isInstance(context) && !Object.class.equals(contextClass)) {
                return null;
            }

            WebhookFormatter<Object, Object> typedFormatter = (WebhookFormatter<Object, Object>) formatter;
            Notification<Object> typedNotification = (Notification<Object>) notification;
            return typedFormatter.format(typedNotification);
        } catch (ClassCastException e) {
            return null;
        } catch (Exception e) {

            return null;
        }
    }
}
