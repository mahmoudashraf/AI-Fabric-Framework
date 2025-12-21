#!/bin/bash

###############################################################################
###############################################################################
# Relationship Query Real API Integration Test Runner (Provider Matrix style)
# 
# This script runs relationship-query real API integration tests (Failsafe) and
# lets you pass provider combinations similar to run-provider-matrix-tests.sh.
#
# Usage:
#   ./run-relationship-query-realapi-tests.sh [LLM:EMBEDDING[:VECTOR_DB]]
#   ./run-relationship-query-realapi-tests.sh "openai:onnx"
#   ./run-relationship-query-realapi-tests.sh "openai:openai:pinecone"
#   ./run-relationship-query-realapi-tests.sh "anthropic:openai" "qdrant"
#
# Prerequisites:
#   - Java 21+
#   - Maven 3.8+
#   - OPENAI_API_KEY (and other provider keys as needed) set in the environment
#   - Local: Dependencies built (script auto-builds if missing)
#   - CI/CD: Dependencies must be pre-built by workflow (script skips build check)
#
# CI/CD:
#   The GitHub workflow builds dependencies once in "Build AI Infrastructure Module" step.
#   Script detects CI environment (CI=true or GITHUB_ACTIONS=true) and skips dependency
#   build check to avoid duplicate builds. The workflow exports OPENAI_API_KEY,
#   AI_INFRASTRUCTURE_LLM_PROVIDER, AI_INFRASTRUCTURE_EMBEDDING_PROVIDER,
#   AI_INFRASTRUCTURE_VECTOR_DATABASE, and AI_INFRASTRUCTURE_PERSISTENCE_DATABASE.
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Configuration
MAVEN_PROFILE="realapi"
TEST_MODULE="relationship-query-integration-tests"
MATRIX_SPEC="${1:-openai:onnx}"
VECTOR_DB="${2:-}"

# Functions
print_header() {
    echo -e "${BLUE}"
    echo "═══════════════════════════════════════════════════════════════"
    echo "$1"
    echo "═══════════════════════════════════════════════════════════════"
    echo -e "${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Pre-flight checks
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

if [ -z "$OPENAI_API_KEY" ]; then
    print_error "OPENAI_API_KEY environment variable is not set"
    echo "Please set: export OPENAI_API_KEY='your-api-key'"
    exit 1
fi
print_success "OpenAI API key is configured"

# Parse provider matrix (LLM:EMBEDDING[:VECTOR_DB])
LLM_PROVIDER=""
EMBEDDING_PROVIDER=""

if [[ "$MATRIX_SPEC" =~ ^([^:]+):([^:]+):(.+)$ ]]; then
    LLM_PROVIDER="${BASH_REMATCH[1]}"
    EMBEDDING_PROVIDER="${BASH_REMATCH[2]}"
    VECTOR_DB_FROM_SPEC="${BASH_REMATCH[3]}"
    MATRIX_SPEC="${LLM_PROVIDER}:${EMBEDDING_PROVIDER}"
    export AI_INFRASTRUCTURE_VECTOR_DATABASE="$VECTOR_DB_FROM_SPEC"
    print_info "Extracted vector DB from matrix spec: $VECTOR_DB_FROM_SPEC"
elif [[ "$MATRIX_SPEC" =~ ^([^:]+):([^:]+)$ ]]; then
    LLM_PROVIDER="${BASH_REMATCH[1]}"
    EMBEDDING_PROVIDER="${BASH_REMATCH[2]}"
else
    print_warning "Matrix spec not recognized, falling back to defaults (openai:onnx)"
    LLM_PROVIDER="openai"
    EMBEDDING_PROVIDER="onnx"
fi

# Override vector DB if passed as second arg
if [ -n "$VECTOR_DB" ]; then
    export AI_INFRASTRUCTURE_VECTOR_DATABASE="$VECTOR_DB"
    print_info "Vector DB (arg): $VECTOR_DB"
fi

# Default providers if not set
export AI_INFRASTRUCTURE_LLM_PROVIDER="${AI_INFRASTRUCTURE_LLM_PROVIDER:-$LLM_PROVIDER}"
export AI_INFRASTRUCTURE_EMBEDDING_PROVIDER="${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER:-$EMBEDDING_PROVIDER}"

print_header "Test Configuration"
print_info "Test Module: $TEST_MODULE"
print_info "Maven Profile: $MAVEN_PROFILE"
print_info "Provider Matrix: ${AI_INFRASTRUCTURE_LLM_PROVIDER}:${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER}"
if [ -n "$AI_INFRASTRUCTURE_VECTOR_DATABASE" ]; then
    print_info "Vector DB: $AI_INFRASTRUCTURE_VECTOR_DATABASE"
fi
print_info "Test Classes: All *RealApiIntegrationTest.java in realapi/ directory"

# Check if dependencies are built (skip in CI/CD - already built by workflow)
if [ "${CI:-false}" == "true" ] || [ "${GITHUB_ACTIONS:-false}" == "true" ]; then
    print_info "Running in CI/CD - skipping dependency build check (already built by workflow)"
else
    PARENT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
    CORE_TARGET="${PARENT_DIR}/ai-infrastructure-core/target"
    if [ ! -d "$CORE_TARGET" ] || [ ! -f "$CORE_TARGET/ai-infrastructure-core-*.jar" ] 2>/dev/null; then
        print_warning "Dependencies may not be built. Attempting to build..."
        cd "$PARENT_DIR" || exit 1
        if ! mvn clean install -DskipTests -B -q; then
            print_error "Failed to build dependencies. Please run 'mvn clean install -DskipTests' from the parent module first."
            exit 1
        fi
        cd "$SCRIPT_DIR" || exit 1
        print_success "Dependencies built successfully"
    else
        print_success "Dependencies appear to be built"
    fi
fi

# Build Maven command (Failsafe)
print_header "Building Maven Command"

cd "$SCRIPT_DIR"

MAVEN_COMMAND="mvn -P${MAVEN_PROFILE}"
MAVEN_COMMAND="$MAVEN_COMMAND -DforkCount=1"
MAVEN_COMMAND="$MAVEN_COMMAND -DreuseForks=false"
MAVEN_COMMAND="$MAVEN_COMMAND failsafe:integration-test failsafe:verify"

# Optional debug flag
if [ "${DEBUG:-false}" == "true" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -X"
fi

print_info "Working Directory: $(pwd)"

# Display the command
print_header "Executing Tests"
echo -e "${BLUE}Command:${NC}"
echo "  $MAVEN_COMMAND"
echo ""

# Execute the tests
start_time=$(date +%s)

if eval "$MAVEN_COMMAND"; then
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    
    print_header "Test Execution Complete"
    print_success "All tests passed"
    print_info "Duration: ${duration}s"
    
    exit 0
else
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    
    print_header "Test Execution Failed"
    print_error "Some tests failed or errored"
    print_info "Duration: ${duration}s"
    
    exit 1
fi

