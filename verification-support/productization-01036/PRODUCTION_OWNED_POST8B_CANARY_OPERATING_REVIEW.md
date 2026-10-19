# Production-Owned AI-Only Canary Operating Review

Reviewed: 2026-10-19. Implementing-agent review under the owner's full-plan
authorization, not customer launch acceptance or an SLA.

## Scope And Evidence

- Production control plane, customer `cus-9fd67f55`, tenant `ten-3977745c`,
  deployment `dep-096af01e`, published version `ver-76abc66f`.
- Customer staging uses `dtp-coolify-staging-behavior` on `46.224.145.148`;
  customer production uses `dtp-coolify-production-behavior` on `46.225.162.106`.
  Both target records belong to the production control plane. No staging
  workload may spill onto the production server.
- Both control-plane backend rollouts actually verify commit
  `8b6671596ffcd7e623a8c674546827ef99264510`, immutable image
  `sha256:f40ce672c0890a582513cb3308a4d44248722da96b99dc49d0181623ddd77235`,
  schema 206, health UP, preserved database/configuration and effective runtime
  worker/SMTP settings. This did not upgrade the framework or runtime image.
- Fresh staging baseline: `/private/tmp/01044-post8b-staging-host-baseline-20261019.json`,
  SHA256 `55d85e50f8bc67dcce6393d2707b3d9e6e97530638d8e8ad1b8076fd12a5933d`.
  Production baseline: `/private/tmp/01044-post8b-production-host-baseline-20261019.json`,
  SHA256 `013fc4d54f8b0ed0a2aaa12b6dcd815b7bee599f068f7d65f1b0d68907c44a94`.
  Both contain 61 actual samples ending around 19:39 UTC. These are current-fleet
  observations, not candidate/startup/concurrency or external-provider quota proof.
- The unused Free01 ownership handoff actually passed all five normal API
  commands and complete 139-table/audit checks on both control planes. Final
  proof SHA256 `e8ce2a20fa7663a8dc94f7650ba4523e15afc8568446b2417868ef93e25041ba`.
  Binding `evb-bc916b29-2cb0-496a-8c08-45091e16f155` belongs only to this
  customer's staging target. Empty provider inventory was observed; no vector
  records were copied and no paid cluster or new key was created.

## Admission Review

The immutable offer's measurement reference
`01044-post205-host-baselines-20261019` is retained only as its existing opaque
compatibility anchor. It is not the timestamp or provenance of this review.
Any new policy must bind new target measurement references, actual fresh report
hashes and current placement fingerprints to these post-rollout observations;
the old measurement reports remain historical and unchanged.

- Proposed conservative staging baseline: 1,000 mCPU, 7 GiB RAM and 64 GiB disk;
  headroom: 2,000 mCPU, 2 GiB RAM and 16 GiB disk. Memory baseline includes the
  observed swap liability; swap is never additional capacity.
- Proposed conservative production baseline: 1,000 mCPU, 4 GiB RAM and 28 GiB
  disk; headroom: 500 mCPU, 2 GiB RAM and 16 GiB disk. The measured host has four
  cores; staging capacity cannot be counted as spare production capacity.
- The generated full per-target topology is 2,000 mCPU, 3,584 MiB RAM and
  6,144 MiB disk, one replica, optional managed AI-state database, two indexes
  and one Basic Inbox dependency. Recheck the actual generated manifest before
  reserving; this document cannot override it.
- Admission must include all 14 dimensions. Nonphysical counters are explicit
  administrative ceilings, not provider measurements. At most two resident
  target envelopes, one per target; only one actual apply/model campaign runs
  at a time, even when both target reservations remain charged.
- The proposed 1 GiB index-byte accounting ceiling per target is not an actual
  Free01 provider quota or proof of an existing positive byte charge. Review
  exact active reservation/binding identities before retention accounting.
- Check both control planes' current held ledgers and actual provider backlogs
  immediately before publication/apply. Current production backlog is empty;
  the old staging reservation is RELEASED. No manual SQL repair is permitted.
- Physical customer quota must outlive the last candidate admitted within the
  measured-policy window plus its maximum lifetime. Independent review caught
  a proposed two-hour grant deadline that could shorten staging on activity;
  correct that input before publication, without extending provider funding.

## Data, Authority And Financial Bounds

- The deployment owns AI runtime, optional AI operational state, derived index
  and Basic Inbox only. The simulator, dealership inventory/CRM, command receiver
  and their business databases remain independent external integrations.
- Index only fictional advertiser `990001` stock and the three fictional
  customer-owned S3 policies under each target's approved prefix. Staging and
  production credentials/bindings and derived namespaces remain separate.
- Production initializes its own index from approved production sources. No
  staging vector copy, conversation migration or cross-target token reuse.
- Approved fictional excerpts may reach the configured OpenAI provider for
  embeddings/grounded generation. This is not a privacy approval for real shopper
  data, real leads, real Auto Trader access or provider retention/training terms.
- The owner's whole-task ceiling is USD 12, 400,000 technical tokens and 50
  chat-ingress attempts, including prior usage and holds across both targets.
  No reset, replacement allowance or recharge is implied. Check actual ledgers
  before issuing target grants; physical admission is not provider funding.
- Actual customer API acceptance uses `CUSTOMER_ADMIN` ownership, not staff
  impersonation. The confirmed notice contact is `platform-user:usr-f31de571`.
  SMTP TLS/authentication is verified, but delivered email/inbox receipt is not.

## Lifecycle, Recovery And Remaining Gates

- Preserve the offer's stated 24-hour staging/recovery bounds; do not shorten
  customer recovery invisibly merely to accelerate a test. Any shorter dedicated
  expiry fixture requires an explicit separate reviewed contract and honest label.
- Keep resources charged while retirement, external-index retention, human work
  or an uncertain provider outcome remains unresolved. A stopped runtime is not
  proof of database/volume deletion or released capacity.
- Use target-scoped retirement with exact owned resource/volume identities and
  verified host readback. No broad Docker prune, fabricated ownership or deletion
  of external fixtures, customer S3 objects, production resources or shared apps.
- Actual prior control-plane backups/restores prove only control-plane recovery.
  Source-based reindexing cannot restore conversations, Inbox work, action
  receipts or financial counters. Customer AI-state restore, budget reconciliation
  and complete topology recovery remain separate live acceptance gates.
- The implementing session observes startup/resource pressure and representative
  workload while active, preserves uncertain receipts and stops further effects
  on failure. `Required for production` is the external-prerequisite escalation
  route; this is not an unattended support promise.
- This review supports bounded admission inputs, not a claim that apply,
  indexing, promotion, delivered notices, retirement, P7 or customer UI
  extraction has already passed. Hosted acceptance remains open; UI last.

## Actual Subsequent Progress

- Policy `01036-post8b-prodcp-canary-20261019` and the customer's physical quota
  were published and verified through normal APIs and stored-record readback at
  19:47-48 UTC. Measurement validity remains two hours; corrected physical quota
  expires 22 October, covering the maximum candidate lifetime plus margin.
  These operations issued no provider funding or runtime release.
- Backend key-file fix `79552555f` passes 38 focused tests, actual packaging and
  backend-only CI. Staging verifies its immutable image and all 139-table/config
  fences; production remains healthy on `8b6671596` pending the bounded host
  setup approval and single normal configuration rollout.
- Read-only verifier assets and per-host owner policy are versioned under
  `infra/coolify/hetzner/retirement-verifier`; 19 Python tests, shell syntax and
  independent review pass. Real read-only host preflight finds the new account
  absent and `/data/coolify` service-owned by UID 9999. No key/account/config is
  installed. Coolify remains the deletion owner; the host adapter cannot repair
  or hide leftovers. Approval is pending in `Required for production`.
- No customer reservation, financial grant, provider/model call or runtime apply
  is complete. Initial placement/review windows expired without a reservation;
  collect fresh normal evidence before the next command. Longer lifecycle proof
  also requires the canary entitlement to remain current. No old proof is relabeled.
