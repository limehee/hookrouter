package io.github.limehee.hookrouter.spring.deadletter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties;
import io.github.limehee.hookrouter.spring.config.WebhookConfigProperties.DeadLetterProperties;
import io.github.limehee.hookrouter.spring.deadletter.DeadLetterReprocessor.ReprocessSummary;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeadLetterSchedulerTest {

    @Mock
    private DeadLetterReprocessor reprocessor;

    @Mock
    private WebhookConfigProperties properties;

    @Mock
    private DeadLetterProperties deadLetterProperties;

    private DeadLetterScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getDeadLetter()).thenReturn(deadLetterProperties);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scheduler != null) {
            scheduler.destroy();
        }
    }

    @Nested
    class AfterPropertiesSetTest {

        @Test
        void shouldInvokeExpectedInteractions() {
            // Given
            given(deadLetterProperties.getSchedulerInterval()).willReturn(100L);
            given(deadLetterProperties.getSchedulerBatchSize()).willReturn(10);
            given(reprocessor.reprocessPending(anyInt())).willReturn(new ReprocessSummary(0, 0, 0));

            scheduler = new DeadLetterScheduler(reprocessor, properties);

            // When
            scheduler.afterPropertiesSet();

            // Then
            await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> verify(reprocessor, atLeast(1)).reprocessPending(10));
        }

        @Test
        void shouldInvokeExpectedInteractionsWhenPendingItemCountIsPositive() {
            // Given
            int customBatchSize = 25;
            given(deadLetterProperties.getSchedulerInterval()).willReturn(100L);
            given(deadLetterProperties.getSchedulerBatchSize()).willReturn(customBatchSize);
            given(reprocessor.reprocessPending(anyInt())).willReturn(new ReprocessSummary(0, 0, 0));

            scheduler = new DeadLetterScheduler(reprocessor, properties);

            // When
            scheduler.afterPropertiesSet();

            // Then
            await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> verify(reprocessor, atLeast(1)).reprocessPending(customBatchSize));
        }
    }

    @Nested
    class DestroyTest {

        @Test
        void shouldNotThrowExceptionDuringExecution() throws Exception {
            // Given
            given(deadLetterProperties.getSchedulerInterval()).willReturn(60_000L);

            scheduler = new DeadLetterScheduler(reprocessor, properties);
            scheduler.afterPropertiesSet();

            assertThatCode(() -> {
                scheduler.destroy();
                scheduler = null;
            }).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowExceptionDuringExecutionWhenInput() throws Exception {
            // Given
            scheduler = new DeadLetterScheduler(reprocessor, properties);

            assertThatCode(() -> {
                scheduler.destroy();
                scheduler = null;
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    class ReprocessLogicTest {

        @Test
        void shouldInvokeExpectedInteractionsWhenPendingItemCountIsPositive() {
            // Given
            given(deadLetterProperties.getSchedulerInterval()).willReturn(100L);
            given(deadLetterProperties.getSchedulerBatchSize()).willReturn(10);
            given(reprocessor.reprocessPending(anyInt()))
                .willReturn(new ReprocessSummary(5, 1, 2));

            scheduler = new DeadLetterScheduler(reprocessor, properties);

            // When
            scheduler.afterPropertiesSet();

            // Then
            await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> verify(reprocessor, atLeast(1)).reprocessPending(10));
        }

        @Test
        void shouldInvokeExpectedInteractionsWhenSchedulerContinuesAfterReprocessException() {
            // Given
            given(deadLetterProperties.getSchedulerInterval()).willReturn(100L);
            given(deadLetterProperties.getSchedulerBatchSize()).willReturn(10);
            given(reprocessor.reprocessPending(anyInt()))
                .willThrow(new RuntimeException("Test exception"))
                .willReturn(new ReprocessSummary(1, 0, 0));

            scheduler = new DeadLetterScheduler(reprocessor, properties);

            // When
            scheduler.afterPropertiesSet();

            await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> verify(reprocessor, atLeast(2)).reprocessPending(10));
        }
    }
}
