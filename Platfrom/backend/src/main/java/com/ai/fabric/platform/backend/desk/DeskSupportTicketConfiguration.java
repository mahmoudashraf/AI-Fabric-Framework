package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Objects;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Target-local environment for the existing managed-URL/secret-reference ACTION primitive. */
@Service
public class DeskSupportTicketConfiguration {
    public static final String COMMANDS_ENV = "MANAGED_DESK_TICKET_COMMANDS_URL";
    public static final String CONNECTOR_KEY_ENV = "MANAGED_DESK_TICKET_CONNECTOR_KEY";
    public static final String COMMANDS = "${" + COMMANDS_ENV + "}";
    private final DeskBindingRepository bindings;
    private final DeploymentTargetProfileRepository targets;
    private final DeskServiceClient desk;

    public DeskSupportTicketConfiguration(DeskBindingRepository bindings, DeploymentTargetProfileRepository targets, DeskServiceClient desk) {
        this.bindings = bindings; this.targets = targets; this.desk = desk;
    }

    public static boolean requested(JsonNode actions, JsonNode routing) {
        if (actions != null) for (var action : actions.path("actions")) if (managedRoute(action.path("route"))) return true;
        if (routing != null) for (var route : routing.path("actions")) if (managedRoute(route)) return true;
        return false;
    }

    private static boolean managedRoute(JsonNode route) {
        return COMMANDS.equals(route.path("url").asText())
            && ("${" + CONNECTOR_KEY_ENV + "}").equals(route.at("/headers/X-LoomAI-Ticket-Connector-Key").asText());
    }

    public Map<String, String> connectorEnvironment(DeploymentEntity deployment, DeploymentVersionEntity version, String targetProfileId) {
        if (!Objects.equals(deployment.getId(), version.getDeploymentId()) || targetProfileId == null || targetProfileId.isBlank())
            throw conflict("Ticket actions require an explicit owned version and target.");
        var target = targets.findById(targetProfileId).filter(t -> t.isActive())
            .orElseThrow(() -> conflict("The support target is unavailable."));
        var binding = bindings.findByDeploymentIdAndTargetProfileId(deployment.getId(), targetProfileId)
            .orElseGet(() -> DeskBindingProvisioner.projection(deployment, targetProfileId));
        if (!Objects.equals(deployment.getCustomerId(), binding.getCustomerId())
            || !Objects.equals(deployment.getTenantId(), binding.getTenantId())
            || !Objects.equals(deployment.getId(), binding.getDeploymentId())
            || !Objects.equals(targetProfileId, binding.getTargetProfileId())
            || !Objects.equals(DeskBindingProvisioner.projection(deployment, targetProfileId).getId(), binding.getId()))
            throw conflict("Ticket action binding does not belong to the selected target.");
        return Map.of(COMMANDS_ENV, desk.connection(target.getEnvironmentName()).baseUrl()
                + "/api/desk/integrations/v1/spaces/" + binding.getId() + "/server-ticket-commands",
            CONNECTOR_KEY_ENV, "${secret:" + DeskBindingProvisioner.ticketConnectorKeyReference(binding) + "}");
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(CONFLICT, message); }
}
