#!/bin/bash

###############################################################################
# Single Integration Test Runner (Bash)
###############################################################################
# Runs a single Maven test class with optional provider/matrix overrides.
#
# Usage examples:
#   ./scripts/run-single-test.sh
#   TEST_CLASS=RealAPIProviderMatrixIntegrationTest MATRIX_SPEC="openai:onnx:qdrant:SINGLE_TABLE" ./scripts/run-single-test.sh
###############################################################################

# ==================== CONFIGURATION (Edit these) ====================

# Test Configuration
TEST_CLASS="${TEST_CLASS:-RealAPIProviderMatrixIntegrationTest}" # Change this to your test class name

# Key loading:
# - Prefer OPENAI_API_KEY from environment if already set
# - Else read from OPENAI_KEY_FILE (first line is the key)
# - Else fall back to OPENAI_KEY (not recommended to hardcode)
OPENAI_KEY_FILE="${OPENAI_KEY_FILE:-dev2.env}"
OPENAI_KEY="${OPENAI_KEY:-}"

# Pinecone defaults (keep aligned with our manual workflow defaults)
PINECONE_KEY_FILE="${PINECONE_KEY_FILE:-dev.env}"
PINECONE_DEFAULT_INDEX_NAME="${PINECONE_DEFAULT_INDEX_NAME:-ai-fabric}"
PINECONE_DEFAULT_API_HOST="${PINECONE_DEFAULT_API_HOST:-https://ai-fabric-pwkuyn5.svc.aped-4627-b74a.pinecone.io}"
# Optional; typically set in CI as a repo variable
PINECONE_DEFAULT_ENVIRONMENT="${PINECONE_DEFAULT_ENVIRONMENT:-}"

# Provider Configuration (used by some tests; provider-matrix tests should prefer MATRIX_SPEC)
LLM_PROVIDER="${LLM_PROVIDER:-openai}"
EMBEDDING_PROVIDER="${EMBEDDING_PROVIDER:-onnx}"
VECTOR_DB="${VECTOR_DB:-lucene}"
STORAGE_STRATEGY="${STORAGE_STRATEGY:-SINGLE_TABLE}"

# Provider matrix override (for RealAPIProviderMatrixIntegrationTest)
# Format: llm:embedding[:vectordb][:storageStrategy]
MATRIX_SPEC="${MATRIX_SPEC:-}"

# Maven Configuration
MAVEN_PROFILE="${MAVEN_PROFILE:-real-api-test}"
MAVEN_MODULE="${MAVEN_MODULE:-integration-Testing/integration-tests}"
FORK_COUNT="${FORK_COUNT:-1}"
REUSE_FORKS="${REUSE_FORKS:-false}"
LOG_LEVEL="${LOG_LEVEL:-WARN}"

# ==================== END CONFIGURATION ====================

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${CYAN}"
    echo "═══════════════════════════════════════════════════════════════"
    echo " $1"
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

# Header
print_header "Single Integration Test Runner"

# Pre-flight checks
if ! command -v java &> /dev/null; then
    print_error "Java is not installed"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    print_error "Maven is not installed"
    exit 1
fi

# Display Configuration
echo ""
print_info "Test Configuration:"
echo "   Test Class: $TEST_CLASS"
echo "   Maven Module: $MAVEN_MODULE"
echo "   Maven Profile: $MAVEN_PROFILE"
echo "   LLM Provider: $LLM_PROVIDER"
echo "   Embedding: $EMBEDDING_PROVIDER"
echo "   Vector DB: $VECTOR_DB"
echo "   Storage: $STORAGE_STRATEGY"
if [ -n "$MATRIX_SPEC" ]; then
  echo "   Matrix Spec: $MATRIX_SPEC"
fi
echo ""

# Resolve OpenAI key (do not echo it)
if [ -z "${OPENAI_API_KEY:-}" ]; then
  if [ -n "$OPENAI_KEY" ]; then
    export OPENAI_API_KEY="$OPENAI_KEY"
  elif [ -f "$OPENAI_KEY_FILE" ]; then
    KEY_FROM_FILE="$(head -n 1 "$OPENAI_KEY_FILE" | tr -d '\r' | xargs)"
    if [ -n "$KEY_FROM_FILE" ]; then
      export OPENAI_API_KEY="$KEY_FROM_FILE"
    fi
  fi
fi

# Resolve Pinecone key (do not echo it)
# Supports:
# - AI_PROVIDERS_PINECONE_API_KEY / PINECONE_API_KEY from environment
# - Else read from PINECONE_KEY_FILE (first line is the key)
if [ -z "${AI_PROVIDERS_PINECONE_API_KEY:-}" ] && [ -z "${PINECONE_API_KEY:-}" ]; then
  if [ -f "$PINECONE_KEY_FILE" ]; then
    PINECONE_KEY_FROM_FILE="$(head -n 1 "$PINECONE_KEY_FILE" | tr -d '\r' | xargs)"
    if [ -n "$PINECONE_KEY_FROM_FILE" ]; then
      export AI_PROVIDERS_PINECONE_API_KEY="$PINECONE_KEY_FROM_FILE"
      export PINECONE_API_KEY="$PINECONE_KEY_FROM_FILE"
    fi
  fi
fi

# Ensure Pinecone host/index defaults exist when running pinecone vector db tests locally.
# These mirror the defaults used in the manual GitHub Action.
if [ "${VECTOR_DB:-}" = "pinecone" ] || [[ "${MATRIX_SPEC:-}" == *":pinecone:"* ]] || [[ "${MATRIX_SPEC:-}" == *":pinecone" ]]; then
  export AI_PROVIDERS_PINECONE_ENABLED="${AI_PROVIDERS_PINECONE_ENABLED:-true}"

  if [ -z "${AI_PROVIDERS_PINECONE_INDEX_NAME:-}" ] && [ -z "${PINECONE_INDEX_NAME:-}" ]; then
    export AI_PROVIDERS_PINECONE_INDEX_NAME="$PINECONE_DEFAULT_INDEX_NAME"
    export PINECONE_INDEX_NAME="$PINECONE_DEFAULT_INDEX_NAME"
  fi

  if [ -z "${AI_PROVIDERS_PINECONE_API_HOST:-}" ] && [ -z "${PINECONE_API_HOST:-}" ]; then
    export AI_PROVIDERS_PINECONE_API_HOST="$PINECONE_DEFAULT_API_HOST"
    export PINECONE_API_HOST="$PINECONE_DEFAULT_API_HOST"
  fi

  if [ -n "$PINECONE_DEFAULT_ENVIRONMENT" ] && [ -z "${PINECONE_ENVIRONMENT:-}" ] && [ -z "${AI_PROVIDERS_PINECONE_ENVIRONMENT:-}" ]; then
    export PINECONE_ENVIRONMENT="$PINECONE_DEFAULT_ENVIRONMENT"
    export AI_PROVIDERS_PINECONE_ENVIRONMENT="$PINECONE_DEFAULT_ENVIRONMENT"
  fi
fi

# Navigate to repo + module directory (Maven needs a pom.xml)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
AI_INFRA_DIR="$REPO_ROOT/ai-infrastructure-module"
cd "$AI_INFRA_DIR" || exit 1

print_info "Working Directory: $(pwd)"
echo ""

# Build Maven command
MAVEN_CMD="mvn test -pl $MAVEN_MODULE"
MAVEN_CMD="$MAVEN_CMD -Dtest=$TEST_CLASS"
MAVEN_CMD="$MAVEN_CMD -Dspring.profiles.active=$MAVEN_PROFILE"
MAVEN_CMD="$MAVEN_CMD -Dai.providers.llm-provider=$LLM_PROVIDER"
MAVEN_CMD="$MAVEN_CMD -Dai.providers.embedding-provider=$EMBEDDING_PROVIDER"
MAVEN_CMD="$MAVEN_CMD -Dai.vector-db.type=$VECTOR_DB"
MAVEN_CMD="$MAVEN_CMD -Dai-infrastructure.storage.strategy=$STORAGE_STRATEGY"
if [ -n "$MATRIX_SPEC" ]; then
  MAVEN_CMD="$MAVEN_CMD -Dai.providers.real-api.matrix=$MATRIX_SPEC"
fi
MAVEN_CMD="$MAVEN_CMD -DforkCount=$FORK_COUNT"
MAVEN_CMD="$MAVEN_CMD -DreuseForks=$REUSE_FORKS"
MAVEN_CMD="$MAVEN_CMD -Dlogging.level.root=$LOG_LEVEL"

print_success "Executing Maven Test..."
echo -e "${GRAY}   Command: $MAVEN_CMD${NC}"
echo ""

# Run Maven test
START_TIME=$(date +%s)

if eval "$MAVEN_CMD"; then
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    
    echo ""
    print_header "Test Results"
    print_success "Tests PASSED!"
    print_info "Duration: ${DURATION}s"
    echo ""
    
    exit 0
else
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    
    echo ""
    print_header "Test Results"
    print_error "Tests FAILED!"
    print_info "Duration: ${DURATION}s"
    print_warning "Check logs above for details"
    echo ""
    
    exit 1
fi
