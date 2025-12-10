# Search vs LLM Generation Feature - Implementation Checklist

## Pre-Implementation Phase (Days 1-2)

### Code Review & Planning
- [ ] Review implementation document: `SEARCH_VS_LLM_GENERATION_FEATURE.md`
- [ ] Review all code changes with team
- [ ] Identify potential edge cases in your system
- [ ] Plan rollout timeline
- [ ] Set up feature flag in configuration management

### Environment Setup
- [ ] Ensure development environment has all dependencies
- [ ] Set up staging environment matching production
- [ ] Configure test data for integration tests
- [ ] Verify OpenAI API key configured for tests

---

## Implementation Phase (Days 3-5)

### Step 1: Add `requiresGeneration` Flag to Intent
**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/Intent.java`

- [ ] Add `@JsonAlias({"requires_generation"})` field
- [ ] Implement `detectIfGenerationNeeded()` method
- [ ] Add `requiresGenerationOrDefault()` getter
- [ ] Update `normalize()` method to set default value
- [ ] Update unit tests for Intent class
- [ ] Verify serialization/deserialization works with JSON
- [ ] Test backward compatibility (missing field)

**Verification**:
```bash
mvn test -Dtest=IntentTest
```

---

### Step 2: Verify SearchableFieldConfig Has include-in-rag Support
**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/SearchableFieldConfig.java`

- [ ] Verify `includeInRag` field exists and defaults to true
- [ ] Verify `getIncludeInRag()` getter exists
- [ ] Verify JSON serialization handles field
- [ ] Check all usages in codebase

**Verification**:
```bash
grep -r "includeInRag\|include_in_rag" ai-infrastructure-module
```

---

### Step 3: Add LLM Context Filtering to RAGOrchestrator
**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/RAGOrchestrator.java`

- [ ] Add `filterContextForLLM()` method
- [ ] Add `convertToMap()` helper method
- [ ] Add `generateLLMResponse()` method stub (or integrate with existing LLM service)
- [ ] Add `formatDocument()` helper for prompt building
- [ ] Implement error handling and logging
- [ ] Add JavaDoc comments

**Verification**:
```bash
mvn compile
```

---

### Step 4: Update handleInformation() Method
**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/RAGOrchestrator.java`

- [ ] Add `requiresGeneration` check at beginning of method
- [ ] Implement search-only flow (when requiresGeneration=false)
- [ ] Implement LLM generation flow (when requiresGeneration=true)
- [ ] Add context filtering step for LLM
- [ ] Add error handling and fallback logic
- [ ] Log both flows appropriately
- [ ] Preserve all existing functionality

**Verification**:
```bash
mvn test -Dtest=RAGOrchestratorTest
```

---

### Step 5: Update ai-entity-config.yml
**File**: `ai-infrastructure-core/src/main/resources/ai-entity-config.yml`

- [ ] Review all `searchable-fields` configurations
- [ ] Add `include-in-rag: true/false` for each field (explicit, not default)
- [ ] Document which fields should be hidden from LLM
- [ ] Validate YAML syntax
- [ ] Test configuration loading

**Example**:
```yaml
searchable-fields:
  - name: costPrice
    include-in-rag: false  ← Add this
    enable-semantic-search: true
    weight: 1.0
```

---

## Testing Phase (Days 6-8)

### Unit Tests
- [ ] Run all Intent tests
- [ ] Run all RAGOrchestrator tests
- [ ] Run all configuration tests
- [ ] Verify 100% pass rate

```bash
mvn test -DskipITs=true
```

### Integration Tests (Mocked)
- [ ] Run SearchVsLLMGenerationIntegrationTest
- [ ] Verify all test cases pass
- [ ] Verify mocking is correct
- [ ] Check code coverage (aim for > 80%)

```bash
mvn verify -Dtest=SearchVsLLMGenerationIntegrationTest
```

### Real API Integration Tests
- [ ] Set OPENAI_API_KEY environment variable
- [ ] Run SearchVsLLMGenerationRealApiIntegrationTest
- [ ] Verify search-only queries work
- [ ] Verify LLM generation queries work
- [ ] Verify context filtering works correctly
- [ ] Check response times are acceptable

```bash
export OPENAI_API_KEY=your-key-here
mvn verify -Dtest=SearchVsLLMGenerationRealApiIntegrationTest
```

### End-to-End Tests
- [ ] Test with real user scenarios
- [ ] Verify search results are complete (no filtering)
- [ ] Verify LLM results have filtered context
- [ ] Check that include-in-rag: false fields are hidden
- [ ] Verify fallback behavior when LLM fails

---

## Staging Deployment (Day 9)

### Pre-Deployment Checks
- [ ] All tests passing locally
- [ ] All tests passing in CI/CD
- [ ] Code review approved
- [ ] Performance benchmarks acceptable
- [ ] Database migrations complete (if any)
- [ ] Configuration in staging matches expectations

### Deployment
- [ ] Deploy to staging environment
- [ ] Verify application starts correctly
- [ ] Run smoke tests in staging
- [ ] Run full integration test suite in staging
- [ ] Verify logging is working
- [ ] Check monitoring/metrics are reporting

```bash
# Staging deployment
./scripts/deploy-to-staging.sh

# Smoke tests
mvn verify -P staging -DskipITs=false
```

### Staging Validation
- [ ] Query search-only scenarios
- [ ] Query LLM generation scenarios
- [ ] Verify context filtering in logs
- [ ] Check database for any issues
- [ ] Review error logs for warnings
- [ ] Load test with realistic traffic

---

## Production Rollout (Days 10-14)

### Phase 1: Feature Flag (0% Traffic)
- [ ] Disable feature via feature flag
- [ ] Deploy to production
- [ ] Verify application health
- [ ] Run smoke tests
- [ ] No traffic to new feature

**Timeline**: Days 10-11

### Phase 2: Soft Launch (5% Traffic)
- [ ] Enable feature flag to 5%
- [ ] Monitor error rates
- [ ] Monitor latency
- [ ] Check logs for issues
- [ ] Verify LLM generation quality

**Timeline**: Days 11-12

**Success Criteria**:
- Error rate < 1%
- Latency within targets
- LLM generation working correctly
- No data loss or corruption

### Phase 3: Gradual Rollout (25% Traffic)
- [ ] Increase to 25%
- [ ] Continue monitoring
- [ ] Gather user feedback
- [ ] Check metrics trending correctly
- [ ] Verify no regressions

**Timeline**: Day 12-13

**Success Criteria**:
- Error rate < 1%
- LLM generation success > 95%
- Context filtering working correctly
- User feedback positive

### Phase 4: Full Rollout (100% Traffic)
- [ ] Increase to 100%
- [ ] Remove feature flag dependency
- [ ] Continue monitoring 24/7
- [ ] Have rollback plan ready

**Timeline**: Day 13-14

---

## Post-Deployment (Day 15+)

### Monitoring & Metrics
- [ ] Monitor error rate (target: < 0.5%)
- [ ] Monitor LLM generation latency (target: < 3 sec)
- [ ] Monitor search-only latency (target: < 250ms)
- [ ] Monitor LLM generation success rate (target: > 95%)
- [ ] Track context filtering metrics
- [ ] Review error logs daily
- [ ] Check database performance impact

### User Communication
- [ ] Announce feature to users
- [ ] Provide documentation
- [ ] Gather feedback
- [ ] Create FAQ if needed
- [ ] Support team training

### Documentation Updates
- [ ] Update API documentation
- [ ] Update configuration guides
- [ ] Update troubleshooting guides
- [ ] Document metrics and dashboards
- [ ] Create runbooks for common issues

### Performance Optimization
- [ ] Analyze performance data
- [ ] Optimize slow paths
- [ ] Cache entity configurations
- [ ] Cache filtering results if applicable
- [ ] Monitor LLM token usage

---

## Rollback Plan

### If Critical Issues Arise

1. **Immediate Action** (< 5 minutes)
   - [ ] Disable feature via feature flag (0% traffic)
   - [ ] Alert on-call team
   - [ ] Create incident ticket

2. **Investigation** (5-30 minutes)
   - [ ] Collect logs and metrics
   - [ ] Identify root cause
   - [ ] Determine if rollback needed

3. **Rollback** (if required)
   - [ ] Revert code changes
   - [ ] Restart services
   - [ ] Verify old behavior restored
   - [ ] Confirm users unaffected

4. **Post-Rollback** (after restoration)
   - [ ] Root cause analysis
   - [ ] Fix issues found
   - [ ] Re-test comprehensively
   - [ ] Plan re-deployment

---

## Success Metrics

### Technical Metrics
- ✅ LLM generation success rate > 95%
- ✅ Search-only latency < 250ms
- ✅ LLM generation latency < 3 seconds
- ✅ Error rate < 0.5%
- ✅ Zero data loss or corruption
- ✅ All tests passing

### User Experience Metrics
- ✅ User satisfaction score > 4.0/5.0
- ✅ Feature adoption rate > 80%
- ✅ Support tickets < 10/day
- ✅ Positive feedback ratio > 70%

### Business Metrics
- ✅ Zero incidents requiring rollback
- ✅ No performance degradation
- ✅ LLM token usage within budget
- ✅ Feature utilization > 50% of eligible queries

---

## Sign-Off

### Implementation Lead
- Name: _________________
- Date: _________________
- Approval: ☐ Approved  ☐ Rejected

### QA Lead
- Name: _________________
- Date: _________________
- Approval: ☐ Approved  ☐ Rejected

### DevOps Lead
- Name: _________________
- Date: _________________
- Approval: ☐ Approved  ☐ Rejected

### Product Owner
- Name: _________________
- Date: _________________
- Approval: ☐ Approved  ☐ Rejected

---

## References

- Implementation Document: `SEARCH_VS_LLM_GENERATION_FEATURE.md`
- Test File (Unit): `SearchVsLLMGenerationIntegrationTest.java`
- Test File (Real API): `SearchVsLLMGenerationRealApiIntegrationTest.java`
- Feature: Search vs LLM Generation Differentiation
- Epic: Privacy & Policy Enforcement Framework

---

## Troubleshooting Guide

### Issue: LLM generation very slow

**Solution**:
1. Check LLM service latency
2. Verify network connectivity
3. Check LLM token limits
4. Reduce context size if needed
5. Enable context caching

### Issue: Context filtering removing too many fields

**Solution**:
1. Review include-in-rag: false configuration
2. Verify fields are intentionally hidden
3. Check if default should be true instead
4. Update configuration in ai-entity-config.yml
5. Re-deploy

### Issue: Search results incomplete

**Solution**:
1. Verify search-only flow is executed (requiresGeneration=false)
2. Check no filtering applied in search flow
3. Verify RAG service returning all fields
4. Check database indexes

### Issue: Intent detection incorrect

**Solution**:
1. Review keyword detection regex in Intent.normalize()
2. Add logging to see detected intent
3. Adjust keywords or heuristics
4. Consider using ML model for detection
5. Update detectIfGenerationNeeded() logic

