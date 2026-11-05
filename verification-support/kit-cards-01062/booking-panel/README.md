# In-Page Integration Booking

Frontend source: `ab4a8778f20027a78c5579f193b17f1b718646c4`.
Core-image CI `42125363664` passed; the backend build was skipped.
Serving-image CI `42125363589` passed for the public site only.

Book integration call and Start a project now open a floating scheduler rather
than navigating away. Console uses its shared booking button/dialog; the website
uses a native dialog shared by its public pages. The existing approved Calendly
event is embedded on demand. Closing removes the iframe, restores focus, and
retains the original route and unsent host-page state. Mobile menus remain usable
under the booking panel. An explicit external-tab option is retained.

No account/email/resource prefill, token forwarding, scheduling script, booking
backend or database changes were introduced. CSP permits the exact Calendly
frame origin without broadening script access. Calendly itself handles time
availability, the booking form and its cookie choices.

Local checks passed both frontend builds, Astro diagnostics, 28 static routes,
20 focused Console tests (including responsive/light/dark panel interaction and
both server CSP variants), public card/theme/preview regression and mobile menu
modal stacking. Test scheduling fixtures are used only in local interaction
tests; the hosted qualification reports use the real Calendly event.

Hosted reports exercise real date/time selection on desktop/mobile, close and
return to the same page, and lazy iframe cleanup. Signed-in Console checks also
cover Support, the customer sidebar and mobile navigation. No appointment is
submitted, no AI query is sent and no kit/deployment/assignment is changed.
The protected-image summaries compare non-frontend application tags before and
after rollout. Sessions, credentials and full Coolify responses stay private.

Staging and production Console each passed five hosted observations; the public
website passed three. Both Console deployments finished healthy, and native
revisions on all three frontends match the source above. The website rollout
was automatic; no duplicate site deployment was requested. No host-page API
mutation occurred in the final hosted checks.
