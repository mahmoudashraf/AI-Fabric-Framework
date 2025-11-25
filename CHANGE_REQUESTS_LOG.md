# Change Requests Log

## Session Information
- **Date**: November 25, 2025
- **Context**: AI-Core Module Refactoring
- **Status**: Ready to receive change requests

---

## Change Requests

### Request #1
**Requested**: November 25, 2025
**Description**: Keep orchestration system in core module
**Action Required**: 
- Do NOT extract orchestration system (14 files) to separate module
- Keep RAGOrchestrator, IntentQueryExtractor, ActionHandlerRegistry, etc. in core
- Update analysis documents to reflect this decision
**Status**: ✅ Registered
**Notes**: 
- This contradicts original recommendation to extract to `ai-infrastructure-orchestration`
- Files affected: ~14 files in `com.ai.infrastructure.intent` package
- Rationale: Orchestration is considered core functionality, not optional feature

---

### Request #2
**Requested**: November 25, 2025
**Description**: Question: Do the 6 REST controllers really do any real job?
**Analysis Completed**: See CONTROLLER_REAL_JOB_ANALYSIS.md
**Answer**: ✅ YES - All 6 controllers provide real functionality:
- 1,171 total lines of code
- 59 REST endpoints
- Delegate to actual service implementations
- Provide significant business value (audit, security, monitoring, profiles, compliance, advanced RAG)

**Key Findings**:
- AIProfileController: 358 lines, 22 endpoints (full CRUD API)
- AIMonitoringController: 279 lines, 15 endpoints (observability)
- AIAuditController: 226 lines, 11 endpoints (audit trail)
- AISecurityController: 141 lines, 6 endpoints (threat detection)
- AdvancedRAGController: 95 lines, 3 endpoints (advanced search)
- AIComplianceController: 72 lines, 2 endpoints (compliance checks)

**Decision Made**: ✅ Extract ALL to ai-infrastructure-web (Option 1)

**Status**: 📋 Implementation plan created
**Notes**: 
- Full analysis in CONTROLLER_REAL_JOB_ANALYSIS.md
- Implementation plan in WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md
- Timeline: 2-3 days
- Risk: Low

---

### Request #3
**Requested**: [Pending]
**Description**: 
**Action Required**: 
**Status**: 
**Notes**: 

---

## Additional Requests
(More requests will be added as received)

---

## Summary
- Total requests: 2
- Completed: 1 (Orchestration kept in core)
- In progress: 1 (Web module extraction - plan ready)
- Registered: 0
- Awaiting decision: 0

---

**Last Updated**: November 25, 2025
