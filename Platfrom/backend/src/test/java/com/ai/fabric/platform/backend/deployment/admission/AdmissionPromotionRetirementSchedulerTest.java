package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AdmissionPromotionRetirementSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private final AdmissionStore store = mock(AdmissionStore.class);
    private final AdmissionPromotionRetirementScheduler scheduler = new AdmissionPromotionRetirementScheduler(
        store, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void schedulingRunsOnlyAfterTheActivationTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        scheduler.scheduleAfterCommit("deployment", "staging", "activate-1");

        verifyNoInteractions(store);
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
        verify(store).scheduleRetirementAfterPromotion(
            "deployment", "staging", "activate-1", "system", NOW);
    }

    @Test
    void schedulingFailureIsVisibleSoActivationReplayCanRetryIt() {
        doThrow(new AdmissionFailure(org.springframework.http.HttpStatus.CONFLICT, "NOTICE_TRANSPORT_UNAVAILABLE"))
            .when(store).scheduleRetirementAfterPromotion("deployment", "staging", "activate-1", "system", NOW);

        assertThatThrownBy(() -> scheduler.scheduleAfterCommit("deployment", "staging", "activate-1"))
            .hasMessageContaining("NOTICE_TRANSPORT_UNAVAILABLE");
    }
}
