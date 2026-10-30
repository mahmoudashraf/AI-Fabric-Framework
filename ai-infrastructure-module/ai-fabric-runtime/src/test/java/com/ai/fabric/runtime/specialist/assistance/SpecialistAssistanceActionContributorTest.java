package com.ai.fabric.runtime.specialist.assistance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.fabric.dto.AIAccessSubjectContext;
import ai.fabric.execution.context.*;
import ai.fabric.execution.gateway.*;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.client.*;
import ai.fabric.intent.action.*;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;
import ai.fabric.intent.orchestration.pipeline.PipelineContext;
import ai.fabric.intent.orchestration.request.*;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class SpecialistAssistanceActionContributorTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final SpecialistClientFactory clients = mock(SpecialistClientFactory.class);
    private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();

    private SpecialistAssistanceBundle bundle(String scopes) throws Exception {
        String content = """
            {"contractVersion":"LOOMAI_SPECIALIST_ASSISTANCE_V1",
             "resources":[{"kind":"Specialist","metadata":{"name":"assist-area-setup","version":"1"}}],
             "invocations":[{"id":"setup","name":"Setup","description":"Application setup guidance",
               "targetType":"SPECIALIST","targetRef":"assist-area-setup@1","deadlineSeconds":30,"requiredScopes":%s}]}
            """.formatted(scopes);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SpecialistAssistanceBundle(mapper, Base64.getEncoder().encodeToString(bytes),
            "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    private AIActionHandler handler(String scopes) throws Exception {
        if (!beans.containsBean("clients")) beans.registerSingleton("clients", clients);
        return new SpecialistAssistanceActionContributor(bundle(scopes), beans.getBeanProvider(SpecialistClientFactory.class),
            beans.getBeanProvider(ai.fabric.execution.chain.SpecialistChainGateway.class), mapper).getHandlers().getFirst();
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
}
