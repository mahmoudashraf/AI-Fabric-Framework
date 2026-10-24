package com.ai.fabric.runtime.documents;

import ai.fabric.indexing.document.model.DocumentMetadataKeys;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.ai.fabric.runtime.documents.repository.DocumentSourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class DocumentActiveVersionFilter {

    private final DocumentSourceRepository sourceRepository;
    private final DocumentRuntimeIdentity runtimeIdentity;
    private final DocumentKnowledgeProperties properties;
    private final ObjectMapper objectMapper;

    public DocumentActiveVersionFilter(DocumentSourceRepository sourceRepository, DocumentRuntimeIdentity runtimeIdentity,
                                      DocumentKnowledgeProperties properties, ObjectMapper objectMapper) {
        this.sourceRepository = sourceRepository;
        this.runtimeIdentity = runtimeIdentity;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean accepts(String entityType, Map<String, Object> metadata) {
        if (properties.getEntityType().equals(entityType)
            && (metadata == null || !StringUtils.hasText(text(metadata.get(DocumentMetadataKeys.SOURCE_ID))))) {
            return false;
        }
        return accepts(metadata);
    }

    public boolean accepts(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return true;
        }
        String sourceId = text(metadata.get(DocumentMetadataKeys.SOURCE_ID));
        if (!StringUtils.hasText(sourceId)) {
            return true;
        }
        Long version = number(metadata.get(DocumentMetadataKeys.SOURCE_VERSION));
        if (version == null) {
            return false;
        }
        var boundary = runtimeIdentity.configuredBoundary().orElse(null);
        if (boundary == null || !boundary.matchesOwner(metadata)) {
            return false;
        }
        return sourceRepository.findByIdAndTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileId(
                sourceId, boundary.tenantId(), boundary.customerId(), boundary.deploymentId(),
                boundary.targetProfileId())
            .filter(source -> DocumentSourceScope.permits(boundary, properties, objectMapper, source))
            .filter(source -> source.getId().equals(metadata.get(DocumentMetadataKeys.SOURCE_ID)))
            .filter(source -> source.getReleaseId().equals(metadata.get("releaseId")))
            .filter(source -> source.getDatasetId().equals(metadata.get("datasetId")))
            .filter(source -> DocumentSourceScope.matchesDatasetHandle(properties, objectMapper, source.getDatasetId(),
                metadata.get("knowledgeSourceHandleRef")))
            .filter(source -> source.getStatus() != DocumentSourceEntity.Status.DELETED)
            .filter(source -> source.getActiveSourceVersion() != null)
            .filter(source -> source.getActiveSourceVersion().equals(version))
            .isPresent();
    }

    private String text(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private Long number(Object value) {
        if (value != null) {
            try {
                long version = value instanceof Number
                    ? new BigDecimal(value.toString()).longValueExact()
                    : Long.parseLong(value.toString());
                return version > 0 ? version : null;
            } catch (NumberFormatException | ArithmeticException ignored) {
                return null;
            }
        }
        return null;
    }
}
