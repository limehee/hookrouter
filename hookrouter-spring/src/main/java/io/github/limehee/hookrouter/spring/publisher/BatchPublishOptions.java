package io.github.limehee.hookrouter.spring.publisher;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

public class BatchPublishOptions {

    public static final int DEFAULT_CHUNK_SIZE = 100;
    public static final BatchPublishOptions DEFAULT = builder().build();
    private final int chunkSize;
    @Nullable
    private final Duration delayBetweenChunks;

    private BatchPublishOptions(int chunkSize, @Nullable Duration delayBetweenChunks) {
        this.chunkSize = chunkSize;
        this.delayBetweenChunks = delayBetweenChunks;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasDelay() {
        return delayBetweenChunks != null && !delayBetweenChunks.isZero() && !delayBetweenChunks.isNegative();
    }

    public int getChunkSize() {
        return this.chunkSize;
    }

    @Nullable
    public Duration getDelayBetweenChunks() {
        return this.delayBetweenChunks;
    }

    public static class Builder {

        private int chunkSize = DEFAULT_CHUNK_SIZE;
        @Nullable
        private Duration delayBetweenChunks;

        public Builder chunkSize(int chunkSize) {
            if (chunkSize < 1) {
                throw new IllegalArgumentException("Chunk size must be at least 1");
            }
            this.chunkSize = chunkSize;
            return this;
        }

        public Builder delayBetweenChunks(@Nullable Duration delay) {
            this.delayBetweenChunks = delay;
            return this;
        }

        public BatchPublishOptions build() {
            return new BatchPublishOptions(chunkSize, delayBetweenChunks);
        }
    }
}
