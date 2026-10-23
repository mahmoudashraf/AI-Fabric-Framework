# M3 Saved-App Qualification

The original real customer save failed with `PLACEMENT_EVIDENCE_STALE` and
created no app. The UI correction reuses the existing exact-offer/target placement
check before explicit app commands; backend authorization is unchanged. It does
not reserve capacity, deploy a runtime or silently retry a failed write.

Local qualification covers denied checks, visible dialog errors, retained input,
explicit retry with the same idempotency key, exact assigned target, changed
account/assignment/entitlement, unmount and old/new-target reassignment.

The hosted lifecycle script uses a newly created temporary draft only. Its route
guard prohibits deployment, runtime, assignment and existing app mutations. It
checks create/reload/edit/reload, delete cancellation/confirmation and unchanged
primary app/customer records. Draft cleanup must be recorded, not assumed.

Run it only with explicit `--allow-draft-writes`, credentials in a private JSON
file, an exact customer and primary-app ID, and a fresh private output directory.
Never put passwords, session cookies, auth configuration or raw app records here.
Hosted results and rollout receipts are added only after actual completion.
