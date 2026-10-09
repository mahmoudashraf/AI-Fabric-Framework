import type {
  AIWorkspaceBrowserApi,
  AIWorkspaceExperiencePackRegistration,
  AIWorkspaceInstallationManifest,
  WorkspaceConnection,
  WorkspaceRuntimeRoutes,
} from "./types";

const INSTALLER_VERSION = "1.0.0";
const MANIFEST_SCHEMA = "loomai-ai-workspace-installation-v1";
const INSTALLATION_PATTERN = /^awi_pub_[a-f0-9]{32}$/;
const SCRIPT_TIMEOUT_MS = 15_000;
const INSTALLER_STATE_KEY = "__LOOMAI_AI_WORKSPACE_INSTALLER_STATE__";

interface InstallerState {
  registrations: Map<string, AIWorkspaceExperiencePackRegistration>;
  activeInstallationId: string | null;
  activeController: unknown;
  activeBootstrap: (() => Promise<void>) | null;
  initialization: Promise<void> | null;
}

const state = sharedInstallerState();

const browserApi: AIWorkspaceBrowserApi = {
  version: INSTALLER_VERSION,
  registerExperiencePack(registration) {
    validateRegistration(registration);
    const key = packKey(registration.code, registration.version);
    const existing = state.registrations.get(key);
    if (existing && existing.mount !== registration.mount) {
      throw new Error(`AI Workspace experience pack ${key} was registered more than once.`);
    }
    state.registrations.set(key, registration);
  },
  getController() {
    return state.activeController;
  },
  async refresh() {
    if (!state.activeBootstrap) throw new Error("AI Workspace has not been initialized.");
    await state.activeBootstrap();
  },
};

declare global {
  interface Window {
    LoomAIWorkspace?: AIWorkspaceBrowserApi;
    MaxMode?: any;
  }
}

if (!window.LoomAIWorkspace) window.LoomAIWorkspace = browserApi;

const owningScript = document.currentScript as HTMLScriptElement | null;
void initializeFromScript(owningScript).catch((error) => {
  emit("error", {
    code: "INSTALLATION_FAILED",
    message: error instanceof Error ? error.message : "AI Workspace could not be installed.",
  });
});

async function initializeFromScript(script: HTMLScriptElement | null) {
  if (!script?.src) throw new Error("AI Workspace installer must be loaded from a script element.");
  const installationId = script.dataset.installationId?.trim() || "";
  if (!INSTALLATION_PATTERN.test(installationId)) throw new Error("A valid data-installation-id is required.");
  if (state.activeInstallationId && state.activeInstallationId !== installationId) {
    throw new Error("Only one AI Workspace installation can be mounted on a page.");
  }
  if (state.activeInstallationId === installationId) {
    if (state.initialization) await state.initialization;
    return;
  }
  state.activeInstallationId = installationId;
  const platformBaseUrl = new URL(script.src).origin;
  const manifestUrl = `${platformBaseUrl}/api/public/ai-workspace/installations/${installationId}/manifest`;
  let mountedManifest: AIWorkspaceInstallationManifest | null = null;
  const refreshAssignment = async () => {
    const latest = await fetchManifest(manifestUrl, installationId, platformBaseUrl);
    if (!mountedManifest || latest.manifestRevision === mountedManifest.manifestRevision) return;
    emit("loading", {
      installationId,
      reason: "assignment-or-installation-changed",
      previousAssignmentRevision: mountedManifest.assignmentRevision,
      assignmentRevision: latest.assignmentRevision,
    });
    window.location.reload();
  };
  const bootstrap = async () => {
    emit("loading", { installationId });
    const manifest = await fetchManifest(manifestUrl, installationId, platformBaseUrl);
    await loadScript(manifest.workspace.asset);
    if (!window.MaxMode?.init) throw new Error("The generic AI Workspace asset did not register MaxMode.");
    await loadScript(manifest.workspace.experiencePack);
    const key = packKey(manifest.workspace.experiencePack.code, manifest.workspace.experiencePack.version);
    const registration = state.registrations.get(key);
    if (!registration) throw new Error(`Experience pack ${key} did not register itself.`);
    const widgetConfig = {
      ...await connectionWidgetConfig(manifest.connection, manifest),
      storageNamespace: [
        installationId,
        manifest.connection.mode,
        manifest.assignmentRevision,
      ].join(":"),
    };
    state.activeController = await registration.mount({
      installationId,
      manifestRevision: manifest.manifestRevision,
      assignmentRevision: manifest.assignmentRevision,
      connectionMode: manifest.connection.mode,
      configuration: manifest.workspace.configuration,
      widgetConfig,
      maxMode: window.MaxMode,
      refreshAssignment,
    });
    mountedManifest = manifest;
    emit("ready", {
      installationId,
      manifestRevision: manifest.manifestRevision,
      assignmentRevision: manifest.assignmentRevision,
      connectionMode: manifest.connection.mode,
    });
  };
  state.activeBootstrap = bootstrap;
  const initialization = bootstrap();
  state.initialization = initialization;
  try {
    await initialization;
  } catch (error) {
    state.activeInstallationId = null;
    state.activeBootstrap = null;
    state.activeController = undefined;
    throw error;
  } finally {
    if (state.initialization === initialization) state.initialization = null;
  }
}

async function fetchManifest(url: string, expectedInstallationId: string, platformBaseUrl: string) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 10_000);
  try {
    const response = await fetch(url, {
      method: "GET",
      mode: "cors",
      credentials: "omit",
      cache: "no-cache",
      headers: { Accept: "application/json" },
      signal: controller.signal,
    });
    if (!response.ok) throw new Error(`Installation manifest returned HTTP ${response.status}.`);
    const manifest = await response.json() as AIWorkspaceInstallationManifest;
    validateManifest(manifest, expectedInstallationId, platformBaseUrl);
    return manifest;
  } finally {
    window.clearTimeout(timeout);
  }
}

async function connectionWidgetConfig(
  connection: WorkspaceConnection,
  manifest: AIWorkspaceInstallationManifest,
) {
  if (connection.mode === "public-runtime-anonymous") {
    return {
      integrationMode: connection.mode,
      apiConfig: directRuntimeApiConfig(connection.runtimeBaseUrl, connection.routes, {
        authorizationHeader: connection.anonymousBootstrap.authorizationHeader,
        tokenScheme: connection.anonymousBootstrap.tokenScheme,
        bootstrapUrl: absoluteUrl(connection.runtimeBaseUrl, connection.anonymousBootstrap.url),
        renewUrl: absoluteUrl(connection.runtimeBaseUrl, connection.anonymousBootstrap.renewUrl),
        authContextUrl: absoluteUrl(connection.runtimeBaseUrl, connection.routes.authContextUrl),
        probeAuthContextOnOpen: true,
      }),
    };
  }
  if (connection.mode === "public-runtime-authenticated") {
    const tokenSupplier = credentialBrokerTokenSupplier(connection, manifest);
    return {
      integrationMode: connection.mode,
      apiConfig: directRuntimeApiConfig(connection.runtimeBaseUrl, connection.routes, {
        authorizationHeader: "Authorization",
        tokenScheme: "Bearer",
        authContextUrl: absoluteUrl(connection.runtimeBaseUrl, connection.routes.authContextUrl),
        probeAuthContextOnOpen: true,
        getBearerToken: tokenSupplier,
      }),
    };
  }
  const adapterConfig = await bootstrapPrivateAdapter(connection, manifest);
  return {
    integrationMode: connection.mode,
    apiConfig: adapterConfig,
  };
}

function directRuntimeApiConfig(runtimeBaseUrl: string, routes: WorkspaceRuntimeRoutes, runtimeAuth: Record<string, unknown>) {
  const base = normalizeHttpsUrl(runtimeBaseUrl, "runtimeBaseUrl");
  return {
    chatBaseUrl: base,
    runtimeRoutes: {
      chatQueryUrl: absoluteUrl(base, routes.queryUrl),
      suggestionsUrl: absoluteUrl(base, routes.suggestionsUrl),
      authContextUrl: absoluteUrl(base, routes.authContextUrl),
      shellConfigUrl: absoluteUrl(base, routes.shellConfigUrl),
      conversationsUrl: absoluteUrl(base, routes.conversationsUrl),
      conversationItemUrlTemplate: absoluteTemplateUrl(base, routes.conversationItemUrlTemplate),
    },
    runtimeAuth,
    probeShellConfigOnOpen: true,
  };
}

function credentialBrokerTokenSupplier(
  connection: Extract<WorkspaceConnection, { mode: "public-runtime-authenticated" }>,
  manifest: AIWorkspaceInstallationManifest,
) {
  let cached: { token: string; expiresAt: number; assignmentRevision: string } | null = null;
  let pending: Promise<string> | null = null;
  return async () => {
    const now = Date.now();
    if (cached && cached.expiresAt - now > 30_000 && cached.assignmentRevision === manifest.assignmentRevision) {
      return cached.token;
    }
    if (pending) return pending;
    pending = (async () => {
      const response = await fetch(normalizeHttpsUrl(connection.credentialBroker.url, "credential broker URL"), {
        method: "POST",
        credentials: connection.credentialBroker.credentials,
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({
          schemaVersion: "loomai-workspace-runtime-token-request-v1",
          installationId: manifest.installationId,
          assignmentRevision: manifest.assignmentRevision,
        }),
      });
      if (!response.ok) throw new Error(`Credential broker returned HTTP ${response.status}.`);
      const result = await response.json() as Record<string, unknown>;
      if (result.schemaVersion !== "loomai-workspace-runtime-token-v1"
        || typeof result.accessToken !== "string" || result.accessToken.length < 20
        || result.tokenType !== "Bearer" || typeof result.expiresAt !== "string"
        || result.assignmentRevision !== manifest.assignmentRevision) {
        throw new Error("Credential broker returned an invalid token contract.");
      }
      const expiresAt = Date.parse(result.expiresAt);
      if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
        throw new Error("Credential broker token is already expired.");
      }
      cached = { token: result.accessToken, expiresAt, assignmentRevision: result.assignmentRevision as string };
      return cached.token;
    })();
    try {
      return await pending;
    } finally {
      pending = null;
    }
  };
}

async function bootstrapPrivateAdapter(
  connection: Extract<WorkspaceConnection, { mode: "backend-mediated-private-runtime" }>,
  manifest: AIWorkspaceInstallationManifest,
) {
  const response = await fetch(normalizeHttpsUrl(connection.adapter.bootstrapUrl, "adapter bootstrap URL"), {
    method: "POST",
    credentials: connection.adapter.credentials,
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({
      schemaVersion: "loomai-workspace-private-adapter-request-v1",
      installationId: manifest.installationId,
      assignmentRevision: manifest.assignmentRevision,
    }),
  });
  if (!response.ok) throw new Error(`Private adapter returned HTTP ${response.status}.`);
  const result = await response.json() as Record<string, any>;
  if (result.schemaVersion !== "loomai-workspace-private-adapter-v1") {
    throw new Error("Private adapter returned an unsupported contract.");
  }
  const chatBaseUrl = normalizeHttpsUrl(result.chatBaseUrl, "adapter chatBaseUrl");
  const routes = validateRoutes(result.routes);
  const defaultHeaders = validateEphemeralHeaders(result.defaultHeaders);
  const conversationsEnabled = result.features?.conversations !== false;
  return {
    chatBaseUrl,
    crudBaseUrl: typeof result.crudBaseUrl === "string" ? normalizeHttpsUrl(result.crudBaseUrl, "adapter crudBaseUrl") : undefined,
    defaultHeaders,
    fetchCredentials: result.fetchCredentials === "include" ? "include" : "omit",
    runtimeRoutes: {
      chatQueryUrl: absoluteUrl(chatBaseUrl, routes.queryUrl),
      suggestionsUrl: absoluteUrl(chatBaseUrl, routes.suggestionsUrl),
      authContextUrl: absoluteUrl(chatBaseUrl, routes.authContextUrl),
      shellConfigUrl: absoluteUrl(chatBaseUrl, routes.shellConfigUrl),
      conversationsUrl: absoluteUrl(chatBaseUrl, routes.conversationsUrl),
      conversationItemUrlTemplate: absoluteTemplateUrl(chatBaseUrl, routes.conversationItemUrlTemplate),
    },
    features: { conversations: conversationsEnabled },
    probeShellConfigOnOpen: result.probeShellConfigOnOpen === true,
    runtimeAuth: { probeAuthContextOnOpen: result.probeAuthContextOnOpen === true },
  };
}

function validateManifest(value: AIWorkspaceInstallationManifest, installationId: string, platformBaseUrl: string) {
  if (!value || value.schemaVersion !== MANIFEST_SCHEMA || value.installationId !== installationId
    || !/^sha256:[a-f0-9]{64}$/.test(value.manifestRevision)
    || !/^sha256:[a-f0-9]{64}$/.test(value.assignmentRevision)) {
    throw new Error("AI Workspace installation manifest is invalid.");
  }
  validateAsset(value.workspace?.asset, false, platformBaseUrl);
  validateAsset(value.workspace?.experiencePack, true, platformBaseUrl);
  if (!value.workspace.configuration || typeof value.workspace.configuration !== "object") {
    throw new Error("AI Workspace configuration is invalid.");
  }
  validateConnection(value.connection);
}

function validateConnection(connection: WorkspaceConnection) {
  if (!connection || typeof connection !== "object") throw new Error("AI Workspace connection is missing.");
  if (connection.mode === "public-runtime-anonymous") {
    if (connection.handler !== "direct-public-runtime") throw new Error("Anonymous connection handler is invalid.");
    normalizeHttpsUrl(connection.runtimeBaseUrl, "runtimeBaseUrl");
    validateRoutes(connection.routes);
    if (!connection.anonymousBootstrap?.url || !connection.anonymousBootstrap?.renewUrl) {
      throw new Error("Anonymous bootstrap contract is invalid.");
    }
  } else if (connection.mode === "public-runtime-authenticated") {
    if (connection.handler !== "brokered-public-runtime") throw new Error("Authenticated connection handler is invalid.");
    normalizeHttpsUrl(connection.runtimeBaseUrl, "runtimeBaseUrl");
    validateRoutes(connection.routes);
    if (connection.credentialBroker?.responseSchemaVersion !== "loomai-workspace-runtime-token-v1") {
      throw new Error("Credential broker contract is invalid.");
    }
    normalizeHttpsUrl(connection.credentialBroker.url, "credential broker URL");
  } else if (connection.mode === "backend-mediated-private-runtime") {
    if (connection.handler !== "private-backend-adapter"
      || connection.adapter?.responseSchemaVersion !== "loomai-workspace-private-adapter-v1") {
      throw new Error("Private adapter contract is invalid.");
    }
    normalizeHttpsUrl(connection.adapter.bootstrapUrl, "adapter bootstrap URL");
  } else {
    throw new Error("AI Workspace connection mode is unsupported.");
  }
}

function validateAsset(asset: any, requireCode: boolean, platformBaseUrl: string) {
  if (!asset || typeof asset.version !== "string" || !/^\d+\.\d+\.\d+$/.test(asset.version)
    || typeof asset.url !== "string" || typeof asset.integrity !== "string"
    || !/^sha384-[A-Za-z0-9+/=]+$/.test(asset.integrity)
    || (requireCode && !/^[a-z0-9-]+$/.test(asset.code || ""))) {
    throw new Error("AI Workspace asset reference is invalid.");
  }
  const assetUrl = new URL(normalizeHttpsUrl(asset.url, "asset URL"));
  if (assetUrl.origin !== new URL(platformBaseUrl).origin) {
    throw new Error("AI Workspace executable assets must be served by the installation Platform.");
  }
}

function validateRoutes(value: any): WorkspaceRuntimeRoutes {
  const keys = ["queryUrl", "suggestionsUrl", "authContextUrl", "shellConfigUrl", "conversationsUrl", "conversationItemUrlTemplate"] as const;
  if (!value || keys.some((key) => typeof value[key] !== "string" || !value[key].startsWith("/api/"))) {
    throw new Error("AI Workspace runtime routes are invalid.");
  }
  return value as WorkspaceRuntimeRoutes;
}

function validateEphemeralHeaders(value: unknown) {
  if (value == null) return undefined;
  if (typeof value !== "object" || Array.isArray(value)) throw new Error("Private adapter headers are invalid.");
  const entries = Object.entries(value as Record<string, unknown>);
  if (entries.length > 4) throw new Error("Private adapter returned too many browser headers.");
  const result: Record<string, string> = {};
  for (const [name, headerValue] of entries) {
    if (!/^X-[A-Za-z0-9-]{1,64}$/.test(name) || typeof headerValue !== "string" || headerValue.length > 512) {
      throw new Error("Private adapter returned an invalid browser header.");
    }
    result[name] = headerValue;
  }
  return result;
}

async function loadScript(asset: { url: string; integrity: string }) {
  const url = normalizeHttpsUrl(asset.url, "asset URL");
  const existing = document.querySelector<HTMLScriptElement>(`script[data-loomai-workspace-asset="${cssEscape(url)}"]`);
  if (existing) {
    if (existing.dataset.loaded === "true") return;
    await waitForScript(existing);
    return;
  }
  const element = document.createElement("script");
  element.src = url;
  element.async = true;
  element.integrity = asset.integrity;
  element.crossOrigin = "anonymous";
  element.dataset.loomaiWorkspaceAsset = url;
  document.head.appendChild(element);
  await waitForScript(element);
  element.dataset.loaded = "true";
}

function waitForScript(script: HTMLScriptElement) {
  return new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(() => reject(new Error("AI Workspace asset loading timed out.")), SCRIPT_TIMEOUT_MS);
    script.addEventListener("load", () => { window.clearTimeout(timeout); resolve(); }, { once: true });
    script.addEventListener("error", () => { window.clearTimeout(timeout); reject(new Error("AI Workspace asset failed integrity or network validation.")); }, { once: true });
  });
}

function normalizeHttpsUrl(value: unknown, label: string) {
  if (typeof value !== "string") throw new Error(`${label} is invalid.`);
  const url = new URL(value, document.baseURI);
  const local = ["localhost", "127.0.0.1", "::1"].includes(url.hostname);
  if (url.protocol !== "https:" && !(local && url.protocol === "http:")) throw new Error(`${label} must use HTTPS.`);
  return url.toString().replace(/\/$/, "");
}

function absoluteUrl(base: string, path: string) {
  return new URL(path, `${base}/`).toString();
}

function absoluteTemplateUrl(base: string, path: string) {
  const placeholder = "__LOOMAI_CONVERSATION_ID__";
  return new URL(path.replace("{conversationId}", placeholder), `${base}/`).toString().replace(placeholder, "{conversationId}");
}

function validateRegistration(registration: AIWorkspaceExperiencePackRegistration) {
  if (!registration || !/^[a-z0-9-]+$/.test(registration.code)
    || !/^\d+\.\d+\.\d+$/.test(registration.version) || typeof registration.mount !== "function") {
    throw new Error("AI Workspace experience-pack registration is invalid.");
  }
}

function packKey(code: string, version: string) {
  return `${code}@${version}`;
}

function cssEscape(value: string) {
  return value.replace(/["\\]/g, "\\$&");
}

function emit(type: "loading" | "ready" | "error", detail: Record<string, unknown>) {
  window.dispatchEvent(new CustomEvent(`loomai:workspace-${type}`, {
    detail: { ...detail, timestamp: new Date().toISOString() },
  }));
}

function sharedInstallerState(): InstallerState {
  const target = window as unknown as Record<string, unknown>;
  const existing = target[INSTALLER_STATE_KEY] as InstallerState | undefined;
  if (existing) return existing;
  const created: InstallerState = {
    registrations: new Map(),
    activeInstallationId: null,
    activeController: undefined,
    activeBootstrap: null,
    initialization: null,
  };
  Object.defineProperty(target, INSTALLER_STATE_KEY, {
    value: created,
    configurable: false,
    enumerable: false,
    writable: false,
  });
  return created;
}
