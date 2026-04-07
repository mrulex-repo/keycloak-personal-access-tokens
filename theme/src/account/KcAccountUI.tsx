import { Suspense } from "react";
import { Spinner } from "@patternfly/react-core";
import { AccountConsole } from "@keycloak/keycloak-account-ui";
import { personalAccessTokensRoute } from "./personal-access-tokens/route";

const extraRoutes = [personalAccessTokensRoute];

export function KcAccountUI() {
  return (
    <Suspense fallback={<Spinner />}>
      <AccountConsole extraRoutes={extraRoutes} />
    </Suspense>
  );
}
