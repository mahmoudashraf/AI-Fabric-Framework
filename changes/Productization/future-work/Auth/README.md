# Authentication and Authorization Design Plans

Status: detailed planning index (2026-04-06)

This folder groups future-work design documents focused on customer-facing authentication and authorization models for AI deployments.

Sequencing clarification:

- the auth foundation in this folder should be built before assistant productization work and before Shopify or other packaged integrations
- the existing platform POC proxy should adopt that shared auth foundation immediately after the core auth and mode work, because it is already a live first-party caller path
- assistant and Shopify documents are kept here because they must consume the same auth foundation later
- they are downstream consumers of the auth work, not prerequisites for starting it

Mode hierarchy:

0. `AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`
   - concrete shared execution order for the whole auth workstream
   - explains what is common foundation vs mode-specific work
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

- `AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`
- `CUSTOMER_STOREFRONT_PRIVATE_RUNTIME_AUTH_PLAN.md`
- `PUBLIC_RUNTIME_BROWSER_CHAT_AUTH_PLAN.md`
- `SHOPIFY_APP_ARCHITECTURE_PLAN.md`

Recommended reading order:

1. `AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`
2. `CUSTOMER_STOREFRONT_PRIVATE_RUNTIME_AUTH_PLAN.md`
3. `PUBLIC_RUNTIME_BROWSER_CHAT_AUTH_PLAN.md`
4. `SHOPIFY_APP_ARCHITECTURE_PLAN.md`

Document intent:

- `AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`
  - the concrete implementation sequencing and migration guide for the auth design set
- `CUSTOMER_STOREFRONT_PRIVATE_RUNTIME_AUTH_PLAN.md`
  - the strict baseline for customer storefront integrations where the browser never talks to runtime or connector directly
- `PUBLIC_RUNTIME_BROWSER_CHAT_AUTH_PLAN.md`
  - the easier public chatbot model where the browser can talk to runtime directly, including anonymous users and tighter guardrails
- `SHOPIFY_APP_ARCHITECTURE_PLAN.md`
  - the productization plan for packaging the customer integration as a Shopify app while preserving the security model

Recommended delivery order:

1. deliver the shared auth foundation from `AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`
2. deliver customer-facing private-runtime and public-runtime auth capabilities
3. adapt the existing platform POC proxy onto that completed foundation
4. after that, attach Shopify packaging and assistant surfaces to the completed auth foundation

Common principles across all three plans:

- connector credentials must remain server-side
- browser-held static deployment API keys are not acceptable for real customer use
- runtime and connector must derive identity from verified auth context, not from caller-supplied `userId`
- authorization must be explicit and fail-closed for sensitive retrieval and action execution

Critical planning hint:

- auth-disabled development shortcuts must not become part of the product auth model
- a runtime or platform that treats `auth disabled` as `synthetic admin principal` will mask real authorization bugs
- this is especially dangerous for customer storefront modes because integration tests may appear to pass while every request is effectively running as admin
- future implementation should treat auth-disabled mode as:
  - local-dev-only
  - clearly labeled
  - not representative of production auth behavior
- the safer fallback is either:
  - unauthenticated local access with no synthetic privileged principal, or
  - an explicit local-only dev principal with tightly scoped non-production permissions
- all customer-facing auth work should assume that disabling auth must never silently grant broad admin authority

Important clarification:

- anonymous public chat does not mean tokenless access
- in public-runtime mode, anonymous browser sessions should still use a short-lived anonymous bearer token
- the default issuer for that anonymous token is the runtime bootstrap endpoint itself
- a trusted site or app backend may issue the token instead if the integration already has one
