package com.ai.fabric.budget.openai;

import ai.fabric.config.AIProviderConfig;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/** Test-only package access to the owned transport: every socket is loopback, never a provider. */
public final class LoopbackGuardedOpenAi implements AutoCloseable {
    private final HttpServer server;
    private final AtomicInteger sends = new AtomicInteger();
    private final BudgetGuardedSpringAiModelResolver resolver;

    public LoopbackGuardedOpenAi(AIProviderConfig config, ProviderBudgetLedger ledger, ProviderBudgetLedger.Approval approval,
        OpenAiBudgetInvocation.Capture capture, String content) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        server.createContext("/v1/chat/completions", exchange -> {
            sends.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            var json = new ObjectMapper();
            var reply = json.createObjectNode().put("id", "chat-loopback-goal").put("object", "chat.completion").put("created", 1)
                .put("model", "gpt-4o-mini-2024-07-18");
            var choice = reply.putArray("choices").addObject().put("index", 0).put("finish_reason", "stop");
            choice.putObject("message").put("role", "assistant").put("content", content);
            reply.putObject("usage").put("prompt_tokens", 12).put("completion_tokens", 3).put("total_tokens", 15);
            byte[] body = json.writeValueAsBytes(reply);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("x-request-id", "req_loopback_goal");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            finally { exchange.close(); }
        });
        server.start();
        var stops = new OpenAiMeteringStops(ledger);
        resolver = new BudgetGuardedSpringAiModelResolver(config, ObservationRegistry.NOOP, (key, timeout) -> {
            Duration bounded = timeout.compareTo(Duration.ofSeconds(5)) < 0 ? timeout : Duration.ofSeconds(5);
            var client = new OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
                .proxy(java.net.Proxy.NO_PROXY).callTimeout(bounded)
                .addNetworkInterceptor(new OpenAiExchangeGuard(ledger, credential -> new OpenAiBudgetScopeResolver.Binding(approval.id(), approval.scope()),
                    HttpUrl.get(endpoint), stops)).build();
            var transport = new NativeOpenAiHttpClient(client);
            var sdk = new OpenAIClientImpl(ClientOptions.builder().apiKey(key).baseUrl(endpoint).maxRetries(0).timeout(bounded).httpClient(transport).build());
            return new BudgetGuardedSpringAiModelResolver.RootConnection(sdk, transport);
        }, capture, BudgetGuardedSpringAiModelResolver.OutputCapMode.MAX_COMPLETION_TOKENS);
    }
    public BudgetGuardedSpringAiModelResolver resolver() { return resolver; }
    public int sends() { return sends.get(); }
    @Override public void close() { try { resolver.destroy(); } finally { server.stop(0); } }
}
