/**
 * Local re-implementation of the Keycloak account UI PageNav component.
 *
 * @keycloak/keycloak-account-ui bundles @patternfly/react-core inside its own
 * bundle, creating a separate PageContext instance. This causes the PatternFly
 * Page component's isManagedSidebar toggle to never reach PageSidebar (different
 * context objects). By implementing PageNav here we use the same
 * @patternfly/react-core as the Page component so all contexts are shared.
 */

import { Suspense, useMemo, useState, type MouseEvent as ReactMouseEvent, type PropsWithChildren } from "react";
import {
  Nav,
  NavExpandable,
  NavItem,
  NavList,
  PageSidebar,
  PageSidebarBody,
  Spinner,
} from "@patternfly/react-core";
import { useTranslation } from "react-i18next";
import { matchPath, useHref, useLinkClickHandler, useLocation } from "react-router-dom";
import { useEnvironment, usePromise } from "@keycloak/keycloak-account-ui";
import type { AccountEnvironment } from "@keycloak/keycloak-account-ui";

type Feature = {
  isRegistrationEmailAsUsername: boolean;
  isEditUserNameAllowed: boolean;
  isLinkedAccountsEnabled: boolean;
  isMyResourcesEnabled: boolean;
  deleteAccountAllowed: boolean;
  updateEmailFeatureEnabled: boolean;
  updateEmailActionEnabled: boolean;
  isViewGroupsEnabled: boolean;
  isViewOrganizationsEnabled: boolean;
  isOid4VciEnabled: boolean;
};

type RootMenuItem = {
  id?: string;
  label: string;
  path: string;
  isVisible?: keyof Feature;
};

type MenuItemWithChildren = {
  label: string;
  children: MenuItem[];
  isVisible?: keyof Feature;
};

type MenuItem = RootMenuItem | MenuItemWithChildren;

async function fetchContentJson(resourceUrl: string, signal: AbortSignal): Promise<MenuItem[]> {
  const response = await fetch(`${resourceUrl}/content.json`, { signal });
  if (!response.ok) throw new Error(`Failed to fetch content.json: ${response.status}`);
  return response.json() as Promise<MenuItem[]>;
}

/**
 * Returns a path relative to the React Router root (without the basename prefix).
 * React Router's useHref and useLinkClickHandler expect paths without the basename;
 * they prepend it automatically. useLocation().pathname also strips the basename.
 * So we simply prepend "/" to the content.json path segment.
 */
function getFullUrl(_baseUrl: string, path: string): string {
  return `/${path}`;
}

function matchMenuItem(baseUrl: string, currentPath: string, menuItem: MenuItem): boolean {
  if ("path" in menuItem) {
    return !!matchPath(getFullUrl(baseUrl, menuItem.path), currentPath);
  }
  return menuItem.children.some((child) => matchMenuItem(baseUrl, currentPath, child));
}

type NavLinkProps = { path: string; baseUrl: string; isActive: boolean };

function NavLink({ path, baseUrl, isActive, children }: PropsWithChildren<NavLinkProps>) {
  const fullPath = getFullUrl(baseUrl, path) + location.search;
  const href = useHref(fullPath);
  const handleClick = useLinkClickHandler(fullPath);

  return (
    <NavItem
      data-testid={path || "personal-info"}
      to={href}
      isActive={isActive}
      onClick={(event) => handleClick(event as unknown as ReactMouseEvent<HTMLAnchorElement>)}
    >
      {children}
    </NavItem>
  );
}

type NavMenuItemProps = { menuItem: MenuItem; baseUrl: string; features: Feature };

function NavMenuItem({ menuItem, baseUrl, features }: NavMenuItemProps) {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const isActive = useMemo(
    () => matchMenuItem(baseUrl, pathname, menuItem),
    [baseUrl, pathname, menuItem],
  );

  if ("path" in menuItem) {
    return (
      <NavLink path={menuItem.path} baseUrl={baseUrl} isActive={isActive}>
        {t(menuItem.label)}
      </NavLink>
    );
  }

  return (
    <NavExpandable
      data-testid={menuItem.label}
      title={t(menuItem.label)}
      isActive={isActive}
      isExpanded={isActive}
    >
      {menuItem.children
        .filter((child) => ("isVisible" in child && child.isVisible ? features[child.isVisible] : true))
        .map((child) => (
          <NavMenuItem key={child.label} menuItem={child} baseUrl={baseUrl} features={features} />
        ))}
    </NavExpandable>
  );
}

export function PageNav() {
  const { environment } = useEnvironment<AccountEnvironment & { features: Feature }>();
  const [menuItems, setMenuItems] = useState<MenuItem[]>();

  usePromise(
    (signal) => fetchContentJson(environment.resourceUrl, signal),
    setMenuItems,
  );

  return (
    <PageSidebar>
      <PageSidebarBody>
        <Nav>
          <NavList>
            <Suspense fallback={<Spinner />}>
              {menuItems
                ?.filter((item) => ("isVisible" in item && item.isVisible ? environment.features[item.isVisible] : true))
                .map((item) => (
                  <NavMenuItem
                    key={item.label}
                    menuItem={item}
                    baseUrl={environment.baseUrl}
                    features={environment.features}
                  />
                ))}
            </Suspense>
          </NavList>
        </Nav>
      </PageSidebarBody>
    </PageSidebar>
  );
}
