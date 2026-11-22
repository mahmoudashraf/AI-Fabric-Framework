# Configuration Assistant Preparation Scope

## Status

Implemented, deployed and verified on staging and production. Final source:
`1cad1d5bda04e47daa764d12f671f4f3206b7a84`. Both Platform backends and shared
configuration-assistant runtimes use this source. The public framework remains
AI Fabric 0.8.62. This closes the bounded preparation-scope fix, not every
assistant quality issue or external API integration.

## Confirmed Finding

The earlier failure returned a valid `NEEDS_INPUT` envelope, not malformed JSON,
an authentication error or missing documentation. It acknowledged the single
documented public endpoint, but asked whether to configure that or additional
undocumented operations. Five comparable recorded Prepare turns saved the
requested definition; one unnecessarily requested scope confirmation. This is a
small observed sample, not a measured long-term failure rate.

The previous preparation prompt asked for essential clarification but did not
explicitly establish the selected documentation as the operation scope. The
existing feedback loop correctly stops on a valid missing-information decision.
Increasing correction rounds does not address that semantic decision.

## Implementation

Private source `3f5ac84696685c4515e594fd0558bb93c15e0e2f`:

- Explicit ACTIONS/PREPARE instructions use the supported operations in selected
  documentation, narrowed by selected operations, editor definitions or the
  user's focus hint. One fully documented operation is sufficient.
- Platform supplies `catalogue.preparationScope` using formal command/task/ID
  values, not prose matching. Its operation selection is
  `ALL_SELECTED_SOURCE_OPERATIONS` or `SELECTED_OPERATION_IDS`.
- Existing selection filtering still excludes deselected sources/operations.
  Ordinary follow-ups do not inherit preparation scope.
- Missing nonessential labels can be ordinary draft choices with assumptions;
  missing material endpoints, inputs, bindings or conflicting requirements
  remain focused questions. No raw credentials should be requested in chat.

The first staging batch passed all six complete/selection cases and correctly
asked for a missing provider host. However, the missing-provider-credential case
saved a partial definition and route while placing the absent credential in
assumptions. It did not invent a credential, call an API or publish anything,
but did not satisfy the required missing-information behavior. That sample is
retained as FAIL. All its temporary sessions and Brains were deleted normally,
and the customer signed out. This source was not released to production.

Follow-up private source `f15b84e8597260aba9cc605703a04044b1d7785d` explicitly tells
the model to return `NEEDS_INPUT`, a focused connection/secret-reference question
and no changes when documented provider authentication has no applicable existing
authorized binding. Caller identity, `anonymousAllowed:false` and the connector's
inbound key are not upstream provider authentication. This is prompt guidance,
not a new semantic validator or automatic override of model output.

The follow-up staging batch correctly stopped for both missing facts and passed
the single-endpoint repeats, subset and focus cases. It saved only one of two
operations in the all-selected case, so that batch also remained FAIL and was
not promoted. Its original results and normal cleanup are retained separately.

Private source `abb7b4e073f51da176a125963c5934969408fc2c` adds the actual parsed,
selected operation IDs/methods/paths and their count to preparation context. The
model is told to cover each selected operation unless the user narrows the
scope; one-operation sufficiency applies only when that is the selected scope.
Prose sources without parsed operation metadata still use their supplied text.
No model-generated operation selection or omitted operation is rewritten by code.

That candidate's staging batch passed all three two-operation repeats, subset,
focus and genuine missing-information cases. One of three complete public/no-auth
prose repeats incorrectly asked for a provider credential; the other two saved.
The batch remained FAIL, was cleaned up normally and was not promoted.

Private source `1cad1d5bda04e47daa764d12f671f4f3206b7a84` balances the provider-auth
guidance with an explicit positive case: documented public/no-auth operations
need no upstream credential or hypothetical authentication. Runtime, workspace,
caller and connector inbound access controls remain separate and unchanged.

## Preserved Boundaries

- No public framework change; public libraries remain AI Fabric 0.8.62.
- No new hard validator, question-text matcher, answer rewrite or forced retry.
- Valid `NEEDS_INPUT` remains a stop, not an excuse for another hidden model call.
- Existing maximum three attempts, one accounted call per runtime request and
  overall deadlines remain unchanged.
- Ownership, scoped references, allowed draft paths, validation, preview, undo
  and explicit publication/application remain unchanged.
- No UI redesign, Brain release, Desk change, infrastructure resizing, new
  infrastructure resource or billing/capacity change is part of this fix.

## Qualification

Focused tests on all four source iterations: 87 Platform and 72 runtime tests passed,
without failures or skips. They cover typed preparation intent and selected
scope, exclusion of other operations/sources, ordinary follow-up isolation,
one-call behavior, structured-output handling and missing-information stops.

The final candidate has ten cases declared in advance per control plane:

1. Three independent actual-UI repeats of the original complete single-GET
   prose document, empty hint and Prepare capabilities button.
2. Three independent repeats covering both READ operations from a selected
   OpenAPI document.
3. Only an explicitly selected operation from that document.
4. Only the operation requested by a narrower user focus hint.
5. A genuinely missing provider base URL.
6. A genuinely missing provider credential reference.

All use normal Supabase customer authentication. Every model attempt and result,
including failed samples, is retained. No case-level retry-until-pass is used.
Complete cases must save the expected READ definitions/routes and preserve
unrelated draft sections. Missing-information cases must ask an actionable
question in one attempt and leave all configuration unchanged. Nothing is
published, provisioned, indexed or executed against a fictional endpoint.

These are preparation-scope and missing-information checks, not qualification
that every response mapping or external connector is executable. Generated draft
validation is not proof of live upstream connectivity. Model narration is not a
receipt: the first multi-operation result incorrectly said one action while the
actual saved receipt contained the requested two.

Separate pre-existing finding: some generated optional `category` filters use
`askUser:false` (one also used `visibility:INTERNAL`). The existing parameter
catalogue states that optional user-provided inputs should remain extractable;
optional is not the same as internal. The same `askUser:false` appears in four
retained pre-fix samples, so this is not presented as a regression introduced by
the preparation-scope change. It is recorded separately, not silently rewritten
or folded into this bounded scope fix. The scope checks observe these fields but
do not claim complete action-parameter or connector-execution qualification.

## Final Hosted Results

Both environments passed all ten predeclared cases on the final source, with
one model attempt per case:

- Three public/no-auth actual-UI preparations saved one READ action each without
  an additional scope or credential question.
- Three full-scope OpenAPI preparations saved both requested READ actions.
- Explicit operation selection and a narrower user hint each saved only the
  intended operation.
- Missing provider host and missing required credential reference each returned
  a focused `NEEDS_INPUT` question and saved no changes. The latter explicitly
  requested a reference, not a secret value.

All sixteen successful preparations preserved the unrelated configuration;
all four missing-information cases preserved the entire configuration.
Connector inbound authentication remained unchanged. Desktop and 390px mobile
UI checks found no new overflow or JavaScript errors. The final live sample is
20/20 for these bounded checks, not a guarantee of future model behavior.

The three earlier candidate batches remain FAIL in their original evidence
directories. Only the final candidate passed staging qualification and manual
review before production rollout. There was no retry-until-pass within a case.

Final CI runs `45050645934` (Platform core images) and `45050646013` (runtime)
both succeeded. Runtime CI passed 821 tests without skips, plus the required
78 PostgreSQL budget and 103 guarded-transport checks. Local focused suites
passed 87 Platform and 72 runtime tests on every source iteration.

Deployed image digests on both hosts:

- Backend: `sha256:db6b697e12cc760328493876e3a6ba97a00613abecfd8ef77d262a26a0cf37fd`.
- Assistant: `sha256:33a17470ee67ab6c6652dc79c3f47eccf0f032b27e5e5904201cc5701c23854e`.

All four updated containers were healthy with zero restarts. Both assistant
containers contained the approved 20 public 0.8.62 JARs, hash-matched to Central.
The UI remains at source `5404944ba7f2d59cbb6af3fd584972df35ecfce2`; ordinary
Brains and Desk were not redeployed. Database schema remains 241.

## Preservation And Cleanup

Final audit passed on both hosts: all original Brain ownership, configuration,
versions and releases were preserved. All 72 Coolify application records remain;
13 staging and nine production stopped applications stayed stopped. Existing
routes, resource limits and unrelated image references were unchanged. No
infrastructure, capacity, billing, model or credential changes were made.

Every temporary authoring session and resource-free test Brain was deleted
through the normal APIs. Both customer browser sessions signed out. Both
task-owned normal staff sessions were revoked, and their old cookies were
verified unauthenticated. Required test, browser, rollout and audit processes
closed. The existing video gallery on port 8766 was untouched.

Private evidence root:
`~/.codex/private/loomai-assistant-scope-20261122/`; corrected-source evidence is
under `public-auth-distinction/`. Original failures remain in the parent root,
`auth-guidance/` and `operation-coverage/`; no failing sample was overwritten.
Final preservation and session-revocation proof is
`public-auth-distinction/final-proof.private.json` (2026-11-22 17:55 UTC).
