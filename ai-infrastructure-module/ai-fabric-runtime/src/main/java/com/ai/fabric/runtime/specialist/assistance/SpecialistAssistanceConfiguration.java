package com.ai.fabric.runtime.specialist.assistance;

import ai.fabric.execution.config.AIExecutionProperties;
import ai.fabric.execution.specialist.manifest.DefaultSpecialistManifestLoader;
import ai.fabric.execution.specialist.manifest.SpecialistManifestLoader;
import com.ai.fabric.runtime.tasks.ManagedTasksBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SpecialistAssistanceConfiguration {
    @Bean
    SpecialistManifestLoader publishedSpecialistManifestLoader(ObjectMapper mapper, SpecialistAssistanceBundle bundle,
        ManagedTasksBundle tasks) {
        var official = new DefaultSpecialistManifestLoader(mapper);
        if (!bundle.enabled() && !tasks.enabled()) return official;
        return properties -> {
            try {
                var locations = new ArrayList<>(properties.getLocations());
                if (bundle.enabled()) materialize(mapper, "loomai-specialist-assistance", bundle.contentHash(),
                    bundle.content().path("resources"), locations);
                if (tasks.enabled()) materialize(mapper, "loomai-managed-tasks", tasks.contentHash(), tasks.resources(), locations);
                var effective = new AIExecutionProperties.Manifests();
                effective.setEnabled(true);
                effective.setFailFast(true);
                effective.setMaxManifestBytes(properties.getMaxManifestBytes());
                effective.setMaxResourceBytes(properties.getMaxResourceBytes());
                effective.setLocations(locations);
                return official.load(effective);
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Published specialist resources could not be materialized.", failure);
            }
        };
    }

    private static void materialize(ObjectMapper mapper, String directory, String hash,
        com.fasterxml.jackson.databind.JsonNode resources, java.util.List<String> locations) throws java.io.IOException {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), directory, hash.substring("sha256:".length()));
        Files.createDirectories(root);
        int index = 0;
        for (var resource : resources) {
            Path file = root.resolve("resource-" + index++ + ".json");
            byte[] bytes = mapper.writeValueAsBytes(resource);
            if (Files.exists(file)) {
                if (!java.util.Arrays.equals(Files.readAllBytes(file), bytes))
                    throw new IllegalStateException("Published specialist resource changed on disk.");
            } else {
                Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
            }
            locations.add(file.toUri().toString());
        }
    }
}
