# Protected AI Product Engineer Profile

Requested: 13 November 2026. Canonical route: `https://loomai.pro/founder`.

The previously prepared profile is reused, including its portrait, real Console
walkthrough, framework/provider diagram, Smart Brain capabilities, skills and
professional experience. The heading remains AI Product Engineer, not Founder.

Access uses the existing Supabase proposal code/session lifecycle with a separate
`founder` grant. Only a successful current authorization releases the profile
HTML or its four images. The build removes these files from the public static
tree; static smoke checks enforce that boundary. The source portfolio content is
not confidential source material; access codes and session credentials are never
committed. Invitation expiry is recorded privately. No Platform/runtime/framework
or deployment-template configuration changed for this website work.

## Local Verification

- 21 proposal/content tests pass, including scoped profile HTML/image access,
  code exchange, no-cache responses, CSRF, revocation and fail-closed outage checks.
- Astro check: 89 files, zero errors/warnings/hints. Build passed.
- Static/content smoke: 28 routes; protected HTML/images absent from public output.
- Actual local browser uses the real Supabase grant: manual code entry, wrong-code
  denial, invitation clean redirect, reload, lock/reopen and secure HttpOnly cookie.
- Desktop 1440px, phone 390px and narrow 320px layouts have no horizontal overflow.
  Walkthrough tabs and theme toggle work; visible images load. Zero unexpected
  browser errors. The deliberate wrong-code HTTP 401 is counted separately.
- Initial browser qualification caught inline style/data-font CSP blocks.
  Astro now emits stylesheet/font files rather than inlining them; the strict
  proposal CSP was retained, without unsafe-inline/unsafe-eval exceptions.

The private raw HTTP/browser reports and screenshots are retained under the
owner's private operational directory, not committed. Live rollout qualification
will be recorded separately after the production image is verified.
