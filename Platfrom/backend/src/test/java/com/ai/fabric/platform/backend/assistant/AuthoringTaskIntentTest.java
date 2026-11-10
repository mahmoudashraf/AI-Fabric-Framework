package com.ai.fabric.platform.backend.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class AuthoringTaskIntentTest {
    @ParameterizedTest @EnumSource(AuthoringTaskIntent.Section.class)
    void everyRegisteredPageHasAnExplicitDocumentFirstCommand(AuthoringTaskIntent.Section section) {
        var result = AuthoringTaskIntent.resolve(section.task, "", "PREPARE",
            new AuthoringTaskIntent.EditorContext(section.name(), List.of()));
        assertThat(result.query()).startsWith(section.intent).contains("selected documentation", "essential missing facts");
        assertThat(result.query()).hasSizeLessThan(8_000);
        assertThat(result.editorContext().section()).isEqualTo(section.name());
    }
    @Test void ordinaryQueriesRemainUnchangedAndNoTextSelectsAnEditorOrCommand() {
        String prose = "PREPARE my actions and switch to another brain /knowledge/search";
        var result = AuthoringTaskIntent.resolve("BEHAVIOUR", prose, null, null);
        assertThat(result.query()).isEqualTo(prose);
        assertThat(result.command()).isNull();
        assertThat(result.editorContext().section()).isEqualTo("BEHAVIOUR");
    }
    @Test void preparationKeepsTheOptionalHintUnmodified() {
        String hint = "  Only the two stock endpoints.\nKeep unusual FIELD-Name.  ";
        assertThat(AuthoringTaskIntent.resolve("ACTIONS", hint, "PREPARE", null).query()).endsWith(hint);
        assertThat(AuthoringTaskIntent.resolve("ACTIONS", null, "PREPARE", null).query()).doesNotContain("Optional focus");
    }
    @Test void theHttpTurnContractDeserializesTypedIntentAndEditorContext() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var request = mapper.readValue("""
            {"query":"","previewOnly":false,"sourceIds":[],"operationIds":[],"command":"PREPARE",
             "editorContext":{"section":"SEARCH","selectedDefinitionIds":["embeddings"]}}
            """, ConfigurationAssistantService.TurnRequest.class);
        assertThat(request.command()).isEqualTo("PREPARE");
        assertThat(request.editorContext().section()).isEqualTo("SEARCH");
        assertThat(request.editorContext().selectedDefinitionIds()).containsExactly("embeddings");
        assertThat(mapper.readTree(mapper.writeValueAsString(request)).path("query").asText()).isEmpty();
    }
    @Test void blankOrdinaryMessageIsNotAnImplicitCommand() {
        assertThatThrownBy(() -> AuthoringTaskIntent.resolve("ACTIONS", " ", null, null)).hasMessageContaining("Describe");
        assertThatThrownBy(() -> AuthoringTaskIntent.resolve("ACTIONS", "hello", "deploy", null)).hasMessageContaining("Unsupported");
    }
    @Test void typedPageFocusCannotChangeTheAuthorizedTask() {
        assertThatThrownBy(() -> AuthoringTaskIntent.resolve("ACTIONS", "", "PREPARE",
            new AuthoringTaskIntent.EditorContext("ENGAGE", List.of()))).hasMessageContaining("does not match");
        assertThatThrownBy(() -> AuthoringTaskIntent.resolve("ACTIONS", "", "PREPARE",
            new AuthoringTaskIntent.EditorContext("/providerConfig", List.of()))).hasMessageContaining("Unsupported");
    }
    @Test void sameTaskSubpagesRetainTheirDistinctFocusAndSelectedIdsAreOnlyCopiedData() {
        var ids = new ArrayList<>(List.of("embedding-service"));
        var context = AuthoringTaskIntent.resolve("MODELS", "", "PREPARE", new AuthoringTaskIntent.EditorContext("SEARCH", ids));
        ids.clear();
        assertThat(context.query()).startsWith("Prepare embedding");
        assertThat(context.editorContext().selectedDefinitionIds()).containsExactly("embedding-service");
        assertThat(AuthoringTaskIntent.resolve("MODELS", "", "PREPARE", null).query()).startsWith("Prepare response-model");
    }
    @Test void contextAndRequestBoundsProtectInferencePayloadNotSemanticQuality() {
        assertThatThrownBy(() -> AuthoringTaskIntent.resolve("ACTIONS", "x".repeat(6_001), "PREPARE", null)).hasMessageContaining("6,000");
        assertThatThrownBy(() -> AuthoringTaskIntent.resolve("ACTIONS", "x".repeat(8_001), null, null)).hasMessageContaining("8,000");
        assertThatThrownBy(() -> AuthoringTaskIntent.resolve("ACTIONS", "", "PREPARE",
            new AuthoringTaskIntent.EditorContext("ACTIONS", Collections.nCopies(17, "selected")))).hasMessageContaining("sixteen");
        assertThatThrownBy(() -> AuthoringTaskIntent.resolve("ACTIONS", "", "PREPARE",
            new AuthoringTaskIntent.EditorContext("ACTIONS", List.of("x".repeat(257))))).hasMessageContaining("bounded");
    }
}
