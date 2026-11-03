package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssignmentService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAuthenticationProfileRegistry;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceSupportReadinessAdapter;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.deployment.service.StandaloneConversationRuntimeArtifactGate;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.*;

/** External Engage requester setup. Console private-host identity remains a separate trusted adapter. */
@Service
public class DeskSupportSpaceService implements AIWorkspaceSupportReadinessAdapter {
    private final AIWorkspaceInstallationRepository installations;
    private final PlatformConsumerRepository consumers;
    private final AIWorkspaceAssignmentService assignments;
    private final DeploymentTargetContextService targets;
    private final PlatformCustomerAccessService access;
    private final DeskBindingRepository bindings;
    private final DeskBindingProvisioner provisioner;
    private final DeskServiceClient desk;
    private final AIWorkspaceAuthenticationProfileRegistry authentication;
    private final EntityManager entityManager;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;
    private final DeploymentRuntimeAuthorityService authority;
    private final StandaloneConversationRuntimeArtifactGate canonicalConversations;

    public DeskSupportSpaceService(AIWorkspaceInstallationRepository installations, PlatformConsumerRepository consumers,
            AIWorkspaceAssignmentService assignments, DeploymentTargetContextService targets,
            PlatformCustomerAccessService access, DeskBindingRepository bindings, DeskBindingProvisioner provisioner,
            DeskServiceClient desk, AIWorkspaceAuthenticationProfileRegistry authentication, EntityManager entityManager,
            PlatformAuditService audit, ObjectMapper mapper, DeploymentRuntimeAuthorityService authority,
            StandaloneConversationRuntimeArtifactGate canonicalConversations) {
        this.installations = installations; this.consumers = consumers; this.assignments = assignments;
        this.targets = targets; this.access = access; this.bindings = bindings; this.provisioner = provisioner;
        this.desk = desk; this.authentication = authentication; this.entityManager = entityManager;
        this.audit = audit; this.mapper = mapper;
        this.authority = authority;
        this.canonicalConversations = canonicalConversations;
    }

    @Transactional(readOnly = true)
    public Status status(String customerId, String installationId, String targetProfileId, String releaseId) {
        var installation = owned(customerId, installationId);
        var current = assignment(installation, false);
        var assignment = selected(current, targetProfileId, releaseId);
        requireVerified(targets.resolveOwnedRead(assignment.deployment().getId(), assignment.release().getTargetProfileId(),
            assignment.release().getId(), installation.getCustomerId(), assignment.deployment().getTenantId()));
        var binding = binding(assignment).orElse(null);
        if (binding == null) return new Status("NOT_CONFIGURED", current.assignmentRevision(), null);
        var space = desk.getOptional(connection(assignment), path(binding.getId())).orElse(null);
        if (space == null) return new Status("NOT_CONFIGURED", current.assignmentRevision(), null);
        validateSpace(installation, assignment, binding, space, false);
        return new Status("CONFIGURED", current.assignmentRevision(), safeSpace(space));
    }

    // Retain prepared target credentials after an uncertain remote write; a readback/conditional PUT reconciles it.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Status configure(String customerId, String installationId, ConfigureRequest request) {
        var installation = owned(customerId, installationId);
        entityManager.lock(installation, LockModeType.PESSIMISTIC_WRITE);
        if (request == null || request.installationRevision() == null || request.installationRevision() != installation.getRowVersion()
            || request.operationKey() == null || !request.operationKey().matches("[A-Za-z0-9._:-]{1,128}"))
            throw conflict("Reload this Support installation before configuring its managed support space.");
        if (!"desk".equals(request.ticketProvider())) throw conflict("Select the registered Desk ticket provider.");
        var current = assignment(installation, true);
        if (!Objects.equals(request.assignmentRevision(), current.assignmentRevision()))
            throw conflict("The assigned release changed. Reload this Support installation.");
        var assignment = selected(current, request.targetProfileId(), request.releaseId());
        var context = targets.resolve(assignment.deployment().getId(), assignment.release().getTargetProfileId(),
            assignment.release().getId(), true);
        requireVerified(context);
        var identity = identity(installation, assignment);
        if (context.connectorBaseUrl() == null || context.connectorBaseUrl().isBlank())
            throw conflict("The assigned target requires its resolved Desk conversation connector.");
        var connection = connection(assignment);
        var previousBinding = binding(assignment).orElse(null);
        if (previousBinding != null) entityManager.refresh(previousBinding, LockModeType.PESSIMISTIC_WRITE);
        if (request.humanHandoverEnabled()) {
            if (previousBinding == null) throw conflict("Enable the assigned target's Desk channel before human support.");
            requireHuman(assignment, previousBinding);
        }
        var previous = previousBinding == null ? null : desk.getOptional(connection, path(previousBinding.getId())).orElse(null);
        if (previous != null) {
            validateSpace(installation, assignment, previousBinding, previous, false);
            if (!Objects.equals(request.expectedVersion(), previous.path("version").asText()))
                throw conflict("The support space changed. Refresh its opaque version before saving.");
        } else if (request.expectedVersion() != null) throw conflict("The support space is not configured yet.");
        var scope = scope(installation, assignment);
        int retention = request.closedRetentionDays() == null
            ? previous == null ? 30 : previous.path("closedRetentionDays").asInt(30) : request.closedRetentionDays();
        if (retention < 1 || retention > 3650) throw conflict("Closed-ticket retention must be between 1 and 3650 days.");
        var binding = provisioner.prepareSupportBinding(context.deployment(), context.target(), context.release().getId(),
            request.operationKey(), context.connectorBaseUrl());
        ObjectNode body = mapper.createObjectNode().put("spaceId", binding.getId()).put("name", context.deployment().getName())
            .put("enabled", request.enabled()).put("humanHandoverEnabled", request.humanHandoverEnabled())
            .put("providerId", "desk").put("providerBindingId", binding.getId()).put("runtimeBaseUrl", context.runtimeBaseUrl())
            .put("runtimeTrustedKeyHeader", RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER)
            .put("connectorKey", provisioner.ticketConnectorKey(binding))
            .put("closedRetentionDays", retention);
        if (request.expectedVersion() == null) body.putNull("expectedVersion");
        else body.put("expectedVersion", request.expectedVersion());
        body.set("identity", identity); body.set("allowedOrigins", mapper.valueToTree(scope.origins()));
        body.set("topics", mapper.valueToTree(scope.topics()));
        body.set("serverConnector", serverConnector(assignment));
        var registered = desk.put(connection, path(binding.getId()), body);
        validateSpace(installation, assignment, binding, registered, false);
        if (registered.path("enabled").asBoolean() != request.enabled()
            || registered.path("humanHandoverEnabled").asBoolean() != request.humanHandoverEnabled())
            throw new ResponseStatusException(BAD_GATEWAY, "Desk did not confirm the requested support features.");
        ObjectNode receipt = mapper.createObjectNode().put("schemaVersion", "loomai-desk-support-registration-v1")
            .put("customerId", binding.getCustomerId()).put("tenantId", binding.getTenantId())
            .put("deploymentId", binding.getDeploymentId()).put("targetProfileId", binding.getTargetProfileId())
            .put("runtimeBaseUrl", context.runtimeBaseUrl()).put("requesterOrigin", connection.baseUrl());
        ObjectNode snapshot = safeSpace(registered);
        snapshot.set("identity", mapper.createObjectNode().put("mode", "SUPABASE")
            .put("issuer", identity.path("issuer").asText()).put("audience", identity.path("audience").asText())
            .put("requesterNamespace", identity.path("requesterNamespace").asText()));
        receipt.set("space", snapshot);
        binding.setSupportRegistrationJson(receipt.toString()); binding.setUpdatedAt(java.time.Instant.now());
        bindings.saveAndFlush(binding);
        audit.record("SUPPORT_SPACE_CONFIGURED", "AI_WORKSPACE_INSTALLATION", installationId, Map.of(
            "customerId", customerId, "deploymentId", assignment.deployment().getId(),
            "targetProfileId", assignment.release().getTargetProfileId(), "bindingId", binding.getId(),
            "ticketingEnabled", request.enabled(), "humanHandoverEnabled", request.humanHandoverEnabled()));
        return new Status("CONFIGURED", current.assignmentRevision(), safeSpace(registered));
    }

    /** No public-config ID or URL can select requester authority or a remote destination. */
    public ObjectNode publicConfiguration(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment) {
        return configuration(installation, assignment, false);
    }

    private ObjectNode configuration(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment,
            boolean probeProvider) {
        if (!Objects.equals(installation.getCustomerId(), assignment.deployment().getCustomerId())
            || !Objects.equals(installation.getConsumerEntityId(), assignment.consumer().getId()))
            throw conflict("The support installation does not own this assignment.");
        ObjectNode config = json(installation.getConfigurationJson()).deepCopy();
        ObjectNode support = config.with("support");
        support.remove(List.of("spaceId", "ticketProviderBindingId", "requesterOrigin"));
        boolean tickets = config.path("capabilities").path("tickets").asBoolean(false);
        boolean human = config.path("capabilities").path("handover").asBoolean(false);
        if (!human) support.put("teamVisible", false);
        if (!tickets && !human) return config;
        var context = targets.resolveOwnedRead(assignment.deployment().getId(), assignment.release().getTargetProfileId(),
            assignment.release().getId(), installation.getCustomerId(), assignment.deployment().getTenantId());
        requireVerified(context);
        if (!Objects.equals(context.runtimeBaseUrl(), assignment.runtimeBaseUrl()))
            throw conflict("The assigned support runtime endpoint changed.");
        var binding = binding(assignment).orElseThrow(() -> conflict("Configure the assigned target's managed support space."));
        JsonNode space;
        String requesterOrigin;
        if (probeProvider) {
            var connection = connection(assignment);
            space = desk.getOptional(connection, path(binding.getId()))
                .orElseThrow(() -> conflict("Configure the assigned target's managed support space."));
            requesterOrigin = connection.baseUrl();
            validateSpace(installation, assignment, binding, space, true);
        } else {
            var receipt = json(binding.getSupportRegistrationJson());
            if (!"loomai-desk-support-registration-v1".equals(receipt.path("schemaVersion").asText())
                || !binding.getCustomerId().equals(receipt.path("customerId").asText())
                || !binding.getTenantId().equals(receipt.path("tenantId").asText())
                || !binding.getDeploymentId().equals(receipt.path("deploymentId").asText())
                || !binding.getTargetProfileId().equals(receipt.path("targetProfileId").asText())
                || !assignment.runtimeBaseUrl().equals(receipt.path("runtimeBaseUrl").asText()))
                throw conflict("Register this verified target before publishing its support references.");
            space = receipt.path("space"); requesterOrigin = receipt.path("requesterOrigin").asText();
            try {
                var uri = java.net.URI.create(requesterOrigin);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty()) throw new IllegalArgumentException();
            } catch (IllegalArgumentException error) { throw conflict("The registered requester origin is unavailable."); }
            validateSpace(installation, assignment, binding, space, false);
            validateRequestedFeatures(installation, space);
        }
        support.put("spaceId", binding.getId()).put("ticketProviderBindingId", space.path("providerBindingId").asText())
            .put("requesterOrigin", requesterOrigin);
        return config;
    }

    @Override public boolean supports(AIWorkspaceInstallationEntity installation) {
        return "support".equals(installation.getExperiencePackCode())
            && installation.getConnectionMode() != AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME;
    }

    @Override public void validate(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment) {
        configuration(installation, assignment, true);
    }

    @Override public void validatePublishedProjection(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment) {
        publicConfiguration(installation, assignment);
    }

    private ObjectNode serverConnector(AIWorkspaceAssignmentService.Assignment assignment) {
        var frozen = authority.requireReleaseUnchangedForRead(assignment.deployment(), assignment.version(), assignment.release()).binding();
        var profile = mapper.createObjectNode().put("authMode", "PUBLIC_RUNTIME_AUTHENTICATED").put("callerType", "PUBLIC_BROWSER")
            .put("issuer", frozen.publicIssuer()).put("audience", frozen.publicAudience());
        profile.set("requiredScopes", mapper.valueToTree(List.of("chat:conversations", "chat:messages")));
        return profile;
    }

    private AIWorkspaceInstallationEntity owned(String customer, String installationId) {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (!CustomerSessionSupport.isCustomerSession(principal) || principal.platformUserId() == null
            || !"SUPABASE".equals(principal.authenticationMode()))
            throw new ResponseStatusException(FORBIDDEN, "A signed-in customer owner is required.");
        access.requireCustomerManagementAccess(customer);
        var installation = installations.findByCustomerIdAndInstallationId(customer, installationId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Support installation not found."));
        if (!"support".equals(installation.getExperiencePackCode()) || !"1.0.0".equals(installation.getExperiencePackVersion()))
            throw conflict("Select a current Support experience installation.");
        return installation;
    }

    private AIWorkspaceAssignmentService.Assignment assignment(AIWorkspaceInstallationEntity installation, boolean lock) {
        var consumer = consumers.findById(installation.getConsumerEntityId())
            .filter(c -> installation.getCustomerId().equals(c.getCustomerId()))
            .orElseThrow(() -> conflict("The owned installation consumer is unavailable."));
        if (lock) consumer = consumers.findBindingForUpdate(installation.getCustomerId(), consumer.getConsumerId())
            .filter(c -> installation.getConsumerEntityId().equals(c.getId()) && installation.getCustomerId().equals(c.getCustomerId()))
            .orElseThrow(() -> conflict("The installation assignment is unavailable."));
        try { return assignments.resolveCurrent(consumer); }
        catch (IllegalStateException error) { throw conflict("Assign a verified release before configuring support."); }
    }

    private Optional<DeskBindingEntity> binding(AIWorkspaceAssignmentService.Assignment assignment) {
        var result = bindings.findByDeploymentIdAndTargetProfileId(assignment.deployment().getId(), assignment.release().getTargetProfileId());
        result.ifPresent(binding -> {
            if (!Objects.equals(binding.getCustomerId(), assignment.deployment().getCustomerId())
                || !Objects.equals(binding.getTenantId(), assignment.deployment().getTenantId())
                || !Objects.equals(binding.getDeploymentId(), assignment.deployment().getId())
                || !Objects.equals(binding.getTargetProfileId(), assignment.release().getTargetProfileId())
                || !Objects.equals(binding.getId(), DeskBindingProvisioner.projection(assignment.deployment(), binding.getTargetProfileId()).getId()))
                throw conflict("The support binding does not belong to the assigned deployment target.");
        });
        return result;
    }

    private AIWorkspaceAssignmentService.Assignment selected(AIWorkspaceAssignmentService.Assignment current,
            String targetId, String releaseId) {
        if (targetId == null && releaseId == null) return current;
        if (targetId == null || targetId.isBlank() || releaseId == null || releaseId.isBlank())
            throw conflict("Select both the verified target and release for support preparation.");
        AIWorkspaceAssignmentService.Assignment selected;
        try { selected = assignments.resolveTarget(current.consumer(), current.deployment().getId(), releaseId); }
        catch (IllegalStateException error) { throw conflict("Select a verified release belonging to this deployment."); }
        if (!targetId.equals(selected.release().getTargetProfileId())) throw conflict("The selected support release belongs to another target.");
        return selected;
    }

    private DeskServiceClient.Connection connection(AIWorkspaceAssignmentService.Assignment assignment) {
        var context = targets.resolveOwnedRead(assignment.deployment().getId(), assignment.release().getTargetProfileId(),
            assignment.release().getId(), assignment.deployment().getCustomerId(), assignment.deployment().getTenantId());
        requireVerified(context);
        return desk.connection(context.target().getEnvironmentName());
    }

    private ObjectNode identity(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment) {
        if (installation.getConnectionMode() == AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME
            || installation.getInteractionPolicy() == null || !installation.getInteractionPolicy().requiresAuthenticationProfile())
            throw conflict("External support requires the supported direct Supabase requester profile.");
        var profile = authentication.resolve(installation.getAuthProfileCode(), installation.getAuthProfileVersion(),
            installation.getInteractionPolicy(), json(installation.getAuthConfigurationJson()));
        if (!AIWorkspaceAuthenticationProfileRegistry.SUPABASE.equals(profile.code()))
            throw conflict("External support requires the supported Supabase requester profile.");
        String issuer = profile.publicConfiguration().path("projectUrl").asText() + "/auth/v1";
        String jwks = issuer + "/.well-known/jwks.json";
        var security = assignment.securityConfig();
        if (!ManagedDeploymentProfileCatalog.externalIdentityBrokerEnabled(security)
            || !"SUPABASE".equalsIgnoreCase(ManagedDeploymentProfileCatalog.externalIdentityProvider(security))
            || !issuer.equals(ManagedDeploymentProfileCatalog.externalIdentityIssuer(security))
            || !jwks.equals(ManagedDeploymentProfileCatalog.externalIdentityJwkSetUri(security))
            || !"authenticated".equals(ManagedDeploymentProfileCatalog.externalIdentityAudience(security)))
            throw conflict("The assigned runtime identity must match this installation's Supabase profile.");
        Set<String> scopes = new HashSet<>();
        Arrays.stream(ManagedDeploymentProfileCatalog.externalIdentityGrantedScopes(security).split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).forEach(scopes::add);
        if (!scopes.containsAll(List.of("chat:messages", "chat:conversations", "chat:handover:read")))
            throw conflict("Support conversation start, ownership and control reads require published chat:messages, chat:conversations and chat:handover:read scopes even with human handover disabled.");
        return mapper.createObjectNode().put("mode", "SUPABASE").put("issuer", issuer).put("jwksUri", jwks)
            .put("audience", "authenticated").put("requesterNamespace", namespace(assignment));
    }

    private void validateSpace(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment,
            DeskBindingEntity binding, JsonNode space, boolean requireFeatures) {
        var expected = identity(installation, assignment);
        if (!space.isObject() || !binding.getId().equals(space.path("spaceId").asText())
            || !binding.getId().equals(space.path("bindingId").asText())
            || !binding.getId().equals(space.path("providerBindingId").asText()) || !"desk".equals(space.path("providerId").asText())
            || !"SUPABASE".equals(space.at("/identity/mode").asText())
            || !expected.path("issuer").asText().equals(space.at("/identity/issuer").asText())
            || !expected.path("audience").asText().equals(space.at("/identity/audience").asText())
            || !expected.path("requesterNamespace").asText().equals(space.at("/identity/requesterNamespace").asText())
            || !space.path("version").isTextual() || !space.path("allowedOrigins").isArray()
            || !space.path("capabilities").isArray())
            throw conflict("The managed support space does not match the assigned target and requester identity.");
        if (!requireFeatures) return;
        validateRequestedFeatures(installation, space);
        if (json(installation.getConfigurationJson()).at("/capabilities/handover").asBoolean(false)) requireHuman(assignment, binding);
    }

    private void validateRequestedFeatures(AIWorkspaceInstallationEntity installation, JsonNode space) {
        var config = json(installation.getConfigurationJson());
        if (config.at("/capabilities/tickets").asBoolean(false)) {
            Set<String> caps = new HashSet<>(); space.path("capabilities").forEach(c -> caps.add(c.asText()));
            if (!space.path("enabled").asBoolean(false) || !caps.containsAll(List.of("CREATE", "LIST", "GET", "LINK_CONVERSATION")))
                throw conflict("The selected support provider is disabled or lacks required own-request operations.");
        }
        var allowed = new HashSet<String>(); space.path("allowedOrigins").forEach(o -> allowed.add(o.asText()));
        for (JsonNode origin : json(installation.getAllowedOriginsJson()))
            if (!allowed.contains(origin.asText())) throw conflict("Register this installation's exact origins in its managed support space.");
        if (config.at("/capabilities/handover").asBoolean(false)) {
            if (!space.path("humanHandoverEnabled").asBoolean(false)) throw conflict("Human support is not enabled for this support space.");
        }
    }

    private void requireHuman(AIWorkspaceAssignmentService.Assignment assignment, DeskBindingEntity binding) {
        var behavior = json(assignment.version().getBehaviorConfigJson());
        boolean selected = false;
        for (var extension : behavior.path("executionExtensions")) if ("CONVERSATION_HANDOVER".equals(extension.asText())) selected = true;
        if (!selected || !DeskTargetConfigurationResolver.isTargetIndependent(behavior))
            throw conflict("Enable Desk handover through its existing release lifecycle before enabling human support.");
        if (binding != null) {
            JsonNode team = desk.get(connection(assignment), "/api/desk/admin/v1/bindings/" + binding.getId() + "/support-team");
            if (!team.isArray() || team.isEmpty()) throw conflict("Assign an actual SUPPORT team before enabling human support.");
        }
    }

    private Scope scope(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment) {
        var origins = new LinkedHashSet<String>(); var topics = new LinkedHashSet<String>();
        var siblings = new ArrayList<>(installations.findByCustomerIdOrderByCreatedAtDesc(installation.getCustomerId()));
        if (siblings.stream().noneMatch(i -> i.getId().equals(installation.getId()))) siblings.add(installation);
        for (var sibling : siblings) {
            if (!"support".equals(sibling.getExperiencePackCode())
                || sibling != installation && sibling.getStatus() == AIWorkspaceInstallationStatus.DISABLED) continue;
            var consumer = consumers.findById(sibling.getConsumerEntityId()).orElse(null);
            if (consumer == null || !"ACTIVE".equals(consumer.getStatus())
                || !installation.getCustomerId().equals(consumer.getCustomerId())
                || !assignment.deployment().getId().equals(consumer.getBoundDeploymentId())
                || !installation.getConsumerEntityId().equals(sibling.getConsumerEntityId())
                    && !assignment.release().getTargetProfileId().equals(consumer.getBoundTargetProfileId())) continue;
            identity(sibling, assignment);
            for (JsonNode origin : json(sibling.getAllowedOriginsJson())) origins.add(origin.asText());
            for (JsonNode topic : json(sibling.getConfigurationJson()).at("/support/topics")) topics.add(topic.path("code").asText());
        }
        if (origins.isEmpty() || origins.size() > 32 || topics.size() > 50)
            throw conflict("Support requires between one and 32 registered exact origins and at most 50 topic codes.");
        return new Scope(List.copyOf(origins), List.copyOf(topics));
    }

    private void requireVerified(DeploymentTargetContextService.Context context) {
        if (!"APPLIED_VERIFIED".equals(context.release().getStatus()) || !"PASSED".equals(context.release().getVerificationStatus())
            || !"ACTIVE".equals(context.release().getProvisioningStatus())) throw conflict("Support requires the assigned verified active runtime.");
        canonicalConversations.requireEnabled(context.version(), context.release());
    }

    private ObjectNode safeSpace(JsonNode space) {
        ObjectNode safe = mapper.createObjectNode();
        for (String field : List.of("spaceId", "bindingId", "name", "enabled", "humanHandoverEnabled", "providerId",
                "providerBindingId", "version", "allowedOrigins", "topics", "closedRetentionDays", "capabilities"))
            safe.set(field, space.path(field).deepCopy());
        return safe;
    }

    private String namespace(AIWorkspaceAssignmentService.Assignment assignment) {
        return "engage:" + DeskBindingProvisioner.projection(assignment.deployment(), assignment.release().getTargetProfileId()).getId();
    }
    private String path(String binding) { return "/api/desk/admin/v1/bindings/" + binding + "/support-space"; }
    private JsonNode json(String raw) {
        try { return raw == null || raw.isBlank() ? mapper.createObjectNode() : mapper.readTree(raw); }
        catch (Exception error) { throw conflict("The saved support configuration is unavailable."); }
    }
    private ResponseStatusException conflict(String reason) { return new ResponseStatusException(CONFLICT, reason); }
    private record Scope(List<String> origins, List<String> topics) { }
    public record ConfigureRequest(@NotNull @PositiveOrZero Long installationRevision, @NotBlank String assignmentRevision,
        String expectedVersion, boolean enabled, boolean humanHandoverEnabled, @NotBlank String ticketProvider,
        @Min(1) @Max(3650) Integer closedRetentionDays,
        @NotBlank @Pattern(regexp="[A-Za-z0-9._:-]{1,128}") String operationKey,
        @Size(max=64) String targetProfileId, @Size(max=64) String releaseId) { }
    public record Status(String status, String assignmentRevision, JsonNode space) { }
}
