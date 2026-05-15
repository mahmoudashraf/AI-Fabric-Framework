import { getWidgetConfig } from "@/config";
import type { CustomerAccountConnectAction } from "@/types";

const CUSTOMER_ACCOUNT_AUTH_ERROR_CODES = new Set([
  "CUSTOMER_ACCOUNT_AUTH_REQUIRED",
  "INVALID_CUSTOMER_ACCOUNT_SESSION",
]);

function trimToNull(value: unknown): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function boolValue(value: unknown): boolean {
  return value === true || value === "true";
}

function readPath(value: any, ...path: string[]): unknown {
  let current = value;
  for (const segment of path) {
    if (!current || typeof current !== "object") {
      return undefined;
    }
    current = current[segment];
  }
  return current;
}

function firstText(...values: unknown[]): string | undefined {
  for (const value of values) {
    const text = trimToNull(value);
    if (text) {
      return text;
    }
  }
  return undefined;
}

function firstErrorCode(payload: any): string | undefined {
  const value = firstText(
    readPath(payload, "result", "sanitizedPayload", "data", "errorCode"),
    readPath(payload, "result", "sanitizedPayload", "data", "actionResult", "errorCode"),
    readPath(payload, "result", "data", "errorCode"),
    readPath(payload, "result", "data", "actionResult", "errorCode"),
    readPath(payload, "result", "sanitizedPayload", "errorCode"),
    readPath(payload, "result", "errorCode"),
    readPath(payload, "errorCode"),
  );
  return value?.toUpperCase();
}

export function extractChatResultMessage(payload: any, fallback: string): string {
  return firstText(
    readPath(payload, "result", "sanitizedPayload", "safeSummary"),
    readPath(payload, "result", "sanitizedPayload", "message"),
    readPath(payload, "result", "sanitizedPayload", "answer"),
    readPath(payload, "result", "message"),
    readPath(payload, "result", "data", "answer"),
    readPath(payload, "response"),
    readPath(payload, "message"),
    readPath(payload, "answer"),
  ) ?? fallback;
}

export function extractCustomerAccountConnectAction(payload: any): CustomerAccountConnectAction | undefined {
  const safeData = readPath(payload, "result", "sanitizedPayload", "data") as Record<string, any> | undefined;
  const resultData = readPath(payload, "result", "data") as Record<string, any> | undefined;
  const authMarker = safeData?.customerAccountAuth ?? resultData?.customerAccountAuth;
  const errorCode = firstErrorCode(payload);
  const required = boolValue(safeData?.customerAccountAuthRequired)
    || boolValue(resultData?.customerAccountAuthRequired)
    || boolValue(authMarker?.required)
    || (errorCode ? CUSTOMER_ACCOUNT_AUTH_ERROR_CODES.has(errorCode) : false);

  if (!required) {
    return undefined;
  }

  const hostAuth = getWidgetConfig().host?.customerAccountAuth;
  const startUrl = trimToNull(hostAuth?.startUrl);
  if (!startUrl) {
    return undefined;
  }

  return {
    startUrl,
    sessionUrl: trimToNull(hostAuth?.sessionUrl),
    shopperSessionId: trimToNull(hostAuth?.shopperSessionId),
    returnTo: trimToNull(hostAuth?.returnTo),
    label: "Connect store account",
    description: "Authorize the assistant to read your customer account securely, then ask again.",
  };
}

export function buildCustomerAccountConnectUrl(action: CustomerAccountConnectAction): string | null {
  const startUrl = trimToNull(action.startUrl);
  if (!startUrl) {
    return null;
  }
  try {
    const base = typeof window !== "undefined" ? window.location.href : "https://localhost/";
    const url = new URL(startUrl, base);
    const shopperSessionId = trimToNull(action.shopperSessionId);
    if (shopperSessionId && !url.searchParams.has("shopperSessionId")) {
      url.searchParams.set("shopperSessionId", shopperSessionId);
    }
    const returnTo = trimToNull(action.returnTo)
      ?? (typeof window !== "undefined" ? window.location.href : undefined);
    if (returnTo && !url.searchParams.has("returnTo")) {
      url.searchParams.set("returnTo", returnTo);
    }
    return url.toString();
  } catch {
    return null;
  }
}
