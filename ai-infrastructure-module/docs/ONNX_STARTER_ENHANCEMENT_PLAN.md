# ONNX Spring Starter Enhancement Plan

## 1. Vision & Scope
- **Goal**: Deliver a standalone Spring Boot starter (`ai-onnx-starter`) that enables production-grade, offline embedding generation compatible with the AI Infrastructure Core.
- **Why separate module**: Allows independent release cadence, lean dependencies, and clear positioning for customers needing local inference (compliance, cost control, latency).
- **Target release**: v1.0.0 within 12 weeks, aligned with AI Infrastructure Core hardening wave.

## 2. Core Outcomes
- **High-quality embeddings** via tokenizer parity with the original ONNX model.
- **Scalable inference** supporting batch workloads and concurrent processing.
- **Operational readiness** through metrics, health checks, configurable limits, and documentation.
- **Seamless integration** with core library (drop-in provider implementation, autoconfiguration).

## 3. Functional Requirements
1. **Tokenizer Integration**
   - Use HuggingFace tokenizers (Java bindings or JNI bridge) with model-specific vocab/merges files.
   - Support multilingual tokenization; include regression corpus covering Latin, CJK, RTL scripts.
   - Provide offline tokenizer asset packaging and checksum validation.
2. **Batch Inference Support**
   - Accept list of texts; pad/truncate to dynamic sequence length (configurable).
   - Convert to `[batchSize, sequenceLength]` tensors; run single ONNX session for the batch.
   - Return embeddings preserving input order; expose batch metrics.
3. **Concurrency & Session Management**
   - Configurable inference pool (fixed sessions) with queueing and instrumentation.
   - Document resource footprint per session; allow warm-up at startup.
4. **Performance Optimizations**
   - Optional memory-mapped model loading.
   - SIMD-friendly float conversions; avoid boxing for embedding arrays.
   - Configurable parallelism for post-processing (e.g., mean pooling).
5. **Observability & Control Plane**
   - Micrometer meters: latency histogram, throughput, queue depth, failures.
   - Health indicator reporting readiness (model loaded) & liveness (recent success).
   - Expose configuration toggles via Spring properties (`ai.onnx.*`).
6. **Fallback & Error Handling**
   - Graceful degradation path (return sentinel status, enable failover to cloud providers).
   - Structured error taxonomy (model load, tokenizer, inference, resource limits).
7. **Packaging & Distribution**
   - Maven artifacts: starter (`ai-onnx-starter`), core runtime (`ai-onnx-runtime`), tokenizer assets module.
   - BOM entry for dependency management; compatibility matrix with AI Infrastructure Core versions.

## 4. Architecture Overview
- **Modules**:
  - `ai-onnx-runtime`: Owns tokenization, model management, inference engine.
  - `ai-onnx-spring-autoconfigure`: Provides `@Configuration` classes, health indicators, metrics.
  - `ai-onnx-starter`: Depends on autoconfigure + runtime; exposes Spring Boot starter semantics.
- **Integration Points**:
  - Implements `EmbeddingProvider` interface from AI Infrastructure Core.
  - Provides `@ConditionalOnProperty("ai.providers.onnx.enabled")` autoconfiguration.
  - Supplies `OnnxEmbeddingProperties` bound to `application.yml`.

## 5. Delivery Milestones

| Milestone | Target Week | Deliverables |
|-----------|-------------|--------------|
| M1: Project Setup | Week 1 | Repositories/Gradle modules created, publishing pipeline stubbed |
| M2: Tokenizer GA | Week 3 | HuggingFace tokenizer integration, regression suite, docs |
| M3: Batch Inference Alpha | Week 5 | Tensor batching implemented, unit benchmarks, latency <1.5x single inference |
| M4: Concurrency & Pooling | Week 7 | Session pool, configurable limits, load tests 100 req/min | 
| M5: Observability Pack | Week 8 | Micrometer metrics, health endpoints, Grafana sample |
| M6: Integration Tests | Week 9 | Contract tests with AI Core registry, fallback scenarios |
| M7: Performance Certification | Week 10 | Benchmarks published (latency, throughput, memory) |
| M8: Release Candidate | Week 11 | Documentation, samples, starter auto-configuration verification |
| M9: v1.0.0 Launch | Week 12 | Maven Central release, launch blog, adoption outreach |

## 6. Engineering Backlog (High-Level)
- **Tokenizer**: load vocab/merges at runtime, caching strategy, safe updates.
- **Inference engine**: mean pooling refactor to vectorized operations; handle 2D/3D outputs.
- **Config**: properties for `model-path`, `max-sequence-length`, `batch-size`, `pool-size`, `queue-capacity`, `timeout`.
- **Validation**: assert dimension correctness; fail fast on mismatched configs.
- **Benchmarks**: JMH suite covering single vs batch, 64/128 token sequences, various CPU types.
- **Testing**: concurrency tests (JUnit + Awaitility), resilience tests (corrupted model, huge input), performance regression guardrails.

## 7. Documentation & Samples
- **Reference Guide**: installation, configuration matrix, tuning tips.
- **Operations Guide**: scaling strategies, monitoring, troubleshooting.
- **Sample Apps**:
  - Simple CLI embedding batcher.
  - Spring Boot REST service generating embeddings via ONNX with caching.
  - Comparison demo toggling between ONNX and OpenAI providers.

## 8. QA & Release Criteria
- **Functional**: Pass full unit + integration suite; embedding cosine similarity within 2% of OpenAI baseline on regression corpus.
- **Performance**: 95th percentile latency <750ms for 512-token input on 8-core CPU; throughput ≥200 req/min with queueing.
- **Reliability**: 72-hour soak without memory leaks; graceful backpressure under overload.
- **Security**: No embedded secrets; signed artifacts; SBOM generated.
- **Docs**: README, migration guide, API docs, troubleshooting FAQ complete.

## 9. Team Roles
- **Module Tech Lead**: Architecture decisions, performance tuning, release management.
- **NLP Engineer**: Tokenizer integration, multilingual validation.
- **Platform Engineer**: Session pooling, observability, deployment samples.
- **QA Engineer**: Regression corpus, concurrency tests, benchmark harness.
- **Technical Writer**: Reference/operations guides, sample app walkthroughs.

## 10. Risks & Mitigations
- **Tokenizer Licensing/Compatibility** → Evaluate Apache-licensed tokenizer options; plan fallback using tokenizer REST microservice.
- **Performance Variability** → Publish hardware requirements; provide tuning knobs; ship baseline benchmark results.
- **JNI/JNA Complexity** → Contain native dependencies in runtime module; add compatibility testing for major OS/CPU combos.
- **Maintenance Overhead** → Automate model/tokenizer updates with checksum validation; document contribution guide for new models.

## 11. Communication Plan
- Weekly Slack/standup for engineering updates.
- Bi-weekly stakeholder readout (progress vs milestones, risks).
- Monthly public blog/changelog once in beta.
- Launch announcement via Spring community channels, LinkedIn, and documentation portal.

## 12. Immediate Next Steps (Week 0)
1. Finalize project charter and assign leads.
2. Decide packaging strategy (multi-module Maven vs Gradle composite); scaffold repo.
3. Secure tokenizer assets and verify licensing.
4. Draft regression dataset (50+ multilingual sentences with OpenAI baseline embeddings).
5. Define CI pipeline (GitHub Actions) with build, test, benchmark stages.

---

**Plan Owner**: ONNX Starter Tech Lead  
**Last Updated**: 2025-11-03

