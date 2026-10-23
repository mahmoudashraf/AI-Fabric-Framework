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

Published UI: `7a1c41598e7fdeba6694b90d1e39aa69e17116ca`, CI40021896499;
both servers verified with backend skipped. The real saved-draft flow passes.
The original stale-placement failure and the first post-fix final-comparison
failure are retained. The latter exposed an overbroad test invariant: an app
origin is intentionally projected into an editable security draft, so the
derived deployment configuration status may become DRAFT without changing its
published release. The corrected assertion records this distinction and compares
the actual authority, released records and restored draft security instead.

Both temporary apps are deleted. An independent final read confirms no temporary
origins remain and the original app is ACTIVE/ready on its unchanged assignment.
Existing shared Engage hostname propagation briefly caused APP_DELIVERY to wait;
its route generation13 is now applied/IDLE. No customer runtime was redeployed.
This qualifies representative supported M3 saves, not M4 promotion, all possible
profile/domain fields or an arbitrary customer configuration editor.

Continuity remains a follow-up: shared route generation changes temporarily make
an unchanged existing hostname fail APP_DELIVERY. Public manifest/bootstrap
require that check. Final readiness convergence does not prove uninterrupted
delivery and is not a fix for that coupling. See M6 in010.45; no backend change
was made here.
