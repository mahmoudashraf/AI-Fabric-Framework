export type GenericWorkspaceTool = {
  label: string
  query: string
  position?: 'search'
  mode?: 'executor'
  icon?: string
}

/** Structural subset of the widget's injectable custom-element renderer contract. */
export type GenericCapabilityCheckPresentationInput = {
  presentationData: Readonly<Record<string, unknown>>
}

export type GenericCapabilityCheckMapping = {
  actionName: string
  rendererId: string
  schemaVersion: string
  projection: { fields: Array<{ sourcePath: string }> }
}

export type GenericCapabilityCheckPresentationConfig = {
  renderers: Array<{ id: string; kind: 'custom-element'; elementName: string; schemaVersions: string[] }>
  mappings: GenericCapabilityCheckMapping[]
}

export type GenericWorkspaceConfig = {
  app: { assistantLabel: string }
  page?: {
    rootSelector?: string
    contextLabel?: string
    maxChars?: number
    maxPages?: number
    maxTotalChars?: number
  }
  capabilities?: { currentPage?: boolean; handover?: boolean }
  copy?: {
    welcomeMessage?: string
    placeholder?: string
    emptyMessage?: string
    companionModeLabel?: string
    starterSuggestions?: string[]
  }
  toolGroups?: Record<string, unknown>
  toolRail?: Record<string, unknown>
  requestContext?: Record<string, unknown>
  theme?: {
    primaryColor?: string
    logoUrl?: string
    borderRadius?: string
    fontFamily?: string
    darkMode?: boolean | 'auto'
  }
}

export type GenericWorkspaceMountContext = {
  installationId: string
  assignmentRevision: string
  connectionMode: string
  deliveryMode: 'embedded' | 'standalone'
  configuration: Record<string, unknown>
  widgetConfig: Record<string, any>
  /** Trusted host mount scope, never a public identity or authorization claim. */
  hostSession?: { namespace: string; accountKey: string }
  maxMode: {
    init(config: Record<string, unknown>): void
    open(): void
    destroy(): void
    getState(): GenericWorkspaceState
    whenReady(): Promise<GenericWorkspaceState>
    openConversation(id: string, options?: { open?: boolean }): Promise<GenericWorkspaceState>
    refreshConversation(): Promise<GenericWorkspaceState>
    onStateChange(listener: (state: GenericWorkspaceState) => void): () => void
  }
  refreshAssignment(): Promise<void>
}

export type GenericWorkspaceController = {
  installationId: string
  destroy(): void
  getState(): GenericWorkspaceState
  whenReady(): Promise<GenericWorkspaceState>
  openConversation(id: string, options?: { open?: boolean }): Promise<GenericWorkspaceState>
  refreshConversation(): Promise<GenericWorkspaceState>
  onStateChange(listener: (state: GenericWorkspaceState) => void): () => void
}

export type GenericWorkspaceState = {
  status: 'initializing' | 'ready' | 'loading' | 'error' | 'destroyed'
  ready: boolean
  isOpen: boolean
  busy: boolean
  conversationId: string | null
  controlState: 'AI_ACTIVE' | 'HUMAN_PENDING' | 'HUMAN_ACTIVE' | 'AI_RESUME_PENDING' | 'CLOSED' | null
  controlVersion: number | null
  error: { code: string; message: string } | null
}
