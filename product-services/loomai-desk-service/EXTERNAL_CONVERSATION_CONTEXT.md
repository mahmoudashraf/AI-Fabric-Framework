# Trusted External Conversation Context (010.54)

Optional integration for an existing case/helpdesk or other conversation context.
Desk remains a human-channel service, not a ticket database. Configuration and its
encrypted credential are stored in `desk_context_provider`; external context is
fetched on demand, never persisted as a case or transcript in Desk.

## Configure A Reviewed Provider

The service operator first sets `DESK_CONTEXT_ALLOWED_ORIGINS`, for example
`https://api.loomai.pro,https://support.example.com`. Entries are exact HTTPS
origins, without paths or trailing slashes; only port 443 is supported. The
default is empty (no external providers). No wildcard or browser-supplied URL is
accepted. DNS must resolve exclusively to public addresses, checked both before
requests and by the actual connection resolver. Redirects, cookies, automatic
retries and proxy discovery are disabled. TLS retains normal hostname/certificate
verification. HTTP loopback requires an explicit test-only property
`loomai.desk.conversation-context-allow-insecure-loopback=true`; never enable it
on a hosted service.

Using the existing Desk **service-admin key**, not a customer/agent browser:

```http
PUT /api/desk/admin/v1/bindings/{bindingId}/conversation-context
X-LoomAI-Desk-Service-Key: <existing Desk administration key>
Content-Type: application/json

{
  "providerUrl": "https://support.example.com/integrations/desk/context",
  "serviceKey": "<distinct server-only context credential, 32-4096 characters>",
  "resolveEnabled": true
}
```

`GET` on that URL returns `{configured, providerUrl, resolveEnabled}`, never the
key. `DELETE` removes only this optional integration; channel/binding/agent
assignments remain unchanged. Ordinary binding synchronization does not overwrite
this configuration. Existing bindings work without it.

## Browser Routes

- `GET /api/desk/v1/bindings/{bindingId}/conversations/{handoverId}/context`
- `POST .../context/resolve` with
  `{"idempotencyKey":"stable-command-id","expectedVersion":3}`

These routes use the actual verified Supabase agent session/bearer and current
**SUPPORT** assignment. OWNER, REVIEWER, a service credential alone, or access to
another binding is insufficient. Cookie mutations retain existing CSRF checks.
The handover must already exist in the binding's trusted channel store. Desk
derives runtime conversation/owner/tenant/deployment/target from that record, not
contact intake, text, a supplied case ID or a browser URL.

Success returns a no-store projection:
`{"state":"AVAILABLE","context":{...}}`. No provider is
`{"state":"NOT_CONFIGURED","context":null}`; an explicitly unlinked
conversation is `NOT_LINKED`. A failed provider does not masquerade as unlinked
or prevent normal claim/reply/control operations. The inspector fetches only when
opened, refreshes current context, hides stale context on failure, and has an
explicit refresh command.

## Server-To-Server Request

Both operations POST to the **same exact configured endpoint**:

```http
POST /integrations/desk/context
Authorization: Bearer <actual agent's verified Supabase access token>
X-LoomAI-Desk-Service-Key: <binding context credential>
X-LoomAI-Desk-Binding-Id: binding-support-prod
Content-Type: application/json

{
  "schemaVersion": 1,
  "operation": "LOOKUP",
  "scope": {
    "bindingId": "binding-support-prod",
    "customerId": "support-service-owner",
    "tenantId": "tenant-support",
    "deploymentId": "dep-support",
    "targetProfileId": "production",
    "handoverId": "handover-42",
    "conversationId": "conversation-42",
    "ownerRef": "opaque-case-principal",
    "externalConversationRef": "desk-conversation-42"
  },
  "actor": {
    "issuer": "https://project.supabase.co/auth/v1",
    "subject": "actual-agent-supabase-subject",
    "agentId": "agent-verified-directory-id"
  },
  "command": null
}
```

`customerId` identifies the **binding's owning customer**, not an arbitrary
requesting customer. The provider resolves its own case using the full trusted
scope and original conversation principal. It must validate the separate context
credential, the actual JWT issuer/audience/expiry and its match to `actor`, and
current authorization for this exact binding. Every Desk request has already
passed the current SUPPORT gate; an external provider may trust that narrowly
scoped server-to-server assertion while applying its own agent/case permissions.
A LoomAI-owned provider such as Platform also rechecks current SUPPORT through
Desk's existing service-authenticated
`GET /api/desk/admin/v1/bindings/{bindingId}/authorizations?issuer=...&subject=...&permission=SUPPORT`
for current assignment proof using its existing internal administration trust.
Do not distribute Desk's global administration key to customer/external context
providers. A third-party provider needing independent live Desk introspection
needs a separately reviewed scoped contract; this feature does not grant global
administration to it. No such credential is supplied by a browser. Do not treat
the context service key as an agent identity. Recheck external requester membership,
case/resource ownership and consent as required by the provider's own system.

`RESOLVE` uses the same scope/actor with
`"command":{"idempotencyKey":"stable-command-id","expectedVersion":3}`.
Desk permits it only when the reviewed configuration has `resolveEnabled=true`.
The provider is the business authority: expose `resolveAction` only when this
agent may resolve the linked case, and enforce that permission again on RESOLVE.
Atomically check the expected version and persist a receipt scoped to
binding/conversation/actor/operation/idempotency key. A retry of the same uncertain
operation must return its original outcome; a changed payload with the same key
is a conflict. Desk never automatically retries writes or stores a second case
state. Audits record the actual agent and handover, not raw context.

## Provider Response

HTTP 200 with `Content-Type: application/json`:

```json
{
  "schemaVersion": 1,
  "scope": {
    "bindingId": "binding-support-prod",
    "customerId": "support-service-owner",
    "tenantId": "tenant-support",
    "deploymentId": "dep-support",
    "targetProfileId": "production",
    "handoverId": "handover-42",
    "conversationId": "conversation-42",
    "ownerRef": "opaque-case-principal",
    "externalConversationRef": "desk-conversation-42"
  },
  "state": "AVAILABLE",
  "context": {
    "reference": "SUP-1042",
    "title": "Workspace domain setup",
    "status": "OPEN",
    "version": 3,
    "accountLabel": "Example customer",
    "resourceLabels": ["Customer workspace", "Production deployment"],
    "consent": {
      "status": "GIVEN",
      "summary": "Current status checks for the selected resources only.",
      "updatedAt": "2026-10-30T10:00:00Z"
    },
    "observations": [{
      "label": "Workspace routing",
      "status": "INCOMPLETE",
      "summary": "The required DNS record has not been verified.",
      "observedAt": "2026-10-30T10:04:00Z"
    }],
    "resolveAction": {
      "label": "Resolve case",
      "confirmation": "Mark this case resolved? The conversation will stay open."
    }
  }
}
```

Echo the exact scope for both operations; a mismatch is rejected. An unlinked
conversation returns `state:NOT_LINKED, context:null` with the same scope, never
another case's context. RESOLVE must return the actual updated AVAILABLE context,
not a generic acknowledgement. Reject unauthorized/unavailable/conflicting
operations with HTTP 401/403/404/409/422 or 5xx as appropriate. No raw upstream
error body is forwarded to the browser.

Only the typed context projection above reaches the browser; extra fields are
discarded. There are no arbitrary URLs, HTML, scripts, generic action names or
browser-credential fields. Maximum decoded response is 32 KiB. Reference is
160 characters, title 240, status 64, account label 160; up to 12 resource labels
of 160 characters and 12 observations (label 160, status 64, summary 1000,
required observation timestamp). Consent is optional, with explicit
GIVEN/DECLINED/REVOKED/NOT_REQUESTED, a 600-character summary and timestamp. Resolve
label is at most 80 characters and confirmation 300. A malformed/oversized
projection is visibly unavailable, not truncated into misleading success.
`resolveAction` is omitted from the browser projection when configuration is
read-only. Agent access and unchanged provider configuration are rechecked after
the upstream response; provider-side authorization remains necessary for an
already-sent mutation.

**Resolve case does not close a runtime conversation or return it to AI.** Existing
channel controls retain their own explicit lifecycle. The external system owns
case reopening/terminal state and all non-conversation business effects.
