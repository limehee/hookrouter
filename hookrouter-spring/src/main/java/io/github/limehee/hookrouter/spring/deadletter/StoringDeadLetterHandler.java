package io.github.limehee.hookrouter.spring.deadletter;

import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.StoredDeadLetter;

public class StoringDeadLetterHandler implements DeadLetterHandler {

    private final DeadLetterStore store;

    public StoringDeadLetterHandler(final DeadLetterStore store) {
        this.store = store;
    }

    @Override
    public void handle(DeadLetter deadLetter) {
        StoredDeadLetter stored = store.save(deadLetter);
    }
}
