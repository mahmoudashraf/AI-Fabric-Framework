# Provider Registry Guide

## Overview

The Provider Registry is a centralized YAML configuration file that serves as the single source of truth for all AI providers (LLM and Embedding) in the AI Infrastructure module. It enables automatic provider discovery, validation, and configuration.

## Location

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml`

This file is loaded at runtime by the `ProviderRegistryService` and used throughout the test framework and GitHub Actions workflows.

## Structure

The registry is organized into two main sections:

```yaml
providers:
  llm:
    provider-name:
      # LLM provider configuration
  embedding:
    provider-name:
      # Embedding provider configuration
```

## Provider Definition Fields

Each provider definition can include the following fields:

### Required Fields

- **`name`**: Internal provider identifier (lowercase, no spaces)
- **`displayName`**: Human-readable name for UI display
- **`description`**: Brief description of the provider
- **`enabled`**: Boolean indicating if provider is enabled (default: `true`)

### Authentication Fields

- **`apiKeyEnvVar`**: Environment variable name for API key (e.g., `OPENAI_API_KEY`)
- **`required`**: Boolean indicating if API key is required (default: `false`)
- **`endpointEnvVar`**: Optional environment variable for endpoint URL (e.g., `AZURE_ENDPOINT`)
- **`deploymentNameEnvVar`**: Optional environment variable for deployment name (e.g., `AZURE_DEPLOYMENT_NAME`)
- **`embeddingDeploymentNameEnvVar`**: Optional environment variable for embedding deployment (e.g., `AZURE_EMBEDDING_DEPLOYMENT_NAME`)
- **`additionalEnvVars`**: List of additional required environment variables

### Configuration Fields

- **`defaultModel`**: Default model name to use
- **`baseUrl`**: Base URL for API calls
- **`dimensions`**: Embedding dimensions (for embedding providers)
- **`supportedFeatures`**: List of supported features (e.g., `chat_completions`, `streaming`)
- **`notes`**: Additional notes or important information

## Example Provider Definition

### Simple Provider (OpenAI)

```yaml
openai:
  name: "openai"
  displayName: "OpenAI (GPT-4, GPT-3.5)"
  apiKeyEnvVar: "OPENAI_API_KEY"
  required: true
  defaultModel: "gpt-4o-mini"
  baseUrl: "https://api.openai.com/v1"
  enabled: true
  description: "OpenAI GPT models"
  supportedFeatures:
    - "chat_completions"
    - "streaming"
    - "function_calling"
```

### Complex Provider (Azure)

```yaml
azure:
  name: "azure"
  displayName: "Azure AI Services"
  apiKeyEnvVar: "AZURE_API_KEY"
  endpointEnvVar: "AZURE_ENDPOINT"
  deploymentNameEnvVar: "AZURE_DEPLOYMENT_NAME"
  required: true
  defaultModel: "gpt-4"
  baseUrl: "${AZURE_ENDPOINT}"
  enabled: true
  description: "Azure OpenAI / AI Services (supports OpenAI-compatible and Foundry formats)"
  supportedFeatures:
    - "chat_completions"
    - "openai_compatible"
    - "foundry_format"
  additionalEnvVars:
    - "AZURE_API_VERSION"
    - "AZURE_EMBEDDING_DEPLOYMENT_NAME"
  notes: "Supports both /openai/v1/ and /models/ endpoint formats"
```

### Optional Provider (ONNX)

```yaml
onnx:
  name: "onnx"
  displayName: "ONNX (Local)"
  apiKeyEnvVar: null
  required: false
  enabled: true
  description: "Local ONNX embedding model"
  defaultModel: "all-MiniLM-L6-v2"
  dimensions: 384
  supportedFeatures:
    - "local_execution"
    - "no_api_key"
```

## Using the Registry

### In Java Code

```java
// Get registry service instance
ProviderRegistryService registry = ProviderRegistryService.getInstance();

// Get all LLM providers
List<ProviderDefinition> llmProviders = registry.getLLMProviders();

// Get available (configured) providers
List<ProviderDefinition> availableLLM = registry.getAvailableLLMProviders();

// Get specific provider
ProviderDefinition openai = registry.getProvider("openai", ProviderType.LLM);

// Check if provider is available
boolean isAvailable = registry.isProviderAvailable("openai", ProviderType.LLM);

// Get required environment variables
List<String> requiredVars = registry.getRequiredEnvVars("azure", ProviderType.LLM);
```

### In Test Framework

The registry is automatically used by `AbstractProviderMatrixIntegrationTest`:

1. **Provider Discovery**: Automatically discovers available providers from registry
2. **Validation**: Validates provider combinations before test execution
3. **Error Messages**: Uses registry to generate helpful error messages

### In GitHub Actions

The registry is used by validation scripts:

- `validate-provider-registry.sh`: Validates registry YAML syntax
- `validate-provider-availability.sh`: Checks required env vars
- `generate-provider-summary.js`: Generates documentation from registry

## Provider Availability

A provider is considered "available" if:

1. **Enabled**: `enabled: true` in registry
2. **Required Variables**: All required environment variables are set
3. **Optional Providers**: Providers with `required: false` are always available

### Availability Check Logic

```java
public boolean isAvailable() {
    if (!enabled) return false;
    if (!required) return true; // Optional providers
    
    // Check required environment variables
    List<String> requiredVars = getRequiredEnvVars();
    for (String var : requiredVars) {
        if (System.getenv(var) == null || System.getenv(var).trim().isEmpty()) {
            return false;
        }
    }
    return true;
}
```

## Best Practices

### Naming Conventions

- **Registry Name**: Lowercase, no spaces (e.g., `openai`, `azure`)
- **Display Name**: Human-readable with provider branding (e.g., "OpenAI (GPT-4)")
- **Environment Variables**: Uppercase with underscores (e.g., `OPENAI_API_KEY`)

### Required vs Optional

- **Required Providers**: Set `required: true` and specify `apiKeyEnvVar`
- **Optional Providers**: Set `required: false` (e.g., ONNX, REST)

### Additional Environment Variables

For providers with complex configuration (like Azure), use `additionalEnvVars`:

```yaml
additionalEnvVars:
  - "AZURE_API_VERSION"
  - "AZURE_EMBEDDING_DEPLOYMENT_NAME"
```

### Notes Field

Use the `notes` field for important information:

```yaml
notes: "Supports both /openai/v1/ and /models/ endpoint formats"
```

## Validation

### Registry Validation

Run the validation script to check registry:

```bash
bash .github/scripts/validate-provider-registry.sh
```

This will:
- Validate YAML syntax
- List all providers
- Check workflow sync
- Report any issues

### Provider Availability Validation

Validate specific provider combination:

```bash
bash .github/scripts/validate-provider-availability.sh openai onnx
```

## Troubleshooting

### Provider Not Found

**Error**: `Provider 'xyz' not found in registry`

**Solution**: Check that provider name matches exactly (case-sensitive). Use `getProviderNames()` to see available providers.

### Provider Not Available

**Error**: `Missing required environment variables: OPENAI_API_KEY`

**Solution**: Set the required environment variables as specified in the registry.

### Registry Not Loading

**Error**: `Failed to load provider registry`

**Solution**: 
- Check file path: `ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml`
- Validate YAML syntax
- Check file permissions

## Extension

To add a new provider, see: [ADDING_NEW_PROVIDER.md](./ADDING_NEW_PROVIDER.md)

## Related Documentation

- [GitHub Actions Provider Guide](./GITHUB_ACTIONS_PROVIDER_GUIDE.md)
- [Adding New Provider Guide](./ADDING_NEW_PROVIDER.md)
- [Migration Guide](./MIGRATION_GUIDE.md)
