package com.ai.fabric.budget.openai;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.dto.AIGenerationRequest;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.JdbcProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.RequestOptions;
import com.openai.core.http.HttpMethod;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.fabric.budget.ProviderBudgetLedger.*;
import static com.ai.fabric.budget.openai.ReviewedOpenAiRateCard.*;
import static com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver.OutputCapMode.*;
import static org.junit.jupiter.api.Assertions.*;

class GuardedNativeTransportTest {
    private static final String KEY = "fictional-not-a-live-api-key";
    private static final String CHAT = """
        {"id":"chat-fixture","object":"chat.completion","created":1,"model":"gpt-4o-mini-2024-07-18",
        "choices":[{"index":0,"message":{"role":"assistant","content":"{}"},"finish_reason":"stop"}],
        "usage":{"prompt_tokens":12,"completion_tokens":3,"total_tokens":15}}
        """;
    private final AtomicInteger sends = new AtomicInteger();
    private final AtomicInteger rootsCreated = new AtomicInteger();
    private final ConcurrentLinkedQueue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> requests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> clientRequestIds = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acceptEncodings = new ConcurrentLinkedQueue<>();
    private final List<AutoCloseable> closeables = new ArrayList<>();
    private ProviderBudgetLedger ledger;
    private OpenAiMeteringStops meteringStops;
    private JdbcDataSource source;
    private Scope scope;
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private String endpoint;
    private OpenAiBudgetInvocation.Capture captures = operation -> OpenAiBudgetInvocation.NONE;
    private boolean astraAmended;

    @BeforeEach void setup() throws Exception {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var schema = getClass().getResourceAsStream("/com/ai/fabric/budget/provider-budget-schema.sql")) {
            assertNotNull(schema);
            RunScript.execute(connection, new StringReader(new String(schema.readAllBytes(), StandardCharsets.UTF_8)));
        }
        ledger = new JdbcProviderBudgetLedger(source);
        meteringStops = new OpenAiMeteringStops(ledger);
        scope = new Scope("fixture-customer", "fixture-deployment", "fixture-target", "staging", credentialIdentity(KEY), REFERENCE);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        server.createContext("/v1", exchange -> {
            sends.incrementAndGet();
            clientRequestIds.add(exchange.getRequestHeaders().getFirst("X-Client-Request-Id"));
            acceptEncodings.add(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Reply reply = replies.poll();
            if (reply == null) reply = new Reply(500, "{}", null, null);
            try {
                if (reply.release() != null) reply.release().await(5, TimeUnit.SECONDS);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("x-request-id", "req_fixture_1");
                reply.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
                if (reply.location() != null) exchange.getResponseHeaders().set("Location", reply.location());
                byte[] bytes = reply.wireBody() == null ? reply.body().getBytes(StandardCharsets.UTF_8) : reply.wireBody();
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach void cleanup() throws Exception {
        for (AutoCloseable closeable : closeables) closeable.close();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> miniReasoningProfiles() {
        return java.util.stream.Stream.of(GPT_54_MINI_MODEL, GPT_54_MINI_SNAPSHOT).flatMap(model ->
            java.util.Arrays.stream(GuardedReasoningEffort.values()).map(effort -> org.junit.jupiter.params.provider.Arguments.of(model, effort)));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("miniReasoningProfiles")
    void miniReservesItsOwnEnvelopeAndSettlesNativeUsageIncludingReasoning(String model, GuardedReasoningEffort effort) throws Exception {
        miniScope();
        approve(600_000);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHAT);
        response.put("model", GPT_54_MINI_SNAPSHOT);
        response.withObject("/usage").put("prompt_tokens", 200_000).put("completion_tokens", 13).put("total_tokens", 200_013);
        response.withObject("/usage/completion_tokens_details").put("reasoning_tokens", 10);
        response.withObject("/usage/prompt_tokens_details").put("cached_tokens", 100_000);
        var release = new CountDownLatch(1);
        replies.add(new Reply(200, json.writeValueAsString(response), null, release));
        var request = AIGenerationRequest.builder().model(model).maxTokens(1000).temperature(0.2)
            .parameters(Map.of("response_format", Map.of("type", "json_object"))).build();
        var chatModel = resolver(MAX_COMPLETION_TOKENS, effort).resolveChatModel("openai", request);
        var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> chatModel.call(new Prompt("fixture")));
        try {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((sends.get() == 0 || requests.isEmpty()) && System.nanoTime() < until) Thread.sleep(5);
            assertEquals(1, sends.get());
            assertFalse(requests.isEmpty(), "The synthetic receiver must capture the request body before inspection.");
            assertEquals(chat(model, 400_000, 1000), ledger.read("approval", scope).accountedUsage());
            var body = json.readTree(requests.element());
            assertEquals(model, body.path("model").textValue());
            assertEquals(1000, body.path("max_completion_tokens").intValue());
            assertFalse(body.has("max_tokens")); assertFalse(body.has("temperature"));
            assertEquals(effort.wireValue(), body.path("reasoning_effort").textValue());
            assertEquals("json_object", body.path("response_format").path("type").textValue());
        } finally { release.countDown(); }
        future.get(3, TimeUnit.SECONDS);
        var snapshot = ledger.read("approval", scope);
        assertEquals(chat(model, 200_000, 13), snapshot.accountedUsage());
        assertEquals(150_058_500L, snapshot.accountedUsage().costNanoUsd());
        assertFalse(snapshot.stopped());
    }

    @Test void miniCannotExpandALegacyAllocation() {
        approve(600_000);
        var request = AIGenerationRequest.builder().model(GPT_54_MINI_MODEL).maxTokens(1000).build();
        var failure = assertThrows(RuntimeException.class,
            () -> resolver().resolveChatModel("openai", request).call(new Prompt("fixture")));
        assertEquals(OpenAiBudgetException.Code.RATE_CARD_MISMATCH,
            OpenAiBudgetTransportException.find(failure).orElseThrow().guardCode().orElseThrow());
        assertEquals(0, sends.get());
        assertEquals(0, ledger.read("approval", scope).providerAttempts());
        assertEquals(REFERENCE, scope.rateCardReference());
    }

    @Test void astraUsesTheOriginalScopeAndAmendmentWithNativeJsonUsageAndMetadataOnly() throws Exception {
        approveAstra(); astraAmended = true;
        var observed = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        var denied = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Denial>();
        captures = operation -> new OpenAiBudgetInvocation(UUID.randomUUID(), denied::add, observed::add);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHAT);
        response.put("model", ASTRA_MODEL);
        response.withObject("/usage/completion_tokens_details").put("reasoning_tokens", 2);
        replies.add(new Reply(200, json.writeValueAsString(response), null, null));
        var request = AIGenerationRequest.builder().model(ASTRA_MODEL).maxTokens(8192).temperature(0.2)
            .parameters(Map.of("response_format", Map.of("type", "json_object"))).build();
        var resolver = resolver(MAX_COMPLETION_TOKENS, GuardedReasoningEffort.MEDIUM);
        assertDoesNotThrow(() -> resolver.resolveChatModel("openai", request).call(new Prompt("private-request-canary")), denied::toString);
        var body = json.readTree(requests.element());
        assertEquals(8192, body.path("max_completion_tokens").intValue());
        assertEquals("medium", body.path("reasoning_effort").asText()); assertFalse(body.has("temperature"));
        assertEquals("json_object", body.path("response_format").path("type").asText());
        var snapshot = ledger.read("approval", scope);
        assertEquals(GPT_54_MINI_REFERENCE, snapshot.approval().scope().rateCardReference());
        assertEquals(chat(ASTRA_MODEL, 12, 3), snapshot.accountedUsage()); assertEquals(1, snapshot.providerAttempts());
        assertEquals(1, sends.get()); assertEquals(1, observed.size());
        var metadata = json.writeValueAsString(observed.element());
        assertTrue(metadata.contains(ASTRA_MODEL)); assertFalse(metadata.contains("private-request-canary"));
        assertEquals(2, observed.element().reasoningTokens());
    }

    @Test void astraWithoutAmendmentOrWithNoneReasoningCannotSendOrReserve() {
        approveAstra();
        var request = AIGenerationRequest.builder().model(ASTRA_MODEL).maxTokens(1000).build();
        assertThrows(RuntimeException.class, () -> resolver(MAX_COMPLETION_TOKENS, GuardedReasoningEffort.MEDIUM)
            .resolveChatModel("openai", request).call(new Prompt("fixture")));
        astraAmended = true;
        assertThrows(OpenAiBudgetException.class, () -> resolver(MAX_COMPLETION_TOKENS, GuardedReasoningEffort.NONE)
            .resolveChatOptions("openai", request));
        assertEquals(0, sends.get()); assertEquals(0, ledger.read("approval", scope).providerAttempts());
    }

    @ParameterizedTest @ValueSource(strings = {"ascii", "unicode", "schema"})
    void astraOversizedUtf8RequestsAreRejectedBeforeAnyHttpOrReservation(String kind) {
        approveAstra(); astraAmended = true;
        String content = kind.equals("unicode") ? "\u20ac".repeat(ASTRA_REQUEST_BYTES / 3 + 1) : "x".repeat(ASTRA_REQUEST_BYTES + 1);
        var builder = AIGenerationRequest.builder().model(ASTRA_MODEL).maxTokens(1000);
        if (kind.equals("schema")) builder.parameters(ai.fabric.llm.structured.StructuredJsonProviderHints.jsonSchemaResponseParameters(
            "{\"type\":\"object\",\"description\":\"" + content + "\",\"properties\":{},\"additionalProperties\":false}"));
        var request = builder.build();
        assertThrows(RuntimeException.class, () -> resolver(MAX_COMPLETION_TOKENS, GuardedReasoningEffort.LOW)
            .resolveChatModel("openai", request).call(new Prompt(kind.equals("schema") ? "fixture" : content)));
        assertEquals(0, sends.get()); assertEquals(0, ledger.read("approval", scope).providerAttempts());
    }

    @Test void astraProviderFailureRetainsOneHoldWithoutRetry() {
        approveAstra(); astraAmended = true;
        replies.add(new Reply(503, "{}", null, null));
        var request = AIGenerationRequest.builder().model(ASTRA_MODEL).maxTokens(1000).build();
        assertThrows(RuntimeException.class, () -> resolver(MAX_COMPLETION_TOKENS, GuardedReasoningEffort.LOW)
            .resolveChatModel("openai", request).call(new Prompt("fixture")));
        var snapshot = ledger.read("approval", scope);
        assertEquals(chat(ASTRA_MODEL, ASTRA_INPUT_TOKENS, 1000), snapshot.accountedUsage());
        assertEquals(1, snapshot.providerAttempts()); assertEquals(1, sends.get()); assertFalse(snapshot.stopped());
    }

    @Test void astraExactByteEnvelopeAndSeparateInputOutputBoundsSettleWithoutAFalseContextConflict() throws Exception {
        approveAstra(); astraAmended = true;
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHAT); response.put("model", ASTRA_MODEL);
        response.withObject("/usage").put("prompt_tokens", ASTRA_INPUT_TOKENS).put("completion_tokens", CHAT_MAX_OUTPUT_TOKENS)
            .put("total_tokens", ASTRA_INPUT_TOKENS + CHAT_MAX_OUTPUT_TOKENS);
        replies.add(new Reply(200, json.writeValueAsString(response), null, null));
        var body = astraBody(json); int overhead = json.writeValueAsBytes(body).length - 1;
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("messages").get(0)).put("content", "x".repeat(ASTRA_REQUEST_BYTES - overhead));
        assertEquals(ASTRA_REQUEST_BYTES, json.writeValueAsBytes(body).length);
        try (var result = rawAstra(json.writeValueAsBytes(body))) { assertEquals(200, result.code()); }
        assertEquals(chat(ASTRA_MODEL, ASTRA_INPUT_TOKENS, CHAT_MAX_OUTPUT_TOKENS), ledger.read("approval", scope).accountedUsage());
        assertEquals(1, sends.get()); assertFalse(ledger.read("approval", scope).stopped());
    }

    @ParameterizedTest @ValueSource(strings = {"bytes", "messages", "tools", "retention", "sampling", "snapshot"})
    void astraUnreviewedInputShapesCannotSendOrReserve(String kind) throws Exception {
        approveAstra(); astraAmended = true;
        var json = new com.fasterxml.jackson.databind.ObjectMapper(); var body = astraBody(json);
        switch (kind) {
            case "bytes" -> ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("messages").get(0)).put("content", "x".repeat(ASTRA_REQUEST_BYTES));
            case "messages" -> { for (int i = 0; i < ASTRA_MAX_MESSAGES; i++) body.withArray("messages").addObject().put("role", "user").put("content", "x"); }
            case "tools" -> body.putArray("tools").addObject().put("type", "function").putObject("function").put("name", "unreviewed");
            case "retention" -> body.put("prompt_cache_retention", "12h");
            case "sampling" -> body.put("temperature", 0.2);
            case "snapshot" -> body.put("model", "gpt-6.2-astra-2026-11-25");
        }
        var denied = assertThrows(OpenAiBudgetTransportException.class, () -> rawAstra(json.writeValueAsBytes(body)));
        if (kind.equals("bytes") || kind.equals("messages"))
            assertEquals(OpenAiBudgetException.Code.SHORT_CONTEXT_INPUT_BOUND_EXCEEDED, denied.guardCode().orElseThrow());
        assertEquals(0, sends.get()); assertEquals(0, ledger.read("approval", scope).providerAttempts());
    }

    private com.fasterxml.jackson.databind.node.ObjectNode astraBody(com.fasterxml.jackson.databind.ObjectMapper json) {
        var body = json.createObjectNode().put("model", ASTRA_MODEL).put("service_tier", "default")
            .put("max_completion_tokens", CHAT_MAX_OUTPUT_TOKENS).put("reasoning_effort", "medium");
        body.putArray("messages").addObject().put("role", "user").put("content", "x"); return body;
    }

    private okhttp3.Response rawAstra(byte[] bytes) throws Exception {
        var client = new OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).callTimeout(Duration.ofSeconds(3))
            .addNetworkInterceptor(new OpenAiExchangeGuard(ledger, identity -> new OpenAiBudgetScopeResolver.Binding("approval", scope,
                ASTRA_REFERENCE, "astra-test"), HttpUrl.get(endpoint), meteringStops, GuardedReasoningEffort.MEDIUM)).build();
        closeables.add(() -> { client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); });
        return client.newCall(new okhttp3.Request.Builder().url(endpoint + "/chat/completions").header("Authorization", "Bearer " + KEY)
            .post(okhttp3.RequestBody.create(bytes, okhttp3.MediaType.get("application/json"))).build()).execute();
    }

    @Test void miniResponseCannotSettleAtAnotherModelsLowerRate() {
        miniScope(); approve(600_000);
        replies.add(new Reply(200, CHAT, null, null));
        var request = AIGenerationRequest.builder().model(GPT_54_MINI_MODEL).maxTokens(1000).build();
        var failure = assertThrows(RuntimeException.class,
            () -> resolver().resolveChatModel("openai", request).call(new Prompt("fixture")));
        assertEquals(OpenAiBudgetException.Code.ATTEMPT_AMBIGUOUS,
            OpenAiBudgetTransportException.find(failure).orElseThrow().guardCode().orElseThrow());
        assertEquals(chat(GPT_54_MINI_MODEL, 400_000, 1000), ledger.read("approval", scope).accountedUsage());
        assertEquals(1, sends.get());
    }

    @Test void miniAllGeneratedTokenBreachStopsTheAllocationAndRetainsItsHold() throws Exception {
        miniScope(); approve(600_000);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHAT);
        response.put("model", GPT_54_MINI_SNAPSHOT);
        response.withObject("/usage").put("completion_tokens", 1001).put("total_tokens", 1013);
        replies.add(new Reply(200, json.writeValueAsString(response), null, null));
        var request = AIGenerationRequest.builder().model(GPT_54_MINI_MODEL).maxTokens(1000).build();
        var resolver = resolver();
        var failure = assertThrows(RuntimeException.class,
            () -> resolver.resolveChatModel("openai", request).call(new Prompt("fixture")));
        assertEquals(OpenAiBudgetException.Code.METERING_BOUND_BREACH,
            OpenAiBudgetTransportException.find(failure).orElseThrow().guardCode().orElseThrow());
        assertTrue(ledger.read("approval", scope).stopped());
        assertEquals(chat(GPT_54_MINI_MODEL, 400_000, 1000), ledger.read("approval", scope).accountedUsage());
        assertThrows(RuntimeException.class, () -> resolver.resolveChatModel("openai", request).call(new Prompt("fixture")));
        assertEquals(1, sends.get());
    }

    private void miniScope() {
        scope = new Scope(scope.customerId(), scope.deploymentId(), scope.targetProfileId(), scope.environment(),
            scope.credentialIdentity(), GPT_54_MINI_REFERENCE);
    }

    @Test void nativeChatSettlesOnceFromJsonAndKeepsStructuredOptions() throws Exception {
        approve(200_000);
        var completions = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        UUID operationId = UUID.randomUUID();
        captures = operation -> new OpenAiBudgetInvocation(operationId, failure -> fail("Unexpected denial"), completions::add);
        replies.add(new Reply(200, CHAT, null, null));
        var resolver = resolver();
        var request = AIGenerationRequest.builder().model(CHAT_MODEL).maxTokens(1000).temperature(0.2)
            .parameters(Map.of("response_format", Map.of("type", "json_object"))).build();
        var options = (OpenAiChatOptions) resolver.resolveChatOptions("openai", request);
        resolver.resolveChatModel("openai", request).call(new Prompt("fixture", options));
        assertEquals(1, sends.get());
        assertTrue(requests.element().contains("\"response_format\":{\"type\":\"json_object\"}"));
        assertTrue(requests.element().contains("\"max_completion_tokens\":1000"));
        assertFalse(requests.element().contains("\"max_tokens\""));
        assertTrue(requests.element().contains("\"service_tier\":\"default\""));
        assertEquals("JSON_OBJECT", options.getResponseFormat().getType().name());
        Snapshot snapshot = ledger.read("approval", scope);
        assertEquals(1, snapshot.providerAttempts());
        assertEquals(chat(12, 3), snapshot.accountedUsage());
        assertFalse(snapshot.stopped());
        assertEquals("identity", acceptEncodings.element());
        UUID clientId = UUID.fromString(clientRequestIds.element());
        assertEquals(List.of(new OpenAiBudgetInvocation.Completion(operationId, clientId, "req_fixture_1",
            OpenAiBudgetInvocation.Operation.CHAT_COMPLETION, CHAT_MODEL, "gpt-4o-mini-2024-07-18", 1000L,
            "stop", 12, 3L, null, 2, false)), List.copyOf(completions));
        try (var connection = source.getConnection(); var query = connection.prepareStatement(
            "SELECT evidence_reference FROM provider_budget_attempt WHERE approval_id = 'approval' AND attempt_id = ?")) {
            query.setString(1, clientId.toString());
            try (var row = query.executeQuery()) {
                assertTrue(row.next());
                assertEquals("openai-request:req_fixture_1:client-attempt:" + clientId, row.getString(1));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"stop", "length", "tool_calls", "content_filter", "function_call"})
    void emptyNativeResponseReportsItsActualFinishCapAndReasoningAfterSettlement(String finish) throws Exception {
        miniScope(); approve(600_000);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHAT);
        response.put("model", GPT_54_MINI_SNAPSHOT);
        response.withObject("/usage").put("completion_tokens", 13).put("total_tokens", 25);
        response.withObject("/usage/completion_tokens_details").put("reasoning_tokens", 10);
        var choice = (com.fasterxml.jackson.databind.node.ObjectNode) response.path("choices").get(0);
        choice.put("finish_reason", finish);
        choice.withObject("/message").put("content", "");
        replies.add(new Reply(200, json.writeValueAsString(response), null, null));
        var completions = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        var settlements = new AtomicInteger();
        var ambiguities = new AtomicInteger();
        ledger = new ForwardingLedger(ledger) {
            @Override public Attempt settle(String id, Scope scope, UUID attempt, Usage usage, String ref) {
                var result = super.settle(id, scope, attempt, usage, ref);
                settlements.incrementAndGet();
                return result;
            }
            @Override public Attempt markAmbiguous(String id, Scope scope, UUID attempt, String reason) {
                ambiguities.incrementAndGet();
                return super.markAmbiguous(id, scope, attempt, reason);
            }
        };
        UUID operationId = UUID.randomUUID();
        captures = operation -> new OpenAiBudgetInvocation(operationId, failure -> fail("Unexpected denial"), completion -> {
            assertEquals(1, settlements.get());
            assertEquals(chat(GPT_54_MINI_MODEL, 12, 13), ledger.read("approval", scope).accountedUsage());
            completions.add(completion);
        });
        var model = resolver(MAX_COMPLETION_TOKENS, GuardedReasoningEffort.MEDIUM).resolveChatModel("openai",
            AIGenerationRequest.builder().model(GPT_54_MINI_MODEL).maxTokens(8192).build());
        model.call(new Prompt("fixture"));
        assertEquals(List.of(new OpenAiBudgetInvocation.Completion(operationId, UUID.fromString(clientRequestIds.element()),
            "req_fixture_1", OpenAiBudgetInvocation.Operation.CHAT_COMPLETION, GPT_54_MINI_MODEL, GPT_54_MINI_SNAPSHOT,
            8192L, finish, 12, 13L, 10L, 0, true)), List.copyOf(completions));
        assertEquals(1, sends.get());
        assertEquals(1, settlements.get());
        assertEquals(0, ambiguities.get());
    }

    @ParameterizedTest @ValueSource(strings = {"null", "\"   \"", "\"fixture-private-response\""})
    void completionProjectionNeverRetainsContentReasoningCredentialsOrPrivateParameters(String content) throws Exception {
        approve(200_000);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHAT);
        var message = (com.fasterxml.jackson.databind.node.ObjectNode) response.path("choices").get(0).path("message");
        message.set("content", json.readTree(content));
        message.put("reasoning_content", "fixture-private-reasoning");
        message.put("refusal", "fixture-private-refusal");
        response.put("private_parameter", "fixture-private-param");
        replies.add(new Reply(200, json.writeValueAsString(response), null, null,
            Map.of("x-request-id", "sk-fixture-secret", "private-header", "fixture-private-header"), null));
        var completions = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        var invocation = new OpenAiBudgetInvocation(UUID.randomUUID(), failure -> fail("Unexpected denial"), completions::add);
        try (var result = rawChat(invocation)) { assertEquals(200, result.code()); }
        assertEquals(1, completions.size());
        var completion = completions.element();
        String text = json.readTree(content).textValue();
        assertEquals(text == null ? 0 : text.length(), completion.contentLength());
        assertEquals(text == null || text.isBlank(), completion.contentEmpty());
        assertNull(completion.providerRequestId());
        String projected = json.writeValueAsString(completion);
        for (String secret : List.of(KEY, "fixture-private-response", "fixture-private-reasoning", "fixture-private-refusal",
            "fixture-private-param", "sk-fixture-secret", "fixture-private-header", "fixture-private-request")) {
            assertFalse(projected.contains(secret));
        }
        assertEquals(chat(12, 3), ledger.read("approval", scope).accountedUsage());
        assertEquals(1, sends.get());
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "4", "1.5", "\"fixture-secret\"", "null", "9223372036854775808"})
    void malformedOptionalMetadataDoesNotRejectOrChangeNativeSettlement(String reasoning) throws Exception {
        approve(200_000);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHAT);
        response.withObject("/usage/completion_tokens_details").set("reasoning_tokens", json.readTree(reasoning));
        var choice = (com.fasterxml.jackson.databind.node.ObjectNode) response.path("choices").get(0);
        choice.put("finish_reason", "fixture-secret");
        choice.withObject("/message").putObject("content").put("private", "fixture-secret");
        replies.add(new Reply(200, json.writeValueAsString(response), null, null));
        var completions = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        try (var result = rawChat(new OpenAiBudgetInvocation(UUID.randomUUID(), failure -> fail("Unexpected denial"), completions::add))) {
            assertEquals(200, result.code());
        }
        assertEquals(1, completions.size());
        var completion = completions.element();
        assertNull(completion.reasoningTokens());
        assertNull(completion.finishReason());
        assertNull(completion.contentLength());
        assertNull(completion.contentEmpty());
        assertEquals(12, completion.inputTokens());
        assertEquals(3L, completion.outputTokens());
        assertEquals(chat(12, 3), ledger.read("approval", scope).accountedUsage());
        assertFalse(completion.toString().contains("fixture-secret"));
        assertEquals(1, sends.get());
    }

    @Test void missingOptionalMetadataIsUnknownRatherThanInventedEmptyOrZero() throws Exception {
        approve(200_000);
        replies.add(new Reply(200, "{\"model\":\"gpt-4o-mini\",\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3,\"total_tokens\":15}}", null, null));
        var completions = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        try (var result = rawChat(new OpenAiBudgetInvocation(UUID.randomUUID(), failure -> fail("Unexpected denial"), completions::add))) {
            assertEquals(200, result.code());
        }
        assertEquals(1, completions.size());
        var completion = completions.element();
        assertNull(completion.finishReason()); assertNull(completion.reasoningTokens());
        assertNull(completion.contentLength()); assertNull(completion.contentEmpty());
        assertEquals(chat(12, 3), ledger.read("approval", scope).accountedUsage());
    }

    @Test void completionObserverFailureCannotReclassifySettlementOrTriggerAnotherSend() {
        approve(200_000);
        var callbacks = new AtomicInteger();
        var denials = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Denial>();
        captures = operation -> new OpenAiBudgetInvocation(UUID.randomUUID(), denials::add, completion -> {
            callbacks.incrementAndGet();
            throw new IllegalStateException("fixture-private-observer-detail");
        });
        replies.add(new Reply(200, CHAT, null, null));
        assertDoesNotThrow(() -> call(resolver()));
        assertEquals(1, callbacks.get()); assertTrue(denials.isEmpty());
        assertEquals(chat(12, 3), ledger.read("approval", scope).accountedUsage());
        assertEquals(1, sends.get()); assertEquals(1, ledger.read("approval", scope).providerAttempts());
    }

    @Test void failedSettlementReportsDenialWithoutSuccessfulCompletion() {
        approve(200_000);
        var completions = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        var denials = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Denial>();
        captures = operation -> new OpenAiBudgetInvocation(UUID.randomUUID(), denials::add, completions::add);
        ledger = new ForwardingLedger(ledger) {
            @Override public Attempt settle(String id, Scope scope, UUID attempt, Usage usage, String ref) {
                throw new BudgetException(BudgetException.Code.STORAGE_UNAVAILABLE);
            }
        };
        replies.add(new Reply(200, CHAT, null, null));
        assertThrows(RuntimeException.class, () -> call(resolver()));
        assertTrue(completions.isEmpty()); assertEquals(1, denials.size());
        assertEquals(chat(128_000, 1000), ledger.read("approval", scope).accountedUsage());
        assertEquals(1, sends.get());
    }

    @Test void preboundNativeClientsKeepSuccessfulAsyncExchangesWithTheirOwnInvocation() throws Exception {
        approve(400_000);
        replies.add(new Reply(200, CHAT, null, null));
        replies.add(new Reply(200, CHAT, null, null));
        var root = root(0, Duration.ofSeconds(2));
        closeables.add(root.client()::close);
        var firstEvents = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        var secondEvents = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        UUID firstId = UUID.randomUUID(); UUID secondId = UUID.randomUUID();
        var firstClient = root.client().withOptions(options -> options.httpClient(new BoundHttpClient(root.transport(),
            new OpenAiBudgetInvocation(firstId, failure -> fail("Unexpected denial"), firstEvents::add))));
        var secondClient = root.client().withOptions(options -> options.httpClient(new BoundHttpClient(root.transport(),
            new OpenAiBudgetInvocation(secondId, failure -> fail("Unexpected denial"), secondEvents::add))));
        var first = firstClient.async().chat().completions().create(params());
        var second = secondClient.async().chat().completions().create(params());
        first.get(3, TimeUnit.SECONDS); second.get(3, TimeUnit.SECONDS);
        assertEquals(1, firstEvents.size()); assertEquals(1, secondEvents.size());
        assertEquals(firstId, firstEvents.element().operationId()); assertEquals(secondId, secondEvents.element().operationId());
        assertNotEquals(firstEvents.element().attemptId(), secondEvents.element().attemptId());
        assertTrue(clientRequestIds.contains(firstEvents.element().attemptId().toString()));
        assertTrue(clientRequestIds.contains(secondEvents.element().attemptId().toString()));
        assertEquals(chat(24, 6), ledger.read("approval", scope).accountedUsage());
        assertEquals(2, sends.get());
    }

    @ParameterizedTest @ValueSource(ints = {1, 400, 16384})
    void explicitCapModeSerializesOnlyTheSameAllGeneratedTokensCap(int cap) throws Exception {
        approve(200_000);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHAT);
        response.withObject("/usage").put("completion_tokens", 1).put("total_tokens", 13);
        var release = new CountDownLatch(1);
        replies.add(new Reply(200, json.writeValueAsString(response), null, release));
        var request = AIGenerationRequest.builder().purpose("fixture-purpose").maxTokens(cap).temperature(0.2)
            .parameters(Map.of("response_format", Map.of("type", "json_object"))).build();
        var model = resolver().resolveChatModel("openai", request);
        var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> model.call(new Prompt("fixture")));
        try {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((sends.get() == 0 || requests.isEmpty()) && System.nanoTime() < until) Thread.sleep(5);
            assertEquals(1, sends.get());
            assertFalse(requests.isEmpty(), "The synthetic receiver must capture the request body before inspection.");
            assertEquals(chat(128_000, cap), ledger.read("approval", scope).accountedUsage());
            var body = json.readTree(requests.element());
            assertEquals(cap, body.path("max_completion_tokens").intValue());
            assertFalse(body.has("max_tokens"));
            assertEquals(CHAT_MODEL, body.path("model").textValue());
            assertEquals(0.2, body.path("temperature").doubleValue());
            assertEquals("json_object", body.path("response_format").path("type").textValue());
            assertEquals(1, body.path("n").intValue());
            assertEquals("default", body.path("service_tier").textValue());
            assertEquals("fixture-purpose", request.getPurpose());
            assertEquals(cap, request.getMaxTokens());
        } finally { release.countDown(); }
        future.get(3, TimeUnit.SECONDS);
        assertEquals(chat(12, 1), ledger.read("approval", scope).accountedUsage());
    }

    @Test void explicitCapModeKeepsNativeJsonSchemaOnTheWire() throws Exception {
        approve(200_000);
        replies.add(new Reply(200, CHAT, null, null));
        String schema = "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}},\"required\":[\"ok\"],\"additionalProperties\":false}";
        var request = AIGenerationRequest.builder().maxTokens(400).temperature(0.2)
            .parameters(ai.fabric.llm.structured.StructuredJsonProviderHints.jsonSchemaResponseParameters(schema)).build();
        resolver().resolveChatModel("openai", request).call(new Prompt("fixture"));
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = json.readTree(requests.element());
        assertEquals(400, body.path("max_completion_tokens").intValue());
        assertFalse(body.has("max_tokens"));
        assertEquals("json_schema", body.path("response_format").path("type").textValue());
        assertEquals(json.readTree(schema), body.path("response_format").path("json_schema").path("schema"));
        assertEquals(chat(12, 3), ledger.read("approval", scope).accountedUsage());
    }

    @Test void initial100kCannotSendAndTypedDenialDoesNotStopCampaign() {
        approve(100_000);
        var failure = assertThrows(RuntimeException.class, () -> call(resolver()));
        var denial = OpenAiBudgetTransportException.find(failure).orElseThrow();
        assertEquals(BudgetException.Code.LIMIT_EXCEEDED, denial.budgetCode().orElseThrow());
        assertNotNull(denial.attemptId());
        assertEquals(0, sends.get());
        assertEquals(0, ledger.read("approval", scope).providerAttempts());
        assertFalse(ledger.read("approval", scope).stopped());
    }

    @Test void pendingHoldDeniesOnlyCurrentSendAndSettledReleaseAdmitsLaterSend() throws Exception {
        approve(200_000);
        CountDownLatch release = new CountDownLatch(1);
        replies.add(new Reply(200, CHAT, null, release));
        var client = client(0, Duration.ofSeconds(4));
        var first = client.async().chat().completions().create(params());
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (sends.get() == 0 && System.nanoTime() < until) Thread.sleep(5);
        try {
            assertEquals(1, sends.get());
            assertEquals(chat(128_000, 1000), ledger.read("approval", scope).accountedUsage());
            var error = assertThrows(RuntimeException.class, () -> client.chat().completions().create(params()));
            assertEquals(BudgetException.Code.LIMIT_EXCEEDED, OpenAiBudgetTransportException.find(error).orElseThrow().budgetCode().orElseThrow());
            assertEquals(1, sends.get());
            assertFalse(ledger.read("approval", scope).stopped());
        } finally { release.countDown(); }
        first.get(3, TimeUnit.SECONDS);
        replies.add(new Reply(200, CHAT, null, null));
        client.chat().completions().create(params());
        assertEquals(2, sends.get());
        assertEquals(chat(24, 6), ledger.read("approval", scope).accountedUsage());
    }

    @ParameterizedTest @ValueSource(ints = {302, 307, 401, 408, 429, 500, 503})
    void errorsAndRedirectsDoNotRetryAndKeepFullHold(int status) {
        approve(200_000);
        var completions = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        captures = operation -> new OpenAiBudgetInvocation(UUID.randomUUID(), failure -> { }, completions::add);
        replies.add(new Reply(status, "{}", endpoint + "/embeddings", null));
        replies.add(new Reply(200, CHAT, null, null));
        assertThrows(RuntimeException.class, () -> call(resolver()));
        assertEquals(1, sends.get());
        assertEquals(1, ledger.read("approval", scope).providerAttempts());
        assertEquals(chat(128_000, 1000), ledger.read("approval", scope).accountedUsage());
        assertTrue(completions.isEmpty());
    }

    @Test void sdkAutomaticRetryStillReservesBeforeEveryNetworkAttempt() {
        approve(400_000);
        replies.add(new Reply(503, "{}", null, null));
        replies.add(new Reply(200, CHAT, null, null));
        client(1, Duration.ofSeconds(2)).chat().completions().create(params());
        assertEquals(2, sends.get());
        assertEquals(2, ledger.read("approval", scope).providerAttempts());
        assertEquals(new Usage(128_012, 1003, chat(128_000, 1000).costNanoUsd() + chat(12, 3).costNanoUsd()),
            ledger.read("approval", scope).accountedUsage());
    }

    @Test void retryAfterUnknownCannotReachProviderWhenSecondHoldDoesNotFit() {
        approve(200_000);
        replies.add(new Reply(503, "{}", null, null));
        replies.add(new Reply(200, CHAT, null, null));
        var error = assertThrows(RuntimeException.class, () -> client(2, Duration.ofSeconds(2)).chat().completions().create(params()));
        assertEquals(BudgetException.Code.LIMIT_EXCEEDED, OpenAiBudgetTransportException.find(error).orElseThrow().budgetCode().orElseThrow());
        assertEquals(1, sends.get());
        assertEquals(1, ledger.read("approval", scope).providerAttempts());
        assertFalse(ledger.read("approval", scope).stopped());
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "broken", "{\"prompt_tokens\":12,\"completion_tokens\":3}",
        "{\"prompt_tokens\":12,\"completion_tokens\":3,\"total_tokens\":14}",
        "{\"prompt_tokens\":12.0,\"completion_tokens\":3,\"total_tokens\":15}",
        "{\"prompt_tokens\":-1,\"completion_tokens\":3,\"total_tokens\":2}",
        "{\"prompt_tokens\":12,\"prompt_tokens\":11,\"completion_tokens\":3,\"total_tokens\":15}"})
    void missingMalformedOrAmbiguousUsageRetainsFullHoldWithoutScopeStop(String usage) {
        approve(200_000);
        var completions = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        captures = operation -> new OpenAiBudgetInvocation(UUID.randomUUID(), failure -> { }, completions::add);
        replies.add(new Reply(200, "{\"model\":\"gpt-4o-mini\",\"usage\":" + usage + "}", null, null));
        var error = assertThrows(RuntimeException.class, () -> call(resolver()));
        assertEquals(OpenAiBudgetException.Code.ATTEMPT_AMBIGUOUS, OpenAiBudgetTransportException.find(error).orElseThrow().guardCode().orElseThrow());
        assertEquals(chat(128_000, 1000), ledger.read("approval", scope).accountedUsage());
        assertFalse(ledger.read("approval", scope).stopped());
        assertEquals(1, sends.get());
        assertTrue(completions.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"{\"prompt_tokens\":12,\"completion_tokens\":1001,\"total_tokens\":1013}",
        "{\"prompt_tokens\":128001,\"completion_tokens\":0,\"total_tokens\":128001}",
        "{\"prompt_tokens\":128000,\"completion_tokens\":3,\"total_tokens\":128003}"})
    void verifiedBoundBreachStopsTheScopeWithoutReleasingTheHold(String usage) {
        approve(400_000);
        replies.add(new Reply(200, "{\"model\":\"gpt-4o-mini\",\"usage\":" + usage + "}", null, null));
        var resolver = resolver();
        var error = assertThrows(RuntimeException.class, () -> call(resolver));
        var failure = OpenAiBudgetTransportException.find(error).orElseThrow();
        assertEquals(OpenAiBudgetException.Code.METERING_BOUND_BREACH, failure.guardCode().orElseThrow());
        Snapshot durable = new JdbcProviderBudgetLedger(source).read("approval", scope);
        assertEquals(chat(128_000, 1000), durable.accountedUsage());
        assertTrue(durable.stopped());
        assertEquals("METERING_BOUND_BREACH", durable.stopReason());
        assertThrows(RuntimeException.class, () -> call(resolver));
        assertEquals(1, sends.get());
    }

    @Test void failedDurableStopWriteCannotReopenTheScopeThroughAnotherRoot() {
        approve(400_000);
        var healthy = ledger;
        var writesToFail = new AtomicInteger(2);
        ledger = new ForwardingLedger(healthy) {
            @Override public Snapshot stop(String id, Scope scope, String reason) {
                if (writesToFail.getAndDecrement() > 0) throw new BudgetException(BudgetException.Code.STORAGE_UNAVAILABLE);
                return super.stop(id, scope, reason);
            }
        };
        meteringStops = new OpenAiMeteringStops(ledger);
        replies.add(new Reply(200, "{\"model\":\"gpt-4o-mini\",\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":1001,\"total_tokens\":1013}}", null, null));
        for (int attempt = 0; attempt < 3; attempt++) {
            var error = assertThrows(RuntimeException.class,
                () -> client(2, Duration.ofSeconds(2)).chat().completions().create(params()));
            var failure = OpenAiBudgetTransportException.find(error).orElseThrow();
            assertEquals(OpenAiBudgetException.Code.METERING_BOUND_BREACH, failure.guardCode().orElseThrow());
            assertEquals(attempt < 2 ? BudgetException.Code.STORAGE_UNAVAILABLE : BudgetException.Code.STOPPED,
                failure.budgetCode().orElseThrow());
            assertEquals(1, sends.get());
            Snapshot durable = new JdbcProviderBudgetLedger(source).read("approval", scope);
            assertEquals(attempt == 2, durable.stopped());
            assertEquals(chat(128_000, 1000), durable.accountedUsage());
            assertEquals(1, durable.providerAttempts());
        }
        // Even without the in-memory latch, a fresh ledger/transport observes the durable stop.
        meteringStops = new OpenAiMeteringStops(ledger);
        var error = assertThrows(RuntimeException.class,
            () -> client(0, Duration.ofSeconds(2)).chat().completions().create(params()));
        assertEquals(BudgetException.Code.STOPPED, OpenAiBudgetTransportException.find(error).orElseThrow().budgetCode().orElseThrow());
        assertEquals(1, sends.get());
    }

    @ParameterizedTest @ValueSource(strings = {"", "\"default\""})
    void missingOrVerifiedDefaultResponseTierCanSettle(String tier) throws Exception {
        approve(200_000);
        replies.add(new Reply(200, responseWithTier(tier), null, null));
        client(0, Duration.ofSeconds(2)).chat().completions().create(params());
        assertEquals(chat(12, 3), ledger.read("approval", scope).accountedUsage());
        assertFalse(ledger.read("approval", scope).stopped());
    }

    @ParameterizedTest @ValueSource(strings = {"\"priority\"", "\"fast\"", "\"auto\"", "\"flex\"", "\"scale\"", "null", "false", "{}"})
    void unverifiedResponseTierCannotSettleEvenWithValidUsage(String tier) throws Exception {
        approve(400_000);
        replies.add(new Reply(200, responseWithTier(tier), null, null));
        var error = assertThrows(RuntimeException.class,
            () -> client(0, Duration.ofSeconds(2)).chat().completions().create(params()));
        assertEquals(OpenAiBudgetException.Code.ATTEMPT_AMBIGUOUS,
            OpenAiBudgetTransportException.find(error).orElseThrow().guardCode().orElseThrow());
        assertEquals(chat(128_000, 1000), ledger.read("approval", scope).accountedUsage());
        assertFalse(ledger.read("approval", scope).stopped());
        assertEquals(1, sends.get());
    }

    @Test void validUsageBreachCannotBeHiddenByAnUnexpectedResponseTier() {
        approve(400_000);
        replies.add(new Reply(200, "{\"model\":\"gpt-4o-mini\",\"service_tier\":\"priority\","
            + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":1001,\"total_tokens\":1013}}", null, null));
        var error = assertThrows(RuntimeException.class,
            () -> client(2, Duration.ofSeconds(2)).chat().completions().create(params()));
        assertEquals(OpenAiBudgetException.Code.METERING_BOUND_BREACH,
            OpenAiBudgetTransportException.find(error).orElseThrow().guardCode().orElseThrow());
        assertTrue(new JdbcProviderBudgetLedger(source).read("approval", scope).stopped());
        assertEquals(1, sends.get());
    }

    @Test void frameworkLegacyLimitIsNotSilentlyTranslatedAndCannotSend() {
        approve(400_000);
        var resolver = resolver(PRESERVE_FRAMEWORK);
        var options = (OpenAiChatOptions) resolver.resolveChatOptions("openai", null);
        assertEquals(1000, options.getMaxTokens());
        assertNull(options.getMaxCompletionTokens());
        var error = assertThrows(RuntimeException.class,
            () -> resolver.resolveChatModel("openai", null).call(new Prompt("fixture", options)));
        assertEquals(OpenAiBudgetException.Code.UNSUPPORTED_OUTPUT_LIMIT_MODE,
            OpenAiBudgetTransportException.find(error).orElseThrow().guardCode().orElseThrow());
        assertEquals(0, sends.get());
        assertEquals(0, ledger.read("approval", scope).providerAttempts());
        assertFalse(ledger.read("approval", scope).stopped());
    }

    @Test void embeddingUsageBeyondItsInputBoundAlsoDurablyStopsTheScope() {
        approve(400_000);
        replies.add(new Reply(200, "{\"model\":\"text-embedding-3-small\",\"usage\":{\"prompt_tokens\":8193,\"total_tokens\":8193}}", null, null));
        var error = assertThrows(RuntimeException.class, () -> client(2, Duration.ofSeconds(2)).embeddings().create(
            com.openai.models.embeddings.EmbeddingCreateParams.builder().model(EMBEDDING_MODEL).input("fixture").build()));
        assertEquals(OpenAiBudgetException.Code.METERING_BOUND_BREACH,
            OpenAiBudgetTransportException.find(error).orElseThrow().guardCode().orElseThrow());
        Snapshot durable = new JdbcProviderBudgetLedger(source).read("approval", scope);
        assertTrue(durable.stopped());
        assertEquals(embedding(8192), durable.accountedUsage());
        assertEquals(1, sends.get());
    }

    @Test void finalBearerCredentialMustBelongToTheTrustedScope() {
        approve(200_000);
        var client = client(0, Duration.ofSeconds(2)).withOptions(options -> options.apiKey("unbound-fixture-key"));
        var error = assertThrows(RuntimeException.class, () -> client.chat().completions().create(params()));
        assertEquals(OpenAiBudgetException.Code.CREDENTIAL_SCOPE_MISMATCH, OpenAiBudgetTransportException.find(error).orElseThrow().guardCode().orElseThrow());
        assertEquals(0, sends.get());
        assertEquals(0, ledger.read("approval", scope).providerAttempts());
    }

    @Test void wrongRateCardCannotReachTheProvider() {
        approve(200_000);
        Scope original = scope;
        scope = new Scope(scope.customerId(), scope.deploymentId(), scope.targetProfileId(), scope.environment(), scope.credentialIdentity(), "unreviewed-rate-card");
        var error = assertThrows(RuntimeException.class, () -> call(resolver()));
        assertEquals(OpenAiBudgetException.Code.RATE_CARD_MISMATCH, OpenAiBudgetTransportException.find(error).orElseThrow().guardCode().orElseThrow());
        assertEquals(0, sends.get());
        assertEquals(0, ledger.read("approval", original).providerAttempts());
    }

    @Test void asyncDenialsStayWithTheCapturedTurnWithoutGlobalCounters() {
        approve(100_000);
        Thread caller = Thread.currentThread();
        ThreadLocal<OpenAiBudgetInvocation> current = new ThreadLocal<>();
        var firstEvents = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Denial>();
        var secondEvents = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Denial>();
        UUID firstTurn = UUID.randomUUID(); UUID secondTurn = UUID.randomUUID();
        captures = operation -> {
            assertSame(caller, Thread.currentThread());
            assertEquals(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION, operation);
            return current.get();
        };
        var resolver = resolver();
        current.set(new OpenAiBudgetInvocation(firstTurn, firstEvents::add));
        var firstModel = resolver.resolveChatModel("openai", null);
        current.set(new OpenAiBudgetInvocation(secondTurn, secondEvents::add));
        var secondModel = resolver.resolveChatModel("openai", null);
        current.remove();
        assertNotSame(firstModel, secondModel);
        assertEquals(1, rootsCreated.get());
        // Spring's stream path invokes the native async SDK; the guard must reject streaming on each bound turn.
        var first = firstModel.stream(new Prompt("fixture")).collectList().toFuture();
        var second = secondModel.stream(new Prompt("fixture")).collectList().toFuture();
        var firstError = assertThrows(RuntimeException.class, first::join);
        var secondError = assertThrows(RuntimeException.class, second::join);
        assertTrue(OpenAiBudgetTransportException.find(firstError).isPresent(), firstError::toString);
        assertTrue(OpenAiBudgetTransportException.find(secondError).isPresent(), secondError::toString);
        assertEquals(1, firstEvents.size()); assertEquals(1, secondEvents.size());
        assertEquals(firstTurn, firstEvents.element().operationId());
        assertEquals(secondTurn, secondEvents.element().operationId());
        assertNotEquals(firstEvents.element().attemptId(), secondEvents.element().attemptId());
        assertEquals(OpenAiBudgetException.Code.UNSUPPORTED_REQUEST, firstEvents.element().guardCode());
        assertEquals(OpenAiBudgetException.Code.UNSUPPORTED_REQUEST, secondEvents.element().guardCode());
        assertEquals(0, sends.get());
    }

    @Test void hardModeCaptureCanRejectMissingTurnBeforeEnqueue() {
        approve(200_000);
        captures = operation -> null;
        var error = assertThrows(OpenAiBudgetException.class, () -> resolver().resolveChatModel("openai", null));
        assertEquals(OpenAiBudgetException.Code.INVOCATION_CONTEXT_REQUIRED, error.code());
        assertEquals(0, rootsCreated.get());
        assertEquals(0, sends.get());
        assertEquals(0, ledger.read("approval", scope).providerAttempts());
    }

    @Test void preboundNativeClientsKeepAsyncBudgetDenialsWithTheirOwnInvocation() {
        approve(100_000);
        var root = root(0, Duration.ofSeconds(2));
        closeables.add(root.client()::close);
        UUID firstId = UUID.randomUUID(); UUID secondId = UUID.randomUUID();
        var firstEvents = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Denial>();
        var secondEvents = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Denial>();
        var firstInvocation = new OpenAiBudgetInvocation(firstId, firstEvents::add);
        var secondInvocation = new OpenAiBudgetInvocation(secondId, secondEvents::add);
        var firstClient = root.client().withOptions(options -> options.httpClient(new BoundHttpClient(root.transport(), firstInvocation)));
        var secondClient = root.client().withOptions(options -> options.httpClient(new BoundHttpClient(root.transport(), secondInvocation)));
        var first = firstClient.async().chat().completions().create(params());
        var second = secondClient.async().chat().completions().create(params());
        assertThrows(RuntimeException.class, first::join);
        assertThrows(RuntimeException.class, second::join);
        assertEquals(1, rootsCreated.get());
        assertEquals(1, firstEvents.size()); assertEquals(1, secondEvents.size());
        assertEquals(firstId, firstEvents.element().operationId());
        assertEquals(secondId, secondEvents.element().operationId());
        assertNotEquals(firstEvents.element().attemptId(), secondEvents.element().attemptId());
        assertEquals(BudgetException.Code.LIMIT_EXCEEDED, firstEvents.element().budgetCode());
        assertEquals(BudgetException.Code.LIMIT_EXCEEDED, secondEvents.element().budgetCode());
        assertEquals(0, sends.get());
        assertFalse(ledger.read("approval", scope).stopped());
    }

    @Test void borrowedCloseDoesNotOwnRootAndResolverClosesTransportExactlyOnce() {
        approve(200_000);
        var shutdowns = new AtomicInteger();
        var ownedExecutor = new java.util.concurrent.ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
            new java.util.concurrent.SynchronousQueue<Runnable>()) {
            @Override public void shutdown() { shutdowns.incrementAndGet(); super.shutdown(); }
        };
        var nativeTransport = new NativeOpenAiHttpClient(new OkHttpClient.Builder()
            .dispatcher(new okhttp3.Dispatcher(ownedExecutor)).retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).callTimeout(Duration.ofSeconds(2))
            .addNetworkInterceptor(new OpenAiExchangeGuard(ledger, identity -> new OpenAiBudgetScopeResolver.Binding("approval", scope),
                HttpUrl.get(endpoint))).build());
        var rootClient = new OpenAIClientImpl(ClientOptions.builder().apiKey(KEY).baseUrl(endpoint).maxRetries(0)
            .httpClient(nativeTransport).build());
        var config = new AIProviderConfig(); config.getOpenai().setApiKey(KEY);
        var resolver = new BudgetGuardedSpringAiModelResolver(config, ObservationRegistry.NOOP, (key, timeout) -> {
            rootsCreated.incrementAndGet();
            return new BudgetGuardedSpringAiModelResolver.RootConnection(rootClient, nativeTransport);
        }, operation -> OpenAiBudgetInvocation.NONE, MAX_COMPLETION_TOKENS);
        closeables.add(resolver::destroy);
        var request = AIGenerationRequest.builder().maxTokens(400).parameters(Map.of("response_format", Map.of("type", "json_object"))).build();
        var first = (org.springframework.ai.openai.OpenAiChatModel) resolver.resolveChatModel("openai", request);
        var second = (org.springframework.ai.openai.OpenAiChatModel) resolver.resolveChatModel("openai", null);
        var embedding = resolver.resolveEmbeddingModel("openai", null);
        assertNotSame(first, second);
        assertNotNull(embedding);
        assertEquals(400, first.getOptions().getMaxCompletionTokens());
        assertNull(first.getOptions().getMaxTokens());
        assertEquals("JSON_OBJECT", first.getOptions().getResponseFormat().getType().name());
        assertEquals(1000, second.getOptions().getMaxCompletionTokens());
        assertNull(second.getOptions().getMaxTokens());
        assertEquals(1, rootsCreated.get());
        rootClient.withOptions(options -> options.httpClient(new BoundHttpClient(nativeTransport, OpenAiBudgetInvocation.NONE))).close();
        assertEquals(0, shutdowns.get());
        replies.add(new Reply(200, CHAT, null, null));
        second.call(new Prompt("fixture"));
        assertEquals(1, sends.get());
        resolver.destroy(); resolver.destroy();
        assertEquals(1, shutdowns.get());
        assertThrows(OpenAiBudgetException.class, () -> second.call(new Prompt("fixture")));
        assertThrows(OpenAiBudgetException.class, () -> resolver.resolveChatModel("openai", null));
        assertEquals(1, rootsCreated.get()); assertEquals(1, sends.get());
    }

    @Test void observerFailureDoesNotEraseTypedDenialOrExposeItsProse() {
        approve(100_000);
        captures = operation -> new OpenAiBudgetInvocation(UUID.randomUUID(), failure -> { throw new IllegalStateException("fixture-host-detail"); });
        var error = assertThrows(RuntimeException.class, () -> call(resolver()));
        var failure = OpenAiBudgetTransportException.find(error).orElseThrow();
        assertEquals(BudgetException.Code.LIMIT_EXCEEDED, failure.budgetCode().orElseThrow());
        assertEquals("DENIAL_OBSERVER_FAILED", failure.getSuppressed()[0].getMessage());
        assertNull(failure.getSuppressed()[0].getCause());
        assertEquals(0, sends.get());
    }

    @Test void timeoutAfterSendRetainsTheFullReservation() {
        approve(200_000);
        CountDownLatch release = new CountDownLatch(1);
        replies.add(new Reply(200, CHAT, null, release));
        try {
            assertThrows(RuntimeException.class, () -> client(0, Duration.ofMillis(250)).chat().completions().create(params()));
            assertEquals(1, sends.get());
            assertEquals(chat(128_000, 1000), ledger.read("approval", scope).accountedUsage());
        } finally { release.countDown(); }
    }

    @Test void gzipDespiteIdentityIsAnUnverifiedFullHold() throws Exception {
        approve(200_000);
        var bytes = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(bytes)) { gzip.write(CHAT.getBytes(StandardCharsets.UTF_8)); }
        replies.add(new Reply(200, "", null, null, Map.of("Content-Encoding", "gzip"), bytes.toByteArray()));
        assertThrows(RuntimeException.class, () -> call(resolver()));
        assertEquals("identity", acceptEncodings.element());
        assertEquals(chat(128_000, 1000), ledger.read("approval", scope).accountedUsage());
    }

    @ParameterizedTest @ValueSource(longs = {0, 10_000})
    void requestOptionsCannotDisableOrExtendTransportTimeout(long requestedSeconds) {
        approve(200_000);
        CountDownLatch release = new CountDownLatch(1);
        replies.add(new Reply(200, CHAT, null, release));
        var timeout = com.openai.core.Timeout.builder().request(Duration.ofSeconds(requestedSeconds))
            .read(Duration.ofSeconds(requestedSeconds)).build();
        long start = System.nanoTime();
        try {
            assertThrows(RuntimeException.class, () -> client(0, Duration.ofMillis(250)).chat().completions()
                .create(params(), RequestOptions.builder().timeout(timeout).build()));
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2));
            assertEquals(1, sends.get());
            assertEquals(chat(128_000, 1000), ledger.read("approval", scope).accountedUsage());
        } finally { release.countDown(); }
    }

    @Test void realEmbeddingBatchSettlesWithNoChatCount() {
        approve(200_000);
        var completions = new ConcurrentLinkedQueue<OpenAiBudgetInvocation.Completion>();
        UUID operationId = UUID.randomUUID();
        replies.add(new Reply(200, """
            {"object":"list","model":"text-embedding-3-small","data":[
             {"object":"embedding","index":0,"embedding":[0.1,0.2]},
             {"object":"embedding","index":1,"embedding":[0.3,0.4]}],
             "usage":{"prompt_tokens":5,"total_tokens":5}}
            """, null, null));
        Thread caller = Thread.currentThread();
        captures = operation -> {
            assertSame(caller, Thread.currentThread());
            assertEquals(OpenAiBudgetInvocation.Operation.EMBEDDING, operation);
            return new OpenAiBudgetInvocation(operationId, failure -> fail("Unexpected denial"), completions::add);
        };
        var resolver = resolver();
        var model = resolver.resolveEmbeddingModel("openai", null);
        var options = resolver.resolveEmbeddingOptions("openai", null);
        java.util.concurrent.CompletableFuture.supplyAsync(() -> model.call(new EmbeddingRequest(List.of("alpha", "beta"), options))).join();
        assertEquals(1, sends.get());
        assertEquals(embedding(5), ledger.read("approval", scope).accountedUsage());
        assertEquals(0, ledger.read("approval", scope).chatAttempts());
        assertEquals(List.of(new OpenAiBudgetInvocation.Completion(operationId, UUID.fromString(clientRequestIds.element()),
            "req_fixture_1", OpenAiBudgetInvocation.Operation.EMBEDDING, EMBEDDING_MODEL, EMBEDDING_MODEL, null, null,
            5, null, null, null, null)), List.copyOf(completions));
    }

    @Test void nativeAsyncBodyCannotBypassStreamingRestriction() {
        approve(200_000);
        var error = assertThrows(RuntimeException.class,
            () -> client(0, Duration.ofSeconds(2)).async().chat().completions().createStreaming(params())
                .subscribe(chunk -> { }).onCompleteFuture().join());
        assertTrue(OpenAiBudgetTransportException.find(error).isPresent());
        assertEquals(0, sends.get());
        assertEquals(0, ledger.read("approval", scope).providerAttempts());
    }

    private void approve(long tokens) {
        ledger.approve(new Approval("approval", scope, new Limits(50, tokens, 2_000_000_000L), Instant.now().plusSeconds(120)));
    }

    private void approveAstra() {
        miniScope();
        ledger.approve(new Approval("approval", scope, new Limits(50, 600_000, 5_000_000_000L), Instant.now().plusSeconds(120)));
    }

    private BudgetGuardedSpringAiModelResolver resolver() {
        return resolver(MAX_COMPLETION_TOKENS);
    }

    private BudgetGuardedSpringAiModelResolver resolver(BudgetGuardedSpringAiModelResolver.OutputCapMode mode) {
        return resolver(mode, GuardedReasoningEffort.NONE);
    }

    private BudgetGuardedSpringAiModelResolver resolver(BudgetGuardedSpringAiModelResolver.OutputCapMode mode, GuardedReasoningEffort effort) {
        var config = new AIProviderConfig(); config.getOpenai().setApiKey(KEY);
        var resolver = new BudgetGuardedSpringAiModelResolver(config, ObservationRegistry.NOOP,
            (key, timeout) -> root(0, timeout, effort), captures, mode, effort);
        closeables.add(resolver::destroy);
        return resolver;
    }

    private void call(BudgetGuardedSpringAiModelResolver resolver) {
        resolver.resolveChatModel("openai", null).call(new Prompt("fixture", resolver.resolveChatOptions("openai", null)));
    }

    private okhttp3.Response rawChat(OpenAiBudgetInvocation invocation) throws Exception {
        var client = new OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
            .callTimeout(Duration.ofSeconds(2)).addNetworkInterceptor(new OpenAiExchangeGuard(ledger,
                identity -> new OpenAiBudgetScopeResolver.Binding("approval", scope), HttpUrl.get(endpoint), meteringStops)).build();
        closeables.add(() -> { client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); });
        var request = new okhttp3.Request.Builder().url(endpoint + "/chat/completions").header("Authorization", "Bearer " + KEY)
            .tag(OpenAiBudgetInvocation.class, invocation).post(okhttp3.RequestBody.create("""
                {"model":"gpt-4o-mini","messages":[{"role":"user","content":"fixture-private-request"}],
                 "max_completion_tokens":1000,"service_tier":"default","metadata":{"private":"fixture-private-param"}}
                """, okhttp3.MediaType.get("application/json"))).build();
        return client.newCall(request).execute();
    }

    private String responseWithTier(String tier) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHAT);
        if (!tier.isEmpty()) response.set("service_tier", json.readTree(tier));
        return json.writeValueAsString(response);
    }

    private OpenAIClient client(int sdkRetries, Duration timeout) {
        var root = root(sdkRetries, timeout);
        closeables.add(root.client()::close);
        return root.client();
    }

    private BudgetGuardedSpringAiModelResolver.RootConnection root(int sdkRetries, Duration timeout) {
        return root(sdkRetries, timeout, GuardedReasoningEffort.NONE);
    }

    private BudgetGuardedSpringAiModelResolver.RootConnection root(int sdkRetries, Duration timeout, GuardedReasoningEffort effort) {
        rootsCreated.incrementAndGet();
        var transport = transport(timeout, effort);
        var client = new OpenAIClientImpl(ClientOptions.builder().apiKey(KEY).baseUrl(endpoint).maxRetries(sdkRetries)
            .timeout(timeout).httpClient(transport).build());
        return new BudgetGuardedSpringAiModelResolver.RootConnection(client, transport);
    }

    private NativeOpenAiHttpClient transport(Duration timeout) {
        return transport(timeout, GuardedReasoningEffort.NONE);
    }

    private NativeOpenAiHttpClient transport(Duration timeout, GuardedReasoningEffort effort) {
        return new NativeOpenAiHttpClient(new OkHttpClient.Builder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).callTimeout(timeout).readTimeout(timeout)
            .addNetworkInterceptor(new OpenAiExchangeGuard(ledger, identity -> astraAmended
                ? new OpenAiBudgetScopeResolver.Binding("approval", scope, ASTRA_REFERENCE, "astra-test")
                : new OpenAiBudgetScopeResolver.Binding("approval", scope),
                HttpUrl.get(endpoint), meteringStops, effort)).build());
    }

    private ChatCompletionCreateParams params() {
        return ChatCompletionCreateParams.builder().model(CHAT_MODEL).addUserMessage("fixture").maxCompletionTokens(1000)
            .serviceTier(ChatCompletionCreateParams.ServiceTier.of("default")).n(1).build();
    }

    private static class ForwardingLedger implements ProviderBudgetLedger {
        private final ProviderBudgetLedger delegate;
        ForwardingLedger(ProviderBudgetLedger delegate) { this.delegate = delegate; }
        public Snapshot approve(Approval value) { return delegate.approve(value); }
        public ChatReceipt recordChatIngress(String id, Scope scope, UUID attempt) { return delegate.recordChatIngress(id, scope, attempt); }
        public ReservationReceipt reserveAttempt(String id, Scope scope, UUID attempt, Usage usage) { return delegate.reserveAttempt(id, scope, attempt, usage); }
        public Attempt settle(String id, Scope scope, UUID attempt, Usage usage, String ref) { return delegate.settle(id, scope, attempt, usage, ref); }
        public Attempt markAmbiguous(String id, Scope scope, UUID attempt, String reason) { return delegate.markAmbiguous(id, scope, attempt, reason); }
        public Snapshot stop(String id, Scope scope, String reason) { return delegate.stop(id, scope, reason); }
        public Snapshot read(String id, Scope scope) { return delegate.read(id, scope); }
    }

    private record Reply(int status, String body, String location, CountDownLatch release, Map<String, String> headers, byte[] wireBody) {
        Reply(int status, String body, String location, CountDownLatch release) { this(status, body, location, release, Map.of(), null); }
    }
}
