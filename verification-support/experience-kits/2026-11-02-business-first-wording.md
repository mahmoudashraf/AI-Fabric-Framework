# Business-First Kit Wording

## Scope

Source: `9b25221059fbb4c3e8d5dbe023607acb7b06e74d`.

The public site and Console kit catalogue, detail, setup and overview describe
business outcomes first. The original Platform/Engage/Desk product names remain.
Main-screen labels use Your AI assistant, Your customer workspace, Get started,
Create my workspace and Get setup help. No changes to the admin/operator UI,
framework, AI prompts, actions, retrieval or runtime behavior are included.

V223 updates only the two canonical catalogue definitions' summary, description
and features. The PostgreSQL regression verifies stable names, template links,
version and workspace defaults. Customer kit records are not rewritten.

Readiness copy is a presentation mapping of exact known codes and statuses, not
text matching of AI output. Original codes, statuses and messages remain in
Technical details. Unknown codes and warning statuses retain the original
message. Activation and ownership conditions are unchanged. Setup explicitly
states it creates saved configuration only, not a live assistant, paid services
or an assigned human agent.

## Local Qualification

- Backend: 33 passed, zero failures/errors/skips. Includes five real PostgreSQL
  migration/JPA cases and 28 kit service cases.
- Console: 36 kit/auth tests passed, none skipped. Includes three readiness-copy
  regressions, unchanged creation/assignment eligibility and safe unknown codes.
- Console production build passed. Existing runtime-config and bundle-size
  warnings remain; no new build error.
- Public site: Astro check passed across 74 files with zero errors/warnings;
  production build, content graph and 28-route static smoke passed.
- Scoped diff whitespace check passed.

## Hosted Qualification

Rollout completed on 3 November 2026 (the work began on 2 November).

- Core image workflow `41548926792` succeeded for exact source above.
- Staging backend job `bit0qrzune5dcj6rwnsekt09` and UI job
  `lnrnv6aulogpu0c90kyv5ml4` finished.
- Production backend job `h11vxgfrse41ut3t85gpr8tw` and UI job
  `nhkj0s1ewwdjfmtqpeuh1up9` finished.
- Native containers for all four services are healthy, with OCI revision labels
  matching the exact source. Both public catalogue APIs return the new summaries
  and retain version `1.0.0` for each kit. Native database readback confirms V223
  succeeded in both environments.
- Public site job `u1366dnsm2s5b941nh5sw3np` finished at the exact source;
  its native container is healthy. This Dockerfile image has no OCI revision
  label, so site provenance uses the Coolify source commit, not a claimed label.

## Visible Customer Journey

Production browser checks verified the public homepage and dealership detail,
Console catalogue and dealership detail, setup through Review your kit, and the
existing Northfield kit overview. The original customer session was retained.

The review screen explicitly says no paid services start at this step. The final
Create my workspace command was not submitted: this wording check did not create
another kit or provision infrastructure. The existing review kit retained its
saved name/branding and remained Not live yet, with human support Setup requested.

Expanding Technical details showed the original ASSIGNMENT, APP_DOMAIN and
APP_DELIVERY blocked messages and exact statuses; they were not erased or treated
as passing. The simulator disclosure and own-agent staffing boundary remain.

Actual browser widths inspected were 1280px (desktop) and 521px (narrow pane),
without horizontal document overflow or observed text overlap. The viewport
override did not change the existing tab to the requested dimensions; no 390px
or 1440px requalification is claimed for this copy pass. Previous layout
qualification remains historical, not a substitute for an unperformed check.
Temporary tabs and viewport overrides were cleared; the owner's review kit tab
was restored. Screenshots are in `business-copy-live-2026-11-02/` beside this file.
