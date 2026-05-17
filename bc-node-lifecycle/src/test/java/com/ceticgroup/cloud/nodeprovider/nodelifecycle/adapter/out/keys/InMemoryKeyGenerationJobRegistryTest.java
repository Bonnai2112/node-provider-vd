package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.keys;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase.GenerateValidatorKeysResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryKeyGenerationJobRegistryTest {

    private final OwnerId ownerA = new OwnerId(UUID.randomUUID());
    private final OwnerId ownerB = new OwnerId(UUID.randomUUID());

    private MutableClock clock;
    private ExecutorService worker;
    private ScheduledExecutorService sweeper;
    private InMemoryKeyGenerationJobRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-05-17T10:00:00Z"));
        // Run synchronously so tests don't depend on thread scheduling.
        worker = Executors.newSingleThreadExecutor();
        sweeper = Executors.newSingleThreadScheduledExecutor();
        registry =
                new InMemoryKeyGenerationJobRegistry(
                        worker, sweeper, clock, Duration.ofMinutes(30));
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void poll_should_returnRunning_when_workNotYetCompleted() throws Exception {
        java.util.concurrent.CountDownLatch block = new java.util.concurrent.CountDownLatch(1);
        KeyGenerationJobId id =
                registry.submit(
                        ownerA,
                        () -> {
                            try {
                                block.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return sampleResult();
                        });

        assertThat(registry.poll(id, ownerA))
                .hasValueSatisfying(
                        s -> assertThat(s).isInstanceOf(KeyGenerationJobState.Running.class));

        block.countDown();
    }

    @Test
    void poll_should_returnSucceededAndRemove_when_workFinishedAndOwnerMatches() {
        GenerateValidatorKeysResult result = sampleResult();
        KeyGenerationJobId id = registry.submit(ownerA, () -> result);
        awaitTerminal(id);

        assertThat(registry.poll(id, ownerA))
                .hasValueSatisfying(
                        s -> assertThat(s).isEqualTo(new KeyGenerationJobState.Succeeded(result)));
        assertThat(registry.poll(id, ownerA)).isEmpty();
    }

    @Test
    void poll_should_returnFailedAndRemove_when_workThrows() {
        KeyGenerationJobId id =
                registry.submit(
                        ownerA,
                        () -> {
                            throw new IllegalStateException("boom");
                        });
        awaitTerminal(id);

        assertThat(registry.poll(id, ownerA))
                .hasValueSatisfying(
                        s -> assertThat(s).isEqualTo(new KeyGenerationJobState.Failed("boom")));
        assertThat(registry.poll(id, ownerA)).isEmpty();
    }

    @Test
    void poll_should_returnEmpty_when_ownerDoesNotMatch() {
        KeyGenerationJobId id = registry.submit(ownerA, this::sampleResult);
        awaitTerminal(id);

        assertThat(registry.poll(id, ownerB)).isEmpty();
        // Job is still readable by the rightful owner.
        assertThat(registry.poll(id, ownerA)).isPresent();
    }

    @Test
    void poll_should_returnEmpty_when_idUnknown() {
        assertThat(registry.poll(new KeyGenerationJobId(UUID.randomUUID()), ownerA)).isEmpty();
    }

    @Test
    void purgeExpired_should_removeEntriesOlderThanTtl() {
        KeyGenerationJobId id = registry.submit(ownerA, this::sampleResult);
        awaitTerminal(id);

        clock.advance(Duration.ofMinutes(31));
        registry.purgeExpired();

        // Even the rightful owner cannot read after TTL.
        assertThat(registry.poll(id, ownerA)).isEmpty();
    }

    @Test
    void purgeExpired_should_keepEntriesWithinTtl() {
        KeyGenerationJobId id = registry.submit(ownerA, this::sampleResult);
        awaitTerminal(id);

        clock.advance(Duration.ofMinutes(15));
        registry.purgeExpired();

        assertThat(registry.poll(id, ownerA)).isPresent();
    }

    private void awaitTerminal(KeyGenerationJobId id) {
        // Worker is single-threaded; submitting & waiting on a no-op task guarantees the
        // previously-submitted task has completed.
        try {
            worker.submit(() -> null).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private GenerateValidatorKeysResult sampleResult() {
        ValidatorKey key =
                new ValidatorKey(
                        UUID.randomUUID(),
                        new com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId(
                                UUID.randomUUID()),
                        "0x" + "a".repeat(96),
                        Instant.parse("2026-05-17T10:00:00Z"));
        return new GenerateValidatorKeysResult("word ".repeat(24).trim(), "pw", List.of(key));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
