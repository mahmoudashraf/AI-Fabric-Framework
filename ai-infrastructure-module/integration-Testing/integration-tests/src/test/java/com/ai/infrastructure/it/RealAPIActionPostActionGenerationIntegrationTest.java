package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.it.support.RealAPITestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Import(RealAPIActionPostActionGenerationIntegrationTest.PostActionGenerationTestConfig.class)
@TestPropertySource(properties = {
    "ai.post-action-generation.enabled=true",
    "ai.post-action-generation.max-chars=4000",
    "ai.post-action-generation.max-tokens=80",
    "ai.post-action-generation.temperature=0.0",
    // Make this test deterministic by bypassing progressive extraction and stubbing the extractor output.
    "ai.intent-extraction.progressive.enabled=false"
})
public class RealAPIActionPostActionGenerationIntegrationTest {

    static {
        RealAPITestSupport.ensureProviderConfigured();
        RealAPITestSupport.ensureLLMProviderSet();
    }

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private PostActionGenerationDemoActionHandler actionHandler;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @BeforeEach
    void resetCounter() {
        actionHandler.resetExecutions();
    }

    @Test
    void shouldExecuteActionOnceAndGenerateSummaryFromHandlerFacts() {
        Assumptions.assumeTrue(hasAnyProviderKeyConfigured(), "No LLM provider API key configured; skipping RealAPI scenario.");

        String verificationToken = "POAG-" + Instant.now().toEpochMilli();

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action(PostActionGenerationDemoActionHandler.ACTION_NAME)
            .confidence(1.0d)
            .requiresRetrieval(false)
            .requiresGeneration(true)
            .generationInstructions("Reply with EXACTLY the value of verificationToken from FACTS, and nothing else.")
            .actionParams(Map.of("verificationToken", verificationToken))
            .build();

        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class))).thenReturn(
            MultiIntentResponse.builder()
                .intents(java.util.List.of(intent))
                .compound(false)
                .orchestrationStrategy("DIRECT_ACTION")
                .build()
        );

        OrchestrationResult result = orchestrator.orchestrate(
            "Execute post action generation demo and then summarize.",
            OrchestrationContext.forUser("post-action-realapi-user")
        );

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();

        assertThat(actionHandler.getExecutionCount()).as("Action must run exactly once").isEqualTo(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = result.getData() instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : Map.of();

        assertThat(payload).containsKey("actionResult");
        assertThat(payload).containsKey("summary");
        assertThat(payload).containsKey("postActionGeneration");

        String message = result.getMessage();
        assertThat(message).isNotBlank();
        assertThat(message).contains(verificationToken);

        Object summaryRaw = payload.get("summary");
        assertThat(summaryRaw).isInstanceOf(String.class);
        assertThat(summaryRaw.toString()).contains(verificationToken);

        @SuppressWarnings("unchecked")
        Map<String, Object> generationMeta = payload.get("postActionGeneration") instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : Map.of();
        assertThat(generationMeta).containsEntry("used", true);
        assertThat(generationMeta).containsEntry("action", PostActionGenerationDemoActionHandler.ACTION_NAME);
    }

    private boolean hasAnyProviderKeyConfigured() {
        return StringUtils.hasText(System.getProperty("OPENAI_API_KEY")) || StringUtils.hasText(System.getenv("OPENAI_API_KEY"))
            || StringUtils.hasText(System.getProperty("ANTHROPIC_API_KEY")) || StringUtils.hasText(System.getenv("ANTHROPIC_API_KEY"))
            || StringUtils.hasText(System.getProperty("GEMINI_API_KEY")) || StringUtils.hasText(System.getenv("GEMINI_API_KEY"))
            || StringUtils.hasText(System.getProperty("COHERE_API_KEY")) || StringUtils.hasText(System.getenv("COHERE_API_KEY"))
            || (StringUtils.hasText(System.getProperty("AZURE_API_KEY")) || StringUtils.hasText(System.getenv("AZURE_API_KEY")));
    }

    @TestConfiguration
    static class PostActionGenerationTestConfig {

        @Bean
        PostActionGenerationDemoActionHandler postActionGenerationDemoActionHandler() {
            return new PostActionGenerationDemoActionHandler();
        }
    }

    static final class PostActionGenerationDemoActionHandler implements ActionHandler {

        static final String ACTION_NAME = "post_action_generation_demo";

        private final AtomicInteger executions = new AtomicInteger();

        void resetExecutions() {
            executions.set(0);
        }

        int getExecutionCount() {
            return executions.get();
        }

        @Override
        public AIActionMetaData getActionMetadata() {
            return AIActionMetaData.builder()
                .name(ACTION_NAME)
                .description("Test-only action used to validate post-action generation grounded in handler facts.")
                .category("test")
                .parameters(Map.of(
                    "verificationToken", "String token that must be echoed back by the post-action generation call"
                ))
                .requiredParameters(Set.of("verificationToken"))
                .build();
        }

        @Override
        public boolean validateActionAllowed(String userId) {
            return true;
        }

        @Override
        public String getConfirmationMessage(Map<String, Object> params) {
            return "Confirm post-action generation demo.";
        }

        @Override
        public ActionResult executeAction(Map<String, Object> params, String userId) {
            int count = executions.incrementAndGet();
            Object token = params != null ? params.get("verificationToken") : null;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("verificationToken", token != null ? token.toString() : null);
            data.put("executionCount", count);

            return ActionResult.builder()
                .success(true)
                .message("demo_action_executed")
                .data(data)
                .build();
        }

        @Override
        public Optional<Map<String, Object>> buildPostActionLlmFacts(ActionResult actionResult, OrchestrationContext context) {
            Map<String, Object> data = actionResult != null && actionResult.getData() instanceof Map<?, ?> map
                ? coerceToStringKeyedMap(map)
                : Map.of();
            Object token = data.get("verificationToken");

            return Optional.of(Map.of(
                "verificationToken", token != null ? token.toString() : "",
                "executionCount", executions.get()
            ));
        }

        @Override
        public ActionResult handleError(Exception e, String userId) {
            return ActionResult.builder()
                .success(false)
                .message(e != null ? e.getMessage() : "error")
                .errorCode("TEST_ERROR")
                .build();
        }

        private Map<String, Object> coerceToStringKeyedMap(Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    result.put(k.toString(), v);
                }
            });
            return result;
        }
    }
}
