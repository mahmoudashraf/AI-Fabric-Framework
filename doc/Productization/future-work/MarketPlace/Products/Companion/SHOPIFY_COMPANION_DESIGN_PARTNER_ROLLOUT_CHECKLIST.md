# Shopify Companion Design Partner Rollout Checklist

Status: Wave 8 rollout baseline

Use this checklist for each real merchant/store onboarding.

## 1. Preconditions

Do not start a design-partner rollout until all of the following are true:

1. the target branch is deployed
2. `scripts/verify-shopify-companion.sh` passes against the deployed environment
3. `scripts/verify-shopify-companion-uninstall.sh` passes against a disposable store mapping
4. the Shopify Bridge service is healthy and visible in the platform product service UI
5. the merchant-facing embedded app loads with valid session auth

## 2. Merchant Store Requirements

Confirm:

- the store is a real development or partner-approved merchant store
- the theme supports app embeds
- the store has enough content in the enabled source categories
- a safe test theme exists when possible

Do not use a production storefront theme first if a safer preview theme is available.

## 3. Rollout Sequence

### Phase A: Install and identity

- install the app on the target store
- confirm the merchant session resolves
- confirm the install record exists

### Phase B: Product provisioning

- bootstrap or confirm:
  - customer
  - deployment
  - consumer binding
- confirm the store mapping points to the intended product service

### Phase C: Source readiness

- review bounded source-category toggles
- run source preflight
- inspect counts and blocked categories

### Phase D: Go live

- request bootstrap/go-live
- confirm publish/apply/verify completes
- confirm apply-time sync reaches `SYNCED`

### Phase E: Storefront enablement

- enable the theme app extension
- place the intended embedded surfaces for the partner tier
- load the storefront
- confirm storefront bootstrap is available

### Phase F: Shopper verification

Run at least:

- one product discovery question
- one comparison question
- one policy question

If the partner is explicitly piloting Elite guided commerce, also run:

- one governed add-to-cart flow with explicit confirmation
- one governed cart update flow

Confirm:

- the widget loads
- shopper answers are grounded
- product and source rendering appears

## 4. Required Evidence To Capture

Capture and store:

- store summary screenshot or export
- storefront preview screenshot
- tier ladder and launch readiness screenshot
- shopper widget screenshot
- support bundle text
- verification script outputs

If Elite is active, also capture:

- recent governed action history from merchant or platform admin
- explicit confirmation UI state

If any of these are missing, the rollout is incomplete.

## 5. Rollback / Recovery

If rollout fails before storefront enablement:

- keep the store blocked
- do not expose the widget publicly

If rollout fails after storefront enablement:

- disable the app embed
- inspect support bundle and store readiness blockers
- rerun go-live only after the failure is understood

If the partner exits:

- run uninstall verification on a disposable mapping pattern first
- then follow the uninstall/cleanup posture for the real store

## 6. Sign-Off Criteria

A design-partner rollout counts as complete only when:

- merchant onboarding reaches live state
- storefront widget is enabled
- the intended embedded surfaces are placed and visible for the partner tier
- the merchant can demonstrate value from the embedded app and storefront
- no unresolved launch blocker remains in support notes

## 7. Live-Only Follow-Up

Track these items per partner:

- real merchant friction not captured by local testing
- real theme-specific storefront issues
- real content-quality issues from Shopify data
- questions support repeatedly receives

These are the inputs for Wave 8 reliability fixes.
