package io.github.limehee.hookrouter.samples.springmapping.sender;

import io.github.limehee.hookrouter.core.port.WebhookSender;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RecordingWebhookSender implements WebhookSender {

    private final String platform;
    private final CopyOnWriteArrayList<SendCall> calls = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> remainingFailuresByUrl = new ConcurrentHashMap<>();
    private final Map<String, Boolean> retryableFailureByUrl = new ConcurrentHashMap<>();

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
        AtomicInteger remainingFailures = remainingFailuresByUrl.get(webhookUrl);
        if (remainingFailures != null && remainingFailures.get() > 0) {
            int afterDecrement = remainingFailures.decrementAndGet();
            if (afterDecrement <= 0) {
                remainingFailuresByUrl.remove(webhookUrl);
            }
            boolean retryable = retryableFailureByUrl.getOrDefault(webhookUrl, false);
            return SendResult.failure(500, "simulated failure for sample DLQ flow", retryable);
        }
        return SendResult.success(200);
    }

    public void configureFailures(String webhookUrl, int failures, boolean retryable) {
        if (failures <= 0) {
            remainingFailuresByUrl.remove(webhookUrl);
            retryableFailureByUrl.remove(webhookUrl);
            return;
        }
        remainingFailuresByUrl.put(webhookUrl, new AtomicInteger(failures));
        retryableFailureByUrl.put(webhookUrl, retryable);
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
