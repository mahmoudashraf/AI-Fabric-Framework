# Azure OpenAI Setup Guide

This guide provides step-by-step instructions to obtain the required Azure OpenAI credentials and deployment names for the AI Infrastructure module.

## Prerequisites

- An active Azure subscription
- Access to Azure Portal (https://portal.azure.com)
- Permission to create Azure resources (Owner or Contributor role)

## Step 1: Create an Azure OpenAI Resource

1. **Log in to Azure Portal**
   - Go to https://portal.azure.com
   - Sign in with your Azure account

2. **Create a new Azure OpenAI resource**
   - Click **"Create a resource"** (or use the search bar)
   - Search for **"Azure OpenAI"**
   - Click **"Azure OpenAI"** from the results
   - Click **"Create"**

3. **Fill in the resource details**
   - **Subscription**: Select your Azure subscription
   - **Resource Group**: Create a new one or select an existing one
   - **Region**: Choose a region where Azure OpenAI is available (e.g., `East US`, `West Europe`, `Southeast Asia`)
   - **Name**: Enter a unique name for your resource (e.g., `my-ai-openai`)
   - **Pricing Tier**: Select a pricing tier (e.g., `S0` for standard)
   - Click **"Review + create"**, then **"Create"**

4. **Wait for deployment**
   - Wait 2-5 minutes for the resource to be created
   - Click **"Go to resource"** when deployment completes

## Step 2: Get Your API Key (AZURE_API_KEY)

1. **Navigate to Keys and Endpoint**
   - In your Azure OpenAI resource page, go to **"Keys and Endpoint"** in the left sidebar
   - Or go to: `https://portal.azure.com` → Your Resource → **Keys and Endpoint**

2. **Copy the API Key**
   - You'll see **KEY 1** and **KEY 2** (both work the same)
   - Click **"Show"** next to KEY 1
   - Click the **copy icon** to copy the key
   - **Save this value** - this is your `AZURE_API_KEY`

   ⚠️ **Important**: Keep this key secure and never commit it to version control!

## Step 3: Get Your Endpoint (AZURE_ENDPOINT)

1. **In the same "Keys and Endpoint" page**
   - Find the **"Endpoint"** field
   - It will look like: `https://your-resource-name.openai.azure.com`
   - Click the **copy icon** to copy the endpoint
   - **Save this value** - this is your `AZURE_ENDPOINT`

   Example format: `https://my-ai-openai.openai.azure.com`

## Step 4: Request Access to Azure OpenAI Models (If Needed)

1. **Check model availability**
   - Go to **"Model deployments"** in the left sidebar
   - If you see a message about requesting access, you need to request it first

2. **Request access (if required)**
   - Go to **"Model deployments"** → **"Manage deployments"**
   - Or visit: https://oai.azure.com/portal
   - You may need to request access to specific models
   - Fill out the access request form and wait for approval (usually 1-2 business days)

## Step 5: Create LLM Deployment (AZURE_DEPLOYMENT_NAME)

1. **Navigate to Model Deployments**
   - In your Azure OpenAI resource, go to **"Model deployments"** in the left sidebar
   - Or use the Azure OpenAI Studio: https://oai.azure.com/portal

2. **Create a new deployment**
   - Click **"Create"** or **"+ Create"**
   - Fill in the deployment details:
     - **Deployment name**: Choose a unique name (e.g., `gpt-4-deployment`, `gpt-35-turbo-deployment`)
     - **Model**: Select a model from the dropdown:
       - For GPT-4: `gpt-4`, `gpt-4-turbo`, `gpt-4o`
       - For GPT-3.5: `gpt-35-turbo`, `gpt-35-turbo-16k`
     - **Model version**: Select the latest version (usually auto-selected)
     - **Capacity**: Set tokens per minute (TPM) or requests per minute (RPM)
   - Click **"Create"**

3. **Wait for deployment**
   - Wait 1-2 minutes for the deployment to be created
   - The status will change to **"Succeeded"**

4. **Copy the deployment name**
   - The deployment name you created is your `AZURE_DEPLOYMENT_NAME`
   - Example: `gpt-4-deployment` or `gpt-35-turbo-deployment`

## Step 6: Create Embedding Deployment (AZURE_EMBEDDING_DEPLOYMENT_NAME)

1. **Create another deployment for embeddings**
   - In **"Model deployments"**, click **"Create"** again
   - Fill in the deployment details:
     - **Deployment name**: Choose a unique name (e.g., `text-embedding-ada-002-deployment`, `text-embedding-3-small-deployment`)
     - **Model**: Select an embedding model:
       - `text-embedding-ada-002` (older, widely available)
       - `text-embedding-3-small` (newer, 1536 dimensions)
       - `text-embedding-3-large` (newer, 3072 dimensions)
     - **Model version**: Select the latest version
     - **Capacity**: Set appropriate limits
   - Click **"Create"**

2. **Wait for deployment**
   - Wait 1-2 minutes for the deployment to be created

3. **Copy the embedding deployment name**
   - The deployment name you created is your `AZURE_EMBEDDING_DEPLOYMENT_NAME`
   - Example: `text-embedding-ada-002-deployment`

## Step 7: Verify Your Configuration

You should now have all four values:

1. ✅ **AZURE_API_KEY**: Your API key (from Keys and Endpoint)
2. ✅ **AZURE_ENDPOINT**: Your endpoint URL (from Keys and Endpoint)
3. ✅ **AZURE_DEPLOYMENT_NAME**: Your LLM deployment name (e.g., `gpt-4-deployment`)
4. ✅ **AZURE_EMBEDDING_DEPLOYMENT_NAME**: Your embedding deployment name (e.g., `text-embedding-ada-002-deployment`)

## Step 8: Set Environment Variables

Set these environment variables in your system:

### Linux/Mac:
```bash
export AZURE_API_KEY="your-api-key-here"
export AZURE_ENDPOINT="https://your-resource-name.openai.azure.com"
export AZURE_DEPLOYMENT_NAME="gpt-4-deployment"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="text-embedding-ada-002-deployment"
export AZURE_ENABLED="true"
```

### Windows (PowerShell):
```powershell
$env:AZURE_API_KEY="your-api-key-here"
$env:AZURE_ENDPOINT="https://your-resource-name.openai.azure.com"
$env:AZURE_DEPLOYMENT_NAME="gpt-4-deployment"
$env:AZURE_EMBEDDING_DEPLOYMENT_NAME="text-embedding-ada-002-deployment"
$env:AZURE_ENABLED="true"
```

### Windows (Command Prompt):
```cmd
set AZURE_API_KEY=your-api-key-here
set AZURE_ENDPOINT=https://your-resource-name.openai.azure.com
set AZURE_DEPLOYMENT_NAME=gpt-4-deployment
set AZURE_EMBEDDING_DEPLOYMENT_NAME=text-embedding-ada-002-deployment
set AZURE_ENABLED=true
```

## Step 9: Optional - Set API Version

The default API version is `2024-02-15-preview`. If you need a different version:

```bash
export AZURE_API_VERSION="2024-02-15-preview"
```

## Quick Reference: Common Model Names

### LLM Models (for AZURE_DEPLOYMENT_NAME):
- `gpt-4` - GPT-4 base model
- `gpt-4-turbo` - GPT-4 Turbo
- `gpt-4o` - GPT-4 Optimized
- `gpt-35-turbo` - GPT-3.5 Turbo
- `gpt-35-turbo-16k` - GPT-3.5 Turbo with 16k context

### Embedding Models (for AZURE_EMBEDDING_DEPLOYMENT_NAME):
- `text-embedding-ada-002` - Ada v2 (1536 dimensions)
- `text-embedding-3-small` - Embedding v3 Small (1536 dimensions)
- `text-embedding-3-large` - Embedding v3 Large (3072 dimensions)

## Troubleshooting

### Issue: "Model not available" or "Access denied"
- **Solution**: Request access to the model through Azure OpenAI Studio
- Go to https://oai.azure.com/portal and submit an access request

### Issue: "Deployment not found"
- **Solution**: Ensure the deployment name matches exactly (case-sensitive)
- Check in Azure Portal → Model Deployments

### Issue: "Invalid API key"
- **Solution**: Regenerate the key in Keys and Endpoint
- Make sure you're using KEY 1 or KEY 2, not the endpoint URL

### Issue: "Endpoint not found"
- **Solution**: Ensure the endpoint URL doesn't have a trailing slash
- Format: `https://your-resource-name.openai.azure.com` (no trailing `/`)

## Additional Resources

- **Azure OpenAI Documentation**: https://learn.microsoft.com/en-us/azure/ai-services/openai/
- **Azure OpenAI Studio**: https://oai.azure.com/portal
- **API Reference**: https://learn.microsoft.com/en-us/azure/ai-services/openai/reference
- **Pricing Information**: https://azure.microsoft.com/en-us/pricing/details/cognitive-services/openai-service/

## Security Best Practices

1. **Never commit API keys to version control**
2. **Use environment variables or secure key management**
3. **Rotate API keys regularly**
4. **Use Azure Key Vault for production deployments**
5. **Set appropriate rate limits on deployments**
6. **Monitor usage through Azure Portal**
