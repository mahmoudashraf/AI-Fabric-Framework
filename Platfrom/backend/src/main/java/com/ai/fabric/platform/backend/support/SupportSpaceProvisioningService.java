package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssignmentService;
import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceSupportReadinessAdapter;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConnectionProfileRegistry;
import com.ai.fabric.platform.backend.desk.DeskServiceClient;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/** Explicit server-side provisioning of Console's private-host support identity. */
@Service
public class SupportSpaceProvisioningService implements AIWorkspaceSupportReadinessAdapter {
    private final SupportSettings settings;
    private final SupportRequesterAssertionService assertions;
    private final SupportTicketClient tickets;
    private final DeskServiceClient desk;
    private final AIWorkspaceInstallationRepository installations;
    private final PlatformConsumerRepository consumers;
    private final AIWorkspaceAssignmentService assignments;
    private final Environment environment;
    private final ObjectMapper mapper;
    public SupportSpaceProvisioningService(SupportSettings settings, SupportRequesterAssertionService assertions,
        SupportTicketClient tickets, DeskServiceClient desk, AIWorkspaceInstallationRepository installations,
        PlatformConsumerRepository consumers, AIWorkspaceAssignmentService assignments, Environment environment,
        ObjectMapper mapper) {
        this.settings = settings; this.assertions = assertions; this.tickets = tickets; this.desk = desk;
        this.installations = installations; this.consumers = consumers; this.assignments = assignments;
        this.environment = environment; this.mapper = mapper;
    }
    public JsonNode prepare() {
        var b = settings.binding();
        var installation = installations.findByInstallationId(environment.getProperty("platform.support.installation-id", ""))
            .filter(i -> b.customerId().equals(i.getCustomerId()) && i.getConnectionMode() == AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME)
            .orElseThrow(SupportPolicy::unavailable);
        var consumer = consumers.findById(installation.getConsumerEntityId()).orElseThrow(SupportPolicy::unavailable);
        var assignment = assignments.resolveCurrent(consumer);
        if (!b.deploymentId().equals(assignment.deployment().getId()) || !b.targetProfileId().equals(assignment.release().getTargetProfileId()))
            throw SupportPolicy.unavailable();
        assertions.prepare();
        var path = "/api/desk/admin/v1/bindings/" + settings.deskBindingId() + "/support-space";
        var connection = tickets.connection();
        var previous = desk.getOptional(connection, path).orElse(null);
        var body = mapper.createObjectNode().put("spaceId", settings.deskBindingId()).put("name", "LoomAI support")
            .put("enabled", true).put("humanHandoverEnabled", true).put("providerId", "desk")
            .put("providerBindingId", settings.deskBindingId()).put("runtimeBaseUrl", assignment.runtimeBaseUrl())
            .put("runtimeTrustedKeyHeader", com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER)
            .put("connectorKey", assertions.connectorKey()).put("closedRetentionDays", 30);
        if (previous != null) body.put("expectedVersion", previous.path("version").asText());
        body.set("identity", mapper.valueToTree(Map.of("mode", "TRUSTED_PRIVATE_HOST", "issuer", assertions.issuer(),
            "jwksUri", assertions.jwksUri(), "audience", assertions.audience(settings.deskBindingId()),
            "requesterNamespace", "loomai-console:" + settings.deskBindingId(), "privateServerKey", assertions.serverKey())));
        body.set("allowedOrigins", mapper.valueToTree(List.of()));
        try {
            var topics = new java.util.ArrayList<String>();
            for (var topic : mapper.readTree(installation.getConfigurationJson()).path("support").path("topics"))
                topics.add(topic.path("code").asText());
            body.set("topics", mapper.valueToTree(topics));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw SupportPolicy.unavailable(); }
        return desk.put(connection, path, body);
    }

    @Override public boolean supports(AIWorkspaceInstallationEntity installation) {
        return "support".equals(installation.getExperiencePackCode())
            && installation.getConnectionMode() == AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME
            && AIWorkspaceConnectionProfileRegistry.SUPPORT_CASE_HOST.equals(installation.getConnectionProfileCode());
    }

    @Override public void validate(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment) {
        var b = settings.binding();
        if (!environment.getProperty("platform.support.installation-id", "").equals(installation.getInstallationId())
            || !b.customerId().equals(installation.getCustomerId())
            || !b.deploymentId().equals(assignment.deployment().getId())
            || !b.targetProfileId().equals(assignment.release().getTargetProfileId())) throw SupportPolicy.unavailable();
        var space = desk.get(tickets.connection(), "/api/desk/admin/v1/bindings/" + settings.deskBindingId() + "/support-space");
        if (!settings.deskBindingId().equals(space.path("spaceId").asText())
            || !settings.deskBindingId().equals(space.path("bindingId").asText())
            || !settings.deskBindingId().equals(space.path("providerBindingId").asText())
            || !"desk".equals(space.path("providerId").asText())
            || !"TRUSTED_PRIVATE_HOST".equals(space.path("identity").path("mode").asText())
            || !assertions.issuer().equals(space.path("identity").path("issuer").asText())
            || !assertions.audience(settings.deskBindingId()).equals(space.path("identity").path("audience").asText()))
            throw SupportPolicy.unavailable();
        try {
            var config = mapper.readTree(installation.getConfigurationJson());
            if (config.path("capabilities").path("tickets").asBoolean(false) && !space.path("enabled").asBoolean(false))
                throw SupportPolicy.unavailable();
            if (config.path("capabilities").path("handover").asBoolean(false)
                && (!space.path("humanHandoverEnabled").asBoolean(false)
                    || !mapper.readTree(assignment.version().getBehaviorConfigJson()).path("conversationHandover").path("enabled").asBoolean(false)))
                throw SupportPolicy.unavailable();
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw SupportPolicy.unavailable(); }
    }
}
