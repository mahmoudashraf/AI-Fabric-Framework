# LoomAI Desk: Customer And Agent Guide

LoomAI Desk is the shared human-support workspace for LoomAI deployments. It is
separate from the customer Console and from the visitor's Engage app.

Release qualification is tracked in the [010.48 implementation plan](../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_48_LOOMAI_DESK_MANAGED_HUMAN_SUPPORT_AND_REVIEW_SERVICE_IMPLEMENTATION_PLAN.md).
This guide describes the implemented product flow; it is not evidence that every
hosting environment has completed its cutover.

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

## Agent Registration

An agent opens Desk, creates a LoomAI account if needed, verifies their email,
signs in, and registers their agent profile. They share their verified email or
agent reference with the customer owner. Registration alone grants no customer
data access and no customer Console administration permissions.

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

Current qualification limitation: the retained dealership runtime's ordinary
chat confirmation does not produce those governed receipts. The review UI and
authorized bridge are implemented, but real dealership approval/rejection is
not qualified until the separate originating application integration is supplied.
An empty live review inbox is not evidence of an approval lifecycle.

## Operations And Ownership

Desk hosts the operator portal, assignments, queue, bounded transcript projection
and delivery records in a shared, customer-scoped store. The deployment continues
to own AI conversation state and review execution. Inventory, bookings, leads and
other business records remain in integrated customer systems.

A Desk interruption must not stop ordinary AI chat or silently return an active
human conversation to AI. Owners can remove an agent's assignment in Desk; that
agent loses the corresponding deployment permission. Signing out of one browser
does not promise instantaneous revocation of every token in every service.

Customer-specific integration credentials stay server-side. No operator API key,
runtime signing key or access token is embedded in an Open Desk link.
