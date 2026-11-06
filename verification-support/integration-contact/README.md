# Personal Integration Contact

The shared Console and public-site integration actions now introduce the actual
engineer before opening the scheduler. Default buttons show the owner's supplied
portrait, `Talk to Mo` and `AI Enablement Engineer`. Kit `Start a project` actions
retain their intent label and open the same personal card.

Approved profile: Mo.Elgammal, Founder / AI Enablement Engineer, United Kingdom.
The portrait is the owner's supplied photograph, not generated imagery. Copies
are bundled at UI `/products/mo-elgammal.png` and site `/assets/mo-elgammal.png`.
Contact configuration is centralized in each frontend's existing integrationTeam
module, with a parity regression for common metadata and portrait bytes.

The card offers the existing Calendly event, WhatsApp, telephone and the existing
Console support-request entry. Scheduling is loaded only after Book a 30-min
call, with a back button to the card. Close/Escape restores the original page
and focus. No availability claim, chat submission, automatic third-party
prefill, new backend, tracking, credential sharing or scheduling script is added.
The request form retains its existing Console account requirements.

Local checks cover Console contact/calendar/back/close in both themes at
1440/390/320px, all three kit-detail Start a project entry points, contact parity,
unchanged CSP, builds/types, public-site cards/previews/theme/mobile-menu stacking.
Hosted verification passed eight observations on each Console and eight on the
public site. This includes all three kit Start a project actions, actual
Calendly date/time selection without submitting a booking, back to the profile,
close/focus restoration, public mobile and authenticated Support/sidebar/mobile
navigation. Signed-in request entry opened the existing form and cancelled it
without submission. WhatsApp and telephone destinations were checked as links;
no call or message was sent.

The initial production public-site qualification used native source
`976c7515491dd4260001040f4ce14ea1df0e606c`. Both staging and production Console
were qualified on native source `f64c2d4703d7908699944fda274a9d0c2ceb2d24`, which
adds explicit opener focus restoration after the Support iframe interaction.
Core CI `42142053210` and `42142883542` passed with backend builds skipped;
site CI `42142053207` passed. All three final deployment jobs finished.
The first two staging checks exposed the focus issue and were not represented
as passes; both final hosted Console runs passed after the focused correction.
Credentials, sessions and customer-page screenshots remain in ignored private
receipts. Public screenshots and safe summaries accompany this record.

## Current Follow-Up State

- Both Consoles are healthy on native source
  `339183006db094c5b942f1c6b83792560059b510`. The production public site is
  healthy on `30a8b1170c087483fee7c158dbd7cd2aa15da85b`. All final deployment
  jobs finished. CI `42145490354`, `42145490355` and `42146835060` passed;
  Platform backend builds were skipped.
- The card now explains Smart Brains, branded AI engagement channels and
  LoomAI Desk handoff, ending with Give your customers the AI experience they
  deserve. Shared introduction/promise fields are checked for parity.
- Explore experiences and Try dealership demo were removed from the homepage
  hero. Talk to Mo remains in the content and mobile menu, not the public-site
  desktop header. Console's own contact entry points remain available.
- `/experiences` and the old general/dealership detail URLs now return fixed
  HTTP301 redirects to their Console equivalents, including trailing slashes.
  Request queries are not forwarded. The three duplicate pages and their three
  unused components were deleted, removed from the sitemap, and Products/menu
  links now point directly to Console. The existing demo remains available.
- No new offer, price or trial terms were published. Discussion of offers on
  the other kits remains a recommendation, not implemented commercial copy.

Narrow follow-up checks passed: metadata/UI interaction tests, frontend types,
Astro check/build, 28 static routes, HTTP GET/HEAD redirect destinations and
method/unknown-path behavior, ten responsive theme states and all nine native
preview formats. Final live site checks passed five scenario groups. Actual
browser clicks also proved homepage navigation to the working Console catalogue
and Products to both Console details, including Start a project and return.
Both Console hosted runs again passed eight observations with the updated
copy and real Calendly selection. No appointment, message or request submitted.
No Platform/runtime/framework/auth/template change was made.

## Branded Console Entry Button

Both Consoles are now healthy on native frontend source
`5fee2718f70c10ac76627b8320e1487f1f390f67`. CI `42150288499` passed,
with the backend image skipped. The public site was not redeployed.

Signed-in Open Console links on the kit and product pages now use the public
website's blue-to-violet fill, northeast arrow, white typography, 8px corners
and subtle shadow. A shared presentation component keeps the existing role-
and page-specific destinations. Anonymous sign-in/registration stays unchanged.
The narrow catalogue header uses separate rows so the larger entry button and
personal contact action cannot overlap the logo or heading.

The UI production build and seven focused tests passed. The final browser
regression covers 24 local theme/surface/width states; live staging and
production each passed 32 states at 1440/768/390/320px, plus authenticated
navigation and anonymous selected-kit sign-in. Screenshots were inspected.
No browser errors or customer-resource mutations occurred. Safe reports are
in `console-entry-local/report.json` and `console-entry-live/{staging,production}.json`.
