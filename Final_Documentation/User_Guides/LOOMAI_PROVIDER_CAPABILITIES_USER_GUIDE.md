# LoomAI Provider Capabilities User Guide

Status: external provider taxonomy (updated 2026-06-17)

This guide defines the official LoomAI provider capability taxonomy for external product teams, integrators, and AI opportunity-analysis systems.

Use this taxonomy when a product needs to decide whether LoomAI is the right AI enablement provider for a customer-facing, operator-facing, or product-owner-facing feature.

The capability codes in this guide are stable for:

- `schemaVersion=loomai-provider-capabilities-v1`
- `provider=LoomAI`
- downstream fields such as `loomaiCapabilityCode`

Do not use product-specific implementation labels as capability codes. Product-specific workflows may reference these capabilities, but the taxonomy itself describes LoomAI provider capabilities that a customer product can adopt.

For ProdUS specifically: ProdUS should pass its own workspace/product/package/finding/opportunity context into LoomAI, but the AI opportunity analysis prompt should recommend one of the LoomAI capability codes below. Do not create ProdUS-only capability codes such as `produs_findings_chat`, `produs_package_review`, or `produs_internal_bridge`.

---

## 1) How To Use This Taxonomy

Include the JSON object in AI opportunity-analysis prompt context when asking a model to recommend AI integrations.

The model should:

- choose one `loomaiCapabilityCode` from the `capabilities[].code` list
- explain why the capability fits the product opportunity
- respect the `notFor` boundaries
- avoid recommending LoomAI for flows that should remain deterministic, unauthenticated, ungoverned, or outside the product data policy

The model should not:

- invent capability codes
- expose backend-only credentials or deployment internals
- recommend LoomAI only because a product has an API
- confuse a customer product's internal integration surface with a LoomAI provider capability
- describe ProdUS-owned screens, database tables, or bridge endpoints as LoomAI capabilities

---

## 2) Stability Rules

The codes below are official/stable for `loomai-provider-capabilities-v1`.

Compatibility rules:

- Add new codes when LoomAI gains a new stable provider capability.
- Do not silently rename existing codes.
- If a capability must be renamed or reshaped, introduce a new schema version or explicitly deprecate the old code.
- Product prompts and generated opportunity records should persist `loomaiCapabilityCode`, not the owner-facing `name`.

---

## 3) Capability Taxonomy JSON

```json
{
  "schemaVersion": "loomai-provider-capabilities-v1",
  "provider": "LoomAI",
  "capabilities": [
    {
      "code": "loomai_runtime_orchestration",
      "name": "AI runtime orchestration",
      "description": "Coordinates intent detection, mode selection, retrieval, tool/action use, policy checks, and final answer generation.",
      "useWhen": "A product needs a governed AI layer instead of scattered prompts or isolated tool calls.",
      "requiredContext": "User journeys, modes, allowed data, allowed actions, auth boundaries, and success criteria.",
      "ownerValue": "Turns AI into a controllable runtime capability that can be reused across product surfaces.",
      "implementationPattern": "Product calls LoomAI runtime APIs with query, conversationId, mode, position, context, and attachments.",
      "notFor": ["Pure deterministic CRUD", "Replacing product business logic", "Uncontrolled autonomous agents"],
      "exampleUseCases": ["Support assistant", "Admin copilot", "Project analysis assistant", "Product-owner workspace assistant"]
    },
    {
      "code": "loomai_curated_modes_prompt_packs",
      "name": "Curated modes and prompt packs",
      "description": "Provides reusable mode profiles, routing defaults, prompt overlays, and product-specific AI behavior packs.",
      "useWhen": "A product needs different assistant behaviors such as shopping help, diagnosis, support, navigator, or executor modes.",
      "requiredContext": "Target modes, surfaces, tone, domain constraints, safety rules, and override rules.",
      "ownerValue": "Lets owners ship consistent AI behavior without rebuilding prompts per feature.",
      "implementationPattern": "Enable curated packs and pass mode or surface position into the runtime.",
      "notFor": ["One prompt with no product behavior differences", "Hardcoded domain logic inside the AI provider", "Bypassing runtime policy"],
      "exampleUseCases": ["Thinker mode analysis", "Resolver mode support", "Commerce guidance mode", "Internal operations copilot"]
    },
    {
      "code": "loomai_thinker_resolver_workflows",
      "name": "Thinker / Resolver workflows",
      "description": "Provides evidence-backed diagnosis, resolution planning, policy review, dry-run, and governed follow-up workflows.",
      "useWhen": "A product needs AI to diagnose an issue, explain evidence, recommend next steps, and optionally prepare a governed resolution action.",
      "requiredContext": "Issue/question, relevant resource context, allowed evidence, resolution policy, action families, confirmation rules, and audit requirements.",
      "ownerValue": "Moves from chat answers to accountable issue-resolution workflows without allowing unrestricted automation.",
      "implementationPattern": "Create or call a Thinker session for read-first diagnosis, then use Resolver policy/dry-run/execution only for allowed action families.",
      "notFor": ["Unrestricted autonomous operations", "High-risk writes without policy and dry-run", "Support replacement without merchant/operator handoff"],
      "exampleUseCases": ["Launch blocker diagnosis", "Customer issue triage", "Support escalation preparation", "Evidence-backed product readiness review"]
    },
    {
      "code": "loomai_model_provider_abstraction",
      "name": "Model provider abstraction",
      "description": "Supports using different LLM and embedding providers through LoomAI runtime configuration.",
      "useWhen": "A product wants provider flexibility, fallback options, or environment-specific model choices.",
      "requiredContext": "Provider choices, model requirements, latency/cost limits, API credentials, and data policy.",
      "ownerValue": "Avoids locking the product to one AI vendor and makes provider choices operational instead of hardcoded.",
      "implementationPattern": "Configure provider profiles for runtime generation, embeddings, file handling, or structured calls.",
      "notFor": ["Bypassing provider terms", "Provider selection without testing", "Assuming all models support identical file/tool features"],
      "exampleUseCases": ["OpenAI runtime", "Gemini file input", "ONNX embeddings", "Cloud/self-hosted vector choices"]
    },
    {
      "code": "loomai_grounded_rag_answers",
      "name": "Grounded answers / RAG",
      "description": "Generates answers grounded in retrieved documents, chunks, citations, or managed knowledge sources.",
      "useWhen": "The product must answer from owned knowledge such as docs, policies, catalog data, product data, or support material.",
      "requiredContext": "Knowledge sources, vector spaces or retrieval endpoint, citation rules, freshness expectations, and access rules.",
      "ownerValue": "Reduces hallucination and makes answers explainable.",
      "implementationPattern": "Use LoomAI-managed retrieval or an external retrieval connector, then let LoomAI generate the answer from returned evidence.",
      "notFor": ["Fresh transactional state without read actions", "Regulated final advice without review", "Private data without authorization"],
      "exampleUseCases": ["Help center Q&A", "Policy explanation", "Catalog guidance", "Product documentation assistant"]
    },
    {
      "code": "loomai_external_retrieval_connector",
      "name": "External retrieval connector",
      "description": "Lets a customer keep their own search/retrieval system while LoomAI handles orchestration and final generation.",
      "useWhen": "The product already owns search or document retrieval and does not want LoomAI to index the data.",
      "requiredContext": "Retrieval endpoint, document schema, auth, citation fields, filters, and timeout behavior.",
      "ownerValue": "Adopts LoomAI without migrating the knowledge system.",
      "implementationPattern": "Customer exposes a documents-only retrieval endpoint; LoomAI calls it during RAG flows.",
      "notFor": ["Endpoints returning unbounded data", "Search results without usable text", "Private retrieval without scoped auth"],
      "exampleUseCases": ["Enterprise search integration", "Existing knowledge base search", "Customer-owned document store"]
    },
    {
      "code": "loomai_managed_vectorization",
      "name": "Managed vectorization and data sync",
      "description": "Ingests, embeds, indexes, updates, and deletes customer-provided documents or entities for AI retrieval.",
      "useWhen": "A product needs AI-searchable knowledge but does not want to build its own vector pipeline.",
      "requiredContext": "Source data, vector-space names, sync cadence, metadata, tenant ownership, deletion rules, and freshness expectations.",
      "ownerValue": "Creates a reusable AI knowledge layer from product data.",
      "implementationPattern": "Push documents/entities through ingestion or sync APIs; LoomAI indexes and retrieves them.",
      "notFor": ["Highly volatile transactional data", "Secrets without governance", "Data that cannot be stored or indexed"],
      "exampleUseCases": ["Product catalog sync", "Documentation indexing", "Support article vectorization", "Policy knowledge refresh"]
    },
    {
      "code": "loomai_semantic_relationship_query",
      "name": "Semantic relationship query",
      "description": "Uses structured entity relationships and semantic query planning to answer across connected business objects.",
      "useWhen": "Questions require reasoning across related entities, not only plain document chunks.",
      "requiredContext": "Entity schemas, relationships, fields, allowed traversal, return shape, and access rules.",
      "ownerValue": "Enables deeper product intelligence over structured data.",
      "implementationPattern": "Configure relationship schemas and allow LoomAI to plan bounded relationship queries.",
      "notFor": ["Unmodeled databases", "Unbounded joins", "Replacing database permissions"],
      "exampleUseCases": ["Find projects blocked by missing dependencies", "Explain customer account relationships", "Analyze linked records"]
    },
    {
      "code": "loomai_transient_document_understanding",
      "name": "Transient document understanding",
      "description": "Analyzes short-lived files or provider file inputs without turning them into a permanent corpus.",
      "useWhen": "Users upload documents for immediate analysis, extraction, comparison, or summarization.",
      "requiredContext": "Temporary file URLs, file type/size limits, retention policy, allowed providers, and desired output.",
      "ownerValue": "Adds document intelligence without permanent indexing.",
      "implementationPattern": "Pass transient attachments or provider file inputs into LoomAI runtime requests.",
      "notFor": ["Long-term knowledge storage", "Unauthorized files", "Files that must never leave the product boundary"],
      "exampleUseCases": ["Proposal review", "PDF summary", "Requirement extraction", "Design-partner evidence review"]
    },
    {
      "code": "loomai_contextual_attachments",
      "name": "Contextual attachments",
      "description": "Allows users or products to attach active documents, products, pages, or resource summaries to a chat or analysis request.",
      "useWhen": "The AI result should be grounded in user-selected project, file, page, product, or workspace context.",
      "requiredContext": "Attachment IDs, active attachment selection, content policy, and user/resource ownership.",
      "ownerValue": "Makes AI feel aware of the current product workspace without reading everything by default.",
      "implementationPattern": "Send attachments and activeAttachmentIds with runtime chat or query-once calls.",
      "notFor": ["Implicitly reading all user files", "Sensitive files without consent", "Permanent indexing unless using vectorization"],
      "exampleUseCases": ["Analyze selected files", "Answer from current product context", "Summarize attached docs", "Compare selected commerce products"]
    },
    {
      "code": "loomai_conversation_session_memory",
      "name": "Conversation session memory",
      "description": "Maintains conversation/session context for multi-turn AI experiences.",
      "useWhen": "The user expects follow-up questions, continuity, or assistant state across turns.",
      "requiredContext": "Conversation ID, user identity, retention rules, session scope, and tenant/resource boundaries.",
      "ownerValue": "Improves usability for real assistant workflows.",
      "implementationPattern": "Use chat/session APIs with conversation identity and product auth.",
      "notFor": ["Stateless one-off analysis", "Workflows where history must not be stored", "Cross-tenant memory"],
      "exampleUseCases": ["Support conversation", "Workspace assistant", "Iterative planning", "Shopping follow-up flow"]
    },
    {
      "code": "loomai_query_once_analysis",
      "name": "One-shot AI analysis",
      "description": "Runs a single AI analysis request without requiring a persistent chat session.",
      "useWhen": "A backend or UI needs one structured answer, recommendation, extraction, classification, or opportunity judgment.",
      "requiredContext": "Prompt objective, input data, expected schema, and authorization.",
      "ownerValue": "Adds AI analysis to product workflows without building chat.",
      "implementationPattern": "Call a query-once/runtime endpoint and consume the response or structured payload.",
      "notFor": ["Multi-turn assistant UX", "Workflows requiring persisted memory", "Actions needing user confirmation"],
      "exampleUseCases": ["AI opportunity analysis", "Risk scoring", "Requirement extraction", "Launch-readiness summary"]
    },
    {
      "code": "loomai_embedded_assistant_ui",
      "name": "Embedded assistant UI / Max Mode",
      "description": "Powers an in-product assistant surface, including richer embedded experiences such as Max Mode.",
      "useWhen": "A product wants users to interact with AI directly inside the application.",
      "requiredContext": "Runtime URL, user/session auth, allowed modes, UI placement, branding, and enabled capabilities.",
      "ownerValue": "Ships a usable AI surface faster than building a full custom assistant UI.",
      "implementationPattern": "Embed LoomAI widget or connect a custom UI to LoomAI runtime APIs using the canonical chat contract.",
      "notFor": ["Backend-only automation", "Unauthenticated private capability access", "A fully custom native UX with no shared shell"],
      "exampleUseCases": ["In-app help", "Workspace assistant", "Storefront assistant", "Owner-facing analysis panel"]
    },
    {
      "code": "loomai_commerce_shopping_assistant",
      "name": "Commerce shopping assistant",
      "description": "Provides storefront shopping help for product discovery, product detail answers, comparisons, policy answers, and grounded recommendations.",
      "useWhen": "An ecommerce product or store wants AI help on shopping, collection, product, search, FAQ, comparison, or policy pages.",
      "requiredContext": "Store/catalog sources, page context, product or collection context, policy sources, allowed surfaces, package/tier posture, and storefront auth boundaries.",
      "ownerValue": "Helps shoppers choose products faster while keeping answers grounded in store content.",
      "implementationPattern": "Use Loom Companion / Companion Thinker surfaces backed by RAG, read-action grounding, Storefront MCP tools, and the embedded assistant UI.",
      "notFor": ["Autonomous checkout", "Payment completion", "Account-specific protected data", "Refund or cancellation execution"],
      "exampleUseCases": ["AI product search", "Product FAQ", "Product comparison", "Return-policy explanation", "Shopping chat"]
    },
    {
      "code": "loomai_account_order_resolution_assistant",
      "name": "Account and order resolution assistant",
      "description": "Provides customer-safe account, order, delivery, return, and support-resolution assistance when auth, scopes, package, and verification gates allow it.",
      "useWhen": "A product or store wants AI to help authenticated customers understand order status, policy posture, and support handoff options.",
      "requiredContext": "Customer/account auth, order lookup inputs, protected-data posture, support handoff configuration, package entitlement, allowed action families, and live verification evidence.",
      "ownerValue": "Deflects basic support while preserving control over sensitive account/order workflows.",
      "implementationPattern": "Use Companion Resolver or a governed product-host flow with read-only order lookup, support handoff, evidence, confirmation, and audit where applicable.",
      "notFor": ["Payment detail exposure", "Unrestricted support-desk replacement", "Refund execution without explicit governed capability", "Cancellation/address/order edits without explicit governed capability"],
      "exampleUseCases": ["Read-only order status lookup", "Delivery question triage", "Return eligibility explanation", "Support escalation handoff"]
    },
    {
      "code": "loomai_tool_mcp_orchestration",
      "name": "Tool and MCP orchestration",
      "description": "Discovers, selects, verifies, and invokes configured tools or MCP-backed capabilities during AI workflows.",
      "useWhen": "The assistant needs real external capabilities beyond text generation.",
      "requiredContext": "Tool schemas, MCP server config, auth, allowed operations, argument templates, drift policy, and result contracts.",
      "ownerValue": "Lets AI use real systems through governed contracts.",
      "implementationPattern": "Register MCP/tool contracts and route execution through LoomAI connector, product host, or MCP Gateway paths.",
      "notFor": ["Arbitrary tool URLs", "Tools without schemas", "High-risk operations without controls"],
      "exampleUseCases": ["Catalog search", "Order lookup", "CRM read", "Ticket creation", "Third-party MCP server call"]
    },
    {
      "code": "loomai_mcp_discovery_plugin_import",
      "name": "MCP discovery and plugin import",
      "description": "Discovers MCP server tools, verifies tool schemas, and creates reviewed Marketplace plugin drafts instead of exposing tools directly at runtime.",
      "useWhen": "A product wants to adopt an MCP server while preserving review, tiering, action catalog, and governance controls.",
      "requiredContext": "MCP endpoint, auth profile, expected tools, schema review rules, target product/vertical, action naming, risk policy, and entitlement mapping.",
      "ownerValue": "Turns MCP optionality into controlled product capabilities that can be installed, versioned, and governed.",
      "implementationPattern": "Operator runs MCP discovery, imports private ACTION plugin drafts, maps tool contracts, reviews risk/tier policy, publishes, and installs into deployments.",
      "notFor": ["Auto-exposing every MCP tool to users", "Skipping schema review", "Using tools/list as production truth"],
      "exampleUseCases": ["Import Shopify Storefront MCP tools", "Create a CRM MCP action pack", "Verify third-party MCP schema drift"]
    },
    {
      "code": "loomai_connector_action_catalog",
      "name": "Connector-backed action catalog",
      "description": "Defines customer actions through portable contracts and executes them through customer connector endpoints.",
      "useWhen": "A product wants LoomAI to call customer-owned APIs in a language-agnostic way.",
      "requiredContext": "Action catalog, connector URL, schemas, auth, parameter rules, confirmation posture, and error behavior.",
      "ownerValue": "Connects AI to product APIs without coupling LoomAI to product internals.",
      "implementationPattern": "Publish action contracts and expose a customer connector API for execution.",
      "notFor": ["Unspecified APIs", "Actions with no validation", "Secrets passed through prompts"],
      "exampleUseCases": ["Create project", "Search products", "Open escalation", "Update support profile"]
    },
    {
      "code": "loomai_read_action_grounding",
      "name": "Live read-action grounding",
      "description": "Executes safe read actions and forces final answer generation from returned action data.",
      "useWhen": "The answer needs fresh live data that should not be indexed as static knowledge.",
      "requiredContext": "Read-only action contract, grounding eligibility, result shape, mode policy, and auth scope.",
      "ownerValue": "Combines live operational facts with natural-language answers.",
      "implementationPattern": "Mark read actions as grounding eligible; LoomAI executes them and generates a user-facing answer from action evidence.",
      "notFor": ["Write actions", "Raw JSON dumps", "Unbounded private data reads"],
      "exampleUseCases": ["Check order status", "Lookup project state", "Find available products", "Inspect launch readiness"]
    },
    {
      "code": "loomai_governed_action_execution",
      "name": "Governed action execution",
      "description": "Executes side-effecting actions with confirmation, access control, idempotency, policy checks, rate limits, and audit-friendly handling.",
      "useWhen": "AI should help users take actions, not only provide recommendations.",
      "requiredContext": "Action schema, side-effect level, permissions, confirmation copy, idempotency, rollback expectations, and post-action verification requirements.",
      "ownerValue": "Enables useful automation while preserving control and consent.",
      "implementationPattern": "Define confirmable actions and let LoomAI pause for approval before execution; record evidence and audit results.",
      "notFor": ["Silent destructive actions", "Payments without explicit approval", "Autonomous production changes"],
      "exampleUseCases": ["Create task", "Submit request", "Start workflow", "Update record after approval", "Create support escalation"]
    },
    {
      "code": "loomai_structured_outputs",
      "name": "Structured output contracts",
      "description": "Produces validated machine-readable JSON for downstream product workflows.",
      "useWhen": "The product needs parseable recommendations, classifications, extracted data, or opportunity records.",
      "requiredContext": "JSON schema, required fields, validation rules, fallback behavior, and consumer expectations.",
      "ownerValue": "Makes AI output usable by backend code without fragile text parsing.",
      "implementationPattern": "Provide schema or typed contract and validate LoomAI output before storing or acting on it.",
      "notFor": ["Free-form chat only", "Schemas that hide uncertainty", "Replacing backend validation"],
      "exampleUseCases": ["AI opportunity records", "Extraction payloads", "Risk classifications", "Provider recommendation payloads"]
    },
    {
      "code": "loomai_privacy_pii_controls",
      "name": "Privacy and PII controls",
      "description": "Supports sensitivity classification, redaction-aware handling, and privacy-oriented runtime policy.",
      "useWhen": "AI touches customer data, personal data, support content, or sensitive business context.",
      "requiredContext": "Data classes, PII policy, redaction rules, retention requirements, provider allowlist, and audit requirements.",
      "ownerValue": "Reduces privacy risk when introducing AI.",
      "implementationPattern": "Enable PII/privacy modules and enforce data handling rules before retrieval, generation, action execution, or logging.",
      "notFor": ["Bypassing legal/compliance review", "Sending secrets to models", "Treating redaction as perfect security"],
      "exampleUseCases": ["Support transcript analysis", "Customer-data assistant", "Sensitive document review", "Protected order-data posture"]
    },
    {
      "code": "loomai_safety_governance",
      "name": "Safety and governance controls",
      "description": "Provides runtime guardrails for auth, scopes, tenant boundaries, policy decisions, and fail-closed behavior.",
      "useWhen": "AI will access private data, tools, actions, or production workflows.",
      "requiredContext": "Caller identity, tenant boundaries, allowed modes/actions, policy requirements, and audit posture.",
      "ownerValue": "Makes AI safer to operate in production.",
      "implementationPattern": "Configure scoped auth, policy packs, guarded execution paths, and denied-by-default boundaries.",
      "notFor": ["Bypassing product authorization", "Frontend exposure of backend-only secrets", "Cross-tenant data sharing"],
      "exampleUseCases": ["Tenant-aware assistant", "Admin-only actions", "Permissioned support AI", "Package-gated commerce actions"]
    },
    {
      "code": "loomai_runtime_auth_assignment",
      "name": "Runtime auth and assignment",
      "description": "Provides secure runtime access, backend assertions, browser-safe auth modes, and consumer-bound runtime assignment.",
      "useWhen": "A product needs to connect safely to the correct active LoomAI runtime across environments.",
      "requiredContext": "Consumer ID, issuer, audience, auth mode, assignment key, runtime URL, and secret boundary.",
      "ownerValue": "Lets products integrate with LoomAI without hardcoding fragile deployment URLs or exposing backend secrets.",
      "implementationPattern": "Backend resolves runtime assignment and calls the assigned runtime with scoped auth/assertions.",
      "notFor": ["Putting backend-only keys in frontend code", "Unauthenticated private runtimes", "Using deployment IDs as user-facing product logic"],
      "exampleUseCases": ["Staging runtime assignment", "Production runtime promotion", "Private backend runtime calls", "Consumer-bound chat"]
    },
    {
      "code": "loomai_managed_runtime_deployment",
      "name": "Managed runtime deployment",
      "description": "Provisions, configures, verifies, restarts, promotes, and operates LoomAI runtimes and managed product services across staging and production targets.",
      "useWhen": "A product owner wants LoomAI-hosted AI runtime infrastructure rather than operating the runtime, gateway, vector store, and product services directly.",
      "requiredContext": "Deployment target profile, environment, package/profile, provider credentials, secrets, runtime config, health checks, rollback requirements, and verification gates.",
      "ownerValue": "Makes AI deployment repeatable and operable without exposing infrastructure details to product teams.",
      "implementationPattern": "Use Platform-managed deployment templates, target profiles, health checks, restartable services, staging/prod promotion, and evidence gates.",
      "notFor": ["Customer-managed infrastructure with no LoomAI operational role", "Manual infrastructure changes outside Platform truth", "Exposing provider secrets to product users"],
      "exampleUseCases": ["Create staging runtime", "Promote to production runtime", "Restart MCP Gateway service", "Verify hosted AI deployment"]
    },
    {
      "code": "loomai_self_service_launch_workflow",
      "name": "Self-service launch and evidence workflow",
      "description": "Provides partner/merchant/operator launch workflows for intake, approval, readiness, staging review, production promotion, evidence bundles, support escalation, and rollback requests.",
      "useWhen": "A product needs a repeatable path from AI configuration to live production use with minimal founder/operator intervention.",
      "requiredContext": "Actor roles, approval lifecycle, package/profile, staging and production readiness checks, evidence requirements, support path, and rollback posture.",
      "ownerValue": "Makes AI adoption operationally repeatable instead of bespoke onboarding.",
      "implementationPattern": "Use LoomAI portal/workspace flows backed by Platform APIs, readiness audits, verification packs, evidence exports, production gates, and support lifecycle records.",
      "notFor": ["One-off consulting-only onboarding", "Skipping merchant/customer approval", "Launching without verification evidence"],
      "exampleUseCases": ["Design-partner launch", "Merchant approval workspace", "Go-production request", "Launch evidence bundle", "Rollback/deactivation request"]
    },
    {
      "code": "loomai_observability_evaluation",
      "name": "Traces, evaluation, and observability",
      "description": "Captures evidence around orchestration decisions, action use, runtime health, smoke tests, prompt regressions, quality checks, and launch readiness.",
      "useWhen": "The owner needs to verify, debug, evaluate, or monitor AI behavior.",
      "requiredContext": "Test prompts, expected behavior, target environment, metrics, traces, and evidence retention.",
      "ownerValue": "Makes AI behavior reviewable instead of opaque.",
      "implementationPattern": "Run readiness checks, trace review, prompt regression tests, hosted verification, and live smoke tests.",
      "notFor": ["Launching AI without acceptance criteria", "Unlogged critical decisions", "Using observability as a replacement for governance"],
      "exampleUseCases": ["Launch readiness", "Prompt regression", "Action audit", "RAG quality checks", "Staging release gate"]
    },
    {
      "code": "loomai_behavior_intelligence",
      "name": "Behavior intelligence",
      "description": "Analyzes user/product behavior signals to classify sentiment, trends, events, engagement patterns, or value evidence.",
      "useWhen": "A product wants AI-assisted insight from usage events or behavioral data.",
      "requiredContext": "Event schema, user/session scope, privacy policy, analysis goals, and aggregation rules.",
      "ownerValue": "Turns product behavior data into insight for owners and operators.",
      "implementationPattern": "Send structured behavior events and analyze them through LoomAI behavior modules or runtime workflows.",
      "notFor": ["Surveillance without consent", "Raw PII-heavy event streams", "Replacing analytics source-of-truth systems"],
      "exampleUseCases": ["Usage trend analysis", "Sentiment classification", "Engagement insights", "Shopper question analysis"]
    },
    {
      "code": "loomai_marketplace_capability_packs",
      "name": "Marketplace capability packs",
      "description": "Packages reusable AI capabilities, prompts, connectors, MCP actions, datasets, templates, inference profiles, and vertical enablement profiles.",
      "useWhen": "A product wants to adopt a prebuilt AI capability instead of designing everything from scratch.",
      "requiredContext": "Target vertical, enabled surfaces, connector needs, pack version, entitlement rules, package/profile, and deployment compatibility.",
      "ownerValue": "Shortens time-to-value for common AI product patterns while keeping capabilities reviewable and versioned.",
      "implementationPattern": "Select and provision a LoomAI capability pack, then configure product-specific data/actions through Marketplace installs and package profiles.",
      "notFor": ["Custom workflows with no reusable pattern", "Installing packs without reviewing boundaries", "Treating templates as finished product logic"],
      "exampleUseCases": ["Commerce assistant pack", "Support assistant pack", "Knowledge assistant pack", "MCP action pack"]
    }
  ]
}
```

---

## 4) Recommended Opportunity Output Field

When this taxonomy is used during opportunity analysis, generated opportunity records should include:

```json
{
  "loomaiCapabilityCode": "loomai_query_once_analysis",
  "recommendedProvider": "LoomAI"
}
```

The selected `loomaiCapabilityCode` must be one of the stable codes from this guide.

For ProdUS, a recommended opportunity can additionally store ProdUS-owned context such as product id, package id, workspace id, finding id, page type, and actor role. Those fields are context for LoomAI; they are not LoomAI capability codes.

---

## 5) Boundary Guidance

LoomAI is a good fit when the product needs governed AI capability, grounded reasoning, structured output, AI-enabled user experience, managed deployment, marketplace capability packaging, or controlled tool/action execution.

LoomAI should not be recommended when:

- the workflow is deterministic and does not need AI reasoning
- the product has no safe way to provide required context
- the user action is high-risk and cannot be confirmed or governed
- the data cannot be shared with an AI runtime or model provider
- the product needs backend-only secrets in frontend code
- the desired behavior is unrestricted autonomous production change
- the recommendation depends only on a product's internal bridge/API surface rather than a LoomAI provider capability

When in doubt, recommend a smaller capability such as `loomai_query_once_analysis`, `loomai_grounded_rag_answers`, or `loomai_read_action_grounding` before recommending broad action execution.
