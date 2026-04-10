import { type APIRequestContext, type Page } from "@playwright/test";

export const KC_URL = process.env.KEYCLOAK_URL ?? "http://localhost:18080";
export const REALM = process.env.KEYCLOAK_REALM ?? "test";
export const CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? "test-client";
export const USERNAME = process.env.KEYCLOAK_USERNAME ?? "alice";
export const PASSWORD = process.env.KEYCLOAK_PASSWORD ?? "alicepass";
export const PAT_API_URL = `${KC_URL}/realms/${REALM}/personal-access-token`;

/** Obtains a Bearer token via the resource-owner password grant. */
export async function getBearerToken(request: APIRequestContext): Promise<string> {
  const response = await request.post(
    `${KC_URL}/realms/${REALM}/protocol/openid-connect/token`,
    {
      form: {
        grant_type: "password",
        client_id: CLIENT_ID,
        username: USERNAME,
        password: PASSWORD,
      },
    },
  );
  const body = await response.json();
  return body.access_token as string;
}

/** Creates a PAT via the REST API and returns the plaintext token. */
export async function createPatViaApi(
  request: APIRequestContext,
  name: string,
  roles: string[],
  expires?: string,
): Promise<string> {
  const token = await getBearerToken(request);
  const response = await request.post(PAT_API_URL, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name, roles, ...(expires ? { expires } : {}) },
  });
  const body = await response.json();
  return body.token as string;
}

/** Deletes all PATs for the test user via the REST API. */
export async function deleteAllPatsViaApi(request: APIRequestContext): Promise<void> {
  const token = await getBearerToken(request);
  const headers = { Authorization: `Bearer ${token}` };

  const listResp = await request.get(PAT_API_URL, { headers });
  const pats: Array<{ id: string }> = await listResp.json();

  await Promise.all(
    pats.map((pat) =>
      request.delete(PAT_API_URL, {
        headers,
        data: { id: pat.id },
      }),
    ),
  );
}

/**
 * Navigates to the PAT page. Tests call this in beforeEach.
 *
 * Authentication is pre-loaded via Playwright's storageState (global-setup.ts
 * authenticates once before any worker starts and persists the session cookies).
 * page.goto() therefore lands directly on the authenticated PAT page — no login
 * redirect or credentials needed here.
 *
 * networkidle wait: PatternFly Brand uses width/height:auto so the masthead <img>
 * has a zero bounding box while the image file is loading. toBeVisible() would
 * fail on a cold run if we return as soon as #pat-name appears. networkidle fires
 * once all pending requests (logo, listPats API, listRoles API) have settled.
 */
export async function loginAndNavigateToPats(page: Page): Promise<void> {
  await page.goto(`${KC_URL}/realms/${REALM}/account/personal-access-tokens`);
  await page.locator("#pat-name").waitFor({ state: "visible", timeout: 30000 });
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
}
