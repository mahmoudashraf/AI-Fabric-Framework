package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

/** Creates owned configuration only; admission and provider effects belong to a separate command. */
@Service
public class CustomerDeploymentBootstrapService {
    private static final String COMMAND = "BOOTSTRAP_CONFIG";
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerEntitlementCommandRepository commands;
    private final MarketplacePluginVersionRepository versions;
    private final DeploymentMarketplacePluginInstallRepository installs;
    private final MarketplaceTemplateBootstrapService templates;
    private final DeploymentRepository deployments;
    private final CustomerOfferCodec codec;
    private final PlatformAuditService audit;
    private final CustomerDeploymentStorageSelectionService storage;
    private final DeploymentMarketplaceDraftCompilerService compiler;
    private final com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationInstallInitializer externalInstalls;

    public CustomerDeploymentBootstrapService(CustomerOfferEntitlementService entitlements,
        CustomerSelfServiceAuthorizationService authorization, CustomerEntitlementCommandRepository commands,
        MarketplacePluginVersionRepository versions, DeploymentMarketplacePluginInstallRepository installs,
        MarketplaceTemplateBootstrapService templates, DeploymentRepository deployments,
        CustomerOfferCodec codec, PlatformAuditService audit, CustomerDeploymentStorageSelectionService storage,
        DeploymentMarketplaceDraftCompilerService compiler,
        com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationInstallInitializer externalInstalls) {
        this.entitlements = entitlements;
        this.authorization = authorization;
        this.commands = commands;
        this.versions = versions;
        this.installs = installs;
        this.templates = templates;
        this.deployments = deployments;
        this.codec = codec;
        this.audit = audit;
        this.storage = storage;
        this.compiler = compiler;
        this.externalInstalls = externalInstalls;
    }

    @Transactional
    public Result bootstrap(String offerId, Request request) {
        if (request == null || request.targetProfileId() == null || request.targetProfileId().isBlank()
            || request.targetProfileId().length() > 64 || request.name() == null || request.name().isBlank()
            || !request.name().equals(request.name().trim()) || request.name().length() > 255
            || request.expectedEntitlementRevision() < 0) throw invalid();
        entitlements.requireOperationKey(request.operationKey());
        var actor = entitlements.requireCustomerActor();
        var grant = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        String actorId = PlatformSecurityContext.currentPrincipal().actorId();
        String fingerprint = codec.hash(Map.of("command", COMMAND, "offerId", offerId,
            "request", request, "customerId", actor.customerId(), "ownerId", actor.userId(), "actor", actorId));
        var previous = commands.findByEntitlementIdAndOperationKey(grant.getId(), request.operationKey()).orElse(null);
        if (previous != null) {
            if (!COMMAND.equals(previous.getCommandType()) || !fingerprint.equals(previous.getRequestFingerprint())
                || !actorId.equals(previous.getActorId())) throw conflict(OPERATION_KEY_CONFLICT);
            return codec.read(previous.getResultJson(), Result.class);
        }
        if (grant.getRevision() != request.expectedEntitlementRevision()) throw conflict(REVISION_CONFLICT);
        Map<Setting, JsonNode> changedSettings = new java.util.LinkedHashMap<>();
        changedSettings.put(Setting.DEPLOYMENT_NAME, TextNode.valueOf(request.name()));
        if (request.aiStateStorageMode() != null) {
            try {
                com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode.valueOf(request.aiStateStorageMode());
            } catch (RuntimeException error) { throw invalid(); }
            changedSettings.put(Setting.AI_STATE_STORAGE_MODE, TextNode.valueOf(request.aiStateStorageMode()));
        }
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            request.targetProfileId(), null, null, null, Operation.BOOTSTRAP_CONFIGURATION,
            request.operationKey(), changedSettings.keySet(), null, null, null));
        authorization.validateSettings(approved, new SettingsPatch(changedSettings));
        var definition = approved.definition();
        boolean storageChoiceRequired = definition.settings().stream()
            .anyMatch(rule -> rule.setting() == Setting.AI_STATE_STORAGE_MODE);
        if (storageChoiceRequired != (request.aiStateStorageMode() != null)) throw invalid();
        var pin = definition.template();
        var version = versions.findById(pin.versionId()).orElseThrow(() -> conflict(REVIEWED_COMPOSITION_CHANGED));
        if (!pin.pluginId().equals(version.getPluginId()) || !"PUBLISHED".equals(version.getStatus())) {
            throw conflict(REVIEWED_COMPOSITION_CHANGED);
        }
        var created = templates.bootstrapConfiguration(pin.pluginId(), new CreateMarketplaceTemplateBootstrapRequest(
            version.getVersion(), request.name(), "staging", definition.templateId(), null, null, null));
        var deployment = deployments.findById(created.id()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!actor.customerId().equals(deployment.getCustomerId()) || !"DRAFT".equals(deployment.getStatus())
            || !"staging".equals(deployment.getEnvironmentName()) || deployment.getActiveVersionId() != null) {
            throw deny(RESOURCE_SCOPE_MISMATCH);
        }
        var installed = installs.findByDeploymentIdOrderByUpdatedAtDesc(created.id());
        var template = installed.stream().filter(install -> pin.pluginId().equals(install.getPluginId())).toList();
        if (template.size() != 1 || !"BOOTSTRAPPED".equals(template.getFirst().getStatus())) {
            throw conflict(REVIEWED_COMPOSITION_CHANGED);
        }
        authorization.requirePinnedComposition(approved, template.getFirst().getPluginVersionId(),
            installed.stream().filter(install -> !pin.pluginId().equals(install.getPluginId()))
                .map(install -> install.getPluginVersionId()).collect(Collectors.toSet()),
            definition.artifacts().stream().map(ArtifactPin::artifactId).collect(Collectors.toSet()));
        if (request.aiStateStorageMode() != null) storage.initialize(deployment, request.aiStateStorageMode());
        authorization.registerBootstrap(approved, deployment.getId());
        if (!definition.externalIntegrationProfiles().isEmpty()) {
            externalInstalls.initialize(deployment);
            compiler.syncDeploymentDraftForTrustedTemplateBootstrap(deployment.getId());
        }
        var result = new Result(deployment.getId(), deployment.getTenantId(), offerId,
            approved.context().offerFingerprint(), request.targetProfileId(), grant.getRevision(), "CONFIGURATION_ONLY",
            request.aiStateStorageMode());
        commands.saveAndFlush(new CustomerEntitlementCommandEntity("cec-" + UUID.randomUUID(), grant.getId(),
            request.operationKey(), fingerprint, COMMAND, actorId, codec.write(result), Instant.now()));
        audit.record("CUSTOMER_OFFER_CONFIGURATION_BOOTSTRAPPED", "DEPLOYMENT", deployment.getId(),
            Map.of("offerId", offerId, "customerId", actor.customerId(), "targetProfileId", request.targetProfileId(),
                "operationKey", request.operationKey(), "creationStatus", result.creationStatus()));
        return result;
    }
}
