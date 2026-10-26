package com.ai.fabric.runtime.review;

import ai.fabric.execution.context.*;
import ai.fabric.execution.gateway.SpecialistCapabilityResolver;
import ai.fabric.execution.gateway.SpecialistCapabilityResolutionException;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.intent.action.*;
import ai.fabric.intent.action.invocation.*;
import ai.fabric.intent.orchestration.capability.EffectiveCapabilityProfile;
import ai.fabric.intent.orchestration.pipeline.steps.OrchestrationPolicyResolutionStep;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewProposalTargetResolverTest {
    private final AIActionRegistry actions = mock(AIActionRegistry.class);
    private final SpecialistRegistry specialists = mock(SpecialistRegistry.class);
    private final OrchestrationPolicyResolutionStep policies = mock(OrchestrationPolicyResolutionStep.class);
    private final SpecialistCapabilityResolver capabilities = mock(SpecialistCapabilityResolver.class);
    private final GovernedActionInvocationService invocations = mock(GovernedActionInvocationService.class);
    private final EffectiveCapabilityProfile effective = mock(EffectiveCapabilityProfile.class);
    private final ReviewProposalProperties properties = new ReviewProposalProperties(true, Set.of("registered_write"),
        new ReviewProposalProperties.TargetLookup("get_record", "reference", "records"));
    private final TrustedExecutionContext source = new TrustedExecutionContext(
        new ExecutionPrincipal("source-actor", ExecutionPrincipalType.END_USER),
        new ExecutionSubjectRef("runtime-subject", "source-actor"), ExecutionSource.INTERACTIVE,
        "tenant-one", "deployment-one", Set.of("action:get_record"), null, Instant.now());

    private ReviewProposalTargetResolver resolver(ActionAccessMode accessMode) {
        when(actions.findMetadata("get_record")).thenReturn(java.util.Optional.of(
            AIActionMetaData.builder().name("get_record").accessMode(accessMode).build()));
        doReturn(new ReviewProposalConfiguration().applicationActionReviewSpecialist(properties))
            .when(specialists).require(ReviewProposalConfiguration.SPECIALIST_ID);
        when(policies.process(any())).thenAnswer(call -> call.getArgument(0));
        when(capabilities.resolve(any(), any(), same(source))).thenReturn(effective);
        return new ReviewProposalTargetResolver(properties, actions, specialists, policies, capabilities, invocations);
    }

    @Test
    void onlyARegisteredAuthorizedReadCanSupplyTheCanonicalTarget() {
        var resolver = resolver(ActionAccessMode.READ);
        when(invocations.invoke(any())).thenReturn(new GovernedActionInvocationOutcome(
            GovernedActionInvocationStatus.EXECUTED,
            ActionResult.builder().success(true).data(ActionPayload.object(Map.of("secret", "never-pass-raw-data")))
                .pinnedTargets(List.of(new ActionTargetRef("canonical-record", "records", "Safe record facts.",
                    Map.of("lookupValue", "canonical-record")))).build(), null));
        var attachments = resolver.resolve("submitted-lookup-candidate", source);
        assertThat(attachments).hasSize(1);
        assertThat(attachments.getFirst().getId()).isEqualTo("canonical-record");
        assertThat(attachments.getFirst().getMetadata()).containsEntry("lookupValue", "canonical-record")
            .doesNotContainKey("secret");
        ArgumentCaptor<GovernedActionInvocation> invocation = ArgumentCaptor.forClass(GovernedActionInvocation.class);
        verify(invocations).invoke(invocation.capture());
        assertThat(invocation.getValue().actionName()).isEqualTo("get_record");
        assertThat(invocation.getValue().parameters()).containsOnly(entry("reference", "submitted-lookup-candidate"));
        assertThat(invocation.getValue().trustedExecutionContext()).isSameAs(source);
        assertThat(invocation.getValue().effectiveCapabilityProfile()).isSameAs(effective);
    }

    @Test
    void neverUsesAWriteActionOrAnUnapprovedCallerForLookup() {
        assertThatThrownBy(() -> resolver(ActionAccessMode.WRITE_ONLY).resolve("candidate", source))
            .hasMessageContaining("READ-only");
        verifyNoInteractions(invocations);
        var resolver = resolver(ActionAccessMode.READ);
        when(capabilities.resolve(any(), any(), any())).thenThrow(
            new SpecialistCapabilityResolutionException("ACTION_AUTHORITY_INTERSECTION_FAILED", "Denied."));
        assertThatThrownBy(() -> resolver.resolve("candidate", source)).hasMessageContaining("403 FORBIDDEN");
        verifyNoInteractions(invocations);
    }

    @Test
    void zeroOrAmbiguousResultsNeverSelectTheFirstRecord() {
        var resolver = resolver(ActionAccessMode.READ);
        var target = new ActionTargetRef("record", "records", "Safe facts.", Map.of());
        for (var targets : List.of(List.<ActionTargetRef>of(), List.of(target, target))) {
            when(invocations.invoke(any())).thenReturn(new GovernedActionInvocationOutcome(
                GovernedActionInvocationStatus.EXECUTED, ActionResult.builder().success(true).pinnedTargets(targets).build(), null));
            assertThatThrownBy(() -> resolver.resolve("candidate", source)).hasMessageContaining("one unambiguous target");
        }
    }

    @Test
    void missingConfiguredReferenceDoesNotTriggerAnInventedOrBroadSearch() {
        assertThatThrownBy(() -> resolver(ActionAccessMode.READ).resolve(null, source))
            .hasMessageContaining("selected target reference");
        verifyNoInteractions(invocations);
    }

    @Test
    void failedInvocationCannotPromoteAnErrorHandlerTargetToTrustedContext() {
        var resolver = resolver(ActionAccessMode.READ);
        for (var status : GovernedActionInvocationStatus.values()) {
            if (status == GovernedActionInvocationStatus.EXECUTED) continue;
            when(invocations.invoke(any())).thenReturn(new GovernedActionInvocationOutcome(status,
                ActionResult.builder().success(true).pinnedTargets(List.of(
                    new ActionTargetRef("record", "records", "Not executed.", Map.of()))).build(), null));
            assertThatThrownBy(() -> resolver.resolve("candidate", source))
                .hasMessageContaining("one unambiguous target");
        }
    }
}
