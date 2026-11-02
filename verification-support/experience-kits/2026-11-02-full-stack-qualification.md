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

Both original stage and production qualifiers had zero published versions,
releases, managed dependency graphs, managed vector resources and provider
allocations. Their later cleanup outcome is recorded below. No model calls or
new business-system databases occurred.

## Production UI Qualification

The Browser connection recovered on the owner's subsequent retry. The prior
connection limitation below is resolved, not an outstanding product blocker.

- Used the existing signed-in Northfield customer account without replacing its
  authentication or changing its existing deployments/assignments.
- Navigated the public catalogue and dealership detail into the three-step setup.
  Selected the actual Dealership simulator offer and customer-provided AI-state
  storage, both Engage channels, public website origin and optional Desk setup.
- Created `kit-4777f699-ae6c-45f9-bf54-e86d2166114a`, draft deployment
  `dep-4e084db3`, standalone installation
  `awi_pub_f58d250fc062f25e0d9daaec4a136fab` and embedded installation
  `awi_pub_dcdd8b92192d16a314eb95a4e12124a2` through the production UI.
- Changed the workspace title to `Northfield Journey Verified AI` and colour to
  `#28bfa6`, saved, performed a real page reload and observed the persisted title.
  The canonical Engage editor independently showed the same title/colour and
  the actual dealership tool groups in its iframe preview.
- Opened deployment setup, knowledge, connections, human support and the full
  Engage preview. Each resolved the new configuration or selected deployment.
  Knowledge/connections/Desk correctly require a target release; the draft does
  not claim to be launched, stocked, staffed or ready for AI queries.
- Verified My Kits lists the saved kit. At 390px, the navigation drawer and kit
  portfolio work, cards stack, and document width does not exceed the viewport.
  Desktop inspection used 1440px. Temporary viewport overrides were reset.
- Saved six live screenshots in `live-ui-2026-11-02/`. Kept the new configuration-
  only kit as an intentional customer review draft, not an active service.

This closes the actual production create/edit/reload/navigation gap. A brand-new
email signup was not performed in this cycle; selection continuity remains
covered by the focused auth/UI tests, not claimed as fresh hosted signup proof.
No new runtime was launched or AI query made by this configuration-layer test.

### Resolved Browser Interruption

After the interruption, new browser tabs failed to attach and navigation returned
`net::ERR_ABORTED`; an existing tab retained its pre-rollout bundle. A zero-sized
viewport was corrected, but navigation remained unavailable. The owner was asked
to reopen this chat's Browser panel and confirmed doing so. A fresh CUA session
reset still exposed only the older tab inventory; creating a new Console tab
timed out waiting for its webview to attach, and a normal refresh did not update
the existing document. Repeating the request to reopen the page is not a useful
next step. This is an unresolved browser-automation connection limitation, not
evidence of an application defect. Native HTTP, API, database and OCI evidence
above remained valid while UI verification was pending. The subsequent actual
UI observations above now supplement them.

### Nonblocking Observations

- The pre-existing expanded support dock can cover the bottom of a narrow setup
  screen. Minimizing it immediately restores the form controls; the complete
  narrow journey remains usable. Reserve bottom space or improve its initial
  collapsed behavior in a separate support-shell polish task.
- Independent delivery-mode edits in the advanced Engage editor and a fresh
  email signup remain outside this hosted walkthrough. Their absence must not
  be presented as verified behavior.

## Qualification Fixture Cleanup

- Checked the exact original Acme qualifiers again: zero releases, three total
  unused DRAFT installations, no live assignment. Kept the new Northfield UI
  qualification kit as an intentional inspectable draft.
- Normal APIs removed the original three apps, revision-checked unbound and
  removed the two original consumers, and archived deployments `dep-9b40e6db`
  (staging control plane) and `dep-1ce96b9d` (production control plane).
- Final metadata deletion jobs `del-6c675a89` and `del-e8a4088d` both FAILED.
  Read-only native database evidence identifies the same existing constraint:
  `platform_customer_offer_deployments_deployment_id_fkey`. The deletion path
  does not remove its template-bootstrap linkage before the deployment row.
- Those archived records were retained pending the normal-service fix. The
  successful follow-up below now closes K5; no foreign key was weakened and no
  manual row-deletion workaround was used.

### Cleanup Fix And Final Hosted Proof

Source: `7c1a5a631154ca366182704560090b7e16d7c3bf`.
`DeploymentDeletionExecutionService` now removes the deployment-owned template
bootstrap association through its canonical JPA repository and flushes it before
the parent deletion, within the existing deletion transaction. It does not
delete the reusable offer or customer and retains existing consumer guards.

- Local: 28 kit service tests and one offer migration test pass; compilation and
  four existing deletion tests also pass. Local Docker was initially stopped,
  so skipped PostgreSQL runs were not accepted as qualification.
- GitHub run `41539591010`: four new PostgreSQL/JPA execution tests, four existing
  deletion tests and five kit PostgreSQL tests pass; a separate report assertion
  verifies zero skips/errors/failures. Checkout is the CI-only revision
  `1d765e796a18a85dcac776be7b79f8a65461013d`; its `Platfrom/backend` tree is
  identical to image source `7c1a5a631154ca366182704560090b7e16d7c3bf`.
- Backend image run `41538946059`: success. UI/framework images unchanged.
- Staging job `gr96wrq6733fpikp4i25j803`: finished. Container
  `/aa3dylvh4stfeg0ersfzdjjm-224540804940`: healthy, exact source OCI label.
- Production job `msntu1442cegilrzke781nbd`: finished. Container
  `/sbdv2pkkqrbsy9m59034hb5j-224845832698`: healthy, exact source OCI label.
- Supported API retry of original staging failure created `del-82e418e4`:
  SUCCEEDED, no error. The production retry created `del-df8401c3`: SUCCEEDED,
  no error. Each environment's native readback is `0|0|0` for the exact original
  deployment, bootstrap association and kit metadata. Original failed and later
  successful deletion receipts remain for audit.
- The intentionally retained Northfield review kit still loads and refreshes
  in the production Browser after the backend rollout. Its draft status,
  branding, both Engage channels and requested-only Desk state are unchanged.

## Safety And Recovery

Unrelated local work was left untouched. Existing customer runtime/app
assignments, live knowledge, provider funding and framework versions were not
changed. V222 adds metadata only. Rolling back UI/backend images does not require
destructive schema rollback; retain kit records and canonical product resources.

Private operational receipts: `~/.codex/private/loomai-kits-01056/`. They contain
fixture details and are deliberately not part of the repository.
