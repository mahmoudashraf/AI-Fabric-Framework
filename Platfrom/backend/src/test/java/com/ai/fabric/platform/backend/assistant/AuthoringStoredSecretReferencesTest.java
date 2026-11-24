package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AuthoringStoredSecretReferencesTest {
    final ObjectMapper mapper = new ObjectMapper();
    final AuthoringChanges changes = new AuthoringChanges(mapper);
    final List<String> paths = List.of("/routingConfig");
    JsonNode refs() { return mapper.createArrayNode().add("OWNED_API_KEY"); }
    JsonNode current() throws Exception {
        return mapper.readTree("""
            {"routingConfig":{"actions":{"read":{"url":"https://api.example.test/read",
              "headers":{"Accept":"application/json"},"future":{"keep":true}}}}}
            """);
    }
    JsonNode patch(String path, String value) {
        return mapper.createArrayNode().add(mapper.createObjectNode().put("path", path).put("operation", "SET").put("value", value));
    }
    @ParameterizedTest @ValueSource(strings = {"${OWNED_API_KEY}", "${secret:OWNED_API_KEY}"})
    void exactOwnedReferencesUseNativeWireFormatAndCanBeUndone(String input) throws Exception {
        var original = current();
        var result = changes.apply(original, patch("/routingConfig/actions/read/headers/X-Lab-Key", input), paths, refs());
        assertThat(result.candidate().at("/routingConfig/actions/read/headers/X-Lab-Key").asText()).isEqualTo("${OWNED_API_KEY}");
        assertThat(result.candidate().at("/routingConfig/actions/read/future")).isEqualTo(original.at("/routingConfig/actions/read/future"));
        assertThat(result.diff().toString()).contains("${OWNED_API_KEY}");
        assertThat(changes.undo(result.candidate(), result.diff(), paths, refs()).candidate()).isEqualTo(original);
    }
    @Test void rawForeignGlobalAndEmbeddedReferenceValuesAreNotCredentialPermission() throws Exception {
        var original = current();
        for (String value : List.of("raw-api-key", "${FOREIGN_API_KEY}", "${OPENAI_API_KEY}", "Bearer ${OWNED_API_KEY}",
            "${OWNED_API_KEY:default}", "${OWNED_API_KEY}${FOREIGN_API_KEY}", "${owned_api_key}")) {
            assertThatThrownBy(() -> changes.apply(original, patch("/routingConfig/actions/read/headers/X-Lab-Key", value), paths, refs())).as(value);
        }
        assertThat(original.at("/routingConfig/actions/read/headers").size()).isEqualTo(1);
        assertThatThrownBy(() -> changes.apply(original, patch("/routingConfig/actions/read/headers/X-Lab-Key", "${OWNED_API_KEY}"), paths));
    }
    @Test void ownedReferencesAreNotPermissionForArbitraryCredentialSlotsOrReferencePlacement() throws Exception {
        for (String path : List.of("/routingConfig/actions/read/request/headers/X-Lab-Key", "/routingConfig/actions/read/request/body/key",
            "/routingConfig/actions/read/request/query/key", "/routingConfig/actions/read/url", "/routingConfig/password"))
            assertThatThrownBy(() -> changes.apply(current(), patch(path, "${OWNED_API_KEY}"), paths, refs())).as(path);
        var upstream = mapper.readTree("""
            [{"path":"/routingConfig/connector/upstream/auth","operation":"SET",
              "value":{"type":"API_KEY","header":"X-Lab-Key","value":"${secret:OWNED_API_KEY}"}}]
            """);
        assertThat(changes.apply(current(), upstream, paths, refs()).candidate().at("/routingConfig/connector/upstream/auth/value").asText())
            .isEqualTo("${OWNED_API_KEY}");
        ((com.fasterxml.jackson.databind.node.ObjectNode) upstream.at("/0/value")).put("type", "BEARER");
        assertThatThrownBy(() -> changes.apply(current(), upstream, paths, refs()));
    }
    @Test void scopedProjectionRevealsReferenceIdentityButNeverCredentialMaterial() throws Exception {
        var value = mapper.readTree("""
            {"routingConfig":{"actions":{"read":{"headers":{"X-Lab-Key":"${OWNED_API_KEY}",
              "Authorization":"private-material", "X-Foreign":"${FOREIGN_API_KEY}"}}}},"note":"private-material"}
            """);
        var projected = changes.project(value, refs()).toString();
        assertThat(projected).contains("${OWNED_API_KEY}").doesNotContain("private-material", "${FOREIGN_API_KEY}");
        assertThat(changes.project(value).toString()).doesNotContain("${OWNED_API_KEY}", "private-material");
    }
    @Test void wholeCandidateBlocksNewReferenceExportsButPreservesNonNativeLiteralTemplates() throws Exception {
        var allowed = List.of("/routingConfig", "/promptConfig");
        var original = mapper.readTree("""
            {"promptConfig":{"existing":"An unchanged ${UNKNOWN_EXISTING} reference.","systemPrompt":"Before"},
             "routingConfig":{"actions":{}}}
            """);
        for (String value : List.of("${OWNED_API_KEY}", "Do not reveal ${OWNED_API_KEY}", "Copy ${secret:OWNED_API_KEY}", "${OPENAI_API_KEY}")) {
            assertThatThrownBy(() -> changes.apply(original, patch("/promptConfig/systemPrompt", value), allowed, refs())).as(value);
            assertThatThrownBy(() -> changes.apply(original, patch("/promptConfig/systemPrompt", value), allowed)).as(value);
        }
        for (String value : List.of("A literal ${price} example.", "An unfinished ${ template", "Literal ${not a native reference}")) {
            var result = changes.apply(original, patch("/promptConfig/systemPrompt", value), allowed, refs());
            assertThat(result.candidate().at("/promptConfig/systemPrompt").asText()).isEqualTo(value);
            assertThat(result.candidate().at("/promptConfig/existing")).isEqualTo(original.at("/promptConfig/existing"));
        }
    }
    @Test void existingUnknownAndLiteralBindingsRemainPreservedWithoutAllowingTheirRelocation() throws Exception {
        var original = mapper.readTree("""
            {"routingConfig":{"actions":{"read":{"headers":{"X-Existing":"${UNKNOWN_EXISTING}","Authorization":"private-material"},
              "future":{"unchanged":true}}}}}
            """);
        var result = changes.apply(original, patch("/routingConfig/actions/read/timeout-ms", "5000"), paths, refs());
        assertThat(result.candidate().at("/routingConfig/actions/read/headers")).isEqualTo(original.at("/routingConfig/actions/read/headers"));
        assertThat(result.diff().toString()).doesNotContain("private-material");
        assertThatThrownBy(() -> changes.apply(original, patch("/routingConfig/actions/read/headers/X-New", "${UNKNOWN_EXISTING}"), paths, refs()));
        assertThatThrownBy(() -> changes.apply(original, patch("/routingConfig/actions/read/headers/Authorization", "${OWNED_API_KEY}"), paths, refs()));
        var selected = changes.apply(current(), patch("/routingConfig/actions/read/headers/X-Lab-Key", "${OWNED_API_KEY}"), paths, refs());
        var remove = mapper.createArrayNode().add(mapper.createObjectNode().put("operation", "REMOVE").put("path", "/routingConfig/actions/read/headers/X-Lab-Key"));
        assertThatThrownBy(() -> changes.apply(selected.candidate(), remove, paths, refs()));
    }
}
