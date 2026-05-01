# Coolify Hetzner Host Automation

Slice 0 creates reproducible Hetzner Cloud infrastructure for the first Coolify staging and production hosts.

Managed resources:

- SSH key resource
- shared firewall
- private network and subnet
- staging server: `coolify-staging-01`, `cpx32`, Ubuntu 24.04
- production server: `coolify-prod-01`, `ccx23`, Ubuntu 24.04
- optional attached data volumes
- DNS `A`/`AAAA` record plan outputs for `coolify.ops.loomai.pro`, `*.runtime.loomai.pro`, and `*.runtime-staging.loomai.pro`
- cloud-init bootstrap for SSH hardening, updates, base tools, UFW, Coolify install, production `AUTOUPDATE=false`, local deployment-user ACLs, proxy start, and status logging

DNS is intentionally output-only in this slice because no DNS-provider credential was available in local context. Add a provider-specific DNS module only after credentials are available through secret-safe handling.

## Secret Handling

The Hetzner token must never be committed, printed, pasted into docs, or stored in `*.tfvars`.

Preferred local flow:

```bash
cd infra/coolify/hetzner
export HETZNER_PRIVATE_DOC=/absolute/path/to/private-document.md
./scripts/load-hcloud-token-from-private-doc.sh
./scripts/terraform-with-hcloud-token.sh init
```

If the token is already in a local secret file:

```bash
cd infra/coolify/hetzner
export HCLOUD_TOKEN_FILE=/tmp/hetzner_cloud_token.secret
./scripts/terraform-with-hcloud-token.sh init
```

## Configure

```bash
cd infra/coolify/hetzner
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:

- set `ssh_public_key` to the operator public key
- set `ssh_allowed_cidrs` to operator, VPN, or Tailscale egress CIDRs
- set `dashboard_allowed_cidrs` only when the dashboard/API allowlist is ready
- set `staging_platform_api_allowed_cidrs` only when the Railway/Platform control plane must reach the staging Coolify API directly; prefer a narrow egress CIDR, and use `0.0.0.0/0` only as a temporary staging unblock when there is no stable egress IP
- enable `enable_data_volumes` only when the mount and backup policy is ready

The default location is `nbg1`. Use `fsn1` instead if project capacity or latency makes it preferable.

## Validate

```bash
PATH=/tmp/codex-tools/bin:$PATH terraform fmt -check -recursive
PATH=/tmp/codex-tools/bin:$PATH ./scripts/terraform-with-hcloud-token.sh init
PATH=/tmp/codex-tools/bin:$PATH ./scripts/terraform-with-hcloud-token.sh validate
bash -n scripts/terraform-with-hcloud-token.sh
bash -n scripts/load-hcloud-token-from-private-doc.sh
bash -n cloud-init/coolify-bootstrap.sh.tftpl
```

If Terraform/OpenTofu is unavailable, the API fallback runner can apply the same Slice 0 baseline with existing system tools only:

```bash
HCLOUD_TOKEN_FILE=/tmp/hetzner_cloud_token.secret \
COOLIFY_ENVIRONMENTS=staging \
./scripts/apply-hcloud-api-baseline.sh

HCLOUD_TOKEN_FILE=/tmp/hetzner_cloud_token.secret \
COOLIFY_ENVIRONMENTS=production \
./scripts/apply-hcloud-api-baseline.sh
```

The fallback runner creates a local SSH key at `~/.ssh/loom_coolify_hetzner_ed25519` if needed, restricts SSH and Coolify port `8000` to the current public IP by default, and performs all server setup on Hetzner.

## Apply Safely

Apply staging and production infrastructure from the same plan, but verify staging bootstrap before trusting production for workloads.

```bash
./scripts/terraform-with-hcloud-token.sh plan -out=tfplan
./scripts/terraform-with-hcloud-token.sh apply tfplan
```

After apply:

```bash
./scripts/terraform-with-hcloud-token.sh output servers
./scripts/terraform-with-hcloud-token.sh output planned_dns_records
```

SSH as the non-root operator user:

```bash
ssh loomops@<server-ip>
sudo cat /var/lib/loom-coolify/bootstrap-status.json
sudo tail -n 200 /var/log/loom-coolify-bootstrap.log
```

Do not put production workloads on Coolify until dashboard/API access is protected and Coolify backups have been configured.

## Backup And Restore Rehearsal

Run a non-destructive rehearsal from the repo root or this directory:

```bash
COOLIFY_BACKUP_ENVIRONMENT=staging \
infra/coolify/hetzner/scripts/rehearse-coolify-backup-restore.sh

COOLIFY_BACKUP_ENVIRONMENT=production \
infra/coolify/hetzner/scripts/rehearse-coolify-backup-restore.sh
```

The script runs on the Hetzner host over SSH. It creates a root-only backup under `/var/backups/loom-coolify/<environment>-<timestamp>` with:

- `coolify-db.dump`: custom-format Coolify Postgres dump
- `coolify-state-files.tgz`: `/data/coolify` state files, SSH keys, proxy/app/service/database directories
- `SHA256SUMS`
- `restore-rehearsal-status.json`

The restore drill is non-destructive: it restores the DB dump into a temporary database, extracts the state archive into a temporary directory, verifies required files, then removes the temporary restore targets. The backup artifacts contain secrets and must stay root-only or be moved only to encrypted backup storage.

## Rebuild

For a disposable staging rebuild:

```bash
./scripts/terraform-with-hcloud-token.sh apply -replace='hcloud_server.coolify["staging"]'
```

For production, do not replace the server until Coolify state, `/data/coolify`, `APP_KEY`, SSH keys, and app volumes have been backed up and a restore target has been rehearsed.

## Destroy

Destroy is destructive. Confirm no active workloads depend on these hosts.

```bash
./scripts/terraform-with-hcloud-token.sh plan -destroy -out=tfdestroy
./scripts/terraform-with-hcloud-token.sh apply tfdestroy
```

If `volume_delete_protection=true`, remove protection deliberately before destroying optional data volumes.

## Live Slice 0 Outputs

Applied on 2026-05-01 through the Hetzner API fallback runner because Terraform/OpenTofu was unavailable locally.

Local Terraform state now exists for these live resources after import/adoption on 2026-05-01. The state file is intentionally ignored by git; move it to an encrypted/remote Terraform backend before multiple operators manage this infrastructure. Do not delete the local state and run apply against the same names unless you intend to re-import or deliberately recreate resources.

Live Hetzner resource IDs:

- SSH key `loom-coolify-operator`: `111657146`
- firewall `loom-coolify-firewall`: `10915120`
- network `loom-coolify-network`: `12181920`
- network subnet `10.44.0.0/24`: Terraform import ID `12181920-10.44.0.0/24`
- staging server `coolify-staging-01`: `128757995`
- production server `coolify-prod-01`: `128758153`

| Environment | Host | Type | Region | IPv4 | IPv6 |
|---|---|---|---|---|---|
| staging | `coolify-staging-01` | `cpx32` | `nbg1` | `46.224.145.148` | `2a01:4f8:c2c:83e2::1` |
| production | `coolify-prod-01` | `ccx23` | `nbg1` | `46.225.162.106` | `2a01:4f8:1c18:c04::1` |

Coolify dashboard URLs before DNS:

- staging: `http://46.224.145.148:8000`
- production: `http://46.225.162.106:8000`

Generated root-user credentials are stored locally at `/tmp/coolify_admin_credentials.env`; do not commit or paste that file.

Coolify API tokens are stored locally at `/tmp/coolify_api_tokens.env`; do not commit or paste that file. The file is shell-quoted because Coolify Sanctum tokens contain `|`.

Coolify API/control-plane bootstrap values:

| Environment | Coolify version | Project | Environment | Server | Destination | Private key |
|---|---|---|---|---|---|---|
| staging | `4.0.0` | `loom-staging` / `id069t43frp519u5i3dg2jpr` | `staging` / `h1433m09ezg882q7xmf3ae0x` | `zf25hgk9694bt7q0zwb98ado` | `xjhfu65nacrr30xax5cp0ry7` | `n117g3g8n75p6x048drc11on` |
| production | `4.0.0` | `loom-production` / `t1400k32bg9yd764chyt1slm` | `production` / `rn5sbycbix789i973okr9ugm` | `kvufjk78dj4wyhjgp1mlxecr` | `r3thf2xmxcjn1tt2bclabebz` | `bmllhht0k5m0gfkuk0ovwisz` |

The built-in Coolify server records were switched from root SSH to the hardened `loomops` user. Server validation currently reports `is_reachable=true` and `is_usable=true` for both hosts. The host firewall/fail2ban configuration allows the local Coolify Docker address pool for self-validation while keeping external SSH and dashboard access restricted to the setup allowlist.

The bootstrap now grants the Coolify SSH deployment user ACL access to the local `/data/coolify` resource directories and starts the bundled proxy from `/data/coolify/proxy`. The same ACL/proxy fix was applied live on staging and production on 2026-05-01; both `coolify-proxy` containers are healthy.

Until DNS credentials or delegation are available, disposable staging app smoke tests use temporary `sslip.io` domains under `*.46.224.145.148.sslip.io`. Replace the seeded temporary suffixes with `*.runtime-staging.loomai.pro` and `*.runtime.loomai.pro` after DNS automation is active.

Terraform adoption status:

- Terraform `1.6.6` was installed into `/tmp/codex-tools/bin` after local caches were cleaned for disk space.
- `terraform init -backend=false`, `terraform fmt -check -recursive`, and `terraform validate` pass.
- Live SSH key, network, subnet, firewall, staging server, and production server were imported into local ignored Terraform state.
- `terraform apply` was run only for the saved in-place firewall convergence plan: `0 added, 1 changed, 0 destroyed`.
- Post-apply `terraform plan -detailed-exitcode` returned `0`.
- The server resource ignores imported create-time fields (`network`, `public_net`, `ssh_keys`, `user_data`) so adopted hosts are not replaced by later plans.
- A staging-only Platform API firewall was added live on 2026-05-01 as `loom-coolify-staging-platform-api-firewall` (`10916648`) and attached only to `coolify-staging-01`; staging host UFW allows port `8000/tcp` from anywhere for Railway-originated Platform preflight/provisioning until a stable control-plane egress CIDR or stronger access layer is available. Production remains behind the original shared dashboard allowlist.

Planned DNS records still need the active DNS provider credential for `loomai.pro`, whose current nameservers are `dns1.registrar-servers.com` and `dns2.registrar-servers.com`.

Strict live provider smoke status:

- staging disposable app create/start/status/delete now passes through `scripts/verify-coolify-provider.sh` with `COOLIFY_STRICT_APPLICATION_SMOKE=true`.
- cleanup confirms staging returns to zero applications after the smoke app is deleted.

Backup/restore rehearsal status:

- 2026-05-01 staging rehearsal passed through this runner: `/var/backups/loom-coolify/staging-20260501T214218Z`
- 2026-05-01 production rehearsal passed through this runner: `/var/backups/loom-coolify/production-20260501T214218Z`
- Each rehearsal produced `coolify-db.dump`, `coolify-state-files.tgz`, `SHA256SUMS`, and `restore-rehearsal-status.json`, then restored the DB dump into a temporary database and verified file archive extraction.

## Slice 1 Plan

Next implementation slice should stay inside the Platform/Railway compatibility layer:

- add `DeploymentProviderType` with `RAILWAY_API`, `RAILWAY_STUB`, and reserved `COOLIFY`
- add `deployment_target_profiles` and seed Railway-compatible default profiles
- extend deployment release records with nullable target profile/provider/artifact/handle fields while keeping current `provisioningTarget` compatibility
- introduce a provider registry that dispatches by target profile provider type
- wrap existing Railway API and stub providers without changing current Railway behavior
- add tests proving existing Railway provisioning still works when profiles are present
- do not call Coolify API in Slice 1
