#!/usr/bin/env bash
set -euo pipefail

# Build a lightweight Shopify Companion review kit directory from the current docs and verifier scripts.
#
# Usage:
#   scripts/build-shopify-companion-review-kit.sh [output-dir]

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${1:-${ROOT_DIR}/tmp/shopify-companion-review-kit}"

mkdir -p "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}/docs" "${OUTPUT_DIR}/scripts"

copy_file() {
  local source="$1"
  local destination="$2"
  cp "${ROOT_DIR}/${source}" "${OUTPUT_DIR}/${destination}"
}

copy_file "doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_APP_REVIEW_GUIDE.md" "docs/SHOPIFY_COMPANION_APP_REVIEW_GUIDE.md"
copy_file "doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md" "docs/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md"
copy_file "doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUBSCRIPTION_AND_GO_LIVE_FLOW.md" "docs/SHOPIFY_COMPANION_SUBSCRIPTION_AND_GO_LIVE_FLOW.md"
copy_file "scripts/verify-shopify-companion.sh" "scripts/verify-shopify-companion.sh"
copy_file "scripts/verify-shopify-companion-uninstall.sh" "scripts/verify-shopify-companion-uninstall.sh"

chmod +x "${OUTPUT_DIR}/scripts/verify-shopify-companion.sh" "${OUTPUT_DIR}/scripts/verify-shopify-companion-uninstall.sh"

cat > "${OUTPUT_DIR}/README.md" <<'EOF'
# Shopify Companion Review Kit

This directory is an assembled handoff package for Shopify app review, design-partner onboarding, and operator support.

Contents:

- `docs/SHOPIFY_COMPANION_APP_REVIEW_GUIDE.md`
- `docs/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md`
- `docs/SHOPIFY_COMPANION_SUBSCRIPTION_AND_GO_LIVE_FLOW.md`
- `scripts/verify-shopify-companion.sh`
- `scripts/verify-shopify-companion-uninstall.sh`

Suggested operator sequence:

1. review the app-review guide
2. confirm the go-live flow for the target store
3. run the normal verification script
4. run uninstall verification on a disposable store mapping
EOF

echo "Shopify Companion review kit generated at ${OUTPUT_DIR}"
