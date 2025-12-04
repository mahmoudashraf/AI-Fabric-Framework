#!/bin/bash

###############################################################################
# Relationship Query Real API Integration Test Runner
# 
# This script runs relationship query integration tests with OpenAI API.
#
# Usage:
#   ./run-relationship-query-realapi-tests.sh
#
# Prerequisites:
#   - Java 21+
#   - Maven 3.8+
#   - Dependencies must be built and installed (run 'mvn clean install -DskipTests' from parent)
#   - OPENAI_API_KEY environment variable set
#
# Note: This script assumes dependencies are already built. In CI/CD workflows,
#       the build step should run 'mvn clean install -DskipTests' first.
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
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Configuration
PROFILE="realapi"
TEST_MODULE="relationship-query-integration-tests"

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

# Check if dependencies are built (check for core module in local repo or target)
PARENT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CORE_TARGET="${PARENT_DIR}/ai-infrastructure-core/target"
if [ ! -d "$CORE_TARGET" ] || [ ! -f "$CORE_TARGET/ai-infrastructure-core-*.jar" ] 2>/dev/null; then
    print_warning "Dependencies may not be built. Attempting to build..."
    cd "$PARENT_DIR"
    if ! mvn clean install -DskipTests -B -q; then
        print_error "Failed to build dependencies. Please run 'mvn clean install -DskipTests' from the parent module first."
        exit 1
    fi
    cd "$SCRIPT_DIR"
    print_success "Dependencies built successfully"
else
    print_success "Dependencies appear to be built"
fi

# Test Configuration
print_header "Test Configuration"

print_info "Test Module: $TEST_MODULE"
print_info "Profile: $TEST_MODULE"
print_info "Test Classes: All *RealApiIntegrationTest.java in realapi/ directory"

# Build Maven command
print_header "Building Maven Command"

cd "$SCRIPT_DIR"

# Note: This assumes dependencies are already built and installed.
# The workflow should run 'mvn clean install -DskipTests' from the parent module first.
MAVEN_COMMAND="mvn test"
MAVEN_COMMAND="$MAVEN_COMMAND -Preal-api-test"
MAVEN_COMMAND="$MAVEN_COMMAND -DforkCount=1"
MAVEN_COMMAND="$MAVEN_COMMAND -DreuseForks=false"

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

