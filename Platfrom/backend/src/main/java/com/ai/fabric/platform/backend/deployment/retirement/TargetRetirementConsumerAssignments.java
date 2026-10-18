package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerBindingHistoryEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerBindingHistoryRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

/** Exact, receipt-backed public assignment fencing used by the authority retirement step. */
@Service
class TargetRetirementConsumerAssignments {
    private final PlatformConsumerRepository consumers;
    private final PlatformConsumerBindingHistoryRepository history;
    private final PlatformCustomerConsumerService consumerService;

    TargetRetirementConsumerAssignments(PlatformConsumerRepository consumers,
                                        PlatformConsumerBindingHistoryRepository history,
                                        PlatformCustomerConsumerService consumerService) {
        this.consumers = consumers;
        this.history = history;
        this.consumerService = consumerService;
    }

    Inspection inspect(Context context) {
        List<String> blockers = new ArrayList<>();
        boolean pending = false;
        Set<String> releaseIds = releaseIds(context);
        for (ConsumerExpectation expectation : context.snapshot().consumers()) {
            PlatformConsumerEntity consumer = consumers.findById(expectation.consumerEntityId()).orElse(null);
            if (consumer == null) {
                blockers.add("CONSUMER_MISSING");
                continue;
            }
            if (!context.snapshot().customerId().equals(consumer.getCustomerId())) {
                blockers.add("CONSUMER_OWNERSHIP_CHANGED");
                continue;
            }
            if (referencesTarget(context, releaseIds, consumer)) {
                if (consumer.getBindingRevision() != expectation.expectedBindingRevision()) {
                    blockers.add("CONSUMER_REVISION_CHANGED");
                } else {
                    pending = true;
                }
                continue;
            }
            if (consumer.getBindingRevision() == expectation.expectedBindingRevision()) continue;
            if (!provedDetach(context, expectation, consumer)) blockers.add("CONSUMER_REVISION_CHANGED");
        }
        List<String> distinct = blockers.stream().distinct().toList();
        return new Inspection(distinct.isEmpty() && !pending, distinct,
            "target-retirement:assignment:" + context.operationId());
    }

    void detach(Context context) {
        Set<String> releaseIds = releaseIds(context);
        for (ConsumerExpectation expectation : context.snapshot().consumers()) {
            PlatformConsumerEntity consumer = consumers.findById(expectation.consumerEntityId())
                .orElseThrow(() -> new TargetRetirementFailure("CONSUMER_MISSING"));
            if (!referencesTarget(context, releaseIds, consumer)) continue;
            try {
                consumerService.detachTargetForRetirement(
                    context.snapshot().customerId(), consumer.getId(), context.snapshot().deploymentId(),
                    context.snapshot().targetProfileId(), releaseIds, expectation.expectedBindingRevision(),
                    context.operationKey()
                );
            } catch (RuntimeException error) {
                throw new TargetRetirementFailure("CONSUMER_ASSIGNMENT_DETACH_FAILED");
            }
        }
        Inspection inspection = inspect(context);
        if (!inspection.blockers().isEmpty()) throw new TargetRetirementFailure(inspection.blockers().getFirst());
        if (!inspection.complete()) throw new TargetRetirementFailure("TARGET_STILL_ASSIGNED");
    }

    private Set<String> releaseIds(Context context) {
        return context.snapshot().releases().stream().map(ReleaseGeneration::releaseId)
            .collect(java.util.stream.Collectors.toSet());
    }

    private boolean referencesTarget(Context context, Set<String> releaseIds, PlatformConsumerEntity consumer) {
        boolean target = context.snapshot().deploymentId().equals(consumer.getBoundDeploymentId())
            && context.snapshot().targetProfileId().equals(consumer.getBoundTargetProfileId());
        return target || releaseIds.contains(consumer.getBoundReleaseId());
    }

    private boolean provedDetach(Context context, ConsumerExpectation expectation, PlatformConsumerEntity consumer) {
        if (consumer.getBoundDeploymentId() != null || consumer.getBoundReleaseId() != null
            || consumer.getBoundTargetProfileId() != null
            || consumer.getBindingRevision() != expectation.expectedBindingRevision() + 1) return false;
        PlatformConsumerBindingHistoryEntity receipt = history
            .findByConsumerEntityIdAndOperationKey(consumer.getId(), context.operationKey()).orElse(null);
        return receipt != null
            && context.snapshot().deploymentId().equals(receipt.getFromDeploymentId())
            && context.snapshot().targetProfileId().equals(receipt.getFromTargetProfileId())
            && receipt.getToDeploymentId() == null && receipt.getToReleaseId() == null
            && receipt.getToTargetProfileId() == null
            && Long.valueOf(expectation.expectedBindingRevision()).equals(receipt.getFromBindingRevision())
            && Long.valueOf(expectation.expectedBindingRevision() + 1).equals(receipt.getToBindingRevision());
    }

    record Inspection(boolean complete, List<String> blockers, String reference) { }
}
