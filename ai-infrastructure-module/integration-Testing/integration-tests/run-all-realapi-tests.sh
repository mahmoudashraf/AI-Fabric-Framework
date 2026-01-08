#!/bin/bash
# Run all RealAPI tests with OpenAI embeddings (512 dimensions) and Lucene

export OPENAI_API_KEY="${OPENAI_API_KEY:-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}"

# All RealAPI test classes
TESTS=(
  "RealAPIIntegrationTest"
  "RealAPIONNXFallbackIntegrationTest"
  "RealAPISmartValidationIntegrationTest"
  "RealAPIVectorLifecycleIntegrationTest"
  "RealAPIHybridRetrievalToggleIntegrationTest"
  "RealAPIIntentHistoryAggregationIntegrationTest"
  "RealAPIActionErrorRecoveryIntegrationTest"
  "RealAPIActionFlowIntegrationTest"
  "RealAPIIntentGenerationRoutingIntegrationTest"
  "RealAPIMultiProviderFailoverIntegrationTest"
  "RealAPISmartSuggestionsIntegrationTest"
  "RealAPIPIIEdgeSpectrumIntegrationTest"
  "RealAPICreativeAIScenariosIntegrationTest"
)

TEST_CLASSES=$(IFS=,; echo "${TESTS[*]}")

echo "=========================================="
echo "Running All RealAPI Tests"
echo "=========================================="
echo "Test Classes: ${TEST_CLASSES}"
echo "Configuration:"
echo "  - Embedding Provider: openai"
echo "  - Embedding Dimensions: 512"
echo "  - Vector DB: lucene"
echo "  - Storage Strategy: SINGLE_TABLE"
echo "=========================================="
echo ""

mvn test \
  "-Dtest=${TEST_CLASSES}" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=openai" \
  "-Dai.providers.openai.embedding-model=text-embedding-3-small" \
  "-Dai.providers.openai.embedding-dimensions=512" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE" \
  "-DforkCount=1" \
  "-DreuseForks=false" \
  "-Dlogging.level.root=WARN" \
  "-Dlogging.level.com.ai.infrastructure.provider.openai=INFO" \
  2>&1 | tee /tmp/realapi-tests-output.log

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
grep -E "Tests run|BUILD|FAILURE|SUCCESS|Running.*RealAPI" /tmp/realapi-tests-output.log | tail -n 30
