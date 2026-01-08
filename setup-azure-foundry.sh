#!/bin/bash

# Azure AI Services (Foundry) Configuration
# Using base endpoint so both LLM and embeddings work
export AZURE_API_KEY="F93lTwneGCESqP6mxDGwonakvrsMzBBJpYmA8w0Rkf2kYcVu3nyCJQQJ99CAACHYHv6XJ3w3AAAAACOGgWJb"
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models"
export AZURE_DEPLOYMENT_NAME="DeepSeek-V3.2"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="embedding-model"  # Informational only for Foundry
export AZURE_API_VERSION="2024-05-01-preview"
export AZURE_ENABLED="true"

# Set providers to use Azure (use system properties for Maven)
# Note: These need to be passed as -D properties to Maven, not as environment variables

echo "✅ Azure AI Services (Foundry) configured!"
echo "   Endpoint: $AZURE_ENDPOINT"
echo "   LLM Deployment: $AZURE_DEPLOYMENT_NAME"
echo "   Embedding Deployment: $AZURE_EMBEDDING_DEPLOYMENT_NAME"
echo "   API Version: $AZURE_API_VERSION"
echo ""
echo "To use these settings, run: source setup-azure-foundry.sh"
