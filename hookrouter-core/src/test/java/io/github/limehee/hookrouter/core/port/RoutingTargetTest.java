package io.github.limehee.hookrouter.core.port;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RoutingTargetTest {

    @Nested
    class ConstructorTest {

        @Test
        void shouldMatchExpectedTargetPlatform() {
            // When
            RoutingTarget target = new RoutingTarget(
                "slack",
                "error-channel",
                "https://hooks.slack.com/services/xxx"
            );

            // Then
            assertThat(target.platform()).isEqualTo("slack");
            assertThat(target.webhookKey()).isEqualTo("error-channel");
            assertThat(target.webhookUrl()).isEqualTo("https://hooks.slack.com/services/xxx");
        }
    }

    @Nested
    class OfTest {

        @Test
        void shouldMatchExpectedTargetPlatformWhenOfHasUrlLikeValue() {
            // When
            RoutingTarget target = RoutingTarget.of(
                "discord",
                "general-channel",
                "https://discord.com/api/webhooks/xxx"
            );

            // Then
            assertThat(target.platform()).isEqualTo("discord");
            assertThat(target.webhookKey()).isEqualTo("general-channel");
            assertThat(target.webhookUrl()).isEqualTo("https://discord.com/api/webhooks/xxx");
        }
    }

    @Nested
    class EqualsHashCodeTest {

        @Test
        void shouldMatchExpectedTarget1() {
            // Given
            RoutingTarget target1 = RoutingTarget.of("slack", "error", "https://hook/1");
            RoutingTarget target2 = RoutingTarget.of("slack", "error", "https://hook/1");

            // When & Then
            assertThat(target1).isEqualTo(target2);
            assertThat(target1.hashCode()).isEqualTo(target2.hashCode());
        }

        @Test
        void shouldVerifyExpectedTarget1() {
            // Given
            RoutingTarget target1 = RoutingTarget.of("slack", "error", "https://hook/1");
            RoutingTarget target2 = RoutingTarget.of("discord", "error", "https://hook/1");

            // When & Then
            assertThat(target1).isNotEqualTo(target2);
        }

        @Test
        void shouldVerifyExpectedTarget1UsingFactoryMethod() {
            // Given
            RoutingTarget target1 = RoutingTarget.of("slack", "error", "https://hook/1");
            RoutingTarget target2 = RoutingTarget.of("slack", "general", "https://hook/1");

            // When & Then
            assertThat(target1).isNotEqualTo(target2);
        }

        @Test
        void shouldVerifyExpectedTarget1UsingFactoryMethodAndIsNotEqualTo() {
            // Given
            RoutingTarget target1 = RoutingTarget.of("slack", "error", "https://hook/1");
            RoutingTarget target2 = RoutingTarget.of("slack", "error", "https://hook/2");

            // When & Then
            assertThat(target1).isNotEqualTo(target2);
        }
    }

    @Nested
    class ToStringTest {

        @Test
        void shouldContainExpectedResult() {
            // Given
            RoutingTarget target = RoutingTarget.of("slack", "error-channel", "https://hook/test");

            // When
            String result = target.toString();

            // Then
            assertThat(result).contains("slack");
            assertThat(result).contains("error-channel");
            assertThat(result).contains("https://hook/test");
        }
    }
}
