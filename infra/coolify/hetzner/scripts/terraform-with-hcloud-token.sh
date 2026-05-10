#!/usr/bin/env bash
set -Eeuo pipefail

if [[ -z "${HCLOUD_TOKEN:-}" ]]; then
  token_file="${HCLOUD_TOKEN_FILE:-/tmp/hetzner_cloud_token.secret}"
  if [[ ! -r "$token_file" ]]; then
    echo "HCLOUD_TOKEN is not set and token file is not readable: $token_file" >&2
    exit 2
  fi
  export HCLOUD_TOKEN
  HCLOUD_TOKEN="$(tr -d '\r\n' < "$token_file")"
fi

if [[ -z "$HCLOUD_TOKEN" ]]; then
  echo "Hetzner Cloud token is empty." >&2
  exit 2
fi

exec terraform "$@"
