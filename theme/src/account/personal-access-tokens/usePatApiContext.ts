import {
  useEnvironment,
  type AccountEnvironment,
} from "@keycloak/keycloak-account-ui";
import type { PatApiContext } from "./pat-client";

export function usePatApiContext(): PatApiContext {
  const { environment, keycloak } = useEnvironment<AccountEnvironment>();
  return {
    authServerUrl: environment.serverBaseUrl,
    realm: environment.realm,
    token: keycloak.token ?? "",
  };
}
