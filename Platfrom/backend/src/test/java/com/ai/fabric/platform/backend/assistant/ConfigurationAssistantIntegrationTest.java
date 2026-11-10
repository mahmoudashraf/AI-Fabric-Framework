package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real encrypted sessions/transactions and async lifecycle; inference/resource endpoints are explicit fixtures. */
@SpringBootTest(properties = {"platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false",
    "platform.assistant.enabled=true", "platform.assistant.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@ActiveProfiles("test")
class ConfigurationAssistantIntegrationTest {
    @Autowired ConfigurationAssistantService assistant;
    @Autowired AuthoringSessionRepository sessions;
    @Autowired PlatformCustomerRepository customers;
    @Autowired ObjectMapper mapper;
    @Autowired AuthoringChanges changes;
    @MockBean AuthoringRuntimeClient runtime;
    @MockBean AuthoringResourceService resources;
    final AtomicReference<JsonNode> configuration = new AtomicReference<>();
    String customerId;
    String resourceId;
    String actor;

    @BeforeEach void prepare() throws Exception {
        customerId = "authoring-test-" + UUID.randomUUID(); resourceId = "dep-" + UUID.randomUUID(); actor = "actor-" + UUID.randomUUID();
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setSlug(customerId); customer.setName("Authoring test");
        customer.setStatus("ACTIVE"); customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now()); customers.saveAndFlush(customer);
        signIn(actor); configuration.set(mapper.readTree("{\"promptConfig\":{\"systemPrompt\":\"Manual\",\"other\":true}}"));
        when(runtime.configured()).thenReturn(true);
        when(resources.load(eq("DEPLOYMENT_DRAFT"), eq(resourceId), eq("BEHAVIOUR"))).thenAnswer(call -> snapshot());
        when(resources.check(anyString(), anyString(), any(), any())).thenReturn(mapper.createObjectNode().put("publishReady", true));
        when(resources.save(eq("DEPLOYMENT_DRAFT"), eq(resourceId), eq("BEHAVIOUR"), any())).thenAnswer(call -> {
            var result = changes.apply(configuration.get(), call.getArgument(3), List.of("/promptConfig"));
            configuration.set(result.candidate());
            return new AuthoringResourceService.Saved(result.diff(), mapper.createObjectNode().put("publishReady", true),
                mapper.createObjectNode().put("draftId", "draft-test").put("published", false), result.candidate());
        });
        when(resources.undo(eq("DEPLOYMENT_DRAFT"), eq(resourceId), eq("BEHAVIOUR"), any())).thenAnswer(call -> {
            var result = changes.undo(configuration.get(), call.getArgument(3), List.of("/promptConfig"));
            configuration.set(result.candidate());
            return new AuthoringResourceService.Saved(result.diff(), mapper.createObjectNode(),
                mapper.createObjectNode().put("draftId", "draft-test").put("published", false), result.candidate());
        });
        when(runtime.generate(any())).thenReturn(output("READY"));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void requestedAiEditSavesWithoutAcceptanceThenUndoUsesSameDraftPath() throws Exception {
        String id = create();
        assistant.turn(id, request(false), "save-one");
        var state = await(id);
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("SAVED");
        assertThat(state.at("/turns/0/receipt/published").asBoolean()).isFalse();
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("AI result");
        assertThat(configuration.get().at("/promptConfig/other").asBoolean()).isTrue();
        assistant.proposal(id, state.at("/turns/0/id").asText(), "undo");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
    }
    @Test void retryingTheSameTurnDoesNotBillOrSaveTwice() throws Exception {
        String id = create(); assistant.turn(id, request(false), "same-command"); await(id);
        assistant.turn(id, request(false), "same-command");
        verify(runtime, times(1)).generate(any()); verify(resources, times(1)).save(anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> assistant.turn(id,
            new ConfigurationAssistantService.TurnRequest("Different request", false, null, null), "same-command"))
            .hasMessageContaining("different request");
    }
    @Test void previewRequiresExplicitApplyButNormalConfigurationDoesNot() throws Exception {
        String id = create(); assistant.turn(id, request(true), "preview-one"); var preview = await(id);
        assertThat(preview.at("/turns/0/status").asText()).isEqualTo("PREVIEW");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
        assistant.proposal(id, preview.at("/turns/0/id").asText(), "apply");
        assertThat(assistant.get(id).at("/turns/0/status").asText()).isEqualTo("SAVED");
    }
    @Test void sourceAndQueryAreEncryptedAndDeclaredCredentialsAreRedacted() throws Exception {
        String id = create();
        assistant.addSource(id, new ConfigurationAssistantService.SourceRequest("API notes", "text/plain",
            "Private example\nAuthorization: Bearer actual-credential", null, null));
        var stored = sessions.findById(id).orElseThrow();
        assertThat(stored.encryptedContent).doesNotContain("Private example", "actual-credential");
        assertThat(assistant.get(id).path("sources").get(0).has("content")).isFalse();
        assistant.turn(id, new ConfigurationAssistantService.TurnRequest("Use the imported documentation", false, null, null), "with-document"); await(id);
        var captured = org.mockito.ArgumentCaptor.forClass(JsonNode.class); verify(runtime).generate(captured.capture());
        assertThat(captured.getValue().toString()).doesNotContain("actual-credential").contains("[REDACTED]");
    }
    @Test void deselectingAllSourcesReallySendsNoDocuments() throws Exception {
        String id = create();
        assistant.addSource(id, new ConfigurationAssistantService.SourceRequest("Not selected", "text/plain", "Private unselected notes", null, null));
        assistant.turn(id, request(false), "no-document"); await(id);
        var captured = org.mockito.ArgumentCaptor.forClass(JsonNode.class); verify(runtime).generate(captured.capture());
        assertThat(captured.getValue().path("sources")).isEmpty();
        assertThat(captured.getValue().toString()).doesNotContain("Private unselected notes");
    }
    @Test void anotherActorCannotReadEditCancelOrDeleteTheSession() {
        String id = create(); signIn("another-actor");
        assertThatThrownBy(() -> assistant.get(id)).hasMessageContaining("404");
        assertThatThrownBy(() -> assistant.cancel(id)).hasMessageContaining("404");
        assertThatThrownBy(() -> assistant.delete(id)).hasMessageContaining("404");
        assertThatThrownBy(() -> assistant.turn(id, request(false), "cross-actor")).hasMessageContaining("404");
        verify(runtime, never()).generate(any());
    }
    @Test void cancelledLateInferenceCannotSaveTheDraft() throws Exception {
        var started = new CountDownLatch(1); var finish = new CountDownLatch(1);
        when(runtime.generate(any())).thenAnswer(call -> { started.countDown(); finish.await(3, TimeUnit.SECONDS); return output("READY"); });
        String id = create(); assistant.turn(id, request(false), "cancel-one");
        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> assistant.cancel(id, "older-turn")).hasMessageContaining("newer assistant turn");
        assertThat(assistant.get(id).path("status").asText()).isEqualTo("RUNNING");
        assistant.cancel(id, assistant.get(id).path("activeTurnId").asText()); finish.countDown(); Thread.sleep(80);
        assertThat(assistant.get(id).at("/turns/0/status").asText()).isEqualTo("CANCELLED");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void reusesOnlyAuthorizedSameWorkspaceSourcesWithoutSharingThemAutomatically() throws Exception {
        String first = create();
        assistant.addSource(first, new ConfigurationAssistantService.SourceRequest("Shared API", "text/plain", "Authorized API notes", null, null));
        when(resources.load("DEPLOYMENT_DRAFT", "another-resource", "BEHAVIOUR")).thenAnswer(call -> snapshot());
        String second = assistant.create(new ConfigurationAssistantService.CreateSession("DEPLOYMENT_DRAFT", "another-resource", "BEHAVIOUR")).path("id").asText();
        assertThat(assistant.reusableSources(second)).hasSize(1);
        String sourceId = assistant.get(first).at("/sources/0/id").asText();
        assistant.reuseSources(second, new ConfigurationAssistantService.ReuseSources(first, List.of(sourceId)));
        assertThat(assistant.sourceContent(second, sourceId).path("content").asText()).contains("Authorized API notes");
        assistant.delete(first);
        assertThat(assistant.get(second).path("sources")).hasSize(1);
        signIn("another-actor");
        assertThatThrownBy(() -> assistant.sourceContent(second, sourceId)).hasMessageContaining("404");
    }
    @Test void malformedOrUnauthorizedOutputIsVisibleAndDoesNotSave() throws Exception {
        var invalid = output("READY"); ((ObjectNode) invalid.at("/changes/0")).put("path", "/providerConfig/apiKey");
        when(runtime.generate(any())).thenReturn(invalid);
        String id = create(); assistant.turn(id, request(false), "invalid-one");
        var result = await(id);
        assertThat(result.at("/turns/0/status").asText()).isEqualTo("FAILED");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void deletingSessionErasesSourcesAndDoesNotUndoSavedConfiguration() throws Exception {
        String id = create(); assistant.turn(id, request(false), "save-before-delete"); await(id); assistant.delete(id);
        assertThat(sessions.findById(id)).isEmpty();
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("AI result");
    }
    private String create() { return assistant.create(new ConfigurationAssistantService.CreateSession("DEPLOYMENT_DRAFT", resourceId, "BEHAVIOUR")).path("id").asText(); }
    private ConfigurationAssistantService.TurnRequest request(boolean preview) { return new ConfigurationAssistantService.TurnRequest("Make the requested wording change", preview, List.of(), List.of()); }
    private AuthoringResourceService.Snapshot snapshot() {
        return new AuthoringResourceService.Snapshot(customerId, "Test Brain", configuration.get(), mapper.createObjectNode(), List.of("/promptConfig"), "draft-test", true, "");
    }
    private ObjectNode output(String status) throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"contractVersion":"LOOMAI_CONFIGURATION_AUTHORING_V1","status":"%s","message":"Updated the requested draft wording.",
             "changes":[{"path":"/promptConfig/systemPrompt","operation":"SET","value":"AI result"}],"questions":[],"assumptions":[],"sourceReferences":[]}
            """.formatted(status));
    }
    private JsonNode await(String id) throws Exception {
        for (int i = 0; i < 160; i++) { var state = assistant.get(id); if (!"RUNNING".equals(state.path("status").asText())) return state; Thread.sleep(25); }
        throw new AssertionError("Assistant turn did not finish");
    }
    private void signIn(String id) {
        var auth = new UsernamePasswordAuthenticationToken(new PlatformPrincipal(id, PlatformRole.PLATFORM_ADMIN, "Fixture", "SESSION"), null,
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth); SecurityContextHolder.setContext(context);
    }
}
