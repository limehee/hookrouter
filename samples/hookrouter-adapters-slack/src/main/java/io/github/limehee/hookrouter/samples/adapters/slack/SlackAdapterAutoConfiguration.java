package io.github.limehee.hookrouter.samples.adapters.slack;

import io.github.limehee.hookrouter.samples.adapters.slack.config.SlackFormatterProperties;
import io.github.limehee.hookrouter.samples.adapters.slack.formatter.GenericSlackFallbackFormatter;
import io.github.limehee.hookrouter.samples.adapters.slack.sender.SlackWebhookSender;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import io.github.limehee.hookrouter.core.registry.NotificationTypeRegistry;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "com.slack.api.Slack")
public class SlackAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "slackWebhookSender")
    public WebhookSender slackWebhookSender() {
        return new SlackWebhookSender();
    }

    @Bean
    @ConditionalOnMissingBean(name = "genericSlackFallbackFormatter")
    public GenericSlackFallbackFormatter genericSlackFallbackFormatter(
        NotificationTypeRegistry typeRegistry,
        Optional<SlackFormatterProperties> slackFormatterProperties
    ) {
        SlackFormatterProperties properties = slackFormatterProperties.orElseGet(
            () -> new SlackFormatterProperties() {
            });
        return new GenericSlackFallbackFormatter(typeRegistry, properties);
    }
}
