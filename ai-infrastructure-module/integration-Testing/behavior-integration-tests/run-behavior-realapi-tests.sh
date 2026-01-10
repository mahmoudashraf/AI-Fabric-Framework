#!/bin/bash

###############################################################################
# Behavior Module Real API Integration Test Runner
#
# Mirrors the relationship-query runner style: runs Failsafe ITs with a
# provider matrix input (LLM:EMBEDDING[:VECTOR_DB]) and uses the "realapi"
# profile for the behavior integration module.
#
# Usage:
#   ./run-behavior-realapi-tests.sh [LLM:EMBEDDING[:VECTOR_DB]]
#   ./run-behavior-realapi-tests.sh "openai:onnx"
#   ./run-behavior-realapi-tests.sh "openai:openai:pinecone"
#
# Prerequisites:
#   - Java 21+, Maven 3.8+
#   - API key environment variable set based on selected providers:
#     * OPENAI_API_KEY (for OpenAI)
#     * ANTHROPIC_API_KEY (for Anthropic)
#     * GEMINI_API_KEY (for Gemini)
#     * COHERE_API_KEY (for Cohere)
#     * AZURE_API_KEY + AZURE_ENDPOINT (for Azure)
#   - ONNX and REST providers don't require API keys
#   - Dependencies built by workflow (CI) or locally (script will try to build)
###############################################################################

set -e

BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Use SPRING_PROFILES_ACTIVE if set (e.g., from GitHub Actions), otherwise default to realapi
MAVEN_PROFILE="${SPRING_PROFILES_ACTIVE:-realapi}"
TEST_MODULE="behavior-integration-tests"
MATRIX_SPEC="${1:-openai:onnx}"
VECTOR_DB="${2:-}"

print_header() { echo -e "${BLUE}\n═══════════════════════════════════════════════════════════════\n$1\n═══════════════════════════════════════════════════════════════${NC}"; }
print_info()    { echo -e "${BLUE}ℹ${NC} $1"; }
print_success() { echo -e "${GREEN}✓${NC} $1"; }
print_error()   { echo -e "${RED}✗${NC} $1"; }
print_warning() { echo -e "${YELLOW}⚠${NC} $1"; }

print_header "Pre-flight Checks"
command -v java >/dev/null || { print_error "Java not installed"; exit 1; }
print_success "Java: $(java -version 2>&1 | head -n1)"
command -v mvn  >/dev/null || { print_error "Maven not installed"; exit 1; }
print_success "Maven: $(mvn -v 2>&1 | head -n1)"
# Dynamic API key check based on selected providers
check_provider_api_keys() {
    local llm_provider="$1"
    local embedding_provider="$2"
    local missing_keys=()
    local providers_checked=()
    
    # Check LLM provider API key
    case "$llm_provider" in
        openai)
            [ -z "$OPENAI_API_KEY" ] && missing_keys+=("OPENAI_API_KEY (for OpenAI LLM)") || providers_checked+=("OpenAI LLM")
            ;;
        anthropic)
            [ -z "$ANTHROPIC_API_KEY" ] && missing_keys+=("ANTHROPIC_API_KEY (for Anthropic LLM)") || providers_checked+=("Anthropic LLM")
            ;;
        gemini)
            [ -z "$GEMINI_API_KEY" ] && missing_keys+=("GEMINI_API_KEY (for Gemini LLM)") || providers_checked+=("Gemini LLM")
            ;;
        cohere)
            [ -z "$COHERE_API_KEY" ] && missing_keys+=("COHERE_API_KEY (for Cohere LLM)") || providers_checked+=("Cohere LLM")
            ;;
        azure)
            [ -z "$AZURE_API_KEY" ] && missing_keys+=("AZURE_API_KEY (for Azure LLM)") || \
            ([ -z "$AZURE_ENDPOINT" ] && missing_keys+=("AZURE_ENDPOINT (for Azure LLM)") || providers_checked+=("Azure LLM"))
            ;;
        onnx|rest)
            providers_checked+=("$llm_provider LLM (no API key required)")
            ;;
        *)
            print_warning "Unknown LLM provider: $llm_provider (skipping API key check)"
            ;;
    esac
    
    # Check Embedding provider API key
    case "$embedding_provider" in
        openai)
            [ -z "$OPENAI_API_KEY" ] && missing_keys+=("OPENAI_API_KEY (for OpenAI Embedding)") || providers_checked+=("OpenAI Embedding")
            ;;
        anthropic)
            [ -z "$ANTHROPIC_API_KEY" ] && missing_keys+=("ANTHROPIC_API_KEY (for Anthropic Embedding)") || providers_checked+=("Anthropic Embedding")
            ;;
        gemini)
            [ -z "$GEMINI_API_KEY" ] && missing_keys+=("GEMINI_API_KEY (for Gemini Embedding)") || providers_checked+=("Gemini Embedding")
            ;;
        cohere)
            [ -z "$COHERE_API_KEY" ] && missing_keys+=("COHERE_API_KEY (for Cohere Embedding)") || providers_checked+=("Cohere Embedding")
            ;;
        azure)
            [ -z "$AZURE_API_KEY" ] && missing_keys+=("AZURE_API_KEY (for Azure Embedding)") || \
            ([ -z "$AZURE_ENDPOINT" ] && missing_keys+=("AZURE_ENDPOINT (for Azure Embedding)") || providers_checked+=("Azure Embedding"))
            ;;
        onnx|rest)
            providers_checked+=("$embedding_provider Embedding (no API key required)")
            ;;
        *)
            print_warning "Unknown Embedding provider: $embedding_provider (skipping API key check)"
            ;;
    esac
    
    # Report results
    if [ ${#missing_keys[@]} -gt 0 ]; then
        print_error "Missing required API keys for selected providers:"
        for key in "${missing_keys[@]}"; do echo "  - $key"; done
        echo ""; echo "Please set the required environment variables before running tests."
        return 1
    else
        print_success "API keys configured for: ${providers_checked[*]}"
        return 0
    fi
}

# Parse matrix spec first to determine providers for API key check
LLM_PROVIDER_TEMP=""
EMBEDDING_PROVIDER_TEMP=""

if [[ "$MATRIX_SPEC" =~ ^([^:]+):([^:]+):(.+)$ ]]; then
    LLM_PROVIDER_TEMP="${BASH_REMATCH[1]}"
    EMBEDDING_PROVIDER_TEMP="${BASH_REMATCH[2]}"
elif [[ "$MATRIX_SPEC" =~ ^([^:]+):([^:]+)$ ]]; then
    LLM_PROVIDER_TEMP="${BASH_REMATCH[1]}"
    EMBEDDING_PROVIDER_TEMP="${BASH_REMATCH[2]}"
else
    LLM_PROVIDER_TEMP="openai"
    EMBEDDING_PROVIDER_TEMP="onnx"
fi

# Check API keys based on providers
if ! check_provider_api_keys "$LLM_PROVIDER_TEMP" "$EMBEDDING_PROVIDER_TEMP"; then
    exit 1
fi

# Parse matrix LLM:EMBEDDING[:VECTOR_DB]
LLM_PROVIDER=""
EMBEDDING_PROVIDER=""
if [[ "$MATRIX_SPEC" =~ ^([^:]+):([^:]+):(.+)$ ]]; then
  LLM_PROVIDER="${BASH_REMATCH[1]}"
  EMBEDDING_PROVIDER="${BASH_REMATCH[2]}"
  VECTOR_DB_FROM_SPEC="${BASH_REMATCH[3]}"
  MATRIX_SPEC="${LLM_PROVIDER}:${EMBEDDING_PROVIDER}"
  export AI_INFRASTRUCTURE_VECTOR_DATABASE="$VECTOR_DB_FROM_SPEC"
  print_info "Vector DB (from spec): $VECTOR_DB_FROM_SPEC"
elif [[ "$MATRIX_SPEC" =~ ^([^:]+):([^:]+)$ ]]; then
  LLM_PROVIDER="${BASH_REMATCH[1]}"
  EMBEDDING_PROVIDER="${BASH_REMATCH[2]}"
else
  print_warning "Unrecognized matrix spec, defaulting to openai:onnx"
  LLM_PROVIDER="openai"
  EMBEDDING_PROVIDER="onnx"
fi
if [ -n "$VECTOR_DB" ]; then
  export AI_INFRASTRUCTURE_VECTOR_DATABASE="$VECTOR_DB"
  print_info "Vector DB (arg): $VECTOR_DB"
fi
export AI_INFRASTRUCTURE_LLM_PROVIDER="${AI_INFRASTRUCTURE_LLM_PROVIDER:-$LLM_PROVIDER}"
export AI_INFRASTRUCTURE_EMBEDDING_PROVIDER="${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER:-$EMBEDDING_PROVIDER}"

print_header "Test Configuration"
print_info "Test Module: $TEST_MODULE"
print_info "Maven Profile: $MAVEN_PROFILE"
print_info "Providers: ${AI_INFRASTRUCTURE_LLM_PROVIDER}:${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER}"
if [ -n "$AI_INFRASTRUCTURE_VECTOR_DATABASE" ]; then
  print_info "Vector DB: $AI_INFRASTRUCTURE_VECTOR_DATABASE"
fi
print_info "Test Classes: *IT.java / *IntegrationIT.java (failsafe)"

# Dependency build check (skip in CI)
if [ "${CI:-false}" != "true" ] && [ "${GITHUB_ACTIONS:-false}" != "true" ]; then
  PARENT_DIR="${PROJECT_ROOT}"
  CORE_TARGET="${PARENT_DIR}/ai-infrastructure-core/target"
  if [ ! -d "$CORE_TARGET" ]; then
    print_warning "Dependencies not built; running mvn clean install -DskipTests"
    cd "$PARENT_DIR" && mvn clean install -DskipTests -B -q
    print_success "Dependencies built"
  fi
else
  print_info "CI detected; dependency build already handled by workflow"
fi

print_header "Executing Tests"
cd "$SCRIPT_DIR"

# Build Maven command
CMD="mvn -P${MAVEN_PROFILE} -Dspring.profiles.active=${MAVEN_PROFILE} -DforkCount=1 -DreuseForks=false"

# Pass embedding provider as system property (more reliable than environment variables)
# This ensures the selected provider from GitHub Actions UI is actually used
if [ -n "$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER" ]; then
    CMD="$CMD -Dai.providers.embedding-provider=$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER"
    print_info "Configured embedding provider via system property: $AI_INFRASTRUCTURE_EMBEDDING_PROVIDER"
fi

# Pass LLM provider as system property for consistency
if [ -n "$AI_INFRASTRUCTURE_LLM_PROVIDER" ]; then
    CMD="$CMD -Dai.providers.llm-provider=$AI_INFRASTRUCTURE_LLM_PROVIDER"
fi

# Enable providers based on selection
case "$AI_INFRASTRUCTURE_LLM_PROVIDER" in
    openai)
        CMD="$CMD -Dai.providers.openai.enabled=true"
        CMD="$CMD -DOPENAI_ENABLED=true"
        ;;
    anthropic)
        CMD="$CMD -Dai.providers.anthropic.enabled=true"
        CMD="$CMD -DANTHROPIC_ENABLED=true"
        ;;
    gemini)
        CMD="$CMD -Dai.providers.gemini.enabled=true"
        CMD="$CMD -DGEMINI_ENABLED=true"
        ;;
    cohere)
        CMD="$CMD -Dai.providers.cohere.enabled=true"
        CMD="$CMD -DCOHERE_ENABLED=true"
        ;;
    azure)
        CMD="$CMD -Dai.providers.azure.enabled=true"
        CMD="$CMD -DAZURE_ENABLED=true"
        ;;
esac

case "$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER" in
    openai)
        CMD="$CMD -Dai.providers.openai.enabled=true"
        CMD="$CMD -DOPENAI_ENABLED=true"
        ;;
    cohere)
        CMD="$CMD -Dai.providers.cohere.enabled=true"
        CMD="$CMD -DCOHERE_ENABLED=true"
        ;;
    azure)
        CMD="$CMD -Dai.providers.azure.enabled=true"
        CMD="$CMD -DAZURE_ENABLED=true"
        ;;
esac

# Auto-configure OpenAI embedding dimensions for Lucene compatibility
# OpenAI embeddings default to 1536 dimensions, but Lucene supports max 1024
# Check if we're using OpenAI embeddings with Lucene vector database
if [ "$EMBEDDING_PROVIDER" == "openai" ] && [ "$AI_INFRASTRUCTURE_VECTOR_DATABASE" == "lucene" ]; then
    CMD="$CMD -Dai.providers.openai.embedding-dimensions=512"
    print_info "Auto-configured OpenAI embedding dimensions to 512 for Lucene compatibility"
fi

CMD="$CMD failsafe:integration-test failsafe:verify"
echo -e "${BLUE}Command:${NC} $CMD"

start_time=$(date +%s)
if eval "$CMD"; then
  end_time=$(date +%s); duration=$((end_time-start_time))
  print_success "All tests passed (${duration}s)"; exit 0
else
  end_time=$(date +%s); duration=$((end_time-start_time))
  print_error "Tests failed (${duration}s)"; exit 1
fi
