package com.ai.fabric.platform.backend.deployment.specialist;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecialistAssistanceCompilerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SpecialistAssistanceCompiler compiler = new SpecialistAssistanceCompiler(mapper);

    @Test
    void emitsOfficialResourcesWithExactTargetsAndNoConversationOrWriteAuthority() throws Exception {
        JsonNode result = compiler.compileBehavior(SpecialistAssistanceFixtures.behavior(mapper), SpecialistAssistanceFixtures.authority());
        JsonNode compiled = result.path("specialistAssistance").path("compiled");
        assertThat(compiled.path("resources")).hasSize(11);
        for (JsonNode resource : compiled.path("resources")) {
            assertThat(resource.path("apiVersion").asText()).isEqualTo("ai.fabric/v1");
            assertThat(resource.path("kind").asText()).isIn("Specialist", "SpecialistSchema", "SpecialistPromptProfile", "SpecialistChain");
            if ("Specialist".equals(resource.path("kind").asText())) {
                assertThat(resource.at("/spec/mode").asText()).isEqualTo(
                    resource.at("/metadata/name").asText().startsWith("assist-manager-") ? "resolver"
                        : resource.at("/spec/capabilities/actions/requestableReads").isEmpty() ? "navigator_deep" : "executor");
                assertThat(resource.at("/spec/execution/writePolicy").asText()).isEqualTo("DISABLED");
                assertThat(resource.at("/spec/capabilities/actions/proposableWrites")).isEmpty();
                assertThat(resource.at("/spec/conversation/binding").asText()).isEqualTo("DISABLED");
                assertThat(resource.at("/spec/conversation/recordValidatedTurns").asBoolean()).isFalse();
                assertThat(resource.at("/spec/conversation/interactionCapability").asText()).isEqualTo("NON_INTERACTIVE");
            }
        }
        JsonNode invocation = compiled.path("invocations").get(2);
        assertThat(invocation.path("targetType").asText()).isEqualTo("CHAIN");
        assertThat(invocation.path("targetRef").asText()).isEqualTo("assist-chain-investigate@1");
        assertThat(invocation.path("deadlineSeconds").asInt()).isEqualTo(75);
        assertThat(mapper.convertValue(invocation.path("requiredScopes"), List.class)).containsExactly(
            "account:read", "action:get_account_status", "specialist:assist-area-account@1", "specialist:assist-area-setup@1",
            "specialist:assist-manager-investigate@1", "vector:product-guide");
        JsonNode chain = resource(compiled, "SpecialistChain", "assist-chain-investigate");
        assertThat(chain.at("/spec/conversationPolicy").asText()).isEqualTo("DISABLED");
        assertThat(chain.at("/spec/targets/0/input/fields/0/source").asText()).isEqualTo("MANAGER_OBJECTIVE");
        assertThat(chain.at("/spec/targets/0/transitions/handoffAllowed").asBoolean()).isFalse();
        assertThat(chain.at("/spec/targets/0/transitions/parallelEligible").asBoolean()).isTrue();
        String actualHash = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(compiled)));
        assertThat(result.at("/specialistAssistance/contentHash").asText()).isEqualTo(actualHash);
        assertThat(compiler.serialized(compiled).getBytes(StandardCharsets.UTF_8)).isEqualTo(mapper.writeValueAsBytes(compiled));
        compiler.verifyCompiled(compiled, actualHash);
    }

    @Test
    void separatesBoundedChainSummariesFromFullDirectAnswersWithAlignedPromptsAndSchemas() {
        for (boolean parallel : List.of(false, true)) {
            ObjectNode behavior = SpecialistAssistanceFixtures.behavior(mapper);
            ((ObjectNode) behavior.at("/specialistAssistance/resolutions/0")).put("allowParallel", parallel);
            JsonNode compiled = compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority())
                .at("/specialistAssistance/compiled");
            JsonNode output = resource(compiled, "SpecialistSchema", "assist-answer").at("/spec/schema");
            assertThat(mapper.convertValue(output.path("required"), List.class)).containsExactly("summary", "answer");
            assertThat(output.path("properties").size()).isEqualTo(2);
            assertThat(output.path("additionalProperties").asBoolean()).isFalse();
            assertThat(output.at("/properties/summary/type").asText()).isEqualTo("string");
            assertThat(output.at("/properties/summary/minLength").asInt()).isEqualTo(1);
            assertThat(output.at("/properties/summary/maxLength").asInt()).isEqualTo(2000);
            assertThat(output.at("/properties/answer/type").asText()).isEqualTo("string");
            assertThat(output.at("/properties/answer/minLength").asInt()).isEqualTo(1);
            assertThat(output.at("/properties/answer/maxLength").asInt()).isEqualTo(4000);

            JsonNode managerInput = resource(compiled, "SpecialistSchema", "assist-manager-investigate-input").at("/spec/schema");
            assertThat(managerInput.at("/properties/completedResults/items/properties/summary"))
                .isEqualTo(output.at("/properties/summary"));
            JsonNode chain = resource(compiled, "SpecialistChain", "assist-chain-investigate");
            for (JsonNode target : chain.at("/spec/targets")) {
                assertThat(target.at("/result/summaryPointer").asText()).isEqualTo("/summary");
            }
            for (String name : List.of("assist-area-setup", "assist-area-account")) {
                JsonNode worker = resource(compiled, "Specialist", name);
                assertThat(worker.at("/spec/output/schemaRef").asText()).isEqualTo("assist-answer@1");
                assertThat(worker.at("/spec/output/conversationTextPointer").asText()).isEqualTo("/answer");
                assertThat(worker.at("/spec/output/finalValidatorRefs")).isEmpty();
                assertThat(resource(compiled, "SpecialistPromptProfile", name).at("/spec/outputContract").asText())
                    .contains("{\"summary\":\"...\",\"answer\":\"...\"}", "at most 2000 characters", "at most 4000 characters",
                        "concise, nonblank", "full useful response", "uncertainty", "both fields");
            }
            assertThat(resource(compiled, "SpecialistPromptProfile", "assist-manager-investigate").at("/spec/constraints").asText())
                .contains("completedResults contains concise worker summaries of at most 2000 characters, not full answers",
                    "uncertainty and limitations");
        }
    }

    @Test
    void directReadOnlyAreaNeedsNoVectorSpaceAndUsesConditionalGrounding() {
        ObjectNode behavior = SpecialistAssistanceFixtures.behavior(mapper);
        ((ArrayNode) behavior.at("/specialistAssistance/areas")).remove(0);
        ((ObjectNode) behavior.path("specialistAssistance")).putArray("resolutions");
        JsonNode compiled = compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority()).at("/specialistAssistance/compiled");
        JsonNode worker = resource(compiled, "Specialist", "assist-area-account");
        assertThat(worker.at("/spec/mode").asText()).isEqualTo("executor");
        assertThat(worker.at("/spec/capabilities/retrieval/enabled").asBoolean()).isFalse();
        assertThat(worker.at("/spec/grounding/requirement").asText()).isEqualTo("WHEN_CAPABILITY_USED");
        assertThat(worker.at("/spec/capabilities/actions/requestableReads/0").asText()).isEqualTo("get_account_status");
        assertThat(worker.at("/spec/output/conversationTextPointer").asText()).isEqualTo("/answer");
        assertThat(resource(compiled, "SpecialistSchema", "assist-answer").at("/spec/schema/properties/answer/maxLength").asInt())
            .isEqualTo(4000);
        assertThat(compiled.path("invocations")).hasSize(1);
        assertThat(mapper.convertValue(compiled.path("invocations").get(0).path("requiredScopes"), List.class))
            .containsExactly("account:read", "action:get_account_status", "specialist:assist-area-account@1");
    }

    @Test
    void selectsWorkerModesAndObservationGuidanceFromDeclaredReadsOnly() {
        ObjectNode behavior = SpecialistAssistanceFixtures.behavior(mapper);
        JsonNode compiled = compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority())
            .at("/specialistAssistance/compiled");
        JsonNode docs = resource(compiled, "Specialist", "assist-area-setup");
        JsonNode read = resource(compiled, "Specialist", "assist-area-account");
        JsonNode manager = resource(compiled, "Specialist", "assist-manager-investigate");
        assertThat(docs.at("/spec/mode").asText()).isEqualTo("navigator_deep");
        assertThat(read.at("/spec/mode").asText()).isEqualTo("executor");
        assertThat(manager.at("/spec/mode").asText()).isEqualTo("resolver");
        assertThat(read.at("/spec/execution/strategy").asText()).isEqualTo("SINGLE_PASS");
        assertThat(mapper.convertValue(read.at("/spec/capabilities/actions/visible"), List.class))
            .containsExactly("get_account_status");
        assertThat(read.at("/spec/capabilities/actions/requestableReads"))
            .isEqualTo(read.at("/spec/capabilities/actions/visible"));
        assertThat(manager.at("/spec/capabilities/actions/visible")).isEmpty();
        String readPrompt = resource(compiled, "SpecialistPromptProfile", "assist-area-account").at("/spec/constraints").asText();
        assertThat(readPrompt).startsWith(behavior.at("/specialistAssistance/areas/1/instructions").asText())
            .contains("When the question needs current observations", "within caller authority",
                "Documentation-only questions do not require live reads", "checks were not performed or failed");
        assertThat(resource(compiled, "SpecialistPromptProfile", "assist-area-setup").at("/spec/constraints").asText())
            .doesNotContain("When the question needs current observations");
        assertThat(resource(compiled, "SpecialistPromptProfile", "assist-manager-investigate").at("/spec/constraints").asText())
            .doesNotContain("When the question needs current observations");

        ((ObjectNode) behavior.at("/specialistAssistance/areas/1")).putArray("readActions");
        JsonNode withoutReads = compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority())
            .at("/specialistAssistance/compiled");
        assertThat(resource(withoutReads, "Specialist", "assist-area-account").at("/spec/mode").asText())
            .isEqualTo("navigator_deep");
        assertThat(resource(withoutReads, "SpecialistPromptProfile", "assist-area-account").at("/spec/constraints").asText())
            .doesNotContain("When the question needs current observations");
        assertThat(mapper.convertValue(withoutReads.path("invocations").get(1).path("requiredScopes"), List.class))
            .containsExactly("specialist:assist-area-account@1");
    }

    @Test
    void reportsOnlySelectedExactReadPrerequisitesWithoutGrantingOtherInstalledActions() {
        var authority = new SpecialistAssistanceCompiler.Authority(
            Map.of("get_account_status", Set.of("account:read"), "get_unrelated_status", Set.of("unrelated:read")),
            Set.of("product-guide"));
        JsonNode compiled = compiler.compileBehavior(SpecialistAssistanceFixtures.behavior(mapper), authority)
            .at("/specialistAssistance/compiled");
        for (JsonNode invocation : compiled.path("invocations")) {
            List<String> scopes = mapper.convertValue(invocation.path("requiredScopes"), new TypeReference<>() { });
            assertThat(scopes).doesNotContain("action:*", "action:get_unrelated_status", "unrelated:read");
        }
        assertThat(mapper.convertValue(compiled.path("invocations").get(0).path("requiredScopes"), List.class))
            .containsExactly("specialist:assist-area-setup@1", "vector:product-guide");
        assertThat(authority.readActionScopes().get("get_account_status")).containsExactly("account:read");
    }

    @Test
    void rejectsUninstalledReadAndVectorBindingsRatherThanGrantingThem() {
        var behavior = SpecialistAssistanceFixtures.behavior(mapper);
        assertThatThrownBy(() -> compiler.compileBehavior(behavior, new SpecialistAssistanceCompiler.Authority(Map.of(), Set.of("product-guide"))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not declared READ");
        assertThatThrownBy(() -> compiler.compileBehavior(behavior, new SpecialistAssistanceCompiler.Authority(Map.of("get_account_status", Set.of()), Set.of())))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("source not declared");
    }

    @Test
    void checksNecessaryExecutionBoundsAndExactTopologyNotQuestionKeywords() {
        ObjectNode behavior = SpecialistAssistanceFixtures.behavior(mapper);
        ((ObjectNode) behavior.at("/specialistAssistance/resolutions/0")).putArray("areaIds").add("outside");
        assertThatThrownBy(() -> compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority())).hasMessageContaining("configured areas only");
        ObjectNode overBudget = SpecialistAssistanceFixtures.behavior(mapper);
        ((ObjectNode) overBudget.at("/specialistAssistance/limits")).put("deadlineSeconds", 1000000);
        assertThatThrownBy(() -> compiler.compileBehavior(overBudget, SpecialistAssistanceFixtures.authority())).hasMessageContaining("deadlineSeconds");
    }

    @Test
    void replacesClientCompiledPayloadAndDetectsLaterTampering() {
        ObjectNode behavior = SpecialistAssistanceFixtures.behavior(mapper);
        ((ObjectNode) behavior.path("specialistAssistance")).putObject("compiled").put("malicious", true);
        JsonNode first = compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority());
        JsonNode second = compiler.compileBehavior(first, SpecialistAssistanceFixtures.authority());
        assertThat(second).isEqualTo(first);
        JsonNode compiled = first.at("/specialistAssistance/compiled");
        ((ObjectNode) compiled.path("invocations").get(0)).put("targetRef", "outside@1");
        assertThatThrownBy(() -> compiler.verifyCompiled(compiled, first.at("/specialistAssistance/contentHash").asText()))
            .hasMessageContaining("hash mismatch");
    }

    @Test
    void configurationChangesProduceNewImmutableContentWithoutTemplatePins() {
        ObjectNode behavior = SpecialistAssistanceFixtures.behavior(mapper);
        JsonNode first = compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority());
        ((ObjectNode) behavior.at("/specialistAssistance/areas/0")).put("instructions", "Use a different approved product guide and report uncertainty.");
        JsonNode second = compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority());
        assertThat(second.at("/specialistAssistance/contentHash")).isNotEqualTo(first.at("/specialistAssistance/contentHash"));
        assertThat(first.at("/specialistAssistance/areas/0/instructions").asText()).contains("approved product setup");
        ((ObjectNode) behavior.at("/specialistAssistance/resolutions/0")).put("allowParallel", false);
        JsonNode sequential = compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority());
        assertThat(resource(sequential.at("/specialistAssistance/compiled"), "SpecialistChain", "assist-chain-investigate")
            .at("/spec/limits/maxParallelWorkers").asInt()).isEqualTo(1);
    }

    @Test
    void optionalExtensionRequiresHonestRuntimeCapabilityAndCoexistsWithHandover() {
        DeploymentBehaviorCatalogService catalog = new DeploymentBehaviorCatalogService(mapper);
        ObjectNode behavior = SpecialistAssistanceFixtures.behavior(mapper);
        assertThat(catalog.validate(behavior, "CONVERSATIONAL").valid()).isTrue();
        behavior.withArray("executionExtensions").add("CONVERSATION_HANDOVER");
        var requirements = catalog.releaseRequirements(behavior);
        assertThat(requirements.capabilities()).contains(SpecialistAssistanceCompiler.CAPABILITY, "conversation-control");
        assertThat(requirements.capabilityManifestRequired()).isTrue();
        assertThat(requirements.migrationIds()).contains("ai-specialist-chain-execution-v1", "loomai-conversation-handover-v1");
        assertThat(catalog.requireSummary("CONVERSATIONAL").allowedExecutionExtensions()).contains(SpecialistAssistanceCompiler.EXTENSION);
        ((ObjectNode) behavior.path("specialistAssistance")).put("enabled", false);
        assertThat(catalog.validate(behavior, "CONVERSATIONAL").valid()).isFalse();
    }

    @Test
    void rejectsInjectedAuthorityAndRecursiveAction() {
        ObjectNode behavior = SpecialistAssistanceFixtures.behavior(mapper);
        ((ObjectNode) behavior.at("/specialistAssistance/areas/0")).putArray("requiredScopes").add("*");
        assertThatThrownBy(() -> compiler.compileBehavior(behavior, SpecialistAssistanceFixtures.authority())).hasMessageContaining("Unsupported area field");
        ObjectNode recursive = SpecialistAssistanceFixtures.behavior(mapper);
        ((ObjectNode) recursive.at("/specialistAssistance/areas/1")).putArray("readActions").add("consult_specialist");
        assertThatThrownBy(() -> compiler.compileBehavior(recursive, new SpecialistAssistanceCompiler.Authority(Map.of("consult_specialist", Set.of()), Set.of("product-guide"))))
            .hasMessageContaining("recursively");
    }

    private JsonNode resource(JsonNode compiled, String kind, String name) {
        for (JsonNode resource : compiled.path("resources")) {
            if (kind.equals(resource.path("kind").asText()) && name.equals(resource.at("/metadata/name").asText())) return resource;
        }
        throw new AssertionError("Missing official resource: " + kind + " " + name);
    }
}
