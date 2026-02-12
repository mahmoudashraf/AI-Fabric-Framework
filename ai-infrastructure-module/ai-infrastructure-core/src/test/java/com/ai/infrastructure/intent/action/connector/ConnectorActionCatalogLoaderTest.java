package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.ActionAccessMode;
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
        assertThat(action.accessMode()).isEqualTo(ActionAccessMode.WRITE_ONLY);
        assertThat(action.requiresConfirmation()).isTrue();
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
}

