# Kit Navigation And Integration Booking

UI source: `73b49a469c211e28cc92039902809525f91f0975`.
Core-image CI `42114424597` and serving-image CI `42114423331` passed.
Only Console and public-site images were built; backend build skipped.

The website explicitly links to kit details in Console. Console kit pages identify
themselves and link back to the website. Hero and closing actions distinguish
Install kit (existing authenticated setup) from Start a project (integration
consultation). Booking is also available through the public navigation/footer,
customer navigation, Support and product pages, with no account required.

The approved Calendly event loaded and displayed LoomAI Integration - Customer
Success, 30 minutes. No appointment was submitted. Links open in a new tab with
noopener/noreferrer and send no account, email or deployment context. No external
scheduling script or iframe was added.

Local qualification passed 11 kit/detail/booking tests, 16 authentication-return
and continuity tests, 42 catalogue/product layout states, nine synthetic Support
kit setup groups, 28 static route checks, and public theme/preview/mobile checks.
The synthetic checks use actual components; they are not live deployment proof.

## Bootstrap Repair Found By Hosted Verification

The first hosted customer-setup attempt correctly failed with HTTP409
REVIEWED_COMPOSITION_CHANGED. Both configured public bootstrap definitions had
stale target-profile fingerprints. Their runtime artifact fingerprints matched;
the current active target profiles did not. This was not a navigation failure.

Using the existing audited staff publication API, the definitions were copied to
new immutable public bootstrap records with only targets[].profileFingerprint
updated from current profile metadata. Runtime artifacts, plugins, AI settings,
targets, scope, limits, operations and policies were preserved. The old records
were not edited or deleted. Onboarding configuration was pointed at the new
records and the same backend image restarted. No validator was bypassed, no new
backend code or framework version was released, and no customer deployment or
assignment was replaced.

Public new-customer defaults remain the owner-approved dealership simulator and
generic knowledge template. Support installation requires an available approved
Support template for that account; booking a project remains available without
one. This change is not a claim that every account has every template entitlement.

Staging hosted verification passed 21 observations: anonymous kit sign-in and
registration intent, three customer setup entry points, website-return links,
booking actions, public detail themes/layouts, product pages and mobile customer
navigation. The customer's app configurations were unchanged. Only ordinary
self-enrollment calls occurred in the browser; no kit/infrastructure was created
and no AI query or booking was submitted.

Production passed 22 observations, additionally clicking through from the real
website to Console, opening the real Calendly event in a new tab, closing it
without submitting, and returning to the website. All three kit sign-in/setup
entry points, native customer authentication, booking actions, public detail
themes/layouts, Support and mobile navigation passed. The website itself passed
both catalogue routes, ten theme/width layouts and every format preview.

Native revisions confirm the same UI source on staging Console, production
Console and the production public site. The site auto-deployed successfully;
no duplicate deployment was triggered. Both backend configuration-only restarts
finished healthy on the unchanged `8bf95331c11faeea7f4d532fbe1bd767e649b0ae`
image. Protected backend, Desk and customer runtime image tags are unchanged;
customer app configurations are unchanged. No customer infrastructure was created.

The JSON reports and screenshots here contain public presentation or sanitized
qualification results only. Environment snapshots, identity sessions, full offer
definitions and deployment request receipts remain in ignored private material.

Operations follow-up: when changing target-profile identity fields, revalidate
and normally republish public bootstrap definitions before directing new
registrations to them. Do not mutate published definitions or bypass their
composition checks to conceal target drift.
