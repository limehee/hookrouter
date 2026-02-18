package io.github.limehee.hookrouter.spring.deadletter;

public final class NoOpDeadLetterHandler implements DeadLetterHandler {

    public static final NoOpDeadLetterHandler INSTANCE = new NoOpDeadLetterHandler();

    private NoOpDeadLetterHandler() {
    }

    @Override
    public void handle(DeadLetter deadLetter) {
        // Intentionally empty - Null Object pattern
    }
}
