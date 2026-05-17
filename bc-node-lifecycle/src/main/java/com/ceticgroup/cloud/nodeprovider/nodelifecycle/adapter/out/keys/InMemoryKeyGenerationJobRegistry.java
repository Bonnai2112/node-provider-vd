package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.keys;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobState.Failed;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobState.Running;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobState.Succeeded;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase.GenerateValidatorKeysResult;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.KeyGenerationJobRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory job registry. Secrets (mnemonic, keystore password) live only here and only until the
 * authenticated owner polls a terminal state — that read removes the entry. A background sweeper
 * also purges entries older than {@code ttl} so abandoned succeeded jobs don't leak secrets.
 */
public final class InMemoryKeyGenerationJobRegistry
        implements KeyGenerationJobRegistry, AutoCloseable {

    private static final Logger log =
            LoggerFactory.getLogger(InMemoryKeyGenerationJobRegistry.class);

    private final ConcurrentHashMap<KeyGenerationJobId, Entry> entries = new ConcurrentHashMap<>();
    private final ExecutorService worker;
    private final ScheduledExecutorService sweeper;
    private final Clock clock;
    private final Duration ttl;

    public InMemoryKeyGenerationJobRegistry() {
        this(
                Executors.newFixedThreadPool(2, namedDaemon("key-gen-worker")),
                Executors.newSingleThreadScheduledExecutor(namedDaemon("key-gen-sweeper")),
                Clock.systemUTC(),
                Duration.ofMinutes(30));
        scheduleSweeper(Duration.ofMinutes(1));
    }

    InMemoryKeyGenerationJobRegistry(
            ExecutorService worker, ScheduledExecutorService sweeper, Clock clock, Duration ttl) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.sweeper = Objects.requireNonNull(sweeper, "sweeper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    private void scheduleSweeper(Duration period) {
        sweeper.scheduleAtFixedRate(
                this::purgeExpired, period.toSeconds(), period.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public KeyGenerationJobId submit(OwnerId owner, Supplier<GenerateValidatorKeysResult> work) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(work, "work");
        KeyGenerationJobId id = new KeyGenerationJobId(UUID.randomUUID());
        entries.put(id, new Entry(owner, new Running(), clock.instant()));
        worker.submit(
                () -> {
                    KeyGenerationJobState resolved;
                    try {
                        resolved = new Succeeded(work.get());
                    } catch (RuntimeException e) {
                        // Log full stack + cause chain so operators can diagnose what the
                        // underlying tool (deposit-cli, ethd…) actually printed; the registry
                        // sits between async worker and user, so swallowing the cause here
                        // means it disappears from logs entirely.
                        log.warn("key generation job {} failed", id.value(), e);
                        resolved = new Failed(messageOf(e));
                    }
                    final KeyGenerationJobState terminal = resolved;
                    entries.computeIfPresent(
                            id, (k, prev) -> new Entry(prev.owner, terminal, prev.createdAt));
                });
        return id;
    }

    @Override
    public Optional<KeyGenerationJobState> poll(KeyGenerationJobId id, OwnerId requester) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requester, "requester");
        AtomicReference<KeyGenerationJobState> taken = new AtomicReference<>();
        entries.compute(
                id,
                (k, entry) -> {
                    if (entry == null || !entry.owner.equals(requester)) {
                        return entry;
                    }
                    taken.set(entry.state);
                    boolean terminal =
                            entry.state instanceof Succeeded || entry.state instanceof Failed;
                    return terminal ? null : entry;
                });
        return Optional.ofNullable(taken.get());
    }

    void purgeExpired() {
        Instant cutoff = clock.instant().minus(ttl);
        for (Map.Entry<KeyGenerationJobId, Entry> e : entries.entrySet()) {
            if (e.getValue().createdAt.isBefore(cutoff)) {
                if (entries.remove(e.getKey(), e.getValue())) {
                    log.info("purged expired key generation job {}", e.getKey().value());
                }
            }
        }
    }

    @Override
    public void close() {
        sweeper.shutdownNow();
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    private static String messageOf(Throwable t) {
        // Walk the cause chain so the Failed state exposes the actual underlying error to the
        // frontend (e.g. the deposit-cli stdout), not just the outermost wrapper message.
        // Defensive bound against self-referential causes.
        StringBuilder sb = new StringBuilder();
        Throwable cursor = t;
        int safety = 8;
        while (cursor != null && safety-- > 0) {
            String m = cursor.getMessage();
            String label = m == null || m.isBlank() ? cursor.getClass().getSimpleName() : m;
            if (!sb.isEmpty()) {
                sb.append(": ");
            }
            sb.append(label);
            Throwable next = cursor.getCause();
            cursor = next == cursor ? null : next;
        }
        return sb.toString();
    }

    private static java.util.concurrent.ThreadFactory namedDaemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    private record Entry(OwnerId owner, KeyGenerationJobState state, Instant createdAt) {}
}
