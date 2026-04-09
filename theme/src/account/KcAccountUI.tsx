import { Suspense } from "react";
import { Page, Spinner } from "@patternfly/react-core";
import { createBrowserRouter, RouterProvider, Outlet } from "react-router-dom";
import { getInjectedEnvironment } from "@keycloak/keycloak-ui-shared";
import {
  KeycloakProvider,
  routes,
  type AccountEnvironment,
} from "@keycloak/keycloak-account-ui";
import { Header } from "./Header";
import { PageNav } from "./PageNav";
import { I18nextProvider } from "react-i18next";
import i18next from "i18next";
import I18NextFetchBackend from "i18next-fetch-backend";
import { initReactI18next } from "react-i18next";
import { personalAccessTokensRoute } from "./personal-access-tokens/route";

const environment = getInjectedEnvironment<AccountEnvironment>();

// @keycloak/keycloak-account-ui's HelpContextProvider reads localStorage("helpEnabled")
// and calls JSON.parse() on it without try/catch. If the value is "" (empty string,
// not null — so the ?? default doesn't apply), it throws "unexpected end of data".
// Clear it upfront so the component falls back to its default "true".
try {
  const stored = localStorage.getItem("helpEnabled");
  if (stored !== null) JSON.parse(stored);
} catch {
  localStorage.removeItem("helpEnabled");
}

// @keycloak/keycloak-account-ui creates its own i18next instance (pa) but never
// calls pa.init(), so setI18n() is never invoked and useTranslation() has no
// instance to read from. We create and initialize our own instance using the
// same Keycloak translations backend and provide it via I18nextProvider.
const i18n = i18next
  .createInstance()
  .use(I18NextFetchBackend)
  .use(initReactI18next);

i18n.init({
  fallbackLng: "en",
  lng: environment.locale,
  nsSeparator: false,
  interpolation: { escapeValue: false },
  backend: {
    loadPath: `${environment.serverBaseUrl}/resources/${environment.realm}/account/{{lng}}`,
    parse: (data: string) => {
      if (!data) return {};
      try {
        const messages = JSON.parse(data) as { key: string; value: string }[];
        return Object.fromEntries(messages.map(({ key, value }) => [key, value]));
      } catch {
        return {};
      }
    },
  },
});

// Simple error fallback rendered when a route's component throws (e.g. 401 from the
// Keycloak account REST API on pages like PersonalInfo).
function RouteError() {
  return (
    <p style={{ padding: "1rem", color: "var(--pf-v5-global--danger-color--100, red)" }}>
      This page could not be loaded.
    </p>
  );
}

function AppLayout() {
  return (
    <Page header={<Header />} sidebar={<PageNav />} isManagedSidebar>
      <Suspense fallback={<Spinner />}>
        <Outlet />
      </Suspense>
    </Page>
  );
}

// KcAccountUiLoader injects baseUrl as a full URL (e.g. "http://host/realms/x/account/").
// React Router basename must be a pathname only without trailing slash (e.g. "/realms/x/account").
// Trailing slash causes stripBasename to return null when pathname lacks it, breaking route matching.
const basename = new URL(environment.baseUrl, window.location.origin).pathname.replace(/\/$/, "");

// Standard routes from @keycloak/keycloak-account-ui (PersonalInfo, SigningIn, DeviceActivity,
// LinkedAccounts, Applications, Groups, Organizations, Resources, Oid4Vci, ContentComponent).
// Each is wrapped with an errorElement so a 401 from the Keycloak account REST API is contained
// per-route rather than crashing the whole application.
const standardRoutes = routes.map((r) => ({ ...r, errorElement: <RouteError /> }));

const router = createBrowserRouter(
  [
    {
      element: <AppLayout />,
      children: [
        ...standardRoutes,
        personalAccessTokensRoute,
      ],
    },
  ],
  { basename },
);

export default function KcAccountUI() {
  return (
    <I18nextProvider i18n={i18n}>
      <KeycloakProvider environment={environment}>
        <RouterProvider router={router} />
      </KeycloakProvider>
    </I18nextProvider>
  );
}
