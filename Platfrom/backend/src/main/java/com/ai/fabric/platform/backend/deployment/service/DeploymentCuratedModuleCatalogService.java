package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentCuratedModuleSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class DeploymentCuratedModuleCatalogService {

    private static final String DEFAULT_MODULE_ID = "default";

    private final Map<String, ModuleDefinition> modulesById;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public DeploymentCuratedModuleCatalogService(ObjectMapper objectMapper,
                                                 ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.modulesById = buildModules();
    }

    public List<DeploymentCuratedModuleSummary> listModules() {
        return modulesById.values().stream()
            .map(ModuleDefinition::summary)
            .toList();
    }

    public DeploymentCuratedModuleSummary resolveSummary(String requestedId) {
        return resolveDefinition(requestedId).summary();
    }

    public String normalizeModuleId(String requestedId) {
        return resolveDefinition(requestedId).summary().id();
    }

    public JsonNode promptPreset(String requestedId) {
        ModuleDefinition definition = resolveDefinition(requestedId);
        Resource resource = resourceLoader.getResource(definition.promptPresetLocation());
        if (!resource.exists()) {
            throw new IllegalStateException(
                "Prompt preset resource not found for curated module " + definition.summary().id()
                    + ": " + definition.promptPresetLocation()
            );
        }
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode node = objectMapper.readTree(inputStream);
            if (node == null || !node.isObject()) {
                return objectMapper.createObjectNode();
            }
            return node;
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Failed to load prompt preset for curated module " + definition.summary().id(),
                ex
            );
        }
    }

    public String runtimeCuratedPack(String requestedId) {
        return resolveDefinition(requestedId).summary().runtimeCuratedPack();
    }

    private ModuleDefinition resolveDefinition(String requestedId) {
        String normalized = normalizeId(requestedId);
        ModuleDefinition definition = modulesById.get(normalized);
        if (definition == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown curatedModuleId: " + requestedId);
        }
        return definition;
    }

    private String normalizeId(String requestedId) {
        if (!StringUtils.hasText(requestedId)) {
            return DEFAULT_MODULE_ID;
        }
        String normalized = requestedId.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? DEFAULT_MODULE_ID : normalized;
    }

    private Map<String, ModuleDefinition> buildModules() {
        Map<String, ModuleDefinition> modules = new LinkedHashMap<>();
        register(
            modules,
            new DeploymentCuratedModuleSummary(
                DEFAULT_MODULE_ID,
                "Default",
                "General-purpose assistant baseline using the platform default prompt preset.",
                "default",
                "default"
            ),
            "classpath:bootstrap/prompts/default-prompt-config.json"
        );
        register(
            modules,
            new DeploymentCuratedModuleSummary(
                "commerce",
                "Commerce",
                "Commerce-focused assistant baseline tuned for products, orders, policies, and customer support workflows.",
                "commerce",
                "commerce"
            ),
            "classpath:bootstrap/prompts/commerce-prompt-config.json"
        );
        register(
            modules,
            new DeploymentCuratedModuleSummary(
                "support",
                "Support",
                "Support-focused assistant baseline tuned for help center, troubleshooting, case triage, and operator workflows.",
                "support",
                "support"
            ),
            "classpath:bootstrap/prompts/support-prompt-config.json"
        );
        return Map.copyOf(modules);
    }

    private void register(Map<String, ModuleDefinition> modules,
                          DeploymentCuratedModuleSummary summary,
                          String promptPresetLocation) {
        modules.put(summary.id(), new ModuleDefinition(summary, promptPresetLocation));
    }

    private record ModuleDefinition(DeploymentCuratedModuleSummary summary, String promptPresetLocation) {
    }
}
