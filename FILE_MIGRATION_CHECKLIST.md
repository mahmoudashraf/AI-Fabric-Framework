# File-by-File Migration Checklist

## Backend to AI Infrastructure Module Migration

### Phase 1: Move Generic AI Services

#### 1.1 Core AI Services
- [ ] **BehaviorTrackingService.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/service/BehaviorTrackingService.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/behavior/GenericBehaviorTrackingService.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove UserBehavior entity dependency

- [ ] **ContentValidationService.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/service/ContentValidationService.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/validation/GenericContentValidationService.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific DTOs

- [ ] **UIAdaptationService.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/service/UIAdaptationService.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/personalization/GenericUIAdaptationService.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove User entity dependency

- [ ] **RecommendationEngine.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/service/RecommendationEngine.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/recommendation/GenericRecommendationEngine.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove Product/User entity dependencies

- [ ] **AISmartValidation.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/service/AISmartValidation.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/validation/GenericAISmartValidation.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific validation

- [ ] **ValidationRuleEngine.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/service/ValidationRuleEngine.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/validation/GenericValidationRuleEngine.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific rules

- [ ] **AIMonitoringService.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/service/AIMonitoringService.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/monitoring/GenericAIMonitoringService.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific monitoring

- [ ] **AIHelperService.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/service/AIHelperService.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/util/GenericAIHelperService.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific helpers

#### 1.2 Generic Controllers
- [ ] **BehavioralAIController.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/controller/BehavioralAIController.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/controller/GenericBehavioralAIController.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific DTOs

- [ ] **SmartValidationController.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/controller/SmartValidationController.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/controller/GenericSmartValidationController.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific DTOs

- [ ] **AIController.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/controller/AIController.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/controller/GenericAIController.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific DTOs

#### 1.3 Generic DTOs
- [ ] **BehaviorAnalysisResult.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/dto/BehaviorAnalysisResult.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/dto/GenericBehaviorAnalysisResult.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific fields

- [ ] **TextValidationResult.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/dto/TextValidationResult.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/dto/GenericTextValidationResult.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific validation

- [ ] **RecommendationResult.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/dto/RecommendationResult.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/dto/GenericRecommendationResult.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific fields

- [ ] **AIHealthStatus.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/dto/AIHealthStatus.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/dto/GenericAIHealthStatus.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific health checks

#### 1.4 Generic Configuration
- [ ] **AIConfigurationValidator.java**
  - **From**: `backend/src/main/java/com/easyluxury/ai/config/AIConfigurationValidator.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/config/GenericAIConfigurationValidator.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove domain-specific validation

### Phase 2: Move AI-Specific Entities

#### 2.1 AI Entities
- [ ] **UserBehavior.java**
  - **From**: `backend/src/main/java/com/easyluxury/entity/UserBehavior.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/entity/Behavior.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove User entity dependency

- [ ] **AIProfile.java**
  - **From**: `backend/src/main/java/com/easyluxury/entity/AIProfile.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/entity/AIProfile.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove User entity dependency

#### 2.2 AI Repositories
- [ ] **UserBehaviorRepository.java**
  - **From**: `backend/src/main/java/com/easyluxury/repository/UserBehaviorRepository.java`
  - **To**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/repository/BehaviorRepository.java`
  - **Changes**: Remove domain coupling, make generic
  - **Dependencies**: Update imports, remove UserBehavior entity dependency

### Phase 3: Create Domain-Specific Adapters

#### 3.1 User AI Adapter
- [ ] **UserAIAdapter.java** (NEW)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/adapter/UserAIAdapter.java`
  - **Purpose**: Bridge between User domain and generic AI services
  - **Dependencies**: Generic AI services, UserRepository

#### 3.2 Product AI Adapter
- [ ] **ProductAIAdapter.java** (NEW)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/adapter/ProductAIAdapter.java`
  - **Purpose**: Bridge between Product domain and generic AI services
  - **Dependencies**: Generic AI services, ProductRepository

#### 3.3 Order AI Adapter
- [ ] **OrderAIAdapter.java** (NEW)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/adapter/OrderAIAdapter.java`
  - **Purpose**: Bridge between Order domain and generic AI services
  - **Dependencies**: Generic AI services, OrderRepository

### Phase 4: Update Domain Services

#### 4.1 Update User AI Services
- [ ] **UserAIService.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/service/UserAIService.java`
  - **Changes**: Use UserAIAdapter instead of direct AI services
  - **Dependencies**: UserAIAdapter, UserRepository

- [ ] **UserBehaviorService.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/service/UserBehaviorService.java`
  - **Changes**: Use UserAIAdapter instead of direct AI services
  - **Dependencies**: UserAIAdapter, UserRepository

#### 4.2 Update Product AI Services
- [ ] **ProductAIService.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/service/ProductAIService.java`
  - **Changes**: Use ProductAIAdapter instead of direct AI services
  - **Dependencies**: ProductAIAdapter, ProductRepository

#### 4.3 Update Order AI Services
- [ ] **OrderAIService.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/service/OrderAIService.java`
  - **Changes**: Use OrderAIAdapter instead of direct AI services
  - **Dependencies**: OrderAIAdapter, OrderRepository

- [ ] **OrderPatternService.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/service/OrderPatternService.java`
  - **Changes**: Use OrderAIAdapter instead of direct AI services
  - **Dependencies**: OrderAIAdapter, OrderRepository

### Phase 5: Update Controllers

#### 5.1 Update User AI Controllers
- [ ] **UserAIController.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/controller/UserAIController.java`
  - **Changes**: Use updated UserAIService
  - **Dependencies**: Updated UserAIService

#### 5.2 Update Product AI Controllers
- [ ] **ProductAIController.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/controller/ProductAIController.java`
  - **Changes**: Use updated ProductAIService
  - **Dependencies**: Updated ProductAIService

#### 5.3 Update Order AI Controllers
- [ ] **OrderAIController.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/controller/OrderAIController.java`
  - **Changes**: Use updated OrderAIService
  - **Dependencies**: Updated OrderAIService

### Phase 6: Update Facades

#### 6.1 Update AI Facades
- [ ] **UserAIFacade.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/facade/UserAIFacade.java`
  - **Changes**: Use updated UserAIService
  - **Dependencies**: Updated UserAIService

- [ ] **ProductAIFacade.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/facade/ProductAIFacade.java`
  - **Changes**: Use updated ProductAIService
  - **Dependencies**: Updated ProductAIService

- [ ] **OrderAIFacade.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/facade/OrderAIFacade.java`
  - **Changes**: Use updated OrderAIService
  - **Dependencies**: Updated OrderAIService

- [ ] **AIFacade.java** (UPDATE)
  - **Location**: `backend/src/main/java/com/easyluxury/ai/facade/AIFacade.java`
  - **Changes**: Use updated AI services
  - **Dependencies**: Updated AI services

### Phase 7: Clean Up Domain Entities

#### 7.1 Clean User Entity
- [ ] **User.java** (CLEAN)
  - **Location**: `backend/src/main/java/com/easyluxury/entity/User.java`
  - **Changes**: Remove AI annotations and fields
  - **Remove**: `@AICapable`, `@AIKnowledge`, `@AIEmbedding`, `searchVector`, `behaviorScore`, `aiInsights`

#### 7.2 Clean Product Entity
- [ ] **Product.java** (CLEAN)
  - **Location**: `backend/src/main/java/com/easyluxury/entity/Product.java`
  - **Changes**: Remove AI annotations and fields
  - **Remove**: `@AICapable`, `@AIKnowledge`, `@AIEmbedding`, `searchVector`

#### 7.3 Clean Order Entity
- [ ] **Order.java** (CLEAN)
  - **Location**: `backend/src/main/java/com/easyluxury/entity/Order.java`
  - **Changes**: Remove AI annotations and fields
  - **Remove**: `@AICapable`, `@AIKnowledge`, `@AIEmbedding`, `searchVector`, `aiAnalysis`, `aiInsights`, `patternFlags`

### Phase 8: Create Configuration Files

#### 8.1 AI Entity Configuration
- [ ] **ai-entity-config.yml** (NEW)
  - **Location**: `backend/src/main/resources/ai-entity-config.yml`
  - **Purpose**: Configure AI behavior for domain entities
  - **Content**: User, Product, Order AI configurations

#### 8.2 AI Infrastructure Configuration
- [ ] **ai-entity-config.yml** (NEW)
  - **Location**: `ai-infrastructure-module/src/main/resources/ai-entity-config.yml`
  - **Purpose**: Configure generic AI behavior
  - **Content**: Generic AI configurations

### Phase 9: Update Dependencies

#### 9.1 Update AI Infrastructure Module Dependencies
- [ ] **pom.xml** (UPDATE)
  - **Location**: `ai-infrastructure-module/pom.xml`
  - **Changes**: Add dependencies for moved services
  - **Add**: Spring AOP, YAML processing, additional AI services

#### 9.2 Update Backend Module Dependencies
- [ ] **pom.xml** (UPDATE)
  - **Location**: `backend/pom.xml`
  - **Changes**: Update AI infrastructure dependency
  - **Update**: AI infrastructure module version

### Phase 10: Testing and Validation

#### 10.1 Unit Tests
- [ ] **Generic AI Services Tests**
  - **Location**: `ai-infrastructure-module/src/test/java/`
  - **Purpose**: Test generic AI services
  - **Coverage**: All moved services

- [ ] **Domain AI Services Tests**
  - **Location**: `backend/src/test/java/`
  - **Purpose**: Test updated domain services
  - **Coverage**: All updated services

#### 10.2 Integration Tests
- [ ] **AI Infrastructure Tests**
  - **Location**: `ai-infrastructure-module/src/test/java/`
  - **Purpose**: Test AI infrastructure integration
  - **Coverage**: End-to-end AI processing

- [ ] **Backend AI Tests**
  - **Location**: `backend/src/test/java/`
  - **Purpose**: Test backend AI integration
  - **Coverage**: Domain-specific AI functionality

#### 10.3 Migration Tests
- [ ] **Backward Compatibility Tests**
  - **Location**: `backend/src/test/java/`
  - **Purpose**: Test backward compatibility
  - **Coverage**: Existing functionality

- [ ] **Performance Tests**
  - **Location**: `backend/src/test/java/`
  - **Purpose**: Test performance impact
  - **Coverage**: AI processing performance

## Summary

This checklist provides a comprehensive, file-by-file migration plan for moving AI-related components from the backend to the AI infrastructure module. The migration follows a phased approach:

1. **Phase 1-2**: Move generic AI services and entities
2. **Phase 3**: Create domain-specific adapters
3. **Phase 4-6**: Update domain services, controllers, and facades
4. **Phase 7**: Clean up domain entities
5. **Phase 8**: Create configuration files
6. **Phase 9**: Update dependencies
7. **Phase 10**: Testing and validation

The result will be a clean separation between generic AI infrastructure and domain-specific AI services, enabling the AI infrastructure to be truly reusable across different applications and domains.