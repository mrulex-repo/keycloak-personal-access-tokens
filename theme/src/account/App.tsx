import { lazy } from "react";
import {
  KcAccountUiLoader,
  type KcContextLike,
} from "@keycloakify/keycloak-account-ui";

declare global {
  interface Window {
    kcContext?: KcContextLike;
  }
}

const KcAccountUi = lazy(() => import("./KcAccountUI"));

export function App() {
  return (
    <KcAccountUiLoader
      kcContext={window.kcContext!}
      KcAccountUi={KcAccountUi}
    />
  );
}
