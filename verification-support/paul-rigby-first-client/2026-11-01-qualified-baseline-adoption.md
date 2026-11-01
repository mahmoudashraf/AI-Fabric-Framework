# Dealership Qualified Baseline Adoption

Date: 1 November 2026.
Status: IN PROGRESS. Owner approved adoption of the recommended existing
framework release and tuning, not a new framework patch or runtime downgrade.

## Scope

Preserve Northfield deployment `dep-3c4dbe4f`, existing production Engage app,
customer/tenant bindings, current product features, sources, auth and Desk.
Build the current maintained runtime against published AI Fabric0.8.42.
No old canary image, lexical routing, hidden query rewrite, weakened trust
boundary or unconditional search-all. Missing-source recovery remains explicit
and bounded to the configured two authorized sources.

Carry the qualified settings in normal versioned deployment configuration:
GPT-5.4-mini unchanged; source overview enabled; ITERATIVE/maxIterations2;
RAG_IF_ACTIONS_INSUFFICIENT; threshold0.4;8context documents/8,000characters;
planner12,000characters; action evidence8,000characters/10items. These are finite
operator-owned budgets, not semantic hard validators.

## Execution

- [x] Upgrade dependency/metadata defaults and qualify current consumer from
  a new empty Central-only cache, including runtime, connector, accounting and
  packaged library identity. No framework source/release change.
- [ ] Implement and test generic versioned READ evidence-budget configuration,
  preserving existing no-override behavior. Publish only affected images.
- [ ] Publish and apply one normal version to the existing staging target.
  Read back actual framework, prompt and effective budgets. Qualify the original
  20 scenarios, selected-context UI behavior and repeated two-car follow-ups.
- [ ] If staging quality qualifies, apply the same version/image through normal
  production artifact promotion/apply/verify and update the existing app binding.
  Verify real published workspace behavior; do not substitute infrastructure
  readiness for semantic quality.
- [ ] Record actual outcomes/remaining gaps, retain failed diagnostic evidence,
  and remove only disposable test resources created by this task.

## Baseline And Evidence

The previous production runtime is framework0.8.41, source63888284, digest902a5d00,
version `ver-73bc5b03`, release `rel-4163ea35`, binding revision12. Staged v7
`ver-dcdb7f61`/`rel-7b983289` removed the domain question but did not qualify
useful follow-ups and was not assigned to production.

The [root-cause record](2026-11-01-domain-routing-correction.md) explains why
restoring and qualifying the existing0.8.42/tuning baseline comes before any
new conversational-context framework work. Historical20-case scores are not
new acceptance evidence. Fresh results and exact artifact/release identities
will be appended as each step completes.

## Local Qualification

- PASS:887 current-consumer tests, zero failures/errors/skips: accounting78,
  guarded transport103, generic connector88, runtime582, vectorization core11,
  runner21 and embedding worker4. New cache began empty and used a Central-only
  mirror; no sibling framework install or old runtime image was consumed.
- PASS:20 packaged public framework libraries exclusively0.8.42; core,
  execution and curated-default packaged bytes independently match Central.
  The two private accounting libraries retain their own1.0.0 product version;
  an initial package-check harness assertion incorrectly included them in the
  framework set, then was corrected to distinguish those exact product JARs.
- PASS:126 focused Platform tests:76draft validation,50provisioning; finite
  budget types/ranges, exact environment mapping, no-override preservation and
  mode/authority isolation. Whitespace check passed. No semantic validator added.
- A normal staging connection/app draft was created for the same deployment on
  the existing shared Engage frontend: `northfield-staging.apps.loomai.pro`,
  installation `awi_pub_2a877305f6066b698c0ba3bf1dbd7455`. Its origin is added
  through the normal managed security projection. No runtime, database, vector
  service or customer was created. App is not activated yet; production binding
  remains unchanged.
