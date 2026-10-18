package com.ai.fabric.runtime.documents;

import ai.fabric.indexing.document.model.DocumentMetadataKeys;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.ai.fabric.runtime.documents.repository.DocumentSourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class DocumentActiveVersionFilter {

    private final DocumentSourceRepository sourceRepository;
    private final DocumentRuntimeIdentity runtimeIdentity;
    private final String documentEntityType;

    public DocumentActiveVersionFilter(DocumentSourceRepository sourceRepository, DocumentRuntimeIdentity runtimeIdentity,
                                      @Value("${loomai.documents.entity-type:document}") String documentEntityType) {
        this.sourceRepository = sourceRepository;
        this.runtimeIdentity = runtimeIdentity;
        this.documentEntityType = documentEntityType;
    }

    public boolean accepts(String entityType, Map<String, Object> metadata) {
        if (documentEntityType.equals(entityType)
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
        if (boundary == null || !boundary.matches(metadata)) {
            return false;
        }
        return sourceRepository.findByIdAndTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndReleaseId(
                sourceId, boundary.tenantId(), boundary.customerId(), boundary.deploymentId(),
                boundary.targetProfileId(), boundary.releaseId())
            .filter(source -> source.getStatus() != DocumentSourceEntity.Status.DELETED)
            .filter(source -> source.getActiveSourceVersion() != null)
            .filter(source -> source.getActiveSourceVersion().equals(version))
            .isPresent();
    }

    private String text(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private Long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
