package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationIndexedFieldSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ShopifyStoreVectorizationFieldCatalogService {

    private static final Map<String, List<ShopifyStoreVectorizationFieldDefinition>> DEFINITIONS = definitions();
    private static final Set<String> NON_SELECTABLE_SYSTEM_FIELDS = Set.of("sourceCategory", "documentType", "updatedAt");

    public List<ShopifyStoreVectorizationIndexedFieldSummary> effectiveFields(VectorizationOverviewSummary overview) {
        JsonNode mappingConfig = overview == null || overview.plan() == null || overview.plan().activeRevision() == null
            ? null
            : overview.plan().activeRevision().mappingConfig();
        List<ShopifyStoreVectorizationIndexedFieldSummary> fields = new ArrayList<>();
        for (String sourceCategory : ShopifyStoreVectorizationConstants.SOURCE_CATEGORIES) {
            String entityType = ShopifyStoreVectorizationConstants.entityTypeForCategory(sourceCategory);
            LinkedHashSet<String> mappedSourceFields = mappedSourceFields(mappingConfig, entityType);
            for (ShopifyStoreVectorizationFieldDefinition definition : DEFINITIONS.getOrDefault(sourceCategory, List.of())) {
                if (mappedSourceFields.contains(definition.sourceField())) {
                    fields.add(new ShopifyStoreVectorizationIndexedFieldSummary(
                        definition.fieldKey(),
                        definition.sourceCategory(),
                        definition.entityType(),
                        definition.sourceField(),
                        definition.label(),
                        definition.selectable()
                    ));
                }
            }
        }
        return List.copyOf(fields);
    }

    public Map<String, ShopifyStoreVectorizationFieldSummarySupport.FieldValue> fieldValuesForRecord(String sourceCategory,
                                                                                                     Map<String, Object> normalizedRecord,
                                                                                                     VectorizationOverviewSummary overview) {
        String normalizedCategory = ShopifyStoreVectorizationConstants.normalizeSourceCategory(sourceCategory);
        List<ShopifyStoreVectorizationIndexedFieldSummary> effectiveFields = effectiveFields(overview);
        Map<String, ShopifyStoreVectorizationFieldSummarySupport.FieldValue> values = new LinkedHashMap<>();
        for (ShopifyStoreVectorizationIndexedFieldSummary field : effectiveFields) {
            if (!normalizedCategory.equals(field.sourceCategory())) {
                continue;
            }
            Object raw = normalizedRecord == null ? null : normalizedRecord.get(field.sourceField());
            values.put(
                field.fieldKey(),
                new ShopifyStoreVectorizationFieldSummarySupport.FieldValue(field, raw == null ? null : raw.toString())
            );
        }
        return values;
    }

    private LinkedHashSet<String> mappedSourceFields(JsonNode mappingConfig, String entityType) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        JsonNode entityMapping = mappingConfig == null
            ? null
            : mappingConfig.path("entityMappings").path(entityType);
        addMappedSourceFields(fields, entityMapping == null ? null : entityMapping.path("entityFieldMappings"));
        addMappedSourceFields(fields, entityMapping == null ? null : entityMapping.path("metadataFieldMappings"));
        fields.removeIf(NON_SELECTABLE_SYSTEM_FIELDS::contains);
        return fields;
    }

    private void addMappedSourceFields(LinkedHashSet<String> target, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String sourceField = entry.getValue().asText(null);
            if (sourceField != null && !sourceField.isBlank()) {
                target.add(sourceField.trim());
            }
        });
    }

    private static Map<String, List<ShopifyStoreVectorizationFieldDefinition>> definitions() {
        Map<String, List<ShopifyStoreVectorizationFieldDefinition>> definitions = new LinkedHashMap<>();
        definitions.put(
            ShopifyStoreVectorizationConstants.SOURCE_PRODUCTS,
            List.of(
                new ShopifyStoreVectorizationFieldDefinition("products.title", "products", "product", "title", "Product title", true),
                new ShopifyStoreVectorizationFieldDefinition("products.content", "products", "product", "content", "Product description", true),
                new ShopifyStoreVectorizationFieldDefinition("products.handle", "products", "product", "handle", "Product handle", true),
                new ShopifyStoreVectorizationFieldDefinition("products.vendor", "products", "product", "vendor", "Vendor", true),
                new ShopifyStoreVectorizationFieldDefinition("products.productType", "products", "product", "productType", "Product type", true),
                new ShopifyStoreVectorizationFieldDefinition("products.storefrontUrl", "products", "product", "storefrontUrl", "Product URL", true)
            )
        );
        definitions.put(
            ShopifyStoreVectorizationConstants.SOURCE_COLLECTIONS,
            List.of(
                new ShopifyStoreVectorizationFieldDefinition("collections.title", "collections", "product", "title", "Collection title", true),
                new ShopifyStoreVectorizationFieldDefinition("collections.content", "collections", "product", "content", "Collection description", true),
                new ShopifyStoreVectorizationFieldDefinition("collections.handle", "collections", "product", "handle", "Collection handle", true),
                new ShopifyStoreVectorizationFieldDefinition("collections.storefrontUrl", "collections", "product", "storefrontUrl", "Collection URL", true)
            )
        );
        definitions.put(
            ShopifyStoreVectorizationConstants.SOURCE_PAGES,
            List.of(
                new ShopifyStoreVectorizationFieldDefinition("pages.title", "pages", "support-policy", "title", "Page title", true),
                new ShopifyStoreVectorizationFieldDefinition("pages.content", "pages", "support-policy", "content", "Page body", true),
                new ShopifyStoreVectorizationFieldDefinition("pages.handle", "pages", "support-policy", "handle", "Page handle", true),
                new ShopifyStoreVectorizationFieldDefinition("pages.storefrontUrl", "pages", "support-policy", "storefrontUrl", "Page URL", true)
            )
        );
        definitions.put(
            ShopifyStoreVectorizationConstants.SOURCE_POLICIES,
            List.of(
                new ShopifyStoreVectorizationFieldDefinition("policies.title", "policies", "support-policy", "title", "Policy title", true),
                new ShopifyStoreVectorizationFieldDefinition("policies.content", "policies", "support-policy", "content", "Policy body", true),
                new ShopifyStoreVectorizationFieldDefinition("policies.policyType", "policies", "support-policy", "policyType", "Policy type", true),
                new ShopifyStoreVectorizationFieldDefinition("policies.storefrontUrl", "policies", "support-policy", "storefrontUrl", "Policy URL", true)
            )
        );
        definitions.put(
            ShopifyStoreVectorizationConstants.SOURCE_ARTICLES,
            List.of(
                new ShopifyStoreVectorizationFieldDefinition("articles.title", "articles", "support-policy", "title", "Article title", true),
                new ShopifyStoreVectorizationFieldDefinition("articles.content", "articles", "support-policy", "content", "Article body", true),
                new ShopifyStoreVectorizationFieldDefinition("articles.handle", "articles", "support-policy", "handle", "Article handle", true),
                new ShopifyStoreVectorizationFieldDefinition("articles.storefrontUrl", "articles", "support-policy", "storefrontUrl", "Article URL", true)
            )
        );
        return Map.copyOf(definitions);
    }
}
