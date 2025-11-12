# Documentation Index: Modular Provider Architecture

## Overview

This index provides quick access to all documentation related to the modular provider architecture.

## Main Documentation

### 1. [AI Provider Modular Architecture Plan](AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md)
**Primary architecture document** - Complete plan for modular provider architecture
- Architecture overview
- Module structure
- Code examples
- Configuration examples
- Migration checklist

### 2. [Quick Start Guide](QUICK_START_GUIDE.md)
**Get started in 5 minutes** - Fast track to using modular providers
- Step-by-step setup
- Common configurations
- Quick troubleshooting

## Detailed Guides

### 3. [Vector Database Modular Architecture](VECTOR_DATABASE_MODULAR_ARCHITECTURE.md)
**Vector database modules** - Separate modules for vector databases
- Module structure
- Implementation examples
- Configuration

### 4. [Integration Test Changes](INTEGRATION_TEST_CHANGES.md)
**Testing with modular providers** - How to update tests
- Test configuration updates
- Parameterized tests
- Test execution

### 5. [Developer Guide: Custom Providers](DEVELOPER_GUIDE_CUSTOM_PROVIDERS.md)
**Create your own provider** - Step-by-step guide
- Module structure
- Implementation examples
- Best practices

## Reference Documentation

### 6. [Configuration Reference](CONFIGURATION_REFERENCE.md)
**Complete configuration guide** - All configuration options
- Provider selection
- Provider-specific configuration
- Vector database configuration
- Environment variables

### 7. [Migration Guide](MIGRATION_GUIDE.md)
**Migrate from monolithic** - Step-by-step migration
- Migration phases
- Breaking changes
- Common scenarios
- Rollback plan

### 8. [Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md)
**Common issues and solutions** - Fix problems quickly
- Common issues
- Debugging tips
- Getting help

## Documentation by Role

### For Developers
1. Start with: [Quick Start Guide](QUICK_START_GUIDE.md)
2. Read: [AI Provider Modular Architecture Plan](AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md)
3. Reference: [Configuration Reference](CONFIGURATION_REFERENCE.md)
4. Create custom: [Developer Guide: Custom Providers](DEVELOPER_GUIDE_CUSTOM_PROVIDERS.md)

### For DevOps/Infrastructure
1. Start with: [Configuration Reference](CONFIGURATION_REFERENCE.md)
2. Read: [Migration Guide](MIGRATION_GUIDE.md)
3. Troubleshoot: [Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md)

### For Testers
1. Start with: [Integration Test Changes](INTEGRATION_TEST_CHANGES.md)
2. Reference: [Configuration Reference](CONFIGURATION_REFERENCE.md)

### For Architects
1. Read: [AI Provider Modular Architecture Plan](AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md)
2. Review: [Vector Database Modular Architecture](VECTOR_DATABASE_MODULAR_ARCHITECTURE.md)
3. Understand: [Developer Guide: Custom Providers](DEVELOPER_GUIDE_CUSTOM_PROVIDERS.md)

## Quick Links

### Getting Started
- [Quick Start Guide](QUICK_START_GUIDE.md) - 5-minute setup
- [Configuration Reference](CONFIGURATION_REFERENCE.md) - All options

### Implementation
- [AI Provider Modular Architecture Plan](AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md) - Complete plan
- [Developer Guide: Custom Providers](DEVELOPER_GUIDE_CUSTOM_PROVIDERS.md) - Create providers

### Migration
- [Migration Guide](MIGRATION_GUIDE.md) - Step-by-step
- [Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md) - Fix issues

### Testing
- [Integration Test Changes](INTEGRATION_TEST_CHANGES.md) - Update tests

### Vector Databases
- [Vector Database Modular Architecture](VECTOR_DATABASE_MODULAR_ARCHITECTURE.md) - Vector DB modules

## Key Concepts

### Modular Architecture
- Each provider in separate module
- Auto-discovery via Spring Boot
- Independent selection of LLM and embedding providers

### Provider Selection
- `llm-provider`: Select LLM provider (openai, azure, anthropic, etc.)
- `embedding-provider`: Select embedding provider (onnx, openai, azure, etc.)
- Can mix and match (e.g., OpenAI LLM + ONNX embeddings)

### Configuration
- Nested YAML structure
- Environment variables for secrets
- Profile-based configuration

## Common Questions

### Q: Which providers are supported?
A: See [Configuration Reference](CONFIGURATION_REFERENCE.md) for complete list.

### Q: How do I add a new provider?
A: See [Developer Guide: Custom Providers](DEVELOPER_GUIDE_CUSTOM_PROVIDERS.md).

### Q: How do I migrate from the old architecture?
A: See [Migration Guide](MIGRATION_GUIDE.md).

### Q: My provider isn't being discovered?
A: See [Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md).

### Q: Can I use different providers for LLM and embeddings?
A: Yes! See [Quick Start Guide](QUICK_START_GUIDE.md) for examples.

## Related Documentation

### Existing Documentation
- [EMBEDDING_PROVIDER_CONFIGURATION.md](../EMBEDDING_PROVIDER_CONFIGURATION.md) - Embedding providers
- [VECTOR_DATABASE_ABSTRACTION.md](../VECTOR_DATABASE_ABSTRACTION.md) - Vector database abstraction
- [ONNX_RUNTIME_EMBEDDINGS_GUIDE.md](../ONNX_RUNTIME_EMBEDDINGS_GUIDE.md) - ONNX embeddings

### Test Plans
- [PROVIDER_MANAGEMENT_TEST_PLAN.md](../test-plans/PROVIDER_MANAGEMENT_TEST_PLAN.md) - Provider tests
- [VECTOR_DATABASE_TEST_PLAN.md](../test-plans/VECTOR_DATABASE_TEST_PLAN.md) - Vector DB tests

## Updates

This documentation is updated as the architecture evolves. Check the main plan document for the latest changes.

## Feedback

If you find issues or have suggestions for improvement, please update the relevant documentation or create an issue.
