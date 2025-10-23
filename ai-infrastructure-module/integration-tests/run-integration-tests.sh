#!/bin/bash

# AI Infrastructure Integration Tests Runner
# This script runs the integration tests for the AI Infrastructure module

echo "🚀 Running AI Infrastructure Integration Tests"
echo "=============================================="

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "❌ ERROR: pom.xml not found. Please run this script from the integration-tests directory."
    exit 1
fi

# Function to run tests with a specific profile
run_tests() {
    local profile=$1
    local description=$2
    
    echo ""
    echo "🧪 Running $description..."
    echo "Profile: $profile"
    echo "----------------------------------------"
    
    mvn test -P$profile
    
    if [ $? -eq 0 ]; then
        echo "✅ $description completed successfully!"
    else
        echo "❌ $description failed!"
        return 1
    fi
}

# Function to run real API tests
run_real_api_tests() {
    echo ""
    echo "🧪 Running Real API Tests..."
    echo "Profile: real-api-tests"
    echo "----------------------------------------"
    
    # Check if OpenAI API key is set
    if [ -z "$OPENAI_API_KEY" ]; then
        echo "❌ ERROR: OPENAI_API_KEY environment variable is not set"
        echo ""
        echo "To run real API tests, you need to:"
        echo "1. Get an OpenAI API key from https://platform.openai.com/api-keys"
        echo "2. Set the environment variable: "
        echo "   export OPENAI_API_KEY=your_actual_openai_api_key"
        echo "3. Run this script again"
        echo ""
        echo "Skipping real API tests..."
        return 0
    fi

    echo "✅ OpenAI API Key found: ${OPENAI_API_KEY:0:10}..."
    echo "This will make actual calls to OpenAI APIs and may incur costs."
    echo ""
    
    mvn test -Preal-api-tests
    
    if [ $? -eq 0 ]; then
        echo "✅ Real API tests completed successfully!"
    else
        echo "❌ Real API tests failed!"
        return 1
    fi
}

# Main execution
echo "Available test profiles:"
echo "1. unit-tests - Unit tests only (default)"
echo "2. integration-tests - Integration tests only"
echo "3. real-api-tests - Real API tests (requires OpenAI API key)"
echo "4. performance-tests - Performance tests only"
echo "5. all-tests - All tests"
echo ""

# Parse command line arguments
if [ "$1" = "unit" ]; then
    run_tests "unit-tests" "Unit Tests"
elif [ "$1" = "integration" ]; then
    run_tests "integration-tests" "Integration Tests"
elif [ "$1" = "real-api" ]; then
    run_real_api_tests
elif [ "$1" = "performance" ]; then
    run_tests "performance-tests" "Performance Tests"
elif [ "$1" = "all" ]; then
    echo "Running all test suites..."
    run_tests "unit-tests" "Unit Tests" && \
    run_tests "integration-tests" "Integration Tests" && \
    run_tests "performance-tests" "Performance Tests" && \
    run_real_api_tests
else
    echo "Usage: $0 [unit|integration|real-api|performance|all]"
    echo ""
    echo "Examples:"
    echo "  $0 unit          # Run unit tests only"
    echo "  $0 integration   # Run integration tests only"
    echo "  $0 real-api      # Run real API tests (requires OpenAI API key)"
    echo "  $0 performance   # Run performance tests only"
    echo "  $0 all           # Run all tests"
    echo ""
    echo "Running default unit tests..."
    run_tests "unit-tests" "Unit Tests"
fi

echo ""
echo "🎉 Integration test execution completed!"
echo "Check the output above for any failures or errors."