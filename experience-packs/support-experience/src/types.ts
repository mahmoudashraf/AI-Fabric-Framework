export type SupportPresentationMode = 'widget' | 'page' | 'standalone' | 'docked'
export type RequestStatus = 'OPEN' | 'RESOLVED' | 'CANCELLED'
export type RequestTransition = 'resolve' | 'cancel' | 'reopen'
export type DeliveryObservation = 'PENDING' | 'IN_FLIGHT' | 'DELIVERED' | 'UNKNOWN' | 'NOT_REQUIRED'

export interface SupportExperienceConfig {
  schemaVersion: 'loomai-support-experience-config-v1'
  app: { assistantLabel: string; title?: string }
  support?: {
    spaceId?: string
    ticketProviderBindingId?: string
    requesterOrigin?: string
    requestLabel?: string
    topics?: Array<{ code: string; label: string }>
    teamVisible?: boolean
  }
  capabilities?: { tickets?: boolean; handover?: boolean; attachments?: boolean; currentPage?: boolean }
  copy?: { welcomeMessage?: string; placeholder?: string; emptyMessage?: string; starterSuggestions?: string[] }
  theme?: {
    stylePreset?: 'current' | 'clarity' | 'editorial' | 'obsidian' | 'botanical' | 'atelier' | 'kinetic' | 'frost' | 'workshop' | 'terminal' | 'pop' | 'gallery' | 'human'
    primaryColor?: string; logoUrl?: string; borderRadius?: string; fontFamily?: string; darkMode?: boolean | 'auto'
  }
  toolGroups?: Record<string, unknown>
  toolRail?: Record<string, unknown>
  requestContext?: Record<string, unknown>
}

export type TicketCapability = 'CREATE' | 'LIST' | 'GET' | 'RESOLVE' | 'CANCEL' | 'REOPEN' | 'SEEN' | 'LINK_CONVERSATION' | 'OBSERVE_ACTIVITY' | 'AGENT_LIST' | 'AGENT_GET' | 'AGENT_RESOLVE' | 'PURGE'
export interface SupportSession {
  authenticated: boolean
  requesterId: string
  space: { spaceId: string; providerBindingId: string; humanHandoverEnabled: boolean; capabilities: TicketCapability[] }
  capabilities: TicketCapability[]
}
export interface SupportAgent { profileRef: string; displayName: string; photoAvailable?: boolean; photoUrl?: string }
export interface SupportEpisode {
  episodeId: string
  conversationId: string
  delivery: DeliveryObservation
  openingMessageRequired?: boolean
  createdAt?: string
}
export interface SupportRequest {
  id: string
  reference: string
  providerBindingId: string
  subject: string
  details?: string
  topic?: string
  status: RequestStatus
  version: string
  createdAt?: string
  updatedAt?: string
  unread?: boolean
  upstreamStatus?: string
  openingMessageRequired?: boolean
  conversation?: SupportEpisode
  episodes?: SupportEpisode[]
  events?: Array<{ type: string; actorId?: string; createdAt: string }>
  capabilities?: TicketCapability[]
}
export interface SupportRequestPage { items: SupportRequest[]; nextCursor?: string | null }
export interface SupportNotificationPreference {
  available: boolean
  enabled: boolean
  deliveryStatus: string
  lastFailureCode?: string | null
  updatedAt?: string | null
}
export interface SupportCreateRequest {
  subject: string
  details: string
  topic?: string
  existingConversationId?: string
  /** Untrusted host intake, interpreted and authorized only by the host adapter. */
  hostContext?: Record<string, unknown>
}
export interface SupportRequesterApi {
  session(): Promise<SupportSession>
  team(): Promise<SupportAgent[]>
  list(options: { status?: RequestStatus; cursor?: string; limit: number }): Promise<SupportRequestPage>
  get(id: string): Promise<SupportRequest>
  create(input: SupportCreateRequest, idempotencyKey: string): Promise<SupportRequest>
  start(request: SupportRequest, idempotencyKey: string): Promise<SupportRequest>
  transition(request: SupportRequest, operation: RequestTransition, idempotencyKey: string): Promise<SupportRequest>
  seen(request: SupportRequest, idempotencyKey: string): Promise<SupportRequest>
  newEpisode?(request: SupportRequest, idempotencyKey: string): Promise<SupportRequest>
  activity?(request: SupportRequest, idempotencyKey: string): Promise<SupportRequest>
  notifications?(): Promise<SupportNotificationPreference>
  setNotifications?(enabled: boolean, returnUrl: string): Promise<SupportNotificationPreference>
  destroy?(): void
}

export interface SupportWorkspaceState {
  status: 'initializing' | 'ready' | 'loading' | 'error' | 'destroyed'
  ready: boolean; isOpen: boolean; busy: boolean; conversationId: string | null
  controlState: 'AI_ACTIVE' | 'HUMAN_PENDING' | 'HUMAN_ACTIVE' | 'AI_RESUME_PENDING' | 'CLOSED' | null
  controlVersion: number | null
  /** Successful canonical history reads or accepted message/control updates, not UI renders. */
  canonicalRevision?: number
  error: { code: string; message: string } | null
}
export interface SupportChatApi {
  init(config: Record<string, any>): void
  open(): void
  close(): void
  destroy(): void
  getState(): SupportWorkspaceState
  whenReady(): Promise<SupportWorkspaceState>
  onStateChange(listener: (state: SupportWorkspaceState) => void): () => void
  openConversation(id: string, options?: { open?: boolean }): Promise<SupportWorkspaceState>
  refreshConversation(): Promise<SupportWorkspaceState>
  setConnection?(config: Record<string, any>): void
  setTheme?(theme: SupportExperienceConfig['theme']): void
  setReadOnly?(readOnly: boolean): void
}
export interface SupportWorkspaceFrame { setTarget(element: HTMLElement | null): void; setContentClipping?(enabled: boolean): void; destroy(): void }
export interface SupportRuntimeBootstrap {
  widgetConfig: Record<string, any>
  hostSession?: { namespace: string; accountKey: string }
  conversationId?: string
}
/** Trusted host response; executable assets are loaded separately by the integrity-checked installer. */
export interface SupportWorkspaceBootstrap {
  apiBaseUrl: string
  conversationId?: string
  principalNamespace?: string
  conversationHandover?: { enabled: boolean; [key: string]: unknown }
  configuration?: Record<string, unknown>
  workspaceAssetUrl?: string
  workspaceIntegrity?: string
  experienceModuleUrl?: string
  experienceIntegrity?: string
  installationId?: string
  assignmentRevision?: string
}
export interface SupportIntakeExtension {
  read(): Record<string, unknown>
  validate?(): string | null
  destroy(): void
}
export interface SupportHostAdapter {
  /** Trusted host code only. May be a same-origin BFF such as /api/support. */
  apiBaseUrl?: string
  api?: SupportRequesterApi
  getIdentityAccessToken?: () => Promise<string | null> | string | null
  getRuntimeAccessToken?: () => Promise<string | null> | string | null
  /** Explicit BFF path: the trusted host supplies runtime proof server-side. Never an auth fallback. */
  runtimeCredentialMode?: 'direct' | 'host-mediated'
  getRuntimeBootstrap?: (request: SupportRequest) => Promise<SupportRuntimeBootstrap>
  /** undefined starts owner-scoped chat only; a ticket is never created by this callback. */
  fetchWorkspace?: (requestId?: string) => Promise<SupportWorkspaceBootstrap>
  onRequestSelected?: (request: SupportRequest | null) => void
  intakeExtension?: (container: HTMLElement) => SupportIntakeExtension
  requestSignIn?: () => Promise<void> | void
}
export interface SupportMountContext {
  /** Abort disposes the controller and its owned frame, including during initialization. */
  signal?: AbortSignal
  installationId: string
  assignmentRevision: string
  deliveryMode: 'embedded' | 'standalone'
  connectionMode: string
  configuration: Record<string, unknown>
  widgetConfig: Record<string, any>
  hostSession?: { namespace: string; accountKey: string }
  maxMode: SupportChatApi
  workspaceFrame: SupportWorkspaceFrame
  presentation?: { mode: SupportPresentationMode; container?: HTMLElement; containerSelector?: string }
  credentials?: {
    getIdentityAccessToken?: () => Promise<string | null> | string | null
    getRuntimeAccessToken?: () => Promise<string | null> | string | null
    getIdentity?: () => { status: 'anonymous' | 'authenticated'; subjectKey?: string } | undefined
    requestSignIn?: () => Promise<void> | void
  }
  supportHost?: SupportHostAdapter
  hostExtensions?: Record<string, unknown>
  refreshAssignment(): Promise<void>
}
export interface SupportExperienceState {
  view: 'chat' | 'form' | 'requests' | 'detail'
  presentation: SupportPresentationMode
  session: SupportSession | null
  request: SupportRequest | null
  requests: SupportRequest[]
  nextCursor: string | null
  team: SupportAgent[]
  busy: boolean
  error: string | null
  notice: string | null
  notifications?: SupportNotificationPreference | null
}
export interface SupportExperienceController {
  installationId: string
  setPresentation(mode: SupportPresentationMode, container?: HTMLElement): void
  setVisible(visible: boolean): void
  setTheme(mode: 'light' | 'dark' | 'auto'): void
  openChat(): void
  openRequestForm(): void
  showRequests(): Promise<void>
  openRequest(id: string): Promise<void>
  refresh(): Promise<void>
  getState(): SupportExperienceState
  onStateChange(listener: (state: SupportExperienceState) => void): () => void
  destroy(): void
}
