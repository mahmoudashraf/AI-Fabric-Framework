# Customer Support Workspace

The customer Console provides **Help & requests** at `/support` and the owner-scoped
case page at `/support/:caseId`. It reuses the generic Engage experience pack; there
is no separate support chat implementation or browser-held runtime assertion.

## Intake and Delivery

- Submit saves `POST /api/support/cases` with one `Idempotency-Key` and an immutable
  snapshot of the form. Diagnostic consent is unchecked by default.
- After save, that explicit submit handler calls `POST /api/support/cases/{id}/start`.
  The server owns the initial message and its stable delivery key. The browser sends
  neither a new query nor an alternate initial body.
- Opening, reloading, refreshing or reopening a case never calls `start` automatically.
  PENDING/UNKNOWN delivery has an explicit same-delivery action. Failed/uncertain AI
  delivery never deletes or hides the saved case.
- Resource changes, versioned consent, resolve/cancel/reopen use the registry routes.
  Resource IDs are selected from the customer's existing deployment/app/connection
  APIs, with authorization still enforced by the server. Status-check permission is
  separate from ordinary documentation help.

## Embedded Engage

`GET /api/support/cases/{id}/workspace` supplies the exact registered conversation,
principal namespace, **real installed Engage ID**, assignment revision, configuration,
asset paths/SRI and safe handover summary. `SupportWorkspace` mounts this ordinary
generic pack in an owned same-origin iframe within the case column. The widget's fixed
viewport fills that iframe, never the entire Console. No global CSS overlay override
or copied transcript renderer is used.

The normal Engage installation uses reviewed connection profile
`platform-support-case-host@1.0.0`, mode `BACKEND_MEDIATED_PRIVATE_RUNTIME`, with
empty connection configuration and the generic pack. It is enabled only with
Platform support, restricted to `platform.support.customer-id`, and intentionally
absent from the public connection catalogue/script manifests. Console supplies
the authorized case bootstrap through the generic host API. Do not register a
Shopify profile as a substitute or seed a special installation row. Create,
readiness and activation still use the normal installation APIs. This internal
adapter does not change other customers' one-script or standalone paths.

`apiBaseUrl` is `/api/support/cases/{id}/runtime/api`. Explicit widget route templates
use `/chat/me/conversations/{conversationId}` with `/messages`, `/control`, `/events`,
`/handover-requests`, `/handover-requests/{handoverId}/cancel|retry` and
`/ai-resume-requests`. The adapter independently validates the registered conversation.
No global conversation listing, new-conversation UI or DELETE route is exposed.

The host calls `openConversation` and `refreshConversation` through the generic
promise-based APIs. History reads restore optional safe `completedTurns` presentation;
exact `aiMessageId` associates each receipt with its canonical AI message while
`userMessageId` retains provenance, including interleaved writes from two tabs.
Canonical participant text remains authoritative. No query replay recovers cards.
Closed cases use generic `host.readOnly` while retaining history/control reads.
When Desk closes the canonical conversation, its authoritative control response
marks that case episode terminal. **Start new conversation** is then an explicit
reopen command, not automatic return to AI. For a newly registered episode, the
adapter first uses the supported runtime control read to initialize the empty
canonical scope before its history read. No synthetic transcript, query retry or
intake resubmission creates that empty conversation.

## Identity and Lifecycle

The iframe asks its parent for the current Supabase browser token on every widget
request and SSE reconnect. The bridge verifies origin, the exact frame window and a
per-instance nonce. Parent-side token resolution also verifies the current customer
and Supabase subject before and after the asynchronous session read. Logout/account
changes reject token requests and dispose of the previous iframe. Tokens are not
included in URLs, srcDoc, case data, persisted widget state or visible text.

Only reviewed Platform-origin `/api/public/ai-workspace/` assets with SHA-384 SRI
are loaded. Bootstrap, theme, namespace/account or read-only changes remount the
frame. Retired frame responses cannot configure the new frame. Reloading the workspace
only repeats authorized GETs, not intake delivery.

## Local Verification

### Internal Team Intake

The direct staff route is `/support/team-intake`. It deliberately enters before
Console customer-membership/onboarding gates, like the Desk authorization route.
A Desk-only agent signs in using the existing Supabase form, without creating a
Platform customer account. The route uses the current Supabase browser bearer for
`GET /api/support/staff/intake?page=0&size=25`, with no cookies, API keys or service
credentials. The API verifies issuer/audience/expiry and the current explicit
Desk SUPPORT grant; a Platform admin, Desk OWNER or UI role flag is not authority.

Help shows the **Team intake** link only after that API authorizes a read (including
an empty result). Normal customer 403 responses reveal no staff data or link.
The page displays saved references, summaries/details, account/resource labels,
backend-projected consent, delivery/status and update time only. It has no runtime
probe, transcript, reply editor or case-lifecycle commands. Customer-owned case
links are intentionally absent: an assigned agent is not the original requester.
Use the existing LoomAI Desk portal for actual conversations and replies.

Staff data is scoped by Supabase subject, discarded on unmount and hidden while
authorization refresh is pending or fails. The local `npm run smoke:support-team-intake`
test exercises the real App early branch, unlinked Desk-agent identity, sign-in,
refreshed bearer, denial, cached-row removal, admin non-authority and logout. This
does not replace hosted proof of actual JWT validation/current SUPPORT grants.

### Commands

First build `max-mode-widget` and `experience-packs/generic-workspace-experience`.
From `Platfrom/ui` run:

```sh
npx tsc -p tsconfig.app.json --noEmit --incremental false
npx vite build
npm run smoke:customer-support
```

The browser fixture uses the real built widget and pack with mocked authorized APIs.
It checks save/start separation, unchecked consent, canonical history/cards, contained
iframe geometry, refreshed bearer use, human handover/message/AI return, consent changes,
resolve/cancel/reopen, reload without replay and durable intake during runtime outage. Evidence is written to
`/private/tmp/loomai-support-ui-01054` (override with `SUPPORT_UI_OUTPUT`). This is local
integration proof, not hosted release verification. Run the widget's host/history/
backend-bearer smokes for controller and transport regressions.
