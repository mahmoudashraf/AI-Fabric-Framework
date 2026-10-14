package com.ai.fabric.platform.backend.deployment.retirement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
@ConditionalOnProperty(name = "platform.target-retirement.worker-enabled", havingValue = "true")
public class TargetRetirementScheduler {
    private static final Logger log = LoggerFactory.getLogger(TargetRetirementScheduler.class);
    private final TargetRetirementOperationRepository operations;
    private final TargetRetirementWorker worker;
    public TargetRetirementScheduler(TargetRetirementOperationRepository operations, TargetRetirementWorker worker) {
        this.operations = operations;
        this.worker = worker;
    }
    @Scheduled(fixedDelayString = "${platform.target-retirement.poll-delay-ms:15000}")
    public void reconcile() {
        for (var operation : operations.findDue(
            List.of(State.QUEUED, State.WAITING, State.RUNNING, State.RETAINED, State.CANCEL_REQUESTED), Instant.now(), PageRequest.of(0, 32))) {
            try { worker.runNext(operation.getId()); }
            catch (RuntimeException ex) { log.warn("Retirement reconciliation could not persist progress: {}", operation.getId()); }
        }
    }
}
