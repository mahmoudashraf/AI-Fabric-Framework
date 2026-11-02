# Deferred Review Queue

Date: 2 November 2026. Owner instruction: propose before any framework change.
No new framework source/test/release change was made in this completion cycle.

## 1. Incident Room Example Authorization

Verified example integration defect, unchanged from before 0.8.45; broad CI
remains failed. The [bounded proposal](2026-11-02-incident-room-authorization-proposal.md)
maps a verified interactive caller through its active application session to
the incident, without impersonation or relaxed authorization. It is example-only;
the current evidence does not require another library/BOM release. Await owner
review before implementation and CI rerun.

## 2. Cancellation After Restored Final Confirmation

Actual staging UI on v11 / 0.8.45, current widget assets:

1. Sign in with the existing synthetic visitor, fetch/pin the current Aster result.
2. Prepare a fictional callback with contact fields and consent; stop at final
   confirmation. No write is authorized.
3. Reload. The widget correctly renders the restored confirmation read-only.
4. Ask to cancel the pending callback: the response instead requests name,
   phone and consent through a callback draft form.
5. Send the widget's ordinary `No, cancel` contract: an acknowledgement follows.

Receiver readback stayed at 83 records with every record unchanged. This proves
no business submission, **not** that all pending state was discarded. The first
untrusted-vehicle attempt was separately cancelled before the pinned positive
case. Preserve that sequence when reproducing; do not describe this as a clean
isolated core regression or assume the browser reload caused it.

Before proposing a fix, inspect the exact conversation's pending confirmation
versus incomplete-draft state and structured planner output, then reproduce in
an isolated conversation. It may be a configuration/model/state interaction;
the current observation alone does not establish the owning layer. Keep final
confirmation, owner/vehicle authority and the serial widget request contract.
No lexical cancellation, silent fallback, automatic resubmission, new lifecycle
architecture or framework release is approved by this note.

## 3. Retrieval And Continuity Rechecks

- Production's warranty/battery question chose the right document source but
  retrieved zero documents. The staging match scored 0.408 against threshold
  0.4, and production retrieved that warranty on a different turn. Retrieval
  sensitivity is a hypothesis, not a proven below-threshold score. Upload and
  index the approved richer pack, then repeat the question before more tuning.
- Active-session navigation retained two attached pages and the conversation.
  A longer-idle trial reset. Fresh evidence does not establish why; do not label
  it normal expiry or claim full idle continuity. Capture identity/conversation
  transitions in a bounded follow-up if it remains relevant to the agreed pilot.

These are retained quality/qualification limitations, not hidden PASS results.
Framework code changes, if justified after diagnosis, return to the owner first.
