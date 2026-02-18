package io.github.limehee.hookrouter.samples.adapters.slack.formatter;

import io.github.limehee.hookrouter.samples.adapters.slack.config.SlackFormatterProperties;
import io.github.limehee.hookrouter.samples.adapters.slack.payload.SlackPayload;
import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.registry.NotificationTypeRegistry;
import com.slack.api.model.Attachment;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class GenericSlackFallbackFormatter implements WebhookFormatter<Object, SlackPayload> {

    private static final String PLATFORM = "slack";
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int MAX_CONTEXT_LENGTH = 2000;

    private final NotificationTypeRegistry typeRegistry;
    private final SlackFormatterProperties slackFormatterProperties;

    public GenericSlackFallbackFormatter(
        NotificationTypeRegistry typeRegistry,
        SlackFormatterProperties slackFormatterProperties
    ) {
        this.typeRegistry = typeRegistry;
        this.slackFormatterProperties = slackFormatterProperties;
    }

    @Override
    public FormatterKey key() {
        return FormatterKey.fallback(PLATFORM);
    }

    @Override
    public Class<Object> contextClass() {
        return Object.class;
    }

    @Override
    public SlackPayload format(Notification<Object> notification) {
        String typeId = notification.getTypeId();
        NotificationTypeDefinition typeDef = typeRegistry.find(typeId);

        String title = typeDef != null ? typeDef.title() : typeId;
        String message = typeDef != null ? typeDef.defaultMessage() : "Notification received";
        final String color = slackFormatterProperties.getDefaultColor();

        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(createHeaderSection(title));

        blocks.add(createMessageSection(message));

        blocks.add(createMetaSection(notification, typeId));

        LayoutBlock contextBlock = createContextSection(notification.getContext());
        if (contextBlock != null) {
            blocks.add(contextBlock);
        }

        Attachment attachment = Attachment.builder()
            .color(color)
            .blocks(blocks)
            .build();

        return SlackPayload.builder()
            .text(title + ": " + message)
            .attachment(attachment)
            .build();
    }

    private LayoutBlock createHeaderSection(String title) {
        return SectionBlock.builder()
            .text(MarkdownTextObject.builder()
                .text("*" + title + "*")
                .build())
            .build();
    }

    private LayoutBlock createMessageSection(String message) {
        return SectionBlock.builder()
            .text(MarkdownTextObject.builder()
                .text(message)
                .build())
            .build();
    }

    private LayoutBlock createMetaSection(Notification<Object> notification, String typeId) {
        String occurredAt = DATE_FORMATTER.format(notification.getOccurredAt());
        String category = notification.getCategory();

        String metaText = String.format(
            "*Type:* `%s`\n*Category:* `%s`\n*Time:* %s",
            typeId, category, occurredAt
        );

        return SectionBlock.builder()
            .text(MarkdownTextObject.builder()
                .text(metaText)
                .build())
            .build();
    }

    @Nullable
    private LayoutBlock createContextSection(Object context) {
        if (context == null) {
            return null;
        }

        String contextStr = context.toString();
        if (contextStr.isEmpty() || contextStr.length() > MAX_CONTEXT_LENGTH) {
            return null;
        }

        return SectionBlock.builder()
            .text(MarkdownTextObject.builder()
                .text("*Context:*\n```" + contextStr + "```")
                .build())
            .build();
    }
}
