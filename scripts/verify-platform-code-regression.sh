#!/usr/bin/env bash
set -euo pipefail

# Platform-owned replacement for the GitHub "Platform Code Regression" workflow.
#
# This script is intentionally workspace-local. It expects the platform backend
# control-plane image to include the checked-out source tree and the required
# toolchain binaries.

BACKEND_TESTS="${BACKEND_TESTS:-true}"
PRODUCT_TESTS="${PRODUCT_TESTS:-true}"
INFRASTRUCTURE_TESTS="${INFRASTRUCTURE_TESTS:-true}"
UI_BUILD="${UI_BUILD:-true}"
SHELL_SYNTAX_CHECKS="${SHELL_SYNTAX_CHECKS:-true}"

run_step() {
  local label="$1"
  shift
  echo ""
  echo "== ${label} =="
  "$@"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}" >&2
    exit 2
  fi
}

if [[ "${BACKEND_TESTS}" == "true" || "${PRODUCT_TESTS}" == "true" || "${INFRASTRUCTURE_TESTS}" == "true" ]]; then
  require_cmd mvn
fi
if [[ "${UI_BUILD}" == "true" ]]; then
  require_cmd npm
fi

if [[ "${BACKEND_TESTS}" == "true" ]]; then
  run_step "Platform backend tests" mvn -f Platfrom/backend/pom.xml test -DskipITs
fi

if [[ "${PRODUCT_TESTS}" == "true" ]]; then
  run_step "Install AI Fabric framework artifacts for product tests" env \
    MAVEN_OPTS="${MAVEN_OPTS:--Xmx2g}" \
    mvn -f ai-infrastructure-module/pom.xml -B -V -DskipTests install \
      -pl '!integration-Testing/testcontainers-support,!integration-Testing/integration-tests,!integration-Testing/relationship-query-integration-tests,!integration-Testing/chat-session-integration-tests,!integration-Testing/behavior-integration-tests' \
      -am
  run_step "ai-fabric-product tests" mvn -f ai-fabric-product/pom.xml test
fi

if [[ "${INFRASTRUCTURE_TESTS}" == "true" ]]; then
  run_step "Targeted platform infrastructure tests" mvn -f ai-infrastructure-module/pom.xml \
    -pl ai-infrastructure-data-sync,victor-databases/ai-infrastructure-vector-pinecone,victor-databases/ai-infrastructure-vector-qdrant,victor-databases/ai-infrastructure-vector-weaviate,victor-databases/ai-infrastructure-vector-milvus \
    -am test -DskipITs
fi

if [[ "${UI_BUILD}" == "true" ]]; then
  run_step "Platform UI build" bash -lc 'cd Platfrom/ui && npm ci && npm run build'
fi

if [[ "${SHELL_SYNTAX_CHECKS}" == "true" ]]; then
  run_step "Shell syntax checks" bash -lc '
    bash -n scripts/verify-ecommerce-deployment.sh &&
    bash -n scripts/verify-marketplace-install-flow.sh &&
    bash -n scripts/verify-managed-vector-providers.sh &&
    bash -n scripts/verify-platform-admin-regression.sh &&
    bash -n scripts/verify-platform-code-regression.sh &&
    bash -n scripts/verify-shopify-companion.sh &&
    bash -n scripts/verify-shopify-companion-uninstall.sh &&
    bash -n scripts/verify-vector-deployment.sh &&
    bash -n scripts/resolve-verification-rollouts.sh &&
    bash -n scripts/run-platform-deployment-verification.sh &&
    bash -n scripts/run-platform-state-verification-suite.sh &&
    bash -n scripts/run-shopify-companion-rollout.sh
  '
fi
