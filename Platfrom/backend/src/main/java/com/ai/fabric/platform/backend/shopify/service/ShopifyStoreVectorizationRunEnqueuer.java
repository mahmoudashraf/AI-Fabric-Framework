package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopifyStoreVectorizationRunEnqueuer {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final VectorizationService vectorizationService;

    public ShopifyStoreVectorizationRunEnqueuer(VectorizationService vectorizationService) {
        this.vectorizationService = vectorizationService;
    }

    public VectorizationRunSummary enqueueAutoRefresh(ShopifyStoreConnectionEntity store,
                                                      String entityType,
                                                      List<String> sourceCategories,
                                                      List<String> eventIds,
                                                      String triggerReason) {
        ObjectNode overrides = JSON.objectNode();
        overrides.put("triggerMode", "SHOPIFY_AUTO_EVENT");
        overrides.put("triggerReason", triggerReason);
        overrides.put("shopDomain", store.getShopDomain());
        overrides.put("eventCount", eventIds == null ? 0 : eventIds.size());
        overrides.set("sourceCategories", jsonArray(sourceCategories));
        overrides.set("eventIds", jsonArray(eventIds));
        return vectorizationService.createRunForTrustedCaller(
            store.getDeploymentId(),
            new CreateVectorizationRunRequest(
                "REFRESH",
                List.of(entityType),
                "Shopify live auto indexing requested a scoped refresh for " + entityType + ".",
                overrides
            ),
            "system:shopify-vectorization-auto"
        );
    }

    private com.fasterxml.jackson.databind.node.ArrayNode jsonArray(List<String> values) {
        var array = JSON.arrayNode();
        for (String value : values == null ? List.<String>of() : values) {
            if (value != null && !value.isBlank()) {
                array.add(value);
            }
        }
        return array;
    }
}
