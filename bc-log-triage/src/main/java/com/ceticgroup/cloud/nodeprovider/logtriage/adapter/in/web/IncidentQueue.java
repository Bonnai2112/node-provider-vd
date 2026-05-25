package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.in.web;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * File en mémoire entre le webhook et le worker. Bornée pour appliquer un back-pressure naturel en
 * cas de pic d'alertes — au-delà, le webhook répond 503 plutôt que d'OOMer.
 */
public final class IncidentQueue {

    private final LinkedBlockingQueue<Incident> queue;

    public IncidentQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public boolean offer(Incident incident) {
        return queue.offer(incident);
    }

    public Incident poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int size() {
        return queue.size();
    }
}
