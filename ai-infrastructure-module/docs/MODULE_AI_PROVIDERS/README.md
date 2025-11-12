# Modular AI Providers Documentation

## Overview

This directory contains comprehensive documentation for the **Modular AI Provider Architecture** - a flexible, Spring Boot-based architecture that allows independent selection of LLM and embedding providers.

## Quick Start

**New to modular providers?** Start here:
1. [Quick Start Guide](QUICK_START_GUIDE.md) - Get up and running in 5 minutes
2. [AI Provider Modular Architecture Plan](AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md) - Complete architecture overview

## Documentation Index

### 📚 Main Documentation

- **[AI Provider Modular Architecture Plan](AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md)** - Primary architecture document
- **[Quick Start Guide](QUICK_START_GUIDE.md)** - 5-minute setup guide
- **[Documentation Index](MODULAR_PROVIDERS_DOCUMENTATION_INDEX.md)** - Complete documentation index

### 🔧 Implementation Guides

- **[Developer Guide: Custom Providers](DEVELOPER_GUIDE_CUSTOM_PROVIDERS.md)** - Create your own provider modules
- **[Vector Database Modular Architecture](VECTOR_DATABASE_MODULAR_ARCHITECTURE.md)** - Vector database modules
- **[Integration Test Changes](INTEGRATION_TEST_CHANGES.md)** - Update tests for modular providers

### 📖 Reference Documentation

- **[Configuration Reference](CONFIGURATION_REFERENCE.md)** - Complete configuration guide
- **[Migration Guide](MIGRATION_GUIDE.md)** - Migrate from monolithic architecture
- **[Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md)** - Common issues and solutions

### 📊 Summary

- **[Documentation Summary](DOCUMENTATION_SUMMARY.md)** - Overview of all documentation

## Key Features

### ✅ Independent Provider Selection
- **LLM Provider**: Select provider for content generation (`llm-provider`)
- **Embedding Provider**: Select provider for vector embeddings (`embedding-provider`)
- **Mix and Match**: Use different providers for LLM and embeddings

### ✅ Modular Architecture
- **Separate Module Per Provider**: Each provider in its own module
- **Auto-Discovery**: Spring Boot automatically discovers providers
- **Conditional Loading**: Only load providers you need

### ✅ Provider Types Supported
- **LLM + Embeddings**: OpenAI, Azure OpenAI, Cohere
- **LLM Only**: Anthropic Claude
- **Embeddings Only**: ONNX, REST

## Example Configuration

```yaml
ai:
  providers:
    llm-provider: openai          # For content generation
    embedding-provider: onnx      # For vector embeddings (different!)
    
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
    
    onnx:
      enabled: true
```

## Documentation by Role

### 👨‍💻 For Developers
1. [Quick Start Guide](QUICK_START_GUIDE.md)
2. [AI Provider Modular Architecture Plan](AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md)
3. [Developer Guide: Custom Providers](DEVELOPER_GUIDE_CUSTOM_PROVIDERS.md)

### 🔧 For DevOps/Infrastructure
1. [Configuration Reference](CONFIGURATION_REFERENCE.md)
2. [Migration Guide](MIGRATION_GUIDE.md)
3. [Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md)

### 🧪 For Testers
1. [Integration Test Changes](INTEGRATION_TEST_CHANGES.md)
2. [Configuration Reference](CONFIGURATION_REFERENCE.md)

### 🏗️ For Architects
1. [AI Provider Modular Architecture Plan](AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md)
2. [Vector Database Modular Architecture](VECTOR_DATABASE_MODULAR_ARCHITECTURE.md)

## Related Documentation

- [Embedding Provider Configuration](../EMBEDDING_PROVIDER_CONFIGURATION.md)
- [Vector Database Abstraction](../VECTOR_DATABASE_ABSTRACTION.md)
- [ONNX Runtime Embeddings Guide](../ONNX_RUNTIME_EMBEDDINGS_GUIDE.md)

## Questions?

- **Which providers are supported?** → [Configuration Reference](CONFIGURATION_REFERENCE.md)
- **How do I add a new provider?** → [Developer Guide: Custom Providers](DEVELOPER_GUIDE_CUSTOM_PROVIDERS.md)
- **How do I migrate?** → [Migration Guide](MIGRATION_GUIDE.md)
- **Having issues?** → [Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md)

## File Structure

```
MODULE_AI_PROVIDERS/
├── README.md (this file)
├── AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md
├── QUICK_START_GUIDE.md
├── CONFIGURATION_REFERENCE.md
├── MIGRATION_GUIDE.md
├── TROUBLESHOOTING_GUIDE.md
├── DEVELOPER_GUIDE_CUSTOM_PROVIDERS.md
├── VECTOR_DATABASE_MODULAR_ARCHITECTURE.md
├── INTEGRATION_TEST_CHANGES.md
├── MODULAR_PROVIDERS_DOCUMENTATION_INDEX.md
└── DOCUMENTATION_SUMMARY.md
```

## Updates

This documentation is maintained as the architecture evolves. Check the main plan document for the latest changes.
