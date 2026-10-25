package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAiStateStorageBindingContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAiStateStorageBindingContracts.Result;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAiStateStorageBindingService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

/** Customer authority is checked before delegating to the same typed, retained-external SQL binding. */
@Service
public class CustomerAiStateStorageBindingService {
    private static final String COMMAND = "BIND_AI_STATE";
    private static final String PROFILE = "CUSTOMER_POSTGRES";
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerEntitlementCommandRepository commands;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentAiStateStorageBindingService bindings;
    private final PlatformSecretService secrets;
    private final CustomerOfferCodec codec;
    private final PlatformAuditService audit;

    public CustomerAiStateStorageBindingService(CustomerOfferEntitlementService entitlements,
        CustomerSelfServiceAuthorizationService authorization, CustomerEntitlementCommandRepository commands,
        DeploymentRepository deployments, DeploymentVersionRepository versions, DeploymentAiStateStorageBindingService bindings,
        PlatformSecretService secrets, CustomerOfferCodec codec, PlatformAuditService audit) {
        this.entitlements = entitlements; this.authorization = authorization; this.commands = commands;
        this.deployments = deployments; this.versions = versions; this.bindings = bindings;
        this.secrets = secrets; this.codec = codec; this.audit = audit;
    }

    @Transactional
    public Result bind(String offerId, String deploymentId, Request request) {
        if (request == null || request.connection() == null
            || blank(request.targetProfileId()) || blank(request.bindingSlotId())
            || blank(request.connection().versionId()) || request.connection().approvalReference() != null) throw invalid();
        entitlements.requireOperationKey(request.operationKey());
        var actor = entitlements.requireCustomerActor();
        var deployment = deployments.findByIdForUpdate(deploymentId)
            .filter(value -> actor.customerId().equals(value.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            request.targetProfileId(), deploymentId, deployment.getTenantId(), null, Operation.BINDING_REPLACE,
            request.operationKey(), Set.of(), request.bindingSlotId(), PROFILE, null));
        requireVersion(approved, deploymentId, request.connection().versionId());
        var namespace = entitlements.findCommandNamespaceForUpdate(actor.customerId(), offerId).orElse(null);
        String fingerprint = null;
        if (namespace != null) {
            fingerprint = fingerprint(offerId, deploymentId, actor.customerId(), request);
            var previous = commands.findByEntitlementIdAndOperationKey(namespace.getId(), request.operationKey()).orElse(null);
            if (previous != null) {
                if (!COMMAND.equals(previous.getCommandType()) || !fingerprint.equals(previous.getRequestFingerprint())) {
                    throw conflict(OPERATION_KEY_CONFLICT);
                }
                return codec.read(previous.getResultJson(), Result.class);
            }
        }
        var input = request.connection();
        String approval = "customer-ai-state:" + codec.hash(Map.of("customerId", actor.customerId(),
            "deploymentId", deploymentId, "targetProfileId", request.targetProfileId(), "versionId", input.versionId()));
        var bound = bindings.bind(deploymentId, request.targetProfileId(), new DeploymentAiStateStorageContracts.Request(
            input.versionId(), input.host(), input.port(), input.databaseName(), input.runtimeSchema(), input.connectorSchema(),
            input.username(), input.password(), input.migrationAuthorized(), approval));
        var result = new Result(offerId, deploymentId, request.targetProfileId(), request.bindingSlotId(), bound);
        if (namespace != null) {
            commands.saveAndFlush(new CustomerEntitlementCommandEntity("cec-" + UUID.randomUUID(), namespace.getId(),
                request.operationKey(), fingerprint, COMMAND, PlatformSecurityContext.currentPrincipal().actorId(),
                codec.write(result), Instant.now()));
        }
        audit.record("CUSTOMER_AI_STATE_STORAGE_BOUND", "DEPLOYMENT", deploymentId,
            Map.of("offerId", offerId, "targetProfileId", request.targetProfileId(), "bindingId", bound.bindingId(),
                "mode", PROFILE, "externalResourceOwned", false));
        return result;
    }

    @Transactional
    public DeploymentAiStateStorageContracts.Summary get(String offerId, String deploymentId, String targetId,
                                                        String bindingSlotId, String versionId) {
        if (blank(targetId) || blank(bindingSlotId) || blank(versionId)) throw invalid();
        var actor = entitlements.requireCustomerActor();
        var deployment = deployments.findById(deploymentId).filter(value -> actor.customerId().equals(value.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId, targetId,
            deploymentId, deployment.getTenantId(), null, Operation.READ, "read-ai-state-binding", Set.of(),
            null, null, null));
        requireVersion(approved, deploymentId, versionId);
        return bindings.get(deploymentId, targetId, versionId);
    }

    private void requireVersion(Authorization approved, String deploymentId, String versionId) {
        var version = versions.findById(versionId).filter(value -> deploymentId.equals(value.getDeploymentId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        authorization.requireReviewedVersion(approved, version);
        if (!com.ai.fabric.platform.backend.deployment.service.DeploymentAiStateStorageSelection.external(
            codec.tree(version.getProviderConfigJson()))) throw deny(BINDING_SLOT_FORBIDDEN);
    }

    private String fingerprint(String offerId, String deploymentId, String customerId, Request request) {
        String name = "MANAGED_AI_STATE_REQUEST_SIGNING_" + codec.hash(Map.of("deploymentId", deploymentId))
            .toUpperCase(java.util.Locale.ROOT);
        String key = secrets.resolveSecret(name);
        if (key == null || key.isBlank()) {
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            key = Base64.getEncoder().encodeToString(random);
            secrets.upsertManagedSecret(name, key, Map.of("deploymentId", deploymentId,
                "customerId", customerId, "purpose", "AI_STATE_REQUEST_FINGERPRINT"));
        }
        // Secret inputs are fingerprinted privately; bootstrap revision and actor identity do not restrict replay.
        var connection = request.connection();
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("command", COMMAND); material.put("offerId", offerId); material.put("deploymentId", deploymentId);
        material.put("customerId", customerId); material.put("targetProfileId", request.targetProfileId());
        material.put("bindingSlotId", request.bindingSlotId()); material.put("versionId", connection.versionId());
        material.put("host", connection.host()); material.put("port", connection.port());
        material.put("databaseName", connection.databaseName()); material.put("runtimeSchema", connection.runtimeSchema());
        material.put("connectorSchema", connection.connectorSchema()); material.put("username", connection.username());
        material.put("password", connection.password()); material.put("migrationAuthorized", connection.migrationAuthorized());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(codec.write(material).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("AI_STATE_REQUEST_FINGERPRINT_UNAVAILABLE"); }
    }

    private boolean blank(String value) { return value == null || value.isBlank() || value.length() > 64; }
}
