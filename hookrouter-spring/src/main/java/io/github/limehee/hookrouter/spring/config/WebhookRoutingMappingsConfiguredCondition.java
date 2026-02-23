package io.github.limehee.hookrouter.spring.config;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class WebhookRoutingMappingsConfiguredCondition extends SpringBootCondition {

    private static final String DEFAULT_MAPPINGS = "hookrouter.default-mappings";
    private static final String TYPE_MAPPINGS = "hookrouter.type-mappings";
    private static final String CATEGORY_MAPPINGS = "hookrouter.category-mappings";
    private static final String DEFAULT_MAPPINGS_ENV = "HOOKROUTER_DEFAULT_MAPPINGS_";
    private static final String TYPE_MAPPINGS_ENV = "HOOKROUTER_TYPE_MAPPINGS_";
    private static final String CATEGORY_MAPPINGS_ENV = "HOOKROUTER_CATEGORY_MAPPINGS_";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (hasRoutingMappingProperty(context)) {
            return ConditionOutcome.match(ConditionMessage.forCondition("Hookrouter routing mappings")
                .because("at least one of default-mappings, type-mappings, or category-mappings is configured"));
        }
        return ConditionOutcome.noMatch(ConditionMessage.forCondition("Hookrouter routing mappings")
            .because("no routing mappings are configured"));
    }

    private boolean hasRoutingMappingProperty(ConditionContext context) {
        if (!(context.getEnvironment() instanceof ConfigurableEnvironment environment)) {
            return false;
        }
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                    if (isRoutingMappingProperty(propertyName)) {
                        return true;
                    }
                }
                continue;
            }
            if (propertySource.getSource() instanceof Map<?, ?> sourceMap) {
                for (Object propertyNameObj : sourceMap.keySet()) {
                    if (propertyNameObj instanceof String propertyName && isRoutingMappingProperty(propertyName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isRoutingMappingProperty(String propertyName) {
        return propertyName.startsWith(DEFAULT_MAPPINGS + "[")
            || propertyName.startsWith(TYPE_MAPPINGS + ".")
            || propertyName.startsWith(TYPE_MAPPINGS + "[")
            || propertyName.startsWith(CATEGORY_MAPPINGS + ".")
            || propertyName.startsWith(CATEGORY_MAPPINGS + "[")
            || propertyName.startsWith(DEFAULT_MAPPINGS_ENV)
            || propertyName.startsWith(TYPE_MAPPINGS_ENV)
            || propertyName.startsWith(CATEGORY_MAPPINGS_ENV);
    }
}
