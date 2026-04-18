# New Deployment Type Concepts: Evaluation

Status: evaluation document (2026-04-15)

---

## 1) What This Document Is

This document evaluates three proposed new deployment type concepts for the Loom AI platform:

1. **Deep Resolver** — read-first-then-act agentic pattern
2. **Thinker** — multi-source knowledge synthesis with accurate attribution
3. **Smart Brain** — always-running background agent for batch analysis

For each concept, it assesses: validity, overlap with existing platform capabilities, market value, architectural distinctness, and recommended path forward. This is an evaluation, not a final design.

---

## 2) Concept 1: Deep Resolver

### 2.1 What It Is

A deployment type where the AI does not answer immediately or execute immediately. Instead, it investigates before acting:

1. User expresses intent in natural language
2. The LLM classifies the intent and identifies what information is needed to resolve it
3. The LLM calls one or more read-only actions to gather context (order status, inventory, policy, user history, account state)
4. The LLM receives results and decides the next step:
   - Answer the user directly with the gathered context
   - Execute a write action to resolve the underlying issue (with governance flow)
   - Ask the user a clarifying question before proceeding
5. Governance applies to any write action before execution

This is the ReAct pattern (Reason + Act) formalized as a first-class deployment type. The operator picks "Deep Resolver" and gets the investigate-then-act loop by default, without having to build it through prompt engineering.

### 2.2 Overlap with Existing Platform

The current platform has most of the parts:

- Action framework with read-only and write metadata
- Confirmation interception for write operations
- Orchestration layer that can invoke actions
- Evidence tracking and source attribution

What is missing is the **multi-step tool use loop**: the ability for the LLM to call a read action, receive the result as part of its reasoning context, then make another call based on what it learned, and finally commit to an answer or a write action. Today's orchestration is largely single-turn: classify intent → answer or execute.

Deep Resolver requires:
- Iterative tool-use loop with budget controls (max steps, max cost)
- Intermediate reasoning state that carries across tool calls
- A decision point at each step: keep investigating, answer, execute, or clarify
- Telemetry for the full investigation chain

### 2.3 Market Value

The agentic resolution category is the hottest segment in conversational AI:

- Sierra AI built its valuation on "autonomous resolution rate"
- Decagon positions as "the AI agent that resolves customer issues"
- Ada's latest product direction is agentic resolution
- Every competitor publishes a percentage metric for autonomous resolution

Deep Resolver is the directly competitive positioning for this category. It is not a niche feature.

### 2.4 Architectural Distinctness

Deep Resolver extends the existing runtime rather than replacing it. The same action framework, policy engine, and shell contract apply. The addition is at the orchestration layer: a new orchestration mode that supports the iterative loop.

This makes it feasible as an extension, not a separate runtime.

### 2.5 Recommendation

**Build first.** This is the closest to the existing architecture, directly addresses the hottest market category, and formalizes what the platform has half-built. Scope is an orchestration extension plus a new deployment type preset.

---

## 3) Concept 2: Thinker

### 3.1 What It Is

A deployment type focused on accurate knowledge synthesis across multiple data sources. The assistant reads from several vector databases and knowledge bases (potentially of different types and backends), ranks and merges results, and produces an evidence-heavy answer with full attribution.

Use case framing: "Ask anything about our product + regulatory environment + industry data + internal policies and get one accurate answer with sources."

### 3.2 Overlap with Existing Platform

Substantial overlap. The current platform already provides:

- Multi-collection vector search
- A search-source abstraction for knowledge sources
- Marketplace data plugins as shared, read-only knowledge sources
- Evidence attribution in the shell's rendering of answers
- Reranker support in planning documents

The differentiation Thinker would add is:

- Federation across different vector database backends at query time (Qdrant + pgvector + Pinecone + others)
- Accuracy-focused prompting and response format
- Heavier reliance on evidence and attribution
- Deeper knowledge navigation (following citations, walking knowledge graph links, expanding context based on initial results)

None of these require a new runtime. They require configuration, prompting, reranker tuning, and potentially a new knowledge source adapter for federated search.

### 3.3 Market Value

Crowded market. Perplexity Enterprise, Glean, Mendable, Alhena AI, and every vertical-specific knowledge assistant compete here. The accuracy-focused knowledge assistant category is valuable but saturated.

### 3.4 Architectural Distinctness

Not architecturally distinct. Thinker is a configuration preset plus prompt and reranker tuning on top of the existing platform.

### 3.5 Recommendation

**Ship as a marketplace template, not a new deployment type.** Create a "Knowledge Expert" template that pre-configures:

- Multi-source RAG with evidence attribution enabled
- Accuracy-focused system prompt
- Heavier reranker weighting
- Evidence-forward shell presentation

This adds immediate differentiation without new runtime code. Ship it alongside the e-commerce and support templates.

---

## 4) Concept 3: Smart Brain

### 4.1 What It Is

A fundamentally different deployment type: an always-running background agent that operates on batches of data without a human-initiated conversation. The flow:

1. A trigger fires (scheduled cron, event, webhook, queue message)
2. The agent reads a batch of items from a configured source (user list, events, documents, tickets, transactions, reviews)
3. For each item, the LLM runs a configured analysis (classification, extraction, scoring, summarization, pattern detection)
4. The analysis produces structured output conforming to a schema
5. The output is written to a configured destination (vector database, SQL table, message queue, webhook, file, platform behavior module)

This is not a chatbot. It is agentic data enrichment as a deployment type.

### 4.2 Example Use Cases

| Vertical | Example Smart Brain deployment |
|---|---|
| E-commerce | Every 4 hours: analyze recent customer sessions, classify intent, write to behavior store |
| Customer support | Every hour: read new tickets, extract sentiment, urgency, and topic, update CRM fields |
| HR / People Ops | Daily: analyze employee engagement signals, update people dashboard |
| Finance | Per transaction: classify for fraud risk and write score to decision store |
| Content / Reviews | Every 10 minutes: read new reviews, analyze sentiment, extract complaints, route to teams |
| Sales | Daily: analyze lead activity, score and prioritise, write to CRM |
| Operations | Hourly: analyze production logs, detect anomalies, alert |
| Compliance | Daily: classify new documents against policy taxonomy, write tags |

### 4.3 Overlap with Existing Platform

Minimal. The current platform is request-response. Smart Brain requires a different execution model:

- A scheduler (cron, event, webhook triggers)
- Batch execution runtime (read N records, process each with bounded concurrency)
- Write actions targeting structured destinations (not just vector storage)
- Run history, success/failure rates, cost per run, per-item error reporting
- Idempotency and retry semantics for long-running jobs
- Observability designed for batch jobs, not conversations

These do not exist in the current platform. They are the shape of a worker/pipeline runtime, not a conversational runtime.

### 4.4 Market Value

This is the most differentiated of the three concepts. The current market for "AI-enriched data pipelines" is fragmented across tools none of which solve it cleanly:

| Tool | What it does | Gap for this use case |
|---|---|---|
| Zapier with AI steps | Event-driven workflows with LLM nodes | Not designed for batch, expensive at scale, no structured output guarantees |
| n8n with AI nodes | Self-hosted workflows with LLM | Requires DevOps, no deployment type abstraction, no governance |
| Temporal + LangChain | Developer-built pipelines | Code-heavy, no product UX, no operator workflow |
| Airbyte + dbt + custom LLM | ETL with bolted-on LLM analysis | Three separate tools, no unified governance |
| Hex or Observable | Analytics notebooks with AI | Interactive, not scheduled autonomous execution |

No existing product packages "deploy a Smart Brain agent" as a self-serve deployment type with governance, run history, cost tracking, and a configuration UX.

This targets the data enrichment market and the AI agents market simultaneously. It moves Loom AI from "customer-facing AI" into "internal AI infrastructure" — a larger total addressable market with different buyers (data engineers, heads of operations, CTOs).

### 4.5 Architectural Distinctness

Highly distinct. Smart Brain is a new runtime model:

- Execution is asynchronous and long-running
- Triggers are scheduled, event-based, or webhook-based
- Input is a batch, not a single message
- Output is structured writes to external destinations
- Observability is per-run and per-item, not per-conversation
- Buyer persona is different (data/ops, not customer success)

### 4.6 Risks

The scope is significant. Smart Brain introduces:

- A new runtime component (batch executor)
- A new scheduling layer
- New action adapter types (datastore writes)
- New observability surfaces
- Potentially a new buyer and go-to-market motion

If bundled into the current platform maturation sprint, it would blow the scope. It needs its own track.

### 4.7 Recommendation

**Treat as a separate product bet on a parallel track.** Do not include in the current maturation sprint. Write its own plan document. Validate the conversational platform in market first, then use the same infrastructure (LLM routing, action framework, policy engine, marketplace) to launch Smart Brain as a second product line.

Smart Brain is the biggest market opportunity of the three, but the biggest scope risk if conflated with the conversational platform.

---

## 5) Summary Matrix

| Concept | Valid | Overlaps existing | Market value | Architecturally distinct | Recommendation |
|---|---|---|---|---|---|
| Deep Resolver | Yes | Partial (parts exist) | High (directly agentic commerce) | Extends existing runtime | Build as new deployment type |
| Thinker | Yes | Yes (mostly covered) | Medium (crowded market) | Not distinct | Ship as marketplace template |
| Smart Brain | Yes | No (different model) | Very high (largely unowned) | Fundamentally new runtime | Separate product track |

---

## 6) Alignment with Marketplace Control-Plane Composition

All three concepts should follow the existing Marketplace Control Plane Composition rules from the implementation baseline documents:

- **Deep Resolver** resolves into a new orchestration mode on the existing runtime. No new runtime code paths for plugins. Actions remain declarative. Policies remain platform-governed.

- **Thinker** resolves into a template plugin. Zero new runtime code. The template seeds deployment configuration using existing knowledge source, reranker, and prompt mechanisms.

- **Smart Brain** requires a new deployment type family and a new runtime execution mode. The marketplace composition model still applies: triggers, data sources, and datastore write destinations should be declarative configurations compiled into deployment drafts. Publish and apply still gate live behavior. Secrets still use the platform secret store.

None of the three concepts should introduce executable plugin code or bypass the deployment draft lifecycle.

---

## 7) Priority Order

Recommended sequence if all three move forward:

1. **Deep Resolver** (now) — extends existing architecture, highest direct competitive value for the current market segment
2. **Thinker as template** (now, zero cost) — ships alongside other marketplace templates
3. **Smart Brain** (separate track, post-launch) — biggest opportunity, biggest scope, needs validation that conversational platform is shipping before opening a second product line

---

## 8) Questions This Document Does Not Answer

- Specific orchestration mode design for Deep Resolver (tool-use budget, step limits, reasoning state format)
- Specific template content for Thinker (exact prompts, reranker weights, evidence display rules)
- Smart Brain trigger model, batch runtime design, datastore adapter types, observability surfaces
- Pricing and packaging for each deployment type
- Whether Smart Brain should launch under the Loom AI brand or as a separate product line

These belong in dedicated plan documents if and when each concept moves forward.
