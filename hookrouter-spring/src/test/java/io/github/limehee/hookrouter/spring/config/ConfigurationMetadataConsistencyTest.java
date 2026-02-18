package io.github.limehee.hookrouter.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ConfigurationMetadataConsistencyTest {

    private static final String METADATA_PATH = "/META-INF/additional-spring-configuration-metadata.json";
    private static final Set<String> EXPECTED_PROPERTY_NAMES = Set.of(
        "hookrouter.platforms",
        "hookrouter.type-mappings",
        "hookrouter.category-mappings",
        "hookrouter.default-mappings",
        "hookrouter.retry.enabled",
        "hookrouter.retry.max-attempts",
        "hookrouter.retry.initial-delay",
        "hookrouter.retry.max-delay",
        "hookrouter.retry.multiplier",
        "hookrouter.retry.jitter-factor",
        "hookrouter.timeout.enabled",
        "hookrouter.timeout.duration",
        "hookrouter.circuit-breaker.enabled",
        "hookrouter.circuit-breaker.failure-threshold",
        "hookrouter.circuit-breaker.failure-rate-threshold",
        "hookrouter.circuit-breaker.wait-duration",
        "hookrouter.circuit-breaker.success-threshold",
        "hookrouter.rate-limiter.enabled",
        "hookrouter.rate-limiter.limit-for-period",
        "hookrouter.rate-limiter.limit-refresh-period",
        "hookrouter.rate-limiter.timeout-duration",
        "hookrouter.bulkhead.enabled",
        "hookrouter.bulkhead.max-concurrent-calls",
        "hookrouter.bulkhead.max-wait-duration",
        "hookrouter.dead-letter.enabled",
        "hookrouter.dead-letter.max-retries",
        "hookrouter.dead-letter.scheduler-enabled",
        "hookrouter.dead-letter.scheduler-interval",
        "hookrouter.dead-letter.scheduler-batch-size",
        "hookrouter.async.core-pool-size",
        "hookrouter.async.max-pool-size",
        "hookrouter.async.queue-capacity",
        "hookrouter.async.thread-name-prefix",
        "hookrouter.async.await-termination-seconds"
    );

    @Test
    void shouldContainMetadataEntriesForAllSupportedProperties() throws IOException {
        JsonNode metadata = loadMetadata();
        Map<String, JsonNode> propertiesByName = toPropertyMap(metadata);

        assertThat(propertiesByName.keySet()).isEqualTo(EXPECTED_PROPERTY_NAMES);
    }

    @Test
    void shouldProvideTypeAndDescriptionForEveryProperty() throws IOException {
        JsonNode metadata = loadMetadata();
        Map<String, JsonNode> propertiesByName = toPropertyMap(metadata);

        for (Map.Entry<String, JsonNode> entry : propertiesByName.entrySet()) {
            JsonNode property = entry.getValue();
            assertThat(property.required("type").asString()).as("type for %s", entry.getKey()).isNotBlank();
            assertThat(property.required("description").asString()).as("description for %s", entry.getKey())
                .isNotBlank();
        }
    }

    @Test
    void shouldDescribeCrossFieldConstraintsForValidatedProperties() throws IOException {
        Map<String, JsonNode> propertiesByName = toPropertyMap(loadMetadata());

        assertThat(propertiesByName.get("hookrouter.retry.max-delay").required("description").asString())
            .contains("timeout.duration");
        assertThat(propertiesByName.get("hookrouter.timeout.duration").required("description").asString())
            .contains("retry.max-delay")
            .contains("rate limiter")
            .contains("bulkhead");
        assertThat(propertiesByName.get("hookrouter.rate-limiter.timeout-duration").required("description").asString())
            .contains("timeout.duration");
        assertThat(propertiesByName.get("hookrouter.bulkhead.max-concurrent-calls").required("description").asString())
            .contains("async.max-pool-size");
        assertThat(propertiesByName.get("hookrouter.bulkhead.max-wait-duration").required("description").asString())
            .contains("timeout.duration");
    }

    @Test
    void shouldKeepMetadataDefaultsAlignedWithWebhookConfigPropertiesDefaults() throws IOException {
        Map<String, JsonNode> propertiesByName = toPropertyMap(loadMetadata());
        Map<String, JsonNode> defaultsInMetadata = propertiesByName.entrySet()
            .stream()
            .filter(entry -> entry.getValue().has("defaultValue"))
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get("defaultValue")));
        Map<String, Object> expectedDefaults = expectedDefaultValues();

        assertThat(defaultsInMetadata.keySet()).isEqualTo(expectedDefaults.keySet());
        expectedDefaults.forEach(
            (name, expectedValue) -> assertJsonDefaultValue(defaultsInMetadata.get(name), expectedValue, name));
    }

    @Test
    void shouldUseHintsOnlyForKnownProperties() throws IOException {
        JsonNode metadata = loadMetadata();
        Map<String, JsonNode> propertiesByName = toPropertyMap(metadata);
        Set<String> hintNames = StreamSupport.stream(metadata.path("hints").spliterator(), false)
            .map(hint -> hint.required("name").asString())
            .collect(Collectors.toSet());

        assertThat(hintNames).allMatch(propertiesByName::containsKey);
    }

    private JsonNode loadMetadata() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(METADATA_PATH)) {
            assertThat(inputStream).as("metadata file must be present").isNotNull();
            return JsonMapper.builder().build().readTree(inputStream);
        }
    }

    private Map<String, JsonNode> toPropertyMap(JsonNode metadata) {
        Map<String, JsonNode> properties = new LinkedHashMap<>();
        for (JsonNode propertyNode : metadata.path("properties")) {
            properties.put(propertyNode.required("name").asString(), propertyNode);
        }
        return properties;
    }

    private Map<String, Object> expectedDefaultValues() {
        WebhookConfigProperties properties = new WebhookConfigProperties();
        Map<String, Object> expectedDefaults = new LinkedHashMap<>();
        expectedDefaults.put("hookrouter.retry.enabled", properties.getRetry().isEnabled());
        expectedDefaults.put("hookrouter.retry.max-attempts", properties.getRetry().getMaxAttempts());
        expectedDefaults.put("hookrouter.retry.initial-delay", properties.getRetry().getInitialDelay());
        expectedDefaults.put("hookrouter.retry.max-delay", properties.getRetry().getMaxDelay());
        expectedDefaults.put("hookrouter.retry.multiplier", properties.getRetry().getMultiplier());
        expectedDefaults.put("hookrouter.retry.jitter-factor", properties.getRetry().getJitterFactor());
        expectedDefaults.put("hookrouter.timeout.enabled", properties.getTimeout().isEnabled());
        expectedDefaults.put("hookrouter.timeout.duration", properties.getTimeout().getDuration());
        expectedDefaults.put("hookrouter.circuit-breaker.enabled", properties.getCircuitBreaker().isEnabled());
        expectedDefaults.put("hookrouter.circuit-breaker.failure-threshold",
            properties.getCircuitBreaker().getFailureThreshold());
        expectedDefaults.put("hookrouter.circuit-breaker.failure-rate-threshold",
            properties.getCircuitBreaker().getFailureRateThreshold());
        expectedDefaults.put("hookrouter.circuit-breaker.wait-duration",
            properties.getCircuitBreaker().getWaitDuration());
        expectedDefaults.put("hookrouter.circuit-breaker.success-threshold",
            properties.getCircuitBreaker().getSuccessThreshold());
        expectedDefaults.put("hookrouter.rate-limiter.enabled", properties.getRateLimiter().isEnabled());
        expectedDefaults.put("hookrouter.rate-limiter.limit-for-period",
            properties.getRateLimiter().getLimitForPeriod());
        expectedDefaults.put("hookrouter.rate-limiter.limit-refresh-period",
            properties.getRateLimiter().getLimitRefreshPeriod());
        expectedDefaults.put("hookrouter.rate-limiter.timeout-duration",
            properties.getRateLimiter().getTimeoutDuration());
        expectedDefaults.put("hookrouter.bulkhead.enabled", properties.getBulkhead().isEnabled());
        expectedDefaults.put("hookrouter.bulkhead.max-concurrent-calls",
            properties.getBulkhead().getMaxConcurrentCalls());
        expectedDefaults.put("hookrouter.bulkhead.max-wait-duration", properties.getBulkhead().getMaxWaitDuration());
        expectedDefaults.put("hookrouter.dead-letter.enabled", properties.getDeadLetter().isEnabled());
        expectedDefaults.put("hookrouter.dead-letter.max-retries", properties.getDeadLetter().getMaxRetries());
        expectedDefaults.put("hookrouter.dead-letter.scheduler-enabled",
            properties.getDeadLetter().isSchedulerEnabled());
        expectedDefaults.put("hookrouter.dead-letter.scheduler-interval",
            properties.getDeadLetter().getSchedulerInterval());
        expectedDefaults.put("hookrouter.dead-letter.scheduler-batch-size",
            properties.getDeadLetter().getSchedulerBatchSize());
        expectedDefaults.put("hookrouter.async.core-pool-size", properties.getAsync().getCorePoolSize());
        expectedDefaults.put("hookrouter.async.max-pool-size", properties.getAsync().getMaxPoolSize());
        expectedDefaults.put("hookrouter.async.queue-capacity", properties.getAsync().getQueueCapacity());
        expectedDefaults.put("hookrouter.async.thread-name-prefix", properties.getAsync().getThreadNamePrefix());
        expectedDefaults.put("hookrouter.async.await-termination-seconds",
            properties.getAsync().getAwaitTerminationSeconds());
        return expectedDefaults;
    }

    private void assertJsonDefaultValue(JsonNode actualNode, Object expectedValue, String propertyName) {
        assertThat(actualNode).as("defaultValue exists for %s", propertyName).isNotNull();
        if (expectedValue instanceof Boolean expectedBoolean) {
            assertThat(actualNode.isBoolean()).as("boolean default for %s", propertyName).isTrue();
            assertThat(actualNode.booleanValue()).isEqualTo(expectedBoolean);
            return;
        }
        if (expectedValue instanceof String expectedString) {
            assertThat(actualNode.isString()).as("string default for %s", propertyName).isTrue();
            assertThat(actualNode.stringValue()).isEqualTo(expectedString);
            return;
        }
        if (expectedValue instanceof Number expectedNumber) {
            assertThat(actualNode.isNumber()).as("numeric default for %s", propertyName).isTrue();
            BigDecimal actualNumber = actualNode.decimalValue();
            BigDecimal expectedAsDecimal = new BigDecimal(expectedNumber.toString());
            assertThat(actualNumber.compareTo(expectedAsDecimal))
                .as("numeric default for %s", propertyName)
                .isZero();
            return;
        }
        throw new IllegalArgumentException("Unsupported default value type for " + propertyName + ": " + expectedValue);
    }
}
