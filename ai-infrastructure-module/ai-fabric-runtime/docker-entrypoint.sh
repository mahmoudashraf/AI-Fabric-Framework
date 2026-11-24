#!/bin/sh
set -eu

RUNTIME_UID="${RUNTIME_UID:-10001}"
RUNTIME_USER="${RUNTIME_USER:-runtime}"
ENTITY_CONFIG_TARGET="/config/ai-entity-config.yml"

prepare_specialist_bundle() {
  bundle_prefix="$1"
  bundle_url="$(printenv "${bundle_prefix}_BUNDLE_URL" || true)"
  [ -n "$bundle_url" ] || return 0
  case "$bundle_url" in
    https://*) ;;
    *) echo "Specialist bundle requires HTTPS." >&2; exit 1 ;;
  esac
  if [ -n "$(printenv "${bundle_prefix}_BUNDLE_BASE64" || true)" ]; then
    echo "Specialist bundle must use exactly one transport." >&2
    exit 1
  fi
  expected_hash="$(printenv "${bundle_prefix}_BUNDLE_HASH" || true)"
  digest="${expected_hash#sha256:}"
  case "$digest" in
    *[!a-f0-9]*) echo "Invalid specialist bundle digest." >&2; exit 1 ;;
  esac
  if [ "$expected_hash" = "$digest" ] || [ "${#digest}" -ne 64 ]; then
    echo "Invalid specialist bundle digest." >&2
    exit 1
  fi
  bundle_file="$(mktemp)"
  trap 'rm -f "$bundle_file"' EXIT HUP INT TERM
  if ! curl --fail --silent --proto '=https' --proto-redir '=https' --location --max-redirs 3 \
      --connect-timeout 10 --max-time 30 --max-filesize 65536 --output "$bundle_file" "$bundle_url"; then
    echo "Specialist bundle download failed." >&2
    exit 1
  fi
  bundle_bytes="$(wc -c < "$bundle_file")"
  if [ "$bundle_bytes" -lt 1 ] || [ "$bundle_bytes" -gt 65536 ]; then
    echo "Specialist bundle exceeds its startup budget or is empty." >&2
    exit 1
  fi
  actual_digest="$(sha256sum "$bundle_file")"
  actual_digest="${actual_digest%% *}"
  if [ "$actual_digest" != "$digest" ]; then
    echo "Specialist bundle digest mismatch." >&2
    exit 1
  fi
  export "${bundle_prefix}_BUNDLE_BASE64=$(base64 < "$bundle_file" | tr -d '\r\n')"
  rm -f "$bundle_file"
  trap - EXIT HUP INT TERM
}

prepare_entity_config() {
  source_location="${AI_CONFIG_DEFAULT_FILE:-}"
  if [ -z "$source_location" ]; then
    export SPRING_CONFIG_IMPORT="${SPRING_CONFIG_IMPORT:-optional:classpath:ai-entity-config.yml}"
    return
  fi

  case "$source_location" in
    https://*)
      temporary_target="${ENTITY_CONFIG_TARGET}.tmp"
      rm -f "$temporary_target"
      curl --fail --silent --show-error --location \
        --connect-timeout "${AI_CONFIG_CONNECT_TIMEOUT_SECONDS:-10}" \
        --max-time "${AI_CONFIG_FETCH_TIMEOUT_SECONDS:-30}" \
        --output "$temporary_target" \
        "$source_location"
      test -s "$temporary_target"
      mv "$temporary_target" "$ENTITY_CONFIG_TARGET"
      export SPRING_CONFIG_IMPORT="file:${ENTITY_CONFIG_TARGET}"
      ;;
    http://*)
      if [ "${AI_CONFIG_ALLOW_INSECURE_HTTP:-false}" != "true" ]; then
        echo "Refusing insecure AI_CONFIG_DEFAULT_FILE; use HTTPS or explicitly set AI_CONFIG_ALLOW_INSECURE_HTTP=true." >&2
        exit 1
      fi
      temporary_target="${ENTITY_CONFIG_TARGET}.tmp"
      rm -f "$temporary_target"
      curl --fail --silent --show-error --location \
        --connect-timeout "${AI_CONFIG_CONNECT_TIMEOUT_SECONDS:-10}" \
        --max-time "${AI_CONFIG_FETCH_TIMEOUT_SECONDS:-30}" \
        --output "$temporary_target" \
        "$source_location"
      test -s "$temporary_target"
      mv "$temporary_target" "$ENTITY_CONFIG_TARGET"
      export SPRING_CONFIG_IMPORT="file:${ENTITY_CONFIG_TARGET}"
      ;;
    classpath:*|file:*)
      export SPRING_CONFIG_IMPORT="$source_location"
      ;;
    /*)
      export SPRING_CONFIG_IMPORT="file:${source_location}"
      ;;
    *)
      export SPRING_CONFIG_IMPORT="classpath:${source_location}"
      ;;
  esac
}

# Ensure mounted volumes are writable by the runtime user.
if [ "$(id -u)" = "0" ]; then
  mkdir -p /data /app/data /config
  chown -R "${RUNTIME_UID}:0" /data /app/data /config || true
  prepare_entity_config
  prepare_specialist_bundle LOOMAI_SPECIALIST_ASSISTANCE
  prepare_specialist_bundle LOOMAI_WORKFLOW_ASSISTANCE
  chown "${RUNTIME_UID}:0" "$ENTITY_CONFIG_TARGET" 2>/dev/null || true
  exec gosu "${RUNTIME_UID}:0" sh -c "exec java ${JAVA_OPTS:-} -jar /app/runtime.jar \"$@\"" -- "$@"
fi

prepare_entity_config
prepare_specialist_bundle LOOMAI_SPECIALIST_ASSISTANCE
prepare_specialist_bundle LOOMAI_WORKFLOW_ASSISTANCE
exec sh -c "exec java ${JAVA_OPTS:-} -jar /app/runtime.jar \"$@\"" -- "$@"
