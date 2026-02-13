# Platform Integrations Strategy — Beyond Shopify

**Date:** 2026-02-13
**Context:** AI Fabric Framework productization — identifying the highest-value platform integrations to build after (or alongside) Shopify.

**Principle:** AI Fabric's connector/relay architecture means every integration follows the same pattern: define an action catalog (YAML), build a relay that wraps the platform's API, and optionally configure a retrieval connector for RAG. Curated packs provide domain-specific orchestration behavior on top.

---

## Why This Matters

Shopify is the right first integration — it proves the commerce vertical. But to be the best AI enablement platform on the planet, AI Fabric needs to demonstrate that the orchestration model works across verticals. Each new integration:
- Validates the domain-agnostic core thesis
- Adds a new curated pack (revenue unit)
- Expands the addressable market
- Reuses the same connector/relay/RAG infrastructure

The connector model means integration effort is bounded: define actions, build a relay, ship a pack. No changes to the core framework.

---

## Integration Architecture (How Every Platform Plugs In)

Every platform integration follows the same three-layer pattern:

```
Platform App / Plugin
  |
  |- Implements Customer Connector API:
  |    POST /actions/execute   (wraps platform API)
  |    POST /retrieval/search  (returns platform data for RAG)
  |
  |- Calls AI Fabric Ingestion API:
  |    POST /api/ai/data-sync/upsert  (push platform data for managed RAG)
  |    POST /api/ai/data-sync/delete
  |    POST /api/ai/data-sync/batch
  |
  |- Uses AI Fabric Orchestration API:
       POST /api/chat/query  (all chat/AI interactions)
```

This pattern is identical for Shopify, Salesforce, Zendesk, or any other platform.

---

## Tier 1: Build Next (Highest Strategic Value)

### 1. Zendesk — Customer Support

| Attribute | Detail |
|---|---|
| **Market** | Help desk market projected $35B by 2035; Zendesk holds ~15% share; 2,000+ marketplace apps |
| **Why now** | Customer support is the #1 proven AI use case in production today. AI that deflects tickets has direct, measurable ROI ($5-15 saved per deflected ticket). Zendesk Marketplace is a strong distribution channel. |
| **AI Fabric fit** | The orchestration pipeline maps directly to the support ticket lifecycle: extract intent from customer message, retrieve relevant KB articles via RAG, execute actions (create/update/escalate ticket), confirm with agent before mutations. PII redaction module is critical — support conversations contain sensitive data. |

**Action catalog (V1):**
```yaml
actions:
  - name: search_knowledge_base
    description: "Search help center articles for relevant answers"
    category: "support"
    accessMode: READ
    params:
      - name: query
        type: string
        required: true

  - name: create_ticket
    description: "Create a new support ticket"
    category: "support"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    confirmationMessage: "Create ticket: {{subject}}?"
    params:
      - name: subject
        type: string
        required: true
      - name: priority
        type: string
        allowedValues: [low, normal, high, urgent]
        required: true

  - name: update_ticket_status
    description: "Update the status of an existing ticket"
    category: "support"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    params:
      - name: ticketId
        type: string
        required: true
      - name: status
        type: string
        allowedValues: [open, pending, solved, closed]
        required: true

  - name: escalate_ticket
    description: "Escalate a ticket to a higher-tier support team"
    category: "support"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    confirmationMessage: "Escalate ticket #{{ticketId}} to {{targetGroup}}?"
    params:
      - name: ticketId
        type: string
        required: true
      - name: targetGroup
        type: string
        required: true
      - name: reason
        type: string
        required: true

  - name: add_internal_note
    description: "Add an internal note to a ticket (not visible to customer)"
    category: "support"
    accessMode: WRITE_ONLY
    requiresConfirmation: false
    params:
      - name: ticketId
        type: string
        required: true
      - name: note
        type: string
        required: true
```

**Retrieval connector:** Indexes Zendesk Guide (Help Center) articles as RAG source. Vector spaces: `article`, `ticket_template`, `macro`.

**Curated pack:** `support` with modes:
- `deflection_agent` — Self-service first, KB retrieval before human handoff
- `agent_assist` — Suggest responses, auto-classify incoming tickets
- `escalation_manager` — Routing + handoff rules + SLA awareness

**Integration complexity:** Medium. Zendesk REST API is mature. OAuth 2.0. Webhooks for real-time events. ~3-4 weeks to build.

**Revenue model:** Per-agent-seat licensing. Measurable ROI via ticket deflection rate.

---

### 2. WooCommerce — E-Commerce (WordPress)

| Attribute | Detail |
|---|---|
| **Market** | 33.4% of all e-commerce websites; 4.5M+ stores; WordPress powers 43% of all websites |
| **Why now** | Largest e-commerce platform by store count. Natural second commerce integration after Shopify. The existing `commerce` curated pack can be reused almost entirely. WordPress plugin ecosystem is massive (60,000+ plugins). Agency channel is strong. |
| **AI Fabric fit** | Identical to Shopify — product search via RAG, cart/checkout actions, policy retrieval. The self-hosted nature of WooCommerce means the relay can optionally run as a WordPress plugin (closer to the data) or as an external service. |

**Action catalog (V1):** Mirrors Shopify catalog with WooCommerce API specifics:
```yaml
actions:
  - name: search_products
    description: "Search products by keyword, category, or attributes"
    category: "commerce"
    accessMode: READ
    params:
      - name: query
        type: string
        required: true
      - name: category
        type: string
        required: false
      - name: maxResults
        type: integer
        required: false
        min: 1
        max: 20

  - name: get_product_details
    description: "Get detailed information about a specific product"
    category: "commerce"
    accessMode: READ
    params:
      - name: productId
        type: string
        required: true

  - name: get_order_status
    description: "Check the status of a customer order"
    category: "commerce"
    accessMode: READ
    params:
      - name: orderId
        type: string
        required: true
      - name: email
        type: string
        required: true
        sensitive: true

  - name: create_checkout_link
    description: "Generate a checkout link with selected products"
    category: "commerce"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    confirmationMessage: "Create checkout with {{itemCount}} items?"
    params:
      - name: items
        type: array
        required: true
      - name: couponCode
        type: string
        required: false
```

**Retrieval connector:** Indexes WooCommerce products, pages, and policies. Vector spaces: `product`, `page`, `policy`, `faq`.

**Curated pack:** Reuse existing `commerce` pack with WooCommerce-specific prompt tuning.

**Integration complexity:** Medium. WooCommerce REST API v3. OAuth 1.0a or API keys. ~3-4 weeks to build.

**Revenue model:** Volume play — lower per-store price than Shopify integration but 4.5M addressable stores. WordPress agency partnerships for distribution.

---

### 3. Salesforce — CRM / Sales / Service

| Attribute | Detail |
|---|---|
| **Market** | $113B CRM market; Salesforce holds 21-31% share; $37.9B revenue; 150,000+ customers; 83% of Fortune 500 |
| **Why now** | Largest CRM platform globally. AI Fabric's action system maps directly to Salesforce object mutations (create Lead, update Opportunity, close Case). Enterprise deals with high ACV. AppExchange is a proven distribution channel. |
| **AI Fabric fit** | The confirmation interceptor pattern is perfect for CRM: "Are you sure you want to update this $2M opportunity stage to Closed Won?" The RAG system indexes Salesforce Knowledge articles and record summaries. PII detection is critical when handling CRM data. |

**Action catalog (V1):**
```yaml
actions:
  - name: search_contacts
    description: "Search for contacts by name, email, or company"
    category: "crm"
    accessMode: READ
    params:
      - name: query
        type: string
        required: true

  - name: create_lead
    description: "Create a new lead in Salesforce"
    category: "crm"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    confirmationMessage: "Create lead for {{firstName}} {{lastName}} at {{company}}?"
    params:
      - name: firstName
        type: string
        required: true
      - name: lastName
        type: string
        required: true
      - name: company
        type: string
        required: true
      - name: email
        type: string
        required: true
        sensitive: true

  - name: update_opportunity_stage
    description: "Update the stage of a sales opportunity"
    category: "crm"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    confirmationMessage: "Move opportunity '{{opportunityName}}' to {{newStage}}?"
    params:
      - name: opportunityId
        type: string
        required: true
      - name: opportunityName
        type: string
        required: true
      - name: newStage
        type: string
        required: true

  - name: search_knowledge
    description: "Search Salesforce Knowledge articles"
    category: "crm"
    accessMode: READ
    params:
      - name: query
        type: string
        required: true

  - name: create_case
    description: "Create a new support case"
    category: "crm"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    params:
      - name: subject
        type: string
        required: true
      - name: description
        type: string
        required: true
      - name: priority
        type: string
        allowedValues: [Low, Medium, High, Critical]
        required: true
      - name: contactId
        type: string
        required: true
```

**Retrieval connector:** Indexes Salesforce Knowledge articles, record summaries (Accounts, Contacts), and custom object data. Vector spaces: `knowledge_article`, `account`, `contact`, `opportunity`.

**Curated pack:** `crm` with modes:
- `sales_copilot` — Pipeline insights, next-best-action suggestions, deal risk scoring
- `service_agent` — Case management, knowledge retrieval, escalation routing
- `admin_helper` — Report generation, data quality checks

**Integration complexity:** High. Salesforce REST API + SOQL. OAuth 2.0 JWT bearer. Complex object model. ~6-8 weeks to build.

**Revenue model:** Enterprise per-user licensing. High ACV. AppExchange distribution.

---

### 4. HubSpot — CRM / Marketing / Sales (Mid-Market)

| Attribute | Detail |
|---|---|
| **Market** | 5.6% CRM share; 248,000+ paying customers; dominant in SMB/mid-market |
| **Why now** | Sweet spot between enterprise complexity and SMB simplicity. HubSpot's own Breeze AI is still early — window for third-party AI. App Marketplace is strong. HubSpot customers want provider-agnostic AI (choose their own LLM). |
| **AI Fabric fit** | HubSpot's Marketing + Sales + Service Hubs create a unified platform where AI Fabric adds value across functions. Same `crm` curated pack as Salesforce with HubSpot-specific adaptations. |

**Action catalog (V1):**
```yaml
actions:
  - name: search_contacts
    description: "Search HubSpot contacts"
    category: "crm"
    accessMode: READ
    params:
      - name: query
        type: string
        required: true

  - name: create_contact
    description: "Create a new contact in HubSpot"
    category: "crm"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    params:
      - name: firstName
        type: string
        required: true
      - name: lastName
        type: string
        required: true
      - name: email
        type: string
        required: true
        sensitive: true

  - name: update_deal_stage
    description: "Update the stage of a deal in the pipeline"
    category: "crm"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    confirmationMessage: "Move deal '{{dealName}}' to {{stage}}?"
    params:
      - name: dealId
        type: string
        required: true
      - name: dealName
        type: string
        required: true
      - name: stage
        type: string
        required: true

  - name: create_ticket
    description: "Create a support ticket in HubSpot Service Hub"
    category: "crm"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    params:
      - name: subject
        type: string
        required: true
      - name: description
        type: string
        required: true
      - name: priority
        type: string
        allowedValues: [LOW, MEDIUM, HIGH]
        required: true

  - name: search_knowledge_base
    description: "Search HubSpot Knowledge Base articles"
    category: "crm"
    accessMode: READ
    params:
      - name: query
        type: string
        required: true
```

**Integration complexity:** Medium. HubSpot API v3 is modern and well-documented. OAuth 2.0. ~4-5 weeks.

**Revenue model:** Per-portal licensing. App Marketplace distribution.

---

## Tier 2: Build in Q2-Q3 (High Value, Strong Distribution)

### 5. Slack — Workplace AI

| Attribute | Detail |
|---|---|
| **Market** | 40-48M DAU; 596,000+ companies; 80% of Fortune 100 |
| **Why** | Distribution play — put AI Fabric inside the tool people use daily. Chat session model maps naturally to Slack threads. Salesforce ownership creates cross-sell with the CRM integration. |
| **Actions** | `send_message`, `search_messages`, `summarize_thread`, `create_channel`, `set_reminder` |
| **RAG** | Index Slack channel history + pinned messages as knowledge base |
| **Pack** | `workplace` — modes: `channel_assistant`, `knowledge_finder`, `standup_bot` |
| **Complexity** | Medium. Slack Bolt SDK. ~4 weeks. |

### 6. Microsoft Teams — Enterprise Workplace AI

| Attribute | Detail |
|---|---|
| **Market** | 320M+ users; 38% collaboration market share |
| **Why** | Massive enterprise user base. Microsoft Copilot costs $30/user/month — AI Fabric can offer targeted, cost-effective AI. SharePoint/OneDrive as RAG sources. |
| **Actions** | `send_message`, `create_meeting`, `search_sharepoint`, `create_planner_task`, `get_email_summary` |
| **RAG** | Index SharePoint sites + OneDrive documents |
| **Pack** | `workplace` (shared with Slack) — modes: `meeting_assistant`, `document_finder`, `task_manager` |
| **Complexity** | High. Microsoft Graph API + Azure AD. ~6-8 weeks. |

### 7. Intercom — Conversational Support

| Attribute | Detail |
|---|---|
| **Market** | 12.8% customer experience market share; conversational-first; SaaS customer base |
| **Why** | Intercom's conversational model is perfectly aligned with AI Fabric's orchestration pipeline. Same `support` pack as Zendesk. SaaS companies (Intercom's customers) are early AI adopters. |
| **Actions** | `search_conversations`, `reply_to_conversation`, `create_ticket`, `tag_conversation`, `assign_to_team` |
| **RAG** | Index Intercom Articles (Help Center) |
| **Pack** | `support` (shared with Zendesk) — modes: `conversational_agent`, `product_guide`, `onboarding_assistant` |
| **Complexity** | Medium. Intercom REST API v2. ~3-4 weeks. |

### 8. Stripe — Payments (Completes Commerce Stack)

| Attribute | Detail |
|---|---|
| **Market** | 21-29% global online payment processing; $91.5B valuation; 80% of largest U.S. software companies |
| **Why** | Completes the commerce stack: products (Shopify/WooCommerce) + payments (Stripe) + marketing (Klaviyo). AI Fabric's idempotency model (`act_{ulid}`) maps directly to Stripe's idempotency keys. The governance module is critical for financial operations. |
| **Actions** | `check_payment_status`, `issue_refund`, `cancel_subscription`, `create_payment_link`, `search_invoices` |
| **Pack** | Extend `commerce` pack with payment actions and financial governance rules |
| **Complexity** | Medium. Stripe API is best-documented API in the industry. ~3 weeks. |

---

## Tier 3: Build on Demand / Partner-Driven

### 9. ServiceNow — Enterprise ITSM
- **Market:** ~$10B+ revenue; dominant in enterprise IT operations
- **Actions:** `create_incident`, `update_incident_priority`, `assign_to_group`, `search_knowledge_base`, `approve_request`
- **Pack:** `itsm` — modes: `incident_responder`, `knowledge_assistant`, `change_advisor`
- **Why wait:** Very high complexity, long enterprise sales cycles

### 10. Jira / Atlassian — Developer Tools
- **Market:** 65%+ market share in issue tracking; 300,000+ customers
- **Actions:** `create_issue`, `update_issue_status`, `search_issues`, `add_comment`, `get_sprint_summary`
- **Pack:** `devops` — modes: `issue_assistant`, `sprint_analyst`, `knowledge_finder`
- **Why wait:** Developer tools market is competitive (GitHub Copilot, Cursor, etc.)

### 11. Freshdesk — Budget Support
- **Market:** 3.1% customer experience share; 37,000+ companies
- **Actions:** Same as Zendesk with Freshdesk API specifics
- **Pack:** Reuse `support` pack with Freshdesk adaptations
- **Why wait:** Build once Zendesk pack is proven; minimal incremental effort

### 12. Adobe Commerce (Magento) — Enterprise E-Commerce
- **Market:** 9.2% e-commerce share; 125,000+ stores
- **Actions:** Same as WooCommerce with Magento API specifics
- **Pack:** Reuse `commerce` pack with Magento-specific prompt tuning
- **Why wait:** Enterprise-focused, longer integration cycles

### 13. Klaviyo — E-Commerce Marketing
- **Market:** 10.5% marketing automation share; 200,000+ customers
- **Actions:** `search_segments`, `get_campaign_performance`, `create_segment`, `trigger_flow`
- **Pack:** `commerce-marketing` — modes: `campaign_analyst`, `segmentation_assistant`
- **Why wait:** Best value when paired with Shopify/WooCommerce connectors

---

## Tier 4: Strategic / High-Regulation Verticals

### 14. Epic Systems — Healthcare / EHR
- **Market:** 37-41% acute care EHR share; 280M+ patients worldwide; $31.7B EHR market
- **Actions:** `search_patients`, `get_patient_summary`, `check_drug_interactions`, `schedule_appointment`
- **Pack:** `healthcare` — modes: `clinical_assistant`, `admin_assistant`, `compliance_auditor`
- **Why strategic:** Highest value per deal but requires HIPAA compliance, BAA, SOC 2, Epic App Orchard certification. 12-24 month sales cycles.
- **AI Fabric advantage:** PII detection module + governance module + fail-closed security model are directly applicable.

### 15. SAP / Oracle NetSuite — ERP
- **Market:** $72.6B ERP market; SAP 141,000+ customers; NetSuite 41,000+ customers
- **Actions:** `check_inventory`, `get_order_status`, `approve_purchase_order`, `search_vendors`
- **Pack:** `erp` — modes: `procurement_assistant`, `inventory_manager`, `financial_analyst`
- **Why strategic:** Massive market but extreme customization variance between implementations. SAP's S/4HANA migration (ECC support ending 2027) creates a window for AI-assisted ERP tools.

---

## Curated Pack Reuse Matrix

The key insight: 8 curated packs cover 15+ platform integrations.

| Pack | Platforms | Modes | Reuse Level |
|---|---|---|---|
| `commerce` (exists) | Shopify, WooCommerce, Magento, Stripe, Klaviyo | `navigator`, `executor`, `cart_assistant`, `payment_assistant` | High — same commerce UX semantics |
| `support` (new) | Zendesk, Freshdesk, Intercom | `deflection_agent`, `agent_assist`, `escalation_manager` | High — same support lifecycle |
| `crm` (new) | Salesforce, HubSpot | `sales_copilot`, `service_agent`, `admin_helper` | Medium — CRM models differ but intent patterns are similar |
| `workplace` (new) | Slack, Microsoft Teams, Notion, Airtable | `channel_assistant`, `knowledge_finder`, `task_manager` | High — same collaboration patterns |
| `devops` (new) | Jira, GitHub | `issue_assistant`, `sprint_analyst`, `pr_reviewer` | Medium — different dev workflows |
| `itsm` (new) | ServiceNow | `incident_responder`, `knowledge_assistant`, `change_advisor` | Low — specialized ITSM semantics |
| `healthcare` (new) | Epic, Cerner/Oracle Health | `clinical_assistant`, `admin_assistant` | Low — highly specialized + regulated |
| `erp` (new) | SAP, NetSuite | `procurement_assistant`, `inventory_manager` | Low — deeply customized per implementation |

---

## Recommended Build Roadmap

### Phase 1: Foundation Verticals (Now — Q1 2026)
**Goal:** Prove the model works across three verticals (commerce, support, CRM)

| Platform | Pack | Est. Effort | Revenue Type |
|---|---|---|---|
| Shopify (in progress) | `commerce` | 6-8 weeks | Per-store |
| **Zendesk** | `support` | 3-4 weeks | Per-agent-seat |
| **WooCommerce** | `commerce` (reuse) | 3-4 weeks | Per-store |

**Why this combination:** Three different verticals, two different revenue models, demonstrates domain-agnostic thesis. Zendesk is the fastest path to provable ROI (ticket deflection is measurable).

### Phase 2: Scale & Distribution (Q2 2026)
**Goal:** Hit the largest CRM market + communication platforms for distribution

| Platform | Pack | Est. Effort | Revenue Type |
|---|---|---|---|
| **Salesforce** | `crm` | 6-8 weeks | Per-user enterprise |
| **HubSpot** | `crm` (reuse) | 4-5 weeks | Per-portal |
| **Slack** | `workplace` | 4 weeks | Per-workspace |

### Phase 3: Complete the Stack (Q3 2026)
**Goal:** Fill vertical gaps, complete the commerce ecosystem

| Platform | Pack | Est. Effort | Revenue Type |
|---|---|---|---|
| **Intercom** | `support` (reuse) | 3-4 weeks | Per-seat |
| **Stripe** | `commerce` (extend) | 3 weeks | Bundled with commerce |
| **Microsoft Teams** | `workplace` (reuse) | 6-8 weeks | Per-user enterprise |

### Phase 4: Enterprise & Emerging (Q4 2026+)
**Goal:** High-value enterprise verticals + developer tools

| Platform | Pack | Est. Effort | Revenue Type |
|---|---|---|---|
| ServiceNow | `itsm` | 6-8 weeks | Enterprise license |
| Jira/Atlassian | `devops` | 4-5 weeks | Per-user |
| Freshdesk | `support` (reuse) | 2-3 weeks | Per-agent |
| Klaviyo | `commerce` (extend) | 3-4 weeks | Bundled |

### Phase 5: Strategic Verticals (2027)
| Platform | Pack | Est. Effort | Revenue Type |
|---|---|---|---|
| Epic Systems | `healthcare` | 12+ weeks + certification | Enterprise license |
| SAP/NetSuite | `erp` | 12+ weeks | Enterprise license |

---

## The Math: Why This Roadmap Works

**AI Fabric's connector model makes integration effort predictable:**
- Define action catalog YAML: 1-2 days
- Build relay service wrapping platform API: 1-2 weeks
- Configure retrieval connector for RAG: 2-3 days
- Adapt/create curated pack: 2-3 days
- Testing + hardening: 1-2 weeks

**Total per integration: 3-8 weeks** depending on platform API complexity.

**Curated pack reuse cuts effort by 40-60%** for same-vertical platforms (e.g., Freshdesk after Zendesk, WooCommerce after Shopify, HubSpot after Salesforce).

**By end of Q3 2026:** 8 platform integrations across 4 verticals, powered by 4 curated packs, all using the same core framework. That is a platform, not a point solution.

---

## The Competitive Moat

When you have 8+ integrations all using the same orchestration pipeline, action model, and confirmation semantics:
1. **Customers can mix integrations** — Salesforce CRM + Zendesk support + Shopify commerce, all orchestrated by one AI platform
2. **Cross-platform actions become possible** — "When a Zendesk ticket mentions a product, search Shopify inventory and update the Salesforce opportunity"
3. **One pack marketplace serves all platforms** — A `support` pack works for Zendesk, Freshdesk, and Intercom
4. **Provider-agnostic LLM** means customers aren't locked into any vendor's built-in AI

That is the "orchestra of AI" vision realized across verticals. No competitor offers this today.
