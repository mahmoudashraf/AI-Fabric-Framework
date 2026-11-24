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
}
