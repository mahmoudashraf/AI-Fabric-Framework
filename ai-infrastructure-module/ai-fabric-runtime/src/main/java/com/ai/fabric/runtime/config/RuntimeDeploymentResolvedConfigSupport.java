package com.ai.fabric.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;

final class RuntimeDeploymentResolvedConfigSupport {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private RuntimeDeploymentResolvedConfigSupport() {
    }

    static Resource resolveResource(ResourceLoader resourceLoader, String location) {
        String trimmed = location == null ? "" : location.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        if (ResourceUtils.isUrl(trimmed) || trimmed.startsWith("classpath:") || trimmed.startsWith("file:")) {
            return resourceLoader.getResource(trimmed);
        }
        return resourceLoader.getResource("classpath:" + trimmed);
    }

    static JsonNode readConfig(String location,
                               InputStream inputStream,
                               ObjectMapper jsonMapper) throws Exception {
        String normalized = location == null ? "" : location.toLowerCase();
        if (normalized.endsWith(".yml") || normalized.endsWith(".yaml")) {
            return YAML_MAPPER.readTree(inputStream);
        }
        return jsonMapper.readTree(inputStream);
    }
}
