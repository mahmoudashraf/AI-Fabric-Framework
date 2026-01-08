#!/bin/bash

# Azure AI Services (Foundry) Configuration
export AZURE_API_KEY="F93lTwneGCESqP6mxDGwonakvrsMzBBJpYmA8w0Rkf2kYcVu3nyCJQQJ99CAACHYHv6XJ3w3AAAAACOGgWJb"
export AZURE_ENDPOINT="https://mahan-mk5op536-eastus2.services.ai.azure.com/models/chat/completions?api-version=2024-05-01-preview"
export AZURE_DEPLOYMENT_NAME="DeepSeek-V3.2"
export AZURE_API_VERSION="2024-05-01-preview"
export AZURE_ENABLED="true"

echo "✅ Azure AI Services (Foundry) configured!"
echo "   Endpoint: $AZURE_ENDPOINT"
echo "   Deployment: $AZURE_DEPLOYMENT_NAME"
echo ""
echo "To use these settings, run: source setup-azure-foundry.sh"
