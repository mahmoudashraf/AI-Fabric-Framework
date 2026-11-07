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
