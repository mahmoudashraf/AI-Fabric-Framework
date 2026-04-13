package com.ai.infrastructure.intent.action.connector.registry.service;

import com.ai.infrastructure.intent.action.AIActionParamType;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.connector.ConnectorActionDefinition;
import com.ai.infrastructure.intent.action.connector.ConnectorActionParamDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorActionDefinitionValidatorTest {

    private final ConnectorActionDefinitionValidator validator = new ConnectorActionDefinitionValidator();

    @Test
    void validate_rejectsMissingName() {
        ConnectorActionDefinition def = new ConnectorActionDefinition(
            null,
            "desc",
            "cat",
            ActionAccessMode.READ,
            false,
            null,
            List.of(),
            false
        );

        assertThatThrownBy(() -> validator.validate(def))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_rejectsDuplicateParamNames() {
        ConnectorActionDefinition def = new ConnectorActionDefinition(
            "a",
            "desc",
            "cat",
            ActionAccessMode.READ,
            false,
            null,
            List.of(
                param("sku"),
                param("SKU")
            ),
            false
        );

        assertThatThrownBy(() -> validator.validate(def))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate");
    }

    @Test
    void validate_rejectsConfirmationTemplateUnknownPlaceholder() {
        ConnectorActionDefinition def = new ConnectorActionDefinition(
            "a",
            "desc",
            "cat",
            ActionAccessMode.WRITE_ONLY,
            true,
            "Create order for {{missing}}?",
            List.of(param("sku")),
            false
        );

        assertThatThrownBy(() -> validator.validate(def))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("placeholder");
    }

    private ConnectorActionParamDefinition param(String name) {
        return new ConnectorActionParamDefinition(
            name,
            "SKU",
            AIActionParamType.STRING,
            true,
            false,
            null,
            List.of(),
            null,
            null,
            false
        );
    }
}
