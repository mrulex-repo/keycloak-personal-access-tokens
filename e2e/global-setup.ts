import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const KC_URL = process.env.KEYCLOAK_URL ?? "http://localhost:18080";
const REALM = process.env.KEYCLOAK_REALM ?? "test";
const USERNAME = process.env.KEYCLOAK_USERNAME ?? "alice";
const PASSWORD = process.env.KEYCLOAK_PASSWORD ?? "alicepass";

export const AUTH_FILE = "playwright/.auth/user.json";

/**
 * Authenticate once before any test worker starts and persist the browser
 * storage state (cookies + localStorage) to AUTH_FILE.
 *
 * Each test worker loads this file as its initial storageState so no test
 * ever has to run a browser-based login flow. This eliminates per-test
 * cold-start latency from Keycloak (Freemarker template compilation, OIDC
 * redirect chain) which was causing random 30 s waitFor timeouts.
 */
export default async function globalSetup() {
  mkdirSync("playwright/.auth", { recursive: true });

  const browser = await chromium.launch();
  const page = await browser.newPage();

  await page.goto(`${KC_URL}/realms/${REALM}/account/personal-access-tokens`);

  // First navigation always redirects to the login page — wait generously
  // since this is the one cold-start hit for the entire suite.
  await page.locator("#username").waitFor({ state: "visible", timeout: 60000 });
  await page.locator("#username").fill(USERNAME);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("[type=submit]").click();

  // PAT form visible confirms the full OIDC exchange succeeded.
  await page.locator("#pat-name").waitFor({ state: "visible", timeout: 60000 });

  await page.context().storageState({ path: AUTH_FILE });
  await browser.close();
}
