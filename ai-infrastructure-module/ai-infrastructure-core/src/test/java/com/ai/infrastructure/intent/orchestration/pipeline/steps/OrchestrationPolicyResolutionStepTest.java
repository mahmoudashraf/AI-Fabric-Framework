package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationPolicy;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrchestrationPolicyResolutionStep")
class OrchestrationPolicyResolutionStepTest {

    @Nested
    @DisplayName("metadata")
    class Metadata {

        @Test
            @DisplayName("Should have correct step name")
            void shouldHaveCorrectStepName() {
            OrchestrationPolicyResolutionStep step = new OrchestrationPolicyResolutionStep(new OrchestrationProperties());
                assertThat(step.getStepName()).isEqualTo("OrchestrationPolicyResolution");
            }

        @Test
            @DisplayName("Should have correct order (after access control)")
            void shouldHaveCorrectOrder() {
            OrchestrationPolicyResolutionStep step = new OrchestrationPolicyResolutionStep(new OrchestrationProperties());
                assertThat(step.getOrder()).isEqualTo(22);
            }
    }

    @Nested
    @DisplayName("process()")
    class ProcessMethod {

        @Test
        @DisplayName("Should attach resolved policy to context and metadata (position routing)")
        void shouldAttachResolvedPolicyToContextAndMetadataPositionRouting() {
            OrchestrationProperties orchestrationProperties = new OrchestrationProperties();
            orchestrationProperties.setProfile(OrchestrationProfile.DEFAULT);

            OrchestrationProperties.ModeOverrides navigator = new OrchestrationProperties.ModeOverrides();
            navigator.setInformationMode(OrchestrationProperties.InformationMode.DETERMINISTIC_RAG_GENERATE);
            orchestrationProperties.getModes().put("navigator", navigator);
            orchestrationProperties.getPositionRouting().put("landing", "navigator");

            OrchestrationPolicyResolutionStep step = new OrchestrationPolicyResolutionStep(orchestrationProperties);

            OrchestrationContext orch = OrchestrationContext.forUser("user-123")
                .toBuilder()
                .position("landing")
                .mode("chatty")
                .build();

            PipelineContext input = PipelineContext.from("hello", orch);
            PipelineContext output = step.process(input);

            OrchestrationPolicy policy = output.getOrchestrationPolicy();
            assertThat(policy).isNotNull();
            assertThat(policy.profile()).isEqualTo(OrchestrationProfile.DEFAULT);
            assertThat(policy.mode()).isEqualTo("navigator");
            assertThat(policy.position()).isEqualTo("landing");
            assertThat(policy.informationMode()).isEqualTo(OrchestrationProperties.InformationMode.DETERMINISTIC_RAG_GENERATE);

            assertThat(output.getOrchestrationContext()).isNotNull();
            assertThat(output.getOrchestrationContext().getOrchestrationPolicy()).isEqualTo(policy);

            Object meta = output.getMetadataView().get("orchestrationPolicy");
            assertThat(meta).isInstanceOf(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> policyMeta = (Map<String, Object>) meta;
            assertThat(policyMeta).containsEntry("profile", "DEFAULT");
            assertThat(policyMeta).containsEntry("mode", "navigator");
            assertThat(policyMeta).containsEntry("position", "landing");
            assertThat(policyMeta).containsEntry("informationModeEffective", "DETERMINISTIC_RAG_GENERATE");
            assertThat(policyMeta).containsEntry("modeSource", "POSITION");
        }

        @Test
        @DisplayName("Should use requested mode when allowlisted and no position routing")
        void shouldUseRequestedModeWhenAllowlistedAndNoPositionRouting() {
            OrchestrationProperties orchestrationProperties = new OrchestrationProperties();
            orchestrationProperties.setProfile(OrchestrationProfile.DEFAULT);

            OrchestrationProperties.ModeOverrides navigator = new OrchestrationProperties.ModeOverrides();
            orchestrationProperties.getModes().put("navigator", navigator);

            OrchestrationPolicyResolutionStep step = new OrchestrationPolicyResolutionStep(orchestrationProperties);

            OrchestrationContext orch = OrchestrationContext.forUser("user-123")
                .toBuilder()
                .mode("navigator")
                .build();

            PipelineContext output = step.process(PipelineContext.from("hello", orch));
            assertThat(output.getOrchestrationPolicy().mode()).isEqualTo("navigator");

            Object meta = output.getMetadataView().get("orchestrationPolicy");
            @SuppressWarnings("unchecked")
            Map<String, Object> policyMeta = (Map<String, Object>) meta;
            assertThat(policyMeta).containsEntry("modeSource", "REQUEST_MODE");
        }

        @Test
        @DisplayName("Should fall back to profile defaults when no mode is resolved")
        void shouldFallBackToProfileDefaultsWhenNoModeIsResolved() {
            OrchestrationProperties orchestrationProperties = new OrchestrationProperties();
            orchestrationProperties.setProfile(OrchestrationProfile.DEMO_CATALOG);

            OrchestrationPolicyResolutionStep step = new OrchestrationPolicyResolutionStep(orchestrationProperties);

            PipelineContext output = step.process(PipelineContext.from("hello", OrchestrationContext.forUser("user-123")));
            OrchestrationPolicy policy = output.getOrchestrationPolicy();

            assertThat(policy.profile()).isEqualTo(OrchestrationProfile.DEMO_CATALOG);
            assertThat(policy.mode()).isNull();
            assertThat(policy.informationMode()).isEqualTo(OrchestrationProperties.InformationMode.DETERMINISTIC_RAG_GENERATE);
        }

        @Test
        @DisplayName("Should apply explicit overrides over profile and mode defaults")
        void shouldApplyExplicitOverridesOverProfileAndModeDefaults() {
            OrchestrationProperties orchestrationProperties = new OrchestrationProperties();
            orchestrationProperties.setProfile(OrchestrationProfile.DEMO_CATALOG);
            orchestrationProperties.setInformationMode(OrchestrationProperties.InformationMode.LLM_DRIVEN);

            OrchestrationPolicyResolutionStep step = new OrchestrationPolicyResolutionStep(orchestrationProperties);
            PipelineContext output = step.process(PipelineContext.from("hello", OrchestrationContext.forUser("user-123")));

            assertThat(output.getOrchestrationPolicy().informationMode()).isEqualTo(OrchestrationProperties.InformationMode.LLM_DRIVEN);
        }

        @Test
        @DisplayName("Should skip when pipeline already terminated")
        void shouldSkipWhenPipelineAlreadyTerminated() {
            OrchestrationPolicyResolutionStep step = new OrchestrationPolicyResolutionStep(new OrchestrationProperties());

            PipelineContext terminated = PipelineContext.from("hello", OrchestrationContext.forUser("user-123"))
                .terminate(com.ai.infrastructure.intent.orchestration.OrchestrationResult.error("nope"));

            assertThat(step.shouldSkip(terminated)).isTrue();
        }
    }
}
