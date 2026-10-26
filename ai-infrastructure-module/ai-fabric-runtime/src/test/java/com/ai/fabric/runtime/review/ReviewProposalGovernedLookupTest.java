package com.ai.fabric.runtime.review;

import ai.fabric.execution.context.ExecutionCallerIdentity;
import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.ExecutionSubjectRef;
import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.execution.gateway.SpecialistCapabilityResolver;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.intent.action.AIActionHandler;
import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.AIActionParamSchema;
import ai.fabric.intent.action.AIActionParamType;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.action.ActionPayload;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.intent.action.ActionTargetRef;
import ai.fabric.intent.action.invocation.DefaultGovernedActionInvocationService;
import ai.fabric.intent.orchestration.capability.EffectiveCapabilityProfile;
import ai.fabric.intent.orchestration.pipeline.steps.OrchestrationPolicyResolutionStep;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewProposalGovernedLookupTest {
    private static final String READ = "lookup_record";
    private static final String CANDIDATE = "submitted-reference";
    private static final Instant EXPIRES_AT = Instant.parse("2030-01-01T00:00:00Z");
    private final TrustedExecutionContext source = new TrustedExecutionContext(
        new ExecutionPrincipal("verified-caller", ExecutionPrincipalType.END_USER),
        new ExecutionSubjectRef("runtime-subject", "verified-caller"), ExecutionSource.INTERACTIVE,
        "tenant-one", "deployment-one", Set.of("action:" + READ), "lookup-correlation",
        Instant.parse("2026-10-26T00:00:00Z")
    ).withCallerIdentity(new ExecutionCallerIdentity(
        "END_USER", "PRIVATE_ASSERTION", "CUSTOMER_BACKEND", "verified-session", "customer-one",
        "https://issuer.loomai.test", List.of("runtime-one"), EXPIRES_AT
    ));

    @Test
    void realGovernedReadPreservesCallerAndReturnsCanonicalTargetWithoutVectorSpace() {
        var handler = new RecordingReadHandler(true);
        var attachments = resolver(handler, true).resolve(CANDIDATE, source);

        assertThat(handler.authorizations).isEqualTo(1);
        assertThat(handler.executions).isEqualTo(1);
        assertVerifiedCaller(handler.authorizationContext);
        assertVerifiedCaller(handler.executionContext);
        assertThat(handler.executionParameters).containsExactlyEntriesOf(Map.of("reference", CANDIDATE));
        assertThat(handler.executionContext.actionParams()).containsExactlyEntriesOf(handler.executionParameters);
        assertThat(handler.executionContext.orchestrationContext().getAttachmentsNormalized()).isNullOrEmpty();

        assertThat(attachments).hasSize(1);
        var target = attachments.getFirst();
        assertThat(target.getId()).isEqualTo("canonical-record").isNotEqualTo(CANDIDATE);
        assertThat(target.getVectorSpace()).isNull();
        assertThat(target.getContentText()).isEqualTo("Safe projected record facts.");
        assertThat(target.getSource()).isEqualTo(READ);
        assertThat(target.getMetadata()).containsOnly(
            Map.entry("lookupValue", "canonical-record"), Map.entry("id", "canonical-record"));
    }

    @Test
    void realGovernedReadCannotPromoteTargetWhenResourceAuthorizationDenies() {
        var handler = new RecordingReadHandler(false);

        assertThatThrownBy(() -> resolver(handler, true).resolve(CANDIDATE, source))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("one unambiguous target");

        assertThat(handler.authorizations).isEqualTo(1);
        assertVerifiedCaller(handler.authorizationContext);
        assertThat(handler.executions).isZero();
    }

    @Test
    void realGovernedServiceRejectsLookupOutsideExecutableReadCapabilities() {
        var handler = new RecordingReadHandler(true);

        assertThatThrownBy(() -> resolver(handler, false).resolve(CANDIDATE, source))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("one unambiguous target");

        assertThat(handler.authorizations).isZero();
        assertThat(handler.executions).isZero();
    }

    private ReviewProposalTargetResolver resolver(RecordingReadHandler handler, boolean executable) {
        var properties = new ReviewProposalProperties(true, Set.of("registered_write"),
            new ReviewProposalProperties.TargetLookup(READ, "reference", null));
        var registry = mock(AIActionRegistry.class);
        when(registry.findMetadata(READ)).thenReturn(Optional.of(handler.getActionMetadata()));
        when(registry.findHandler(READ)).thenReturn(Optional.of(handler));
        var specialists = mock(SpecialistRegistry.class);
        doReturn(new ReviewProposalConfiguration().applicationActionReviewSpecialist(properties))
            .when(specialists).require(ReviewProposalConfiguration.SPECIALIST_ID);
        // Isolate policy setup; execution, parameter contracts and handler authorization are real.
        var policies = mock(OrchestrationPolicyResolutionStep.class);
        when(policies.process(any())).thenAnswer(call -> call.getArgument(0));
        var capabilities = mock(SpecialistCapabilityResolver.class);
        var effective = new EffectiveCapabilityProfile("review-lookup-test", "executor", false,
            Set.of(), Set.of(READ), executable ? Set.of(READ) : Set.of(), Set.of(), null, null,
            "test-effective-profile");
        when(capabilities.resolve(any(), any(), same(source))).thenReturn(effective);
        return new ReviewProposalTargetResolver(properties, registry, specialists, policies, capabilities,
            new DefaultGovernedActionInvocationService(registry));
    }

    private void assertVerifiedCaller(ActionContext context) {
        assertThat(context).isNotNull();
        assertThat(context.pipelineContext().getOrchestrationRequest().trustedExecutionContext()).isSameAs(source);
        assertThat(context.isAuthenticated()).isTrue();
        var auth = context.authContext();
        assertThat(auth.getSubjectId()).isEqualTo("verified-caller");
        assertThat(auth.getSubjectType()).isEqualTo("END_USER");
        assertThat(auth.getTenantId()).isEqualTo("tenant-one");
        assertThat(auth.getDeploymentId()).isEqualTo("deployment-one");
        assertThat(auth.getCustomerId()).isEqualTo("customer-one");
        assertThat(auth.getSessionId()).isEqualTo("verified-session");
        assertThat(auth.getAuthMode()).isEqualTo("PRIVATE_ASSERTION");
        assertThat(auth.getCallerType()).isEqualTo("CUSTOMER_BACKEND");
        assertThat(auth.getIssuer()).isEqualTo("https://issuer.loomai.test");
        assertThat(auth.getAudiences()).containsExactly("runtime-one");
        assertThat(auth.getGrantedScopes()).containsExactly("action:" + READ);
        assertThat(auth.getExpiresAt()).isEqualTo(EXPIRES_AT.toString());
    }

    private static final class RecordingReadHandler implements AIActionHandler {
        private final boolean authorized;
        private int authorizations;
        private int executions;
        private ActionContext authorizationContext;
        private ActionContext executionContext;
        private Map<String, Object> executionParameters;
        private final AIActionMetaData metadata = AIActionMetaData.builder().name(READ)
            .accessMode(ActionAccessMode.READ).anonymousAllowed(false).confirmationRequired(false)
            .groundingEligible(false).groundingVectorSpaces(List.of()).requiredParameters(Set.of("reference"))
            .parameterSchemas(Map.of("reference", AIActionParamSchema.builder().name("reference")
                .type(AIActionParamType.STRING).required(true).visibility("INTERNAL").askUser(false)
                .evidenceBound(true).evidenceKeys(List.of("lookupValue")).build())).build();

        private RecordingReadHandler(boolean authorized) { this.authorized = authorized; }

        public AIActionMetaData getActionMetadata() { return metadata; }
        public boolean requiresConfirmation() { return false; }
        public String getConfirmationMessage(Map<String, Object> params, ActionContext context) { return "Read only."; }

        public boolean validateActionAllowed(ActionContext context) {
            authorizations++;
            authorizationContext = context;
            var auth = context.authContext();
            return authorized && "tenant-one".equals(auth.getTenantId())
                && "deployment-one".equals(auth.getDeploymentId())
                && CANDIDATE.equals(context.actionParams().get("reference"));
        }

        public ActionResult executeAction(Map<String, Object> params, ActionContext context) {
            executions++;
            executionContext = context;
            executionParameters = params;
            return ActionResult.builder().success(true)
                .data(ActionPayload.object(Map.of("privateRawValue", "must-not-be-promoted")))
                .pinnedTargets(List.of(new ActionTargetRef("canonical-record", null,
                    "Safe projected record facts.", Map.of("lookupValue", "canonical-record"))))
                .build();
        }
    }
}
