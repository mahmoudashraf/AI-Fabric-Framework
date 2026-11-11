export type McpConnectionModel = 'PUBLIC' | 'BUSINESS'
export type McpAuthMode = 'NONE' | 'BEARER_TOKEN_SECRET_REF' | 'API_KEY_HEADER_SECRET' | 'MULTIPLE_HEADERS' | 'OAUTH2_CLIENT_CREDENTIALS' | 'OAUTH_AUTHORIZATION_CODE'
export type McpTransport = 'AUTO' | 'STREAMABLE_HTTP' | 'SSE'

export type McpOAuthConfig = {
  authorizationServerUrl?: string
  resourceUrl?: string
  registrationMode: 'AUTO' | 'PREREGISTERED' | 'CIMD' | 'DCR'
  clientId?: string
  clientSecretRef?: string
  clientAuthenticationMethod?: 'none' | 'client_secret_basic' | 'client_secret_post'
  scopes: string[]
}

export type McpAuthConfig = {
  mode: McpAuthMode
  secretRef?: string
  headerName?: string
  headers?: { name: string; secretRef: string }[]
  oauth?: McpOAuthConfig
}

export type McpConnectionInput = {
  name: string
  endpointUrl: string
  connectionModel: McpConnectionModel
  transport: McpTransport
  auth: McpAuthConfig
}

export type McpConnection = McpConnectionInput & {
  id: string
  deploymentId: string
  customerId: string
  serverRef: string
  targetProfileId: string
  allowedTargetProfileIds: string[]
  revision: number
  status: string
  authStatus: string
  tokenExpiresAt?: string | null
  lastDiscoveryId?: string | null
  createdAt: string
  updatedAt: string
}

export type McpConnectionList = { deploymentId: string; connections: McpConnection[] }

export type McpTool = {
  name: string
  title?: string | null
  description?: string | null
  inputSchema: unknown
  outputSchema?: unknown
  schemaHash: string
  compatible: boolean
  findings: string[]
  annotations?: Record<string, unknown>
  execution?: Record<string, unknown>
}

export type McpDiscovery = {
  id: string
  connectionId: string
  connectionRevision: number
  observedAt: string
  complete: boolean
  protocolVersion: string
  tools: McpTool[]
  message: string
}

export type McpToolPolicy = {
  accessMode: 'READ' | 'READ_WRITE' | 'WRITE_ONLY'
  requiresConfirmation: boolean
  allowAnonymous: boolean
  readGrounding: boolean
  enabled: boolean
}

export type McpImportInput = {
  snapshotId: string
  connectionRevision: number
  tools: (Partial<McpToolPolicy> & { toolName: string })[]
}

export type McpActionChange = { actionName: string; toolName: string; changeType: 'CREATED' | 'UPDATED'; before: unknown; after: unknown }

export type McpImportReceipt = {
  id: string
  connectionId: string
  snapshotId: string
  draftId: string
  draftRevision: number
  createdCount: number
  updatedCount: number
  actionNames: string[]
  createdAt: string
  changes?: McpActionChange[]
}

export type McpUndoReceipt = { importReceiptId: string; draftId: string; draftRevision: number; restoredAt: string }

export type McpAuthorization = {
  authorizationUrl: string
  expiresAt: string
  connectionId: string
}
