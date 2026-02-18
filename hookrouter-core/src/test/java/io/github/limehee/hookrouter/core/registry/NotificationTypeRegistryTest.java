package io.github.limehee.hookrouter.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.exception.DuplicateNotificationTypeException;
import io.github.limehee.hookrouter.core.exception.NotificationTypeNotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationTypeRegistryTest {

    private NotificationTypeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NotificationTypeRegistry();
    }

    @Nested
    class RegisterTest {

        @Test
        void shouldMatchExpectedRegistryContains() {
            // Given
            NotificationTypeDefinition definition = createDefinition("demo.server.start");

            // When
            registry.register(definition);

            // Then
            assertThat(registry.contains("demo.server.start")).isTrue();
            assertThat(registry.size()).isEqualTo(1);
        }

        @Test
        void shouldMatchExpectedRegistrySize() {
            // Given
            List<NotificationTypeDefinition> definitions = List.of(
                createDefinition("demo.server.start"),
                createDefinition("demo.server.error"),
                createDefinition("demo.user.registered")
            );

            // When
            registry.registerAll(definitions);

            // Then
            assertThat(registry.size()).isEqualTo(3);
            assertThat(registry.contains("demo.server.start")).isTrue();
            assertThat(registry.contains("demo.server.error")).isTrue();
            assertThat(registry.contains("demo.user.registered")).isTrue();
        }

        @Test
        void shouldThrowDuplicateNotificationTypeExceptionWhenInvalidInput() {
            // Given
            NotificationTypeDefinition definition1 = createDefinition("demo.server.start");
            NotificationTypeDefinition definition2 = createDefinition("demo.server.start");
            registry.register(definition1);

            // When & Then
            assertThatThrownBy(() -> registry.register(definition2))
                .isInstanceOf(DuplicateNotificationTypeException.class)
                .hasMessageContaining("demo.server.start");
        }

        private NotificationTypeDefinition createDefinition(String typeId) {
            return NotificationTypeDefinition.builder()
                .typeId(typeId)
                .title("Test Title")
                .defaultMessage("Test message")
                .category("general")
                .build();
        }
    }

    @Nested
    class GetTest {

        @Test
        void shouldMatchExpectedResultTypeId() {
            // Given
            NotificationTypeDefinition definition = NotificationTypeDefinition.builder()
                .typeId("demo.server.error")
                .title("Server error")
                .defaultMessage("Server error occurred.")
                .category("general")
                .build();
            registry.register(definition);

            // When
            NotificationTypeDefinition result = registry.get("demo.server.error");

            // Then
            assertThat(result.typeId()).isEqualTo("demo.server.error");
            assertThat(result.title()).isEqualTo("Server error");
            assertThat(result.category()).isEqualTo("general");
        }

        @Test
        void shouldReturnNullResult() {
            // When
            NotificationTypeDefinition result = registry.find("non.existent.type");

            // Then
            assertThat(result).isNull();
        }

        @Test
        void shouldThrowNotificationTypeNotFoundExceptionWhenInvalidInput() {
            // When & Then
            assertThatThrownBy(() -> registry.get("non.existent.type"))
                .isInstanceOf(NotificationTypeNotFoundException.class)
                .hasMessageContaining("non.existent.type");
        }

        @Test
        void shouldContainExpectedResult() {
            // Given
            registry.register(NotificationTypeDefinition.builder()
                .typeId("type1")
                .title("Title 1")
                .defaultMessage("Message 1")
                .category("general")
                .build());
            registry.register(NotificationTypeDefinition.builder()
                .typeId("type2")
                .title("Title 2")
                .defaultMessage("Message 2")
                .category("general")
                .build());

            // When
            var result = registry.list();

            // Then
            assertThat(result).hasSize(2);
            assertThat(registry.listTypeIds()).containsExactlyInAnyOrder("type1", "type2");
        }
    }
}
