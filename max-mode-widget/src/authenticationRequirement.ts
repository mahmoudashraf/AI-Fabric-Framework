import type { MaxModeHostAuthenticationConfig } from "./config";

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

function structuredRequirement(payload: unknown): WorkspaceAuthenticationRequirement | undefined {
  const candidate = record(record(payload)?.authenticationRequirement);
  if (!candidate
    || candidate.schemaVersion !== WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA
    || candidate.reasonCode !== "AUTHENTICATION_REQUIRED"
    || candidate.retryPolicy !== "EXPLICIT_NEW_REQUEST") return undefined;
  return {
    schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
    reasonCode: "AUTHENTICATION_REQUIRED",
    retryPolicy: "EXPLICIT_NEW_REQUEST",
    authenticationProfile: text(candidate.authenticationProfile),
    capability: text(candidate.capability),
    message: text(candidate.message),
  };
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

export async function dispatchWorkspaceAuthenticationRequirement(
  requirement: WorkspaceAuthenticationRequirement,
  authentication: MaxModeHostAuthenticationConfig | undefined,
  conversationId: string | undefined,
  emit: (event: "authentication:required" | "error", data: Record<string, unknown>) => void,
): Promise<void> {
  emit("authentication:required", { ...requirement, conversationId });
  if (authentication?.interactionPolicy !== "public-with-protected-capabilities"
    || authentication.status !== "anonymous" || !authentication.signInAvailable || !authentication.onSignIn) return;
  try {
    await authentication.onSignIn();
  } catch {
    emit("error", { source: "authentication-challenge", message: "Sign in could not be started." });
  }
}
