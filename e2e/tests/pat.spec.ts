import { test, expect } from "@playwright/test";
import {
  KC_URL,
  REALM,
  USERNAME,
  PAT_API_URL,
  createPatViaApi,
  deleteAllPatsViaApi,
  loginAndNavigateToPats,
} from "../lib/keycloak";

test.beforeEach(async ({ page, request }) => {
  await deleteAllPatsViaApi(request);
  await loginAndNavigateToPats(page);
});

// ---------------------------------------------------------------------------
// UI: create
// ---------------------------------------------------------------------------

test("creates a PAT and shows token once", async ({ page }) => {
  await page.locator("#pat-name").fill("my-ci-token");
  await page.locator("button:has-text('Select roles')").click();
  await page.getByRole("menuitem", { name:"maven-read" }).click();
  await page.keyboard.press("Escape");

  await page.locator("button:has-text('Create token')").click();

  await expect(page.getByText("Copy this token now")).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Personal access token" })).toBeVisible();
});

test("reveals the token value", async ({ page }) => {
  await page.locator("#pat-name").fill("reveal-test");
  await page.locator("button:has-text('Select roles')").click();
  await page.getByRole("menuitem", { name:"maven-read" }).click();
  await page.keyboard.press("Escape");
  await page.locator("button:has-text('Create token')").click();

  await page.getByLabel("Show token").click();
  const tokenInput = page.getByRole("textbox", { name: "Personal access token" });
  await expect(tokenInput).toHaveValue(/^.{64}$/);
});

test("PAT appears in list with roles and expiry after creation", async ({ page }) => {
  const tomorrow = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000)
    .toISOString()
    .split("T")[0];

  await page.locator("#pat-name").fill("list-test-token");
  await page.locator("button:has-text('Select roles')").click();
  await page.getByRole("menuitem", { name:"maven-read" }).click();
  await page.getByRole("menuitem", { name:"maven-deploy" }).click();
  await page.keyboard.press("Escape");
  await page.locator("#pat-expires").fill(tomorrow);
  await page.locator("button:has-text('Create token')").click();

  await page.getByText("Done").click();

  await expect(page.getByText("list-test-token")).toBeVisible();
  await expect(page.getByText("maven-read")).toBeVisible();
  await expect(page.getByText("maven-deploy")).toBeVisible();
});

// ---------------------------------------------------------------------------
// UI: delete
// ---------------------------------------------------------------------------

test("deletes a PAT and removes it from the list", async ({ page, request }) => {
  await createPatViaApi(request, "to-delete", ["maven-read"]);
  await page.reload();
  await page.waitForURL("**/personal-access-tokens**");

  await expect(page.getByText("to-delete")).toBeVisible();
  await page.locator("button.pf-m-danger").first().click();

  await expect(
    page.getByText(`Delete "to-delete"? This action cannot be undone.`),
  ).toBeVisible();
  await page.getByRole("dialog").getByRole("button", { name: "Delete" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();

  await expect(page.getByText("to-delete")).not.toBeVisible();
});

// ---------------------------------------------------------------------------
// UI: validation
// ---------------------------------------------------------------------------

test("shows error for duplicate token name", async ({ page, request }) => {
  await createPatViaApi(request, "existing-token", ["maven-read"]);
  await page.reload();
  await page.waitForURL("**/personal-access-tokens**");

  await page.locator("#pat-name").fill("existing-token");
  await page.locator("button:has-text('Select roles')").click();
  await page.getByRole("menuitem", { name:"maven-read" }).click();
  await page.keyboard.press("Escape");
  await page.locator("button:has-text('Create token')").click();

  await expect(page.locator("#pat-server-error")).toBeVisible();
});

test("shows validation error when no name is given", async ({ page }) => {
  await page.locator("button:has-text('Select roles')").click();
  await page.getByRole("menuitem", { name:"maven-read" }).click();
  await page.keyboard.press("Escape");
  await page.locator("button:has-text('Create token')").click();

  await expect(page.getByText("Name is required")).toBeVisible();
});

test("shows validation error when no roles are selected", async ({ page }) => {
  await page.locator("#pat-name").fill("noroles-token");
  await page.locator("button:has-text('Create token')").click();

  await expect(page.getByText("At least one role is required")).toBeVisible();
});

// ---------------------------------------------------------------------------
// API: /auth endpoint
// ---------------------------------------------------------------------------

test.describe("/auth endpoint", () => {
  test("returns 200 with identity headers for a valid token", async ({ request }) => {
    const token = await createPatViaApi(request, "auth-test", ["maven-read"]);
    const basicAuth = Buffer.from(`${USERNAME}:${token}`).toString("base64");

    const response = await request.get(`${KC_URL}/realms/${REALM}/personal-access-token/auth`, {
      headers: { Authorization: `Basic ${basicAuth}` },
    });

    expect(response.status()).toBe(200);
    expect(response.headers()["x-user"]).toBe(USERNAME);
    expect(response.headers()["x-user-id"]).toBeTruthy();
    expect(response.headers()["x-roles"]).toContain("maven-read");
  });

  test("returns 403 when X-Required-Role is not in PAT", async ({ request }) => {
    const token = await createPatViaApi(request, "role-test", ["maven-read"]);
    const basicAuth = Buffer.from(`${USERNAME}:${token}`).toString("base64");

    const response = await request.get(`${KC_URL}/realms/${REALM}/personal-access-token/auth`, {
      headers: {
        Authorization: `Basic ${basicAuth}`,
        "X-Required-Role": "docker-pull",
      },
    });

    expect(response.status()).toBe(403);
  });

  test("returns 401 for wrong token", async ({ request }) => {
    const wrongToken = "a".repeat(64);
    const basicAuth = Buffer.from(`${USERNAME}:${wrongToken}`).toString("base64");

    const response = await request.get(`${KC_URL}/realms/${REALM}/personal-access-token/auth`, {
      headers: { Authorization: `Basic ${basicAuth}` },
    });

    expect(response.status()).toBe(401);
  });
});
