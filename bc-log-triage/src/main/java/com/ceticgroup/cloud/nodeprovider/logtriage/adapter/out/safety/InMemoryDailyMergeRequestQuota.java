package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.safety;

import com.ceticgroup.cloud.nodeprovider.logtriage.config.SafetyProperties;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.MergeRequestQuotaPort;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Compteur en mémoire borné par jour. Suffisant pour une instance unique (l'app-bootstrap est un
 * monolithe à ce stade) ; à remplacer par un compteur distribué (Redis, table Postgres) le jour où
 * plusieurs instances tournent en parallèle.
 */
public final class InMemoryDailyMergeRequestQuota implements MergeRequestQuotaPort {

    private final SafetyProperties properties;
    private final Clock clock;
    private final AtomicReference<LocalDate> currentDay;
    private final AtomicInteger count;

    public InMemoryDailyMergeRequestQuota(SafetyProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.currentDay = new AtomicReference<>(LocalDate.now(clock));
        this.count = new AtomicInteger(0);
    }

    @Override
    public synchronized boolean tryReserve() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(currentDay.get())) {
            currentDay.set(today);
            count.set(0);
        }
        if (count.get() >= properties.dailyMergeRequestQuota()) {
            return false;
        }
        count.incrementAndGet();
        return true;
    }

    @Override
    public int dailyLimit() {
        return properties.dailyMergeRequestQuota();
    }
}
