# AI-Core Module Extraction Map

## Visual Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  CURRENT: ai-infrastructure-core (211 files - TOO LARGE)            │
│                                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐                │
│  │   Core      │  │  Business    │  │   Web       │                │
│  │ Services    │  │   Logic      │  │   Layer     │                │
│  │ (needed)    │  │  (wrong!)    │  │  (wrong!)   │                │
│  └─────────────┘  └──────────────┘  └─────────────┘                │
│                                                                      │
│  Everything mixed together!                                         │
└─────────────────────────────────────────────────────────────────────┘

                            ↓ REFACTOR ↓

┌─────────────────────────────────────────────────────────────────────┐
│  AFTER: Modular Architecture (8 focused modules)                    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  ai-infrastructure-core (~105 files)                         │   │
│  │  Essential infrastructure only                               │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │   web       │  │ orchestration│  │ rag-advanced │               │
│  │  (6 files)  │  │  (14 files)  │  │  (5 files)   │               │
│  └─────────────┘  └──────────────┘  └──────────────┘               │
│                                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │  security   │  │  compliance  │  │  monitoring  │               │
│  │ (20 files)  │  │  (4 files)   │  │  (6 files)   │               │
│  └─────────────┘  └──────────────┘  └──────────────┘               │
│                                                                      │
│  ┌─────────────┐                                                    │
│  │test-support │                                                    │
│  │  (2 files)  │                                                    │
│  └─────────────┘                                                    │
│                                                                      │
│  Clean separation of concerns!                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## File Distribution Map

### 🟢 KEEP IN CORE (~105 files)

#### Core Services (8 files)
```
✅ AICoreService.java
✅ AIEmbeddingService.java
✅ AISearchService.java
✅ AICapabilityService.java
✅ AIConfigurationService.java
✅ AIInfrastructureProfileService.java
✅ VectorManagementService.java
✅ AIHealthService.java (basic only)
```

#### RAG Foundation (6 files)
```
✅ RAGService.java
✅ VectorDatabaseService.java
✅ VectorSearchService.java
✅ SearchableEntityVectorDatabaseService.java
✅ VectorDatabase.java (interface)
✅ VectorDatabaseServiceAdapter.java
```

#### Provider Management (4 files)
```
✅ AIProviderManager.java
✅ EmbeddingProvider.java (interface)
✅ AIProvider.java
✅ ProviderConfig.java
✅ ProviderStatus.java
```

#### Configuration (13 files)
```
✅ AIInfrastructureAutoConfiguration.java
✅ AIProviderConfig.java
✅ AIServiceConfig.java
✅ AIIndexingProperties.java
✅ AICleanupProperties.java
✅ PIIDetectionProperties.java
✅ SecurityProperties.java
✅ SmartSuggestionsProperties.java
✅ ResponseSanitizationProperties.java
✅ IntentHistoryProperties.java
✅ VectorDatabaseConfig.java
✅ AIEntityConfigurationLoader.java
✅ ProviderConfiguration.java
```

#### Indexing System (14 files)
```
✅ IndexingCoordinator.java
✅ IndexingQueueService.java
✅ IndexingStrategyResolver.java
✅ IndexingWorkProcessor.java
✅ AsyncIndexingWorker.java
✅ BatchIndexingWorker.java
✅ IndexingCleanupScheduler.java
✅ IndexingRequest.java
✅ IndexingConfiguration.java
✅ IndexingActionPlan.java
✅ IndexingOperation.java
✅ IndexingStatus.java
✅ IndexingStrategy.java
✅ IndexingPriority.java
```

#### Cleanup/Retention (6 files)
```
✅ SearchableEntityCleanupScheduler.java
✅ CleanupPolicyProvider.java
✅ DefaultCleanupPolicyProvider.java
✅ CleanupStrategy.java
✅ RetentionPolicyProvider.java
```

#### Aspects/Processors (4 files)
```
✅ AICapableAspect.java
✅ AICapableProcessor.java
✅ EmbeddingProcessor.java
✅ ResponseSanitizer.java (basic)
✅ SanitizationEvent.java
```

#### Entities (4 files)
```
✅ AISearchableEntity.java
✅ IndexingQueueEntry.java
✅ IntentHistory.java
✅ AIInfrastructureProfile.java
```

#### Repositories (4 files)
```
✅ AISearchableEntityRepository.java
✅ IndexingQueueRepository.java
✅ IntentHistoryRepository.java
✅ AIInfrastructureProfileRepository.java
```

#### Core DTOs (~30 files)
```
✅ AIEmbeddingRequest.java
✅ AIEmbeddingResponse.java
✅ AIGenerationRequest.java
✅ AIGenerationResponse.java
✅ AISearchRequest.java
✅ AISearchResponse.java
✅ RAGRequest.java
✅ RAGResponse.java
✅ AIEntityConfig.java
✅ AIEmbeddableField.java
✅ AISearchableField.java
✅ AIMetadataField.java
✅ VectorRecord.java
✅ AIConfigurationDto.java
✅ AIHealthDto.java
✅ AIContentFilterRequest.java
✅ AIContentFilterResponse.java
✅ Intent.java
✅ IntentType.java
✅ MultiIntentResponse.java
✅ NextStepRecommendation.java
✅ PIIDetection.java
✅ PIIDetectionResult.java
✅ PIIMode.java
✅ AICrudOperation.java
... (and other essential DTOs)
```

#### Exceptions (5 files)
```
✅ AIServiceException.java
✅ AISecurityException.java
✅ AIComplianceException.java
✅ AIAuditException.java
✅ AIDataPrivacyException.java
```

#### Basic Security/Privacy (3 files - minimal)
```
✅ PIIDetectionService.java (basic detection only)
✅ AuditService.java (basic audit)
✅ AIContentFilterService.java (basic filtering)
```

#### Utilities (2 files)
```
✅ MetadataJsonSerializer.java
✅ AIHealthIndicator.java
```

---

### 🔵 EXTRACT TO: ai-infrastructure-web (~6 files)

#### Controllers (6 files)
```
🔄 AdvancedRAGController.java
🔄 AIAuditController.java
🔄 AIComplianceController.java
🔄 AIMonitoringController.java
🔄 AIProfileController.java
🔄 AISecurityController.java
```

**New Module Structure**:
```
ai-infrastructure-web/
├── pom.xml (depends on core)
├── src/main/java/
│   └── com/ai/infrastructure/web/
│       ├── controller/
│       │   ├── AdvancedRAGController.java
│       │   ├── AIAuditController.java
│       │   ├── AIComplianceController.java
│       │   ├── AIMonitoringController.java
│       │   ├── AIProfileController.java
│       │   └── AISecurityController.java
│       └── config/
│           └── AIWebAutoConfiguration.java
└── src/main/resources/
    └── META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

### 🟣 EXTRACT TO: ai-infrastructure-orchestration (~14-19 files)

#### Intent System (14 files)
```
🔄 RAGOrchestrator.java (517 lines!)
🔄 IntentQueryExtractor.java
🔄 IntentHistoryService.java
🔄 ActionHandler.java
🔄 ActionHandlerRegistry.java
🔄 ActionInfo.java
🔄 ActionResult.java
🔄 AIActionMetaData.java
🔄 AIActionProvider.java
🔄 AvailableActionsRegistry.java
🔄 ClearVectorIndexActionHandler.java
🔄 RemoveVectorActionHandler.java
🔄 EnrichedPromptBuilder.java
🔄 KnowledgeBaseOverview.java
🔄 KnowledgeBaseOverviewService.java
🔄 SystemContext.java
🔄 SystemContextBuilder.java
🔄 OrchestrationResult.java
🔄 OrchestrationResultType.java
```

**New Module Structure**:
```
ai-infrastructure-orchestration/
├── pom.xml (depends on core)
├── src/main/java/
│   └── com/ai/infrastructure/orchestration/
│       ├── RAGOrchestrator.java
│       ├── intent/
│       │   ├── IntentQueryExtractor.java
│       │   ├── IntentHistoryService.java
│       │   └── ...
│       ├── action/
│       │   ├── ActionHandler.java
│       │   ├── ActionHandlerRegistry.java
│       │   ├── handlers/
│       │   │   ├── ClearVectorIndexActionHandler.java
│       │   │   └── RemoveVectorActionHandler.java
│       │   └── ...
│       └── config/
│           └── OrchestrationAutoConfiguration.java
```

---

### 🟡 EXTRACT TO: ai-infrastructure-rag-advanced (~5 files)

#### Advanced RAG (5 files)
```
🔄 AdvancedRAGService.java
🔄 AdvancedRAGRequest.java (DTO)
🔄 AdvancedRAGResponse.java (DTO)
🔄 QueryExpansionService.java (if exists)
🔄 ReRankingService.java (if exists)
```

**New Module Structure**:
```
ai-infrastructure-rag-advanced/
├── pom.xml (depends on core)
├── src/main/java/
│   └── com/ai/infrastructure/rag/advanced/
│       ├── AdvancedRAGService.java
│       ├── QueryExpansionService.java
│       ├── ReRankingService.java
│       ├── dto/
│       │   ├── AdvancedRAGRequest.java
│       │   └── AdvancedRAGResponse.java
│       └── config/
│           └── AdvancedRAGAutoConfiguration.java
```

---

### 🟠 EXTRACT TO: ai-infrastructure-security (~20 files)

#### Security/Compliance/Audit (20 files)
```
🔄 AISecurityService.java
🔄 AIComplianceService.java
🔄 AIAuditService.java
🔄 AIAccessControlService.java
🔄 AIDataPrivacyService.java
🔄 SecurityAnalysisPolicy.java
🔄 SecurityAnalysisResult.java
🔄 ComplianceCheckProvider.java
🔄 ComplianceCheckResult.java
🔄 EntityAccessPolicy.java
🔄 AISecurityRequest.java (DTO)
🔄 AISecurityResponse.java (DTO)
🔄 AIComplianceRequest.java (DTO)
🔄 AIComplianceResponse.java (DTO)
🔄 AIComplianceReport.java (DTO)
🔄 AIAccessControlRequest.java (DTO)
🔄 AIAccessControlResponse.java (DTO)
🔄 AIAuditRequest.java (DTO)
🔄 AIAuditResponse.java (DTO)
🔄 AIAuditLog.java (DTO)
🔄 AIDataPrivacyRequest.java (DTO)
🔄 AIDataPrivacyResponse.java (DTO)
+ Event classes (8 files)
```

**New Module Structure**:
```
ai-infrastructure-security/
├── pom.xml (depends on core)
├── src/main/java/
│   └── com/ai/infrastructure/security/
│       ├── AISecurityService.java
│       ├── AIComplianceService.java
│       ├── AIAuditService.java
│       ├── AIAccessControlService.java
│       ├── AIDataPrivacyService.java
│       ├── policy/
│       │   ├── SecurityAnalysisPolicy.java
│       │   ├── ComplianceCheckProvider.java
│       │   └── EntityAccessPolicy.java
│       ├── dto/
│       │   └── ... (all security DTOs)
│       ├── event/
│       │   └── ... (all security events)
│       └── config/
│           └── SecurityAutoConfiguration.java
```

---

### 🟤 EXTRACT TO: ai-infrastructure-compliance (~4-6 files)

#### User Data Deletion / GDPR (4 files)
```
🔄 UserDataDeletionService.java
🔄 UserDataDeletionResult.java
🔄 UserDataDeletionProvider.java
🔄 BehaviorDeletionPort.java
```

**New Module Structure**:
```
ai-infrastructure-compliance/
├── pom.xml (depends on core)
├── src/main/java/
│   └── com/ai/infrastructure/compliance/
│       ├── UserDataDeletionService.java
│       ├── UserDataDeletionResult.java
│       ├── policy/
│       │   └── UserDataDeletionProvider.java
│       ├── port/
│       │   └── BehaviorDeletionPort.java
│       └── config/
│           └── ComplianceAutoConfiguration.java
```

---

### 🔵 EXTRACT TO: ai-infrastructure-monitoring (~6 files)

#### Monitoring Services (6 files)
```
🔄 AIMetricsService.java
🔄 AIAnalyticsService.java
🔄 AIHealthService.java (enhanced)
```

**New Module Structure**:
```
ai-infrastructure-monitoring/
├── pom.xml (depends on core, actuator)
├── src/main/java/
│   └── com/ai/infrastructure/monitoring/
│       ├── AIMetricsService.java
│       ├── AIAnalyticsService.java
│       ├── AIHealthService.java
│       └── config/
│           └── MonitoringAutoConfiguration.java
```

---

### ⚪ EVALUATE THEN EXTRACT OR DELETE

#### Option 1: ai-infrastructure-api-generator (~4 files)
```
❓ AIAutoGeneratorService.java (interface)
❓ DefaultAIAutoGeneratorService.java (impl)
❓ APIEndpointDefinition.java
❓ APISpecification.java
```
**Decision Needed**: Is this complete? If yes, extract. If no, DELETE.

#### Option 2: ai-infrastructure-validation (~1 file + inner classes)
```
❓ AIValidationService.java (786 lines!)
```
**Decision Needed**: Is this used? Too opinionated? Extract or DELETE.

#### Option 3: ai-infrastructure-cache (~5 files)
```
❓ AIIntelligentCacheService.java
❓ DefaultAIIntelligentCacheService.java
❓ CacheConfig.java
❓ CacheStatistics.java
❓ AICacheConfig.java
```
**Decision Needed**: What makes it "intelligent"? Better than Spring Cache? Extract or DELETE.

---

### 🔴 DELETE IMMEDIATELY

#### Deprecated Code (1 file)
```
❌ PineconeVectorDatabase.java
```
**Reason**: Already marked `@Deprecated(forRemoval = true)`

#### Performance Service (1 file)
```
❌ AIPerformanceService.java
```
**Reason**: Duplicates Spring Cache and @Async functionality

---

### 🧪 MOVE TO TEST (2 files)

#### Mock Services (2 files)
```
🔄 MockAIService.java → src/test/java
🔄 MockAIConfiguration.java → src/test/java
```
**Reason**: Mock implementations belong in test scope

---

## Summary Statistics

### File Counts by Destination:

| Destination | Files | Status |
|-------------|-------|--------|
| **KEEP IN CORE** | ~105 | ✅ Keep |
| **web** | 6 | 🔄 Extract |
| **orchestration** | 14-19 | 🔄 Extract |
| **rag-advanced** | 5 | 🔄 Extract |
| **security** | 20+ | 🔄 Extract |
| **compliance** | 4-6 | 🔄 Extract |
| **monitoring** | 6 | 🔄 Extract |
| **api-generator** | 4 | ❓ Evaluate |
| **validation** | 1 | ❓ Evaluate |
| **cache** | 5 | ❓ Evaluate |
| **DELETE** | 2 | ❌ Remove |
| **MOVE TO TEST** | 2 | 🧪 Move |

### Reduction:
- **Before**: 211 files
- **After Core**: ~105 files
- **Reduction**: ~50%

### New Modules Created:
- **Definite**: 6 modules (web, orchestration, rag-advanced, security, compliance, monitoring)
- **Possible**: 3 modules (api-generator, validation, cache)
- **Test Support**: 1 module

**Total**: 6-10 new modules

---

## Dependency Graph (After Refactoring)

```
┌─────────────────────────────────────────┐
│     ai-infrastructure-core              │
│  (Essential infrastructure only)         │
└─────────────────────────────────────────┘
              ↑    ↑    ↑    ↑
              │    │    │    │
    ┌─────────┘    │    │    └─────────┐
    │              │    │              │
    │              │    │              │
┌───┴───┐   ┌─────┴────┴─────┐   ┌────┴────┐
│  web  │   │  orchestration  │   │ security│
└───────┘   └─────────────────┘   └─────────┘
                    ↑
                    │
           ┌────────┴────────┐
           │                 │
      ┌────┴────┐      ┌────┴────┐
      │rag-adv  │      │complian.│
      └─────────┘      └─────────┘

All depend on core. No cross-dependencies between feature modules.
```

---

## Configuration After Refactoring

### application.yml Example:

```yaml
ai:
  enabled: true                    # Core module
  
  # Optional modules (disabled by default)
  web:
    enabled: false                 # REST controllers
  
  orchestration:
    enabled: false                 # Intent system
  
  rag:
    advanced:
      enabled: false               # Advanced RAG
  
  security:
    enabled: false                 # Enhanced security
  
  compliance:
    enabled: false                 # GDPR features
  
  monitoring:
    enabled: false                 # Metrics/analytics
```

### Maven Dependencies Example:

```xml
<!-- Core - Always needed -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>

<!-- Optional - Only if you want REST controllers -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-web</artifactId>
</dependency>

<!-- Optional - Only if you want intent orchestration -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-orchestration</artifactId>
</dependency>
```

---

**Last Updated**: November 25, 2025  
**Status**: Ready for Implementation  
**Next Step**: Begin Phase 1 - Quick Wins
