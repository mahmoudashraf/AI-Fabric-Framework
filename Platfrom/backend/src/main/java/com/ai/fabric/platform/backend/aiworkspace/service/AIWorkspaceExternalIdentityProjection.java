package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class AIWorkspaceExternalIdentityProjection {

    static final String MANAGED_FIELD = "aiWorkspaceManagedExternalIdentity";
    private static final String ENABLED = "externalIdentityBrokerEnabled";
    private static final String SCOPES = "externalIdentityGrantedScopes";
    private static final List<String> FIELDS = List.of(ENABLED, "externalIdentityProvider",
        "externalIdentityIssuer", "externalIdentityJwkSetUri", "externalIdentityAudience", SCOPES);
    private static final List<String> CHAT_SCOPES = List.of("chat:query", "chat:suggestions", "chat:conversations");
    private static final List<String> HANDOVER_SCOPES = List.of("chat:messages", "chat:handover:request",
        "chat:handover:control", "chat:handover:read");

    private final ObjectMapper objectMapper;
    private final AIWorkspaceAuthenticationProfileRegistry authenticationProfiles;

    public AIWorkspaceExternalIdentityProjection(ObjectMapper objectMapper,
                                                AIWorkspaceAuthenticationProfileRegistry authenticationProfiles) {
        this.objectMapper = objectMapper;
        this.authenticationProfiles = authenticationProfiles;
    }

    public void project(ObjectNode security, List<AIWorkspaceInstallationEntity> installations, JsonNode behavior) {
        if (security == null || installations == null) throw conflict();
        ObjectNode updated = security.deepCopy();
        restorePrevious(updated);
        ObjectNode desired = desiredIdentity(installations, behavior);
        if (desired != null) {
            JsonNode enabled = updated.get(ENABLED);
            if (enabled != null && !enabled.isBoolean()) throw conflict();
            if (enabled != null && enabled.booleanValue()) {
                requireMatchingManualBroker(updated, desired);
            } else {
                ObjectNode metadata = objectMapper.createObjectNode();
                metadata.put("schemaVersion", 1);
                metadata.set("previous", snapshot(updated));
                metadata.set("projected", desired.deepCopy());
                updated.setAll(desired);
                updated.set(MANAGED_FIELD, metadata);
            }
        }
        // Publish only after profile, multi-installation, ownership and drift checks all succeed.
        for (String field : FIELDS) copyField(updated, security, field);
        copyField(updated, security, MANAGED_FIELD);
        // This public lookup key accompanies the retained manual email policy, not app-owned scope grants.
        if (desired != null && ManagedDeploymentProfileCatalog.externalIdentityRequireVerifiedEmail(security)) {
            for (var installation : installations) {
                if (!installation.getInteractionPolicy().requiresAuthenticationProfile()) continue;
                try {
                    var profile = authenticationProfiles.resolve(installation.getAuthProfileCode(), installation.getAuthProfileVersion(),
                        installation.getInteractionPolicy(), objectMapper.readTree(installation.getAuthConfigurationJson()));
                    security.put("externalIdentitySupabasePublicKey", profile.publicConfiguration().path("publishableKey").asText());
                    break;
                } catch (Exception invalid) { throw conflict(); }
            }
        }
    }

    private ObjectNode desiredIdentity(List<AIWorkspaceInstallationEntity> installations, JsonNode behavior) {
        ObjectNode desired = null;
        for (AIWorkspaceInstallationEntity installation : installations) {
            if (installation == null || installation.getInteractionPolicy() == null) throw conflict();
            if (!installation.getInteractionPolicy().requiresAuthenticationProfile()) continue;
            AIWorkspaceAuthenticationProfileRegistry.AuthenticationProfile profile;
            try {
                String raw = installation.getAuthConfigurationJson();
                if (raw == null) throw conflict();
                JsonNode configuration = objectMapper.readTree(raw);
                if (configuration == null || !configuration.isObject()) throw conflict();
                profile = authenticationProfiles.resolve(installation.getAuthProfileCode(),
                    installation.getAuthProfileVersion(), installation.getInteractionPolicy(), configuration);
            } catch (Exception error) {
                throw conflict();
            }
            if (!AIWorkspaceAuthenticationProfileRegistry.SUPABASE.equals(profile.code())) throw conflict();
            String issuer = profile.publicConfiguration().path("projectUrl").asText() + "/auth/v1";
            ObjectNode candidate = objectMapper.createObjectNode();
            candidate.put(ENABLED, true);
            candidate.put("externalIdentityProvider", "SUPABASE");
            candidate.put("externalIdentityIssuer", issuer);
            candidate.put("externalIdentityJwkSetUri", issuer + "/.well-known/jwks.json");
            candidate.put("externalIdentityAudience", "authenticated");
            LinkedHashSet<String> scopes = new LinkedHashSet<>(CHAT_SCOPES);
            if (handoverEnabled(behavior)) scopes.addAll(HANDOVER_SCOPES);
            candidate.put(SCOPES, String.join(",", scopes));
            if (desired != null && !desired.equals(candidate)) throw conflict();
            desired = candidate;
        }
        return desired;
    }

    private boolean handoverEnabled(JsonNode behavior) {
        if (behavior == null || !behavior.path("executionExtensions").isArray()) return false;
        JsonNode enabled = behavior.path("conversationHandover").path("enabled");
        if (!enabled.isBoolean() || !enabled.booleanValue()) return false;
        for (JsonNode extension : behavior.path("executionExtensions")) {
            if (extension.isTextual() && "CONVERSATION_HANDOVER".equals(extension.textValue())) return true;
        }
        return false;
    }

    private void requireMatchingManualBroker(ObjectNode security, ObjectNode desired) {
        for (String field : FIELDS) {
            if (!ENABLED.equals(field) && security.has(field) && !security.get(field).isTextual()) throw conflict();
        }
        if (!desired.path("externalIdentityProvider").asText().equals(ManagedDeploymentProfileCatalog.externalIdentityProvider(security))
            || !desired.path("externalIdentityIssuer").asText().equals(ManagedDeploymentProfileCatalog.externalIdentityIssuer(security))
            || !desired.path("externalIdentityJwkSetUri").asText().equals(ManagedDeploymentProfileCatalog.externalIdentityJwkSetUri(security))
            || !desired.path("externalIdentityAudience").asText().equals(ManagedDeploymentProfileCatalog.externalIdentityAudience(security))) {
            throw conflict();
        }
        Set<String> granted = new LinkedHashSet<>(Arrays.stream(
            ManagedDeploymentProfileCatalog.externalIdentityGrantedScopes(security).split(",")).map(String::trim).toList());
        if (!granted.containsAll(Arrays.asList(desired.path(SCOPES).asText().split(",")))) throw conflict();
    }

    private void restorePrevious(ObjectNode security) {
        JsonNode metadata = security.get(MANAGED_FIELD);
        if (metadata == null) return;
        if (!metadata.isObject() || metadata.size() != 3
            || !metadata.path("schemaVersion").isInt() || metadata.path("schemaVersion").intValue() != 1
            || !metadata.path("previous").isObject() || !metadata.path("projected").isObject()) throw conflict();
        ObjectNode previous = (ObjectNode) metadata.path("previous");
        ObjectNode projected = (ObjectNode) metadata.path("projected");
        if (projected.size() != FIELDS.size()) throw conflict();
        previous.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field) || !validValue(field, previous.get(field), true)) throw conflict();
        });
        for (String field : FIELDS) {
            if (!validValue(field, projected.get(field), false)
                || !projected.get(field).equals(security.get(field))) throw conflict();
        }
        if (!projected.path(ENABLED).booleanValue()
            || !"SUPABASE".equals(projected.path("externalIdentityProvider").asText())
            || !"authenticated".equals(projected.path("externalIdentityAudience").asText())) throw conflict();
        for (String field : FIELDS) {
            if (!projected.get(field).equals(previous.get(field))) copyField(previous, security, field);
        }
        security.remove(MANAGED_FIELD);
    }

    private boolean validValue(String field, JsonNode value, boolean nullable) {
        if (value == null) return false;
        if (value.isNull()) return nullable;
        return ENABLED.equals(field) ? value.isBoolean() : value.isTextual() && (nullable || !value.textValue().isBlank());
    }

    private ObjectNode snapshot(ObjectNode security) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        for (String field : FIELDS) {
            if (security.has(field) && !validValue(field, security.get(field), true)) throw conflict();
            copyField(security, snapshot, field);
        }
        return snapshot;
    }

    private void copyField(ObjectNode from, ObjectNode to, String field) {
        if (from.has(field)) to.set(field, from.get(field).deepCopy());
        else to.remove(field);
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(CONFLICT,
            "AI Workspace external identity projection requires reconciliation before deployment publication.");
    }
}
