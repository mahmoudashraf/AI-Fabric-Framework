import { resolveSupportPresentation } from './config'
import { deliveryCopy, SupportStore } from './store'
import { supportStyles } from './styles'
import { applyThemeToElement, bindWidgetColorScheme, ensureWorkspaceStyleFonts } from './widget-theme.js'
import type { RequestStatus, RequestTransition, SupportExperienceConfig, SupportExperienceState, SupportHostAdapter, SupportIntakeExtension, SupportPresentationMode, SupportRequest } from './types'

export interface SupportShellCallbacks {
  chat(): void
  submit(input: { subject: string; details: string; topic?: string; existingConversationId?: string; hostContext?: Record<string, unknown> }): void
  list(append?: boolean): void
  detail(id: string): void
  openEpisode(request: SupportRequest, conversationId?: string): void
  transition(operation: RequestTransition): void
  start(request: SupportRequest): void
  canContinue(): boolean
  continueConversation(): void
  signIn(): void
  notifications(enabled: boolean): void
  currentConversation(): string | null
  layout(): void
}
export class SupportShell {
  readonly element = document.createElement('div')
  readonly shadow = this.element.attachShadow({ mode: 'open' })
  readonly chatSlot: HTMLElement
  private readonly form: HTMLFormElement
  private readonly panel: HTMLElement
  private readonly roster: HTMLElement
  private readonly notice: HTMLElement
  private readonly error: HTMLElement
  private readonly launcher: HTMLButtonElement
  private readonly extension?: SupportIntakeExtension
  private readonly signInAvailable: boolean
  private expanded = false
  private mode: SupportPresentationMode = 'widget'
  private continuationAvailable = false
  private releaseColorScheme?: () => void
  constructor(readonly config: SupportExperienceConfig, readonly store: SupportStore, readonly callbacks: SupportShellCallbacks, host: SupportHostAdapter) {
    const p = resolveSupportPresentation(config)
    this.signInAvailable = typeof host.requestSignIn === 'function'
    this.element.dataset.loomaiSupport = 'true'
    this.setTheme(config.theme)
    if (config.theme?.primaryColor && CSS.supports('color', config.theme.primaryColor)) this.element.style.setProperty('--support-accent', config.theme.primaryColor)
    const style = document.createElement('style'); style.textContent = supportStyles; this.shadow.append(style)
    this.shadow.append(node('section', { class: 'shell', 'aria-label': p.title },
      node('header', { class: 'header' }, node('div', { class: 'identity' },
        ...(safeImage(config.theme?.logoUrl) ? [node('img', { class: 'logo', alt: '', src: config.theme!.logoUrl! })] : []),
        node('div', {}, node('h1', { class: 'title' }, p.title), node('p', { class: 'subtitle' }, p.assistantLabel))),
        node('nav', { class: 'actions', 'aria-label': 'Support' },
          button('Chat', () => callbacks.chat()),
          ...(p.tickets ? [button('Request help', () => this.showForm()), button(p.requestLabel, () => callbacks.list())] : []),
          ...(host.requestSignIn ? [button('Sign in', callbacks.signIn, { 'data-sign-in': '' })] : []),
          button('Close', () => this.setExpanded(false), { 'data-close': 'true' }))),
      node('div', { class: 'team', hidden: '' }),
      node('p', { class: 'message', role: 'status', hidden: '' }),
      node('p', { class: 'message error', role: 'alert', hidden: '' }),
      node('form', { class: 'intake', hidden: '' },
        node('h2', { class: 'form-heading' }, 'Request help'),
        node('div', { class: 'fields' },
          label('Subject', node('input', { name: 'subject', required: '', maxlength: '240', autocomplete: 'off' })),
          ...(config.support?.topics?.length ? [label('Topic', node('select', { name: 'topic' }, node('option', { value: '' }, 'Select a topic'),
            ...config.support.topics.map(t => node('option', { value: t.code }, t.label))))] : []),
          label('Details', node('textarea', { name: 'details', required: '', maxlength: '12000', rows: '3' }), 'wide')),
        node('div', { 'data-host-intake': '' }),
        node('label', { class: 'muted', 'data-link-label': '', hidden: '' }, node('span', {},
          node('input', { type: 'checkbox', name: 'link', style: 'width:auto;margin-right:6px' }), 'Link my current conversation')),
        node('div', { class: 'form-footer' }, node('p', { class: 'muted' }, 'Do not include passwords or access keys.'),
          node('div', { class: 'actions' }, button('Cancel', () => callbacks.chat()), button('Submit request', () => {}, { type: 'submit', class: 'primary' })))),
      node('div', { class: 'columns' }, node('div', { class: 'chat', 'aria-label': 'Conversation' }, node('p', { class: 'chat-wait' }, 'Connecting support conversation...')),
        node('aside', { class: 'panel', 'aria-label': p.requestLabel, hidden: '' })),
      node('footer', { class: 'footer' }, p.tickets ? 'Updates in My Requests' : 'AI support')))
    this.launcher = button(p.assistantLabel, () => this.setExpanded(!this.expanded), { class: 'launcher primary', 'aria-expanded': 'false' })
    this.shadow.append(this.launcher)
    this.chatSlot = this.shadow.querySelector('.chat')!
    this.form = this.shadow.querySelector('form')!
    this.panel = this.shadow.querySelector('.panel')!
    this.roster = this.shadow.querySelector('.team')!
    this.notice = this.shadow.querySelector('.message:not(.error)')!
    this.error = this.shadow.querySelector('.error')!
    this.extension = host.intakeExtension?.(this.shadow.querySelector('[data-host-intake]')!)
    this.form.addEventListener('submit', event => {
      event.preventDefault()
      const validation = this.extension?.validate?.()
      if (validation) { store.update({ error: validation }); return }
      const data = new FormData(this.form)
      callbacks.submit({ subject: String(data.get('subject') ?? ''), details: String(data.get('details') ?? ''),
        ...(data.get('topic') ? { topic: String(data.get('topic')) } : {}),
        ...(data.get('link') ? { existingConversationId: callbacks.currentConversation() ?? undefined } : {}),
        ...(this.extension ? { hostContext: this.extension.read() } : {}) })
    })
  }
  showForm() { this.setExpanded(true); this.store.update({ view: 'form', error: null }); (this.form.elements.namedItem('subject') as HTMLElement).focus() }
  setMode(mode: SupportPresentationMode) {
    this.element.classList.remove(this.mode)
    this.mode = mode; this.element.classList.add(mode)
    this.launcher.hidden = mode !== 'widget'
    ;(this.shadow.querySelector('[data-close]') as HTMLElement).hidden = mode !== 'widget'
    if (mode !== 'widget') this.expanded = true
    this.setExpanded(this.expanded)
  }
  setExpanded(expanded: boolean) {
    this.expanded = expanded
    ;(this.shadow.querySelector('.shell') as HTMLElement).hidden = this.mode === 'widget' && !expanded
    this.launcher.setAttribute('aria-expanded', String(expanded))
    this.callbacks.layout()
  }
  chatVisible() {
    return this.expanded && this.element.isConnected && !this.element.hidden && getComputedStyle(this.chatSlot).display !== 'none'
  }
  visible() { return this.expanded && this.element.isConnected && !this.element.hidden }
  setChatReady(ready: boolean) { (this.shadow.querySelector('.chat-wait') as HTMLElement).hidden = ready }
  syncConversation() {
    const available = this.callbacks.canContinue()
    if (available === this.continuationAvailable) return
    this.continuationAvailable = available
    const state = this.store.getState()
    if (state.view === 'detail' && state.request) this.renderDetail(state)
  }
  render(state: SupportExperienceState) {
    ;(this.shadow.querySelector('.shell') as HTMLElement).dataset.view = state.view
    this.form.hidden = state.view !== 'form'
    const hasPanel = state.view === 'requests' || state.view === 'detail'
    this.panel.hidden = !hasPanel
    this.shadow.querySelector('.columns')!.classList.toggle('has-panel', hasPanel)
    this.error.hidden = !state.error; this.error.textContent = state.error
    const signIn = this.shadow.querySelector<HTMLElement>('[data-sign-in]')
    if (signIn) signIn.hidden = state.session?.authenticated === true
    this.notice.hidden = !state.notice; this.notice.textContent = state.notice
    this.form.querySelectorAll<HTMLButtonElement | HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('button,input,textarea,select').forEach(input => { input.disabled = state.busy })
    const link = this.shadow.querySelector<HTMLElement>('[data-link-label]')!
    link.hidden = !state.session?.capabilities.includes('LINK_CONVERSATION') || !this.callbacks.currentConversation()
    if (state.view === 'detail' && state.request) this.renderDetail(state)
    else if (state.view === 'requests') this.renderList(state)
    this.renderTeam(state)
    this.callbacks.layout()
  }
  private renderTeam(state: SupportExperienceState) {
    this.roster.replaceChildren()
    this.roster.hidden = !this.config.support?.teamVisible
    if (this.roster.hidden) return
    if (!state.team.length) { this.roster.textContent = this.config.capabilities?.tickets ? 'AI support and request tracking' : 'AI support'; return }
    this.roster.append(node('span', {}, 'Assigned support team'), node('div', { class: 'team-members' }, ...state.team.map(agent =>
      node('span', { class: 'person' }, safeImage(agent.photoUrl, true)
        ? node('img', { class: 'portrait', src: agent.photoUrl!, alt: '', referrerpolicy: 'no-referrer' })
        : node('span', { class: 'initials', 'aria-hidden': 'true' }, agent.displayName.slice(0, 1)), node('span', {}, agent.displayName)))))
  }
  private renderList(state: SupportExperienceState) {
    const filter = node('select', { class: 'filter', 'aria-label': 'Request status' },
      ...['', 'OPEN', 'RESOLVED', 'CANCELLED'].map(value => node('option', { value }, value || 'All statuses'))) as HTMLSelectElement
    filter.value = this.store.status ?? ''
    filter.disabled = state.busy
    filter.addEventListener('change', () => { this.store.status = filter.value as RequestStatus || undefined; this.callbacks.list() })
    this.panel.replaceChildren(node('div', { class: 'panel-header' }, node('h2', {}, resolveSupportPresentation(this.config).requestLabel),
      filter, button('Refresh', () => this.callbacks.list(), { disabled: state.busy })))
    if (state.session?.authenticated && state.notifications) {
      const preference = state.notifications
      const toggle = node('input', { type: 'checkbox', 'aria-label': 'Email request updates', style: 'width:auto' }) as HTMLInputElement
      toggle.checked = preference.enabled
      toggle.disabled = state.busy || !preference.available
      toggle.addEventListener('change', () => this.callbacks.notifications(toggle.checked))
      this.panel.append(node('div', { class: 'detail' },
        node('label', { class: 'notification-toggle' }, toggle, 'Email request updates'),
        node('p', { class: 'muted', role: 'status' }, !preference.available ? 'Email is not available. Updates remain in My Requests.'
          : preference.lastFailureCode ? 'An email update could not be delivered. Your request is saved; check My Requests.'
          : preference.enabled ? 'Status changes and human replies. No conversation text is sent by email.' : 'Optional. Send updates to your verified sign-in email.')))
    }
    if (!state.session?.authenticated) this.panel.append(node('div', { class: 'empty' }, node('p', {}, this.signInAvailable ? 'Sign in to view your requests.' : 'Sign in through the host application to view your requests.'),
      ...(this.signInAvailable ? [button('Sign in', this.callbacks.signIn)] : [])))
    else if (!state.requests.length) this.panel.append(node('p', { class: 'empty' }, state.busy ? 'Loading requests...' : 'No requests in this view.'))
    else this.panel.append(node('ul', { class: 'request-list' }, ...state.requests.map(request => node('li', {},
      button('', () => this.callbacks.detail(request.id), { class: 'request-row', disabled: state.busy },
        node('span', { class: 'row-top' }, node('span', {}, request.reference), statusBadge(request.status)),
        node('span', { class: 'request-subject' }, request.subject), node('span', { class: 'muted' }, dateLabel(request.updatedAt)),
        ...(request.unread ? [node('span', { class: 'unread' }, ' New update')] : []))))))
    if (state.nextCursor) this.panel.append(button('Load more', () => this.callbacks.list(true), { disabled: state.busy }))
    this.panel.append(button('Return to chat', this.callbacks.chat))
  }
  private renderDetail(state: SupportExperienceState) {
    const request = state.request!, capabilities = state.session?.capabilities
    const can = (name: 'RESOLVE' | 'CANCEL' | 'REOPEN') => capabilities?.includes(name) === true && (!request.capabilities || request.capabilities.includes(name))
    const commands: Array<[RequestTransition, string, boolean]> = request.status === 'OPEN'
      ? [['resolve', 'Resolve request', can('RESOLVE')], ['cancel', 'Cancel request', can('CANCEL')]]
      : [['reopen', 'Reopen request', can('REOPEN')]]
    const episodes = request.episodes ?? (request.conversation ? [request.conversation] : [])
    this.panel.replaceChildren(node('div', { class: 'panel-header' }, button('All requests', () => this.callbacks.list()), statusBadge(request.status)),
      node('div', { class: 'detail' }, node('span', { class: 'muted' }, request.reference), node('h3', {}, request.subject),
        node('p', { class: 'intake-text' }, request.details ?? ''),
        ...(request.topic ? [node('p', { class: 'muted' }, request.topic)] : []),
        ...(request.upstreamStatus ? [node('p', { class: 'muted' }, request.upstreamStatus)] : []),
        node('p', { class: 'muted' }, deliveryCopy(request)),
        node('div', { class: 'detail-actions' }, ...commands.map(([operation, text, supported]) =>
          button(text, () => this.callbacks.transition(operation), { disabled: state.busy || !supported, title: supported ? text : 'Not supported by the selected request provider' }))),
        ...(request.status !== 'OPEN' ? [node('p', { class: 'muted' }, 'Reopen this request before continuing its conversation.')] : []),
        ...(this.callbacks.canContinue() ? [button('Continue in a new conversation', this.callbacks.continueConversation, { disabled: state.busy })] : []),
        ...episodes.map((episode, index) => node('div', { class: 'episode' }, node('span', {}, `Conversation ${index + 1}: ${episode.delivery.split('_').join(' ').toLowerCase()}`),
          button('Open', () => this.callbacks.openEpisode(request, episode.conversationId), { disabled: state.busy }))),
        ...(request.status === 'OPEN' && request.openingMessageRequired !== false && ['PENDING', 'UNKNOWN'].includes(request.conversation?.delivery ?? '')
          ? [button('Check conversation delivery', () => this.callbacks.start(request), { disabled: state.busy })] : []),
        ...(request.events ?? []).map(event => node('p', { class: 'muted' }, `${dateLabel(event.createdAt)}: ${event.type}`))),
      button('Return to chat', this.callbacks.chat))
  }
  clearForm() { this.form.reset() }
  setTheme(theme: SupportExperienceConfig['theme']) {
    this.releaseColorScheme?.()
    this.releaseColorScheme = undefined
    this.element.dataset.dark = String(theme?.darkMode ?? 'auto')
    ensureWorkspaceStyleFonts(this.element.ownerDocument, theme?.stylePreset)
    if (theme?.stylePreset && theme.stylePreset !== 'current') {
      applyThemeToElement(this.element, theme)
      this.releaseColorScheme = bindWidgetColorScheme(this.element, theme.darkMode ?? 'auto', theme)
    } else {
      applyThemeToElement(this.element, theme)
      this.element.removeAttribute('data-mxw-style')
    }
  }
  destroy() { try { this.releaseColorScheme?.(); this.extension?.destroy() } finally { this.element.remove() } }
}
function dateLabel(value?: string) { if (!value) return ''; const date = new Date(value); return Number.isNaN(date.valueOf()) ? '' : date.toLocaleString() }
function safeImage(value?: string, localBlob = false) { if (!value) return false; try { const url = new URL(value); return (url.protocol === 'https:' || (localBlob && url.protocol === 'blob:' && url.origin === location.origin)) && !url.username && !url.password } catch { return false } }
function statusBadge(status: string) { return node('span', { class: `status ${status}` }, status.slice(0, 1) + status.slice(1).toLowerCase()) }
function label(text: string, input: HTMLElement, className = '') { input.setAttribute('aria-label', text); return node('label', { class: className }, text, input) }
function node(tag: string, attributes: Record<string, string | boolean>, ...children: Array<Node | string>): HTMLElement {
  const element = document.createElement(tag)
  for (const [key, value] of Object.entries(attributes)) if (value !== false) element.setAttribute(key, value === true ? '' : value)
  element.append(...children)
  return element
}
function button(text: string, click: () => void, attributes: Record<string, string | boolean> = {}, ...children: Array<Node | string>): HTMLButtonElement {
  const element = node('button', { type: 'button', ...attributes }, text, ...children) as HTMLButtonElement
  element.addEventListener('click', click)
  return element
}
