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
        assertThat(result.query()).isEqualTo(section.intent + " for this resource.");
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
        assertThat(AuthoringTaskIntent.resolve("ACTIONS", hint, "PREPARE", null).query()).startsWith(hint);
        assertThat(AuthoringTaskIntent.resolve("ACTIONS", null, "PREPARE", null).query()).doesNotContain("Optional focus");
    }
    @Test void actionPreparationIsAUserCommandNotASecondJavaSystemPrompt() {
        var result = AuthoringTaskIntent.resolve("ACTIONS", "Only the availability endpoint.", "PREPARE", null);
        assertThat(result.query()).startsWith("Only the availability endpoint.")
            .contains(AuthoringTaskIntent.Section.ACTIONS.intent)
            .doesNotContain("do not ask", "never its secret value", "Explicitly public/no-auth");
        var template = new ConfigurationAssistantTemplate(new com.fasterxml.jackson.databind.ObjectMapper()).behavior();
        assertThat(template.at("/workflowAssistance/profiles/0/instructions").asText())
            .contains("Public/no-auth operations need no credential", "A single documented operation is sufficient");
        assertThat(result.query()).doesNotContain("Optional focus");
        assertThat(AuthoringTaskIntent.resolve("ACTIONS", "Explain this route only.", null, null).query())
            .isEqualTo("Explain this route only.");
        assertThat(AuthoringTaskIntent.resolve("MODELS", "", "PREPARE", null).query())
            .doesNotContain("ALL_SELECTED_SOURCE_OPERATIONS", "operation scope", "undocumented operations");
    }
    @Test void explicitPreparationRequirementsPrecedeGenericPageGuidanceWithoutChangingAuthority() {
        String request = "Save the initial lookup, read it back, then separately rename it and save again.";
        var result = AuthoringTaskIntent.resolve("ACTIONS", request, "PREPARE",
            new AuthoringTaskIntent.EditorContext("ACTIONS", List.of("product_lookup")));
        assertThat(result.query()).isEqualTo(request + "\n\nCurrent editor task:\n"
            + AuthoringTaskIntent.Section.ACTIONS.intent + " for this resource.");
        assertThat(result.editorContext().section()).isEqualTo("ACTIONS");
        assertThat(result.editorContext().selectedDefinitionIds()).containsExactly("product_lookup");
    }
    @Test void apiPreparationExplicitlyRequestsCreationAndDraftSaveWithoutSelectingTheActionSequence() {
        for (String task : List.of("ACTIONS", "CONNECTIONS")) {
            var prepared = AuthoringTaskIntent.resolve(task, "", "PREPARE", null);
            assertThat(prepared.query()).startsWith("Create or update").contains("save the requested changes", "when not preview-only")
                .doesNotContain("authoring_check_candidate", "authoring_update_draft", "publish", "provision");
        }
        assertThat(AuthoringTaskIntent.resolve("ACTIONS", "Explain the endpoint only.", null, null).query())
            .isEqualTo("Explain the endpoint only.");
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
        var context = AuthoringTaskIntent.resolve("SEARCH", "", "PREPARE", new AuthoringTaskIntent.EditorContext("SEARCH", ids));
        ids.clear();
        assertThat(context.query()).startsWith("Prepare supported embedding");
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
