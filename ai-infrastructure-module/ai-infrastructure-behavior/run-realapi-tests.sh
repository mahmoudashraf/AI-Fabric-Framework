#!/bin/bash

###############################################################################
# Behavior Real API Integration Test Runner
# 
# This script runs Real API integration tests for the behavior module with
# different provider combinations (LLM provider, embedding provider, vector database).
# 
# It runs Real API tests in realapi/ package (BehaviorRealApi*IntegrationTest)
# that make actual API calls to external providers (e.g., OpenAI).
#
# Usage:
#   ./run-realapi-tests.sh [LLM:EMBEDDING:VECTOR_DB]
#
# Examples:
#   # Run with default (OpenAI + ONNX + Lucene)
#   ./run-realapi-tests.sh
#
#   # Run with specific combination
#   ./run-realapi-tests.sh "openai:onnx:lucene"
#   ./run-realapi-tests.sh "openai:onnx"
#
#   # Run multiple combinations (for matrix test)
#   ./run-realapi-tests.sh "openai:onnx:lucene,openai:openai:lucene"
#
# Prerequisites:
#   - Java 21+
#   - Maven 3.8+
#   - OPENAI_API_KEY environment variable set (required)
#
# Environment Variables:
#   OPENAI_API_KEY           - OpenAI API key (required)
#   AI_INFRASTRUCTURE_LLM_PROVIDER - LLM provider (default: openai)
#   AI_INFRASTRUCTURE_EMBEDDING_PROVIDER - Embedding provider (default: onnx)
#   AI_INFRASTRUCTURE_VECTOR_DATABASE - Vector database (default: lucene)
#   AI_INFRASTRUCTURE_PERSISTENCE_DATABASE - Persistence database (default: postgres)
#
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

# Configuration
PROFILE="real-api-test"
MAVEN_MODULE="ai-infrastructure-behavior"

# Parse matrix specification from command line
MATRIX_SPEC="${1:-openai:onnx:lucene}"

# Pre-flight checks
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE} Running Behavior Real API Integration Tests ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"

if ! command -v java &> /dev/null; then
    echo -e "${RED}✗ Java is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java found: $(java -version 2>&1 | head -n 1)${NC}"

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}✗ Maven is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Maven found: $(mvn -v 2>&1 | head -n 1)${NC}"

if [ -z "$OPENAI_API_KEY" ]; then
    echo -e "${RED}✗ OPENAI_API_KEY environment variable is not set${NC}"
    echo "Please set: export OPENAI_API_KEY='your-api-key'"
    exit 1
fi
echo -e "${GREEN}✓ OpenAI API key is configured${NC}"

# Parse matrix specification
IFS=':' read -r LLM_PROVIDER EMBEDDING_PROVIDER VECTOR_DB <<< "$MATRIX_SPEC"

# Set defaults if not provided
LLM_PROVIDER="${LLM_PROVIDER:-openai}"
EMBEDDING_PROVIDER="${EMBEDDING_PROVIDER:-onnx}"
VECTOR_DB="${VECTOR_DB:-lucene}"

echo -e "${BLUE}ℹ Provider Configuration:${NC}"
echo -e "  LLM Provider: ${LLM_PROVIDER}"
echo -e "  Embedding Provider: ${EMBEDDING_PROVIDER}"
echo -e "  Vector Database: ${VECTOR_DB}"

# Build Maven command
echo -e "${BLUE}ℹ Building Maven Command${NC}"

# Navigate to the module directory
cd "$SCRIPT_DIR"

MAVEN_COMMAND="mvn test -P realapi-tests"
MAVEN_COMMAND="$MAVEN_COMMAND -Dspring.profiles.active=$PROFILE"
MAVEN_COMMAND="$MAVEN_COMMAND -Dai.behavior.realapi.enabled=true"
MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.llm-provider=$LLM_PROVIDER"
MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.embedding-provider=$EMBEDDING_PROVIDER"
MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.vector-database=$VECTOR_DB"
MAVEN_COMMAND="$MAVEN_COMMAND -DforkCount=1 -DreuseForks=false"

echo -e "${BLUE}ℹ Working Directory: $(pwd)${NC}"
echo -e "${BLUE}ℹ Maven Profile: realapi-tests${NC}"
echo -e "${BLUE}ℹ Spring Profile: $PROFILE${NC}"

# Display the command
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Command:${NC}"
echo "  $MAVEN_COMMAND"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"

# Execute the tests
start_time=$(date +%s)

if eval "$MAVEN_COMMAND"; then
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    echo -e "${GREEN}✓ All Behavior Real API tests passed in ${duration}s${NC}"
    exit 0
else
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    echo -e "${RED}✗ Some Behavior Real API tests failed or errored in ${duration}s${NC}"
    exit 1
fi

