# ProdOps AI Enablement Plan - Using LoomAI Deployments

## The Model

ProdOps is not a new AI build. It is a new application deployed on the **existing LoomAI Product Platform** - the same managed runtime that already runs Companion for Shopify commerce.

The LoomAI platform is already deployed on Coolify/Hetzner with:
- Thinker service (diagnosis sessions, evidence, plans, audit)
- Resolver service (proposals, policy, dry-run, confirmation, execution)
- Companion service (RAG-powered intelligent search and answers)
- MCP execution engine (tool discovery, invocation, governance)
- RAG pipeline (vector indexing, semantic retrieval, grounding)
- Action registry (registered actions with schema, policy, execution)
- Marketplace plugin system (capability packaging)
- Deployment profiles (managed provisioning per product)
- Partner/merchant approval workflows
- Verification gates

ProdOps **consumes these as deployed services**. It does not rebuild them. It configures them with productization-specific data, plugins, and policies.

```
┌─────────────────────────────────────────────────────────┐
│                  LoomAI Product Platform                  │
│              (Coolify / Hetzner / Managed)                │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ Companion│  │ Thinker  │  │ Resolver │              │
│  │ Service  │  │ Service  │  │ Service  │              │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘              │
│       │              │              │                    │
│  ┌────┴──────────────┴──────────────┴────┐              │
│  │         MCP Execution Engine          │              │
│  │    (Actions / Tools / Governance)     │              │
│  └────────────────┬──────────────────────┘              │
│                   │                                      │
│  ┌────────────────┴──────────────────────┐              │
│  │         RAG Pipeline + Vector Store    │              │
│  └────────────────┬──────────────────────┘              │
│                   │                                      │
│  ┌────────────────┴──────────────────────┐              │
│  │    Marketplace Plugins / Profiles      │              │
│  └───────────────────────────────────────┘              │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  APPS RUNNING ON THE PLATFORM:                           │
│                                                          │
│  ┌────────────┐  ┌────────────┐  ┌────────────────┐    │
│  │  Loom      │  │  ProdOps   │  │  Future Apps   │    │
│  │  Companion │  │  Network   │  │  (220 products)│    │
│  │  (Shopify) │  │  (Product- │  │                │    │
│  │            │  │   ization) │  │                │    │
│  └────────────┘  └────────────┘  └────────────────┘    │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## What ProdOps Deploys vs. What It Consumes

| Need | Consumes from LoomAI Platform | ProdOps builds only |
|---|---|---|
| Product diagnosis | Thinker deployment (create sessions, add evidence, generate plans) | Productization-specific question templates |
| Package governance | Resolver deployment (proposals, policy check, dry-run, confirm) | Package policy rules and templates |
| Service discovery | Companion deployment (RAG search, grounded answers) | Service taxonomy data + dependency rules |
| Repo/deployment scanning | MCP execution engine (invoke registered tools) | GitHub/deployment tool registrations |
| Knowledge base | RAG pipeline (index documents, semantic search) | Productization corpus (templates, cases, checklists) |
| Team capabilities | Marketplace plugin system (capability registration) | Team capability schema and verification plugins |
| Team/owner access | Partner/merchant approval workflows | ProdOps-specific roles (owner, team, expert) |
| Workspace hosting | Deployment profiles (managed provisioning) | Workspace UI and collaboration logic |
| Quality checks | Verification gates | Productization-specific gate criteria |

**ProdOps builds the UI, the data, and the domain rules. LoomAI provides all AI execution.**

---

## Thinker Deployment for ProdOps

### How ProdOps uses the deployed Thinker service

Thinker already exposes:
- Create session
- Add evidence (with source, type, content)
- Generate plan (from evidence)
- Export session (audit trail)
- Redacted partner views

ProdOps calls these APIs directly:

### Use Case 1: Product Health Diagnosis

```
POST /thinker/sessions
{
  "context": "prodops-product-intake",
  "question": "What is the productization state of this product?",
  "product_id": "owner-product-123"
}

POST /thinker/sessions/{id}/evidence
{
  "type": "repo-scan",
  "source": "mcp:github.repo.analyze",
  "content": { ... scan results ... }
}

POST /thinker/sessions/{id}/evidence
{
  "type": "deployment-probe",
  "source": "mcp:deployment.health",
  "content": { ... probe results ... }
}

POST /thinker/sessions/{id}/plan
→ Returns: structured diagnosis with service recommendations
```

**Result:** Owner sees a full evidence-backed health report. Each finding links to real evidence (repo scan, deployment probe, dependency audit). The plan recommends specific productization services.

### Use Case 2: Deliverable Assessment

```
POST /thinker/sessions
{
  "context": "prodops-milestone-review",
  "question": "Does this deliverable meet the acceptance criteria?",
  "milestone_id": "milestone-456",
  "acceptance_criteria": ["CI passing", "Tests > 80%", "No critical vulns", "Docs present"]
}

POST /thinker/sessions/{id}/evidence
[... automated MCP checks for each criterion ...]

POST /thinker/sessions/{id}/plan
→ Returns: completeness assessment with pass/fail per criterion
```

### Use Case 3: Team Capability Assessment

```
POST /thinker/sessions
{
  "context": "prodops-team-verification",
  "question": "What service categories is this team qualified for?",
  "team_id": "team-789"
}

POST /thinker/sessions/{id}/evidence
{
  "type": "portfolio-analysis",
  "source": "mcp:github.repos.scan",
  "content": { ... repos analysis ... }
}

POST /thinker/sessions/{id}/plan
→ Returns: recommended service categories with evidence
```

---

## Resolver Deployment for ProdOps

### How ProdOps uses the deployed Resolver service

Resolver already exposes:
- Create proposal (connected to Thinker evidence)
- Policy check (evaluate against rules)
- Dry-run (preview expected outcomes)
- Confirmation (exact confirmation with idempotency)
- Execute (audited action)
- Execution evidence (proof of what happened)

ProdOps uses this for every governed decision:

### Use Case 1: Package Creation

```
POST /resolver/proposals
{
  "context": "prodops-package-creation",
  "thinker_session": "session-123",  ← links to product diagnosis
  "action": "create-package",
  "payload": {
    "services": ["cloud-deployment", "security-hardening", "ci-cd"],
    "dependencies_added": ["monitoring", "backup-restore"],
    "milestones": [...],
    "budget_estimate": { "min": 8000, "max": 15000 },
    "timeline_estimate": "6-8 weeks"
  }
}

POST /resolver/proposals/{id}/policy
→ Checks: all dependency rules satisfied, budget range realistic,
          timeline feasible for scope, no unsupported claims

POST /resolver/proposals/{id}/dry-run
→ Preview: "This package will have 5 service layers, 8 milestones,
           estimated $8K-15K, 6-8 weeks. Missing: support handoff layer."

POST /resolver/proposals/{id}/confirm
→ Owner confirms the package

POST /resolver/proposals/{id}/execute
→ Package created, workspace provisioned, team matching triggered
```

### Use Case 2: Team Assignment

```
POST /resolver/proposals
{
  "context": "prodops-team-assignment",
  "action": "assign-team",
  "payload": {
    "package_id": "pkg-456",
    "team_id": "team-789",
    "layers": ["cloud-deployment", "ci-cd"],
    "match_evidence": { "score": 0.92, "reasons": [...] }
  }
}

POST /resolver/proposals/{id}/policy
→ Checks: team verified for these categories, capacity available,
          no conflict of interest, budget alignment

POST /resolver/proposals/{id}/dry-run
→ Preview: "Team X will own cloud-deployment and ci-cd layers.
           They have 92% match score. 3 similar projects completed."

POST /resolver/proposals/{id}/confirm
→ Owner confirms team selection

POST /resolver/proposals/{id}/execute
→ Team assigned, workspace access granted, notification sent
```

### Use Case 3: Handoff Gate

```
POST /resolver/proposals
{
  "context": "prodops-handoff",
  "thinker_session": "deliverable-assessment-session",
  "action": "approve-handoff",
  "payload": {
    "package_id": "pkg-456",
    "checklist": {
      "deployment_docs": true,
      "monitoring_configured": true,
      "backup_verified": true,
      "known_issues_listed": true,
      "support_sla_defined": true
    }
  }
}

POST /resolver/proposals/{id}/policy
→ Checks: all required checklist items present,
          Thinker evidence confirms each item

POST /resolver/proposals/{id}/execute
→ Handoff approved, workspace transitions to support mode
```

---

## Companion Deployment for ProdOps

### How ProdOps uses the deployed Companion service

Companion already exposes:
- Natural language search over indexed data
- Grounded answers (RAG-backed, cites sources)
- Context-aware responses
- FAQ/comparison/policy answers

ProdOps configures Companion with productization data:

### Use Case 1: Service Catalog Discovery

**Owner asks:** "My Next.js app needs to handle enterprise customers"

```
POST /companion/query
{
  "context": "prodops-service-catalog",
  "store_id": "prodops",  ← ProdOps is the "store" in Companion terms
  "query": "My Next.js app needs to handle enterprise customers",
  "grounding_sources": ["service-taxonomy", "dependency-rules", "case-patterns"]
}

→ Response:
{
  "answer": "Enterprise readiness typically requires 4 service layers...",
  "services_identified": [
    { "name": "SaaS Launch Readiness", "confidence": 0.95, "source": "service-taxonomy" },
    { "name": "Security Hardening", "confidence": 0.91, "source": "dependency-rule: enterprise→security" },
    { "name": "Database Scaling", "confidence": 0.82, "source": "case-pattern: next.js-enterprise" }
  ],
  "citations": [...]
}
```

### Use Case 2: Workspace Assistant

**Team member asks in workspace:** "What are the acceptance criteria for the deployment milestone?"

```
POST /companion/query
{
  "context": "prodops-workspace",
  "workspace_id": "ws-456",
  "query": "What are the acceptance criteria for the deployment milestone?",
  "grounding_sources": ["package-brief", "milestone-definition", "handoff-template"]
}

→ Response grounded in the actual package documents
```

### Use Case 3: Team Discovery

**Owner asks:** "Who has experience with fintech security on Node.js?"

```
POST /companion/query
{
  "context": "prodops-team-search",
  "query": "fintech security experience with Node.js",
  "grounding_sources": ["team-profiles", "team-portfolios", "team-outcomes"]
}

→ Response with specific teams, grounded in their actual portfolio data
```

---

## MCP Execution for ProdOps

### How ProdOps registers productization-specific MCP tools

The MCP execution engine is already deployed. ProdOps registers new tools specific to productization assessment:

### Tool Registry (ProdOps Marketplace Plugins)

```
Plugin: prodops-github-assessment
Tools:
  - prodops.repo.languages      → Detect tech stack from repo
  - prodops.repo.dependencies   → List and audit dependencies
  - prodops.repo.ci_status      → Check CI/CD configuration and status
  - prodops.repo.test_coverage  → Extract test coverage metrics
  - prodops.repo.security_alerts → Get open security advisories
  - prodops.repo.docs_presence  → Check for README, API docs, deploy docs

Plugin: prodops-deployment-assessment
Tools:
  - prodops.deploy.health_probe → HTTP health check + SSL + headers
  - prodops.deploy.uptime_check → Basic availability verification
  - prodops.deploy.performance  → Response time and basic load metrics

Plugin: prodops-package-intelligence
Tools:
  - prodops.package.dependency_check → Run dependency rule engine
  - prodops.package.estimate         → Generate budget/timeline estimate
  - prodops.package.similar_cases    → Find similar completed packages

Plugin: prodops-team-matching
Tools:
  - prodops.match.score         → Multi-dimensional team scoring
  - prodops.match.explain       → Generate match explanation
  - prodops.match.availability  → Check team capacity
```

### MCP Governance (Already Built)

Every tool invocation goes through the platform's existing governance:
- Tool must be registered in the marketplace
- Action must have defined schema (inputs/outputs)
- Execution follows the policy → dry-run → confirm → execute model
- All invocations are audited
- Owner must grant access (connect repo, provide URL)

---

## RAG Pipeline for ProdOps

### How ProdOps uses the deployed RAG infrastructure

The RAG pipeline is already deployed with vector indexing and semantic retrieval. ProdOps creates a new **index namespace** with productization-specific documents:

### ProdOps RAG Namespaces

```
Namespace: prodops-service-taxonomy
Documents:
  - 8 service categories with full descriptions
  - 40+ service modules with inputs/outputs/deliverables
  - Dependency rules (structured: "if X then recommend Y because Z")
  - Acceptance criteria templates per service type
  - Timeline/budget ranges per service type

Namespace: prodops-package-templates
Documents:
  - MVP Stabilization template (milestones, deliverables, criteria)
  - SaaS Launch Readiness template
  - Scale Readiness template
  - Security Hardening template
  - No-Code Migration template
  - Custom Package structure

Namespace: prodops-case-patterns
Documents:
  - Anonymized completed packages (stack, services, timeline, outcome)
  - Common risk patterns by product type
  - Failure patterns (what went wrong and why)
  - Success patterns (what worked and why)

Namespace: prodops-team-profiles
Documents:
  - Team capabilities (indexed for semantic search)
  - Portfolio items (tech stacks, domains, project types)
  - Outcome data (completion rates, satisfaction, specializations)

Namespace: prodops-checklists
Documents:
  - Security launch checklist
  - Deployment readiness checklist
  - Handoff documentation checklist
  - Support transition checklist
  - Per-stack productization guides
```

### Indexing happens through the existing RAG pipeline:

```
POST /rag/index
{
  "namespace": "prodops-service-taxonomy",
  "document_id": "cloud-deployment-module",
  "content": "Cloud deployment service module. Includes production environment setup...",
  "metadata": {
    "category": "cloud-devops",
    "dependencies": ["ci-cd", "monitoring", "backup-restore"],
    "typical_timeline": "2-4 weeks",
    "typical_budget": "$3000-$8000"
  }
}
```

---

## Deployment Profile for ProdOps

### How ProdOps is provisioned on the platform

The LoomAI platform already manages deployment profiles for products. ProdOps gets its own profile:

```
Deployment Profile: prodops-network
├── App: prodops-web (Next.js frontend)
├── App: prodops-api (backend service)
├── Companion config: prodops service catalog + workspace grounding
├── Thinker config: prodops session types (intake, review, verification)
├── Resolver config: prodops policies (package, assignment, handoff)
├── MCP plugins: prodops-github, prodops-deployment, prodops-package, prodops-match
├── RAG namespaces: prodops-service-taxonomy, prodops-templates, prodops-cases, prodops-teams
├── Roles: owner, team-manager, specialist, expert, admin
├── Approval workflows: team verification, package approval, handoff gate
└── Verification gates: team eligibility, milestone acceptance, handoff readiness
```

The platform provisions all of this through the same Coolify deployment pipeline that provisions Companion for Shopify stores.

---

## What ProdOps Actually Builds (New Code)

ProdOps builds ONLY the domain-specific layer:

### ProdOps Application Code

```
prodops-web/          (Frontend - new build)
├── pages/
│   ├── owner/        Owner portal
│   │   ├── intake    Product intake wizard (calls Thinker)
│   │   ├── packages  Package view (calls Resolver)
│   │   ├── teams     Team discovery (calls Companion)
│   │   └── workspace Collaboration (calls Companion + MCP)
│   ├── team/         Team portal
│   │   ├── profile   Capability management
│   │   ├── matches   Opportunity feed (calls matching tools)
│   │   └── workspace Delivery workspace
│   └── admin/        Platform admin
│       ├── catalog   Service taxonomy editor → indexes to RAG
│       ├── teams     Verification workflow → calls Thinker
│       └── packages  Package template editor → indexes to RAG

prodops-api/          (Backend - new build)
├── routes/
│   ├── intake/       Calls Thinker API for product diagnosis
│   ├── packages/     Calls Resolver API for package governance
│   ├── discovery/    Calls Companion API for search
│   ├── matching/     Calls MCP tools for team scoring
│   ├── workspace/    Calls Companion for workspace Q&A
│   ├── review/       Calls Thinker + MCP for deliverable checks
│   └── handoff/      Calls Resolver for handoff gate
├── data/
│   ├── service-taxonomy.json       → Indexed into RAG
│   ├── dependency-rules.json       → Used by package policy
│   ├── package-templates/          → Indexed into RAG
│   └── checklists/                 → Indexed into RAG
└── plugins/
    ├── prodops-github-assessment/  → Registered as MCP tools
    ├── prodops-deployment-probe/   → Registered as MCP tools
    └── prodops-matching-engine/    → Registered as MCP tools
```

**Total new code: ~30-40% of a typical app.** The AI layer is already deployed and running.

---

## End-to-End Flow (Deployments Used)

### Owner submits product for diagnosis

```
1. Owner → ProdOps Web (new UI)
2. ProdOps API → POST /thinker/sessions (DEPLOYED Thinker)
3. ProdOps API → POST /mcp/execute (DEPLOYED MCP engine)
   → Tool: prodops.repo.languages (via GitHub API)
   → Tool: prodops.repo.dependencies
   → Tool: prodops.deploy.health_probe
4. ProdOps API → POST /thinker/sessions/{id}/evidence × N (DEPLOYED Thinker)
5. ProdOps API → POST /thinker/sessions/{id}/plan (DEPLOYED Thinker)
6. Owner sees evidence-backed health report
```

### Owner searches for services

```
1. Owner types: "need to make my app secure for enterprise"
2. ProdOps API → POST /companion/query (DEPLOYED Companion)
   → RAG retrieval from prodops-service-taxonomy namespace
   → Grounded answer with specific services + dependencies
3. Owner sees recommended services with citations
```

### Package gets created

```
1. ProdOps API → POST /resolver/proposals (DEPLOYED Resolver)
   → Links to Thinker diagnosis session as evidence
   → Includes recommended services, milestones, estimates
2. ProdOps API → POST /resolver/proposals/{id}/policy (DEPLOYED Resolver)
   → ProdOps dependency rules evaluated
3. ProdOps API → POST /resolver/proposals/{id}/dry-run (DEPLOYED Resolver)
   → Preview shown to owner
4. Owner confirms → POST /resolver/proposals/{id}/confirm
5. Package created → POST /resolver/proposals/{id}/execute
```

### Team gets matched

```
1. ProdOps API → POST /mcp/execute (DEPLOYED MCP engine)
   → Tool: prodops.match.score (scores all eligible teams)
2. ProdOps API → POST /companion/query (DEPLOYED Companion)
   → RAG retrieval from prodops-team-profiles namespace
   → "Why is Team X a good fit?" with portfolio evidence
3. ProdOps API → POST /resolver/proposals (DEPLOYED Resolver)
   → Team assignment with policy check and confirmation
```

### Milestone gets reviewed

```
1. Team submits milestone
2. ProdOps API → POST /thinker/sessions (DEPLOYED Thinker)
3. ProdOps API → POST /mcp/execute (DEPLOYED MCP)
   → Tool: prodops.repo.ci_status
   → Tool: prodops.repo.test_coverage
   → Tool: prodops.repo.security_alerts
   → Tool: prodops.deploy.health_probe
4. Each result → POST /thinker/sessions/{id}/evidence (DEPLOYED Thinker)
5. ProdOps API → POST /thinker/sessions/{id}/plan (DEPLOYED Thinker)
   → "4/5 acceptance criteria met. Missing: documentation."
6. Owner reviews evidence report and decides
```

---

## Cost Model

ProdOps pays LoomAI platform fees - just like merchants pay for Companion:

| Platform Service Used | ProdOps Pays |
|---|---|
| Thinker sessions (product diagnosis, reviews) | Per session or monthly allocation |
| Resolver executions (packages, assignments, handoffs) | Per execution |
| Companion queries (service discovery, workspace Q&A) | Per query or monthly allocation |
| MCP tool invocations (repo scans, deployment probes) | Per invocation |
| RAG indexing and retrieval | Per document indexed + per query |
| Deployment hosting | Monthly infrastructure |

This means **LoomAI earns platform revenue from ProdOps** in addition to ProdOps earning marketplace revenue from owners/teams. Two revenue layers on one infrastructure.

---

## Build Timeline (Using Existing Deployments)

Because the AI layer already exists, ProdOps builds much faster:

| Phase | Weeks | What ProdOps builds | What it consumes (already deployed) |
|---|---|---|---|
| **0: Data** | 2-3 | Service taxonomy, dependency rules, package templates | RAG indexing |
| **1: Intake** | 3-4 | Owner portal + intake wizard UI | Thinker sessions, MCP tools, Companion search |
| **2: Packages** | 3-4 | Package builder UI + policy rules | Resolver governance, RAG retrieval |
| **3: Teams** | 4-5 | Team portal + matching UI + verification | MCP matching tools, Thinker assessment, Companion search |
| **4: Workspace** | 4-5 | Collaboration UI + milestone tracking | Companion workspace Q&A, Thinker reviews, MCP checks |
| **5: Handoff** | 2-3 | Handoff UI + support transition | Resolver handoff gate, Thinker evidence |

**Total: ~18-24 weeks** instead of 42+ weeks. Because you're not building AI infrastructure - you're configuring existing deployments.

---

## What This Proves

ProdOps running on LoomAI deployments proves:

1. **The platform is real** - it's not just for commerce, it works for any domain
2. **The "220 products" vision works** - each product is a new app on the same platform
3. **Revenue compounds** - LoomAI earns from Companion merchants AND from ProdOps usage AND ProdOps earns from owners/teams
4. **Marginal cost of new products drops** - the next product after ProdOps is even faster (15-18 weeks)
5. **The AI Fabric Framework thesis is validated** - one AI platform, many domain applications

---

## Summary

ProdOps does NOT build:
- ❌ Its own RAG pipeline
- ❌ Its own diagnosis engine
- ❌ Its own governance model
- ❌ Its own MCP execution
- ❌ Its own tool framework
- ❌ Its own deployment infrastructure

ProdOps DOES build:
- ✅ Service taxonomy and dependency rules (data)
- ✅ Package templates and checklists (data)
- ✅ Owner portal and team portal (UI)
- ✅ Collaboration workspace (UI)
- ✅ Productization-specific MCP tool implementations (plugins)
- ✅ Domain-specific policy rules for Resolver (config)
- ✅ Team matching scoring logic (plugin)

Everything else is already deployed and running.
