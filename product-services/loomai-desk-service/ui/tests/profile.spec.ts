import { test, expect } from "@playwright/test";
import { mockDesk } from "./fixture";

async function image(page: import("@playwright/test").Page) {
  const data = await page.evaluate(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 80; canvas.height = 120;
    const context = canvas.getContext("2d")!;
    context.fillStyle = "#227d77"; context.fillRect(0, 0, 80, 120);
    return canvas.toDataURL("image/png").split(",")[1]!;
  });
  return { name: "profile.png", mimeType: "image/png", buffer: Buffer.from(data, "base64") };
}

test("save name and photo, refresh, switch deployments and remove photo without changing roles", async ({ page }, testInfo) => {
  const state = await mockDesk(page);
  const grants = structuredClone(state.session.bindings);
  await page.goto("/");
  await page.getByRole("button", { name: "Profile", exact: true }).click();
  const profile = page.getByRole("dialog", { name: "Your profile" });
  await profile.getByLabel("Display name", { exact: true }).fill("Morgan Support");
  await profile.getByLabel("Agent photo", { exact: true }).setInputFiles(await image(page));
  await expect(profile.locator(".profile-photo-preview img")).toBeVisible();
  expect(state.calls.some(call => call.path.endsWith("/profile"))).toBe(false);
  await profile.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(profile.getByText("Profile saved.", { exact: true })).toBeVisible();
  await expect(profile.getByRole("button", { name: "Save profile", exact: true })).toBeDisabled();
  await page.screenshot({ path: testInfo.outputPath("agent-profile-light.png"), animations: "disabled" });
  await profile.getByRole("button", { name: "Close dialog" }).click();
  await expect(page.locator(".identity-row strong")).toHaveText("Morgan Support");
  await expect(page.locator(".identity-row img")).toBeVisible();
  await page.reload();
  await expect(page.locator(".identity-row strong")).toHaveText("Morgan Support");
  await expect(page.locator(".identity-row img")).toBeVisible();
  await page.getByLabel("Workspace", { exact: true }).selectOption("support-stage");
  await expect(page.locator(".identity-row strong")).toHaveText("Morgan Support");
  await page.getByRole("button", { name: "Use dark theme" }).click();
  await page.getByRole("button", { name: "Profile", exact: true }).click();
  await page.screenshot({ path: testInfo.outputPath("agent-profile-dark.png"), animations: "disabled" });
  await profile.getByRole("button", { name: "Remove photo", exact: true }).click();
  await expect(profile.locator(".profile-photo-preview img")).toHaveCount(0);
  await profile.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(profile.getByText("Profile saved.", { exact: true })).toBeVisible();
  await profile.getByRole("button", { name: "Close dialog" }).click();
  await expect(page.locator(".identity-row img")).toHaveCount(0);
  expect(state.session.bindings).toEqual(grants);
  const writes = state.calls.filter(call => call.path.endsWith("/profile"));
  expect(writes).toHaveLength(2);
  expect(writes[0]!.method).toBe("PUT");
  expect(writes[0]!.csrf).toBe("fixture-csrf");
  expect(writes[0]!.body?.photoDataUrl).toMatch(/^data:image\/jpeg;base64,/);
  expect(writes[0]!.body).not.toHaveProperty("roles");
  expect(writes[0]!.body).not.toHaveProperty("email");
  expect(writes[1]!.body?.removePhoto).toBe(true);
});

test("failed save preserves edits and never claims success", async ({ page }) => {
  const state = await mockDesk(page); state.failProfile = true;
  await page.goto("/");
  await page.getByRole("button", { name: "Profile", exact: true }).click();
  const profile = page.getByRole("dialog");
  await profile.getByLabel("Display name").fill("Unsaved name");
  await profile.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(profile.getByRole("alert")).toContainText("Profile could not be saved");
  await expect(profile.getByLabel("Display name")).toHaveValue("Unsaved name");
  await expect(profile.getByText("Profile saved.", { exact: true })).toHaveCount(0);
  expect(state.session.operator.displayName).toBe("Morgan Reed");
  state.failProfile = false;
  await profile.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(profile.getByText("Profile saved.", { exact: true })).toBeVisible();
});

test("reject active content before upload and keep name drafts", async ({ page }) => {
  const state = await mockDesk(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Profile", exact: true }).click();
  const profile = page.getByRole("dialog");
  await profile.getByLabel("Agent photo").setInputFiles({ name: "unsafe.svg", mimeType: "image/svg+xml", buffer: Buffer.from("<svg/>") });
  await expect(profile.getByRole("alert")).toContainText("Choose a JPG, PNG or WebP image.");
  expect(state.calls.filter(call => call.path.endsWith("/profile"))).toHaveLength(0);
  await profile.getByLabel("Agent photo").setInputFiles({ name: "large.jpg", mimeType: "image/jpeg", buffer: Buffer.alloc(5 * 1024 * 1024 + 1) });
  await expect(profile.getByRole("alert")).toContainText("smaller than 5 MB");
});

test("registered unassigned agent can edit own profile on mobile", async ({ page }, testInfo) => {
  const state = await mockDesk(page); state.session.bindings = [];
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page.getByRole("button", { name: "Edit profile", exact: true }).click();
  const profile = page.getByRole("dialog", { name: "Your profile" });
  await profile.getByLabel("Display name").fill("New Agent");
  await profile.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(profile.getByText("Profile saved.", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("agent-profile-mobile.png"), animations: "disabled" });
  expect(state.session.bindings).toEqual([]);
});
