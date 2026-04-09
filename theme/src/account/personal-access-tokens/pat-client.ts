import type { PatListItem, PatCreated, PatRole, PatCreateRequest } from "./types";

export type PatApiContext = {
  authServerUrl: string;
  realm: string;
  token: string;
};

function patApiUrl(ctx: PatApiContext, path = ""): string {
  return `${ctx.authServerUrl}/realms/${ctx.realm}/personal-access-token${path}`;
}

async function fetchJson<T>(
  ctx: PatApiContext,
  url: string,
  options?: RequestInit,
): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${ctx.token}`,
      "Content-Type": "application/json",
      ...options?.headers,
    },
  });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(body || `${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

export async function listPats(
  signal: AbortSignal,
  ctx: PatApiContext,
): Promise<PatListItem[]> {
  return fetchJson<PatListItem[]>(ctx, patApiUrl(ctx), { signal });
}

export async function createPat(
  ctx: PatApiContext,
  request: PatCreateRequest,
): Promise<PatCreated> {
  return fetchJson<PatCreated>(ctx, patApiUrl(ctx), {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export async function deletePat(ctx: PatApiContext, id: string): Promise<void> {
  const response = await fetch(patApiUrl(ctx), {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${ctx.token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ id }),
  });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(body || `${response.status} ${response.statusText}`);
  }
}

export async function listRoles(
  signal: AbortSignal,
  ctx: PatApiContext,
): Promise<PatRole[]> {
  return fetchJson<PatRole[]>(ctx, patApiUrl(ctx, "/roles"), { signal });
}
