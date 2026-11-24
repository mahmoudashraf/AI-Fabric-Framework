# Smart Brain Configuration Form Coverage

Audit date: 22 November 2026. Scope: supported customer-owned Brain configuration,
not every framework property, diagnostic field or staff infrastructure setting.
The approved 010.64 workspace and existing draft APIs remain authoritative.

## Retrieval Controls Added

Canonical home: Smart Brain > Knowledge > Search settings > Answers & sources.
Direct entry: `/smart-brains/:brainId/knowledge/search?searchTask=answers`.
Keep the selected `targetProfileId` when linking from another Brain screen.

The main form owns these existing provider overrides:

- `orchestrationModeOverrides.executor.readActionResolution.ragCooperationMode`:
  Automatic, tools + knowledge, or tools independently. The last option stops
  automatic read-tool/RAG cooperation; it is not a global RAG-disable switch.
- `orchestrationModeOverrides.executor.rag.fanoutEnabled`: bounded multi-source
  search, using only authorized sources.
- `orchestrationModeOverrides.executor.rag.maxSpaces`: source limit, 1-12.

Advanced read planning & context limits owns:

- `readActionResolution.planningMode`: OFF, SINGLE_PASS or ITERATIVE.
- `readActionResolution.maxIterations`: 1-5.
- `readActionResolution.maxPlannerContextChars`: 1-2147483647.
- `readActionResolution.maxActionEvidenceCharsPerAction`: 1-2147483647.
- `readActionResolution.maxActionEvidenceItems`: 1-100.
- `executor.vectorSpaceSelectionRequired`: planner source selection, not a
  request for the end user to choose an internal domain.
- `executor.knowledgeBaseOverviewEnabled`: source overview for planning.

The five `readActionResolution` paths above are relative to
`providerConfig.orchestrationModeOverrides.executor`. These are all ten supported
executor retrieval/read-planning leaves previously missing form controls.
`ragRequiredWithReadActions` is a derived runtime diagnostic, not another setting.
No editable authorization allowlist is introduced.

## Existing Coverage Reviewed

010.73 software slice deployed on staging/production, 24 November 2026:
Search settings now also owns the
optional Meaning / Words and meaning profile, candidate/result/context bounds,
failure policies and optional Cohere model/consent/limits. Existing values and
unknown fields are preserved when the profile is disabled. Keys & secrets owns
the separate required customer reranking key; exact selected-target allowance
readback is read-only and unknown is not zero. Hybrid readiness is qualified
only for the implemented Weaviate adapter, not inferred for every vector store.
Build with AI uses a typed SEARCH task and the same provider draft API; it cannot
create external-processing consent, keys, financial grants or quality evidence.
See 010.73 and its dated verification record for actual publication/adoption.

The source-level Evaluate view has three explicitly started search variants and
a comparison inspector gated by matching experiment/model provenance. It never
multiplies one run into three paid jobs. Optional expected-evidence forms use
exact source/version/excerpt IDs, relevance grades and explicit human review;
AI may propose labels but cannot attest its own new/changed labels as reviewed.
Older runtimes must advertise both non-default variants and label support before
their controls become editable. Existing history/labels remain readable.
No JSON editor is required for these fields. Shipped controls are not proof of
completed Ragas calibration or entity/mixed-corpus evaluation. Search/reranking
defaults were not promoted; both qualified targets finish with reranking off.

Absent profile result/context limits inherit the Brain's existing prompt limits;
enabling a profile must not silently replace them with the form's examples. The
immutable serving artifact must advertise shared retrieval and, when requested,
learned ranking plus independent host accounting before Apply can queue work.

- AI models / Purpose overrides: provider, model, temperature, timeout, token
  budgets and fallback already have forms. Provider parameters remain Advanced.
- Knowledge / Search settings: vector engine, hosted/BYO connection, embeddings
  and customer separation already have forms. Storage & uploads retains S3
  provisioning, prefixes, uploads and document approval/indexing.
- Responses: tone, answer breadth, length, suggestions, instructions, similarity
  threshold and final-answer context/generation budgets already have controls.
  Final-answer context budgets are distinct from read-planner context budgets;
  they have not been copied into the new form.
- Advanced / Recovery: repair rounds, admission window and semantic recovery
  policies already have controls. A fan-out policy still needs its explicit
  enabled search and source limit; the new form surfaces this dependency.
- AI capabilities / Actions: identity, access, confirmation, parameters,
  grounding, read eligibility and Automatic/Selected fields evidence already
  have controls. Automatic remains the standard non-JSON path.
- Data plugins: the existing supported-source wizard, mappings, required-input
  forms and install configuration remain the owners. No new plugin type.
- Managed Tasks: task instructions, resource selection, limits and basic input /
  output schema fields already have editors. Runtime test input is explicitly
  structured data, not a replacement Brain-configuration editor.
- Engage, Keys & secrets and specialist assistance retain their existing forms
  and permissions; there is no second editor or new mutation authority.

## Intentional Expert Surfaces

Do not claim every possible JSON document now has a visual builder. Arbitrary
nested action schemas, trusted resolver objects, HTTP body/query templates and
advanced evidence list/object projections still have expert JSON inputs or can
be prepared through Build with AI. Full plugin manifests and task schemas retain
their advanced source editors. These are structured payloads, not missing
checkboxes for the ten supported retrieval settings addressed here. No existing
expert functionality was removed or hidden.

## Preservation Contract

- Opening a form never writes defaults, publishes, applies or invokes AI.
- An absent or null override displays inheritance, not an invented effective
  value. Selecting inheritance removes only that explicit override.
- Unknown saved enum values remain visible as Saved value and are preserved
  unless explicitly replaced. Unknown keys, inactive-provider fields and other
  configuration sections survive a focused form save.
- Both scoped and legacy provider saves use the existing field-delta merge onto
  the newest cached draft. They do not rebuild the whole provider object from
  defaults. This does not replace the server's concurrency/authorization rules.
- Numeric bounds mirror existing backend execution budgets. Unrelated saved
  values are not normalized or silently repaired.
- Shared draft save, validation, discard, review, publish and apply are retained.
  These settings do not need knowledge reindexing.
- Optional full Provider configuration JSON remains at Advanced > Configuration
  JSON. Unsaved raw-source and form editing retain existing navigation guards.

## Verification

Automated cases: `Platfrom/ui/scripts/brain-settings.test.mjs`, including typed
round-trips for every new field, no-op/unknown/null/inherited preservation,
concurrent unrelated edits, all three RAG choices, read-only access, numeric
validation, discard, task switching and 1280/390/320px light/dark views.
Backend contract regression: DeploymentDraftValidationServiceTest and
RailwayProvisioningPlanServiceTest. See the task verification record for actual
deployed UI source and live save/reload/restoration evidence in
`verification-support/configuration-ui-controls/README.md`.

Final UI source `5404944ba7f2d59cbb6af3fd584972df35ecfce2` is deployed and native-
verified on staging and production (CI 45027515729 SUCCESS). All three RAG modes,
all ten controls and exact original-draft restoration passed in live UI checks
on both environments. Running releases, backend/framework and infrastructure
settings were not changed. Automated results: 32 UI cases, 173 existing backend
contract tests and production UI build PASS.
