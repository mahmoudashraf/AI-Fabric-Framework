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
        assertThat(compiler.serialized(config.path("compiled")).getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(WorkflowAssistanceCompiler.MAX_BYTES);
        String export = System.getProperty("workflow.fixture.export");
        if (export != null) Files.writeString(Path.of(export), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
    }
    @Test void disabledAndAbsentConfigurationDoNotChangeExistingSpecialists() {
        var ordinary = mapper.createObjectNode().put("type", "CONVERSATIONAL");
        assertThat(compiler.compileBehavior(ordinary, null)).isEqualTo(ordinary);
        ordinary.putObject("workflowAssistance").put("enabled", false);
        assertThat(compiler.compileBehavior(ordinary, null)).isEqualTo(ordinary);
    }
    @Test void everyWorkerTreatsAReceiptAsOneStepRatherThanTheWholeRequestedOutcome() {
        var config = template.behavior().path("workflowAssistance");
        for (var profile : config.path("profiles"))
            assertThat(profile.path("instructions").asText()).contains("Continue remaining authorized draft steps",
                "A receipt completes its own step, not necessarily the whole request",
                "Preserve user-requested intermediate saves and readbacks as separate tool operations",
                "use authoring_get_work_status to inspect the actual operation receipts");
        var api = java.util.stream.StreamSupport.stream(config.path("profiles").spliterator(), false)
            .filter(profile -> "api".equals(profile.path("id").asText())).findFirst().orElseThrow();
        assertThat(api.path("instructions").asText()).contains("not a stopping rule for multi-step requests",
            "Do not put the second edit's value into the initial save",
            "Preview-only preparation still requires building supported candidate changes",
            "Never call authoring_update_draft in preview-only mode");
        assertThat(config.path("policy").path("maxWriteSteps").asInt()).isEqualTo(3);
    }
    @Test void installedAuthorityCannotBeManufacturedByTheManifest() {
        assertThatThrownBy(() -> compiler.compileBehavior(template.behavior(), new SpecialistAssistanceCompiler.Authority(Map.of(), Set.of(), Map.of())))
            .hasMessageContaining("unconfigured READ");
        var config = boundedBehavior();
        ((ObjectNode) config.at("/workflowAssistance/profiles/0")).putArray("readActions").add("unregistered_admin_tool");
        assertThatThrownBy(() -> compiler.compileBehavior(config, authority())).hasMessageContaining("unconfigured READ");
    }
    @Test void publicationRejectsUnboundedPolicyAndCrossAccessAliases() {
        var config = boundedBehavior();
        ((ObjectNode) config.at("/workflowAssistance/policy")).put("maxWriteSteps", 4);
        assertThatThrownBy(() -> compiler.validateConfiguration(config)).hasMessageContaining("maxWriteSteps");
        var invalid = boundedBehavior();
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
    @Test void goalSegmentsCompileWithFiniteLifetimeAndPinnedProfileDelegation() {
        var behavior = goalBehavior();
        var config = (ObjectNode) behavior.path("workflowAssistance");
        ((ObjectNode) config.path("policy")).put("maxCorrections", 8).put("maxModelCalls", 64).put("maxActionSteps", 64).put("maxWriteSteps", 32);
        var setup = profile(config, "setup"); setup.putArray("delegatedProfiles").add("api").add("responses");
        var compiled = compiler.compileBehavior(behavior, authority());
        var bundle = compiled.at("/workflowAssistance/compiled");
        assertThat(bundle.at("/goal/maxElapsedSeconds").asInt()).isEqualTo(86400);
        var resource = java.util.stream.StreamSupport.stream(bundle.path("resources").spliterator(), false)
            .filter(r -> "Specialist".equals(r.path("kind").asText()) && "workflow-setup".equals(r.at("/metadata/name").asText())).findFirst().orElseThrow();
        assertThat(resource.at("/spec/execution/workflow/goal/enabled").asBoolean()).isTrue();
        assertThat(resource.at("/spec/execution/workflow/goal/maxElapsed").asText()).isEqualTo("PT86400S");
        assertThat(resource.at("/spec/execution/workflow/goal/delegatedProfiles")).hasSize(2);
        assertThat(resource.at("/spec/execution/workflow/maxCorrections").asInt()).isEqualTo(8);
        var publishedSetup = java.util.stream.StreamSupport.stream(bundle.path("profiles").spliterator(), false)
            .filter(r -> "setup".equals(r.path("id").asText())).findFirst().orElseThrow();
        assertThat(publishedSetup.path("requiredScopes").toString()).contains("specialist:workflow-api@1", "specialist:workflow-responses@1");
        assertThat(publishedSetup.path("allowedActions")).hasSize(AuthoringNativeCapabilities.READS.size() + AuthoringNativeCapabilities.WRITES.size());
    }
    @Test void boundedModeDoesNotInheritGoalPolicyCeilings() {
        var behavior = boundedBehavior();
        ((ObjectNode) behavior.at("/workflowAssistance/policy")).put("maxCorrections", 8);
        assertThatThrownBy(() -> compiler.validateConfiguration(behavior)).hasMessageContaining("maxCorrections");
        assertThat(WorkflowAssistanceCompiler.goalEnabled(behavior)).isFalse();
    }
    @Test void disablingGoalsPreservesConfiguredDelegationWithoutGrantingItToBoundedExecution() {
        var behavior = template.behavior();
        profile((ObjectNode) behavior.path("workflowAssistance"), "setup").put("instructions", "x".repeat(8000));
        ((ObjectNode) behavior.at("/workflowAssistance/goal")).put("enabled", false);
        ((ObjectNode) behavior.at("/workflowAssistance/policy")).put("maxActionSteps", 64).put("maxWriteSteps", 32)
            .put("maxModelCalls", 64).put("maxCorrections", 8).put("deadlineSeconds", 140);
        var compiled = compiler.compileBehavior(behavior, authority());
        assertThat(profile((ObjectNode) compiled.path("workflowAssistance"), "setup").path("delegatedProfiles")).hasSize(8);
        assertThat(compiled.at("/workflowAssistance/policy")).isEqualTo(behavior.at("/workflowAssistance/policy"));
        var bundle = compiled.at("/workflowAssistance/compiled");
        assertThat(bundle.at("/policy/maxActionSteps").asInt()).isEqualTo(8);
        assertThat(bundle.at("/policy/maxWriteSteps").asInt()).isEqualTo(3);
        assertThat(bundle.at("/policy/maxModelCalls").asInt()).isEqualTo(16);
        assertThat(bundle.at("/policy/maxCorrections").asInt()).isEqualTo(2);
        assertThat(bundle.at("/policy/deadlineSeconds").asInt()).isEqualTo(120);
        assertThat(WorkflowAssistanceCompiler.extendedBundleRequired(compiled)).isTrue();
        var requirements = new com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService(mapper).releaseRequirements(compiled);
        assertThat(requirements.capabilities()).contains(WorkflowAssistanceCompiler.EXTENDED_BUNDLE_CAPABILITY)
            .doesNotContain(WorkflowAssistanceCompiler.GOAL_CAPABILITY, "workflow-goal-profile-delegation-v1");
        for (var entry : bundle.path("profiles")) {
            assertThat(entry.path("delegatedProfiles")).isEmpty();
            assertThat(entry.path("requiredScopes").toString()).doesNotContain("specialist:workflow-foreign");
            assertThat(java.util.stream.StreamSupport.stream(entry.path("requiredScopes").spliterator(), false)
                .filter(scope -> scope.asText().startsWith("specialist:")).count()).isOne();
        }
        for (var resource : bundle.path("resources")) if ("Specialist".equals(resource.path("kind").asText())) {
            assertThat(resource.at("/spec/execution/workflow/goal").isMissingNode()).isTrue();
            assertThat(resource.at("/spec/execution/workflow/maxModelCalls").asInt()).isEqualTo(16);
            assertThat(resource.at("/spec/execution/workflow/maxCorrections").asInt()).isEqualTo(2);
        }
    }
    @Test void goalPolicyCannotRemoveFiniteLifetimeOrIntroduceUnknownProfiles() {
        var invalid = goalBehavior();
        ((ObjectNode) invalid.at("/workflowAssistance/goal")).put("maxElapsedSeconds", -1);
        assertThatThrownBy(() -> compiler.validateConfiguration(invalid)).hasMessageContaining("maxElapsedSeconds");
        var unknown = goalBehavior(); profile((ObjectNode) unknown.path("workflowAssistance"), "setup").putArray("delegatedProfiles").add("foreign");
        assertThatThrownBy(() -> compiler.validateConfiguration(unknown)).hasMessageContaining("existing different");
        var recursive = goalBehavior(); profile((ObjectNode) recursive.path("workflowAssistance"), "setup").putArray("delegatedProfiles").add("setup");
        assertThatThrownBy(() -> compiler.validateConfiguration(recursive)).hasMessageContaining("existing different");
    }
    @Test void delegatedProfileCannotEscalateReadOnlyGoalOwner() {
        var behavior = goalBehavior();
        profile((ObjectNode) behavior.path("workflowAssistance"), "guide").putArray("delegatedProfiles").add("api");
        assertThatThrownBy(() -> compiler.validateConfiguration(behavior)).hasMessageContaining("cannot widen");
    }
    @Test void startupBundleRemainsBoundedBelowTheEncodedEnvironmentLimit() {
        var oversized = goalBehavior();
        oversized.at("/workflowAssistance/profiles").forEach(profile ->
            ((ObjectNode) profile).put("instructions", "\u20ac".repeat(8000)));
        assertThatThrownBy(() -> compiler.compileBehavior(oversized, authority())).hasMessageContaining("bundle size limit");
        assertThat(4 * ((WorkflowAssistanceCompiler.MAX_BYTES + 2) / 3)
            + "LOOMAI_WORKFLOW_ASSISTANCE_BUNDLE_BASE64=".length() + 1).isLessThan(131072);
    }
    private ObjectNode boundedBehavior() {
        var result = template.behavior(); var config = (ObjectNode) result.path("workflowAssistance");
        config.remove("goal"); ((ObjectNode) config.path("policy")).put("maxCorrections", 2);
        config.path("profiles").forEach(p -> ((ObjectNode) p).remove("delegatedProfiles"));
        return result;
    }
    private ObjectNode goalBehavior() {
        var result = boundedBehavior();
        ((ObjectNode) result.path("workflowAssistance")).putObject("goal").put("enabled", true)
            .put("maxElapsedSeconds", 86400).put("continuationDelaySeconds", 1)
            .put("maxConsecutiveNoProgress", 8).put("maxConsecutivePlannerFailures", 4);
        return result;
    }
    private ObjectNode profile(ObjectNode config, String id) {
        return (ObjectNode) java.util.stream.StreamSupport.stream(config.path("profiles").spliterator(), false)
            .filter(p -> id.equals(p.path("id").asText())).findFirst().orElseThrow();
    }
}
