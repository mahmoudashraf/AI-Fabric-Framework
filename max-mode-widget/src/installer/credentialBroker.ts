import type { AIWorkspaceHostCapabilities, AIWorkspaceInstallationManifest, WorkspaceCredentialBroker } from "./types";

export function credentialBrokerTokenSupplier(
  broker: WorkspaceCredentialBroker,
  manifest: AIWorkspaceInstallationManifest,
  required: boolean,
  host: () => AIWorkspaceHostCapabilities | undefined,
  authenticationRequired: () => void,
) {
  const mountedIdentity = host()?.identity;
  const boundSubject = mountedIdentity?.status === "authenticated" ? mountedIdentity.subjectKey : undefined;
  let cached: { token: string; expiresAt: number } | null = null;
  let pending: Promise<string> | null = null;
  function requireBoundIdentity() {
    const identity = host()?.identity;
    if (!boundSubject || identity?.status !== "authenticated" || identity.subjectKey !== boundSubject) {
      cached = null;
      throw new Error("Workspace identity changed; start a new conversation before connecting.");
    }
  }
  return async () => {
    const externalToken = await host()?.getIdentityAccessToken?.();
    if (!externalToken) {
      cached = null;
      if (required || boundSubject) {
        authenticationRequired();
        throw new Error("Authentication is required before this workspace can connect.");
      }
      return null;
    }
    requireBoundIdentity();
    if (cached && cached.expiresAt - Date.now() > 30_000) return cached.token;
    if (pending) return pending;
    pending = (async () => {
      const response = await fetch(broker.url, {
        method: "POST", credentials: broker.credentials,
        headers: { "Content-Type": "application/json", Accept: "application/json",
          [broker.externalAuthorizationHeader]: `Bearer ${externalToken}` },
        body: JSON.stringify({ schemaVersion: "loomai-workspace-runtime-token-request-v1" }),
      });
      if (!response.ok) throw new Error(`Credential broker returned HTTP ${response.status}.`);
      const result = await response.json() as Record<string, unknown>;
      requireBoundIdentity();
      if (result.schemaVersion !== "loomai-workspace-runtime-token-v1"
        || typeof result.accessToken !== "string" || result.accessToken.length < 20
        || result.tokenType !== "Bearer" || typeof result.expiresAt !== "string" || result.subjectId !== boundSubject) {
        throw new Error("Credential broker returned an invalid identity/token contract.");
      }
      const expiresAt = Date.parse(result.expiresAt);
      if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) throw new Error("Credential broker token is already expired.");
      cached = { token: result.accessToken, expiresAt };
      return cached.token;
    })();
    try { return await pending; }
    catch (error) { cached = null; throw error; }
    finally { pending = null; }
  };
}
