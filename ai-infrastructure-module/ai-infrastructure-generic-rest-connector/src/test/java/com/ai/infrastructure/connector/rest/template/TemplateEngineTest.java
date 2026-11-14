package com.ai.infrastructure.connector.rest.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TemplateEngineTest {

    private final TemplateEngine engine = new TemplateEngine();

    @Test
    void pathUsesExistingNestedAndIndexedPlaceholderGrammar() {
        assertThat(engine.resolvePath("/v1/{{ params.items[0].type }}/item-{{params.items[0].id}}.json", Map.of(
            "params", Map.of("items", List.of(Map.of("type", "a/b", "id", 42)))
        ))).isEqualTo("/v1/a%2Fb/item-42.json");
    }

    @Test
    void pathPreservesSuppliedScalarInsteadOfApplyingNumericDefault() {
        assertThat(engine.resolvePath("/v1/products/{{params.id|1}}", Map.of(
            "params", Map.of("id", 0)
        ))).isEqualTo("/v1/products/0");
        assertThat(engine.resolvePath("/v1/products/{{params.id}}", Map.of(
            "params", Map.of("id", false)
        ))).isEqualTo("/v1/products/false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/products/{{params.id}}", "/v1/products/{{params.id|invented-id}}"})
    void pathDoesNotInventMissingIdentifier(String path) {
        assertThatIllegalArgumentException().isThrownBy(() -> engine.resolvePath(path, Map.of("params", Map.of())))
            .withMessageContaining("params.id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\r\n", "\u2003", "\u3000"})
    void pathRejectsBlankScalarInsteadOfResolvingToCollectionOrRoot(String id) {
        for (String path : List.of("/{{params.id}}", "/v1/products/{{params.id}}", "/v1/products/{{params.id|invented-id}}")) {
            assertThatIllegalArgumentException().isThrownBy(() -> engine.resolvePath(path, Map.of("params", Map.of("id", id))))
                .withMessageContaining("params.id")
                .withMessageContaining("nonblank");
        }
    }

    @Test
    void pathPreservesWhitespaceInNonblankIdentifier() {
        assertThat(engine.resolvePath("/v1/products/{{params.id}}", Map.of("params", Map.of("id", " HBR-123 "))))
            .isEqualTo("/v1/products/%20HBR-123%20");
    }

    @Test
    void generalTemplatesKeepExistingTypesAndDefaults() {
        Map<String, Object> params = Map.of("id", "a/b", "quantity", 0);
        assertThat(engine.resolve("{{params}}", Map.of("params", params))).isSameAs(params);
        assertThat(engine.resolve("{{params.quantity|1}}", Map.of("params", params))).isEqualTo(1);
        assertThat(engine.resolve("value={{params.missing|default}}", Map.of("params", params)))
            .isEqualTo("value=default");
    }
}
