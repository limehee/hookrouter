package io.github.limehee.hookrouter.core.port;

public record RoutingTarget(
    String platform,
    String webhookKey,
    String webhookUrl
) {

    public static RoutingTarget of(String platform, String webhookKey, String webhookUrl) {
        return new RoutingTarget(platform, webhookKey, webhookUrl);
    }
}
