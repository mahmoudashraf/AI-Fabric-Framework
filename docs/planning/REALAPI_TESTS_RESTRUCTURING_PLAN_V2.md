# Real API Tests Restructuring Plan V2
## Extensible Provider Support & GitHub Actions Integration

**Version**: 2.0  
**Date**: 2026-01-08  
**Status**: Implementation Ready

---

## Executive Summary

This plan restructures the Real API test execution system to be fully extensible, supporting all current providers (OpenAI, Anthropic, Gemini, Cohere, Azure) and enabling easy addition of future providers. The restructuring includes:

1. **Provider Registry System**: Centralized, extensible provider configuration
2. **GitHub Actions Enhancement**: Dynamic provider selection with all new providers
3. **Test Matrix Automation**: Automatic discovery and execution across all provider combinations
4. **Environment Variable Management**: Standardized API key handling for all providers
5. **Documentation & Maintenance**: Clear guidelines for adding new providers

---

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Goals & Requirements](#goals--requirements)
3. [Architecture Design](#architecture-design)
4. [Implementation Phases](#implementation-phases)
5. [Provider Registry System](#provider-registry-system)
6. [GitHub Actions Workflow Updates](#github-actions-workflow-updates)
7. [Test Execution Framework](#test-execution-framework)
8. [Migration Strategy](#migration-strategy)
9. [Testing & Validation](#testing--validation)
10. [Documentation](#documentation)

---

## Current State Analysis

### Supported Providers

#### LLM Providers
- ✅ **OpenAI** (`openai`) - Fully supported
- ✅ **Anthropic** (`anthropic`) - Fully supported
- ✅ **Gemini** (`gemini`) - Implemented, **NOT in GitHub Actions**
- ✅ **Cohere** (`cohere`) - Implemented, **NOT in GitHub Actions**
- ✅ **Azure** (`azure`) - Implemented, partially in GitHub Actions as `azure-openai`
- ✅ **REST** (`rest`) - Fully supported

#### Embedding Providers
- ✅ **ONNX** (`onnx`) - Fully supported
- ✅ **OpenAI** (`openai`) - Fully supported
- ✅ **Anthropic** (`anthropic`) - Fully supported (via OpenAI-compatible)
- ✅ **Gemini** (`gemini`) - Implemented, **NOT in GitHub Actions**
- ✅ **Cohere** (`cohere`) - Implemented, **NOT in GitHub Actions**
- ✅ **Azure** (`azure`) - Implemented, partially in GitHub Actions as `azure-openai`
- ✅ **REST** (`rest`) - Fully supported

### Current GitHub Actions Workflow Issues

**File**: `.github/workflows/integration-tests-manual.yml`

**Problems Identified**:
1. ❌ **Missing Providers**: Gemini and Cohere not in dropdown options
2. ❌ **Inconsistent Naming**: Azure listed as `azure-openai` instead of `azure`
3. ❌ **Hardcoded Options**: Provider list requires manual workflow file edits
4. ❌ **Single API Key**: Only `openai_api_key` input, no support for other providers
5. ❌ **No Auto-Discovery**: Cannot automatically detect available providers
6. ❌ **Limited Extensibility**: Adding new provider requires multiple file edits

### Current Test Framework

**Strengths**:
- ✅ Matrix test execution (`RealAPIProviderMatrixIntegrationTest`)
- ✅ Provider auto-discovery (`AbstractProviderMatrixIntegrationTest`)
- ✅ Test chunking (core, vector, intent-actions, advanced)
- ✅ Environment variable support for all providers
- ✅ Multiple module support (ai-infrastructure, relationship-query, behavior)

**Gaps**:
- ⚠️ Provider discovery not integrated with GitHub Actions
- ⚠️ No centralized provider metadata
- ⚠️ Manual provider configuration in multiple places

---

## Goals & Requirements

### Primary Goals

1. **Extensibility**: Add new providers without modifying core workflow files
2. **Completeness**: Support all implemented providers in GitHub Actions
3. **Maintainability**: Single source of truth for provider configuration
4. **User Experience**: Clear, intuitive provider selection in GitHub UI
5. **Automation**: Automatic provider discovery and matrix generation

### Functional Requirements

1. **Provider Registry**
   - Centralized provider metadata (name, type, API key env var, etc.)
   - Support for LLM and Embedding providers
   - Provider-specific configuration (endpoints, models, etc.)
   - Validation rules per provider

2. **GitHub Actions Integration**
   - Dynamic provider dropdown generation
   - Multi-provider API key inputs
   - Automatic provider availability detection
   - Matrix execution across all combinations

3. **Test Execution**
   - Backward compatible with existing tests
   - Support for all test chunks
   - Provider-specific test filtering
   - Comprehensive reporting

4. **Documentation**
   - Provider setup guides
   - Workflow usage documentation
   - Extension guide for new providers

---

## Architecture Design

### Provider Registry System

```
┌─────────────────────────────────────────────────────────┐
│              Provider Registry (YAML/JSON)                │
│  - Provider metadata                                     │
│  - API key environment variables                         │
│  - Configuration requirements                            │
│  - Validation rules                                      │
└─────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│         Provider Registry Service (Java)                 │
│  - Load provider definitions                             │
│  - Validate provider availability                        │
│  - Generate provider combinations                        │
└─────────────────────────────────────────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        ▼                               ▼
┌───────────────────┐         ┌──────────────────────┐
│  GitHub Actions   │         │  Test Framework      │
│  Workflow         │         │  (Java)              │
│  - Dynamic inputs │         │  - Auto-discovery    │
│  - Matrix gen     │         │  - Matrix execution  │
└───────────────────┘         └──────────────────────┘
```

### Component Overview

1. **Provider Registry** (`providers-registry.yml`)
   - YAML file defining all providers
   - Single source of truth
   - Version controlled

2. **Provider Registry Service**
   - Java class to load and query registry
   - Used by test framework
   - Can be used by GitHub Actions scripts

3. **GitHub Actions Scripts**
   - Python/JavaScript to read registry
   - Generate dynamic workflow inputs
   - Validate provider availability

4. **Test Framework Integration**
   - Enhanced `AbstractProviderMatrixIntegrationTest`
   - Uses registry for discovery
   - Validates provider combinations

---

## Implementation Phases

### Phase 1: Provider Registry Foundation (Week 1)

**Deliverables**:
1. Create `providers-registry.yml` with all current providers
2. Implement `ProviderRegistryService` Java class
3. Update `AbstractProviderMatrixIntegrationTest` to use registry
4. Unit tests for registry service

**Files to Create**:
- `ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml`
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/registry/ProviderRegistryService.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/registry/ProviderDefinition.java`

**Files to Modify**:
- `AbstractProviderMatrixIntegrationTest.java` - Use registry for discovery

### Phase 2: GitHub Actions Scripts (Week 1-2)

**Deliverables**:
1. Script to read provider registry and generate workflow inputs
2. Update workflow file to use dynamic provider lists
3. Add API key inputs for all providers
4. Provider availability validation

**Files to Create**:
- `.github/scripts/generate-provider-inputs.js` (or Python)
- `.github/scripts/validate-provider-availability.sh`

**Files to Modify**:
- `.github/workflows/integration-tests-manual.yml` - Dynamic inputs

### Phase 3: Test Framework Enhancement (Week 2)

**Deliverables**:
1. Enhanced provider discovery using registry
2. Provider-specific test filtering
3. Improved error messages for missing providers
4. Provider combination validation

**Files to Modify**:
- `AbstractProviderMatrixIntegrationTest.java`
- `RealAPIProviderMatrixIntegrationTest.java`

### Phase 4: Documentation & Migration (Week 2-3)

**Deliverables**:
1. Provider setup guides
2. Workflow usage documentation
3. Migration guide from old to new system
4. Extension guide for adding new providers

**Files to Create**:
- `docs/ProviderRegistry/PROVIDER_REGISTRY_GUIDE.md`
- `docs/ProviderRegistry/GITHUB_ACTIONS_PROVIDER_GUIDE.md`
- `docs/ProviderRegistry/ADDING_NEW_PROVIDER.md`

---

## Provider Registry System

### Registry File Structure

**Location**: `ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml`

```yaml
providers:
  llm:
    openai:
      name: "OpenAI"
      displayName: "OpenAI (GPT-4, GPT-3.5)"
      apiKeyEnvVar: "OPENAI_API_KEY"
      required: true
      defaultModel: "gpt-4o-mini"
      baseUrl: "https://api.openai.com/v1"
      enabled: true
      description: "OpenAI GPT models"
      
    anthropic:
      name: "Anthropic"
      displayName: "Anthropic (Claude)"
      apiKeyEnvVar: "ANTHROPIC_API_KEY"
      required: true
      defaultModel: "claude-3-haiku-20240307"
      baseUrl: "https://api.anthropic.com"
      enabled: true
      description: "Anthropic Claude models"
      
    gemini:
      name: "Gemini"
      displayName: "Google Gemini"
      apiKeyEnvVar: "GEMINI_API_KEY"
      required: true
      defaultModel: "gemini-2.5-flash"
      baseUrl: "https://generativelanguage.googleapis.com/v1beta"
      enabled: true
      description: "Google Gemini models"
      
    cohere:
      name: "Cohere"
      displayName: "Cohere (Command)"
      apiKeyEnvVar: "COHERE_API_KEY"
      required: true
      defaultModel: "command-r7b-12-2024"
      baseUrl: "https://api.cohere.com"
      enabled: true
      description: "Cohere Command models"
      
    azure:
      name: "Azure"
      displayName: "Azure AI Services"
      apiKeyEnvVar: "AZURE_API_KEY"
      endpointEnvVar: "AZURE_ENDPOINT"
      deploymentNameEnvVar: "AZURE_DEPLOYMENT_NAME"
      required: true
      defaultModel: "gpt-4"
      baseUrl: "${AZURE_ENDPOINT}"
      enabled: true
      description: "Azure OpenAI / AI Services"
      additionalEnvVars:
        - "AZURE_API_VERSION"
        - "AZURE_EMBEDDING_DEPLOYMENT_NAME"
        
    rest:
      name: "REST"
      displayName: "REST API"
      apiKeyEnvVar: null
      required: false
      enabled: true
      description: "Generic REST API provider"

  embedding:
    onnx:
      name: "ONNX"
      displayName: "ONNX (Local)"
      apiKeyEnvVar: null
      required: false
      enabled: true
      description: "Local ONNX embedding model"
      
    openai:
      name: "OpenAI"
      displayName: "OpenAI Embeddings"
      apiKeyEnvVar: "OPENAI_API_KEY"
      required: true
      defaultModel: "text-embedding-3-small"
      baseUrl: "https://api.openai.com/v1"
      enabled: true
      description: "OpenAI text embeddings"
      
    anthropic:
      name: "Anthropic"
      displayName: "Anthropic Embeddings"
      apiKeyEnvVar: "ANTHROPIC_API_KEY"
      required: true
      enabled: true
      description: "Anthropic embeddings (via OpenAI-compatible)"
      
    gemini:
      name: "Gemini"
      displayName: "Google Gemini Embeddings"
      apiKeyEnvVar: "GEMINI_API_KEY"
      required: true
      defaultModel: "text-embedding-004"
      baseUrl: "https://generativelanguage.googleapis.com/v1beta"
      enabled: true
      description: "Google Gemini embeddings"
      
    cohere:
      name: "Cohere"
      displayName: "Cohere Embeddings"
      apiKeyEnvVar: "COHERE_API_KEY"
      required: true
      defaultModel: "embed-english-v3.0"
      baseUrl: "https://api.cohere.com"
      enabled: true
      description: "Cohere embeddings"
      
    azure:
      name: "Azure"
      displayName: "Azure Embeddings"
      apiKeyEnvVar: "AZURE_API_KEY"
      endpointEnvVar: "AZURE_ENDPOINT"
      embeddingDeploymentNameEnvVar: "AZURE_EMBEDDING_DEPLOYMENT_NAME"
      required: true
      defaultModel: "text-embedding-ada-002"
      baseUrl: "${AZURE_ENDPOINT}"
      enabled: true
      description: "Azure OpenAI / AI Services embeddings"
      
    rest:
      name: "REST"
      displayName: "REST Embeddings"
      apiKeyEnvVar: null
      required: false
      enabled: true
      description: "Generic REST embedding API"
```

### Provider Registry Service

**Java Interface**:
```java
public interface ProviderRegistryService {
    List<ProviderDefinition> getLLMProviders();
    List<ProviderDefinition> getEmbeddingProviders();
    ProviderDefinition getProvider(String name, ProviderType type);
    boolean isProviderAvailable(String name, ProviderType type);
    List<String> getRequiredEnvVars(String name, ProviderType type);
}
```

---

## GitHub Actions Workflow Updates

### Dynamic Provider Inputs

**Current Approach** (Hardcoded):
```yaml
llm_provider:
  type: choice
  options:
    - openai
    - azure-openai
    - cohere
    - anthropic
```

**New Approach** (Dynamic):
```yaml
# Generated from providers-registry.yml
llm_provider:
  type: choice
  options: ${GENERATED_FROM_REGISTRY}
```

### Implementation Strategy

1. **Pre-workflow Script**: Generate workflow inputs from registry
2. **Composite Action**: Reusable provider configuration action
3. **Matrix Strategy**: Use GitHub Actions matrix for combinations

### Updated Workflow Structure

```yaml
name: Integration Tests (Manual Trigger)

on:
  workflow_dispatch:
    inputs:
      # Dynamic inputs generated from registry
      llm_provider:
        description: 'LLM Provider'
        type: choice
        options: [openai, anthropic, gemini, cohere, azure, rest]
      
      embedding_provider:
        description: 'Embedding Provider'
        type: choice
        options: [onnx, openai, anthropic, gemini, cohere, azure, rest]
      
      # Provider-specific API keys
      openai_api_key:
        description: 'OpenAI API Key'
        type: string
        required: false
        
      anthropic_api_key:
        description: 'Anthropic API Key'
        type: string
        required: false
        
      gemini_api_key:
        description: 'Gemini API Key'
        type: string
        required: false
        
      cohere_api_key:
        description: 'Cohere API Key'
        type: string
        required: false
        
      azure_api_key:
        description: 'Azure API Key'
        type: string
        required: false
        
      azure_endpoint:
        description: 'Azure Endpoint URL'
        type: string
        required: false
        
      azure_deployment_name:
        description: 'Azure Deployment Name'
        type: string
        required: false
      
      # ... other inputs
```

### Provider Configuration Action

Create reusable composite action: `.github/actions/configure-providers/action.yml`

```yaml
name: 'Configure Providers'
description: 'Configure provider environment variables from inputs'

inputs:
  llm_provider:
    required: true
  embedding_provider:
    required: true
  openai_api_key:
    required: false
  anthropic_api_key:
    required: false
  # ... all provider keys

runs:
  using: composite
  steps:
    - name: Set provider environment variables
      shell: bash
      run: |
        # Set LLM provider
        echo "LLM_PROVIDER=${{ inputs.llm_provider }}" >> $GITHUB_ENV
        echo "ai.providers.llm-provider=${{ inputs.llm_provider }}" >> $GITHUB_ENV
        
        # Set embedding provider
        echo "EMBEDDING_PROVIDER=${{ inputs.embedding_provider }}" >> $GITHUB_ENV
        echo "ai.providers.embedding-provider=${{ inputs.embedding_provider }}" >> $GITHUB_ENV
        
        # Set API keys based on selected providers
        if [ "${{ inputs.llm_provider }}" == "openai" ] || [ "${{ inputs.embedding_provider }}" == "openai" ]; then
          echo "OPENAI_API_KEY=${{ inputs.openai_api_key }}" >> $GITHUB_ENV
        fi
        
        # ... similar for all providers
```

---

## Test Execution Framework

### Enhanced Provider Discovery

Update `AbstractProviderMatrixIntegrationTest` to use registry:

```java
protected List<ProviderCombination> discoverAvailableCombinations() {
    ProviderRegistryService registry = new ProviderRegistryService();
    
    // Get available providers from registry
    List<String> llmProviders = registry.getLLMProviders().stream()
        .filter(p -> isProviderAvailable(p))
        .map(ProviderDefinition::getName)
        .collect(Collectors.toList());
    
    List<String> embeddingProviders = registry.getEmbeddingProviders().stream()
        .filter(p -> isProviderAvailable(p))
        .map(ProviderDefinition::getName)
        .collect(Collectors.toList());
    
    // Generate combinations
    // ...
}
```

### Provider Availability Check

```java
private boolean isProviderAvailable(ProviderDefinition provider) {
    // Check required environment variables
    List<String> requiredVars = provider.getRequiredEnvVars();
    for (String envVar : requiredVars) {
        if (System.getenv(envVar) == null || System.getenv(envVar).trim().isEmpty()) {
            return false;
        }
    }
    return true;
}
```

---

## Migration Strategy

### Step 1: Add Registry (Non-Breaking)

1. Create `providers-registry.yml`
2. Implement `ProviderRegistryService`
3. Keep existing code working
4. Test with existing providers

### Step 2: Update Test Framework (Backward Compatible)

1. Enhance `AbstractProviderMatrixIntegrationTest` to use registry
2. Fallback to old discovery if registry not available
3. Test all existing test combinations

### Step 3: Update GitHub Actions (Breaking Change)

1. Add new provider inputs
2. Update workflow to use new inputs
3. Update documentation
4. Test with all providers

### Step 4: Cleanup (Optional)

1. Remove hardcoded provider lists
2. Remove old discovery code
3. Update all documentation

---

## Testing & Validation

### Test Scenarios

1. **Provider Discovery**
   - All providers discovered correctly
   - Missing API keys handled gracefully
   - Invalid provider combinations rejected

2. **GitHub Actions**
   - All providers appear in dropdowns
   - API keys set correctly
   - Tests run with all provider combinations

3. **Test Execution**
   - All test chunks work
   - Provider-specific tests pass
   - Matrix execution works

4. **Extensibility**
   - New provider added to registry
   - Automatically appears in GitHub Actions
   - Tests discover new provider

### Validation Checklist

- [ ] All current providers in registry
- [ ] Provider registry service loads correctly
- [ ] Test framework uses registry
- [ ] GitHub Actions shows all providers
- [ ] API keys configured correctly
- [ ] Tests run with all providers
- [ ] Documentation updated
- [ ] Migration guide complete

---

## Documentation

### Required Documentation

1. **Provider Registry Guide**
   - How registry works
   - Adding new providers
   - Provider metadata fields

2. **GitHub Actions Guide**
   - How to use workflow
   - Provider selection
   - API key configuration
   - Troubleshooting

3. **Extension Guide**
   - Step-by-step: Adding new provider
   - Registry file updates
   - Test framework integration
   - GitHub Actions updates

4. **Migration Guide**
   - Moving from old to new system
   - Breaking changes
   - Compatibility notes

---

## Implementation Checklist

### Phase 1: Foundation
- [ ] Create `providers-registry.yml`
- [ ] Implement `ProviderRegistryService`
- [ ] Create `ProviderDefinition` class
- [ ] Unit tests for registry service
- [ ] Update `AbstractProviderMatrixIntegrationTest`

### Phase 2: GitHub Actions
- [ ] Create provider configuration action
- [ ] Update workflow with all providers
- [ ] Add API key inputs for all providers
- [ ] Test workflow with each provider

### Phase 3: Documentation
- [ ] Provider registry guide
- [ ] GitHub Actions usage guide
- [ ] Extension guide
- [ ] Migration guide

### Phase 4: Testing
- [ ] Test all provider combinations
- [ ] Validate GitHub Actions workflow
- [ ] Test provider discovery
- [ ] Test error handling

---

## Success Criteria

1. ✅ All providers (OpenAI, Anthropic, Gemini, Cohere, Azure) appear in GitHub Actions
2. ✅ API keys can be configured for all providers
3. ✅ Tests run successfully with all provider combinations
4. ✅ New provider can be added by updating registry only
5. ✅ Documentation is complete and accurate
6. ✅ Backward compatibility maintained

---

## Future Enhancements

1. **Provider Health Checks**: Pre-flight validation of provider availability
2. **Cost Tracking**: Track API usage per provider
3. **Performance Metrics**: Compare provider performance
4. **Auto-Retry**: Smart retry logic per provider
5. **Provider-Specific Tests**: Tests that only run for certain providers

---

## Appendix

### Provider Naming Convention

- **Registry Name**: Lowercase, no spaces (e.g., `openai`, `azure`)
- **Display Name**: Human-readable (e.g., "OpenAI (GPT-4)")
- **Environment Variable**: Uppercase with underscores (e.g., `OPENAI_API_KEY`)

### File Locations

- Registry: `ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml`
- Service: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/registry/`
- GitHub Actions: `.github/workflows/integration-tests-manual.yml`
- Scripts: `.github/scripts/`
- Documentation: `docs/`

---

**End of Plan**
