# Account Resolver 0.8.56 source handoff release

## Scope

The owner separately approved the bounded source/query handoff correction,
verified framework release and Account Resolver adoption. This follows the
read-only [investigation](2026-11-19_ACCOUNT_RESOLVER_INVESTIGATION.md).
It does not authorize upgrading Platform or Support runtimes, which remain
verified on 0.8.55. Final-answer contradictions are a separate quality finding.

## Implementation

Public implementation commit fb04ef5304bfea443a7cdaccc5f430c68c09da8d retains the
latest model-selected source/query pair within one iterative read-resolution
call, only when its effective policy still requires RAG. Later explicit source
selections replace rather than merge it. An invalid later choice is not replaced
with an earlier valid one. No catalogue-derived guess, natural-language matcher,
extra model request or action replay is added.

The selected source still passes through registration, source allowlist,
capability/profile, tenant and fan-out budget checks. Carry-forward diagnostics
record the originating iteration and selection source. Context-only/optional-RAG
behavior and explicit empty task restrictions remain intact.

The Account Resolver example explicitly enables BOUNDED_ALLOWED_FAN_OUT using
its existing two approved sources and max-spaces=2. Its Dockerfile pins 0.8.56.
Packaged application/build metadata takes precedence over stale environment
labels. Model gpt-4o-mini and business/extraction prompts are unchanged.

Immutable release source f01690615207a8fa395602cf4dce0b81ad798a27, tag
ai-fabric-framework-v0.8.56. One GitHub Release triggered Central run44625446902;
no second publication dispatch or moved release tag.

## Local gates

- Red regression reproduced lost sources on unmodified 0.8.55.
- Focused source handoff/routing/planner/profile suites: 92 passed, included in
  the full 1,150 core tests. Full release-profile reactor: 30 modules passed.
- Vector contracts: 18 unit and 8 actual container tests, none skipped.
- Example: 53 Account Resolver tests and 13 smoke tests passed.
- Minimal consumer compilation and standalone execution consumer verification
  passed; the latter ran 5 tests.
- Integration suites compiled. A missing locally installed support JAR stopped
  the first native attempt before any provider query; the support module was
  installed normally, with the failed setup log retained.
- Real OpenAI/ONNX/Lucene matrix core check passed.
- Release guards, semantic source guard and public coding-skill validator passed.

Both local seven-turn journeys passed payment form, actual confirmation,
successful receipt and follow-up routing. The first deliberately retained
LLM_REPAIR_THEN_CLARIFY, proving the source handoff itself worked without bounded
fan-out recovery. The shipping-policy run also used
READ_ACTION_PLANNER_CARRIED_FORWARD on the eligibility turn. Current readiness
reported active subscription, verified payment and validated billing address.
Both temporary local JVMs were stopped.

## Quality limits retained

The broader native real-provider suite was 19/21, with no skipped tests. One
public INFORMATION query chose ANSWER_FROM_CONTEXT without calling the public
READ tool. One context-only continuation returned the guide rather than the
offered checklist. Both used RAG cooperation NONE and useRag=false, so the new
source carry-forward branch was not applied. An unchanged-0.8.55 comparison of
the selected five cases passed. This does not erase candidate failures or prove
that those failures were reproduced on the baseline. No assertions, model,
fixture or prompts were weakened, and no green retry replaced the failed run.

Local Account Resolver wording remains inconsistent: one why-question claimed
a billing-address blocker despite the actual profile/readiness being validated.
The release is qualified for its bounded routing correction, not universal model
quality. No canned answer substitution, hidden retries or repeated writes mask it.

## Publication and hosted adoption

Central run44625446902 succeeded. Accepted deployment
c62bb48f-362f-4bfe-9f99-29824569c075 published at 22:29 UTC. Official BOM, core,
curated-default, starter, RAG, core source and Javadoc URLs returned 200.

Fresh git-archive source built in an isolated Central-only Maven cache. It was
seeded only with the earlier verified public consumer cache; no 0.8.56 candidate
artifact was present. All 12 packaged framework libraries are 0.8.56 and match
public cache bytes. Core SHA-256:
5237585ef734228214ca325442dcf2a73d21c285864f4db5047c24f48750f61b.
The clean build passed 53 example and 13 smoke tests, then booted and reported
version/source identity correctly. Public main advanced by normal fast-forward
from 45ad0b05 to f0169061, with no force push.

The first real-provider journey against that public package reached successful
payment confirmation but failed the eligibility turn before READ planning.
All three configured extraction attempts omitted requiresTargetResolution;
the first also omitted needsAdvancedRAG. The recovery trace retained an
incomplete INFORMATION sibling while recording a source-change preservation
decision. No read-resolution diagnostic existed: this is not the source-loss
branch fixed by this patch. The failed turn was retained and not retried.
Continuing the same conversation with its remaining two questions confirmed
the verified payment/validated address and used carried-forward retrieval for
the final question, whose wording remained contradictory. Final actual
readiness remained true. This is NOT a full seven-turn semantic success.
The temporary public-package JVM was stopped.

Image workflow44629376034 succeeded, building only Account Resolver from
the released source. Only the four stale diagnostic environment
rows (normal/preview AI_FABRIC_VERSION and APP_BUILD_COMMIT) were removed;
all other key/value/runtime/build flags matched the fresh snapshot.

The existing image updater requested deployment cj42a8un51ry6wy4d92cggel at
22:38 UTC; Coolify finished it and the updater reconciled its receipt to done.
No serving-host build, duplicate deploy request, global timer pause or replacement
app was used. Existing app UUID and public URL were retained. Current container
c26285bf1643 runs the f01690615207a8fa395602cf4dce0b81ad798a27 image tag;
native digest sha256:3975bec668670dd007ceb4014f771442aa9bbac45791d5f225f35c02fb3d4ad5.
All 12 deployed framework libraries are 0.8.56 and the deployed core hash matches
the public artifact above. Health reports 0.8.56 and the exact source commit.

Native comparisons retained model/key, datasource URL, vector path, Java options,
memory/swap/reservation, CPU affinity/limit, port bindings, restart policy and
mounts. App domain, identity, build pack, port and resource-limit settings also
matched their original values. Platform/Support were not rolled out.

## Live journey and browser verification

The live API completed the seven original turns once in a fresh synthetic
session. Payment required form input and actual confirmation, returned a real
successful receipt, then final readiness remained true. Both follow-up questions
routed without VECTOR_SPACE_SELECTION_REQUIRED. The eligibility planner recorded
carry-forward; its final router also had an independently selected LLM namespace.
The local original-policy test remains the stronger proof that carry-forward
itself supplied the final route. Do not conflate those diagnostics.

The desktop headless-browser journey used the real public page, Open live demo,
typed last4 form, Submit and Proceed, Confirm and all seven chat turns. The same
fresh browser session was independently found on the expected backend with
canContinue, verified payment and validated billing address all true. No
application JavaScript errors were observed. The eligibility answer said the
account could order; the final why-answer still opened with a contradictory
negative before stating that everything was ready. The existing large policy
result cards remain; no chat UI redesign was performed.

The first two browser harness attempts sent no AI queries: one did not dismiss
the current video gate; the next incorrectly waited for a health request that
the UI does not make. The corrected harness waits for actual session creation
and API connected, then checks version separately. These are retained harness
failures, not passing product runs. The first mobile screenshot caught its
loading overlay; an additional initialization-only check waited for that overlay
to disappear. Final mobile width390 equals scrollWidth390, all three observed
account API responses were successful on the expected backend, and no JavaScript
errors occurred. A full seven-turn phone conversation is not claimed.

## Final verdict and limits

The approved source-handoff fix is published and adopted. Local focused routing,
actual public consumption, native artifact identity and live desktop UI/API
journeys are verified. This is not an all-scenarios semantic-quality pass:

- Native real-provider qualification remains 19/21 with its two failures retained.
- The independent published-package eligibility extraction failure remains open.
- Contradictory final answer wording remains visible in local and live evidence.
- Broad Framework Build run44629351707 is not green: its library reactor,
  minimal/execution consumers and vector-container gate passed, then the all-app
  suite failed the same 14 Incident Room methods with the same summary lines as
  0.8.55 run44603376611. No added or removed failing methods. Those unrelated
  specialist-demo failures were not altered or bypassed.
- The staging updater's service-level warning also remains: the separate stopped
  Agentic app fails existing-running-image-app-required. It did not prevent
  Account Resolver adoption, and no stopped app was automatically resurrected.

No further prompt, validator, generic recovery or answer-rewriting correction
was included. Those changes require separate review. All temporary local JVMs
and headless browser contexts created for this work were stopped/closed.

Release: https://github.com/Loom-AI-Labs/ai-fabric-framework/releases/tag/ai-fabric-framework-v0.8.56

Live demo: https://ai-fabric.dev/demos/ai-fabric-account-resolver

The existing public demo uses an in-memory H2 database; normal container
replacement resets its synthetic state. Verify in a fresh isolated demo session,
not by mutating the user's supplied conversation. Preserve app identity, URL,
credentials, model, datasource/index settings and resource allocation.

Raw provider logs, synthetic sessions, native configuration and private helpers
remain outside Git under ~/.codex/private/loomai-framework-0.8.56/. No credentials
or native environment snapshots belong in this report or the public repository.
