package com.ai.fabric.runtime.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class WorkflowAssistanceBundleTest {
    static final ObjectMapper MAPPER = new ObjectMapper();
    static ObjectNode fixture() {
        try (var input = new ClassPathResource("workflow-assistance-compiler-fixture.json").getInputStream()) {
            return (ObjectNode) MAPPER.readTree(input);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    static WorkflowAssistanceBundle bundle(ObjectNode content) throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(content);
        return new WorkflowAssistanceBundle(MAPPER, Base64.getEncoder().encodeToString(bytes), hash(bytes));
    }
    static String hash(byte[] bytes) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    @Test void defaultsOffWithoutAHiddenWorkflowOrProfile() {
        var disabled = new WorkflowAssistanceBundle(MAPPER, "", "");
        assertThat(disabled.enabled()).isFalse();
        assertThatThrownBy(() -> disabled.select("ACTIONS", null)).hasMessage("WORKFLOW_TASK_UNAVAILABLE");
        assertThatThrownBy(() -> new WorkflowAssistanceBundle(MAPPER, "", "forged")).isInstanceOf(IllegalStateException.class);
    }
    @Test void publishedTaskBindingChoosesTheKnownExpertWithoutManagerInference() throws Exception {
        var fixture = fixture(); var bundle = bundle((ObjectNode) fixture.path("compiled"));
        assertThat(bundle.contentHash()).isEqualTo(fixture.path("contentHash").asText());
        assertThat(bundle.select("ACTIONS", null).specialist().toString()).isEqualTo("workflow-api@1");
        assertThat(bundle.select("SEARCH", null).specialist().toString()).isEqualTo("workflow-models-search@1");
        assertThat(bundle.select("BRAIN_SETUP", null).specialist().toString()).isEqualTo("workflow-setup@1");
        assertThat(bundle.select("KIT", null).requiredScopes()).doesNotContain("action:authoring_update_draft", "action:authoring_undo_request");
        assertThat(bundle.select("HUMAN_SUPPORT", null).requiredScopes()).contains("action:authoring_update_draft");
        assertThat(bundle.select("ACTIONS", "api").requiredScopes()).contains("configuration:author", "action:authoring_update_draft");
        assertThatThrownBy(() -> bundle.select("ACTIONS", "teams")).hasMessage("WORKFLOW_PROFILE_NOT_ALLOWED_FOR_TASK");
        assertThatThrownBy(() -> bundle.select("UNKNOWN_TASK", null)).hasMessage("WORKFLOW_TASK_UNAVAILABLE");
        assertThat(bundle.select("CONFIGURATION", null).id()).isEqualTo("guide");
    }
    @Test void changedBytesAndDuplicateRoutesFailBeforeInference() throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(fixture().path("compiled"));
        assertThatThrownBy(() -> new WorkflowAssistanceBundle(MAPPER, Base64.getEncoder().encodeToString(bytes), "sha256:" + "0".repeat(64)))
            .isInstanceOf(IllegalStateException.class);
        var content = (ObjectNode) fixture().path("compiled");
        content.withArray("taskBindings").add(content.path("taskBindings").get(0));
        assertThatThrownBy(() -> bundle(content)).isInstanceOf(IllegalStateException.class);
    }
    @Test void delegationDescriptorsUsePinnedResourceInstructionsAndCannotWidenPublishedScopes() throws Exception {
        var content = (ObjectNode) fixture().path("compiled");
        for (var item : content.path("profiles")) {
            var profile = (ObjectNode) item;
            var allowed = profile.putArray("allowedActions");
            for (var scope : profile.path("requiredScopes")) if (scope.asText().startsWith("action:")) allowed.add(scope.asText().substring(7));
            if ("setup".equals(profile.path("id").asText())) profile.putArray("delegatedProfiles").add("api");
        }
        var published = bundle(content);
        assertThat(published.profiles()).hasSize(9);
        assertThat(published.byId("setup").delegatedProfiles()).containsExactly("api");
        assertThatThrownBy(() -> published.profiles().clear()).isInstanceOf(UnsupportedOperationException.class);
        var registry = org.mockito.Mockito.mock(ai.fabric.execution.specialist.SpecialistRegistry.class);
        var registered = org.mockito.Mockito.mock(ai.fabric.execution.specialist.RegisteredSpecialist.class);
        org.mockito.Mockito.when(registered.contentHash()).thenReturn("a".repeat(64));
        org.mockito.Mockito.when(registry.requireRegistered(published.byId("api").specialist())).thenReturn(registered);
        var descriptor = published.delegationProfiles("setup", registry).getFirst();
        assertThat(descriptor.definitionFingerprint()).isEqualTo("a".repeat(64));
        assertThat(descriptor.instructions()).contains("Prepare all supported operations");
        assertThat(descriptor.allowedActions()).contains("authoring_update_draft");
        ((ObjectNode) content.path("profiles").get(0)).withArray("allowedActions").add("unpublished_admin_write");
        assertThatThrownBy(() -> bundle(content)).isInstanceOf(IllegalStateException.class);
    }
    @Test void nativeLifecyclesRemainTypedInsteadOfUnparseablePollingStates() {
        var empty = MAPPER.createObjectNode();
        assertThat(RuntimeDurableGoalHost.lifecycle(ai.fabric.execution.gateway.ExecutionHandleStatus.REJECTED, empty)).isEqualTo("BLOCKED");
        assertThat(RuntimeDurableGoalHost.lifecycle(ai.fabric.execution.gateway.ExecutionHandleStatus.EXPIRED, empty)).isEqualTo("FAILED");
        assertThat(RuntimeDurableGoalHost.lifecycle(ai.fabric.execution.gateway.ExecutionHandleStatus.FAILED, empty.put("state", "UNSUPPORTED"))).isEqualTo("BLOCKED");
        assertThat(RuntimeDurableGoalHost.lifecycle(ai.fabric.execution.gateway.ExecutionHandleStatus.PAUSED, empty.put("state", "NEEDS_INPUT"))).isEqualTo("WAITING_FOR_INPUT");
    }
    @Test void publishedGoalBundleMayExceed64kButCannotExceedTheSingleEnvironmentValueBound() throws Exception {
        var content = (ObjectNode) fixture().path("compiled");
        int initial = MAPPER.writeValueAsBytes(content).length;
        content.put("padding", "x".repeat(90000 - initial));
        assertThat(MAPPER.writeValueAsBytes(content).length).isBetween(65537, WorkflowAssistanceBundle.MAX_BUNDLE_BYTES);
        assertThat(bundle(content).enabled()).isTrue();
        content.put("padding", "x".repeat(WorkflowAssistanceBundle.MAX_BUNDLE_BYTES));
        assertThatThrownBy(() -> bundle(content)).hasMessageContaining("96000 bytes");
        assertThatThrownBy(() -> new WorkflowAssistanceBundle(MAPPER, "x".repeat(WorkflowAssistanceBundle.MAX_BASE64_CHARACTERS + 1), "sha256:" + "0".repeat(64)))
            .hasMessageContaining("128000 Base64 characters");
    }
}
