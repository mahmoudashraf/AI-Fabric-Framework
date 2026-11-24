package com.ai.fabric.platform.backend.assistant;

import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;

/** Bound both the response bytes and the entire body-delivery time, not only response headers. */
final class AuthoringHttp {
    private AuthoringHttp() { }
    static HttpResponse<byte[]> send(HttpClient http, HttpRequest request, Instant deadline, int limit) throws Exception {
        var pending = http.sendAsync(request, ignored -> new LimitedBody(limit));
        try {
            long remaining = Duration.between(Instant.now(), deadline).toNanos();
            if (remaining <= 0) throw new TimeoutException("AUTHORING_TIMEOUT");
            return pending.get(remaining, TimeUnit.NANOSECONDS);
        } finally { if (!pending.isDone()) pending.cancel(true); }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private final int limit; private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) { subscription.cancel(); body.completeExceptionally(new IllegalStateException("AUTHORING_RESPONSE_LIMIT")); return; }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable failure) { body.completeExceptionally(failure); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
