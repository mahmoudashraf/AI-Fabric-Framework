package com.ai.fabric.runtime.review;

import ai.fabric.execution.action.ActionOutcomeProjectorRegistry;
import ai.fabric.execution.specialist.SpecialistDefinition;
import ai.fabric.execution.specialist.SpecialistDefinitionValidator;
import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.intent.action.ActionPayload;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewProposalConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(ReviewProposalConfiguration.class);

    @Test
    void disabledByDefaultAndWhenReviewIsUnavailable() {
        context.run(application -> assertThat(application).doesNotHaveBean(SpecialistDefinition.class));
        context.withPropertyValues("app.human-review.proposals.enabled=true")
            .run(application -> assertThat(application).doesNotHaveBean(SpecialistDefinition.class));
    }

    @Test
    void registersARealBoundedSpecialistAndSafeProjectorOnlyWhenExplicitlyEnabled() {
        context.withPropertyValues("ai.execution.reviews.enabled=true",
                "app.human-review.proposals.enabled=true",
                "app.human-review.proposals.allowed-actions=registered_write")
            .run(application -> {
                assertThat(application).hasNotFailed().hasSingleBean(SpecialistDefinition.class);
                var definition = application.getBean(SpecialistDefinition.class);
                AIActionRegistry actions = mock(AIActionRegistry.class);
                var metadata = AIActionMetaData.builder().name("registered_write")
                    .accessMode(ActionAccessMode.WRITE_ONLY).confirmationRequired(true).build();
                when(actions.getAllMetadata()).thenReturn(List.of(metadata));
                new SpecialistDefinitionValidator(actions, Set.of("executor"), Set.of()).validate(definition);
                assertThat(definition.executionProfile().writeEnabled()).isTrue();
                var projector = application.getBean(ActionOutcomeProjectorRegistry.class).require("registered_write");
                var outcome = projector.project(ActionResult.builder().success(true)
                    .data(ActionPayload.object(Map.of("privateCredential", "must-never-escape", "email", "private@example.invalid"))).build());
                assertThat(outcome.data()).containsOnly(entry("completed", true));
                assertThat(outcome.message()).isEqualTo("The application action completed.");
                assertThatThrownBy(() -> application.getBean(ActionOutcomeProjectorRegistry.class).require("not_allowed"))
                    .hasMessageContaining("safe action outcome projector");
            });
    }

    @Test
    void refusesEnabledProposalSurfaceWithoutExplicitActions() {
        context.withPropertyValues("ai.execution.reviews.enabled=true", "app.human-review.proposals.enabled=true")
            .run(application -> assertThat(application).hasFailed());
    }

    @Test
    void targetLookupAddsOnlyItsReadCapabilityWithoutRequestingVectorRetrieval() {
        context.withPropertyValues("ai.execution.reviews.enabled=true",
                "app.human-review.proposals.enabled=true",
                "app.human-review.proposals.allowed-actions=registered_write",
                "app.human-review.proposals.target-lookup.action=get_record",
                "app.human-review.proposals.target-lookup.parameter=reference",
                "app.human-review.proposals.target-lookup.vector-space=records")
            .run(application -> {
                assertThat(application).hasNotFailed();
                var requested = application.getBean(SpecialistDefinition.class).executionProfile().requestedCapabilities();
                assertThat(requested.requestableReadActions()).containsExactly("get_record");
                assertThat(requested.proposableWriteActions()).containsExactly("registered_write");
                assertThat(requested.retrievalEnabled()).isFalse();
                assertThat(requested.requestedVectorSpaces()).isEmpty();
                AIActionRegistry actions = mock(AIActionRegistry.class);
                when(actions.getAllMetadata()).thenReturn(List.of(
                    AIActionMetaData.builder().name("registered_write").accessMode(ActionAccessMode.WRITE_ONLY)
                        .confirmationRequired(true).build(),
                    AIActionMetaData.builder().name("get_record").accessMode(ActionAccessMode.READ)
                        .groundingEligible(true).groundingVectorSpaces(List.of("records")).build()));
                // Target projection labels are not a request to query the vector provider.
                new SpecialistDefinitionValidator(actions, Set.of("executor"), Set.of("document"))
                    .validate(application.getBean(SpecialistDefinition.class));
            });
    }
}
