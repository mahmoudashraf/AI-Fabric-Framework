# 010.64 Smart Brain Workspace Implementation Record

Date: 7 November 2026
Status: **Deployed and hosted UI verified on staging and production.**

Plan: [010.64 outcome-led workspace](010_64_SMART_BRAIN_OUTCOME_LED_WORKSPACE_AND_NAVIGATION_REDESIGN_PLAN.md).

## Scope Kept

The owner approved the Smart Brain redesign only. Existing Home/resource lists, Kit pages, Engage app configuration/preview/domains, Support/requests, Account/team, notifications and authentication page designs remain in place. Entry links now open the resource workspace rather than independent technical configuration pages. The same workspace is available to authorized staff; staff-wide administration remains separate.

No backend, database, runtime, framework, model instructions, AI routing, retrieval policy or release mechanism was changed. The preview reads existing authorized data but blocks live resource writes and AI calls.

## Completed Locally

- One `/smart-brains/:brainId/...` hierarchy, resource sidebar, breadcrumbs, target context and fixed task footer.
- Path identity wins over a stale query-string/global Brain selection. Exact target IDs survive navigation. Invalid/unavailable resources and targets do not silently select another.
- Customer targets use the deployment-scoped customer API; staff targets use the existing staff target catalogue. The initial permission mismatch discovered during checking was corrected rather than bypassed.
- Authenticated routing supports the existing data-router navigation guard for both customer and staff. A normal local Brain deep link can return after sign-in; unrelated sign-in layouts and product starts remain unchanged.
- Accurate shared-draft and target-applied labels, without the previous `Live draft` string. Loading or failed environment lookup is not shown as a successful deployment state.
- Outcome Overview, Knowledge/source lists, document tasks, live connections, Channels, Human support and Try views.
- Responses with four existing server-defined profile groups, illustrative preview, canonical composition/save, Instructions and focused Advanced views.
- Capabilities Connected/Available list-detail views, metadata-driven task inputs, existing install forms and retained technical action/route/webhook editors. No domain-specific behavior entered the shared UI.
- Technical settings index and focused model, storage and technical-file editors. Deeper access/delivery settings link to their owning action editor rather than duplicate configuration.
- Updates with target-specific comparisons, readiness, explicit confirmation, canonical publish/apply/verify progress, history and existing advanced version actions. A successful publish remains available if apply fails; retries do not silently republish.
- Existing dirty editor protection plus shared Save/Discard actions. A background release change does not remount a task and discard its state. Dirty/busy tasks retain their explicit release context until finished or discarded, with a visible newer-release notice. Test results retain their original scope; they are not reused as another release's conversation.
- Source/storage operations retain their explicit separate commands. Scoped storage forms participate in the navigation guard; the selected environment is reused instead of prompting for another target.
- Support remains the real existing support pack. It starts minimized on entry to the Brain workspace and respects the measured footer inset, without deleting conversations or changing support on other pages.

## Local Preview

The read-only development service listens at:

```text
http://127.0.0.1:4398/
http://127.0.0.1:4398/smart-brains/dep-fb1094af/responses
```

Use normal Console sign-in. Only resources allowed by that account are available. A local URL does not grant access to the named resource.

Start it again from `Platfrom/ui` with:

```sh
node scripts/smart-brain-preview.mjs
```

The development proxy defaults to the existing production API, with **resource writes and provider calls through the preview blocked locally**. Authentication and preferences continue through their normal contracts; profile composition is a read-only calculation. Saving, installing, uploading, publishing, applying and querying AI cannot modify the serving installation through this review server. The Try view does not embed a live app in this mode. External app and Desk links still open those existing live services separately; they are clearly outside the preview's write protection. `PLATFORM_PREVIEW_API_URL` can point at another existing HTTPS Platform API. Do not enable `PLATFORM_PREVIEW_ALLOW_WRITES` against production merely for visual review.

The browser write-workflow harnesses use isolated memory fixtures. Those tests exercise actual UI components and API wrappers but do not claim that a new hosted release was applied.

## Verification

- Final pre-rollout rerun: production build and **113 targeted regression checks passed**, with no failures or skips.
- Production UI build passed, including TypeScript and the Engage preview build. Existing bundle-size and runtime-config build warnings remain; they are not suppressed.
- Combined Brain/profile/configuration/product/navigation suite: **70 checks passed**, including the focused Settings browser cases. Two additional targeted regressions passed for background-release retention and permission/route contracts after integration review.
- Existing hosted-storage/source-scope suite: **15 checks passed** after retaining its original shared-component integration contract.
- Existing support-pack/auth/session-continuity suite: **26 checks passed**.
- Responses browser harness: **19 isolated scenarios passed**.
- Capabilities browser harness: **21 isolated scenarios passed**.
- Updates browser harness: **14 isolated scenarios passed**.
- The three isolated harnesses cover save failure/retry, stale changes, retained unknown fields, read-only actors, explicit installation/publication, apply retry without republish, cancellation and responsive light/dark layouts.
- Actual authorized API-backed local checks passed for a staff account on Northfield and a real Supabase customer account on Formal Support. Main sections and fourteen deeper routes fit the desktop frame with zero document-level horizontal/vertical overflow and a visible footer. Basic mobile navigation and light mode were checked.
- API-backed visual checks performed **no live resource writes, releases or AI calls**. Existing runtime/connector permission or connectivity errors remain visible; this redesign is not evidence that an unrelated integration defect was fixed.

Read-only reports and screenshots: [verification directory](../../../../../../../../verification-support/smart-brain-workspace-01064/README.md).

## Important Boundaries

The existing technical POC query API addresses the Brain's current serving target, not an arbitrary selected environment. Try it verifies the target/version/endpoint correspondence before using that API and otherwise directs the user to the actual assigned Engage experience. No staff credential or alternative target is substituted. Prompt-only previews remain seven-string overlays, not full-draft runtime simulations.

Normal action permissions and confirmation remain in effect when a real experience is eventually tested. No mock scenario is advertised as a production action or a successful indexing operation.

The guided action-definition builder is not invented by this redesign. Installed capability forms are used when provided; full existing action JSON, routing, anonymous-access and webhook controls remain in a focused technical view.

The existing Desk activation API is customer-only. The same Human support page therefore gives authorized staff its operational channel view and an explicit owner-only activation notice, rather than calling that API with a staff credential and producing 403 errors. Extending staff activation on a customer's behalf would be a separately reviewed backend permission feature, not a UI workaround. Staff Engage links pass the actual installation ID expected by their existing editor.

## Hosted Rollout

The owner requested deployment and verification after local review. Rollout is UI-only, through the existing CI immutable-image and Coolify path: staging first, then production. Backend/runtime versions and customer configuration remain unchanged. Hosted checks cover customer/staff navigation and rendered data; isolated browser harnesses cover write workflows. No customer publish/apply or business write is needed merely to prove this visual redesign.

### Completed 7 November 2026

- Source commit: `3013df7f433899da36bab743c81c5a7c9f8505f7` (`Platform-V11`).
- [CI run 42373316258](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42373316258): successful; UI image published, backend build skipped.
- Image: `ghcr.io/mahmoudashraf/loomai-platform-ui:3013df7f433899da36bab743c81c5a7c9f8505f7`.
- Staging Coolify deployment `n7aey2g0yl0zr9t0hj6s2tf1`: finished; exact revision healthy.
- Production Coolify deployment `dys7pw6ca281h98ljd25xgpt`: finished; exact revision healthy.
- [Production Console](https://console.loomai.pro/deployments) and [staging Console](https://loomai-platform-ui.46.224.145.148.sslip.io/deployments) serve the new resource workspace.
- Hosted browser checks: **55 route checks** (23 staging staff, 23 production customer, nine production staff), all with zero document-level horizontal/vertical overflow at the normal 1280x720 desktop viewport.
- Production deep-link reload, old customer `/prompts?deploymentId=...` redirect, retained target IDs, light/dark display, basic 390x844 mobile navigation/footer, and unchanged staff-wide administration passed.
- Staging unsaved-edit warning, Keep editing and Discard passed without saving the temporary change. No captured browser console errors remained in the verification tabs.
- Backend containers remain healthy on `baf6ea3328a1ef72d83d01552ffbc02da1582105`; no backend/runtime deployment was performed.

Hosted verification deliberately did not save Brain configuration, install capabilities, upload documents, publish/apply a Brain version, run business actions or send AI queries. The isolated browser harnesses above qualify those UI command flows without changing customer resources. This is a UI rollout qualification, not a new end-to-end runtime release gate.

Hosted reports and screenshots: [live evidence](../../../../../../../../verification-support/smart-brain-workspace-01064/README.md#hosted-rollout).

## Staging Preparation And Go-Live Consolidation

Owner-approved follow-up on 7 November 2026: the old `step=staging` page is a
release-preparation task, not an AI conversation test. Keep that distinction in
the customer journey:

1. Kit remains the project home. Its assistant links open the existing Smart
   Brain workspace, not a second configuration or release system.
2. Smart Brain **Updates / Prepare staging** owns shared-draft comparison,
   explicit publication, compatible runtime selection, apply and verification.
3. **Connections** reuses the existing customer external-service and AI-state
   binding forms. They inherit the exact workspace target; no second environment
   selector or implicit alternate target is introduced. Credentials and binding
   operations participate in dirty/busy navigation protection.
4. **Connect Engage app** reuses existing assignment creation/configuration and
   exact verified-staging-release binding. Existing production assignments are
   not silently moved back to staging.
5. After a release is verified, **Try your AI** names that exact target/release.
   Try it still requires an active, ready app assigned to that release; it does
   not substitute a production app when staging has no app.
6. **Review go-live** opens production Updates / **Promote staging**. The existing
   practical-promotion APIs, reviewed candidate, document selection, readiness,
   production verification, explicit activation and assignment rollback remain.
   Publishing/updating a version is not the same action as activating an app.
7. Old staging/production setup links resolve through the owned portfolio and
   authorized target API. Missing targets remain visible errors. Multiple
   production targets require an explicit choice instead of selecting the first.

The focused panels reuse `CustomerDeploymentConsole` command logic without its
old resource picker, setup stage bar, creation form or global navigation. The
existing creation/configuration/retention views and staff version controls remain.
No backend/runtime/framework, AI settings, permissions, infrastructure or database
contract is changed. The rejected 010.65 global redesign is not reintroduced;
the independent staging developer profile stays intact.

Local verification covers the legacy staging redirect, scoped connections,
assignment selection, absent exact-release test app, production selection and
retained readiness/activation controls. Existing internal-support connection and
readiness findings remain visible; this UI work does not claim to resolve them
or qualify a fresh runtime promotion. Hosted rollout status and screenshots are
recorded separately in `verification-support/smart-brain-release-journey/README.md`.

### Consolidation Hosted Closeout

Source `e65525d4d3f28c425526961aca53e6c2b664d2a3` is published on
`Platform-V11`; CI `42390937952` passed (UI only). Staging and production
Coolify jobs finished, and both native UI containers were verified healthy on
that exact source. Hosted customer navigation, target inheritance, assignment
controls and explicit production/readiness UI passed without changing customer
configuration, releases or app bindings. The test account was signed out on both
hosts. The verification record preserves the observed transient Support iframe
cleanup warning; it was not hidden or expanded into a separate transport rewrite.

## Approved Account Workspace Follow-Up

The owner separately approved redesigning customer Account / Team & access after
reviewing its visual proposal. Use the same restrained resource-frame concepts,
but give it its own `/account/...` hierarchy: My account, Your team, LoomAI
collaboration and Sign-in & security. Team and staff-collaboration access are
workspace-wide, not selected-Brain permissions. Preserve a Back to Smart Brain
link without inheriting its environment/draft/release controls. The staff-wide
Users administration page remains unchanged. This does not reinstate the rejected
customer-wide redesign.

Implementation, existing authentication limitations and qualification are tracked
in `verification-support/customer-account-workspace/README.md`.

## Approved Management Overview Follow-Up

The owner approved replacing the legacy customer `view=manage` page with the
resource-first Smart Brain overview. This retires a duplicate navigation surface,
not its release/setup capabilities. Keep Brain creation at the portfolio entry;
Knowledge, AI capabilities, Responses, Channels, Human support and Updates own
their existing focused workflows. Updates continues to reuse the filtered
customer command component; do not remove its connection, assignment, promotion
or activation logic when retiring the full-page view.

Show real draft/environment/app status and one contextual next task. Never infer
verified readiness from a merely configured draft, or count a different release's
Engage app as this environment's connected experience. Preserve explicit target
and release context on legacy deep links; unknown ownership/targets are errors,
not reasons to open a different resource. Keep the overview compact on desktop,
with ordinary scroll access on small screens. No broader customer/staff shell
redesign, backend contract or operational policy change is part of this follow-up.

Qualification: `verification-support/smart-brain-management/README.md`.

## Approved Settings Navigation Correction - 8 November 2026

The mixed Technical settings directory is retired. AI models is a Brain sidebar
entry; embedding/vector configuration belongs to Knowledge -> Search settings;
action access/webhooks stay with AI capabilities. Advanced is pinned below the
scrollable resource navigation and expands its addressable child tasks. Old
settings URLs are adapters only, preserving exact Brain/target/release context.

The same provider editor serves each focused task; no new configuration store or
duplicate editor is introduced. Structured Advanced siblings retain their mounted
buffer for Probe current edits. Raw JSON, target/release changes, busy operations
and leaving the editor retain the shared navigation safeguards.

Source `1cb4b5b899eda855786c5c4adbcdf7e9713a624d` is deployed and healthy on
staging and production. UI-only CI passed. 129 focused checks, the full-shell
settings browser and existing Knowledge/capabilities journeys passed. Hosted
navigation/themes/basic mobile passed without customer configuration changes.
Evidence: `verification-support/brain-settings-navigation/README.md`.

## Approved Knowledge Clarity Follow-Up - 8 November 2026

The owner requested source counts, view-specific empty states and clearer
selected-view wording, plus focused Search settings. All sources, Documents and
Live data remain filters over the same controllers. Counts are loaded source
records, not vector/record totals; unavailable reads never become zero sources.
Relevant titles/actions and search clearing distinguish the three views.

Search settings now exposes Search service, Embedding model, Service connection
and Customer separation in a bounded resource task layout. It reuses all existing
provider fields and one save buffer. Connection inputs stay mounted across task
switches; unknown settings survive focused saves. No default configuration is
presented as loaded customer state after an initial read failure.

Verification and rollout status: `verification-support/knowledge-source-experience/README.md`.
