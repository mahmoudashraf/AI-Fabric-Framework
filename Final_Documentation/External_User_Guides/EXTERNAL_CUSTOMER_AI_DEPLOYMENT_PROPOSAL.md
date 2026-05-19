# External Customer Proposal: AI Enablement For Existing Web Applications

Status: customer-facing proposal draft
Audience: founders, product leaders, and engineering teams with an existing web application
Positioning: use LoomAI deployment infrastructure to add governed AI capabilities without rebuilding the customer product

---

## Executive Summary

LoomAI helps software companies add production-grade AI to an existing web application.

The customer does not need to rebuild their product as an AI-native platform. A traditional web application can expose selected data and actions through APIs or an MCP server, and LoomAI can deploy the AI layer around those capabilities: assistant UI, retrieval, governed actions, approval flows, audit, observability, staging, production rollout, and rollback.

The result is a controlled AI experience that can answer questions, retrieve customer-specific information, guide users through workflows, and execute approved actions through the customer application's existing business logic.

---

## The Customer Problem

Many teams want AI in their product, but the hard part is not calling an LLM. The hard parts are:

- connecting AI to live product data safely
- deciding which actions the AI is allowed to take
- handling authentication and user-owned resources
- keeping sensitive data tenant-scoped
- testing quality, reliability, and speed
- giving operators evidence when something goes wrong
- deploying safely to staging and production
- rolling back without breaking the main application

Most existing web apps already contain the needed business logic. They need an AI deployment layer that can use it safely.

---

## Proposed Solution

LoomAI deploys a governed AI layer beside the customer's existing application.

The customer application exposes selected capabilities through one of three integration paths:

1. **Existing API integration**
   LoomAI connects to the customer's current REST, GraphQL, or internal service APIs.

2. **MCP server integration**
   The customer exposes product capabilities as MCP tools. LoomAI discovers, governs, tests, and deploys those tools as AI actions.

3. **Hybrid integration**
   LoomAI wraps existing APIs into an MCP-compatible capability layer where that is faster than changing the core application.

MCP is not required to be visible to end users. It is an integration contract between the application and the AI deployment layer.

---

## Technical Integration

LoomAI integrates beside the customer's application. The customer keeps ownership of their existing product, database, authentication, and business rules. LoomAI provides the AI runtime, assistant surface, capability governance, and deployment operations.

### High-Level Architecture

```text
Customer Web App
  |
  | embeds assistant UI or calls LoomAI API
  v
LoomAI Assistant Surface
  |
  | sends chat request with page/session context
  v
LoomAI Runtime
  |
  | retrieves approved knowledge
  | selects governed capabilities
  | asks for confirmation when required
  v
LoomAI Capability Gateway
  |
  | calls MCP tools or customer APIs
  v
Customer Application APIs / MCP Server
  |
  | enforces customer business logic and permissions
  v
Customer System Of Record
```

The customer system remains the source of truth. LoomAI does not replace the customer's application backend.

### Integration Paths

#### 1. MCP Server

The customer exposes selected application capabilities as MCP tools.

Typical MCP tools:

- `search_knowledge_base`
- `get_current_user_profile`
- `get_current_user_orders`
- `create_support_ticket`
- `update_subscription_plan`
- `request_refund`

LoomAI uses MCP discovery to inspect available tools, then imports approved tools into a governed capability catalog. Runtime execution uses `tools/call` through the LoomAI capability gateway, not direct prompt-side calls.

#### 2. Existing API

If the customer already has stable APIs, LoomAI can call them directly through configured connectors.

Supported patterns:

- REST endpoints
- GraphQL endpoints
- internal service endpoints exposed through a secure gateway
- webhook-style action endpoints

The API contract should return structured success and failure responses. LoomAI normalizes those responses into safe user answers and operator evidence.

#### 3. API-To-MCP Wrapper

If MCP is desired but the customer does not want to modify the main application, LoomAI or the customer can deploy a small adapter service.

The adapter translates:

```text
MCP tools/list -> approved customer API capabilities
MCP tools/call -> customer API request
Customer API response -> MCP tool result
```

This keeps the customer application unchanged while still giving LoomAI a standard MCP capability contract.

### Assistant Embedding

The assistant can be introduced in one of three ways:

1. **JavaScript embed**
   The customer's web app loads a LoomAI assistant bundle and passes page/session context.

2. **Hosted assistant page**
   LoomAI hosts the assistant experience and the customer's app links users into it.

3. **Native product integration**
   The customer calls LoomAI APIs from their own UI and renders messages in their existing design system.

The first deployment usually starts with a JavaScript embed because it is the fastest path to staging proof.

### Runtime Request Flow

For a normal assistant request:

1. User asks a question in the customer product.
2. Customer app or embed sends the message to LoomAI with safe page context.
3. LoomAI resolves tenant, environment, user session, and enabled package.
4. LoomAI retrieves approved context from configured knowledge sources.
5. The LLM decides whether to answer directly or use a governed capability.
6. If a capability is needed, LoomAI validates required parameters.
7. If the action is sensitive, LoomAI requests confirmation.
8. LoomAI calls the customer's MCP tool or API through the gateway.
9. LoomAI maps the result into:
   - user-safe answer
   - structured action evidence
   - audit event
   - operator diagnostics, if needed

No customer secret or raw protected data is exposed to the prompt unless it is explicitly approved as safe runtime context.

### Authentication And User-Owned Resources

For user-owned data, LoomAI does not guess ownership from text.

The customer provides one of these auth models:

- signed user session token
- OAuth / OIDC delegated token
- short-lived customer access token
- backend token exchange endpoint
- server-to-server API with explicit user ownership claims

LoomAI stores only the minimum session material needed for the integration, preferably short-lived tokens or encrypted references. When a user asks about their own resources, the runtime resolves parameters from authenticated session context first, not from broad user data stuffed into the prompt.

Example:

```text
User asks: "Show my latest invoice."

Runtime does not ask the LLM to infer the user id.
Runtime resolves:
  tenant_id -> deployment context
  user_id -> authenticated session
  invoice_id -> optional user input or latest-owned-resource lookup

Then it calls:
  get_current_user_invoices(user_id=<session user>, limit=1)
```

If user ownership cannot be proven, the action fails closed with a safe message.

### Capability Governance

Every AI-accessible capability is registered with policy metadata:

- read or write
- required authentication
- required owner scope
- required inputs
- input resolution sources
- confirmation requirement
- risk level
- allowed environments
- package/tier availability
- audit requirements
- safe failure mappings

The LLM can propose an action, but LoomAI controls whether that action is allowed to execute.

### Data And Retrieval

LoomAI can index approved customer content into a tenant-scoped retrieval store.

Typical sources:

- public docs
- help center articles
- product catalog
- policy pages
- support macros
- selected database exports
- API-sourced records approved for retrieval

For sensitive or user-owned records, LoomAI prefers live API/MCP lookup over bulk indexing. This avoids overfilling prompts with personal data and keeps customer-owned data under the customer's authorization rules.

### Environments

Each integration should have separate staging and production configuration:

```text
staging customer app -> staging LoomAI deployment -> staging MCP/API endpoints
production customer app -> production LoomAI deployment -> production MCP/API endpoints
```

Environment-specific values:

- customer app URL
- assistant embed URL
- MCP server URL
- API base URL
- OAuth redirect URI
- token broker endpoint
- API credentials
- webhook secrets
- allowed origins
- rollout cohort

Production is not promoted by copying staging secrets. It is approved through a managed production profile.

### Observability And Evidence

Each governed action captures:

- tenant and environment
- user/session reference
- selected capability
- normalized inputs
- confirmation state
- execution result
- safe user message
- operator-safe diagnostics
- timing and retry metadata

This lets support teams answer: what did the assistant do, why did it do it, and what system produced the result?

### Deployment Communication Contract

Each customer deployment has three communication surfaces:

1. **User-facing assistant runtime**
   The customer web app or embedded assistant sends chat and suggestion requests to the LoomAI runtime deployment.

2. **Knowledge indexing and retrieval**
   Shared product knowledge can be pushed into the LoomAI runtime index. Customer-owned or sensitive records should stay behind the customer system and be fetched live through retrieval, MCP, or API calls.

3. **Governed capability execution**
   LoomAI calls approved MCP tools or customer APIs through the capability gateway. The customer application still enforces business rules and ownership.

Exact public hostnames are assigned per staging or production deployment. The path contract below is the standard deployment contract. A Platform-managed deployment can also expose these through a deployment-specific Platform proxy during staging verification.

```text
Customer browser/app
  -> https://<loom-runtime-domain>/api/public/chat/session
  -> https://<loom-runtime-domain>/api/chat/me/query
  -> https://<loom-runtime-domain>/api/chat/me/suggestions

Customer backend or data sync job
  -> https://<loom-runtime-domain>/api/ai/data-sync/upsert
  -> https://<loom-runtime-domain>/api/ai/data-sync/batch
  -> https://<loom-runtime-domain>/api/ai/data-sync/delete

LoomAI runtime/gateway
  -> https://<customer-connector-domain>/retrieval/search
  -> https://<customer-connector-domain>/actions/execute
  -> https://<customer-mcp-domain>/mcp

LoomAI operator/release gate
  -> https://<loom-runtime-domain>/api/admin/indexing/overview
  -> https://<loom-runtime-domain>/api/admin/indexing/vectors
```

#### Runtime Flow Sequences

Chat with indexed retrieval:

```text
1. Customer app loads assistant UI.
2. Assistant calls LoomAI /api/public/chat/session and receives a scoped runtime token.
3. User asks a question.
4. Assistant calls LoomAI /api/chat/me/query with query, conversationId, mode, position, and safe page attachments.
5. LoomAI retrieves approved tenant-scoped indexed knowledge.
6. LoomAI generates a grounded answer and returns user-safe evidence metadata.
```

Chat with customer-owned live retrieval:

```text
1. User asks about data that should remain in the customer system.
2. LoomAI validates the runtime auth context and retrieval policy.
3. LoomAI calls the customer connector /retrieval/search with trace.authContext.
4. Customer connector enforces ACLs and returns only documents visible to that user/session.
5. LoomAI uses the returned documents as one-turn evidence and answers safely.
```

Chat with governed action:

```text
1. User asks to perform or inspect a business operation.
2. LoomAI selects an approved catalog capability.
3. LoomAI resolves action inputs from session, page context, explicit user input, retrieval, or previous action result.
4. If required, LoomAI asks for confirmation before execution.
5. LoomAI calls customer /actions/execute or MCP tools/call through the gateway.
6. LoomAI normalizes the result into a user-safe response, evidence, and audit event.
```

Indexing:

```text
1. Customer backend export, webhook worker, or sync job reads approved shared-knowledge records.
2. Customer backend calls LoomAI /api/ai/data-sync/upsert, /batch, or /delete with trusted backend auth.
3. LoomAI validates tenant/deployment scope and indexes records into the configured vector space.
4. Release/support checks use /api/admin/indexing/overview to confirm coverage.
```

#### Endpoint Summary

| Direction | Owner | Endpoint | Purpose | Auth |
| --- | --- | --- | --- | --- |
| Customer browser -> LoomAI | LoomAI runtime | `POST /api/public/chat/session` | Bootstrap a scoped runtime session for the assistant UI | allowed origin plus public bootstrap policy |
| Customer browser -> LoomAI | LoomAI runtime | `POST /api/chat/me/query` | Send a chat turn to the assistant | runtime bearer token or verified customer session |
| Customer browser -> LoomAI | LoomAI runtime | `POST /api/chat/me/suggestions` | Generate contextual starter prompts | runtime bearer token or verified customer session |
| Customer backend -> LoomAI | LoomAI runtime | `POST /api/ai/data-sync/upsert` | Upsert one approved shared-knowledge record | trusted backend key or private network auth |
| Customer backend -> LoomAI | LoomAI runtime | `POST /api/ai/data-sync/batch` | Batch upsert/delete approved shared-knowledge records | trusted backend key or private network auth |
| Customer backend -> LoomAI | LoomAI runtime | `POST /api/ai/data-sync/delete` | Remove one indexed record | trusted backend key or private network auth |
| LoomAI -> Customer | Customer connector | `POST /retrieval/search` | Customer-owned retrieval without copying source data into LoomAI | connector API key, HMAC, mTLS, or private network auth |
| LoomAI -> Customer | Customer connector | `POST /actions/execute` | Execute a non-MCP configured customer action | connector API key, HMAC, mTLS, or private network auth |
| LoomAI -> Customer | Customer MCP server | `POST /mcp` | MCP `initialize`, `tools/list`, and `tools/call` | OAuth token, service token, mTLS, or private network auth |
| LoomAI operator -> LoomAI | LoomAI runtime | `GET /api/admin/indexing/overview` | Verify vector/indexing health | operator/trusted backend scope |
| LoomAI operator -> LoomAI | LoomAI runtime | `GET /api/admin/indexing/vectors` | Inspect indexed vectors during support/debug | operator/trusted backend scope |

For Platform-hosted staging verification, the Platform can proxy chat to a specific deployment using:

```text
POST /api/deployments/{deploymentId}/poc-widget/chat/me/query?authPath=PLATFORM_PRIVATE
```

That proxy is an operator/staging convenience. The customer product should integrate with the runtime deployment URL, not with provider internals.

#### Assistant Session Bootstrap

The embedded assistant first obtains a short-lived runtime token.

```http
POST /api/public/chat/session
Origin: https://app.customer.example
Content-Type: application/json
```

Request:

```json
{}
```

The bootstrap request body is intentionally empty in the current runtime contract. Page context is passed with chat turns through `attachments`, `position`, and `mode`, where it can be audited with the user request.

Response:

```json
{
  "success": true,
  "tokenScheme": "Bearer",
  "token": "<short-lived-runtime-token>",
  "authMode": "PUBLIC_RUNTIME",
  "subjectType": "ANONYMOUS_SESSION",
  "sessionId": "sess_01HX...",
  "deploymentId": "dep_customer_staging",
  "customerId": "customer_acme",
  "tenantId": "tenant_acme",
  "grantedScopes": ["chat:query", "chat:suggestions"],
  "expiresAt": "2026-05-17T12:30:00Z",
  "shellConfig": {
    "success": true,
    "starterPrompts": []
  }
}
```

If the customer app has authenticated users, the deployment can replace anonymous bootstrap with a verified session token or token exchange. In that mode, the returned auth context uses the customer's verified user identity instead of an anonymous session.

#### Chat Query

```http
POST /api/chat/me/query
Authorization: Bearer <runtime-token-or-verified-session-token>
Content-Type: application/json
```

Request:

```json
{
  "query": "Show my latest invoice",
  "conversationId": "chat_01HX...",
  "position": "billing",
  "mode": "executor",
  "attachments": [
    {
      "id": "invoice_summary_panel",
      "vectorSpace": "page_context",
      "source": "page",
      "contentText": "The user is viewing Billing > Invoices.",
      "metadata": {
        "pageType": "billing",
        "entityType": "invoice"
      }
    }
  ]
}
```

Response:

```json
{
  "success": true,
  "conversationId": "chat_01HX...",
  "sessionId": "sess_01HX...",
  "authContext": {
    "subjectId": "user_123",
    "subjectType": "CUSTOMER_USER",
    "authMode": "VERIFIED_SESSION",
    "deploymentId": "dep_customer_staging",
    "customerId": "customer_acme",
    "tenantId": "tenant_acme",
    "grantedScopes": ["chat:query"]
  },
  "result": {
    "type": "INFORMATION_PROVIDED",
    "success": true,
    "message": "Your latest invoice is INV-1042 for 280.00 USD, due May 31.",
    "data": {
      "invoiceId": "inv_1042",
      "status": "open"
    },
    "metadata": {
      "evidence": [
        {
          "type": "action",
          "actionId": "get_current_user_invoices",
          "source": "customer_mcp",
          "ownedResource": true
        }
      ]
    }
  }
}
```

The chat request intentionally stays small. It should carry page context and selected attachments, not a dump of all customer-owned records. User-owned data is fetched live only when the authenticated session and action policy allow it.

#### Chat Suggestions

```http
POST /api/chat/me/suggestions
Authorization: Bearer <runtime-token-or-verified-session-token>
Content-Type: application/json
```

Request:

```json
{
  "content": "User is on Billing > Invoices and may need account, invoice, or payment help.",
  "maxSuggestions": 4,
  "attachments": [
    {
      "id": "billing_page",
      "vectorSpace": "page_context",
      "source": "page",
      "contentText": "The user is viewing Billing > Invoices.",
      "metadata": {
        "pageType": "billing"
      }
    }
  ]
}
```

Response:

```json
{
  "success": true,
  "suggestions": [
    "What does my current plan include?",
    "Show my latest invoice",
    "How do I update payment details?",
    "Can I export my billing history?"
  ]
}
```

#### LoomAI-Managed Indexing

Use indexing for shared knowledge that can safely live in a tenant-scoped LoomAI vector store, such as help docs, policy pages, product docs, public catalog records, and support macros. Do not use indexing as the primary mechanism for sensitive user-owned records.

Single upsert:

```http
POST /api/ai/data-sync/upsert
Authorization: Bearer <trusted-backend-token>
Content-Type: application/json
```

```json
{
  "vectorSpace": "help_articles",
  "id": "article_refunds",
  "content": "Refunds are reviewed within five business days...",
  "metadata": {
    "title": "Refund policy",
    "url": "https://app.customer.example/help/refunds",
    "locale": "en-US",
    "visibility": "public",
    "updatedAt": "2026-05-17T10:00:00Z"
  },
  "identity": {
    "sourceRecordId": "article_refunds",
    "sourceRecordVersion": "2026-05-17T10:00:00Z",
    "contentFingerprint": "sha256:..."
  },
  "trace": {
    "requestId": "sync_01HX_refunds",
    "metadata": {
      "syncMode": "INCREMENTAL",
      "source": "help-center"
    },
    "authContext": {
      "subjectId": "sync_job",
      "subjectType": "SERVICE_ACCOUNT",
      "authMode": "TRUSTED_BACKEND",
      "callerType": "CUSTOMER_BACKEND",
      "deploymentId": "dep_customer_staging",
      "customerId": "customer_acme",
      "tenantId": "tenant_acme",
      "grantedScopes": ["data-sync:write"]
    }
  }
}
```

Batch sync:

```http
POST /api/ai/data-sync/batch
Authorization: Bearer <trusted-backend-token>
Content-Type: application/json
```

```json
{
  "trace": {
    "requestId": "sync_01HX_full",
    "metadata": {
      "syncMode": "FULL",
      "source": "docs-export"
    },
    "authContext": {
      "subjectId": "sync_job",
      "subjectType": "SERVICE_ACCOUNT",
      "authMode": "TRUSTED_BACKEND",
      "callerType": "CUSTOMER_BACKEND",
      "deploymentId": "dep_customer_staging",
      "customerId": "customer_acme",
      "tenantId": "tenant_acme",
      "grantedScopes": ["data-sync:write"]
    }
  },
  "operations": [
    {
      "type": "UPSERT",
      "vectorSpace": "help_articles",
      "id": "article_refunds",
      "content": "Refunds are reviewed within five business days...",
      "metadata": {
        "title": "Refund policy",
        "url": "https://app.customer.example/help/refunds"
      },
      "identity": {
        "sourceRecordId": "article_refunds",
        "sourceRecordVersion": "v17"
      }
    },
    {
      "type": "DELETE",
      "vectorSpace": "help_articles",
      "id": "article_old_policy"
    }
  ]
}
```

Delete:

```http
POST /api/ai/data-sync/delete
Authorization: Bearer <trusted-backend-token>
Content-Type: application/json
```

```json
{
  "vectorSpace": "help_articles",
  "id": "article_old_policy",
  "trace": {
    "requestId": "sync_01HX_delete_old_policy",
    "authContext": {
      "subjectId": "sync_job",
      "subjectType": "SERVICE_ACCOUNT",
      "authMode": "TRUSTED_BACKEND",
      "deploymentId": "dep_customer_staging",
      "customerId": "customer_acme",
      "tenantId": "tenant_acme",
      "grantedScopes": ["data-sync:write"]
    }
  }
}
```

Indexing verification:

```http
GET /api/admin/indexing/overview
Authorization: Bearer <operator-or-trusted-backend-token>
```

```json
{
  "success": true,
  "vectorDb": "PineconeVectorDatabaseService",
  "supportsVectorScan": true,
  "entityTypes": ["help_articles", "policies"],
  "countsByEntityType": {
    "help_articles": 128,
    "policies": 12
  },
  "totalVectors": 140
}
```

#### Customer-Owned Retrieval

If the customer does not want LoomAI to store source content, or if records are user-owned, the customer can expose a retrieval connector. LoomAI calls the connector at runtime and uses the returned documents as evidence for that one turn.

```http
POST /retrieval/search
Authorization: Bearer <connector-token>
Content-Type: application/json
```

Request:

```json
{
  "query": "refund policy",
  "vectorSpace": "help_articles",
  "topK": 8,
  "cursor": null,
  "filters": {
    "locale": "en-US",
    "visibility": "public"
  },
  "trace": {
    "requestId": "req_01HX...",
    "conversationId": "chat_01HX...",
    "authContext": {
      "subjectId": "user_123",
      "subjectType": "CUSTOMER_USER",
      "authMode": "VERIFIED_SESSION",
      "sessionId": "sess_01HX...",
      "deploymentId": "dep_customer_staging",
      "customerId": "customer_acme",
      "tenantId": "tenant_acme",
      "grantedScopes": ["retrieval:search"]
    }
  }
}
```

Response:

```json
{
  "success": true,
  "documents": [
    {
      "id": "article_refunds",
      "content": "Refunds are reviewed within five business days...",
      "score": 0.91,
      "source": "help-center",
      "url": "https://app.customer.example/help/refunds",
      "vectorSpace": "help_articles",
      "metadata": {
        "title": "Refund policy",
        "updatedAt": "2026-05-17T10:00:00Z"
      }
    }
  ],
  "count": 1,
  "totalCount": 1,
  "cursor": null
}
```

The connector must enforce customer-side ACLs. If the verified user cannot access a document, the connector must not return it.

#### Governed Action Execution Through Customer API

For non-MCP integrations, LoomAI can call a configured action endpoint.

```http
POST /actions/execute
Authorization: Bearer <connector-token>
Content-Type: application/json
```

Request:

```json
{
  "actionId": "create_support_ticket",
  "params": {
    "subject": "Billing question",
    "body": "The user asked for help understanding invoice INV-1042."
  },
  "idempotencyKey": "chat_01HX_turn_04_create_support_ticket",
  "trace": {
    "requestId": "req_01HX...",
    "conversationId": "chat_01HX...",
    "authContext": {
      "subjectId": "user_123",
      "subjectType": "CUSTOMER_USER",
      "authMode": "VERIFIED_SESSION",
      "sessionId": "sess_01HX...",
      "deploymentId": "dep_customer_staging",
      "customerId": "customer_acme",
      "tenantId": "tenant_acme",
      "grantedScopes": ["actions:execute"]
    }
  }
}
```

Response:

```json
{
  "success": true,
  "message": "Support ticket created.",
  "data": {
    "ticketId": "ticket_789",
    "status": "open"
  },
  "pinnedTargets": [
    {
      "id": "ticket_789",
      "vectorSpace": "support_tickets",
      "contentText": "Ticket #789: Billing question",
      "metadata": {
        "status": "open",
        "url": "https://app.customer.example/support/tickets/789"
      }
    }
  ]
}
```

Failures should be explicit and safe:

```json
{
  "success": false,
  "message": "The invoice was not found for this account.",
  "errorCode": "OWNED_RESOURCE_NOT_FOUND",
  "data": {
    "safeReason": "No matching invoice is visible to the authenticated user."
  }
}
```

#### MCP Server Contract

For MCP integrations, the customer exposes a Streamable HTTP MCP endpoint. LoomAI uses it for discovery and execution.

```http
POST /mcp
Authorization: Bearer <mcp-token>
Accept: application/json, text/event-stream
Content-Type: application/json
MCP-Protocol-Version: <agreed-version>
```

The server must support:

- `initialize`
- `tools/list`
- `tools/call`

Example discovered tool:

```json
{
  "name": "get_current_user_invoices",
  "description": "Return invoices owned by the authenticated user.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "invoice_id": {
        "type": "string",
        "description": "Optional invoice id. If absent, return the latest invoices."
      },
      "limit": {
        "type": "integer",
        "default": 5,
        "minimum": 1,
        "maximum": 20
      }
    }
  }
}
```

LoomAI imports approved MCP tools into the capability catalog. Runtime action selection uses the published catalog and policy metadata, not raw runtime `tools/list` as customer-visible product truth.

#### Recommended Data Boundaries

- Index shared knowledge that can be safely searched across a tenant.
- Fetch user-owned data live through MCP/API using a verified session.
- Keep action parameters structured and policy-resolved.
- Use confirmation for write operations and high-risk reads.
- Return normalized, safe errors for unauthorized, not-found, ownership mismatch, rate limit, and upstream failure.
- Avoid placing full customer records in chat context. The runtime should retrieve or call tools only when needed.

### Customer Technical Checklist

Before implementation, the customer should provide:

- staging web app URL
- production web app URL
- auth/session model
- list of initial AI journeys
- API or MCP server documentation
- test user accounts
- test records for owned-resource flows
- approved knowledge sources
- allowed origins and redirect URLs
- security review requirements
- production rollout constraints

---

## What LoomAI Provides

### AI User Experience

- embedded assistant for the customer's web application
- full-screen assistant mode for deeper workflows
- contextual page-aware answers
- retrieval from approved product, help, policy, or operational data
- action confirmations before sensitive operations
- support for user-owned resources when authenticated

### Governed Capability Execution

- capability catalog for approved AI actions
- per-action permission, confirmation, and risk policy
- MCP `tools/list` discovery as verification evidence
- MCP `tools/call` or API execution through a governed adapter
- normalized action evidence for support and audit
- safe error mapping for not-found, unauthorized, and blocked actions

### Deployment And Operations

- staging deployment before production
- production profile managed by the Platform
- environment and secret management
- health checks and release gates
- evidence bundles for launch review
- rollback and deactivation workflows
- operator diagnostics separated from user-safe messages

### Security And Governance

- least-privilege integration credentials
- tenant and user scoping
- no arbitrary secret exposure to plugins or prompts
- audit trail for sensitive actions
- confirmation gates for write operations
- support for per-customer auth and session material
- fail-closed behavior when credentials, permissions, or ownership cannot be proven

---

## Example Use Cases

### Customer Support

Users ask questions about their account, subscription, order, ticket, invoice, usage, or policy. The assistant retrieves the right data and answers inside the product.

### Guided Operations

The assistant helps users complete workflows such as updating a profile, changing a plan, creating a request, filing a return, submitting a document, or starting a support case.

### Internal Operator Copilot

Support or operations teams use AI to inspect customer state, summarize issues, recommend next steps, and prepare governed actions for approval.

### Self-Service Product Assistant

Users get contextual help inside the app, with answers grounded in the product's own data and documentation.

---

## Integration Model

The customer chooses which product capabilities become AI-accessible.

Each capability should define:

- capability name and business purpose
- required input parameters
- where each parameter can be resolved from: user session, page context, retrieved evidence, explicit user input, or prior action result
- required authentication and ownership checks
- read or write classification
- confirmation requirement
- safe success response
- safe failure response
- audit fields

Example:

```text
Capability: get_current_user_orders
Type: read
Source: MCP tool or customer API
Auth: authenticated user session required
Ownership: current user's account only
Inputs:
  - user_id: resolved from authenticated session
  - order_id: optional, resolved from user input or page context
Failure behavior:
  - not authenticated -> ask user to connect account
  - no matching order -> explain that no matching order was found
  - ownership mismatch -> fail closed
```

---

## Delivery Plan

### Phase 1: Discovery And Design

- identify target AI user journeys
- inventory available APIs, data sources, and auth model
- select initial read-only capabilities
- define action governance policy
- define staging and production deployment profiles

Output: implementation blueprint and launch gate checklist.

### Phase 2: Staging AI Deployment

- configure customer tenant and staging deployment
- connect retrieval sources
- connect first MCP/API capabilities
- deploy assistant UI to staging
- run quality, safety, and reliability tests
- produce evidence bundle

Output: working staging AI experience.

### Phase 3: Governed Actions

- add approved write actions
- add confirmation and audit policy
- test not-found, unauthorized, and failure paths
- validate user-owned resource resolution
- confirm support and rollback behavior

Output: governed action execution in staging.

### Phase 4: Production Launch

- customer approves production readiness
- deploy production profile
- verify production health and evidence
- monitor early usage
- support rollback/deactivation if needed

Output: production AI capability live in the customer product.

---

## Customer Responsibilities

The customer provides:

- staging and production application URLs
- API documentation or MCP server details
- test accounts and representative test data
- authentication requirements
- approved data sources
- business rules for actions
- brand/UI constraints
- security and compliance requirements
- production rollout window

The customer does not need to expose the whole application to AI. Only approved data and capabilities are connected.

---

## What Makes This Different

This is not just a chatbot widget.

LoomAI provides a deployment and governance layer for AI products:

- AI capabilities are configured and tested before exposure
- actions execute through controlled adapters, not direct prompt-side code
- sensitive operations require policy and confirmation
- staging and production are managed separately
- evidence is captured for operators and support
- failures return safe user messages while preserving diagnostics
- existing applications can adopt AI incrementally

---

## Launch-Safe First Scope

Recommended first launch scope:

- embedded assistant on selected pages
- retrieval over help, policy, product, or account documentation
- 2-4 read-only user-owned capabilities
- 1 low-risk write capability with confirmation
- staging proof before production
- production launch to a limited user cohort
- weekly review of answer quality and action evidence

This creates real product value without opening the entire application surface area on day one.

---

## Commercial Packaging Draft

### Design Partner Deployment

Best for first customer cohort.

Includes:

- staging and production AI deployment
- one web app integration
- MCP/API capability mapping
- assistant UI configuration
- retrieval setup
- governed action setup
- evidence and launch support

### Managed AI Product Deployment

Best for customers ready to run AI as an ongoing product capability.

Includes:

- managed staging and production profiles
- capability catalog management
- release gates
- monitoring and support workflow
- recurring optimization
- additional capability expansion

---

## Success Criteria

The initial deployment is successful when:

- the assistant is live in staging
- the assistant answers grounded questions from approved customer data
- selected user-owned resources are resolved only for authenticated owners
- at least one governed action works with confirmation and audit
- failure paths are safe and understandable
- production deployment can be approved, launched, verified, and rolled back
- support has evidence for every sensitive action

---

## Proposed Next Step

Run a 60-90 minute integration workshop.

Workshop output:

- target AI journeys
- first capability list
- auth and data-access map
- staging deployment requirements
- production launch constraints
- fixed-scope implementation plan

After the workshop, LoomAI can provide a scoped implementation proposal with timeline, cost, and launch gates.
