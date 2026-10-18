package com.ai.fabric.budget.openai;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.dto.AIEmbeddingRequest;
import ai.fabric.dto.AIGenerationRequest;
import ai.fabric.provider.ProviderConfig;
import ai.fabric.provider.ProviderRequestOverrideSupport;
import ai.fabric.provider.springai.SpringAiModelResolver;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ai.fabric.budget.openai.OpenAiBudgetException.Code.*;
import static com.ai.fabric.budget.openai.ReviewedOpenAiRateCard.*;

/** Explicit opt-in replacement SPI. This module does not register a bean or change framework configuration. */
public final class BudgetGuardedSpringAiModelResolver extends SpringAiModelResolver {
    public enum OutputCapMode { PRESERVE_FRAMEWORK, MAX_COMPLETION_TOKENS }

    private final AIProviderConfig config;
    private final ObservationRegistry observations;
    private final NativeClientFactory clients;
    private final OpenAiBudgetInvocation.Capture invocations;
    private final OutputCapMode outputCapMode;
    private final Map<String, RootConnection> connections = new ConcurrentHashMap<>();
    private boolean destroyed;

    public BudgetGuardedSpringAiModelResolver(AIProviderConfig config, ObservationRegistry observations,
        ProviderBudgetLedger ledger, OpenAiBudgetScopeResolver scopes) {
        this(config, observations, ledger, scopes, operation -> OpenAiBudgetInvocation.NONE);
    }

    public BudgetGuardedSpringAiModelResolver(AIProviderConfig config, ObservationRegistry observations,
        ProviderBudgetLedger ledger, OpenAiBudgetScopeResolver scopes, OpenAiBudgetInvocation.Capture invocations) {
        this(config, observations, ledger, scopes, invocations, OutputCapMode.PRESERVE_FRAMEWORK);
    }

    /** The host must explicitly select and record this structural cap mode in its guarded profile. */
    public BudgetGuardedSpringAiModelResolver(AIProviderConfig config, ObservationRegistry observations,
        ProviderBudgetLedger ledger, OpenAiBudgetScopeResolver scopes, OpenAiBudgetInvocation.Capture invocations,
        OutputCapMode outputCapMode) {
        this(config, observations, guardedClients(ledger, scopes), invocations, outputCapMode);
    }

    private static NativeClientFactory guardedClients(ProviderBudgetLedger ledger, OpenAiBudgetScopeResolver scopes) {
        var meteringStops = new OpenAiMeteringStops(ledger);
        return (apiKey, timeout) -> {
            var transport = new NativeOpenAiHttpClient(ledger, scopes, timeout, meteringStops);
            try {
                var client = new OpenAIClientImpl(ClientOptions.builder().apiKey(apiKey)
                    .baseUrl(OpenAiExchangeGuard.BASE_URL).timeout(timeout).maxRetries(0).httpClient(transport).build());
                return new RootConnection(client, transport);
            } catch (RuntimeException invalid) { transport.close(); throw invalid; }
        };
    }

    // Test-only injection. The public constructor always installs the owned network transport.
    BudgetGuardedSpringAiModelResolver(AIProviderConfig config, ObservationRegistry observations, NativeClientFactory clients) {
        this(config, observations, clients, operation -> OpenAiBudgetInvocation.NONE);
    }

    BudgetGuardedSpringAiModelResolver(AIProviderConfig config, ObservationRegistry observations, NativeClientFactory clients,
        OpenAiBudgetInvocation.Capture invocations) {
        this(config, observations, clients, invocations, OutputCapMode.PRESERVE_FRAMEWORK);
    }

    BudgetGuardedSpringAiModelResolver(AIProviderConfig config, ObservationRegistry observations, NativeClientFactory clients,
        OpenAiBudgetInvocation.Capture invocations, OutputCapMode outputCapMode) {
        super(config, observations);
        this.config = java.util.Objects.requireNonNull(config);
        this.observations = observations == null ? ObservationRegistry.NOOP : observations;
        this.clients = java.util.Objects.requireNonNull(clients);
        this.invocations = java.util.Objects.requireNonNull(invocations);
        this.outputCapMode = java.util.Objects.requireNonNull(outputCapMode);
        configuration();
    }

    @Override public ChatModel resolveChatModel(String provider, AIGenerationRequest request) {
        requireOpenAi(provider); configuration();
        OpenAiChatOptions options = (OpenAiChatOptions) resolveChatOptions(provider, request);
        var override = override(request == null ? null : request.getParameters());
        String key = first(override.apiKey(), config.getOpenai().getApiKey());
        endpoint(first(override.baseUrl(), config.getOpenai().getBaseUrl(), OpenAiExchangeGuard.BASE_URL));
        OpenAiBudgetInvocation invocation = capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION);
        OpenAIClient client = boundClient(connection(key), invocation);
        return OpenAiChatModel.builder().openAiClient(client).openAiClientAsync(client.async())
            .options(options).observationRegistry(observations).build();
    }

    @Override public ChatOptions resolveChatOptions(String provider, AIGenerationRequest request) {
        requireOpenAi(provider); configuration();
        override(request == null ? null : request.getParameters());
        // Keep the framework's request overrides, token selection and native structured JSON options intact.
        OpenAiChatOptions options = (OpenAiChatOptions) super.resolveChatOptions(provider, request);
        if (!CHAT_MODEL.equals(options.getModel()) || options.getMaxTokens() == null || options.getMaxTokens() < 1
            || options.getMaxTokens() > CHAT_MAX_OUTPUT_TOKENS) throw new OpenAiBudgetException(UNSUPPORTED_CONFIGURATION);
        var guarded = options.mutate().n(1).serviceTier("default");
        if (outputCapMode == OutputCapMode.MAX_COMPLETION_TOKENS) {
            // Explicit host opt-in moves the same effective cap using only the public native option API.
            guarded.maxTokens(null).maxCompletionTokens(options.getMaxTokens());
        }
        return guarded.build();
    }

    @Override public EmbeddingModel resolveEmbeddingModel(String provider, AIEmbeddingRequest request) {
        requireOpenAi(provider); configuration();
        OpenAiEmbeddingOptions options = (OpenAiEmbeddingOptions) resolveEmbeddingOptions(provider, request);
        var override = override(request == null ? null : request.getParameters());
        String key = first(override.apiKey(), config.getEmbeddingApiKey(), config.getOpenai().getEmbeddingApiKey(), config.getOpenai().getApiKey());
        endpoint(first(override.baseUrl(), config.getEmbeddingBaseUrl(), config.getOpenai().getEmbeddingBaseUrl(),
            config.getOpenai().getBaseUrl(), OpenAiExchangeGuard.BASE_URL));
        OpenAiBudgetInvocation invocation = capture(OpenAiBudgetInvocation.Operation.EMBEDDING);
        OpenAIClient client = boundClient(connection(key), invocation);
        return OpenAiEmbeddingModel.builder().openAiClient(client).options(options).observationRegistry(observations).build();
    }

    @Override public EmbeddingOptions resolveEmbeddingOptions(String provider, AIEmbeddingRequest request) {
        requireOpenAi(provider); configuration();
        override(request == null ? null : request.getParameters());
        OpenAiEmbeddingOptions options = (OpenAiEmbeddingOptions) super.resolveEmbeddingOptions(provider, request);
        if (!EMBEDDING_MODEL.equals(options.getModel()) || (options.getDimensions() != null
            && (options.getDimensions() < 1 || options.getDimensions() > 1536))) throw new OpenAiBudgetException(UNSUPPORTED_CONFIGURATION);
        return options;
    }

    @Override public boolean isChatAvailable(String provider) { return isChatAvailable(provider, null); }
    @Override public boolean isChatAvailable(String provider, AIGenerationRequest request) {
        configuration();
        if (!openAi(provider)) return false;
        resolveChatOptions(provider, request);
        var override = override(request == null ? null : request.getParameters());
        endpoint(first(override.baseUrl(), config.getOpenai().getBaseUrl(), OpenAiExchangeGuard.BASE_URL));
        return super.isChatAvailable(provider, request);
    }

    @Override public boolean isEmbeddingAvailable(String provider) { return isEmbeddingAvailable(provider, null); }
    @Override public boolean isEmbeddingAvailable(String provider, AIEmbeddingRequest request) {
        configuration();
        if (!openAi(provider)) return false;
        resolveEmbeddingOptions(provider, request);
        var override = override(request == null ? null : request.getParameters());
        endpoint(first(override.baseUrl(), config.getEmbeddingBaseUrl(), config.getOpenai().getEmbeddingBaseUrl(),
            config.getOpenai().getBaseUrl(), OpenAiExchangeGuard.BASE_URL));
        return super.isEmbeddingAvailable(provider, request);
    }

    @Override public boolean supportsEmbedding(String provider) { configuration(); return openAi(provider); }
    @Override public ProviderConfig providerConfig(String provider) { requireOpenAi(provider); configuration(); return super.providerConfig(provider); }
    @Override public int embeddingDimension(String provider) {
        requireOpenAi(provider); resolveEmbeddingOptions(provider, null);
        Integer dimensions = config.getOpenai().getEmbeddingDimensions();
        return dimensions == null ? 1536 : dimensions;
    }

    private OpenAiBudgetInvocation capture(OpenAiBudgetInvocation.Operation operation) {
        OpenAiBudgetInvocation invocation = invocations.capture(operation);
        if (invocation == null) throw new OpenAiBudgetException(INVOCATION_CONTEXT_REQUIRED);
        return invocation;
    }

    private static OpenAIClient boundClient(RootConnection root, OpenAiBudgetInvocation invocation) {
        return root.client().withOptions(options -> options.httpClient(new BoundHttpClient(root.transport(), invocation)));
    }

    private synchronized RootConnection connection(String apiKey) {
        if (destroyed) throw new OpenAiBudgetException(UNSUPPORTED_CONFIGURATION);
        String identity = credentialIdentity(apiKey);
        int seconds = config.getOpenai().getTimeout() == null || config.getOpenai().getTimeout() < 1 ? 60 : config.getOpenai().getTimeout();
        return connections.computeIfAbsent(identity + ":" + seconds, ignored -> clients.create(apiKey, Duration.ofSeconds(seconds)));
    }

    private void configuration() {
        if (!config.isEnabled() || !config.getOpenai().isEnabled() || config.getAzure().isEnabled()
            || config.getAnthropic().isEnabled() || config.getGemini().isEnabled() || config.getCohere().isEnabled())
            throw new OpenAiBudgetException(UNSUPPORTED_PROVIDER);
        requireOpenAi(config.getLlmProvider());
        if (config.getOrchestration() != null && first(config.getOrchestration().getLlmProvider()) != null)
            requireOpenAi(config.getOrchestration().getLlmProvider());
        if (config.getGeneration() != null && first(config.getGeneration().getLlmProvider()) != null)
            requireOpenAi(config.getGeneration().getLlmProvider());
        String embedding = first(config.getEmbeddingProvider());
        if (embedding != null && !LOCAL_EMBEDDING_PROVIDERS.contains(embedding) && !openAi(embedding))
            throw new OpenAiBudgetException(UNSUPPORTED_PROVIDER);
        endpoint(first(config.getOpenai().getBaseUrl(), OpenAiExchangeGuard.BASE_URL));
        endpoint(first(config.getEmbeddingBaseUrl(), config.getOpenai().getEmbeddingBaseUrl(), config.getOpenai().getBaseUrl(), OpenAiExchangeGuard.BASE_URL));
    }

    private static final java.util.Set<String> LOCAL_EMBEDDING_PROVIDERS = java.util.Set.of("onnx", "spring-ai-onnx");

    private static ProviderRequestOverrideSupport.LlmConnectionOverride override(Map<String, Object> parameters) {
        var value = ProviderRequestOverrideSupport.read(parameters);
        if (value.apiVersion() != null || value.deploymentName() != null) throw new OpenAiBudgetException(UNSUPPORTED_CONFIGURATION);
        if (value.baseUrl() != null) endpoint(value.baseUrl());
        return value;
    }

    private static void endpoint(String endpoint) {
        if (!OpenAiExchangeGuard.BASE_URL.equals(endpoint) && !(OpenAiExchangeGuard.BASE_URL + "/").equals(endpoint))
            throw new OpenAiBudgetException(UNSUPPORTED_CONFIGURATION);
    }

    private static void requireOpenAi(String provider) { if (!openAi(provider)) throw new OpenAiBudgetException(UNSUPPORTED_PROVIDER); }
    private static boolean openAi(String provider) { return provider != null && "openai".equalsIgnoreCase(provider.trim()); }
    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    @Override public synchronized void destroy() {
        if (destroyed) return;
        destroyed = true;
        connections.values().forEach(connection -> connection.client().close());
        connections.clear();
        super.destroy();
    }

    @FunctionalInterface interface NativeClientFactory { RootConnection create(String apiKey, Duration timeout); }
    record RootConnection(OpenAIClient client, NativeOpenAiHttpClient transport) { }
}
