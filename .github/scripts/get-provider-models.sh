#!/bin/bash
# Get available models for a provider from the registry
# Usage: get-provider-models.sh <provider-name> <provider-type>
# Example: get-provider-models.sh openai llm

PROVIDER_NAME="$1"
PROVIDER_TYPE="$2"

if [ -z "$PROVIDER_NAME" ] || [ -z "$PROVIDER_TYPE" ]; then
    echo "Usage: get-provider-models.sh <provider-name> <provider-type>"
    exit 1
fi

REGISTRY_FILE="ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml"

if [ ! -f "$REGISTRY_FILE" ]; then
    echo "Registry file not found: $REGISTRY_FILE"
    exit 1
fi

# Extract default model from registry using Python
python3 << PYTHON_SCRIPT
import yaml
import sys
import os

provider_name = "$PROVIDER_NAME"
provider_type = "$PROVIDER_TYPE"
registry_file = "$REGISTRY_FILE"

try:
    with open(registry_file, 'r') as f:
        data = yaml.safe_load(f)
    
    providers = data.get('providers', {}).get(provider_type, {})
    provider = providers.get(provider_name)
    
    if provider:
        default_model = provider.get('defaultModel', '')
        if default_model:
            print(default_model)
        else:
            print("")
    else:
        print("")
except Exception as e:
    print("", file=sys.stderr)
PYTHON_SCRIPT
