# Public Framework Demos: AI Fabric 0.8.42

Verified on 2026-10-27. This record concerns the public framework examples on
the staging host, not a LoomAI Platform/runtime, Desk, Shopify or ProdUS upgrade.

## Outcome

- All twelve public demo backends and the MCP reference server are running and healthy.
- Every running executable contains only `0.8.42` AI Fabric library JARs.
- Each service has one running container. Native image source, terminal deployment
  receipt, public build identity, Actuator health and Docker health agree.
- Representative functional checks passed for all thirteen services: 83 HTTP
  requests in the final qualifying traces, including health and bounded work polling.
- All fourteen website views loaded with working backend requests and no application
  JavaScript errors: twelve demos, Human Review Desk and behavior-driven UI preview.
  Handover was rechecked in a fresh browser after old-instance retirement; it showed
  `0.8.42`, loaded its isolated conversation and had no browser errors.

The target was selected from Maven Central's BOM metadata, which identifies
`0.8.42` as the latest published version. GitHub's latest hosted release still
identifies `0.8.41`; that badge was not used as artifact verification.

## Live Demos

- [AI Shopping Experience](https://ai-fabric.dev/demos/ai-shopping-experience): indexed catalogue and a real-provider answer with product evidence.
- [Account Resolver](https://ai-fabric.dev/demos/ai-fabric-account-resolver): isolated demo account and account-readiness query.
- [Agentic Action Resolver](https://ai-fabric.dev/demos/ai-fabric-agentic-action-resolver): real-provider missing-payment, missing-address and ready-account assessments.
- [Conversation Handover](https://ai-fabric.dev/demos/ai-fabric-conversation-handover): AI-selected handover, required consented email intake, claim, visitor/human messages, return to AI; no AI replies while human authority is active.
- [Deployment Knowledge Guard](https://ai-fabric.dev/demos/ai-fabric-deployment-knowledge-guard): scoped evidence and grounded answer; missing-scope request denied without substituted output.
- [Document Knowledge Operations](https://ai-fabric.dev/demos/ai-fabric-document-knowledge-operations): source preview, indexing, new-version activation, active-version retrieval and exact deletion.
- [Incident Investigation](https://ai-fabric.dev/demos/ai-fabric-incident-investigation): successful parallel specialist plan.
- [Live Data Sync](https://ai-fabric.dev/demos/ai-fabric-live-data-sync): indexed workspace, retrieval and tracked create/update/delete work completing successfully.
- [MCP Operations](https://ai-fabric.dev/demos/ai-fabric-mcp-operations): authenticated remote connection, status read, wrong-server denial, confirmation-gated sandbox restart and replay without a second restart.
- [Privacy Shield](https://ai-fabric.dev/demos/ai-fabric-privacy-shield): isolated sample submission with email redaction. Its intentional local privacy mode was preserved.
- [Tenant Guard](https://ai-fabric.dev/demos/ai-fabric-tenant-guard): tenant-scoped real-provider answer, citations and successful boundary proof.
- [Behavior Signals](https://ai-fabric.dev/demos/ai-fabric-behavior-signals): isolated events and real OpenAI analysis with model identity, risk, patterns and recommendations.

The [MCP reference server](https://ai-fabric-mcp-reference-server.46.224.145.148.sslip.io/api/demo/health)
was rebuilt too; the MCP client checks above exercise its authenticated tools.

## Corrections Made

1. **Example account identity:** Agentic Resolver actions incorrectly parsed the
   opaque conversation actor as an account UUID. The example now resolves the
   trusted execution subject and checks its account/tenant/deployment boundary.
   Profile evidence and account write handlers share that resolution. Existing
   confirmation and grounding checks remain enforced.
2. **Example health metadata:** Handover reported a hardcoded framework version.
   It now reports deployment version and source commit.
3. **Example build argument:** The MCP reference server did not pass the selected
   framework version to Maven. Its Dockerfile now honors `AI_FABRIC_VERSION`.
4. **Hosting:** Restored the retained stopped demos; set the framework build
   argument to `0.8.42`, enabled source-commit injection and main-branch auto-deploy,
   and configured native stop-then-start replacement for these single-writer apps.
   The initial MCP rolling update failed on an H2 file lock. Transitioning to fixed
   container names also left three old timestamp-named instances running; those
   exact instances were stopped, retained and prevented from automatic restart.
   The final audit confirms one healthy current instance per service.
5. **Demo data:** The shopping database was empty after the earlier stop/reset.
   Its existing full-demo seed operation populated 100 products, 200 reviews,
   20 policies, 20 coupons and 50 fictional support tickets; indexed retrieval passed.

No framework library was modified or released. No semantic text-matching routing,
synthetic answer, silent fallback or weakened authorization was introduced.

## Source And Verification

- Framework example fixes: `e51459d0`; MCP Dockerfile: `6e5a3548`.
- Deployed application source is `1bde2bd4` for unchanged examples and `6e5a3548`
  for the corrected examples and Behavior Signals. All resolve framework `0.8.42`.
- Focused example tests: 51 account/identity/receipt/session tests and 13 handover tests passed against `0.8.42`.
- The automatic broad CI run at `6e5a3548` exposed one new unit test's assumption
  about `ActionContext.userId()` projection. The fixture now explicitly supplies
  an opaque actor, retaining the regression instead of depending on an upstream
  accessor implementation. All seven subject-resolution tests passed again.
  Test-only correction `84207902` is pushed; its broader automatic CI result is
  tracked separately and is not claimed green by this live-demo record.
- Build/deployment steps were followed to their exact terminal receipts, then
  verified independently against running containers and actual packaged JARs.
- Sanitized native evidence is in [live-0.8.42-20261027.json](live-0.8.42-20261027.json).
  Raw private receipts and traces are under
  `~/.codex/private/framework-demos-20261027/`.

## Explicit Limits And Follow-Up

- The public demo catalogue still has old static version labels. Website commit
  `afed600` updates all twelve labels, passes the website build and is pushed to
  `mahmoudashraf/aifabric`. Publishing that existing Lovable project requires its
  signed-in publisher; the available browser was not signed in. Individual demo
  pages read current `0.8.42` health correctly. This is a cosmetic publishing item,
  not an old backend version.
- Auto-deploy settings and the existing GitHub push webhook are enabled, but this
  session did not observe a new GitHub delivery for its corrective push. The
  qualifying corrected deployments were explicitly triggered and verified;
  do not present them as proof of successful push-triggered deployment.
- One exploratory MCP question asked for unsupported recent logs and referred to
  the selected service; it returned visible clarification. Supported status and
  restart flows passed. No hidden query rewrite or automatic retry was added.
- This is representative deployed-functionality verification, not exhaustive
  certification of every model response, every UI interaction or framework release
  matrix. Initial harness envelope/status/payload mistakes were corrected without
  changing the application contract; failed deployment receipts are retained privately.
- At the post-rollout observation, staging had 3,311 MiB physical memory
  available out of 15,608 MiB and load averages `1.44 / 2.25 / 2.26`; native
  audit showed no OOM-killed current containers. Continue sequential builds.
