# Platform UI Capability Baseline

Read-only source capture and comparison for 010.36 shared-editor extraction.
This is not a browser/live release gate and changes no product configuration.

The frozen `2026-10-14-admin-operator-source-baseline.json` is from commit
`5206acd97423ee857b5a60c1fe370633bf3fd5a9`.
Its sourceCommit property and the companion capability register are authoritative.

The register is
`doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_36_ADMIN_OPERATOR_UI_CAPABILITY_BASELINE_2026_10_14.md`.
It maps 39 capability groups to the current staff console and defines the
required role-aware browser parity process. Source controls/API declarations
are inventory, not proof of authorized or successful execution.

Run from the repository root with the existing Platform UI dependencies installed:

```sh
node verification-support/platform-ui-capability-baseline/capture.mjs --compare verification-support/platform-ui-capability-baseline/2026-10-14-admin-operator-source-baseline.json
node --test verification-support/platform-ui-capability-baseline/capture.test.mjs
```

After P0-P5 functional verification, use `--output <new-dated-path.json>` to
capture a separate pre-extraction baseline. Existing files are never overwritten.
The script uses TypeScript AST parsing, tracked/untracked nonignored UI source,
and SHA-256 hashes; it performs no requests to live Platform or provider APIs.
Do not include credentials or private environment files in a snapshot.

Comparison emits `reviewRequired`, not an automatic functional pass/fail.
Resolve route/API/control/permission changes against the capability register
and browser evidence. Equivalent component moves and approved contract changes
need an explicit mapping; lost functionality must not be hidden by recapturing
over the original baseline. Current script tests exercise capture, nonoverwrite,
and static change detection only.
