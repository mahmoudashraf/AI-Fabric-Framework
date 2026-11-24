package com.ai.fabric.runtime.evaluation;

import ai.fabric.rag.evaluation.RagEvaluationResult;
import ai.fabric.rag.evaluation.RagEvaluationSample;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Generic scorer for already-authorized samples. Child processes never inherit identity, keys or provider endpoints. */
@Component
public final class RagasWorker {
    public static final String RUBRIC = "ragas-0.4.4-v1";
    public static final int MAX_CALLS = 24;
    private static final int MAX_FRAME = 1_048_576;
    private final ObjectMapper mapper;
    private final Path python;
    private final Path script;
    private final Semaphore capacity = new Semaphore(1);
    private final java.util.concurrent.atomic.AtomicReference<Process> active = new java.util.concurrent.atomic.AtomicReference<>();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("ragas-process-deadline").daemon().factory());

    public RagasWorker(ObjectMapper mapper,
        @Value("${loomai.documents.ragas.python:/opt/loomai-ragas/.venv/bin/python}") String python,
        @Value("${loomai.documents.ragas.script:/opt/loomai-ragas/worker.py}") String script) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.python = absolute(python); this.script = absolute(script);
    }

    public interface Inference {
        JsonNode generate(String metricPrompt, JsonNode responseSchema);
        List<Double> embed(String text);
    }

    public RagEvaluationResult evaluate(RagEvaluationSample sample, Inference inference, Duration timeout) {
        if (sample == null || inference == null || timeout == null || timeout.isNegative()
            || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(120)) > 0) throw new IllegalArgumentException("A bounded evaluation is required.");
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (!capacity.tryAcquire()) throw new IllegalStateException("RAGAS_CAPACITY_BUSY");
        Process process = null;
        java.util.concurrent.ScheduledFuture<?> deadline = null;
        try {
            var builder = new ProcessBuilder(python.toString(), "-I", "-B", script.toString());
            builder.environment().clear();
            builder.environment().putAll(Map.of("LANG", "C.UTF-8", "RAGAS_DO_NOT_TRACK", "true",
                "HF_HUB_OFFLINE", "1", "HF_DATASETS_OFFLINE", "1", "OPENBLAS_NUM_THREADS", "1", "OMP_NUM_THREADS", "1"));
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            active.set(process);
            var running = process;
            deadline = deadlines.schedule(running::destroyForcibly, timeout.toMillis(), TimeUnit.MILLISECONDS);
            long until = System.nanoTime() + timeout.toNanos();
            try (var output = process.getOutputStream(); var input = process.getInputStream()) {
                send(output, Map.of("type", "evaluate", "sample", sample));
                int calls = 0;
                while (true) {
                    if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                    if (System.nanoTime() >= until) throw new IllegalStateException("RAGAS_DEADLINE_EXCEEDED");
                    JsonNode message = receive(input);
                    String type = message.path("type").asText("");
                    if ("complete".equals(type)) {
                        if (!message.path("modelCalls").isIntegralNumber() || message.path("modelCalls").asInt(-1) != calls) throw invalid();
                        var result = mapper.treeToValue(message.path("result"), RagEvaluationResult.class);
                        if (!"ragas".equals(result.providerId()) || !RUBRIC.equals(result.rubricVersion())
                            || !result.metrics().keySet().equals(Set.of("faithfulness", "relevance", "contextPrecision", "contextRecall", "referenceAgreement"))) throw invalid();
                        if (!process.waitFor(Math.min(1000, Math.max(1, (until - System.nanoTime()) / 1_000_000)), TimeUnit.MILLISECONDS)
                            || process.exitValue() != 0) throw invalid();
                        return result;
                    }
                    if (!Set.of("generate", "embed").contains(type) || ++calls > MAX_CALLS
                        || !message.path("id").isIntegralNumber() || message.path("id").asInt(-1) != calls) throw invalid();
                    Object value;
                    if ("generate".equals(type)) {
                        String prompt = boundedText(message.path("prompt"), 200_000);
                        var schema = message.path("schema");
                        if (!schema.isObject() || schema.toString().length() > 80_000) throw invalid();
                        value = inference.generate(prompt, schema);
                        if (!(value instanceof JsonNode node) || !node.isObject()) throw invalid();
                    } else {
                        var vector = inference.embed(boundedText(message.path("text"), 8000));
                        if (vector == null || vector.isEmpty() || vector.size() > 4096
                            || vector.stream().anyMatch(number -> number == null || !Double.isFinite(number))) throw invalid();
                        value = vector;
                    }
                    send(output, Map.of("type", "result", "id", calls, "value", value));
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new CancellationException();
        } catch (CancellationException cancelled) { throw cancelled;
        } catch (Exception failure) {
            // Keep provider/input/OS payloads out of API errors; the caller retains actual model accounting.
            throw new IllegalStateException("RAGAS_EVALUATION_UNAVAILABLE_OR_FAILED");
        } finally {
            if (deadline != null) deadline.cancel(false);
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (process != null) active.compareAndSet(process, null);
            capacity.release();
        }
    }

    private void send(OutputStream output, Object message) throws java.io.IOException {
        byte[] bytes = mapper.writeValueAsBytes(message);
        if (bytes.length > MAX_FRAME) throw invalid();
        output.write(bytes); output.write('\n'); output.flush();
    }

    private JsonNode receive(InputStream input) throws java.io.IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        while (bytes.size() <= MAX_FRAME) {
            int value = input.read();
            if (value < 0) throw invalid();
            if (value == '\n') return mapper.readTree(bytes.toByteArray());
            bytes.write(value);
        }
        throw invalid();
    }

    private static String boundedText(JsonNode value, int maximum) {
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > maximum) throw invalid();
        return value.textValue();
    }

    private static Path absolute(String value) {
        var path = Path.of(value);
        if (!path.isAbsolute()) throw new IllegalArgumentException("Evaluation executable paths must be absolute server configuration.");
        return path;
    }

    private static IllegalStateException invalid() { return new IllegalStateException("INVALID_RAGAS_PROTOCOL"); }
    @PreDestroy public void close() {
        var process = active.getAndSet(null);
        if (process != null && process.isAlive()) process.destroyForcibly();
        deadlines.shutdownNow();
    }
}
