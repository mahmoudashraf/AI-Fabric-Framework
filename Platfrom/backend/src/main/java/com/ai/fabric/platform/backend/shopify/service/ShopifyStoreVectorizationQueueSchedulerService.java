package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.config.ShopifyStoreVectorizationTriggerProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationEventEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationAutomationSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "platform.shopify.vectorization", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ShopifyStoreVectorizationQueueSchedulerService {

    private final ShopifyStoreConnectionRepository storeRepository;
    private final DeploymentRepository deploymentRepository;
    private final ShopifyStoreVectorizationEventService eventService;
    private final ShopifyStoreVectorizationPolicyService policyService;
    private final ShopifyStoreVectorizationEligibilityEvaluator eligibilityEvaluator;
    private final ShopifyBridgeAdminClient bridgeAdminClient;
    private final ShopifyStoreVectorizationRunEnqueuer runEnqueuer;
    private final ShopifyStoreVectorizationTriggerProperties properties;
    private final VectorizationService vectorizationService;

    public ShopifyStoreVectorizationQueueSchedulerService(ShopifyStoreConnectionRepository storeRepository,
                                                          DeploymentRepository deploymentRepository,
                                                          ShopifyStoreVectorizationEventService eventService,
                                                          ShopifyStoreVectorizationPolicyService policyService,
                                                          ShopifyStoreVectorizationEligibilityEvaluator eligibilityEvaluator,
                                                          ShopifyBridgeAdminClient bridgeAdminClient,
                                                          ShopifyStoreVectorizationRunEnqueuer runEnqueuer,
                                                          ShopifyStoreVectorizationTriggerProperties properties,
                                                          VectorizationService vectorizationService) {
        this.storeRepository = storeRepository;
        this.deploymentRepository = deploymentRepository;
        this.eventService = eventService;
        this.policyService = policyService;
        this.eligibilityEvaluator = eligibilityEvaluator;
        this.bridgeAdminClient = bridgeAdminClient;
        this.runEnqueuer = runEnqueuer;
        this.properties = properties;
        this.vectorizationService = vectorizationService;
    }

    @Scheduled(
        initialDelayString = "${platform.shopify.vectorization.initial-delay:PT20S}",
        fixedDelayString = "${platform.shopify.vectorization.fixed-delay:PT10S}"
    )
    public void processQueue() {
        Instant now = Instant.now();
        for (String aggregateKey : eventService.nextAggregateKeys(now, properties.schedulerBatchSize())) {
            processAggregate(aggregateKey);
        }
        eventService.pruneExpired();
    }

    @Transactional
    void processAggregate(String aggregateKey) {
        Instant now = Instant.now();
        String leaseOwner = "shopify-vectorization-" + UUID.randomUUID();
        List<ShopifyStoreVectorizationEventEntity> leasedEvents = eventService.leaseAggregate(
            aggregateKey,
            leaseOwner,
            now.plus(properties.leaseTtl())
        );
        if (leasedEvents.isEmpty()) {
            return;
        }
        ShopifyStoreVectorizationEventEntity seed = leasedEvents.get(0);
        ShopifyStoreConnectionEntity store = storeRepository.findByShopDomainIgnoreCase(seed.getShopDomain()).orElse(null);
        if (store == null || store.getDeploymentId() == null || store.getDeploymentId().isBlank()) {
            leasedEvents.forEach(event -> eventService.markSkipped(event, "Store is not bootstrapped to a deployment for auto indexing."));
            return;
        }
        DeploymentEntity deployment = deploymentRepository.findById(store.getDeploymentId()).orElse(null);
        if (deployment == null) {
            leasedEvents.forEach(event -> eventService.markSkipped(event, "Deployment binding could not be resolved for auto indexing."));
            return;
        }
        VectorizationOverviewSummary overview = vectorizationService.getOverviewForTrustedCaller(deployment);
        ShopifyStoreVectorizationPolicyService.ResolvedPolicy resolvedPolicy = policyService.resolveForEvaluation(store, overview);

        Instant latestOccurredAt = leasedEvents.stream().map(ShopifyStoreVectorizationEventEntity::getOccurredAt).max(Instant::compareTo).orElse(now);
        int debounceSeconds = leasedEvents.stream()
            .map(event -> resolvedPolicy.requireSourcePolicy(event.getSourceCategory()).debounceWindowSeconds())
            .max(Integer::compareTo)
            .orElse(0);
        Instant quietUntil = latestOccurredAt.plusSeconds(debounceSeconds);
        if (quietUntil.isAfter(now)) {
            eventService.requeue(leasedEvents, quietUntil, "Waiting for debounce quiet window before live auto indexing.");
            return;
        }

        List<Decision> decisions = new ArrayList<>();
        for (ShopifyStoreVectorizationEventEntity event : leasedEvents) {
            try {
                ShopifyStoreVectorizationEligibilityEvaluator.EventEvaluation evaluation = eligibilityEvaluator.evaluate(
                    store,
                    event,
                    resolvedPolicy,
                    overview
                );
                decisions.add(new Decision(event, evaluation));
            } catch (RuntimeException ex) {
                eventService.failEvent(
                    event,
                    ShopifyStoreVectorizationConstants.FAILURE_POLICY_EVALUATION,
                    ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "Shopify live update trigger evaluation failed."
                        : ex.getMessage()
                );
            }
        }

        List<Decision> eligible = decisions.stream().filter(decision -> decision.evaluation.dispatch()).toList();
        if (eligible.isEmpty()) {
            decisions.forEach(decision -> eventService.markSkipped(decision.event, decision.evaluation.summary()));
            return;
        }

        int minimumRunIntervalSeconds = eligible.stream()
            .map(decision -> resolvedPolicy.requireSourcePolicy(decision.event.getSourceCategory()).minimumRunIntervalSeconds())
            .max(Integer::compareTo)
            .orElse(0);
        ShopifyStoreVectorizationAutomationSummary automation = eventService.summarizeAutomation(store.getShopDomain());
        if (automation.lastSuccessfulAutoIndexAt() != null
            && automation.lastSuccessfulAutoIndexAt().plusSeconds(minimumRunIntervalSeconds).isAfter(now)) {
            eventService.requeue(
                leasedEvents,
                automation.lastSuccessfulAutoIndexAt().plusSeconds(minimumRunIntervalSeconds),
                "Waiting for the minimum live auto indexing interval before dispatch."
            );
            return;
        }

        try {
            bridgeAdminClient.runSync(store);
        } catch (RuntimeException ex) {
            for (Decision decision : decisions) {
                if (!decision.evaluation.dispatch()) {
                    eventService.markSkipped(decision.event, decision.evaluation.summary());
                    continue;
                }
                eventService.failEvent(
                    decision.event,
                    ShopifyStoreVectorizationConstants.FAILURE_BRIDGE_UNAVAILABLE,
                    ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "Shopify bridge sync could not complete before live auto indexing."
                        : ex.getMessage()
                );
            }
            return;
        }

        Set<String> sourceCategories = new LinkedHashSet<>();
        List<String> eventIds = new ArrayList<>();
        eligible.forEach(decision -> {
            sourceCategories.add(decision.event.getSourceCategory());
            eventIds.add(decision.event.getId());
        });
        Decision dispatchDecision = eligible.get(eligible.size() - 1);

        try {
            VectorizationRunSummary run = runEnqueuer.enqueueAutoRefresh(
                store,
                dispatchDecision.event.getEntityType(),
                List.copyOf(sourceCategories),
                eventIds,
                dispatchDecision.evaluation.triggerReason()
            );

            for (Decision decision : decisions) {
                if (!decision.evaluation.dispatch()) {
                    eventService.markSkipped(decision.event, decision.evaluation.summary());
                    continue;
                }
                if (decision == dispatchDecision) {
                    eventService.markDispatched(decision.event, decision.evaluation.triggerReason(), run.id(), decision.evaluation.summary());
                } else {
                    eventService.markCoalesced(decision.event, run.id(), "Superseded by newer event in the same aggregate dispatch window.");
                }
            }
        } catch (RuntimeException ex) {
            for (Decision decision : decisions) {
                if (!decision.evaluation.dispatch()) {
                    eventService.markSkipped(decision.event, decision.evaluation.summary());
                    continue;
                }
                eventService.failEvent(
                    decision.event,
                    ShopifyStoreVectorizationConstants.FAILURE_RUN_ENQUEUE,
                    ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "Shopify live auto indexing failed before run enqueue."
                        : ex.getMessage()
                );
            }
        }
    }

    private record Decision(
        ShopifyStoreVectorizationEventEntity event,
        ShopifyStoreVectorizationEligibilityEvaluator.EventEvaluation evaluation
    ) {
    }
}
