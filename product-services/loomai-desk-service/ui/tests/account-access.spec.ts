import { expect, test } from "@playwright/test";
import { fixtureState, mockDesk } from "./fixture";

test("assigned agent profile exposes their email, reference and shared password recovery", async ({
  page,
}, testInfo) => {
  const state = await mockDesk(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Profile", exact: true }).click();
  const profile = page.getByRole("dialog", { name: "Your account" });
  await expect(
    profile.getByText("morgan@example.test", { exact: true }),
  ).toBeVisible();
  await expect(
    profile.getByText("agent-morgan", { exact: true }),
  ).toBeVisible();
  await expect(
    profile.getByRole("link", { name: "Change password" }),
  ).toHaveAttribute(
    "href",
    "https://console.example.test/auth/desk/sign-in?method=recovery",
  );
  await page.screenshot({
    path: testInfo.outputPath("account-profile.png"),
    animations: "disabled",
  });
  await page.keyboard.press("Escape");
  await expect(profile).toHaveCount(0);
  expect(state.calls.filter((call) => call.method !== "GET")).toEqual([]);
});

test("unassigned agent can manage account without gaining deployment access", async ({
  page,
}) => {
  const state = fixtureState();
  state.session.bindings = [];
  state.session.emailVerified = false;
  await mockDesk(page, state);
  await page.goto("/");
  await expect(
    page.getByRole("link", { name: "Change password" }),
  ).toHaveAttribute(
    "href",
    "https://console.example.test/auth/desk/sign-in?method=recovery",
  );
  await expect(
    page.getByRole("link", { name: "Resend verification" }),
  ).toHaveAttribute(
    "href",
    "https://console.example.test/auth/desk/sign-in?method=verify",
  );
  await expect(
    page.getByRole("button", { name: "Finish registration" }),
  ).toHaveCount(0);
  expect(
    state.calls.some(
      (call) => call.path.includes("/bindings/") || call.method !== "GET",
    ),
  ).toBe(false);
});

for (const portal of [
  "",
  "javascript:alert(1)",
  "http://console.example.test/auth/desk/sign-in",
  "https://user:password@console.example.test/auth/desk/sign-in",
  "https://console.example.test/auth/desk/sign-in?next=https://untrusted.example.test",
  "https://console.example.test/auth/desk/sign-in#redirect",
]) {
  test(`missing or unsafe account portal never replaces OIDC (${portal || "unset"})`, async ({
    page,
  }) => {
    const state = fixtureState();
    state.session.authenticated = false;
    state.session.bindings = [];
    await mockDesk(page, state);
    await page.route("**/api/desk/v1/config", (route) =>
      route.fulfill({
        json: {
          oidcEnabled: true,
          registrationEnabled: true,
          identityPortalUrl: portal,
        },
      }),
    );
    await page.goto("/");
    await expect(
      page.getByRole("link", { name: "Sign in to LoomAI Desk" }),
    ).toHaveAttribute("href", "/oauth2/authorization/supabase");
    await expect(
      page.getByText("Email sign-in and account recovery are not configured.", {
        exact: false,
      }),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: "Email sign-in link" }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("link", { name: "Create account" }),
    ).toHaveCount(0);
  });
}

for (const [label, method] of [
  ["Email sign-in link", "email"],
  ["Forgot password", "recovery"],
  ["Resend verification", "verify"],
  ["Create account", "register"],
]) {
  test(`${label} navigates only to the shared identity portal`, async ({
    page,
  }) => {
    const state = fixtureState();
    state.session.authenticated = false;
    state.session.bindings = [];
    await mockDesk(page, state);
    await page.route(
      "https://console.example.test/auth/desk/sign-in**",
      (route) =>
        route.fulfill({
          contentType: "text/html",
          body: "<main>Shared identity portal fixture</main>",
        }),
    );
    await page.goto("/");
    await page.getByRole("link", { name: label, exact: true }).click();
    await expect(page).toHaveURL(
      `https://console.example.test/auth/desk/sign-in?method=${method}`,
    );
    await expect(
      page.getByText("Shared identity portal fixture"),
    ).toBeVisible();
    expect(state.calls.some((call) => call.method !== "GET")).toBe(false);
  });
}
