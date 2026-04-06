import { getWidgetConfig } from "@/config";

async function readErrorBody(response: Response) {
  try {
    return await response.text();
  } catch {
    return "";
  }
}

function getApiHeaders(baseUrl?: string): Record<string, string> {
  const config = getWidgetConfig();
  const configHeaders = config.apiConfig.headers ?? {};
  // Chat API base URL gets the configured auth headers
  if (!baseUrl || baseUrl === config.apiConfig.chatBaseUrl) {
    return { ...configHeaders };
  }
  return {};
}

function mergeHeaders(init?: RequestInit, baseUrl?: string): Record<string, string> {
  const existing = (init?.headers as Record<string, string>) || {};
  const apiHeaders = getApiHeaders(baseUrl);
  return { ...apiHeaders, ...existing };
}

function getChatBaseUrl(): string {
  return getWidgetConfig().apiConfig.chatBaseUrl;
}

function getCrudBaseUrl(): string {
  return getWidgetConfig().apiConfig.crudBaseUrl;
}

export async function apiFetchJson<T>(
  path: string,
  init?: RequestInit,
  baseUrl?: string,
): Promise<T> {
  const base = baseUrl ?? getChatBaseUrl();
  const response = await fetch(`${base}${path}`, {
    ...init,
    headers: mergeHeaders(init, base),
  });
  if (!response.ok) {
    const body = await readErrorBody(response);
    throw new Error(
      `Request failed (${response.status}): ${body || response.statusText}`,
    );
  }
  return (await response.json()) as T;
}

export async function apiFetchOk(
  path: string,
  init?: RequestInit,
  baseUrl?: string,
): Promise<void> {
  const base = baseUrl ?? getChatBaseUrl();
  const response = await fetch(`${base}${path}`, {
    ...init,
    headers: mergeHeaders(init, base),
  });
  if (!response.ok) {
    const body = await readErrorBody(response);
    throw new Error(
      `Request failed (${response.status}): ${body || response.statusText}`,
    );
  }
}

export function apiFetchResponse(
  path: string,
  init?: RequestInit,
  baseUrl?: string,
) {
  const base = baseUrl ?? getChatBaseUrl();
  return fetch(`${base}${path}`, {
    ...init,
    headers: mergeHeaders(init, base),
  });
}

/** Get the CRUD API base URL from widget config */
export function getCrudApiBaseUrl(): string {
  return getCrudBaseUrl();
}
