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
public class RuntimeDeploymentKnowledgeSourceConfigService {

    public static final String CONTRACT_VERSION = "KNOWLEDGE_SOURCE_CONFIG_V1";

    private final RuntimeDeploymentKnowledgeSourceConfigProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private volatile JsonNode root = defaultConfig(null);
    private volatile String contractVersion = CONTRACT_VERSION;
    private volatile List<String> sourceIds = List.of();
    private volatile List<String> sourceTypes = List.of();

    public RuntimeDeploymentKnowledgeSourceConfigService(RuntimeDeploymentKnowledgeSourceConfigProperties properties,
                                                         ResourceLoader resourceLoader,
                                                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        String location = properties.getConfigFile();
        if (!StringUtils.hasText(location)) {
            root = defaultConfig(null);
            contractVersion = CONTRACT_VERSION;
            sourceIds = List.of();
            sourceTypes = List.of();
            log.info("No deployment knowledge source config file configured.");
            return;
        }

        Resource resource = RuntimeDeploymentResolvedConfigSupport.resolveResource(resourceLoader, location);
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("Deployment knowledge source config not found: " + location);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode loaded = RuntimeDeploymentResolvedConfigSupport.readConfig(location, inputStream, objectMapper);
            ObjectNode sanitized = sanitize(loaded);
            root = sanitized;
            contractVersion = sanitized.path("contractVersion").asText(CONTRACT_VERSION);
            sourceIds = readStringList(sanitized.path("sources"), "id");
            sourceTypes = readStringList(sanitized.path("sources"), "type");
            log.info(
                "Loaded deployment knowledge source config from {} with {} source(s).",
                location,
                sourceIds.size()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load deployment knowledge source config from " + location, ex);
        }
    }

    public JsonNode currentRoot() {
        return root;
    }

    public String currentContractVersion() {
        return contractVersion;
    }

    public List<String> currentSourceIds() {
        return sourceIds;
    }

    public List<String> currentSourceTypes() {
        return sourceTypes;
    }

    public int currentSourceCount() {
        return sourceIds.size();
    }

    private ObjectNode sanitize(JsonNode candidate) {
        ObjectNode sanitized = defaultConfig(candidate);
        JsonNode sources = candidate != null ? candidate.path("sources") : null;
        if (sources instanceof ArrayNode arrayNode) {
            sanitized.set("sources", arrayNode.deepCopy());
        }
        return sanitized;
    }

    private ObjectNode defaultConfig(JsonNode candidate) {
        ObjectNode root = objectMapper.createObjectNode();
        String effectiveContractVersion = candidate != null && candidate.path("contractVersion").isTextual()
            ? candidate.path("contractVersion").asText().trim()
            : CONTRACT_VERSION;
        root.put("contractVersion", StringUtils.hasText(effectiveContractVersion) ? effectiveContractVersion : CONTRACT_VERSION);
        root.set("sources", objectMapper.createArrayNode());
        return root;
    }

    private List<String> readStringList(JsonNode sourceEntries, String field) {
        if (!(sourceEntries instanceof ArrayNode arrayNode)) {
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
