package com.ai.fabric.runtime.search;

import ai.fabric.core.AISearchService;
import ai.fabric.dto.AIAccessSubjectContext;
import ai.fabric.dto.RAGRequest;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.rag.source.KnowledgeSourceAdapterType;
import ai.fabric.rag.source.ResolvedKnowledgeSource;
import ai.fabric.rag.source.SearchSource;
import ai.fabric.rag.source.SearchSourceRegistry;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.config.RuntimeDeploymentKnowledgeSourceConfigService;
import com.ai.fabric.runtime.documents.DocumentActiveVersionFilter;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class RuntimeDeploymentSearchSourceRegistry implements SearchSourceRegistry {

    public static final String CONTRACT_VERSION = "SEARCH_SOURCE_REGISTRY_V1";
    public static final String DIAGNOSTICS_CONTRACT_VERSION = "SEARCH_SOURCE_DIAGNOSTICS_V1";

    private static final List<String> SUPPORTED_ADAPTER_TYPES = List.of(
        KnowledgeSourceAdapterType.DEPLOYMENT_PRIVATE_VECTOR.wireValue(),
        KnowledgeSourceAdapterType.SHARED_INDEX.wireValue()
    );
    private static final Pattern CANONICAL_ENTITY_TYPE = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private final RuntimeDeploymentKnowledgeSourceConfigService knowledgeSourceConfigService;
    private final AISearchService searchService;
    private final VectorDatabaseService vectorDatabaseService;
    private final DocumentActiveVersionFilter documentActiveVersionFilter;

    private volatile List<ResolvedKnowledgeSource> configuredSources = List.of();
    private final ConcurrentMap<String, SearchSourceHealthState> sourceHealth = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> requestedEntityTypeCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> noMatchingSourceCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, AtomicLong>> matchedSourceCounts = new ConcurrentHashMap<>();
    private final AtomicLong unresolvedEntityTypeCount = new AtomicLong();
    private final AtomicLong recordedSearchExecutions = new AtomicLong();
    private final AtomicLong degradedSearchExecutions = new AtomicLong();
    private volatile Instant lastRecordedSearchAt;

    public RuntimeDeploymentSearchSourceRegistry(RuntimeDeploymentKnowledgeSourceConfigService knowledgeSourceConfigService,
                                                 AISearchService searchService,
                                                 VectorDatabaseService vectorDatabaseService) {
        this(knowledgeSourceConfigService, searchService, vectorDatabaseService, null);
    }

    @Autowired
    public RuntimeDeploymentSearchSourceRegistry(RuntimeDeploymentKnowledgeSourceConfigService knowledgeSourceConfigService,
                                                 AISearchService searchService,
                                                 VectorDatabaseService vectorDatabaseService,
                                                 DocumentActiveVersionFilter documentActiveVersionFilter) {
        this.knowledgeSourceConfigService = knowledgeSourceConfigService;
        this.searchService = searchService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.documentActiveVersionFilter = documentActiveVersionFilter;
    }

    @PostConstruct
    void validateAndLoad() {
        List<ResolvedKnowledgeSource> sources = knowledgeSourceConfigService.currentSources();
        Map<String, Object> vectorDiagnostics = vectorDatabaseService.adminDiagnostics();
        boolean sharedStorageSupported = Boolean.TRUE.equals(vectorDiagnostics.get("sharedStorage"));
        Set<String> sourceIds = new HashSet<>();
        for (ResolvedKnowledgeSource source : sources) {
            String normalizedSourceId = source.getId() != null
                ? source.getId().trim().toLowerCase(Locale.ROOT)
                : "";
            if (!StringUtils.hasText(normalizedSourceId) || !sourceIds.add(normalizedSourceId)) {
                throw new IllegalStateException("Deployment knowledge source ids must be present and unique.");
            }
            if (source.isEnabled() && !StringUtils.hasText(source.getEntityType())) {
                throw new IllegalStateException(
                    "Enabled deployment knowledge source '" + source.getId() + "' requires a canonical entityType."
                );
            }
            if (source.isEnabled() && !CANONICAL_ENTITY_TYPE.matcher(source.getEntityType()).matches()) {
                throw new IllegalStateException(
                    "Enabled deployment knowledge source '" + source.getId()
                        + "' has non-canonical entityType '" + source.getEntityType() + "'."
                );
            }
            if (!SUPPORTED_ADAPTER_TYPES.contains(source.getAdapterType())) {
                throw new IllegalStateException(
                    "Unsupported deployment knowledge source adapter '" + source.getAdapterType()
                        + "' for source '" + source.getId() + "'. Supported adapters: " + SUPPORTED_ADAPTER_TYPES
                );
            }
            if (KnowledgeSourceAdapterType.SHARED_INDEX.wireValue().equals(source.getAdapterType())) {
                if (!StringUtils.hasText(source.getHandleRef())) {
                    throw new IllegalStateException(
                        "Shared-index knowledge source '" + source.getId() + "' requires handleRef."
                    );
                }
                if (!sharedStorageSupported) {
                    throw new IllegalStateException(
                        "Shared-index knowledge source '" + source.getId()
                            + "' requires a shared-storage-capable vector provider."
                    );
                }
            }
        }
        configuredSources = List.copyOf(sources);
        initializeHealthState(configuredSources);
    }

    @Override
    public String contractVersion() {
        return CONTRACT_VERSION;
    }

    @Override
    public List<String> supportedAdapterTypes() {
        return SUPPORTED_ADAPTER_TYPES;
    }

    @Override
    public List<SearchSource> resolveSearchSources(RAGRequest request) {
        Map<String, Object> trustedBoundaryFilters = trustedBoundaryFilters(request);
        boolean deploymentKnowledgeRequest = !trustedBoundaryFilters.isEmpty();
        String requestedEntityType = normalizedEntityType(request != null ? request.getEntityType() : null);

        if (!StringUtils.hasText(requestedEntityType)) {
            unresolvedEntityTypeCount.incrementAndGet();
            return List.of();
        }
        increment(requestedEntityTypeCounts, requestedEntityType);

        if (configuredSources.isEmpty()) {
            ResolvedKnowledgeSource defaultSource = withTrustedBoundary(
                defaultPrivateSource(request),
                trustedBoundaryFilters
            );
            incrementMatchedSource(requestedEntityType, defaultSource.getId());
            return List.of(new DeploymentPrivateVectorSearchSource(
                defaultSource,
                searchService,
                vectorDatabaseService,
                documentActiveVersionFilter
            ));
        }

        List<SearchSource> resolved = new ArrayList<>();
        configuredSources.stream()
            .filter(ResolvedKnowledgeSource::isEnabled)
            .filter(source -> entityTypeMatches(source, requestedEntityType))
            .filter(source -> !deploymentKnowledgeRequest
                || KnowledgeSourceAdapterType.DEPLOYMENT_PRIVATE_VECTOR.wireValue().equals(source.getAdapterType()))
            .map(source -> toSearchSource(source, trustedBoundaryFilters))
            .filter(Objects::nonNull)
            .forEach(resolved::add);
        if (resolved.isEmpty()) {
            increment(noMatchingSourceCounts, requestedEntityType);
        } else {
            resolved.forEach(source -> incrementMatchedSource(requestedEntityType, source.sourceId()));
        }
        return List.copyOf(resolved);
    }

    @Override
    public List<Map<String, Object>> resolutionDiagnostics(RAGRequest request) {
        String requestedEntityType = normalizedEntityType(request != null ? request.getEntityType() : null);
        String reason = StringUtils.hasText(requestedEntityType)
            ? "NO_MATCHING_KNOWLEDGE_SOURCE"
            : "UNRESOLVED_KNOWLEDGE_SOURCE_TYPE";
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("sourceId", "knowledge-source-registry");
        diagnostic.put("sourceType", "routing");
        diagnostic.put("adapterType", "registry");
        diagnostic.put("eligible", false);
        diagnostic.put("status", "SKIPPED");
        diagnostic.put("reason", reason);
        if (StringUtils.hasText(requestedEntityType)) {
            diagnostic.put("requestedEntityType", requestedEntityType);
        }
        diagnostic.put("configuredEntityTypes", configuredSources.stream()
            .filter(ResolvedKnowledgeSource::isEnabled)
            .map(ResolvedKnowledgeSource::getEntityType)
            .map(this::normalizedEntityType)
            .filter(StringUtils::hasText)
            .distinct()
            .sorted()
            .toList());
        return List.of(Collections.unmodifiableMap(diagnostic));
    }

    @Override
    public void recordSearchExecution(List<Map<String, Object>> sourceDiagnostics, boolean degraded) {
        Instant recordedAt = Instant.now();
        lastRecordedSearchAt = recordedAt;
        recordedSearchExecutions.incrementAndGet();
        if (degraded) {
            degradedSearchExecutions.incrementAndGet();
        }
        if (sourceDiagnostics == null || sourceDiagnostics.isEmpty()) {
            return;
        }

        for (Map<String, Object> diagnostic : sourceDiagnostics) {
            if (diagnostic == null || diagnostic.isEmpty()) {
                continue;
            }
            String sourceId = textValue(diagnostic.get("sourceId"));
            if (!StringUtils.hasText(sourceId)) {
                continue;
            }
            if ("knowledge-source-registry".equals(sourceId)) {
                continue;
            }
            SearchSourceHealthState state = sourceHealth.computeIfAbsent(
                sourceId,
                ignored -> SearchSourceHealthState.fromDiagnostic(diagnostic)
            );
            state.record(diagnostic, recordedAt);
        }
    }

    @Override
    public Map<String, Object> adminDiagnostics() {
        List<Map<String, Object>> sourceEntries = sourceHealth.values().stream()
            .sorted(Comparator.comparing(SearchSourceHealthState::sourceId))
            .map(SearchSourceHealthState::toDiagnostics)
            .toList();
        long degradedSources = sourceEntries.stream()
            .filter(entry -> "DEGRADED".equals(entry.get("healthStatus")))
            .count();
        long disabledSources = sourceEntries.stream()
            .filter(entry -> "DISABLED".equals(entry.get("healthStatus")))
            .count();

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("contractVersion", DIAGNOSTICS_CONTRACT_VERSION);
        diagnostics.put("degradedSearchSupported", true);
        diagnostics.put("configuredSourcesCount", sourceEntries.size());
        diagnostics.put("recordedSearchExecutions", recordedSearchExecutions.get());
        diagnostics.put("degradedSearchExecutions", degradedSearchExecutions.get());
        diagnostics.put("degradedSourcesCount", degradedSources);
        diagnostics.put("disabledSourcesCount", disabledSources);
        diagnostics.put("requestedEntityTypeCounts", snapshotCounters(requestedEntityTypeCounts));
        diagnostics.put("matchedSourceCounts", snapshotMatchedSourceCounters());
        diagnostics.put("noMatchingSourceCounts", snapshotCounters(noMatchingSourceCounts));
        diagnostics.put("unresolvedEntityTypeCount", unresolvedEntityTypeCount.get());
        diagnostics.put("lastRecordedSearchAt", lastRecordedSearchAt != null ? lastRecordedSearchAt.toString() : null);
        diagnostics.put("sources", sourceEntries);
        return Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
    }

    public List<ResolvedKnowledgeSource> configuredSources() {
        return configuredSources;
    }

    private void initializeHealthState(List<ResolvedKnowledgeSource> sources) {
        sourceHealth.clear();
        if (sources == null || sources.isEmpty()) {
            sourceHealth.put("deployment-private-vector", SearchSourceHealthState.fromResolved(defaultPrivateSource(null), true));
        }
        for (ResolvedKnowledgeSource source : sources) {
            if (source == null || !StringUtils.hasText(source.getId())) {
                continue;
            }
            sourceHealth.put(source.getId(), SearchSourceHealthState.fromResolved(source, false));
        }
    }

    private ResolvedKnowledgeSource defaultPrivateSource(RAGRequest request) {
        String entityType = request != null ? request.getEntityType() : null;
        return ResolvedKnowledgeSource.builder()
            .id("deployment-private-vector")
            .type("deployment-private-vector")
            .adapterType(KnowledgeSourceAdapterType.DEPLOYMENT_PRIVATE_VECTOR.wireValue())
            .attributionLabel("Deployment knowledge")
            .entityType(StringUtils.hasText(entityType) ? entityType : null)
            .filters(Map.of())
            .enabled(true)
            .build();
    }

    private boolean entityTypeMatches(ResolvedKnowledgeSource source, String requestedEntityType) {
        if (source == null || !StringUtils.hasText(requestedEntityType)) {
            return false;
        }
        String sourceEntityType = normalizedEntityType(source.getEntityType());
        return StringUtils.hasText(sourceEntityType) && sourceEntityType.equals(requestedEntityType);
    }

    private SearchSource toSearchSource(ResolvedKnowledgeSource source,
                                        Map<String, Object> trustedBoundaryFilters) {
        if (source == null) {
            return null;
        }
        if (KnowledgeSourceAdapterType.DEPLOYMENT_PRIVATE_VECTOR.wireValue().equals(source.getAdapterType())) {
            return new DeploymentPrivateVectorSearchSource(
                withTrustedBoundary(source, trustedBoundaryFilters),
                searchService,
                vectorDatabaseService,
                documentActiveVersionFilter
            );
        }
        if (KnowledgeSourceAdapterType.SHARED_INDEX.wireValue().equals(source.getAdapterType())) {
            return new SharedIndexSearchSource(source, searchService, vectorDatabaseService);
        }
        return null;
    }

    private String normalizedEntityType(String value) {
        return StringUtils.hasText(value)
            ? value.trim().toLowerCase(java.util.Locale.ROOT)
            : null;
    }

    private void increment(ConcurrentMap<String, AtomicLong> counters, String key) {
        if (StringUtils.hasText(key)) {
            counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        }
    }

    private void incrementMatchedSource(String requestedEntityType, String sourceId) {
        if (!StringUtils.hasText(requestedEntityType) || !StringUtils.hasText(sourceId)) {
            return;
        }
        ConcurrentMap<String, AtomicLong> bySource = matchedSourceCounts.computeIfAbsent(
            requestedEntityType,
            ignored -> new ConcurrentHashMap<>()
        );
        increment(bySource, sourceId);
    }

    private Map<String, Long> snapshotCounters(ConcurrentMap<String, AtomicLong> counters) {
        Map<String, Long> snapshot = new TreeMap<>();
        counters.forEach((key, value) -> snapshot.put(key, value.get()));
        return Collections.unmodifiableMap(snapshot);
    }

    private Map<String, Map<String, Long>> snapshotMatchedSourceCounters() {
        Map<String, Map<String, Long>> snapshot = new TreeMap<>();
        matchedSourceCounts.forEach((entityType, counters) -> snapshot.put(entityType, snapshotCounters(counters)));
        return Collections.unmodifiableMap(snapshot);
    }

    private Map<String, Object> trustedBoundaryFilters(RAGRequest request) {
        AIAccessSubjectContext authContext = request != null
            ? request.getAuthContext()
            : null;
        List<String> grantedScopes = authContext != null
            && authContext.getGrantedScopes() != null
            ? authContext.getGrantedScopes()
            : List.of();
        if (!grantedScopes.contains(RuntimeScopeCatalog.DEPLOYMENT_KNOWLEDGE_SPECIALIST)) {
            return Map.of();
        }

        String tenantId = authContext != null
            ? textValue(authContext.getTenantId())
            : null;
        String deploymentId = authContext != null
            ? textValue(authContext.getDeploymentId())
            : null;
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(deploymentId)) {
            throw new IllegalStateException(
                "Deployment knowledge retrieval requires trusted tenant and deployment boundaries."
            );
        }
        if (!"TRUSTED_APPLICATION".equalsIgnoreCase(textValue(authContext.getAuthMode()))
            || !"SERVICE".equalsIgnoreCase(textValue(authContext.getCallerType()))
            || !"deployment".equalsIgnoreCase(textValue(authContext.getSubjectType()))
            || !deploymentId.equals(textValue(authContext.getSubjectId()))) {
            throw new IllegalStateException(
                "Deployment knowledge retrieval requires a trusted application service identity bound to the deployment."
            );
        }
        if (!vectorDatabaseService.supportsSearchMetadataFiltering()) {
            throw new IllegalStateException(
                "Deployment knowledge retrieval requires metadata-filtered vector search."
            );
        }
        return Map.of(
            "tenantId", tenantId,
            "deploymentId", deploymentId
        );
    }

    private ResolvedKnowledgeSource withTrustedBoundary(
        ResolvedKnowledgeSource source,
        Map<String, Object> trustedBoundaryFilters
    ) {
        if (trustedBoundaryFilters == null || trustedBoundaryFilters.isEmpty()) {
            return source;
        }
        Map<String, Object> mergedFilters = new LinkedHashMap<>();
        if (source.getFilters() != null) {
            mergedFilters.putAll(source.getFilters());
        }
        trustedBoundaryFilters.forEach((key, trustedValue) -> {
            Object configuredValue = mergedFilters.get(key);
            if (configuredValue != null
                && !Objects.equals(configuredValue, trustedValue)) {
                throw new IllegalStateException(
                    "Deployment knowledge source '" + source.getId()
                        + "' conflicts with trusted " + key + "."
                );
            }
            mergedFilters.put(key, trustedValue);
        });
        return source.toBuilder()
            .filters(Map.copyOf(mergedFilters))
            .build();
    }

    private String textValue(Object value) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static final class SearchSourceHealthState {
        private final String sourceId;
        private volatile String sourceType;
        private volatile String adapterType;
        private final boolean defaultSource;
        private volatile boolean enabled;
        private volatile String entityType;
        private volatile boolean handleRefConfigured;
        private volatile List<String> authModes;
        private volatile String lastStatus;
        private volatile String lastReason;
        private volatile String lastFailureMessage;
        private volatile Long lastProcessingTimeMs;
        private volatile Long lastResultsCount;
        private volatile Instant lastAttemptAt;
        private volatile Instant lastSuccessAt;
        private volatile Instant lastFailureAt;
        private volatile Instant lastSkippedAt;
        private long successCount;
        private long failureCount;
        private long skippedCount;
        private long emptyResultCount;
        private long vectorSpaceMismatchCount;

        private SearchSourceHealthState(String sourceId,
                                        String sourceType,
                                        String adapterType,
                                        boolean defaultSource,
                                        boolean enabled,
                                        String entityType,
                                        boolean handleRefConfigured,
                                        List<String> authModes) {
            this.sourceId = sourceId;
            this.sourceType = sourceType;
            this.adapterType = adapterType;
            this.defaultSource = defaultSource;
            this.enabled = enabled;
            this.entityType = entityType;
            this.handleRefConfigured = handleRefConfigured;
            this.authModes = authModes != null ? List.copyOf(authModes) : List.of();
        }

        private synchronized void record(Map<String, Object> diagnostic, Instant recordedAt) {
            this.lastAttemptAt = recordedAt;
            this.sourceType = coalesce(textValue(diagnostic.get("sourceType")), this.sourceType);
            this.adapterType = coalesce(textValue(diagnostic.get("adapterType")), this.adapterType);
            this.lastStatus = coalesce(textValue(diagnostic.get("status")), this.lastStatus);
            this.lastReason = coalesce(textValue(diagnostic.get("reason")), this.lastReason);
            this.lastFailureMessage = coalesce(textValue(diagnostic.get("errorMessage")), this.lastFailureMessage);
            Long processingTimeMs = longValue(diagnostic.get("processingTimeMs"));
            if (processingTimeMs != null) {
                this.lastProcessingTimeMs = processingTimeMs;
            }
            Long resultsCount = longValue(diagnostic.get("resultsCount"));
            if (resultsCount != null) {
                this.lastResultsCount = resultsCount;
            }
            if ("SUCCEEDED".equals(this.lastStatus)) {
                successCount++;
                lastSuccessAt = recordedAt;
                if (resultsCount != null && resultsCount == 0L) {
                    emptyResultCount++;
                }
            } else if ("FAILED".equals(this.lastStatus)) {
                failureCount++;
                lastFailureAt = recordedAt;
            } else if ("SKIPPED".equals(this.lastStatus)) {
                skippedCount++;
                lastSkippedAt = recordedAt;
            }
            Long mismatches = longValue(diagnostic.get("vectorSpaceMismatchCount"));
            if (mismatches != null && mismatches > 0) {
                vectorSpaceMismatchCount += mismatches;
            }
        }

        private Map<String, Object> toDiagnostics() {
            Map<String, Object> diagnostics = new LinkedHashMap<>();
            diagnostics.put("sourceId", sourceId);
            diagnostics.put("sourceType", sourceType);
            diagnostics.put("adapterType", adapterType);
            diagnostics.put("defaultSource", defaultSource);
            diagnostics.put("enabled", enabled);
            diagnostics.put("entityType", entityType);
            diagnostics.put("authModes", authModes);
            diagnostics.put("handleRefConfigured", handleRefConfigured);
            diagnostics.put("healthStatus", healthStatus());
            diagnostics.put("lastStatus", lastStatus);
            diagnostics.put("lastReason", lastReason);
            diagnostics.put("lastFailureMessage", lastFailureMessage);
            diagnostics.put("lastProcessingTimeMs", lastProcessingTimeMs);
            diagnostics.put("lastResultsCount", lastResultsCount);
            diagnostics.put("lastAttemptAt", lastAttemptAt != null ? lastAttemptAt.toString() : null);
            diagnostics.put("lastSuccessAt", lastSuccessAt != null ? lastSuccessAt.toString() : null);
            diagnostics.put("lastFailureAt", lastFailureAt != null ? lastFailureAt.toString() : null);
            diagnostics.put("lastSkippedAt", lastSkippedAt != null ? lastSkippedAt.toString() : null);
            diagnostics.put("successCount", successCount);
            diagnostics.put("failureCount", failureCount);
            diagnostics.put("skippedCount", skippedCount);
            diagnostics.put("emptyResultCount", emptyResultCount);
            diagnostics.put("vectorSpaceMismatchCount", vectorSpaceMismatchCount);
            return Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
        }

        private String sourceId() {
            return sourceId;
        }

        private String healthStatus() {
            if (!enabled) {
                return "DISABLED";
            }
            if ("FAILED".equals(lastStatus)) {
                return "DEGRADED";
            }
            return "READY";
        }

        private static SearchSourceHealthState fromResolved(ResolvedKnowledgeSource source, boolean defaultSource) {
            return new SearchSourceHealthState(
                source.getId(),
                source.getType(),
                source.getAdapterType(),
                defaultSource,
                source.isEnabled(),
                source.getEntityType(),
                StringUtils.hasText(source.getHandleRef()),
                source.getAuthModes()
            );
        }

        private static SearchSourceHealthState fromDiagnostic(Map<String, Object> diagnostic) {
            String sourceId = textValue(diagnostic.get("sourceId"));
            return new SearchSourceHealthState(
                StringUtils.hasText(sourceId) ? sourceId : "unknown",
                textValue(diagnostic.get("sourceType")),
                textValue(diagnostic.get("adapterType")),
                false,
                true,
                null,
                false,
                List.of()
            );
        }

        private static String coalesce(String candidate, String currentValue) {
            return StringUtils.hasText(candidate) ? candidate : currentValue;
        }

        private static String textValue(Object value) {
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
            return null;
        }

        private static Long longValue(Object value) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && StringUtils.hasText(text)) {
                try {
                    return Long.parseLong(text.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }
}
