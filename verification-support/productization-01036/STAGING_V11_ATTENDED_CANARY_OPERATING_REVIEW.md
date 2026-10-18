# Staging v11 Attended Canary Operating Review

Reviewed: 2026-10-18. Scope: `dep-6e29bab9`, version `ver-3c0e3b88`,
customer `cus-19a32c9b`, target `dtp-coolify-staging-behavior` only.
This is the implementing agent's operational acceptance of a bounded synthetic
measurement, under the owner's existing implementation and campaign approval.
It is not owner acceptance of a production service, an SLA, or a launch gate.

## Operating Policy

- One immutable v11 composition: 2,750 mCPU, 5,888 MiB memory and 16 GiB disk,
  including its full dependency graph. No candidate build runs on this host.
- Admission uses the independently checked physical measurements and actual
  held-reservation ledger, not a reduced feature graph or invented capacity.
  Preserve 2,000 mCPU, 2 GiB memory and 16 GiB disk headroom. Existing swap
  liability stays included in baseline memory; swap is not usable capacity.
- The initial measurement authorizes a staging workload measurement only.
  Observe startup, service health and representative work before claiming fit.
  Suspend further work if resource pressure, control-plane failures, unknown
  financial outcomes or an unbounded background loop appear. Reconcile exact
  operation receipts before retrying; never restart with new allowance keys.
- Owner-approved Free-01 is existing, external and retained. Use only this
  deployment's immutable collection prefix. Never clear, drop, recreate, upgrade
  or repurpose its data. Retained-index retirement remains fail-closed until a
  durable retained outcome and charged-capacity accounting are implemented.
- Keep the whole reservation charged if retention blocks cleanup. Neither a
  stop request nor an absent runtime alone proves all resources were removed.
  No production placement, production promotion or paid vector fallback.
- The existing cumulative campaign remains USD 12 including embeddings,
  200,000 tokens and 50 interactive requests. No reset or replacement grant.
  Check actual runtime budget-store identity, holds and usage after activation.
- Adopt the proposed lifecycle bounds: 24-hour inactivity/lifetime, one-hour
  notice lead, no extension, 15-minute drain, 24-hour recovery, five-minute
  retry, three attempts and 60-second readback freshness. These are bounded
  staging defaults, not evidence that every expiry/notice path passed live.

## Synthetic Data Handling

- Use fictional inventory, test identities and the three fictional policies in
  the owner's private `dealership/staging/` S3 prefix. No real customer content,
  real shopper contact details or real Auto Trader credentials are introduced.
- Approved inventory/policy excerpts may reach the configured OpenAI models
  for embeddings and grounded generation under the unchanged campaign. Derived
  vectors go only to this deployment's Free-01 prefix. This does not attest to
  the provider's retention or training terms or authorize real personal data.
- Test requests and human replies use fictional contact values in the isolated
  receiver/Basic Inbox databases. No real lead, booking or Auto Trader write
  is performed, and no real shopper is contacted.
- Keep S3 private, read-only, prefix-scoped and customer-owned. Do not mutate
  source objects or claim LoomAI-managed document storage.
- Secrets remain in private files and target-owned secret storage. Evidence
  exposes safe identity, state, counts and fingerprints only. The wider serving
  key's provider ACL is not represented as per-deployment tenant isolation.
- Retain derived synthetic data as authorized. This staging review is not a
  production privacy assessment, legal agreement or customer retention policy.

## Attended Support And Escalation

- The implementing Codex session operates and observes this canary while work
  is active. It accepts responsibility for reading failures, stopping further
  attempts and preserving receipts. This does not promise unattended coverage.
- The existing active customer-admin account `platform-user:usr-7238e886`
  is the contact record; the owner's dedicated `Required for production` chat
  is the escalation route for permissions or external integration failures.
- Console and SMTP are configured. Actual notice delivery remains a separate
  live verification; configuration is not represented as delivered email.
- Keep the 30 previously paused workloads unchanged. No pruning, recreation,
  data deletion or silent capacity release. Hetzner access stays open under
  the owner's explicit instruction until full implementation is complete.

## Recovery Evidence And Limits

Fresh pre-fix Platform database archives at migration 193 were restored into
isolated local PostgreSQL 18 fixtures and those fixtures were removed:

- Staging: `/private/tmp/01036-local-control-plane-restore-l4lMMDmKgXts/report.json`.
- Production: `/private/tmp/01036-local-control-plane-restore-YGALPNohTkT1/report.json`.

The staging archive includes the retained vector binding. These are actual
control-plane database restores only. They do not prove customer-runtime,
whole-topology, provider-index or financial-ledger recovery. In particular,
never reactivate a stale financial snapshot without reconciling external use.
Those broader gates remain open and cannot be inferred from this review.
If runtime, receiver or inbox recovery is needed, stop further canary attempts
and preserve their actual handles, volumes, credentials and operation receipts.
Source inventory/policies can rebuild derived evidence only after scope and
retained-index reconciliation; they cannot restore conversations, request
receipts or budget counters. Do not rebuild those stores from empty defaults,
clear the retained index, or claim whole-topology recovery without an actual
scoped export/restore and financial reconciliation.
