package io.github.limehee.hookrouter.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ConfigurationMetadataConsistencyTest {

    private static final String METADATA_PATH = "/META-INF/additional-spring-configuration-metadata.json";

    @Test
    void shouldDescribeCrossFieldConstraintsForValidatedProperties() throws IOException {
        String propertiesSection = loadPropertiesSection();

        assertThat(findPropertyBlock(propertiesSection, "hookrouter.retry.max-delay"))
            .contains("timeout.duration");
        assertThat(findPropertyBlock(propertiesSection, "hookrouter.timeout.duration"))
            .contains("retry.max-delay")
            .contains("rate limiter")
            .contains("bulkhead");
        assertThat(findPropertyBlock(propertiesSection, "hookrouter.rate-limiter.timeout-duration"))
            .contains("timeout.duration");
        assertThat(findPropertyBlock(propertiesSection, "hookrouter.bulkhead.max-concurrent-calls"))
            .contains("async.max-pool-size");
        assertThat(findPropertyBlock(propertiesSection, "hookrouter.bulkhead.max-wait-duration"))
            .contains("timeout.duration");
    }

    @Test
    void shouldKeepMetadataDefaultsAlignedWithWebhookConfigPropertiesDefaults() throws IOException {
        String propertiesSection = loadPropertiesSection();
        WebhookConfigProperties properties = new WebhookConfigProperties();

        assertThat(extractDefaultValue(propertiesSection, "hookrouter.retry.max-attempts"))
            .isEqualTo(String.valueOf(properties.getRetry().getMaxAttempts()));
        assertThat(extractDefaultValue(propertiesSection, "hookrouter.retry.initial-delay"))
            .isEqualTo(String.valueOf(properties.getRetry().getInitialDelay()));
        assertThat(extractDefaultValue(propertiesSection, "hookrouter.retry.max-delay"))
            .isEqualTo(String.valueOf(properties.getRetry().getMaxDelay()));
        assertThat(extractDefaultValue(propertiesSection, "hookrouter.retry.multiplier"))
            .isEqualTo(String.valueOf(properties.getRetry().getMultiplier()));
        assertThat(extractDefaultValue(propertiesSection, "hookrouter.timeout.duration"))
            .isEqualTo(String.valueOf(properties.getTimeout().getDuration()));
        assertThat(extractDefaultValue(propertiesSection, "hookrouter.rate-limiter.timeout-duration"))
            .isEqualTo(String.valueOf(properties.getRateLimiter().getTimeoutDuration()));
        assertThat(extractDefaultValue(propertiesSection, "hookrouter.bulkhead.max-concurrent-calls"))
            .isEqualTo(String.valueOf(properties.getBulkhead().getMaxConcurrentCalls()));
        assertThat(extractDefaultValue(propertiesSection, "hookrouter.async.max-pool-size"))
            .isEqualTo(String.valueOf(properties.getAsync().getMaxPoolSize()));
    }

    private String loadPropertiesSection() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(METADATA_PATH)) {
            assertThat(inputStream).as("metadata file must be present").isNotNull();
            String metadata = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            int propertiesIndex = metadata.indexOf("\"properties\"");
            int hintsIndex = metadata.indexOf("\"hints\"");
            assertThat(propertiesIndex).isGreaterThanOrEqualTo(0);
            assertThat(hintsIndex).isGreaterThan(propertiesIndex);
            return metadata.substring(propertiesIndex, hintsIndex);
        }
    }

    private String findPropertyBlock(String propertiesSection, String propertyName) {
        String quotedPropertyName = Pattern.quote(propertyName);
        Pattern pattern = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"" + quotedPropertyName + "\"[\\s\\S]*?\\}",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(propertiesSection);
        assertThat(matcher.find()).as("property block not found: " + propertyName).isTrue();
        return matcher.group();
    }

    private String extractDefaultValue(String propertiesSection, String propertyName) {
        String propertyBlock = findPropertyBlock(propertiesSection, propertyName);
        Pattern pattern = Pattern.compile(
            "\"defaultValue\"\\s*:\\s*(\"([^\"]*)\"|[0-9]+(?:\\.[0-9]+)?|true|false)"
        );
        Matcher matcher = pattern.matcher(propertyBlock);
        assertThat(matcher.find()).as("defaultValue not found: " + propertyName).isTrue();
        String rawValue = matcher.group(1);
        if (rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
            return rawValue.substring(1, rawValue.length() - 1);
        }
        return rawValue;
    }
}
