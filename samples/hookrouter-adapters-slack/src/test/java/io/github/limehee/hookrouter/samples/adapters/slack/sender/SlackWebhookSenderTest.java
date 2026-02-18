package io.github.limehee.hookrouter.samples.adapters.slack.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import io.github.limehee.hookrouter.samples.adapters.slack.payload.SlackPayload;
import io.github.limehee.hookrouter.core.port.WebhookSender.SendResult;
import com.slack.api.Slack;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.WebhookResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlackWebhookSenderTest {

    private SlackWebhookSender sender;

    @Mock
    private Slack slack;

    @BeforeEach
    void setUp() {
        sender = new SlackWebhookSender(slack);
    }

    @Nested
    class PlatformTest {

        @Test
        void shouldMatchExpectedPlatform() {
            // When
            String platform = sender.platform();

            // Then
            assertThat(platform).isEqualTo("slack");
        }
    }

    @Nested
    class SendTest {

        private final String webhookUrl = "https://hooks.slack.com/services/test";

        @Test
        void shouldReturnNullResultSuccess() throws IOException {
            // Given
            SlackPayload payload = new SlackPayload("test message");
            WebhookResponse response = WebhookResponse.builder()
                .code(200)
                .body("ok")
                .build();
            given(slack.send(eq(webhookUrl), any(Payload.class))).willReturn(response);

            // When
            SendResult result = sender.send(webhookUrl, payload);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        void shouldMatchExpectedResultSuccess() {
            // Given
            String invalidPayload = "invalid payload";

            // When
            SendResult result = sender.send(webhookUrl, invalidPayload);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(-1);
            assertThat(result.errorMessage()).contains("Invalid payload type");
            assertThat(result.retryable()).isFalse();
        }

        @Test
        void shouldMatchExpectedResultSuccessWhenCodeIsPositive() throws IOException {
            // Given
            SlackPayload payload = new SlackPayload("test");
            WebhookResponse response = WebhookResponse.builder()
                .code(400)
                .body("invalid_payload")
                .build();
            given(slack.send(eq(webhookUrl), any(Payload.class))).willReturn(response);

            // When
            SendResult result = sender.send(webhookUrl, payload);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(400);
            assertThat(result.errorMessage()).contains("Slack API error");
            assertThat(result.retryable()).isFalse();
        }

        @Test
        void shouldMatchExpectedResultSuccessWhenServerErrorHasNoResponseBody() throws IOException {
            // Given
            SlackPayload payload = new SlackPayload("test");
            WebhookResponse response = WebhookResponse.builder()
                .code(500)
                .body(null)
                .build();
            given(slack.send(eq(webhookUrl), any(Payload.class))).willReturn(response);

            // When
            SendResult result = sender.send(webhookUrl, payload);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(500);
            assertThat(result.errorMessage()).contains("Slack API error");
            assertThat(result.errorMessage()).contains("(no response body)");
            assertThat(result.retryable()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {429, 500, 502, 503, 504})
        void shouldBeTrueResultSuccess(int statusCode) throws IOException {
            // Given
            SlackPayload payload = new SlackPayload("test");
            WebhookResponse response = WebhookResponse.builder()
                .code(statusCode)
                .body("error")
                .build();
            given(slack.send(eq(webhookUrl), any(Payload.class))).willReturn(response);

            // When
            SendResult result = sender.send(webhookUrl, payload);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.retryable()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 401, 403, 404})
        void shouldBeFalseResultSuccess(int statusCode) throws IOException {
            // Given
            SlackPayload payload = new SlackPayload("test");
            WebhookResponse response = WebhookResponse.builder()
                .code(statusCode)
                .body("error")
                .build();
            given(slack.send(eq(webhookUrl), any(Payload.class))).willReturn(response);

            // When
            SendResult result = sender.send(webhookUrl, payload);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.retryable()).isFalse();
        }

        @Test
        void shouldMatchExpectedResultSuccessWhenSend() throws IOException {
            // Given
            SlackPayload payload = new SlackPayload("test");
            given(slack.send(eq(webhookUrl), any(Payload.class)))
                .willThrow(new IOException("Connection refused"));

            // When
            SendResult result = sender.send(webhookUrl, payload);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(-1);
            assertThat(result.errorMessage()).contains("Connection refused");
            assertThat(result.retryable()).isTrue();
        }

        @Test
        void shouldMatchExpectedResultSuccessWhenSlackClientThrowsUnexpectedException() throws IOException {
            // Given
            SlackPayload payload = new SlackPayload("test");
            given(slack.send(eq(webhookUrl), any(Payload.class)))
                .willThrow(new RuntimeException("Unexpected error"));

            // When
            SendResult result = sender.send(webhookUrl, payload);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isEqualTo(-1);
            assertThat(result.errorMessage()).contains("Unexpected error");
            assertThat(result.retryable()).isFalse();
        }
    }

    @Nested
    class PayloadConversionTest {

        private final String webhookUrl = "https://hooks.slack.com/services/test";

        @Test
        void shouldBeTrueResultSuccessWhenCodeIsPositive() throws IOException {
            // Given
            SlackPayload payload = new SlackPayload("text only");
            WebhookResponse response = WebhookResponse.builder()
                .code(200)
                .body("ok")
                .build();
            given(slack.send(eq(webhookUrl), any(Payload.class))).willReturn(response);

            // When
            SendResult result = sender.send(webhookUrl, payload);

            // Then
            assertThat(result.success()).isTrue();
        }

        @Test
        void shouldBeTrueResultSuccessWhenText() throws IOException {
            // Given
            SlackPayload payload = SlackPayload.builder()
                .text("fallback")
                .build();
            WebhookResponse response = WebhookResponse.builder()
                .code(200)
                .body("ok")
                .build();
            given(slack.send(eq(webhookUrl), any(Payload.class))).willReturn(response);

            // When
            SendResult result = sender.send(webhookUrl, payload);

            // Then
            assertThat(result.success()).isTrue();
        }
    }

    @Nested
    class ConstructorTest {

        @Test
        void shouldReturnNotNullDefaultSender() {
            // When
            SlackWebhookSender defaultSender = new SlackWebhookSender();

            // Then
            assertThat(defaultSender).isNotNull();
            assertThat(defaultSender.platform()).isEqualTo("slack");
        }
    }
}
