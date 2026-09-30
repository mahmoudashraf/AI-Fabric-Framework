package com.ai.fabric.runtime;

import ai.fabric.config.OrchestrationProperties;
import ai.fabric.curated.CuratedPackEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeReadActionResolutionConfigurationTest {

    @Test
    void deploymentRetrievalAllowlistBindsForExecutorMode() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
            "deployment-runtime-env",
            Map.of(
                "LOOMAI_RUNTIME_RETRIEVAL_VECTOR_SPACES_ALLOWLIST",
                "dealer-vehicle,dealer-policy",
                "ai.curated.pack",
                "default"
            )
        ));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("runtime-application", new ClassPathResource("application.yml"))
            .forEach(environment.getPropertySources()::addLast);
        new CuratedPackEnvironmentPostProcessor()
            .postProcessEnvironment(environment, new SpringApplication(Object.class));

        OrchestrationProperties properties = Binder.get(environment)
            .bind("ai.orchestration", OrchestrationProperties.class)
            .orElseThrow(() -> new IllegalStateException("Runtime orchestration configuration was not bound"));

        assertThat(properties.getModes().get("executor").getRag().getRetrievalVectorSpacesAllowlist())
            .containsExactly("dealer-vehicle", "dealer-policy");
    }

    @Test
    void deploymentReadActionAllowlistBindsAcrossCommerceModes() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
            "deployment-runtime-env",
            Map.of(
                "LOOMAI_RUNTIME_READ_ACTION_RESOLUTION_ALLOWED_ACTIONS",
                "shopify_get_cart,shopify_search_catalog",
                "ai.curated.pack",
                "commerce"
            )
        ));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("runtime-application", new ClassPathResource("application.yml"))
            .forEach(environment.getPropertySources()::addLast);
        new CuratedPackEnvironmentPostProcessor()
            .postProcessEnvironment(environment, new SpringApplication(Object.class));

        OrchestrationProperties properties = Binder.get(environment)
            .bind("ai.orchestration", OrchestrationProperties.class)
            .orElseThrow(() -> new IllegalStateException("Runtime orchestration configuration was not bound"));

        List<String> expected = List.of("shopify_get_cart", "shopify_search_catalog");
        for (String mode : List.of(
            "navigator_deep",
            "executor",
            "cart_assistant",
            "resolver_assistant",
            "thinker"
        )) {
            assertThat(properties.getModes().get(mode).getReadActionResolution().getAllowedReadActions())
                .as("deployment read-action allowlist for %s", mode)
                .containsExactlyElementsOf(expected);
        }
    }

    @Test
    void deploymentExecutorPolicyOverridesCuratedDefaults() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
            "deployment-runtime-env",
            Map.of(
                "AI_ORCHESTRATION_MODES_EXECUTOR_READ_ACTION_RESOLUTION_PLANNING_MODE",
                "ITERATIVE",
                "AI_ORCHESTRATION_MODES_EXECUTOR_READ_ACTION_RESOLUTION_MAX_ITERATIONS",
                "2",
                "AI_ORCHESTRATION_MODES_EXECUTOR_READ_ACTION_RESOLUTION_RAG_COOPERATION_MODE",
                "RAG_IF_ACTIONS_INSUFFICIENT",
                "ai.curated.pack",
                "default"
            )
        ));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("runtime-application", new ClassPathResource("application.yml"))
            .forEach(environment.getPropertySources()::addLast);
        new CuratedPackEnvironmentPostProcessor()
            .postProcessEnvironment(environment, new SpringApplication(Object.class));

        OrchestrationProperties properties = Binder.get(environment)
            .bind("ai.orchestration", OrchestrationProperties.class)
            .orElseThrow(() -> new IllegalStateException("Runtime orchestration configuration was not bound"));
        OrchestrationProperties.ReadActionResolutionModeOverrides executor =
            properties.getModes().get("executor").getReadActionResolution();

        assertThat(executor.getPlanningMode())
            .isEqualTo(OrchestrationProperties.ReadActionResolutionPlanningMode.ITERATIVE);
        assertThat(executor.getMaxIterations()).isEqualTo(2);
        assertThat(executor.getRagCooperationMode())
            .isEqualTo(OrchestrationProperties.ReadActionResolutionRagCooperationMode.RAG_IF_ACTIONS_INSUFFICIENT);
    }
}
