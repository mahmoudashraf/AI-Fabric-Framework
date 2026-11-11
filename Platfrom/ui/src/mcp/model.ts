import type { McpConnection, McpConnectionInput, McpDiscovery, McpToolPolicy, McpTransport } from './types'

export const defaultToolPolicy = (): McpToolPolicy => ({ accessMode: 'READ_WRITE', requiresConfirmation: true, allowAnonymous: false, readGrounding: false, enabled: true })

export const emptyConnection = (): McpConnectionInput => ({ name: '', endpointUrl: '', connectionModel: 'PUBLIC', transport: 'AUTO', auth: { mode: 'NONE' } })

export const connectionEnabledInTarget = (connection: McpConnection, targetProfileId: string) => connection.allowedTargetProfileIds?.includes(targetProfileId) === true

export function httpsEndpointError(value: string): string | null {
  if (!value.trim()) return 'Enter the HTTPS address supplied by the service.'
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' || url.username || url.password || url.hash) return 'Use an HTTPS address without embedded credentials or a fragment.'
    return null
  } catch { return 'Enter a complete HTTPS address, for example https://service.example.com/mcp.' }
}

export function connectionError(input: McpConnectionInput): string | null {
  if (!input.name.trim()) return 'Give this connection a name.'
  const endpoint = httpsEndpointError(input.endpointUrl)
  if (endpoint) return endpoint
  if (input.connectionModel === 'PUBLIC') return null
  const auth = input.auth
  if (auth.mode === 'NONE') return 'Choose how your company authorizes this service.'
  if ((auth.mode === 'BEARER_TOKEN_SECRET_REF' || auth.mode === 'API_KEY_HEADER_SECRET') && !auth.secretRef) return 'Select a saved key reference.'
  if (auth.mode === 'API_KEY_HEADER_SECRET' && !validHeader(auth.headerName ?? '')) return 'Enter an allowed API-key header name, for example X-API-Key.'
  if (auth.mode === 'MULTIPLE_HEADERS') {
    if (!auth.headers?.length || auth.headers.some(header => !validHeader(header.name) || !header.secretRef)) return 'Each header needs an allowed name and a saved key reference.'
    if (new Set(auth.headers.map(header => header.name.toLowerCase())).size !== auth.headers.length) return 'Use each header name once.'
  }
  if (auth.mode === 'OAUTH2_CLIENT_CREDENTIALS') {
    if (!auth.oauth?.clientId?.trim() || !auth.oauth.clientSecretRef) return 'Enter the service client ID and select its saved client secret.'
    if (!auth.oauth.authorizationServerUrl?.trim()) return 'Enter the HTTPS authorization server URL for the service credentials.'
  }
  if (auth.mode === 'OAUTH_AUTHORIZATION_CODE' || auth.mode === 'OAUTH2_CLIENT_CREDENTIALS') {
    if (auth.oauth?.authorizationServerUrl && httpsEndpointError(auth.oauth.authorizationServerUrl)) return 'Use an HTTPS authorization-server address without embedded credentials.'
    if (auth.oauth?.resourceUrl && httpsEndpointError(auth.oauth.resourceUrl)) return 'Use an HTTPS resource address without embedded credentials.'
    if (auth.oauth?.registrationMode === 'PREREGISTERED' && !auth.oauth.clientId?.trim()) return 'Enter the client ID registered with this provider.'
    if (auth.oauth?.clientAuthenticationMethod && auth.oauth.clientAuthenticationMethod !== 'none' && !auth.oauth.clientSecretRef) return 'Select the saved client secret required by this provider.'
  }
  return null
}

// This protects routing/protocol headers, not a semantic classification of tools.
export function validHeader(value: string): boolean {
  return /^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(value)
    && !['host', 'cookie', 'set-cookie', 'connection', 'content-length', 'content-type', 'accept', 'transfer-encoding', 'proxy-authorization', 'origin', 'referer', 'mcp-session-id', 'mcp-protocol-version'].includes(value.toLowerCase())
}

export function connectionPayload(input: McpConnectionInput): McpConnectionInput {
  return { name: input.name.trim(), endpointUrl: input.endpointUrl.trim(), connectionModel: input.connectionModel, transport: input.transport,
    auth: input.connectionModel === 'PUBLIC' ? { mode: 'NONE' } : input.auth }
}

export function updateToolPolicy(policy: Partial<McpToolPolicy>, update: Partial<McpToolPolicy>): Partial<McpToolPolicy> {
  const next = { ...policy, ...update }
  if (update.accessMode && update.accessMode !== 'READ') next.readGrounding = false
  return next
}

export function existingToolPolicies(actionsConfig: unknown, connectionId: string): Record<string, McpToolPolicy> {
  const object = (value: unknown): Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
  const actions = object(actionsConfig).actions
  const result: Record<string, McpToolPolicy> = Object.create(null)
  for (const candidate of Array.isArray(actions) ? actions : []) {
    const action = object(candidate), mcp = object(object(action.execution).mcp)
    if (mcp.connectionId !== connectionId || typeof mcp.toolName !== 'string') continue
    const policy = defaultToolPolicy()
    if (action.accessMode === 'READ' || action.accessMode === 'READ_WRITE' || action.accessMode === 'WRITE_ONLY') policy.accessMode = action.accessMode
    if (typeof action.requiresConfirmation === 'boolean') policy.requiresConfirmation = action.requiresConfirmation
    if (typeof action.anonymousAllowed === 'boolean') policy.allowAnonymous = action.anonymousAllowed
    if (typeof action.groundingEligible === 'boolean') policy.readGrounding = action.groundingEligible
    if (typeof action.enabled === 'boolean') policy.enabled = action.enabled
    result[mcp.toolName] = policy
  }
  return result
}

export type McpToolChange = 'NEW' | 'SCHEMA_CHANGED' | 'UNCHANGED' | 'NEEDS_REVIEW'
export function compareMcpTools(snapshot: McpDiscovery, actionsConfig: unknown) {
  const object = (value: unknown): Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
  const actions = object(actionsConfig).actions
  const imported = new Map<string, Set<string>>()
  for (const candidate of Array.isArray(actions) ? actions : []) {
    const mcp = object(object(object(candidate).execution).mcp)
    if (mcp.connectionId !== snapshot.connectionId || typeof mcp.toolName !== 'string') continue
    const hashes = imported.get(mcp.toolName) ?? new Set<string>()
    hashes.add(typeof mcp.toolSchemaHash === 'string' ? mcp.toolSchemaHash : '')
    imported.set(mcp.toolName, hashes)
  }
  const states = new Map<string, McpToolChange>()
  for (const tool of snapshot.tools) {
    const hashes = imported.get(tool.name)
    states.set(tool.name, !hashes ? 'NEW' : !tool.schemaHash || hashes.size !== 1 || hashes.has('') ? 'NEEDS_REVIEW'
      : hashes.has(tool.schemaHash) ? 'UNCHANGED' : 'SCHEMA_CHANGED')
  }
  return { states, added: snapshot.tools.filter(tool => states.get(tool.name) === 'NEW').map(tool => tool.name),
    updated: snapshot.tools.filter(tool => states.get(tool.name) === 'SCHEMA_CHANGED').map(tool => tool.name),
    unchanged: snapshot.tools.filter(tool => states.get(tool.name) === 'UNCHANGED').map(tool => tool.name),
    unverified: snapshot.tools.filter(tool => states.get(tool.name) === 'NEEDS_REVIEW').map(tool => tool.name),
    notReturned: [...imported.keys()].filter(name => !states.has(name)) }
}

export const transportLabel: Record<McpTransport, string> = { AUTO: 'Automatic', STREAMABLE_HTTP: 'Streamable HTTP', SSE: 'HTTP + SSE' }

export function safeAuthorizationUrl(value: string): string {
  const url = new URL(value)
  if (url.protocol !== 'https:' || url.username || url.password) throw new Error('The service did not provide a safe HTTPS authorization address.')
  return url.href
}
