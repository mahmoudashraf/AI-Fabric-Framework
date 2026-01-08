#!/bin/bash

# Azure AI Services (Foundry) Test Configuration
export AZURE_API_KEY="F93lTwneGCESqP6mxDGwonakvrsMzBBJpYmA8w0Rkf2kYcVu3nyCJQQJ99CAACHYHv6XJ3w3AAAAACOGgWJb"
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models"
export AZURE_DEPLOYMENT_NAME="DeepSeek-V3.2"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="embedding-model"
export AZURE_API_VERSION="2024-05-01-preview"
export AZURE_ENABLED="true"

echo "✅ Azure AI Services (Foundry) configured for testing"
echo "   Endpoint: $AZURE_ENDPOINT"
echo "   LLM Deployment: $AZURE_DEPLOYMENT_NAME"
echo "   Embedding Deployment: $AZURE_EMBEDDING_DEPLOYMENT_NAME"
echo ""

cd /workspace/ai-infrastructure-module/integration-Testing/integration-tests

echo "Running Real API Integration Tests with Azure..."
mvn test -Dtest=RealAPIIntegrationTest \
  -Dai.providers.llm-provider=azure \
  -Dai.providers.embedding-provider=azure \
  -Dspring.profiles.active=real-api-test
