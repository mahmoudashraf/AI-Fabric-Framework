# Authentication and Authorization Design Plans

Status: detailed planning index (2026-04-06)

This folder groups future-work design documents focused on customer-facing authentication and authorization models for AI deployments.

Mode hierarchy:

1. `CUSTOMER_STOREFRONT_PRIVATE_RUNTIME_AUTH_PLAN.md`
   - default and preferred production posture
   - browser does not call runtime or connector directly
   - storefront or app backend is the trusted caller
2. `PUBLIC_RUNTIME_BROWSER_CHAT_AUTH_PLAN.md`
   - explicit easier-integration mode
   - browser may call runtime directly
   - browser-held short-lived bearer tokens are allowed only in this mode
3. `SHOPIFY_APP_ARCHITECTURE_PLAN.md`
   - product packaging plan for Shopify
   - defaults to the private-runtime posture
   - may adopt public-runtime mode later as an opt-in variant

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

Important clarification:

- anonymous public chat does not mean tokenless access
- in public-runtime mode, anonymous browser sessions should still use a short-lived anonymous bearer token
- the default issuer for that anonymous token is the runtime bootstrap endpoint itself
- a trusted site or app backend may issue the token instead if the integration already has one
