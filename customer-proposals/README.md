# Protected Customer Proposals

Small, independent proposal delivery for `https://loomai.pro/proposals/<slug>`.
There are no Platform backend, AI runtime or framework changes.

## Protected Engineer Portfolio

`https://loomai.pro/founder` reuses the same Supabase code/session service with
its own `founder` grant and a `/founder`-scoped cookie. It is not a Console account
or a second authentication service. An invitation exchanges its code and redirects
to the clean URL; visitors can also enter the code manually and lock the page.
Revocation, expiry, withdrawal and temporary authorization outages are checked
before serving HTML or each profile image. An outage never exposes the profile.

The existing owner-approved engineer profile and screenshots are built as a
dedicated page. The build moves its HTML/images out of the public `dist` tree into
`protected-dist`; only the gateway can serve them. Static smoke checks enforce
this separation. These are owner portfolio materials in source, not private
customer proposal documents; this gates website viewing, not access to source
already published in the repository. Access codes, sessions and operational
credentials remain private and are never part of the site image or Git.

The page remains AI Product Engineer-focused, with framework/Smart Brain diagrams,
real assistant screens, learning/stack and professional experience. No founder
headline was reintroduced. It has no visit tracker, AI widget, external video or
booking request. Existing proposal layouts and access grants are unchanged.

Local server verification must set `LOOMAI_SITE_DIST_DIR` to the site's `dist`
directory and `LOOMAI_SITE_PROTECTED_DIR` to its sibling `protected-dist`. The
production image copies both, but only the former is a public static root.

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
   A web-only proposal can explicitly set `PROPOSAL_WEB_ONLY=true` without a
   matching DOCX; its download control is hidden. Set `PROPOSAL_PRESENTATION` to
   include a supported private visual layout during initial publication.

The initial publisher issues a 30-day invitation without emailing it. The raw
code, gateway key, source/file hashes and expiry are saved only in ignored
`.private/access.private.json`, created with owner-only permissions. Keep that
file in the private operational backup; normal Git is not its backup. Publishing
an existing slug deliberately requires reconciliation instead of overwriting it.
Changing content/version is a separate reviewed administrative operation.
New proposals are recorded independently under `proposals[slug]` in that private
file. The original legacy `proposal` entry remains readable and unchanged; a new
invitation never replaces another recipient's code.

### Visual Presentation

An authorized document may additionally contain a `presentation` object using
the `buying-workspace-v1` layout. It adds the founder-video area, website and
expanded-workspace scenes, distinct standalone-app address/preview, integration
flow, business outcomes and pilot call to action. The original approved scope
and protected Word download remain in an expandable section. All recipient
copy still comes from private Supabase JSON, not the static shell or image.

The `partnership-v1` layout presents the partnership intent, immediate access
request, interactive retailer illustration, scoped data/return flow, API scope,
proof-of-concept phases and proposed commercial routes. It uses the same safe
DOM helpers, fixed public demo link, contact panel and protected full-document
renderer. Recipient copy and endpoints are private content, not public assets.

The optional `presentation.integrationProcess` replaces the smaller integration
diagram and appears immediately after customer outcomes, before the pilot offer.
It distinguishes customer uploads in Console from LoomAI-provided private S3
storage, and customer inventory authorization from LoomAI connector setup. Two
input lanes feed the Smart Brain and the three branded customer channels, with
optional Desk and a joint review before launch. These are proposal illustrations,
not live upload controls or evidence of an approved third-party connection.

The illustrative controls switch between comparison, policy and human-support
explanations without calling an AI deployment or submitting an enquiry. The
proposed app address opens a local preview dialog, never an unconfigured domain.
The separate public-demo button opens the existing fictional dealership demo.

Until the actual recording is supplied, the video cover is explicitly labelled
as illustrative and forthcoming, without a fake play action or duration. Set
`presentation.video.youtubeId` to the owner's actual recording ID to enable
click-only playback through YouTube's privacy-enhanced embed. No third-party
video or calendar request is made before its corresponding explicit click.

For an owner-approved visual-only update, use `update-presentation` with
`PROPOSAL_SLUG`, a private JSON file in `PROPOSAL_PRESENTATION`, and the current
JSON content SHA256 in `PROPOSAL_EXPECTED_CONTENT_SHA256`. The command backs up
the previous row and compares its full content before updating. It leaves the
document version, Word attachment, invitation expiry and access grants intact.
Changing substantive contractual scope still requires a document/version review.

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

## Initial Hosted Qualification

On 6 November 2026, source `4361d92241e97926523b6d82a6ac33cc6b01f37e`
was built by site-only CI run `42211529977` and deployed healthy on the production
site. Native OCI image identity was checked independently of the site's existing
pinned health metadata. The dedicated Edge Function is ACTIVE; public browser
roles have no schema/RPC access. Existing authentication and analytics remained
unchanged.

Thirteen focused tests, the transactional database lifecycle qualification and
twelve production HTTP checks passed. Actual browser code entry, protected Word
download, responsive phone layout, lock/reopen, personal contact and real calendar
availability were checked. No recipient email, enquiry or booking was sent.
Customer content, invitation links, downloads and screenshots remain private.

The approved visual replacement was qualified later on 6 November, from source
`e0477eb4e2c10efae8ccdf03ec06b3236db19ac2`. Site-only CI `42227703771` passed;
production deployment `auu1biwmo3c9957ok0l5dnej` finished, and its native OCI
revision and healthy state were verified. Seventeen focused tests, Astro check,
the site build and content/static smoke passed. Sixteen production HTTP checks
passed, including the unchanged invitation, identical protected Word document,
anonymous denial and logout revocation. Live comparison/policy/enquiry/human
illustrations, standalone preview, booking panel, Word download, wrong-code
handling and lock/reopen passed. Desktop, 390px and 320px layouts had no
horizontal overflow or broken visible images; Escape dismissed the preview.
The actual introduction recording is still owner-supplied, not fabricated.

The integration-journey addition is live immediately after customer outcomes.
Final source `acfde3ec6dea3274c8c532c7259d9321a6b3c926`, site-only CI
`42237033638`, production deployment `zjhunf0qzatvx2taybx11dpx`: passed and
healthy with native source verification. Eighteen focused tests and twelve
production HTTP checks passed; the page was inspected live at desktop, 390px
and 320px widths. Returning-browser verification caught a cached SVG sprite;
the versioned `icons-v2.svg` fixes it without asking visitors to clear caches.
The original invitation, protected Word attachment and access model are unchanged.

The readability follow-up is live from source
`de3c89f00834f9d852c6ea30f3f267b45d5a45d1` (CI `42241864945`, production
`fv2xjxozk8hnsr6oi1sf1bcm`, finished/native healthy). Stage 3/4 owner badges
now read "LoomAI Team" through private presentation content, at 13px/700 instead
of 9px/400. Inter is retained, with larger explanatory text, stronger contrast
and less diffuse video-title shadows. Tablet document labels have their own row.
Eighteen focused tests, Astro check/build/static smoke and live desktop, 390px,
320px and 820px no-overflow checks passed. The branded preview opens/dismisses;
no browser warnings or errors. Private proof: `qualification/typography-*`.
The presentation update was backed up; invitation and Word content are unchanged.

The second, independently code-scoped partnership proposal is published using
`partnership-v1`. Source `efb38d69fa10b4faef81c36fe4b054989ad0180b`, site-only
CI `42249198989`, production `y115g9yjfqgqwlwsopsrwl0s`: passed/finished and
native healthy. Nineteen focused tests, Astro check/build/static smoke and
fourteen live HTTP access checks passed. Actual manual code entry, interactive
illustrations, full details, contact-panel dismiss and lock/reopen passed, with
390px/320px/820px no-overflow checks and no broken images or browser warnings.
The original proposal's database row/Word hash and live owner captions remained
unchanged. This new proposal is explicitly web-only, with no fabricated download.
Its source, screenshots, checks and invitation are retained only in `.private`.
No recipient email or booking was submitted.

The partnership lead-focus follow-up is live from source
`129e45f0cddef9a4e0c27b0c0f24695416a60376`, CI `42256797874`, production
`pk52mu60xk9qu1ficav2o6it` (finished/native healthy). Optional private
`value.leadJourney` shows the four-step creation path and `video` reserves a
founder introduction. A valid `video.youtubeId` enables click-only playback on
the fixed YouTube privacy-enhanced host; without it, there is an explicitly
pending non-playable space with the presenter's existing photo. No fabricated
recording or duration. Twenty focused tests, check/build/static smoke, seventeen
live HTTP checks and desktop/390px/320px/820px browser checks passed. Full web copy
and presentation were backed up and compare-updated together, without changing
technical scope, access grants, invitation expiry or version. Private lead-focus
screenshots/reports are under `.private/autotrader`; no recording supplied yet.
