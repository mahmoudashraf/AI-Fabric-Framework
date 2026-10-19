# Read-Only Retirement Host Verifier

One-time operator infrastructure for the existing deployment-local target
retirement flow. This is not a customer plugin, inventory database, new hosted
business service, or per-customer root-maintained manifest.

The backend freezes and verifies provider resource ownership and generations.
Coolify's supported, ownership-checked APIs perform deletion. This adapter only
reports actual container IDs, an exact volume name or absence, and approved
retained-directory existence. It never converts an error or present resource
into an absence result. A remaining volume blocks cleanup: SSH deletion and
manual pruning are not a hidden recovery path.

## Installation Boundary

Installing accounts, keys, secrets, sudoers or sshd configuration requires a
separate explicit operator approval. These files do not install themselves.
The bounded 010.36 staging setup received explicit owner approval on 19 October
2026. Source tests alone still do not prove host installation, effective
configuration or live retirement; retain separate installed-state receipts.

Use one dedicated password-locked noninteractive `loomai-retire` identity on
the approved staging host, with a distinct SSH key. Do not reuse a root key,
grant Docker-group membership or general sudo, or enable a shell, forwarding,
PTY, user RC/environment, password or keyboard-interactive authentication.
Permit only the approved production-backend egress and already verified host pin.

Install the launcher at `/usr/local/libexec/loomai-retirement-read-only-entry`
and adapter at `/usr/local/libexec/loomai-retirement-read-only-adapter.py`.
Files and ancestors must be root-owned and not group/world writable. Keep the
account's home and root-managed authorized-key file nonwritable by the account.
Use `restrict`, the exact approved `from` address and the forced `command` in
the sole root-managed authorized-key entry. A separate sshd Match rule is not
required when every effective authentication path is reviewed: disable password,
keyboard-interactive, host-based and GSSAPI authentication, external key commands
and trusted user CAs; inspect every authorized-key path, environment acceptance
and tunnel setting. Keep the public authorized-key file readable by the account
but nonwritable, including its parent directories. The account's shell exists
only to launch the forced command, not to grant interactive access.

If any daemon setting must change, validate `sshd -t`, effective settings and
recovery access before a separately approved reload. Do not weaken an unsupported
configuration or assume key restrictions govern another authentication path.

The sole sudoers command is this exact argv, with `env_reset`, no `SETENV`, no
wildcards or extra arguments:

```text
loomai-retire ALL=(root) NOPASSWD: /usr/bin/python3 -I /usr/local/libexec/loomai-retirement-read-only-adapter.py
```

Validate sudoers before installation. Create
`/etc/loomai-retirement-read-only/docker` root-owned with mode 0700. The adapter
uses only `/usr/bin/docker` and the local `/var/run/docker.sock`, with a fixed
empty Docker config and clean environment. Verify those exact host paths and
the trusted `/data/coolify` directory ownership before activation.

For sudoers list options, clear inherited lists with `!env_keep,!env_check`;
strict `visudo` rejects empty-string list assignments. Check the installed user's
effective `sudo -l` for exactly the single command above, with `NOSETENV` and no
inherited group or broader rule. Preserve failed candidate validation records;
never replay a partially completed installation blindly.

Validate the effective command and permission-tag set, not sudo's display order:
`NOSETENV: NOPASSWD:` and `NOPASSWD: NOSETENV:` represent the same restrictions.
A root `sudo -l -U` query can also exit 0 while reporting that a user is denied;
an exit code alone is not proof of permission. Include explicit command-specific
authorization denials, without executing the denied commands. Read the host's
effective `useradd -D` defaults before selecting flags; `CREATE_MAIL_SPOOL` is
not a valid `-K` override for `login.defs`, and global defaults must not be changed
to accommodate this service account.

Install `host-policy.example.json` as
`/etc/loomai-retirement-read-only/host-policy.json`, root-owned with mode 0600,
setting `dataRootOwnerUid` to the independently inspected owner of exactly
`/data/coolify`. The current staging read-only preflight observes UID 9999;
the example's 0 is not a deployment default or evidence of the host's owner.
Ancestors must still be protected root-owned directories; symlinks, writable
policy files, duplicate/unknown fields and undeclared owners are rejected.
This fixed per-host setting has no customer or per-operation resource manifest.

The approved command grammar is intentionally limited to the backend runner's
five current/legacy container-owner read forms, exact anchored volume-name
lookups and `test -d` on normalized descendants of `/data/coolify` without
symlink components. No arbitrary Docker command, inspect/log/data access, shell
syntax, deletion or repair is accepted. Requests/output and process time are
bounded. Root execution is only for these metadata checks, not customer data.

## Backend Configuration

Store a dedicated key and exact known-host pin through supported private
managed-secret APIs. Never put raw keys in environment variables, command-line
arguments, logs or this repository. The backend key materialization boundary
restores the final LF removed by managed-secret normalization.

```text
PLATFORM_PROVIDER_HOST_VOLUME_RECLAMATION_ENABLED=true
PLATFORM_PROVIDER_HOST_VOLUME_RECLAMATION_TIMEOUT=PT15S
PLATFORM_PROVIDER_HOST_STAGING_USERNAME=loomai-retire
PLATFORM_PROVIDER_HOST_STAGING_PORT=22
PLATFORM_PROVIDER_HOST_STAGING_PRIVATE_KEY_SECRET_NAME=MANAGED_PROVIDER_HOST_STAGING_SSH_PRIVATE_KEY
PLATFORM_PROVIDER_HOST_STAGING_KNOWN_HOSTS_SECRET_NAME=MANAGED_PROVIDER_HOST_STAGING_SSH_KNOWN_HOSTS
```

These are proposed names, not evidence of configuration. Preserve the existing
trusted server UUID, SSH address, provider-reported address and retirement
allowlist. Do not replace a provider-reported `host.docker.internal` value with
a guessed public address. Review shared infrastructure-secret metadata and
explicit rotation/revocation ownership; it is not a customer's managed secret.
Use a normal approved backend rollout with no active customer apply, and verify
effective runtime properties and strict host-key validation afterwards.

## Verification

```sh
python3 -B -m unittest -v test_retirement_host_read_only_adapter.py
/bin/sh -n retirement-host-read-only-entry.sh
```

Tests use synthetic local subprocesses and mocked Docker, with no SSH, Docker
daemon, provider or customer data access. Also run the focused Java
`SshProviderHostCommandRunnerTest`, `CoolifyHostVolumeReclamationServiceTest` and
`TargetRetirementProviderResourcesTest` when changing this boundary.

After separately approved installation, prove denied shell, PTY/forwarding,
mutation, unapproved source and stale host pin; prove actual metadata/presence
results. During real retirement retain the exact provider/host container and
volume readbacks and terminal ledger evidence. Offline tests, an HTTP404 or a
healthy runtime are not cleanup proof. Preserve resources and charged capacity
whenever evidence is incomplete or an outcome is unknown.
