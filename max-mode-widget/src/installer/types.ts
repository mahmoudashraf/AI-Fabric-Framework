import type { MaxModeAPI } from "@/entries/iife";

export type WorkspaceConnectionMode =
  | "public-runtime-anonymous"
  | "public-runtime-authenticated"
  | "backend-mediated-private-runtime";

export interface WorkspaceAssetReference {
  code?: string;
  version: string;
  url: string;
  integrity: string;
}

export interface WorkspaceRuntimeRoutes {
  queryUrl: string;
  suggestionsUrl: string;
  authContextUrl: string;
  shellConfigUrl: string;
  conversationsUrl: string;
  conversationItemUrlTemplate: string;
}

interface WorkspaceConnectionBase {
  mode: WorkspaceConnectionMode;
  profileCode: string;
  profileVersion: string;
  handler: "direct-public-runtime" | "brokered-public-runtime" | "private-backend-adapter";
}

export interface WorkspaceAnonymousConnection extends WorkspaceConnectionBase {
  mode: "public-runtime-anonymous";
  handler: "direct-public-runtime";
  runtimeBaseUrl: string;
  routes: WorkspaceRuntimeRoutes;
  anonymousBootstrap: {
    url: string;
    renewUrl: string;
    authorizationHeader: string;
    tokenScheme: string;
  };
}

export interface WorkspaceAuthenticatedConnection extends WorkspaceConnectionBase {
  mode: "public-runtime-authenticated";
  handler: "brokered-public-runtime";
  runtimeBaseUrl: string;
  routes: WorkspaceRuntimeRoutes;
  credentialBroker: {
    url: string;
    method: "POST";
    credentials: "include" | "same-origin" | "omit";
    responseSchemaVersion: "loomai-workspace-runtime-token-v1";
  };
}

export interface WorkspacePrivateConnection extends WorkspaceConnectionBase {
  mode: "backend-mediated-private-runtime";
  handler: "private-backend-adapter";
  adapter: {
    bootstrapUrl: string;
    method: "POST";
    credentials: "include" | "same-origin" | "omit";
    responseSchemaVersion: "loomai-workspace-private-adapter-v1";
  };
}

export type WorkspaceConnection =
  | WorkspaceAnonymousConnection
  | WorkspaceAuthenticatedConnection
  | WorkspacePrivateConnection;

export interface AIWorkspaceInstallationManifest {
  schemaVersion: "loomai-ai-workspace-installation-v1";
  installationId: string;
  manifestRevision: string;
  assignmentRevision: string;
  generatedAt: string;
  cacheTtlSeconds: number;
  connection: WorkspaceConnection;
  workspace: {
    asset: WorkspaceAssetReference;
    experiencePack: WorkspaceAssetReference & { code: string };
    configuration: Record<string, unknown>;
  };
}

export interface AIWorkspacePackMountContext {
  installationId: string;
  manifestRevision: string;
  assignmentRevision: string;
  connectionMode: WorkspaceConnectionMode;
  configuration: Record<string, unknown>;
  widgetConfig: Record<string, unknown> & { apiConfig: Record<string, unknown> };
  maxMode: MaxModeAPI;
  refreshAssignment: () => Promise<void>;
}

export interface AIWorkspaceExperiencePackRegistration {
  code: string;
  version: string;
  mount(context: AIWorkspacePackMountContext): Promise<unknown> | unknown;
}

export interface AIWorkspaceBrowserApi {
  version: string;
  registerExperiencePack(registration: AIWorkspaceExperiencePackRegistration): void;
  getController(): unknown;
  refresh(): Promise<void>;
}
