package io.github.limehee.hookrouter.samples.adapters.slack.config;

public interface SlackFormatterProperties {

    String DEFAULT_COLOR = "#36a64f";

    default String getDefaultColor() {
        return DEFAULT_COLOR;
    }
}
