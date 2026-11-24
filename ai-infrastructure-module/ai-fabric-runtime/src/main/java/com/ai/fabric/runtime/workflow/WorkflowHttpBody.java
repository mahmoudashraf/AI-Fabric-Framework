package com.ai.fabric.runtime.workflow;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

final class WorkflowHttpBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    private Flow.Subscription subscription;
    public CompletionStage<byte[]> getBody() { return body; }
    public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
    public void onNext(List<ByteBuffer> buffers) {
        for (var buffer : buffers) {
            if (buffer.remaining() > 524288 - bytes.size()) { subscription.cancel(); body.completeExceptionally(new IllegalStateException("AUTHORING_TOOL_RESPONSE_LIMIT")); return; }
            byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }
    public void onError(Throwable failure) { body.completeExceptionally(failure); }
    public void onComplete() { body.complete(bytes.toByteArray()); }
}
