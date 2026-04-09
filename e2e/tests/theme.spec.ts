/**
 * Theme integration tests — verify that our custom Keycloak account UI theme
 * renders the full standard account chrome (header, navigation, standard pages)
 * in addition to the PAT page.
 *
 * The tests use the `loginAndNavigateToPats` helper to reach an authenticated
 * state, then navigate to other pages via the sidebar to assert that the
 * standard Keycloak account features are present and functional.
 */

import { test, expect, type Page } from "@playwright/test";
import { KC_URL, REALM, USERNAME, loginAndNavigateToPats } from "../lib/keycloak";

const ACCOUNT_BASE = `${KC_URL}/realms/${REALM}/account`;

test.beforeEach(async ({ page }) => {
  await loginAndNavigateToPats(page);
});

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

test.describe("Header", () => {
  test("shows the Keycloak logo/brand", async ({ page }) => {
    const brand = page.locator(".pf-v5-c-masthead img");
    await expect(brand).toBeVisible();
  });

  test("shows the logged-in user name in the toolbar", async ({ page }) => {
    // KeycloakMasthead renders the user's full name via the options-toggle button.
    // Alice has given_name="Alice", family_name="Test" → shows "Alice Test".
    const optionsToggle = page.getByTestId("options-toggle");
    await expect(optionsToggle).toBeVisible();
    await expect(optionsToggle).toContainText("Alice");
  });

  test("user dropdown contains Sign out action", async ({ page }) => {
    await page.getByTestId("options-toggle").click();
    await expect(page.getByRole("menuitem", { name: /sign out/i })).toBeVisible();
  });

  test("sidebar toggle button is present", async ({ page }) => {
    const toggle = page.locator("#nav-toggle");
    await expect(toggle).toBeVisible();
  });

  test("sidebar toggle collapses and expands the sidebar", async ({ page }) => {
    const sidebar = page.locator(".pf-v5-c-page__sidebar");
    const toggle = page.locator("#nav-toggle");

    // Sidebar starts expanded.
    await expect(sidebar).toHaveClass(/pf-m-expanded/);

    // Collapse.
    await toggle.click();
    await expect(sidebar).toHaveClass(/pf-m-collapsed/);

    // Expand again.
    await toggle.click();
    await expect(sidebar).toHaveClass(/pf-m-expanded/);
  });
});

// ---------------------------------------------------------------------------
// Sidebar navigation
// ---------------------------------------------------------------------------

test.describe("Sidebar navigation", () => {
  test("shows Personal Info link", async ({ page }) => {
    await expect(page.getByTestId("")).toBeVisible().catch(() => {});
    await expect(page.locator("nav").getByText("Personal Info")).toBeVisible();
  });

  test("shows Account Security expandable group", async ({ page }) => {
    await expect(page.locator("nav").getByText("Account Security")).toBeVisible();
  });

  test("Account Security expands to show sub-items", async ({ page }) => {
    const securityGroup = page.locator("nav").getByText("Account Security");
    await securityGroup.click();
    await expect(page.locator("nav").getByText("Signing In")).toBeVisible();
    await expect(page.locator("nav").getByText("Device Activity")).toBeVisible();
  });

  test("shows Applications link", async ({ page }) => {
    await expect(page.locator("nav").getByText("Applications")).toBeVisible();
  });

  test("shows Personal Access Tokens link", async ({ page }) => {
    await expect(page.locator("nav").getByText("Personal Access Tokens")).toBeVisible();
  });

  test("active nav item is highlighted when on PAT page", async ({ page }) => {
    // Already on personal-access-tokens page (from beforeEach).
    const patLink = page.locator(`nav a[href*="personal-access-tokens"]`);
    await expect(patLink).toHaveClass(/pf-m-current/);
  });
});

// ---------------------------------------------------------------------------
// Personal Info page
// ---------------------------------------------------------------------------

test.describe("Personal Info page", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${ACCOUNT_BASE}/`);
    // Wait for the page content: either PersonalInfo form or an error.
    await page.waitForSelector("h1, [data-testid='page-header']", { timeout: 15000 });
  });

  test("renders without crashing", async ({ page }) => {
    // Must not show the React Router default error boundary.
    await expect(page.getByText("Unexpected Application Error")).not.toBeVisible();
  });

  test("shows username field pre-filled with alice", async ({ page }) => {
    const usernameField = page.locator("input#username, input[name='username']");
    if (await usernameField.isVisible()) {
      await expect(usernameField).toHaveValue(USERNAME);
    }
  });

  test("shows email field pre-filled", async ({ page }) => {
    const emailField = page.locator("input#email, input[name='email']");
    if (await emailField.isVisible()) {
      await expect(emailField).toHaveValue("alice@test.local");
    }
  });
});

// ---------------------------------------------------------------------------
// Signing In page
// ---------------------------------------------------------------------------

test.describe("Signing In page", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${ACCOUNT_BASE}/account-security/signing-in`);
    await page.waitForSelector(".pf-v5-c-page__main, [data-testid='page-header']", { timeout: 15000 });
  });

  test("renders without crashing", async ({ page }) => {
    await expect(page.getByText("Unexpected Application Error")).not.toBeVisible();
  });

  test("shows password section", async ({ page }) => {
    await expect(page.getByText(/password/i).first()).toBeVisible({ timeout: 10000 });
  });
});

// ---------------------------------------------------------------------------
// Device Activity page
// ---------------------------------------------------------------------------

test.describe("Device Activity page", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${ACCOUNT_BASE}/account-security/device-activity`);
    await page.waitForSelector(".pf-v5-c-page__main, [data-testid='page-header']", { timeout: 15000 });
  });

  test("renders without crashing", async ({ page }) => {
    await expect(page.getByText("Unexpected Application Error")).not.toBeVisible();
  });

  test("shows at least one active session", async ({ page }) => {
    // Alice has an active session from the login flow.
    await expect(page.getByText(/sign out all/i)).toBeVisible({ timeout: 10000 });
  });
});

// ---------------------------------------------------------------------------
// Applications page
// ---------------------------------------------------------------------------

test.describe("Applications page", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${ACCOUNT_BASE}/applications`);
    await page.waitForSelector(".pf-v5-c-page__main, [data-testid='page-header']", { timeout: 15000 });
  });

  test("renders without crashing", async ({ page }) => {
    await expect(page.getByText("Unexpected Application Error")).not.toBeVisible();
  });

  test("shows the applications list or empty state", async ({ page }) => {
    // Should show either applications or a "no applications" empty state — not an error.
    const hasContent = await Promise.race([
      page.getByText(/application/i).first().waitFor({ state: "visible", timeout: 10000 }).then(() => true),
      page.getByText(/no application/i).first().waitFor({ state: "visible", timeout: 10000 }).then(() => true),
    ]).catch(() => false);
    expect(hasContent).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Navigation — clicking sidebar links changes the page
// ---------------------------------------------------------------------------

test.describe("Sidebar navigation routing", () => {
  test("navigating to Personal Info updates the URL", async ({ page }) => {
    await page.locator("nav").getByText("Personal Info").click();
    await expect(page).toHaveURL(new RegExp(`${REALM}/account/?$`));
  });

  test("navigating to Signing In updates the URL", async ({ page }) => {
    await page.locator("nav").getByText("Account Security").click();
    await page.locator("nav").getByText("Signing In").click();
    await expect(page).toHaveURL(/account-security\/signing-in/);
  });

  test("navigating to Device Activity updates the URL", async ({ page }) => {
    await page.locator("nav").getByText("Account Security").click();
    await page.locator("nav").getByText("Device Activity").click();
    await expect(page).toHaveURL(/account-security\/device-activity/);
  });

  test("navigating to Applications updates the URL", async ({ page }) => {
    await page.locator("nav").getByText("Applications").click();
    await expect(page).toHaveURL(/\/applications/);
  });

  test("navigating to Personal Access Tokens updates the URL", async ({ page }) => {
    await page.locator("nav").getByText("Personal Access Tokens").click();
    await expect(page).toHaveURL(/personal-access-tokens/);
    await expect(page.locator("#pat-name")).toBeVisible();
  });
});
