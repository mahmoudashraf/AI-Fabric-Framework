import { expect, test } from "@playwright/test";
import { fixtureState, mockDesk, type Fixture } from "./fixture";

function withContext(): Fixture {
  const state = fixtureState();
  state.externalContext = {
    state: "AVAILABLE",
    context: {
      reference: "REQ-1042",
      title: "Workspace connection help",
      status: "OPEN",
      version: 3,
      accountLabel: "Example company",
      resourceLabels: ["Public workspace", "Production deployment"],
      consent: {
        status: "REVOKED",
        summary: "Future resource checks are no longer permitted.",
        updatedAt: "2026-10-30T10:00:00Z",
      },
      observations: [
        {
          label: "Routing verification",
          status: "INCOMPLETE",
          summary: "The routing record is not verified.",
          observedAt: "2026-10-30T09:55:00Z",
        },
      ],
      resolveAction: {
        label: "Resolve case",
        confirmation: "Mark REQ-1042 resolved?",
      },
    },
  };
  return state;
}

test("external case context and explicit resolution do not mutate human control", async ({
  page,
}, testInfo) => {
  const state = withContext();
  await mockDesk(page, state);
  await page.goto("/?bindingId=studio-prod");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  expect(state.calls.some((call) => call.path.endsWith("/context"))).toBe(
    false,
  );
  await page.getByRole("button", { name: "Show conversation context" }).click();
  const context = page.getByRole("region", { name: "External context" });
  await expect(context.getByText("REQ-1042", { exact: true })).toBeVisible();
  await expect(context.getByText("Example company")).toBeVisible();
  await expect(
    context.getByText("Public workspace", { exact: true }),
  ).toBeVisible();
  await expect(context.getByText("Revoked", { exact: true })).toBeVisible();
  await expect(
    context.getByText("The routing record is not verified."),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("external-context-desktop.png"),
  });
  await page.getByRole("button", { name: "Use dark theme" }).click();
  await page.screenshot({
    path: testInfo.outputPath("external-context-dark.png"),
  });
  await context
    .getByRole("button", { name: "Resolve case", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText(
    "Conversation control stays unchanged.",
  );
  expect(state.calls.filter((call) => call.method === "POST")).toHaveLength(0);
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Resolve case", exact: true })
    .click();
  await expect(context.getByText("Resolved", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Claim", exact: true }),
  ).toBeVisible();
  const writes = state.calls.filter((call) => call.method === "POST");
  expect(writes).toHaveLength(1);
  expect(writes[0]?.path).toBe(
    "/api/desk/v1/bindings/studio-prod/conversations/hand-1/context/resolve",
  );
  expect(writes[0]?.body?.expectedVersion).toBe(3);
  expect(writes[0]?.body?.idempotencyKey).toBeTruthy();
});

test("uncertain case resolve keeps command identity and does not auto retry", async ({
  page,
}) => {
  const state = withContext();
  state.failResolve = true;
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await page.getByRole("button", { name: "Show conversation context" }).click();
  await page.getByRole("button", { name: "Resolve case", exact: true }).click();
  const confirm = page
    .getByRole("dialog")
    .getByRole("button", { name: "Resolve case", exact: true });
  await confirm.click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText(
    "Case provider did not confirm",
  );
  let calls = state.calls.filter((call) =>
    call.path.endsWith("/context/resolve"),
  );
  expect(calls).toHaveLength(1);
  state.failResolve = false;
  await confirm.click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  calls = state.calls.filter((call) => call.path.endsWith("/context/resolve"));
  expect(calls).toHaveLength(2);
  expect(calls[0]?.body).toEqual(calls[1]?.body);
  expect(calls[0]?.body?.expectedVersion).toBe(3);
  expect(calls[0]?.csrf).toBe("fixture-csrf");
});

test("context outage hides stale observations without blocking conversation replies", async ({
  page,
}) => {
  const state = withContext();
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await page.getByRole("button", { name: "Show conversation context" }).click();
  await expect(page.getByText("REQ-1042", { exact: true })).toBeVisible();
  state.failContext = true;
  await page.getByRole("button", { name: "Refresh external context" }).click();
  await expect(page.getByText(/External context unavailable/)).toBeVisible();
  await expect(page.getByText("REQ-1042", { exact: true })).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Resolve case", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Claim", exact: true }).click();
  await page
    .getByLabel("Reply to Eve Lin")
    .fill("I can help while the context service recovers.");
  await page.getByRole("button", { name: "Send reply", exact: true }).click();
  await expect(
    page.getByText("I can help while the context service recovers.", {
      exact: true,
    }),
  ).toBeVisible();
  expect(
    state.calls.some((call) => call.path.endsWith("/context/resolve")),
  ).toBe(false);
});

test("a refreshed case version cannot silently replace the confirmed version", async ({
  page,
}) => {
  await page.clock.install();
  const state = withContext();
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await page.getByRole("button", { name: "Show conversation context" }).click();
  await page.getByRole("button", { name: "Resolve case", exact: true }).click();
  state.externalContext.context!.version = 4;
  await page.clock.fastForward(30001);
  await expect(page.getByRole("dialog").getByRole("status")).toContainText(
    "Context changed.",
  );
  await expect(
    page
      .getByRole("dialog")
      .getByRole("button", { name: "Resolve case", exact: true }),
  ).toBeDisabled();
  expect(
    state.calls.some((call) => call.path.endsWith("/context/resolve")),
  ).toBe(false);
});

test("context is plain text, read-only when no resolve action and usable on narrow screens", async ({
  page,
}, testInfo) => {
  const state = withContext();
  state.externalContext.context!.title = "<img src=x onerror=alert(1)>";
  state.externalContext.context!.resolveAction = null;
  await mockDesk(page, state);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await page.getByRole("button", { name: "Show conversation context" }).click();
  await expect(
    page.getByText("<img src=x onerror=alert(1)>", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".external-context img")).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Resolve case", exact: true }),
  ).toHaveCount(0);
  await page.screenshot({
    path: testInfo.outputPath("external-context-mobile.png"),
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});
