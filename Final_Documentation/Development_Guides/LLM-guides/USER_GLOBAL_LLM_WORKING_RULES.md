# User Global LLM Working Rules

Purpose: capture the recurring project-level rules the user gave in the old Codex rollout session so future LLM sessions do not treat them as one-off task wording.

Source rollout:

- `/Users/mahmoudashraf/.codex/sessions/2026/05/03/rollout-2026-05-03T08-24-49-019decb9-bf8e-7150-b458-2c074d8835a9.jsonl`
- Session title observed in the rollout: `Fix SMTP email delivery`
- This file uses short direct-message excerpts from the user. Spelling is preserved where quoted.
- No raw secret values are included here. Private auth material belongs in gitignored/private handoff docs only.

Use this guide together with:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md`
- `Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md`
- `Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md` when auth material is needed

## 1. Greenfield First

Direct user message anchors:

> Greenfield. Do not preserve legacy Shopify action aliases.

> no backward compitability

> make sure to consider it is green field ignore backward compitability

LLM rule:

- Treat this project as greenfield unless the current user request explicitly says otherwise.
- Prefer clean architecture over preserving stale aliases, legacy routes, or compatibility wrappers.
- Do not add backward-compatibility code just because an old path exists.
- If a compatibility bridge is genuinely required for live safety, document the exact reason, owner, and removal condition.

## 2. No Dummy, Stub, Fake, Or Static Success

Direct user message anchors:

> i need prod ready enterpese ready secure solution not gaps , no stups.

> Is there any stubs in the fixes for text matching or temp solutioons or workarrounds. ?

> No launch-critical partner/merchant page shows dummy success states.

> make sure wwe have no static/fixed data showed in the widget

LLM rule:

- Do not ship dummy state, fake success, static product data, placeholder text, or text-matching hacks as product behavior.
- Do not hide a real blocker behind a cosmetic UI state.
- If only a mock/unit-tested path is possible, label it as mock/unit-tested and list the exact auth/env/live blocker.
- Prefer fail-closed behavior with clear diagnostics over pretending a feature is ready.

## 3. Full Implementation Over Slice Theater

Direct user message anchors:

> the goal is to implement those plans. fully non-stobable.

> make sure every thing is implemented. never mind slices and iterative.

> the sequence plan is just guidenece the mail goal is fully implementation

> ok start implementation fully all slices. tested/live verified / deployed. do it fully

> implement fully , all slices , fdo niot stop untill done.

LLM rule:

- Use slices only as an execution tactic, not as a way to narrow the final obligation.
- Do not stop at a plan, skeleton, partial wiring, or "next steps" when the user asked for implementation.
- Continue through code, tests, deploy, live verification, docs, commit, and push where those are in scope.
- If true completion is blocked, name the blocker concretely and do everything else that can be completed safely.

## 4. Production-Grade, Enterprise-Ready, Secure

Direct user message anchors:

> This is not V1/POC work. Implement production-grade self-service readiness.

> make it secure production enterprise levvel

> We need secure production setup

> prod ready enterpese ready secure solution

LLM rule:

- Design for production operation, security, auditability, and recovery, not demo-only behavior.
- Treat auth boundaries, tenant isolation, SSRF prevention, secret scoping, provider isolation, and rollback proof as first-class work.
- Public/partner/merchant surfaces must expose business-safe information, not provider internals or raw deployment secrets.
- Production claims require production evidence; do not claim public readiness when only staging or local proof exists.

## 5. Live Verification Is Part Of Done

Direct user message anchors:

> continue develope test verify commit push live verify untill all done.

> tested live verified , not backward compitablitty

> ok deploy and live verify it.

> do not claim completeness without testing verification

> Explicit statement whether shopify_search_catalog is fully live-verified or only unit/mock verified.

LLM rule:

- Run local tests and live checks when behavior affects deployed systems.
- Say exactly what passed, what failed, and what was only unit/mock verified.
- Do not claim "done" from compile success alone.
- Keep evidence paths, deployment ids, release ids, verification ids, and health results in working context when they matter.

## 6. Auth Material Lives In Private Context

Direct user message anchors:

> may be you find auth material in private doc

> do you have coolify. auth material ?

> Do not ask for approval unless you miss auth material and live verifiy as long you can.

> add any missing auth material to private document.

> Never print secrets.

LLM rule:

- Check the private handoff docs for credentials before asking the user.
- Ask the user only when required auth material is genuinely missing or expired.
- Never print, commit, or paste raw secrets, tokens, private keys, JWTs, API keys, webhook secrets, or password material.
- Record non-secret secret names, header names, file locations, ownership, and rotation guidance in docs.

## 7. Staging First, Production Only When Explicit

Direct user message anchors:

> we are only using staging for now make sure all linked to staging on coolify

> ok , we develop staging first , we will not move to prod now.

> Production failures leave staging untouched and return merchant-safe guidance plus operator-safe diagnostics.

LLM rule:

- Default to staging work unless the active request explicitly asks for production.
- Keep staging and production target profiles, provider records, secrets, URLs, service refs, and evidence separate.
- Production work must preserve staging isolation.
- Do not reuse staging service refs, staging secrets, or staging provider metadata as production truth.

## 8. Use Existing Architecture And Product Boundaries

Direct user message anchors:

> Do not add a new Marketplace plugin type.

> Use existing ACTION plugins with adapterType = mcp-tool.

> Use existing architecture and patterns. Do not add a new platform layer.

> Partner/merchant UIs must call Platform APIs only. They must not call Coolify, provider APIs, or secret/deployment internals directly.

> shopify bridge should remove all legacy action implementation and depend fully on plugin based mcp actions on the generic mcp execution servce.

LLM rule:

- Prefer existing plugin types, provider abstractions, Platform APIs, Marketplace compilation, and managed service patterns.
- Do not invent a new layer or plugin type unless the current architecture cannot express the requirement.
- Keep product UIs behind Platform APIs.
- Keep Bridge/product services as governance, auth, session, audit, rate-limit, and adapter boundaries, not parallel product-truth systems.

## 9. Product Truth Comes From Runtime-Backed Contracts

Direct user message anchors:

> Do not use runtime MCP tools/list as shopper-visible product truth.

> It correctly treats tools/list as discovery/verification evidence, not runtime product truth.

> Do not duplicate action definitions across staging and production templates.

> LoomAI must not mark a file USED unless it returns owner-safe evidence extracted from the file

LLM rule:

- Discovery output is evidence, not automatically product behavior.
- Marketplace/config/runtime contracts must be explicit and verified.
- User-facing claims should be backed by runtime responses, verification, and evidence.
- Do not duplicate or fork action definitions where a shared template/config contribution should own the contract.

## 10. Commands-Only Tasks Should Stay Commands-Only

Direct user message anchors:

> I expect you to run scripts/commands only. no code chnages should be needed

> Do the nessessary redepploy triggers , or do a dummy/empty commit then check if the new commit is triggering the redeploy again

LLM rule:

- For operational tasks, first try the existing scripts, APIs, admin commands, and deployment controls.
- Do not edit code when the task is clearly deployment/config/checkup-only unless the commands prove a real code defect.
- When a command-only task uncovers a code issue, document why code changes became necessary.

## 11. Documentation And Handoff Must Track Truth

Direct user message anchors:

> add to the working context doc the most critical fixes you mad to make it work

> Updated documentation only where implementation truth changed.

> update handover document and tell me what to give to proud as update

> make sure documents got return in respose of queries if exisy for rag

LLM rule:

- Update context and handoff docs when implementation truth, live status, auth contract, deployment ids, or operational procedure changes.
- Keep docs truthful and bounded. Do not inflate readiness.
- Prefer concise handoff updates that name what changed, what passed, what is blocked, and what the external party must do.
- Do not store raw secrets in tracked docs.

## 12. Merchant/Product Messaging Must Stay Business-Facing

Direct user message anchors:

> Merchant-facing copy must sell Loom Companion for Shopify, not MCP, Coolify, Hetzner, or AI Fabric.

> No partner/merchant UI exposes provider internals.

LLM rule:

- Merchant-facing and partner-facing screens should describe product value and safe actions.
- Hide Coolify, Hetzner, deployment internals, raw provider ids, secret names, and low-level framework language unless the surface is explicitly operator/admin-only.
- Operator diagnostics can be detailed, but customer guidance must stay merchant-safe.

## 13. External Docs And Current Specs Matter

Direct user message anchors:

> Official docs to verify before MCP implementation

> review the below plan review by LLM session and check if you need to aligh accoredngly or not

LLM rule:

- For platform integrations, verify current official specs before coding when the behavior depends on external protocols or APIs.
- Reconcile review comments and external assessments against the repo before implementing.
- If a review says a thread is outdated, still verify the underlying code path; `OUTDATED` does not automatically mean fixed.

## Quick LLM Checklist

Before finalizing work in this project, answer:

- Is this greenfield-clean, or did I preserve legacy behavior without a current reason?
- Did I avoid dummy, stub, static, fake, or text-matching behavior?
- Did I implement the full requested outcome, not just a plan or skeleton?
- Did I run the relevant local tests?
- Did I live-verify deployed behavior when the task affects live systems?
- Did I keep secrets private and document only non-secret contracts?
- Did I preserve staging/production separation?
- Did I update working context or handoff docs where truth changed?
