package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;

class StagingExpiryContractTest extends AdmissionFixture {
    @Test
    void expiryRequiresBothDeliveredNoticesAndFullNoticeWindowThenPersistsClaim() {
        Reservation r = one(); Instant noticeTime = NOW.plusSeconds(900);
        assertThatThrownBy(() -> store.claimExpiry(r.fence(), "operator", NOW.plusSeconds(1001))).hasMessageContaining("EXPIRY_NOT_ELIGIBLE");
        List<NoticeWork> work = store.noticeWork(r.fence(), "operator", noticeTime);
        assertThat(work).hasSize(2);
        Reservation partial = store.noticeDelivered(work.get(0), receipt(work.get(0), noticeTime), "operator", noticeTime);
        assertThat(partial.expiryState()).isEqualTo(ExpiryState.NOTICE_PENDING);
        assertThatThrownBy(() -> store.claimExpiry(partial.fence(), "operator", NOW.plusSeconds(1001))).hasMessageContaining("EXPIRY_NOT_ELIGIBLE");
        Reservation notified = store.noticeDelivered(work.get(1), receipt(work.get(1), noticeTime.plusSeconds(1)), "operator", noticeTime.plusSeconds(1));
        assertThat(notified.expiryState()).isEqualTo(ExpiryState.NOTIFIED);
        assertThat(store.noticeWork(notified.fence(), "operator", NOW.plusSeconds(1000))).isEmpty();
        assertThatThrownBy(() -> store.claimExpiry(notified.fence(), "operator", NOW.plusSeconds(1000))).hasMessageContaining("NOTICE_WINDOW_NOT_ELAPSED");
        ExpiryWork claim = store.claimExpiry(notified.fence(), "operator", NOW.plusSeconds(1001));
        var restarted = new AdmissionStore(jdbc, json, new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource), java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        assertThat(restarted.expiryWork(restarted.require(r.id()))).isEqualTo(claim);
        assertThat(restarted.require(r.id()).consumesCapacity()).isTrue();
        assertThatThrownBy(() -> store.claimExpiry(claim.fence(), "operator", NOW.plusSeconds(1002))).hasMessageContaining("EXPIRY_NOT_ELIGIBLE");
        Reservation handoff = store.handoff(claim, "r3-operation", "r3-plan-evidence", "operator", NOW.plusSeconds(1002));
        assertThat(handoff.expiryState()).isEqualTo(ExpiryState.HANDED_OFF);
        assertThat(handoff.retirementOperationId()).isEqualTo("r3-operation");
        assertThatThrownBy(() -> store.reconcile(handoff.fence(), readback(handoff, NOW.plusSeconds(1003), true, 0L, 0L), true, "operator", NOW.plusSeconds(1003)))
            .hasMessageContaining("RECOVERY_WINDOW_NOT_ELAPSED");
        assertThatThrownBy(() -> store.reconcile(handoff.fence(), readback(handoff, NOW.plusSeconds(1022), true, 2L, 0L), true, "operator", NOW.plusSeconds(1022)))
            .hasMessageContaining("RECLAMATION_UNPROVEN");
        assertThat(store.reconcile(handoff.fence(), readback(handoff, NOW.plusSeconds(1022), true, 0L, 0L), true, "operator", NOW.plusSeconds(1022)).state())
            .isEqualTo(State.RELEASED);
    }
    @Test
    void noticeFailureIsVisibleBoundedAndNeverReleasesCapacity() {
        Reservation r = one(); List<NoticeWork> first = store.noticeWork(r.fence(), "operator", NOW.plusSeconds(900));
        r = store.noticeFailed(first.get(0).fence(), "EMAIL_UNAVAILABLE", "operator", NOW.plusSeconds(901));
        assertThat(r.expiryState()).isEqualTo(ExpiryState.NOTICE_FAILED);
        assertThat(store.noticeWork(r.fence(), "operator", NOW.plusSeconds(910))).isEmpty();
        List<NoticeWork> retry = store.noticeWork(r.fence(), "operator", NOW.plusSeconds(920));
        assertThat(retry.get(0).deliveryKey()).isEqualTo(first.get(0).deliveryKey());
        r = store.noticeFailed(retry.get(0).fence(), "EMAIL_UNAVAILABLE", "operator", NOW.plusSeconds(921));
        assertThat(r.expiryState()).isEqualTo(ExpiryState.BLOCKED); assertThat(r.consumesCapacity()).isTrue();
        assertThat(r.failureCode()).isEqualTo("EMAIL_UNAVAILABLE");
    }
    @Test
    void silentDeliveryTimeoutEventuallyDeadLettersWithoutDeletion() {
        Reservation r = one(); store.noticeWork(r.fence(), "operator", NOW.plusSeconds(900));
        r = store.require(r.id()); store.noticeWork(r.fence(), "operator", NOW.plusSeconds(920));
        r = store.require(r.id()); assertThat(store.noticeWork(r.fence(), "operator", NOW.plusSeconds(940))).isEmpty();
        r = store.require(r.id()); assertThat(r.expiryState()).isEqualTo(ExpiryState.BLOCKED);
        assertThat(r.failureCode()).isEqualTo("NOTICE_DELIVERY_DEAD_LETTER"); assertThat(r.consumesCapacity()).isTrue();
    }
    @Test
    void explicitExtensionInvalidatesDelayedNoticesAndOldFences() {
        Reservation r = one(); var work = store.noticeWork(r.fence(), "operator", NOW.plusSeconds(900));
        Reservation current = store.require(r.id());
        Reservation extended = store.extend(current.fence(), false, grant(1), "owner-extension-choice", "owner", NOW.plusSeconds(901));
        assertThat(extended.expiresAt()).isEqualTo(NOW.plusSeconds(1100));
        assertThat(extended.extensions()).isEqualTo(1);
        assertThatThrownBy(() -> store.noticeDelivered(work.get(0), receipt(work.get(0), NOW.plusSeconds(902)), "operator", NOW.plusSeconds(902)))
            .hasMessageContaining("STALE_NOTICE_CYCLE");
        assertThatThrownBy(() -> store.claimExpiry(current.fence(), "operator", NOW.plusSeconds(1001))).hasMessageContaining("STALE_RESERVATION_FENCE");
        extended = store.extend(extended.fence(), false, grant(1), "extension-two", "owner", NOW.plusSeconds(903));
        Reservation max = extended;
        assertThatThrownBy(() -> store.extend(max.fence(), false, grant(1), "extension-three", "owner", NOW.plusSeconds(904))).hasMessageContaining("STAGING_EXTENSION_FORBIDDEN");
    }
    @Test
    void keepStagingRequiresEntitlementAndHasVisibleBoundedExpiry() {
        Reservation r = one();
        CustomerGrant noKeep = new CustomerGrant("customer", vector(1), grant(1).targetLimits(), grant(1).validUntil(), "verified-owner", "payer", null);
        store.approveGrant(noKeep, "operator", NOW);
        assertThatThrownBy(() -> store.extend(r.fence(), true, noKeep, "keep", "owner", NOW.plusSeconds(1))).hasMessageContaining("KEEP_STAGING_ENTITLEMENT_REQUIRED");
        store.approveGrant(grant(1), "operator", NOW);
        Reservation kept = store.extend(r.fence(), true, grant(1), "keep-choice", "owner", NOW.plusSeconds(2));
        assertThat(kept.keepUntil()).isEqualTo(NOW.plusSeconds(1200));
        assertThat(kept.keepEntitlementReference()).isEqualTo("keep-entitlement"); assertThat(kept.consumesCapacity()).isTrue();
    }
    @Test
    void onlyAcceptedWorkActivityExtendsExpiryAndCannotUndoClaim() {
        Reservation r = one();
        Reservation active = store.activity(r.fence(), Activity.ACCEPTED_SESSION, "accepted-session", "operator", NOW.plusSeconds(1));
        assertThat(active.lastActivityAt()).isEqualTo(NOW.plusSeconds(1)); assertThat(active.expiresAt()).isEqualTo(NOW.plusSeconds(1001));
        assertThatThrownBy(() -> store.activity(active.fence(), null, "health-probe", "operator", NOW.plusSeconds(2))).hasMessageContaining("ACTIVITY_REQUIRES_CURRENT_STAGING");
    }
    @Test
    void productionActivationSchedulesOneFreshBoundedNoticeCycleAndReplayIsStable() {
        Reservation r = one();

        Reservation scheduled = store.scheduleRetirementAfterPromotion(
            r.deploymentId(), r.targetProfileId(), "activate-1", "owner", NOW.plusSeconds(10));

        assertThat(scheduled.expiresAt()).isEqualTo(NOW.plusSeconds(110));
        assertThat(scheduled.expiryState()).isEqualTo(ExpiryState.CURRENT);
        assertThat(store.noticeWork(scheduled.fence(), "worker", NOW.plusSeconds(10))).hasSize(2);
        Reservation replay = store.scheduleRetirementAfterPromotion(
            r.deploymentId(), r.targetProfileId(), "activate-1", "owner", NOW.plusSeconds(20));
        assertThat(replay.revision()).isEqualTo(scheduled.revision() + 1);
        assertThat(replay.expiresAt()).isEqualTo(scheduled.expiresAt());
        assertThat(replay.noticeCycle()).isEqualTo(scheduled.noticeCycle());
    }
    @Test
    void productionActivationCannotClaimRetirementWasScheduledWithoutAStagingReservation() {
        assertThatThrownBy(() -> store.scheduleRetirementAfterPromotion(
            "missing-deployment", TARGET, "activate-1", "owner", NOW))
            .hasMessageContaining("STAGING_RESERVATION_REQUIRED");
    }
    private NoticeDelivery receipt(NoticeWork work, Instant at) { return new NoticeDelivery(work.deliveryKey(), "durable-transport-receipt", at); }
}
