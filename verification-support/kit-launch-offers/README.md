# Kit Launch Offers

Frontend source `92cd86778d07b7b37d05dbebfdac0ee3fb964b7d` is live and healthy
on staging Console, production Console and the production public site. Native
container revisions match. Core CI42154355391 and serving CI42154355394 passed;
backend images were skipped. The public site auto-deployed without a duplicate
deployment request.

All three cards have compact, kit-colored offer strips. Customer Experience
shows Integration included and Tailored pricing; Support shows Setup included,
Knowledge onboarding included and Tailored pricing. Only Dealership retains
the existing GBP500/month, three-month free period and Free integration offer.
Detail pages match the offers. Scope wording makes included integration,
AI usage, storage, agent seats and external charges subject to the agreed
proposal. Dealership's pilot acceptance milestone, free-period start and billing
start are agreed in writing, not automatically enforced by this marketing UI.

No billing, entitlement, runtime, framework or customer-resource changes.
Local checks passed 12 focused tests, the UI build, Astro check/build,
28-route static smoke, 42 Console responsive/theme states, ten site layout
states and the native preview formats. The website and Console offer metadata
and scope copy have an equality regression.

Live browser evidence passed twelve theme/width/surface states across the site
and both Consoles, all three production detail journeys, scope text and personal
Start a project panels. No form or appointment was submitted. Reports and
screenshots are under `live/`, `local-console/` and `local-site/`.
