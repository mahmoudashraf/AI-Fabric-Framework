# Business Experience Kits: Full-Stack Qualification

Date: 2 November 2026
Scope: 010.56, two business kits, optional Desk and canonical product resources.
Result: Code and hosted API/native rollout qualified; final production UI proof
is not closed. No AI Fabric change or release in this cycle.

## Source And Deployment Identity

Backend/public source: `9ea92aa91a8d3c386acc71b6828a39b47be8e7f5`.
Console source: `50fbe023fd571ab16f4a7f8f73c706ce8d538803`.

- Core image run `41486149211`: success, original full-stack source.
- Core image run `41526916999`: success, UI-only signup-intent follow-up.
- Staging backend job `odc2c1c52k7c363tekzx70ay`: finished; matching OCI source,
  healthy container `/aa3dylvh4stfeg0ersfzdjjm-165907662236`.
- Production backend job `nvr842jvuuak9db7htflj19y`: finished; matching OCI source,
  healthy container `/sbdv2pkkqrbsy9m59034hb5j-170201812335`.
- Final staging UI job `j2jyxjksvrrbxnskuu30fcth`: finished; matching OCI source,
  healthy container `/u67gepsowf7oo90dwjdx018r-212004328113`.
- Final production UI job `s4xhsbtmkqtngw7fmx4z0psw`: finished; matching OCI source,
  healthy container `/a6fz2ncuzzulg6lrzzwrjshu-212008147421`.
- Public-site job `gbnfk7gsrbwhx6k6qycoqy7g`: finished at the exact full-stack
  commit; healthy container `/t3r7unm08sh3tfatpadz7qky-165821720197`.
  This image has no OCI revision label, so its native health is not mislabeled
  as an OCI source proof; the exact Coolify build commit provides source evidence.
- Both databases report successful migration `222`.
- Both Console domains return the new `index-B4vSgMJl.js` document. Public and
  both experience routes, plus the authenticated app shell route, return 200.
  An app-shell HTTP 200 is not an authenticated rendering proof.

## Local Verification

- New backend suite: 28 service cases and five real PostgreSQL cases, no skips.
  Full Flyway chain through V222, keys/constraints, rollback and metadata-only
  cascade tested. The transaction test uses JDBC resource collaborators;
  the hosted negative case below additionally exercises canonical services.
- Reused bootstrap/consumer configuration/Engage installation suites: 139 pass,
  no skips. Combined affected backend cases: 172.
- Final kit/auth UI tests: 33 pass, no skips. Includes exact local intent return,
  malicious redirect rejection, both delivery modes, exact release assignment,
  missing products, row versions and safe links.
- Console production build passes. Existing large-bundle warning remains.
- Public-site production build and Astro check pass (zero diagnostics). Existing
  content/static smoke passes. Public-site worker additionally recorded 32
  responsive/theme checks and eight accessibility scans; main visually reviewed
  the hosted light/dark homepage before the interruption.

## Actual Staging UI

Existing Acme fixture; no new public signup or email verification is claimed.

1. Opened the hosted dark chooser, dealership detail and setup.
2. Selected the real public Dealership simulator template and customer-owned
   PostgreSQL choice, then configured a name, app slug, exact website origin,
   both channels and a desired Desk setup.
3. Created `kit-62a92123-a01c-44b0-99a7-7b15fcd42055`, linked to draft deployment
   `dep-9b40e6db`, with two canonical Engage installations sharing one consumer.
4. The overview showed actual blockers, pending domains and no deployed release;
   it did not claim Desk was connected or agents assigned.
5. Submitted the edit from `Kit Journey AI` to `Journey Support AI`. The turn
   was interrupted before the next browser observation. Fresh native readback
   confirms revision 1 and the new title on both installation rows.

This is creation and persisted edit evidence, not final production navigation,
first-signup, activation, Desk delivery or promotion evidence for a new kit.

## Actual Production API

Existing Acme Supabase fixture and its normal customer workspace. Public template
enrollment was invoked through the existing authenticated enrollment endpoint.
No role, ownership grant or email-confirmation state was manufactured.

- Generic kit `kit-76fd7547-0b05-4f56-a8ca-1d6724db92ed`, deployment
  `dep-1ce96b9d`, app `awi_pub_1e3ed1f35495428e8a17fe5c90d8c0ca` created DRAFT.
- Same operation replay returned the same ID. Changed replay returned 409.
- Branding/title update persisted; stale revision returned 409; read-after-write
  returned the updated canonical app.
- Normal Engage API set `capabilities.currentPage=true`. A stale kit form was
  rejected, and a fresh kit edit retained that setting.
- A separate existing Northfield customer identity could neither read nor
  update the Acme kit: both requests returned 404.
- A both-channel create with an invalid second origin returned 400. Before and
  after counts were identical: one kit, one deployment, one app, one consumer.
  This exercised the real canonical services, not mock collaborators.
- Anonymous public catalogue: 200/two entries. Anonymous customer collection:
  401. Staff-key customer collection: 403 on both environments.
- Console-origin POST preflight returned 200 with the exact allowed origin.

Both stage and production qualifiers have zero published versions, releases,
managed dependency graphs, managed vector resources and provider allocations.
They are inspectable configuration-only fixtures, not active orphan services.
No model calls or new business-system databases occurred.

## Remaining Verification

After the interruption, new browser tabs failed to attach and navigation returned
`net::ERR_ABORTED`; an existing tab retained its pre-rollout bundle. A zero-sized
viewport was corrected, but navigation remained unavailable. The owner was asked
to reopen this chat's Browser panel and confirmed doing so. A fresh CUA session
reset still exposed only the older tab inventory; creating a new Console tab
timed out waiting for its webview to attach, and a normal refresh did not update
the existing document. Repeating the request to reopen the page is not a useful
next step. This is an unresolved browser-automation connection limitation, not
evidence of an application defect. Native HTTP, API, database and OCI evidence
above remain valid, but do not replace these outstanding observations:

- Production customer create/edit/reload through the new UI.
- Existing deployment, knowledge, integration, Engage preview and Desk links.
- First-signup selection continuity and basic narrow layout of these screens.

## Safety And Recovery

Unrelated local work was left untouched. Existing customer runtime/app
assignments, live knowledge, provider funding and framework versions were not
changed. V222 adds metadata only. Rolling back UI/backend images does not require
destructive schema rollback; retain kit records and canonical product resources.

Private operational receipts: `~/.codex/private/loomai-kits-01056/`. They contain
fixture details and are deliberately not part of the repository.
