#!/bin/bash
# Validate Provider Availability
# Checks if required environment variables are set for selected providers

set -e

LLM_PROVIDER="${1:-}"
EMBEDDING_PROVIDER="${2:-}"

if [ -z "$LLM_PROVIDER" ] || [ -z "$EMBEDDING_PROVIDER" ]; then
    echo "Usage: validate-provider-availability.sh <llm_provider> <embedding_provider>"
    exit 1
fi

echo "🔍 Validating provider availability for: LLM=$LLM_PROVIDER, Embedding=$EMBEDDING_PROVIDER"
echo ""

ERRORS=0

# Function to check environment variable
check_env_var() {
    local var_name=$1
    local provider_name=$2
    local provider_type=$3
    
    if [ -z "${!var_name}" ]; then
        echo "❌ ERROR: $provider_type provider '$provider_name' requires environment variable: $var_name"
        ERRORS=$((ERRORS + 1))
        return 1
    else
        echo "✅ $var_name is set"
        return 0
    fi
}

# Validate LLM provider
case "$LLM_PROVIDER" in
    openai)
        check_env_var "OPENAI_API_KEY" "openai" "LLM" || true
        ;;
    anthropic)
        check_env_var "ANTHROPIC_API_KEY" "anthropic" "LLM" || true
        ;;
    gemini)
        check_env_var "GEMINI_API_KEY" "gemini" "LLM" || true
        ;;
    cohere)
        check_env_var "COHERE_API_KEY" "cohere" "LLM" || true
        ;;
    azure)
        check_env_var "AZURE_API_KEY" "azure" "LLM" || true
        check_env_var "AZURE_ENDPOINT" "azure" "LLM" || true
        check_env_var "AZURE_DEPLOYMENT_NAME" "azure" "LLM" || true
        ;;
    rest)
        echo "ℹ️  REST provider does not require API keys"
        ;;
    onnx)
        echo "❌ ERROR: ONNX is not a valid LLM provider"
        ERRORS=$((ERRORS + 1))
        ;;
    *)
        echo "⚠️  WARNING: Unknown LLM provider: $LLM_PROVIDER"
        ;;
esac

echo ""

# Validate Embedding provider
case "$EMBEDDING_PROVIDER" in
    onnx)
        echo "ℹ️  ONNX provider does not require API keys"
        ;;
    openai)
        check_env_var "OPENAI_API_KEY" "openai" "Embedding" || true
        ;;
    anthropic)
        check_env_var "ANTHROPIC_API_KEY" "anthropic" "Embedding" || true
        ;;
    gemini)
        check_env_var "GEMINI_API_KEY" "gemini" "Embedding" || true
        ;;
    cohere)
        check_env_var "COHERE_API_KEY" "cohere" "Embedding" || true
        ;;
    azure)
        check_env_var "AZURE_API_KEY" "azure" "Embedding" || true
        check_env_var "AZURE_ENDPOINT" "azure" "Embedding" || true
        check_env_var "AZURE_EMBEDDING_DEPLOYMENT_NAME" "azure" "Embedding" || true
        ;;
    rest)
        echo "ℹ️  REST provider does not require API keys"
        ;;
    *)
        echo "⚠️  WARNING: Unknown Embedding provider: $EMBEDDING_PROVIDER"
        ;;
esac

echo ""

if [ $ERRORS -eq 0 ]; then
    echo "✅ All required environment variables are set"
    exit 0
else
    echo "❌ Validation failed: $ERRORS error(s) found"
    echo ""
    echo "Please set the required environment variables before running tests."
    exit 1
fi
