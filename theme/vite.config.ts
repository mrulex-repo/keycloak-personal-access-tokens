import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { keycloakify } from "keycloakify/vite-plugin";

export default defineConfig({
  plugins: [
    react(),
    keycloakify({
      accountThemeImplementation: "Single-Page",
      themeName: "keycloak-plugin-pat",
      postBuild: async (buildContext) => {
        const { writeFileSync, existsSync, mkdirSync } = await import("node:fs");
        const { join } = await import("node:path");

        // The PageNav component fetches content.json from the theme's resourceUrl root
        // (e.g. /resources/{hash}/account/keycloak-plugin-pat/content.json).
        // Keycloakify maps the vite build output to resources/dist/ inside the JAR, so
        // public/content.json lands at resources/dist/content.json — the wrong path.
        // This postBuild hook writes it directly to resources/content.json (the root).
        // Only expose the PAT page — other standard account pages (PersonalInfo, SigningIn, etc.)
        // require the Keycloak account REST API which is not available in all deployments.
        // Mirror the standard Keycloak account content.json, then append the PAT page.
        const contentJson = [
          { label: "personalInfo", path: "" },
          {
            label: "accountSecurity",
            children: [
              { label: "signingIn", path: "account-security/signing-in" },
              { label: "deviceActivity", path: "account-security/device-activity" },
              { label: "linkedAccounts", path: "account-security/linked-accounts", isVisible: "isLinkedAccountsEnabled" },
            ],
          },
          { label: "applications", path: "applications" },
          { label: "groups", path: "groups", isVisible: "isViewGroupsEnabled" },
          { label: "organizations", path: "organizations", isVisible: "isViewOrganizationsEnabled" },
          { label: "resources", path: "resources", isVisible: "isMyResourcesEnabled" },
          { label: "oid4vci", path: "oid4vci", isVisible: "isOid4VciEnabled" },
          { label: "personalAccessTokens", path: "personal-access-tokens" },
        ];

        // These translations are expected by @keycloak/keycloak-ui-shared but are missing from
        // the "base" parent theme. The "keycloak.v3" theme has them as interpolated templates,
        // but our theme inherits from "base". We write them into messages_en.properties so
        // Keycloak includes them in the /resources/{realm}/account/en translation endpoint.
        const messagesEn = [
          // Interpolated name: e.g. "Alice Test" instead of static "Full name".
          // i18next uses {{key}} interpolation, not Java MessageFormat {0} positional args.
          `fullName={{givenName}} {{familyName}}`,
          // Sidebar nav labels
          `personalInfo=Personal Info`,
          `accountSecurity=Account Security`,
          `signingIn=Signing In`,
          `deviceActivity=Device Activity`,
          `linkedAccounts=Linked Accounts`,
          `groups=Groups`,
          `organizations=Organizations`,
          `resources=Resources`,
          `oid4vci=OID4VCI`,
          `personalAccessTokens=Personal Access Tokens`,
          // Masthead user dropdown
          `signOut=Sign out`,
          `manageAccount=Manage account`,
          `unknownUser=Unknown user`,
          // Page descriptions (optional but improves UX)
          `personalInfoDescription=Manage your basic information`,
          `accountSecurityDescription=Control your password and account access`,
          `signingInDescription=Manage your signing in credentials`,
          `deviceActivityDescription=Manage your active sessions`,
        ].join("\n");

        const resourcesDirPath = join(buildContext.keycloakifyBuildDirPath, "resources");
        for (const themeName of buildContext.themeNames) {
          const themeAccountDir = join(resourcesDirPath, "theme", themeName, "account");
          const themeAccountResourcesDir = join(themeAccountDir, "resources");

          // Write content.json
          if (existsSync(themeAccountResourcesDir)) {
            writeFileSync(
              join(themeAccountResourcesDir, "content.json"),
              JSON.stringify(contentJson, null, 2),
              "utf8",
            );
          }

          // Write messages_en.properties override
          const messagesDir = join(themeAccountDir, "messages");
          mkdirSync(messagesDir, { recursive: true });
          writeFileSync(join(messagesDir, "messages_en.properties"), messagesEn, "utf8");

          // Override theme.properties to inherit from keycloak.v3 instead of base.
          // keycloak.v3 provides the full set of standard account UI translations, so our
          // messages_en.properties only needs to add/override what keycloak.v3 doesn't provide.
          const themePropertiesPath = join(themeAccountDir, "theme.properties");
          if (existsSync(themePropertiesPath)) {
            const { readFileSync } = await import("node:fs");
            const existing = readFileSync(themePropertiesPath, "utf8");
            writeFileSync(
              themePropertiesPath,
              existing.replace("parent=base", "parent=keycloak.v3"),
              "utf8",
            );
          }
        }
      },
    }),
  ],
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
    globals: true,
  },
});
