package io.github.limehee.hookrouter.samples.springmapping.sender;

import io.github.limehee.hookrouter.core.port.WebhookSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingWebhookSender implements WebhookSender {

    private final String platform;
    private final CopyOnWriteArrayList<SendCall> calls = new CopyOnWriteArrayList<>();

    public RecordingWebhookSender(String platform) {
        this.platform = platform;
    }

    @Override
    public String platform() {
        return platform;
    }

    @Override
    public SendResult send(String webhookUrl, Object payload) {
        calls.add(new SendCall(platform, webhookUrl, payload));
        return SendResult.success(200);
    }

    public List<SendCall> calls() {
        return List.copyOf(calls);
    }

    public void clear() {
        calls.clear();
    }

    public record SendCall(String platform, String webhookUrl, Object payload) {

    }
}
