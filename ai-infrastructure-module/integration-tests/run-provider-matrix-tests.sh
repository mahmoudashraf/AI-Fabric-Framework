#!/bin/bash

###############################################################################
# Dynamic Provider Matrix Integration Test Runner
# 
# This script runs integration tests dynamically with different provider 
# combinations (LLM provider, embedding provider, and optionally vector database).
#
# Usage:
#   ./run-provider-matrix-tests.sh [COMBINATION] [VECTOR_DB]
#
# Examples:
#   # Run with default (OpenAI + ONNX)
#   ./run-provider-matrix-tests.sh
#
#   # Run with specific combination
#   ./run-provider-matrix-tests.sh "openai:onnx"
#   ./run-provider-matrix-tests.sh "anthropic:openai"
#   ./run-provider-matrix-tests.sh "azure:azure"
#
#   # Run multiple combinations
#   ./run-provider-matrix-tests.sh "openai:onnx,anthropic:openai,azure:azure"
#
#   # Run with vector database specification
#   ./run-provider-matrix-tests.sh "openai:onnx" "pinecone"
#   ./run-provider-matrix-tests.sh "openai:onnx:memory"
#
# Prerequisites:
#   - Java 21+
#   - Maven 3.8+
#   - OPENAI_API_KEY environment variable set (minimum)
#   - Optional: ANTHROPIC_API_KEY, AZURE_OPENAI_API_KEY, AZURE_OPENAI_ENDPOINT
#
# Environment Variables:
#   OPENAI_API_KEY           - OpenAI API key (required)
#   ANTHROPIC_API_KEY        - Anthropic API key (optional)
#   AZURE_OPENAI_API_KEY     - Azure OpenAI API key (optional)
#   AZURE_OPENAI_ENDPOINT    - Azure OpenAI endpoint (optional)
#   SKIP_TESTS               - Set to skip tests (default: false)
#   DEBUG                    - Set to enable debug logging (default: false)
#
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
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

# Configuration
MATRIX_SPEC="${1:-openai:onnx}"
VECTOR_DB="${2:-}"
PROFILE="real-api-test"
TEST_CLASS="RealAPIProviderMatrixIntegrationTest"
SKIP_TESTS="${SKIP_TESTS:-false}"

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

# Build matrix specification
print_header "Test Configuration"

print_info "Matrix Specification: $MATRIX_SPEC"

if [ -n "$VECTOR_DB" ]; then
    print_info "Vector Database: $VECTOR_DB"
    # Append vector DB to matrix if not already included
    if [[ ! "$MATRIX_SPEC" =~ : ]]; then
        # Format: llm:embedding
        MATRIX_SPEC="${MATRIX_SPEC}:${VECTOR_DB}"
    elif [[ "$MATRIX_SPEC" =~ ^[^:]*:[^:]*:?$ ]]; then
        # Format: llm:embedding (no vector db yet)
        MATRIX_SPEC="${MATRIX_SPEC}:${VECTOR_DB}"
    fi
    print_info "Updated Matrix: $MATRIX_SPEC"
fi

# Count combinations
COMBO_COUNT=$(echo "$MATRIX_SPEC" | awk -F',' '{print NF}')
print_info "Total Combinations: $COMBO_COUNT"

# Build Maven command
print_header "Building Maven Command"

MAVEN_COMMAND="mvn -pl integration-tests -am test"
MAVEN_COMMAND="$MAVEN_COMMAND -Dtest=$TEST_CLASS"
MAVEN_COMMAND="$MAVEN_COMMAND -Dspring.profiles.active=$PROFILE"
MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.real-api.matrix='$MATRIX_SPEC'"

# Add optional flags
if [ "$SKIP_TESTS" == "true" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -DskipTests"
fi

if [ "${DEBUG:-false}" == "true" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -X"
fi

# Ensure we're in the project root
cd "$PROJECT_ROOT"

print_info "Working Directory: $(pwd)"
print_info "Maven Profile: $PROFILE"
print_info "Test Class: $TEST_CLASS"

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
    print_info "Combinations tested: $COMBO_COUNT"
    
    exit 0
else
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    
    print_header "Test Execution Failed"
    print_error "Some tests failed or errored"
    print_info "Duration: ${duration}s"
    
    exit 1
fi
