package com.ai.fabric.runtime.tasks;

import static com.ai.fabric.runtime.tasks.ManagedTaskFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import ai.fabric.execution.gateway.AIExecutionGateway;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.execution.specialist.client.DefaultSpecialistClientFactory;
import ai.fabric.execution.specialist.client.SpecialistInvocation;
import ai.fabric.execution.specialist.manifest.SpecialistManifestRuntimeStatus;
import ai.fabric.execution.specialist.manifest.SpecialistResourceBundle;
import com.ai.fabric.runtime.AIFabricRuntimeApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(classes = AIFabricRuntimeApplication.class,
    args = "--spring.config.import=classpath:test-runtime-entity-config.yml",
    properties = {
        "spring.datasource.url=jdbc:h2:mem:managed-tasks-startup;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "OPENAI_ENABLED=false", "ai.providers.openai.enabled=false", "AI_VECTOR_DB_TYPE=memory",
        "AI_EXECUTION_MANIFESTS_ENABLED=true",
        "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=startup-task-backend-key",
        "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=startup-task-assertion-key",
        "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS=backend-test",
        "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES=dep-123",
        "AI_FABRIC_RUNTIME_DEPLOYMENT_ID=dep-123", "AI_FABRIC_RUNTIME_TENANT_ID=tenant-123",
        "AI_FABRIC_RUNTIME_CUSTOMER_ID=customer-123", "PLATFORM_DEPLOYMENT_VERSION_ID=version-123",
        "logging.level.ai.fabric=WARN"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ManagedTasksStartupTest {
    private static final ObjectNode FIXTURE = combinedFixture();
    @Autowired ManagedTasksBundle bundle;
    @Autowired SpecialistRegistry specialists;
    @Autowired SpecialistResourceBundle resources;
    @Autowired SpecialistManifestRuntimeStatus manifests;
    @Autowired ManagedTaskService tasks;
    @Autowired ManagedTasksHealthIndicator health;
    @Autowired HealthEndpointGroups healthGroups;

    @DynamicPropertySource static void bundle(DynamicPropertyRegistry properties) throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(FIXTURE);
        properties.add("LOOMAI_MANAGED_TASKS_BUNDLE_BASE64", () -> Base64.getEncoder().encodeToString(bytes));
        properties.add("LOOMAI_MANAGED_TASKS_BUNDLE_HASH", () -> hash(bytes));
    }

    @Test void officialReleasedBootstrapCompilesActualMainJsonAndEquivalentTextResources() throws Exception {
        assertThat(bundle.enabled()).isTrue();
        assertThat(manifests.ready()).isTrue();
        assertThat(manifests.diagnostics()).isEmpty();
        assertThat(resources.diagnostics()).isEmpty();
        assertThat(tasks.ready()).isTrue();
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
        for (String ref : new String[] {"task-assess-request@1", "task-assess-text@1"}) {
            var specialist = specialists.require(SpecialistId.parse(ref));
            assertThat(specialist.executionProfile().writeEnabled()).isFalse();
            @SuppressWarnings("unchecked") var adapter = (ai.fabric.execution.specialist.SpecialistInputAdapter<JsonNode>) specialist.inputAdapter();
            var wrapper = MAPPER.createObjectNode().put("request", "Assess request: Assess the supplied support request.");
            wrapper.putObject("taskInput").put("request", "How can I connect this?");
            assertThatCode(() -> adapter.validate(wrapper)).doesNotThrowAnyException();
            assertThat(adapter.renderModelInput(wrapper)).contains("Untrusted application JSON context", "How can I connect this?");
            @SuppressWarnings("unchecked") var output = (ai.fabric.execution.specialist.SpecialistOutputAdapter<JsonNode>) specialist.outputAdapter();
            assertThatCode(() -> output.validate(MAPPER.createObjectNode().put("answer", "A bounded answer."))).doesNotThrowAnyException();
        }
        var wire = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(tasks.status());
        assertThat(MAPPER.readTree(wire).path("tasks")).isEqualTo(FIXTURE.path("tasks"));
        assertThat(tasks.status()).containsEntry("contractVersion", ManagedTasksBundle.CONTRACT).containsEntry("contentHash", bundle.contentHash());
        assertThat(healthGroups.get("readiness").isMember("managedTasks")).isTrue();
        assertThat(healthGroups.get("liveness").isMember("managedTasks")).isFalse();
    }

    @Test void actualJsonNodeClientBindingForwardsWrapperAndDeadlineWithoutAnyInference() {
        var gateway = mock(AIExecutionGateway.class);
        var factory = new DefaultSpecialistClientFactory(specialists, gateway, MAPPER);
        try (var h = new ManagedTaskServiceTest.Harness(fixture())) {
            var result = h.success(MAPPER.createObjectNode().put("answer", "A verified response."), java.util.List.of());
            doReturn(result).when(gateway).execute(any());
            var wrapper = MAPPER.createObjectNode().put("request", "Assess request: Assess the supplied support request.");
            wrapper.putObject("taskInput").put("request", "Explain the next step.");
            var trusted = ai.fabric.execution.context.TrustedExecutionContext.application("verified-backend", null, "tenant-123", java.util.Set.of("specialist:task-assess-request@1"));
            var deadline = java.time.Instant.now().plusSeconds(20);
            var actual = factory.bind(SpecialistId.parse("task-assess-request@1"), JsonNode.class, JsonNode.class)
                .execute(new SpecialistInvocation<>(wrapper, trusted, null, deadline, null));
            assertThat(actual.output()).isEqualTo(result.output());
            var capture = org.mockito.ArgumentCaptor.forClass(ai.fabric.execution.gateway.AIExecutionRequest.class);
            verify(gateway).execute(capture.capture());
            assertThat(capture.getValue().input()).isEqualTo(wrapper);
            assertThat(capture.getValue().deadline()).isEqualTo(deadline);
        }
    }

    private static ObjectNode combinedFixture() {
        var all = fixture();
        var text = textFixture();
        // Rename exact resource identifiers in this test variant, not user text or model output.
        for (var resource : text.path("resources")) {
            var metadata = (ObjectNode) resource.path("metadata");
            metadata.put("name", metadata.path("name").asText().replace("task-assess-request", "task-assess-text"));
            if ("Specialist".equals(resource.path("kind").asText())) {
                ((ObjectNode) resource.at("/spec/input")).put("schemaRef", "task-assess-text-input@1");
                ((ObjectNode) resource.at("/spec/output")).put("schemaRef", "task-assess-text-output@1");
                ((ObjectNode) resource.at("/spec/instructions")).put("promptProfileRef", "task-assess-text@1");
            }
            ((com.fasterxml.jackson.databind.node.ArrayNode) all.path("resources")).add(resource);
        }
        var entry = (ObjectNode) text.at("/tasks/0");
        entry.put("code", "assess-text").put("specialistRef", "task-assess-text@1");
        entry.putArray("requiredScopes").add("specialist:task-assess-text@1");
        ((com.fasterxml.jackson.databind.node.ArrayNode) all.path("tasks")).add(entry);
        return all;
    }
}
