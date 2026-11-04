# Support Kit: Email And Live Mobile Follow-Up

Date: 4 November 2026. Scope: owner-requested completion of ticket email and live
phone-size verification. Complex AI follow-up quality is explicitly accepted as
a remaining limitation; no prompt/framework optimization is part of this change.

Status: implemented, deployed and qualified on staging/production. Ticket email
delivery is verified through real Gmail inbox receipts. Live phone-size browser
checks are qualified; physical iPhone/Safari hardware testing is not claimed.

## Source And Local Checks

- Console/pack `102dade215d3dbf23c156535643e42bd964231db`: opt-in Email
  request updates control, verified original Supabase email and signed private
  host delivery assertion. Email is not chosen by a model, form or ticket field.
- Initial Console/pack `ce01d686e9f9851497d0640cebd1dae921268ffe`: support-enabled
  gate and standalone fixed viewport correction. Core image CI `41753200255`
  succeeded. Both staging backend and Console are native-source verified and
  healthy after jobs `qe83oqzt2arhcnquxab9h2fn` and `mu0d6ei7vf68q6g03pzpqc3x`.
- Desk `781b23b40e243762f24ce98e26302ce0923a96c7`: encrypted, requester-scoped
  opt-in preferences and native shared-DB outbox. Four attempts, bounded backlog,
  recipient/event deduplication, current binding/space checks, isolated SMTP
  worker, safe references and sign-in links. Human replies become eligible only
  after correlated callback completion. No transcript or reusable token in mail.
- Focused Platform tests: 14 passed, zero failures/skips. Pack unit tests: 21
  passed. Pack browser/installer groups: 10 passed, including desktop, 390x844
  and 360x640 standalone checks. Console build passed.
- Desk affected regression: 72 passed. Final notification verification/package:
  20 passed, zero skips, using H2 plus isolated PostgreSQL 16.15. Real local SMTP
  capture exercised a 451 failure followed by 250 acceptance with the same
  Message-ID; no transcript or intake data appeared in either message.
- Certificate-validated STARTTLS and SMTP authentication succeeded from both
  hosted servers. Existing SMTP credentials and approved sender were configured
  for Desk without publishing secrets. This alone is not inbox-arrival evidence.

AI runtime/connector image, framework 0.8.45, knowledge, models and prompts are
unchanged. No vector transfer, alternate provider or hidden recovery was added.

## Live Mobile Finding And Correction

The browser now genuinely reports a 390x844 CSS viewport. The old standalone
Support host followed the startup shell in normal document flow: the document
became 1688px tall and focus scrolled the body, leaving blank space and a clipped
composer. This was a real pack framing defect, not a failed AI request.

The generic standalone host now occupies the viewport and constrains its chat,
request panel and form scrolling. A regression inserts a full-height startup
shell before mounting, then checks the host and composer bounds on desktop and
both phone sizes. Form cancellation retains the unsent chat draft.

Staging's actual external Acme standalone app now reports host y=0, height=844,
body height=844 at width390; the composer is fully visible. The Console page also
shows no horizontal overflow. Form, request list/detail and return-to-chat
navigation were inspected. These are real hosted browser checks at phone-sized
viewports, not a claim of physical iPhone/Safari hardware qualification.

## Hosted Completion

- Desk CI `41753677220` succeeded. Staging job `ayw4kk6xb22jqbof79gruiwv`
  and production job `oppmhnj38ku1zudfvjy4zzd9` both finished; native source
  `781b23b40e243762f24ce98e26302ce0923a96c7` is healthy in both environments.
- External Acme signed-in UI opt-in, resolve and actual Gmail INBOX arrival
  passed for `SUP-794BDC0B-F8A`. Desk reports SENT, attempt1. Message-ID matches
  the durable outbox ID. Opt-out followed by reopening restored OPEN without
  a second notification row. No intake or AI turn was replayed.
- Production Console opt-in initially failed, correctly, because its private
  support space had an empty registered-origin list. Source `535bde111` now
  prepares the exact approved installation origins for safe return links.
  Private requester routes still independently reject every browser Origin;
  no private key is exposed and no authentication boundary was broadened.
  Final backend `535bde111da74786213873bf6524e2e7c7a97a2d` passed core CI
  `41755915805` and is native-source verified/healthy in both environments:
  staging job `q3oc0u3ojf0srycko9xu3576`, production `t79z2lypwd60mqjysxqjy790`.
  Normal `/api/platform/support/prepare` registered the actual installation
  origins in staging and production, without changing identity modes or keys.
- Final Console styling source `fb687c24f` aligns the new checkbox with its
  label. Core CI `41755225756` succeeded. Native-source/healthy readback passed
  after staging job `ivr82ir0w6fua46e26hhjcpy` and production
  `j10mbzqual3v7izff89845c9`.
- Production Console requester opted in through the actual UI. Resolve and
  reopen of the owned fictional ticket `SUP-4D634B38-A15` each generated one
  actual SENT event and Gmail INBOX receipt. No original intake was resent.
- From the phone-sized chat, the requester initiated handover
  `handover-e52e3c1638d904b381cba0362558f8f9`. The existing personal agent used
  the normal Desk SSO continuation, claimed and replied. The reply arrived
  asynchronously in the visitor UI without another AI query. Visitor reply
  was visible in Desk, followed by an explicitly confirmed return to AI.
- Human callback `callback-message-405cc2efbca027b16630a8c2d8a8e63a` is
  COMPLETED. Native readback proves its owner equals the ticket runtime
  principal and its binding equals the Support space. Its distinct notification
  is SENT, attempt1, with Gmail INBOX receipt. Message-ID matches the durable
  outbox ID. Mail contains only the safe reference and protected Console link,
  not the actual human message or ticket details.
- Final test state: ticket remains OPEN, conversation returned to AI; temporary
  test email subscriptions are restored to opt-out. Existing tickets, sources,
  agents and assignments remain. No production runtime replacement was needed.
- Live Console 390x844: no horizontal overflow, working request list/detail,
  form editing/cancel, canonical chat, email controls, human reply and explicit
  return to AI. Live external standalone 390x844: corrected framing, reachable
  composer, list/detail, status changes and opt-in/out. Local browser suites
  also exercise 360x640 and desktop, page/widget/standalone layouts.
- Screenshots: `live-ui-2026-11-04/console-mobile-email.jpg`,
  `console-mobile-human-reply.jpg`, `console-mobile-returned-to-ai.jpg` and
  `standalone-mobile-requests.jpg`. These are actual hosted pages, not mockups.

The existing approved Brevo sender is reused. Branded custom sender-domain
qualification and reply-by-email ingestion are not claimed. Delivery is bounded
SMTP at-least-once: a crash after remote acceptance can still duplicate a message;
a stable Message-ID is not a universal exactly-once guarantee. Specific external
helpdesk vendors and the broader historical outage/expiry matrix remain separate.

## Separate Next Requirement

Shopify Kit analysis is in 010.58. Optional hosted document storage and staging
uploads are specified in 010.59; this follow-up does not implement that separate
storage feature. BYO S3-compatible sources remain supported. The owner intends
one bucket shared across customer staging/production with purpose subpaths,
reusing the existing indexing and target isolation rather than copying vectors.
