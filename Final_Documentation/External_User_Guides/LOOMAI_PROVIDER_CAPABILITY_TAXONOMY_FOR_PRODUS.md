# LoomAI Provider Capability Taxonomy For ProdUS

Status: official prompt/context taxonomy for ProdUS AI opportunity analysis.

Last updated: 2026-06-04.

Purpose: give ProdUS a stable provider capability object it can include in AI opportunity analysis prompts when deciding whether to recommend LoomAI as the AI enablement provider.

Use this as LoomAI provider truth, not as ProdUS internal integration truth. ProdUS should reason over the capability names and descriptions below. Do not expose internal LoomAI deployment ids, runtime endpoints, MCP tool names, private auth details, bridge implementation details, or Platform operator surfaces as customer-facing capabilities.

```json
{
  "schemaVersion": "loomai-provider-capabilities-v1",
  "provider": "LoomAI",
  "capabilities": [
    {
      "code": "commerce_ai_shopping_companion",
      "name": "AI shopping companion for commerce stores",
      "description": "LoomAI provides an embedded AI companion that helps shoppers discover products, ask product questions, compare options, and get grounded store guidance before purchase.",
      "useWhen": "Use when a commerce product, marketplace, or Shopify store needs shopper-facing AI assistance that improves discovery, confidence, and conversion.",
      "requiredContext": "Storefront platform, product catalog access, enabled source categories, target storefront pages, merchant policy boundaries, and desired shopper surfaces.",
      "ownerValue": "Turns static storefront pages into assisted shopping journeys and reduces product-discovery friction.",
      "implementationPattern": "Deploy Loom Companion through a platform-specific storefront integration, backed by LoomAI managed runtime, knowledge indexing, and merchant configuration controls.",
      "notFor": [
        "Autonomous checkout",
        "Payment handling",
        "Unbounded support automation",
        "Stores without accessible product or policy data"
      ],
      "exampleUseCases": [
        "AI assistant on Shopify product and collection pages",
        "Guided buying help for a fashion, electronics, or specialty store",
        "Natural-language storefront companion for a multi-category catalog"
      ]
    },
    {
      "code": "commerce_product_discovery",
      "name": "Natural-language product discovery",
      "description": "LoomAI lets shoppers search and browse a catalog using ordinary language instead of rigid filters or exact keywords.",
      "useWhen": "Use when customers struggle to find the right product, when search terms are vague, or when catalog breadth creates decision friction.",
      "requiredContext": "Product titles, descriptions, variants, collections, tags, availability signals, and any store-specific merchandising constraints.",
      "ownerValue": "Improves product findability and helps shoppers reach relevant items faster.",
      "implementationPattern": "Index catalog sources and expose an AI search surface, chat prompt, or embedded product discovery component.",
      "notFor": [
        "Replacing the primary ecommerce search engine without validation",
        "Inventing products not present in the catalog",
        "Guaranteeing availability without live inventory context"
      ],
      "exampleUseCases": [
        "Find lightweight backpacks for carry-on travel",
        "Search for gifts under a specific budget",
        "Suggest products for a use case instead of an exact SKU"
      ]
    },
    {
      "code": "commerce_product_guidance",
      "name": "Product detail and fit guidance",
      "description": "LoomAI answers product-specific questions using grounded catalog, page, policy, and review-aware evidence where available.",
      "useWhen": "Use when shoppers need help understanding specifications, sizing, compatibility, materials, suitability, or feature tradeoffs.",
      "requiredContext": "Product page data, product metafields, variant information, relevant policies, and optional attached products or page context.",
      "ownerValue": "Reduces hesitation on product pages and gives shoppers confidence without waiting for human support.",
      "implementationPattern": "Render contextual product insight, FAQ, or companion chat surfaces on product pages and connect them to grounded product evidence.",
      "notFor": [
        "Medical, legal, or regulated product advice without domain review",
        "Claims not supported by product data",
        "Replacing required merchant disclosures"
      ],
      "exampleUseCases": [
        "Explain whether a product suits a beginner",
        "Summarize key product differences",
        "Answer product-specific shipping or return questions"
      ]
    },
    {
      "code": "commerce_product_comparison",
      "name": "Product comparison guidance",
      "description": "LoomAI compares selected or retrieved products using grounded attributes, evidence, and shopper goals.",
      "useWhen": "Use when buyers often choose between similar products or need a recommendation based on tradeoffs.",
      "requiredContext": "Comparable product records, product attributes, shopper criteria, known constraints, and optional attached products.",
      "ownerValue": "Helps undecided shoppers make a decision and keeps comparison activity inside the store experience.",
      "implementationPattern": "Enable comparison surfaces, product attachments, and chat-based comparison prompts using the indexed catalog.",
      "notFor": [
        "Ranking products by unsupported claims",
        "Comparing unavailable or unknown products as if they are in stock",
        "Replacing expert review where regulated advice is required"
      ],
      "exampleUseCases": [
        "Compare three laptops for a student",
        "Choose between two skincare products based on stated needs",
        "Explain tradeoffs between product bundles"
      ]
    },
    {
      "code": "commerce_policy_faq_guidance",
      "name": "Policy and FAQ guidance",
      "description": "LoomAI answers store policy, FAQ, shipping, return, refund, and content questions from published merchant sources.",
      "useWhen": "Use when shoppers ask repeated operational questions that are already answered in store policies or help content.",
      "requiredContext": "Published policies, FAQ pages, help content, shipping and return pages, and merchant-approved support wording.",
      "ownerValue": "Deflects repetitive questions and gives consistent, source-grounded guidance.",
      "implementationPattern": "Index policy and content sources, then expose policy strips, FAQ blocks, and chat answers with safe fallback when evidence is missing.",
      "notFor": [
        "Order-specific refund decisions",
        "Customer-account data exposure",
        "Policy interpretation beyond published merchant wording"
      ],
      "exampleUseCases": [
        "What is the return window?",
        "Do you ship internationally?",
        "Can I return this product if it does not fit?"
      ]
    },
    {
      "code": "commerce_review_aware_grounding",
      "name": "Review-aware product grounding",
      "description": "LoomAI can incorporate compatible review and rating metadata into product answers when the store exposes supported review signals.",
      "useWhen": "Use when review sentiment, rating summaries, or UGC context can improve product confidence and the data is available in a compatible store format.",
      "requiredContext": "Compatible review provider metadata, product-review associations, rating summaries, and provider-specific availability.",
      "ownerValue": "Lets product guidance reflect customer feedback without claiming unsupported universal review coverage.",
      "implementationPattern": "Detect compatible review metadata during source indexing and include it as grounded product evidence.",
      "notFor": [
        "Claiming every review provider is supported",
        "Scraping private review data without permission",
        "Creating fake review summaries"
      ],
      "exampleUseCases": [
        "Use Judge.me or Yotpo rating metadata in product answers",
        "Mention review count and rating where available",
        "Compare products using compatible review signals"
      ]
    },
    {
      "code": "commerce_cart_assistance",
      "name": "Governed cart assistance",
      "description": "LoomAI can support bounded cart help, such as cart review and cart updates, only when the store is entitled, verified, and action governance is enabled.",
      "useWhen": "Use when a commerce product needs customer-confirmed cart assistance rather than only product answers.",
      "requiredContext": "Cart access capability, action entitlement, merchant action policy, confirmation rules, audit requirements, and live verification status.",
      "ownerValue": "Moves from advice to safe shopper action while preserving merchant control and auditability.",
      "implementationPattern": "Expose cart actions through LoomAI governed action flow with confirmation, policy checks, audit, and fail-closed behavior.",
      "notFor": [
        "Autonomous checkout",
        "Payment collection",
        "Unconfirmed cart mutation",
        "Inventory or pricing writes outside approved cart capability"
      ],
      "exampleUseCases": [
        "Add a selected product to cart after shopper confirmation",
        "Update cart quantity with explicit confirmation",
        "Review cart contents and suggest missing accessories"
      ]
    },
    {
      "code": "commerce_account_order_assistance",
      "name": "Account and order assistance",
      "description": "LoomAI can support account and order resolution flows when customer authentication, scopes, protected-data posture, tier entitlement, and live verification allow it.",
      "useWhen": "Use when a commerce owner wants customers to get safe order, delivery, return, or account help inside a governed assistant experience.",
      "requiredContext": "Customer authentication path, allowed order data, protected-data approval posture, support policies, entitlement, and verification evidence.",
      "ownerValue": "Extends AI assistance into post-purchase support while avoiding unsafe customer-data exposure.",
      "implementationPattern": "Route account/order pages to Companion Resolver, then allow only verified read or governed support flows with clear blocked-capability reasons.",
      "notFor": [
        "Payment detail exposure",
        "Autonomous refund execution",
        "Unapproved cancellation execution",
        "Order edits without explicit verified capability",
        "Broad support-desk replacement"
      ],
      "exampleUseCases": [
        "Authenticated order lookup where scope and verification pass",
        "Explain return eligibility from policy and order context",
        "Escalate unresolved order issue to merchant support"
      ]
    },
    {
      "code": "commerce_support_handoff",
      "name": "Support handoff and escalation",
      "description": "LoomAI creates structured support handoff or escalation records when the AI reaches a safe boundary or when a governed support workflow is approved.",
      "useWhen": "Use when the AI should not pretend to solve every issue and needs a controlled path to human or partner support.",
      "requiredContext": "Support contact settings, escalation policy, assignment permissions, evidence bundle requirements, and confirmation posture.",
      "ownerValue": "Reduces support chaos by turning AI failures or unresolved issues into structured, evidence-backed handoffs.",
      "implementationPattern": "Use Thinker evidence and Resolver policy to create confirmed support handoffs with audit and partner/merchant visibility.",
      "notFor": [
        "Replacing the merchant support desk",
        "Creating tickets without evidence or permission",
        "Handling emergencies or regulated support cases without human review"
      ],
      "exampleUseCases": [
        "Create a support escalation for a failed delivery question",
        "Bundle evidence for a partner to inspect a store issue",
        "Route refund questions to the merchant when action gates are missing"
      ]
    },
    {
      "code": "grounded_knowledge_assistant",
      "name": "Grounded knowledge assistant",
      "description": "LoomAI provides AI assistants that answer from indexed first-party knowledge rather than generic model memory.",
      "useWhen": "Use when a product, website, or internal tool needs AI answers grounded in its own documents, catalog, policies, or operational data.",
      "requiredContext": "Approved knowledge sources, source freshness expectations, answer style, access boundaries, and target user journeys.",
      "ownerValue": "Creates reliable AI experiences that can explain what they know and avoid unsupported claims.",
      "implementationPattern": "Connect approved sources, index them into the LoomAI knowledge layer, and run the assistant through a governed runtime.",
      "notFor": [
        "Answering from private data without authorization",
        "Replacing expert review in high-risk domains",
        "Using stale sources as if they are current"
      ],
      "exampleUseCases": [
        "Docs assistant for SaaS product documentation",
        "Internal knowledge assistant for employee questions",
        "Compliance FAQ assistant with cited policy sources"
      ]
    },
    {
      "code": "live_knowledge_indexing",
      "name": "Knowledge sync and freshness",
      "description": "LoomAI synchronizes selected business sources into AI-usable knowledge and exposes readiness, freshness, and recovery controls.",
      "useWhen": "Use when AI answers must stay aligned with changing product, content, policy, or operational data.",
      "requiredContext": "Source categories, create/update/delete event sources, sync cadence, indexing policy, and recovery expectations.",
      "ownerValue": "Prevents the AI from becoming stale and gives owners visibility into whether the AI knows current business content.",
      "implementationPattern": "Configure source categories, preflight checks, vectorization policies, webhook or scheduled sync, and readiness reporting.",
      "notFor": [
        "One-time ingestion with no freshness process",
        "Unapproved data crawling",
        "Guaranteeing real-time data when the source does not provide events"
      ],
      "exampleUseCases": [
        "Keep Shopify products and policies updated",
        "Reindex changed documentation pages",
        "Recover indexing health from merchant or operator UI"
      ]
    },
    {
      "code": "multi_source_rag_attribution",
      "name": "Multi-source retrieval and attribution",
      "description": "LoomAI retrieves relevant evidence across multiple source types and uses it to ground answers, recommendations, and action decisions.",
      "useWhen": "Use when answers need to combine product data, documents, policies, user context, and tool/read-action evidence.",
      "requiredContext": "Source taxonomy, user permissions, retrieval rules, citation needs, and acceptable answer boundaries.",
      "ownerValue": "Makes AI answers more trustworthy by connecting them to the business knowledge behind them.",
      "implementationPattern": "Use LoomAI retrieval, read-action evidence, post-action facts, and prompt controls to produce evidence-backed responses.",
      "notFor": [
        "Hallucinated citations",
        "Cross-tenant knowledge sharing",
        "Answering from sources the user is not allowed to access"
      ],
      "exampleUseCases": [
        "Answer a product question using catalog plus policy",
        "Diagnose an issue using logs, records, and documentation",
        "Generate a recommendation with explicit missing-evidence language"
      ]
    },
    {
      "code": "embedded_ai_ui_surfaces",
      "name": "Embedded AI UI surfaces",
      "description": "LoomAI provides embeddable assistant surfaces such as search docks, contextual pills, insight cards, FAQ blocks, comparison modules, and chat/depth panels.",
      "useWhen": "Use when AI should appear inside the user workflow rather than as a separate generic chatbot.",
      "requiredContext": "Target pages, placement rules, UI theme constraints, user journey, enabled capabilities, and fallback behavior.",
      "ownerValue": "Puts AI help exactly where users need it, improving adoption and usefulness.",
      "implementationPattern": "Embed LoomAI components through a storefront, website, or application shell and configure page-aware behavior.",
      "notFor": [
        "Purely decorative AI widgets",
        "Pages where AI would obstruct critical user tasks",
        "Unbranded overlays that ignore product UX"
      ],
      "exampleUseCases": [
        "AI search dock on a storefront",
        "Product insight block on a product page",
        "Contextual assistant inside a SaaS onboarding page"
      ]
    },
    {
      "code": "conversational_assistant_runtime",
      "name": "Conversational assistant runtime",
      "description": "LoomAI provides a runtime for chat, query-once answers, mode routing, suggestions, attachments, and structured action interactions.",
      "useWhen": "Use when a product needs a production AI conversation layer with controllable modes, private integration, and reusable UI.",
      "requiredContext": "Target users, conversation modes, runtime auth model, request context, allowed actions, and desired persistence behavior.",
      "ownerValue": "Gives product owners a production AI assistant foundation without building chat infrastructure from scratch.",
      "implementationPattern": "Integrate through LoomAI private runtime APIs, browser-safe surfaces, or product-specific bridge services.",
      "notFor": [
        "Unauthenticated access to private data",
        "Unbounded agent autonomy",
        "Persisting sensitive temporary content without explicit design"
      ],
      "exampleUseCases": [
        "One-time product opportunity analysis answer",
        "Persistent support diagnosis session",
        "Shopping assistant chat with product attachments"
      ]
    },
    {
      "code": "thinker_read_first_diagnosis",
      "name": "Read-first issue diagnosis",
      "description": "LoomAI Thinker records diagnostic sessions with evidence, recommendations, safe answers, resolution plans, and audit history.",
      "useWhen": "Use when the product needs AI to understand an issue, explain the evidence, and recommend next steps before any action is taken.",
      "requiredContext": "User question, relevant deployment/store/product context, allowed evidence sources, and diagnosis mode.",
      "ownerValue": "Separates analysis from execution so owners can trust the system before enabling actions.",
      "implementationPattern": "Create Thinker sessions from runtime answers or operator/partner workflows and attach evidence and resolution plans.",
      "notFor": [
        "Executing changes directly",
        "Unsupported semantic guesses without evidence",
        "Operator-only raw diagnostics exposed to end users"
      ],
      "exampleUseCases": [
        "Diagnose why an AI answer is missing evidence",
        "Analyze a shopper support issue before escalation",
        "Create a resolution plan from retrieved records"
      ]
    },
    {
      "code": "resolver_governed_resolution",
      "name": "Governed resolution workflow",
      "description": "LoomAI Resolver supports proposed actions, policy decisions, dry-runs, confirmations, execution records, and post-action evidence for approved low-risk workflows.",
      "useWhen": "Use when AI should move from recommendation to controlled action only after policy, simulation, and confirmation.",
      "requiredContext": "Action family, proposal parameters, policy controls, dry-run expectations, confirmation text, idempotency key, and rollback posture.",
      "ownerValue": "Lets product owners add AI-assisted resolution without giving the AI unchecked write access.",
      "implementationPattern": "Use governed proposal, dry-run, exact confirmation, execution, audit, and verification steps before state-changing work.",
      "notFor": [
        "High-risk autonomous writes",
        "Bypassing policy or dry-run",
        "Refund, cancellation, order edit, or payment actions unless specifically entitled and verified"
      ],
      "exampleUseCases": [
        "Create a support escalation after confirmed dry-run",
        "Run a non-mutating simulation before an approved workflow",
        "Audit action attempts and failures"
      ]
    },
    {
      "code": "action_confirmation_audit_controls",
      "name": "Confirmation, policy, and audit controls",
      "description": "LoomAI applies action allowlists, confirmation requirements, tenant and entitlement checks, rate limits, audit history, and fail-closed behavior.",
      "useWhen": "Use when the AI can call tools, perform actions, or access sensitive business workflows.",
      "requiredContext": "Allowed action families, user role, tenant binding, entitlement, confirmation posture, rate limits, and audit retention needs.",
      "ownerValue": "Makes AI action capability safer, reviewable, and commercially governable.",
      "implementationPattern": "Compile allowed capabilities into runtime catalogs, check policy before execution, require confirmation where needed, and record action evidence.",
      "notFor": [
        "Silent state changes",
        "Unreviewed runtime tool expansion",
        "Cross-tenant or cross-customer action access"
      ],
      "exampleUseCases": [
        "Require confirmation before cart update",
        "Audit support escalation attempts",
        "Disable blocked action classes for a deployment"
      ]
    },
    {
      "code": "mcp_capability_integration",
      "name": "MCP capability integration",
      "description": "LoomAI can consume MCP tools as governed product capabilities while keeping product truth in reviewed capability catalogs.",
      "useWhen": "Use when a product wants to connect to MCP-enabled systems without exposing raw tools directly to users.",
      "requiredContext": "MCP server endpoint, authentication, discovered tools, reviewed capability mapping, schemas, entitlement, and drift policy.",
      "ownerValue": "Connects modern MCP ecosystems to productized AI workflows without losing governance or stability.",
      "implementationPattern": "Discover MCP tools, import reviewed capability drafts, publish action plugins, compile runtime catalogs, and execute through governed MCP adapters.",
      "notFor": [
        "Letting tools/list automatically become user-visible capability",
        "Exposing raw MCP tool names as product UX",
        "Calling untrusted MCP servers without authentication and SSRF controls"
      ],
      "exampleUseCases": [
        "Use Shopify MCP for storefront catalog actions",
        "Connect a customer product's MCP tools to LoomAI runtime",
        "Detect MCP tool drift before production exposure"
      ]
    },
    {
      "code": "marketplace_capability_packaging",
      "name": "Capability packaging and entitlement",
      "description": "LoomAI packages actions, data sources, templates, and inference profiles into governed Marketplace capability bundles.",
      "useWhen": "Use when a product needs different capability sets by tier, deployment, customer, or product mode without duplicating action definitions.",
      "requiredContext": "Target product, package profile, installed capability bundles, required/optional capabilities, entitlement rules, and deployment target.",
      "ownerValue": "Lets owners configure what each product or customer can do without rewriting code for every capability change.",
      "implementationPattern": "Publish reviewed Marketplace plugins and use package profiles to decide which bundles are required, allowed, or disabled.",
      "notFor": [
        "Ad hoc action definitions spread across tiers and code",
        "Runtime capability changes outside review",
        "Customer-facing UX that exposes plugin internals"
      ],
      "exampleUseCases": [
        "Starter package gets read-only shopping capabilities",
        "Elite package gets governed action capabilities",
        "ProdUS deployment gets approved productization MCP actions"
      ]
    },
    {
      "code": "private_runtime_integration",
      "name": "Private runtime integration",
      "description": "LoomAI supports private, backend-mediated runtime integration so customer products can call AI safely without exposing runtime secrets to browsers.",
      "useWhen": "Use when an external product needs LoomAI AI answers or actions inside its own application while preserving tenant and auth boundaries.",
      "requiredContext": "Consumer/customer id, runtime assignment, issuer and audience policy, signed assertion model, target endpoints, and allowed modes.",
      "ownerValue": "Lets product owners embed LoomAI into their own product experience with secure, customer-scoped runtime access.",
      "implementationPattern": "Customer backend discovers assigned runtime, signs private assertions, and calls LoomAI query or action endpoints through the approved integration pattern.",
      "notFor": [
        "Browser-side private secrets",
        "Hardcoded deployment routing as permanent source of truth",
        "Unauthenticated direct runtime access"
      ],
      "exampleUseCases": [
        "ProdUS calls LoomAI for AI opportunity analysis",
        "A SaaS app embeds LoomAI query-once answers",
        "A customer product routes selected user context into a private AI runtime"
      ]
    },
    {
      "code": "managed_ai_runtime_deployment",
      "name": "Managed AI runtime deployment",
      "description": "LoomAI can provision, deploy, monitor, restart, and verify AI runtimes and companion services for customer or product deployments.",
      "useWhen": "Use when a product owner wants LoomAI to operate the AI runtime layer rather than self-hosting and wiring infrastructure manually.",
      "requiredContext": "Deployment template, environment, runtime configuration, provider target, secrets, vector strategy, and verification requirements.",
      "ownerValue": "Reduces infrastructure burden and gives the owner a repeatable path from setup to live AI service.",
      "implementationPattern": "Use LoomAI deployment control plane with managed provider targets, service templates, health checks, verification runs, and operations controls.",
      "notFor": [
        "Unmanaged one-off scripts with no verification",
        "Infrastructure claims without provider connectivity proof",
        "Customer secrets stored in documentation"
      ],
      "exampleUseCases": [
        "Deploy a staging AI runtime for a customer product",
        "Restart or verify a runtime service",
        "Operate vectorization runner and connector services"
      ]
    },
    {
      "code": "staging_production_release_flow",
      "name": "Staging-to-production release flow",
      "description": "LoomAI supports staging validation, evidence collection, merchant or owner approval, production promotion, rollback posture, and live verification.",
      "useWhen": "Use when AI capability should be tested safely before going live to customers or shoppers.",
      "requiredContext": "Staging target, production target, approval owner, verification gates, release evidence, and rollback/deactivation rules.",
      "ownerValue": "Makes AI rollout controlled and less founder-dependent by separating preview from production launch.",
      "implementationPattern": "Run staging deployment, collect evidence, request approval, promote to production target, verify live behavior, and retain rollback controls.",
      "notFor": [
        "Unreviewed direct production changes",
        "Skipping verification evidence",
        "Treating staging success as production proof without promotion test"
      ],
      "exampleUseCases": [
        "Merchant previews Shopify Companion before production",
        "ProdUS validates a LoomAI staging deployment before go-live",
        "Partner verifies a store and exports launch evidence"
      ]
    },
    {
      "code": "partner_merchant_enablement_portals",
      "name": "Partner and merchant self-service enablement",
      "description": "LoomAI provides self-service portal workflows for onboarding, approval, scoped access, configuration, evidence, launch readiness, and revocation.",
      "useWhen": "Use when partners, agencies, merchants, or customer teams need to manage AI setup without constant founder or operator intervention.",
      "requiredContext": "User roles, assigned stores or deployments, approval policy, configuration scope, verification requirements, and support handoff rules.",
      "ownerValue": "Turns AI deployment from a bespoke services task into a repeatable operating workflow.",
      "implementationPattern": "Use partner/merchant portals for scoped access, configuration, readiness checks, evidence exports, approval, support escalation, and revocation.",
      "notFor": [
        "Granting partners unrestricted cross-customer access",
        "Exposing raw secrets or operator identifiers",
        "Replacing legal or commercial approval where required"
      ],
      "exampleUseCases": [
        "Agency configures a merchant's Loom Companion staging store",
        "Merchant approves production launch",
        "Partner exports evidence for a design-partner review"
      ]
    },
    {
      "code": "analytics_roi_evidence",
      "name": "Usage, ROI, and evidence reporting",
      "description": "LoomAI surfaces AI usage, shopper journeys, question patterns, action attempts, strongest surfaces, readiness, and evidence bundles.",
      "useWhen": "Use when product owners need to evaluate whether AI is creating business value and where to improve it.",
      "requiredContext": "Tracked events, surfaces, conversations, action outcomes, source freshness, business goals, and reporting audience.",
      "ownerValue": "Helps owners make continue, price, pause, or expand decisions using evidence instead of vibes.",
      "implementationPattern": "Collect runtime, surface, action, and readiness signals and expose them through dashboards, exports, and support bundles.",
      "notFor": [
        "Full replacement for BI or financial attribution systems",
        "Claims of conversion lift without measurement design",
        "Sharing private user data outside approved scope"
      ],
      "exampleUseCases": [
        "Show common unanswered shopper questions",
        "Review action attempts and failures",
        "Export launch dossier or support bundle"
      ]
    },
    {
      "code": "transient_document_context",
      "name": "Temporary document context",
      "description": "LoomAI can use owner-approved temporary document URLs as runtime context without permanently indexing or persisting the document content.",
      "useWhen": "Use when an AI request needs selected files for one analysis but those files should not become permanent knowledge.",
      "requiredContext": "Temporary access URLs, document metadata, user approval, provider support for document usage evidence, and retention policy.",
      "ownerValue": "Allows richer AI analysis while keeping sensitive or one-off files out of permanent indexes.",
      "implementationPattern": "Pass temporary document context to a private runtime request, require provider document-usage evidence, redact URLs, and avoid persistence of file contents.",
      "notFor": [
        "Long-term knowledge sync",
        "Unapproved private file ingestion",
        "Providers that cannot prove document usage"
      ],
      "exampleUseCases": [
        "Analyze selected documents during product opportunity evaluation",
        "Use a temporary PDF or proposal in a one-time answer",
        "Compare owner-approved uploaded files without indexing them"
      ]
    },
    {
      "code": "multi_provider_ai_stack",
      "name": "Flexible AI provider stack",
      "description": "LoomAI supports configurable LLM, embedding, retrieval, and vector provider choices for different deployments and operating constraints.",
      "useWhen": "Use when a customer needs provider choice, bring-your-own model posture, cost control, or deployment-specific AI stack configuration.",
      "requiredContext": "Preferred LLM provider, embedding provider, vector strategy, credentials posture, model constraints, latency goals, and compliance boundaries.",
      "ownerValue": "Avoids lock-in and lets the AI stack match the customer's cost, quality, and governance needs.",
      "implementationPattern": "Configure provider profiles and runtime deployment settings, then verify provider connectivity and retrieval quality.",
      "notFor": [
        "Unlimited model switching without validation",
        "Using customer provider keys without clear ownership",
        "Claiming a provider is live before preflight and runtime verification"
      ],
      "exampleUseCases": [
        "Use OpenAI with managed vector search",
        "Switch embedding provider for cost or dimension compatibility",
        "Support BYO provider keys for an enterprise customer"
      ]
    },
    {
      "code": "custom_vertical_ai_productization",
      "name": "Custom vertical AI productization",
      "description": "LoomAI can productize AI assistants for verticals beyond Shopify by combining domain knowledge sources, embedded UI, actions, governance, and deployment operations.",
      "useWhen": "Use when a product owner wants to create a specialized AI assistant for a specific market rather than a generic chatbot.",
      "requiredContext": "Target vertical, user journeys, domain sources, required actions, risk boundaries, distribution channel, and operating model.",
      "ownerValue": "Turns an AI idea into a productized, governed, deployable AI application.",
      "implementationPattern": "Create a product-specific bridge/integration layer, source model, prompt posture, UI surfaces, capability bundles, and managed deployment.",
      "notFor": [
        "One-off demos with no product owner",
        "Domains requiring certification or legal approval without expert review",
        "Generic chatbot wrappers with no domain data"
      ],
      "exampleUseCases": [
        "Documentation assistant for a SaaS product",
        "Compliance knowledge assistant",
        "Internal knowledge assistant for operations teams"
      ]
    },
    {
      "code": "white_label_enterprise_ai_deployment",
      "name": "White-label or enterprise AI deployment",
      "description": "LoomAI can support branded or dedicated AI deployments for customers that need a custom product posture, private deployment model, or enterprise controls.",
      "useWhen": "Use when a customer has budget, governance requirements, or brand requirements that make a standard subscription insufficient.",
      "requiredContext": "Commercial scope, branding requirements, security constraints, deployment model, support expectations, and success metrics.",
      "ownerValue": "Lets larger customers use LoomAI capability under their own brand or operating model without rebuilding the platform.",
      "implementationPattern": "Package LoomAI runtime, portal, integrations, deployment, governance, and support into a scoped customer engagement.",
      "notFor": [
        "Small merchants better served by standard Loom Companion",
        "Undefined custom work without budget and scope",
        "Enterprise SLA promises not contractually agreed"
      ],
      "exampleUseCases": [
        "Agency-branded AI shopping assistant program",
        "Enterprise dedicated knowledge assistant deployment",
        "Custom AI enablement layer for a product portfolio"
      ]
    }
  ]
}
```

## Prompt Use Guidance

When ProdUS uses this taxonomy in an AI opportunity analysis prompt, instruct the model to:

- recommend LoomAI only when one or more capability `useWhen` fields match the customer's product opportunity
- explain the recommendation using the capability `name`, `description`, and `ownerValue`
- ask for missing `requiredContext` instead of inventing implementation details
- respect every `notFor` boundary
- avoid presenting ProdUS internal APIs, MCP tool names, deployment ids, auth mechanisms, or runtime endpoints as LoomAI product capabilities
- prefer simple owner-facing language such as "AI shopping companion", "grounded knowledge assistant", "governed actions", "private runtime integration", and "staging-to-production release flow"
