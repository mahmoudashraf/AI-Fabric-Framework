# Desk Backend Contract (010.48)

Scope: backend only; no runtime/framework changes. The existing Basic Inbox channel lifecycle is reused in `com.loomai.desk.channel` with immutable binding-scoped repository instances and composite persistence keys. Original service remains untouched until final retirement.

## Browser Identity

- `GET /api/desk/v1/config`: `{supabaseUrl,supabasePublishableKey,issuer,loginUrl,identityPortalUrl,registrationEnabled,oidcEnabled}`. Public keys only. `identityPortalUrl` comes only from `DESK_IDENTITY_PORTAL_URL`; empty means account links/registration are unavailable, not that OIDC is disabled. The shared Console entry `/auth/desk/sign-in` supports the fixed `method=email|recovery|verify|register` links. No client-supplied portal or redirect parameter is accepted.
- `GET /api/desk/v1/session`: `{authenticated,operator:{id,displayName,email},bindings:[{bindingId,customerId,deploymentId,targetProfileId,deploymentName,environment,roles:[OWNER,SUPPORT,REVIEWER]}],csrfToken,csrfHeaderName}`. Anonymous returns `authenticated:false` and no bindings.
- `GET /oauth2/authorization/supabase?bindingId=<opaque-id>`: standard OIDC authorization-code + PKCE. Callback `/login/oauth2/code/supabase`; successful login returns to `/?bindingId=<id>` using a server-session-held `[A-Za-z0-9_-]{1,160}` hint (or `/` without one). No redirect URL is accepted. Session cookie is host-only Secure/HttpOnly/SameSite=Lax. Binding hint is navigation only; queue access remains independently authorized.
- `POST /api/desk/v1/agents/register`: verified authenticated identity only; optional `{displayName}` changes presentation only (1-160 characters). Issuer, subject, verified email and credentials never come from the body. Returns `{id,displayName,email,agentReference}`. Unassigned registration grants no queue access.
- `POST /logout`: empty `204`; invalidates local session, expires the Desk cookie and removes saved authorized-client credentials. Session CSRF is required; no redirect and no global provider logout claim.
- Browser session mutations send `X-CSRF-TOKEN` from session response. Bearer APIs require a validated configured issuer/audience JWT and do not use cookies or disable session CSRF.

## Binding APIs

Prefix `/api/desk/v1/bindings/{bindingId}`. Access is checked against current assignment on every call. OWNER manages agents; SUPPORT handles conversations; REVIEWER handles reviews. OWNER alone is not SUPPORT/REVIEWER.

- `GET /conversations?status=&limit=` -> Basic `ConversationSummary[]`.
- `GET /conversations/{handoverId}` -> `{conversation,consentedIntake,messages}`.
- `POST /conversations/{handoverId}/claim`, `/return-to-ai`, `/close`: `{idempotencyKey}` -> `{accepted,outcome,conversation}`.
- `POST /conversations/{handoverId}/messages`: `{text,idempotencyKey}` -> same command result.
- `GET /agents` -> `[{agentId,agentReference,displayName,email,roles}]`.
- `POST /agents`: `{email or agentReference,roles:[SUPPORT,REVIEWER]}` -> assignment. Exact verified registered email lookup only; no directory browsing. Idempotent replacement for that binding/profile.
- `DELETE /agents/{agentId}` -> 204.
- `GET /reviews?status=&limit=`, `GET /reviews/{taskId}`, `POST /reviews/{taskId}/decisions` (`{decisionId,decision,expectedVersion}`): transparent safe Platform bridge result; no local review engine.

The current published runtime uses `WAITING_FOR_REVIEW` (not `PENDING`) for
decidable action-review tasks. Its decision result is
`{task,outcome,successorTaskId,failure}`; a failure contains
`{reason,publicMessage,retryable}` and may arrive with HTTP 409. Desk preserves
that bounded typed conflict so the portal can show the actual public reason.
An action outcome is `{actionName,message,data}`. Do not invent a successful
execution status from approval alone. Task details have their own `message` and
`failureReason` fields; they deliberately do not expose executable action
parameters through a fabricated `proposal` projection.

## Platform Administration

All require `X-LoomAI-Desk-Service-Key`; never browser credentials. Prefix `/api/desk/admin/v1`.

- `PUT /workspaces/{customerId}`: `{name,owners:[{issuer,subject,email,displayName}]}`. Owners are authoritative immutable identity links; no email-based ownership matching.
- `PUT /bindings/{bindingId}`: `{customerId,tenantId,deploymentId,targetProfileId,deploymentName,environment,callbackUrl,providerApiKey,callbackSigningSecret,reviewBridgeUrl,reviewBridgeKey,enabled}`. Binding owner/scope immutable after creation; endpoint/credentials can rotate explicitly. Credentials encrypted at rest, provider key unique across service.
- `GET /bindings/{bindingId}`: safe binding summary, never credentials.
- `GET /bindings/{bindingId}/authorizations?issuer=<encoded>&subject=<encoded>&permission=SUPPORT|REVIEWER`: `{authorized:boolean}`. Service authentication required; exact immutable Supabase identity, enabled binding and currently registered/assigned role. OWNER alone never authorizes support/review. Platform uses this to recheck actual reviewer assignment on each delegated review request.

## Channel Protocol

Unchanged `POST /api/conversation-channel/v1/dispatches`, header `X-LoomAI-Inbox-Key`. Key authenticates exactly one binding; payload tenant/deployment/binding must match. Composite binding/handover/dispatch/message/outbox keys permit identical local IDs for different customers. Callback destination and secret come only from registered binding. Existing HMAC timestamp/nonce/body protocol preserved.

## Review Bridge Coordination

`reviewBridgeUrl` is the full server-configured list endpoint: `https://api.loomai.pro/api/desk/bridge/v1/bindings/{bindingId}/reviews` (or the staging control-plane equivalent). Desk GETs that URL for the queue, appends `/{taskId}` for detail and `/{taskId}/decisions` for POST decisions. Sends `X-LoomAI-Desk-Service-Key: <reviewBridgeKey>` AND `Authorization: Bearer <actual Supabase reviewer access token>` AND `X-LoomAI-Desk-Binding-Id`. Platform independently validates the actual bearer, binding key and current assignment through the service-protected authorizations endpoint; no shared reviewer identity. Cookie sessions obtain/refresh the actual access token through Spring OAuth2 client, whose access/refresh tokens are encrypted in Desk PostgreSQL. Transport errors remain visible; Desk does not approve/execute locally.

OAuth registration ID is `supabase`; callback URLs are `https://loomai-desk.46.224.145.148.sslip.io/login/oauth2/code/supabase` and `https://desk.loomai.pro/login/oauth2/code/supabase`. Supabase's central authorization UI is configured separately by Platform at `https://console.loomai.pro/auth/desk/authorize`. Desk never changes Supabase Site URL or existing redirects itself.

The UI is built separately and bundled into the same service image. Browser identity and credentials remain governed by the session contract above.

## Optional External Conversation Context

The reviewed per-binding context provider, browser projection and explicit case
resolution callback are specified in [External Conversation Context](EXTERNAL_CONVERSATION_CONTEXT.md).
They use the actual agent's SUPPORT assignment and do not replace the generic
human channel, action review or runtime conversation-control lifecycle.
