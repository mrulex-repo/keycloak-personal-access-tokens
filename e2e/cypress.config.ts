import { defineConfig } from "cypress";

const KC_URL = process.env.KEYCLOAK_URL ?? "http://localhost:8080";
const REALM = process.env.KEYCLOAK_REALM ?? "test";
const CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? "test-client";
const USERNAME = process.env.KEYCLOAK_USERNAME ?? "alice";
const PASSWORD = process.env.KEYCLOAK_PASSWORD ?? "alicepass";

export default defineConfig({
  e2e: {
    baseUrl: `${KC_URL}/realms/${REALM}/account`,
    supportFile: "support/e2e.ts",
    specPattern: "tests/**/*.cy.ts",
    env: {
      keycloakUrl: KC_URL,
      realm: REALM,
      clientId: CLIENT_ID,
      username: USERNAME,
      password: PASSWORD,
      patApiUrl: `${KC_URL}/realms/${REALM}/personal-access-token`,
    },
  },
});
