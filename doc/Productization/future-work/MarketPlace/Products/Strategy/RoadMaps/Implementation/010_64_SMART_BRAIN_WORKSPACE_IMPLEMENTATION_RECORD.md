# 010.64 Smart Brain Workspace Implementation Record

Date: 7 November 2026
Status: Local implementation verified; hosted rollout authorized and in progress.

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

Exact source, CI, deployment and hosted verification results will be recorded here after readback.
