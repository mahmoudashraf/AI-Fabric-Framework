package com.ai.fabric.platform.backend.deployment.tasks;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ManagedTaskCompilerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ManagedTaskCompiler compiler = new ManagedTaskCompiler(mapper);

    static ObjectNode behavior(ObjectMapper mapper) throws Exception {
        return ManagedTaskFixtures.behavior(mapper);
    }

    @Test
    void compilesPureInputTasksWithoutVectorOrInstallationRequirements() throws Exception {
        JsonNode source = behavior(mapper);
        JsonNode result = compiler.compileBehavior(source, null);
        JsonNode bundle = result.at("/managedTasks/compiled");
        assertThat(bundle.path("resources")).hasSize(4);
        assertThat(bundle.at("/tasks/0/specialistRef").asText()).isEqualTo("task-assess-request@1");
        assertThat(bundle.at("/tasks/0/requiredScopes").toString()).isEqualTo("[\"specialist:task-assess-request@1\"]");
        JsonNode specialist = resource(bundle, "Specialist");
        assertThat(specialist.at("/spec/mode").asText()).isEqualTo("resolver");
        assertThat(specialist.at("/spec/execution/writePolicy").asText()).isEqualTo("DISABLED");
        assertThat(specialist.at("/spec/capabilities/retrieval/enabled").asBoolean()).isFalse();
        assertThat(specialist.at("/spec/conversation/interactionCapability").asText()).isEqualTo("NON_INTERACTIVE");
        assertThat(specialist.at("/spec/input/contextPointers").toString()).isEqualTo("[\"/taskInput\"]");
        compiler.verifyCompiled(bundle, result.at("/managedTasks/contentHash").asText());
        assertThat(source.at("/managedTasks/compiled").isMissingNode()).isTrue();
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/managed-tasks-fixture.json"), compiler.serialized(bundle));
    }

    @Test
    void stableHashIncludesInstructionsAndNeverTrustsClientCompiledResources() throws Exception {
        ObjectNode source = behavior(mapper);
        String original = compiler.compileBehavior(source, null).at("/managedTasks/contentHash").asText();
        ((ObjectNode) source.path("managedTasks")).putObject("compiled").put("malicious", true);
        ((ObjectNode) source.path("managedTasks")).put("contentHash", "forged");
        assertThat(compiler.compileBehavior(source, null).at("/managedTasks/contentHash").asText()).isEqualTo(original);
        ((ObjectNode) source.at("/managedTasks/tasks/0")).put("instructions", "Return a concise structured recommendation.");
        assertThat(compiler.compileBehavior(source, null).at("/managedTasks/contentHash").asText()).isNotEqualTo(original);
    }

    @Test
    void textOutputHasARealStringProjectionContract() throws Exception {
        ObjectNode source = behavior(mapper);
        ((ObjectNode) source.at("/managedTasks/tasks/0")).put("outputMode", "TEXT").remove("outputSchema");
        JsonNode bundle = compiler.compileBehavior(source, null).at("/managedTasks/compiled");
        assertThat(bundle.at("/tasks/0/outputSchema/properties/answer/type").asText()).isEqualTo("string");
        assertThat(resource(bundle, "Specialist").at("/spec/output/conversationTextPointer").asText()).isEqualTo("/answer");
    }

    @Test
    void explicitReadWithoutVectorsUsesOnlyInstalledReadAuthority() throws Exception {
        ObjectNode source = behavior(mapper);
        ((ObjectNode) source.at("/managedTasks/tasks/0")).putArray("readActions").add("get_account");
        assertThatThrownBy(() -> compiler.compileBehavior(source, null)).isInstanceOf(ResponseStatusException.class);
        JsonNode bundle = compiler.compileBehavior(source, new SpecialistAssistanceCompiler.Authority(Map.of("get_account", Set.of("account:read")), Set.of()))
            .at("/managedTasks/compiled");
        assertThat(resource(bundle, "Specialist").at("/spec/mode").asText()).isEqualTo("executor");
        assertThat(resource(bundle, "Specialist").at("/spec/capabilities/retrieval/enabled").asBoolean()).isFalse();
        assertThat(bundle.at("/tasks/0/requiredScopes").toString()).contains("account:read", "action:get_account").doesNotContain("vector:");
    }

    @Test
    void knowledgeDoesNotGrantUnrelatedCollections() throws Exception {
        ObjectNode source = behavior(mapper);
        ((ObjectNode) source.at("/managedTasks/tasks/0")).putArray("vectorSpaces").add("approved-guide");
        assertThatThrownBy(() -> compiler.compileBehavior(source, new SpecialistAssistanceCompiler.Authority(Map.of(), Set.of("foreign-guide"))))
            .isInstanceOf(ResponseStatusException.class);
        JsonNode bundle = compiler.compileBehavior(source, new SpecialistAssistanceCompiler.Authority(Map.of(), Set.of("approved-guide", "unrelated")))
            .at("/managedTasks/compiled");
        assertThat(bundle.at("/tasks/0/requiredScopes").toString()).contains("vector:approved-guide").doesNotContain("unrelated");
    }

    @Test
    void preservesLocalDefinitionReferencesInsideWrappedTaskInput() throws Exception {
        ObjectNode source = behavior(mapper);
        ((ObjectNode) source.at("/managedTasks/tasks/0")).set("inputSchema", mapper.readTree("""
            {"type":"object","$defs":{"label":{"type":"string"}},"properties":{"label":{"$ref":"#/$defs/label"}}}
            """));
        JsonNode compiled = compiler.compileBehavior(source, null).at("/managedTasks/compiled");
        assertThat(resource(compiled, "SpecialistSchema").at("/spec/schema/properties/taskInput/properties/label/$ref").asText())
            .isEqualTo("#/properties/taskInput/$defs/label");
        assertThat(compiled.at("/tasks/0/inputSchema/properties/label/$ref").asText()).isEqualTo("#/$defs/label");
    }

    @Test
    void schemaKeywordsInPropertyNamesOrExampleValuesDoNotBecomeConfigurationInstructions() throws Exception {
        ObjectNode source = behavior(mapper);
        ((ObjectNode) source.at("/managedTasks/tasks/0")).set("inputSchema", mapper.readTree("""
            {"type":"object","properties":{"$id":{"type":"string"}},"default":{"$id":"literal-data"}}
            """));
        JsonNode bundle = compiler.compileBehavior(source, null).at("/managedTasks/compiled");
        assertThat(bundle.at("/tasks/0/inputSchema/default/$id").asText()).isEqualTo("literal-data");
        assertThat(resource(bundle, "SpecialistSchema").at("/spec/schema/properties/taskInput/properties/$id/type").asText()).isEqualTo("string");
    }

    @ParameterizedTest
    @ValueSource(strings = {"$ref", "$dynamicRef", "$recursiveRef", "$id"})
    void rejectsNetworkOrDynamicSchemaResolution(String keyword) throws Exception {
        ObjectNode source = behavior(mapper);
        ((ObjectNode) source.at("/managedTasks/tasks/0/inputSchema")).put(keyword, "https://private.example/schema");
        assertThatThrownBy(() -> compiler.compileBehavior(source, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMalformedSchemaAndUnsupportedExecutionOverrides() throws Exception {
        ObjectNode source = behavior(mapper);
        ((ObjectNode) source.at("/managedTasks/tasks/0/inputSchema")).put("required", true);
        assertThatThrownBy(() -> compiler.compileBehavior(source, null)).isInstanceOf(ResponseStatusException.class);
        ObjectNode changed = behavior(mapper);
        ((ObjectNode) changed.at("/managedTasks/tasks/0")).put("provider", "unconfigured");
        assertThatThrownBy(() -> compiler.compileBehavior(changed, null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Unsupported");
    }

    @Test
    void disabledTasksDoNotEmitExecutableResources() throws Exception {
        ObjectNode source = behavior(mapper);
        ((ObjectNode) source.path("managedTasks")).put("enabled", false).putObject("compiled");
        assertThat(compiler.compileBehavior(source, null).at("/managedTasks/compiled").isMissingNode()).isTrue();
    }

    @Test
    void capabilityRequirementsDoNotConvertConversationalBehaviorOrRequireADatabase() throws Exception {
        var requirements = new DeploymentBehaviorCatalogService(mapper).releaseRequirements(behavior(mapper));
        assertThat(requirements.capabilities()).contains(ManagedTaskCompiler.CAPABILITY);
        assertThat(requirements.endpointClasses()).contains("managed-task-execute");
        assertThat(requirements.behaviorType()).isEqualTo("CONVERSATIONAL");
        assertThat(requirements.migrationIds()).doesNotContain("loomai-smart-brain-operation-v1");
        assertThat(requirements.capabilityManifestRequired()).isTrue();
    }

    private JsonNode resource(JsonNode bundle, String kind) {
        for (JsonNode resource : bundle.path("resources")) if (kind.equals(resource.path("kind").asText())) return resource;
        throw new AssertionError(kind);
    }
}
