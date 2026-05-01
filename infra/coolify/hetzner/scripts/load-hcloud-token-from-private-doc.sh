#!/usr/bin/env bash
set -Eeuo pipefail

private_doc="${1:-${HETZNER_PRIVATE_DOC:-}}"
secret_file="${HCLOUD_TOKEN_FILE:-/tmp/hetzner_cloud_token.secret}"

if [[ -z "$private_doc" ]]; then
  echo "Set HETZNER_PRIVATE_DOC or pass the private document path as the first argument." >&2
  exit 2
fi

if [[ ! -r "$private_doc" ]]; then
  echo "Private document is not readable: $private_doc" >&2
  exit 2
fi

umask 077
tmp_file="$(mktemp "${secret_file}.XXXXXX")"
cleanup() {
  rm -f "$tmp_file"
}
trap cleanup EXIT

if ! perl -0777 -ne '
  my @patterns = (
    qr/(?:HCLOUD_TOKEN|HETZNER_CLOUD_TOKEN|HETZNER_API_TOKEN)\s*[:=]\s*`?([A-Za-z0-9._~|:-]{20,})`?/i,
    qr/Hetzner\s+(?:Cloud\s+)?(?:API\s+)?token\s*[:=]\s*`?([A-Za-z0-9._~|:-]{20,})`?/i
  );
  for my $pattern (@patterns) {
    if ($_ =~ $pattern) {
      print $1;
      exit 0;
    }
  }
  exit 1;
' "$private_doc" > "$tmp_file"; then
  echo "No Hetzner Cloud token pattern was found in the private document." >&2
  exit 3
fi

if [[ ! -s "$tmp_file" ]]; then
  echo "No Hetzner Cloud token pattern was found in the private document." >&2
  exit 3
fi

mv "$tmp_file" "$secret_file"
trap - EXIT
chmod 0600 "$secret_file"
echo "Hetzner Cloud token loaded into local secret file."
