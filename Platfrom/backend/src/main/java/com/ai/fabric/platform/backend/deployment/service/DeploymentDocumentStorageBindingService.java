package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DocumentStorageBindingSummary;
import com.ai.fabric.platform.backend.deployment.model.UpsertDocumentStorageBindingRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DeploymentDocumentStorageBindingService {

    public static final String RESOURCE_KIND = "EXTERNAL_DOCUMENT_STORAGE_BINDING";
    public static final String S3_CONNECTOR = "S3_COMPATIBLE_OBJECT_STORAGE";
    public static final String MOUNTED_CONNECTOR = "MOUNTED_FOLDER";
    public static final String MOUNTED_ROOT = "/app/document-sources";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final DeploymentProviderResourceHandleRepository handleRepository;
    private final PlatformSecretService secretService;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;

    public DeploymentDocumentStorageBindingService(
        DeploymentRepository deploymentRepository,
        DeploymentAccessService deploymentAccessService,
        DeploymentTargetProfileRepository targetProfileRepository,
        DeploymentProviderResourceHandleRepository handleRepository,
        PlatformSecretService secretService,
        PlatformAuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.targetProfileRepository = targetProfileRepository;
        this.handleRepository = handleRepository;
        this.secretService = secretService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<DocumentStorageBindingSummary> list(String deploymentId) {
        DeploymentEntity deployment = requireDeployment(deploymentId, false);
        return handleRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId()).stream()
            .filter(handle -> RESOURCE_KIND.equals(handle.getResourceKind()))
            .map(this::summary)
            .toList();
    }

    @Transactional
    public DocumentStorageBindingSummary upsert(String deploymentId, UpsertDocumentStorageBindingRequest request) {
        DeploymentEntity deployment = requireDeployment(deploymentId, true);
        if (request == null) {
            throw badRequest("Document storage binding request is required.");
        }
        DeploymentTargetProfileEntity profile = targetProfileRepository.findById(required(request.targetProfileId(), "targetProfileId"))
            .filter(DeploymentTargetProfileEntity::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active deployment target profile was not found."));
        if (StringUtils.hasText(deployment.getEnvironmentName())
            && StringUtils.hasText(profile.getEnvironmentName())
            && !deployment.getEnvironmentName().equalsIgnoreCase(profile.getEnvironmentName())) {
            throw badRequest("Document storage binding target environment must match the deployment environment.");
        }
        String connectorType = required(request.connectorType(), "connectorType").toUpperCase(Locale.ROOT);
        if (!S3_CONNECTOR.equals(connectorType) && !MOUNTED_CONNECTOR.equals(connectorType)) {
            throw badRequest("Unsupported document storage connector type.");
        }
        if (MOUNTED_CONNECTOR.equals(connectorType)
            && ("production".equalsIgnoreCase(deployment.getEnvironmentName())
                || "production".equalsIgnoreCase(profile.getEnvironmentName()))) {
            throw badRequest("Mounted document storage is limited to non-production demos and small-data canaries.");
        }
        if (MOUNTED_CONNECTOR.equals(connectorType)
            && profile.getProviderType() != com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY) {
            throw badRequest("Mounted document storage is currently supported only by Coolify demo target profiles.");
        }

        String key = secretKey(deployment.getId(), profile.getId());
        ObjectNode safe = objectMapper.createObjectNode();
        safe.put("connectorType", connectorType);
        safe.put("storageOwnership", "CUSTOMER_MANAGED");
        safe.put("readOnly", true);
        safe.put("deleteSourceOnRemoval", false);
        safe.put("configuredAt", Instant.now().toString());

        if (S3_CONNECTOR.equals(connectorType)) {
            URI candidateEndpoint = parseEndpoint(request.endpoint());
            boolean privateEndpointApproved = privateEndpointApproved(candidateEndpoint.getHost(), profile);
            URI endpoint = endpoint(
                candidateEndpoint,
                Boolean.TRUE.equals(request.allowInsecureEndpoint()),
                privateEndpointApproved,
                profile
            );
            String bucket = required(request.bucket(), "bucket");
            if (!bucket.matches("[A-Za-z0-9][A-Za-z0-9._-]{1,253}")) {
                throw badRequest("S3-compatible bucket is invalid.");
            }
            String prefix = normalizePrefix(request.prefix());
            String region = StringUtils.hasText(request.region()) ? request.region().trim() : "us-east-1";
            String accessKey = required(request.accessKey(), "accessKey");
            String secretKey = required(request.secretKey(), "secretKey");

            putManagedSecret(key, "ENDPOINT", endpoint.toString(), deployment);
            putManagedSecret(key, "REGION", region, deployment);
            putManagedSecret(key, "BUCKET", bucket, deployment);
            putManagedSecret(key, "PREFIX", prefix, deployment);
            putManagedSecret(key, "ACCESS_KEY", accessKey, deployment);
            putManagedSecret(key, "SECRET_KEY", secretKey, deployment);
            putManagedSecret(key, "SESSION_TOKEN", request.sessionToken(), deployment);
            clearManagedSecret(key, "MOUNTED_ROOT", deployment);

            safe.put("endpointHost", endpoint.getHost());
            safe.put("bucketAlias", bounded(bucket, 64));
            safe.put("allowedPrefixDigest", sha256(prefix));
            safe.put("region", bounded(region, 64));
            safe.put("pathStyleAccess", request.pathStyleAccess() == null || request.pathStyleAccess());
            safe.put("objectVersioningAvailable", Boolean.TRUE.equals(request.objectVersioningAvailable()));
            safe.put("allowInsecureEndpoint", Boolean.TRUE.equals(request.allowInsecureEndpoint()));
            safe.put("privateEndpointApproved", privateEndpointApproved);
        } else {
            String mountedRoot = StringUtils.hasText(request.mountedRoot()) ? request.mountedRoot().trim() : MOUNTED_ROOT;
            if (!MOUNTED_ROOT.equals(mountedRoot)) {
                throw badRequest("Mounted document storage must use the target-profile path " + MOUNTED_ROOT + ".");
            }
            putManagedSecret(key, "MOUNTED_ROOT", mountedRoot, deployment);
            clearManagedSecret(key, "ENDPOINT", deployment);
            clearManagedSecret(key, "REGION", deployment);
            clearManagedSecret(key, "BUCKET", deployment);
            clearManagedSecret(key, "PREFIX", deployment);
            clearManagedSecret(key, "ACCESS_KEY", deployment);
            clearManagedSecret(key, "SECRET_KEY", deployment);
            clearManagedSecret(key, "SESSION_TOKEN", deployment);
            safe.put("demoOrSmallDataOnly", true);
            safe.put("mountedRootDigest", sha256(mountedRoot));
        }

        DeploymentProviderResourceHandleEntity handle = handleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(), profile.getId(), RESOURCE_KIND
            )
            .orElseGet(DeploymentProviderResourceHandleEntity::new);
        Instant now = Instant.now();
        if (!StringUtils.hasText(handle.getId())) {
            handle.setId("dsh-" + UUID.randomUUID().toString().substring(0, 12));
            handle.setCreatedAt(now);
        }
        handle.setDeploymentId(deployment.getId());
        handle.setTargetProfileId(profile.getId());
        handle.setProviderType(profile.getProviderType());
        handle.setResourceKind(RESOURCE_KIND);
        handle.setProviderResourceUuid("external-document-storage:" + deployment.getId() + ":" + profile.getId());
        handle.setStatus("CONFIGURED");
        handle.setLastObservedStatus("NOT_PREFLIGHTED");
        handle.setMetadataJson(writeJson(safe));
        handle.setUpdatedAt(now);
        handleRepository.save(handle);

        auditService.record(
            "DOCUMENT_STORAGE_BINDING_CONFIGURED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("deploymentId", deployment.getId(), "bindingRef", handle.getId(), "connectorType", connectorType)
        );
        return summary(handle);
    }

    @Transactional
    public void remove(String deploymentId, String bindingRef) {
        DeploymentEntity deployment = requireDeployment(deploymentId, true);
        DeploymentProviderResourceHandleEntity handle = requireBinding(deployment, bindingRef);
        String key = secretKey(deployment.getId(), handle.getTargetProfileId());
        for (String suffix : List.of(
            "ENDPOINT", "REGION", "BUCKET", "PREFIX", "ACCESS_KEY", "SECRET_KEY", "SESSION_TOKEN", "MOUNTED_ROOT"
        )) {
            clearManagedSecret(key, suffix, deployment);
        }
        handleRepository.delete(handle);
        auditService.record(
            "DOCUMENT_STORAGE_BINDING_REMOVED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("deploymentId", deployment.getId(), "bindingRef", handle.getId(), "sourceObjectsDeleted", false)
        );
    }

    @Transactional
    public int cleanupForDeletedDeployment(DeploymentEntity deployment) {
        if (deployment == null || !StringUtils.hasText(deployment.getId())) {
            return 0;
        }
        List<DeploymentProviderResourceHandleEntity> bindings = handleRepository
            .findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId()).stream()
            .filter(handle -> RESOURCE_KIND.equals(handle.getResourceKind()))
            .toList();
        for (DeploymentProviderResourceHandleEntity binding : bindings) {
            String key = secretKey(deployment.getId(), binding.getTargetProfileId());
            for (String suffix : List.of(
                "ENDPOINT", "REGION", "BUCKET", "PREFIX", "ACCESS_KEY", "SECRET_KEY", "SESSION_TOKEN", "MOUNTED_ROOT"
            )) {
                clearManagedSecret(key, suffix, deployment);
            }
        }
        if (!bindings.isEmpty()) {
            handleRepository.deleteAll(bindings);
            auditService.record(
                "DOCUMENT_STORAGE_BINDINGS_CLEANED",
                "DEPLOYMENT",
                deployment.getId(),
                Map.of(
                    "deploymentId", deployment.getId(),
                    "bindingCount", bindings.size(),
                    "sourceObjectsDeleted", false
                )
            );
        }
        return bindings.size();
    }

    private DeploymentEntity requireDeployment(String deploymentId, boolean edit) {
        DeploymentEntity deployment = deploymentRepository.findById(required(deploymentId, "deploymentId"))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment was not found."));
        return edit
            ? deploymentAccessService.requireDeploymentAdminAccess(deployment)
            : deploymentAccessService.requireDeploymentAccess(deployment);
    }

    private DeploymentProviderResourceHandleEntity requireBinding(DeploymentEntity deployment, String bindingRef) {
        return handleRepository.findById(required(bindingRef, "bindingRef"))
            .filter(handle -> deployment.getId().equals(handle.getDeploymentId()))
            .filter(handle -> RESOURCE_KIND.equals(handle.getResourceKind()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document storage binding was not found."));
    }

    private DocumentStorageBindingSummary summary(DeploymentProviderResourceHandleEntity handle) {
        JsonNode metadata = readJson(handle.getMetadataJson());
        String connectorType = metadata.path("connectorType").asText("");
        String key = secretKey(handle.getDeploymentId(), handle.getTargetProfileId());
        boolean present = MOUNTED_CONNECTOR.equals(connectorType)
            ? secretService.isSecretPresent(key + "_MOUNTED_ROOT")
            : secretService.isSecretPresent(key + "_ENDPOINT")
                && secretService.isSecretPresent(key + "_BUCKET")
                && secretService.isSecretPresent(key + "_ACCESS_KEY")
                && secretService.isSecretPresent(key + "_SECRET_KEY");
        return new DocumentStorageBindingSummary(
            handle.getId(),
            handle.getDeploymentId(),
            handle.getTargetProfileId(),
            connectorType,
            handle.getStatus(),
            present,
            metadata,
            handle.getUpdatedAt()
        );
    }

    private URI parseEndpoint(String value) {
        try {
            return URI.create(required(value, "endpoint"));
        } catch (RuntimeException exception) {
            throw badRequest("S3-compatible endpoint is invalid.");
        }
    }

    private URI endpoint(
        URI endpoint,
        boolean allowInsecure,
        boolean privateEndpointApproved,
        DeploymentTargetProfileEntity profile
    ) {
        boolean httpAllowed = allowInsecure
            && privateEndpointApproved
            && !"production".equalsIgnoreCase(profile.getEnvironmentName());
        if (endpoint.getHost() == null
            || endpoint.getUserInfo() != null
            || endpoint.getQuery() != null
            || endpoint.getFragment() != null
            || (StringUtils.hasText(endpoint.getPath()) && !"/".equals(endpoint.getPath()))
            || !("https".equalsIgnoreCase(endpoint.getScheme())
                || httpAllowed && "http".equalsIgnoreCase(endpoint.getScheme()))) {
            throw badRequest("S3-compatible endpoint must be an approved origin without a path, query, or fragment.");
        }
        if (!privateEndpointApproved && isLocalOrPrivateLiteral(endpoint.getHost())) {
            throw badRequest("Private document storage endpoints require an exact target-profile network-policy approval.");
        }
        return endpoint;
    }

    private boolean isLocalOrPrivateLiteral(String host) {
        String normalized = normalizeHost(host);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            return true;
        }
        if (!normalized.contains(":") && !normalized.matches("[0-9.]+")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
        } catch (Exception exception) {
            return true;
        }
    }

    private boolean privateEndpointApproved(String endpointHost, DeploymentTargetProfileEntity profile) {
        if (!StringUtils.hasText(endpointHost)) {
            return false;
        }
        String expected = normalizeHost(endpointHost);
        JsonNode allowedHosts = readJson(profile.getNetworkPolicyJson()).path("documentStoragePrivateEndpointHosts");
        if (!allowedHosts.isArray()) {
            return false;
        }
        for (JsonNode host : allowedHosts) {
            if (host.isTextual() && expected.equals(normalizeHost(host.asText()))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeHost(String value) {
        String host = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private String normalizePrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.equals("..") || normalized.contains("../") || normalized.contains("://")) {
            throw badRequest("S3-compatible prefix is invalid.");
        }
        return normalized.isEmpty() || normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private void putManagedSecret(String key, String suffix, String value, DeploymentEntity deployment) {
        if (StringUtils.hasText(value)) {
            secretService.upsertManagedSecret(key + "_" + suffix, value, Map.of(
                "deploymentId", deployment.getId(),
                "purpose", "DOCUMENT_STORAGE_BINDING"
            ));
        } else {
            clearManagedSecret(key, suffix, deployment);
        }
    }

    private void clearManagedSecret(String key, String suffix, DeploymentEntity deployment) {
        secretService.clearManagedSecret(key + "_" + suffix, Map.of(
            "deploymentId", deployment.getId(),
            "purpose", "DOCUMENT_STORAGE_BINDING"
        ));
    }

    public static String secretKey(String deploymentId, String targetProfileId) {
        return "MANAGED_DOCUMENT_STORAGE_DEP_"
            + deploymentId.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_")
            + "_PROFILE_"
            + targetProfileId.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    private String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw badRequest(field + " is required.");
        }
        return value.trim();
    }

    private String bounded(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store document storage binding metadata.", exception);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(StringUtils.hasText(value) ? value : "{}");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read document storage binding metadata.", exception);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
