export type DealershipRuntimeState = 'checking' | 'ready' | 'unavailable'

export type DealershipPageKind = 'inventory' | 'vehicle-detail' | 'auto'

export type DealershipToolIcon =
  | 'calendar'
  | 'compare'
  | 'details'
  | 'documents'
  | 'location'
  | 'phone'
  | 'search'
  | 'shield'
  | 'sparkles'
  | 'tools'

export interface DealershipTool {
  label: string
  query: string
  position: 'search'
  mode: 'executor'
  icon?: DealershipToolIcon
}

export interface DealershipToolGroup {
  label: string
  icon?: DealershipToolIcon
  tools: DealershipTool[]
  contextLabel?: string
  availableWithoutAttachments?: boolean
}

export interface DealershipToolGroups {
  initialScope: 'default' | 'contextual'
  default: DealershipToolGroup
  contextual: DealershipToolGroup
}

export type DealershipToolRailTone = 'primary' | 'teal' | 'violet' | 'amber' | 'neutral'

interface DealershipToolRailItemBase {
  id: string
  label: string
  icon?: DealershipToolIcon
  tone?: DealershipToolRailTone
}

export type DealershipToolRailItem = DealershipToolRailItemBase & (
  | { action: 'open-tools'; scope?: 'default' | 'contextual' }
  | { action: 'open-documents' }
  | {
      action: 'prompt'
      query: string
      position?: 'landing' | 'catalog' | 'search' | 'cart'
      mode?: 'conversational' | 'navigator' | 'navigator_deep' | 'thinker_deep' | 'cart_assistant' | 'executor'
      requiresContext?: boolean
    }
)

export interface DealershipToolRailConfig {
  items: DealershipToolRailItem[]
  initiallyCollapsed?: boolean
}

export interface DealershipCapabilities {
  comparison?: boolean
  testDrive?: boolean
  callback?: boolean
}

export interface DealershipActionNames {
  searchInventory: string
  getVehicle: string
  compareVehicles: string
  requestTestDrive: string
  requestCallback: string
}

export interface DealershipPresentationConfig {
  actionNames?: Partial<DealershipActionNames>
  capabilities?: DealershipCapabilities
  detailBasePath?: string
  imageHostAllowlist?: string[]
  imageFallbacks?: Record<string, string>
  detailSlugs?: Record<string, string>
}

export interface DealershipVehicleContext {
  id: string
  stockId: string
  make: string
  model: string
  derivative?: string
  registrationYear: number | string
  summary?: string
  priceGbp?: number
  priceFormatted?: string
  currency?: string
  mileage?: number
  fuelType?: string
  bodyType?: string
  lifecycleState?: string
  sourceLabel?: string
  sourceUpdatedAt?: string
  [key: string]: unknown
}

export interface DealershipExperienceCopy {
  welcomeMessage?: string
  placeholder?: string
  emptyMessage?: string
  companionModeLabel?: string
  starterSuggestions?: string[]
}

export interface DealershipExperienceConfig {
  dealer: {
    id: string
    assistantLabel: string
    sourceMode?: string
  }
  page: {
    kind: DealershipPageKind
    rootSelector: string
    contextLabel: string
    subjectLabel?: string
    maxChars?: number
    maxPages?: number
    maxTotalChars?: number
  }
  knowledge?: {
    inventoryVectorSpace?: string
    retrievalVectorSpaces?: string[]
  }
  capabilities?: DealershipCapabilities
  copy?: DealershipExperienceCopy
  toolGroups?: DealershipToolGroups
  toolRail?: DealershipToolRailConfig
  presentation?: DealershipPresentationConfig
  requestContext?: Record<string, unknown>
  theme?: {
    primaryColor?: string
    borderRadius?: string
    fontFamily?: string
    darkMode?: boolean | 'auto'
  }
  onRuntimeState?: (state: DealershipRuntimeState, title: string, detail: string) => void
  onEvent?: (event: { type?: string; data?: unknown; timestamp?: string }) => void
}

export interface DealershipExperienceController {
  readonly installationId: string
  readonly connectionMode: string
  attachVehicle(vehicle: DealershipVehicleContext): void
  sendMessage(message: string, requestContext?: Record<string, unknown>): void
  destroy(): void
}

export interface MaxModeActionPresentationConfig {
  renderers: Array<{
    id: string
    kind: 'custom-element'
    elementName: string
    schemaVersions: string[]
  }>
  mappings: Array<{
    actionName: string
    rendererId: string
    schemaVersion: string
    projection: Record<string, unknown>
    rendererContext?: Record<string, string | number | boolean>
  }>
}

export interface MaxModeBrowserApi {
  init(config: Record<string, unknown> & { apiConfig: Record<string, unknown> }): void
  open(): void
  close(): void
  attachItem(item: { type: string; data: Record<string, unknown>; contextLabel?: string }): void
  sendMessage(message: string, options?: Record<string, unknown>): void
  destroy(): void
}

export interface AIWorkspacePackMountContext {
  installationId: string
  manifestRevision: string
  assignmentRevision: string
  connectionMode: 'public-runtime-anonymous' | 'public-runtime-authenticated' | 'backend-mediated-private-runtime'
  configuration: Record<string, unknown>
  widgetConfig: Record<string, unknown> & { apiConfig: Record<string, unknown> }
  maxMode: MaxModeBrowserApi
  refreshAssignment(): Promise<void>
}

export interface AIWorkspaceBrowserApi {
  registerExperiencePack(registration: {
    code: string
    version: string
    mount(context: AIWorkspacePackMountContext): Promise<unknown> | unknown
  }): void
}

declare global {
  interface Window {
    MaxMode?: MaxModeBrowserApi
    LoomAIWorkspace?: AIWorkspaceBrowserApi
    LoomAIDealershipExperience?: {
      mountInstallation(context: AIWorkspacePackMountContext): Promise<DealershipExperienceController>
      attachVehicle(vehicle: DealershipVehicleContext): boolean
      sendMessage(message: string, requestContext?: Record<string, unknown>): boolean
      destroy(): void
    }
  }
}
