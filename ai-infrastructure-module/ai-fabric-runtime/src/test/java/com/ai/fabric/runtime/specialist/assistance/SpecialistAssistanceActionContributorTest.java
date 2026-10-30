package com.ai.fabric.runtime.specialist.assistance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.fabric.dto.AIAccessSubjectContext;
import ai.fabric.config.ResponseSanitizationProperties;
import ai.fabric.execution.chain.*;
import ai.fabric.execution.context.*;
import ai.fabric.execution.gateway.*;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.client.*;
import ai.fabric.intent.action.*;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;
import ai.fabric.intent.orchestration.pipeline.PipelineContext;
import ai.fabric.intent.orchestration.request.*;
import ai.fabric.privacy.pii.PIIDetectionService;
import ai.fabric.security.ResponseSanitizer;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class SpecialistAssistanceActionContributorTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final SpecialistClientFactory clients = mock(SpecialistClientFactory.class);
    private final SpecialistChainGateway chains = mock(SpecialistChainGateway.class);
    private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();

    private SpecialistAssistanceBundle bundle(String scopes, boolean chain) throws Exception {
        String content = """
            {"contractVersion":"LOOMAI_SPECIALIST_ASSISTANCE_V1",
             "resources":[{"kind":"Specialist","metadata":{"name":"assist-area-setup","version":"1"}}],
             "invocations":[{"id":"setup","name":"Setup","description":"Application setup guidance",
               "targetType":"%s","targetRef":"%s","deadlineSeconds":30,"requiredScopes":%s}]}
            """.formatted(chain ? "CHAIN" : "SPECIALIST", chain ? "assist-chain-setup@1" : "assist-area-setup@1", scopes);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SpecialistAssistanceBundle(mapper, Base64.getEncoder().encodeToString(bytes),
            "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    private AIActionHandler handler(String scopes) throws Exception {
        return handler(scopes, false);
    }

    private AIActionHandler handler(String scopes, boolean chain) throws Exception {
        if (!beans.containsBean("clients")) beans.registerSingleton("clients", clients);
        if (!beans.containsBean("chains")) beans.registerSingleton("chains", chains);
        return new SpecialistAssistanceActionContributor(bundle(scopes, chain), beans.getBeanProvider(SpecialistClientFactory.class),
            beans.getBeanProvider(SpecialistChainGateway.class), mapper).getHandlers().getFirst();
    }

    private ActionContext context(Set<String> scopes, OrchestrationTurnGuard guard) {
        var auth = AIAccessSubjectContext.builder().subjectId("opaque-case-principal").subjectType("END_USER")
            .authMode("PRIVATE_RUNTIME_BACKEND_MEDIATED").callerType("TRUSTED_BACKEND").sessionId("opaque-case-principal")
            .customerId("internal-support-owner").tenantId("support-tenant").deploymentId("support-deployment")
            .issuer("support-issuer").audiences(List.of("support-audience")).grantedScopes(List.copyOf(scopes)).build();
        var context = OrchestrationContext.builder().userId(auth.getSubjectId()).conversationId("canonical-case-conversation")
            .orchestrationTurnGuard(guard).metadata(Map.of("subjectId", auth.getSubjectId(), "subjectType", auth.getSubjectType(),
                "authMode", auth.getAuthMode(), "callerType", auth.getCallerType(), "customerId", auth.getCustomerId(),
                "tenantId", auth.getTenantId(), "deploymentId", auth.getDeploymentId(), "grantedScopes", List.copyOf(scopes))).build();
        var trusted = new TrustedExecutionContext(new ExecutionPrincipal(auth.getSubjectId(), ExecutionPrincipalType.SERVICE),
            new ExecutionSubjectRef("conversation", "canonical-case-conversation"), ExecutionSource.APPLICATION,
            auth.getTenantId(), auth.getDeploymentId(), scopes, "parent-request", Instant.now())
            .withCallerIdentity(new ExecutionCallerIdentity(auth.getSubjectType(), auth.getAuthMode(), auth.getCallerType(),
                auth.getSessionId(), auth.getCustomerId(), auth.getIssuer(), auth.getAudiences(), null));
        var request = new OrchestrationRequest("Check my setup", context, trusted, ConversationPersistencePolicy.READ_ONLY);
        return new ActionContext(context, PipelineContext.from(request));
    }

    @Test void disabledBundleDoesNotPublishAnAction() {
        var empty = new SpecialistAssistanceBundle(mapper, "", "");
        assertThat(new SpecialistAssistanceActionContributor(empty, beans.getBeanProvider(SpecialistClientFactory.class),
            beans.getBeanProvider(ai.fabric.execution.chain.SpecialistChainGateway.class), mapper).getHandlers()).isEmpty();
    }

    @Test void tamperedPublishedContentFailsStartup() {
        assertThatThrownBy(() -> new SpecialistAssistanceBundle(mapper, Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8)),
            "sha256:" + "0".repeat(64))).isInstanceOf(IllegalStateException.class);
    }

    @Test void exactCatalogueAndExistingScopesAreRequiredWithoutSubstitution() throws Exception {
        var action = handler("[\"specialist:assist-area-setup@1\"]");
        var context = context(Set.of(), null);
        assertThat(action.executeAction(Map.of("capabilityId", "setup", "question", "Help"), context).getErrorCode())
            .isEqualTo("SPECIALIST_SCOPE_DENIED");
        assertThat(action.executeAction(Map.of("capabilityId", "arbitrary-chain", "question", "Help"), context).getErrorCode())
            .isEqualTo("SPECIALIST_REQUEST_INVALID");
        verifyNoInteractions(clients);
        assertThat(action.getActionMetadata().getAccessMode()).isEqualTo(ActionAccessMode.READ);
        assertThat(action.getActionMetadata().getGroundingVectorSpaces()).isEmpty();
    }

    @Test void directChildKeepsOriginalIdentitySourceAndNoConversationBinding() throws Exception {
        SpecialistClient<JsonNode, JsonNode> client = mock(SpecialistClient.class);
        when(clients.bind(SpecialistId.parse("assist-area-setup@1"), JsonNode.class, JsonNode.class)).thenReturn(client);
        var handle = mock(ExecutionHandle.class);
        when(handle.invocationId()).thenReturn("check-1");
        when(client.submit(any(SpecialistInvocation.class))).thenAnswer(call -> {
            SpecialistInvocation<JsonNode> invocation = call.getArgument(0);
            assertThat(invocation.conversationBinding()).isNull();
            assertThat(invocation.trustedExecutionContext().source()).isEqualTo(ExecutionSource.APPLICATION);
            assertThat(invocation.trustedExecutionContext().initiator().principalId()).isEqualTo("opaque-case-principal");
            assertThat(invocation.trustedExecutionContext().callerIdentity().customerId()).isEqualTo("internal-support-owner");
            assertThat(invocation.trustedExecutionContext().grantedScopes()).containsExactly("specialist:assist-area-setup@1");
            return handle;
        });
        when(client.find(eq("check-1"), any())).thenReturn(Optional.of(new SpecialistExecutionSnapshot<>(handle,
            new AIExecutionResult<>("check-1", SpecialistId.parse("assist-area-setup@1"), AIExecutionStatus.SUCCEEDED,
                mapper.readTree("{\"answer\":\"Configure the approved domain record.\"}"), List.of(), Map.of(), null, Instant.now(), Instant.now()))));
        var result = handler("[\"specialist:assist-area-setup@1\"]").executeAction(
            Map.of("capabilityId", "setup", "question", "Domain setup"), context(Set.of("specialist:assist-area-setup@1"), null));
        assertThat(result.isSuccess()).isTrue();
    }

    @Test void humanTakeoverDuringChildExecutionDiscardsTheResult() throws Exception {
        SpecialistClient<JsonNode, JsonNode> client = mock(SpecialistClient.class);
        var human = new AtomicBoolean();
        OrchestrationTurnGuard guard = mock(OrchestrationTurnGuard.class);
        doAnswer(call -> { if (human.get()) throw new OrchestrationControlException("AI_TURN_REVOKED", "Human takeover"); return null; })
            .when(guard).verifyBeforeOrchestration();
        when(clients.bind(SpecialistId.parse("assist-area-setup@1"), JsonNode.class, JsonNode.class)).thenReturn(client);
        var handle = mock(ExecutionHandle.class);
        when(handle.invocationId()).thenReturn("late-check");
        when(client.submit(any(SpecialistInvocation.class))).thenReturn(handle);
        when(client.find(eq("late-check"), any())).thenAnswer(call -> {
            human.set(true);
            return Optional.of(new SpecialistExecutionSnapshot<>(handle,
                new AIExecutionResult<>("late-check", SpecialistId.parse("assist-area-setup@1"), AIExecutionStatus.SUCCEEDED,
                    mapper.createObjectNode().put("answer", "Stale findings"), List.of(), Map.of(), null, Instant.now(), Instant.now())));
        });
        var action = handler("[]");
        var context = context(Set.of(), guard);
        assertThatThrownBy(() -> action.executeAction(Map.of("capabilityId", "setup", "question", "Check setup"), context))
            .isInstanceOf(OrchestrationControlException.class);
    }

    @Test void chainSubmissionMeetsTheOfficialRequestContractAndPreservesAuthority() throws Exception {
        var context = context(Set.of("specialist:assist-area-setup@1"), null);
        var trusted = context.pipelineContext().getOrchestrationRequest().trustedExecutionContext();
        var handle = chainHandle();
        when(chains.submit(any(SpecialistChainExecutionRequest.class))).thenReturn(handle);
        when(chains.findResult("chain-check-1", trusted))
            .thenReturn(Optional.empty(), Optional.of(chainResult(SpecialistChainExecutionStatus.COMPLETED, null)));

        var result = handler("[\"specialist:assist-area-setup@1\"]", true).executeAction(
            Map.of("capabilityId", "setup", "question", "Check setup and policy"), context);

        assertThat(result.isSuccess()).isTrue();
        var request = ArgumentCaptor.forClass(SpecialistChainExecutionRequest.class);
        verify(chains).submit(request.capture());
        assertThat(request.getValue().chainId()).isEqualTo(SpecialistChainId.parse("assist-chain-setup@1"));
        assertThat(request.getValue().input()).isEqualTo(mapper.createObjectNode().put("question", "Check setup and policy"));
        assertThat(request.getValue().trustedExecutionContext()).isSameAs(trusted);
        assertThat(request.getValue().conversationBinding()).isNull();
        assertThat(request.getValue().deadline()).isAfter(Instant.now()).isBefore(Instant.now().plusSeconds(31));
        assertThat(request.getValue().idempotencyKey()).startsWith("specialist-assistance-").hasSizeLessThanOrEqualTo(200);
        assertThatCode(() -> UUID.fromString(request.getValue().idempotencyKey().substring("specialist-assistance-".length())))
            .doesNotThrowAnyException();
        verify(chains, times(2)).findResult("chain-check-1", trusted);
        verify(chains, never()).cancel(anyString(), any());
        verifyNoInteractions(clients);
    }

    @Test void distinctActionInvocationsDoNotReplayAnEarlierDiagnosticResult() throws Exception {
        when(chains.submit(any(SpecialistChainExecutionRequest.class))).thenReturn(chainHandle());
        when(chains.findResult(eq("chain-check-1"), any()))
            .thenReturn(Optional.of(chainResult(SpecialistChainExecutionStatus.COMPLETED, null)));
        var action = handler("[]", true);
        var context = context(Set.of(), null);
        var params = Map.<String, Object>of("capabilityId", "setup", "question", "Check current setup");

        assertThat(action.executeAction(params, context).isSuccess()).isTrue();
        assertThat(action.executeAction(params, context).isSuccess()).isTrue();

        var requests = ArgumentCaptor.forClass(SpecialistChainExecutionRequest.class);
        verify(chains, times(2)).submit(requests.capture());
        assertThat(requests.getAllValues()).extracting(SpecialistChainExecutionRequest::idempotencyKey)
            .doesNotHaveDuplicates();
    }

    @Test void chainScopeDenialDoesNotSubmitOrSubstituteADirectSpecialist() throws Exception {
        var result = handler("[\"specialist:assist-area-setup@1\"]", true).executeAction(
            Map.of("capabilityId", "setup", "question", "Check setup"), context(Set.of(), null));
        assertThat(result.getErrorCode()).isEqualTo("SPECIALIST_SCOPE_DENIED");
        verifyNoInteractions(chains, clients);
    }

    @Test void failedChainRemainsAFailureWithoutSubstitution() throws Exception {
        when(chains.submit(any(SpecialistChainExecutionRequest.class))).thenReturn(chainHandle());
        when(chains.findResult(eq("chain-check-1"), any())).thenReturn(Optional.of(chainResult(
            SpecialistChainExecutionStatus.FAILED, new SpecialistChainFailure("WORKER_FAILED", "Worker did not complete", false))));

        var result = handler("[]", true).executeAction(
            Map.of("capabilityId", "setup", "question", "Check setup"), context(Set.of(), null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("WORKER_FAILED");
        assertThat(result.getMessage()).contains("Do not describe the failed checks as successful");
        verifyNoInteractions(clients);
    }

    @Test void humanTakeoverWhileAwaitingChainCancelsItAndDiscardsItsResult() throws Exception {
        var human = new AtomicBoolean();
        OrchestrationTurnGuard guard = mock(OrchestrationTurnGuard.class);
        doAnswer(call -> {
            if (human.get()) throw new OrchestrationControlException("AI_TURN_REVOKED", "Human takeover");
            return null;
        }).when(guard).verifyBeforeOrchestration();
        when(chains.submit(any(SpecialistChainExecutionRequest.class))).thenReturn(chainHandle());
        when(chains.findResult(eq("chain-check-1"), any())).thenAnswer(call -> {
            human.set(true);
            return Optional.empty();
        });
        var context = context(Set.of(), guard);

        assertThatThrownBy(() -> handler("[]", true).executeAction(
            Map.of("capabilityId", "setup", "question", "Check setup"), context))
            .isInstanceOf(OrchestrationControlException.class);
        verify(chains).cancel("chain-check-1", context.pipelineContext().getOrchestrationRequest().trustedExecutionContext());
        verifyNoInteractions(clients);
    }

    @Test void rawJsonNodeIsRetainedByTheContractButTreatedAsIterableByTheFrameworkSanitizer() throws Exception {
        var output = mapper.readTree("{\"answer\":\"Approved guidance\"}");
        var payload = ActionResultContracts.object(Map.of("findings", output));
        assertThat(payload.toMap().get("findings")).isSameAs(output);

        var raw = ActionResult.builder().success(true).data(payload).build();
        assertThat(sanitizedWire(raw).at("/data/actionResult/data/findings"))
            .isEqualTo(mapper.readTree("[[]]"));
    }

    @Test void structuredDirectFindingsRetainKeysAndNativeTypesThroughContractSanitizationAndHttpSerialization() throws Exception {
        JsonNode output = mapper.readerFor(JsonNode.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue("""
            {"answer":"Approved guidance","verified":true,"changed":false,"count":2,
             "longCount":9007199254740993,"largeCount":123456789012345678901234567890,
             "ratio":0.12345678901234567890123456789,
             "steps":[{"label":"Check DNS","ready":false,"attempts":3},["next",7,true]],
             "emptyObject":{},"emptyArray":[]}
            """);
        var result = directResult(output);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().toMap().get("findings")).isInstanceOf(Map.class);
        var findings = (Map<?, ?>) result.getData().toMap().get("findings");
        assertThat(findings.get("answer")).isEqualTo("Approved guidance");
        assertThat(findings.get("verified")).isEqualTo(true);
        assertThat(findings.get("changed")).isEqualTo(false);
        assertThat(findings.get("count")).isEqualTo(2);
        assertThat(findings.get("longCount")).isEqualTo(9007199254740993L);
        assertThat(findings.get("largeCount")).isEqualTo(new BigInteger("123456789012345678901234567890"));
        assertThat(findings.get("ratio")).isEqualTo(new BigDecimal("0.12345678901234567890123456789"));
        assertThat(sanitizedWire(result).at("/data/actionResult/data/findings")).isEqualTo(output);
    }

    @Test void directFindingsAlsoPreserveArrayAndScalarJsonShapes() throws Exception {
        for (String json : List.of("[1,{\"ready\":false},[\"next\",true]]", "\"Guidance\"", "false", "42", "[]", "{}")) {
            JsonNode output = mapper.readTree(json);
            assertThat(sanitizedWire(directResult(output)).at("/data/actionResult/data/findings"))
                .as("structured output %s", json).isEqualTo(output);
        }
    }

    @Test void directFindingsPreserveExplicitNullAtTheActionContractBoundary() throws Exception {
        var result = directResult(mapper.readTree("{\"answer\":\"Guidance\",\"missing\":null,\"values\":[null,false]}"));
        assertThat(result.getData().toMap().get("findings")).isInstanceOf(Map.class);
        var findings = (Map<?, ?>) result.getData().toMap().get("findings");
        assertThat(findings.containsKey("missing")).isTrue();
        assertThat(findings.get("missing")).isNull();
        assertThat(findings.get("values")).isEqualTo(Arrays.asList(null, false));
    }

    @Test void officialChainProjectionRecordsKeepStructuredFactsEvidenceAndLineage() throws Exception {
        var projections = List.of(
            new SpecialistChainResultView("result-setup", "assist-area-setup@1", "worker-setup", "Check setup",
                Map.of("ready", "false", "attempts", "3"), List.of("guide-setup"), "b".repeat(64), Instant.parse("2026-10-30T19:00:00Z")),
            new SpecialistChainResultView("result-policy", "assist-area-policy@1", "worker-policy", "Check policy",
                Map.of("status", "CURRENT"), List.of("guide-policy"), "c".repeat(64), Instant.parse("2026-10-30T19:00:01Z")));
        var completed = new SpecialistChainExecutionResult("chain-check-1", SpecialistChainId.parse("assist-chain-setup@1"),
            "a".repeat(64), SpecialistChainExecutionStatus.COMPLETED, "Checks completed", null, projections, List.of(),
            null, 0, null, false, true, Instant.now(), Instant.now());
        when(chains.submit(any(SpecialistChainExecutionRequest.class))).thenReturn(chainHandle());
        when(chains.findResult(eq("chain-check-1"), any())).thenReturn(Optional.of(completed));

        var result = handler("[]", true).executeAction(
            Map.of("capabilityId", "setup", "question", "Check setup and policy"), context(Set.of(), null));

        assertThat(result.getData().toMap().get("findings")).isEqualTo(projections);
        var wire = sanitizedWire(result).at("/data/actionResult/data/findings");
        assertThat(wire).hasSize(2);
        assertThat(wire.at("/0/summary").asText()).isEqualTo("Check setup");
        assertThat(wire.at("/0/facts")).isEqualTo(mapper.valueToTree(Map.of("ready", "false", "attempts", "3")));
        assertThat(wire.at("/0/evidenceReferenceIds")).isEqualTo(mapper.valueToTree(List.of("guide-setup")));
        assertThat(wire.at("/0/resultId").asText()).isEqualTo("result-setup");
        assertThat(wire.at("/0/workerInvocationId").asText()).isEqualTo("worker-setup");
        assertThat(wire.at("/1/facts/status").asText()).isEqualTo("CURRENT");
        assertThat(wire.at("/1/resultHash").asText()).isEqualTo("c".repeat(64));
        assertThat(wire.at("/1/completedAt").asText()).isEqualTo("2026-10-30T19:00:01Z");
    }

    private ActionResult directResult(JsonNode output) throws Exception {
        SpecialistClient<JsonNode, JsonNode> client = mock(SpecialistClient.class);
        when(clients.bind(SpecialistId.parse("assist-area-setup@1"), JsonNode.class, JsonNode.class)).thenReturn(client);
        var handle = mock(ExecutionHandle.class);
        when(handle.invocationId()).thenReturn("structured-check");
        when(client.submit(any(SpecialistInvocation.class))).thenReturn(handle);
        var completed = new AIExecutionResult<>("structured-check", SpecialistId.parse("assist-area-setup@1"),
            AIExecutionStatus.SUCCEEDED, output, List.of(), Map.of(), null, Instant.now(), Instant.now());
        when(client.find(eq("structured-check"), any())).thenReturn(Optional.of(new SpecialistExecutionSnapshot<>(handle, completed)));
        return handler("[]").executeAction(Map.of("capabilityId", "setup", "question", "Check setup"), context(Set.of(), null));
    }

    private JsonNode sanitizedWire(ActionResult result) throws Exception {
        var properties = new ResponseSanitizationProperties();
        properties.setEnabled(true);
        var sanitizer = new ResponseSanitizer(beans.getBeanProvider(PIIDetectionService.class), properties);
        var response = sanitizer.sanitize(OrchestrationResult.builder().success(true).data(Map.of("actionResult", result)).build(), "test-session");
        var httpMapper = tools.jackson.databind.json.JsonMapper.builder().build();
        return mapper.readerFor(JsonNode.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .readValue(httpMapper.writeValueAsBytes(response));
    }

    private SpecialistChainExecutionHandle chainHandle() {
        return new SpecialistChainExecutionHandle("chain-check-1", SpecialistChainId.parse("assist-chain-setup@1"),
            SpecialistChainExecutionStatus.RUNNING, false, true, null, Instant.now(), Instant.now().plusSeconds(30));
    }

    private SpecialistChainExecutionResult chainResult(SpecialistChainExecutionStatus status, SpecialistChainFailure failure) {
        return new SpecialistChainExecutionResult("chain-check-1", SpecialistChainId.parse("assist-chain-setup@1"),
            "a".repeat(64), status, failure == null ? "Checks completed" : null, null, List.of(), List.of(), null, 0,
            failure, false, true, Instant.now(), Instant.now());
    }
}
