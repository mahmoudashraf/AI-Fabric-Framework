package com.ai.fabric.runtime.documents;

import com.ai.fabric.runtime.documents.DocumentRuntimeIdentity.Boundary;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class DocumentSourceScope {

    private DocumentSourceScope() { }

    static boolean permits(Boundary boundary, DocumentKnowledgeProperties properties, ObjectMapper mapper,
                           DocumentSourceEntity source) {
        String bindingRef = properties.getConnector().getBindingRef();
        return source != null
            && boundary.tenantId().equals(source.getTenantId())
            && boundary.customerId().equals(source.getCustomerId())
            && boundary.deploymentId().equals(source.getDeploymentId())
            && boundary.targetProfileId().equals(source.getTargetProfileId())
            && StringUtils.hasText(source.getReleaseId())
            && StringUtils.hasText(bindingRef)
            && bindingRef.equals(source.getConnectorBindingRef())
            && properties.getConnector().getType() != null
            && properties.getConnector().getType().name().equals(source.getConnectorType())
            && properties.getAllowedDatasetIds() != null
            && properties.getAllowedDatasetIds().contains(source.getDatasetId())
            && source.getConnectorScopeDigest() != null
            && source.getConnectorScopeDigest().equals(storageDigest(properties, mapper));
    }

    static String storageDigest(DocumentKnowledgeProperties properties, ObjectMapper mapper) {
        try {
            var connector = properties.getConnector();
            List<String> identity = switch (connector.getType()) {
                case MOUNTED_FOLDER -> List.of(connector.getType().name(),
                    Path.of(required(connector.getMountedFolder().getRoot())).toRealPath(LinkOption.NOFOLLOW_LINKS).toString());
                case S3_COMPATIBLE_OBJECT_STORAGE -> {
                    var s3 = connector.getS3();
                    yield List.of(connector.getType().name(), origin(s3.getEndpoint()), required(s3.getRegion()),
                        required(s3.getBucket()), DocumentConnectorSupport.normalizePrefix(s3.getPrefix()));
                }
            };
            return DocumentConnectorSupport.sha256(mapper.writeValueAsString(identity));
        } catch (Exception exception) {
            throw new DocumentKnowledgeException("DOCUMENT_STORAGE_SCOPE_REQUIRED", HttpStatus.SERVICE_UNAVAILABLE,
                "Document physical storage identity is not configured or available.", exception);
        }
    }

    static boolean matchesDatasetHandle(DocumentKnowledgeProperties properties, ObjectMapper mapper,
                                        String datasetId, Object handle) {
        try {
            String configured = mapper.readTree(properties.getDatasetHandleRefsJson()).path(datasetId).asText("");
            return StringUtils.hasText(configured) && configured.equals(handle);
        } catch (Exception exception) {
            return false;
        }
    }

    private static String origin(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "aws-s3";
        }
        URI uri = URI.create(endpoint.trim());
        String scheme = required(uri.getScheme()).toLowerCase(Locale.ROOT);
        String host = required(uri.getHost()).toLowerCase(Locale.ROOT);
        if ((!"https".equals(scheme) && !"http".equals(scheme)) || uri.getUserInfo() != null
            || uri.getQuery() != null || uri.getFragment() != null
            || (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Document endpoint must be an origin.");
        }
        int port = uri.getPort() >= 0 ? uri.getPort() : ("https".equals(scheme) ? 443 : 80);
        return scheme + "://" + host + ":" + port;
    }

    private static String required(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Document physical storage identity is incomplete.");
        }
        return value.trim();
    }
}
