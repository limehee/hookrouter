package io.github.limehee.hookrouter.spring.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.DeadLetter;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterHandler.FailureReason;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.DeadLetterStatus;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterStore.StoredDeadLetter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class InMemoryDeadLetterStoreTest {

    private InMemoryDeadLetterStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryDeadLetterStore();
    }

    private DeadLetter createDeadLetter(String platform, String webhookKey) {
        Notification<String> notification = Notification.<String>builder("TEST_TYPE")
            .category("general")
            .context("test payload")
            .build();

        return DeadLetter.of(
            notification,
            platform,
            webhookKey,
            "https://hooks.example.com/" + webhookKey,
            "payload",
            FailureReason.MAX_RETRIES_EXCEEDED,
            "Test error",
            3
        );
    }

    @Nested
    class SaveTest {

        @Test
        void shouldReturnNotNullStoredId() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");

            // When
            StoredDeadLetter stored = store.save(deadLetter);

            // Then
            assertThat(stored.id()).isNotNull();
            assertThat(stored.deadLetter()).isEqualTo(deadLetter);
            assertThat(stored.status()).isEqualTo(DeadLetterStatus.PENDING);
            assertThat(stored.retryCount()).isZero();
            assertThat(stored.maxRetries()).isEqualTo(InMemoryDeadLetterStore.DEFAULT_MAX_RETRIES);
            assertThat(stored.createdAt()).isNotNull();
            assertThat(stored.updatedAt()).isNotNull();
        }

        @Test
        void shouldMatchExpectedStoreSize() {
            // Given
            DeadLetter deadLetter1 = createDeadLetter("slack", "channel-1");
            DeadLetter deadLetter2 = createDeadLetter("slack", "channel-2");

            // When
            store.save(deadLetter1);
            store.save(deadLetter2);

            // Then
            assertThat(store.size()).isEqualTo(2);
        }

        @Test
        void shouldMatchExpectedSmallStoreSize() {
            // Given
            InMemoryDeadLetterStore smallStore = new InMemoryDeadLetterStore(2, 3);
            DeadLetter deadLetter1 = createDeadLetter("slack", "channel-1");
            DeadLetter deadLetter2 = createDeadLetter("slack", "channel-2");
            DeadLetter deadLetter3 = createDeadLetter("slack", "channel-3");

            StoredDeadLetter stored1 = smallStore.save(deadLetter1);
            smallStore.save(deadLetter2);
            smallStore.updateStatus(stored1.id(), DeadLetterStatus.RESOLVED);

            // When
            smallStore.save(deadLetter3);

            // Then
            assertThat(smallStore.size()).isEqualTo(2);
            assertThat(smallStore.findById(stored1.id())).isEmpty();
        }
    }

    @Nested
    class FindByIdTest {

        @Test
        void shouldMatchExpectedFound() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);

            // When
            Optional<StoredDeadLetter> found = store.findById(stored.id());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(stored.id());
            assertThat(found.get().deadLetter()).isEqualTo(deadLetter);
        }

        @Test
        void shouldBeEmptyFound() {
            // When
            Optional<StoredDeadLetter> found = store.findById("non-existent-id");

            // Then
            assertThat(found).isEmpty();
        }
    }

    @Nested
    class FindByStatusTest {

        @Test
        void shouldHaveExpectedSizePendingList() {
            // Given
            DeadLetter deadLetter1 = createDeadLetter("slack", "channel-1");
            DeadLetter deadLetter2 = createDeadLetter("slack", "channel-2");
            DeadLetter deadLetter3 = createDeadLetter("slack", "channel-3");

            StoredDeadLetter stored1 = store.save(deadLetter1);
            store.save(deadLetter2);
            store.save(deadLetter3);

            store.updateStatus(stored1.id(), DeadLetterStatus.RESOLVED);

            // When
            List<StoredDeadLetter> pendingList = store.findByStatus(DeadLetterStatus.PENDING);
            List<StoredDeadLetter> resolvedList = store.findByStatus(DeadLetterStatus.RESOLVED);

            // Then
            assertThat(pendingList).hasSize(2);
            assertThat(resolvedList).hasSize(1);
        }
    }

    @Nested
    class FindReadyForReprocessTest {

        @Test
        void shouldHaveExpectedSizeReadyList() {
            // Given
            DeadLetter deadLetter1 = createDeadLetter("slack", "channel-1");
            DeadLetter deadLetter2 = createDeadLetter("slack", "channel-2");

            store.save(deadLetter1);
            store.save(deadLetter2);

            // When
            List<StoredDeadLetter> readyList = store.findReadyForReprocess(10);

            // Then
            assertThat(readyList).hasSize(2);
        }

        @Test
        void shouldHaveExpectedSizeReadyListWhenSave() {
            // Given
            DeadLetter deadLetter1 = createDeadLetter("slack", "channel-1");
            DeadLetter deadLetter2 = createDeadLetter("slack", "channel-2");

            StoredDeadLetter stored1 = store.save(deadLetter1);
            store.save(deadLetter2);

            store.updateRetryInfo(stored1.id(), 1, Instant.now().plus(Duration.ofHours(1)), null);

            // When
            List<StoredDeadLetter> readyList = store.findReadyForReprocess(10);

            // Then
            assertThat(readyList).hasSize(1);
        }

        @Test
        void shouldHaveExpectedSizeReadyListWhenSaveAndHasSize() {
            // Given
            for (int i = 0; i < 5; i++) {
                store.save(createDeadLetter("slack", "channel-" + i));
            }

            // When
            List<StoredDeadLetter> readyList = store.findReadyForReprocess(3);

            // Then
            assertThat(readyList).hasSize(3);
        }
    }

    @Nested
    class UpdateStatusTest {

        @Test
        void shouldMatchExpectedResult() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);

            // When
            boolean result = store.updateStatus(stored.id(), DeadLetterStatus.PROCESSING);

            // Then
            assertThat(result).isTrue();
            assertThat(store.findById(stored.id()).get().status()).isEqualTo(DeadLetterStatus.PROCESSING);
        }

        @Test
        void shouldBeFalseResult() {
            // When
            boolean result = store.updateStatus("non-existent-id", DeadLetterStatus.PROCESSING);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class UpdateRetryInfoTest {

        @Test
        void shouldMatchExpectedResultWhenSave() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);
            Instant nextRetryAt = Instant.now().plus(Duration.ofMinutes(1));

            // When
            boolean result = store.updateRetryInfo(stored.id(), 1, nextRetryAt, "Connection failed");

            // Then
            assertThat(result).isTrue();
            StoredDeadLetter updated = store.findById(stored.id()).get();
            assertThat(updated.retryCount()).isEqualTo(1);
            assertThat(updated.nextRetryAt()).isEqualTo(nextRetryAt);
            assertThat(updated.lastErrorMessage()).isEqualTo("Connection failed");
            assertThat(updated.status()).isEqualTo(DeadLetterStatus.PENDING);
        }

        @Test
        void shouldMatchExpectedUpdatedStatus() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);

            // When
            store.updateRetryInfo(stored.id(), 3, null, "Max retries exceeded");

            // Then
            StoredDeadLetter updated = store.findById(stored.id()).get();
            assertThat(updated.status()).isEqualTo(DeadLetterStatus.ABANDONED);
        }
    }

    @Nested
    class DeleteTest {

        @Test
        void shouldBeEmptyResult() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);

            // When
            boolean result = store.delete(stored.id());

            // Then
            assertThat(result).isTrue();
            assertThat(store.findById(stored.id())).isEmpty();
            assertThat(store.size()).isZero();
        }

        @Test
        void shouldBeFalseResultWhenDelete() {
            // When
            boolean result = store.delete("non-existent-id");

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class DeleteOlderThanTest {

        @Test
        void shouldMatchExpectedDeleted() {
            // Given
            DeadLetter deadLetter1 = createDeadLetter("slack", "channel-1");
            DeadLetter deadLetter2 = createDeadLetter("slack", "channel-2");

            store.save(deadLetter1);
            store.save(deadLetter2);

            // When
            int deleted = store.deleteOlderThan(Instant.now().plus(Duration.ofHours(1)));

            // Then
            assertThat(deleted).isEqualTo(2);
            assertThat(store.size()).isZero();
        }
    }

    @Nested
    class CountByStatusTest {

        @Test
        void shouldMatchExpectedPendingCount() {
            // Given
            StoredDeadLetter stored1 = store.save(createDeadLetter("slack", "channel-1"));
            store.save(createDeadLetter("slack", "channel-2"));
            store.save(createDeadLetter("slack", "channel-3"));

            store.updateStatus(stored1.id(), DeadLetterStatus.RESOLVED);

            // When
            long pendingCount = store.countByStatus(DeadLetterStatus.PENDING);
            long resolvedCount = store.countByStatus(DeadLetterStatus.RESOLVED);

            // Then
            assertThat(pendingCount).isEqualTo(2);
            assertThat(resolvedCount).isEqualTo(1);
        }
    }

    @Nested
    class StoredDeadLetterRecordTest {

        @Test
        void shouldBeTrueStoredCanRetry() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);

            // Then
            assertThat(stored.canRetry()).isTrue();
        }

        @Test
        void shouldBeFalseUpdatedCanRetry() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);
            store.updateRetryInfo(stored.id(), 3, null, null);

            // When
            StoredDeadLetter updated = store.findById(stored.id()).get();

            // Then
            assertThat(updated.canRetry()).isFalse();
        }

        @Test
        void shouldMatchExpectedStoredTypeId() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);

            // Then
            assertThat(stored.typeId()).isEqualTo("TEST_TYPE");
        }

        @Test
        void shouldMatchExpectedStoredPlatform() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);

            // Then
            assertThat(stored.platform()).isEqualTo("slack");
        }

        @Test
        void shouldMatchExpectedStoredWebhookKey() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);

            // Then
            assertThat(stored.webhookKey()).isEqualTo("test-channel");
        }

        @Test
        void shouldMatchExpectedStoredFailureReason() {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);

            // Then
            assertThat(stored.failureReason()).isEqualTo(FailureReason.MAX_RETRIES_EXCEEDED);
        }
    }

    @Nested
    class ClearTest {

        @Test
        void shouldVerifyExpectedStoreSize() {
            // Given
            store.save(createDeadLetter("slack", "channel-1"));
            store.save(createDeadLetter("slack", "channel-2"));

            // When
            store.clear();

            // Then
            assertThat(store.size()).isZero();
        }
    }

    @Nested
    class ConcurrencyTest {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void shouldMatchExpectedSuccessCount() throws InterruptedException {
            // Given
            int maxSize = 100;
            int threadCount = 20;
            int savesPerThread = 3;
            InMemoryDeadLetterStore smallStore = new InMemoryDeadLetterStore(maxSize, 3);

            for (int i = 0; i < maxSize / 2; i++) {
                StoredDeadLetter stored = smallStore.save(createDeadLetter("slack", "initial-" + i));
                smallStore.updateStatus(stored.id(), DeadLetterStatus.RESOLVED);
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < savesPerThread; j++) {
                            StoredDeadLetter saved = smallStore.save(
                                createDeadLetter("slack", "channel-" + threadId + "-" + j));
                            if (saved != null) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(threadCount * savesPerThread);
            assertThat(smallStore.size()).isLessThanOrEqualTo(maxSize);
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void shouldMatchExpectedSuccessCountWhenSave() throws InterruptedException {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);
            String id = stored.id();

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);

            DeadLetterStatus[] statuses = {
                DeadLetterStatus.PROCESSING,
                DeadLetterStatus.RESOLVED,
                DeadLetterStatus.ABANDONED,
                DeadLetterStatus.PENDING
            };

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        DeadLetterStatus targetStatus = statuses[threadId % statuses.length];
                        boolean result = store.updateStatus(id, targetStatus);
                        if (result) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(threadCount);
            StoredDeadLetter updated = store.findById(id).orElseThrow();
            assertThat(updated.status()).isIn((Object[]) statuses);
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void shouldBeWithinExpectedRangeUpdatedRetryCount() throws InterruptedException {
            // Given
            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);
            String id = stored.id();

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            ConcurrentHashMap<Integer, Integer> retryCountMap = new ConcurrentHashMap<>();

            for (int i = 0; i < threadCount; i++) {
                final int retryCount = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        boolean result = store.updateRetryInfo(
                            id, retryCount, Instant.now(), "Error " + retryCount);
                        if (result) {
                            retryCountMap.put(retryCount, retryCount);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            StoredDeadLetter updated = store.findById(id).orElseThrow();
            assertThat(updated.retryCount()).isBetween(1, threadCount);
            assertThat(retryCountMap).isNotEmpty();
        }
    }

    @Nested
    class StuckProcessingRecoveryTest {

        @Test
        void shouldVerifyExpectedCurrentStatus() throws InterruptedException {

            DeadLetter deadLetter = createDeadLetter("slack", "test-channel");
            StoredDeadLetter stored = store.save(deadLetter);
            String id = stored.id();

            store.updateStatus(id, DeadLetterStatus.PROCESSING);

            Thread.sleep(100);

            List<StoredDeadLetter> readyList = store.findReadyForReprocess(10);

            StoredDeadLetter current = store.findById(id).orElseThrow();

            assertThat(current.status()).isIn(DeadLetterStatus.PROCESSING, DeadLetterStatus.PENDING);
        }

        @Test
        void shouldMatchExpectedReadyList() {
            // Given
            DeadLetter deadLetter1 = createDeadLetter("slack", "channel-1");
            DeadLetter deadLetter2 = createDeadLetter("slack", "channel-2");

            store.save(deadLetter1);
            StoredDeadLetter stored2 = store.save(deadLetter2);

            store.updateStatus(stored2.id(), DeadLetterStatus.PROCESSING);

            // When
            List<StoredDeadLetter> readyList = store.findReadyForReprocess(10);

            assertThat(readyList).hasSize(1);
            assertThat(readyList.get(0).status()).isEqualTo(DeadLetterStatus.PENDING);
        }

        @Test
        void shouldMatchExpectedReadyListWhenSave() {
            // Given
            DeadLetter deadLetter1 = createDeadLetter("slack", "channel-1");
            DeadLetter deadLetter2 = createDeadLetter("slack", "channel-2");
            DeadLetter deadLetter3 = createDeadLetter("slack", "channel-3");

            StoredDeadLetter stored1 = store.save(deadLetter1);
            StoredDeadLetter stored2 = store.save(deadLetter2);
            store.save(deadLetter3);

            store.updateStatus(stored1.id(), DeadLetterStatus.RESOLVED);
            store.updateStatus(stored2.id(), DeadLetterStatus.ABANDONED);

            // When
            List<StoredDeadLetter> readyList = store.findReadyForReprocess(10);

            assertThat(readyList).hasSize(1);
            assertThat(readyList.get(0).status()).isEqualTo(DeadLetterStatus.PENDING);
        }
    }
}
