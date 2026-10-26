import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  workers: 2,
  timeout: 30_000,
  use: {
    baseURL: "http://127.0.0.1:5189",
    viewport: { width: 1440, height: 1000 },
    trace: "retain-on-failure",
  },
  reporter: [["list"], ["html", { open: "never" }]],
  webServer: {
    command: process.env.DESK_TEST_PREVIEW
      ? "npm run preview -- --port 5189 --strictPort"
      : "npm run dev -- --port 5189 --strictPort",
    url: "http://127.0.0.1:5189",
    reuseExistingServer: !process.env.CI,
  },
});
