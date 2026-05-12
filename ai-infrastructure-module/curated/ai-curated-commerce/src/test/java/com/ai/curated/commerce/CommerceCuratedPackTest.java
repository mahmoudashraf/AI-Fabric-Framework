package com.ai.curated.commerce;

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

class CommerceCuratedPackTest {

    @Test
    void shouldLoadCommercePackDefaultsAndTemplates() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
            "ai.curated.pack", "commerce"
        )));

        new CuratedPackEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication(Object.class));

        OrchestrationProperties props = Binder.get(environment)
            .bind("ai.orchestration", OrchestrationProperties.class)
            .orElseThrow(() -> new IllegalStateException("Failed to bind ai.orchestration"));

        assertThat(props.getProfile()).isEqualTo(OrchestrationProfile.PRODUCTION_CHAT);
        assertThat(props.isAlwaysGenerateInformation()).isTrue();
        assertThat(props.getModes()).containsKey("navigator");
        assertThat(props.getModes()).containsKey("navigator_deep");
        assertThat(props.getModes().get("navigator_deep").getUseAdvancedRag()).isEqualTo(true);
        assertThat(props.getModes().get("navigator_deep").getReadActionResolution()).isNotNull();
        assertThat(props.getModes().get("navigator_deep").getReadActionResolution().getEnabled()).isTrue();
        assertThat(props.getModes().get("navigator_deep").getReadActionResolution().getAllowedReadActions())
            .contains("list_products", "search_products", "get_product_details", "check_availability", "get_policy", "view_cart")
            .doesNotContain("relationship_query", "find_similar_products", "compare_products");
        assertThat(props.getModes()).containsKey("cart_assistant");
        assertThat(props.getModes()).containsKey("resolver_assistant");
        assertThat(props.getModes()).containsKey("thinker");
        assertThat(props.getModes().get("resolver_assistant").getReadActionResolution()).isNotNull();
        assertThat(props.getModes().get("resolver_assistant").getReadActionResolution().getEnabled()).isTrue();
        assertThat(props.getModes().get("resolver_assistant").getReadActionResolution().getAllowedReadActions())
            .contains("list_products", "search_products", "get_product_details", "check_availability", "get_policy", "view_cart")
            .doesNotContain("relationship_query", "find_similar_products", "compare_products");
        assertThat(props.getModes().get("thinker").getReadActionResolution()).isNotNull();
        assertThat(props.getModes().get("thinker").getReadActionResolution().getPlanningMode())
            .isEqualTo(OrchestrationProperties.ReadActionResolutionPlanningMode.ITERATIVE);
        assertThat(props.getModes().get("thinker").getReadActionResolution().getAllowedReadActions())
            .contains("list_products", "search_products", "get_product_details", "check_availability", "get_policy", "view_cart")
            .doesNotContain("relationship_query", "find_similar_products", "compare_products");

        assertThat(environment.getProperty("ai.prompts.bundle.overlays[0]")).isEqualTo("v1-commerce");
        PromptBundleProperties promptBundle = Binder.get(environment)
            .bind("ai.prompts.bundle", PromptBundleProperties.class)
            .orElseGet(PromptBundleProperties::new);

        PromptTemplateResolver resolver = new PromptTemplateResolver(
            new ClasspathPromptTemplateStore(new DefaultResourceLoader()),
            promptBundle
        );
        var classifierTemplate = resolver.resolve("intent-extraction/multi-step", "classify").template();
        assertThat(classifierTemplate.key().version()).isEqualTo("v1-commerce");
        assertThat(classifierTemplate.template())
            .contains("asks about assistant implementation/infrastructure")
            .contains("Never use directAnswer to discuss assistant implementation")
            .contains("answer only the shopper-safe capability or use OUT_OF_SCOPE")
            .contains("Do not retrieve or substitute another product")
            .contains("I can help with this store's products, policies, comparisons, cart, and approved order help.")
            .contains("must not repeat or quote the unsupported topic/request");
        assertThat(resolver.resolve("rag/generation", "answer-managed").template().template())
            .contains("Use only the relevant commerce context above")
            .contains("Do not repeat labels such as \"Current page\"")
            .contains("Do not treat retrieved catalog/search results as the current product")
            .contains("treat, cure, diagnose, or prevent a health condition")
            .contains("asks for professional advice, asks whether a product can treat")
            .contains("no safest option can be identified from the available live store data")
            .contains("state that it is not available in the live store data")
            .contains("treat the explicit availability fact as the stock-status source of truth")
            .contains("Do not recommend checking another website, contacting support, contacting a vendor/manufacturer")
            .contains("Do not add next-step or handoff sentences for missing live data")
            .contains("Do not substitute price, availability, vendor, or product title as safety evidence")
            .contains("present explicit price, availability, variant, shipping-policy, review-signal")
            .contains("Do not append generic closers");
    }
}
