# Azure AI Services (Foundry) Configuration Guide

This guide shows how to configure Azure AI Services (Foundry) with the AI Infrastructure module.

## Your Azure AI Services (Foundry) Credentials

**Never commit real credentials to git.** Use the placeholders below and supply secrets via environment variables or your CI secret store.

- **API Key**: `YOUR_AZURE_API_KEY`
- **Endpoint**: `https://mahan-mk5op536-eastus2.services.ai.azure.com/models/chat/completions?api-version=2024-05-01-preview`
- **Base Endpoint**: `https://mahan-mk5op536-eastus2.services.ai.azure.com/models`
- **Deployment Name**: `DeepSeek-V3.2` (informational, not used in URL for Foundry)
- **API Version**: `2024-05-01-preview`

## Configuration Options

Azure AI Services (Foundry) uses a different endpoint format than Azure OpenAI. You have two options:

### Option 1: Use Full Endpoint (Recommended)

If your endpoint already includes the full path (`/models/chat/completions`), use it as-is:

```bash
export AZURE_API_KEY="YOUR_AZURE_API_KEY"
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models/chat/completions?api-version=2024-05-01-preview"
export AZURE_DEPLOYMENT_NAME="DeepSeek-V3.2"  # Optional for Foundry, but good to set
export AZURE_ENABLED="true"
```

### Option 2: Use Base Endpoint

If you want to use the base endpoint and let the provider add the path:

```bash
export AZURE_API_KEY="YOUR_AZURE_API_KEY"
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models"
export AZURE_API_VERSION="2024-05-01-preview"
export AZURE_DEPLOYMENT_NAME="DeepSeek-V3.2"  # Optional for Foundry
export AZURE_ENABLED="true"
```

The provider will automatically detect the Foundry format and construct the correct URL.

## Embeddings Configuration

For embeddings, you'll need a separate endpoint. If you have an embeddings deployment:

```bash
export AZURE_EMBEDDING_DEPLOYMENT_NAME="your-embedding-deployment-name"
```

Or if your embeddings endpoint is different:

```bash
# Option 1: Full embeddings endpoint
export AZURE_EMBEDDING_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models/embeddings?api-version=2024-05-01-preview"

# Option 2: Base endpoint (will add /embeddings automatically)
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models"
```

## Differences: Azure OpenAI vs Azure AI Services (Foundry)

| Feature | Azure OpenAI | Azure AI Services (Foundry) |
|---------|-------------|----------------------------|
| Endpoint Format | `{endpoint}/openai/deployments/{deployment}/chat/completions` | `{endpoint}/models/chat/completions` |
| Deployment in URL | Required | Not required (deployment name is informational) |
| Domain | `*.openai.azure.com` | `*.services.ai.azure.com` |
| API Version | `2024-02-15-preview` | `2024-05-01-preview` |

## Quick Setup Script

Create a file `setup-azure-foundry.sh`:

```bash
#!/bin/bash

# Azure AI Services (Foundry) Configuration
export AZURE_API_KEY="YOUR_AZURE_API_KEY"
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models/chat/completions?api-version=2024-05-01-preview"
export AZURE_DEPLOYMENT_NAME="DeepSeek-V3.2"
export AZURE_API_VERSION="2024-05-01-preview"
export AZURE_ENABLED="true"

# Optional: If you have embeddings
# export AZURE_EMBEDDING_DEPLOYMENT_NAME="your-embedding-deployment"

echo "Azure AI Services (Foundry) configured!"
echo "Endpoint: $AZURE_ENDPOINT"
echo "Deployment: $AZURE_DEPLOYMENT_NAME"
```

Make it executable and run:
```bash
chmod +x setup-azure-foundry.sh
source setup-azure-foundry.sh
```

## Testing the Configuration

After setting the environment variables, you can test the configuration:

```bash
# Run a simple integration test
cd ai-infrastructure-module/integration-Testing/integration-tests
mvn test -Dtest=RealAPIIntegrationTest#testRealEmbeddingGeneration -Dai.providers.llm-provider=azure -Dai.providers.embedding-provider=azure
```

## Application Configuration

You can also configure it in `application.yml` or `application-real-api-test.yml`:

```yaml
ai:
  providers:
    llm-provider: azure
    embedding-provider: azure  # or "onnx" if you don't have embeddings
    azure:
      enabled: true
      api-key: ${AZURE_API_KEY}
      endpoint: ${AZURE_ENDPOINT}
      deployment-name: ${AZURE_DEPLOYMENT_NAME:DeepSeek-V3.2}
      api-version: ${AZURE_API_VERSION:2024-05-01-preview}
      timeout: 60
      priority: 80
```

## Notes

1. **Deployment Name**: For Azure AI Services (Foundry), the deployment name is informational and not used in the URL path. However, it's still good practice to set it for logging and tracking purposes.

2. **API Version**: The default API version for Azure OpenAI is `2024-02-15-preview`, but Azure AI Services (Foundry) uses `2024-05-01-preview`. Make sure to set the correct version.

3. **Endpoint Detection**: The provider automatically detects whether you're using Azure OpenAI or Azure AI Services (Foundry) based on the endpoint format:
   - If endpoint contains `/models` or `services.ai.azure.com` → Foundry format
   - Otherwise → Azure OpenAI format

4. **Embeddings**: If you don't have a separate embeddings deployment, you can use ONNX for embeddings:
   ```yaml
   ai:
     providers:
       llm-provider: azure
       embedding-provider: onnx  # Use ONNX for embeddings
   ```

## Troubleshooting

### Issue: "Endpoint not found" or "404 Not Found"
- **Solution**: Make sure your endpoint includes the full path or base path correctly
- For Foundry: Should contain `/models` in the path
- Check that the API version matches your deployment

### Issue: "Invalid API key"
- **Solution**: Verify the API key is correct and hasn't been rotated
- Make sure there are no extra spaces or newlines in the key

### Issue: "Deployment not found"
- **Solution**: For Foundry, this shouldn't happen as deployment name is not in the URL
- If you see this, check that you're using the correct endpoint format

## Security Reminder

⚠️ **Never commit API keys to version control!**

Always use environment variables or secure key management systems.
