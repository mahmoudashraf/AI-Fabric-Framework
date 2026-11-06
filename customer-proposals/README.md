# Protected Customer Proposals

Small, independent proposal delivery for `https://loomai.pro/proposals/<slug>`.
There are no Platform backend, AI runtime or framework changes.

## Data and Access

The existing Supabase project holds the private proposal content, protected Word
file, code digests and short-lived session digests in the unexposed
`loomai_customer_proposals` schema. All tables use RLS and deny browser roles.
Only three service-role RPCs exchange a code, read an authorized proposal/file
and revoke a session. The dedicated Edge Function additionally requires a
server-only gateway key; it does not accept direct anonymous browser calls.

The existing public site's Node server handles the same-origin access routes.
Invitation query codes are exchanged before returning any HTML, then redirected
to the clean URL. Manual codes are POSTed in JSON. A proposal-scoped cookie is
Secure, HttpOnly and SameSite=Lax, with no Domain attribute. Sessions last at most
four hours and never outlive their code. Every content/download request checks
current code revocation, expiry, publication status and content version.

The generic page shell and assets contain no recipient-specific content. Stored
content is a structured document, rendered as text nodes and safe links, never
arbitrary HTML. The Markdown publisher uses the standard mdast parser. Customer
documents, codes and administrative credentials are not committed or copied into
the site image. Downloads remain behind the same session checks.

No visit tracker or AI widget runs on this page. The Calendly iframe loads only
after a booking click, with no-referrer and no proposal/code/session parameters.
Responses are private/no-store and noindex. Proxy request logging must remain
disabled or redact query codes before invitation links are issued. Codes remain
bearer secrets and may be forwarded; this is not recipient identity verification.

Rate limits use a rotating keyed address digest rather than storing raw IPs.
They bound ordinary guessing, not every distributed abuse scenario. The public
site must remain behind its trusted proxy; a caller-supplied forwarded-address
header is not authentication. Secrets and high-entropy codes remain the actual
authorization boundary. Supabase retains its own normal platform logs.

## Configuration

Set only these runtime variables on the public site:

- `CUSTOMER_PROPOSALS_ENDPOINT`: exact HTTPS function URL ending
  `/functions/v1/customer-proposals`.
- `CUSTOMER_PROPOSALS_GATEWAY_KEY`: dedicated random server secret, also stored as
  a Supabase Edge Function secret. Never a build argument or browser value.
- `CUSTOMER_PROPOSALS_ORIGIN`: exact public origin, normally `https://loomai.pro`.

Unconfigured or unavailable authorization returns an unavailable state, never
public or cached content. Existing public pages continue independently.

## Administration

Use the existing Supabase management identity through environment variables,
not command-line credential arguments. Set `SUPABASE_ACCESS_TOKEN` and
`SUPABASE_PROJECT_REF`, then invoke `scripts/admin.mjs` with:

1. `migrate`: apply this feature's isolated migration and check grants.
2. `qualify-database`: test lifecycle cases in a transaction that rolls back.
3. `deploy-function`: set the dedicated secret and deploy only this Edge Function.
4. `publish`: read the owner-approved local Markdown and matching DOCX. Set
   `PROPOSAL_SOURCE`, `PROPOSAL_SLUG`, `PROPOSAL_RECIPIENT`, `PROPOSAL_DATE`.

The initial publisher issues a 30-day invitation without emailing it. The raw
code, gateway key, source/file hashes and expiry are saved only in ignored
`.private/access.private.json`, created with owner-only permissions. Keep that
file in the private operational backup; normal Git is not its backup. Publishing
an existing slug deliberately requires reconciliation instead of overwriting it.
Changing content/version is a separate reviewed administrative operation.

Staff can revoke a code by setting its `revoked_at`, or withdraw a proposal by
setting its status to `withdrawn`, through the Supabase SQL Editor. Existing
sessions then fail their next protected request; previously viewed/downloaded
copies cannot be recalled. Session/attempt cleanup runs on code exchanges; old
proposal/code records are retained for explicit owner review.

## Verification and Rollback

```sh
node --test customer-proposals/tests/*.test.mjs
node --test Platfrom/loomai-site/scripts/proposal-content.test.mjs
```

Run the normal site check/build/static smoke. Qualify real UI code entry, wrong
code, invitation redirect, reload, download, lock/reopen, phone layout and
contact/calendar dismiss. Check anonymous direct content/file access and RPC
grants, wrong-scope sessions, revocation, expiry and withdrawal. Confirm static
builds and source control contain no customer proposal or secret.

To disable access, withdraw the proposal or remove only the site's gateway
configuration. Do not weaken authorization or expose static content as a recovery
path. Public website operation, analytics and all AI deployments are independent.
