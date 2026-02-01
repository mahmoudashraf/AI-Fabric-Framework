package com.ai.curated.catalog;

import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.config.PromptBundleProperties;
import com.ai.infrastructure.curated.CuratedPackEnvironmentPostProcessor;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationProfile;
import com.ai.infrastructure.prompt.ClasspathPromptTemplateStore;
import com.ai.infrastructure.prompt.PromptTemplateResolver;
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

        assertThat(props.getProfile()).isEqualTo(OrchestrationProfile.PRODUCTION_NAVIGATOR);
        assertThat(props.getModes()).containsKey("navigator");
        assertThat(props.getModes()).containsKey("navigator_deep");
        assertThat(props.getModes().get("navigator_deep").getUseAdvancedRag()).isEqualTo(true);

        PromptBundleProperties promptBundle = Binder.get(environment)
            .bind("ai.prompts.bundle", PromptBundleProperties.class)
            .orElseThrow(() -> new IllegalStateException("Failed to bind ai.prompts.bundle"));
        assertThat(promptBundle.getOverlays()).contains("v1-catalog");

        PromptTemplateResolver resolver = new PromptTemplateResolver(
            new ClasspathPromptTemplateStore(new DefaultResourceLoader()),
            promptBundle
        );
        assertThat(resolver.resolve("intent-extraction/multi-step", "classify").template().key().version()).isEqualTo("v1-catalog");
    }
}
