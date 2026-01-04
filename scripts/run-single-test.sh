#!/bin/bash

###############################################################################
# Relationship Query Integration Test Runner (Bash)
###############################################################################
# This script runs a specific integration test with configurable parameters
# Usage: ./run-single-test.sh
###############################################################################

# ==================== CONFIGURATION (Edit these) ====================

# Test Configuration
TEST_CLASS="OrchestratorAccessPolicyRealApiIntegrationTest"  # Change this to your test class name
OPENAI_KEY="sk-proj-YOUR-API-KEY-HERE"                       # Change this to your OpenAI API key

# Provider Configuration
LLM_PROVIDER="openai"
EMBEDDING_PROVIDER="onnx"
VECTOR_DB="lucene"
STORAGE_STRATEGY="SINGLE_TABLE"

# Maven Configuration
MAVEN_PROFILE="realapi"
FORK_COUNT="1"
REUSE_FORKS="false"
LOG_LEVEL="WARN"

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
print_header "Relationship Query Integration Test Runner"

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
echo "   Maven Profile: $MAVEN_PROFILE"
echo "   LLM Provider: $LLM_PROVIDER"
echo "   Embedding: $EMBEDDING_PROVIDER"
echo "   Vector DB: $VECTOR_DB"
echo "   Storage: $STORAGE_STRATEGY"
echo ""

# Set environment variables
export OPENAI_API_KEY="$OPENAI_KEY"

# Navigate to script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

print_info "Working Directory: $(pwd)"
echo ""

# Build Maven command
MAVEN_CMD="mvn test"
MAVEN_CMD="$MAVEN_CMD -Dtest=$TEST_CLASS"
MAVEN_CMD="$MAVEN_CMD -Dspring.profiles.active=$MAVEN_PROFILE"
MAVEN_CMD="$MAVEN_CMD -Dai.providers.llm-provider=$LLM_PROVIDER"
MAVEN_CMD="$MAVEN_CMD -Dai.providers.embedding-provider=$EMBEDDING_PROVIDER"
MAVEN_CMD="$MAVEN_CMD -Dai.vector-db.type=$VECTOR_DB"
MAVEN_CMD="$MAVEN_CMD -Dai-infrastructure.storage.strategy=$STORAGE_STRATEGY"
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

