package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.ProviderBudgetLedger;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;
import com.openai.errors.OpenAIIoException;
import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Complete native SDK HTTP SPI, with the budget guard below SDK retries and HTTP follow-ups. */
final class NativeOpenAiHttpClient implements HttpClient {
    private final OkHttpClient transport;
    private final Duration maximumTimeout;
    private final AtomicBoolean closed = new AtomicBoolean();

    NativeOpenAiHttpClient(ProviderBudgetLedger ledger, OpenAiBudgetScopeResolver scopes, Duration timeout) {
        this(ledger, scopes, timeout, new OpenAiMeteringStops(ledger));
    }

    NativeOpenAiHttpClient(ProviderBudgetLedger ledger, OpenAiBudgetScopeResolver scopes, Duration timeout,
        OpenAiMeteringStops meteringStops) {
        this(new OkHttpClient.Builder()
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE).proxy(Proxy.NO_PROXY)
            .cache(null).connectTimeout(timeout).readTimeout(timeout).writeTimeout(timeout).callTimeout(timeout)
            .addNetworkInterceptor(new OpenAiExchangeGuard(ledger, scopes, okhttp3.HttpUrl.get(OpenAiExchangeGuard.BASE_URL), meteringStops)).build());
    }

    // Test-only package hook; no public factory/property permits replacing the guarded production transport.
    NativeOpenAiHttpClient(OkHttpClient transport) {
        this.transport = transport;
        if (transport.callTimeoutMillis() <= 0) throw new OpenAiBudgetException(OpenAiBudgetException.Code.UNSUPPORTED_CONFIGURATION);
        this.maximumTimeout = Duration.ofMillis(transport.callTimeoutMillis());
    }

    @Override public HttpResponse execute(HttpRequest request, RequestOptions options) {
        return execute(request, options, OpenAiBudgetInvocation.NONE);
    }

    HttpResponse execute(HttpRequest request, RequestOptions options, OpenAiBudgetInvocation invocation) {
        try {
            return response(newCall(request, options, invocation).execute());
        } catch (OpenAiBudgetTransportException error) {
            if (denial(error)) throw new OpenAiBudgetDeniedException(error);
            throw new OpenAIIoException("GUARDED_OPENAI_EXCHANGE_FAILED", error);
        } catch (IOException error) {
            throw new OpenAIIoException("GUARDED_OPENAI_EXCHANGE_FAILED", error);
        } catch (RuntimeException error) {
            throw error instanceof OpenAiBudgetException ? error : new OpenAiBudgetException(OpenAiBudgetException.Code.UNSUPPORTED_CONFIGURATION);
        } finally {
            if (request.body() != null) request.body().close();
        }
    }

    @Override public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
        return executeAsync(request, options, OpenAiBudgetInvocation.NONE);
    }

    CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options, OpenAiBudgetInvocation invocation) {
        var future = new CompletableFuture<HttpResponse>();
        final Call call;
        try { call = newCall(request, options, invocation); }
        catch (IOException | RuntimeException error) {
            if (request.body() != null) request.body().close();
            future.completeExceptionally(error instanceof OpenAiBudgetException ? error
                : new OpenAiBudgetException(OpenAiBudgetException.Code.UNSUPPORTED_CONFIGURATION));
            return future;
        }
        future.whenComplete((unused, error) -> {
            if (future.isCancelled()) call.cancel();
            if (request.body() != null) request.body().close();
        });
        call.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) {
                future.completeExceptionally(error instanceof OpenAiBudgetTransportException failure && denial(failure)
                    ? new OpenAiBudgetDeniedException(failure) : new OpenAIIoException("GUARDED_OPENAI_EXCHANGE_FAILED", error));
            }
            @Override public void onResponse(Call call, Response response) {
                HttpResponse wrapped = response(response);
                if (!future.complete(wrapped)) wrapped.close();
            }
        });
        return future;
    }

    private static boolean denial(OpenAiBudgetTransportException failure) {
        return failure.budgetCode().isPresent() || failure.guardCode().orElse(null) != OpenAiBudgetException.Code.ATTEMPT_AMBIGUOUS;
    }

    private Call newCall(HttpRequest request, RequestOptions options, OpenAiBudgetInvocation invocation) throws IOException {
        if (invocation == null) throw new OpenAiBudgetException(OpenAiBudgetException.Code.INVOCATION_CONTEXT_REQUIRED);
        if (closed.get()) throw new OpenAiBudgetException(OpenAiBudgetException.Code.UNSUPPORTED_CONFIGURATION);
        if (request.body() == null || !"POST".equals(request.method().name())) throw new IOException("UNSUPPORTED_REQUEST");
        var bytes = new ByteArrayOutputStream() {
            @Override public synchronized void write(byte[] value, int offset, int length) {
                limit(length); super.write(value, offset, length);
            }
            @Override public synchronized void write(int value) { limit(1); super.write(value); }
            private void limit(int extra) {
                if (extra > OpenAiExchangeGuard.MAX_BODY_BYTES - size()) {
                    throw new OpenAiBudgetException(OpenAiBudgetException.Code.UNSUPPORTED_REQUEST);
                }
            }
        };
        request.body().writeTo(bytes);
        String contentType = request.body().contentType();
        if (contentType == null || !contentType.startsWith("application/json")) throw new IOException("UNSUPPORTED_REQUEST");
        Request.Builder builder = new Request.Builder().url(request.url()).post(RequestBody.create(bytes.toByteArray(), MediaType.get(contentType)));
        request.headers().names().forEach(name -> request.headers().values(name).forEach(value -> builder.addHeader(name, value)));
        builder.header("Accept-Encoding", "identity");
        builder.tag(OpenAiBudgetInvocation.class, invocation);
        OkHttpClient client = transport;
        if (options.getTimeout() != null) {
            var timeout = options.getTimeout();
            client = transport.newBuilder().connectTimeout(bounded(timeout.connect(), transport.connectTimeoutMillis()))
                .readTimeout(bounded(timeout.read(), transport.readTimeoutMillis()))
                .writeTimeout(bounded(timeout.write(), transport.writeTimeoutMillis()))
                .callTimeout(bounded(timeout.request(), transport.callTimeoutMillis())).build();
        }
        return client.newCall(builder.build());
    }

    private Duration bounded(Duration requested, int configuredMillis) {
        Duration configured = configuredMillis > 0 ? Duration.ofMillis(configuredMillis) : maximumTimeout;
        if (configured.compareTo(maximumTimeout) > 0) configured = maximumTimeout;
        if (requested == null || requested.isZero() || requested.isNegative() || requested.compareTo(configured) > 0) return configured;
        return requested.toMillis() < 1 ? Duration.ofMillis(1) : requested;
    }

    private static HttpResponse response(Response response) {
        Headers.Builder headers = Headers.builder();
        response.headers().names().forEach(name -> response.headers(name).forEach(value -> headers.put(name, value)));
        Headers result = headers.build();
        InputStream body = response.body() == null ? InputStream.nullInputStream() : response.body().byteStream();
        return new HttpResponse() {
            @Override public int statusCode() { return response.code(); }
            @Override public Headers headers() { return result; }
            @Override public InputStream body() { return body; }
            @Override public void close() { response.close(); }
        };
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        transport.dispatcher().cancelAll();
        transport.dispatcher().executorService().shutdown();
        transport.connectionPool().evictAll();
    }
}
