import { getWidgetConfig } from "@/config";
import type { ChatResult, CustomerAccountConnectAction, ResultType } from "@/types";

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
    if (Array.isArray(current) && /^\d+$/.test(segment)) {
      current = current[Number(segment)];
    } else {
      current = current[segment];
    }
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
    readPath(payload, "actions", "0", "errorCode"),
    readPath(payload, "actions", "0", "actionResult", "errorCode"),
    readPath(payload, "fallbackReason"),
    readPath(payload, "errorCode"),
  );
  return value?.toUpperCase();
}

export function extractChatResultMessage(payload: any, fallback: string): string {
  return firstText(
    readPath(payload, "safeSummary"),
    readPath(payload, "answer"),
    readPath(payload, "response"),
  ) ?? fallback;
}

export function canonicalChatResult(payload: any): ChatResult | undefined {
  if (!payload || typeof payload !== "object") {
    return undefined;
  }
  const resultType = (firstText(payload.type) || "INFORMATION_PROVIDED") as ResultType;
  const success = typeof payload.success === "boolean" ? payload.success : true;
  const answer = extractChatResultMessage(payload, success ? "I processed your query successfully." : "I could not process that request.");
  const sources = Array.isArray(payload.sources) ? payload.sources : [];
  const actions = Array.isArray(payload.actions) ? payload.actions : [];
  const data: Record<string, any> = {};
  if (answer) {
    data.answer = answer;
  }
  if (sources.length > 0) {
    data.documents = sources;
  }
  if (actions.length === 1 && actions[0] && typeof actions[0] === "object") {
    Object.assign(data, actions[0]);
  } else if (actions.length > 1) {
    data.actions = actions;
  }
  return {
    type: resultType,
    success,
    sanitizedPayload: {
      type: resultType,
      success,
      message: answer,
      safeSummary: answer,
      answer,
      errorCode: firstErrorCode(payload),
      data,
    },
  };
}

export function extractCustomerAccountConnectAction(payload: any): CustomerAccountConnectAction | undefined {
  const topAction = Array.isArray(payload?.actions)
    ? payload.actions.find((action: any) => action && typeof action === "object" && (
      action.customerAccountAuthRequired || action.customerAccountAuth || action.errorCode
    ))
    : undefined;
  const authMarker = topAction?.customerAccountAuth;
  const errorCode = firstErrorCode(payload);
  const required = boolValue(topAction?.customerAccountAuthRequired)
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
