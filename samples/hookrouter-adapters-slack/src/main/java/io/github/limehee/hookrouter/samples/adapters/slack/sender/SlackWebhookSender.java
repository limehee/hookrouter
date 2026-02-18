package io.github.limehee.hookrouter.samples.adapters.slack.sender;

import io.github.limehee.hookrouter.samples.adapters.slack.payload.SlackPayload;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import com.slack.api.Slack;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.Payload.PayloadBuilder;
import com.slack.api.webhook.WebhookResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class SlackWebhookSender implements WebhookSender {

    private static final String PLATFORM_NAME = "slack";
    private final Slack slack;

    public SlackWebhookSender() {
        this.slack = Slack.getInstance();
    }

    SlackWebhookSender(Slack slack) {
        this.slack = slack;
    }

    @Override
    public String platform() {
        return PLATFORM_NAME;
    }

    @Override
    public SendResult send(String webhookUrl, Object payload) {

        if (!(payload instanceof SlackPayload slackPayload)) {
            return SendResult.failure(-1, "Invalid payload type: expected SlackPayload", false);
        }

        try {

            Payload sdkPayload = buildSdkPayload(slackPayload);

            WebhookResponse response = slack.send(webhookUrl, sdkPayload);

            int statusCode = response.getCode();
            if (statusCode == HttpStatus.OK.value()) {
                return SendResult.success(statusCode);
            }

            boolean retryable = isRetryable(statusCode);

            String responseBody = Objects.requireNonNullElse(response.getBody(), "(no response body)");
            String errorMessage = String.format("Slack API error: status=%d, body=%s",
                statusCode, responseBody);

            return SendResult.failure(statusCode, errorMessage, retryable);

        } catch (IOException e) {
            return SendResult.networkError(
                Objects.requireNonNullElse(e.getMessage(), "network error"));
        } catch (Exception e) {
            return SendResult.failure(-1,
                Objects.requireNonNullElse(e.getMessage(), "unexpected error"), false);
        }
    }

    private Payload buildSdkPayload(SlackPayload slackPayload) {
        PayloadBuilder builder = Payload.builder();

        if (slackPayload.getText() != null) {
            builder.text(slackPayload.getText());
        }

        if (!slackPayload.getBlocks().isEmpty()) {
            builder.blocks(slackPayload.getBlocks());
        }

        if (!slackPayload.getAttachments().isEmpty()) {
            builder.attachments(slackPayload.getAttachments());
        }

        return builder.build();
    }

    private boolean isRetryable(int statusCode) {
        HttpStatusCode status = HttpStatusCode.valueOf(statusCode);
        // 429: Too Many Requests, 5xx: Server errors
        return status.is5xxServerError() || status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
    }
}
