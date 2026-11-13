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

## Rollout Checkpoint

Staging `rel-3618a1fb` is `APPLIED_VERIFIED/PASSED` on the existing 0.8.52
artifact `dsa-f8ef9102`. Its original Engage consumer is bound normally.
One actual Console query, using the applied prompts without an override, returned
the correct driving-licence and 30-minute policy facts. The trace confirms the
policy source is eligible, searched and successful, returning three documents.

The public UI check stopped at its read-only document-list prerequisite before
opening the browser or sending a model query. Finish that previously unexecuted
check after deploying the backend contract correction. Production remains on
`rel-0139e718` until staging qualifies. No duplicate runtime, inventory database,
vector copy, source upload or new image for the customer runtime is required.

The publication helper initially expected full configuration in the summary
response. Publication had succeeded; the actual saved version was inspected and
its checkpoint recovered without publishing again. That verification also
accounts for the compiler-generated source catalogue update. Neither helper
finding is a reason for a framework release.

Private evidence: `~/.codex/private/loomai-followup-20261113/`.
The authoritative rollout state is `policy-preview-rollout.private.json` there,
not the older temporary directory's copy. Parent optimisation work, richer S3
policies, accepted template defaults and measured model-quality limits remain
separate workstreams.
