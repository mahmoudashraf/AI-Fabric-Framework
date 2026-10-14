package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AIWorkspaceExperiencePackRegistry {

    public static final String DEALERSHIP_CODE = "dealership";
    public static final String DEALERSHIP_VERSION = "1.1.0";
    public static final String GENERIC_CODE = "generic-workspace";
    public static final String GENERIC_VERSION = "1.0.0";

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
        else throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace experience pack.");
    }

    public List<AIWorkspaceCatalogSummary.ExperiencePackSummary> summaries() {
        return List.of(
            summary(GENERIC_CODE, GENERIC_VERSION),
            summary(DEALERSHIP_CODE, DEALERSHIP_VERSION)
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
            return new ExperiencePackDefinition("Dealership experience", "loomai-dealership-experience-config-v1");
        }
        if (GENERIC_CODE.equals(code) && GENERIC_VERSION.equals(version)) {
            return new ExperiencePackDefinition("Generic knowledge and service experience", "loomai-generic-workspace-config-v1");
        }
        return null;
    }

    private record ExperiencePackDefinition(String name, String configurationSchemaVersion) {
    }

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
