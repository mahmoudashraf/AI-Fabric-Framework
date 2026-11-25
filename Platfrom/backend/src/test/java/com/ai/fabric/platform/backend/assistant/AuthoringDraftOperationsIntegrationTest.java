package com.ai.fabric.platform.backend.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;

/** Real database transactions/receipts; a local resource adapter stands in for supported draft services. */
@SpringBootTest(properties = {"platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false",
    "platform.assistant.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@ActiveProfiles("test")
class AuthoringDraftOperationsIntegrationTest {
    @Autowired AuthoringDraftOperations operations;
    @Autowired AuthoringSessionRepository sessions;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformUserRepository users;
    @Autowired ObjectMapper mapper;
    @Autowired AuthoringChanges changes;
    @SpyBean AuthoringCrypto crypto;
    @MockBean AuthoringResourceService resources;
    @MockBean AuthoringDraftLock draftLock;
    String sessionId, turnId, customerId, resourceId, userId, actor;
    AtomicBoolean delegated = new AtomicBoolean(true);
    AuthoringDraftOperations.Caller caller;

    @BeforeEach void setup() throws Exception {
        String suffix = UUID.randomUUID().toString();
        sessionId = "cas-" + suffix; turnId = "turn-" + suffix; customerId = "customer-" + suffix;
        resourceId = "dep-" + suffix; userId = "user-" + suffix; actor = suffix + "@example.test";
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setSlug(customerId);
        customer.setName("Manual"); customer.setStatus("ACTIVE"); customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now());
        customers.saveAndFlush(customer);
        var user = new PlatformUserEntity(); user.setId(userId); user.setEmail(actor); user.setDisplayName("Local author");
        user.setPasswordHash("not-a-login-credential"); user.setRole("PLATFORM_ADMIN"); user.setStatus("ACTIVE");
        user.setCreatedAt(Instant.now()); user.setUpdatedAt(Instant.now()); users.saveAndFlush(user);
        var session = new AuthoringSessionEntity(); session.id = sessionId; session.actorId = actor; session.customerId = customerId;
        session.resourceKind = "DEPLOYMENT_DRAFT"; session.resourceId = resourceId; session.task = "BEHAVIOUR";
        session.status = "RUNNING"; session.activeTurnId = turnId; session.deadline = Instant.now().plusSeconds(120);
        session.createdAt = Instant.now(); session.updatedAt = Instant.now(); session.expiresAt = Instant.now().plusSeconds(3600);
        var content = mapper.createObjectNode(); content.putArray("sources"); content.putArray("turns").addObject().put("id", turnId);
        session.encryptedContent = crypto.encrypt(sessionId, content.toString()); sessions.saveAndFlush(session);
        var principal = new PlatformPrincipal(actor, PlatformRole.PLATFORM_ADMIN, "Local author", "SESSION", userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))));
        when(resources.load("DEPLOYMENT_DRAFT", resourceId, "BEHAVIOUR")).thenAnswer(call -> {
            assertThat(PlatformSecurityContext.currentPrincipal().actorId()).isEqualTo(actor);
            if (!delegated.get()) throw new ResponseStatusException(FORBIDDEN, "Delegation revoked");
            var configuration = mapper.createObjectNode(); configuration.putObject("promptConfig")
                .put("systemPrompt", customers.findById(customerId).orElseThrow().getName()).put("unrelated", "keep");
            return new AuthoringResourceService.Snapshot(customerId, "Fixture", configuration, mapper.createObjectNode(),
                List.of("/promptConfig"), "draft", true, "");
        });
        when(resources.check(anyString(), anyString(), any(), any())).thenReturn(mapper.createObjectNode().put("valid", true));
        when(resources.save(eq("DEPLOYMENT_DRAFT"), eq(resourceId), eq("BEHAVIOUR"), any())).thenAnswer(call -> {
            var before = resources.load("DEPLOYMENT_DRAFT", resourceId, "BEHAVIOUR");
            var result = changes.apply(before.configuration(), call.getArgument(3), before.allowedPaths());
            var current = customers.findById(customerId).orElseThrow(); current.setName(result.candidate().at("/promptConfig/systemPrompt").asText());
            customers.saveAndFlush(current);
            return new AuthoringResourceService.Saved(result.diff(), mapper.createObjectNode().put("valid", true),
                mapper.createObjectNode().put("draftId", "draft").put("published", false), result.candidate());
        });
        caller = new AuthoringDraftOperations.Caller(sessionId, turnId, "assistant-brain", "verified-release");
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext(); sessions.deleteById(sessionId); users.deleteById(userId); customers.deleteById(customerId);
    }
    void bind(boolean preview) {
        operations.bind(sessionId, turnId, new AuthoringDraftOperations.Binding("assistant-brain", "verified-release", "writer@1", "hash", 3, preview));
        SecurityContextHolder.clearContext();
    }
    JsonNode change(String text) {
        return mapper.createArrayNode().add(mapper.createObjectNode().put("operation", "SET")
            .put("path", "/promptConfig/systemPrompt").put("value", text));
    }
    String operation(int step) { return turnId + ":step:" + step; }
    @Test void emptyOptionalDefinitionFiltersRetainTheCurrentTaskConfiguration() {
        bind(false);
        var unfiltered = operations.context(caller).path("configuration");
        var empty = operations.context(caller, new AuthoringDraftOperations.ContextRead(List.of(), List.of()));
        assertThat(empty.path("configuration")).isEqualTo(unfiltered);
        assertThat(empty.has("configurationSelection")).isFalse();
    }
    @Test void actionDefinitionFiltersCannotHideANonActionTasksConfiguration() {
        bind(false);
        var unfiltered = operations.context(caller).path("configuration");
        var selected = operations.context(caller, new AuthoringDraftOperations.ContextRead(List.of("unrelated-action"), null));
        assertThat(selected.path("configuration")).isEqualTo(unfiltered);
        assertThat(selected.path("configurationSelection").asText()).isEqualTo("TASK_CONTEXT_NOT_ACTION_DEFINITIONS");
    }
    @Test void nonemptyActionDefinitionFiltersStillNarrowOnlyTheSelectedDefinitions() throws Exception {
        var session = sessions.findById(sessionId).orElseThrow(); session.task = "ACTIONS"; sessions.saveAndFlush(session);
        var config = mapper.readTree("{\"actionsConfig\":{\"actions\":[{\"name\":\"first\"},{\"name\":\"second\"}]},\"routingConfig\":{\"actions\":{\"first\":{\"method\":\"GET\"},\"second\":{\"method\":\"POST\"}}}}");
        when(resources.load("DEPLOYMENT_DRAFT", resourceId, "ACTIONS")).thenReturn(new AuthoringResourceService.Snapshot(
            customerId, "Fixture", config, mapper.createObjectNode(), List.of("/actionsConfig", "/routingConfig"), "draft", true, ""));
        bind(false);
        var selected = operations.context(caller, new AuthoringDraftOperations.ContextRead(List.of("second"), List.of("first")));
        assertThat(selected.path("configurationSelection").asText()).isEqualTo("SELECTED_DEFINITIONS");
        assertThat(selected.at("/configuration/actionsConfig/actions")).hasSize(1);
        assertThat(selected.at("/configuration/actionsConfig/actions/0/name").asText()).isEqualTo("second");
        assertThat(selected.at("/configuration/routingConfig/actions").properties()).hasSize(1);
        assertThat(selected.at("/configuration/routingConfig/actions/first/method").asText()).isEqualTo("GET");
    }
    @Test void explicitPreparationCarriesAMatchingSaveIdentityAndNoOpChecksNeedNoWrite() throws Exception {
        var session = sessions.findById(sessionId).orElseThrow();
        var content = mapper.readTree(crypto.decrypt(sessionId, session.encryptedContent));
        ((com.fasterxml.jackson.databind.node.ObjectNode) content.at("/turns/0")).put("command", "PREPARE");
        session.encryptedContent = crypto.encrypt(sessionId, content.toString()); sessions.saveAndFlush(session);
        bind(false);
        var checked = operations.check(caller, change("Requested result"));
        assertThat(checked.path("draftSaveRequested").asBoolean()).isTrue();
        assertThat(checked.path("hasChanges").asBoolean()).isTrue();
        var saved = operations.save(caller, operation(1), change("Requested result"));
        assertThat(saved.path("parameterFingerprint")).isEqualTo(checked.path("mutationFingerprint"));
        var noOp = operations.check(caller, change("Requested result"));
        assertThat(noOp.path("hasChanges").asBoolean()).isFalse();
        assertThat(operations.status(caller)).hasSize(1);
    }
    @Test void previewChecksNeverDeclareASaveRequirement() throws Exception {
        var session = sessions.findById(sessionId).orElseThrow();
        var content = mapper.readTree(crypto.decrypt(sessionId, session.encryptedContent));
        ((com.fasterxml.jackson.databind.node.ObjectNode) content.at("/turns/0")).put("command", "PREPARE");
        session.encryptedContent = crypto.encrypt(sessionId, content.toString()); sessions.saveAndFlush(session);
        bind(true);
        assertThat(operations.check(caller, change("Preview")).path("draftSaveRequested").asBoolean()).isFalse();
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void selectedProseRemainsAvailableWhenNoFormalOperationsWereParsed() throws Exception {
        var session = sessions.findById(sessionId).orElseThrow(); session.task = "ACTIONS";
        var content = mapper.readTree(crypto.decrypt(sessionId, session.encryptedContent));
        var source = ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("sources")).addObject();
        source.put("sourceId", "src-prose").put("title", "API notes").put("mediaType", "text/plain")
            .put("content", "GET https://example.test/products is public and returns named products.").putArray("operations");
        var turn = (com.fasterxml.jackson.databind.node.ObjectNode) content.path("turns").get(0);
        turn.put("command", "PREPARE"); turn.putArray("sourceIds").add("src-prose"); turn.putArray("operationIds");
        session.encryptedContent = crypto.encrypt(sessionId, content.toString()); sessions.saveAndFlush(session);
        when(resources.load("DEPLOYMENT_DRAFT", resourceId, "ACTIONS")).thenReturn(new AuthoringResourceService.Snapshot(
            customerId, "Fixture", mapper.createObjectNode(), mapper.createObjectNode(),
            List.of("/actionsConfig", "/routingConfig"), "draft", true, ""));
        bind(false);
        var sourceCaller = new AuthoringDraftOperations.Caller(sessionId, turnId, "assistant-brain", "verified-release", null, true);
        var context = operations.context(sourceCaller);
        assertThat(context.path("sourceIds")).hasSize(1);
        assertThat(context.path("selectedSourceCount").asInt()).isEqualTo(1);
        assertThat(context.at("/selectedSourcePreviews/0/excerpt").asText()).contains("https://example.test/products");
        assertThat(context.at("/selectedSourcePreviews/0/complete").asBoolean()).isTrue();
        assertThat(operations.context(caller).has("selectedSourcePreviews")).isFalse();
        assertThat(context.at("/preparationScope/operationSelection").asText()).isEqualTo("ALL_SELECTED_SOURCE_OPERATIONS");
        assertThat(context.at("/preparationScope/documentedOperationCount").asInt()).isZero();
        var selected = operations.selectedSources(caller);
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).path("id").asText()).isEqualTo("src-prose");
        assertThat(selected.get(0).path("content").asText()).contains("https://example.test/products");
        var stored = sessions.findById(sessionId).orElseThrow();
        var large = mapper.readTree(crypto.decrypt(sessionId, stored.encryptedContent));
        ((com.fasterxml.jackson.databind.node.ObjectNode) large.at("/sources/0")).put("content", "Approved text. ".repeat(3000));
        stored.encryptedContent = crypto.encrypt(sessionId, large.toString()); sessions.saveAndFlush(stored);
        var limited = operations.context(sourceCaller);
        assertThat(limited.at("/selectedSourcePreviews/0/excerpt").asText()).hasSize(16000);
        assertThat(limited.at("/selectedSourcePreviews/0/complete").asBoolean()).isFalse();
        assertThat(operations.selectedSources(caller).get(0).path("content").asText()).hasSize(45000);
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void aDisabledSourceToolCannotBeBypassedThroughContextPreviews() throws Exception {
        var session = sessions.findById(sessionId).orElseThrow();
        var content = mapper.readTree(crypto.decrypt(sessionId, session.encryptedContent));
        ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("sources")).addObject()
            .put("sourceId", "src-private").put("title", "Private notes").put("content", "private-selected-canary");
        ((com.fasterxml.jackson.databind.node.ObjectNode) content.at("/turns/0")).putArray("sourceIds").add("src-private");
        session.encryptedContent = crypto.encrypt(sessionId, content.toString()); sessions.saveAndFlush(session);
        operations.bind(sessionId, turnId, new AuthoringDraftOperations.Binding("assistant-brain", "verified-release", "reader@1", "hash", 0, true,
            List.of("authoring_get_context", "authoring_get_work_status")));
        SecurityContextHolder.clearContext();
        var sourceCaller = new AuthoringDraftOperations.Caller(sessionId, turnId, "assistant-brain", "verified-release", "authoring_get_context", true);
        var context = operations.context(sourceCaller);
        assertThat(context.has("selectedSourcePreviews")).isFalse();
        assertThat(context.toString()).doesNotContain("private-selected-canary");
        assertThatThrownBy(() -> operations.selectedSources(new AuthoringDraftOperations.Caller(sessionId, turnId,
            "assistant-brain", "verified-release", "authoring_read_selected_sources", true))).hasMessageContaining("ACCESS_DENIED");
    }
    @Test void sourcePreviewsCannotReintroduceUnselectedOperationsOrRawContractContent() throws Exception {
        var session = sessions.findById(sessionId).orElseThrow();
        var content = mapper.readTree(crypto.decrypt(sessionId, session.encryptedContent));
        var source = ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("sources")).addObject()
            .put("sourceId", "src-contract").put("title", "API contract").put("content", "Raw contract also contains /private-unselected-route");
        var parsed = source.putArray("operations");
        parsed.addObject().put("id", "selected").put("path", "/public-products");
        parsed.addObject().put("id", "unselected").put("path", "/private-unselected-route");
        var turn = (com.fasterxml.jackson.databind.node.ObjectNode) content.at("/turns/0");
        turn.putArray("sourceIds").add("src-contract"); turn.putArray("operationIds").add("selected");
        session.encryptedContent = crypto.encrypt(sessionId, content.toString()); sessions.saveAndFlush(session);
        bind(false);
        var sourceCaller = new AuthoringDraftOperations.Caller(sessionId, turnId, "assistant-brain", "verified-release", null, true);
        var preview = operations.context(sourceCaller).path("selectedSourcePreviews").get(0);
        assertThat(preview.path("representation").asText()).isEqualTo("PARSED_OPERATIONS");
        assertThat(preview.path("excerpt").asText()).contains("/public-products").doesNotContain("/private-unselected-route", "Raw contract");
        assertThat(this.operations.selectedSources(sourceCaller).toString()).doesNotContain("/private-unselected-route", "Raw contract");
    }
    @Test void committedWriteAndEncryptedReceiptReplayWithoutSecondSave() {
        bind(false); var first = operations.save(caller, operation(1), change("Helpful"));
        assertThat(first.path("status").asText()).isEqualTo("EXECUTED");
        assertThat(operations.save(caller, operation(1), change("Helpful"))).isEqualTo(first);
        verify(resources, times(1)).save(anyString(), anyString(), anyString(), any());
        assertThat(customers.findById(customerId).orElseThrow().getName()).isEqualTo("Helpful");
        assertThat(sessions.findById(sessionId).orElseThrow().encryptedContent).doesNotContain("Helpful", "principal", "operationId");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void sameOperationDifferentPayloadIsRejected() {
        bind(false); operations.save(caller, operation(1), change("Helpful"));
        assertThatThrownBy(() -> operations.save(caller, operation(1), change("Different")))
            .hasMessageContaining("AUTHORING_OPERATION_REPLAY_CONFLICT");
        verify(resources, times(1)).save(anyString(), anyString(), anyString(), any());
    }
    @Test void receiptPersistenceFailureRollsBackTheResourceWriteInTheSameTransaction() {
        bind(false); doThrow(new IllegalStateException("Simulated receipt failure")).when(crypto).encrypt(eq(sessionId), anyString());
        try {
            assertThatThrownBy(() -> operations.save(caller, operation(1), change("Must roll back"))).hasMessageContaining("receipt failure");
            assertThat(customers.findById(customerId).orElseThrow().getName()).isEqualTo("Manual");
        } finally { doCallRealMethod().when(crypto).encrypt(eq(sessionId), anyString()); }
        assertThat(operations.status(caller)).isEmpty();
    }
    @Test void previewAllowsReadingAndCheckingButNeverSaving() {
        bind(true); assertThat(operations.context(caller).path("configuration").isObject()).isTrue();
        assertThat(operations.check(caller, change("Preview")).path("saved").asBoolean()).isFalse();
        assertThatThrownBy(() -> operations.save(caller, operation(1), change("Preview"))).hasMessageContaining("AUTHORING_WORK_READ_ONLY");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void swappedReleaseAndSessionCannotReadOrWrite() {
        bind(false);
        var other = new AuthoringDraftOperations.Caller(sessionId, turnId, "assistant-brain", "other-release");
        assertThatThrownBy(() -> operations.context(other)).hasMessageContaining("ACCESS_DENIED");
        var missing = new AuthoringDraftOperations.Caller("missing-session", turnId, "assistant-brain", "verified-release");
        assertThatThrownBy(() -> operations.context(missing)).hasMessageContaining("ACCESS_DENIED");
    }
    @Test void revokedUserAndDelegationAreRecheckedForEveryOperation() {
        bind(false); operations.context(caller);
        delegated.set(false);
        assertThatThrownBy(() -> operations.save(caller, operation(1), change("No"))).hasMessageContaining("Delegation revoked");
        delegated.set(true);
        var user = users.findById(userId).orElseThrow(); user.setStatus("DISABLED"); users.saveAndFlush(user);
        assertThatThrownBy(() -> operations.context(caller)).hasMessageContaining("ACCESS_DENIED");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void cancelBlocksLateWritesButRetainsActualReceiptsForReadback() {
        bind(false); operations.save(caller, operation(1), change("Saved"));
        var session = sessions.findById(sessionId).orElseThrow(); session.status = "IDLE"; session.activeTurnId = null; sessions.saveAndFlush(session);
        assertThatThrownBy(() -> operations.save(caller, operation(2), change("Late"))).hasMessageContaining("NOT_ACTIVE");
        assertThat(operations.status(caller)).hasSize(1);
        assertThat(customers.findById(customerId).orElseThrow().getName()).isEqualTo("Saved");
    }
    @Test void writesAreFiniteAndDistinctCorrectionsRemainAllowed() {
        bind(false);
        for (int step = 1; step <= 3; step++) operations.save(caller, operation(step), change("Revision " + step));
        assertThatThrownBy(() -> operations.save(caller, operation(4), change("Too many"))).hasMessageContaining("AUTHORING_WRITE_LIMIT");
        assertThat(operations.status(caller)).hasSize(3);
    }
    @Test void outOfScopeFieldsAndCredentialValuesRemainDenied() {
        bind(false);
        var invalid = mapper.createArrayNode().add(mapper.createObjectNode().put("operation", "SET")
            .put("path", "/securityConfig/enabled").put("value", false));
        assertThatThrownBy(() -> operations.check(caller, invalid)).hasMessageContaining("outside this configuration task");
        var credential = mapper.createArrayNode().add(mapper.createObjectNode().put("operation", "SET")
            .put("path", "/promptConfig/apiKey").put("value", "do-not-save"));
        assertThatThrownBy(() -> operations.check(caller, credential)).isInstanceOf(ResponseStatusException.class);
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void frozenProfileWithoutAWriteToolCannotBeExpandedByAReceiverCall() {
        operations.bind(sessionId, turnId, new AuthoringDraftOperations.Binding("assistant-brain", "verified-release", "reader@1", "hash", 3, false,
            List.of("authoring_get_context", "authoring_get_work_status")));
        SecurityContextHolder.clearContext();
        assertThat(operations.context(caller).path("configuration").isObject()).isTrue();
        assertThatThrownBy(() -> operations.save(caller, operation(1), change("Not authorized"))).hasMessageContaining("ACCESS_DENIED");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void aNewPublishedDraftCannotSilentlyBecomeTheTargetOfAnOldRequest() {
        bind(false);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "The editable draft changed."))
            .when(draftLock).lock("DEPLOYMENT_DRAFT", resourceId, "draft");
        assertThatThrownBy(() -> operations.save(caller, operation(1), change("Late old request"))).hasMessageContaining("draft changed");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
        assertThat(operations.status(caller)).isEmpty();
    }
}
