package com.ai.fabric.runtime.search;

import com.ai.fabric.runtime.config.RuntimeDeploymentKnowledgeSourceConfigService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.source.KnowledgeSourceAdapterType;
import com.ai.infrastructure.rag.source.ResolvedKnowledgeSource;
import com.ai.infrastructure.rag.source.SearchSource;
import com.ai.infrastructure.rag.source.SearchSourceRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuntimeDeploymentSearchSourceRegistry implements SearchSourceRegistry {

    public static final String CONTRACT_VERSION = "SEARCH_SOURCE_REGISTRY_V1";

    private static final List<String> SUPPORTED_ADAPTER_TYPES = List.of(
        KnowledgeSourceAdapterType.DEPLOYMENT_PRIVATE_VECTOR.wireValue(),
        KnowledgeSourceAdapterType.SHARED_INDEX.wireValue()
    );

    private final RuntimeDeploymentKnowledgeSourceConfigService knowledgeSourceConfigService;
    private final AISearchService searchService;
    private final VectorDatabaseService vectorDatabaseService;

    private volatile List<ResolvedKnowledgeSource> configuredSources = List.of();

    public RuntimeDeploymentSearchSourceRegistry(RuntimeDeploymentKnowledgeSourceConfigService knowledgeSourceConfigService,
                                                 AISearchService searchService,
                                                 VectorDatabaseService vectorDatabaseService) {
        this.knowledgeSourceConfigService = knowledgeSourceConfigService;
        this.searchService = searchService;
        this.vectorDatabaseService = vectorDatabaseService;
    }

    @PostConstruct
    void validateAndLoad() {
        List<ResolvedKnowledgeSource> sources = knowledgeSourceConfigService.currentSources();
        Map<String, Object> vectorDiagnostics = vectorDatabaseService.adminDiagnostics();
        boolean sharedStorageSupported = Boolean.TRUE.equals(vectorDiagnostics.get("sharedStorage"));
        for (ResolvedKnowledgeSource source : sources) {
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
        List<SearchSource> resolved = new ArrayList<>();
        ResolvedKnowledgeSource configuredPrivateSource = configuredSources.stream()
            .filter(source -> KnowledgeSourceAdapterType.DEPLOYMENT_PRIVATE_VECTOR.wireValue().equals(source.getAdapterType()))
            .findFirst()
            .orElse(defaultPrivateSource(request));
        resolved.add(new DeploymentPrivateVectorSearchSource(configuredPrivateSource, searchService, vectorDatabaseService));
        configuredSources.stream()
            .filter(source -> KnowledgeSourceAdapterType.SHARED_INDEX.wireValue().equals(source.getAdapterType()))
            .map(source -> new SharedIndexSearchSource(source, searchService, vectorDatabaseService))
            .forEach(resolved::add);
        return List.copyOf(resolved);
    }

    public List<ResolvedKnowledgeSource> configuredSources() {
        return configuredSources;
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
}
