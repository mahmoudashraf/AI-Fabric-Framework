package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;

/** Connects committed production activation to the existing admission notice/expiry/drain lifecycle. */
@Service
public class AdmissionPromotionRetirementScheduler {
    private final AdmissionStore store;
    private final Clock clock;

    @Autowired
    public AdmissionPromotionRetirementScheduler(AdmissionStore store) {
        this(store, Clock.systemUTC());
    }

    AdmissionPromotionRetirementScheduler(AdmissionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public void scheduleAfterCommit(String deploymentId, String stagingTargetProfileId, String operationKey) {
        String actor = PlatformSecurityContext.actorIdOrSystem();
        Runnable schedule = () -> store.scheduleRetirementAfterPromotion(
            deploymentId, stagingTargetProfileId, operationKey, actor, clock.instant());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            schedule.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                schedule.run();
            }
        });
    }
}
