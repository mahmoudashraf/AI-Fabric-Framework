package com.ai.fabric.runtime.specialist.assistance;

import ai.fabric.execution.config.AIExecutionProperties;
import ai.fabric.execution.specialist.manifest.DefaultSpecialistManifestLoader;
import ai.fabric.execution.specialist.manifest.SpecialistManifestLoader;
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
    SpecialistManifestLoader publishedSpecialistManifestLoader(ObjectMapper mapper, SpecialistAssistanceBundle bundle) {
        var official = new DefaultSpecialistManifestLoader(mapper);
        if (!bundle.enabled()) return official;
        return properties -> {
            try {
                Path root = Path.of(System.getProperty("java.io.tmpdir"), "loomai-specialist-assistance",
                    bundle.contentHash().substring("sha256:".length()));
                Files.createDirectories(root);
                var locations = new ArrayList<>(properties.getLocations());
                int index = 0;
                for (var resource : bundle.content().path("resources")) {
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
}
