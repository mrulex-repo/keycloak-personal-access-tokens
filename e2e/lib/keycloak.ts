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
 * Navigates to the PAT page and performs the Keycloak login flow if needed.
 *
 * page.goto() resolves on the HTML load event, before the React SPA runs.
 * keycloak.init() inside KeycloakProvider then JS-redirects to the Keycloak login page.
 * URL-based detection is unreliable: the initial URL already contains "/personal-access-tokens"
 * so waitForURL would resolve immediately before the redirect happens.
 *
 * Instead, we wait for visible DOM elements — either the login username field (redirect
 * happened) or the PAT name input (already authenticated, app loaded). This is robust
 * regardless of redirect timing.
 */
export async function loginAndNavigateToPats(page: Page): Promise<void> {
  await page.goto(`${KC_URL}/realms/${REALM}/account/personal-access-tokens`);

  const patName = page.locator("#pat-name");
  const username = page.locator("#username");

  // Wait for whichever appears first: login form or authenticated PAT form.
  await Promise.race([
    patName.waitFor({ state: "visible", timeout: 30000 }),
    username.waitFor({ state: "visible", timeout: 30000 }),
  ]);

  if (await username.isVisible()) {
    await username.fill(USERNAME);
    await page.locator("#password").fill(PASSWORD);
    await page.locator("[type=submit]").click();
    // Wait for the PAT form — confirms the full OIDC exchange succeeded.
    await patName.waitFor({ state: "visible", timeout: 30000 });
  }
}
