package io.github.limehee.hookrouter.samples.adapters.slack.formatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import io.github.limehee.hookrouter.samples.adapters.slack.config.SlackFormatterProperties;
import io.github.limehee.hookrouter.samples.adapters.slack.payload.SlackPayload;
import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.registry.NotificationTypeRegistry;
import com.slack.api.model.Attachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenericSlackFallbackFormatterTest {

    private GenericSlackFallbackFormatter formatter;

    @Mock
    private NotificationTypeRegistry typeRegistry;

    @Mock
    private SlackFormatterProperties slackFormatterProperties;

    @BeforeEach
    void setUp() {
        lenient().when(slackFormatterProperties.getDefaultColor()).thenReturn("#36a64f");
        formatter = new GenericSlackFallbackFormatter(typeRegistry, slackFormatterProperties);
    }

    private record TestContext(String data) {

        @Override
        public String toString() {
            return "TestContext{data='" + data + "'}";
        }
    }

    @Nested
    class KeyTest {

        @Test
        void shouldMatchExpectedKeyPlatform() {
            // When
            FormatterKey key = formatter.key();

            // Then
            assertThat(key.platform()).isEqualTo("slack");
            assertThat(key.isFallback()).isTrue();
        }
    }

    @Nested
    class ContextClassTest {

        @Test
        void shouldMatchExpectedContextClass() {
            // When
            Class<Object> contextClass = formatter.contextClass();

            // Then
            assertThat(contextClass).isEqualTo(Object.class);
        }
    }

    @Nested
    class FormatTest {

        @Test
        void shouldContainExpectedPayloadText() {
            // Given
            String typeId = "demo.test.event";
            NotificationTypeDefinition typeDef = NotificationTypeDefinition.builder()
                .typeId(typeId)
                .title("Test event")
                .defaultMessage("This is a test message.")
                .category("general")
                .build();

            given(typeRegistry.find(typeId)).willReturn(typeDef);

            @SuppressWarnings("unchecked")
            Notification<Object> notification = Notification.builder(typeId)
                .category("general")
                .context(new TestContext("Context data"))
                .build();

            // When
            SlackPayload payload = formatter.format(notification);

            // Then
            assertThat(payload.getText()).contains("Test event");
            assertThat(payload.getText()).contains("This is a test message.");
            assertThat(payload.getAttachments()).hasSize(1);
            assertThat(payload.getAttachments().get(0).getBlocks()).isNotEmpty();
        }

        @Test
        void shouldContainExpectedPayloadTextWhenFind() {
            // Given
            String typeId = "demo.unknown.event";
            given(typeRegistry.find(typeId)).willReturn(null);

            @SuppressWarnings("unchecked")
            Notification<Object> notification = Notification.builder(typeId)
                .category("general")
                .context(new TestContext("context"))
                .build();

            // When
            SlackPayload payload = formatter.format(notification);

            // Then
            assertThat(payload.getText()).contains(typeId);
            assertThat(payload.getText()).contains("Notification received");
        }

        @Test
        void shouldMatchExpectedPayloadAttachments() {
            // Given
            String color = "#ff0000";
            lenient().when(slackFormatterProperties.getDefaultColor()).thenReturn(color);

            String typeId = "demo.test.event";
            given(typeRegistry.find(typeId)).willReturn(null);

            @SuppressWarnings("unchecked")
            Notification<Object> notification = Notification.builder(typeId)
                .category("general")
                .context(new TestContext("context"))
                .build();

            // When
            SlackPayload payload = formatter.format(notification);

            // Then
            assertThat(payload.getAttachments()).hasSize(1);
            Attachment attachment = payload.getAttachments().get(0);
            assertThat(attachment.getColor()).isEqualTo(color);
        }

        @Test
        void shouldHaveExpectedSizePayloadAttachments() {
            // Given
            String typeId = "demo.test.event";
            given(typeRegistry.find(typeId)).willReturn(null);

            TestContext context = new TestContext("Important context information");
            @SuppressWarnings("unchecked")
            Notification<Object> notification = Notification.builder(typeId)
                .category("general")
                .context(context)
                .build();

            // When
            SlackPayload payload = formatter.format(notification);

            // Then
            assertThat(payload.getAttachments()).hasSize(1);
            assertThat(payload.getAttachments().get(0).getBlocks()).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        void shouldHaveExpectedSizePayloadAttachmentsWhenFind() {
            // Given
            String typeId = "demo.test.event";
            given(typeRegistry.find(typeId)).willReturn(null);

            String longString = "x".repeat(3000);
            @SuppressWarnings("unchecked")
            Notification<Object> notification = Notification.builder(typeId)
                .category("general")
                .context(longString)
                .build();

            // When
            SlackPayload payload = formatter.format(notification);

            assertThat(payload.getAttachments()).hasSize(1);
            assertThat(payload.getAttachments().get(0).getBlocks()).hasSize(3);
        }

        @Test
        void shouldHaveExpectedSizePayloadAttachmentsWhenFindAndGetFirst() {
            // Given
            String typeId = "demo.test.event";
            given(typeRegistry.find(typeId)).willReturn(null);

            @SuppressWarnings("unchecked")
            Notification<Object> notification = Notification.builder(typeId)
                .category("general")
                .context("")
                .build();

            // When
            SlackPayload payload = formatter.format(notification);

            assertThat(payload.getAttachments()).hasSize(1);
            assertThat(payload.getAttachments().get(0).getBlocks()).hasSize(3);
        }
    }
}
