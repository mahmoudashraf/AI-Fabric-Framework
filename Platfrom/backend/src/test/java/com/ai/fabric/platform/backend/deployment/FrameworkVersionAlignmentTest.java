package com.ai.fabric.platform.backend.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class FrameworkVersionAlignmentTest {
    @Test void defaultPublishedVersionMatchesTheDeclaredRuntimeArtifact() throws Exception {
        var environment = new MockEnvironment();
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")))
            environment.getPropertySources().addLast(source);
        var declaration = Path.of(System.getProperty("basedir", "."))
            .resolve("../../ai-infrastructure-module/ai-fabric-runtime/src/main/resources/META-INF/loomai/runtime-capabilities.json");
        var manifest = new ObjectMapper().readTree(Files.readString(declaration));
        assertThat(environment.getProperty("platform.ai-fabric.framework-version"))
            .isEqualTo(manifest.path("aiFabricVersion").asText());
        environment.setProperty("AI_FABRIC_FRAMEWORK_VERSION", "0.8.68");
        assertThat(environment.getProperty("platform.ai-fabric.framework-version")).isEqualTo("0.8.68");
    }
}
