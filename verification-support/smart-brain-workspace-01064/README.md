# Smart Brain Workspace Verification

Scope: 010.64 UI redesign, local qualification followed by the owner-authorized hosted rollout.

Main record: [implementation status](../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_64_SMART_BRAIN_WORKSPACE_IMPLEMENTATION_RECORD.md).

## Reproducible Checks

Run from `Platfrom/ui`:

```sh
npm run build
node --test scripts/brain-*.test.mjs scripts/ai-behaviour-profiles.test.mjs scripts/customer-configuration-design.test.mjs scripts/customer-product-models.test.mjs scripts/deployment-workspace-navigation.test.mjs
node --test src/components/documents/HostedDocumentStorageSetup.test.mjs scripts/customer-document-storage-scope.test.mjs
node --test scripts/customer-support-pack-host.test.mjs scripts/customer-supabase-auth.test.mjs scripts/customer-auth-session-continuity.test.mjs
node scripts/brain-responses.browser.mjs
node scripts/brain-capabilities.browser.mjs
node scripts/brain-updates.browser.mjs
```

The standalone browser harnesses use in-memory API fixtures; their screenshots are written to isolated system temporary directories and their operations do not reach a live backend. The main local review uses `scripts/smart-brain-preview.mjs`, which blocks resource writes by default.

`scripts/smart-brain-readonly.browser.mjs` uses existing private test credentials supplied through environment variables. It signs in normally and renders authorized API data in the local UI, taking screenshots and checking document overflow. It must not be pointed at a hosted UI. Credentials and tokens are never written to these evidence files.

## Actual Local Screenshots

- [Northfield Responses, dark](local/responses-dark.png)
- [Northfield Capabilities, dark](local/capabilities-dark.png)
- [Northfield Updates, dark](local/updates-dark.png)
- [Customer Responses, dark](local/customer-responses-dark.png)
- [Customer Knowledge/storage](local/customer-knowledge-storage-dark.png)
- [Staff read-only report](local/read-only-browser-report.json)
- [Customer read-only report](local/customer-read-only-browser-report.json)

The initial checking cycle exposed and fixed staff data-router guard support and role-specific target-catalogue selection. Fresh successful runs supersede the initial failures. Existing backend/runtime connectivity warnings are preserved, not converted to successful states.

The local evidence above includes no runtime/framework update, infrastructure apply, provider query or production UI deployment.

## Hosted Rollout

Source `3013df7f433899da36bab743c81c5a7c9f8505f7` is healthy on both staging and production. CI `42373316258` published only the UI image. Both Coolify deployments finished; backend/runtime versions were not changed.

- [Deployment identity readback](live/deployment-report.json)
- [55 hosted route checks and verification boundaries](live/browser-report.json)
- [Production dealership Responses](live/production-staff-dealership-responses.jpg)
- [Production customer, light](live/production-customer-responses-light.jpg)
- [Production customer, dark](live/production-customer-responses-dark.jpg)
- [Production customer, mobile](live/production-customer-mobile.jpg)
- [Staging staff Responses](live/staging-staff-responses-dark.jpg)

Verified nine main sections and fourteen deeper tasks for staging staff and a production customer, plus the nine main production staff sections. Desktop containment, mobile navigation, deep-link reload, legacy customer redirect, unsaved-change protection and retained staff administration passed. No captured browser console errors were reported.

Normal authentication and theme preferences were used. No hosted Brain save/publish/apply, capability install, upload, business action or provider query was executed. Write-workflow proof remains the isolated harness evidence above, not a claim of fresh live runtime execution.

## Real Kit Journey Corrections, 2026-11-16

This later focused cycle includes real customer writes and provider queries, unlike
the historical read-only rollout above. Current execution detail and exact private
receipts are in `CODEX_WORKING_CONTEXT.md`, dated2026-11-16.

- UI source `e7378cefdc226c5d2162a2fe0fe6e24f2236b5e4` and shared Engage source
  `a406c7d827adb067e621ab70a7680231d1c4d4a0` deployed healthy on both environments.
- Fixed decimal composition round trips, branding save completion, target-aware
  Kit entry and direct canonical branding navigation. Existing forms and security
  validation remain. Trusted Console framing is explicitly configured on both
  servers; unrelated embedding remains blocked.
- Fresh production dealership Kit created through UI with both channels. Branding
  saved/reopened correctly; Warm/Brief presets persisted with a native numeric0.4;
  Check setup returned publishReady=true. Saved preview and390px navigation passed.
  The test provisioned no infrastructure and was completely removed afterward.
- Staging/production normal-customer Engage journeys pass. Actual production Try it
  iframe renders its composer and answers a real runtime query, not a fixture.
- Harbor received generic numerical-condition/consistent-total prompt guidance via
  the existing version/apply/assignment path, with unchanged framework/runtime image.
  Fifteen observed live question checks passed their facts and limitations, including
  three independent natural delivery follow-ups and below/at-threshold totals.
  No query-term logic, answer replacement, hidden retry or new business write added.
- This does not qualify new infrastructure provisioning/promotion, new domains,
  external sign-in redirects or actual human-support transfers. The original Kit,
  Brain and two active apps remain; all temporary records were deleted.

Focused regression commands:

```sh
cd Platfrom/ui
node --test scripts/experience-kits-ui.test.mjs scripts/kit-app-address-preview.test.mjs scripts/ai-behaviour-profiles.test.mjs scripts/workspace-csp.test.mjs
node scripts/kit-overview-navigation.browser.mjs
node scripts/brain-responses.browser.mjs
node scripts/engage-guided.browser.mjs
cd ../../workspace-apps/standalone-ai-workspace
npm run typecheck
npm run build
npm test
npm run test:browser
```

These local browser suites remain controlled fixtures. Hosted customer evidence
and provider results are stored privately, not embedded in the public repository.

Final Core image, shared Engage, storage and quota/Desk CI checks pass. Runner
compatibility and the existing data-URL test-loader import were corrected without
weakening tests or changing backend/framework code. Test-only follow-up commits
were not deployed over the already-qualified application images above.
