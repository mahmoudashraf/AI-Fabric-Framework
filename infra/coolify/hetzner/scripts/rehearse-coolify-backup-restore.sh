#!/usr/bin/env bash
set -Eeuo pipefail

ENVIRONMENT="${COOLIFY_BACKUP_ENVIRONMENT:-staging}"
SSH_USER="${COOLIFY_BACKUP_SSH_USER:-loomops}"
SSH_KEY_PATH="${COOLIFY_BACKUP_SSH_KEY_PATH:-$HOME/.ssh/loom_coolify_hetzner_ed25519}"
BACKUP_ROOT="${COOLIFY_BACKUP_ROOT:-/var/backups/loom-coolify}"

case "$ENVIRONMENT" in
  staging)
    SSH_HOST="${COOLIFY_BACKUP_SSH_HOST:-46.224.145.148}"
    ;;
  production)
    SSH_HOST="${COOLIFY_BACKUP_SSH_HOST:-46.225.162.106}"
    ;;
  *)
    if [[ -z "${COOLIFY_BACKUP_SSH_HOST:-}" ]]; then
      echo "Set COOLIFY_BACKUP_SSH_HOST for custom environment: $ENVIRONMENT" >&2
      exit 2
    fi
    SSH_HOST="$COOLIFY_BACKUP_SSH_HOST"
    ;;
esac

if [[ ! -r "$SSH_KEY_PATH" ]]; then
  echo "SSH key is not readable: $SSH_KEY_PATH" >&2
  exit 2
fi

ssh -o BatchMode=yes \
  -o ConnectTimeout=10 \
  -o StrictHostKeyChecking=accept-new \
  -i "$SSH_KEY_PATH" \
  "$SSH_USER@$SSH_HOST" \
  "COOLIFY_BACKUP_ENVIRONMENT='$ENVIRONMENT' COOLIFY_BACKUP_ROOT='$BACKUP_ROOT' bash -s" <<'REMOTE'
set -Eeuo pipefail

env_name="${COOLIFY_BACKUP_ENVIRONMENT}"
backup_root="${COOLIFY_BACKUP_ROOT}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="${backup_root}/${env_name}-${timestamp}"
restore_db="coolify_restore_rehearsal_${env_name}"

sudo install -d -m 700 "$backup_root" "$backup_dir"

sudo sh -c "docker exec coolify-db pg_dump -U coolify -Fc coolify > '$backup_dir/coolify-db.dump'"
sudo tar --xattrs --acls \
  -C /data/coolify \
  -czf "$backup_dir/coolify-state-files.tgz" \
  source/.env \
  ssh/keys \
  proxy/dynamic \
  applications \
  databases \
  services \
  backups \
  sentinel
sudo sh -c "cd '$backup_dir' && sha256sum coolify-db.dump coolify-state-files.tgz > SHA256SUMS"

sudo docker cp "$backup_dir/coolify-db.dump" coolify-db:/tmp/coolify-restore-rehearsal.dump >/dev/null
sudo docker exec coolify-db dropdb -U coolify --if-exists "$restore_db" >/dev/null
sudo docker exec coolify-db createdb -U coolify "$restore_db" >/dev/null
sudo docker exec coolify-db pg_restore -U coolify -d "$restore_db" /tmp/coolify-restore-rehearsal.dump >/dev/null
restored_user_count="$(sudo docker exec coolify-db psql -U coolify -d "$restore_db" -tAc "select count(*) from users;")"
sudo docker exec coolify-db dropdb -U coolify "$restore_db" >/dev/null
sudo docker exec coolify-db rm -f /tmp/coolify-restore-rehearsal.dump >/dev/null

restore_smoke_dir="$backup_dir/file-restore-smoke"
sudo install -d -m 700 "$restore_smoke_dir"
sudo tar -xzf "$backup_dir/coolify-state-files.tgz" -C "$restore_smoke_dir" source/.env ssh/keys >/dev/null
sudo test -s "$restore_smoke_dir/source/.env"
sudo test -d "$restore_smoke_dir/ssh/keys"
sudo rm -rf "$restore_smoke_dir"

sudo sh -c "cat > '$backup_dir/restore-rehearsal-status.json'" <<EOF
{"environment":"$env_name","timestamp":"$timestamp","databaseRestore":"passed","fileRestore":"passed","restoredUserCount":$restored_user_count}
EOF
sudo sh -c "chmod 600 '$backup_dir'/*"

echo "backup-dir=$backup_dir"
sudo find "$backup_dir" -maxdepth 1 -type f -printf "%f %m %s bytes\n" | sort
sudo cat "$backup_dir/restore-rehearsal-status.json"
REMOTE
