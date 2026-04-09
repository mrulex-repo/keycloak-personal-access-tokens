import { defineConfig, devices } from "@playwright/test";

const KC_URL = process.env.KEYCLOAK_URL ?? "http://localhost:18080";
const REALM = process.env.KEYCLOAK_REALM ?? "test";

export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  retries: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: `${KC_URL}/realms/${REALM}/account`,
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
