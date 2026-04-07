import { Suspense } from "react";
import { Spinner } from "@patternfly/react-core";
import { createBrowserRouter, RouterProvider, Outlet } from "react-router-dom";
import { getInjectedEnvironment } from "@keycloak/keycloak-ui-shared";
import {
  KeycloakProvider,
  routes,
  Header,
  PageNav,
  type AccountEnvironment,
} from "@keycloak/keycloak-account-ui";
import { personalAccessTokensRoute } from "./personal-access-tokens/route";

const environment = getInjectedEnvironment<AccountEnvironment>();

function AppLayout() {
  return (
    <>
      <Header />
      <PageNav />
      <main>
        <Outlet />
      </main>
    </>
  );
}

const router = createBrowserRouter(
  [{ element: <AppLayout />, children: [...routes, personalAccessTokensRoute] }],
  { basename: environment.baseUrl },
);

export function KcAccountUI() {
  return (
    <KeycloakProvider environment={environment}>
      <Suspense fallback={<Spinner />}>
        <RouterProvider router={router} />
      </Suspense>
    </KeycloakProvider>
  );
}
