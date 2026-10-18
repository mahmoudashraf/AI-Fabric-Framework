package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAuthenticationProfileRegistry;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConfigurationValidator;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConnectionProfileRegistry;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceExperiencePackRegistry;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.invalid;

/** Reviewed public payloads let a fresh customer select a configuration without an existing installation. */
@Service
public class CustomerWorkspaceProfileValidator {
    private final CustomerOfferCodec codec;
    private final AIWorkspaceConfigurationValidator configuration;
    private final AIWorkspaceExperiencePackRegistry packs;
    private final AIWorkspaceConnectionProfileRegistry connections;
    private final AIWorkspaceAuthenticationProfileRegistry authentication;

    public CustomerWorkspaceProfileValidator(CustomerOfferCodec codec, AIWorkspaceConfigurationValidator configuration,
        AIWorkspaceExperiencePackRegistry packs, AIWorkspaceConnectionProfileRegistry connections,
        AIWorkspaceAuthenticationProfileRegistry authentication) {
        this.codec = codec; this.configuration = configuration; this.packs = packs;
        this.connections = connections; this.authentication = authentication;
    }

    public void validate(Definition definition) {
        if (definition.workspaceProfiles().size() > 20) throw invalid();
        Set<String> ids = new HashSet<>();
        for (WorkspaceProfile profile : definition.workspaceProfiles()) {
            if (profile == null || profile.id() == null || !profile.id().matches("[A-Za-z0-9._:-]{1,64}")
                || !ids.add(profile.id()) || profile.label() == null || profile.label().isBlank()
                || profile.label().length() > 120) throw invalid();
            JsonNode interaction = profile.interaction();
            JsonNode auth = profile.authentication();
            exactFields(interaction, Set.of("packCode", "packVersion", "connectionMode", "connectionProfileCode",
                "connectionProfileVersion", "deliveryMode", "connectionConfiguration", "configuration"));
            exactFields(auth, Set.of("profileCode", "profileVersion", "interactionPolicy", "configuration"));
            try {
                configuration.validatePublicConfiguration(interaction);
                if (profile.theme() == null) throw invalid();
                if (!profile.theme().isNull()) configuration.validatePublicConfiguration(profile.theme());
                configuration.validatePublicConfiguration(auth);
                ObjectNode packConfiguration = object(interaction.path("configuration")).deepCopy();
                if (packConfiguration.has("theme")) throw invalid();
                packConfiguration.set("theme", profile.theme());
                packs.validateConfiguration(text(interaction, "packCode"), text(interaction, "packVersion"), packConfiguration);
                var mode = AIWorkspaceConnectionMode.valueOf(text(interaction, "connectionMode"));
                AIWorkspaceDeliveryMode.valueOf(text(interaction, "deliveryMode"));
                var connection = connections.resolve(text(interaction, "connectionProfileCode"), text(interaction, "connectionProfileVersion"),
                    mode, object(interaction.path("connectionConfiguration")));
                if (!codec.hash(connection.publicConfiguration()).equals(codec.hash(interaction.path("connectionConfiguration")))) throw invalid();
                var policy = AIWorkspaceInteractionPolicy.valueOf(text(auth, "interactionPolicy"));
                if ((policy == AIWorkspaceInteractionPolicy.LOGIN_REQUIRED && mode != AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED)
                    || (policy == AIWorkspaceInteractionPolicy.PUBLIC_WITH_PROTECTED_CAPABILITIES
                        && mode != AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS)) throw invalid();
                var resolvedAuth = authentication.resolve(text(auth, "profileCode"), text(auth, "profileVersion"), policy,
                    object(auth.path("configuration")));
                if (!resolvedAuth.code().equals(text(auth, "profileCode"))
                    || !codec.hash(resolvedAuth.publicConfiguration()).equals(codec.hash(auth.path("configuration")))) throw invalid();
                requirePin(definition, Setting.INTERACTION_PROFILE_ID, "workspace", interaction);
                requirePin(definition, Setting.THEME_PROFILE_ID, "theme", profile.theme());
                requirePin(definition, Setting.AUTH_PROFILE_ID, "auth", auth);
            } catch (RuntimeException error) { throw invalid(); }
        }
    }

    private void requirePin(Definition definition, Setting setting, String prefix, JsonNode payload) {
        String id = prefix + ":" + codec.hash(payload);
        if (definition.settings().stream().noneMatch(rule -> rule.setting() == setting
            && rule.type() == ValueType.CHOICE && rule.allowedValues().contains(id) && id.length() <= rule.maxLength())) throw invalid();
    }

    private void exactFields(JsonNode value, Set<String> fields) {
        if (value == null || !value.isObject() || value.size() != fields.size()) throw invalid();
        value.fieldNames().forEachRemaining(field -> { if (!fields.contains(field)) throw invalid(); });
    }

    private ObjectNode object(JsonNode value) {
        if (value == null || !value.isObject()) throw invalid();
        return (ObjectNode) value;
    }

    private String text(JsonNode value, String key) {
        JsonNode node = value.path(key);
        if (!node.isTextual() || node.asText().isBlank() || node.asText().length() > 100
            || !node.asText().equals(node.asText().trim())) throw invalid();
        return node.asText();
    }
}
