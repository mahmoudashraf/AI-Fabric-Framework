# Relationship Query Module Alignment Review

_Last updated: 2025-11-21_

## Scope
- Reviewed internal planning set inside this folder:
  - `ARCHITECTURAL_DECISIONS.md`
  - `COMPREHENSIVE_IMPLEMENTATION_PLAN.md`
  - `IMPLEMENTATION_CHECKLIST.md`
  - `MODULE_ARCHITECTURE_GUIDE.md`
  - `EXECUTIVE_SUMMARY_IMPLEMENTATION.md`
- Cross-checked against the current repository state (`ai-infrastructure-module` Maven reactor and `ai-infrastructure-core` services).

## Documented Solution (Nov 2024)
- The plan assumes a brand-new optional module, `ai-infrastructure-relationship-query`, containing planners, builders, traversal services, validators, caches, auto-configuration, and orchestration logic, all stacked on top of `ai-infrastructure-core`.
- LLM-centric behaviors dominate the flow: an LLM planner produces a `RelationshipQueryPlan`, a deterministic builder translates that into JPQL, then relational and semantic phases are combined with extensive fallbacks, caching, and monitoring.
- Reliability guards (validation, caching, fallback to metadata/vector search, performance tracking) are implemented inside the new module itself.

```
24:47:ai-infrastructure-module/docs/semantic-relational-implementation/internal-module/COMPREHENSIVE_IMPLEMENTATION_PLAN.md
### **Module Structure:**
ai-infrastructure-module/
├── ai-infrastructure-core/ (foundational)
└── ai-infrastructure-relationship-query/ (NEW - optional)
    ├── RelationshipQueryPlanner.java
    ├── DynamicJPAQueryBuilder.java
    ├── LLMDrivenJPAQueryService.java
    ├── RelationshipTraversalService.java
    ├── JPARelationshipTraversalService.java
    ├── EntityRelationshipMapper.java
    ├── RelationshipSchemaProvider.java
```

## Current Codebase Snapshot (Nov 2025)
- The Maven reactor (`ai-infrastructure-module/pom.xml`) has no `ai-infrastructure-relationship-query` entry; only core, behavior, provider, and vector modules build today.

```
16:33:ai-infrastructure-module/pom.xml
    <modules>
        <module>ai-infrastructure-core</module>
        <module>ai-infrastructure-behavior</module>
        ...
        <module>integration-tests</module>
    </modules>
```

- Recent architectural changes favor **minimal infrastructure services + hook delegation**. Core services now validate input, call user-supplied policy hooks, log to the audit trail, and stop; they no longer embed customer logic (example: `AIAccessControlService`).

```
19:63:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/access/AIAccessControlService.java
/**
 * Minimal infrastructure access control service: validate request, delegate to customer hook,
 * record audit trail, and fail closed when hooks are unavailable.
 */
public class AIAccessControlService {
    ...
    boolean granted = policy.canUserAccessEntity(userId, Collections.unmodifiableMap(entityContext));
    auditService.logOperation(...);
}
```

- Other infrastructure services follow the same hook-first pattern (`UserDataDeletionService`, retention and compliance services), emphasizing orchestration + auditing instead of deep business logic.

```
27:92:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/deletion/UserDataDeletionService.java
/**
 * Infrastructure service orchestrating ... delegating domain logic to UserDataDeletionProvider hooks
 * while handling infrastructure owned data stores.
 */
public class UserDataDeletionService {
    public UserDataDeletionResult deleteUser(String userId) {
        UserDataDeletionProvider provider = requireProvider();
        ...
        int domainRecordsDeleted = safelyDeleteDomainData(provider, userId);
    }
}
```

## Alignment Assessment

| Area | Document Assumption | Current Reality | Status | Notes |
| --- | --- | --- | --- | --- |
| Module packaging | New `ai-infrastructure-relationship-query` optional module with its own auto-config | Module not present in the Maven reactor; no code paths reference it | ❌ Not aligned | Introduce module or retire plan. |
| Service responsibilities | Relationship module owns planners, builders, validators, caches, fallbacks | Core now enforces “framework ≈ hook delegate + audit”; complex decision logic belongs to customer hooks | ❌ Not aligned | Planned logic would violate minimal-framework rule seen in current services. |
| LLM dependency | Module mandates LLM planning + semantic ranking even for relational questions | Core allows customers to opt into LLM features; infrastructure avoids hard LLM coupling outside `AICoreService` wrappers | ⚠️ Partially aligned | Need explicit opt-in hooks/config + deterministic fallbacks if still desired. |
| Reliability features | Query validation, caching, monitoring live inside the module | Present stack centralizes observability/compliance inside `ai-infrastructure-core`; new module would duplicate this logic | ⚠️ Partially aligned | Would need to reuse shared audit/metrics hooks instead of duplicating them. |
| Adoption path | Users add dependency, enable `ai.infrastructure.relationship.*` props | No properties or starters exist today; enabling flag has no effect | ❌ Not aligned | Requires new starter, documentation, tests before enabling. |

## Recommendations
1. **Decide the fate of the module**: either formally drop it (and archive these docs) or schedule implementation work that brings a new module into the reactor with tests, CI, and hook-first APIs.
2. **If pursuing implementation**, refactor the plan to match today’s architectural guardrails:
   - Keep the framework minimal: expose a `RelationshipQueryPolicy` hook (mirroring `EntityAccessPolicy`) and push any domain-specific traversal/filtering into customer code.
   - Reuse shared infrastructure (audit logging, caching, retention services) instead of embedding parallel logic in a standalone module.
   - Gate all LLM usage behind existing `AICoreService` abstractions and allow a “no-LLM” path that still fulfills relational queries deterministically.
3. **Update documentation**: whichever decision we make, replace these 2024 documents with an explicit alignment statement so new contributors do not assume the module exists.
4. **Keep hooks and compliance in sync**: if a relationship-aware feature ships, ensure policy hooks (access, retention, deletion) can evaluate relationship query intent before execution so the rest of the platform retains its compliance posture.

Until those actions are taken, this planning set does **not** reflect the code that currently ships.

