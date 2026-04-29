package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationEventEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationLedgerEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreVectorizationLedgerRepository;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ShopifyStoreVectorizationEligibilityEvaluator {

    private final ShopifyBridgeAdminClient bridgeAdminClient;
    private final ShopifyStoreVectorizationLedgerRepository ledgerRepository;
    private final ShopifyStoreVectorizationLedgerService ledgerService;
    private final ShopifyStoreVectorizationFingerprintService fingerprintService;

    public ShopifyStoreVectorizationEligibilityEvaluator(ShopifyBridgeAdminClient bridgeAdminClient,
                                                         ShopifyStoreVectorizationLedgerRepository ledgerRepository,
                                                         ShopifyStoreVectorizationLedgerService ledgerService,
                                                         ShopifyStoreVectorizationFingerprintService fingerprintService) {
        this.bridgeAdminClient = bridgeAdminClient;
        this.ledgerRepository = ledgerRepository;
        this.ledgerService = ledgerService;
        this.fingerprintService = fingerprintService;
    }

    public EventEvaluation evaluate(ShopifyStoreConnectionEntity store,
                                    ShopifyStoreVectorizationEventEntity event,
                                    ShopifyStoreVectorizationPolicyService.ResolvedPolicy resolvedPolicy,
                                    VectorizationOverviewSummary overview) {
        String sourceCategory = event.getSourceCategory();
        if (!ShopifyStoreVectorizationConstants.sourceEnabled(store, sourceCategory)) {
            return EventEvaluation.skip("Source category is disabled for this store.");
        }
        ShopifyStoreVectorizationPolicyState sourcePolicy = resolvedPolicy.requireSourcePolicy(sourceCategory);
        if (!sourcePolicy.autoIndexingEnabled()) {
            return EventEvaluation.skip("Live auto indexing is disabled for " + sourceCategory + ".");
        }

        return switch (event.getOperation()) {
            case ShopifyStoreVectorizationConstants.OPERATION_CREATE -> sourcePolicy.createTriggerEnabled()
                ? EventEvaluation.dispatch(ShopifyStoreVectorizationConstants.TRIGGER_REASON_CREATE, "Create trigger enabled.")
                : EventEvaluation.skip("Create trigger is disabled for " + sourceCategory + ".");
            case ShopifyStoreVectorizationConstants.OPERATION_DELETE -> sourcePolicy.deleteTriggerEnabled()
                ? EventEvaluation.dispatch(ShopifyStoreVectorizationConstants.TRIGGER_REASON_DELETE, "Delete trigger enabled.")
                : EventEvaluation.skip("Delete trigger is disabled for " + sourceCategory + ".");
            default -> evaluateUpdate(store, event, sourcePolicy, overview);
        };
    }

    private EventEvaluation evaluateUpdate(ShopifyStoreConnectionEntity store,
                                           ShopifyStoreVectorizationEventEntity event,
                                           ShopifyStoreVectorizationPolicyState sourcePolicy,
                                           VectorizationOverviewSummary overview) {
        return switch (sourcePolicy.updateTriggerMode()) {
            case ShopifyStoreVectorizationConstants.UPDATE_MODE_NONE -> EventEvaluation.skip("Update triggers are disabled for " + event.getSourceCategory() + ".");
            case ShopifyStoreVectorizationConstants.UPDATE_MODE_ANY -> EventEvaluation.dispatch(
                ShopifyStoreVectorizationConstants.TRIGGER_REASON_ANY_UPDATE,
                "Any update trigger is enabled."
            );
            case ShopifyStoreVectorizationConstants.UPDATE_MODE_INDEXED_ONLY -> evaluateIndexedFieldsChanged(
                store,
                event,
                overview,
                List.of(),
                ShopifyStoreVectorizationConstants.TRIGGER_REASON_INDEXED_FIELDS_CHANGED
            );
            case ShopifyStoreVectorizationConstants.UPDATE_MODE_SELECTED -> evaluateIndexedFieldsChanged(
                store,
                event,
                overview,
                sourcePolicy.selectedIndexedFields(),
                ShopifyStoreVectorizationConstants.TRIGGER_REASON_SELECTED_FIELDS_CHANGED
            );
            default -> EventEvaluation.skip("Unsupported update trigger mode: " + sourcePolicy.updateTriggerMode());
        };
    }

    private EventEvaluation evaluateIndexedFieldsChanged(ShopifyStoreConnectionEntity store,
                                                         ShopifyStoreVectorizationEventEntity event,
                                                         VectorizationOverviewSummary overview,
                                                         List<String> selectedFieldKeys,
                                                         String triggerReason) {
        if (ShopifyStoreVectorizationConstants.SOURCE_POLICIES.equals(event.getSourceCategory())) {
            return evaluatePolicyAggregate(store, event, overview, selectedFieldKeys, triggerReason);
        }
        if (event.getSourceObjectId() == null || event.getSourceObjectId().isBlank()) {
            return EventEvaluation.dispatch(triggerReason, "Source object id was not available; dispatching conservative refresh.");
        }
        ShopifyStoreVectorizationSourceRecordSnapshot record = bridgeAdminClient.fetchRecord(
            store,
            event.getSourceCategory(),
            event.getSourceObjectId()
        );
        ShopifyStoreVectorizationFingerprintService.FingerprintResult current = fingerprintService.fingerprintRecord(
            event.getSourceCategory(),
            record.values(),
            overview
        );
        ShopifyStoreVectorizationLedgerEntity ledger = ledgerRepository.findByDeploymentIdAndEntityTypeAndSourceObjectId(
            event.getDeploymentId(),
            event.getEntityType(),
            event.getSourceObjectId()
        ).orElse(null);
        if (ledger == null || ledger.getDeletedAt() != null) {
            return EventEvaluation.dispatch(triggerReason, "Source object has not been indexed yet.");
        }
        if (selectedFieldKeys == null || selectedFieldKeys.isEmpty()) {
            if (!current.fullFingerprint().equals(ledger.getIndexedOutputFingerprint())) {
                return EventEvaluation.dispatch(triggerReason, "Indexed fields changed.");
            }
            return EventEvaluation.skip("No indexed field change detected.");
        }
        boolean changed = fingerprintService.selectedFieldsChanged(
            ledgerService.readFieldFingerprints(ledger),
            current.fieldFingerprints(),
            selectedFieldKeys
        );
        return changed
            ? EventEvaluation.dispatch(triggerReason, "Selected indexed fields changed.")
            : EventEvaluation.skip("Selected indexed fields did not change.");
    }

    private EventEvaluation evaluatePolicyAggregate(ShopifyStoreConnectionEntity store,
                                                    ShopifyStoreVectorizationEventEntity event,
                                                    VectorizationOverviewSummary overview,
                                                    List<String> selectedFieldKeys,
                                                    String triggerReason) {
        List<Map<String, Object>> records = new ArrayList<>();
        String cursor = null;
        do {
            ShopifyStoreVectorizationSourcePageSnapshot page = bridgeAdminClient.fetchPage(store, event.getEntityType(), cursor, 250);
            page.items().stream()
                .filter(item -> ShopifyStoreVectorizationConstants.SOURCE_POLICIES.equals(item.sourceCategory()))
                .map(ShopifyStoreVectorizationSourceRecordSnapshot::values)
                .forEach(records::add);
            cursor = page.hasMore() ? page.nextCursor() : null;
        } while (cursor != null);

        ShopifyStoreVectorizationFingerprintService.FingerprintResult current = fingerprintService.fingerprintRecordSet(
            event.getSourceCategory(),
            records,
            overview
        );
        Map<String, String> previous = ledgerService.aggregateStoredFingerprints(
            event.getDeploymentId(),
            event.getEntityType(),
            event.getSourceCategory()
        );
        if (selectedFieldKeys == null || selectedFieldKeys.isEmpty()) {
            boolean changed = !previous.equals(current.fieldFingerprints());
            return changed
                ? EventEvaluation.dispatch(triggerReason, "Policy indexed fields changed.")
                : EventEvaluation.skip("No policy indexed field change detected.");
        }
        boolean changed = fingerprintService.selectedFieldsChanged(previous, current.fieldFingerprints(), selectedFieldKeys);
        return changed
            ? EventEvaluation.dispatch(triggerReason, "Selected policy indexed fields changed.")
            : EventEvaluation.skip("Selected policy indexed fields did not change.");
    }

    public record EventEvaluation(
        boolean dispatch,
        String triggerReason,
        String summary
    ) {
        static EventEvaluation dispatch(String triggerReason, String summary) {
            return new EventEvaluation(true, triggerReason, summary);
        }

        static EventEvaluation skip(String summary) {
            return new EventEvaluation(false, null, summary);
        }
    }
}
