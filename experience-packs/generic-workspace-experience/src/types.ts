export type GenericWorkspaceTool = {
  label: string
  query: string
  position?: 'search'
  mode?: 'executor'
  icon?: string
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
  maxMode: {
    init(config: Record<string, unknown>): void
    open(): void
    destroy(): void
  }
  refreshAssignment(): Promise<void>
}

export type GenericWorkspaceController = {
  installationId: string
  destroy(): void
}
