# 010.57 Support Experience Kit Execution Record

Started: 3 November 2026
Status: Implementation in progress; no new deployment or migration yet
Source baseline: `0ea836cc85ec68efc0e33a86b9658f7fd6872648`

## Owner Decisions

- Implement 010.57 fully, including real LoomAI Console adoption, production
  verification, S3 knowledge preservation and legacy ticketing retirement.
- Greenfield: current LoomAI tickets are disposable. No old-ticket import,
  compatibility readers or historical transcript migration is required.
- Preserve actual approved S3 documents/connections, environment-scoped secrets,
  current qualified behavior and real trust boundaries.
- TicketProvider is generic; Desk is the first implementation. External vendor
  adapters remain deferred, but replaceability must have contract-test evidence.

## Streams and Ownership

- Main: shared integration contracts, Console thin adapter/context, provisioning,
  S3/configuration snapshots, actual rollout, quality and customer-journey proof.
- Desk backend: TicketProvider, native persistence, requester/agent/integration
  routes, security and focused persistence/contract tests.
- Support pack: generic support experience and contained installer/host behavior.
- Catalogue: registry/schema/assets, kit bootstrap and template contributions.
- Desk UI: ticket view in the existing managed service UI.

Parallel streams have disjoint write sets. Main reviews and integrates their
work; no parallel task independently deploys or changes customer resources.

## Phase State

- S0: IN_PROGRESS. Baseline complete; contract qualification is still running.
  Current source/philosophy reviewed; actual environment snapshots
  and a fresh production baseline retained privately. Both deployed support
  environments report 13 active sources and AI Fabric 0.8.44. This is deployed
  evidence, not the newer source POM default.
- S1: IN_PROGRESS. Desk backend stream started.
- S2: PENDING integration contract implementation.
- S3: IN_PROGRESS. Support pack and Desk UI streams started.
- S4: IN_PROGRESS. Registry/kit/template stream started.
- S5: PENDING hosted and AI-quality verification.
- S6: PENDING Console cutover, legacy retirement and final guides.

## Evidence Rules

Code or a health probe is not hosted customer-journey proof. Record actual test
counts/skips, source/asset/image identities, exact deployment jobs and first AI
outcomes. Retain failures and known limitations. Never publish secrets or private
customer transcripts. Source-only quality candidates are not accepted defaults.

No completion claim is made by this initial record.

## Integration Progress, 3 November

- Baseline: all nine production quality turns completed. The existing wrong-subject
  compound follow-up was reproduced; it is not counted as a successful answer.
- Catalogue slice: 141 Java tests, 22 Node tests, a PostgreSQL migration and real
  asset packaging passed locally. Managed support binding activation is being
  integrated next.
- Desk ticket UI: 87 full UI tests and production build passed locally; no hosted
  claim yet.
- Console host: provider-delegated request routes, separate private requester
  assertions, runtime transport and minimal consent/resource context added.
  Conversation-first support does not automatically create a ticket.
- The old Console case service/store/controller and duplicate ticket-operation
  store were removed from production source. V227 retires their four tables at
  rollout under the owner's greenfield authorization; no data migration is added.
- Platform backend compilation/package passed with tests skipped while the
  obsolete lifecycle tests are being replaced. This is compilation evidence only.
- An existing conversation may be adopted by a ticket only with independently
  verified runtime ownership and a host-signed principal/conversation binding.
  Model parameters cannot choose requester identity. No transcript copying.

Private evidence directory:
`~/.codex/private/loomai-support-kit-01057/`.
S3 bindings, credentials and approved documents have not been changed.
