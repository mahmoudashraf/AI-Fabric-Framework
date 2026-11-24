package com.ai.fabric.runtime.evaluation;

import ai.fabric.rag.evaluation.RagEvaluationSample;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RagasWorkerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path directory;
    private final RagEvaluationSample sample = new RagEvaluationSample("Return window?", "30 days. Returns are free.",
        List.of(new RagEvaluationSample.Evidence("chunk", "policy", "Returns are accepted within 30 days.")), "30 days.");

    @Test void executablePathsCannotBeCustomerRelativePaths() {
        assertThatThrownBy(() -> new RagasWorker(mapper, "python", "/worker.py")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagasWorker(mapper, "/python", "customer-script.py")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @EnabledIfSystemProperty(named = "loomai.ragas.python", matches = ".+")
    void actualPinnedLibraryUsesMeteredParentBridgeWithoutProviderCredentialsOrNetwork() {
        var calls = new AtomicInteger();
        var script = Path.of(System.getProperty("basedir"), "ragas-worker", "worker.py").toString();
        try (var worker = new WorkerScope(new RagasWorker(mapper, python(), script))) {
            var result = worker.value.evaluate(sample, new RagasWorker.Inference() {
                public JsonNode generate(String prompt, JsonNode schema) {
                    calls.incrementAndGet();
                    String json = switch (schema.path("title").asText()) {
                        case "StatementGeneratorOutput" -> "{\"statements\":[\"30 days.\",\"Returns are free.\"]}";
                        case "ClaimDecompositionOutput" -> "{\"claims\":[\"30 days.\",\"Returns are free.\"]}";
                        case "NLIStatementOutput" -> "{\"statements\":[{\"statement\":\"30 days.\",\"reason\":\"Supported\",\"verdict\":1},{\"statement\":\"Free.\",\"reason\":\"Unsupported\",\"verdict\":0}]}";
                        case "AnswerRelevanceOutput" -> "{\"question\":\"Return window?\",\"noncommittal\":0}";
                        case "ContextPrecisionOutput" -> "{\"reason\":\"Contains policy\",\"verdict\":1}";
                        case "ContextRecallOutput" -> "{\"classifications\":[{\"statement\":\"30 days.\",\"reason\":\"Present\",\"attributed\":1}]}";
                        default -> throw new AssertionError("Unqualified metric schema " + schema.path("title"));
                    };
                    try { return mapper.readTree(json); } catch (Exception failure) { throw new AssertionError(failure); }
                }
                public List<Double> embed(String text) { calls.incrementAndGet(); return List.of(1d, 0d); }
            }, Duration.ofSeconds(60));
            assertThat(result.providerId()).isEqualTo("ragas");
            assertThat(result.rubricVersion()).isEqualTo(RagasWorker.RUBRIC);
            assertThat(result.metrics().get("faithfulness").score()).isEqualTo(.5);
            assertThat(result.metrics().get("relevance").score()).isEqualTo(1);
            assertThat(result.metrics().get("referenceAgreement").score()).isEqualTo(.5);
            assertThat(calls).hasValue(15);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "loomai.ragas.python", matches = ".+")
    void childEnvironmentIsAnExplicitAllowlistAndUnavailableEngineHasNoFallback() throws Exception {
        var child = script("""
            import os,sys,json
            json.loads(sys.stdin.readline())
            print(json.dumps({'type':'complete','modelCalls':0,'result':{'providerId':'ragas','rubricVersion':'ragas-0.4.4-v1',
              'metrics':{key:{'status':'NOT_SCORED','score':None,'explanation':','.join(sorted(os.environ))} for key in
                ['faithfulness','relevance','contextPrecision','contextRecall','referenceAgreement']}}}),flush=True)
            """);
        try (var worker = new WorkerScope(new RagasWorker(mapper, python(), child.toString()))) {
            var result = worker.value.evaluate(sample, unused(), Duration.ofSeconds(5));
            assertThat(result.metrics()).hasSize(5);
            assertThat(java.util.Set.of(result.metrics().get("faithfulness").explanation().split(",")))
                .contains("LANG", "RAGAS_DO_NOT_TRACK", "HF_HUB_OFFLINE", "HF_DATASETS_OFFLINE", "OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS")
                .isSubsetOf(java.util.Set.of("LANG", "RAGAS_DO_NOT_TRACK", "HF_HUB_OFFLINE", "HF_DATASETS_OFFLINE",
                    "OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "LC_CTYPE", "__CF_USER_TEXT_ENCODING"));
        }
        try (var worker = new WorkerScope(new RagasWorker(mapper, python(), directory.resolve("absent.py").toString()))) {
            assertThatThrownBy(() -> worker.value.evaluate(sample, unused(), Duration.ofSeconds(5)))
                .hasMessage("RAGAS_EVALUATION_UNAVAILABLE_OR_FAILED").hasNoCause();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "loomai.ragas.python", matches = ".+")
    void unknownOperationCannotCallInferenceAndDeadlineKillsBlockedWorker() throws Exception {
        var unknown = script("import sys; sys.stdin.readline(); print('{\"type\":\"http\",\"id\":1,\"url\":\"https://example.com\"}',flush=True)");
        try (var worker = new WorkerScope(new RagasWorker(mapper, python(), unknown.toString()))) {
            assertThatThrownBy(() -> worker.value.evaluate(sample, unused(), Duration.ofSeconds(5)))
                .hasMessage("RAGAS_EVALUATION_UNAVAILABLE_OR_FAILED");
        }
        var blocked = script("import time; time.sleep(30)");
        long start = System.nanoTime();
        try (var worker = new WorkerScope(new RagasWorker(mapper, python(), blocked.toString()))) {
            assertThatThrownBy(() -> worker.value.evaluate(sample, unused(), Duration.ofMillis(200)))
                .hasMessage("RAGAS_EVALUATION_UNAVAILABLE_OR_FAILED");
        }
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @EnabledIfSystemProperty(named = "loomai.ragas.python", matches = ".+")
    void extraCallsCannotExceedTheSharedMetricAllowance() throws Exception {
        var tooMany = script("""
            import sys,json
            json.loads(sys.stdin.readline())
            for i in range(1,26):
                print(json.dumps({'type':'embed','id':i,'text':'bounded text'}),flush=True)
                json.loads(sys.stdin.readline())
            """);
        var count = new AtomicInteger();
        try (var worker = new WorkerScope(new RagasWorker(mapper, python(), tooMany.toString()))) {
            assertThatThrownBy(() -> worker.value.evaluate(sample, new RagasWorker.Inference() {
                public JsonNode generate(String prompt, JsonNode schema) { throw new AssertionError(); }
                public List<Double> embed(String text) { count.incrementAndGet(); return List.of(1d); }
            }, Duration.ofSeconds(5))).hasMessage("RAGAS_EVALUATION_UNAVAILABLE_OR_FAILED");
        }
        assertThat(count).hasValue(RagasWorker.MAX_CALLS);
    }

    private Path script(String source) throws Exception {
        var path = directory.resolve("fixture-" + java.util.UUID.randomUUID() + ".py");
        Files.writeString(path, source); return path;
    }

    private String python() { return System.getProperty("loomai.ragas.python"); }
    private RagasWorker.Inference unused() {
        return new RagasWorker.Inference() {
            public JsonNode generate(String prompt, JsonNode schema) { throw new AssertionError("Unexpected generation"); }
            public List<Double> embed(String text) { throw new AssertionError("Unexpected embedding"); }
        };
    }

    private record WorkerScope(RagasWorker value) implements AutoCloseable {
        public void close() { value.close(); }
    }
}
