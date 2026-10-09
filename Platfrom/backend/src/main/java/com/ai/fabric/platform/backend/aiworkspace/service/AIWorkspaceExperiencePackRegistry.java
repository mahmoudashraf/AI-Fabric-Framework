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

    private final AIWorkspaceAssetCatalogService assets;
    private final AIWorkspaceConfigurationValidator validator;

    public AIWorkspaceExperiencePackRegistry(AIWorkspaceAssetCatalogService assets,
                                             AIWorkspaceConfigurationValidator validator) {
        this.assets = assets;
        this.validator = validator;
    }

    public ExperiencePack resolve(String code, String version) {
        if (!DEALERSHIP_CODE.equals(code) || !DEALERSHIP_VERSION.equals(version)) {
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
            "Dealership experience",
            "loomai-dealership-experience-config-v1",
            true,
            asset
        );
    }

    public void validateConfiguration(String code, String version, JsonNode configuration) {
        resolve(code, version);
        validator.validateDealershipConfiguration(configuration);
    }

    public List<AIWorkspaceCatalogSummary.ExperiencePackSummary> summaries() {
        boolean enabled;
        try {
            enabled = resolve(DEALERSHIP_CODE, DEALERSHIP_VERSION).asset() != null;
        } catch (RuntimeException ex) {
            enabled = false;
        }
        return List.of(new AIWorkspaceCatalogSummary.ExperiencePackSummary(
            DEALERSHIP_CODE,
            DEALERSHIP_VERSION,
            "Dealership experience",
            "loomai-dealership-experience-config-v1",
            enabled
        ));
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
