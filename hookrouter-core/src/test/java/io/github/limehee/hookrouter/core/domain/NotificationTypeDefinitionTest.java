package io.github.limehee.hookrouter.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.limehee.hookrouter.core.exception.InvalidWebhookArgumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationTypeDefinitionTest {

    @Nested
    class BuilderTest {

        @Test
        void shouldMatchExpectedDefinitionTypeId() {
            // When
            NotificationTypeDefinition definition = NotificationTypeDefinition.builder()
                .typeId("demo.server.error")
                .title("Server error")
                .defaultMessage("Server error occurred.")
                .category("general")
                .schemaVersion("1.0")
                .build();

            // Then
            assertThat(definition.typeId()).isEqualTo("demo.server.error");
            assertThat(definition.title()).isEqualTo("Server error");
            assertThat(definition.defaultMessage()).isEqualTo("Server error occurred.");
            assertThat(definition.category()).isEqualTo("general");
            assertThat(definition.schemaVersion()).isEqualTo("1.0");
        }

        @Test
        void shouldReturnNullDefinitionTypeId() {
            // When
            NotificationTypeDefinition definition = NotificationTypeDefinition.builder()
                .typeId("demo.server.start")
                .title("Server started")
                .defaultMessage("Server started.")
                .category("general")
                .build();

            // Then
            assertThat(definition.typeId()).isEqualTo("demo.server.start");
            assertThat(definition.schemaVersion()).isNull();
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenInvalidInput() {
            // When & Then
            assertThatThrownBy(() -> NotificationTypeDefinition.builder()
                .title("Server error")
                .defaultMessage("Server error occurred.")
                .category("general")
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenDefaultMessageIsMissing() {
            // When & Then
            assertThatThrownBy(() -> NotificationTypeDefinition.builder()
                .typeId("   ")
                .title("Server error")
                .defaultMessage("Server error occurred.")
                .category("general")
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenTitleIsBlank() {
            // When & Then
            assertThatThrownBy(() -> NotificationTypeDefinition.builder()
                .typeId("demo.server.error")
                .defaultMessage("Server error occurred.")
                .category("general")
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenDefaultMessageIsBlank() {
            // When & Then
            assertThatThrownBy(() -> NotificationTypeDefinition.builder()
                .typeId("demo.server.error")
                .title("  ")
                .defaultMessage("Server error occurred.")
                .category("general")
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenCategoryIsMissing() {
            // When & Then
            assertThatThrownBy(() -> NotificationTypeDefinition.builder()
                .typeId("demo.server.error")
                .title("Server error")
                .category("general")
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }

        @Test
        void shouldThrowInvalidWebhookArgumentExceptionWhenInvalidInputAndTypeId() {
            // When & Then
            assertThatThrownBy(() -> NotificationTypeDefinition.builder()
                .typeId("demo.server.error")
                .title("Server error")
                .defaultMessage("Server error occurred.")
                .build())
                .isInstanceOf(InvalidWebhookArgumentException.class);
        }
    }

    @Nested
    class ConstructorTest {

        @Test
        void shouldReturnNullDefinitionTypeIdWhenInput() {
            // When
            NotificationTypeDefinition definition = new NotificationTypeDefinition(
                "demo.server.error",
                "Server error",
                "Server error occurred.",
                "general"
            );

            // Then
            assertThat(definition.typeId()).isEqualTo("demo.server.error");
            assertThat(definition.title()).isEqualTo("Server error");
            assertThat(definition.defaultMessage()).isEqualTo("Server error occurred.");
            assertThat(definition.category()).isEqualTo("general");
            assertThat(definition.schemaVersion()).isNull();
        }

        @Test
        void shouldMatchExpectedDefinitionSchemaVersion() {
            // When
            NotificationTypeDefinition definition = new NotificationTypeDefinition(
                "demo.server.error",
                "Server error",
                "Server error occurred.",
                "general",
                "2.0"
            );

            // Then
            assertThat(definition.schemaVersion()).isEqualTo("2.0");
        }
    }
}
