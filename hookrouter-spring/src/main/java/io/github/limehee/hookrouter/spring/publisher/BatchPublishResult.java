package io.github.limehee.hookrouter.spring.publisher;

public record BatchPublishResult(
    int publishedCount,
    int chunkCount,
    int totalCount
) {

    public static BatchPublishResult empty() {
        return new BatchPublishResult(0, 0, 0);
    }

    public static BatchPublishResult of(int publishedCount, int chunkCount, int totalCount) {
        return new BatchPublishResult(publishedCount, chunkCount, totalCount);
    }

    public int skippedCount() {
        return totalCount - publishedCount;
    }

    public boolean isFullyPublished() {
        return publishedCount == totalCount;
    }
}
