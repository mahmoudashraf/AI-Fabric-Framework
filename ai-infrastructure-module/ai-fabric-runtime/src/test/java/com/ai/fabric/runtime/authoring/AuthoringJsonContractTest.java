package com.ai.fabric.runtime.authoring;

import static com.ai.fabric.runtime.authoring.AuthoringTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthoringJsonContractTest {
    private final AuthoringJsonContract contract = new AuthoringJsonContract(MAPPER);

    @Test
    void preservesMultipleDefinitionsNestedArraysDynamicKeysNullsAndAllEnvelopeFields() throws Exception {
        assertThat(contract.strictResponse(output().toString(), request())).isEqualTo(output());
        var setNull = output();
        setNull.withArray("changes").removeAll().addObject().put("path", "/nullable").put("operation", "SET").putNull("value");
        assertThat(contract.strictResponse(setNull.toString(), request())).isEqualTo(setNull);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SET", "REMOVE"})
    void preservesUnusedNullKeysWithoutRewritingOperations(String operation) throws Exception {
        var raw = output();
        var change = raw.putArray("changes").addObject().put("path", "/setting")
            .put("operation", operation).putNull("key");
        if (operation.equals("SET")) change.put("value", 600);
        assertThat(contract.strictResponse(raw.toString(), request())).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPSERT", "REMOVE_ITEM"})
    void rejectsMissingNullBlankOrInvalidKeysForArrayOperations(String operation) throws Exception {
        var raw = output();
        var change = raw.putArray("changes").addObject().put("path", "/actions")
            .put("operation", operation);
        if (operation.equals("UPSERT")) change.putObject("value").put("name", "example");
        assertInvalid(raw.toString());
        change.putNull("key");
        assertInvalid(raw.toString());
        change.put("key", " \t\n");
        assertInvalid(raw.toString());
        change.put("key", 42);
        assertInvalid(raw.toString());
        change.put("key", "x".repeat(513));
        assertInvalid(raw.toString());
        change.put("key", "example");
        assertThat(contract.strictResponse(raw.toString(), request())).isEqualTo(raw);
    }

    @Test
    void schemaAndGuidanceRequireKeysOnlyForArrayOperations() throws Exception {
        var changeSchema = MAPPER.readTree(AuthoringInstructions.RESPONSE_SCHEMA)
            .path("properties").path("changes").path("items");
        assertThat(changeSchema.path("properties").path("key").isEmpty()).isTrue();
        assertThat(changeSchema.path("if").path("properties").path("operation").path("enum"))
            .isEqualTo(MAPPER.readTree("[\"UPSERT\",\"REMOVE_ITEM\"]"));
        assertThat(changeSchema.path("then").path("required"))
            .isEqualTo(MAPPER.readTree("[\"key\"]"));
        var requiredKey = changeSchema.path("then").path("properties").path("key");
        assertThat(requiredKey.path("type").asText()).isEqualTo("string");
        assertThat(requiredKey.path("minLength").asInt()).isEqualTo(1);
        assertThat(requiredKey.path("maxLength").asInt()).isEqualTo(512);
        var nonblank = java.util.regex.Pattern.compile(requiredKey.path("pattern").asText());
        assertThat(nonblank.matcher(" \t\n").find()).isFalse();
        assertThat(nonblank.matcher("example").find()).isTrue();
        assertThat(AuthoringInstructions.system(request())).contains(
            "UPSERT/REMOVE_ITEM require a nonblank string key", "Omit unused key on SET/REMOVE",
            "preserved unchanged, never repaired");
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "NEEDS_INPUT", "EXPLAIN", "UNSUPPORTED"})
    void allModelStatusesSurviveWithoutManufacturingADecision(String status) throws Exception {
        var output = output().put("status", status);
        assertThat(contract.strictResponse(output.toString(), request())).isEqualTo(output);
    }

    @Test
    void platformMessageLimitAndCollectionGuidanceStayAligned() throws Exception {
        var boundary = output().put("message", "x".repeat(8_000));
        assertThat(contract.strictResponse(boundary.toString(), request())).isEqualTo(boundary);
        assertInvalid(output().put("message", "x".repeat(8_001)).toString());
        var schema = MAPPER.readTree(AuthoringInstructions.RESPONSE_SCHEMA);
        assertThat(schema.path("properties").path("message").path("maxLength").asInt()).isEqualTo(8_000);
        assertThat(schema.path("properties").path("changes").path("maxItems").asInt()).isEqualTo(30);
        assertThat(AuthoringInstructions.system(request())).contains("params use name", "sources use id",
            "knowledgeSources use sourceKey", "datasets use datasetId", "REMOVE/REMOVE_ITEM");
    }

    @Test
    void actionPromptGuidesActualRouteAndParameterShapesWithoutRepairingModelValues() {
        assertThat(AuthoringInstructions.system(request())).contains("collectionShapes", "routeChangeExample",
            "routingConfig.actions is an object map", "[200]", "min/max", "requiredProperties",
            "never encode null as [\"null\"]", "Never invent properties[\"*\"]", "state the limitation",
            "Supported partial changes may remain visible");
    }

    @Test
    void specialistPromptRequiresInstalledAuthorityAndConsistentExtensionProposal() throws Exception {
        ObjectNode input = MAPPER.valueToTree(request());
        input.put("task", "SPECIALISTS");
        assertThat(AuthoringInstructions.system(MAPPER.treeToValue(input, ConfigurationAuthoringRequest.class)))
            .contains("authorizedSpecialistCapabilities", "Missing installed authority remains a question",
                "both enabled:true and executionExtensions", "retaining", "SPECIALIST_ASSISTANCE exactly once",
                "compiled/contentHash");
    }

    @Test
    void kitPromptIsGuideOnlyAndGenericCollectionOperationsStayExplicit() throws Exception {
        ObjectNode input = MAPPER.valueToTree(request());
        input.put("task", "KIT");
        assertThat(AuthoringInstructions.system(MAPPER.treeToValue(input, ConfigurationAuthoringRequest.class)))
            .contains("GUIDE-ONLY", "already-created Kit", "allowedPaths: []", "existing resource editors",
                "EXPLAIN or NEEDS_INPUT with changes: []", "Never save a Kit directly, install, publish or deploy",
                "REMOVE_ITEM are array-only", "Use collectionShapes", "Object-map entries use SET at their exact escaped path");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prose %s", "```json\n%s\n```", "%s {}", "%s trailing", "[%s]"})
    void rejectsAnythingExceptOneWholeRawObject(String format) throws Exception {
        assertInvalid(format.formatted(output()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"changes\":[", "{\"status\":\"READY\",\"status\":\"EXPLAIN\"}"})
    void rejectsInvalidOrIncompleteExecutionEnvelopes(String raw) { assertInvalid(raw); }

    @Test
    void rejectsBadOperationPointerAndMissingValue() throws Exception {
        var changed = output();
        ((ObjectNode) changed.path("changes").get(0)).put("operation", "EXECUTE");
        assertInvalid(changed.toString());
        changed = output();
        ((ObjectNode) changed.path("changes").get(0)).put("path", "/bad~2pointer");
        assertInvalid(changed.toString());
        changed = output();
        ((ObjectNode) changed.path("changes").get(0)).remove("value");
        assertInvalid(changed.toString());
    }

    @Test
    void missingAdvisoryMetadataPreservesUsefulChangesAndReportsOmissions() throws Exception {
        var raw = output();
        raw.remove(List.of("questions", "assumptions", "sourceReferences"));
        var result = contract.strictResponse(raw.toString(), request());
        assertThat(result.path("changes")).isEqualTo(raw.path("changes"));
        for (String field : List.of("questions", "assumptions", "sourceReferences")) assertThat(result.path(field).isEmpty()).isTrue();
        assertThat(result.path("diagnostics")).hasSize(3);
        for (var diagnostic : result.path("diagnostics")) {
            assertThat(diagnostic.path("code").asText()).isEqualTo("AUTHORING_METADATA_MISSING");
            assertThat(diagnostic.path("originalMissing").asBoolean()).isTrue();
            assertThat(diagnostic.path("policy").asText()).isEqualTo("REPORT_AND_PRESERVE");
        }
    }

    @Test
    void wrongMetadataTypesReturnSafeArraysWithEveryOriginalValue() throws Exception {
        var raw = output();
        raw.putObject("questions").put("question", "Missing an ID");
        raw.putNull("assumptions");
        raw.put("sourceReferences", 42);
        var result = contract.strictResponse(raw.toString(), request());
        assertThat(result.path("changes")).isEqualTo(raw.path("changes"));
        for (int i = 0; i < 3; i++) {
            String field = List.of("questions", "assumptions", "sourceReferences").get(i);
            assertThat(result.path(field).isArray()).isTrue();
            assertThat(result.path(field).isEmpty()).isTrue();
            assertThat(result.path("diagnostics").get(i).get("originalValue")).isEqualTo(raw.get(field));
        }
    }

    @Test
    void malformedMetadataItemsPreserveValidItemsAndOriginalDiagnosticLocations() throws Exception {
        var raw = output();
        var questions = raw.putArray("questions");
        questions.addObject().put("id", "bad").put("question", 12);
        questions.addObject().put("id", "good").put("question", "Which source?").put("field", 7);
        questions.add("unexpected prose");
        raw.putArray("assumptions").add("valid assumption").addObject().put("unexpected", true);
        raw.putArray("sourceReferences").addObject().put("source", "not a string");
        var result = contract.strictResponse(raw.toString(), request());
        assertThat(result.path("questions")).hasSize(1);
        assertThat(result.path("questions").get(0).path("id").asText()).isEqualTo("good");
        assertThat(result.path("questions").get(0).has("field")).isFalse();
        assertThat(result.path("assumptions")).hasSize(1);
        assertThat(result.path("diagnostics")).hasSize(5);
        assertThat(result.path("diagnostics").get(1).path("field").asText()).isEqualTo("/questions/1/field");
        assertThat(result.path("diagnostics").get(1).path("originalValue").asInt()).isEqualTo(7);
        assertThat(result.path("diagnostics").get(2).path("originalValue").asText()).isEqualTo("unexpected prose");
    }

    @Test
    void unknownCitationsAreVisibleUnverifiedDiagnosticsNotAuthorityOrProposalFailure() throws Exception {
        var raw = output();
        raw.withArray("sourceReferences").add("another-tenant-source").add("https://untrusted.invalid/source");
        var result = contract.strictResponse(raw.toString(), request());
        assertThat(result.path("changes")).isEqualTo(raw.path("changes"));
        assertThat(result.path("sourceReferences")).isEqualTo(output().path("sourceReferences"));
        assertThat(result.path("diagnostics")).hasSize(2);
        for (var diagnostic : result.path("diagnostics")) {
            assertThat(diagnostic.path("code").asText()).isEqualTo("AUTHORING_SOURCE_REFERENCE_UNVERIFIED");
            assertThat(diagnostic.path("verified").asBoolean(true)).isFalse();
            assertThat(diagnostic.path("originalValue").isTextual()).isTrue();
        }
    }

    @Test
    void advisorySchemaGuidanceLimitsDoNotBecomeHardProposalGates() throws Exception {
        var raw = output();
        var questions = raw.putArray("questions");
        for (int i = 0; i < 40; i++) questions.addObject().put("id", "q" + i).put("question", "Question " + i);
        var assumptions = raw.putArray("assumptions");
        for (int i = 0; i < 65; i++) assumptions.add("a".repeat(4_001));
        // Stay within the whole raw response memory bound while exercising the old per-item gate.
        for (int i = 1; i < assumptions.size(); i++) assumptions.set(i, MAPPER.getNodeFactory().textNode("assumption"));
        var result = contract.strictResponse(raw.toString(), request());
        assertThat(result.path("questions")).hasSize(40);
        assertThat(result.path("assumptions")).hasSize(65);
        assertThat(result.path("assumptions").get(0).asText()).hasSize(4_001);
    }

    @Test
    void providerCannotForgeRuntimeDiagnosticsAndAdvisoryContentStillHasTreeBounds() throws Exception {
        var raw = output();
        raw.putArray("diagnostics").addObject().put("code", "TRUST_ME").put("verified", true);
        var result = contract.strictResponse(raw.toString(), request());
        assertThat(result.path("diagnostics").get(0).path("code").asText()).isEqualTo("AUTHORING_PROVIDER_DIAGNOSTICS");
        assertThat(result.path("diagnostics").get(0).path("originalValue")).isEqualTo(raw.path("diagnostics"));
        ObjectNode cursor = raw.putObject("assumptions");
        for (int i = 0; i < 35; i++) cursor = cursor.putObject("nested");
        assertInvalid(raw.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"contractVersion", "status", "message", "changes"})
    void metadataPolicyNeverRepairsMissingExecutableEnvelopeFields(String field) throws Exception {
        var raw = output();
        raw.remove(field);
        assertInvalid(raw.toString());
    }

    @Test
    void contextSeparationStillCountsQueryAndHistoryAgainstWholeInputBound() {
        for (String field : List.of("query", "priorTurns")) {
            ObjectNode input = MAPPER.valueToTree(request());
            input.putObject("currentConfiguration").put("large", "x".repeat(AuthoringJsonContract.MAX_PROMPT_CHARS - 2_000));
            if (field.equals("query")) input.put("query", "q".repeat(4_000));
            else input.withArray("priorTurns").addObject().put("role", "USER").put("content", "h".repeat(4_000));
            assertThatThrownBy(() -> contract.input(MAPPER.treeToValue(input, ConfigurationAuthoringRequest.class)))
                .isInstanceOf(AuthoringFailure.class);
        }
    }

    @Test
    void boundsCombinedInputAndTreeDepthWithoutTruncatingCustomerData() {
        var request = MAPPER.valueToTree(request());
        var config = ((ObjectNode) request).putObject("currentConfiguration");
        config.put("large", "x".repeat(AuthoringJsonContract.MAX_PROMPT_CHARS));
        assertThatThrownBy(() -> contract.input(MAPPER.treeToValue(request, ConfigurationAuthoringRequest.class)))
            .isInstanceOf(AuthoringFailure.class);
        config.removeAll();
        ObjectNode cursor = config;
        for (int i = 0; i < 35; i++) cursor = cursor.putObject("nested");
        assertThatThrownBy(() -> contract.input(MAPPER.treeToValue(request, ConfigurationAuthoringRequest.class)))
            .isInstanceOf(AuthoringFailure.class);
    }

    @Test
    void boundsIndividualFieldsHistorySourcesAndEnums() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request())).isEmpty();
            for (String field : List.of("query", "sessionId", "turnId")) {
                ObjectNode invalid = MAPPER.valueToTree(request());
                invalid.put(field, "x".repeat(8_001));
                assertThat(validator.validate(MAPPER.treeToValue(invalid, ConfigurationAuthoringRequest.class))).isNotEmpty();
            }
            ObjectNode invalid = MAPPER.valueToTree(request());
            for (int i = 0; i < 25; i++) invalid.withArray("priorTurns").addObject().put("role", "USER").put("content", "hello");
            assertThat(validator.validate(MAPPER.treeToValue(invalid, ConfigurationAuthoringRequest.class))).isNotEmpty();
            invalid = MAPPER.valueToTree(request());
            ((ObjectNode) invalid.path("sources").get(0)).put("content", "x".repeat(32_001));
            assertThat(validator.validate(MAPPER.treeToValue(invalid, ConfigurationAuthoringRequest.class))).isNotEmpty();
            invalid = MAPPER.valueToTree(request());
            ((ObjectNode) invalid.path("priorTurns").get(0)).put("role", "SYSTEM");
            ObjectNode unknownRole = invalid;
            assertThatThrownBy(() -> MAPPER.treeToValue(unknownRole, ConfigurationAuthoringRequest.class)).isInstanceOf(Exception.class);
        }
    }

    private void assertInvalid(String raw) {
        assertThatThrownBy(() -> contract.strictResponse(raw, request()))
            .isInstanceOfSatisfying(AuthoringFailure.class, failure -> assertThat(failure.code).isEqualTo("AUTHORING_INVALID_OUTPUT"));
    }
}
