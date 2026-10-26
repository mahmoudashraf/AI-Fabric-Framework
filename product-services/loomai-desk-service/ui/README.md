# LoomAI Desk Portal

The React portal for the shared, multi-customer LoomAI Desk service. It is an
operator workspace, not a customer Console or a chat runtime. All production
data comes from same-origin Desk APIs. Fictional content lives only in `tests/`.

## Build And Run

```sh
npm ci
npm run build
npm run dev
```

The development server uses `http://127.0.0.1:5188`. `/api`, `/oauth2`, `/auth`,
and `/logout` requests proxy to the backend at `http://127.0.0.1:8085`.
Set `DESK_API_PROXY` to use another development backend. Vite never supplies
fixture responses or authentication bypasses.

`npm run build` type-checks the app and produces `dist/`. The backend/image
integration should package the contents of that directory as its static UI,
with `index.html` at `/` and hashed assets under `/assets/`. This UI task does
not copy files into the backend static directory or change container builds.
No runtime, framework, or Platform source is modified here.

## API Integration

This portal follows the current service's `../API_CONTRACT.md`:

- Public config: `supabaseUrl`, `supabasePublishableKey`, `loginUrl`,
  `oidcEnabled`, `registrationEnabled`. There are no embedded private keys.
- Session: `authenticated`, nullable `operator`, `bindings`, `csrfToken`,
  `csrfHeaderName`. Mutations carry the latest session CSRF header and the
  host-only server session cookie; no browser-stored operator/access token.
- An unauthenticated Console deep link `/?bindingId=...` automatically starts
  `/oauth2/authorization/supabase?bindingId=...`. The backend owns state, PKCE,
  code exchange, session creation, and return to the selected binding. There
  is no custom client-side SSO protocol or local-storage identity sharing.
- Direct Desk visits show the sign-in/sign-up UI. `?login=cancelled` shows a
  recoverable login state instead of automatically restarting authorization.
  If OAuth is not configured, the UI says so rather than offering a broken
  login action. A user with an established session never reauthorizes just
  to switch between their permitted workspaces.
- Agent sign-up uses only Supabase's public client and disables persisted
  client sessions. It explicitly sets `emailRedirectTo` to
  `${window.location.origin}/`, never the shared Supabase Site URL. The exact
  hosting allowlist entries are `https://desk.loomai.pro/` and
  `https://loomai-desk.46.224.145.148.sslip.io/`; local verification uses the
  local UI origin. The verification email precedes native server sign-in.
  If an email return contains access/refresh tokens in its URL fragment, the
  bootstrap discards that fragment before starting API reads and shows only a
  continue-sign-in hint. It never decodes/trusts/persists those credentials or
  grants membership. The server's standard native OAuth flow still establishes
  and authorizes the Desk session. The email landing root is distinct from the
  OIDC code callback `/login/oauth2/code/supabase`.
  Signed-in, unregistered agents use `POST /api/desk/v1/agents/register`.
  A profile alone grants no workspace membership. The server remains the
  authority for email verification and registration eligibility.
- Agent records use `agentId` and `agentReference`. Assign and edit use the
  existing exact registered-agent lookup `POST /bindings/{id}/agents`;
  remove uses `DELETE /bindings/{id}/agents/{agentId}`. There is no public
  directory, account creation by the owner, or app-specific permission model.
- Conversation summaries/details/commands use the existing Basic Inbox
  contracts. Callbacks use `/bindings/{id}/operations/callbacks`, including
  explicitly authorized, idempotent retry of eligible delivery failures.
- Review lists/details/decisions consume the actual review bridge responses.
  `requestedInformation`, `suppliedInformation`, failures, successor task
  references and returned execution outcomes are rendered as safe text.
  Decisions submit `decisionId`, `decision`, and the inspected
  `expectedVersion`; an approval is never invented as execution success.
  The contract is the published `ai-fabric-execution:0.8.41` JAR, checked with
  `javap`: review task status is `WAITING_FOR_REVIEW`, not `PENDING`. Only that
  status enables approve/reject, still subject to `allowedDecisions` and expiry.
  Decision responses are `{task,outcome,successorTaskId,failure}`; failure is
  `{reason,publicMessage,retryable}` and is an error even on HTTP 200. Typed
  HTTP 409 bodies remain accessible as `ApiError.payload`; their public message
  and reason are preserved rather than replaced by a generic bridge error.
  No write is automatically retried. Detail still uses `message` and
  `failureReason`. There is no `proposal` projection. `ActionOutcomeView` has
  only `actionName`, `message`, and `data`, with no invented top-level status or
  success flag. Task types, status enums, fixtures, and tests are type-checked
  together; obsolete review contract aliases are not accepted.
- `POST /logout` includes session CSRF, accepts the backend's native same-origin
  logout redirect, then clears cached customer state and returns to `/`.

`OWNER` manages assignments but does not imply `SUPPORT` or `REVIEWER`.
The UI checks those capabilities, and the server must continue authorizing
every read and mutation. An unassigned `bindingId` never falls back to another
customer's workspace. No template/domain-specific logic is present.

## Behavior And Scope

- Queues, selected transcript, callbacks, agents and review detail poll every
  five seconds while the tab is active. Session/assignments revalidate every
  fifteen seconds; any authorization failure also requests a fresh session.
- Switching binding aborts/removes the previous binding's cached queries,
  unmounts its transcript and composer, and clears unsent drafts and pending
  decisions. Request keys include the binding and selected resource.
- Permission loss or session expiry removes the protected workspace. Queue,
  transcript, identity and review failures are visible, not empty success.
- Failed replies retain the draft. Retrying an uncertain identical command
  reuses its idempotency identity. Switching resource or editing the message
  creates a new command; the UI does not automatically replay writes.
- Claim and delivery states remain server-returned. Only the owning agent in
  `ACTIVE` can reply or transition; return-to-AI and close are explicit.
- Light/dark preference is the only persistent local UI setting. Desktop is
  the primary layout; mobile has a navigation drawer and queue/back pattern.
  Modals use native dialogs, icon buttons have labels/tooltips, and reduced
  motion is respected. No third-party fonts or remote visual assets load.
- Queue and callback reads are bounded to the latest 100 records, with that
  limit visible. Full history pagination is not claimed by this UI.

## Verification

```sh
npx playwright install chromium
npm test
npm run build
DESK_TEST_PREVIEW=1 npm test
npm audit --omit=dev --audit-level=high
```

Playwright starts its own isolated Vite server on `127.0.0.1:5189` and mocks
the actual HTTP contract at the browser boundary. `DESK_TEST_PREVIEW=1` runs the
same tests against the built production bundle instead of Vite's source server.
Fixtures are never imported
by application code. Tests cover conversation support, delivery failure/retry,
review decisions/conflicts, agent assignments/revocation, role separation,
workspace switching, identity expiry, native OAuth launch, verification/signup,
CSRF logout, safe text rendering, light/dark and mobile. Screenshots are written
inside the ignored `test-results/` directory, with HTML report and failure traces.

Browser fixtures prove frontend contract behavior, not hosted Supabase setup,
cross-origin first-party SSO, real callback delivery, runtime review execution,
or the dealership cutover. Those require the main implementation's hosted
verification against the integrated service.

To recheck the current framework DTO signatures without a framework build:

```sh
javap -classpath "$HOME/.m2/repository/io/github/loom-ai-labs/ai-fabric-execution/0.8.41/ai-fabric-execution-0.8.41.jar" \
  ai.fabric.execution.review.ReviewTaskStatus \
  ai.fabric.execution.review.ReviewTaskView \
  ai.fabric.execution.review.ReviewTaskDetailView \
  ai.fabric.execution.review.decision.ReviewDecisionResult \
  ai.fabric.execution.review.decision.ReviewDecisionFailure \
  ai.fabric.execution.action.ActionOutcomeView
```

Supabase JS is pinned to `2.50.0`, the smallest following minor checked here
with the patched Auth JS dependency, rather than carrying the older `2.49.1`
Auth JS advisory into this new UI. React 18, Vite 6, TypeScript 5, TanStack
Query 5, and Lucide follow the existing workspace toolchain patterns.
