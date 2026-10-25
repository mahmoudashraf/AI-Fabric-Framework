# 010.47 Template Bootstrap And Normal Deployment Upgrades

## Owner Decision

2026-10-25: templates provide initial configuration, not permanent product,
plugin, runtime, model, setting, or upgrade restrictions. Existing customer
deployments use the normal draft -> publish -> apply -> verify lifecycle,
including direct production updates, without a separate human approval.
Keep the deployment identity and Engage app URL. A fresh application is not an
upgrade mechanism.

Funding is release-scoped too: the server-owned funding mode and exact team
target authorization select the contract for a new release. Historical
allocations do not permanently override that explicit selection. An existing
FUNDED release remains FUNDED, including all its caps, counters, pricing and
expiry; an old release without recorded authority cannot inherit today's team
mode. Never switch funding on an inference error or from browser input.

Published versions and release evidence remain immutable historical records.
Customer/tenant ownership, assigned staff permissions, credentials, actual
runtime compatibility, data isolation, write confirmation and usage accounting
remain enforced. Removing template locks does not grant access to another
customer or expose operator secrets.

## Execution

- [verified locally] Remove post-bootstrap offer/composition locks from shared
  editing, Marketplace installation, publishing, apply and supporting services.
  Preserve offer/template records as bootstrap provenance.
- [verified locally] Remove manual apply approval requirements. Keep ordinary explicit
  user publish/apply actions, validation and verification.
- [verified locally] Restore original template settings to the editable draft on request;
  never implicitly deploy, replace credentials, delete data or change app URLs.
- [verified locally] Expose normal upgrades/reset in the customer console using existing
  configuration and version APIs, without duplicating staff editors.
- [pending] Verify changed runtime/plugin/model configuration, direct production
  apply, reset and unchanged app identity; retain negative ownership tests.
- [pending] Commit, deploy and verify the resulting customer lifecycle. Record
  any external blockers explicitly rather than creating a substitute app.

## Boundaries

Do not rewrite immutable historical offers, releases, allocations or receipts.
Do not disable authentication, provider spending enforcement or write-action
confirmation. Do not add framework changes, semantic text matching, a new
workflow engine, capacity redesign, business databases or data migration.
Reset changes configuration only; customer business systems and S3 objects
remain external integrations.

The original staging-vs-production quality evidence remains historical. The
previously authorized fresh-production route is paused by this owner decision.

## Verification Progress

- Direct production apply without an apply approval, draft-only reset and
  release-scoped funding selection: 28 tests passed together. Historical delete
  approval remains tested and enforced.
- PostgreSQL V213 migration passed: adds recorded defaults, clears only the
  obsolete apply-approval flag, preserves current draft and delete controls.
- Customer UI browser fixture covers changed composition, direct apply, reset,
  explicit verified-release assignment and unchanged app URL. Hosted proof is
  still outstanding; fixture success is not a live deployment claim.
- Combined affected-service run: 882 scenarios, 881 passed and one read fixture
  still used the former binding-selection request. Corrected that fixture to
  match the real read caller; all 19 tests in its suite passed on rerun.
  Ownership, read-only boundaries, PostgreSQL atomicity/retry, template sync and
  uninstall, storage, integrations, dependencies and apply/reset are covered.
- UI contracts: 45 tests passed. Browser fixture passed direct production
  apply, plugin editing, publish/reset and verified existing-app assignment.
  Production UI build passed. No framework source/release was changed.
- For pre-existing deployments, reset restores the earliest retained draft
  snapshot because the former implementation did not save immutable bootstrap
  defaults. New deployments record the fully composed initial defaults before
  first publication. Reset does not uninstall plugins or replace integration
  bindings/credentials; those remain separately editable customer resources.

## Customer Upgrade Path

1. Open the deployment and select **Versions & updates**, or an advanced editor
   such as Providers, Prompts, Actions or Plugins.
2. Save the changed draft, validate it and publish a new immutable version.
3. Select the production target and a compatible runtime image, then **Apply**.
   No separate apply-approval request or new customer app is needed.
4. After the release is verified, select **Use verified release** for the
   existing app assignment. The deployment identity and app address stay the
   same. Staging-first promotion remains optional, not a prerequisite to every
   production upgrade.
5. **Reset to template** restores recorded initial settings to the editable
   draft only. Review, publish and apply remain explicit separate actions.

## Hosted Progress

- `4b8367166`: backend and UI deployed and verified on staging and production;
  schema 213, exact OCI/source identity, protected history and environments
  preserved. No customer app or runtime replaced by that Platform rollout.
- Customer session live reset succeeded on the existing production deployment's
  draft. Saved pre-test draft restored using the normal draft API; live release
  `rel-572ceb27` and app assignment stayed unchanged.
- Live UI found the shared editor still requested the staff-only global target
  list. Follow-up adds an owned-deployment target projection using the existing
  authorization boundary, without credentials/provider/network/resource
  configuration. Customer UI uses that projection; staff target management is
  unchanged. Internal provisioning-environment previews remain staff-only in
  the UI. Focused backend 5 tests, customer browser smoke and UI build passed.
- Customer publish/apply/verified assignment live proof remains in progress.
- Live provider editing exposed an existing serializer bug: empty optional
  numeric fields were sent as configured overrides, causing 26 validation
  errors after saving a model change. The generic provider serializer now omits
  unset strings, preserves zero/false values and server-owned binding metadata.
  Browser regression and production UI build passed; numeric validation remains
  unchanged. This is form serialization, not an LLM policy or fallback change.
- Existing customer's model draft now validates with zero errors and published
  as `ver-fe6de5ea` / v2. This version still records framework 0.8.41; it is not
  evidence of adopting the separately qualified 0.8.42 canary.
- Target listing's outer transaction caused an excluded target's authorization
  denial to mark the entire list rollback-only. Removed the outer transaction;
  each existing authorization boundary completes its own short transaction.
  Real customer-session HTTP tests now verify scoped listing, continued denial
  of the global staff target API and rejection of anonymous access. All six
  focused tests passed. No authorization check was removed.
- The first real browser Apply preflight rejected `Idempotency-Key`, so no
  release was created. Added this standard header to the explicit CORS header
  list, keeping origins and credential policy unchanged. Six focused real HTTP
  tests passed, including trusted-origin preflight, untrusted-origin denial,
  customer scope and direct operator apply without manual approval.
