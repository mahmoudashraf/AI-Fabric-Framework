# ProdOps AI Enablement Plan - Integrating with LoomAI Runtime

## The Correct Model

ProdOps is an **independent product** that integrates with a **LoomAI deployment** as its AI service provider. ProdOps owns its own infrastructure, database, frontend, backend, and business logic. It configures a LoomAI instance to use internally as an AI tool - the same way a product uses Stripe for payments or Algolia for search.

LoomAI is not running ProdOps. LoomAI is **selling AI runtime capabilities** to ProdOps as a customer.

```
┌──────────────────────────────────────────────────────────┐
│                  ProdOps Network                          │
│            (Independent Product / Company)                │
│                                                          │
│  Own hosting ─ Own database ─ Own UI ─ Own backend       │
│  Own business logic ─ Own marketplace ─ Own billing      │
│                                                          │
│  Needs AI capabilities for:                              │
│  • Product diagnosis                                     │
│  • Package governance                                    │
│  • Service discovery                                     │
│  • Team matching                                         │
│  • Deliverable assessment                                │
│  • Workspace intelligence                                │
│                                                          │
└────────────────────────┬─────────────────────────────────┘
                         │
                    Integrates with
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│              LoomAI Runtime Deployment                    │
│           (AI Service Provider to ProdOps)                │
│                                                          │
│  ProdOps configures its own LoomAI instance with:        │
│  • Productization-specific RAG data                      │
│  • Domain-specific Thinker session types                 │
│  • ProdOps policy rules for Resolver                     │
│  • Productization MCP tool registrations                 │
│  • Service catalog as Companion knowledge base           │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ Thinker  │ │ Resolver │ │Companion │ │   MCP    │   │
│  │   API    │ │   API    │ │   API    │ │  Engine  │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
│                      ┌──────────┐                        │
│                      │   RAG    │                        │
│                      │ Pipeline │                        │
│                      └──────────┘                        │
└──────────────────────────────────────────────────────────┘
```

---

## What LoomAI Sells to ProdOps

LoomAI is a product that ProdOps purchases and deploys. The commercial relationship:

| What LoomAI provides | What ProdOps gets |
|---|---|
| Thinker API | Evidence-backed diagnosis engine for any domain |
| Resolver API | Governed execution with policy/dry-run/confirm for any workflow |
| Companion API | RAG-powered intelligent search and grounded answers |
| MCP Engine | Register and execute domain-specific tools with governance |
| RAG Pipeline | Index domain data, retrieve semantically |
| Configuration interface | Set up session types, policies, tools, knowledge bases |

LoomAI earns recurring revenue from ProdOps through:
- Deployment license / subscription
- Usage-based pricing (per session, per query, per execution)
- Or a combination

ProdOps is one customer. Any other product can do the same integration.

---

## What ProdOps Owns (Everything Except AI Runtime)

ProdOps is a full product with its own stack:

```
ProdOps (independent)
├── Infrastructure: own hosting (any provider)
├── Database: own PostgreSQL / whatever they choose
├── Frontend: own React/Next.js app
│   ├── Owner portal
│   ├── Team portal
│   ├── Admin portal
│   └── Public marketing site
├── Backend: own API service
│   ├── User management / auth
│   ├── Service catalog logic
│   ├── Package builder logic
│   ├── Team network management
│   ├── Collaboration workspace
│   ├── Contracts / payments
│   ├── Reputation system
│   └── AI integration layer (calls LoomAI)
├── Business logic: marketplace rules, pricing, matching weights
├── Data: service taxonomy, templates, team profiles, projects
└── Billing: Stripe or similar for owner/team payments
```

The **AI integration layer** is the part of ProdOps backend that calls the LoomAI deployment. It's a client library / SDK integration.

---

## How ProdOps Configures Its LoomAI Deployment

ProdOps spins up (or subscribes to) a LoomAI instance and configures it for productization:

### 1. RAG Configuration

ProdOps indexes its domain knowledge into the LoomAI RAG pipeline:

```
ProdOps → LoomAI RAG API

Index: Service taxonomy
  - 8 categories, 40+ modules, descriptions, dependencies
  - Inputs/outputs/deliverables per service
  - Budget/timeline ranges

Index: Package templates
  - MVP Stabilization, SaaS Launch Readiness, Security Hardening...
  - Milestone structures, acceptance criteria, handoff checklists

Index: Case patterns (grows over time)
  - Anonymized completed packages
  - Common risk patterns by product type
  - Success/failure patterns

Index: Team knowledge (grows over time)
  - Team capability descriptions
  - Portfolio summaries
  - Outcome data
```

### 2. Thinker Configuration

ProdOps defines session types for its domain:

```
Session type: product-health-diagnosis
  - Question template: "What is the productization state of {product}?"
  - Expected evidence types: repo-scan, deployment-probe, dependency-audit
  - Plan output format: service recommendations with priority

Session type: deliverable-assessment
  - Question template: "Does {deliverable} meet {acceptance_criteria}?"
  - Expected evidence types: ci-status, test-coverage, security-scan, docs-check
  - Plan output format: pass/fail per criterion with evidence

Session type: team-capability-assessment
  - Question template: "What service categories is {team} qualified for?"
  - Expected evidence types: portfolio-analysis, repo-scan, outcome-history
  - Plan output format: eligible categories with confidence
```

### 3. Resolver Configuration

ProdOps defines policies and governed workflows:

```
Policy: package-creation
  - Rule: all dependency services must be present or explicitly declined
  - Rule: budget estimate must be within market range for scope
  - Rule: timeline must be feasible (min days per service layer)
  - Rule: no unsupported capability claims
  - Dry-run: show complete package preview before creation

Policy: team-assignment
  - Rule: team must be verified for assigned service categories
  - Rule: team must have available capacity
  - Rule: no conflict of interest
  - Rule: budget alignment within 20% of team's range
  - Dry-run: show match reasoning and alternatives

Policy: handoff-approval
  - Rule: all required checklist items must have evidence
  - Rule: Thinker assessment must show no critical gaps
  - Rule: both owner and team must confirm
  - Dry-run: show checklist completeness report
```

### 4. MCP Tool Registration

ProdOps registers its domain-specific tools in the LoomAI MCP engine:

```
Tool: prodops.repo.analyze
  - Input: { repo_url, access_token }
  - Output: { languages, frameworks, dependencies, ci_config, test_coverage }
  - Governance: read-only, requires owner consent

Tool: prodops.deploy.probe
  - Input: { url }
  - Output: { status, ssl, headers, response_time, uptime }
  - Governance: read-only, no auth needed

Tool: prodops.deps.audit
  - Input: { repo_url, package_manager }
  - Output: { vulnerabilities, outdated, risk_level }
  - Governance: read-only, requires owner consent

Tool: prodops.match.score
  - Input: { package_profile, team_profiles[], weights }
  - Output: { ranked_teams[], scores[], explanations[] }
  - Governance: internal only, no external access

Tool: prodops.package.estimate
  - Input: { services[], complexity_signals }
  - Output: { budget_range, timeline_range, confidence }
  - Governance: uses RAG case patterns
```

### 5. Companion Configuration

ProdOps configures the Companion search/answer engine with its data:

```
Companion instance for ProdOps:
  - Knowledge source: prodops service taxonomy (from RAG)
  - Knowledge source: prodops package templates (from RAG)
  - Knowledge source: workspace documents (per-workspace context)
  - Answer grounding: must cite service taxonomy or template source
  - Scope: only answer from indexed ProdOps knowledge, not general
```

---

## ProdOps Backend Integration Patterns

### Pattern: Product Diagnosis

```javascript
// ProdOps backend calls its LoomAI deployment

async function diagnoseProduct(product) {
  // 1. Create Thinker session
  const session = await loomaiClient.thinker.createSession({
    type: 'product-health-diagnosis',
    question: `What is the productization state of ${product.name}?`,
    metadata: { product_id: product.id }
  });

  // 2. Gather evidence using MCP tools
  if (product.repoUrl) {
    const repoAnalysis = await loomaiClient.mcp.execute('prodops.repo.analyze', {
      repo_url: product.repoUrl,
      access_token: product.githubToken
    });
    await loomaiClient.thinker.addEvidence(session.id, {
      type: 'repo-scan',
      source: 'prodops.repo.analyze',
      content: repoAnalysis
    });
  }

  if (product.deploymentUrl) {
    const deployProbe = await loomaiClient.mcp.execute('prodops.deploy.probe', {
      url: product.deploymentUrl
    });
    await loomaiClient.thinker.addEvidence(session.id, {
      type: 'deployment-probe',
      source: 'prodops.deploy.probe',
      content: deployProbe
    });
  }

  // 3. Generate diagnosis plan
  const diagnosis = await loomaiClient.thinker.generatePlan(session.id);

  // 4. Store result in ProdOps own database
  await db.productDiagnosis.save({
    product_id: product.id,
    thinker_session_id: session.id,
    services_recommended: diagnosis.plan.services,
    risk_level: diagnosis.plan.risk_level,
    evidence_summary: diagnosis.plan.evidence_summary
  });

  return diagnosis;
}
```

### Pattern: Service Discovery

```javascript
// Owner asks a question about what services they need

async function discoverServices(query, context) {
  const result = await loomaiClient.companion.query({
    query: query,
    grounding: ['service-taxonomy', 'dependency-rules', 'case-patterns'],
    context: context  // e.g., product tech stack, business stage
  });

  // ProdOps formats and enriches the response with its own data
  const services = result.citations.map(citation => ({
    ...citation,
    ...await db.services.findByTaxonomyId(citation.source_id),
    available_teams: await db.teams.countByCategory(citation.category)
  }));

  return { answer: result.answer, services };
}
```

### Pattern: Package Governance

```javascript
// Create a governed package through Resolver

async function createPackage(owner, packageDraft) {
  // 1. Submit proposal to Resolver
  const proposal = await loomaiClient.resolver.createProposal({
    policy: 'package-creation',
    thinker_session: packageDraft.diagnosis_session_id,
    action: 'create-package',
    payload: {
      services: packageDraft.services,
      milestones: packageDraft.milestones,
      budget_estimate: packageDraft.budget,
      timeline_estimate: packageDraft.timeline
    }
  });

  // 2. Run policy check
  const policyResult = await loomaiClient.resolver.checkPolicy(proposal.id);
  if (!policyResult.passed) {
    return { status: 'policy-failed', issues: policyResult.violations };
  }

  // 3. Dry-run for owner preview
  const preview = await loomaiClient.resolver.dryRun(proposal.id);

  // 4. Return preview to ProdOps UI - owner decides
  return { status: 'awaiting-confirmation', preview, proposal_id: proposal.id };
}

// After owner confirms in ProdOps UI:
async function confirmPackage(proposalId) {
  await loomaiClient.resolver.confirm(proposalId);
  const result = await loomaiClient.resolver.execute(proposalId);

  // ProdOps creates the package in its own database
  await db.packages.create({
    resolver_execution_id: result.execution_id,
    ...result.payload
  });
}
```

### Pattern: Team Matching

```javascript
// Score and explain team matches

async function matchTeams(packageProfile) {
  // 1. Score using MCP tool
  const teams = await db.teams.findEligible(packageProfile.categories);
  const scores = await loomaiClient.mcp.execute('prodops.match.score', {
    package_profile: packageProfile,
    team_profiles: teams.map(t => t.profile),
    weights: { category_fit: 0.3, stack_fit: 0.25, reputation: 0.2, availability: 0.15, budget: 0.1 }
  });

  // 2. Generate explanations using Companion
  const explanations = await Promise.all(
    scores.ranked_teams.slice(0, 5).map(team =>
      loomaiClient.companion.query({
        query: `Why is ${team.name} a good fit for this package?`,
        grounding: ['team-profiles', 'team-portfolios'],
        context: { package: packageProfile, team: team.profile }
      })
    )
  );

  return scores.ranked_teams.map((team, i) => ({
    ...team,
    explanation: explanations[i].answer
  }));
}
```

### Pattern: Deliverable Review

```javascript
// Automated milestone assessment

async function reviewDeliverable(milestone) {
  // 1. Create Thinker session for assessment
  const session = await loomaiClient.thinker.createSession({
    type: 'deliverable-assessment',
    question: `Does this deliverable meet the acceptance criteria?`,
    metadata: { milestone_id: milestone.id, criteria: milestone.acceptance_criteria }
  });

  // 2. Run automated checks via MCP
  for (const criterion of milestone.acceptance_criteria) {
    const tool = mapCriterionToTool(criterion);  // ProdOps logic
    if (tool) {
      const result = await loomaiClient.mcp.execute(tool.name, tool.params);
      await loomaiClient.thinker.addEvidence(session.id, {
        type: criterion.type,
        source: tool.name,
        content: result
      });
    }
  }

  // 3. Generate assessment plan
  const assessment = await loomaiClient.thinker.generatePlan(session.id);

  // 4. Store in ProdOps - owner still makes final accept/reject decision
  await db.milestoneReviews.save({
    milestone_id: milestone.id,
    thinker_session_id: session.id,
    criteria_results: assessment.plan.criteria_results,
    gaps: assessment.plan.gaps,
    recommendation: assessment.plan.recommendation
  });

  return assessment;
}
```

### Pattern: Workspace Assistant

```javascript
// Answer questions within a collaboration workspace

async function workspaceQuery(workspaceId, userQuery) {
  const workspace = await db.workspaces.findWithDocuments(workspaceId);

  // Index workspace-specific documents into RAG (if not already)
  await ensureWorkspaceIndexed(workspace);

  const result = await loomaiClient.companion.query({
    query: userQuery,
    grounding: ['workspace-documents', 'package-templates', 'service-taxonomy'],
    context: {
      workspace_id: workspaceId,
      package_brief: workspace.package_brief,
      milestones: workspace.milestones,
      current_status: workspace.status
    }
  });

  return result;
}
```

---

## What LoomAI Needs to Expose for This Model

For ProdOps (and any future customer) to integrate, LoomAI must provide:

### API Endpoints

```
Thinker API:
  POST   /api/thinker/sessions              Create session
  POST   /api/thinker/sessions/:id/evidence  Add evidence
  POST   /api/thinker/sessions/:id/plan      Generate plan
  GET    /api/thinker/sessions/:id           Get session with evidence
  GET    /api/thinker/sessions/:id/export    Export full audit trail

Resolver API:
  POST   /api/resolver/proposals             Create proposal
  POST   /api/resolver/proposals/:id/policy  Run policy check
  POST   /api/resolver/proposals/:id/dry-run Preview execution
  POST   /api/resolver/proposals/:id/confirm Confirm execution
  POST   /api/resolver/proposals/:id/execute Execute with audit
  GET    /api/resolver/proposals/:id         Get proposal state

Companion API:
  POST   /api/companion/query               Ask question with RAG grounding
  POST   /api/companion/index               Index documents
  DELETE /api/companion/index/:doc_id        Remove document

MCP API:
  POST   /api/mcp/tools                     Register tool
  POST   /api/mcp/execute                   Execute tool (governed)
  GET    /api/mcp/tools                     List registered tools

RAG API:
  POST   /api/rag/index                     Index document
  POST   /api/rag/search                    Semantic search
  DELETE /api/rag/documents/:id             Remove document
  GET    /api/rag/namespaces                List namespaces

Configuration API:
  POST   /api/config/thinker/session-types  Define session types
  POST   /api/config/resolver/policies      Define policies
  POST   /api/config/companion/sources      Define knowledge sources
```

### SDK / Client Library

```
npm install @loomai/sdk

import { LoomAI } from '@loomai/sdk';

const loomaiClient = new LoomAI({
  endpoint: 'https://my-prodops-instance.loomai.pro',
  apiKey: 'prodops-api-key',
  project: 'prodops-network'
});
```

### Configuration Dashboard

A web interface where ProdOps admins can:
- Define Thinker session types
- Define Resolver policies
- Register MCP tools
- Manage RAG namespaces and documents
- Monitor usage and costs
- View audit trails

---

## LoomAI Revenue from ProdOps

| Revenue Stream | Model |
|---|---|
| Deployment license | Monthly subscription for a LoomAI instance |
| Thinker sessions | Per session or monthly allocation (e.g., 1000 sessions/month) |
| Resolver executions | Per execution or included in tier |
| Companion queries | Per query or monthly allocation |
| MCP tool invocations | Per invocation |
| RAG storage + retrieval | Per GB indexed + per query |
| Support / SLA | Tier-based (standard, priority, dedicated) |

Example pricing tiers for LoomAI as a service:

```
Starter:     $299/month  - 500 Thinker sessions, 2000 Companion queries,
                           500 Resolver executions, 5GB RAG, standard support

Growth:      $999/month  - 5000 sessions, 20K queries, 5000 executions,
                           50GB RAG, priority support

Enterprise:  Custom      - Unlimited, dedicated instance, SLA,
                           custom integrations, dedicated support
```

---

## What This Means for LoomAI's Business

LoomAI is not running ProdOps. LoomAI is **selling AI runtime** to ProdOps.

The same deployment model works for any customer:
- ProdOps uses LoomAI for productization AI
- A legal tech startup uses LoomAI for case diagnosis + document governance
- A healthcare company uses LoomAI for patient triage + treatment protocols
- An education platform uses LoomAI for learning path recommendation + assessment
- An HR tool uses LoomAI for candidate evaluation + hiring governance

Each customer:
1. Deploys (or subscribes to) a LoomAI instance
2. Configures it with their domain data (RAG), session types (Thinker), policies (Resolver), and tools (MCP)
3. Integrates via SDK from their own product
4. Pays LoomAI subscription + usage fees

**LoomAI's job:** Make the runtime so capable, well-documented, and easy to integrate that products choose LoomAI over building their own AI layer.

**ProdOps's job:** Build the best productization marketplace. AI capabilities come from LoomAI, not from ProdOps engineering AI infrastructure.

---

## ProdOps Build Timeline (With LoomAI Integration)

ProdOps builds its product independently. The AI integration is just one layer of its backend:

| Phase | Weeks | ProdOps Builds | LoomAI Integration |
|---|---|---|---|
| **0: Foundation** | 3-4 | Auth, database, basic owner/team portals | Deploy LoomAI instance, configure SDK |
| **1: Service Catalog** | 3-4 | Catalog UI, service taxonomy, dependency rules | Index taxonomy into RAG, configure Companion |
| **2: Intake + Diagnosis** | 4-5 | Intake wizard, product profile, repo connection | Thinker sessions, MCP tool registrations |
| **3: Package Builder** | 4-5 | Package UI, milestone editor, SOW generator | Resolver policies, Companion for estimation |
| **4: Team Network** | 4-5 | Team profiles, verification, matching UI | Thinker for assessment, MCP for scoring |
| **5: Workspace** | 5-6 | Collaboration UI, milestones, documents, messages | Companion workspace Q&A, Thinker for reviews |
| **6: Commerce** | 4-5 | Quotes, contracts, invoices, payments | Resolver for governed financial actions |

**Total ProdOps build: ~27-34 weeks** (comparable to any marketplace build)

**LoomAI integration effort within that: ~20% of backend work** (calling APIs, configuring policies/tools, indexing data). Not 0% (it's still integration work), but dramatically less than building AI from scratch.

---

## Summary

| | ProdOps Owns | LoomAI Provides |
|---|---|---|
| **Infrastructure** | Own servers, own choice | Deployed instance (self-hosted or managed) |
| **Database** | Own PostgreSQL, own schema | RAG vector store for indexed domain knowledge |
| **UI** | Full owner/team/admin portals | Configuration dashboard for AI setup |
| **Business logic** | Marketplace rules, pricing, workflows | None - LoomAI is domain-agnostic |
| **AI capabilities** | Calls LoomAI APIs from backend | Thinker, Resolver, Companion, MCP, RAG |
| **Domain data** | Service taxonomy, templates, team data | Stores what ProdOps indexes, returns on query |
| **Revenue** | Marketplace fees from owners/teams | Subscription + usage fees from ProdOps |

ProdOps is a customer. LoomAI is a vendor. Clear boundary.
