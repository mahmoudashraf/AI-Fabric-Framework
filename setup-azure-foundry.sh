#!/bin/bash

# Azure AI Services Configuration
# LLM: Llama-4-Maverick-17B-128E-Instruct-FP8 (OpenAI-compatible format)
# Embeddings: Using /models endpoint
export AZURE_API_KEY="F93lTwneGCESqP6mxDGwonakvrsMzBBJpYmA8w0Rkf2kYcVu3nyCJQQJ99CAACHYHv6XJ3w3AAAAACOGgWJb"

# LLM endpoint - OpenAI-compatible format
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/openai/v1"
export AZURE_DEPLOYMENT_NAME="Llama-4-Maverick-17B-128E-Instruct-FP8"

# Embeddings endpoint - Foundry format (separate endpoint if needed)
# If embeddings use the same base, they'll use /models/embeddings
export AZURE_EMBEDDING_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="embedding-model"
export AZURE_API_VERSION="2024-05-01-preview"
export AZURE_ENABLED="true"

echo "✅ Azure AI Services configured!"
echo "   LLM Endpoint: $AZURE_ENDPOINT"
echo "   LLM Model: $AZURE_DEPLOYMENT_NAME"
echo "   Embedding Endpoint: $AZURE_EMBEDDING_ENDPOINT"
echo "   Embedding Model: $AZURE_EMBEDDING_DEPLOYMENT_NAME"
echo ""
echo "To use these settings, run: source setup-azure-foundry.sh"
