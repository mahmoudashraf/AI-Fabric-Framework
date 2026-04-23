# Shopify Companion App Review Guide

Status: Wave 7 review package baseline

This guide defines the reviewer-facing posture for Shopify Companion.

Use it with:

- [Shopify Companion App Store Listing Package](SHOPIFY_COMPANION_APP_STORE_LISTING_PACKAGE.md)
- [Shopify Companion Review Screencast Script](SHOPIFY_COMPANION_REVIEW_SCREENCAST_SCRIPT.md)
- [Shopify Companion Support Runbook](SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md)

## 1. Product Posture

Shopify Companion is a read-first shopper decision-support product.

V1 scope:

- AI search
- contextual storefront intelligence surfaces
- product discovery
- collection and catalog guidance
- policy answers
- comparison support
- storefront companion widget for depth

Explicit non-goals for V1:

- checkout behavior
- autonomous purchasing
- write actions against merchant orders/customers
- arbitrary merchant scripting

Important review rule:

- governed Elite actions are not part of the default V1 reviewer story unless the review package explicitly enables the Elite posture for that store

## 2. Architecture Summary

Shopify Companion is built on top of the existing platform rather than as a separate AI stack.

Main parts:

- Shopify Bridge Service
- embedded merchant admin UI
- theme app extension
- platform deployment created from the canonical Shopify Companion bundle

The storefront widget talks to the Shopify Bridge, and the bridge mediates access to the private runtime posture.

## 3. Requested Shopify Scope Posture

The app should request only the scopes needed for the V1 read-first product.

Example launch posture:

- product/catalog reads
- content/policy reads
- metaobject reads for bounded structured commerce content
- webhook topics needed for install lifecycle and bounded sync invalidation

The review package should explicitly show that V1 does not require write scopes for transactional behavior.

If Elite is ever included in a later review package, the package must separately explain:

- explicit confirmation
- audit trail
- bounded action families
- why those flows are still not autonomous checkout behavior

## 4. Reviewer Test Story

The reviewer should be able to validate this sequence:

1. install the app on a test store
2. open the embedded admin UI
3. connect the store and persist credentials
4. run source preflight
5. bootstrap and go live
6. enable the theme app extension
7. ask shopper questions from the storefront widget
8. confirm grounded product/policy responses
9. confirm the embedded surfaces contract:
   - AI search
   - contextual pill
   - product insight
   - policy strip
   - product FAQ
   - comparison

## 5. Review Package Contents

The review package should contain:

- this guide
- the subscription and go-live flow
- the support runbook
- a disposable review store/domain and expected setup notes
- verification scripts:
  - `scripts/verify-shopify-companion.sh`
  - `scripts/verify-shopify-companion-uninstall.sh`

## 6. Verification Evidence Expected Before Submission

Required evidence:

- platform product service summary is healthy
- store summary reaches `storefrontReady=true`
- storefront bootstrap returns `available=true`
- shopper query path executes successfully
- launch and App Review readiness summary is visible in the merchant app
- store intelligence health is visible in the merchant app
- tier ladder and governance posture are visible in the merchant app
- platform-admin investigation surface for recent governed actions is visible when present
- uninstall verification passes on a disposable store mapping

## 7. Merchant-Facing UI Expectations

The embedded app should visibly expose:

- store connection state
- source-category toggles
- source preflight state
- sync state
- storefront preview
- launch and App Review readiness summary
- store intelligence health
- tier ladder and governance posture summary
- support bundle

The embedded app should not expose:

- raw plugin editing
- arbitrary action authoring
- arbitrary vectorization controls

## 8. Theme App Extension Expectations

The review package should state clearly:

- V1 uses a theme app extension
- checkout pages are not in scope
- widget behavior is constrained to supported storefront pages

## 9. Billing Review Posture

If the app is launched in free mode:

- review package must explicitly state that no merchant billing approval is required

If the app is launched in paid mode:

- include the chosen Shopify billing path and the merchant-facing billing explanation

Do not ship with ambiguous billing posture.

## 10. Review Notes For Operators

Before handing the app to a reviewer:

1. confirm the exact review store and credentials
2. run the live verification scripts
3. verify the merchant UI resolves session and support bundle
4. verify the storefront widget is active on the review theme
5. verify uninstall cleanup on a disposable mapping
6. verify the listing copy and screenshots still match the current live surface set

If any of the above fail, the package is not review-ready.
