# Authentication and Authorization Design Plans

Status: detailed planning index (2026-04-06)

This folder groups future-work design documents focused on customer-facing authentication and authorization models for AI deployments.

Documents in this folder:

- `CUSTOMER_STOREFRONT_PRIVATE_RUNTIME_AUTH_PLAN.md`
- `PUBLIC_RUNTIME_BROWSER_CHAT_AUTH_PLAN.md`
- `SHOPIFY_APP_ARCHITECTURE_PLAN.md`

Recommended reading order:

1. `CUSTOMER_STOREFRONT_PRIVATE_RUNTIME_AUTH_PLAN.md`
2. `PUBLIC_RUNTIME_BROWSER_CHAT_AUTH_PLAN.md`
3. `SHOPIFY_APP_ARCHITECTURE_PLAN.md`

Document intent:

- `CUSTOMER_STOREFRONT_PRIVATE_RUNTIME_AUTH_PLAN.md`
  - the strict baseline for customer storefront integrations where the browser never talks to runtime or connector directly
- `PUBLIC_RUNTIME_BROWSER_CHAT_AUTH_PLAN.md`
  - the easier public chatbot model where the browser can talk to runtime directly, including anonymous users and tighter guardrails
- `SHOPIFY_APP_ARCHITECTURE_PLAN.md`
  - the productization plan for packaging the customer integration as a Shopify app while preserving the security model

Common principles across all three plans:

- connector credentials must remain server-side
- browser-held static deployment API keys are not acceptable for real customer use
- runtime and connector must derive identity from verified auth context, not from caller-supplied `userId`
- authorization must be explicit and fail-closed for sensitive retrieval and action execution

