import { defineConfig, devices } from "@playwright/test";
import { AUTH_FILE } from "./global-setup";

const KC_URL = process.env.KEYCLOAK_URL ?? "http://localhost:18080";
const REALM = process.env.KEYCLOAK_REALM ?? "test";

export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  retries: 1,
  // Single worker prevents browser process crashes from memory contention
  // when running inside Docker (Keycloak's PatternFly pages are heavy).
  workers: 1,
  // Authenticate once before any worker starts; all tests reuse the saved
  // session so no test ever has to run a browser-based login flow.
  globalSetup: "./global-setup",
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: `${KC_URL}/realms/${REALM}/account`,
    storageState: AUTH_FILE,
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
