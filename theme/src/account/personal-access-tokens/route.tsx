import { lazy, createElement } from "react";
import type { RouteObject } from "react-router-dom";

export const personalAccessTokensRoute: RouteObject = {
  path: "personal-access-tokens",
  element: createElement(lazy(() => import("./PersonalAccessTokens"))),
};
