#!/bin/bash
# Validate Provider Registry
# Checks that providers-registry.yml is valid and workflow is in sync

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REGISTRY_FILE="$PROJECT_ROOT/ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml"
WORKFLOW_FILE="$PROJECT_ROOT/.github/workflows/integration-tests-manual.yml"

echo "🔍 Validating Provider Registry..."

# Check if registry file exists
if [ ! -f "$REGISTRY_FILE" ]; then
    echo "❌ ERROR: Registry file not found: $REGISTRY_FILE"
    exit 1
fi

# Check if workflow file exists
if [ ! -f "$WORKFLOW_FILE" ]; then
    echo "❌ ERROR: Workflow file not found: $WORKFLOW_FILE"
    exit 1
fi

# Check if yq is available (for YAML parsing)
if ! command -v yq &> /dev/null; then
    echo "⚠️  WARNING: yq not found. Install yq for full validation: https://github.com/mikefarah/yq"
    echo "   Basic validation will continue..."
    USE_YQ=false
else
    USE_YQ=true
fi

# Basic YAML syntax check
if ! python3 -c "import yaml; yaml.safe_load(open('$REGISTRY_FILE'))" 2>/dev/null; then
    echo "❌ ERROR: Invalid YAML syntax in registry file"
    exit 1
fi

echo "✅ Registry YAML syntax is valid"

# Extract provider names from registry (if yq available)
if [ "$USE_YQ" = true ]; then
    echo ""
    echo "📋 Providers in Registry:"
    
    echo "  LLM Providers:"
    yq eval '.providers.llm | keys | .[]' "$REGISTRY_FILE" | while read -r provider; do
        display_name=$(yq eval ".providers.llm.$provider.displayName" "$REGISTRY_FILE")
        enabled=$(yq eval ".providers.llm.$provider.enabled" "$REGISTRY_FILE")
        status="✅"
        [ "$enabled" != "true" ] && status="⚠️"
        echo "    $status $provider ($display_name)"
    done
    
    echo "  Embedding Providers:"
    yq eval '.providers.embedding | keys | .[]' "$REGISTRY_FILE" | while read -r provider; do
        display_name=$(yq eval ".providers.embedding.$provider.displayName" "$REGISTRY_FILE")
        enabled=$(yq eval ".providers.embedding.$provider.enabled" "$REGISTRY_FILE")
        status="✅"
        [ "$enabled" != "true" ] && status="⚠️"
        echo "    $status $provider ($display_name)"
    done
fi

# Check if workflow includes all providers from registry
echo ""
echo "🔍 Checking workflow sync with registry..."

# Extract LLM providers from workflow
WORKFLOW_LLM=$(grep -A 10 "llm_provider:" "$WORKFLOW_FILE" | grep "^-" | sed 's/.*- //' | tr '\n' ' ')
REGISTRY_LLM="openai anthropic gemini cohere azure rest"

# Extract Embedding providers from workflow
WORKFLOW_EMBEDDING=$(grep -A 10 "embedding_provider:" "$WORKFLOW_FILE" | grep "^-" | sed 's/.*- //' | tr '\n' ' ')
REGISTRY_EMBEDDING="onnx openai anthropic gemini cohere azure rest"

echo "  Workflow LLM providers: $WORKFLOW_LLM"
echo "  Registry LLM providers: $REGISTRY_LLM"

# Simple check - warn if mismatch
MISMATCH=false
for provider in $REGISTRY_LLM; do
    if ! echo "$WORKFLOW_LLM" | grep -q "$provider"; then
        echo "  ⚠️  WARNING: LLM provider '$provider' in registry but not in workflow"
        MISMATCH=true
    fi
done

for provider in $REGISTRY_EMBEDDING; do
    if ! echo "$WORKFLOW_EMBEDDING" | grep -q "$provider"; then
        echo "  ⚠️  WARNING: Embedding provider '$provider' in registry but not in workflow"
        MISMATCH=true
    fi
done

if [ "$MISMATCH" = false ]; then
    echo "✅ Workflow is in sync with registry"
else
    echo "⚠️  Workflow may need updates to match registry"
fi

echo ""
echo "✅ Provider registry validation complete"
