package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyStoreVectorizationTriggerProperties;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationPolicyEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationIndexedFieldSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationPolicySummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationSourcePolicyInput;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationSourcePolicySummary;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreVectorizationPolicyRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreVectorizationPolicyRepository;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class ShopifyStoreVectorizationPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyStoreVectorizationPolicyService.class);

    private static final TypeReference<LinkedHashMap<String, ShopifyStoreVectorizationPolicyState>> POLICY_TYPE =
        new TypeReference<>() { };

    private final ShopifyStoreVectorizationPolicyRepository repository;
    private final ShopifyStoreVectorizationFieldCatalogService fieldCatalogService;
    private final ShopifyStoreVectorizationTriggerProperties properties;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public ShopifyStoreVectorizationPolicyService(ShopifyStoreVectorizationPolicyRepository repository,
                                                  ShopifyStoreVectorizationFieldCatalogService fieldCatalogService,
                                                  ShopifyStoreVectorizationTriggerProperties properties,
                                                  PlatformAuditService platformAuditService,
                                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.fieldCatalogService = fieldCatalogService;
        this.properties = properties;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ShopifyStoreVectorizationPolicySummary getSummary(ShopifyStoreConnectionEntity store,
                                                             VectorizationOverviewSummary overview) {
        ShopifyStoreVectorizationPolicyEntity entity = repository.findByShopDomainIgnoreCase(store.getShopDomain()).orElse(null);
        ResolvedPolicy resolved = resolve(entity, store, overview);
        return resolved.summary();
    }

    @Transactional(readOnly = true)
    public ResolvedPolicy resolveForEvaluation(ShopifyStoreConnectionEntity store,
                                               VectorizationOverviewSummary overview) {
        return resolve(repository.findByShopDomainIgnoreCase(store.getShopDomain()).orElse(null), store, overview);
    }

    @Transactional
    public ShopifyStoreVectorizationPolicySummary update(ShopifyStoreConnectionEntity store,
                                                         VectorizationOverviewSummary overview,
                                                         UpdateShopifyStoreVectorizationPolicyRequest request) {
        ShopifyStoreVectorizationPolicyEntity entity = repository.findByShopDomainIgnoreCase(store.getShopDomain())
            .orElseGet(() -> newPolicyEntity(store.getShopDomain()));
        if (request != null && request.policyVersion() != null && request.policyVersion() != entity.getPolicyVersion()) {
            throw new ResponseStatusException(CONFLICT, "Shopify vectorization policy changed. Refresh and try again.");
        }

        ResolvedPolicy current = resolve(entity, store, overview);
        Map<String, ShopifyStoreVectorizationPolicyState> nextStates = new LinkedHashMap<>(current.states());
        List<ShopifyStoreVectorizationIndexedFieldSummary> effectiveFields = fieldCatalogService.effectiveFields(overview);
        Map<String, List<String>> selectableByCategory = effectiveFields.stream()
            .filter(ShopifyStoreVectorizationIndexedFieldSummary::selectableForTriggerPolicy)
            .collect(Collectors.groupingBy(
                ShopifyStoreVectorizationIndexedFieldSummary::sourceCategory,
                LinkedHashMap::new,
                Collectors.mapping(ShopifyStoreVectorizationIndexedFieldSummary::fieldKey, Collectors.toList())
            ));

        for (ShopifyStoreVectorizationSourcePolicyInput input : request == null || request.sourcePolicies() == null ? List.<ShopifyStoreVectorizationSourcePolicyInput>of() : request.sourcePolicies()) {
            String category = ShopifyStoreVectorizationConstants.normalizeSourceCategory(input.sourceCategory());
            ShopifyStoreVectorizationPolicyState currentState = nextStates.get(category);
            String updateMode = ShopifyStoreVectorizationConstants.normalizeUpdateTriggerMode(
                input.updateTriggerMode() == null ? currentState.updateTriggerMode() : input.updateTriggerMode()
            );
            List<String> selectedFields = normalizeSelectedFields(category, input.selectedIndexedFields(), selectableByCategory);
            if (ShopifyStoreVectorizationConstants.UPDATE_MODE_SELECTED.equals(updateMode) && selectedFields.isEmpty()) {
                throw new ResponseStatusException(CONFLICT, "Selected indexed fields are required when update mode is SELECTED_INDEXED_FIELDS.");
            }
            nextStates.put(
                category,
                new ShopifyStoreVectorizationPolicyState(
                    input.autoIndexingEnabled() == null ? currentState.autoIndexingEnabled() : input.autoIndexingEnabled(),
                    input.createTriggerEnabled() == null ? currentState.createTriggerEnabled() : input.createTriggerEnabled(),
                    input.deleteTriggerEnabled() == null ? currentState.deleteTriggerEnabled() : input.deleteTriggerEnabled(),
                    updateMode,
                    selectedFields,
                    normalizePositive(input.debounceWindowSeconds(), currentState.debounceWindowSeconds()),
                    normalizePositive(input.minimumRunIntervalSeconds(), currentState.minimumRunIntervalSeconds())
                )
            );
        }

        entity.setSourcePoliciesJson(writePolicies(nextStates));
        entity.setPolicyVersion(entity.getPolicyVersion() + 1);
        entity.setUpdatedBy(PlatformSecurityContext.actorIdOrSystem());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        ResolvedPolicy updated = resolve(entity, store, overview);
        platformAuditService.record(
            "SHOPIFY_STORE_VECTORIZATION_POLICY_UPDATED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "policyVersion", Long.toString(updated.summary().policyVersion()),
                "updatedBy", PlatformSecurityContext.actorIdOrSystem(),
                "sourcePolicies", updated.rawStates()
            )
        );
        return updated.summary();
    }

    private ResolvedPolicy resolve(ShopifyStoreVectorizationPolicyEntity entity,
                                   ShopifyStoreConnectionEntity store,
                                   VectorizationOverviewSummary overview) {
        ShopifyStoreVectorizationPolicyEntity effectiveEntity = entity;
        if (effectiveEntity == null) {
            effectiveEntity = newPolicyEntity(store.getShopDomain());
        }
        Map<String, ShopifyStoreVectorizationPolicyState> rawStates = parsePolicies(effectiveEntity.getSourcePoliciesJson());
        LinkedHashMap<String, ShopifyStoreVectorizationPolicyState> mergedStates = new LinkedHashMap<>();
        List<ShopifyStoreVectorizationSourcePolicySummary> summaries = new ArrayList<>();
        for (String sourceCategory : ShopifyStoreVectorizationConstants.SOURCE_CATEGORIES) {
            ShopifyStoreVectorizationPolicyState state = rawStates.getOrDefault(sourceCategory, defaultState(sourceCategory));
            mergedStates.put(sourceCategory, state);
            summaries.add(toSummary(sourceCategory, state, store));
        }
        return new ResolvedPolicy(
            effectiveEntity,
            Map.copyOf(mergedStates),
            rawStateSnapshot(mergedStates),
            new ShopifyStoreVectorizationPolicySummary(
                effectiveEntity.getPolicyVersion(),
                effectiveEntity.isAutoIndexingDefault(),
                List.copyOf(summaries),
                effectiveEntity.getUpdatedBy(),
                effectiveEntity.getUpdatedAt()
            )
        );
    }

    private ShopifyStoreVectorizationSourcePolicySummary toSummary(String sourceCategory,
                                                                  ShopifyStoreVectorizationPolicyState state,
                                                                  ShopifyStoreConnectionEntity store) {
        return new ShopifyStoreVectorizationSourcePolicySummary(
            sourceCategory,
            ShopifyStoreVectorizationConstants.sourceEnabled(store, sourceCategory),
            ShopifyStoreVectorizationConstants.manualAllowed(store, sourceCategory),
            ShopifyStoreVectorizationConstants.manualAllowed(store, sourceCategory),
            state.autoIndexingEnabled(),
            state.createTriggerEnabled(),
            state.deleteTriggerEnabled(),
            state.updateTriggerMode(),
            state.selectedIndexedFields(),
            state.debounceWindowSeconds(),
            state.minimumRunIntervalSeconds()
        );
    }

    private List<String> normalizeSelectedFields(String sourceCategory,
                                                 List<String> requested,
                                                 Map<String, List<String>> selectableByCategory) {
        LinkedHashSet<String> allowed = new LinkedHashSet<>(selectableByCategory.getOrDefault(sourceCategory, List.of()));
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String value : requested == null ? List.<String>of() : requested) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.trim();
            if (!allowed.contains(normalized)) {
                throw new ResponseStatusException(CONFLICT, "Unsupported indexed field selection for " + sourceCategory + ": " + normalized);
            }
            selected.add(normalized);
        }
        return List.copyOf(selected);
    }

    private int normalizePositive(Integer requested, int fallback) {
        if (requested == null) {
            return fallback;
        }
        if (requested < 0) {
            throw new ResponseStatusException(CONFLICT, "Shopify vectorization timing values must be zero or greater.");
        }
        return requested;
    }

    private LinkedHashMap<String, ShopifyStoreVectorizationPolicyState> parsePolicies(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new LinkedHashMap<>();
            }
            LinkedHashMap<String, ShopifyStoreVectorizationPolicyState> parsed = objectMapper.readValue(json, POLICY_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return new LinkedHashMap<>();
            }
            LinkedHashMap<String, ShopifyStoreVectorizationPolicyState> sanitized = new LinkedHashMap<>();
            parsed.forEach((key, value) -> {
                String sourceCategory = normalizePersistedSourceCategory(key);
                if (sourceCategory == null) {
                    return;
                }
                sanitized.put(sourceCategory, sanitizeState(sourceCategory, value));
            });
            return sanitized;
        } catch (Exception ex) {
            log.warn("Ignoring malformed Shopify vectorization policy JSON and falling back to defaults: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String writePolicies(Map<String, ShopifyStoreVectorizationPolicyState> states) {
        try {
            return objectMapper.writeValueAsString(states);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Shopify vectorization policy JSON.", ex);
        }
    }

    private ShopifyStoreVectorizationPolicyEntity newPolicyEntity(String shopDomain) {
        ShopifyStoreVectorizationPolicyEntity entity = new ShopifyStoreVectorizationPolicyEntity();
        Instant now = Instant.now();
        entity.setId("svp-" + UUID.randomUUID().toString().substring(0, 8));
        entity.setShopDomain(shopDomain.trim().toLowerCase(Locale.ROOT));
        entity.setPolicyVersion(1);
        entity.setAutoIndexingDefault(false);
        LinkedHashMap<String, ShopifyStoreVectorizationPolicyState> defaults = new LinkedHashMap<>();
        ShopifyStoreVectorizationConstants.SOURCE_CATEGORIES.forEach(category -> defaults.put(category, defaultState(category)));
        entity.setSourcePoliciesJson(writePolicies(defaults));
        entity.setUpdatedBy(PlatformSecurityContext.actorIdOrSystem());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private ShopifyStoreVectorizationPolicyState defaultState(String sourceCategory) {
        return switch (ShopifyStoreVectorizationConstants.normalizeSourceCategory(sourceCategory)) {
            case ShopifyStoreVectorizationConstants.SOURCE_PRODUCTS,
                 ShopifyStoreVectorizationConstants.SOURCE_COLLECTIONS -> new ShopifyStoreVectorizationPolicyState(
                false,
                true,
                true,
                ShopifyStoreVectorizationConstants.UPDATE_MODE_INDEXED_ONLY,
                List.of(),
                30,
                60
            );
            case ShopifyStoreVectorizationConstants.SOURCE_PAGES -> new ShopifyStoreVectorizationPolicyState(
                false,
                false,
                false,
                ShopifyStoreVectorizationConstants.UPDATE_MODE_INDEXED_ONLY,
                List.of(),
                30,
                60
            );
            case ShopifyStoreVectorizationConstants.SOURCE_POLICIES -> new ShopifyStoreVectorizationPolicyState(
                false,
                false,
                false,
                ShopifyStoreVectorizationConstants.UPDATE_MODE_INDEXED_ONLY,
                List.of(),
                30,
                60
            );
            case ShopifyStoreVectorizationConstants.SOURCE_ARTICLES -> new ShopifyStoreVectorizationPolicyState(
                false,
                false,
                false,
                ShopifyStoreVectorizationConstants.UPDATE_MODE_INDEXED_ONLY,
                List.of(),
                30,
                60
            );
            case ShopifyStoreVectorizationConstants.SOURCE_METAOBJECTS -> new ShopifyStoreVectorizationPolicyState(
                false,
                false,
                false,
                ShopifyStoreVectorizationConstants.UPDATE_MODE_INDEXED_ONLY,
                List.of(),
                30,
                60
            );
            default -> throw new IllegalStateException("Unsupported Shopify source category: " + sourceCategory);
        };
    }

    private ShopifyStoreVectorizationPolicyState sanitizeState(String sourceCategory,
                                                               ShopifyStoreVectorizationPolicyState persisted) {
        ShopifyStoreVectorizationPolicyState fallback = defaultState(sourceCategory);
        if (persisted == null) {
            return fallback;
        }
        return new ShopifyStoreVectorizationPolicyState(
            persisted.autoIndexingEnabled(),
            persisted.createTriggerEnabled(),
            persisted.deleteTriggerEnabled(),
            sanitizeUpdateTriggerMode(persisted.updateTriggerMode(), fallback.updateTriggerMode()),
            sanitizeSelectedIndexedFields(persisted.selectedIndexedFields()),
            Math.max(0, persisted.debounceWindowSeconds()),
            Math.max(0, persisted.minimumRunIntervalSeconds())
        );
    }

    private String sanitizeUpdateTriggerMode(String requested, String fallback) {
        try {
            return ShopifyStoreVectorizationConstants.normalizeUpdateTriggerMode(requested);
        } catch (ResponseStatusException ex) {
            return fallback;
        }
    }

    private List<String> sanitizeSelectedIndexedFields(List<String> selectedIndexedFields) {
        if (selectedIndexedFields == null || selectedIndexedFields.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : selectedIndexedFields) {
            if (value == null || value.isBlank()) {
                continue;
            }
            values.add(value.trim());
        }
        return List.copyOf(values);
    }

    private String normalizePersistedSourceCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return ShopifyStoreVectorizationConstants.SOURCE_CATEGORIES.contains(normalized) ? normalized : null;
    }

    private Map<String, Object> rawStateSnapshot(Map<String, ShopifyStoreVectorizationPolicyState> states) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        states.forEach((key, value) -> snapshot.put(key, value));
        return snapshot;
    }

    public record ResolvedPolicy(
        ShopifyStoreVectorizationPolicyEntity entity,
        Map<String, ShopifyStoreVectorizationPolicyState> states,
        Map<String, Object> rawStates,
        ShopifyStoreVectorizationPolicySummary summary
    ) {
        public ShopifyStoreVectorizationPolicyState requireSourcePolicy(String sourceCategory) {
            String normalized = ShopifyStoreVectorizationConstants.normalizeSourceCategory(sourceCategory);
            ShopifyStoreVectorizationPolicyState state = states.get(normalized);
            if (state == null) {
                throw new ResponseStatusException(CONFLICT, "Shopify vectorization policy is missing source family: " + normalized);
            }
            return state;
        }
    }
}
