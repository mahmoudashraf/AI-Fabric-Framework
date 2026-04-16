#!/bin/sh
set -eu

ollama serve &
server_pid=$!

until ollama list >/dev/null 2>&1; do
  sleep 2
done

if [ -n "${OLLAMA_BOOTSTRAP_MODELS:-}" ]; then
  echo "$OLLAMA_BOOTSTRAP_MODELS" | tr ',' '\n' | while read -r model; do
    if [ -n "$model" ]; then
      ollama pull "$model"
    fi
  done
fi

wait "$server_pid"
