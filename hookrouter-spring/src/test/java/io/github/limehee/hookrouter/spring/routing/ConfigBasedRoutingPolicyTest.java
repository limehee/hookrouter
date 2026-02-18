package io.github.limehee.hookrouter.spring.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.core.port.RoutingTarget;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformConfig;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.PlatformMapping;
import io.github.limehee.hookrouter.spring.config.WebhookEndpointConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConfigBasedRoutingPolicyTest {

    private WebhookConfigProperties properties;
    private ConfigBasedRoutingPolicy routingPolicy;

    @BeforeEach
    void setUp() {
        properties = new WebhookConfigProperties();

        PlatformConfig slackConfig = new PlatformConfig();
        addEndpoint(slackConfig, "error-channel", "https://hooks.slack.com/error");
        addEndpoint(slackConfig, "general-channel", "https://hooks.slack.com/general");
        addEndpoint(slackConfig, "category-channel", "https://hooks.slack.com/category");

        Map<String, PlatformConfig> platforms = new HashMap<>();
        platforms.put("slack", slackConfig);
        properties.setPlatforms(platforms);

        routingPolicy = new ConfigBasedRoutingPolicy(properties);
    }

    private void addEndpoint(PlatformConfig config, String webhookKey, String url) {
        WebhookEndpointConfig endpointConfig = new WebhookEndpointConfig();
        endpointConfig.setUrl(url);
        config.getEndpoints().put(webhookKey, endpointConfig);
    }

    private PlatformMapping createMapping(String platform, String webhook) {
        return createMapping(platform, webhook, true);
    }

    private PlatformMapping createMapping(String platform, String webhook, boolean enabled) {
        PlatformMapping mapping = new PlatformMapping();
        mapping.setPlatform(platform);
        mapping.setWebhook(webhook);
        mapping.setEnabled(enabled);
        return mapping;
    }

    @Nested
    class ResolveTest {

        @Test
        void shouldMatchExpectedTargets() {
            // Given
            PlatformMapping typeMapping = createMapping("slack", "error-channel");
            properties.setTypeMappings(Map.of("demo.server.error", List.of(typeMapping)));

            PlatformMapping categoryMapping = createMapping("slack", "general-channel");
            properties.setCategoryMappings(Map.of("general", List.of(categoryMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.server.error", "general");

            // Then
            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).webhookKey()).isEqualTo("error-channel");
            assertThat(targets.get(0).webhookUrl()).isEqualTo("https://hooks.slack.com/error");
        }

        @Test
        void shouldMatchExpectedTargetsWhenCategoryMappingsAreConfigured() {
            // Given
            PlatformMapping categoryMapping = createMapping("slack", "category-channel");
            properties.setCategoryMappings(Map.of("general", List.of(categoryMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.test.event", "general");

            // Then
            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).webhookKey()).isEqualTo("category-channel");
            assertThat(targets.get(0).webhookUrl()).isEqualTo("https://hooks.slack.com/category");
        }

        @Test
        void shouldMatchExpectedTargetsWhenDefaultMappingsAreConfigured() {
            // Given
            PlatformMapping defaultMapping = createMapping("slack", "general-channel");
            properties.setDefaultMappings(List.of(defaultMapping));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("unknown.type", "general");

            // Then
            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).webhookKey()).isEqualTo("general-channel");
        }

        @Test
        void shouldBeEmptyTargets() {
            // When
            List<RoutingTarget> targets = routingPolicy.resolve("unknown.type", "general");

            // Then
            assertThat(targets).isEmpty();
        }

        @Test
        void shouldHaveExpectedSizeTargets() {
            // Given
            PlatformMapping mapping1 = createMapping("slack", "error-channel");
            PlatformMapping mapping2 = createMapping("slack", "general-channel");
            properties.setTypeMappings(Map.of("demo.server.error", List.of(mapping1, mapping2)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.server.error", "general");

            // Then
            assertThat(targets).hasSize(2);
        }
    }

    @Nested
    class InvalidMappingTest {

        @Test
        void shouldMatchExpectedTargetsWhenPlatformIsNull() {
            // Given
            PlatformMapping invalidMapping = new PlatformMapping();
            invalidMapping.setPlatform(null);
            invalidMapping.setWebhook("error-channel");

            PlatformMapping validMapping = createMapping("slack", "general-channel");

            properties.setTypeMappings(Map.of("demo.test", List.of(invalidMapping, validMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.test", "general");

            // Then
            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).webhookKey()).isEqualTo("general-channel");
        }

        @Test
        void shouldBeEmptyTargetsWhenWebhookKeyIsMissing() {
            // Given
            PlatformMapping invalidMapping = new PlatformMapping();
            invalidMapping.setPlatform("slack");
            invalidMapping.setWebhook(null);

            properties.setTypeMappings(Map.of("demo.test", List.of(invalidMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.test", "general");

            // Then
            assertThat(targets).isEmpty();
        }

        @Test
        void shouldBeEmptyTargetsWhenWebhookEndpointDoesNotExist() {
            // Given
            PlatformMapping invalidMapping = createMapping("slack", "non-existent-channel");
            properties.setTypeMappings(Map.of("demo.test", List.of(invalidMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.test", "general");

            // Then
            assertThat(targets).isEmpty();
        }

        @Test
        void shouldBeEmptyTargetsWhenPlatformHasNoConfiguredEndpoints() {
            // Given
            PlatformMapping invalidMapping = createMapping("discord", "some-channel");
            properties.setTypeMappings(Map.of("demo.test", List.of(invalidMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.test", "general");

            // Then
            assertThat(targets).isEmpty();
        }
    }

    @Nested
    class RoutingTargetCreationTest {

        @Test
        void shouldMatchExpectedTargetsWhenTypeMappingsAreConfigured() {
            // Given
            PlatformMapping mapping = createMapping("slack", "error-channel");
            properties.setTypeMappings(Map.of("demo.server.error", List.of(mapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.server.error", "general");

            // Then
            assertThat(targets).hasSize(1);
            RoutingTarget target = targets.get(0);
            assertThat(target.platform()).isEqualTo("slack");
            assertThat(target.webhookKey()).isEqualTo("error-channel");
            assertThat(target.webhookUrl()).isEqualTo("https://hooks.slack.com/error");
        }
    }

    @Nested
    class MappingEnabledTest {

        @Test
        void shouldBeEmptyTargetsWhenTypeMappingIsDisabled() {
            // Given
            PlatformMapping disabledMapping = createMapping("slack", "error-channel", false);
            properties.setTypeMappings(Map.of("demo.server.error", List.of(disabledMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.server.error", "general");

            // Then
            assertThat(targets).isEmpty();
        }

        @Test
        void shouldBeEmptyTargetsWhenCategoryMappingIsDisabled() {
            // Given
            PlatformMapping disabledMapping = createMapping("slack", "category-channel", false);
            properties.setCategoryMappings(Map.of("general", List.of(disabledMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.test.event", "general");

            // Then
            assertThat(targets).isEmpty();
        }

        @Test
        void shouldBeEmptyTargetsWhenDefaultMappingIsDisabled() {
            // Given
            PlatformMapping disabledMapping = createMapping("slack", "general-channel", false);
            properties.setDefaultMappings(List.of(disabledMapping));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("unknown.type", "general");

            // Then
            assertThat(targets).isEmpty();
        }

        @Test
        void shouldMatchExpectedTargetsWhenEnabledAndDisabledTypeMappingsAreMixed() {
            // Given
            PlatformMapping enabledMapping = createMapping("slack", "error-channel", true);
            PlatformMapping disabledMapping = createMapping("slack", "general-channel", false);
            properties.setTypeMappings(Map.of("demo.server.error", List.of(enabledMapping, disabledMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.server.error", "general");

            // Then
            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).webhookKey()).isEqualTo("error-channel");
        }

        @Test
        void shouldBeEmptyTargetsWhenAllTypeMappingsAreDisabled() {
            // Given
            PlatformMapping disabled1 = createMapping("slack", "error-channel", false);
            PlatformMapping disabled2 = createMapping("slack", "general-channel", false);
            properties.setTypeMappings(Map.of("demo.server.error", List.of(disabled1, disabled2)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.server.error", "general");

            // Then
            assertThat(targets).isEmpty();
        }

        @Test
        void shouldMatchExpectedTargetsWhenPlatformAndWebhookAreSet() {
            // Given
            PlatformMapping mapping = new PlatformMapping();
            mapping.setPlatform("slack");
            mapping.setWebhook("error-channel");

            properties.setTypeMappings(Map.of("demo.server.error", List.of(mapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.server.error", "general");

            // Then
            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).webhookKey()).isEqualTo("error-channel");
        }

        @Test
        void shouldBeEmptyTargetsWhenTypeMappingIsDisabledEvenIfCategoryMappingIsEnabled() {

            PlatformMapping disabledTypeMapping = createMapping("slack", "error-channel", false);
            properties.setTypeMappings(Map.of("demo.server.error", List.of(disabledTypeMapping)));

            PlatformMapping enabledCategoryMapping = createMapping("slack", "category-channel", true);
            properties.setCategoryMappings(Map.of("general", List.of(enabledCategoryMapping)));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.server.error", "general");

            assertThat(targets).isEmpty();
        }

        @Test
        void shouldBeEmptyTargetsWhenDisabledCategoryMappingOverridesEnabledDefault() {

            PlatformMapping disabledCategoryMapping = createMapping("slack", "category-channel", false);
            properties.setCategoryMappings(Map.of("general", List.of(disabledCategoryMapping)));

            PlatformMapping enabledDefaultMapping = createMapping("slack", "general-channel", true);
            properties.setDefaultMappings(List.of(enabledDefaultMapping));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.test.event", "general");

            assertThat(targets).isEmpty();
        }

        @Test
        void shouldBeEmptyTargetsWhenAllMappingsAreDisabled() {
            // Given
            PlatformMapping disabledTypeMapping = createMapping("slack", "error-channel", false);
            properties.setTypeMappings(Map.of("demo.server.error", List.of(disabledTypeMapping)));

            PlatformMapping disabledCategoryMapping = createMapping("slack", "category-channel", false);
            properties.setCategoryMappings(Map.of("general", List.of(disabledCategoryMapping)));

            PlatformMapping disabledDefaultMapping = createMapping("slack", "general-channel", false);
            properties.setDefaultMappings(List.of(disabledDefaultMapping));

            // When
            List<RoutingTarget> targets = routingPolicy.resolve("demo.server.error", "general");

            // Then
            assertThat(targets).isEmpty();
        }
    }
}
