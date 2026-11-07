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
- The 11 new presentation/scope tests and eight existing AI profile-contract
  tests pass (19/19). Additional workspace navigation, customer product-model
  and Kit tests pass 36/37. The existing public-page source assertion expects
  `/sign-up?kit=${definition.code}` in `ExperienceKitsPublicPage.tsx`; the page
  and assertion have identical Git object IDs before and after this change.
  That stale marketing-link assertion is not changed in this UI-editor scope.
- Local browser checks cover dark/light desktop layouts, 390px mobile framing,
  profile selection/review/editor-only application, the five configuration
  sections, and knowledge drawer/close confirmation. The Actions page has no
  document-level horizontal overflow after the grid containment adjustment.
- Browser checks use the actual existing customer account and API. No Brain
  configuration or live release is saved or applied during visual qualification.
- Source/image revision: `371f4d1ea0f33d8b9e702b6111c85f7a2c4ffdae`.
- CI `42353393194`: success; UI image built, backend image skipped.
- Staging UI deployment `qxvo64chcr5yzyh5imuzflzx`: finished; native container
  healthy with the exact source revision. Existing staff sign-in and the shared
  AI behaviour editor/profile/save controls verified in the hosted browser.
- Production UI deployment `f6ercoxuux1jn3wc0ol30fcf`: finished; native container
  healthy with the same exact source revision. Customer configuration header,
  four profile controls, save control and real API-backed review dialog verified.
  Review cancelled and choices discarded; saved Consultative tone retained and
  save disabled. No Brain configuration saved or released.
- Backend remains on its previous image. Runtime/framework images are untouched.

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
