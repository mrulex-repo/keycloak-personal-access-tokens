import { lazy } from "react";
import type { RouteObject } from "react-router-dom";

const PersonalAccessTokens = lazy(
  () => import("./PersonalAccessTokens"),
);

export const personalAccessTokensRoute: RouteObject = {
  path: "personal-access-tokens",
  element: <PersonalAccessTokens />,
};
