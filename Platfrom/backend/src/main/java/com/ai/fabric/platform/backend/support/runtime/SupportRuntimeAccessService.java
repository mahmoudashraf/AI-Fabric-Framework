package com.ai.fabric.platform.backend.support.runtime;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssignmentService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssetCatalogService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConfigurationValidator;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConnectionProfileRegistry;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.support.SupportCaseService;
import com.ai.fabric.platform.backend.support.SupportContracts.OwnedCase;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Case authorization precedes access to the ordinary Engage assignment and exact target keys. */
@Service
public class SupportRuntimeAccessService {
    private final SupportCaseService cases;
    private final AIWorkspaceInstallationRepository installations;
    private final PlatformConsumerRepository consumers;
    private final AIWorkspaceAssignmentService assignments;
    private final AIWorkspaceAssetCatalogService assets;
    private final DeploymentRuntimeAuthorityService authority;
    private final Environment environment;
    private final ObjectMapper mapper;
    private final AIWorkspaceConfigurationValidator configurationValidator;

    public SupportRuntimeAccessService(SupportCaseService cases, AIWorkspaceInstallationRepository installations,
        PlatformConsumerRepository consumers, AIWorkspaceAssignmentService assignments, AIWorkspaceAssetCatalogService assets,
        DeploymentRuntimeAuthorityService authority, Environment environment, ObjectMapper mapper,
        AIWorkspaceConfigurationValidator configurationValidator) {
        this.cases = cases; this.installations = installations; this.consumers = consumers; this.assignments = assignments;
        this.assets = assets; this.authority = authority; this.environment = environment; this.mapper = mapper;
        this.configurationValidator = configurationValidator;
    }

    public Access resolve(String caseId) {
        OwnedCase owned = cases.requireOwnedCase(caseId);
        String installationId = environment.getProperty("platform.support.installation-id", "");
        AIWorkspaceInstallationEntity installation = installations.findByInstallationId(installationId)
            .filter(value -> value.getStatus() == AIWorkspaceInstallationStatus.ACTIVE
                && owned.customerId().equals(value.getCustomerId())
                && value.getConnectionMode() == AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME
                && AIWorkspaceConnectionProfileRegistry.SUPPORT_CASE_HOST.equals(value.getConnectionProfileCode())
                && AIWorkspaceConnectionProfileRegistry.VERSION.equals(value.getConnectionProfileVersion()))
            .orElseThrow(SupportRuntimeAccessService::unavailable);
        var consumer = consumers.findById(installation.getConsumerEntityId()).orElseThrow(SupportRuntimeAccessService::unavailable);
        var assignment = assignments.resolveCurrent(consumer);
        if (!owned.deploymentId().equals(assignment.deployment().getId())
            || !owned.targetProfileId().equals(assignment.release().getTargetProfileId())
            || !owned.tenantId().equals(assignment.deployment().getTenantId())) throw unavailable();
        JsonNode behavior = json(assignment.version().getBehaviorConfigJson());
        var scopes = new LinkedHashSet<>(List.of("chat:query", "chat:conversations", "chat:messages", "chat:shell",
            "chat:auth-context", "chat:handover:request", "chat:handover:control", "chat:handover:read", "support:diagnostics:read"));
        // These are deployed, reviewed capabilities; model parameters never add authority.
        if (behavior.path("specialistAssistance").path("enabled").asBoolean(false)) {
            behavior.path("specialistAssistance").path("compiled").path("invocations").forEach(invocation ->
                invocation.path("requiredScopes").forEach(scope -> scopes.add(scope.asText())));
        }
        var snapshot = authority.requireReleaseUnchangedForProvisioning(assignment.deployment(), assignment.version(), assignment.release());
        if (!owned.assertionIssuer().equals(snapshot.binding().privateIssuer())
            || !owned.assertionAudience().equals(snapshot.binding().privateAudience())) throw unavailable();
        var headers = authority.issueReviewedIntegrationSessionHeaders(assignment.deployment(), assignment.version(), assignment.release(),
            owned.principalId(), List.copyOf(scopes));
        return new Access(owned, installation, assignment, behavior, headers);
    }

    public Bootstrap bootstrap(String caseId) {
        var access = resolve(caseId);
        var configuration = json(access.installation().getConfigurationJson());
        configurationValidator.validatePublicConfiguration(configuration);
        var workspace = assets.workspace();
        var pack = assets.experiencePack(access.installation().getExperiencePackCode(), access.installation().getExperiencePackVersion());
        var handover = mapper.createObjectNode();
        var configured = access.behavior().path("conversationHandover");
        for (String field : List.of("enabled", "contractVersion", "modelInitiatedPolicy", "userCanCancelPending", "userCanResumeAi", "queueTimeoutSeconds", "intake")) {
            if (configured.has(field)) handover.set(field, configured.path(field));
        }
        return new Bootstrap(access.owned().conversationId(), "support:" + caseId + ":" + access.owned().episodeId(),
            "/api/support/cases/" + caseId + "/runtime/api", access.installation().getInstallationId(),
            workspace.requestPath(), workspace.integrity(), pack.requestPath(), pack.integrity(),
            access.owned().reference(), access.owned().status().name(), access.owned().initialMessageDelivery().name(),
            access.assignment().assignmentRevision(), configuration, handover);
    }

    private JsonNode json(String value) {
        try { return mapper.readTree(value); }
        catch (Exception failure) { throw unavailable(); }
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SUPPORT_WORKSPACE_CONFIGURATION_UNAVAILABLE");
    }
    public record Access(OwnedCase owned, AIWorkspaceInstallationEntity installation,
        AIWorkspaceAssignmentService.Assignment assignment, JsonNode behavior, Map<String, String> headers) { }
    public record Bootstrap(String conversationId, String principalNamespace, String apiBaseUrl, String installationId,
        String workspaceAssetUrl, String workspaceIntegrity, String experienceModuleUrl, String experienceIntegrity,
        String reference, String status, String initialMessageDelivery, String assignmentRevision,
        JsonNode configuration, JsonNode conversationHandover) { }
}
