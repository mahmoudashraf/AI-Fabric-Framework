#!/bin/bash

###############################################################################
# Dynamic Provider Matrix Integration Test Runner
# 
# This script runs integration tests dynamically with different provider 
# combinations (LLM provider, embedding provider, and optionally vector database).
#
# Usage:
#   ./run-provider-matrix-tests.sh [COMBINATION] [VECTOR_DB] [TEST_CHUNK]
#
# Examples:
#   # Run with default (OpenAI + ONNX, all tests)
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
#   ./run-provider-matrix-tests.sh "openai:onnx:lucene:SINGLE_TABLE"
#
#   # Run specific test chunk (faster execution)
#   ./run-provider-matrix-tests.sh "openai:onnx" "" "core"
#   ./run-provider-matrix-tests.sh "openai:onnx" "" "vector"
#   ./run-provider-matrix-tests.sh "openai:onnx" "" "intent-actions"
#   ./run-provider-matrix-tests.sh "openai:onnx" "" "advanced"
#   ./run-provider-matrix-tests.sh "openai:onnx" "" "core,vector"
#
# Prerequisites:
#   - Java 21+
#   - Maven 3.8+
#   - Dependencies must be built and installed (run 'mvn clean install -DskipTests' from parent)
#   - API key environment variable set based on selected providers:
#     * OPENAI_API_KEY (for OpenAI)
#     * ANTHROPIC_API_KEY (for Anthropic)
#     * GEMINI_API_KEY (for Gemini)
#     * COHERE_API_KEY (for Cohere)
#     * AZURE_API_KEY + AZURE_ENDPOINT (for Azure)
#   - ONNX and REST providers don't require API keys
#
# Note: This script assumes dependencies are already built. In CI/CD workflows,
#       the build step should run 'mvn clean install -DskipTests' first.
#
# Environment Variables:
#   OPENAI_API_KEY           - OpenAI API key (required if using OpenAI)
#   ANTHROPIC_API_KEY        - Anthropic API key (required if using Anthropic)
#   GEMINI_API_KEY           - Gemini API key (required if using Gemini)
#   COHERE_API_KEY           - Cohere API key (required if using Cohere)
#   AZURE_API_KEY            - Azure API key (required if using Azure)
#   AZURE_ENDPOINT            - Azure endpoint URL (required if using Azure)
#   AZURE_DEPLOYMENT_NAME     - Azure deployment name (required if using Azure for LLM)
#   AZURE_EMBEDDING_DEPLOYMENT_NAME - Azure embedding deployment (required if using Azure for Embedding)
#   SKIP_TESTS               - Set to skip tests (default: false)
#   MAVEN_LOGGING_LEVEL      - Maven logging level: quiet, normal, verbose, debug (default: quiet)
#   AI_PROVIDERS_REAL_API_TEST_CHUNK - Test chunk: core, vector, intent-actions, advanced, all (default: all)
#
# Test Chunks:
#   core            - Core functionality (3 test classes)
#   vector          - Vector operations (3 test classes)
#   intent-actions  - Intent & Actions (3 test classes)
#   advanced        - Advanced features (4 test classes)
#   all             - All tests (13 test classes, default)
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
TEST_CHUNK="${3:-all}"
LOGGING_LEVEL="${MAVEN_LOGGING_LEVEL:-quiet}"
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

# Dynamic API key check based on selected providers
# Parse matrix spec to determine which providers are being used
check_provider_api_keys() {
    local matrix_spec="$1"
    local missing_keys=()
    local providers_checked=()
    local providers_needed_llm=()
    local providers_needed_embedding=()
    
    # Handle comma-separated combinations - check all unique providers needed
    IFS=',' read -ra COMBINATIONS <<< "$matrix_spec"
    
    for combo in "${COMBINATIONS[@]}"; do
        # Extract providers from each combination (format: llm:embedding or llm:embedding:vectordb:storage)
        IFS=':' read -r llm_provider embedding_provider <<< "$combo"
        
        # Trim whitespace
        llm_provider=$(echo "$llm_provider" | xargs)
        embedding_provider=$(echo "$embedding_provider" | xargs)
        
        # Collect unique providers needed
        if [[ ! " ${providers_needed_llm[@]} " =~ " ${llm_provider} " ]]; then
            providers_needed_llm+=("$llm_provider")
        fi
        if [[ ! " ${providers_needed_embedding[@]} " =~ " ${embedding_provider} " ]]; then
            providers_needed_embedding+=("$embedding_provider")
        fi
    done
    
    # Check API keys for all unique LLM providers needed
    for llm_provider in "${providers_needed_llm[@]}"; do
        # Check LLM provider API key
        case "$llm_provider" in
        openai)
            if [ -z "$OPENAI_API_KEY" ]; then
                missing_keys+=("OPENAI_API_KEY (for OpenAI LLM)")
            else
                providers_checked+=("OpenAI LLM")
            fi
            ;;
        anthropic)
            if [ -z "$ANTHROPIC_API_KEY" ]; then
                missing_keys+=("ANTHROPIC_API_KEY (for Anthropic LLM)")
            else
                providers_checked+=("Anthropic LLM")
            fi
            ;;
        gemini)
            if [ -z "$GEMINI_API_KEY" ]; then
                missing_keys+=("GEMINI_API_KEY (for Gemini LLM)")
            else
                providers_checked+=("Gemini LLM")
            fi
            ;;
        cohere)
            if [ -z "$COHERE_API_KEY" ]; then
                missing_keys+=("COHERE_API_KEY (for Cohere LLM)")
            else
                providers_checked+=("Cohere LLM")
            fi
            ;;
        azure)
            if [ -z "$AZURE_API_KEY" ]; then
                missing_keys+=("AZURE_API_KEY (for Azure LLM)")
            elif [ -z "$AZURE_ENDPOINT" ]; then
                missing_keys+=("AZURE_ENDPOINT (for Azure LLM)")
            else
                providers_checked+=("Azure LLM")
            fi
            ;;
        onnx|rest)
            # ONNX and REST don't require API keys
            providers_checked+=("$llm_provider LLM (no API key required)")
            ;;
        *)
            print_warning "Unknown LLM provider: $llm_provider (skipping API key check)"
            ;;
    esac
    done
    
    # Check API keys for all unique Embedding providers needed
    for embedding_provider in "${providers_needed_embedding[@]}"; do
        # Check Embedding provider API key
        case "$embedding_provider" in
        openai)
            if [ -z "$OPENAI_API_KEY" ]; then
                missing_keys+=("OPENAI_API_KEY (for OpenAI Embedding)")
            else
                providers_checked+=("OpenAI Embedding")
            fi
            ;;
        anthropic)
            if [ -z "$ANTHROPIC_API_KEY" ]; then
                missing_keys+=("ANTHROPIC_API_KEY (for Anthropic Embedding)")
            else
                providers_checked+=("Anthropic Embedding")
            fi
            ;;
        gemini)
            if [ -z "$GEMINI_API_KEY" ]; then
                missing_keys+=("GEMINI_API_KEY (for Gemini Embedding)")
            else
                providers_checked+=("Gemini Embedding")
            fi
            ;;
        cohere)
            if [ -z "$COHERE_API_KEY" ]; then
                missing_keys+=("COHERE_API_KEY (for Cohere Embedding)")
            else
                providers_checked+=("Cohere Embedding")
            fi
            ;;
        azure)
            if [ -z "$AZURE_API_KEY" ]; then
                missing_keys+=("AZURE_API_KEY (for Azure Embedding)")
            elif [ -z "$AZURE_ENDPOINT" ]; then
                missing_keys+=("AZURE_ENDPOINT (for Azure Embedding)")
            else
                providers_checked+=("Azure Embedding")
            fi
            ;;
        onnx|rest)
            # ONNX and REST don't require API keys
            providers_checked+=("$embedding_provider Embedding (no API key required)")
            ;;
        *)
            print_warning "Unknown Embedding provider: $embedding_provider (skipping API key check)"
            ;;
    esac
    done
    
    # Report results
    if [ ${#missing_keys[@]} -gt 0 ]; then
        print_error "Missing required API keys for selected providers:"
        for key in "${missing_keys[@]}"; do
            echo "  - $key"
        done
        echo ""
        echo "Please set the required environment variables before running tests."
        return 1
    else
        print_success "API keys configured for: ${providers_checked[*]}"
        return 0
    fi
}

# Check API keys based on matrix spec
if ! check_provider_api_keys "$MATRIX_SPEC"; then
    exit 1
fi

# Check if dependencies are built (skip in CI/CD - already built by workflow)
if [ "${CI:-false}" == "true" ] || [ "${GITHUB_ACTIONS:-false}" == "true" ]; then
    print_info "Running in CI/CD - skipping dependency build check (already built by workflow)"
else
    # SCRIPT_DIR is ai-infrastructure-module/integration-Testing/integration-tests, so parent is ai-infrastructure-module
    PARENT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
    CORE_TARGET="${PARENT_DIR}/ai-infrastructure-core/target"
    if [ ! -d "$CORE_TARGET" ] || [ ! -f "$CORE_TARGET/ai-infrastructure-core-*.jar" ] 2>/dev/null; then
        print_warning "Dependencies may not be built. Attempting to build..."
        cd "$PARENT_DIR" || exit 1
        # Use quiet logging for dependency build unless debug is requested
        BUILD_LOG_FLAG="-q"
        if [ "$LOGGING_LEVEL" == "verbose" ] || [ "$LOGGING_LEVEL" == "debug" ]; then
            BUILD_LOG_FLAG=""
        fi
        if ! mvn clean install -DskipTests -B $BUILD_LOG_FLAG; then
            print_error "Failed to build dependencies. Please run 'mvn clean install -DskipTests' from the parent module first."
            exit 1
        fi
        cd "$SCRIPT_DIR" || exit 1
        print_success "Dependencies built successfully"
    else
        print_success "Dependencies appear to be built"
    fi
fi

# Build matrix specification
print_header "Test Configuration"

print_info "Matrix Specification: $MATRIX_SPEC"

# Parse matrix spec - supports llm:embedding[:vectordb[:storage]]
if [[ "$MATRIX_SPEC" =~ ^([^:]+):([^:]+):([^:]+):([^:]+)$ ]]; then
    LLM_PROVIDER="${BASH_REMATCH[1]}"
    EMBEDDING_PROVIDER="${BASH_REMATCH[2]}"
    VECTOR_DB_FROM_SPEC="${BASH_REMATCH[3]}"
    STORAGE_STRATEGY_FROM_SPEC="${BASH_REMATCH[4]}"
    export AI_INFRASTRUCTURE_VECTOR_DATABASE="$VECTOR_DB_FROM_SPEC"
    export AI_INFRASTRUCTURE_STORAGE_STRATEGY="$STORAGE_STRATEGY_FROM_SPEC"
    print_info "Extracted Vector DB: $VECTOR_DB_FROM_SPEC"
    print_info "Extracted Storage Strategy: $STORAGE_STRATEGY_FROM_SPEC"
elif [[ "$MATRIX_SPEC" =~ ^([^:]+):([^:]+):([^:]+)$ ]]; then
    LLM_PROVIDER="${BASH_REMATCH[1]}"
    EMBEDDING_PROVIDER="${BASH_REMATCH[2]}"
    VECTOR_DB_FROM_SPEC="${BASH_REMATCH[3]}"
    export AI_INFRASTRUCTURE_VECTOR_DATABASE="$VECTOR_DB_FROM_SPEC"
    print_info "Extracted Vector Database from matrix spec: $VECTOR_DB_FROM_SPEC"
elif [ -n "$VECTOR_DB" ]; then
    # Vector DB passed as separate parameter
    export AI_INFRASTRUCTURE_VECTOR_DATABASE="$VECTOR_DB"
    print_info "Vector Database: $VECTOR_DB"
fi

# Count combinations
COMBO_COUNT=$(echo "$MATRIX_SPEC" | awk -F',' '{print NF}')
print_info "Total Combinations: $COMBO_COUNT"
print_info "Test Chunk: $TEST_CHUNK"
print_info "Logging Level: $LOGGING_LEVEL"

# Build Maven command
print_header "Building Maven Command"

# Change to integration-tests directory for simpler Maven execution
TEST_DIR="$SCRIPT_DIR"
cd "$TEST_DIR"

# Note: This assumes dependencies are already built and installed.
# The workflow should run 'mvn clean install -DskipTests' from the parent module first.
MAVEN_COMMAND="mvn test"
MAVEN_COMMAND="$MAVEN_COMMAND -Dtest=$TEST_CLASS"
MAVEN_COMMAND="$MAVEN_COMMAND -Dspring.profiles.active=$PROFILE"
MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.real-api.matrix='$MATRIX_SPEC'"
MAVEN_COMMAND="$MAVEN_COMMAND -DforkCount=1"
MAVEN_COMMAND="$MAVEN_COMMAND -DreuseForks=false"

# Add vector database as system property if specified
if [ -n "$AI_INFRASTRUCTURE_VECTOR_DATABASE" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -Dai.vector-db.type=$AI_INFRASTRUCTURE_VECTOR_DATABASE"
fi

# Add storage strategy as system property if specified
if [ -n "$AI_INFRASTRUCTURE_STORAGE_STRATEGY" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -Dai-infrastructure.storage.strategy=$AI_INFRASTRUCTURE_STORAGE_STRATEGY"
fi

# Auto-configure OpenAI embedding dimensions for Lucene compatibility
# OpenAI embeddings default to 1536 dimensions, but Lucene supports max 1024
# Check if we're using OpenAI embeddings with Lucene vector database
# Matrix spec format: "llm:embedding" or "llm:embedding:vectordb" or "llm:embedding:vectordb:storage"
# Check if embedding provider is "openai" (second field in colon-separated spec)
if [ "$AI_INFRASTRUCTURE_VECTOR_DATABASE" == "lucene" ]; then
    # Check if EMBEDDING_PROVIDER is explicitly set to "openai"
    if [ "$EMBEDDING_PROVIDER" == "openai" ]; then
        MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.openai.embedding-dimensions=512"
        print_info "Auto-configured OpenAI embedding dimensions to 512 for Lucene compatibility"
    # Otherwise, check matrix spec for any combination with "openai" as embedding provider (2nd field)
    # Format: "something:openai" or "something:openai:something" (embedding is 2nd field)
    # Pattern matches: start or comma, then any chars, then colon, then "openai" as whole word, then colon/comma/end
    elif echo "$MATRIX_SPEC" | grep -qE "(^|,)([^:]+:)\bopenai\b(:|,|$)"; then
        MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.openai.embedding-dimensions=512"
        print_info "Auto-configured OpenAI embedding dimensions to 512 for Lucene compatibility (detected in matrix spec)"
    fi
fi

# Add test chunk as system property if specified
if [ -n "$TEST_CHUNK" ] && [ "$TEST_CHUNK" != "all" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.real-api.test-chunk=$TEST_CHUNK"
fi

# Configure application logging level based on Maven logging level
# Map Maven logging levels to Spring Boot logging levels
# These override the DEBUG settings in application-test.yml
case "$LOGGING_LEVEL" in
    quiet)
        # Quiet mode: Only WARN and ERROR (suppress all DEBUG/INFO)
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.root=WARN"
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.com.ai.infrastructure=WARN"
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.com.ai.infrastructure.provider=WARN"
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.com.ai.infrastructure.core=WARN"
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.com.ai.infrastructure.embedding=WARN"
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.org.springframework=WARN"
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.org.hibernate=WARN"
        ;;
    normal)
        # Normal mode: INFO level (suppress DEBUG)
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.root=INFO"
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.com.ai.infrastructure=INFO"
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.com.ai.infrastructure.embedding=INFO"
        ;;
    verbose|debug)
        # Verbose/Debug mode: DEBUG level (keep all logs)
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.root=DEBUG"
        MAVEN_COMMAND="$MAVEN_COMMAND -Dlogging.level.com.ai.infrastructure=DEBUG"
        ;;
esac

# Add optional flags
if [ "$SKIP_TESTS" == "true" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -DskipTests"
fi

if [ "${DEBUG:-false}" == "true" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -X"
fi

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
