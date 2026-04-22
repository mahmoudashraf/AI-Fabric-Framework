# Runbook Template

Status: canonical runbook format for every LoomAI service that can page a human (2026-04-21)

Purpose:

- enforce a consistent shape across runbooks so on-call muscle memory works on any product
- ensure every runbook answers the questions a new on-call engineer will actually ask at 2am
- keep runbooks short enough to stay correct and long enough to be useful

Usage:

- copy this file to `doc/Operations/runbooks/{service-name}.md`
- fill every section — if a section does not apply, explain why rather than deleting it
- link the runbook file from every alert that can fire for the service
- review quarterly; any section not touched in 180 days must be revalidated

Related:

- [OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md](../observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md)
- [CARDINALITY_GOVERNANCE_POLICY.md](../observability/CARDINALITY_GOVERNANCE_POLICY.md)

---

# Runbook: {SERVICE_NAME}

Last reviewed: `YYYY-MM-DD` by `{owner}`
Primary owner: `{team or person}`
Escalation owner: `{secondary}`
Dashboards: [`{product} health`]({dashboard-url}), [`tenant debugger`]({templated-dashboard-url})
Source code: [`{repo-path}`]({repo-link})
Deployment tool: `{ArgoCD / GitHub Actions / manual}`

## 1) What is this service?

One paragraph, written for an engineer who has never touched this code. Explain:

- what the service does
- who talks to it (upstream)
- what it talks to (downstream)
- why its failure matters to merchants or end users

Avoid internal jargon. If a term is unavoidable, link to its definition.

## 2) What "healthy" looks like

The **three metrics** to check first, in order. Each with its green / yellow / red ranges.

| Metric | Green | Yellow | Red | Where to see it |
|---|---|---|---|---|
| `{metric_1}` | e.g. <1% error rate | 1–5% | >5% | `{dashboard link}` |
| `{metric_2}` | e.g. P95 <500ms | 500ms–2s | >2s | `{dashboard link}` |
| `{metric_3}` | e.g. queue <100 | 100–1000 | >1000 | `{dashboard link}` |

If all three are green and a page fired, suspect the alert rule before the service.

## 3) Common failure modes

At minimum five, ranked by frequency. Each follows the **Signal → Diagnosis → Remediation** shape.

### 3.1 {Failure name}

- **Signal**: what the on-call sees first — alert title, dashboard change, user report
- **Diagnosis**: commands / queries that confirm or rule out this failure
  ```
  # concrete commands or LogQL / PromQL queries
  ```
- **Remediation**: exact steps to recover, in order. If it requires a deploy, say so. If it requires data fixup, link the migration or script.
- **Blast radius**: who is affected while this is broken (one merchant, all merchants, specific region)
- **Time to resolve (typical)**: from page to green

### 3.2 {Failure name}

...repeat...

### 3.3 {Failure name}

...repeat...

### 3.4 {Failure name}

...repeat...

### 3.5 {Failure name}

...repeat...

## 4) Escalation path

| Elapsed time | Action |
|---|---|
| 0–15 min | Primary on-call investigates. Acknowledge page. Post in `#incidents-{product}` with status and dashboard link. |
| 15 min | If root cause unclear, page secondary on-call. Update status-page if merchant-facing. |
| 60 min | Escalate to product owner. Start an incident doc from [template](INCIDENT_DOC_TEMPLATE.md) if not already. Assign an incident commander if more than one responder. |
| 240 min | Executive notification. External comms review. |

On-call contacts live in PagerDuty — do not duplicate here.

## 5) Communication templates

### Merchant-facing status-page update

```
We are investigating reports of {symptom} affecting {scope} since {time UTC}.
Merchants may experience {impact}. We will update within 30 minutes.
```

### Support reply skeleton

```
Hi {name},

Thanks for reaching out. We are aware of {symptom} and actively working on it.
Your store: {store-context-if-available}.
Expected resolution: {best estimate or "will update within N minutes"}.

We'll reach back out the moment it's resolved.
```

### Resolution announcement

```
Resolved at {time UTC}. Root cause: {one sentence}. Full post-mortem within 5 business days.
```

## 6) Recovery and verification

After remediation:

1. Confirm the three healthy metrics in Section 2 are green for 15 minutes continuous
2. Run smoke test: `{command or script path}`
3. Check Sentry for new issue groups that started during the incident
4. Confirm no residual merchant impact by sampling `{N}` affected tenants
5. Close the page in PagerDuty with a one-line summary
6. Remove status-page banner if one was posted

## 7) Post-incident checklist

Before considering the incident closed:

- [ ] Timestamps captured (first signal, first human action, resolution)
- [ ] Affected tenants counted and listed
- [ ] Revenue impact estimated if known
- [ ] Incident doc exists and is linked from `#incidents-{product}`
- [ ] Retrospective scheduled within 5 business days
- [ ] At least one action item filed with an owner and due date
- [ ] Runbook updated if this failure mode was missing or the steps were wrong

## 8) Known-not-broken oddities

Things that look alarming but are expected. Documented so on-call doesn't chase them:

- `{log line or metric pattern}` — expected during `{condition}`, not a failure
- `{another one}` — ...

## 9) Change log

| Date | Author | Change |
|---|---|---|
| YYYY-MM-DD | {name} | Initial version |
| YYYY-MM-DD | {name} | Added failure mode 3.6 |
