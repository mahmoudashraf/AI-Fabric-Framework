package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger.Usage;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import static com.ai.fabric.budget.openai.OpenAiBudgetException.Code.*;
import static com.ai.fabric.budget.openai.ReviewedOpenAiRateCard.*;

/** Installed as a network interceptor: every exchange gets a new reservation, even follow-ups. */
final class OpenAiExchangeGuard implements Interceptor {
    static final String BASE_URL = "https://api.openai.com/v1";
    static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    private static final Set<String> CHAT_FIELDS = Set.of("model", "messages", "max_tokens", "max_completion_tokens",
        "n", "stream", "frequency_penalty", "presence_penalty", "logit_bias", "logprobs", "top_logprobs",
        "response_format", "seed", "stop", "temperature", "top_p", "user", "parallel_tool_calls", "store",
        "metadata", "service_tier", "prompt_cache_key", "tools", "tool_choice", "modalities");
    private static final Set<String> EMBEDDING_FIELDS = Set.of("model", "input", "dimensions", "encoding_format", "user");
    private final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final ProviderBudgetLedger ledger;
    private final OpenAiBudgetScopeResolver scopes;
    private final HttpUrl origin;
    private final OpenAiMeteringStops meteringStops;

    OpenAiExchangeGuard(ProviderBudgetLedger ledger, OpenAiBudgetScopeResolver scopes) {
        this(ledger, scopes, HttpUrl.get(BASE_URL), new OpenAiMeteringStops(ledger));
    }

    // Package-private loopback fixture construction; the public resolver never accepts a transport or endpoint.
    OpenAiExchangeGuard(ProviderBudgetLedger ledger, OpenAiBudgetScopeResolver scopes, HttpUrl origin) {
        this(ledger, scopes, origin, new OpenAiMeteringStops(ledger));
    }

    OpenAiExchangeGuard(ProviderBudgetLedger ledger, OpenAiBudgetScopeResolver scopes, HttpUrl origin,
        OpenAiMeteringStops meteringStops) {
        this.ledger = java.util.Objects.requireNonNull(ledger);
        this.scopes = java.util.Objects.requireNonNull(scopes);
        this.origin = origin;
        this.meteringStops = java.util.Objects.requireNonNull(meteringStops);
    }

    @Override public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Plan plan;
        OpenAiBudgetScopeResolver.Binding binding = null;
        UUID attemptId = UUID.randomUUID();
        try {
            plan = plan(request);
            binding = binding(request);
            meteringStops.requireUnbreached(binding);
            var receipt = ledger.reserveAttempt(binding.approvalId(), binding.scope(), attemptId, plan.upperBound());
            if (receipt == null || !receipt.newlyReserved()) throw new OpenAiBudgetException(ATTEMPT_REPLAY);
        } catch (RuntimeException denied) {
            OpenAiBudgetTransportException failure = binding != null && meteringStops.contains(binding)
                ? meteringFailure(attemptId, denied) : sanitized(attemptId, denied);
            observe(request, failure);
            throw failure;
        }

        Response response = null;
        String requestEvidence = "native-json-usage:" + attemptId;
        try {
            request = request.newBuilder().header("X-Client-Request-Id", attemptId.toString()).build();
            response = chain.proceed(request);
            requestEvidence = evidenceReference(response, attemptId);
            if (!response.isSuccessful() || response.body() == null
                || (response.header("Content-Encoding") != null && !"identity".equalsIgnoreCase(response.header("Content-Encoding")))
                || !"application/json".equals(response.body().contentType() == null ? null
                    : response.body().contentType().type() + "/" + response.body().contentType().subtype())) {
                throw new OpenAiBudgetException(ATTEMPT_AMBIGUOUS);
            }
            byte[] bytes = response.body().byteStream().readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) throw new OpenAiBudgetException(ATTEMPT_AMBIGUOUS);
            Usage actual = actual(plan, json.readTree(bytes));
            Response result = response.newBuilder().body(ResponseBody.create(bytes, response.body().contentType())).build();
            response.close();
            response = null;
            // Successful settlement is performed once, from the native body, before the SDK sees it.
            ledger.settle(binding.approvalId(), binding.scope(), attemptId, actual, requestEvidence);
            return result;
        } catch (Exception unknown) {
            if (response != null) {
                try { response.close(); } catch (RuntimeException ignored) { /* Preserve the original unknown outcome. */ }
            }
            boolean breach = unknown instanceof OpenAiBudgetException failure && failure.code() == METERING_BOUND_BREACH;
            RuntimeException stopFailure = null;
            if (breach) {
                try { meteringStops.stop(binding); }
                catch (RuntimeException unavailable) { stopFailure = unavailable; }
            }
            try {
                ledger.markAmbiguous(binding.approvalId(), binding.scope(), attemptId,
                    (breach ? "METERING_BOUND_BREACH:" : "NATIVE_EXCHANGE_UNVERIFIED:") + requestEvidence);
            } catch (RuntimeException unavailable) {
                // RESERVED already holds the complete bound if the ambiguity update cannot commit.
            }
            var failure = breach ? meteringFailure(attemptId, stopFailure)
                : new OpenAiBudgetTransportException(attemptId, null, ATTEMPT_AMBIGUOUS);
            observe(request, failure);
            throw failure;
        }
    }

    private static void observe(Request request, OpenAiBudgetTransportException failure) {
        OpenAiBudgetInvocation invocation = request.tag(OpenAiBudgetInvocation.class);
        if (invocation != null) invocation.denied(failure);
    }

    private static String evidenceReference(Response response, UUID attemptId) {
        var values = response.headers("x-request-id");
        if (values.size() == 1 && values.getFirst().length() <= 128 && values.getFirst().matches("[A-Za-z0-9._:-]+")) {
            return "openai-request:" + values.getFirst() + ":client-attempt:" + attemptId;
        }
        return "native-json-usage:" + attemptId;
    }

    Plan plan(Request request) throws IOException {
        HttpUrl url = request.url();
        if (!"POST".equals(request.method()) || !url.scheme().equals(origin.scheme()) || !url.host().equals(origin.host())
            || url.port() != origin.port() || !url.username().isEmpty() || !url.password().isEmpty()
            || url.query() != null || url.fragment() != null || request.body() == null) throw unsupported();
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        if (buffer.size() > MAX_BODY_BYTES) throw unsupported();
        JsonNode body;
        try { body = json.readTree(buffer.readByteArray()); }
        catch (Exception invalid) { throw unsupported(); }
        if (body == null || !body.isObject()) throw unsupported();
        String prefix = origin.encodedPath().replaceAll("/$", "");
        if ((prefix + "/chat/completions").equals(url.encodedPath())) return chatPlan(body);
        if ((prefix + "/embeddings").equals(url.encodedPath())) return embeddingPlan(body);
        throw unsupported();
    }

    private Plan chatPlan(JsonNode body) {
        fields(body, CHAT_FIELDS);
        if (!CHAT_MODEL.equals(body.path("model").asText()) || (body.has("stream") && !body.get("stream").isBoolean())
            || body.path("stream").asBoolean() || (body.has("n") && integer(body.get("n")) != 1)
            || !"default".equals(body.path("service_tier").asText())) throw unsupported();
        if (body.has("modalities") && (!body.get("modalities").isArray() || body.get("modalities").size() != 1
            || !"text".equals(body.get("modalities").get(0).asText()))) throw unsupported();
        if (body.has("max_tokens") || !body.has("max_completion_tokens"))
            throw new OpenAiBudgetException(UNSUPPORTED_OUTPUT_LIMIT_MODE);
        long output = integer(body.get("max_completion_tokens"));
        if (output < 1 || output > CHAT_MAX_OUTPUT_TOKENS) throw unsupported();
        var messages = body.path("messages");
        if (!messages.isArray() || messages.isEmpty()) throw unsupported();
        for (JsonNode message : messages) {
            if (!message.isObject()) throw unsupported();
            fields(message, Set.of("role", "content", "name", "tool_calls", "tool_call_id", "refusal"));
            if (!Set.of("system", "developer", "user", "assistant", "tool").contains(message.path("role").asText())) throw unsupported();
            JsonNode content = message.get("content");
            if (content != null && !content.isNull() && !content.isTextual()) {
                if (!content.isArray()) throw unsupported();
                for (JsonNode part : content) {
                    fields(part, Set.of("type", "text"));
                    if (!"text".equals(part.path("type").asText()) || !part.path("text").isTextual()) throw unsupported();
                }
            }
            if (message.has("tool_calls")) functionTools(message.get("tool_calls"));
        }
        if (body.has("tools")) functionTools(body.get("tools"));
        // The ledger has component-wise bounds; this deliberately overholds the shared context envelope.
        return new Plan(true, output, chat(CHAT_CONTEXT_TOKENS, output));
    }

    private Plan embeddingPlan(JsonNode body) {
        fields(body, EMBEDDING_FIELDS);
        if (!EMBEDDING_MODEL.equals(body.path("model").asText())) throw unsupported();
        JsonNode input = body.path("input");
        long bound;
        if (input.isTextual() && !input.textValue().isEmpty()) bound = EMBEDDING_INPUT_TOKENS;
        else if (input.isArray() && !input.isEmpty()) {
            if (input.get(0).isIntegralNumber()) bound = tokenArray(input);
            else if (input.get(0).isArray()) {
                if (input.size() > 2048) throw unsupported();
                long total = 0;
                for (JsonNode item : input) total = Math.addExact(total, tokenArray(item));
                if (total > EMBEDDING_REQUEST_TOKENS) throw unsupported();
                bound = total;
            } else {
                if (input.size() > 2048) throw unsupported();
                for (JsonNode item : input) if (!item.isTextual() || item.textValue().isEmpty()) throw unsupported();
                bound = Math.min(Math.multiplyExact(input.size(), EMBEDDING_INPUT_TOKENS), EMBEDDING_REQUEST_TOKENS);
            }
        } else throw unsupported();
        return new Plan(false, 0, embedding(bound));
    }

    private static long tokenArray(JsonNode input) {
        if (!input.isArray() || input.isEmpty() || input.size() > EMBEDDING_INPUT_TOKENS) throw unsupported();
        for (JsonNode token : input) if (integer(token) > Integer.MAX_VALUE) throw unsupported();
        return input.size();
    }

    private OpenAiBudgetScopeResolver.Binding binding(Request request) {
        var headers = request.headers("Authorization");
        if (headers.size() != 1 || !headers.getFirst().startsWith("Bearer ")) throw new OpenAiBudgetException(CREDENTIAL_SCOPE_MISMATCH);
        String identity = credentialIdentity(headers.getFirst().substring(7));
        var binding = scopes.requireBinding(identity);
        if (binding == null || !identity.equals(binding.scope().credentialIdentity())) throw new OpenAiBudgetException(CREDENTIAL_SCOPE_MISMATCH);
        if (!REFERENCE.equals(binding.scope().rateCardReference())) throw new OpenAiBudgetException(RATE_CARD_MISMATCH);
        return binding;
    }

    private Usage actual(Plan plan, JsonNode response) {
        if (response == null || !response.isObject() || response.has("error")) throw new OpenAiBudgetException(ATTEMPT_AMBIGUOUS);
        String model = response.path("model").asText();
        if (plan.chat() ? !Set.of(CHAT_MODEL, "gpt-4o-mini-2024-07-18").contains(model) : !EMBEDDING_MODEL.equals(model))
            throw new OpenAiBudgetException(ATTEMPT_AMBIGUOUS);
        var usage = response.path("usage");
        if (!usage.isObject()) throw new OpenAiBudgetException(ATTEMPT_AMBIGUOUS);
        long input = integer(usage.get("prompt_tokens"));
        long output = plan.chat() ? integer(usage.get("completion_tokens")) : 0;
        long total = integer(usage.get("total_tokens"));
        if (Math.addExact(input, output) != total) throw new OpenAiBudgetException(ATTEMPT_AMBIGUOUS);
        if (input > plan.upperBound().inputTokens() || output > plan.outputCap() || (plan.chat() && total > CHAT_CONTEXT_TOKENS))
            throw new OpenAiBudgetException(METERING_BOUND_BREACH);
        if (response.has("service_tier") && (!response.get("service_tier").isTextual()
            || !"default".equals(response.get("service_tier").textValue())))
            throw new OpenAiBudgetException(ATTEMPT_AMBIGUOUS);
        return plan.chat() ? chat(input, output) : embedding(input);
    }

    private static void functionTools(JsonNode tools) {
        if (!tools.isArray()) throw unsupported();
        for (JsonNode tool : tools) {
            if (!tool.isObject() || !"function".equals(tool.path("type").asText()) || !tool.path("function").isObject()) throw unsupported();
        }
    }

    private static void fields(JsonNode body, Set<String> allowed) {
        if (!body.isObject()) throw unsupported();
        body.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw unsupported(); });
    }

    private static long integer(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) throw unsupported();
        return node.longValue();
    }

    private static OpenAiBudgetException unsupported() { return new OpenAiBudgetException(UNSUPPORTED_REQUEST); }

    private static OpenAiBudgetTransportException sanitized(UUID attemptId, RuntimeException error) {
        if (error instanceof BudgetException budget) return new OpenAiBudgetTransportException(attemptId, budget.code(), null);
        return new OpenAiBudgetTransportException(attemptId, null,
            error instanceof OpenAiBudgetException guard ? guard.code() : UNSUPPORTED_CONFIGURATION);
    }

    private static OpenAiBudgetTransportException meteringFailure(UUID attemptId, RuntimeException stopFailure) {
        BudgetException.Code code = stopFailure == null ? null : stopFailure instanceof BudgetException budget
            ? budget.code() : BudgetException.Code.STORAGE_UNAVAILABLE;
        return new OpenAiBudgetTransportException(attemptId, code, METERING_BOUND_BREACH);
    }

    record Plan(boolean chat, long outputCap, Usage upperBound) { }
}
