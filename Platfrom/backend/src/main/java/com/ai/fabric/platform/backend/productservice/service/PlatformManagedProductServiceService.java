package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.CreatePlatformManagedProductServiceRequest;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.model.UpdatePlatformManagedProductServiceShopifyBillingConfigRequest;
import com.ai.fabric.platform.backend.productservice.model.UpdatePlatformManagedProductServiceReleaseRequest;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformManagedProductServiceService {

    public static final String STANDALONE_AI_WORKSPACE_SERVICE = "STANDALONE_AI_WORKSPACE_SERVICE";
    public static final String OWNED_DEPENDENCY_SERVICE = "OWNED_DEPENDENCY_SERVICE";

    private final PlatformManagedProductServiceRepository repository;
    private final ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public PlatformManagedProductServiceService(PlatformManagedProductServiceRepository repository,
                                                ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                                PlatformSecretService platformSecretService,
                                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.shopifyStoreConnectionRepository = shopifyStoreConnectionRepository;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    public List<PlatformManagedProductServiceSummary> listServices() {
        return repository.findAllByOrderByDisplayNameAsc().stream()
            .map(this::toSummary)
            .toList();
    }

    public PlatformManagedProductServiceSummary getService(String serviceRef) {
        return toSummary(requireService(serviceRef));
    }

    public PlatformManagedProductServiceEntity requireService(String serviceRef) {
        return repository.findByServiceRefIgnoreCase(serviceRef)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Managed product service not found: " + serviceRef));
    }

    public PlatformManagedProductServiceEntity requireServiceById(String serviceId) {
        return repository.findById(serviceId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Managed product service not found: " + serviceId));
    }

    public PlatformManagedProductServiceSummary createService(CreatePlatformManagedProductServiceRequest request) {
        if (OWNED_DEPENDENCY_SERVICE.equals(normalizeEnum(request.serviceKind()))) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_GRAPH_REQUIRED");
        }
        String normalizedServiceRef = normalizeServiceRef(request.serviceRef());
        repository.findByServiceRefIgnoreCase(normalizedServiceRef).ifPresent(existing -> {
            throw new ResponseStatusException(CONFLICT, "Managed product service already exists: " + normalizedServiceRef);
        });

        int minReplicas = request.minReplicas() != null && request.minReplicas() > 0 ? request.minReplicas() : 1;
        int maxReplicas = request.maxReplicas() != null && request.maxReplicas() > 0 ? request.maxReplicas() : Math.max(minReplicas, 3);
        int desiredReplicas = request.desiredReplicas() != null && request.desiredReplicas() > 0 ? request.desiredReplicas() : minReplicas;
        if (desiredReplicas < minReplicas || desiredReplicas > maxReplicas) {
            throw new ResponseStatusException(CONFLICT, "desiredReplicas must be between minReplicas and maxReplicas.");
        }

        PlatformManagedProductServiceEntity entity = new PlatformManagedProductServiceEntity();
        entity.setId(generateId("psv"));
        entity.setServiceRef(normalizedServiceRef);
        entity.setDisplayName(trimmed(request.displayName()));
        entity.setProductFamily(normalizeEnum(request.productFamily()));
        entity.setServiceKind(normalizeEnum(request.serviceKind()));
        entity.setDeploymentMode(normalizeEnum(request.deploymentMode()));
        entity.setTenantMode(normalizeEnum(request.tenantMode()));
        entity.setEnvironmentScope(trimToNull(request.environmentScope()));
        entity.setDeploymentId(trimToNull(request.deploymentId()));
        entity.setDesiredReplicas(desiredReplicas);
        entity.setActualReplicas(0);
        entity.setMinReplicas(minReplicas);
        entity.setMaxReplicas(maxReplicas);
        entity.setBaseUrl(trimToNull(request.baseUrl()));
        entity.setHealthPath(defaultHealthPath(entity.getServiceKind(), request.healthPath()));
        entity.setServiceRoot(defaultServiceRoot(entity.getServiceKind(), request.serviceRoot()));
        entity.setDockerfilePath(defaultDockerfilePath(entity.getServiceKind(), request.dockerfilePath()));
        entity.setSecretName(trimToNull(request.secretName()));
        entity.setStatus(StringUtils.hasText(entity.getBaseUrl()) ? "ACTIVE" : "CREATED");
        entity.setDetailsJson(initialDetailsJson(
            entity.getServiceKind(),
            request.shopifyBillingConfig(),
            request.targetProfileId(),
            request.imageRepository(),
            request.imageTag(),
            request.imageDigest(),
            request.sourceCommit()
        ));
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return toSummary(entity);
    }

    @org.springframework.transaction.annotation.Transactional
    public PlatformManagedProductServiceEntity ensureOwnedDependency(String graphId, String deploymentId,
            String targetProfileId, String serviceRef, String slot, String image, String fingerprint) {
        PlatformManagedProductServiceEntity existing = repository.findByServiceRefIgnoreCase(serviceRef).orElse(null);
        if (existing != null) {
            JsonNode details = readDetails(existing.getDetailsJson());
            if (!OWNED_DEPENDENCY_SERVICE.equals(existing.getServiceKind())
                || !graphId.equals(details.path("dependencyGraphId").asText())
                || !fingerprint.equals(details.path("specFingerprint").asText())
                || !targetProfileId.equals(details.path("targetProfileId").asText())
                || !deploymentId.equals(existing.getDeploymentId())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_OWNERSHIP_CONFLICT");
            }
            return existing;
        }
        PlatformManagedProductServiceEntity entity = new PlatformManagedProductServiceEntity();
        entity.setId(generateId("psv"));
        entity.setServiceRef(serviceRef);
        entity.setDisplayName(slot + " / " + graphId);
        entity.setProductFamily("MANAGED_DEPENDENCY");
        entity.setServiceKind(OWNED_DEPENDENCY_SERVICE);
        entity.setDeploymentMode("DEDICATED_COOLIFY");
        entity.setTenantMode("DEDICATED");
        entity.setDeploymentId(deploymentId);
        entity.setDesiredReplicas(1);
        entity.setActualReplicas(0);
        entity.setMinReplicas(1);
        entity.setMaxReplicas(1);
        entity.setStatus("CREATED");
        ObjectNode details = objectMapper.createObjectNode();
        details.put("dependencyGraphId", graphId);
        details.put("specFingerprint", fingerprint);
        details.put("targetProfileId", targetProfileId);
        details.put("providerType", "COOLIFY");
        details.put("imageRepository", image);
        entity.setDetailsJson(details.toString());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        return repository.save(entity);
    }

    public PlatformManagedProductServiceSummary updateDesiredReplicas(String serviceRef, Integer desiredReplicas) {
        PlatformManagedProductServiceEntity service = requireService(serviceRef);
        requireNotOwnedDependency(service);
        int min = service.getMinReplicas() != null && service.getMinReplicas() > 0 ? service.getMinReplicas() : 1;
        int max = service.getMaxReplicas() != null && service.getMaxReplicas() > 0 ? service.getMaxReplicas() : Math.max(min, 1);
        if (desiredReplicas == null || desiredReplicas < min || desiredReplicas > max) {
            throw new ResponseStatusException(CONFLICT, "desiredReplicas must be between " + min + " and " + max + " for service " + serviceRef + ".");
        }
        service.setDesiredReplicas(desiredReplicas);
        service.setUpdatedAt(Instant.now());
        repository.save(service);
        return toSummary(service);
    }

    public void requireNotOwnedDependency(PlatformManagedProductServiceEntity service) {
        if (OWNED_DEPENDENCY_SERVICE.equals(service.getServiceKind())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_GRAPH_LIFECYCLE_REQUIRED");
        }
    }

    public PlatformManagedProductServiceSummary updateShopifyBillingConfig(String serviceRef,
                                                                           UpdatePlatformManagedProductServiceShopifyBillingConfigRequest request) {
        PlatformManagedProductServiceEntity service = requireService(serviceRef);
        if (!PlatformManagedProductServiceShopifyBillingConfigSupport.supports(service.getServiceKind())) {
            throw new ResponseStatusException(CONFLICT, "Shopify billing configuration is only supported for Shopify Bridge services.");
        }
        ObjectNode details = PlatformManagedProductServiceShopifyBillingConfigSupport.detailsWithConfig(
            objectMapper,
            service.getDetailsJson(),
            request
        );
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        repository.save(service);
        return toSummary(service);
    }

    public PlatformManagedProductServiceSummary updateRelease(
        String serviceRef,
        UpdatePlatformManagedProductServiceReleaseRequest request
    ) {
        PlatformManagedProductServiceEntity service = requireService(serviceRef);
        requireStandaloneWorkspace(service);
        ObjectNode details = readDetails(service.getDetailsJson()).deepCopy();
        writeStandaloneRelease(
            details,
            request.imageRepository(),
            request.imageTag(),
            request.imageDigest(),
            request.sourceCommit()
        );
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        repository.save(service);
        return toSummary(service);
    }

    PlatformManagedProductServiceSummary toSummary(PlatformManagedProductServiceEntity entity) {
        JsonNode details = readDetails(entity.getDetailsJson());
        int dependentStores = (int) shopifyStoreConnectionRepository.countByProductServiceId(entity.getId());
        int activeDependentStores = (int) shopifyStoreConnectionRepository.countByProductServiceIdAndInstallStatusIgnoreCase(entity.getId(), "INSTALLED");
        return new PlatformManagedProductServiceSummary(
            entity.getId(),
            entity.getServiceRef(),
            entity.getDisplayName(),
            entity.getProductFamily(),
            entity.getServiceKind(),
            entity.getDeploymentMode(),
            entity.getTenantMode(),
            entity.getEnvironmentScope(),
            entity.getDeploymentId(),
            entity.getRailwayProjectId(),
            entity.getRailwayEnvironmentId(),
            entity.getRailwayServiceId(),
            entity.getDesiredReplicas(),
            entity.getActualReplicas(),
            entity.getMinReplicas(),
            entity.getMaxReplicas(),
            entity.getBaseUrl(),
            entity.getPrivateNetworkUrl(),
            entity.getHealthPath(),
            entity.getServiceRoot(),
            entity.getDockerfilePath(),
            entity.getSecretName(),
            entity.getStatus(),
            hasText(entity.getSecretName()) && platformSecretService.isSecretPresent(entity.getSecretName()),
            text(details, "lastDeploymentId", "deploymentId"),
            text(details, "lastReconciledAt", "reconciledAt"),
            text(details, "lastReconcileStatus"),
            text(details, "lastReconcileMessage", "statusMessage"),
            text(details, "lastHealthyAt"),
            text(details, "lastProbeAt"),
            text(details, "lastSuccessfulProbeAt"),
            text(details, "lastFailedProbeAt"),
            text(details, "lastProbeStatus"),
            text(details, "lastProbeMessage"),
            text(details, "lastVerifiedOperation"),
            text(details, "lastVerifiedAt"),
            text(details, "lastVerifiedStatus"),
            text(details, "lastVerifiedMessage"),
            text(details, "driftStatus"),
            text(details, "driftMessage"),
            dependentStores,
            activeDependentStores,
            text(details, "imageRepository"),
            text(details, "imageTag"),
            text(details, "imageDigest"),
            text(details, "sourceCommit"),
            PlatformManagedProductServiceShopifyBillingConfigSupport.summaryFromDetails(
                objectMapper,
                entity.getDetailsJson(),
                entity.getServiceKind()
            )
        );
    }

    private String initialDetailsJson(String serviceKind,
                                      UpdatePlatformManagedProductServiceShopifyBillingConfigRequest shopifyBillingConfig,
                                      String targetProfileId,
                                      String imageRepository,
                                      String imageTag,
                                      String imageDigest,
                                      String sourceCommit) {
        ObjectNode details = objectMapper.createObjectNode();
        String normalizedTargetProfileId = trimToNull(targetProfileId);
        if (normalizedTargetProfileId != null) {
            details.put("providerType", "COOLIFY");
            details.put("targetProfileId", normalizedTargetProfileId);
        }
        if (PlatformManagedProductServiceShopifyBillingConfigSupport.supports(serviceKind)) {
            details = PlatformManagedProductServiceShopifyBillingConfigSupport.detailsWithConfig(
                objectMapper,
                details.toPrettyString(),
                shopifyBillingConfig
            );
        }
        if (STANDALONE_AI_WORKSPACE_SERVICE.equals(normalizeEnum(serviceKind))) {
            writeStandaloneRelease(details, imageRepository, imageTag, imageDigest, sourceCommit);
        } else if (hasAnyText(imageRepository, imageTag, imageDigest, sourceCommit)) {
            throw new ResponseStatusException(CONFLICT, "Immutable image release fields are supported only for standalone AI Workspace services.");
        }
        return details.toPrettyString();
    }

    private void requireStandaloneWorkspace(PlatformManagedProductServiceEntity service) {
        if (!STANDALONE_AI_WORKSPACE_SERVICE.equals(normalizeEnum(service.getServiceKind()))) {
            throw new ResponseStatusException(CONFLICT, "Immutable image release updates are supported only for standalone AI Workspace services.");
        }
    }

    private void writeStandaloneRelease(ObjectNode details,
                                        String imageRepository,
                                        String imageTag,
                                        String imageDigest,
                                        String sourceCommit) {
        String repository = trimToNull(imageRepository);
        String tag = trimToNull(imageTag);
        String digest = trimToNull(imageDigest);
        String commit = trimToNull(sourceCommit);
        if (!hasText(repository) || !hasText(tag) || !hasText(digest) || !hasText(commit)) {
            throw new ResponseStatusException(CONFLICT, "Standalone AI Workspace services require image repository, immutable tag, digest, and source commit.");
        }
        if (!repository.matches("[A-Za-z0-9._/-]+")) {
            throw new ResponseStatusException(CONFLICT, "Standalone image repository is invalid.");
        }
        if (!tag.matches("[a-f0-9]{40}") || !commit.matches("[a-f0-9]{40}")) {
            throw new ResponseStatusException(CONFLICT, "Standalone image tag and source commit must be exact 40-character commits.");
        }
        if (!tag.equals(commit)) {
            throw new ResponseStatusException(CONFLICT, "Standalone image tag must match the source commit.");
        }
        if (!digest.matches("sha256:[a-f0-9]{64}")) {
            throw new ResponseStatusException(CONFLICT, "Standalone image digest must be a sha256 digest.");
        }
        details.put("imageRepository", repository);
        details.put("imageTag", tag);
        details.put("imageDigest", digest);
        details.put("sourceCommit", commit);
        details.put("releaseUpdatedAt", Instant.now().toString());
    }

    private boolean hasAnyText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode readDetails(String detailsJson) {
        try {
            return hasText(detailsJson) ? objectMapper.readTree(detailsJson) : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String... candidates) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String candidate : candidates) {
            JsonNode value = node.path(candidate);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = trimToNull(value.asText());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private String normalizeServiceRef(String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "serviceRef is required.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]{2,127}")) {
            throw new ResponseStatusException(CONFLICT, "serviceRef must be 3-128 characters and contain only lowercase letters, numbers, hyphen, or underscore.");
        }
        return normalized;
    }

    private String normalizeEnum(String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "Enum-like values are required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimmed(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(CONFLICT, "Required value is blank.");
        }
        return trimmed;
    }

    private String defaultHealthPath(String serviceKind, String requestedValue) {
        String explicit = trimToNull(requestedValue);
        if (explicit != null) {
            return explicit;
        }
        return switch (normalizeEnum(serviceKind)) {
            case "SHOPIFY_BRIDGE_SERVICE" -> "/actuator/health";
            case STANDALONE_AI_WORKSPACE_SERVICE -> "/health";
            default -> "/actuator/health";
        };
    }

    private String defaultServiceRoot(String serviceKind, String requestedValue) {
        String explicit = trimToNull(requestedValue);
        if (explicit != null) {
            return explicit;
        }
        return switch (normalizeEnum(serviceKind)) {
            case "SHOPIFY_BRIDGE_SERVICE" -> "product-services/shopify-bridge-service";
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> "product-services/mcp-execution-gateway-service";
            case STANDALONE_AI_WORKSPACE_SERVICE -> "workspace-apps/standalone-ai-workspace";
            default -> null;
        };
    }

    private String defaultDockerfilePath(String serviceKind, String requestedValue) {
        String explicit = trimToNull(requestedValue);
        if (explicit != null) {
            return explicit;
        }
        return switch (normalizeEnum(serviceKind)) {
            case "SHOPIFY_BRIDGE_SERVICE" -> "product-services/shopify-bridge-service/deploy/container/Dockerfile";
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> "product-services/mcp-execution-gateway-service/deploy/container/Dockerfile";
            case STANDALONE_AI_WORKSPACE_SERVICE -> "workspace-apps/standalone-ai-workspace/Dockerfile";
            default -> null;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
