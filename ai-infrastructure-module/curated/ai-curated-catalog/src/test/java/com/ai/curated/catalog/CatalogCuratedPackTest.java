package com.ai.curated.catalog;

import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.curated.CuratedPackEnvironmentPostProcessor;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationProfile;
import com.ai.infrastructure.prompt.ClasspathPromptTemplateStore;
import com.ai.infrastructure.prompt.PromptTemplate;
import com.ai.infrastructure.prompt.PromptTemplateKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogCuratedPackTest {

    @Test
    void shouldLoadCatalogPackDefaultsAndTemplates() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
            "ai.curated.pack", "catalog"
        )));

        new CuratedPackEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication(Object.class));

        OrchestrationProperties props = Binder.get(environment)
            .bind("ai.orchestration", OrchestrationProperties.class)
            .orElseThrow(() -> new IllegalStateException("Failed to bind ai.orchestration"));

        assertThat(props.getProfile()).isEqualTo(OrchestrationProfile.DEMO_CATALOG);
        assertThat(props.getModes()).containsKey("navigator");

        String version = environment.getProperty("ai.prompts.intent-extraction.multi-step.version");
        assertThat(version).isEqualTo("v1-catalog");

        ClasspathPromptTemplateStore store = new ClasspathPromptTemplateStore(new DefaultResourceLoader());
        PromptTemplate template = store.load(new PromptTemplateKey("intent-extraction/multi-step", version, "classify"));
        assertThat(template.template()).contains("requiresTargetResolution");
    }
}
