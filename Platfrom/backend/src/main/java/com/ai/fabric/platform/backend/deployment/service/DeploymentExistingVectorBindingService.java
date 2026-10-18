package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.AuthenticationType;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Request;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Summary;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** An immutable, exclusive, retained connection. No create, rotate, upgrade, drop or delete provider API is used. */
@Service
public class DeploymentExistingVectorBindingService {
    public static final String RESOURCE_KIND = PreservedExternalBindingKinds.VECTOR_STORAGE;
    public static final String REQUIRED_FIELD = "milvusExternalBindingRequired";
    public static final String AUTHENTICATION_TYPE_FIELD = "milvusAuthenticationType";

    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentProviderResourceHandleRepository handles;
    private final PlatformSecretService secrets;
    private final ZillizCloudControlPlaneClient zilliz;
    private final TenantScopedVectorHandleResolver scopes;
    private final TargetRetirementGateService retirement;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;

    public DeploymentExistingVectorBindingService(DeploymentRepository deployments, DeploymentVersionRepository versions,
        DeploymentTargetProfileRepository targets, DeploymentProviderResourceHandleRepository handles,
        PlatformSecretService secrets, ZillizCloudControlPlaneClient zilliz, TenantScopedVectorHandleResolver scopes,
        TargetRetirementGateService retirement, PlatformAuditService audit, ObjectMapper mapper) {
        this.deployments = deployments; this.versions = versions; this.targets = targets; this.handles = handles;
        this.secrets = secrets; this.zilliz = zilliz; this.scopes = scopes; this.retirement = retirement;
        this.audit = audit; this.mapper = mapper;
    }

    public static boolean required(JsonNode config) {
        return config != null && config.path(REQUIRED_FIELD).isBoolean() && config.path(REQUIRED_FIELD).booleanValue();
    }

    @Transactional
    public Summary bind(String deploymentId, String targetId, Request request) {
        if (request == null) throw failure("EXTERNAL_VECTOR_BINDING_REQUEST_REQUIRED");
        var deployment = deployments.findByIdForUpdate(deploymentId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        scope(deployment, targetId);
        var target = targets.findById(targetId).filter(value -> value.isActive())
            .orElseThrow(() -> failure("EXTERNAL_VECTOR_TARGET_REQUIRED"));
        retirement.requireAvailableForMutation(deploymentId, targetId);
        var version = versions.findById(text(request.versionId(), 64)).filter(value -> deploymentId.equals(value.getDeploymentId())
            && "PUBLISHED".equals(value.getStatus())).orElseThrow(() -> failure("EXTERNAL_VECTOR_PUBLISHED_VERSION_REQUIRED"));
        var config = config(version);
        mode(config);
        AuthenticationType authType = configuredAuthenticationType(config);
        if (request.authType() != authType) throw failure("EXTERNAL_VECTOR_AUTH_TYPE_MISMATCH");
        var credentials = credentials(authType, request.username(), request.password(), request.token());
        text(version.getConfigHash(), 128);
        String clusterId = text(request.clusterId(), 128);
        String database = text(request.databaseName(), 128);
        if (!"default".equals(database)) throw failure("EXTERNAL_VECTOR_DATABASE_UNSUPPORTED");
        String approval = text(request.approvalReference(), 255);
        String project = text(request.projectId(), 128);
        String region = text(request.regionId(), 128);
        String option = text(request.expectedDeploymentOption(), 32);
        if (!java.util.Set.of("Free", "Serverless", "Dedicated").contains(option)) throw failure("EXTERNAL_VECTOR_OPTION_INVALID");
        var existing = handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(deploymentId, targetId, RESOURCE_KIND);
        String token = scopeToken(deploymentId, targetId);
        String fingerprintSecretRef = "MANAGED_EXTERNAL_VECTOR_FINGERPRINT_" + token;
        String fingerprintKey;
        if (existing.isPresent()) {
            fingerprintKey = secrets.resolveSecret(fingerprintSecretRef);
            if (fingerprintKey == null || fingerprintKey.isBlank()) throw failure("EXTERNAL_VECTOR_FINGERPRINT_KEY_REQUIRED");
        } else {
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            fingerprintKey = Base64.getEncoder().encodeToString(random);
        }
        var credentialDigest = credentialDigest(credentials, fingerprintKey);
        var identity = mapper.createObjectNode().put("customerId", deployment.getCustomerId()).put("tenantId", deployment.getTenantId())
            .put("deploymentId", deploymentId).put("targetProfileId", targetId).put("versionId", version.getId())
            .put("configHash", version.getConfigHash()).put("clusterId", clusterId).put("projectId", project)
            .put("regionId", region).put("deploymentOption", option).put("databaseName", database)
            .put("approvalReference", approval).put("authType", authType.name()).put("credentialDigest", credentialDigest);
        String requestFingerprint = DeploymentEvidenceFingerprint.hash(mapper, identity);
        if (existing.isPresent()) {
            var previous = existing.get();
            if (!"ACTIVE".equals(previous.getStatus()) || !requestFingerprint.equals(metadata(previous).path("requestFingerprint").asText())) {
                throw new ResponseStatusException(CONFLICT, "EXTERNAL_VECTOR_BINDING_IMMUTABLE");
            }
            resolve(deployment, version, config, targetId);
            return summary(previous);
        }
        if (handles.findFirstByProviderTypeAndProviderResourceUuid(target.getProviderType(), clusterId).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "EXTERNAL_VECTOR_CLUSTER_ALREADY_BOUND");
        }
        String managementKey = secrets.resolveSecret("ZILLIZ_CLOUD_API_KEY");
        if (managementKey == null || managementKey.isBlank()) throw failure("EXTERNAL_VECTOR_READBACK_CREDENTIAL_REQUIRED");
        if (authType == AuthenticationType.USERNAME_PASSWORD
            && (managementKey.equals(credentials.password()) || managementKey.equals(credentials.username()))) {
            throw failure("EXTERNAL_VECTOR_SERVING_CREDENTIAL_REQUIRED");
        }
        var cluster = observe(clusterId, managementKey);
        if (cluster == null || !clusterId.equals(cluster.clusterId()) || !project.equals(cluster.projectId())
            || !region.equals(cluster.regionId()) || !option.equals(cluster.deploymentOption())
            || !"RUNNING".equalsIgnoreCase(cluster.status())) throw failure("EXTERNAL_VECTOR_PROVIDER_IDENTITY_CHANGED");
        URI endpoint;
        try { endpoint = URI.create(cluster.connectAddress()); }
        catch (RuntimeException ex) { throw failure("EXTERNAL_VECTOR_ENDPOINT_INVALID"); }
        if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getUserInfo() != null
            || endpoint.getQuery() != null || endpoint.getFragment() != null || endpoint.getPort() > 0
            || !(endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()))
            || !(endpoint.getHost().endsWith(".zilliz.com") || endpoint.getHost().endsWith(".zillizcloud.com"))) {
            throw failure("EXTERNAL_VECTOR_ENDPOINT_INVALID");
        }
        JsonNode scoped = scopes.targetScopedProviderConfig(deployment, config, targetId);
        verifyServingScope(version, endpoint.toString(), credentials,
            scoped.path("milvusCollectionPrefix").asText(), option);
        Map<String, ?> ownership = Map.of("deploymentId", deploymentId, "targetProfileId", targetId,
            "resourceKind", RESOURCE_KIND, "externalResourceOwned", false);
        if (authType == AuthenticationType.TOKEN) {
            String tokenRef = "MANAGED_EXTERNAL_VECTOR_TOKEN_" + token;
            secrets.upsertManagedSecret(tokenRef, credentials.token(), ownership);
            identity.put("tokenSecretRef", tokenRef);
        } else {
            String usernameRef = "MANAGED_EXTERNAL_VECTOR_USERNAME_" + token;
            String passwordRef = "MANAGED_EXTERNAL_VECTOR_PASSWORD_" + token;
            secrets.upsertManagedSecret(usernameRef, credentials.username(), ownership);
            secrets.upsertManagedSecret(passwordRef, credentials.password(), ownership);
            identity.put("usernameSecretRef", usernameRef).put("passwordSecretRef", passwordRef);
        }
        secrets.upsertManagedSecret(fingerprintSecretRef, fingerprintKey, ownership);
        identity.put("endpointHost", endpoint.getHost()).put("collectionPrefix", scoped.path("milvusCollectionPrefix").asText())
            .put("credentialFingerprintSecretRef", fingerprintSecretRef)
            .put("lifecycle", "RETAIN_EXTERNAL").put("externalResourceOwned", false).put("requestFingerprint", requestFingerprint);
        String fingerprint = DeploymentEvidenceFingerprint.hash(mapper, identity);
        identity.put("fingerprint", fingerprint);
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("evb-" + UUID.randomUUID()); handle.setDeploymentId(deploymentId); handle.setTargetProfileId(targetId);
        handle.setProviderType(target.getProviderType()); handle.setResourceKind(RESOURCE_KIND); handle.setProviderResourceUuid(clusterId);
        handle.setProviderProjectUuid(project); handle.setFqdn(endpoint.toString()); handle.setStatus("ACTIVE");
        handle.setLastObservedStatus(cluster.status()); handle.setLastObservedAt(now); handle.setCreatedAt(now); handle.setUpdatedAt(now);
        handle.setMetadataJson(identity.toString()); handles.saveAndFlush(handle);
        audit.record("EXTERNAL_VECTOR_BOUND", "DEPLOYMENT", deploymentId,
            Map.of("targetProfileId", targetId, "versionId", version.getId(), "bindingId", handle.getId(),
                "fingerprint", fingerprint, "externalResourceOwned", false, "approvalReference", approval));
        return summary(handle);
    }

    public JsonNode resolve(DeploymentEntity deployment, DeploymentVersionEntity version, JsonNode provider, String targetId) {
        if (!required(provider)) return provider;
        var local = validateLocalBinding(deployment, version, provider, targetId);
        var handle = local.handle();
        var metadata = local.metadata();
        var credentials = local.credentials();
        String managementKey = secrets.resolveSecret("ZILLIZ_CLOUD_API_KEY");
        if (managementKey == null || managementKey.isBlank()) throw failure("EXTERNAL_VECTOR_READBACK_CREDENTIAL_REQUIRED");
        if (credentials.authType() == AuthenticationType.USERNAME_PASSWORD
            && (managementKey.equals(credentials.username()) || managementKey.equals(credentials.password()))) {
            throw failure("EXTERNAL_VECTOR_SERVING_CREDENTIAL_REQUIRED");
        }
        var observed = observe(handle.getProviderResourceUuid(), managementKey);
        if (observed == null || !handle.getProviderResourceUuid().equals(observed.clusterId())
            || !metadata.path("projectId").asText().equals(observed.projectId())
            || !metadata.path("regionId").asText().equals(observed.regionId())
            || !metadata.path("deploymentOption").asText().equals(observed.deploymentOption())
            || !"RUNNING".equalsIgnoreCase(observed.status())
            || !handle.getFqdn().equals(observed.connectAddress())) throw failure("EXTERNAL_VECTOR_PROVIDER_IDENTITY_CHANGED");
        verifyServingScope(version, observed.connectAddress(), credentials, local.collectionPrefix(),
            metadata.path("deploymentOption").asText());
        return local.providerConfig();
    }

    /** Local credential evidence only. Provider identity, serving access and health are not checked. */
    public JsonNode resolveLocal(DeploymentEntity deployment, DeploymentVersionEntity version, JsonNode provider, String targetId) {
        if (!required(provider)) return provider;
        return validateLocalBinding(deployment, version, provider, targetId).providerConfig();
    }

    private LocalBinding validateLocalBinding(DeploymentEntity deployment, DeploymentVersionEntity version, JsonNode provider, String targetId) {
        scope(deployment, targetId); mode(provider);
        AuthenticationType authType = configuredAuthenticationType(provider);
        if (version == null || !"PUBLISHED".equals(version.getStatus()) || version.getConfigHash() == null || version.getConfigHash().isBlank()) {
            throw failure("EXTERNAL_VECTOR_PUBLISHED_VERSION_REQUIRED");
        }
        var handle = handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(deployment.getId(), targetId, RESOURCE_KIND)
            .filter(value -> "ACTIVE".equals(value.getStatus())).orElseThrow(() -> failure("EXTERNAL_VECTOR_BINDING_REQUIRED"));
        var metadata = metadata(handle);
        ObjectNode integrity = (ObjectNode) metadata.deepCopy();
        integrity.remove("fingerprint");
        if (!DeploymentEvidenceFingerprint.hash(mapper, integrity).equals(metadata.path("fingerprint").asText())) {
            throw failure("EXTERNAL_VECTOR_BINDING_CHANGED");
        }
        if (!authType.name().equals(metadata.path("authType").asText())) throw failure("EXTERNAL_VECTOR_AUTH_TYPE_MISMATCH");
        if (!deployment.getCustomerId().equals(metadata.path("customerId").asText())
            || !deployment.getTenantId().equals(metadata.path("tenantId").asText())
            || !deployment.getId().equals(metadata.path("deploymentId").asText())
            || !targetId.equals(metadata.path("targetProfileId").asText())
            || !version.getId().equals(metadata.path("versionId").asText())
            || !deployment.getId().equals(version.getDeploymentId())
            || !deployment.getId().equals(handle.getDeploymentId())
            || !targetId.equals(handle.getTargetProfileId())
            || !RESOURCE_KIND.equals(handle.getResourceKind())
            || !version.getConfigHash().equals(metadata.path("configHash").asText())
            || !"RETAIN_EXTERNAL".equals(metadata.path("lifecycle").asText())
            || metadata.path("externalResourceOwned").asBoolean(true)) throw failure("EXTERNAL_VECTOR_BINDING_SCOPE_CHANGED");
        var effective = (ObjectNode) provider.deepCopy();
        effective.put("milvusHost", metadata.path("endpointHost").asText()).put("milvusPort", 443).put("milvusSecure", true)
            .put("milvusDatabaseName", metadata.path("databaseName").asText());
        String expectedPrefix = scopes.targetScopedProviderConfig(deployment, effective, targetId).path("milvusCollectionPrefix").asText();
        if (!expectedPrefix.equals(metadata.path("collectionPrefix").asText())) throw failure("EXTERNAL_VECTOR_PREFIX_CHANGED");
        String token = scopeToken(deployment.getId(), targetId);
        ServingCredentials credentials;
        effective.put(AUTHENTICATION_TYPE_FIELD, authType.name());
        effective.remove(java.util.List.of("milvusRuntimeUsernameSecretName", "milvusRuntimePasswordSecretName", "milvusRuntimeTokenSecretName"));
        if (authType == AuthenticationType.TOKEN) {
            String tokenRef = metadata.path("tokenSecretRef").asText();
            if (!("MANAGED_EXTERNAL_VECTOR_TOKEN_" + token).equals(tokenRef)
                || metadata.has("usernameSecretRef") || metadata.has("passwordSecretRef")) throw failure("EXTERNAL_VECTOR_SECRET_SCOPE_CHANGED");
            credentials = runtimeCredentials(authType, null, null, secrets.resolveSecret(tokenRef));
            effective.put("milvusRuntimeTokenSecretName", tokenRef);
        } else {
            String username = metadata.path("usernameSecretRef").asText();
            String password = metadata.path("passwordSecretRef").asText();
            if (!("MANAGED_EXTERNAL_VECTOR_USERNAME_" + token).equals(username)
                || !("MANAGED_EXTERNAL_VECTOR_PASSWORD_" + token).equals(password)
                || metadata.has("tokenSecretRef")) throw failure("EXTERNAL_VECTOR_SECRET_SCOPE_CHANGED");
            credentials = runtimeCredentials(authType, secrets.resolveSecret(username), secrets.resolveSecret(password), null);
            effective.put("milvusRuntimeUsernameSecretName", username).put("milvusRuntimePasswordSecretName", password);
        }
        String fingerprintRef = metadata.path("credentialFingerprintSecretRef").asText();
        if (!("MANAGED_EXTERNAL_VECTOR_FINGERPRINT_" + token).equals(fingerprintRef)) throw failure("EXTERNAL_VECTOR_SECRET_SCOPE_CHANGED");
        String fingerprintKey = secrets.resolveSecret(fingerprintRef);
        if (fingerprintKey == null || fingerprintKey.isBlank()) throw failure("EXTERNAL_VECTOR_FINGERPRINT_KEY_REQUIRED");
        if (!credentialDigest(credentials, fingerprintKey)
            .equals(metadata.path("credentialDigest").asText())) throw failure("EXTERNAL_VECTOR_RUNTIME_CREDENTIALS_CHANGED");
        return new LocalBinding(handle, metadata, effective, credentials, expectedPrefix);
    }

    private record LocalBinding(DeploymentProviderResourceHandleEntity handle, JsonNode metadata, JsonNode providerConfig,
                                ServingCredentials credentials, String collectionPrefix) { }

    public Summary get(String deploymentId, String targetId) {
        return handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(deploymentId, targetId, RESOURCE_KIND)
            .map(this::summary).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    private Summary summary(DeploymentProviderResourceHandleEntity handle) {
        var meta = metadata(handle);
        return new Summary(handle.getId(), handle.getDeploymentId(), handle.getTargetProfileId(), meta.path("versionId").asText(),
            handle.getProviderResourceUuid(), meta.path("deploymentOption").asText(), meta.path("endpointHost").asText(),
            meta.path("databaseName").asText(), meta.path("collectionPrefix").asText(), meta.path("lifecycle").asText(),
            meta.path("fingerprint").asText(), handle.getLastObservedAt(), metadataAuthenticationType(meta));
    }

    private void scope(DeploymentEntity deployment, String targetId) {
        if (deployment == null || deployment.getId() == null || deployment.getCustomerId() == null || deployment.getTenantId() == null
            || deployment.getCustomerId().isBlank() || deployment.getTenantId().isBlank() || targetId == null || targetId.isBlank()) {
            throw failure("EXTERNAL_VECTOR_COMPLETE_SCOPE_REQUIRED");
        }
    }

    private void mode(JsonNode config) {
        if (!required(config) || !ManagedDeploymentProfileCatalog.milvusExternalExisting(config)
            || !ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(config)) throw failure("EXTERNAL_VECTOR_REVIEWED_MODE_REQUIRED");
    }

    private JsonNode config(DeploymentVersionEntity version) {
        try { return mapper.readTree(version.getProviderConfigJson()); }
        catch (Exception ex) { throw failure("EXTERNAL_VECTOR_CONFIG_INVALID"); }
    }

    private AuthenticationType configuredAuthenticationType(JsonNode config) {
        // Published 1.7 config has no mode; an explicit malformed mode must never select another credential form.
        JsonNode value = config.path(AUTHENTICATION_TYPE_FIELD);
        if (value.isMissingNode()) return AuthenticationType.USERNAME_PASSWORD;
        return authenticationType(value.asText());
    }

    private AuthenticationType metadataAuthenticationType(JsonNode metadata) {
        return authenticationType(metadata.path("authType").asText());
    }

    private AuthenticationType authenticationType(String value) {
        try { return AuthenticationType.valueOf(value); }
        catch (IllegalArgumentException error) { throw failure("EXTERNAL_VECTOR_AUTH_TYPE_INVALID"); }
    }

    private ServingCredentials credentials(AuthenticationType authType, String username, String password, String token) {
        if (authType == AuthenticationType.TOKEN) {
            if (username != null || password != null) throw failure("EXTERNAL_VECTOR_AUTH_CREDENTIALS_MIXED");
            return new ServingCredentials(authType, null, null, text(token, 4096));
        }
        if (token != null) throw failure("EXTERNAL_VECTOR_AUTH_CREDENTIALS_MIXED");
        text(username, 256); text(password, 4096);
        if (username.contains(":")) throw failure("EXTERNAL_VECTOR_USERNAME_INVALID");
        return new ServingCredentials(authType, username, password, null);
    }

    private ServingCredentials runtimeCredentials(AuthenticationType authType, String username, String password, String token) {
        try { return credentials(authType, username, password, token); }
        catch (ResponseStatusException error) { throw failure("EXTERNAL_VECTOR_RUNTIME_CREDENTIALS_REQUIRED"); }
    }

    private record ServingCredentials(AuthenticationType authType, String username, String password, String token) {
        @Override public String toString() { return "ServingCredentials[redacted]"; }
    }

    private JsonNode metadata(DeploymentProviderResourceHandleEntity handle) {
        try {
            JsonNode value = mapper.readTree(handle.getMetadataJson());
            if (!value.isObject()) throw failure("EXTERNAL_VECTOR_BINDING_INVALID");
            return value;
        }
        catch (Exception ex) { throw failure("EXTERNAL_VECTOR_BINDING_INVALID"); }
    }

    private String text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())) throw failure("EXTERNAL_VECTOR_BINDING_FIELD_INVALID");
        return value;
    }

    private String scopeToken(String deploymentId, String targetId) {
        return DeploymentEvidenceFingerprint.hash(mapper, mapper.valueToTree(Map.of("deploymentId", deploymentId, "targetProfileId", targetId)))
            .toUpperCase(java.util.Locale.ROOT);
    }

    private String credentialDigest(ServingCredentials credentials, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            ObjectNode value = mapper.createObjectNode().put("authType", credentials.authType().name());
            if (credentials.authType() == AuthenticationType.TOKEN) value.put("token", credentials.token());
            else value.put("username", credentials.username()).put("password", credentials.password());
            return HexFormat.of().formatHex(mac.doFinal(mapper.writeValueAsBytes(value)));
        } catch (Exception error) { throw failure("EXTERNAL_VECTOR_CREDENTIAL_FINGERPRINT_FAILED"); }
    }

    private void verifyServingScope(DeploymentVersionEntity version, String endpoint, ServingCredentials credentials,
                                   String prefix, String deploymentOption) {
        java.util.List<String> collections;
        try {
            collections = credentials.authType() == AuthenticationType.TOKEN
                ? zilliz.listServingCollections(endpoint, credentials.token())
                : zilliz.listServingCollections(endpoint, credentials.username(), credentials.password());
        }
        catch (RuntimeException error) { throw failure("EXTERNAL_VECTOR_SERVING_VERIFICATION_FAILED"); }
        if (collections == null || prefix.isBlank() || collections.stream().anyMatch(name -> !name.startsWith(prefix))) {
            throw failure("EXTERNAL_VECTOR_FOREIGN_COLLECTIONS_PRESENT");
        }
        if ("Free".equals(deploymentOption)) {
            java.util.Set<String> expected = new java.util.HashSet<>();
            try {
                JsonNode entities = mapper.readTree(version.getEntityConfigJson()).path("ai-entities");
                if (!entities.isObject() || entities.isEmpty()) throw failure("EXTERNAL_VECTOR_ENTITY_SCOPE_REQUIRED");
                var names = entities.fieldNames();
                while (names.hasNext()) expected.add(collectionName(names.next(), prefix));
                if (expected.size() != entities.size()) throw failure("EXTERNAL_VECTOR_COLLECTION_NAME_COLLISION");
            } catch (ResponseStatusException error) { throw error; }
            catch (Exception error) { throw failure("EXTERNAL_VECTOR_ENTITY_SCOPE_REQUIRED"); }
            expected.addAll(collections);
            if (expected.size() > 5) throw failure("EXTERNAL_VECTOR_FREE_COLLECTION_LIMIT");
        }
    }

    /** Physical identifier contract of the pinned Milvus adapter, not query-text interpretation. */
    private String collectionName(String entityType, String prefix) throws Exception {
        String normalized = entityType.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) throw failure("EXTERNAL_VECTOR_ENTITY_SCOPE_REQUIRED");
        String name = normalized.replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_")
            .replaceAll("^_+", "").replaceAll("_+$", "");
        if (name.isEmpty()) name = "collection";
        if (Character.isDigit(name.charAt(0))) name = "c_" + name;
        if (!name.equals(normalized)) name += "_h" + HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        if (name.length() > 255) name = name.substring(0, 255);
        String combined = prefix + name;
        return combined.length() > 255 ? combined.substring(0, 255) : combined;
    }

    private ZillizCloudControlPlaneClient.ZillizClusterSummary observe(String clusterId, String managementKey) {
        try { return zilliz.getCluster(clusterId, managementKey); }
        catch (RuntimeException error) { throw failure("EXTERNAL_VECTOR_PROVIDER_READBACK_FAILED"); }
    }

    private ResponseStatusException failure(String code) { return new ResponseStatusException(PRECONDITION_FAILED, code); }
}
