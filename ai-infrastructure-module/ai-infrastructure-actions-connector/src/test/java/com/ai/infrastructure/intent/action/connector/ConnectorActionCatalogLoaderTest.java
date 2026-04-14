package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionResultPresentationHint;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorActionCatalogLoaderTest {

    @Test
    void loadActions_shouldLoadValidContract() {
        ConnectorActionCatalogLoader loader = new ConnectorActionCatalogLoader(new DefaultResourceLoader());

        AIActionCatalogProperties.ActionSourceProperties source = new AIActionCatalogProperties.ActionSourceProperties();
        source.setType(AIActionCatalogProperties.ActionSourceType.FILE);
        source.setPath("classpath:actions/valid-actions.yml");

        List<ConnectorActionDefinition> actions = loader.loadActions(List.of(source));
        assertThat(actions).hasSize(1);

        ConnectorActionDefinition action = actions.get(0);
        assertThat(action.name()).isEqualTo("create_purchase_order");
        assertThat(action.displayName()).isEqualTo("Create Purchase Order");
        assertThat(action.accessMode()).isEqualTo(ActionAccessMode.WRITE_ONLY);
        assertThat(action.anonymousAllowed()).isTrue();
        assertThat(action.requiresConfirmation()).isTrue();
        assertThat(action.groundingEligible()).isFalse();
        assertThat(action.resultPresentationHint()).isEqualTo(ActionResultPresentationHint.STATUS);
        assertThat(action.builtInModuleId()).isEqualTo("purchase-orders");
        assertThat(action.builtInCardId()).isEqualTo("purchase-order-status");
        assertThat(action.provenance()).isNotNull();
        assertThat(action.provenance().getSourceType()).isEqualTo("ACTION_CATALOG");
        assertThat(action.provenance().getPublisher()).isEqualTo("internal-test");
        assertThat(action.confirmationMessage()).contains("{{quantity}}").contains("{{sku}}");
        assertThat(action.params()).hasSize(2);
        assertThat(action.params().stream().anyMatch(p -> "sku".equals(p.name()) && p.required())).isTrue();
    }

    @Test
    void loadActions_shouldFailFastOnUnknownTemplatePlaceholder() {
        ConnectorActionCatalogLoader loader = new ConnectorActionCatalogLoader(new DefaultResourceLoader());

        AIActionCatalogProperties.ActionSourceProperties source = new AIActionCatalogProperties.ActionSourceProperties();
        source.setType(AIActionCatalogProperties.ActionSourceType.FILE);
        source.setPath("classpath:actions/invalid-placeholder.yml");

        assertThatThrownBy(() -> loader.loadActions(List.of(source)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("confirmationMessage placeholder");
    }

    @Test
    void loadActions_shouldFailFastOnMissingFile_whenNotOptional() {
        ConnectorActionCatalogLoader loader = new ConnectorActionCatalogLoader(new DefaultResourceLoader());

        AIActionCatalogProperties.ActionSourceProperties source = new AIActionCatalogProperties.ActionSourceProperties();
        source.setType(AIActionCatalogProperties.ActionSourceType.FILE);
        source.setPath("classpath:actions/does-not-exist.yml");

        assertThatThrownBy(() -> loader.loadActions(List.of(source)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Action contract file not found");
    }

    @Test
    void loadActions_shouldSkipMissingFile_whenOptional() {
        ConnectorActionCatalogLoader loader = new ConnectorActionCatalogLoader(new DefaultResourceLoader());

        AIActionCatalogProperties.ActionSourceProperties source = new AIActionCatalogProperties.ActionSourceProperties();
        source.setType(AIActionCatalogProperties.ActionSourceType.FILE);
        source.setPath("classpath:actions/does-not-exist.yml");
        source.setOptional(true);

        List<ConnectorActionDefinition> actions = loader.loadActions(List.of(source));
        assertThat(actions).isEmpty();
    }

    @Test
    void loadCatalog_shouldLoadConfirmationInterceptors() {
        ConnectorActionCatalogLoader loader = new ConnectorActionCatalogLoader(new DefaultResourceLoader());

        AIActionCatalogProperties.ActionSourceProperties source = new AIActionCatalogProperties.ActionSourceProperties();
        source.setType(AIActionCatalogProperties.ActionSourceType.FILE);
        source.setPath("classpath:actions/valid-actions-with-confirmation-interceptors.yml");

        ConnectorActionCatalog catalog = loader.loadCatalog(List.of(source));

        assertThat(catalog.actions()).hasSize(2);
        assertThat(catalog.confirmationInterceptors()).hasSize(1);
        assertThat(catalog.confirmationInterceptors().getFirst().name()).isEqualTo("cancel_to_retention_offer");
        assertThat(catalog.confirmationInterceptors().getFirst().decision().action()).isEqualTo("offer_order_discount");
    }

    @Test
    void loadCatalog_shouldRejectPromptActionTargetThatDoesNotRequireConfirmation() {
        ConnectorActionCatalogLoader loader = new ConnectorActionCatalogLoader(new DefaultResourceLoader());

        AIActionCatalogProperties.ActionSourceProperties source = new AIActionCatalogProperties.ActionSourceProperties();
        source.setType(AIActionCatalogProperties.ActionSourceType.FILE);
        source.setPath("classpath:actions/invalid-confirmation-interceptors.yml");

        assertThatThrownBy(() -> loader.loadCatalog(List.of(source)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PROMPT_ACTION")
            .hasMessageContaining("non-confirmable action");
    }

    @Test
    void loadActions_shouldRejectUnsupportedBuiltInShellMappings() {
        ConnectorActionCatalogLoader loader = new ConnectorActionCatalogLoader(new DefaultResourceLoader());

        AIActionCatalogProperties.ActionSourceProperties source = new AIActionCatalogProperties.ActionSourceProperties();
        source.setType(AIActionCatalogProperties.ActionSourceType.FILE);
        source.setPath("classpath:actions/invalid-built-in-shell-mapping.yml");

        assertThatThrownBy(() -> loader.loadActions(List.of(source)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unsupported builtInModuleId");
    }
}
