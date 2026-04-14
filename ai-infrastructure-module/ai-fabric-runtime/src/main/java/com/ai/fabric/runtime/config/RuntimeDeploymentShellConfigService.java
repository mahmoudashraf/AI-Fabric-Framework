package com.ai.fabric.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RuntimeDeploymentShellConfigService {

    public static final String CONTRACT_VERSION = "SHELL_CONFIG_V1";

    private final RuntimeDeploymentShellConfigProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private volatile JsonNode root;
    private volatile String contractVersion;
    private volatile List<String> moduleIds;
    private volatile List<String> cardIds;

    public RuntimeDeploymentShellConfigService(RuntimeDeploymentShellConfigProperties properties,
                                               ResourceLoader resourceLoader,
                                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.root = defaultConfig(null);
        this.contractVersion = CONTRACT_VERSION;
        this.moduleIds = List.of();
        this.cardIds = List.of();
    }

    @PostConstruct
    void load() {
        String location = properties.getConfigFile();
        if (!StringUtils.hasText(location)) {
            root = defaultConfig(null);
            contractVersion = CONTRACT_VERSION;
            moduleIds = List.of();
            cardIds = List.of();
            log.info("No deployment shell config file configured.");
            return;
        }

        Resource resource = RuntimeDeploymentResolvedConfigSupport.resolveResource(resourceLoader, location);
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("Deployment shell config not found: " + location);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode loaded = RuntimeDeploymentResolvedConfigSupport.readConfig(location, inputStream, objectMapper);
            ObjectNode sanitized = sanitize(loaded);
            root = sanitized;
            contractVersion = sanitized.path("contractVersion").asText(CONTRACT_VERSION);
            moduleIds = readStringList(sanitized.path("modules"), "id");
            cardIds = readStringList(sanitized.path("cards"), "id");
            log.info(
                "Loaded deployment shell config from {} with {} module(s) and {} card(s).",
                location,
                moduleIds.size(),
                cardIds.size()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load deployment shell config from " + location, ex);
        }
    }

    public JsonNode currentRoot() {
        return root;
    }

    public String currentContractVersion() {
        return contractVersion;
    }

    public List<String> currentModuleIds() {
        return moduleIds;
    }

    public List<String> currentCardIds() {
        return cardIds;
    }

    public int currentModuleCount() {
        return moduleIds.size();
    }

    public int currentCardCount() {
        return cardIds.size();
    }

    private ObjectNode sanitize(JsonNode candidate) {
        ObjectNode sanitized = defaultConfig(candidate);
        JsonNode modules = candidate != null ? candidate.path("modules") : null;
        if (modules instanceof ArrayNode arrayNode) {
            sanitized.set("modules", arrayNode.deepCopy());
        }
        JsonNode cards = candidate != null ? candidate.path("cards") : null;
        if (cards instanceof ArrayNode arrayNode) {
            sanitized.set("cards", arrayNode.deepCopy());
        }
        return sanitized;
    }

    private ObjectNode defaultConfig(JsonNode candidate) {
        ObjectNode root = objectMapper.createObjectNode();
        String effectiveContractVersion = candidate != null && candidate.path("contractVersion").isTextual()
            ? candidate.path("contractVersion").asText().trim()
            : CONTRACT_VERSION;
        root.put("contractVersion", StringUtils.hasText(effectiveContractVersion) ? effectiveContractVersion : CONTRACT_VERSION);
        root.set("modules", objectMapper.createArrayNode());
        root.set("cards", objectMapper.createArrayNode());
        return root;
    }

    private List<String> readStringList(JsonNode entries, String field) {
        if (!(entries instanceof ArrayNode arrayNode)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            if (!node.isObject()) {
                continue;
            }
            String value = node.path(field).asText("").trim();
            if (StringUtils.hasText(value) && !values.contains(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }
}
