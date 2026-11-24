package com.ai.fabric.platform.backend.deployment.specialist;

import com.ai.fabric.platform.backend.assistant.AuthoringNativeCapabilities;
import com.ai.fabric.platform.backend.assistant.ConfigurationAssistantTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkflowAssistanceCompilerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkflowAssistanceCompiler compiler = new WorkflowAssistanceCompiler(mapper);
    private final ConfigurationAssistantTemplate template = new ConfigurationAssistantTemplate(mapper);
    private SpecialistAssistanceCompiler.Authority authority() {
        return new SpecialistAssistanceCompiler.Authority(AuthoringNativeCapabilities.READS.stream()
            .collect(Collectors.toMap(n -> n, n -> Set.of("configuration:author"))), Set.of(),
            AuthoringNativeCapabilities.WRITES.stream().collect(Collectors.toMap(n -> n, n -> Set.of("configuration:author"))));
    }
    @Test void publishesNativeWriteProfilesAndTaskBindingsWithoutInference() throws Exception {
        var published = compiler.compileBehavior(template.behavior(), authority());
        var config = published.path("workflowAssistance");
        compiler.verifyCompiled(config.path("compiled"), config.path("contentHash").asText());
        assertThat(config.path("compiled").path("profiles")).hasSize(9);
        assertThat(config.path("compiled").path("taskBindings")).hasSize(13);
        for (var resource : config.path("compiled").path("resources")) {
            if (!"Specialist".equals(resource.path("kind").asText())) continue;
            assertThat(resource.at("/spec/execution/strategy").asText()).isEqualTo("BOUNDED_ITERATIVE");
            boolean guide = "workflow-guide".equals(resource.at("/metadata/name").asText());
            assertThat(resource.at("/spec/execution/writePolicy").asText()).isEqualTo(guide ? "DISABLED" : "ACTION_POLICY");
            assertThat(resource.at("/spec/execution/workflow/maxWrites").asInt()).isEqualTo(guide ? 0 : 3);
            assertThat(resource.at("/spec/conversation/recordValidatedTurns").asBoolean()).isFalse();
            assertThat(resource.at("/spec/capabilities/retrieval/enabled").asBoolean()).isFalse();
            assertThat(resource.at("/spec/capabilities/actions/proposableWrites")).hasSize(guide ? 0 : 2);
        }
        assertThat(compiler.serialized(config.path("compiled")).getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(65536);
        String export = System.getProperty("workflow.fixture.export");
        if (export != null) Files.writeString(Path.of(export), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
    }
    @Test void disabledAndAbsentConfigurationDoNotChangeExistingSpecialists() {
        var ordinary = mapper.createObjectNode().put("type", "CONVERSATIONAL");
        assertThat(compiler.compileBehavior(ordinary, null)).isEqualTo(ordinary);
        ordinary.putObject("workflowAssistance").put("enabled", false);
        assertThat(compiler.compileBehavior(ordinary, null)).isEqualTo(ordinary);
    }
    @Test void installedAuthorityCannotBeManufacturedByTheManifest() {
        assertThatThrownBy(() -> compiler.compileBehavior(template.behavior(), new SpecialistAssistanceCompiler.Authority(Map.of(), Set.of(), Map.of())))
            .hasMessageContaining("unconfigured READ");
        var config = template.behavior();
        ((ObjectNode) config.at("/workflowAssistance/profiles/0")).putArray("readActions").add("unregistered_admin_tool");
        assertThatThrownBy(() -> compiler.compileBehavior(config, authority())).hasMessageContaining("unconfigured READ");
    }
    @Test void publicationRejectsUnboundedPolicyAndCrossAccessAliases() {
        var config = template.behavior();
        ((ObjectNode) config.at("/workflowAssistance/policy")).put("maxWriteSteps", 4);
        assertThatThrownBy(() -> compiler.validateConfiguration(config)).hasMessageContaining("maxWriteSteps");
        var invalid = template.behavior();
        ((ObjectNode) invalid.at("/workflowAssistance/profiles/0")).putArray("writeActions").add("authoring_get_context");
        assertThatThrownBy(() -> compiler.validateConfiguration(invalid)).hasMessageContaining("both READ and WRITE");
    }
    @Test void editableInstructionChangesAffectPublishedHashWithoutChangingCapabilities() {
        var config = template.behavior(); var before = compiler.compileBehavior(config, authority());
        ((ObjectNode) config.at("/workflowAssistance/profiles/0")).put("instructions", "Explain the actual operations before finishing.");
        var after = compiler.compileBehavior(config, authority());
        assertThat(after.at("/workflowAssistance/contentHash")).isNotEqualTo(before.at("/workflowAssistance/contentHash"));
        assertThat(after.at("/workflowAssistance/compiled/profiles")).isEqualTo(before.at("/workflowAssistance/compiled/profiles"));
        assertThatThrownBy(() -> compiler.verifyCompiled(after.at("/workflowAssistance/compiled"), before.at("/workflowAssistance/contentHash").asText()))
            .hasMessageContaining("invalid");
    }
    @Test void generatedFieldsAreRecompiledNotTrustedFromTheDraft() {
        var config = template.behavior();
        ((ObjectNode) config.path("workflowAssistance")).put("contentHash", "forged").putObject("compiled").put("forged", true);
        var result = compiler.compileBehavior(config, authority());
        assertThat(result.at("/workflowAssistance/compiled/forged").isMissingNode()).isTrue();
        assertThat(result.at("/workflowAssistance/contentHash").asText()).startsWith("sha256:");
    }
}
