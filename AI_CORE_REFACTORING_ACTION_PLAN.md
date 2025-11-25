# AI-Core Module Refactoring Action Plan

## 🎯 Quick Summary

The `ai-infrastructure-core` module is **too large** (211 Java files) and contains **too many concerns**. 

**Key Issues**:
1. ❌ REST Controllers in infrastructure module (should be in web layer)
2. ❌ Complex orchestration/intent logic (application-level, not infrastructure)
3. ❌ Incomplete/stub features (API Generator, Intelligent Cache)
4. ❌ Business validation logic in infrastructure
5. ❌ Deprecated code still present
6. ❌ Mock services in main source code

**Recommended Split**: 
- Core → ~100-120 files (essential infrastructure only)
- Extract → ~90-110 files to 6-8 new modules

---

## 📋 Quick Action Checklist

### 🔴 Critical - Do First (Week 1)

- [ ] **Delete** `PineconeVectorDatabase.java` - Already marked for removal
- [ ] **Move** mock services (`MockAIService`, `MockAIConfiguration`) to `src/test/java`
- [ ] **Extract** 6 controllers to new module `ai-infrastructure-web`
  - AdvancedRAGController
  - AIAuditController
  - AIComplianceController
  - AIMonitoringController
  - AIProfileController
  - AISecurityController

### 🟡 High Priority (Week 2-3)

- [ ] **Extract** Intent/Orchestration system (14 files) to `ai-infrastructure-orchestration`
  - RAGOrchestrator (517 lines!)
  - IntentQueryExtractor
  - ActionHandlerRegistry
  - All action handlers
  - Intent history service

- [ ] **Extract** `AdvancedRAGService` to `ai-infrastructure-rag-advanced`

- [ ] **Evaluate & Decide** on `AIAutoGeneratorService`:
  - [ ] Review implementation completeness
  - [ ] If incomplete → DELETE
  - [ ] If complete → Extract to `ai-infrastructure-api-generator`

### 🟢 Medium Priority (Week 4-5)

- [ ] **Extract** Security/Compliance to `ai-infrastructure-security`
  - AISecurityService
  - AIComplianceService
  - AIAuditService
  - AIAccessControlService
  - Related events and policies

- [ ] **Evaluate** `AIValidationService` (786 lines):
  - [ ] Review if used in production
  - [ ] If used → Extract to `ai-infrastructure-validation`
  - [ ] If not → DELETE (too opinionated for infrastructure)

- [ ] **Extract** User Deletion features to `ai-infrastructure-compliance`
  - UserDataDeletionService
  - GDPR compliance features

### ⚪ Low Priority (Week 6+)

- [ ] **Delete** `AIPerformanceService` (duplicates Spring features)
- [ ] **Evaluate** `AIIntelligentCacheService`:
  - [ ] If has unique value → Extract to `ai-infrastructure-cache`
  - [ ] If not → DELETE (use Spring Cache)

- [ ] **Extract** Monitoring to `ai-infrastructure-monitoring`
  - AIMetricsService
  - AIAnalyticsService
  - AIHealthService

---

## 📊 Impact Analysis

### Before Refactoring:
```
ai-infrastructure-core: 211 files
├── Everything mixed together
├── Hard to use (too many dependencies)
└── Forces all features on consumers
```

### After Refactoring:
```
ai-infrastructure-core: ~105 files (essential only)
├── Core AI services
├── RAG foundation
├── Provider management
└── Configuration

ai-infrastructure-web: ~6 files
└── REST controllers (optional)

ai-infrastructure-orchestration: ~14 files
└── Intent system (optional)

ai-infrastructure-rag-advanced: ~5 files
└── Advanced RAG features (optional)

ai-infrastructure-security: ~20 files
└── Security/Audit/Compliance (optional)

[Additional optional modules...]
```

### Benefits:
✅ **Modular**: Pick only what you need  
✅ **Maintainable**: Clear boundaries  
✅ **Testable**: Isolated concerns  
✅ **Extensible**: Easy to add features  
✅ **Smaller artifacts**: Faster builds  

---

## 🚀 Execution Strategy

### Phase 1: Quick Wins (3-4 days)
**Goal**: Remove obvious issues

```bash
# Day 1: Delete deprecated code
git rm ai-infrastructure-core/src/main/java/com/ai/infrastructure/vector/PineconeVectorDatabase.java

# Day 2: Move mocks to test
mkdir -p ai-infrastructure-core/src/test/java/com/ai/infrastructure/mock
git mv ai-infrastructure-core/src/main/java/com/ai/infrastructure/mock/* \
       ai-infrastructure-core/src/test/java/com/ai/infrastructure/mock/

# Day 3-4: Create web module and move controllers
./scripts/extract-web-module.sh
```

### Phase 2: Extract Web Layer (2-3 days)
**Goal**: Remove REST controllers from core

1. Create new module: `ai-infrastructure-web`
2. Add pom.xml with dependency on core
3. Move all 6 controllers
4. Create AutoConfiguration for web beans
5. Update documentation
6. Test backward compatibility

### Phase 3: Extract Orchestration (4-5 days)
**Goal**: Separate application logic from infrastructure

1. Create new module: `ai-infrastructure-orchestration`
2. Move intent system (14 files)
3. Define clear interfaces
4. Update dependencies
5. Create integration tests
6. Update documentation

### Phase 4: Extract Advanced Features (5-7 days)
**Goal**: Make advanced features optional

1. Extract `AdvancedRAGService`
2. Extract or delete `AIAutoGeneratorService`
3. Extract or delete `AIValidationService`
4. Extract security/compliance
5. Update documentation

### Phase 5: Final Cleanup (2-3 days)
**Goal**: Polish and document

1. Clean up core dependencies
2. Update README files
3. Create migration guide
4. Write upgrade notes
5. Update examples

---

## 📝 Module Creation Template

For each extracted module:

### 1. Create Module Structure
```bash
mkdir -p ai-infrastructure-MODULE_NAME/src/main/java/com/ai/infrastructure/MODULE_NAME
mkdir -p ai-infrastructure-MODULE_NAME/src/main/resources
mkdir -p ai-infrastructure-MODULE_NAME/src/test/java/com/ai/infrastructure/MODULE_NAME
```

### 2. Create pom.xml
```xml
<artifactId>ai-infrastructure-MODULE_NAME</artifactId>
<dependencies>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
    </dependency>
</dependencies>
```

### 3. Create AutoConfiguration
```java
@Configuration
@EnableConfigurationProperties(ModuleProperties.class)
@ConditionalOnProperty(prefix = "ai.MODULE_NAME", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ModuleAutoConfiguration {
    // Bean definitions
}
```

### 4. Create spring.factories or AutoConfiguration.imports
```
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ai.infrastructure.MODULE_NAME.ModuleAutoConfiguration
```

### 5. Update Parent POM
```xml
<module>ai-infrastructure-MODULE_NAME</module>
```

---

## 🧪 Testing Strategy

### For Each Extraction:

1. **Unit Tests**
   - Test extracted module in isolation
   - Mock dependencies

2. **Integration Tests**
   - Test with core module
   - Test AutoConfiguration loading

3. **Backward Compatibility Tests**
   - Ensure existing code still works
   - Test with/without new module

4. **Performance Tests**
   - Ensure no performance regression
   - Test startup time

---

## 📚 Documentation Updates

### For Each Module:

- [ ] Create README.md with:
  - Purpose
  - Features
  - Configuration properties
  - Usage examples
  - Migration notes

- [ ] Update main documentation:
  - Architecture diagrams
  - Module dependency graph
  - Getting started guide
  - Upgrade guide

- [ ] Update JavaDocs:
  - Add deprecation notices
  - Add module references
  - Update package docs

---

## ⚠️ Risks & Mitigation

### Risk 1: Breaking Existing Consumers
**Mitigation**:
- Keep old classes with @Deprecated
- Add migration guide
- Provide backward-compatibility module
- Version bump (major version)

### Risk 2: Complex Dependencies
**Mitigation**:
- Create dependency graph
- Refactor in small steps
- Test each extraction
- Use Maven dependency analysis

### Risk 3: Incomplete Features
**Mitigation**:
- Document incomplete features
- Add warnings in code
- Consider deleting if not used
- Get team input before deletion

### Risk 4: Time Constraints
**Mitigation**:
- Prioritize high-impact items
- Do quick wins first
- Can do in phases
- Document what's left

---

## 📊 Success Metrics

### Code Metrics:
- [ ] Core module: <120 files (from 211)
- [ ] Module count: 6-8 new modules
- [ ] Test coverage: >80% on extracted modules
- [ ] Build time: Not increased

### Quality Metrics:
- [ ] Cyclomatic complexity: Reduced
- [ ] Coupling: Reduced between modules
- [ ] Cohesion: Increased within modules
- [ ] Technical debt: Reduced

### User Metrics:
- [ ] Migration guide: Complete
- [ ] Breaking changes: Documented
- [ ] Examples: Updated
- [ ] Support issues: <10% increase

---

## 👥 Team Coordination

### Before Starting:
- [ ] Review analysis with team
- [ ] Get buy-in on approach
- [ ] Assign module ownership
- [ ] Set timeline expectations

### During Execution:
- [ ] Daily standups on progress
- [ ] Weekly demos of extracted modules
- [ ] Code reviews on each PR
- [ ] Update documentation continuously

### After Completion:
- [ ] Retrospective on process
- [ ] Document lessons learned
- [ ] Celebrate wins
- [ ] Plan next improvements

---

## 📅 Timeline

### Conservative Estimate (Sequential):
- **Phase 1**: 3-4 days (quick wins)
- **Phase 2**: 2-3 days (web layer)
- **Phase 3**: 4-5 days (orchestration)
- **Phase 4**: 5-7 days (advanced features)
- **Phase 5**: 2-3 days (cleanup)

**Total**: 16-22 working days (~3-4 weeks)

### Aggressive Estimate (Parallel):
- Multiple people working simultaneously
- **Total**: 10-15 working days (~2-3 weeks)

---

## 🎯 Definition of Done

For the refactoring to be considered complete:

- [ ] Core module: <120 files
- [ ] All controllers: Moved to web module
- [ ] All orchestration: Moved to orchestration module
- [ ] All deprecated code: Removed
- [ ] All mock code: Moved to test scope
- [ ] All tests: Passing
- [ ] All documentation: Updated
- [ ] Migration guide: Written
- [ ] Examples: Updated
- [ ] Code review: Approved
- [ ] Team: Trained on new structure

---

## 📞 Questions to Resolve

1. **API Auto-Generator Service**:
   - Is it used in production?
   - Is it complete?
   - Decision: Extract or Delete?

2. **AI Validation Service**:
   - Is it used in production?
   - Is it business-specific?
   - Decision: Extract, Move to App Layer, or Delete?

3. **Intelligent Cache Service**:
   - What makes it "intelligent"?
   - Is it better than Spring Cache?
   - Decision: Extract or Delete?

4. **Performance Service**:
   - Is it used anywhere?
   - Decision: Delete? (likely yes)

5. **Monitoring Services**:
   - Are they connected to metrics backend?
   - Decision: Complete and Extract, or Delete?

6. **User Data Deletion**:
   - Is this GDPR compliance critical?
   - Decision: Extract to compliance module

---

## 🔗 Related Documents

- [Full Analysis](./AI_CORE_MODULE_ANALYSIS.md) - Detailed analysis of all files
- [Module Dependencies](./MODULE_DEPENDENCIES.md) - To be created
- [Migration Guide](./AI_CORE_MIGRATION_GUIDE.md) - To be created
- [Architecture Decisions](./ARCHITECTURE_DECISIONS.md) - To be created

---

**Last Updated**: November 25, 2025  
**Status**: Ready for Team Review  
**Next Step**: Team discussion and approval
