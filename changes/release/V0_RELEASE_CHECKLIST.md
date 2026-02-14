# v0 Release Checklist (Developer-Friendly Preview)

## Status
Draft (review requested)

This checklist complements:
- `changes/release/V0_RELEASE_PLAN_CHAT_CAPABILITIES_BASELINE.md`

---

## 1) Scope + supported matrix
- [ ] Supported set is explicitly stated (modules, provider, vector DB).
- [ ] Everything outside the supported set is explicitly “not supported”.

---

## 2) Golden path run (end-to-end)
- [ ] `docs/V0_QUICKSTART.md` is accurate and complete.
- [ ] Demo app runs cleanly from scratch:
  - [ ] build framework
  - [ ] run `Real_Apps/chat-capabilities-demo`
  - [ ] execute the demo flow (`Real_Apps/chat-capabilities-demo/requests/demo.http`)
- [ ] `/api/chat/query` matches the documented contract:
  - [ ] `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

---

## 3) Build gates
- [ ] `mvn -f ai-infrastructure-module/pom.xml -DskipTests install` succeeds.
- [ ] `mvn -f Real_Apps/chat-capabilities-demo/pom.xml test` succeeds.

---

## 4) Safety + public readiness
- [ ] No committed secrets (keys, tokens).
- [ ] Demo fails closed when provider is missing (clear error message).
- [ ] No obvious broken docs (placeholders, merge markers).

---

## 5) Release assets
- [ ] Tag created (e.g., `v0.1.0`).
- [ ] Release notes include:
  - [ ] supported matrix
  - [ ] quickstart link
  - [ ] known limitations
- [ ] Optional (high value): short demo video link.

---

## 6) Artifact publishing (optional for v0.1)
Choose one:
- [ ] A) Build-from-source only (no Maven publish)
- [ ] B) Publish supported subset (after coordinate cleanup for that subset)

Reference:
- `changes/release/ARTIFACT_COORDINATES_AND_NAMING_FIXES_CHANGE_PLAN.md`
