import { test, expect } from "@playwright/test";
import { fixtureState, mockDesk } from "./fixture";

test("claim, reply, return to AI with honest delivery outcome; screenshots in both themes", async ({
  page,
}, testInfo) => {
  const state = await mockDesk(page);
  await page.goto("/?bindingId=studio-prod");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await expect(page.getByLabel("Reply to Eve Lin")).toBeDisabled();
  await page.getByRole("button", { name: "Claim", exact: true }).click();
  await page
    .getByLabel("Reply to Eve Lin")
    .fill(
      "Hi Eve, I'm Morgan. Happy to help arrange your visit. What would you like us to prepare?",
    );
  await page.getByRole("button", { name: "Send reply", exact: true }).click();
  await expect(
    page
      .getByRole("status")
      .filter({ hasText: "Reply accepted delivery pending" }),
  ).toBeVisible();
  await expect(page.getByLabel("Reply to Eve Lin")).toHaveValue("");
  await page.getByRole("button", { name: "Show conversation context" }).click();
  await expect(page.getByText("eve@example.test")).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("desk-light.png"),
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Use dark theme" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(page.locator(".workspace-main")).toHaveCSS(
    "background-color",
    "rgb(32, 34, 37)",
  );
  await expect(page.locator(".workspace-rail")).toHaveCSS(
    "background-color",
    "rgb(25, 27, 30)",
  );
  await page.screenshot({
    path: testInfo.outputPath("desk-dark.png"),
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Return to AI", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Return to AI", exact: true })
    .click();
  await expect(page.getByText("Back with AI")).toBeVisible();
  const commands = state.calls.filter((call) => call.method === "POST");
  expect(commands.map((call) => call.path.split("/").at(-1))).toEqual([
    "claim",
    "messages",
    "return-to-ai",
  ]);
  expect(
    commands.every(
      (call) => call.body?.idempotencyKey && call.csrf === "fixture-csrf",
    ),
  ).toBe(true);
  await expect(
    page.getByRole("button", { name: "Close conversation", exact: true }),
  ).toBeDisabled();
});

test("close needs explicit confirmation and produces read-only transcript", async ({
  page,
}) => {
  const state = fixtureState();
  state.conversations[0]!.status = "ACTIVE";
  state.conversations[0]!.claimedBy = "agent-1";
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await page
    .getByRole("button", { name: "Close conversation", exact: true })
    .click();
  await page.getByRole("button", { name: "Keep conversation" }).click();
  expect(state.calls.some((call) => call.path.endsWith("/close"))).toBe(false);
  await page
    .getByRole("button", { name: "Close conversation", exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Close conversation", exact: true })
    .click();
  await expect(
    page.getByText("Conversation closed", { exact: true }),
  ).toBeVisible();
});

test("review decisions use version and distinct IDs, with real returned outcome", async ({
  page,
}, testInfo) => {
  const state = await mockDesk(page);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Action reviews", exact: true })
    .click();
  await page
    .getByRole("button", { name: /Confirm a visitor appointment/ })
    .click();
  await expect(
    page.getByText("Accessibility arrangements requested"),
  ).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("reviews.png") });
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await page
    .getByRole("button", { name: "Approve action", exact: true })
    .click();
  await expect(
    page.getByText("The application returned appointment reference REQ-1042."),
  ).toBeVisible();
  await expect(page.getByText("REQ-1042", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: /Update a service request/ }).click();
  await page.getByRole("button", { name: "Reject", exact: true }).click();
  await page
    .getByRole("button", { name: "Reject action", exact: true })
    .click();
  await expect(
    page.getByRole("status").filter({ hasText: "Task rejected" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Returned execution outcome" }),
  ).toHaveCount(0);
  const decisions = state.calls.filter((call) =>
    call.path.endsWith("/decisions"),
  );
  expect(decisions.map((call) => call.body?.expectedVersion)).toEqual([3, 1]);
  expect(decisions.map((call) => call.body?.decision)).toEqual([
    "APPROVE",
    "REJECT",
  ]);
  expect(decisions[0]!.body!.decisionId).not.toBe(
    decisions[1]!.body!.decisionId,
  );
});

test("assign registered agent with explicit roles, then revoke scoped assignment", async ({
  page,
}, testInfo) => {
  const state = await mockDesk(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Agents & access" }).click();
  await page.screenshot({ path: testInfo.outputPath("agents.png") });
  await page.getByRole("button", { name: "Assign agent", exact: true }).click();
  await page
    .getByLabel("Verified email", { exact: true })
    .fill("new@example.test");
  await page.getByLabel("Action reviewer").check();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Assign agent", exact: true })
    .click();
  await expect(page.getByText("new@example.test")).toBeVisible();
  const assignment = state.calls.find(
    (call) => call.method === "POST" && call.path.endsWith("/agents"),
  );
  expect(assignment?.body).toEqual({
    email: "new@example.test",
    roles: ["SUPPORT", "REVIEWER"],
  });
  await page
    .getByRole("button", { name: "Remove access for New agent" })
    .click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Remove access", exact: true })
    .click();
  await expect(page.getByText("new@example.test")).toHaveCount(0);
  expect(
    state.calls.some(
      (call) =>
        call.method === "DELETE" &&
        call.path === "/api/desk/v1/bindings/studio-prod/agents/assignment-new",
    ),
  ).toBe(true);
});

test("support-only does not load reviews or management and owner alone cannot read conversations", async ({
  page,
}) => {
  const state = fixtureState();
  state.session.bindings[0]!.roles = ["SUPPORT"];
  await mockDesk(page, state);
  await page.goto("/");
  await expect(
    page.getByRole("button", { name: "Agents & access" }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Action reviews", exact: true })
    .click();
  await expect(page.getByText("Reviewer access required")).toBeVisible();
  expect(state.calls.some((call) => call.path.endsWith("/reviews"))).toBe(
    false,
  );
  state.session.bindings[0]!.roles = ["OWNER"];
  const beforeReload = state.calls.filter((call) =>
    call.path.endsWith("/conversations"),
  ).length;
  await page.reload();
  await expect(page.getByText("Support access required")).toBeVisible();
  expect(
    state.calls.filter((call) => call.path.endsWith("/conversations")),
  ).toHaveLength(beforeReload);
});

test("unknown requested binding never silently selects another customer", async ({
  page,
}) => {
  const state = await mockDesk(page);
  await page.goto("/?bindingId=not-assigned");
  await expect(page.getByText("Workspace unavailable")).toBeVisible();
  expect(state.calls.some((call) => call.path.includes("/bindings/"))).toBe(
    false,
  );
});

test("switching workspace clears transcript and draft; browser back restores only authorized scope", async ({
  page,
}) => {
  const state = await mockDesk(page);
  await page.goto("/?bindingId=studio-prod");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await page.getByRole("button", { name: "Claim", exact: true }).click();
  await page.getByLabel("Reply to Eve Lin").fill("PRIVATE UNSENT DRAFT");
  await page
    .getByLabel("Workspace", { exact: true })
    .selectOption("support-stage");
  await expect(
    page.getByRole("button", { name: /Cedar visitor/ }),
  ).toBeVisible();
  await expect(page.getByText("Eve Lin", { exact: true })).toHaveCount(0);
  await expect(page.getByText("PRIVATE UNSENT DRAFT")).toHaveCount(0);
  await page.goBack();
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await expect(page.getByLabel("Reply to Eve Lin")).toHaveValue("");
  expect(
    state.calls.filter(
      (call) => call.method === "POST" && call.path.includes("support-stage"),
    ),
  ).toHaveLength(0);
});

test("failed reply keeps draft and retry identity, and transcript fetch failure disables sends", async ({
  page,
}) => {
  const state = fixtureState();
  state.failReply = true;
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await page.getByRole("button", { name: "Claim", exact: true }).click();
  await page.getByLabel("Reply to Eve Lin").fill("Preserve this reply");
  await page.getByRole("button", { name: "Send reply", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Your draft is preserved",
  );
  await expect(page.getByLabel("Reply to Eve Lin")).toHaveValue(
    "Preserve this reply",
  );
  state.failReply = false;
  await page.getByRole("button", { name: "Send reply", exact: true }).click();
  await expect(page.getByLabel("Reply to Eve Lin")).toHaveValue("");
  const sends = state.calls.filter((call) => call.path.endsWith("/messages"));
  expect(sends[0]!.body!.idempotencyKey).toBe(sends[1]!.body!.idempotencyKey);
  state.failDetail = true;
  await page.getByRole("button", { name: "Refresh workspace" }).click();
  await expect(page.getByRole("alert")).toContainText("Updates paused");
  await expect(page.getByLabel("Reply to Eve Lin")).toBeDisabled();
});

test("five-second polling receives new messages without reselecting conversation", async ({
  page,
}) => {
  const state = await mockDesk(page);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  state.messages.push({
    ...state.messages[0]!,
    messageId: "fresh",
    content: "New incoming message from visitor",
    createdAt: new Date().toISOString(),
  });
  await expect(page.getByText("New incoming message from visitor")).toBeVisible(
    { timeout: 8000 },
  );
});

test("session expiry clears customer transcript and shows sign in, without redirecting tokens", async ({
  page,
}) => {
  const state = await mockDesk(page);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  state.expire = true;
  await page.getByRole("button", { name: "Refresh workspace" }).click();
  await expect(
    page.getByRole("link", { name: "Sign in to LoomAI Desk" }),
  ).toBeVisible();
  await expect(page.getByText("Eve Lin", { exact: true })).toHaveCount(0);
  await expect(
    page.getByRole("link", { name: "Sign in to LoomAI Desk" }),
  ).toHaveAttribute("href", "/oauth2/authorization/supabase");
});

test("stale review version is shown and never reported as approved", async ({
  page,
}) => {
  const state = fixtureState();
  state.decisionFailure = {
    reason: "REVIEW_VERSION_CONFLICT",
    publicMessage:
      "This task changed. Review the current version before deciding.",
    retryable: false,
  };
  await mockDesk(page, state);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Action reviews", exact: true })
    .click();
  await page
    .getByRole("button", { name: /Confirm a visitor appointment/ })
    .click();
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await page
    .getByRole("button", { name: "Approve action", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("This task changed");
  await expect(page.getByText("Task approved")).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText(
    "REVIEW_VERSION_CONFLICT",
  );
  await expect(page.getByRole("alert")).toContainText(
    "This decision is not retryable.",
  );
  expect(
    state.calls.filter((call) => call.path.endsWith("/decisions")),
  ).toHaveLength(1);
});

test("mobile navigation, queue and transcript remain usable without overflow", async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockDesk(page);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await page.getByRole("button", { name: "Claim", exact: true }).click();
  await page
    .getByLabel("Reply to Eve Lin")
    .fill("Thanks for getting in touch.");
  await page.screenshot({ path: testInfo.outputPath("desk-mobile.png") });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "Back to queue" }).click();
  await page.getByRole("button", { name: "Open navigation" }).click();
  await page.getByRole("button", { name: "Agents & access" }).click();
  await expect(page.getByText("Your support team")).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});

test("unassigned verified agent can register but does not see customer workspaces", async ({
  page,
}) => {
  const state = fixtureState();
  state.session.bindings = [];
  state.session.registered = false;
  state.session.operator = null!;
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: "Finish registration" }).click();
  await expect(page.getByText("You're on the team. Almost.")).toBeVisible();
  await expect(page.getByText("agent-morgan")).toBeVisible();
  expect(state.calls.some((call) => call.path.includes("/bindings/"))).toBe(
    false,
  );
});

test("callback failure is visible and an explicit retry stays scoped to its binding", async ({
  page,
}) => {
  const state = fixtureState();
  state.callbacks.push({
    callbackId: "delivery-1",
    handoverId: "hand-1",
    eventType: "OPERATOR_MESSAGE",
    status: "DEAD",
    failureCode: "CONNECTOR_UNAVAILABLE",
    attemptCount: 8,
    retryEligible: true,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  });
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await page.getByRole("button", { name: "View delivery" }).click();
  await expect(
    page.getByText("8 attempts / CONNECTOR_UNAVAILABLE"),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Retry delivery", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Retry delivery", exact: true }),
  ).toHaveCount(0);
  const retry = state.calls.find((call) =>
    call.path.endsWith("/delivery-1/retry"),
  );
  expect(retry?.path).toBe(
    "/api/desk/v1/bindings/studio-prod/operations/callbacks/delivery-1/retry",
  );
  expect(retry?.body?.idempotencyKey).toBeTruthy();
});

test("sign in, cancelled OAuth, and real public-client signup use provider flow", async ({
  page,
}, testInfo) => {
  const state = fixtureState();
  state.session.authenticated = false;
  state.session.bindings = [];
  await mockDesk(page, state);
  let signupRedirect: string | null = null;
  await page.route(
    "https://identity.example.test/auth/v1/signup**",
    (route) => {
      signupRedirect = new URL(route.request().url()).searchParams.get(
        "redirect_to",
      );
      return route.fulfill({
        status: 200,
        headers: { "access-control-allow-origin": "*" },
        json: {
          user: { id: "new", email: "new@example.test", identities: [] },
          session: null,
        },
      });
    },
  );
  await page.goto("/?bindingId=studio-prod&login=cancelled");
  await expect(page.getByRole("alert")).toContainText("Sign-in was cancelled");
  await page.screenshot({
    path: testInfo.outputPath("login.png"),
    animations: "disabled",
  });
  await page
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await page.getByLabel("Full name", { exact: true }).fill("New Agent");
  await page.getByLabel("Email", { exact: true }).fill("new@example.test");
  await page
    .getByLabel("Password", { exact: true })
    .fill("fixture-password-only");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(
    page.getByText("Check your inbox", { exact: true }),
  ).toBeVisible();
  expect(signupRedirect).toBe(`${new URL(page.url()).origin}/`);
  await expect(
    page.getByRole("link", { name: "Continue to sign in" }),
  ).toHaveAttribute(
    "href",
    "/oauth2/authorization/supabase?bindingId=studio-prod",
  );
  expect(
    await page.evaluate(() =>
      Object.keys(localStorage).some((key) => key.includes("auth-token")),
    ),
  ).toBe(false);
  expect(state.calls.some((call) => call.path.includes("/bindings/"))).toBe(
    false,
  );
});

test("email-return JWTs are removed without creating a Desk session or permissions", async ({
  page,
}) => {
  const state = fixtureState();
  state.session.authenticated = false;
  state.session.bindings = [];
  await mockDesk(page, state);
  const headers: string[] = [];
  page.on("request", (request) => {
    const authorization = request.headers()["authorization"];
    if (authorization) headers.push(authorization);
  });
  await page.goto(
    "/#access_token=untrusted-email-jwt&refresh_token=untrusted-refresh&type=signup",
  );
  await expect(
    page.getByRole("heading", { name: "Continue your sign-in" }),
  ).toBeVisible();
  await expect(page).toHaveURL("http://127.0.0.1:5189/");
  await expect(
    page.getByRole("link", { name: "Sign in to LoomAI Desk" }),
  ).toHaveAttribute("href", "/oauth2/authorization/supabase");
  expect(headers).toEqual([]);
  expect(
    state.calls.some(
      (call) =>
        call.path.includes("/bindings/") ||
        call.path.endsWith("/agents/register"),
    ),
  ).toBe(false);
  expect(
    await page.evaluate(() =>
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ),
  ).not.toMatch(/untrusted-email-jwt|untrusted-refresh|auth-token/);
});

test("one-click Console deep link automatically enters native OAuth with binding preserved", async ({
  page,
}) => {
  const state = fixtureState();
  state.session.authenticated = false;
  state.session.bindings = [];
  await mockDesk(page, state);
  let launches = 0;
  await page.route(
    "**/oauth2/authorization/supabase?bindingId=studio-prod",
    (route) => {
      launches++;
      return route.fulfill({
        contentType: "text/html",
        body: "<main>Native identity provider authorization</main>",
      });
    },
  );
  await page.goto("/?bindingId=studio-prod");
  await expect(page).toHaveURL(
    /\/oauth2\/authorization\/supabase\?bindingId=studio-prod$/,
  );
  await expect(
    page.getByText("Native identity provider authorization"),
  ).toBeVisible();
  expect(launches).toBe(1);
});

test("owner edits existing agent permissions through registered-agent assignment contract", async ({
  page,
}) => {
  const state = await mockDesk(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Agents & access" }).click();
  await page
    .getByRole("button", { name: "Edit permissions for Sam Taylor" })
    .click();
  await page.getByLabel("Action reviewer").check();
  await page
    .getByRole("button", { name: "Save permissions", exact: true })
    .click();
  const edit = state.calls.find(
    (call) => call.method === "POST" && call.path.endsWith("/agents"),
  );
  expect(edit?.body).toEqual({
    agentReference: "agent-sam",
    roles: ["SUPPORT", "REVIEWER"],
  });
});

test("revoked assignment removes transcript on the next authorized refresh", async ({
  page,
}) => {
  const state = await mockDesk(page);
  await page.goto("/?bindingId=studio-prod");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await expect(
    page.getByRole("region", { name: "Conversation transcript" }),
  ).toBeVisible();
  state.session.bindings = [state.session.bindings[1]!];
  await page.getByRole("button", { name: "Refresh workspace" }).click();
  await expect(page.getByText("Workspace unavailable")).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Conversation transcript" }),
  ).toHaveCount(0);
});

test("local logout sends session CSRF and clears protected workspace", async ({
  page,
}) => {
  const state = await mockDesk(page);
  let csrf = "";
  await page.route("**/logout", async (route) => {
    csrf = route.request().headers()["x-csrf-test"] ?? "";
    state.session.authenticated = false;
    await route.fulfill({ status: 204 });
  });
  await page.goto("/");
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "Sign in to LoomAI Desk" }),
  ).toBeVisible();
  expect(csrf).toBe("fixture-csrf");
});

test("safe text rendering does not execute provider markup or attachment URLs", async ({
  page,
}) => {
  const state = fixtureState();
  state.messages[0]!.content = "<img src=x onerror=alert(1)>";
  state.messages[0]!.attachmentRefs = ["javascript:alert(2)"] as never[];
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await expect(
    page.getByText("<img src=x onerror=alert(1)>", { exact: true }),
  ).toBeVisible();
  expect(await page.locator(".message-content img").count()).toBe(0);
  expect(await page.locator(".message-attachments a").count()).toBe(0);
});

test("keyboard opens and dismisses assignment dialog; compact desktop stays in bounds", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1024, height: 768 });
  await mockDesk(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Agents & access" }).focus();
  await page.keyboard.press("Enter");
  await page.getByRole("button", { name: "Assign agent", exact: true }).focus();
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("dialog", { name: "Assign an agent" }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});

test("a queued claim remains read-only until the deployment confirms it", async ({
  page,
}) => {
  const state = fixtureState();
  state.conversations[0]!.status = "CLAIM_PENDING";
  state.conversations[0]!.claimedBy = "agent-1";
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await expect(
    page.getByText("Claim pending. Waiting for deployment confirmation."),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Claim", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByLabel("Reply to Eve Lin")).toBeDisabled();
  state.conversations[0]!.status = "ACTIVE";
  await page.getByRole("button", { name: "Refresh workspace" }).click();
  await expect(page.getByLabel("Reply to Eve Lin")).toBeEnabled();
  expect(state.calls.filter((call) => call.method === "POST")).toHaveLength(0);
});

test("Pending reviews use WAITING_FOR_REVIEW; other actual states remain in All tasks", async ({
  page,
}) => {
  const state = fixtureState();
  state.reviews.push({
    ...state.reviews[0]!,
    taskId: "deciding",
    title: "Decision in progress",
    status: "DECIDING",
  });
  state.reviews.push({
    ...state.reviews[0]!,
    taskId: "information",
    title: "More information requested",
    status: "WAITING_FOR_INFORMATION",
  });
  await mockDesk(page, state);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Action reviews", exact: true })
    .click();
  await expect(
    page.getByRole("tab", { name: "Pending", exact: true }),
  ).toHaveAttribute("aria-selected", "true");
  await expect(
    page.getByRole("heading", { name: "Action reviews 2" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: /Confirm a visitor appointment/ }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: /Decision in progress/ }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: /More information requested/ }),
  ).toHaveCount(0);
  await page.getByRole("tab", { name: "All tasks" }).click();
  await expect(
    page.getByRole("button", { name: /Decision in progress/ }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: /More information requested/ }),
  ).toBeVisible();
});

for (const status of [
  "DECIDING",
  "WAITING_FOR_INFORMATION",
  "APPROVED",
  "REJECTED",
  "CORRECTED",
  "ESCALATED",
  "EXPIRED",
  "FAILED",
] as const) {
  test(`actual review status ${status} never enables approve or reject`, async ({
    page,
  }) => {
    const state = fixtureState();
    state.reviews = [{ ...state.reviews[0]!, status }];
    await mockDesk(page, state);
    await page.goto("/");
    await page
      .getByRole("button", { name: "Action reviews", exact: true })
      .click();
    await page.getByRole("tab", { name: "All tasks" }).click();
    await page
      .getByRole("button", { name: /Confirm a visitor appointment/ })
      .click();
    await expect(page.locator(".review-detail-top .status")).toHaveText(
      status
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/^./, (char) => char.toUpperCase()),
    );
    await expect(
      page.getByRole("button", { name: "Approve", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Reject", exact: true }),
    ).toHaveCount(0);
    expect(state.calls.some((call) => call.method === "POST")).toBe(false);
  });
}

test("typed HTTP 200 decision failure remains an error and polling never retries the write", async ({
  page,
}) => {
  const state = fixtureState();
  state.decisionFailure = {
    reason: "REVIEW_DECISION_IN_PROGRESS",
    publicMessage: "Another decision is being processed.",
    retryable: true,
  };
  state.decisionFailureStatus = 200;
  await mockDesk(page, state);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Action reviews", exact: true })
    .click();
  await page
    .getByRole("button", { name: /Confirm a visitor appointment/ })
    .click();
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await page
    .getByRole("button", { name: "Approve action", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Another decision is being processed.",
  );
  await expect(page.getByRole("alert")).toContainText(
    "REVIEW_DECISION_IN_PROGRESS",
  );
  await expect(page.getByRole("alert")).toContainText(
    "Retry permitted. No automatic retry was made.",
  );
  await expect(page.getByText("Task approved", { exact: true })).toHaveCount(0);
  const reads = () =>
    state.calls.filter(
      (call) => call.method === "GET" && call.path.endsWith("/reviews/task-1"),
    ).length;
  const beforePoll = reads();
  await expect.poll(reads, { timeout: 8000 }).toBeGreaterThan(beforePoll);
  expect(
    state.calls.filter((call) => call.path.endsWith("/decisions")),
  ).toHaveLength(1);
});

test("typed HTTP 409 preserves public failure, returned outcome and successor task", async ({
  page,
}) => {
  const state = fixtureState();
  state.decisionFailure = {
    reason: "ACTION_EXECUTION_IN_PROGRESS",
    publicMessage: "The application action is still in progress.",
    retryable: true,
  };
  state.decisionSuccessorTaskId = "review-followup-42";
  state.decisionFailureOutcome = {
    actionName: "confirm_appointment",
    message: "The application returned tracking reference TRACK-42.",
    data: { trackingReference: "TRACK-42" },
  };
  await mockDesk(page, state);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Action reviews", exact: true })
    .click();
  await page
    .getByRole("button", { name: /Confirm a visitor appointment/ })
    .click();
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await page
    .getByRole("button", { name: "Approve action", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "The application action is still in progress.",
  );
  await expect(page.getByRole("alert")).toContainText(
    "ACTION_EXECUTION_IN_PROGRESS",
  );
  await expect(page.getByText("TRACK-42", { exact: true })).toBeVisible();
  await expect(
    page.getByText("review-followup-42", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Task approved", { exact: true })).toHaveCount(0);
});

test("approval without an ActionOutcomeView does not invent execution success", async ({
  page,
}) => {
  const state = fixtureState();
  state.decisionOutcomes["task-1"] = null;
  state.decisionSuccessorTaskId = "review-next-step";
  await mockDesk(page, state);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Action reviews", exact: true })
    .click();
  await page
    .getByRole("button", { name: /Confirm a visitor appointment/ })
    .click();
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await page
    .getByRole("button", { name: "Approve action", exact: true })
    .click();
  await expect(
    page.getByRole("status").filter({ hasText: "Task approved" }),
  ).toContainText("The decision alone does not confirm business execution.");
  await expect(
    page.getByRole("heading", { name: "Returned execution outcome" }),
  ).toHaveCount(0);
  await expect(
    page.getByText("review-next-step", { exact: true }),
  ).toBeVisible();
});

test("review detail displays actual information, message and failureReason projections", async ({
  page,
}) => {
  const state = fixtureState();
  state.reviewDetails["task-1"] = {
    message: "The reviewer must inspect the supplied information.",
    failureReason: "REVIEW_SOURCE_STORE_UNAVAILABLE",
    requestedInformation: { question: "Is a revised visit time required?" },
    successorTaskId: "detail-followup",
  };
  await mockDesk(page, state);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Action reviews", exact: true })
    .click();
  await page
    .getByRole("button", { name: /Confirm a visitor appointment/ })
    .click();
  await expect(
    page.getByText("The reviewer must inspect the supplied information."),
  ).toBeVisible();
  await expect(page.getByRole("alert")).toContainText(
    "REVIEW_SOURCE_STORE_UNAVAILABLE",
  );
  await expect(
    page.getByText("Is a revised visit time required?", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Accessibility arrangements requested", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("detail-followup", { exact: true }),
  ).toBeVisible();
});
