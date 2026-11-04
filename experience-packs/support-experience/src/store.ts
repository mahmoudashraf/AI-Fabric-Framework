import { SupportApiError } from './api'
import { validateIntake } from './config'
import type { RequestStatus, RequestTransition, SupportCreateRequest, SupportExperienceConfig, SupportExperienceState, SupportRequest, SupportRequesterApi, SupportWorkspaceState, TicketCapability } from './types'

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value))
export class SupportStore {
  private state: SupportExperienceState
  private listeners = new Set<(state: SupportExperienceState) => void>()
  private active = true
  private generation = 0
  private submission?: { fingerprint: string; key: string }
  private commands = new Map<string, string>()
  status?: RequestStatus
  constructor(readonly config: SupportExperienceConfig, readonly api: SupportRequesterApi) {
    this.state = { view: 'chat', presentation: 'widget', session: null, request: null, requests: [], nextCursor: null,
      team: [], busy: false, error: null, notice: null }
  }
  getState = () => clone(this.state)
  subscribe(listener: (state: SupportExperienceState) => void) {
    this.listeners.add(listener); listener(this.getState())
    return () => { this.listeners.delete(listener) }
  }
  update(value: Partial<SupportExperienceState>) {
    if (!this.active) return
    this.state = { ...this.state, ...value }
    for (const listener of this.listeners) {
      try { listener(this.getState()) } catch { console.error('Support state listener failed.') }
    }
  }
  async run(operation: () => Promise<void>, preserveNotice = false) {
    if (!this.active || this.state.busy) return
    this.update({ busy: true, error: null, ...(preserveNotice ? {} : { notice: null }) })
    const generation = this.generation
    try { await operation() }
    catch (error) {
      if (generation !== this.generation) return
      if (error instanceof SupportApiError && error.status === 401) {
        this.api.destroy?.()
        this.update({ session: null, requests: [], request: null, team: [] })
      }
      this.update({ error: error instanceof SupportApiError ? error.message : 'The operation could not be confirmed. Refresh to check saved work before retrying.' })
    } finally { if (generation === this.generation) this.update({ busy: false }) }
  }
  private requireCapability(name: TicketCapability) {
    if (!this.active) throw new Error('This support experience is no longer active.')
    if (!this.state.session?.authenticated || !this.state.session.capabilities.includes(name)) throw new SupportApiError(403, 'UNSUPPORTED', 'This operation is not available.')
  }
  async loadSession() {
    const session = await this.api.session()
    if (session.space.providerBindingId !== this.config.support?.ticketProviderBindingId || session.space.spaceId !== this.config.support.spaceId) throw new SupportApiError(409, 'BINDING_CHANGED', 'The request provider changed. Reload this support page.')
    this.update({ session })
    if (session.authenticated && this.config.support?.teamVisible && this.config.capabilities?.handover === true && session.space.humanHandoverEnabled) this.update({ team: await this.api.team() })
  }
  async list(append = false) {
    if (!this.state.session) await this.loadSession()
    this.requireCapability('LIST')
    const page = await this.api.list({ status: this.status, cursor: append ? this.state.nextCursor ?? undefined : undefined, limit: 20 })
    const rows = append ? [...this.state.requests, ...page.items] : page.items
    this.update({ requests: [...new Map(rows.map(row => [row.id, row])).values()], nextCursor: page.nextCursor ?? null })
    if (this.api.notifications) this.update({ notifications: await this.api.notifications() })
  }
  async setNotifications(enabled: boolean) {
    if (!this.state.session?.authenticated || !this.api.setNotifications) return
    // No query string, fragment or identity token belongs in an email link.
    const returnUrl = new URL(location.pathname, location.origin).href
    const notifications = await this.api.setNotifications(enabled, returnUrl)
    this.update({ notifications, notice: enabled ? 'Email updates enabled for future request updates.' : 'Email updates disabled. Updates remain in My Requests.' })
  }
  async detail(id: string) {
    if (!this.state.session) await this.loadSession()
    this.requireCapability('GET')
    const request = await this.api.get(id)
    this.update({ request, view: 'detail' })
  }
  private saveObservation(request: SupportRequest) {
    this.update({ ...(this.state.request?.id === request.id ? { request } : {}),
      requests: this.state.requests.map(row => row.id === request.id ? request : row) })
  }
  async markSeen(request: SupportRequest) {
    if (!this.state.session?.capabilities.includes('SEEN') || !request.unread || !request.updatedAt) return request
    const seen = await this.command(request, 'seen', key => this.api.seen(request, key))
    this.saveObservation(seen)
    return seen
  }
  async observeActivity(request: SupportRequest) {
    if (!this.api.activity || !this.state.session?.capabilities.includes('OBSERVE_ACTIVITY')
      || (request.capabilities && !request.capabilities.includes('OBSERVE_ACTIVITY'))) return request
    const observed = await this.command(request, 'activity', key => this.api.activity!(request, key))
    this.saveObservation(observed)
    return observed
  }
  canContinue(chat: SupportWorkspaceState) {
    const request = this.state.request, episode = request && latestEpisode(request)
    return !!(this.api.newEpisode && this.state.session?.authenticated && this.state.session.capabilities.includes('LINK_CONVERSATION')
      && (!request?.capabilities || request.capabilities.includes('LINK_CONVERSATION'))
      && request?.status === 'OPEN' && episode?.conversationId === chat.conversationId && chat.controlState === 'CLOSED' && !chat.busy)
  }
  async newEpisode(chat: SupportWorkspaceState) {
    if (!this.canContinue(chat)) throw new SupportApiError(409, 'CONTINUATION_UNAVAILABLE', 'Open the current closed conversation of an open request before continuing.')
    const request = this.state.request!
    const result = await this.command(request, 'episode', key => this.api.newEpisode!(request, key))
    this.saveObservation(result)
    this.update({ notice: 'New conversation created. Previous conversations remain saved.' })
    return result
  }
  async create(input: SupportCreateRequest): Promise<SupportRequest> {
    validateIntake(input, this.config)
    if (!this.state.session) await this.loadSession()
    this.requireCapability('CREATE')
    if (input.existingConversationId) this.requireCapability('LINK_CONVERSATION')
    const fingerprint = JSON.stringify(input)
    if (this.submission && this.submission.fingerprint !== fingerprint) {
      throw new SupportApiError(409, 'UNCONFIRMED_CREATE', 'A previous submission is unconfirmed. Retry its unchanged details before submitting another request.')
    }
    this.submission ??= { fingerprint, key: operationKey('create') }
    try {
      const request = await this.api.create(input, this.submission.key)
      this.submission = undefined
      this.update({ request, view: 'detail', notice: 'Request saved. Updates in My Requests.' })
      return request
    } catch (error) {
      if (definitive(error)) this.submission = undefined
      throw error
    }
  }
  async start(request: SupportRequest) {
    const episode = request.conversation ?? request.episodes?.[request.episodes.length - 1]
    if (!episode || request.openingMessageRequired === false || episode.openingMessageRequired === false || episode.delivery === 'NOT_REQUIRED' || episode.delivery === 'DELIVERED') return request
    const result = await this.command(request, 'start', key => this.api.start(request, key))
    this.update({ request: result, notice: deliveryCopy(result) })
    return result
  }
  async transition(operation: RequestTransition) {
    const request = this.state.request
    if (!request) return
    const capability = operation === 'resolve' ? 'RESOLVE' : operation === 'cancel' ? 'CANCEL' : 'REOPEN'
    this.requireCapability(capability)
    if (request.capabilities && !request.capabilities.includes(capability)) throw new SupportApiError(403, 'UNSUPPORTED', 'This operation is not available for this request.')
    const allowed = operation === 'reopen' ? request.status !== 'OPEN' : request.status === 'OPEN'
    if (!allowed) throw new SupportApiError(409, 'STATUS_CHANGED', 'Review the current request status before continuing.')
    const result = await this.command(request, operation, key => this.api.transition(request, operation, key))
    this.update({ request: result, notice: 'Request status updated. Conversation control is unchanged.' })
  }
  private async command(request: SupportRequest, kind: string, execute: (key: string) => Promise<SupportRequest>) {
    const scope = JSON.stringify([request.id, request.providerBindingId, request.version, kind])
    const key = this.commands.get(scope) ?? operationKey(kind)
    this.commands.set(scope, key)
    try {
      const result = await execute(key)
      this.commands.delete(scope)
      return result
    } catch (error) {
      if (definitive(error)) this.commands.delete(scope)
      if (error instanceof SupportApiError && error.status === 409 && error.current) this.update({ request: error.current })
      throw error
    }
  }
  destroy() { this.active = false; this.generation++; this.listeners.clear(); this.api.destroy?.() }
}
function definitive(error: unknown) { return error instanceof SupportApiError && error.status >= 400 && error.status < 500 }
function operationKey(kind: string) { return `support-${kind}-${crypto.randomUUID()}` }
export function deliveryCopy(request: SupportRequest) {
  const delivery = (request.conversation ?? request.episodes?.[request.episodes.length - 1])?.delivery
  if (delivery === 'DELIVERED') return 'Request saved. Opening message delivered; open the conversation to view its result.'
  if (delivery === 'NOT_REQUIRED') return 'Request saved. No opening message needs to be sent.'
  if (delivery === 'IN_FLIGHT') return 'Request saved. Opening conversation delivery is in progress.'
  if (delivery === 'UNKNOWN') return 'Request saved. Conversation delivery is unconfirmed.'
  return 'Request saved; conversation delivery pending.'
}
export function latestEpisode(request: SupportRequest) { return request.conversation ?? request.episodes?.[request.episodes.length - 1] }
