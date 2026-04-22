#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHOPIFY_WORKSPACE_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
REPO_ROOT="$(cd "${SHOPIFY_WORKSPACE_DIR}/../.." && pwd)"
WIDGET_DIST_DIR="${REPO_ROOT}/max-mode-widget/dist"
WIDGET_IIFE_PATH="${WIDGET_DIST_DIR}/max-mode-widget.iife.js"
EXTENSION_ASSETS_DIR="${SHOPIFY_WORKSPACE_DIR}/extensions/companion-theme-app-extension/assets"
TARGET_PATH="${EXTENSION_ASSETS_DIR}/max-mode-widget.iife.js"

if [[ ! -f "${WIDGET_IIFE_PATH}" ]]; then
  echo "Missing Max Mode IIFE bundle: ${WIDGET_IIFE_PATH}" >&2
  echo "Build it first with: npm -C max-mode-widget run build" >&2
  exit 1
fi

mkdir -p "${EXTENSION_ASSETS_DIR}"
cp "${WIDGET_IIFE_PATH}" "${TARGET_PATH}"

echo "Synced Max Mode widget bundle to ${TARGET_PATH}"
