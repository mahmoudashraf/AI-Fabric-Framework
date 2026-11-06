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

The production public site is healthy on native source
`976c7515491dd4260001040f4ce14ea1df0e606c`. Both staging and production Console
are healthy on native source `f64c2d4703d7908699944fda274a9d0c2ceb2d24`, which
adds explicit opener focus restoration after the Support iframe interaction.
Core CI `42142053210` and `42142883542` passed with backend builds skipped;
site CI `42142053207` passed. All three final deployment jobs finished.
The first two staging checks exposed the focus issue and were not represented
as passes; both final hosted Console runs passed after the focused correction.
Credentials, sessions and customer-page screenshots remain in ignored private
receipts. Public screenshots and safe summaries accompany this record.
