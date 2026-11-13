# Approved Console Policy-Source Access

## Scope

The owner explicitly approved adding the existing `PLATFORM_PROXY_SESSION`
mode to Northfield's approved-policy source, with staging verification before
production. This supersedes the approval-pending checkpoint in
[the earlier quality record](2026-11-13-follow-up-field-evidence.md).
It is not approval to append that mode to every customer source or publish
global plugin/template changes.

Northfield version `ver-fcd3ee7b` / v15 was published once from `drf-e620ed89`.
The current editable draft is `drf-91912e48`. Persisted-version inspection proves
the exact one-field source change: the three existing modes remain and Console's
mode is added. Source filters, `PUBLIC_APPROVED`, handles, credentials, actions
and eight other configuration sections are unchanged. Authored prompts are
unchanged; the normal compiler-generated source catalogue reflects the new mode.
No experimental evidence guidance was adopted.

## Platform Contract Correction

The staging Console query now retrieves the approved policy. The document
management API, however, rejected the same source configuration with
`Document source authModes contains an unsupported mode.` Its audience mapper
recognized the customer-facing runtime modes but not the existing Console mode.
The runtime and manifest contracts already support that mode.

The bounded backend change recognizes Console as a management transport, without
adding a customer document audience. Existing public/authenticated audience
derivation, reviewed-audience intersection, ownership, selected target/release,
operation scopes and signed authority remain enforced. Console-only sources still
fail without customer audience evidence; unknown modes still fail. No runtime or
framework implementation is changed.

Focused checks passed:

- Document operations: 47 tests, including Console transport alongside each
  existing mode, unchanged mixed-audience write requirements, Console-only
  read/write denial and unknown-mode rejection.
- Console query/trace: 41 tests.
- Target context: 25 tests.
- Runtime authority: 46 tests.
- Existing runtime search-source registry: 23 tests, unchanged runtime code.

The first runtime test attempt could not initialize Mockito instrumentation
under restricted local process permissions. The same offline tests passed with
process access; the failed XML and passing XML remain separate private evidence.
This was not repaired by changing a test expectation or runtime behavior.

## Verified Rollout

Completed 13 November 2026, 13:34 UTC. Staging `rel-3618a1fb` and production
`rel-712024cd` are both `APPLIED_VERIFIED/PASSED` on `ver-fcd3ee7b`. The same
existing Engage consumers are bound through the normal API. Staging qualified
before the production apply was requested.

Backend source `bc7fc16ffe5c7655effa3d4a4fc96b86d10bf89b` is native-verified
healthy on both control planes, digest
`sha256:015cb0a97a77c7f73f8037da3a04e695c8db1810b191c6bb695086bfdf3de035`.
[Backend-only image CI](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/43430949751)
passed; the UI image job was skipped. Staging's authenticated API also passed
before deploying the backend to production. No database migration was added.

Four actual chat queries were submitted once: one ordinary Console query and
one anonymous public-workspace query per environment. Both Console queries used
the applied prompts without a preview override. Their actual traces show the
approved policy source eligible, attempted and successful, returning three
documents. All four answers correctly stated a valid driving licence and a
30-minute test drive, with the source's advance-booking, accompaniment and
availability/checks qualifications. Public answers cited their target's existing
active test-drive document. No business action executed, inference was not
replayed, and no synthetic response was used for acceptance.

The initial staging public check stopped at its read-only document-list
prerequisite before browser launch or a model query. It was not bypassed: after
the backend correction, the same document-management prerequisite succeeded and
the previously unexecuted public UI case ran normally. Both public workspaces'
desktop/mobile screenshots were inspected, with zero page errors and basic
viewport checks passing. These checks are not a new full sign-in, handover,
write-action, accessibility or all-mobile-interaction qualification.

## Preservation And Boundaries

Both environments retain their original runtime, connector and AI-state database
identities. Native image inspection matches the exact already-qualified 0.8.52
runtime image, digest
`sha256:fdba473452bf2e1ca681ea0bb30e6291e3b3844c59d22849a9704597cbd62910`,
source `0089818162f8805bc58578dc20149dec9a93d237`, artifact `dsa-f8ef9102`.
No framework release, runtime rebuild, duplicate infrastructure or customer
inventory database was needed. The version declares `reindexRequired=false`;
no indexing, vector copy, document upload, S3/IAM or model-setting change occurred.

The original staging and production standalone apps and production embedded
installation remain `ACTIVE` on their respective new releases. Public policy
behavior was exercised through standalone apps; the embedded installation's
identity, status and assignment were checked, not a second embedded UI journey.
Unauthenticated Console access still returns HTTP 401. All nine other saved
configuration sections, including authored prompts, security, provider, actions
and behavior, match the pre-change draft. The approved source addition is the
only authored configuration difference; its prior modes and all filters remain.

The publication helper initially expected full configuration in the summary
response. Publication had succeeded; the actual saved version was inspected and
its checkpoint recovered without publishing again. That verification also
accounts for the compiler-generated source catalogue update. Neither helper
finding is a reason for a framework release.

Private evidence: `~/.codex/private/loomai-followup-20261113/`.
`policy-preview-complete.private.json` is the final completion proof; the rollout
state and per-environment Console/public/qualification records are beside it.
Do not replace that state with the older temporary directory's copy or replay
publication/apply. This closes the approved Console policy-access gap, not all
parent optimisation work. Richer S3 policies, accepted template defaults and
measured model-quality limits remain separate workstreams. No global source-mode
rewrite or plugin/template version was published.
