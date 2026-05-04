package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.scheduler;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ReconcileNodeStatusUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class NodeStatusReconcilerScheduler {

    private final ReconcileNodeStatusUseCase useCase;

    NodeStatusReconcilerScheduler(ReconcileNodeStatusUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${app.platform.reconciler.fixed-delay-ms:30000}")
    @SchedulerLock(name = "node-status-reconciler", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void reconcile() {
        useCase.reconcileAll();
    }
}
