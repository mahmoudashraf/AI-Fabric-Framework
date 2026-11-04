package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseVerificationCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DeskReleaseVerificationListener {
    private static final Logger log = LoggerFactory.getLogger(DeskReleaseVerificationListener.class);
    private final DeskBindingReconciliationService reconciliation;

    public DeskReleaseVerificationListener(DeskBindingReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void completed(DeploymentReleaseVerificationCompleted event) {
        try {
            reconciliation.reconcile(event.releaseId(), event.verificationRunId());
        } catch (RuntimeException error) {
            // The release is already durable. A Desk projection failure must not rewrite its result.
            log.error("Desk status reconciliation failed: releaseId={}, verificationRunId={}. Owner status refresh can retry.",
                event.releaseId(), event.verificationRunId(), error);
        }
    }
}
