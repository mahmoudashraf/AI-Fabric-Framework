package com.ai.fabric.product.mcp.gateway.client;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** HTTP-only bridge: the maintained SDK still owns JSON-RPC, correlation, session and SSE parsing. */
public final class PinnedDnsHttpClient extends HttpClient implements AutoCloseable {
    private final McpGatewayProperties properties;
    private final PublicMcpAddressPolicy policy;
    private final SSLContext ssl;
    private final CloseableHttpClient apache;
    private final Scheduler io = Schedulers.newBoundedElastic(32, 64, "mcp-pinned-http");
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "mcp-pinned-http-deadline"); thread.setDaemon(true); return thread;
    });
    private final Set<Exchange> active = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public PinnedDnsHttpClient(McpGatewayProperties properties) {
        this(properties, new PublicMcpAddressPolicy(), org.apache.hc.core5.ssl.SSLContexts.createSystemDefault());
    }

    PinnedDnsHttpClient(McpGatewayProperties properties, PublicMcpAddressPolicy policy, SSLContext ssl) {
        this.properties = properties;
        this.policy = policy;
        this.ssl = ssl;
        this.apache = McpApacheHttp.create(properties, policy, ssl);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        CompletableFuture<HttpResponse<T>> result = new CompletableFuture<>();
        Exchange exchange = new Exchange(new HttpUriRequestBase(request.method(), request.uri()));
        active.add(exchange);
        result.whenComplete((ignored, failure) -> { if (result.isCancelled()) exchange.close(); });
        try {
            io.schedule(() -> execute(request, handler, result, exchange));
        } catch (RuntimeException rejected) {
            exchange.close(); result.completeExceptionally(rejected);
        }
        return result;
    }

    private <T> void execute(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                             CompletableFuture<HttpResponse<T>> result, Exchange exchange) {
        try {
            if (result.isCancelled()) return;
            policy.validateUri(request.uri());
            Duration timeout = request.timeout().orElse(properties.readTimeout());
            exchange.request.setConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.of(properties.connectTimeout()))
                .setResponseTimeout(Timeout.of(timeout)).setHardCancellationEnabled(true).build());
            request.headers().map().forEach((name, values) -> values.forEach(value -> exchange.request.addHeader(name, value)));
            if (request.bodyPublisher().isPresent()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                JdkFlowAdapter.flowPublisherToFlux(request.bodyPublisher().get()).doOnNext(buffer -> {
                    if ((long) bytes.size() + buffer.remaining() > properties.maxResponseBytes())
                        throw new IllegalArgumentException("MCP outbound request exceeds its size limit.");
                    byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
                }).then().block(timeout);
                exchange.request.setEntity(new ByteArrayEntity(bytes.toByteArray(), ContentType.APPLICATION_JSON));
            }
            var deadline = deadlines.schedule(() -> {
                result.completeExceptionally(new java.net.http.HttpTimeoutException("MCP HTTP exchange deadline exceeded."));
                exchange.close();
            }, timeout.toNanos(), TimeUnit.NANOSECONDS);
            exchange.deadline.set(deadline);
            if (exchange.closed.get()) { deadline.cancel(false); return; }
            ClassicHttpResponse response = apache.executeOpen(HttpHost.create(request.uri()), exchange.request, null);
            if (!exchange.install(response)) return;
            Map<String, List<String>> headers = new LinkedHashMap<>();
            for (var header : response.getHeaders()) headers.computeIfAbsent(header.getName(), ignored -> new ArrayList<>()).add(header.getValue());
            HttpHeaders httpHeaders = HttpHeaders.of(headers, (name, value) -> true);
            HttpResponse.ResponseInfo info = new ResponseInfo(response.getCode(), httpHeaders);
            HttpResponse.BodySubscriber<T> subscriber = handler.apply(info);
            subscriber.getBody().whenComplete((body, failure) -> {
                if (failure == null) result.complete(new Response<>(request, info.statusCode(), httpHeaders, body));
                else { result.completeExceptionally(failure); exchange.close(); }
            });
            InputStream input = response.getEntity() == null ? InputStream.nullInputStream() : response.getEntity().getContent();
            Flux<List<ByteBuffer>> chunks = Flux.<List<ByteBuffer>>generate(sink -> {
                try {
                    byte[] chunk = new byte[8192];
                    int size = input.read(chunk);
                    if (size < 0) sink.complete();
                    else sink.next(List.of(ByteBuffer.wrap(chunk, 0, size)));
                } catch (IOException failure) { sink.error(failure); }
            }).subscribeOn(io).doFinally(ignored -> exchange.close());
            JdkFlowAdapter.publisherToFlowPublisher(chunks).subscribe(subscriber);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
            exchange.close();
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                           HttpResponse.PushPromiseHandler<T> push) {
        return sendAsync(request, handler);
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        CompletableFuture<HttpResponse<T>> pending = sendAsync(request, handler);
        try { return pending.get(); }
        catch (InterruptedException interrupted) { pending.cancel(true); throw interrupted; }
        catch (java.util.concurrent.ExecutionException failure) { throw new IOException("MCP HTTP request failed.", failure.getCause()); }
    }

    @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
    @Override public Optional<Duration> connectTimeout() { return Optional.of(properties.connectTimeout()); }
    @Override public Redirect followRedirects() { return Redirect.NEVER; }
    @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
    @Override public SSLContext sslContext() { return ssl; }
    @Override public SSLParameters sslParameters() { return ssl.getDefaultSSLParameters(); }
    @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
    @Override public Version version() { return Version.HTTP_1_1; }
    @Override public Optional<Executor> executor() { return Optional.empty(); }

    @Override
    public void close() {
        active.forEach(Exchange::close);
        apache.close(CloseMode.IMMEDIATE);
        deadlines.shutdownNow();
        io.dispose();
    }

    private final class Exchange {
        private final HttpUriRequestBase request;
        private final AtomicReference<ClassicHttpResponse> response = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<java.util.concurrent.ScheduledFuture<?>> deadline = new AtomicReference<>();
        private Exchange(HttpUriRequestBase request) { this.request = request; }
        private boolean install(ClassicHttpResponse received) throws IOException {
            response.set(received);
            if (closed.get()) { received.close(); return false; }
            return true;
        }
        private void close() {
            if (!closed.compareAndSet(false, true)) return;
            request.cancel();
            ClassicHttpResponse received = response.getAndSet(null);
            if (received != null) try { received.close(); } catch (IOException ignored) { }
            var scheduled = deadline.get(); if (scheduled != null) scheduled.cancel(false);
            active.remove(this);
        }
    }

    private record ResponseInfo(int statusCode, HttpHeaders headers) implements HttpResponse.ResponseInfo {
        @Override public Version version() { return Version.HTTP_1_1; }
    }

    private record Response<T>(HttpRequest request, int statusCode, HttpHeaders headers, T body) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public Version version() { return Version.HTTP_1_1; }
    }
}
