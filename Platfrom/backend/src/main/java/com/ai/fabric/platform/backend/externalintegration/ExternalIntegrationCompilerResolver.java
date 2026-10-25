package com.ai.fabric.platform.backend.externalintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;

/** Symbolic compiler input only. Never writes an install, draft or published version. */
@Component
public class ExternalIntegrationCompilerResolver {
    private final ObjectMapper mapper;
    public ExternalIntegrationCompilerResolver(ObjectMapper mapper) { this.mapper = mapper; }

    public InstallInputs installInputs(Profile profile, JsonNode config, JsonNode secretRefs) {
        ObjectNode effectiveConfig = object(config), effectiveSecrets = object(secretRefs);
        for (String field : profile.configFields()) put(effectiveConfig, field, "${" + alias(profile, field) + "}");
        for (String field : profile.secretFields()) put(effectiveSecrets, field, alias(profile, field));
        return new InstallInputs(effectiveConfig, effectiveSecrets);
    }
    public ObjectNode compilerEvidence(Profile profile) {
        return mapper.valueToTree(evidenceFields(profile, new CustomerOfferCodec(mapper)));
    }
    static Map<String, Object> evidenceFields(Profile profile, CustomerOfferCodec codec) {
        Map<String, String> config = new LinkedHashMap<>(), secrets = new LinkedHashMap<>();
        profile.configFields().forEach(field -> config.put(field, alias(profile, field)));
        profile.secretFields().forEach(field -> secrets.put(field, alias(profile, field)));
        return Map.of("schemaVersion", "loomai-external-integration-compiler/v1", "profileId", profile.id(),
            "bindingSlotId", profile.bindingSlotId(), "pluginId", profile.pluginId(), "pluginVersionId", profile.pluginVersionId(),
            "profileFingerprint", codec.hash(profile), "configAliases", config, "secretAliases", secrets);
    }
    public static String alias(Profile profile, String field) {
        if (!profile.configFields().contains(field) && !profile.secretFields().contains(field)) {
            throw ExternalIntegrationPolicy.conflict("FIELD_NOT_REVIEWED");
        }
        return "MANAGED_EXTERNAL_INTEGRATION_" + digest(profile.bindingSlotId() + "\n" + profile.id() + "\n"
            + profile.pluginVersionId() + "\n" + field).substring(0, 40).toUpperCase(java.util.Locale.ROOT);
    }
    public static String secretName(String bindingId, Scope scope, String field) {
        return "MANAGED_EXTERNAL_INTEGRATION_CREDENTIAL_" + digest(bindingId + "\n" + scope.deploymentId()
            + "\n" + scope.versionId() + "\n" + scope.targetProfileId() + "\n" + field).toUpperCase(java.util.Locale.ROOT);
    }
    private ObjectNode object(JsonNode input) {
        if (input == null || input.isNull()) return mapper.createObjectNode();
        if (!input.isObject()) throw ExternalIntegrationPolicy.conflict("INSTALL_INPUT_INVALID");
        return ((ObjectNode) input).deepCopy();
    }
    private void put(ObjectNode object, String field, String value) {
        if (object.has(field)) {
            JsonNode previous = object.path(field);
            // These are compiler-owned symbols, never resolved credentials or endpoint values.
            // Recompile the symbol for the selected plugin version instead of pinning the bootstrap alias.
            String aliasPattern = value.startsWith("${")
                ? "\\$\\{MANAGED_EXTERNAL_INTEGRATION_[A-F0-9]{40}}" : "MANAGED_EXTERNAL_INTEGRATION_[A-F0-9]{40}";
            if (!previous.isTextual() || (!value.equals(previous.textValue()) && !previous.textValue().matches(aliasPattern))) {
                throw ExternalIntegrationPolicy.conflict("MUTABLE_INSTALL_BINDING_FORBIDDEN");
            }
        }
        object.put(field, value);
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
