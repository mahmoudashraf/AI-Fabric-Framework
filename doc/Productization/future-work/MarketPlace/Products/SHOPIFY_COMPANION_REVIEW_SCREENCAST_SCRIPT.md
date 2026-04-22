# Shopify Companion Review Screencast Script

Status: Wave 7 review artifact

This is a bounded walkthrough script for recording the Shopify Companion review/demo screencast.

## 1. Recording Goal

Show the full V1 product loop without implying unsupported behavior.

The screencast should prove:

- install and merchant app access work
- source preflight and go-live work
- storefront widget loads through the theme app extension
- shopper answers are grounded
- uninstall/recovery posture exists

## 2. Constraints

Do not demonstrate:

- checkout behavior
- autonomous purchase flows
- write actions
- unsupported review-provider behavior
- arbitrary merchant editing of plugins or vectorization settings

## 3. Recommended Recording Order

### Segment 1: Product posture

Show:

- Shopify Companion embedded admin home
- short statement that V1 is read-first and shopper-facing

Say:

- the app helps shoppers discover products and answer policy questions
- V1 does not automate checkout or write merchant data

### Segment 2: Merchant connection

Show:

- merchant session loaded
- connected store identity
- billing posture

Say:

- the app binds one store to one platform deployment bundle
- the merchant-facing app does not expose low-level platform internals

### Segment 3: Source readiness

Show:

- source-category toggles
- source preflight action
- readiness results

Say:

- the merchant chooses bounded source categories
- Companion validates source readiness before go-live

### Segment 4: Bootstrap and go-live

Show:

- bootstrap action
- go-live action
- readiness status after publish/apply/verify completes

Say:

- real vectorized readiness is established during apply-time sync and verification

### Segment 5: Theme app extension

Show:

- storefront preview
- theme editor activation link or active widget state

Say:

- the widget is delivered through a theme app extension
- V1 does not target checkout pages

### Segment 6: Shopper experience

Show:

- storefront widget opening
- product-aware suggestion examples
- one product discovery question
- one comparison or policy question
- matched products and grounding sources in the response

Say:

- answers are grounded in synced store data
- the product behaves as a companion, not a generic chatbot

### Segment 7: Diagnostics

Show:

- support bundle section
- copy/download support bundle

Say:

- bounded diagnostics are available without exposing secret material

### Segment 8: Uninstall posture

Show:

- uninstall verification or blocked state from a disposable mapping
- merchant reinstall guidance after uninstall

Say:

- uninstall disables storefront access and clears platform-side access posture

## 4. Expected Runtime During Recording

Target:

- 5 to 8 minutes

If longer, the recording will start to drift into platform walkthrough instead of product review.

## 5. Pre-Recording Checklist

Before recording:

1. run `scripts/verify-shopify-companion.sh`
2. verify merchant session resolves normally
3. verify storefront bootstrap is available
4. confirm the demo store theme has the app embed enabled
5. prepare one disposable mapping for uninstall demonstration if needed

## 6. Output Rule

The screencast should match the review guide and support runbook.

If the recording implies behavior the current product does not support, it should be redone instead of explained away later.
