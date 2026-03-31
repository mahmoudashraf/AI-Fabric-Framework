package com.ai.fabric.runtime.config;

import com.ai.infrastructure.prompt.PromptPreviewOverlaySupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class RuntimeDeploymentPromptConfigService {

    private final RuntimeDeploymentPromptConfigProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    private volatile Map<String, String> promptOverlay = Map.of();

    public RuntimeDeploymentPromptConfigService(RuntimeDeploymentPromptConfigProperties properties,
                                                ResourceLoader resourceLoader,
                                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.jsonMapper = objectMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @PostConstruct
    void load() {
        String location = properties.getConfigFile();
        if (!StringUtils.hasText(location)) {
            promptOverlay = Map.of();
            log.info("No deployment prompt config file configured.");
            return;
        }

        Resource resource = resolveResource(location);
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("Deployment prompt config not found: " + location);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = readConfig(location, inputStream);
            promptOverlay = sanitize(root);
            log.info(
                "Loaded deployment prompt config from {} with {} prompt override(s).",
                location,
                promptOverlay.size()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load deployment prompt config from " + location, ex);
        }
    }

    public Map<String, String> currentPromptOverlay() {
        return promptOverlay;
    }

    private Resource resolveResource(String location) {
        String trimmed = location == null ? "" : location.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        if (ResourceUtils.isUrl(trimmed) || trimmed.startsWith("classpath:") || trimmed.startsWith("file:")) {
            return resourceLoader.getResource(trimmed);
        }
        return resourceLoader.getResource("classpath:" + trimmed);
    }

    private JsonNode readConfig(String location, InputStream inputStream) throws Exception {
        String normalized = location.toLowerCase();
        if (normalized.endsWith(".yml") || normalized.endsWith(".yaml")) {
            return yamlMapper.readTree(inputStream);
        }
        return jsonMapper.readTree(inputStream);
    }

    private Map<String, String> sanitize(JsonNode root) {
        if (root == null || !root.isObject()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (String key : PromptPreviewOverlaySupport.SUPPORTED_KEYS) {
            JsonNode candidate = root.path(key);
            if (!candidate.isTextual()) {
                continue;
            }
            String value = candidate.asText();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            sanitized.put(key, value.trim());
        }
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }
}
