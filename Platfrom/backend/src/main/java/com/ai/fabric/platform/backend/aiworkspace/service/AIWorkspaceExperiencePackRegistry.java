package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AIWorkspaceExperiencePackRegistry {

    public static final String DEALERSHIP_CODE = "dealership";
    public static final String DEALERSHIP_VERSION = "1.1.0";
    public static final String GENERIC_CODE = "generic-workspace";
    public static final String GENERIC_VERSION = "1.0.0";
    public static final String SUPPORT_CODE = "support";
    public static final String SUPPORT_VERSION = "1.0.0";
    public static final String SUPPORT_SCHEMA = "loomai-support-experience-config-v1";

    private final AIWorkspaceAssetCatalogService assets;
    private final AIWorkspaceConfigurationValidator validator;

    public AIWorkspaceExperiencePackRegistry(AIWorkspaceAssetCatalogService assets,
                                             AIWorkspaceConfigurationValidator validator) {
        this.assets = assets;
        this.validator = validator;
    }

    public ExperiencePack resolve(String code, String version) {
        ExperiencePackDefinition definition = definition(code, version);
        if (definition == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace experience pack.");
        }
        AIWorkspaceAssetDescriptor asset = null;
        try {
            asset = assets.experiencePack(code, version);
        } catch (IllegalStateException ignored) {
            // Draft authoring remains available; activation requires a verified packaged asset catalog.
        }
        return new ExperiencePack(
            code,
            version,
            definition.name(),
            definition.configurationSchemaVersion(),
            true,
            asset
        );
    }

    public void validateConfiguration(String code, String version, JsonNode configuration) {
        resolve(code, version);
        if (DEALERSHIP_CODE.equals(code)) validator.validateDealershipConfiguration(configuration);
        else if (GENERIC_CODE.equals(code)) validator.validateGenericConfiguration(configuration);
        else if (SUPPORT_CODE.equals(code)) validator.validateSupportConfiguration(configuration);
        else throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace experience pack.");
    }

    /** Pack-owned field identities for configuration authoring, not model instructions. */
    public AuthoringContract authoringContract(String code, String version) {
        ExperiencePack pack = resolve(code, version);
        return new AuthoringContract(pack.code(), pack.version(), pack.configurationSchemaVersion(), Map.of(
            "assistantLabel", definition(code, version).assistantLabelPath(),
            "welcomeMessage", "/configuration/copy/welcomeMessage",
            "starterSuggestions", "/configuration/copy/starterSuggestions",
            "theme", "/configuration/theme",
            "toolGroups", "/configuration/toolGroups",
            "toolRail", "/configuration/toolRail"));
    }

    public List<AIWorkspaceCatalogSummary.ExperiencePackSummary> summaries() {
        return List.of(
            summary(GENERIC_CODE, GENERIC_VERSION),
            summary(DEALERSHIP_CODE, DEALERSHIP_VERSION),
            summary(SUPPORT_CODE, SUPPORT_VERSION)
        );
    }

    private AIWorkspaceCatalogSummary.ExperiencePackSummary summary(String code, String version) {
        ExperiencePackDefinition definition = definition(code, version);
        boolean enabled;
        try {
            enabled = resolve(code, version).asset() != null;
        } catch (RuntimeException ex) {
            enabled = false;
        }
        return new AIWorkspaceCatalogSummary.ExperiencePackSummary(
            code, version, definition.name(), definition.configurationSchemaVersion(), enabled);
    }

    private ExperiencePackDefinition definition(String code, String version) {
        if (DEALERSHIP_CODE.equals(code) && DEALERSHIP_VERSION.equals(version)) {
            return new ExperiencePackDefinition("Dealership experience", "loomai-dealership-experience-config-v1", "/configuration/dealer/assistantLabel");
        }
        if (GENERIC_CODE.equals(code) && GENERIC_VERSION.equals(version)) {
            return new ExperiencePackDefinition("Generic knowledge and service experience", "loomai-generic-workspace-config-v1", "/configuration/app/assistantLabel");
        }
        if (SUPPORT_CODE.equals(code) && SUPPORT_VERSION.equals(version)) {
            return new ExperiencePackDefinition("AI Support Experience", SUPPORT_SCHEMA, "/configuration/app/assistantLabel");
        }
        return null;
    }

    private record ExperiencePackDefinition(String name, String configurationSchemaVersion, String assistantLabelPath) {
    }

    public record AuthoringContract(String code, String version, String configurationSchemaVersion,
                                    Map<String, String> fieldPaths) { }

    public record ExperiencePack(
        String code,
        String version,
        String name,
        String configurationSchemaVersion,
        boolean enabled,
        AIWorkspaceAssetDescriptor asset
    ) {
    }
}
