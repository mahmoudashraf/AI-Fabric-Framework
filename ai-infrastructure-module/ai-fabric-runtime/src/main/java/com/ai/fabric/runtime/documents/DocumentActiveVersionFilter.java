package com.ai.fabric.runtime.documents;

import ai.fabric.indexing.document.model.DocumentMetadataKeys;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.ai.fabric.runtime.documents.repository.DocumentSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class DocumentActiveVersionFilter {

    private final DocumentSourceRepository sourceRepository;

    public DocumentActiveVersionFilter(DocumentSourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
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
        return sourceRepository.findById(sourceId)
            .filter(source -> source.getStatus() != DocumentSourceEntity.Status.DELETED)
            .filter(source -> source.getActiveSourceVersion() != null)
            .filter(source -> source.getActiveSourceVersion().equals(version))
            .filter(source -> boundaryMatches(source, metadata))
            .isPresent();
    }

    private boolean boundaryMatches(DocumentSourceEntity source, Map<String, Object> metadata) {
        String tenant = text(metadata.get("tenantId"));
        String customer = text(metadata.get("customerId"));
        String deployment = text(metadata.get("deploymentId"));
        return StringUtils.hasText(tenant)
            && StringUtils.hasText(customer)
            && StringUtils.hasText(deployment)
            && source.getTenantId().equals(tenant)
            && source.getCustomerId().equals(customer)
            && source.getDeploymentId().equals(deployment);
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
