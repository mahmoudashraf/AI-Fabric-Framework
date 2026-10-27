import { test, expect } from "@playwright/test";
import { fixtureState, mockDesk } from "./fixture";

for (const width of [1440, 390]) {
  for (const theme of ["light", "dark"]) {
    test(`customer and AI left, agents right at ${width}px in ${theme} theme`, async ({
      page,
    }, testInfo) => {
      await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
      const state = fixtureState();
      state.conversations[0]!.status = "ACTIVE";
      state.conversations[0]!.claimedBy = "agent-1";
      const createdAt = new Date().toISOString();
      const author = "support.agent.with.a.long.name@customer-support.example.test";
      state.messages = [
        {
          messageId: "layout-customer",
          participantRole: "USER",
          actorRef: "visitor",
          safeActorDisplayName: "Eve Lin",
          content: "Could you explain the available options before I make a booking?",
          attachmentRefs: [],
          createdAt,
        },
        {
          messageId: "layout-ai",
          participantRole: "ASSISTANT",
          actorRef: "ai",
          safeActorDisplayName: "AI assistant",
          content: "There are two available options. I can explain the differences or connect you to a person.\n\nYour reference is REF-" + "ABCDEFGH".repeat(18),
          attachmentRefs: [],
          createdAt,
        },
        {
          messageId: "layout-agent",
          participantRole: "OPERATOR",
          actorRef: "agent-1",
          safeActorDisplayName: author,
          content: "Hello Eve, I'm your support agent. Happy to help with your questions.",
          attachmentRefs: ["Booking policy and visit arrangements.txt"],
          createdAt,
        },
        {
          messageId: "layout-agent-followup",
          participantRole: "OPERATOR",
          actorRef: "agent-1",
          safeActorDisplayName: author,
          content: "Which date would suit you?",
          attachmentRefs: [],
          createdAt,
        },
        {
          messageId: "layout-system",
          participantRole: "SYSTEM",
          actorRef: "system",
          safeActorDisplayName: "System",
          content: "Support agent joined the conversation.",
          attachmentRefs: [],
          createdAt,
        },
        {
          messageId: "layout-visitor",
          participantRole: "VISITOR",
          actorRef: "visitor",
          safeActorDisplayName: "Eve Lin",
          content: "Friday afternoon would work well. Thank you.",
          attachmentRefs: [],
          createdAt,
        },
      ];
      await mockDesk(page, state);
      await page.goto("/?bindingId=studio-prod");
      await page.getByRole("button", { name: /Eve Lin/ }).click();
      if (theme === "dark") {
        if (width === 390) await page.getByRole("button", { name: "Open navigation" }).click();
        await page.getByRole("button", { name: "Use dark theme" }).click();
        if (width === 390) await page.locator(".workspace-rail").getByRole("button", { name: "Close navigation" }).click();
      }
      await expect(page.locator("html")).toHaveAttribute("data-theme", theme);
      await expect(page.locator(".message-row")).toHaveCount(5);
      await expect(page.locator(".message-time")).toHaveCount(5);
      await expect(page.locator(".agent-message.grouped .message-meta")).toHaveCount(0);
      await expect(page.locator(".agent-message.grouped .message-time")).toBeVisible();
      await expect(page.locator(".system-message")).toContainText("Support agent joined");
      await expect(page.locator(".message-attachments")).toContainText("Booking policy");

      const geometry = await page.locator(".message-stream").evaluate((stream) => {
        const bounds = stream.getBoundingClientRect();
        const style = getComputedStyle(stream);
        const box = (el: Element) => {
          const r = el.getBoundingClientRect();
          return { x: r.x, right: r.right, width: r.width };
        };
        return {
          left: bounds.left + parseFloat(style.paddingLeft),
          right: bounds.right - parseFloat(style.paddingRight),
          contentWidth: stream.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight),
          rows: [...stream.querySelectorAll(".message-row")].map((row) => ({
            ...box(row),
            agent: row.classList.contains("agent-message"),
            avatar: box(row.querySelector(".message-avatar")!),
            body: box(row.querySelector(".message-body")!),
            content: box(row.querySelector(".message-content")!),
          })),
          overflows: stream.scrollWidth > stream.clientWidth,
        };
      });
      for (const row of geometry.rows) {
        if (row.agent) {
          expect(Math.abs(row.right - geometry.right)).toBeLessThan(2);
          expect(row.x).toBeGreaterThan(geometry.left + 4);
          expect(row.avatar.x).toBeGreaterThan(row.body.x);
        } else {
          expect(Math.abs(row.x - geometry.left)).toBeLessThan(2);
          expect(row.right).toBeLessThan(geometry.right - 4);
          expect(row.avatar.x).toBeLessThan(row.body.x);
        }
        expect(row.width).toBeLessThan(geometry.contentWidth * (width === 390 ? 0.95 : 0.85));
        expect(row.content.right).toBeLessThanOrEqual(row.right + 1);
      }
      expect(geometry.overflows).toBe(false);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      expect(state.calls.filter((call) => call.method !== "GET")).toHaveLength(0);
      await page.screenshot({ path: testInfo.outputPath(`conversation-${width}-${theme}.png`), animations: "disabled" });
    });
  }
}

test("all existing human role aliases align right without reordering messages", async ({ page }) => {
  const state = fixtureState();
  state.messages = ["USER", "ASSISTANT", "OPERATOR", "AGENT", "HUMAN_AGENT", "HUMAN", "AI", "VISITOR"].map((role, index) => ({
    messageId: `role-${index}`,
    participantRole: role,
    actorRef: `actor-${index}`,
    safeActorDisplayName: `Sender ${index}`,
    content: `Message ${index}`,
    attachmentRefs: [],
    createdAt: new Date().toISOString(),
  }));
  await mockDesk(page, state);
  await page.goto("/");
  await page.getByRole("button", { name: /Eve Lin/ }).click();
  await expect(page.locator(".message-row.agent-message")).toHaveCount(4);
  await expect(page.locator(".message-row.assistant-message")).toHaveCount(2);
  await expect(page.locator(".message-row.customer-message")).toHaveCount(2);
  await expect(page.locator(".message-content")).toHaveText(state.messages.map((message) => message.content));
});
