package io.github.limehee.hookrouter.samples.purejava.router;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.core.registry.FormatterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PureJavaWebhookRouter {

    private final RoutingPolicy routingPolicy;
    private final FormatterRegistry formatterRegistry;
    private final Map<String, WebhookSender> senders;

    public PureJavaWebhookRouter(
        RoutingPolicy routingPolicy,
        FormatterRegistry formatterRegistry,
        Map<String, WebhookSender> senders
    ) {
        this.routingPolicy = Objects.requireNonNull(routingPolicy, "routingPolicy");
        this.formatterRegistry = Objects.requireNonNull(formatterRegistry, "formatterRegistry");
        this.senders = Objects.requireNonNull(senders, "senders");
    }

    public int dispatch(Notification<?> notification) {
        List<RoutingTarget> targets = routingPolicy.resolve(notification.getTypeId(), notification.getCategory());
        int sentCount = 0;

        for (RoutingTarget target : targets) {
            Object payload = createPayload(target.platform(), notification);
            if (payload == null) {
                continue;
            }

            WebhookSender sender = senders.get(target.platform());
            if (sender == null) {
                continue;
            }

            sender.send(target.webhookUrl(), payload);
            sentCount++;
        }

        return sentCount;
    }

    @SuppressWarnings("unchecked")
    private Object createPayload(String platform, Notification<?> notification) {
        WebhookFormatter<?, ?> formatter = formatterRegistry.getOrFallback(platform, notification.getTypeId());
        if (formatter == null) {
            return null;
        }

        Class<?> contextClass = formatter.contextClass();
        Object context = notification.getContext();
        if (!Object.class.equals(contextClass) && !contextClass.isInstance(context)) {
            return null;
        }

        try {
            WebhookFormatter<Object, Object> castedFormatter = (WebhookFormatter<Object, Object>) formatter;
            Notification<Object> castedNotification = (Notification<Object>) notification;
            return castedFormatter.format(castedNotification);
        } catch (ClassCastException ex) {
            return null;
        }
    }
}
