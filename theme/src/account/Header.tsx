/**
 * Local re-implementation of the Keycloak account UI Header component.
 *
 * @keycloak/keycloak-account-ui bundles @patternfly/react-core inside its own
 * bundle, creating a separate PageContext instance from our app's @patternfly/react-core.
 * Its Header component uses PageToggleButton from the bundled PF, which reads/writes
 * a different PageContext than the one provided by our Page component. This makes
 * the sidebar toggle button non-functional.
 *
 * @keycloak/keycloak-ui-shared imports @patternfly/react-core as an external ESM
 * dependency (not bundled), so KeycloakMasthead from keycloak-ui-shared uses the
 * same PageContext as our Page component. Sidebar toggling works correctly.
 */

import { KeycloakMasthead } from "@keycloak/keycloak-ui-shared";
import { useEnvironment } from "@keycloak/keycloak-account-ui";
import type { AccountEnvironment } from "@keycloak/keycloak-account-ui";

export function Header() {
  const { environment, keycloak } = useEnvironment<AccountEnvironment>();
  const logoSrc = `${environment.resourceUrl}/${environment.logo ?? "logo.svg"}`;

  return (
    <KeycloakMasthead
      data-testid="page-header"
      keycloak={keycloak}
      brand={{ src: logoSrc, alt: "Logo", href: environment.logoUrl ?? "/" }}
      features={{ hasLogout: true, hasManageAccount: true, hasUsername: true }}
    />
  );
}
