#!/bin/bash

###############################################################################
# Chat Session Real API Integration Test Runner
#
# Runs chat-session real API integration tests (Failsafe) and lets you pass
# provider combinations similar to relationship-query suite runners.
#
# Usage:
#   ./run-chat-session-realapi-tests.sh [LLM:EMBEDDING[:VECTOR_DB]]
#   ./run-chat-session-realapi-tests.sh "openai:onnx:lucene"
#
# Prerequisites:
#   - Java 21+
#   - Maven 3.8+
#   - Provider credentials via env vars (e.g., OPENAI_API_KEY)
###############################################################################

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

MAVEN_PROFILE="realapi"
SPRING_PROFILE="${SPRING_PROFILES_ACTIVE:-realapi}"
MATRIX_SPEC="${1:-openai:onnx:lucene}"

print_header() {
  echo -e "${BLUE}"
  echo "═══════════════════════════════════════════════════════════════"
  echo "$1"
  echo "═══════════════════════════════════════════════════════════════"
  echo -e "${NC}"
}

print_info() { echo -e "${BLUE}ℹ${NC} $1"; }
print_success() { echo -e "${GREEN}✓${NC} $1"; }
print_error() { echo -e "${RED}✗${NC} $1"; }
print_warning() { echo -e "${YELLOW}⚠${NC} $1"; }

print_header "Pre-flight Checks"

if ! command -v java &> /dev/null; then
  print_error "Java is not installed"
  exit 1
fi
print_success "Java found: $(java -version 2>&1 | head -n 1)"

if ! command -v mvn &> /dev/null; then
  print_error "Maven is not installed"
  exit 1
fi
print_success "Maven found: $(mvn -v 2>&1 | head -n 1)"

LLM_PROVIDER="$(echo "$MATRIX_SPEC" | cut -d':' -f1)"
EMBEDDING_PROVIDER="$(echo "$MATRIX_SPEC" | cut -d':' -f2)"
VECTOR_DB="$(echo "$MATRIX_SPEC" | cut -d':' -f3)"
if [ -z "$VECTOR_DB" ]; then
  VECTOR_DB="lucene"
fi

export AI_INFRASTRUCTURE_LLM_PROVIDER="${AI_INFRASTRUCTURE_LLM_PROVIDER:-$LLM_PROVIDER}"
export AI_INFRASTRUCTURE_EMBEDDING_PROVIDER="${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER:-$EMBEDDING_PROVIDER}"
export AI_INFRASTRUCTURE_VECTOR_DATABASE="${AI_INFRASTRUCTURE_VECTOR_DATABASE:-$VECTOR_DB}"

print_info "Provider matrix: LLM=$AI_INFRASTRUCTURE_LLM_PROVIDER, Embedding=$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER, VectorDB=$AI_INFRASTRUCTURE_VECTOR_DATABASE"

check_provider_api_keys() {
  local llm_provider="$1"
  local embedding_provider="$2"
  local missing=()

  case "$llm_provider" in
    openai) [ -z "$OPENAI_API_KEY" ] && missing+=("OPENAI_API_KEY") ;;
    anthropic) [ -z "$ANTHROPIC_API_KEY" ] && missing+=("ANTHROPIC_API_KEY") ;;
    gemini) [ -z "$GEMINI_API_KEY" ] && missing+=("GEMINI_API_KEY") ;;
    cohere) [ -z "$COHERE_API_KEY" ] && missing+=("COHERE_API_KEY") ;;
    azure)
      [ -z "$AZURE_API_KEY" ] && missing+=("AZURE_API_KEY")
      [ -z "$AZURE_ENDPOINT" ] && missing+=("AZURE_ENDPOINT")
      ;;
  esac

  case "$embedding_provider" in
    openai) [ -z "$OPENAI_API_KEY" ] && missing+=("OPENAI_API_KEY") ;;
    anthropic) [ -z "$ANTHROPIC_API_KEY" ] && missing+=("ANTHROPIC_API_KEY") ;;
    gemini) [ -z "$GEMINI_API_KEY" ] && missing+=("GEMINI_API_KEY") ;;
    cohere) [ -z "$COHERE_API_KEY" ] && missing+=("COHERE_API_KEY") ;;
    azure)
      [ -z "$AZURE_API_KEY" ] && missing+=("AZURE_API_KEY")
      [ -z "$AZURE_ENDPOINT" ] && missing+=("AZURE_ENDPOINT")
      ;;
  esac

  if [ ${#missing[@]} -gt 0 ]; then
    print_error "Missing required credentials: ${missing[*]}"
    exit 1
  fi
}

check_provider_api_keys "$AI_INFRASTRUCTURE_LLM_PROVIDER" "$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER"

cd "$SCRIPT_DIR"

print_header "Connectivity Verification"
CONNECTIVITY_COMMAND="mvn -P${MAVEN_PROFILE} -Dspring.profiles.active=${SPRING_PROFILE} -Dai.realapi.connectivity.check=true -Dtest=RealApiConnectivityVerificationTest"
if [ -n "$AI_INFRASTRUCTURE_VECTOR_DATABASE" ]; then
  CONNECTIVITY_COMMAND="$CONNECTIVITY_COMMAND -Dai.vector-db.type=${AI_INFRASTRUCTURE_VECTOR_DATABASE}"
fi
if [ -n "$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER" ]; then
  CONNECTIVITY_COMMAND="$CONNECTIVITY_COMMAND -Dai.providers.embedding-provider=$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER"
fi
if [ -n "$AI_INFRASTRUCTURE_LLM_PROVIDER" ]; then
  CONNECTIVITY_COMMAND="$CONNECTIVITY_COMMAND -Dai.providers.llm-provider=$AI_INFRASTRUCTURE_LLM_PROVIDER"
fi

print_info "Connectivity command:"
echo "  $CONNECTIVITY_COMMAND"
echo ""

if eval "$CONNECTIVITY_COMMAND test"; then
  print_success "Connectivity verification passed"
else
  print_error "Connectivity verification failed - aborting test run"
  exit 1
fi

print_header "Executing RealAPI Tests"
MAVEN_COMMAND="mvn -P${MAVEN_PROFILE} -Dspring.profiles.active=${SPRING_PROFILE} -DforkCount=1 -DreuseForks=false"
if [ -n "$AI_INFRASTRUCTURE_VECTOR_DATABASE" ]; then
  MAVEN_COMMAND="$MAVEN_COMMAND -Dai.vector-db.type=${AI_INFRASTRUCTURE_VECTOR_DATABASE}"
fi
if [ -n "$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER" ]; then
  MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.embedding-provider=$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER"
fi
if [ -n "$AI_INFRASTRUCTURE_LLM_PROVIDER" ]; then
  MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.llm-provider=$AI_INFRASTRUCTURE_LLM_PROVIDER"
fi

case "$AI_INFRASTRUCTURE_LLM_PROVIDER" in
  openai) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.openai.enabled=true -DOPENAI_ENABLED=true" ;;
  anthropic) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.anthropic.enabled=true -DANTHROPIC_ENABLED=true" ;;
  gemini) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.gemini.enabled=true -DGEMINI_ENABLED=true" ;;
  cohere) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.cohere.enabled=true -DCOHERE_ENABLED=true" ;;
  azure) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.azure.enabled=true -DAZURE_ENABLED=true" ;;
esac

case "$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER" in
  openai) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.openai.enabled=true -DOPENAI_ENABLED=true" ;;
  cohere) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.cohere.enabled=true -DCOHERE_ENABLED=true" ;;
  azure) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.azure.enabled=true -DAZURE_ENABLED=true" ;;
esac

case "${AI_INFRASTRUCTURE_VECTOR_DATABASE:-}" in
  pinecone) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.pinecone.enabled=true" ;;
  weaviate) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.weaviate.enabled=true" ;;
  qdrant) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.qdrant.enabled=true" ;;
  milvus) MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.milvus.enabled=true" ;;
esac

MAVEN_COMMAND="$MAVEN_COMMAND failsafe:integration-test failsafe:verify"

print_info "Command:"
echo "  $MAVEN_COMMAND"
echo ""

start_time=$(date +%s)
if eval "$MAVEN_COMMAND"; then
  end_time=$(date +%s)
  duration=$((end_time - start_time))
  print_success "All RealAPI chat-session tests passed (${duration}s)"
  exit 0
else
  end_time=$(date +%s)
  duration=$((end_time - start_time))
  print_error "Chat-session RealAPI tests failed (${duration}s)"
  exit 1
fi
