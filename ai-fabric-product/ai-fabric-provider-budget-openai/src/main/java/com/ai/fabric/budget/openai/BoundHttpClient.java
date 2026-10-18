package com.ai.fabric.budget.openai;

import com.openai.core.RequestOptions;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Per-invocation native SDK transport view. Only the root connection owns the shared transport. */
final class BoundHttpClient implements HttpClient {
    private final NativeOpenAiHttpClient shared;
    private final OpenAiBudgetInvocation invocation;

    BoundHttpClient(NativeOpenAiHttpClient shared, OpenAiBudgetInvocation invocation) {
        this.shared = Objects.requireNonNull(shared);
        this.invocation = Objects.requireNonNull(invocation);
    }

    @Override public HttpResponse execute(HttpRequest request, RequestOptions options) {
        return shared.execute(request, options, invocation);
    }

    @Override public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
        return shared.executeAsync(request, options, invocation);
    }

    @Override public void close() { /* Borrowed: the resolver closes the root transport. */ }
}
