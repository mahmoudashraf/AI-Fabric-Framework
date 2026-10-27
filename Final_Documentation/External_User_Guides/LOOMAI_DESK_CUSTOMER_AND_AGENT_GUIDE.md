# LoomAI Desk: Customer And Agent Guide

LoomAI Desk is the shared human-support workspace for LoomAI deployments. It is
separate from the customer Console and from the visitor's Engage app.

Release qualification is tracked in the [010.48 implementation plan](../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_48_LOOMAI_DESK_MANAGED_HUMAN_SUPPORT_AND_REVIEW_SERVICE_IMPLEMENTATION_PLAN.md).
This guide describes the implemented product flow; it is not evidence that every
hosting environment has completed its cutover.

Current verified deployment: the existing
[Northfield Engage app](https://northfield-console-live.apps.loomai.pro/) uses
shared Desk on verified production. The production portal is
[LoomAI Desk](https://desk.loomai.pro/), with verified HTTPS and HTTP-to-HTTPS
routing. Native signup confirmation and email-driven password recovery passed
on 2026-10-27, including sign-in with the recovered password. Gmail delivered
the signup message to Inbox but classified the recovery message as Spam.
Inbox placement therefore remains a launch-quality follow-up, not a delivery
or recovery pass inferred from an accepted send request.

The Console's **Open LoomAI Desk** link now targets the branded address.
The cancelled owner OAuth callback exposed during qualification was traced to
a Console router remount, corrected in `4eafcd08f`, and deployed to both
Consoles. Actual fresh, repeated-fresh and returning owner launches passed on
production; fresh launches each made exactly one native OAuth callback.
The existing customer identity and deployment ownership were preserved.
Conversation handover and real action-review decisions have both passed on the
dealership's staging and production targets. The old per-deployment Inbox
services and their dedicated databases have been retired.

The same customer enablement flow has also passed on a fresh, domain-neutral
conversational deployment using the generic workspace pack. Real API checks
denied access between that customer and Northfield in both directions. Adding
that customer reused shared Desk and its database; it did not create another
Inbox. The temporary qualification deployment and identity were removed after
testing. Native public signup email is now qualified separately; this generic
proof does not qualify every visitor sign-in integration.

The [recorded customer/agent journey](../Demo_Videos/LOOMAI_DESK_DEALERSHIP_CUSTOMER_AGENT_DEMO.mp4)
shows the current shared Desk flow side by side. It demonstrates handover and
replies, not every answer-quality or restart-recovery case. The known compound
delivery-policy retrieval limitation remains visible in the recording.

## Customer Setup

1. Sign in to the customer Console. Existing password-based customers connect a
   verified Supabase identity once, while signed in to their existing account.
   This preserves their customer account and deployments; it does not match or
   merge accounts by email.
2. Open **Human support**, and select the deployment and target release.
3. Select **Enable LoomAI Desk**. Optionally enable **Action reviews** for
   separately assigned reviewers. Platform publishes, applies and verifies a new
   configuration using the normal deployment lifecycle.
4. Wait for **Enabled**, then select **Open LoomAI Desk**. The normal first-party
   sign-in flow reuses the Console identity without another password prompt.
5. In **Agents & access**, assign registered agents to this deployment and
   environment. Assign yourself explicitly if you will also handle conversations
   or reviews.

The app and deployment addresses stay the same. A new configuration release does
not automatically change an Engage app's pinned assignment: use the Console's
existing verified-release assignment control when a switch is needed. Each app
uses the support team belonging to its assigned deployment target.

## Deployment Assignment To Desk

There are two separate assignments: **connect the deployment to Desk**, then
**give registered people access to that deployment**.

1. In the customer Console, open **Human support** for the owned deployment and
   select the staging or production target and its release.
2. Select **Enable LoomAI Desk**. Platform creates or reuses a binding for that
   exact deployment and target. The binding records the customer, tenant,
   deployment and target; it is not an assignment to a particular agent.
3. Platform registers the customer workspace and its linked owners in the shared
   Desk service for that environment. It generates integration credentials
   server-side, registers the connector callback and review bridge, and configures
   the Desk conversation-channel plugin. No customer has to copy operator keys.
4. The existing publish, apply and verify process installs the configuration.
   If there are unrelated draft edits, review them first. **Enabled** means the
   resulting target release has passed verification, not just that a checkbox
   was saved.
5. If the Engage app still references an older release, open **Versions &
   updates**, select the apply target and use **Use verified release**. The
   picker shows this deployment's existing apps, their current target and the
   selected verified target. A cross-environment switch is explicitly labelled;
   reading the page or applying a release does not switch the app. The same
   public app URL then uses the assigned target and its Desk binding. Use the
   go-live promotion flow when creating a production candidate from staging.
6. Select **Open LoomAI Desk**. Console sign-in is reused through the native
   Supabase authorization flow. In **Agents & access**, assign an already
   registered person as **Support**, **Reviewer**, or both for this target.

The owner can manage these assignments but must explicitly assign themselves
Support or Reviewer permission to do that work. A Desk link contains only
navigation context; Desk checks the signed-in person's permissions independently.

Staging and production have different bindings and agent assignments. Applying a
Desk-enabled version to production prepares that target's binding and callback
automatically; it does not copy staging's agent grants. Normal upgrades on the
same deployment and target reuse its binding and preserve its existing agent
assignments. Multiple Engage apps on that target share the same support team.

This creates metadata and credentials, **not another Inbox service or database**.
One shared Desk application/store serves the customers in each environment.
Ordinary AI chat still goes directly to the deployment. During handover, the
deployment connector sends the support conversation to Desk, and signed Desk
callbacks carry human replies back through that connector to the visitor.

Existing deployments get their target choices from their current authorized
deployment, not the original template's catalogue availability. Retiring an old
bootstrap template does not hide an existing production target. Go-live evidence
has a freshness window: an expired activation check is not a live health result.
Use **Verify production release** before a new activation when fresh evidence is
required; merely opening this screen does not deploy or switch the app.

## Authentication Between Desk And A Deployment

Human sign-in and service integration use different credentials:

- **Agent to Desk:** Supabase authenticates the person. Desk checks the active
  Support or Reviewer assignment for the selected deployment and target on each
  protected operation. Sign-in alone grants no deployment access.
- **Deployment connector to Desk:** HTTPS requests carry the binding-specific
  provider API key in `X-LoomAI-Inbox-Key` (the existing protocol header). Desk
  resolves that key to an enabled binding and validates the payload's binding,
  tenant and deployment. One binding's key cannot select another deployment.
- **Desk to deployment connector:** callbacks carry an HMAC-SHA256 signature
  over the timestamp, nonce and exact body. The connector verifies the signature,
  allowed timestamp, binding and nonce replay protection. It forwards accepted
  events to the runtime with the runtime API credential and a separate callback
  signature. These are not ordinary anonymous chat requests.
- **Action reviews:** Desk forwards the verified reviewer's Supabase access token
  and a separate binding-specific service credential to Platform's review bridge.
  The bridge validates both, rechecks the current Reviewer assignment with Desk,
  and resolves the current verified release. It then issues a two-minute runtime
  identity assertion limited to review viewing or review decision scopes, carrying
  the real reviewer's subject. Runtime review policy still governs the decision.

Platform generates and configures the integration secrets server-side during
activation/apply. Staging and production use separate binding credentials.
Customers do not manually distribute these keys, and they never appear in an
Engage visitor script, an Open Desk link or the agent's browser configuration.
This is HTTPS plus scoped service keys and signed messages; it is not mTLS or a
shared end-user password between services.

## Agent Registration

An agent opens Desk, creates a LoomAI account if needed, verifies their email,
signs in, and registers their agent profile. They share their verified email or
agent reference with the customer owner. Registration alone grants no customer
data access and no customer Console administration permissions.

### Email Verification And Password Recovery

Select **Create an account** on Desk, then confirm the email using the received
link. The link returns to Desk; complete normal sign-in before registering an
agent profile. Email confirmation alone creates no deployment assignment.

For password recovery, open [Console sign-in](https://console.loomai.pro/), enter
the account email and select **Forgot password?**. Open the recovery link in the
same browser that requested it, choose and confirm a new password, then sign in
to Desk with that password. The recovery flow uses the normal Supabase PKCE
session, not an administrator password reset or a bypass of identity checks.
Check Spam if a message is absent; LoomAI-domain sender/inbox-placement
qualification is still outstanding.

The customer owner chooses the deployment, staging or production target, and
one or both agent roles:

- **Support:** view assigned conversations, claim, reply, explicitly return to AI,
  or close a conversation.
- **Reviewer:** inspect assigned action-review tasks and record permitted
  approve/reject decisions.

**Owner** manages assignments. Owner access does not implicitly include Support
or Reviewer access. Staging and production assignments are separate. Application-
specific agent teams are not part of this release.

## Conversations

The visitor requests a human through the configured AI handover action or Engage
button. The existing deployment policy determines which contact fields are
requested and what transcript can be shared.

In Desk, select **Conversations**, choose the correct deployment/environment,
open the waiting conversation and select **Claim**. Read the approved transcript
and contact details, then reply. The visitor receives the message through the
deployment's existing event delivery, without having to submit another AI query.

Desk distinguishes a reply accepted for delivery from a delivered message. A
pending or failed callback is not shown as successful delivery. Review the
delivery state before retrying; retries retain their command identity.

Use **Return to AI** for an explicit transfer back, or **Close conversation** to
finish human support. Merely navigating away, signing out or losing connectivity
does not authorize a transfer back to AI.

## Action Reviews

Select **Action reviews** to inspect a real runtime review task. The proposal,
policy, expiry, allowed decisions and current version come from that deployment.
Reviews are not ordinary conversation messages.

Review the proposal, choose **Approve** or **Reject**, and confirm the decision.
Approval can authorize the existing runtime action gateway to continue the
business operation. Desk displays the actual returned execution outcome, which
may still be pending or unsuccessful. Rejection does not authorize execution.
Expired, changed or already-decided tasks may reject a stale decision; refresh
the task rather than submitting a new business request.

Enabling review support does not automatically route every business action to a
human. The originating application must already create a governed action receipt
and submit it through the runtime's review-creation API with the correct trusted
identity. Desk neither creates fictional tasks nor executes the business write
itself.

Ordinary dealership chat confirmation does not produce governed review receipts.
The separately authorized application integration below passes real staging and
production approval/rejection: approval creates exactly one external request,
rejection none, and decision replay creates no duplicate.
An empty review inbox alone is still not evidence of an approval lifecycle.

### Originating Application Integration (Opt-In)

The owner-authorized runtime adapter adds a separate, private application API;
ordinary chat confirmations remain unchanged. Its dealership staging and
production journeys are qualified. Enable it through versioned deployment
behavior configuration:

```json
{
  "executionExtensions": ["HUMAN_REVIEW"],
  "humanReview": {
    "actionProposals": {
      "enabled": true,
      "allowedActions": ["your_registered_confirmable_write"],
      "targetLookup": {
        "action": "your_registered_read",
        "parameter": "reference"
      }
    }
  }
}
```

Merge this fragment with the deployment's existing behavior, rather than
replacing its other extensions. Publish/apply a compatible runtime advertising
`application-action-review-proposals-v1`. The adapter is off by default.

Your trusted application calls the assigned deployment, not Desk:

```http
POST /api/reviews/v1/application-proposals
Idempotency-Key: opaque-application-request-id
Content-Type: application/json

{
  "request": "Prepare the requested registered action using these supplied facts ...",
  "title": "Application request",
  "summary": "A concise, accurate, application-owned summary without contact details or secrets.",
  "targetReference": "the-application-selected-resource-reference"
}
```

Use the deployment's verified private-runtime authentication contract. The
server-issued identity must contain `review:action-proposals:create`,
`specialist:application-action-review@1` and `action:<name>` for each configured
action. Browser input cannot select identity, tenant, deployment or scopes.
Do not put these credentials in a visitor script or a Desk launch link.

`targetLookup` is optional. Configure it when the write requires an existing
trusted resource: the application submits a candidate reference, and the
registered READ handler must authorize and return exactly one canonical target.
Only that handler's safe target projection becomes specialist context; supplied
IDs or text alone are never treated as trusted. Grant the caller the lookup's
`action:<name>` scope too. An optional `vectorSpace` checks the returned target's
projection label; omit it for non-indexed resources. It does not enable vector
retrieval or grant access to an index. The READ handler remains responsible for
resource authorization. No search fallback, first-result selection, business write or raw
handler-data attachment is performed by this preflight.

The registered specialist asks the model to propose a permitted action. Only a
genuine framework confirmation receipt becomes a review task, using the SAME
trusted source context. A successful submission returns HTTP 201 with `task`,
`dispatchAccepted` and `failure`; no proposal returns a visible HTTP 409 failure.
Missing facts or provider failures never create a fabricated task. Reuse the
same idempotency key and unchanged request on transport retry, and do not retry
a business action merely because a response is uncertain.

Desk's separately assigned reviewer decides that task through the existing
review API. Registered external handlers remain responsible for current
business authorization and execution. The default result projection exposes
only the action name, a completion message and `completed`; raw handler payloads
and PII are not returned. An application-specific registered safe projector can
provide richer results. None of this makes visitor consent equivalent to staff
approval, or routes every normal chat action to a reviewer.

The review summary includes the actual action name and the framework's rendered
confirmation message. Configure the registered action's existing
`confirmationMessage` template to include decision-critical non-sensitive
parameters, for example `Review request for {{vehicle}} on {{preferredDate}}?`.
This renders the actual candidate parameters, not a copy of the original user
request. Do not include contact details or credentials in that template.
The additional application summary is labelled **Requested facts**, not proposed
parameters. Overlong combined summaries fail visibly instead of silently
truncating decision-critical information. No private receipt-decryption contract
or duplicate proposal store is introduced.

## Operations And Ownership

Desk hosts the operator portal, assignments, queue, bounded transcript projection
and delivery records in a shared, customer-scoped store. The deployment continues
to own AI conversation state and review execution. Inventory, bookings, leads and
other business records remain in integrated customer systems.

A Desk interruption must not stop ordinary AI chat or silently return an active
human conversation to AI. Owners can remove an agent's assignment in Desk; that
agent loses the corresponding deployment permission. Signing out of one browser
does not promise instantaneous revocation of every token in every service.

Managed Desk configurations now use eight bounded dispatch attempts; other
channel providers keep the default of five. The setting is server-owned and
accepts only one through eight. Real staging and production interruption tests
on 26 October 2026 proved ordinary AI remained available, human control was
preserved, and a queued visitor message reached Desk exactly once after the
same service restarted. The human reply and explicit return to AI also passed.

This is bounded retry, not indefinite delivery or failover. A longer outage can
still exhaust the attempts and leave delivery failed. Permanent authorization
failures stop immediately; a Desk outage never authorizes an automatic AI return.
The earlier failed tests remain recorded in the implementation plan.

Customer-specific integration credentials stay server-side. No operator API key,
runtime signing key or access token is embedded in an Open Desk link.
