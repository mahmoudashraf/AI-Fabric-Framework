import { createRequesterApi, SupportApiError } from './api'
import { resolveSupportPresentation, validateConfig } from './config'
import { SupportShell } from './shell'
import { latestEpisode, SupportStore } from './store'
import { ConversationActivityObserver } from './activity'
import type { SupportExperienceController, SupportHostAdapter, SupportMountContext, SupportPresentationMode, SupportRequest, SupportRequesterApi, SupportRuntimeBootstrap, SupportWorkspaceBootstrap } from './types'

export * from './types'
export { validateConfig, validateIntake, resolveSupportPresentation } from './config'
export { createRequesterApi, SupportApiError } from './api'
export { supportExperiencePreviewFixtures } from './preview-fixtures'
export const code = 'support'
export const version = '1.0.0'
let activeController: SupportExperienceController | undefined

export async function mountInstallation(context: SupportMountContext): Promise<SupportExperienceController> {
  const config = validateConfig(context.configuration)
  if (activeController) throw new Error('Only one support experience can be mounted per document. Reuse its controller.')
  if (!context.workspaceFrame) throw new Error('The support experience requires an isolated workspace frame host.')
  const p = resolveSupportPresentation(config)
  let theme = p.theme
  const host = context.supportHost ?? context.hostExtensions?.support as SupportHostAdapter ?? {}
  const requesterEnabled = p.tickets || p.handover
  const api = host.api ?? (requesterEnabled ? createRequesterApi(context, host) : unavailableApi())
  const store = new SupportStore(config, api)
  let destroyed = false, initialized = false, runtimeConfig: Record<string, any> | undefined
  let hostVisible = true
  let releaseChat: (() => void) | undefined
  const linkedConversations = new Set<string>()
  const currentChat = () => context.maxMode.getState()
  const activity = new ConversationActivityObserver(store, currentChat,
    () => !destroyed && document.visibilityState !== 'hidden' && shell.chatVisible())
  let container = context.presentation?.container
  let selector = context.presentation?.containerSelector
  let mode: SupportPresentationMode = context.presentation?.mode ?? (context.deliveryMode === 'standalone' ? 'standalone' : 'widget')
  if (mode === 'page' && (!container?.isConnected || container === document.body || container === document.documentElement)) {
    throw new Error('Page presentation requires a connected local container.')
  }
  const ensureActive = () => { if (destroyed) throw new Error('This support experience has been destroyed.') }
  const layout = () => {
    if (destroyed) return
    context.workspaceFrame.setTarget(shell.chatVisible() ? shell.chatSlot : null)
  }
  const initializeChat = (bootstrap: SupportRuntimeBootstrap) => {
    ensureActive()
    const supplied = bootstrap.widgetConfig
    if (!supplied.apiConfig?.chatBaseUrl) throw new Error('A trusted runtime bootstrap must supply apiConfig.chatBaseUrl.')
    const widgetConfig: Record<string, any> = {
      ...supplied, hostSession: bootstrap.hostSession ?? context.hostSession ?? supplied.hostSession,
      launcher: false, theme,
      features: { ...supplied.features, cart: false, debug: false, quickActions: true, attachments: p.attachments,
        conversations: bootstrap.hostSession || context.hostSession ? false : supplied.features?.conversations !== false },
      beforeNewConversation: context.refreshAssignment,
      host: { ...supplied.host, assistantLabel: p.assistantLabel, welcomeMessage: p.welcomeMessage,
        starterSuggestions: p.starterSuggestions, companionPlaceholder: p.placeholder,
        companionEmptyMessage: config.copy?.emptyMessage, toolGroups: config.toolGroups, toolRail: config.toolRail,
        defaultConversationMode: 'executor', effectiveConversationMode: 'executor', allowedConversationModes: ['executor'],
        showUtilityPanel: false, showCloseButton: false, companionDock: false,
        requestContext: { ...config.requestContext, workspaceInstallationId: context.installationId, assignmentRevision: context.assignmentRevision },
        currentPageAttachment: p.currentPage && p.attachments ? { enabled: true, invalidateOnNavigation: false, contentProvider: () => ({
          title: document.title, url: location.href,
          text: (document.querySelector('main')?.textContent ?? '').slice(0, 1800),
        }) } : undefined,
      },
      apiConfig: { ...supplied.apiConfig,
        conversationHandover: p.handover ? supplied.apiConfig.conversationHandover : { enabled: false } },
    }
    if (!initialized) {
      context.maxMode.init(widgetConfig)
      initialized = true
      releaseChat = context.maxMode.onStateChange(state => {
        shell.setChatReady(state.ready); shell.syncConversation(); activity.changed(state); layout()
      })
    } else {
      if (!context.maxMode.setConnection) throw new Error('The chat engine does not support trusted connection rebinding.')
      context.maxMode.setConnection({ apiConfig: widgetConfig.apiConfig, integrationMode: widgetConfig.integrationMode,
        hostSession: widgetConfig.hostSession, readOnly: widgetConfig.host.readOnly })
    }
    runtimeConfig = widgetConfig
    context.maxMode.open()
    layout()
  }
  function fromWorkspace(bootstrap: SupportWorkspaceBootstrap): SupportRuntimeBootstrap {
    const endpoint = new URL(bootstrap.apiBaseUrl, document.baseURI)
    const local = ['localhost', '127.0.0.1', '[::1]'].includes(endpoint.hostname)
    if ((endpoint.protocol !== 'https:' && !(local && endpoint.protocol === 'http:')) || endpoint.username || endpoint.password || endpoint.search || endpoint.hash) {
      throw new Error('The trusted runtime adapter requires a safe HTTPS endpoint.')
    }
    if (host.runtimeCredentialMode === 'host-mediated' && endpoint.origin !== new URL(host.apiBaseUrl ?? document.baseURI, document.baseURI).origin) {
      throw new Error('A private host runtime adapter must stay on its registered BFF origin.')
    }
    const accountKey = context.hostSession?.accountKey ?? context.credentials?.getIdentity?.()?.subjectKey
    if (bootstrap.principalNamespace && !accountKey) throw new Error('Private workspace bootstrap requires an opaque host account key.')
    const token = host.getRuntimeAccessToken ?? (host.runtimeCredentialMode === 'host-mediated' ? host.getIdentityAccessToken ?? context.credentials?.getIdentityAccessToken : context.credentials?.getRuntimeAccessToken)
    const base = '/chat/me/conversations/{conversationId}'
    return {
      conversationId: bootstrap.conversationId,
      hostSession: bootstrap.principalNamespace ? { namespace: bootstrap.principalNamespace, accountKey: accountKey! } : context.hostSession,
      widgetConfig: { ...context.widgetConfig, integrationMode: context.connectionMode,
        apiConfig: { chatBaseUrl: endpoint.href.replace(/\/$/, ''), fetchCredentials: 'omit', probeShellConfigOnOpen: false,
          runtimeAuth: { getBearerToken: token, probeAuthContextOnOpen: false }, conversationHandover: bootstrap.conversationHandover,
          runtimeRoutes: { conversationItemUrlTemplate: base, conversationMessageUrlTemplate: `${base}/messages`,
            handoverRequestUrlTemplate: `${base}/handover-requests`, handoverCancelUrlTemplate: `${base}/handover-requests/{handoverId}/cancel`,
            handoverRetryUrlTemplate: `${base}/handover-requests/{handoverId}/retry`, conversationControlUrlTemplate: `${base}/control`,
            conversationEventsUrlTemplate: `${base}/events`, aiResumeRequestUrlTemplate: `${base}/ai-resume-requests` } },
      },
    }
  }
  const episodeReadOnly = (request: SupportRequest, id?: string) => request.status !== 'OPEN'
    || (!!id && id !== latestEpisode(request)?.conversationId)
  async function prepare(request: SupportRequest, conversationId?: string) {
    ensureActive()
    if (initialized && context.maxMode.getState().busy) throw new SupportApiError(409, 'CHAT_BUSY', 'Wait for the current conversation operation to finish.')
    if (host.fetchWorkspace || host.getRuntimeBootstrap) {
      const bootstrap = host.fetchWorkspace ? fromWorkspace(await host.fetchWorkspace(request.id)) : await host.getRuntimeBootstrap!(request)
      ensureActive()
      initializeChat({ ...bootstrap, widgetConfig: { ...bootstrap.widgetConfig,
        host: { ...bootstrap.widgetConfig.host, readOnly: episodeReadOnly(request, conversationId) } } })
      return bootstrap.conversationId
    }
    if (!initialized) throw new Error('The authorized conversation connection is unavailable.')
    if (context.maxMode.setConnection && runtimeConfig) {
      context.maxMode.setConnection({ apiConfig: runtimeConfig.apiConfig, hostSession: runtimeConfig.hostSession,
        readOnly: episodeReadOnly(request, conversationId) })
    }
  }
  async function openEpisode(request: SupportRequest, conversationId?: string, keepDetail = false) {
    const bootstrappedId = await prepare(request, conversationId)
    const id = conversationId ?? bootstrappedId ?? request.conversation?.conversationId ?? request.episodes?.[request.episodes.length - 1]?.conversationId
    ensureActive()
    if (!id) throw new Error('No authorized conversation has been linked yet.')
    shell.setExpanded(true)
    if (context.maxMode.getState().conversationId !== id) await context.maxMode.openConversation(id)
    else await context.maxMode.refreshConversation()
    if (!keepDetail) store.update({ view: 'chat' })
    if (id === (request.conversation ?? request.episodes?.[request.episodes.length - 1])?.conversationId
      && shell.chatVisible() && document.visibilityState !== 'hidden') await store.markSeen(store.getState().request ?? request)
  }
  async function selectRequest(id: string) {
    await store.detail(id)
    ensureActive()
    const request = store.getState().request!
    host.onRequestSelected?.(request)
    if (['DELIVERED', 'NOT_REQUIRED'].includes(request.conversation?.delivery ?? '')) await openEpisode(request, undefined, true)
  }
  const shell = new SupportShell(config, store, {
    chat: () => { ensureActive(); shell.setExpanded(true); store.update({ view: 'chat' }); if (initialized) context.maxMode.open() },
    submit: input => { void store.run(async () => {
      const request = await store.create(input)
      ensureActive(); shell.clearForm(); host.onRequestSelected?.(request)
      // Saving a request is independent of the runtime; any later error leaves its real receipt visible.
      await prepare(request)
      const started = await store.start(request)
      ensureActive()
      const conversationId = started.conversation?.conversationId
      if (conversationId && started.conversation?.delivery !== 'PENDING') await openEpisode(started, conversationId)
    }) },
    list: append => { shell.setExpanded(true); store.update({ view: 'requests' }); void store.run(() => store.list(append)) },
    detail: id => { void store.run(() => selectRequest(id)) },
    openEpisode: (request, id) => { void store.run(() => openEpisode(request, id)) },
    start: request => { void store.run(async () => {
      // An uncertain opening is read back, never replayed by a check-delivery control.
      await store.detail(request.id)
      const current = store.getState().request!
      if (['DELIVERED', 'NOT_REQUIRED'].includes(current.conversation?.delivery ?? '')) await openEpisode(current, undefined, true)
    }) },
    transition: operation => { void store.run(() => store.transition(operation)) },
    canContinue: () => initialized && store.canContinue(currentChat()),
    continueConversation: () => { void store.run(async () => {
      const request = await store.newEpisode(currentChat())
      ensureActive(); host.onRequestSelected?.(request)
      // Continuation is an empty canonical episode, never another opening-intake submission.
      await openEpisode(request, undefined, true)
    }) },
    signIn: () => { void Promise.resolve((host.requestSignIn ?? context.credentials?.requestSignIn)?.()).catch(() => store.update({ error: 'Sign-in could not be completed.' })) },
    notifications: enabled => { void store.run(() => store.setNotifications(enabled)) },
    currentConversation: () => {
      const id = initialized ? currentChat().conversationId : null
      return id && !linkedConversations.has(id) ? id : null
    },
    layout,
  }, { ...host, requestSignIn: host.requestSignIn ?? context.credentials?.requestSignIn })
  const place = () => {
    if (destroyed) return
    if (mode === 'page') {
      const current = selector ? document.getElementById(selector.slice(1)) : container
      if (current?.isConnected) {
        container = current
        if (shell.element.parentNode !== current) current.append(shell.element)
        shell.element.hidden = !hostVisible
      } else { shell.element.hidden = true; context.workspaceFrame.setTarget(null) }
    } else {
      if (shell.element.parentNode !== document.body) document.body.append(shell.element)
      shell.element.hidden = !hostVisible
    }
    layout()
  }
  const releaseStore = store.subscribe(state => {
    for (const request of [...state.requests, ...(state.request ? [state.request] : [])]) {
      if (request.conversation) linkedConversations.add(request.conversation.conversationId)
      for (const episode of request.episodes ?? []) linkedConversations.add(episode.conversationId)
    }
    shell.render(state)
    if (initialized && state.request && context.maxMode.setReadOnly) {
      const id = context.maxMode.getState().conversationId
      if (id && (state.request.conversation?.conversationId === id || state.request.episodes?.some(episode => episode.conversationId === id))) {
        context.maxMode.setReadOnly(episodeReadOnly(state.request, id))
      }
    }
  })
  const observer = new MutationObserver(place)
  let poll: ReturnType<typeof setInterval>
  const refresh = async () => {
    ensureActive()
    if (!requesterEnabled || document.visibilityState === 'hidden') return
    const state = store.getState()
    if (state.view === 'form') return
    if (state.view === 'requests') await store.run(() => store.list())
    else if (state.view === 'detail' && state.request) await store.run(() => store.detail(state.request!.id))
    else if (initialized && shell.chatVisible() && currentChat().conversationId && !currentChat().busy) {
      await store.run(async () => { await context.maxMode.refreshConversation() }, true)
    }
  }
  const onResize = () => layout()
  const controller: SupportExperienceController = {
    installationId: context.installationId,
    setPresentation(next, nextContainer) {
      ensureActive()
      if (!['widget', 'page', 'standalone'].includes(next)) throw new Error('Unsupported support presentation.')
      if (next === 'page' && (!nextContainer || !nextContainer.isConnected || nextContainer === document.body || nextContainer === document.documentElement)) {
        throw new Error('Page presentation requires a connected local container.')
      }
      mode = next; container = nextContainer; selector = nextContainer?.id ? `#${nextContainer.id}` : undefined
      shell.setMode(mode); store.update({ presentation: mode }); place()
    },
    setVisible(visible) { ensureActive(); hostVisible = visible; place() },
    setTheme(mode) {
      ensureActive()
      if (!['light', 'dark', 'auto'].includes(mode)) throw new Error('Unsupported support color scheme.')
      theme = { ...theme, darkMode: mode === 'auto' ? 'auto' : mode === 'dark' }
      shell.setTheme(theme)
      if (initialized) {
        if (!context.maxMode.setTheme) throw new Error('The chat engine cannot update its theme without remounting.')
        context.maxMode.setTheme(theme)
      }
    },
    openChat() { ensureActive(); hostVisible = true; shell.setExpanded(true); store.update({ view: 'chat' }); place(); if (initialized) context.maxMode.open() },
    openRequestForm() { ensureActive(); if (p.tickets) shell.showForm() },
    async showRequests() { ensureActive(); if (!p.tickets) return; shell.setExpanded(true); store.update({ view: 'requests' }); await store.run(() => store.list()) },
    async openRequest(id) { ensureActive(); if (!p.tickets) return; shell.setExpanded(true); await store.run(() => selectRequest(id)) },
    refresh,
    getState() { ensureActive(); return store.getState() },
    onStateChange(listener) { ensureActive(); return store.subscribe(listener) },
    destroy() {
      if (destroyed) return
      destroyed = true; activity.destroy(); clearInterval(poll); observer.disconnect(); releaseStore(); releaseChat?.()
      removeEventListener('resize', onResize)
      store.destroy()
      try { shell.destroy() } finally {
        try { context.workspaceFrame.destroy() } finally { if (activeController === controller) activeController = undefined }
      }
    },
  }
  activeController = controller
  try {
    shell.setMode(mode)
    store.update({ presentation: mode })
    place()
    observer.observe(document.body, { childList: true, subtree: true })
    addEventListener('resize', onResize)
    // The engine owns live chat delivery. Shell polling must not replace chat controls on focus.
    poll = setInterval(() => {
      const view = store.getState().view
      if (shell.visible() && (view === 'requests' || view === 'detail')) void refresh()
    }, 15000)
    if (requesterEnabled && context.credentials?.getIdentity?.()?.status !== 'anonymous') await store.run(() => store.loadSession())
    try {
      if (host.fetchWorkspace) {
        const bootstrap = fromWorkspace(await host.fetchWorkspace())
        ensureActive(); initializeChat(bootstrap)
        if (bootstrap.conversationId) await context.maxMode.openConversation(bootstrap.conversationId)
        host.onRequestSelected?.(null)
      } else if (context.widgetConfig.apiConfig?.chatBaseUrl) initializeChat({ widgetConfig: context.widgetConfig, hostSession: context.hostSession })
    } catch (error) {
      ensureActive()
      store.update({ error: 'The conversation is unavailable. Saved requests can still be viewed and managed.' })
    }
    return controller
  } catch (error) { controller.destroy(); throw error }
}

function unavailableApi(): SupportRequesterApi {
  const unavailable = async (): Promise<never> => { throw new Error('Tracked requests are not configured.') }
  return { session: unavailable, team: unavailable, list: unavailable, get: unavailable, create: unavailable, start: unavailable, transition: unavailable, seen: unavailable }
}
