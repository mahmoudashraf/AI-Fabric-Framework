package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.marketplace.model.*;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.repository.PlatformCustomerIdentityLinkRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class DeskActivationService {
    public static final String PLUGIN = "mkp-integration-loomai-desk";
    private final DeskBindingRepository bindings;
    private final DeploymentTargetContextService targets;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentDraftRepository drafts;
    private final DeploymentAccessService access;
    private final DeploymentTargetProfileRepository profiles;
    private final DeploymentService deploymentService;
    private final DeploymentMarketplaceInstallService installs;
    private final DeploymentMarketplaceDraftCompilerService compiler;
    private final MarketplacePluginVersionRepository pluginVersions;
    private final PlatformCustomerIdentityLinkRepository identities;
    private final PlatformSecretService secrets;
    private final DeskServiceClient desk;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;
    private final DeskBindingProvisioner bindingProvisioner;

    public DeskActivationService(DeskBindingRepository bindings, DeploymentTargetContextService targets,
        DeploymentRepository deployments, DeploymentReleaseRepository releases, DeploymentVersionRepository versions,
        DeploymentDraftRepository drafts,
        DeploymentService deploymentService, DeploymentAccessService access, DeploymentTargetProfileRepository profiles,
        DeploymentMarketplaceInstallService installs,
        DeploymentMarketplaceDraftCompilerService compiler, MarketplacePluginVersionRepository pluginVersions,
        PlatformCustomerIdentityLinkRepository identities, PlatformSecretService secrets, DeskServiceClient desk,
        PlatformAuditService audit, ObjectMapper mapper, DeskBindingProvisioner bindingProvisioner) {
        this.bindings = bindings; this.targets = targets; this.deployments = deployments; this.releases = releases;
        this.versions = versions; this.drafts = drafts; this.deploymentService = deploymentService; this.access = access;
        this.profiles = profiles; this.installs = installs;
        this.compiler = compiler; this.pluginVersions = pluginVersions; this.identities = identities;
        this.secrets = secrets; this.desk = desk; this.audit = audit; this.mapper = mapper; this.bindingProvisioner = bindingProvisioner;
    }

    @Transactional
    public Status status(String deploymentId, String targetId, String releaseId) {
        requireOwner();
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        access.requireDeploymentAccess(deployment);
        var binding = bindings.findByDeploymentIdAndTargetProfileId(deploymentId, targetId).orElse(null);
        if (binding == null) {
            var context = targets.resolve(deploymentId, targetId, releaseId, false);
            return new Status(configured(context.target().getEnvironmentName()) != null, null, "NOT_ENABLED", null, null, null, null);
        }
        var target = profiles.findById(targetId).filter(value -> value.isActive())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Desk target is unavailable."));
        var connection = configured(target.getEnvironmentName());
        requireBindingOwner(binding, deployment);
        if (binding.getReleaseId() != null) {
            var applied = releases.findById(binding.getReleaseId()).orElseThrow();
            if ("APPLIED_VERIFIED".equals(applied.getStatus()) && "PASSED".equals(applied.getVerificationStatus())) {
                binding.setStatus("ENABLED"); binding.setErrorMessage(null);
            } else if (Set.of("FAILED", "APPLIED_VERIFICATION_FAILED").contains(applied.getStatus())) {
                binding.setStatus("FAILED"); binding.setErrorMessage("The Desk configuration did not pass deployment verification. Open Versions & updates for details.");
            }
            bindings.save(binding);
        }
        return summary(binding, connection);
    }

    @Transactional
    public Status enable(String deploymentId, EnableRequest request) {
        PlatformPrincipal principal = requireOwner();
        if (request == null || request.operationKey() == null
            || !request.operationKey().matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new ResponseStatusException(BAD_REQUEST, "A valid operationKey is required.");
        }
        var context = targets.resolve(deploymentId, request.targetProfileId(), request.releaseId(), true);
        var existing = bindings.findByDeploymentIdAndTargetProfileId(deploymentId, request.targetProfileId());
        if (existing.isPresent()) {
            requireBindingOwner(existing.get(), context.deployment());
            if (Set.of("PREPARING", "APPLYING").contains(existing.get().getStatus())) {
                return summary(existing.get(), desk.connection(context.target().getEnvironmentName()));
            }
            try {
                if ("ENABLED".equals(existing.get().getStatus())
                    && DeskTargetConfigurationResolver.isTargetIndependent(mapper.readTree(context.version().getBehaviorConfigJson()))
                    && (!request.enableReviews() || hasExtension(mapper.readTree(context.version().getBehaviorConfigJson()), "HUMAN_REVIEW"))) {
                    return summary(existing.get(), desk.connection(context.target().getEnvironmentName()));
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                throw new ResponseStatusException(CONFLICT, "The selected target configuration is unavailable.");
            }
        }
        var connection = desk.connection(context.target().getEnvironmentName());
        identities.findByPlatformUserId(principal.platformUserId())
            .filter(value -> value.getRevokedAt() == null).orElseThrow(() ->
                new ResponseStatusException(FORBIDDEN, "Link your customer Supabase identity before enabling LoomAI Desk."));
        var draft = deploymentService.getActiveDraftForDeployment(deploymentId);
        requireCleanDraft(deploymentId, draft);
        if (context.connectorBaseUrl() == null) throw new ResponseStatusException(CONFLICT, "The selected target has no conversation connector.");

        var binding = bindingProvisioner.prepareBinding(context.deployment(), context.target(), request.releaseId(), request.operationKey());
        bindingProvisioner.registerCallback(context.deployment(), context.target(), context.connectorBaseUrl());

        var currentInstalls = installs.listInstallsForTrustedCaller(context.deployment());
        for (var install : currentInstalls) {
            if (!PLUGIN.equals(install.pluginId()) && install.contributions() != null && install.contributions().conversationChannel() != null) {
                installs.updateInstallForTrustedCallerWithoutDraftSync(context.deployment(), install.id(),
                    new UpdateDeploymentMarketplaceInstallRequest(null, "DISABLED", null, null));
            }
            if ("TEMPLATE".equals(install.pluginType()) && "BOOTSTRAPPED".equals(install.status())) {
                var replacement = pluginVersions.findByPluginIdOrderByPublishedAtDesc(install.pluginId()).stream()
                    .filter(value -> value.getId().startsWith("mkv-desk-") && "PUBLISHED".equals(value.getStatus()))
                    .findFirst();
                if (replacement.isPresent()) installs.updateInstallForTrustedCallerWithoutDraftSync(context.deployment(), install.id(),
                    new UpdateDeploymentMarketplaceInstallRequest(replacement.get().getVersion(), null, null, null));
            }
        }
        if (currentInstalls.stream().noneMatch(value -> "mkp-action-conversation-handover".equals(value.pluginId()))) {
            installs.createInstallForTrustedCallerWithoutDraftSync(context.deployment(),
                new CreateDeploymentMarketplaceInstallRequest("mkp-action-conversation-handover", "1.1.0", mapper.createObjectNode(), mapper.createObjectNode()));
        }
        ObjectNode config = mapper.createObjectNode().put("bindingId", DeskTargetConfigurationResolver.BINDING)
            .put("providerBaseUrl", DeskTargetConfigurationResolver.PROVIDER).put("operatorConsoleUrl", DeskTargetConfigurationResolver.CONSOLE);
        ObjectNode references = mapper.createObjectNode().put("providerApiKeySecretRef", DeskTargetConfigurationResolver.PROVIDER_KEY)
            .put("runtimeCallbackSigningSecretRef", DeskTargetConfigurationResolver.RUNTIME_CALLBACK_KEY)
            .put("providerWebhookSigningSecretRef", DeskTargetConfigurationResolver.PROVIDER_CALLBACK_KEY);
        var deskInstall = currentInstalls.stream().filter(value -> PLUGIN.equals(value.pluginId())).findFirst();
        if (deskInstall.isPresent()) {
            installs.updateInstallForTrustedCallerWithoutDraftSync(context.deployment(), deskInstall.get().id(),
                new UpdateDeploymentMarketplaceInstallRequest(null, "ENABLED", config, references));
        } else {
            installs.createInstallForTrustedCallerWithoutDraftSync(context.deployment(),
                new CreateDeploymentMarketplaceInstallRequest(PLUGIN, "1.0.0", config, references));
        }
        Set<String> extensions = request.enableReviews()
            ? Set.of("CONVERSATION_HANDOVER", "HUMAN_REVIEW") : Set.of("CONVERSATION_HANDOVER");
        var compiled = compiler.syncDeploymentDraftForTrustedCaller(deploymentId, extensions);
        var version = deploymentService.publishDraft(compiled.id());
        binding.setVersionId(version.id());
        var release = deploymentService.applyVersion(deploymentId, version.id(), null,
            request.targetProfileId(), context.release().getSourceArtifactId(), request.operationKey());
        binding.setReleaseId(release.id()); binding.setStatus("APPLYING"); binding.setUpdatedAt(Instant.now());
        bindings.save(binding);
        audit.record("DESK_ACTIVATION_SUBMITTED", "DEPLOYMENT", deploymentId,
            Map.of("bindingId", binding.getId(), "targetProfileId", request.targetProfileId(), "releaseId", release.id()));
        return summary(binding, connection);
    }

    public DeskBindingEntity requireBridgeBinding(String bindingId, String suppliedKey) {
        var binding = bindings.findById(bindingId).filter(value -> Set.of("APPLYING", "ENABLED").contains(value.getStatus()))
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Desk binding is unavailable."));
        String expected = secrets.resolveSecret(binding.getBridgeKeyRef());
        if (expected == null || suppliedKey == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
            suppliedKey.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(UNAUTHORIZED, "Invalid Desk integration credential.");
        var deployment = deployments.findById(binding.getDeploymentId()).orElseThrow();
        requireBindingOwner(binding, deployment);
        if (deployment.getArchivedAt() != null) throw new ResponseStatusException(CONFLICT, "Desk deployment is archived.");
        return binding;
    }

    public DeploymentReleaseEntity currentReviewRelease(DeskBindingEntity binding) {
        var current = releases.findByDeploymentIdOrderByCreatedAtDesc(binding.getDeploymentId()).stream()
            .filter(value -> binding.getTargetProfileId().equals(value.getTargetProfileId())
                && "ACTIVE".equals(value.getProvisioningStatus()))
            .findFirst().orElseThrow(() -> new ResponseStatusException(CONFLICT, "Desk requires a verified deployment target."));
        if (!"APPLIED_VERIFIED".equals(current.getStatus()) || !"PASSED".equals(current.getVerificationStatus())) {
            throw new ResponseStatusException(CONFLICT, "Desk requires the current deployment target to pass verification.");
        }
        var version = versions.findById(current.getDeploymentVersionId()).orElseThrow();
        try {
            if (!DeskTargetConfigurationResolver.isTargetIndependent(mapper.readTree(version.getBehaviorConfigJson()))) {
                throw new ResponseStatusException(CONFLICT, "Desk is not enabled in the target's current verified configuration.");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new ResponseStatusException(CONFLICT, "The current review configuration is unavailable.");
        }
        return current;
    }

    private PlatformPrincipal requireOwner() {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (!CustomerSessionSupport.isCustomerSession(principal) || !"SUPABASE".equals(principal.authenticationMode())
            || principal.platformUserId() == null) throw new ResponseStatusException(FORBIDDEN, "A Supabase-authenticated customer owner is required.");
        return principal;
    }

    private void requireBindingOwner(DeskBindingEntity binding, DeploymentEntity deployment) {
        if (!Objects.equals(binding.getCustomerId(), deployment.getCustomerId())
            || !Objects.equals(binding.getTenantId(), deployment.getTenantId())) {
            throw new ResponseStatusException(FORBIDDEN, "Desk binding ownership no longer matches the deployment.");
        }
    }

    private void requireCleanDraft(String deploymentId, com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse draft) {
        var latest = versions.findByDeploymentIdOrderByPublishedAtDesc(deploymentId).stream()
            .filter(value -> "PUBLISHED".equals(value.getStatus())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Publish the deployment configuration before enabling Desk."));
        // Publication compiles prompts; compare original draft inputs, not generated artifacts or another target's old version.
        var published = drafts.findById(latest.getSourceDraftId())
            .filter(value -> deploymentId.equals(value.getDeploymentId()) && "PUBLISHED".equals(value.getStatus()))
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "The published configuration inputs are unavailable."));
        List<JsonNode> saved = List.of(draft.actionsConfig(), draft.entityConfig(), draft.routingConfig(), draft.providerConfig(),
            draft.securityConfig(), draft.promptConfig(), draft.knowledgeSourceConfig(), draft.shellConfig(), draft.marketplaceDatasetConfig(), draft.behaviorConfig());
        List<String> applied = List.of(published.getActionsConfigJson(), published.getEntityConfigJson(), published.getRoutingConfigJson(),
            published.getProviderConfigJson(), published.getSecurityConfigJson(), published.getPromptConfigJson(), published.getKnowledgeSourceConfigJson(),
            published.getShellConfigJson(), published.getMarketplaceDatasetConfigJson(), published.getBehaviorConfigJson());
        for (int index = 0; index < saved.size(); index++) {
            try {
                if (!saved.get(index).equals(mapper.readTree(applied.get(index)))) {
                    throw new ResponseStatusException(CONFLICT, "This deployment has unrelated draft edits. Review them in Versions & updates before enabling Desk.");
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                throw new ResponseStatusException(CONFLICT, "The saved deployment configuration cannot be compared safely.");
            }
        }
    }

    private boolean hasExtension(JsonNode behavior, String extension) {
        for (var item : behavior.path("executionExtensions")) if (extension.equals(item.asText())) return true;
        return false;
    }

    private DeskServiceClient.Connection configured(String environment) {
        try { return desk.connection(environment); }
        catch (ResponseStatusException error) { if (error.getStatusCode().value() == 503) return null; throw error; }
    }

    private Status summary(DeskBindingEntity binding, DeskServiceClient.Connection connection) {
        return new Status(connection != null, binding.getId(), binding.getStatus(), binding.getVersionId(), binding.getReleaseId(),
            connection == null ? null : portal(connection, binding.getId()), binding.getErrorMessage());
    }

    private String portal(DeskServiceClient.Connection connection, String binding) { return connection.baseUrl() + "/?bindingId=" + binding; }
    public record EnableRequest(String targetProfileId, String releaseId, String operationKey, boolean enableReviews) { }
    public record Status(boolean available, String bindingId, String status, String versionId, String releaseId,
                         String portalUrl, String errorMessage) { }
}
