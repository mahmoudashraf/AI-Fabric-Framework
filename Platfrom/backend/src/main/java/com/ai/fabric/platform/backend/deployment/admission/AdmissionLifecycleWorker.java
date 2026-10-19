package com.ai.fabric.platform.backend.deployment.admission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Bounded durable orchestrator. It coordinates stores but never performs provider deletion itself. */
@Service
@ConditionalOnProperty(name = "platform.deployment.admission.lifecycle.worker-enabled", havingValue = "true")
final class AdmissionLifecycleWorker {
    @Autowired
    private AdmissionActivityReconciler activity;
    private static final Logger log = LoggerFactory.getLogger(AdmissionLifecycleWorker.class);
    private static final String ACTOR = "system:admission-lifecycle";
    private final AdmissionStore store;
    private final ProductionAdmissionNoticeDeliverySource delivery;
    private final AdmissionNoticeDeliverySource deliveryReadback;
    private final AdmissionResourceReadbackSource resourceReadback;
    private final AdmissionRetirementHandoffService retirement;
    private final AdmissionLifecycleProperties properties;
    private final Clock clock;

    @Autowired
    AdmissionLifecycleWorker(AdmissionStore store,
                             ProductionAdmissionNoticeDeliverySource delivery,
                             AdmissionNoticeDeliverySource deliveryReadback,
                             AdmissionResourceReadbackSource resourceReadback,
                             AdmissionRetirementHandoffService retirement,
                             AdmissionLifecycleProperties properties) {
        this(store, delivery, deliveryReadback, resourceReadback, retirement, properties, Clock.systemUTC());
    }

    AdmissionLifecycleWorker(AdmissionStore store,
                             ProductionAdmissionNoticeDeliverySource delivery,
                             AdmissionNoticeDeliverySource deliveryReadback,
                             AdmissionResourceReadbackSource resourceReadback,
                             AdmissionRetirementHandoffService retirement,
                             AdmissionLifecycleProperties properties,
                             Clock clock) {
        this.store = store;
        this.delivery = delivery;
        this.deliveryReadback = deliveryReadback;
        this.resourceReadback = resourceReadback;
        this.retirement = retirement;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${platform.deployment.admission.lifecycle.initial-delay:PT30S}",
        fixedDelayString = "${platform.deployment.admission.lifecycle.poll-delay:PT30S}")
    void reconcile() {
        store.lifecycleBatch(properties.batchSize()).forEach(this::reconcileSafely);
    }

    void reconcileSafely(Reservation reservation) {
        try {
            reconcileOne(reservation);
        } catch (AdmissionFailure ex) {
            log.warn("Admission lifecycle step blocked for reservation {}: {}", reservation.id(), code(ex));
        } catch (RuntimeException ex) {
            log.warn("Admission lifecycle step failed closed for reservation {}", reservation.id());
        }
    }

    void reconcileOne(Reservation input) {
        Reservation reservation = store.require(input.id());
        if (!reservation.occupiesActiveSlot() || reservation.intent() != Intent.CUSTOMER_STAGING
            || reservation.expiryState() == ExpiryState.BLOCKED) return;
        if (activity != null && reservation.expiryState() != ExpiryState.CLAIMED && reservation.expiryState() != ExpiryState.HANDED_OFF) {
            reservation = activity.reconcile(reservation);
        }
        Instant now = clock.instant();
        if (noticeEligible(reservation, now)) reservation = deliverNotices(reservation, now);
        reservation = store.require(reservation.id());
        if (reservation.expiryState() == ExpiryState.NOTIFIED && !now.isBefore(reservation.expiresAt())) {
            store.claimExpiry(reservation.fence(), ACTOR, now);
            reservation = store.require(reservation.id());
        }
        if (reservation.expiryState() == ExpiryState.CLAIMED) {
            retirement.handoff(reservation);
            reservation = store.require(reservation.id());
        }
        if (reservation.expiryState() == ExpiryState.HANDED_OFF) {
            var retirementState = retirement.reconcile(reservation);
            if (retirementState.state() == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.COMPLETE
                || retirementState.state() == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.RETIRED_WITH_RETAINED_DATA) {
                FleetPolicy policy = store.reviewed("POLICY", reservation.policyId(), FleetPolicy.class);
                ResourceReadback evidence = resourceReadback.read(new ReadbackContext(reservation, policy, reservation.workerToken()));
                if (retirementState.state() == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.RETIRED_WITH_RETAINED_DATA)
                    store.settleRetained(reservation.fence(), evidence, ACTOR, now);
                else store.reconcile(reservation.fence(), evidence, true, ACTOR, now);
            }
        }
    }

    private Reservation deliverNotices(Reservation reservation, Instant now) {
        for (NoticeWork work : store.noticeWork(reservation.fence(), ACTOR, now)) {
            try {
                delivery.deliver(store.require(reservation.id()), work);
                NoticeDelivery receipt = deliveryReadback.read(work);
                reservation = store.noticeDelivered(work, receipt, ACTOR, clock.instant());
            } catch (AdmissionFailure ex) {
                Reservation current = store.require(reservation.id());
                if (current.expiryState() == ExpiryState.NOTICE_PENDING) {
                    if ("NOTICE_DELIVERY_OUTCOME_UNKNOWN".equals(code(ex))) {
                        store.noticeOutcomeUnknown(current.fence(), ACTOR, clock.instant());
                    } else {
                        store.noticeFailed(current.fence(), boundedCode(ex), ACTOR, clock.instant());
                    }
                }
                throw ex;
            }
        }
        return reservation;
    }

    private boolean noticeEligible(Reservation reservation, Instant now) {
        if (reservation.expiryState() == ExpiryState.CLAIMED
            || reservation.expiryState() == ExpiryState.HANDED_OFF
            || reservation.expiryState() == ExpiryState.RELEASED
            || reservation.expiryState() == ExpiryState.NOTIFIED) return false;
        FleetPolicy policy = store.reviewed("POLICY", reservation.policyId(), FleetPolicy.class);
        return !now.isBefore(reservation.expiresAt().minusSeconds(policy.lifecycle().noticeLeadSeconds()));
    }

    private String boundedCode(AdmissionFailure failure) {
        String code = code(failure);
        return code.length() > 64 ? "NOTICE_DELIVERY_FAILED" : code;
    }

    private String code(AdmissionFailure failure) {
        return failure.getReason() == null || failure.getReason().isBlank()
            ? "ADMISSION_LIFECYCLE_BLOCKED"
            : failure.getReason();
    }
}
