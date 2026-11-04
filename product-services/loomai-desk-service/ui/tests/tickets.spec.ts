import { expect, test, type Page } from "@playwright/test";
import type { Ticket } from "../src/types";
import { fixtureState, mockDesk } from "./fixture";

const createdAt = "2026-11-03T09:00:00Z";
function ticketFixture(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: "request-1",
    reference: "SUP-1042",
    providerBindingId: "provider-native-1",
    subject: "Approved guide is missing from answers",
    details:
      "The approved guide was connected yesterday. Please check its status.",
    topic: "knowledge",
    status: "OPEN",
    version: 'W/"upstream-etag:42"',
    createdAt,
    updatedAt: createdAt,
    unread: true,
    openingMessageRequired: true,
    conversation: {
      episodeId: "episode-1",
      conversationId: "ai-only-conversation",
      delivery: "PENDING",
    },
    episodes: [
      {
        episodeId: "episode-1",
        conversationId: "ai-only-conversation",
        number: 1,
        delivery: "PENDING",
        openingMessageRequired: true,
        createdAt,
      },
    ],
    events: [
      { type: "CREATED", actorId: "private-requester-subject", createdAt },
    ],
    capabilities: [
      "CREATE",
      "LIST",
      "GET",
      "RESOLVE",
      "AGENT_LIST",
      "AGENT_GET",
      "AGENT_RESOLVE",
    ],
    ...overrides,
  };
}

async function setup(
  page: Page,
  options: { roles?: string[]; tickets?: Ticket[]; ticketingConfigured?: boolean } = {},
) {
  const desk = fixtureState();
  if (options.roles) desk.session.bindings[0]!.roles = options.roles;
  if (options.ticketingConfigured !== undefined)
    desk.session.bindings[0]!.ticketingConfigured = options.ticketingConfigured;
  await mockDesk(page, desk);
  const state = {
    desk,
    tickets: options.tickets ?? [
      ticketFixture(),
      ticketFixture({
        id: "request-2",
        reference: "SUP-1043",
        subject: "Resolved connection request",
        status: "RESOLVED",
        unread: false,
      }),
    ],
    error: 0,
    listError: 0,
    resolveError: 0,
    nextCursor: null as string | null,
    calls: [] as {
      path: string;
      method: string;
      query: URLSearchParams;
      body: unknown;
      key?: string;
      csrf?: string;
    }[],
  };
  await page.route("**/api/desk/v1/bindings/*/tickets**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const call = {
      path: url.pathname,
      query: url.searchParams,
      method: request.method(),
      body: request.postDataJSON(),
      key: request.headers()["idempotency-key"],
      csrf: request.headers()["x-csrf-test"],
    };
    state.calls.push(call);
    if (desk.expire)
      return route.fulfill({ status: 401, json: { message: "expired" } });
    if (url.pathname.endsWith("/tickets")) {
      if (state.listError)
        return route.fulfill({
          status: state.listError,
          json: { message: "private list upstream error" },
        });
      const filter = url.searchParams.get("status");
      return route.fulfill({
        json: {
          items: state.tickets.filter(
            (ticket) => !filter || ticket.status === filter,
          ),
          nextCursor: url.searchParams.has("cursor") ? null : state.nextCursor,
        },
      });
    }
    if (state.error)
      return route.fulfill({
        status: state.error,
        json: {
          message: "private upstream error; Authorization: Bearer secret",
        },
      });
    const id = decodeURIComponent(
      url.pathname.split("/tickets/")[1]!.split("/")[0]!,
    );
    const ticket = state.tickets.find((item) => item.id === id);
    if (!ticket)
      return route.fulfill({ status: 404, json: { code: "TICKET_NOT_FOUND" } });
    if (request.method() === "POST") {
      if (state.resolveError)
        return route.fulfill({
          status: state.resolveError,
          json: { code: "TICKET_CONFLICT", message: "private upstream error" },
        });
      ticket.status = "RESOLVED";
      ticket.version = '"resolved-version"';
      ticket.events.push({ type: "RESOLVED", actorId: "agent-1", createdAt });
    }
    return route.fulfill({ json: ticket });
  });
  return state;
}

async function openTickets(page: Page) {
  await page.goto("/?bindingId=studio-prod");
  const nav = page.getByRole("button", { name: "Tickets", exact: true });
  await expect(page.locator(".desk-shell")).toBeVisible();
  if (page.viewportSize()!.width <= 900)
    await page.getByRole("button", { name: "Open navigation" }).click();
  await nav.click();
}

async function openFirstTicket(page: Page) {
  await openTickets(page);
  await page.getByRole("button", { name: /SUP-1042/ }).click();
  await expect(
    page.getByRole("region", { name: "Ticket detail" }),
  ).toBeVisible();
}

test("ticketing capability disables navigation without requesting an unconfigured ticket list", async ({ page }) => {
  const state = await setup(page, { ticketingConfigured: false });
  await page.goto("/?bindingId=studio-prod");
  await expect(page.locator(".desk-shell")).toBeVisible();
  await expect(page.getByRole("button", { name: "Tickets", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Tickets", exact: true }))
    .toHaveAttribute("title", "Ticketing is not configured for this workspace.");
  expect(state.calls).toHaveLength(0);
});

test("ticketing capability refresh retires ticket detail and stops polling after configuration becomes unavailable", async ({ page }) => {
  await page.clock.install();
  const state = await setup(page);
  await openFirstTicket(page);
  state.desk.session.bindings[0]!.ticketingConfigured = false;
  await page.getByRole("button", { name: "Refresh workspace" }).click();
  await expect(page.getByText("Ticketing not configured", { exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Ticket detail" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Tickets", exact: true })).toBeDisabled();
  const count = state.calls.length;
  await page.clock.fastForward(6000);
  expect(state.calls).toHaveLength(count);
  state.desk.session.bindings[0]!.ticketingConfigured = true;
  await page.getByRole("button", { name: "Refresh workspace" }).click();
  await expect(page.getByRole("region", { name: "Ticket queue" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Ticket detail" })).toHaveCount(0);
});

test("ticketing capability guards a retained Tickets view on browser workspace changes", async ({ page }) => {
  const state = await setup(page);
  state.desk.session.bindings[1]!.ticketingConfigured = false;
  await openFirstTicket(page);
  await page.evaluate(() => {
    history.pushState({}, "", "/?bindingId=support-stage");
    window.dispatchEvent(new PopStateEvent("popstate"));
  });
  await expect(page.getByText("Ticketing not configured", { exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Ticket detail" })).toHaveCount(0);
  expect(state.calls.some(call => call.path.includes("/support-stage/tickets"))).toBe(false);
  await page.goBack();
  await expect(page.getByRole("region", { name: "Ticket queue" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Ticket detail" })).toHaveCount(0);
});

test("ticketing capability does not turn a configured list 404 into an empty list or missing ticket", async ({ page }) => {
  const state = await setup(page);
  state.listError = 404;
  await openTickets(page);
  await expect(page.getByRole("alert")).toContainText("The ticket list is unavailable for this workspace.");
  await expect(page.getByText("This ticket is unavailable in the selected workspace.", { exact: false })).toHaveCount(0);
  expect(state.calls.some(call => call.path.endsWith("/tickets"))).toBe(true);
});

test("ticket list, provider status, intake and lifecycle events use normalized data without fetching AI transcripts", async ({
  page,
}) => {
  const state = await setup(page);
  await openFirstTicket(page);
  const detail = page.getByRole("region", { name: "Ticket detail" });
  await expect(detail.getByText(state.tickets[0]!.details)).toBeVisible();
  await expect(
    detail.getByText(state.tickets[0]!.version, { exact: true }),
  ).toBeVisible();
  await expect(
    detail
      .getByRole("region", { name: "Ticket status history" })
      .getByText("Created", { exact: true }),
  ).toBeVisible();
  await expect(
    detail.getByText("No human handover in the current queue."),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Open conversation", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByLabel(/Reply to/)).toHaveCount(0);
  await expect(page.getByText("private-requester-subject")).toHaveCount(0);
  expect(
    state.desk.calls.some((call) => /\/conversations\/[^/]+$/.test(call.path)),
  ).toBe(false);
  expect(state.calls.filter((call) => call.method === "POST")).toHaveLength(0);
  await page.getByRole("tab", { name: "Open", exact: true }).click();
  await expect(page.getByRole("button", { name: /SUP-1043/ })).toHaveCount(0);
  expect(state.calls.at(-1)?.query.get("status")).toBe("OPEN");
  await page
    .getByLabel("Ticket status", { exact: true })
    .selectOption("RESOLVED");
  await expect(page.getByRole("button", { name: /SUP-1043/ })).toBeVisible();
  await page.getByLabel("Search tickets").fill("not-a-reference");
  await expect(page.getByText("No matching tickets")).toBeVisible();
});

test("pagination passes opaque cursors and resets them when status changes", async ({
  page,
}) => {
  const state = await setup(page);
  state.nextCursor = "cursor/opaque+value=";
  await openTickets(page);
  await page.getByRole("button", { name: "Next ticket page" }).click();
  await expect(page.getByText("2 tickets / page 2")).toBeVisible();
  expect(state.calls.at(-1)?.query.get("cursor")).toBe(state.nextCursor);
  expect(state.calls.at(-1)?.query.get("limit")).toBe("50");
  await page.getByRole("button", { name: "Previous ticket page" }).click();
  await expect(page.getByText("2 tickets / page 1")).toBeVisible();
  await page.getByRole("button", { name: "Next ticket page" }).click();
  await page.getByRole("tab", { name: "Open", exact: true }).click();
  await expect(page.getByText("1 tickets / page 1")).toBeVisible();
  expect(state.calls.at(-1)?.query.has("cursor")).toBe(false);
});

test("resolution confirms the inspected opaque version with session CSRF and never changes conversation control", async ({
  page,
}) => {
  const state = await setup(page);
  const version = state.tickets[0]!.version;
  await openFirstTicket(page);
  await page
    .getByRole("button", { name: "Resolve ticket", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText("Conversation control stays unchanged.");
  expect(state.calls.some((call) => call.method === "POST")).toBe(false);
  await dialog
    .getByRole("button", { name: "Resolve ticket", exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("status")).toContainText("Ticket resolved.");
  const writes = state.calls.filter((call) => call.method === "POST");
  expect(writes).toHaveLength(1);
  expect(writes[0]?.body).toEqual({ expectedVersion: version });
  expect(writes[0]?.key).toBeTruthy();
  expect(writes[0]?.csrf).toBe("fixture-csrf");
  expect(
    state.desk.calls.filter((call) => call.method === "POST"),
  ).toHaveLength(0);
  expect(state.desk.conversations[0]?.status).toBe("WAITING");
});

test("uncertain resolution is not retried by polling; a deliberate identical retry preserves its key", async ({
  page,
}) => {
  await page.clock.install();
  const state = await setup(page);
  state.resolveError = 503;
  await openFirstTicket(page);
  await page
    .getByRole("button", { name: "Resolve ticket", exact: true })
    .click();
  const confirm = page
    .getByRole("dialog")
    .getByRole("button", { name: "Resolve ticket", exact: true });
  await confirm.click();
  await expect(page.getByRole("alert")).toContainText(
    "No command was automatically retried.",
  );
  await expect(page.getByRole("alert")).not.toContainText("private upstream");
  await page.clock.fastForward(16_000);
  expect(state.calls.filter((call) => call.method === "POST")).toHaveLength(1);
  state.resolveError = 0;
  await confirm.click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const writes = state.calls.filter((call) => call.method === "POST");
  expect(writes).toHaveLength(2);
  expect(writes[1]?.key).toBe(writes[0]?.key);
  expect(writes[1]?.body).toEqual(writes[0]?.body);
});

test("polling cannot silently substitute a newer version into an open resolution confirmation", async ({
  page,
}) => {
  await page.clock.install();
  const state = await setup(page);
  await openFirstTicket(page);
  await page
    .getByRole("button", { name: "Resolve ticket", exact: true })
    .click();
  state.tickets[0]!.version = 'W/"different-etag"';
  await page.clock.fastForward(6000);
  await expect(page.getByRole("dialog").getByRole("status")).toContainText(
    "Ticket changed.",
  );
  await expect(
    page
      .getByRole("dialog")
      .getByRole("button", { name: "Resolve ticket", exact: true }),
  ).toBeDisabled();
  expect(state.calls.some((call) => call.method === "POST")).toBe(false);
});

test("409 refreshes state for review without automatic mutation or a success receipt", async ({
  page,
}) => {
  const state = await setup(page);
  await openFirstTicket(page);
  await page
    .getByRole("button", { name: "Resolve ticket", exact: true })
    .click();
  state.tickets[0]!.version = "another-agent-version";
  state.resolveError = 409;
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Resolve ticket", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Review the latest ticket",
  );
  await expect(
    page
      .getByRole("dialog")
      .getByRole("button", { name: "Resolve ticket", exact: true }),
  ).toBeDisabled();
  await expect(page.getByText("Ticket resolved.", { exact: true })).toHaveCount(
    0,
  );
  expect(state.calls.filter((call) => call.method === "POST")).toHaveLength(1);
});

for (const roles of [["OWNER"], ["REVIEWER"], ["OWNER", "REVIEWER"]]) {
  test(`${roles.join("+")} does not imply SUPPORT ticket permission`, async ({
    page,
  }) => {
    const state = await setup(page, { roles });
    await openTickets(page);
    await expect(
      page.getByText("Support access required", { exact: true }),
    ).toBeVisible();
    expect(state.calls).toHaveLength(0);
  });
}

test("provider-neutral view disables unsupported agent resolution even if requester resolution exists", async ({
  page,
}) => {
  const state = await setup(page, {
    tickets: [
      ticketFixture({
        providerBindingId: "external-provider",
        upstreamStatus: "Awaiting customer",
        capabilities: ["GET", "LIST", "RESOLVE", "AGENT_LIST", "AGENT_GET"],
      }),
    ],
  });
  await openFirstTicket(page);
  await expect(
    page.getByText("Awaiting customer", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Resolve ticket", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByText("Resolution is not supported by this ticket provider."),
  ).toBeVisible();
  expect(state.calls.some((call) => call.method === "POST")).toBe(false);
});

test("only a real linked handover opens existing claim, reply and return controls, with a route back to the ticket", async ({
  page,
}) => {
  const ticket = ticketFixture();
  ticket.episodes[0]!.conversationId = "conversation-82f1";
  const state = await setup(page, { tickets: [ticket] });
  await openFirstTicket(page);
  expect(
    state.desk.calls.some((call) =>
      call.path.endsWith("/conversations/hand-1"),
    ),
  ).toBe(false);
  await page
    .getByRole("button", { name: "Open conversation", exact: true })
    .click();
  await expect(
    page.getByRole("region", { name: "Conversation transcript" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Claim", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Reply to Eve Lin")).toBeDisabled();
  expect(
    state.desk.calls.filter((call) => call.method === "POST"),
  ).toHaveLength(0);
  await page.getByRole("button", { name: "Claim", exact: true }).click();
  await page
    .getByLabel("Reply to Eve Lin")
    .fill("I will check the approved guide.");
  await page.getByRole("button", { name: "Send reply", exact: true }).click();
  await expect(
    page.getByText("I will check the approved guide.", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Return to AI", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Return to AI", exact: true })
    .click();
  await expect(page.getByText("Back with AI", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Back to ticket SUP-1042" }).click();
  await expect(
    page.getByRole("region", { name: "Ticket detail" }),
  ).toBeVisible();
  expect(state.tickets[0]?.status).toBe("OPEN");
  expect(state.calls.some((call) => call.method === "POST")).toBe(false);
});

test("failed handover metadata never authorizes a stale conversation link", async ({
  page,
}) => {
  await page.clock.install();
  const ticket = ticketFixture();
  ticket.episodes[0]!.conversationId = "conversation-82f1";
  await setup(page, { tickets: [ticket] });
  await openFirstTicket(page);
  await expect(
    page.getByRole("button", { name: "Open conversation", exact: true }),
  ).toBeVisible();
  await page.route("**/conversations?limit=100", (route) =>
    route.fulfill({ status: 503, json: { message: "Unavailable" } }),
  );
  await page.clock.fastForward(6000);
  await expect(
    page.getByRole("button", { name: "Open conversation", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByText("Human handover status unavailable."),
  ).toBeVisible();
});

test("ticket failures hide stale detail and never display dependency secrets", async ({
  page,
}) => {
  const state = await setup(page);
  await openFirstTicket(page);
  state.error = 404;
  await page
    .getByRole("button", { name: "Refresh ticket", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "unavailable in the selected workspace",
  );
  await expect(page.getByRole("region", { name: "Ticket intake" })).toHaveCount(
    0,
  );
  await expect(page.getByText(/Bearer secret/)).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Resolve ticket", exact: true }),
  ).toHaveCount(0);
});

test("revoked SUPPORT and workspace switching clear ticket state and use only the new binding", async ({
  page,
}) => {
  const state = await setup(page);
  await openFirstTicket(page);
  state.desk.session.bindings[0]!.roles = ["OWNER"];
  await page.getByRole("button", { name: "Refresh workspace" }).click();
  await expect(
    page.getByText("Support access required", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("region", { name: "Ticket detail" })).toHaveCount(
    0,
  );
  await page
    .getByLabel("Workspace", { exact: true })
    .selectOption("support-stage");
  await page.getByRole("button", { name: "Tickets", exact: true }).click();
  await expect(page.getByRole("button", { name: /SUP-1042/ })).toBeVisible();
  await expect(page.getByRole("region", { name: "Ticket detail" })).toHaveCount(
    0,
  );
  expect(state.calls.at(-1)?.path).toContain("/support-stage/tickets");
});

test("expired ticket session clears all protected content", async ({
  page,
}) => {
  const state = await setup(page);
  await openFirstTicket(page);
  state.desk.expire = true;
  await page
    .getByRole("button", { name: "Refresh ticket", exact: true })
    .click();
  await expect(
    page.getByRole("link", { name: "Sign in to LoomAI Desk" }),
  ).toBeVisible();
  await expect(page.getByRole("region", { name: "Ticket detail" })).toHaveCount(
    0,
  );
  await expect(page.getByText("SUP-1042", { exact: true })).toHaveCount(0);
});

for (const viewport of [
  { width: 1440, height: 1000 },
  { width: 1024, height: 768 },
  { width: 390, height: 844 },
  { width: 320, height: 740 },
]) {
  for (const theme of ["light", "dark"] as const) {
    test(`ticket ${theme} layout ${viewport.width}px is readable, safe and navigable`, async ({
      page,
    }, testInfo) => {
      await page.setViewportSize(viewport);
      await page.addInitScript(
        (value) => localStorage.setItem("loomai-desk-theme", value),
        theme,
      );
      const ticket = ticketFixture({
        subject: `Guide ${"longsubject".repeat(16)}`,
        reference: "SUP-1042",
        details: "<img src=x onerror=alert(1)>\nThis is literal intake text.",
      });
      ticket.providerBindingId = "provider/" + "opaque-".repeat(22);
      ticket.episodes[0]!.conversationId = "conversation-".repeat(18);
      await setup(page, { tickets: [ticket] });
      await openFirstTicket(page);
      await expect(page.locator("html")).toHaveAttribute("data-theme", theme);
      await expect(
        page.getByRole("region", { name: "Ticket intake" }),
      ).toContainText("<img src=x onerror=alert(1)>");
      await expect(page.locator(".ticket-detail img")).toHaveCount(0);
      await expect(
        page.getByRole("button", { name: "Resolve ticket", exact: true }),
      ).toBeInViewport();
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      ).toBe(true);
      expect(
        await page
          .locator(".ticket-detail")
          .evaluate((el) => el.scrollWidth <= el.clientWidth),
      ).toBe(true);
      await page.screenshot({
        path: testInfo.outputPath(`tickets-${theme}-${viewport.width}.png`),
      });
      if (viewport.width <= 640) {
        await page
          .getByRole("button", { name: "Back to queue", exact: true })
          .click();
        await expect(
          page.getByRole("region", { name: "Ticket queue" }),
        ).toBeVisible();
        await expect(
          page.getByRole("region", { name: "Ticket detail" }),
        ).toHaveCount(0);
      }
    });
  }
}
