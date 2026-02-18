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

    private static final System.Logger LOGGER = System.getLogger(NotificationListener.class.getName());
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
            LOGGER.log(System.Logger.Level.ERROR,
                "Failed to process notification typeId=" + typeId + ", category=" + notification.getCategory(), e);
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

        PayloadResult payloadResult = formatPayload(notification, formatter);
        Object payload = payloadResult.payload();
        if (payload == null) {
            String reason = payloadResult.errorMessage() != null
                ? payloadResult.errorMessage()
                : "Formatter returned null payload";
            deadLetterProcessor.processPayloadCreationFailed(notification, target, reason);
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
    private <T> PayloadResult formatPayload(Notification<T> notification, WebhookFormatter<?, ?> formatter) {
        try {
            Class<?> contextClass = formatter.contextClass();
            Object context = notification.getContext();
            if (!contextClass.isInstance(context) && !Object.class.equals(contextClass)) {
                return new PayloadResult(null,
                    "Formatter context type mismatch: expected " + contextClass.getName() + ", actual "
                        + context.getClass().getName());
            }
            WebhookFormatter<Object, Object> typedFormatter = (WebhookFormatter<Object, Object>) formatter;
            Notification<Object> typedNotification = (Notification<Object>) notification;
            return new PayloadResult(typedFormatter.format(typedNotification), null);
        } catch (ClassCastException e) {
            return new PayloadResult(null, "Formatter type cast failed: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Formatter failed for typeId=" + notification.getTypeId() + ", platform=" + formatter.platform(), e);
            return new PayloadResult(null,
                "Formatter execution failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
        }
    }

    private record PayloadResult(@Nullable Object payload, @Nullable String errorMessage) {

    }
}
