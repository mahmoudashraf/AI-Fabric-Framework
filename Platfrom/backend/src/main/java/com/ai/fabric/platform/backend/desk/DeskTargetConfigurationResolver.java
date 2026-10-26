package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Resolves selected-target Desk slots; an unregistered preview projects names without creating credentials. */
@Service
public class DeskTargetConfigurationResolver {
    public static final String BINDING = "${MANAGED_DESK_BINDING_ID}";
    public static final String PROVIDER = "${MANAGED_DESK_PROVIDER_BASE_URL}";
    public static final String CONSOLE = "${MANAGED_DESK_OPERATOR_CONSOLE_URL}";
    public static final String PROVIDER_KEY = "MANAGED_DESK_PROVIDER_API_KEY";
    public static final String RUNTIME_CALLBACK_KEY = "MANAGED_DESK_RUNTIME_CALLBACK_SIGNING_SECRET";
    public static final String PROVIDER_CALLBACK_KEY = "MANAGED_DESK_PROVIDER_WEBHOOK_SIGNING_SECRET";

    private final DeskBindingRepository bindings;
    private final DeploymentTargetProfileRepository targets;
    private final DeskServiceClient desk;

    public DeskTargetConfigurationResolver(DeskBindingRepository bindings,
            DeploymentTargetProfileRepository targets, DeskServiceClient desk) {
        this.bindings = bindings;
        this.targets = targets;
        this.desk = desk;
    }

    public static boolean usesDesk(JsonNode behavior) {
        return DeskActivationService.PLUGIN.equals(behavior.path("conversationHandover")
            .path("marketplacePluginId").asText());
    }

    public static boolean isTargetIndependent(JsonNode behavior) {
        JsonNode handover = behavior.path("conversationHandover");
        JsonNode channel = handover.path("channel");
        return usesDesk(behavior) && handover.path("enabled").asBoolean(false)
            && BINDING.equals(handover.path("channelBindingId").asText())
            && BINDING.equals(channel.path("bindingId").asText())
            && PROVIDER.equals(channel.path("providerBaseUrl").asText())
            && CONSOLE.equals(channel.path("operatorConsoleUrl").asText())
            && PROVIDER_KEY.equals(channel.path("providerApiKeySecretRef").asText())
            && RUNTIME_CALLBACK_KEY.equals(channel.path("runtimeCallbackSigningSecretRef").asText())
            && PROVIDER_CALLBACK_KEY.equals(channel.path("providerWebhookSigningSecretRef").asText());
    }

    public JsonNode resolve(DeploymentEntity deployment, DeploymentVersionEntity version,
                            String targetProfileId, JsonNode behavior) {
        if (!usesDesk(behavior)) return behavior;
        if (!isTargetIndependent(behavior)) {
            throw conflict("Desk must use target-independent managed configuration. Enable Desk before applying this version.");
        }
        if (!Objects.equals(deployment.getId(), version.getDeploymentId())
            || targetProfileId == null || targetProfileId.isBlank()) {
            throw conflict("Desk requires an explicit deployment target.");
        }
        var binding = bindings.findByDeploymentIdAndTargetProfileId(deployment.getId(), targetProfileId)
            .orElseGet(() -> DeskBindingProvisioner.projection(deployment, targetProfileId));
        // Tenant/deployment/target ownership is the credential boundary; never fall back to another binding.
        if (!Objects.equals(deployment.getCustomerId(), binding.getCustomerId())
            || !Objects.equals(deployment.getTenantId(), binding.getTenantId())
            || !Objects.equals(deployment.getId(), binding.getDeploymentId())
            || !Objects.equals(targetProfileId, binding.getTargetProfileId())) {
            throw conflict("Desk binding is unavailable for the selected deployment target.");
        }
        var target = targets.findById(targetProfileId).filter(value -> value.isActive())
            .orElseThrow(() -> conflict("Desk target is unavailable."));
        var connection = desk.connection(target.getEnvironmentName());
        ObjectNode resolved = behavior.deepCopy();
        ObjectNode handover = (ObjectNode) resolved.path("conversationHandover");
        ObjectNode channel = (ObjectNode) handover.path("channel");
        handover.put("channelBindingId", binding.getId());
        channel.put("bindingId", binding.getId());
        channel.put("providerBaseUrl", connection.baseUrl());
        channel.put("operatorConsoleUrl", connection.baseUrl() + "/?bindingId=" + binding.getId());
        channel.put("providerApiKeySecretRef", binding.getProviderKeyRef());
        channel.put("runtimeCallbackSigningSecretRef", binding.getRuntimeCallbackKeyRef());
        channel.put("providerWebhookSigningSecretRef", binding.getCallbackKeyRef());
        return resolved;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(CONFLICT, message);
    }
}
