package com.ai.fabric.budget.openai;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.dto.AIGenerationRequest;
import ai.fabric.dto.AIEmbeddingRequest;
import ai.fabric.llm.structured.StructuredJsonProviderHints;
import ai.fabric.provider.ProviderRequestOverrideSupport;
import com.ai.fabric.budget.ProviderBudgetLedger;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.fabric.budget.openai.ReviewedOpenAiRateCard.*;
import static com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver.OutputCapMode.*;
import static org.junit.jupiter.api.Assertions.*;

class GuardedResolverContractTest {
    private final AtomicInteger ledgerCalls = new AtomicInteger();
    private final ProviderBudgetLedger ledger = new ProviderBudgetLedger() {
        private AssertionError unexpected() { ledgerCalls.incrementAndGet(); return new AssertionError("Ledger IO is forbidden"); }
        public Snapshot approve(Approval value) { throw unexpected(); }
        public ChatReceipt recordChatIngress(String id, Scope scope, java.util.UUID attempt) { throw unexpected(); }
        public ReservationReceipt reserveAttempt(String id, Scope scope, java.util.UUID attempt, Usage usage) { throw unexpected(); }
        public Attempt settle(String id, Scope scope, java.util.UUID attempt, Usage usage, String ref) { throw unexpected(); }
        public Attempt markAmbiguous(String id, Scope scope, java.util.UUID attempt, String reason) { throw unexpected(); }
        public Snapshot stop(String id, Scope scope, String reason) { throw unexpected(); }
        public Snapshot read(String id, Scope scope) { throw unexpected(); }
    };
    private final OpenAiExchangeGuard guard = new OpenAiExchangeGuard(ledger, identity -> { throw new AssertionError("No IO allowed"); });
    private final AtomicInteger constructed = new AtomicInteger();

    @ParameterizedTest @ValueSource(strings = {GPT_54_MINI_MODEL, GPT_54_MINI_SNAPSHOT})
    void miniUsesAnAllGeneratedTokenCapAndOmitsSamplingWithoutMutatingTheRequest(String model) {
        var config = config(); config.getOpenai().setModel(model); config.getOpenai().setTemperature(0.7);
        var request = AIGenerationRequest.builder().maxTokens(400).temperature(0.2)
            .parameters(Map.of("response_format", Map.of("type", "json_object"))).build();
        var options = (OpenAiChatOptions) resolver(config, MAX_COMPLETION_TOKENS).resolveChatOptions("openai", request);
        assertEquals(model, options.getModel());
        assertNull(options.getMaxTokens());
        assertEquals(400, options.getMaxCompletionTokens());
        assertNull(options.getTemperature());
        assertNull(options.getTopP());
        assertEquals("none", options.getReasoningEffort());
        assertEquals("JSON_OBJECT", options.getResponseFormat().getType().name());
        assertEquals(0.2, request.getTemperature());
        assertEquals(0.7, config.getOpenai().getTemperature());
        var nativeCap = (OpenAiChatOptions) resolver(config).resolveChatOptions("openai", request);
        assertNull(nativeCap.getMaxTokens());
        assertEquals(400, nativeCap.getMaxCompletionTokens());
        assertEquals(0, constructed.get()); assertEquals(0, ledgerCalls.get());
    }

    @ParameterizedTest @ValueSource(strings = {"\"temperature\":0.2", "\"top_p\":0.9", "\"logprobs\":true"})
    void miniNativeSamplingOverridesAreDeniedBeforeIo(String option) {
        assertThrows(OpenAiBudgetException.class, () -> guard.plan(request("chat/completions",
            "{\"model\":\"gpt-5.4-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"fixture\"}],"
                + "\"max_completion_tokens\":400,\"service_tier\":\"default\",\"reasoning_effort\":\"none\"," + option + "}")));
        assertEquals(0, ledgerCalls.get());
    }

    @ParameterizedTest @ValueSource(strings = {"\"reasoning_effort\":\"high\",\"max_completion_tokens\":400",
        "\"reasoning_effort\":\"none\",\"max_tokens\":400", "\"reasoning_effort\":\"none\",\"max_completion_tokens\":16385"})
    void miniCannotExpandReasoningOrUseAnUnsupportedCap(String options) {
        assertThrows(OpenAiBudgetException.class, () -> guard.plan(request("chat/completions",
            "{\"model\":\"gpt-5.4-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"fixture\"}],"
                + "\"service_tier\":\"default\"," + options + "}")));
        assertEquals(0, ledgerCalls.get());
    }

    @Test void superclassNativeOptionsAndStructuredJsonAreRetained() {
        var config = config(); config.getOpenai().setTemperature(0.7); config.getOpenai().setMaxTokens(800);
        config.getOpenai().setEmbeddingDimensions(512);
        var resolver = resolver(config);
        String schema = "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}},\"additionalProperties\":false,\"required\":[\"ok\"]}";
        var request = AIGenerationRequest.builder().maxTokens(400).temperature(0.2)
            .parameters(StructuredJsonProviderHints.jsonSchemaResponseParameters(schema)).build();
        var options = (OpenAiChatOptions) resolver.resolveChatOptions("openai", request);
        assertEquals(CHAT_MODEL, options.getModel());
        assertEquals(400, options.getMaxTokens());
        assertEquals(0.2, options.getTemperature());
        assertEquals("JSON_SCHEMA", options.getResponseFormat().getType().name());
        assertEquals(schema, options.getResponseFormat().getJsonSchema());
        assertEquals(1, options.getN());
        assertEquals("default", options.getServiceTier());
        var embeddings = (OpenAiEmbeddingOptions) resolver.resolveEmbeddingOptions("openai", null);
        assertEquals(EMBEDDING_MODEL, embeddings.getModel());
        assertEquals(512, embeddings.getDimensions());
        assertEquals(512, resolver.embeddingDimension("openai"));
        assertEquals(0, constructed.get());
        assertEquals(0, ledgerCalls.get());
    }

    @ParameterizedTest @ValueSource(ints = {1, 400, 1000, 16384})
    void explicitCapModeMovesOnlyTheExistingEffectiveCap(int cap) {
        var config = config(); config.getOpenai().setMaxTokens(800); config.getOpenai().setTemperature(0.7);
        String schema = "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}},\"required\":[\"ok\"],\"additionalProperties\":false}";
        var parameters = StructuredJsonProviderHints.jsonSchemaResponseParameters(schema);
        var request = AIGenerationRequest.builder().purpose("fixture-purpose").maxTokens(cap).temperature(0.2)
            .parameters(parameters).build();
        var preserved = (OpenAiChatOptions) resolver(config).resolveChatOptions("openai", request);
        var selected = (OpenAiChatOptions) resolver(config, MAX_COMPLETION_TOKENS).resolveChatOptions("openai", request);
        assertEquals(cap, preserved.getMaxTokens());
        assertNull(preserved.getMaxCompletionTokens());
        assertNull(selected.getMaxTokens());
        assertEquals(preserved.getMaxTokens(), selected.getMaxCompletionTokens());
        assertEquals(preserved.getModel(), selected.getModel());
        assertEquals(preserved.getTemperature(), selected.getTemperature());
        assertEquals(preserved.getResponseFormat().getType(), selected.getResponseFormat().getType());
        assertEquals(preserved.getResponseFormat().getJsonSchema(), selected.getResponseFormat().getJsonSchema());
        assertEquals(preserved.getOutputSchema(), selected.getOutputSchema());
        assertEquals(preserved.getN(), selected.getN());
        assertEquals(preserved.getServiceTier(), selected.getServiceTier());
        assertEquals("fixture-purpose", request.getPurpose());
        assertEquals(parameters, request.getParameters());
        assertEquals(cap, request.getMaxTokens());
        assertEquals(800, config.getOpenai().getMaxTokens());
        assertEquals(0, constructed.get()); assertEquals(0, ledgerCalls.get());
    }

    @ParameterizedTest @ValueSource(ints = {0, 800, 16384})
    void explicitCapModeUsesTheSameFrameworkConfiguredOrDefaultLimit(int configured) {
        var config = config(); config.getOpenai().setMaxTokens(configured);
        var preserved = (OpenAiChatOptions) resolver(config).resolveChatOptions("openai", null);
        var selected = (OpenAiChatOptions) resolver(config, MAX_COMPLETION_TOKENS).resolveChatOptions("openai", null);
        assertEquals(preserved.getMaxTokens(), selected.getMaxCompletionTokens());
        assertNull(selected.getMaxTokens());
        assertEquals(0, constructed.get()); assertEquals(0, ledgerCalls.get());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 16385})
    void explicitCapModeCannotExpandOrRepairAnUnsupportedRequestedLimit(int cap) {
        var error = assertThrows(OpenAiBudgetException.class, () -> resolver(config(), MAX_COMPLETION_TOKENS)
            .resolveChatOptions("openai", AIGenerationRequest.builder().maxTokens(cap).build()));
        assertEquals(OpenAiBudgetException.Code.UNSUPPORTED_CONFIGURATION, error.code());
        assertEquals(0, constructed.get()); assertEquals(0, ledgerCalls.get());
    }

    @Test void publicConstructorsRequireExplicitModeForAnyTranslation() {
        var config = config();
        OpenAiBudgetScopeResolver scopes = identity -> { throw new AssertionError("Binding IO is forbidden"); };
        var four = new BudgetGuardedSpringAiModelResolver(config, ObservationRegistry.NOOP, ledger, scopes);
        var five = new BudgetGuardedSpringAiModelResolver(config, ObservationRegistry.NOOP, ledger, scopes,
            operation -> OpenAiBudgetInvocation.NONE);
        var six = new BudgetGuardedSpringAiModelResolver(config, ObservationRegistry.NOOP, ledger, scopes,
            operation -> OpenAiBudgetInvocation.NONE, MAX_COMPLETION_TOKENS);
        for (var resolver : new BudgetGuardedSpringAiModelResolver[]{four, five}) {
            var options = (OpenAiChatOptions) resolver.resolveChatOptions("openai", null);
            assertEquals(1000, options.getMaxTokens());
            assertNull(options.getMaxCompletionTokens());
            resolver.destroy();
        }
        var selected = (OpenAiChatOptions) six.resolveChatOptions("openai", null);
        assertNull(selected.getMaxTokens()); assertEquals(1000, selected.getMaxCompletionTokens());
        six.destroy();
        assertEquals(0, ledgerCalls.get());
    }

    @ParameterizedTest @ValueSource(strings = {"azure", "anthropic", "gemini", "cohere", "other", "spring-ai-onnx"})
    void unsupportedResolutionNeverFallsBackToSuperclass(String provider) {
        var resolver = resolver(config());
        assertThrows(OpenAiBudgetException.class, () -> resolver.resolveChatModel(provider, null));
        assertThrows(OpenAiBudgetException.class, () -> resolver.resolveEmbeddingModel(provider, null));
        assertThrows(OpenAiBudgetException.class, () -> resolver.resolveChatOptions(provider, null));
        assertThrows(OpenAiBudgetException.class, () -> resolver.resolveEmbeddingOptions(provider, null));
        assertFalse(resolver.isChatAvailable(provider));
        assertFalse(resolver.isEmbeddingAvailable(provider));
        assertEquals(0, constructed.get());
    }

    @ParameterizedTest @ValueSource(strings = {"azure", "anthropic", "gemini", "cohere"})
    void anyOtherEnabledPaidFamilyRejectsConstruction(String provider) {
        var config = config();
        switch (provider) {
            case "azure" -> config.getAzure().setEnabled(true);
            case "anthropic" -> config.getAnthropic().setEnabled(true);
            case "gemini" -> config.getGemini().setEnabled(true);
            case "cohere" -> config.getCohere().setEnabled(true);
            default -> throw new AssertionError();
        }
        assertThrows(OpenAiBudgetException.class, () -> resolver(config));
        assertEquals(0, constructed.get());
    }

    @Test void unreviewedEndpointsAndModelsFailBeforeNativeClientCreation() {
        var resolver = resolver(config());
        var request = AIGenerationRequest.builder().parameters(Map.of(ProviderRequestOverrideSupport.PARAM_PROVIDER_CONNECTION_OVERRIDE,
            Map.of("baseUrl", "https://example.invalid/v1"))).build();
        assertThrows(OpenAiBudgetException.class, () -> resolver.resolveChatModel("openai", request));
        assertThrows(OpenAiBudgetException.class, () -> resolver.resolveChatOptions("openai", AIGenerationRequest.builder().model("gpt-4o").build()));
        assertThrows(OpenAiBudgetException.class, () -> resolver.resolveEmbeddingOptions("openai", AIEmbeddingRequest.builder().model("text-embedding-3-large").build()));
        assertEquals(0, constructed.get());
    }

    @ParameterizedTest @ValueSource(strings = {"\"n\":2", "\"stream\":true", "\"modalities\":[\"audio\"]",
        "\"web_search_options\":{}", "\"tools\":[{\"type\":\"web_search\"}]", "\"prediction\":{}"})
    void unsupportedPaidSurfaceIsDeniedAtSerializedPayload(String extra) {
        String body = "{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"fixture\"}],"
            + "\"max_completion_tokens\":1000,\"service_tier\":\"default\"," + extra + "}";
        assertThrows(OpenAiBudgetException.class, () -> guard.plan(request("chat/completions", body)));
        assertEquals(0, ledgerCalls.get());
    }

    @Test void noVisionOrResponsesApiOrPriorityTierCanEnterTheBudgetedRoute() {
        for (String body : new String[]{
            "{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.invalid/image\"}}]}],\"max_completion_tokens\":1,\"service_tier\":\"default\"}",
            "{\"model\":\"gpt-4o-mini\",\"messages\":[],\"max_completion_tokens\":1,\"service_tier\":\"priority\"}"}) {
            assertThrows(OpenAiBudgetException.class, () -> guard.plan(request("chat/completions", body)));
        }
        assertThrows(OpenAiBudgetException.class, () -> guard.plan(request("responses", "{}")));
    }

    @Test void stringsBatchesAndExactTokenArraysHaveDocumentedNotEstimatedBounds() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (int count : new int[]{1, 2, 36, 37, 2048}) {
            var body = json.writeValueAsString(Map.of("model", EMBEDDING_MODEL, "input", Collections.nCopies(count, "tiny")));
            assertEquals(embedding(Math.min(8192L * count, 300_000)), guard.plan(request("embeddings", body)).upperBound());
        }
        assertEquals(embedding(8192), guard.plan(request("embeddings", json.writeValueAsString(Map.of("model", EMBEDDING_MODEL,
            "input", Collections.nCopies(8192, 42))))).upperBound());
        assertEquals(embedding(5), guard.plan(request("embeddings", "{\"model\":\"text-embedding-3-small\",\"input\":[[1,2],[3,4,5]]}")).upperBound());
        assertThrows(OpenAiBudgetException.class, () -> guard.plan(request("embeddings", json.writeValueAsString(Map.of("model", EMBEDDING_MODEL,
            "input", Collections.nCopies(8193, 42))))));
        assertThrows(OpenAiBudgetException.class, () -> guard.plan(request("embeddings", json.writeValueAsString(Map.of("model", EMBEDDING_MODEL,
            "input", Collections.nCopies(37, Collections.nCopies(8192, 42)))))));
    }

    @Test void fullInputPlusEffectiveOutputIsReservedWithSeparateRates() throws Exception {
        for (int output : new int[]{1, 1000, 16384}) {
            var plan = guard.plan(request("chat/completions", "{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"tiny\"}],"
                + "\"max_completion_tokens\":" + output + ",\"service_tier\":\"default\"}"));
            assertEquals(chat(128_000, output), plan.upperBound());
            assertEquals(128_000 + output, plan.upperBound().totalTokens());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"\"max_tokens\":1000", "\"max_tokens\":1000,\"max_completion_tokens\":1000", "\"user\":\"fixture\""})
    void legacyMixedOrMissingAllGeneratedTokenLimitIsExplicitlyUnsupported(String limit) {
        var error = assertThrows(OpenAiBudgetException.class, () -> guard.plan(request("chat/completions",
            "{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"fixture\"}],"
                + "\"service_tier\":\"default\"," + limit + "}")));
        assertEquals(OpenAiBudgetException.Code.UNSUPPORTED_OUTPUT_LIMIT_MODE, error.code());
        assertEquals(0, ledgerCalls.get());
    }

    private Request request(String endpoint, String body) {
        return new Request.Builder().url("https://api.openai.com/v1/" + endpoint)
            .post(RequestBody.create(body, MediaType.get("application/json"))).build();
    }

    private AIProviderConfig config() { var config = new AIProviderConfig(); config.getOpenai().setApiKey("fixture-key"); return config; }
    private BudgetGuardedSpringAiModelResolver resolver(AIProviderConfig config) {
        return resolver(config, PRESERVE_FRAMEWORK);
    }

    private BudgetGuardedSpringAiModelResolver resolver(AIProviderConfig config, BudgetGuardedSpringAiModelResolver.OutputCapMode mode) {
        return new BudgetGuardedSpringAiModelResolver(config, ObservationRegistry.NOOP, (key, timeout) -> {
            constructed.incrementAndGet(); throw new AssertionError("Native client construction is forbidden in source-contract tests");
        }, operation -> OpenAiBudgetInvocation.NONE, mode);
    }
}
