package io.github.limehee.hookrouter.samples.purejava.router;

import io.github.limehee.hookrouter.core.port.RoutingPolicy;
import io.github.limehee.hookrouter.core.port.RoutingTarget;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MappingBasedRoutingPolicy implements RoutingPolicy {

    private final Map<String, List<RoutingTarget>> typeMappings;
    private final Map<String, List<RoutingTarget>> categoryMappings;
    private final List<RoutingTarget> defaultMappings;

    public MappingBasedRoutingPolicy(
        Map<String, List<RoutingTarget>> typeMappings,
        Map<String, List<RoutingTarget>> categoryMappings,
        List<RoutingTarget> defaultMappings
    ) {
        this.typeMappings = Objects.requireNonNull(typeMappings, "typeMappings");
        this.categoryMappings = Objects.requireNonNull(categoryMappings, "categoryMappings");
        this.defaultMappings = Objects.requireNonNull(defaultMappings, "defaultMappings");
    }

    @Override
    public List<RoutingTarget> resolve(String typeId, String category) {
        List<RoutingTarget> byType = typeMappings.get(typeId);
        if (byType != null && !byType.isEmpty()) {
            return byType;
        }

        List<RoutingTarget> byCategory = categoryMappings.get(category);
        if (byCategory != null && !byCategory.isEmpty()) {
            return byCategory;
        }

        return defaultMappings;
    }
}
