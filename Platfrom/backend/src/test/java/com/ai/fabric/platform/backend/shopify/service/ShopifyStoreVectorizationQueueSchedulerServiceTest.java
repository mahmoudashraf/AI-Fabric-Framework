package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.config.ShopifyStoreVectorizationTriggerProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationEventEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationAutomationSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreVectorizationQueueSchedulerServiceTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void processAggregateEnqueuesAutoRefreshWithoutLegacyDocumentSyncPreflight() {
        ShopifyStoreConnectionRepository storeRepository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        ShopifyStoreVectorizationEventService eventService = mock(ShopifyStoreVectorizationEventService.class);
        ShopifyStoreVectorizationPolicyService policyService = mock(ShopifyStoreVectorizationPolicyService.class);
        ShopifyStoreVectorizationEligibilityEvaluator eligibilityEvaluator = mock(ShopifyStoreVectorizationEligibilityEvaluator.class);
        ShopifyStoreVectorizationRunEnqueuer runEnqueuer = mock(ShopifyStoreVectorizationRunEnqueuer.class);
        VectorizationService vectorizationService = mock(VectorizationService.class);

        ShopifyStoreConnectionEntity store = new ShopifyStoreConnectionEntity();
        store.setShopDomain("alpha.myshopify.com");
        store.setDeploymentId("dep-123");
        store.setProductsEnabled(true);
        store.setCollectionsEnabled(true);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");

        ShopifyStoreVectorizationEventEntity event = new ShopifyStoreVectorizationEventEntity();
        event.setId("evt-1");
        event.setShopDomain("alpha.myshopify.com");
        event.setDeploymentId("dep-123");
        event.setSourceCategory(ShopifyStoreVectorizationConstants.SOURCE_PRODUCTS);
        event.setEntityType("product");
        event.setOperation(ShopifyStoreVectorizationConstants.OPERATION_UPDATE);
        event.setOccurredAt(Instant.now().minusSeconds(120));

        ShopifyStoreVectorizationPolicyState sourcePolicy = new ShopifyStoreVectorizationPolicyState(
            true,
            true,
            true,
            ShopifyStoreVectorizationConstants.UPDATE_MODE_ANY,
            List.of(),
            0,
            0
        );
        ShopifyStoreVectorizationPolicyService.ResolvedPolicy resolvedPolicy =
            new ShopifyStoreVectorizationPolicyService.ResolvedPolicy(
                null,
                Map.of(ShopifyStoreVectorizationConstants.SOURCE_PRODUCTS, sourcePolicy),
                Map.of(),
                null
            );
        ShopifyStoreVectorizationEligibilityEvaluator.EventEvaluation evaluation =
            ShopifyStoreVectorizationEligibilityEvaluator.EventEvaluation.dispatch(
                ShopifyStoreVectorizationConstants.TRIGGER_REASON_ANY_UPDATE,
                "Any update trigger is enabled."
            );
        VectorizationRunSummary run = new VectorizationRunSummary(
            "vrn-123",
            "REFRESH",
            "QUEUED",
            "QUEUED",
            "PLATFORM_MANAGED_AUTO",
            List.of("product"),
            JSON.objectNode(),
            JSON.objectNode(),
            JSON.objectNode(),
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            null,
            null,
            Instant.now()
        );

        when(eventService.leaseAggregate(eq("products:alpha"), anyString(), any())).thenReturn(List.of(event));
        when(storeRepository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(vectorizationService.getOverviewForTrustedCaller(deployment)).thenReturn(null);
        when(policyService.resolveForEvaluation(eq(store), nullable(com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary.class)))
            .thenReturn(resolvedPolicy);
        when(eligibilityEvaluator.evaluate(eq(store), eq(event), eq(resolvedPolicy), nullable(com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary.class)))
            .thenReturn(evaluation);
        when(eventService.summarizeAutomation("alpha.myshopify.com")).thenReturn(automationSummary());
        when(runEnqueuer.enqueueAutoRefresh(
            eq(store),
            eq("product"),
            eq(List.of(ShopifyStoreVectorizationConstants.SOURCE_PRODUCTS)),
            eq(List.of("evt-1")),
            eq(ShopifyStoreVectorizationConstants.TRIGGER_REASON_ANY_UPDATE)
        )).thenReturn(run);

        ShopifyStoreVectorizationQueueSchedulerService service = new ShopifyStoreVectorizationQueueSchedulerService(
            storeRepository,
            deploymentRepository,
            eventService,
            policyService,
            eligibilityEvaluator,
            runEnqueuer,
            triggerProperties(),
            vectorizationService
        );

        service.processAggregate("products:alpha");

        verify(runEnqueuer).enqueueAutoRefresh(
            store,
            "product",
            List.of(ShopifyStoreVectorizationConstants.SOURCE_PRODUCTS),
            List.of("evt-1"),
            ShopifyStoreVectorizationConstants.TRIGGER_REASON_ANY_UPDATE
        );
        verify(eventService).markDispatched(event, ShopifyStoreVectorizationConstants.TRIGGER_REASON_ANY_UPDATE, "vrn-123", "Any update trigger is enabled.");
    }

    private ShopifyStoreVectorizationAutomationSummary automationSummary() {
        return new ShopifyStoreVectorizationAutomationSummary(
            true,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            null,
            List.of()
        );
    }

    private ShopifyStoreVectorizationTriggerProperties triggerProperties() {
        return new ShopifyStoreVectorizationTriggerProperties(
            true,
            Duration.ofSeconds(20),
            Duration.ofSeconds(10),
            Duration.ofSeconds(45),
            Duration.ofSeconds(30),
            Duration.ofDays(7),
            Duration.ofDays(30),
            5,
            25,
            20
        );
    }
}
