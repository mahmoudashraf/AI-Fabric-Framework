#!/bin/bash

# Azure AI Services Test Configuration
# LLM: Llama-4-Maverick-17B-128E-Instruct-FP8
# Embeddings: Using /models endpoint
export AZURE_API_KEY="F93lTwneGCESqP6mxDGwonakvrsMzBBJpYmA8w0Rkf2kYcVu3nyCJQQJ99CAACHYHv6XJ3w3AAAAACOGgWJb"
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/openai/v1"
export AZURE_DEPLOYMENT_NAME="Llama-4-Maverick-17B-128E-Instruct-FP8"
export AZURE_EMBEDDING_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="embedding-model"
export AZURE_API_VERSION="2024-05-01-preview"
export AZURE_ENABLED="true"

echo "✅ Azure AI Services configured for testing"
echo "   LLM Endpoint: $AZURE_ENDPOINT"
echo "   LLM Model: $AZURE_DEPLOYMENT_NAME"
echo "   Embedding Endpoint: $AZURE_EMBEDDING_ENDPOINT"
echo "   Embedding Model: $AZURE_EMBEDDING_DEPLOYMENT_NAME"
echo ""

cd /workspace/ai-infrastructure-module/integration-Testing/integration-tests

echo "Running Real API Integration Tests with Azure (Llama-4-Maverick)..."
mvn test -Dtest=RealAPIIntegrationTest \
  -Dai.providers.llm-provider=azure \
  -Dai.providers.embedding-provider=azure \
  -Dspring.profiles.active=real-api-test
