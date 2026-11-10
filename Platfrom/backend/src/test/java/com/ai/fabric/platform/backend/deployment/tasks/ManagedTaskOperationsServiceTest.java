package com.ai.fabric.platform.backend.deployment.tasks;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManagedTaskOperationsServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final DeploymentTargetContextService targets = mock(DeploymentTargetContextService.class);
    final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    final PlatformAuditService audit = mock(PlatformAuditService.class);
    final HttpClient http = mock(HttpClient.class);
    final ManagedTaskOperationsService service = new ManagedTaskOperationsService(targets, authority, audit, mapper, http);
    DeploymentTargetContextService.Context context;
    JsonNode compiled;

    @BeforeEach void setup() throws Exception {
        var principal = new PlatformPrincipal("customer-user", PlatformRole.CUSTOMER_ADMIN, "Customer", "SUPABASE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        var deployment = new DeploymentEntity(); deployment.setId("dep-one"); deployment.setRuntimeBaseUrl("https://wrong-production.example");
        var target = new DeploymentTargetProfileEntity(); target.setId("stage");
        var release = new DeploymentReleaseEntity(); release.setId("rel-stage"); release.setRuntimeAuthorityFingerprint("target-proof");
        var version = new DeploymentVersionEntity(); version.setId("ver-stage");
        compiled = new ManagedTaskCompiler(mapper).compileBehavior(ManagedTaskCompilerTest.behavior(mapper), null);
        version.setBehaviorConfigJson(compiled.toString());
        context = new DeploymentTargetContextService.Context(deployment, target, release, version, "https://stage.example", null);
        when(targets.resolveCurrent(eq("dep-one"), eq("stage"), anyBoolean())).thenReturn(context);
        when(authority.issuePlatformProxyHeaders(eq("dep-one"), eq("stage"), eq("rel-stage"), eq("target-proof"), anyList(), any(Duration.class)))
            .thenReturn(Map.of("X-Private-Auth", "server-owned"));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void appliedCatalogueUsesSelectedVersionAndDoesNotCallNetworkOrExposePrompt() {
        JsonNode result = service.applied("dep-one", "stage");
        assertThat(result.path("configurationVersion").asText()).isEqualTo("ver-stage");
        assertThat(result.path("runtimeBaseUrl").asText()).isEqualTo("https://stage.example");
        assertThat(result.path("tasks")).hasSize(1);
        assertThat(result.toString()).doesNotContain("instructions", "requiredScopes", "server-owned");
        verify(targets).resolveCurrent("dep-one", "stage", false);
        verifyNoInteractions(http, authority, audit);
    }

    @Test void executesAppliedTaskWithOnlyServerChosenGrantAndSelectedTarget() throws Exception {
        respond(200, result("ver-stage", compiled.at("/managedTasks/contentHash").asText()));
        var result = service.execute("dep-one", "stage", "assess-request", mapper.readTree("{\"input\":{\"request\":\"Assess this\"}}"));
        assertThat(result.body().path("taskCode").asText()).isEqualTo("assess-request");
        verify(targets).resolveCurrent("dep-one", "stage", true);
        verify(authority).issuePlatformProxyHeaders("dep-one", "stage", "rel-stage", "target-proof", List.of("task:assess-request:execute"), Duration.ofMinutes(5));
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo("https://stage.example/api/tasks/v1/tasks/assess-request/execute");
        assertThat(request.getValue().headers().firstValue("X-Private-Auth")).contains("server-owned");
        verify(audit).record(eq("MANAGED_TASK_EXECUTED"), eq("DEPLOYMENT"), eq("dep-one"), argThat(fields -> !fields.containsKey("input")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "hash", "task"})
    void successfulResponseCannotMisrepresentAppliedConfiguration(String mismatch) throws Exception {
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) result("ver-stage", compiled.at("/managedTasks/contentHash").asText());
        response.put(switch (mismatch) { case "version" -> "configurationVersion"; case "hash" -> "taskConfigurationHash"; default -> "taskCode"; }, "different");
        respond(200, response);
        assertThatThrownBy(() -> service.execute("dep-one", "stage", "assess-request", input()))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("does not match");
        verifyNoInteractions(audit);
    }

    @Test void unavailableTaskIsNotSentToAnotherVersionOrInferredFromText() {
        assertThatThrownBy(() -> service.execute("dep-one", "stage", "other-task", input()))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Apply this task");
        verifyNoInteractions(http, authority);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"input\":{},\"scopes\":[\"task:other:execute\"]}", "{\"input\":{},\"prompt\":\"replace\"}",
        "{\"input\":{},\"model\":\"another-model\"}", "{\"input\":[]}", "{\"input\":{},\"correlationId\":4}"})
    void requestCannotOverrideServerConfiguration(String body) throws Exception {
        assertThatThrownBy(() -> service.execute("dep-one", "stage", "assess-request", mapper.readTree(body)))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(http, authority);
    }

    @Test void unauthenticatedAndMissingTargetDoNotResolveAnyRuntime() {
        assertThatThrownBy(() -> service.applied("dep-one", "")).isInstanceOf(ResponseStatusException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.applied("dep-one", "stage")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(targets, http, authority);
    }

    @Test void deniedTargetDoesNotIssueHeadersOrCallRuntime() {
        when(targets.resolveCurrent("dep-one", "stage", true)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.execute("dep-one", "stage", "assess-request", input())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(http, authority);
    }

    @Test void runtimeFailureIsNotRetriedOrTurnedIntoSuccess() throws Exception {
        respond(422, mapper.readTree("{\"code\":\"INVALID_TASK_INPUT\"}"));
        assertThat(service.execute("dep-one", "stage", "assess-request", input()).status()).isEqualTo(422);
        verify(http, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
        verifyNoInteractions(audit);
    }

    @Test void taskMustBeEnabledInAppliedVersionNotJustDraft() {
        context.version().setBehaviorConfigJson("{\"managedTasks\":{\"enabled\":false,\"tasks\":[]}}");
        assertThatThrownBy(() -> service.applied("dep-one", "stage")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("not enabled");
        verifyNoInteractions(http, authority);
    }

    @Test void explicitDecisionUsesSeparateGrantAndExactAppliedTargetWithoutRetry() throws Exception {
        respond(200, result("ver-stage", compiled.at("/managedTasks/contentHash").asText()));
        var decided = service.decide("dep-one", "stage", "assess-request", "action-receipt-1", mapper.readTree("{\"decision\":\"CONFIRM\"}"), "ver-stage");
        assertThat(decided.status()).isEqualTo(200);
        verify(authority).issuePlatformProxyHeaders("dep-one", "stage", "rel-stage", "target-proof", List.of("task:assess-request:confirm"), Duration.ofMinutes(5));
        var sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(1)).send(sent.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(sent.getValue().uri().toString()).isEqualTo("https://stage.example/api/tasks/v1/tasks/assess-request/action-proposals/action-receipt-1/decisions");
        assertThat(sent.getValue().headers().firstValue("If-Match")).contains("\"ver-stage\"");
        verify(audit).record(eq("MANAGED_TASK_PROPOSAL_DECIDED"), eq("DEPLOYMENT"), eq("dep-one"), anyMap());
    }

    @Test void humanReviewAndUnknownDecisionFieldsCannotBecomeImplicitConfirmations() throws Exception {
        for (String body : List.of("{\"decision\":\"CONFIRM\",\"parameters\":{}}", "{\"decision\":\"APPROVE\"}", "{\"decision\":true}"))
            assertThatThrownBy(() -> service.decide("dep-one", "stage", "assess-request", "action-receipt-1", mapper.readTree(body), "ver-stage"))
                .isInstanceOf(ResponseStatusException.class);
        var behavior = ManagedTaskCompilerTest.behavior(mapper);
        behavior.withArray("executionExtensions").add("HUMAN_REVIEW");
        ((com.fasterxml.jackson.databind.node.ObjectNode) behavior.at("/managedTasks/tasks/0")).put("confirmationMode", "HUMAN_REVIEW");
        compiled = new ManagedTaskCompiler(mapper).compileBehavior(behavior, null);
        context.version().setBehaviorConfigJson(compiled.toString());
        assertThatThrownBy(() -> service.decide("dep-one", "stage", "assess-request", "action-receipt-1", mapper.readTree("{\"decision\":\"CONFIRM\"}"), "ver-stage"))
            .hasMessageContaining("human review");
        verifyNoInteractions(http, authority);
    }

    @Test void staleConsoleVersionDoesNotCallTheRuntimeForEitherCommand() throws Exception {
        assertThatThrownBy(() -> service.execute("dep-one", "stage", "assess-request", input(), "stale"))
            .hasMessageContaining("applied version changed");
        assertThatThrownBy(() -> service.decide("dep-one", "stage", "assess-request", "action-receipt-1",
            mapper.readTree("{\"decision\":\"CONFIRM\"}"), "stale")).hasMessageContaining("applied version changed");
        verifyNoInteractions(http, authority);
    }

    @Test void failedSynthesisReceiptsRemainVisibleAndMustMatchTheSelectedVersion() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) result("ver-stage", compiled.at("/managedTasks/contentHash").asText());
        body.put("status", "FAILED").put("sideEffectsExecuted", true).put("sideEffectsStatus", "EXECUTED");
        body.putArray("actionExecutions").addObject().put("actionName", "save_preference").put("status", "EXECUTED");
        respond(502, body);
        assertThat(service.execute("dep-one", "stage", "assess-request", input()).body().path("actionExecutions")).hasSize(1);
        verify(http, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
        body.put("configurationVersion", "different-version");
        respond(502, body);
        assertThatThrownBy(() -> service.execute("dep-one", "stage", "assess-request", input())).hasMessageContaining("does not match");
    }

    JsonNode input() { return mapper.createObjectNode().set("input", mapper.createObjectNode().put("request", "Assess this")); }
    JsonNode result(String version, String hash) {
        return mapper.createObjectNode().put("taskCode", "assess-request").put("configurationVersion", version)
            .put("taskConfigurationHash", hash).put("status", "SUCCEEDED");
    }
    @SuppressWarnings("unchecked") void respond(int status, JsonNode body) throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
}
