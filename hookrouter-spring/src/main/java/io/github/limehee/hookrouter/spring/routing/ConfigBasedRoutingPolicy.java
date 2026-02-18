package io.github.limehee.hookrouter.spring.routing;

import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformMapping;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigBasedRoutingPolicy implements RoutingPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigBasedRoutingPolicy.class);
    private final WebhookConfigProperties properties;

    public ConfigBasedRoutingPolicy(final WebhookConfigProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<RoutingTarget> resolve(String typeId, String category) {

        List<PlatformMapping> typeMappings = properties.getTypeMappings().get(typeId);
        if (typeMappings != null && !typeMappings.isEmpty()) {
            return toRoutingTargets(typeMappings);
        }

        List<PlatformMapping> categoryMappings = properties.getCategoryMappings().get(category);
        if (categoryMappings != null && !categoryMappings.isEmpty()) {
            return toRoutingTargets(categoryMappings);
        }

        List<PlatformMapping> defaultMappings = properties.getDefaultMappings();
        if (!defaultMappings.isEmpty()) {
            return toRoutingTargets(defaultMappings);
        }
        return List.of();
    }

    private List<RoutingTarget> toRoutingTargets(List<PlatformMapping> mappings) {
        List<RoutingTarget> targets = new ArrayList<>();
        for (PlatformMapping mapping : mappings) {

            if (!mapping.isEnabled()) {
                continue;
            }
            String platform = mapping.getPlatform();
            String webhookKey = mapping.getWebhook();
            if (platform == null || webhookKey == null) {
                continue;
            }
            String webhookUrl;
            try {
                webhookUrl = properties.getWebhookUrl(platform, webhookKey);
            } catch (Exception e) {
                LOGGER.warn("Failed to resolve webhook URL for platform={}, webhookKey={}", platform, webhookKey, e);
                continue;
            }
            if (webhookUrl == null) {
                continue;
            }
            targets.add(RoutingTarget.of(platform, webhookKey, webhookUrl));
        }
        return targets;
    }
}
