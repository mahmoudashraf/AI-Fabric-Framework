#!/bin/sh
set -eu

VECTORIZATION_RUNNER_UID="${VECTORIZATION_RUNNER_UID:-10003}"

if [ "$(id -u)" = "0" ]; then
  exec gosu "${VECTORIZATION_RUNNER_UID}:0" sh -c "exec java ${JAVA_OPTS:-} -jar /app/vectorization-runner.jar \"$@\"" -- "$@"
fi

exec sh -c "exec java ${JAVA_OPTS:-} -jar /app/vectorization-runner.jar \"$@\"" -- "$@"
