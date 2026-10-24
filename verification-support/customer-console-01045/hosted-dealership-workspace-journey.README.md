# Hosted Dealership Workspace Journey

An actual Playwright browser journey for a newly created, active Engage app. It does not provision, deploy, configure, seed or clean up anything. Main owns hosted execution and answer review. No hosted execution was performed when adding this runner.

24 October execution update: real staging and production runs have now completed.
The single staging business confirmation and the no-write handover journeys are
recorded separately in the canonical 010.45 execution record. Original stopped
runs remain retained; no business confirmation was replayed to obtain a green test.

The browser obtains the runtime only from its normal public app bootstrap and installation manifest. There is no runtime URL argument, guessed deployment address, temporary helper import, API query shortcut or injected authentication state. Playwright is loaded from the existing `max-mode-widget` package.

## Inputs

Use absolute private paths. Create a scope file **after** actual app activation, assignment and document registration. Do not invent the installation ID, revision, release or document source IDs.

```json
{
  "customerId": "cus-9fd67f55",
  "tenantId": "<actual tenant ID>",
  "deploymentId": "<actual deployment ID>",
  "releaseId": "<actual release ID>",
  "installationId": "<actual awi_pub_ plus 32 hexadecimal characters>",
  "assignmentRevision": "<actual assignment revision>",
  "appUrl": "https://<actual app slug>.apps.loomai.pro",
  "sourceIds": ["<actual registered policy source ID>"],
  "vehicleLabel": "2025 Aster E1",
  "inboxUrl": "https://<actual Basic Inbox host>",
  "receiptStore": {
    "environment": "staging",
    "container": "s10ag41pp0ti4l93e5o0xq0f",
    "user": "loomdealership",
    "database": "loomdealership"
  },
  "testDrive": {
    "name": "Synthetic Console Journey Buyer",
    "phone": "07700900900",
    "date": "<future YYYY-MM-DD>"
  }
}
```

- All scope values are caller-supplied; none are hardcoded in the runner. Capture the actual app assignment after activation, not a previous fixture's IDs.
- `appUrl` and `inboxUrl` must be HTTPS origins. The app assignment is pinned throughout the run. Promotion needs a new scope file, new assignment/release/document source pins and a new output directory.
- `sourceIds` must identify registered document sources in this exact release. Alternatively, `--document-evidence` accepts a JSON file containing `customerId`, `tenantId`, `deploymentId`, `releaseId` and `sourceIds`. Both lists must agree if both are supplied. Lightweight source references are joined to returned documents by exact document ID, knowledge source ID and vector space. Their metadata must carry an exact registered `_aiDocumentSourceId`; arbitrary source text does not pass.
- `vehicleLabel` must exactly match the rendered inventory card heading for a current electric automatic SUV below GBP 40,000 and 20,000 miles. Full mode selects it using **Keep in context**, not a hidden resource ID.
- `receiptStore` points to the existing external demo vendor database, not a new deployment-owned business database. The operations module must export `ssh(environment, command, input, timeout)` and `quote(value)`. The runner executes only a bounded `BEGIN READ ONLY` query on `dealership_lead_request`, scoped by customer, tenant and deployment. It never imports the module unless receipt proof is configured.
- Full mode requires `inboxUrl`, `vehicleLabel` and `receiptStore`. Read-only mode can omit them. `testDrive` is required only when the explicit receipt flag is present; its date must not be in the past.
- The customer credentials file is the **Supabase shopper** fixture, not the Platform customer administrator: `{ "email": "...", "password": "...", "userId": "..." }`. Optional `projectRef`, `projectUrl` and `canaryCustomerId` are checked when supplied. The existing private fixture may be used at `/private/tmp/01044-p4-supabase-auth-fixture-20261020/actual-r2/credentials.private.json`.
- The caller-provided one-time operator key file accepts `{ "apiKey": "...", "deploymentId": "..." }` or an existing delivery envelope `{ "value": { "apiKey": "..." } }`. The runner neither creates nor revokes an operator. The Basic Inbox URL and credential must belong to the selected deployment.

Keep scope, credentials, keys, evidence and output outside the repository, with restricted permissions. No passwords, operator keys, runtime bearer tokens, auth response bodies, browser storage, traces or HAR files are written. Screenshots mask credential inputs. Sanitized response/dispatch evidence and synthetic contact details remain private evidence, not publishing assets.

## Run

Safe local checks, with no browser or service access:

```sh
node --check verification-support/customer-console-01045/hosted-dealership-workspace-journey.mjs
node verification-support/customer-console-01045/hosted-dealership-workspace-journey.mjs --self-test
```

Read-only post-promotion check:

```sh
node verification-support/customer-console-01045/hosted-dealership-workspace-journey.mjs \
  --mode read-only \
  --scope /private/path/production-workspace-scope.json \
  --root /private/path/NEW-production-workspace-run
```

"Read-only" still creates an anonymous session and sends a real, billable AI query through the composer. It does not perform sign-in, handover, a signed-in action proposal or business confirmation. It verifies a compound stock/policy answer with an inventory presentation and a registered policy citation, exact visible conversation restoration without replay, and a basic mobile composer. Supplying `receiptStore` and `--operations` adds proof of zero external receipt changes.

Full journey without a business write:

```sh
node verification-support/customer-console-01045/hosted-dealership-workspace-journey.mjs \
  --mode full \
  --scope /private/path/staging-workspace-scope.json \
  --root /private/path/NEW-staging-workspace-run \
  --credentials /private/path/supabase-shopper.json \
  --operator-key /private/path/one-time-operator.json \
  --operations /private/path/operations.mjs
```

Add `--allow-one-synthetic-receipt` only when explicitly authorizing **one** synthetic test-drive confirmation in that exact scope. Add `--headed` to show the browser. There is no resume, retry, alternate-runtime or success-override flag.

## Full Journey And Evidence

1. Observe normal bootstrap, manifest and anonymous session; verify app assignment and customer/tenant/deployment scope.
2. Ask a compound inventory plus delivery/warranty policy question. Require an actual inventory card and registered document citation. One explicit user clarification is permitted across the entire run, only for a typed `CLARIFICATION_REQUIRED` response, and is recorded.
3. Reload and compare exact visible message IDs/text. An unsolicited/replayed query is blocked, not accepted as a second chance.
4. Request a protected test drive anonymously. Require the structured sign-in challenge with `EXPLICIT_NEW_REQUEST`, cancel the actual dialog and verify no identity change or receipt.
5. Perform real Supabase password sign-in and observe the credential broker's actual subject. Login must not replay the action. Select a vehicle through the authenticated inventory UI; the authenticated conversation must differ from the anonymous one.
6. With the receipt flag only: send one new explicit request, inspect the confirmation UI, click **Confirm** once, require either the custom receipt or the supported generic structured result and exactly one new external record with its matching reference. The successful structured action receipt must match that record too. Existing receipt records must remain byte-equivalent after JSON readback. Without the flag this step is visibly marked **not run**, never passed.
7. Use the widget's **Talk to a person** / **Continue anonymously** flow. Send a human-bound message; require `202 HUMAN_MESSAGE_ACCEPTED` rather than an AI answer.
8. Sign in to the actual Basic Inbox with the supplied operator key. Wait at most 45 seconds for its normal queue delivery, select the exact handover ID, require a consistent provider-side reference in queue/detail, claim it, reply through its form and observe the reply arriving asynchronously in the workspace. The provider reference is not the runtime chat ID. Use the Inbox's **Return to AI** command, then require an AI answer in the same conversation with another registered policy citation.
9. Verify an editable, in-viewport composer at 390 x 844 and final external receipt invariants.

The browser guard permits only expected composer sends, bounded session/broker/suggestion traffic, the one real password login, the armed handover, and the three exact Inbox commands. Canonical message dispatches require their real idempotency keys. User sends, including clarification, confirmation and human-bound text, are capped at **10**. Operator replies are separate and limited by their once-only command intents.

Each run exclusively creates `--root`; an existing directory is rejected. Every request intent and every failure is preserved with timestamps. A timeout after dispatch does **not** mean zero effect: inspect the report and exact external records before authorizing any new run. No automatic confirmation, callback retry, action replay, hidden fallback, model-output repair or result fabrication occurs.

`report.private.json`, sanitized response JSON and screenshots are the evidence. Exit 0 means `PASS_TECHNICAL_PENDING_ANSWER_REVIEW`, not semantic approval or a framework-wide release gate. Read the captured answers for relevance/completeness before acceptance. Blank optional metadata never converts a runtime error, access denial or budget rejection into success. Failed or indeterminate runs exit 1 as `NOT_ACCEPTED`; a created handover may require an explicit operator follow-up. No automatic cleanup obscures those outcomes.
