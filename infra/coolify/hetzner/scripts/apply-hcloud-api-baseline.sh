#!/usr/bin/env bash
set -Eeuo pipefail

API_BASE="${HCLOUD_API_BASE:-https://api.hetzner.cloud/v1}"
TOKEN_FILE="${HCLOUD_TOKEN_FILE:-/tmp/hetzner_cloud_token.secret}"
LOCATION="${HCLOUD_LOCATION:-nbg1}"
NETWORK_ZONE="${HCLOUD_NETWORK_ZONE:-eu-central}"
NETWORK_NAME="${HCLOUD_NETWORK_NAME:-loom-coolify-network}"
NETWORK_CIDR="${HCLOUD_NETWORK_CIDR:-10.44.0.0/16}"
NETWORK_SUBNET_CIDR="${HCLOUD_NETWORK_SUBNET_CIDR:-10.44.0.0/24}"
FIREWALL_NAME="${HCLOUD_FIREWALL_NAME:-loom-coolify-firewall}"
SSH_KEY_NAME="${HCLOUD_SSH_KEY_NAME:-loom-coolify-operator}"
SSH_KEY_PATH="${COOLIFY_SSH_KEY_PATH:-$HOME/.ssh/loom_coolify_hetzner_ed25519}"
SSH_USER="${COOLIFY_SSH_USER:-loomops}"
ENVIRONMENTS="${COOLIFY_ENVIRONMENTS:-staging}"
DNS_ZONE_NAME="${COOLIFY_DNS_ZONE_NAME:-loomai.pro}"
INSTALL_COOLIFY="${INSTALL_COOLIFY:-true}"
DOCKER_ADDRESS_POOL_BASE="${DOCKER_ADDRESS_POOL_BASE:-172.30.0.0/16}"
DOCKER_ADDRESS_POOL_SIZE="${DOCKER_ADDRESS_POOL_SIZE:-24}"
COOLIFY_INSTALL_URL="${COOLIFY_INSTALL_URL:-https://cdn.coollabs.io/coolify/install.sh}"

if [[ ! -r "$TOKEN_FILE" ]]; then
  echo "Token file is not readable: $TOKEN_FILE" >&2
  exit 2
fi

HCLOUD_TOKEN="$(tr -d '\r\n' < "$TOKEN_FILE")"
if [[ -z "$HCLOUD_TOKEN" ]]; then
  echo "Hetzner Cloud token is empty." >&2
  exit 2
fi

mkdir -p "$HOME/.ssh"
chmod 700 "$HOME/.ssh"
if [[ ! -f "$SSH_KEY_PATH" ]]; then
  ssh-keygen -t ed25519 -N "" -C "$SSH_KEY_NAME" -f "$SSH_KEY_PATH" >/dev/null
fi
chmod 600 "$SSH_KEY_PATH"
chmod 644 "${SSH_KEY_PATH}.pub"
SSH_PUBLIC_KEY="$(<"${SSH_KEY_PATH}.pub")"

if [[ -z "${SSH_ALLOWED_CIDRS:-}" ]]; then
  CURRENT_IP="$(curl -fsS https://api.ipify.org)"
  SSH_ALLOWED_CIDRS="${CURRENT_IP}/32"
fi

if [[ -z "${COOLIFY_DASHBOARD_ALLOWED_CIDRS:-}" ]]; then
  COOLIFY_DASHBOARD_ALLOWED_CIDRS="$SSH_ALLOWED_CIDRS"
fi

cidr_json() {
  local csv="$1"
  jq -cn --arg csv "$csv" '$csv | split(",") | map(gsub("^\\s+|\\s+$"; "")) | map(select(length > 0))'
}

SSH_ALLOWED_CIDRS_JSON="$(cidr_json "$SSH_ALLOWED_CIDRS")"
DASHBOARD_ALLOWED_CIDRS_JSON="$(cidr_json "$COOLIFY_DASHBOARD_ALLOWED_CIDRS")"

api() {
  local method="$1"
  local path="$2"
  local body_file="${3:-}"
  local output_file
  output_file="$(mktemp)"
  local http_code
  if [[ -n "$body_file" ]]; then
    http_code="$(curl -sS -o "$output_file" -w "%{http_code}" \
      -X "$method" \
      -H "Authorization: Bearer $HCLOUD_TOKEN" \
      -H "Content-Type: application/json" \
      --data-binary "@${body_file}" \
      "${API_BASE}${path}")"
  else
    http_code="$(curl -sS -o "$output_file" -w "%{http_code}" \
      -X "$method" \
      -H "Authorization: Bearer $HCLOUD_TOKEN" \
      -H "Content-Type: application/json" \
      "${API_BASE}${path}")"
  fi
  if [[ "$http_code" -lt 200 || "$http_code" -ge 300 ]]; then
    echo "Hetzner API request failed: $method $path -> HTTP $http_code" >&2
    jq -c '.' "$output_file" >&2 || cat "$output_file" >&2
    rm -f "$output_file"
    exit 1
  fi
  cat "$output_file"
  rm -f "$output_file"
}

api_body() {
  local method="$1"
  local path="$2"
  local body="$3"
  local body_file
  body_file="$(mktemp)"
  printf '%s' "$body" > "$body_file"
  api "$method" "$path" "$body_file"
  rm -f "$body_file"
}

resource_id_by_name() {
  local collection="$1"
  local root="$2"
  local name="$3"
  api GET "/${collection}?per_page=100" | jq -r --arg root "$root" --arg name "$name" '.[$root][] | select(.name == $name) | .id' | head -n 1
}

wait_action() {
  local action_id="$1"
  local status
  for _ in $(seq 1 120); do
    status="$(api GET "/actions/${action_id}" | jq -r '.action.status')"
    case "$status" in
      success) return 0 ;;
      error)
        echo "Hetzner action failed: $action_id" >&2
        return 1
        ;;
    esac
    sleep 2
  done
  echo "Timed out waiting for Hetzner action: $action_id" >&2
  return 1
}

labels_json() {
  local environment="$1"
  jq -cn --arg environment "$environment" '{
    "loom.component": "coolify",
    "loom.environment": $environment,
    "loom.hostRole": "coolify-controller",
    "loom.managedBy": "hcloud-api",
    "loom.owner": "platform"
  }'
}

ensure_ssh_key() {
  local id body
  id="$(resource_id_by_name ssh_keys ssh_keys "$SSH_KEY_NAME")"
  if [[ -n "$id" ]]; then
    echo "$id"
    return
  fi
  body="$(jq -cn \
    --arg name "$SSH_KEY_NAME" \
    --arg public_key "$SSH_PUBLIC_KEY" \
    --argjson labels "$(labels_json shared)" \
    '{name: $name, public_key: $public_key, labels: $labels}')"
  api_body POST /ssh_keys "$body" \
    | jq -r '.ssh_key.id'
}

firewall_rules_json() {
  jq -cn \
    --argjson ssh "$SSH_ALLOWED_CIDRS_JSON" \
    --argjson dashboard "$DASHBOARD_ALLOWED_CIDRS_JSON" '
    [
      {direction: "in", protocol: "tcp", port: "22", source_ips: $ssh},
      {direction: "in", protocol: "tcp", port: "80", source_ips: ["0.0.0.0/0", "::/0"]},
      {direction: "in", protocol: "tcp", port: "443", source_ips: ["0.0.0.0/0", "::/0"]}
    ]
    + (if ($dashboard | length) > 0 then [
      {direction: "in", protocol: "tcp", port: "8000", source_ips: $dashboard}
    ] else [] end)
  '
}

ensure_firewall() {
  local id rules body action_id
  rules="$(firewall_rules_json)"
  id="$(resource_id_by_name firewalls firewalls "$FIREWALL_NAME")"
  if [[ -z "$id" ]]; then
    body="$(jq -cn \
      --arg name "$FIREWALL_NAME" \
      --argjson labels "$(labels_json shared)" \
      --argjson rules "$rules" \
      '{name: $name, labels: $labels, rules: $rules}')"
    api_body POST /firewalls "$body" | jq -r '.firewall.id'
    return
  fi

  body="$(jq -cn --argjson rules "$rules" '{rules: $rules}')"
  action_id="$(api_body POST "/firewalls/${id}/actions/set_rules" "$body" | jq -r '.action.id // .actions[0].id')"
  wait_action "$action_id" >/dev/null
  echo "$id"
}

ensure_network() {
  local id body existing_subnet action_id
  id="$(resource_id_by_name networks networks "$NETWORK_NAME")"
  if [[ -z "$id" ]]; then
    body="$(jq -cn \
      --arg name "$NETWORK_NAME" \
      --arg ip_range "$NETWORK_CIDR" \
      --argjson labels "$(labels_json shared)" \
      '{name: $name, ip_range: $ip_range, labels: $labels}')"
    id="$(api_body POST /networks "$body" | jq -r '.network.id')"
  fi

  existing_subnet="$(api GET "/networks/${id}" | jq -r --arg ip_range "$NETWORK_SUBNET_CIDR" '.network.subnets[]? | select(.ip_range == $ip_range) | .ip_range' | head -n 1)"
  if [[ -z "$existing_subnet" ]]; then
    body="$(jq -cn \
      --arg type "cloud" \
      --arg network_zone "$NETWORK_ZONE" \
      --arg ip_range "$NETWORK_SUBNET_CIDR" \
      '{type: $type, network_zone: $network_zone, ip_range: $ip_range}')"
    action_id="$(api_body POST "/networks/${id}/actions/add_subnet" "$body" | jq -r '.action.id // .actions[0].id')"
    wait_action "$action_id" >/dev/null
  fi
  echo "$id"
}

host_config() {
  local environment="$1"
  case "$environment" in
    staging)
      jq -cn '{name: "coolify-staging-01", server_type: "cpx32", autoupdate: "true", runtime_wildcard: "*.runtime-staging"}'
      ;;
    production)
      jq -cn '{name: "coolify-prod-01", server_type: "ccx23", autoupdate: "false", runtime_wildcard: "*.runtime"}'
      ;;
    *)
      echo "Unknown environment: $environment" >&2
      exit 2
      ;;
  esac
}

render_bootstrap() {
  local environment="$1"
  local host_name="$2"
  local autoupdate="$3"
  cat <<EOF
#!/usr/bin/env bash
set -Eeuo pipefail

ENVIRONMENT="$environment"
HOST_NAME="$host_name"
INSTALL_COOLIFY="$INSTALL_COOLIFY"
COOLIFY_INSTALL_URL="$COOLIFY_INSTALL_URL"
COOLIFY_AUTOUPDATE="$autoupdate"
DOCKER_ADDRESS_POOL_BASE="$DOCKER_ADDRESS_POOL_BASE"
DOCKER_ADDRESS_POOL_SIZE="$DOCKER_ADDRESS_POOL_SIZE"
SSH_ALLOWED_CIDRS_JSON='$SSH_ALLOWED_CIDRS_JSON'
DASHBOARD_ALLOWED_CIDRS_JSON='$DASHBOARD_ALLOWED_CIDRS_JSON'
BOOTSTRAP_LOG_PATH="/var/log/loom-coolify-bootstrap.log"
BOOTSTRAP_STATUS_PATH="/var/lib/loom-coolify/bootstrap-status.json"

mkdir -p "\$(dirname "\$BOOTSTRAP_LOG_PATH")" "\$(dirname "\$BOOTSTRAP_STATUS_PATH")"
touch "\$BOOTSTRAP_LOG_PATH"
chmod 0600 "\$BOOTSTRAP_LOG_PATH"
exec > >(tee -a "\$BOOTSTRAP_LOG_PATH") 2>&1

write_status() {
  local phase="\$1"
  local status="\$2"
  local detail="\$3"
  local now
  now="\$(date -Is)"
  jq -n \
    --arg environment "\$ENVIRONMENT" \
    --arg host "\$HOST_NAME" \
    --arg phase "\$phase" \
    --arg status "\$status" \
    --arg detail "\$detail" \
    --arg updatedAt "\$now" \
    '{environment: \$environment, host: \$host, phase: \$phase, status: \$status, detail: \$detail, updatedAt: \$updatedAt}' \
    > "\${BOOTSTRAP_STATUS_PATH}.tmp"
  mv "\${BOOTSTRAP_STATUS_PATH}.tmp" "\$BOOTSTRAP_STATUS_PATH"
  chmod 0644 "\$BOOTSTRAP_STATUS_PATH"
}

on_error() {
  local line="\$1"
  write_status "bootstrap" "failed" "failed at line \${line}"
}
trap 'on_error "\$LINENO"' ERR

write_status "bootstrap" "running" "starting host bootstrap"
export DEBIAN_FRONTEND=noninteractive

systemctl reload ssh || systemctl reload sshd || true

ufw --force reset
ufw default deny incoming
ufw default allow outgoing
jq -r '.[]' <<< "\$SSH_ALLOWED_CIDRS_JSON" | while read -r cidr; do
  if [[ -n "\$cidr" ]]; then
    ufw allow from "\$cidr" to any port 22 proto tcp
  fi
done
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow from "\$DOCKER_ADDRESS_POOL_BASE" to any port 22 proto tcp
jq -r '.[]' <<< "\$DASHBOARD_ALLOWED_CIDRS_JSON" | while read -r cidr; do
  if [[ -n "\$cidr" ]]; then
    ufw allow from "\$cidr" to any port 8000 proto tcp
  fi
done
ufw --force enable

cat >/etc/fail2ban/jail.d/loom-coolify-local.conf <<F2B
[sshd]
ignoreip = 127.0.0.1/8 ::1 \$DOCKER_ADDRESS_POOL_BASE
F2B
systemctl enable --now fail2ban || true
systemctl restart fail2ban || true
systemctl enable --now unattended-upgrades || true
mkdir -p /data/coolify /var/lib/loom-coolify
chmod 0755 /data/coolify /var/lib/loom-coolify

if [[ "\$INSTALL_COOLIFY" == "true" ]]; then
  write_status "coolify" "running" "installing Coolify"
  curl -fsSL "\$COOLIFY_INSTALL_URL" -o /tmp/coolify-install.sh
  chmod 0700 /tmp/coolify-install.sh
  env \
    AUTOUPDATE="\$COOLIFY_AUTOUPDATE" \
    DOCKER_ADDRESS_POOL_BASE="\$DOCKER_ADDRESS_POOL_BASE" \
    DOCKER_ADDRESS_POOL_SIZE="\$DOCKER_ADDRESS_POOL_SIZE" \
    bash /tmp/coolify-install.sh
  rm -f /tmp/coolify-install.sh
  if getent group docker >/dev/null; then
    usermod -aG docker "$SSH_USER"
  fi
else
  write_status "coolify" "skipped" "install_coolify=false"
fi

write_status "bootstrap" "completed" "host bootstrap completed"
EOF
}

render_cloud_init() {
  local environment="$1"
  local host_name="$2"
  local autoupdate="$3"
  local bootstrap
  bootstrap="$(render_bootstrap "$environment" "$host_name" "$autoupdate" | sed 's/^/      /')"
  cat <<EOF
#cloud-config
hostname: $host_name
manage_etc_hosts: true
ssh_pwauth: false
disable_root: true

users:
  - default
  - name: $SSH_USER
    gecos: LoomAI Coolify Operator
    groups: [adm, sudo]
    lock_passwd: true
    shell: /bin/bash
    sudo: ["ALL=(ALL) NOPASSWD:ALL"]
    ssh_authorized_keys:
      - $SSH_PUBLIC_KEY

package_update: true
package_upgrade: true
packages:
  - ca-certificates
  - curl
  - fail2ban
  - git
  - gnupg
  - htop
  - jq
  - lsb-release
  - rsync
  - ufw
  - unattended-upgrades
  - unzip

write_files:
  - path: /etc/ssh/sshd_config.d/99-loom-hardening.conf
    owner: root:root
    permissions: "0644"
    content: |
      PasswordAuthentication no
      KbdInteractiveAuthentication no
      PermitRootLogin no
      PubkeyAuthentication yes
      X11Forwarding no
      AllowTcpForwarding yes
      MaxAuthTries 3
      ClientAliveInterval 300
      ClientAliveCountMax 2
  - path: /usr/local/sbin/loom-coolify-bootstrap.sh
    owner: root:root
    permissions: "0755"
    content: |
$bootstrap

runcmd:
  - [bash, -lc, "/usr/local/sbin/loom-coolify-bootstrap.sh"]
EOF
}

ensure_server() {
  local environment="$1"
  local ssh_key_id="$2"
  local firewall_id="$3"
  local network_id="$4"
  local config name server_type autoupdate id body user_data
  config="$(host_config "$environment")"
  name="$(jq -r '.name' <<< "$config")"
  server_type="$(jq -r '.server_type' <<< "$config")"
  autoupdate="$(jq -r '.autoupdate' <<< "$config")"
  id="$(resource_id_by_name servers servers "$name")"
  if [[ -n "$id" ]]; then
    echo "$id"
    return
  fi

  user_data="$(render_cloud_init "$environment" "$name" "$autoupdate")"
  body="$(jq -cn \
    --arg name "$name" \
    --arg server_type "$server_type" \
    --arg image "ubuntu-24.04" \
    --arg location "$LOCATION" \
    --arg user_data "$user_data" \
    --argjson ssh_key_id "$ssh_key_id" \
    --argjson firewall_id "$firewall_id" \
    --argjson network_id "$network_id" \
    --argjson labels "$(labels_json "$environment")" \
    '{
      name: $name,
      server_type: $server_type,
      image: $image,
      location: $location,
      ssh_keys: [$ssh_key_id],
      firewalls: [{firewall: $firewall_id}],
      networks: [$network_id],
      public_net: {enable_ipv4: true, enable_ipv6: true},
      user_data: $user_data,
      labels: $labels,
      start_after_create: true
    }')"
  api_body POST /servers "$body" | jq -r '.server.id'
}

server_json() {
  local server_id="$1"
  api GET "/servers/${server_id}" | jq '.server'
}

wait_server_running() {
  local server_id="$1"
  local status
  for _ in $(seq 1 180); do
    status="$(server_json "$server_id" | jq -r '.status')"
    if [[ "$status" == "running" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for server to run: $server_id" >&2
  return 1
}

ssh_cmd() {
  local ip="$1"
  shift
  ssh -i "$SSH_KEY_PATH" \
    -o BatchMode=yes \
    -o ConnectTimeout=10 \
    -o StrictHostKeyChecking=accept-new \
    "${SSH_USER}@${ip}" "$@"
}

wait_bootstrap() {
  local environment="$1"
  local ip="$2"
  local raw status detail
  echo "Waiting for SSH and bootstrap on ${environment} (${ip})..."
  for _ in $(seq 1 240); do
    if raw="$(ssh_cmd "$ip" 'sudo cat /var/lib/loom-coolify/bootstrap-status.json 2>/dev/null' 2>/dev/null)"; then
      status="$(jq -r '.status // empty' <<< "$raw" 2>/dev/null || true)"
      detail="$(jq -r '.detail // empty' <<< "$raw" 2>/dev/null || true)"
      if [[ "$status" == "completed" ]]; then
        echo "${environment} bootstrap completed: ${detail}"
        return 0
      fi
      if [[ "$status" == "failed" ]]; then
        echo "${environment} bootstrap failed: ${detail}" >&2
        ssh_cmd "$ip" 'sudo tail -n 120 /var/log/loom-coolify-bootstrap.log' >&2 || true
        return 1
      fi
    fi
    sleep 15
  done
  echo "Timed out waiting for bootstrap on ${environment}" >&2
  return 1
}

verify_coolify_local() {
  local environment="$1"
  local ip="$2"
  local code
  code="$(ssh_cmd "$ip" 'curl -fsS -o /dev/null -w "%{http_code}" http://127.0.0.1:8000 2>/dev/null || true')"
  if [[ "$code" =~ ^(200|301|302|307|308)$ ]]; then
    echo "${environment} Coolify local HTTP check passed with ${code}"
  else
    echo "${environment} Coolify local HTTP check returned ${code:-no-response}" >&2
    return 1
  fi
}

public_ipv6_for_dns() {
  local ip="$1"
  ssh_cmd "$ip" 'ip -6 -o addr show scope global | awk "{print \$4}" | grep -v "^fd" | cut -d/ -f1 | head -n 1' 2>/dev/null || true
}

ssh_key_id="$(ensure_ssh_key)"
firewall_id="$(ensure_firewall)"
network_id="$(ensure_network)"

echo "Using Hetzner location ${LOCATION}; SSH/dashboard CIDRs are restricted to configured allowlists."

IFS=',' read -ra envs <<< "$ENVIRONMENTS"
for environment in "${envs[@]}"; do
  environment="$(sed 's/^ *//;s/ *$//' <<< "$environment")"
  [[ -z "$environment" ]] && continue
  server_id="$(ensure_server "$environment" "$ssh_key_id" "$firewall_id" "$network_id")"
  wait_server_running "$server_id"
  server="$(server_json "$server_id")"
  ip="$(jq -r '.public_net.ipv4.ip' <<< "$server")"
  name="$(jq -r '.name' <<< "$server")"
  echo "${environment}: ${name} is running at ${ip}"
  wait_bootstrap "$environment" "$ip"
  verify_coolify_local "$environment" "$ip"
  ipv6="$(public_ipv6_for_dns "$ip")"
  echo "${environment} DNS plan:"
  if [[ "$environment" == "production" ]]; then
    echo "  A coolify.ops.${DNS_ZONE_NAME} -> ${ip}"
    if [[ -n "$ipv6" ]]; then
      echo "  AAAA coolify.ops.${DNS_ZONE_NAME} -> ${ipv6}"
    fi
    echo "  A *.runtime.${DNS_ZONE_NAME} -> ${ip}"
    if [[ -n "$ipv6" ]]; then
      echo "  AAAA *.runtime.${DNS_ZONE_NAME} -> ${ipv6}"
    fi
  else
    echo "  A *.runtime-staging.${DNS_ZONE_NAME} -> ${ip}"
    if [[ -n "$ipv6" ]]; then
      echo "  AAAA *.runtime-staging.${DNS_ZONE_NAME} -> ${ipv6}"
    fi
  fi
done
