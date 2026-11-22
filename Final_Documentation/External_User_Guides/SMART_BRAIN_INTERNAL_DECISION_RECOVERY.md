# Smart Brain Internal Decision Recovery

Internal AI planning fields are not customer questions. When ordinary chat cannot
complete intent or knowledge-source selection, the Brain can ask the model to
correct that decision, then make one final answer from context if repair stops.

## Configure

Open your Smart Brain -> Advanced -> Recovery policy.

- **Maximum repair rounds:** default 3, allowed 0-10. Shared across the request,
  including its compound parts. Zero skips extra repairs, not the final answer.
- **Repair admission window (ms):** default 30000, allowed 1000-300000. After
  this window, no new correction starts. An in-flight call retains its provider
  timeout; this is not a hard total request deadline.

Save the shared draft, then use Updates to review, publish and apply a compatible
runtime. Saving does not change an already applied release. The runtime must
advertise `internal-decision-recovery-v1`; older images cannot apply these settings.
Changing these repair limits does not require reindexing knowledge. Actual
embedding or indexed-data changes still use the normal reindexing checks.

The saved provider configuration is:

```json
{
  "decisionRecovery": {
    "maxRepairRounds": 3,
    "repairAdmissionTimeoutMs": 30000
  }
}
```

More rounds can increase latency and inference cost. Existing extraction-call,
token, provider and funding limits still apply. One repair strategy can itself
require more than one model call; rounds are not a promise of unlimited calls.

## What The Customer Sees

The assistant corrects internal decisions without asking the person to name a
knowledge domain or database namespace. If it cannot resolve them within the
allowance, it answers using permitted history, explicit attachments and already
prepared evidence. It can acknowledge missing current facts or ask a genuine
business question. It does not pretend a new search succeeded.

The final answer follows the Brain's managed answer, evidence and presentation
guidance. Its constraints remain: no invented business facts, no new tool call,
no search, no background retry and no replay of an already completed write.

## Boundaries And Limits

Ownership, authentication, source/document filters, action schemas, confirmation
and spending rules remain unchanged. Automatic/Selected action evidence still
applies. Explicitly attached item content remains user-provided context, not
proof of ownership, freshness or permission to execute a business operation.

A real access denial or unavailable provider is not a model-planning problem.
Those failures remain honest service/access outcomes. Existing successful-action
receipts and pending confirmations are retained even if answer generation fails.
Strict specialist and Managed Task output contracts are not replaced by prose.

Query diagnostics identify repair rounds and final context-answer use. A
successful context answer is not proof that fresh knowledge retrieval succeeded.
The final provider call can still fail during outage or exhausted credits.

See 010.71 and its dated verification record for actual publication and adoption.
