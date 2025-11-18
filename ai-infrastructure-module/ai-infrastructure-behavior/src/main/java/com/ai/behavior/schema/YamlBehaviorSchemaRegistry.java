package com.ai.behavior.schema;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class YamlBehaviorSchemaRegistry implements BehaviorSchemaRegistry {

    private final BehaviorModuleProperties properties;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, BehaviorSignalDefinition> definitions = new ConcurrentHashMap<>();

    public YamlBehaviorSchemaRegistry(BehaviorModuleProperties properties) {
        this.properties = properties;
        objectMapper.findAndRegisterModules();
    }

    @PostConstruct
    public void loadDefinitions() {
        definitions.clear();
        var schemaProps = properties.getSchemas();
        String location = schemaProps.getPath();
        try {
            Resource[] resources = resolver.getResources(location);
            if ((resources == null || resources.length == 0) && schemaProps.isFailOnStartupIfMissing()) {
                throw new SchemaValidationException("No behavior schema files found at " + location);
            }
            if (resources != null) {
                for (Resource resource : resources) {
                    parseResource(resource);
                }
            }
            log.info("Loaded {} behavior schema definitions", definitions.size());
        } catch (IOException ex) {
            throw new SchemaValidationException("Failed to load behavior schema definitions", ex);
        }
    }

    private void parseResource(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            var parsed = objectMapper.readValue(inputStream, new TypeReference<Collection<BehaviorSignalDefinition>>() { });
            if (parsed == null) {
                return;
            }
            parsed.forEach(this::registerDefinition);
        }
    }

    private void registerDefinition(BehaviorSignalDefinition definition) {
        if (definition == null || !StringUtils.hasText(definition.getId())) {
            throw new SchemaValidationException("Behavior schema id must be provided");
        }
        definitions.put(definition.getId(), definition);
    }

    @Override
    public Optional<BehaviorSignalDefinition> find(String schemaId) {
        if (!StringUtils.hasText(schemaId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(schemaId));
    }

    @Override
    public BehaviorSignalDefinition getRequired(String schemaId) {
        return find(schemaId).orElseThrow(() ->
            new SchemaValidationException("Unknown behavior schema id: " + schemaId));
    }

    @Override
    public Collection<BehaviorSignalDefinition> getAll() {
        return Collections.unmodifiableCollection(definitions.values());
    }
}
