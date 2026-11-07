# Customer Configuration UI Alignment

Date: 2026-11-07

## Scope

- Match the existing Kit overview palette, typography and restrained surfaces.
- Update the customer Brain configuration header and its five existing sections:
  versions, actions, AI behaviour, providers and deployment-scoped plugins.
- Apply the same editor presentation inside customer configuration drawers and
  the knowledge/connection management views.
- Shared editor bodies also improve for staff. Unrelated staff/admin pages,
  navigation and the global staff theme are intentionally unchanged.

## Preserved Contracts

No backend, runtime, framework, model instructions, API payloads, permissions,
resource assignment or release behaviour changes. Existing advanced fields and
status details remain accessible. Profile review still updates only the editor;
save, publish and apply remain separate actions. Large JSON inputs scroll rather
than expanding to the entire configuration length.

## Verification

- `npm run test:customer-configuration`: theme parity, exact scoped navigation,
  active section, no-selection state, opt-in editor skin, retained controls,
  profile catalogue/custom/read-only states and presentation-only rendering.
- `npm run build`: TypeScript and production bundles.
- Local browser checks cover dark/light desktop layouts, 390px mobile framing,
  profile selection/review/editor-only application, the five configuration
  sections, and knowledge drawer/close confirmation. The Actions page has no
  document-level horizontal overflow after the grid containment adjustment.
- Browser checks use the actual existing customer account and API. No Brain
  configuration or live release is saved or applied during visual qualification.
- Hosted rollout and final browser evidence: pending qualification.

Existing operational warnings remain visible rather than being hidden by the
redesign (the checked older target reports unavailable document connector and
HTTP 401 for webhook administration). Those backend/runtime states are outside
this presentation-only change.

## Local Preview

```sh
cd Platfrom/ui
PLATFORM_PREVIEW_API_URL=https://api.loomai.pro node scripts/customer-configuration-preview.mjs
```

The loopback-only preview runs at `http://127.0.0.1:4398`. It uses normal sign-in
and existing API authorization; it is not a fixture account or a mock backend.
