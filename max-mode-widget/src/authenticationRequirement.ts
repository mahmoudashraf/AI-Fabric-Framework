export const WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA =
  "loomai-workspace-authentication-requirement-v1" as const;

export interface WorkspaceAuthenticationRequirement {
  schemaVersion: typeof WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA;
  reasonCode: "AUTHENTICATION_REQUIRED";
  retryPolicy: "EXPLICIT_NEW_REQUEST";
  authenticationProfile?: string;
  capability?: string;
  message?: string;
}

function record(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function text(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  return trimmed || undefined;
}

function readPath(value: unknown, ...path: string[]): unknown {
  let current: unknown = value;
  for (const segment of path) {
    if (Array.isArray(current) && /^\d+$/.test(segment)) {
      current = current[Number(segment)];
      continue;
    }
    const currentRecord = record(current);
    if (!currentRecord) return undefined;
    current = currentRecord[segment];
  }
  return current;
}

function structuredRequirement(payload: unknown): WorkspaceAuthenticationRequirement | undefined {
  const candidates = [
    readPath(payload, "authenticationRequirement"),
    readPath(payload, "result", "authenticationRequirement"),
    readPath(payload, "result", "data", "authenticationRequirement"),
    readPath(payload, "metadata", "authenticationRequirement"),
  ];
  for (const value of candidates) {
    const candidate = record(value);
    if (!candidate
      || candidate.schemaVersion !== WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA
      || String(candidate.reasonCode ?? "").trim().toUpperCase() !== "AUTHENTICATION_REQUIRED") {
      continue;
    }
    const retryPolicy = String(candidate.retryPolicy ?? "EXPLICIT_NEW_REQUEST").trim().toUpperCase();
    if (retryPolicy !== "EXPLICIT_NEW_REQUEST") continue;
    return {
      schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
      reasonCode: "AUTHENTICATION_REQUIRED",
      retryPolicy: "EXPLICIT_NEW_REQUEST",
      authenticationProfile: text(candidate.authenticationProfile),
      capability: text(candidate.capability),
      message: text(candidate.message),
    };
  }
  return undefined;
}

/**
 * Reads only typed response fields. User-facing prose is intentionally ignored:
 * a generic denial or a model saying "sign in" must never expand access.
 */
export function extractWorkspaceAuthenticationRequirement(
  payload: unknown,
): WorkspaceAuthenticationRequirement | undefined {
  return structuredRequirement(payload);
}
