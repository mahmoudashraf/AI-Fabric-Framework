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
#   - OPENAI_API_KEY set (and other provider keys as needed)
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

MAVEN_PROFILE="realapi"
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
if [ -z "$OPENAI_API_KEY" ]; then
  print_error "OPENAI_API_KEY is not set"; exit 1;
fi
print_success "OpenAI API key present"

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
CMD="mvn -P${MAVEN_PROFILE} -DforkCount=1 -DreuseForks=false failsafe:integration-test failsafe:verify"
echo -e "${BLUE}Command:${NC} $CMD"

start_time=$(date +%s)
if eval "$CMD"; then
  end_time=$(date +%s); duration=$((end_time-start_time))
  print_success "All tests passed (${duration}s)"; exit 0
else
  end_time=$(date +%s); duration=$((end_time-start_time))
  print_error "Tests failed (${duration}s)"; exit 1
fi
