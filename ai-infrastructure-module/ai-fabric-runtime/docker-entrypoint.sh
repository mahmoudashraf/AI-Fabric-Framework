#!/bin/sh
set -eu

RUNTIME_UID="${RUNTIME_UID:-10001}"
RUNTIME_USER="${RUNTIME_USER:-runtime}"
ENTITY_CONFIG_TARGET="/config/ai-entity-config.yml"

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
  chown "${RUNTIME_UID}:0" "$ENTITY_CONFIG_TARGET" 2>/dev/null || true
  exec gosu "${RUNTIME_UID}:0" sh -c "exec java ${JAVA_OPTS:-} -jar /app/runtime.jar \"$@\"" -- "$@"
fi

prepare_entity_config
exec sh -c "exec java ${JAVA_OPTS:-} -jar /app/runtime.jar \"$@\"" -- "$@"
