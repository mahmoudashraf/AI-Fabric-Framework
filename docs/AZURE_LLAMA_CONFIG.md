# Azure AI Services - Llama-4-Maverick Configuration

## Configuration Summary

Your Azure AI Services setup uses:

- **LLM Model**: `Llama-4-Maverick-17B-128E-Instruct-FP8`
- **LLM Endpoint**: `https://mahan-mk5op536-eastus2.services.ai.azure.com/openai/v1`
- **Embedding Endpoint**: `https://mahan-mk5op536-eastus2.services.ai.azure.com/models`
- **API Key**: `YOUR_AZURE_API_KEY`

## Environment Variables

```bash
export AZURE_API_KEY="YOUR_AZURE_API_KEY"
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/openai/v1"
export AZURE_DEPLOYMENT_NAME="Llama-4-Maverick-17B-128E-Instruct-FP8"
export AZURE_EMBEDDING_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="embedding-model"
export AZURE_API_VERSION="2024-05-01-preview"
export AZURE_ENABLED="true"
```

## Endpoint Format

The Llama model uses **OpenAI-compatible format** (`/openai/v1/`), which is different from the Foundry format (`/models/`). The provider automatically detects this format and:

1. Uses `/chat/completions` endpoint (not `/models/chat/completions`)
2. Includes the model name in the request body (not in the URL)
3. Uses the same authentication (`api-key` header)

## Running Tests

### Quick Setup
```bash
source /workspace/setup-azure-foundry.sh
```

### Run Tests
```bash
source /workspace/run-azure-tests.sh
```

Or manually:
```bash
cd /workspace/ai-infrastructure-module/integration-Testing/integration-tests

export AZURE_API_KEY="YOUR_AZURE_API_KEY"
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/openai/v1"
export AZURE_DEPLOYMENT_NAME="Llama-4-Maverick-17B-128E-Instruct-FP8"
export AZURE_EMBEDDING_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="embedding-model"
export AZURE_API_VERSION="2024-05-01-preview"
export AZURE_ENABLED="true"

mvn test -Dtest=RealAPIIntegrationTest \
  -Dai.providers.llm-provider=azure \
  -Dai.providers.embedding-provider=azure
```

## API Request Format

For the Llama model (OpenAI-compatible format), the provider sends:

**URL**: `https://mahan-mk5op536-eastus2.services.ai.azure.com/openai/v1/chat/completions`

**Headers**:
```
Content-Type: application/json
api-key: YOUR_AZURE_API_KEY
```

**Body**:
```json
{
  "model": "Llama-4-Maverick-17B-128E-Instruct-FP8",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "temperature": 0.3,
  "max_tokens": 2000
}
```

## Differences from Foundry Format

| Feature | Foundry Format | OpenAI-Compatible Format (Llama) |
|---------|---------------|----------------------------------|
| Endpoint Path | `/models/chat/completions` | `/openai/v1/chat/completions` |
| Model in URL | No | No |
| Model in Body | No | Yes |
| API Version | Required in query | Not required |

## Notes

1. The provider automatically detects the endpoint format and adjusts the request accordingly
2. The model name is included in the request body for OpenAI-compatible format
3. Both LLM and embeddings can use different endpoint formats (as configured)
4. The embedding endpoint still uses Foundry format (`/models/embeddings`)
