type StaffSession = {
  success: boolean
  authenticated: boolean
  username: string
}

type LeadSummary = {
  id: string
  receiptCode: string
  actionType: string
  status: 'NEW' | 'CONTACTED' | 'COMPLETED' | 'CANCELLED'
  vehicleId: string
  vehicle: string
  createdAt: string
  updatedAt: string
}

type LeadDetail = {
  lead: LeadSummary
  contact: Record<string, string>
}

type IntegrationStatus = {
  runtimeConfigured?: boolean
  vectorSpace?: string
  sourceId?: string
  available?: boolean
  message?: string
  source?: {
    sourceId?: string
    enabled?: boolean
    freshnessState?: string
    lagSeconds?: number
    targetedRecordFetchEnabled?: boolean
    state?: {
    status?: string
      lastSuccessAt?: string | null
      errorMessage?: string | null
      counts?: {
        sourceCount?: number
        normalizedCount?: number
        indexedCount?: number
        deletedCount?: number
        failedWorkCount?: number
      }
    }
  }
}

type ProviderSimulatorStatus = {
  enabled: boolean
  providerContract: string
  availableScenarios: string[]
  fixtureResetAt?: string | null
}

type ProviderScenarioReceipt = {
  scenario: string
  stockId: string
  mutationOperation: string
  eventId: string
  eventSequence: number
  deliveryStatus: number
  reconciliationStatus: string
  reconciliationAttempts: number
  reconciliationErrorClass?: string | null
}

type HumanOperator = {
  operatorId: string
  displayName: string
  bindingIds: string[]
}

type HumanConversationSummary = {
  handoverId: string
  externalConversationRef: string
  status: string
  safeCustomerLabel: string
  claimedBy?: string | null
  claimedDisplayName?: string | null
  unreadCount: number
  createdAt: string
  updatedAt: string
}

type HumanConversationMessage = {
  messageId: string
  participantRole: 'USER' | 'AI' | 'HUMAN' | 'SYSTEM'
  safeActorDisplayName?: string | null
  content: string
  createdAt: string
}

type HumanConversationDetail = {
  conversation: HumanConversationSummary
  consentedIntake?: Record<string, unknown>
  messages: HumanConversationMessage[]
}

type HumanInboxUiState = {
  enabled: boolean
  operator: HumanOperator | null
  selectedHandoverId: string
  refreshTask: Promise<void> | null
  pollTimer: number | null
}

const root = document.querySelector<HTMLElement>('[data-dealership-staff]')

if (root) {
  void startStaffWorkspace(root)
}

async function startStaffWorkspace(app: HTMLElement) {
  let apiBaseUrl = ''
  let activeLeadId = ''
  const humanState: HumanInboxUiState = {
    enabled: false,
    operator: null,
    selectedHandoverId: '',
    refreshTask: null,
    pollTimer: null,
  }
  const loginPanel = required<HTMLElement>(app, '[data-login-panel]')
  const workspace = required<HTMLElement>(app, '[data-staff-workspace]')
  const loginForm = required<HTMLFormElement>(app, '[data-login-form]')
  const statusForm = required<HTMLFormElement>(app, '[data-lead-status-form]')
  const humanReplyForm = required<HTMLFormElement>(app, '[data-human-reply-form]')

  try {
    apiBaseUrl = await resolveApiBaseUrl(app)
  } catch (error) {
    showAlert(app, messageOf(error))
    disableForm(loginForm)
    return
  }

  const showLogin = () => {
    stopHumanInboxPolling(humanState)
    loginPanel.hidden = false
    workspace.hidden = true
  }

  const showWorkspace = async (session: StaffSession) => {
    loginPanel.hidden = true
    workspace.hidden = false
    setText(app, '[data-staff-username]', session.username)
    await refreshWorkspace(app, apiBaseUrl, (id) => {
      activeLeadId = id
      void openLead(app, apiBaseUrl, id)
    })
    await refreshHumanInbox(app, apiBaseUrl, humanState)
    startHumanInboxPolling(app, apiBaseUrl, humanState)
  }

  try {
    const session = await fetchApi<StaffSession>(apiBaseUrl, '/api/staff/session', { method: 'GET' })
    if (session.authenticated) await showWorkspace(session)
    else showLogin()
  } catch (error) {
    if (isUnauthorized(error)) showLogin()
    else {
      showLogin()
      showAlert(app, messageOf(error))
    }
  }

  loginForm.addEventListener('submit', async (event) => {
    event.preventDefault()
    clearAlert(app)
    const submit = required<HTMLButtonElement>(loginForm, 'button[type="submit"]')
    submit.disabled = true
    try {
      const data = new FormData(loginForm)
      const session = await fetchApi<StaffSession>(apiBaseUrl, '/api/staff/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: data.get('username')?.toString() || '',
          password: data.get('password')?.toString() || '',
        }),
      })
      loginForm.reset()
      await showWorkspace(session)
    } catch (error) {
      showAlert(app, isUnauthorized(error) ? 'The staff username or password was not accepted.' : messageOf(error))
    } finally {
      submit.disabled = false
    }
  })

  app.querySelector('[data-refresh-workspace]')?.addEventListener('click', () => {
    void refreshWorkspace(app, apiBaseUrl, (id) => {
      activeLeadId = id
      void openLead(app, apiBaseUrl, id)
    })
    void refreshHumanInbox(app, apiBaseUrl, humanState)
  })

  app.querySelector('[data-refresh-human]')?.addEventListener('click', () => {
    void refreshHumanInbox(app, apiBaseUrl, humanState)
  })

  app.querySelector('[data-human-claim]')?.addEventListener('click', () => {
    void runHumanCommand(app, apiBaseUrl, humanState, 'claim')
  })

  app.querySelector('[data-human-return]')?.addEventListener('click', () => {
    void runHumanCommand(app, apiBaseUrl, humanState, 'return-to-ai')
  })

  app.querySelector('[data-human-close]')?.addEventListener('click', () => {
    void runHumanCommand(app, apiBaseUrl, humanState, 'close')
  })

  humanReplyForm.addEventListener('submit', async (event) => {
    event.preventDefault()
    if (!humanState.selectedHandoverId) return
    const textarea = required<HTMLTextAreaElement>(humanReplyForm, 'textarea[name="reply"]')
    const text = textarea.value.trim()
    if (!text) return
    const submit = required<HTMLButtonElement>(humanReplyForm, 'button[type="submit"]')
    submit.disabled = true
    try {
      await runHumanCommand(app, apiBaseUrl, humanState, 'messages', { text })
      textarea.value = ''
    } finally {
      if (!textarea.disabled) submit.disabled = false
    }
  })

  app.querySelector('[data-run-reconcile]')?.addEventListener('click', async (event) => {
    const button = event.currentTarget as HTMLButtonElement
    button.disabled = true
    clearAlert(app)
    try {
      const csrf = await csrfHeaders(apiBaseUrl)
      await fetchApi(apiBaseUrl, '/api/staff/integration/reconcile', { method: 'POST', headers: csrf })
      await refreshWorkspace(app, apiBaseUrl, (id) => {
        activeLeadId = id
        void openLead(app, apiBaseUrl, id)
      })
    } catch (error) {
      showAlert(app, messageOf(error))
    } finally {
      button.disabled = false
    }
  })

  app.querySelectorAll<HTMLButtonElement>('[data-provider-scenario]').forEach((button) => {
    button.addEventListener('click', async () => {
      const scenario = button.dataset.providerScenario
      if (!scenario) return
      setScenarioButtonsDisabled(app, true)
      clearAlert(app)
      setText(app, '[data-provider-simulator-receipt]', 'Changing provider stock and delivering the signed notification...')
      try {
        const csrf = await csrfHeaders(apiBaseUrl)
        const response = await fetchApi<{ receipt: ProviderScenarioReceipt }>(
          apiBaseUrl,
          `/api/staff/provider-simulator/scenarios/${encodeURIComponent(scenario)}`,
          { method: 'POST', headers: csrf },
        )
        const receipt = response.receipt
        setText(
          app,
          '[data-provider-simulator-receipt]',
          `${titleCase(receipt.scenario.replaceAll('-', ' '))}: ${receipt.stockId} · ${receipt.mutationOperation.toLowerCase()} · notification HTTP ${receipt.deliveryStatus} · reconciliation ${receipt.reconciliationStatus.toLowerCase()} (${receipt.reconciliationAttempts} attempt${receipt.reconciliationAttempts === 1 ? '' : 's'}) · event ${receipt.eventId}`,
        )
        await refreshWorkspace(app, apiBaseUrl, (id) => {
          activeLeadId = id
          void openLead(app, apiBaseUrl, id)
        }, false)
      } catch (error) {
        showAlert(app, messageOf(error))
        setText(app, '[data-provider-simulator-receipt]', 'The provider scenario did not complete.')
      } finally {
        setScenarioButtonsDisabled(app, false)
      }
    })
  })

  app.querySelector('[data-logout]')?.addEventListener('click', async () => {
    clearAlert(app)
    try {
      const csrf = await csrfHeaders(apiBaseUrl)
      await fetchApi(apiBaseUrl, '/api/staff/session', { method: 'DELETE', headers: csrf })
    } catch (error) {
      if (!isUnauthorized(error)) showAlert(app, messageOf(error))
    }
    resetHumanInbox(app, humanState)
    showLogin()
  })

  app.querySelector('[data-close-lead]')?.addEventListener('click', () => {
    required<HTMLDialogElement>(app, '[data-lead-dialog]').close()
  })

  required<HTMLDialogElement>(app, '[data-lead-dialog]').addEventListener('click', (event) => {
    if (event.target === event.currentTarget) {
      (event.currentTarget as HTMLDialogElement).close()
    }
  })

  statusForm.addEventListener('submit', async (event) => {
    event.preventDefault()
    if (!activeLeadId) return
    const submit = required<HTMLButtonElement>(statusForm, 'button[type="submit"]')
    submit.disabled = true
    clearAlert(app)
    try {
      const csrf = await csrfHeaders(apiBaseUrl)
      const status = new FormData(statusForm).get('status')?.toString() || ''
      await fetchApi(apiBaseUrl, `/api/staff/leads/${encodeURIComponent(activeLeadId)}/status`, {
        method: 'PATCH',
        headers: { ...csrf, 'Content-Type': 'application/json' },
        body: JSON.stringify({ status }),
      })
      required<HTMLDialogElement>(app, '[data-lead-dialog]').close()
      await refreshWorkspace(app, apiBaseUrl, (id) => {
        activeLeadId = id
        void openLead(app, apiBaseUrl, id)
      })
    } catch (error) {
      showAlert(app, messageOf(error))
    } finally {
      submit.disabled = false
    }
  })
}

function startHumanInboxPolling(app: HTMLElement, apiBaseUrl: string, state: HumanInboxUiState) {
  stopHumanInboxPolling(state)
  state.pollTimer = window.setInterval(() => {
    if (!document.hidden) void refreshHumanInbox(app, apiBaseUrl, state, true)
  }, 4000)
}

function stopHumanInboxPolling(state: HumanInboxUiState) {
  if (state.pollTimer != null) window.clearInterval(state.pollTimer)
  state.pollTimer = null
}

function resetHumanInbox(app: HTMLElement, state: HumanInboxUiState) {
  stopHumanInboxPolling(state)
  state.enabled = false
  state.operator = null
  state.selectedHandoverId = ''
  required<HTMLElement>(app, '[data-human-inbox]').hidden = true
}

async function refreshHumanInbox(
  app: HTMLElement,
  apiBaseUrl: string,
  state: HumanInboxUiState,
  quiet = false,
) {
  if (state.refreshTask) {
    await state.refreshTask
    return
  }

  const task = performHumanInboxRefresh(app, apiBaseUrl, state, quiet)
  state.refreshTask = task.finally(() => {
    state.refreshTask = null
  })
  await state.refreshTask
}

async function performHumanInboxRefresh(
  app: HTMLElement,
  apiBaseUrl: string,
  state: HumanInboxUiState,
  quiet: boolean,
) {
  const panel = required<HTMLElement>(app, '[data-human-inbox]')
  try {
    const status = await fetchApi<{ enabled: boolean; operator?: HumanOperator; message?: string }>(
      apiBaseUrl,
      '/api/staff/human-conversations/status',
      { method: 'GET' },
    )
    state.enabled = status.enabled === true
    state.operator = status.operator || null
    panel.hidden = !state.enabled
    if (!state.enabled) return

    setText(app, '[data-human-operator]', state.operator?.displayName || 'Dealership operator')
    const response = await fetchApi<{ items: HumanConversationSummary[] }>(
      apiBaseUrl,
      '/api/staff/human-conversations?limit=100',
      { method: 'GET' },
    )
    const conversations = Array.isArray(response.items) ? response.items : []
    setText(app, '[data-human-count]', String(conversations.filter((item) => item.status !== 'CLOSED').length))
    renderHumanQueue(app, conversations, state, (handoverId) => {
      state.selectedHandoverId = handoverId
      app.querySelectorAll<HTMLElement>('[data-human-handover-id]').forEach((item) => {
        const selected = item.dataset.humanHandoverId === handoverId
        item.dataset.selected = String(selected)
        item.setAttribute('aria-pressed', String(selected))
      })
      void openHumanConversation(app, apiBaseUrl, state)
    })

    if (state.selectedHandoverId) {
      await openHumanConversation(app, apiBaseUrl, state)
    }
  } catch (error) {
    panel.hidden = false
    setText(app, '[data-human-operator]', 'Inbox unavailable')
    showHumanError(app, messageOf(error))
    if (!quiet && isUnauthorized(error)) showAlert(app, 'Your staff session expired. Sign in again.')
  }
}

function renderHumanQueue(
  app: HTMLElement,
  conversations: HumanConversationSummary[],
  state: HumanInboxUiState,
  onSelect: (handoverId: string) => void,
) {
  const root = required<HTMLElement>(app, '[data-human-queue]')
  root.replaceChildren()
  if (conversations.length === 0) {
    const empty = document.createElement('p')
    empty.className = 'human-inbox__empty-copy'
    empty.textContent = 'No customers are waiting for a human conversation.'
    root.append(empty)
    return
  }

  for (const conversation of conversations) {
    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'human-queue-item'
    button.dataset.humanHandoverId = conversation.handoverId
    button.dataset.selected = String(conversation.handoverId === state.selectedHandoverId)
    button.setAttribute('aria-pressed', button.dataset.selected)

    const top = document.createElement('span')
    top.className = 'human-queue-item__top'
    const customer = document.createElement('strong')
    customer.textContent = conversation.safeCustomerLabel || 'Anonymous customer'
    const status = document.createElement('span')
    status.dataset.state = conversation.status
    status.textContent = humanStatusLabel(conversation.status)
    top.append(customer, status)

    const meta = document.createElement('small')
    meta.textContent = `${conversation.unreadCount || 0} unread · ${formatDateTime(conversation.updatedAt)}`
    button.append(top, meta)
    button.addEventListener('click', () => onSelect(conversation.handoverId))
    root.append(button)
  }
}

async function openHumanConversation(app: HTMLElement, apiBaseUrl: string, state: HumanInboxUiState) {
  if (!state.selectedHandoverId) return
  try {
    const response = await fetchApi<{ item: HumanConversationDetail }>(
      apiBaseUrl,
      `/api/staff/human-conversations/${encodeURIComponent(state.selectedHandoverId)}`,
      { method: 'GET' },
    )
    renderHumanConversation(app, response.item, state.operator)
  } catch (error) {
    showHumanError(app, messageOf(error))
  }
}

function renderHumanConversation(app: HTMLElement, detail: HumanConversationDetail, operator: HumanOperator | null) {
  required<HTMLElement>(app, '[data-human-empty]').hidden = true
  required<HTMLElement>(app, '[data-human-conversation]').hidden = false
  hideHumanError(app)
  setText(app, '[data-human-reference]', detail.conversation.externalConversationRef || 'Customer conversation')
  setText(app, '[data-human-customer]', detail.conversation.safeCustomerLabel || 'Anonymous customer')
  const stateBadge = required<HTMLElement>(app, '[data-human-state]')
  stateBadge.textContent = humanStatusLabel(detail.conversation.status)
  stateBadge.dataset.state = detail.conversation.status

  const intake = required<HTMLElement>(app, '[data-human-intake]')
  const intakeEntries = Object.entries(detail.consentedIntake || {})
  intake.hidden = intakeEntries.length === 0
  intake.textContent = intakeEntries.map(([key, value]) => `${titleCase(key)}: ${String(value)}`).join(' · ')

  const messages = required<HTMLElement>(app, '[data-human-messages]')
  messages.replaceChildren()
  for (const message of detail.messages || []) {
    const item = document.createElement('article')
    item.className = 'human-message'
    item.dataset.role = message.participantRole
    const meta = document.createElement('div')
    meta.className = 'human-message__meta'
    const actor = document.createElement('strong')
    actor.textContent = message.safeActorDisplayName || participantLabel(message.participantRole)
    const time = document.createElement('time')
    time.textContent = formatDateTime(message.createdAt)
    meta.append(actor, time)
    const content = document.createElement('p')
    content.textContent = message.content
    item.append(meta, content)
    messages.append(item)
  }
  messages.scrollTop = messages.scrollHeight

  const ownActive = detail.conversation.status === 'ACTIVE'
    && Boolean(operator?.operatorId)
    && detail.conversation.claimedBy === operator?.operatorId
  required<HTMLButtonElement>(app, '[data-human-claim]').hidden = !['WAITING', 'CLAIM_PENDING'].includes(detail.conversation.status)
  required<HTMLButtonElement>(app, '[data-human-return]').hidden = !ownActive
  required<HTMLButtonElement>(app, '[data-human-close]').hidden = !ownActive
  const reply = required<HTMLTextAreaElement>(app, '[data-human-reply-form] textarea[name="reply"]')
  const send = required<HTMLButtonElement>(app, '[data-human-reply-form] button[type="submit"]')
  reply.disabled = !ownActive
  send.disabled = !ownActive
  reply.placeholder = ownActive ? 'Reply to this customer...' : 'Claim this conversation to reply...'
}

async function runHumanCommand(
  app: HTMLElement,
  apiBaseUrl: string,
  state: HumanInboxUiState,
  operation: 'claim' | 'messages' | 'return-to-ai' | 'close',
  values: { text?: string } = {},
) {
  if (!state.selectedHandoverId) return
  hideHumanError(app)
  try {
    const csrf = await csrfHeaders(apiBaseUrl)
    await fetchApi(
      apiBaseUrl,
      `/api/staff/human-conversations/${encodeURIComponent(state.selectedHandoverId)}/${operation}`,
      {
        method: 'POST',
        headers: { ...csrf, 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...values, idempotencyKey: crypto.randomUUID() }),
      },
    )
    if (operation === 'close') state.selectedHandoverId = ''
    if (state.refreshTask) await state.refreshTask
    await refreshHumanInbox(app, apiBaseUrl, state)
    if (!state.selectedHandoverId) {
      required<HTMLElement>(app, '[data-human-empty]').hidden = false
      required<HTMLElement>(app, '[data-human-conversation]').hidden = true
    }
  } catch (error) {
    showHumanError(app, messageOf(error))
  }
}

function showHumanError(app: HTMLElement, message: string) {
  const error = required<HTMLElement>(app, '[data-human-error]')
  error.textContent = message
  error.hidden = false
}

function hideHumanError(app: HTMLElement) {
  required<HTMLElement>(app, '[data-human-error]').hidden = true
}

function humanStatusLabel(status: string) {
  if (status === 'WAITING' || status === 'CLAIM_PENDING') return 'Waiting'
  if (status === 'ACTIVE') return 'Human active'
  if (status === 'RETURN_PENDING') return 'Returning to AI'
  return titleCase(status.toLowerCase().replaceAll('_', ' '))
}

function participantLabel(role: HumanConversationMessage['participantRole']) {
  if (role === 'USER') return 'Customer'
  if (role === 'HUMAN') return 'Dealership team'
  if (role === 'AI') return 'Northfield AI'
  return 'System'
}

async function refreshWorkspace(
  app: HTMLElement,
  apiBaseUrl: string,
  onOpenLead: (id: string) => void,
  refreshSimulatorReceipt = true,
) {
  clearAlert(app)
  try {
    const [publicStatus, integrationResponse, leadResponse, simulatorResponse] = await Promise.all([
      fetchApi<{ inventoryCount: number; runtimeConfigured: boolean }>(apiBaseUrl, '/api/public/status', { method: 'GET' }),
      fetchApi<{ integration: IntegrationStatus }>(apiBaseUrl, '/api/staff/integration/status', { method: 'GET' }),
      fetchApi<{ items: LeadSummary[] }>(apiBaseUrl, '/api/staff/leads?limit=50', { method: 'GET' }),
      fetchApi<{ simulator: ProviderSimulatorStatus }>(apiBaseUrl, '/api/staff/provider-simulator/status', { method: 'GET' }),
    ])

    setText(app, '[data-staff-inventory-count]', String(publicStatus.inventoryCount ?? 0))
    setText(app, '[data-staff-runtime-status]', publicStatus.runtimeConfigured ? 'Configured' : 'Not configured')
    setText(
      app,
      '[data-staff-runtime-detail]',
      publicStatus.runtimeConfigured ? 'assigned public and private routes' : 'runtime integration disabled',
    )

    const integration = integrationResponse.integration || {}
    const source = integration.source
    const state = source?.state
    const counts = state?.counts
    setText(
      app,
      '[data-staff-sync-status]',
      integration.available ? (source?.freshnessState || state?.status || 'Ready') : 'Unavailable',
    )
    setText(
      app,
      '[data-staff-sync-detail]',
      integration.available
        ? `${counts?.indexedCount || 0} indexed · ${counts?.deletedCount || 0} deleted · targeted ${source?.targetedRecordFetchEnabled ? 'on' : 'off'}`
        : (integration.message || `source: ${integration.sourceId || 'not configured'}`),
    )
    renderLeads(app, leadResponse.items || [], onOpenLead)
    renderProviderSimulator(app, simulatorResponse.simulator, refreshSimulatorReceipt)
  } catch (error) {
    showAlert(app, messageOf(error))
    if (isUnauthorized(error)) {
      required<HTMLElement>(app, '[data-staff-workspace]').hidden = true
      required<HTMLElement>(app, '[data-login-panel]').hidden = false
    }
  }
}

function renderProviderSimulator(app: HTMLElement, status: ProviderSimulatorStatus, refreshReceipt: boolean) {
  const panel = required<HTMLElement>(app, '[data-provider-simulator-panel]')
  panel.hidden = !status.enabled
  if (!status.enabled) return
  setText(app, '[data-provider-simulator-status]', 'Contract fixture ready')
  const allowed = new Set(status.availableScenarios || [])
  app.querySelectorAll<HTMLButtonElement>('[data-provider-scenario]').forEach((button) => {
    button.disabled = !button.dataset.providerScenario || !allowed.has(button.dataset.providerScenario)
  })
  if (refreshReceipt) {
    setText(
      app,
      '[data-provider-simulator-receipt]',
      status.fixtureResetAt
        ? `Simulator fixture active since ${formatDateTime(status.fixtureResetAt)}.`
        : 'The provider simulator is ready.',
    )
  }
}

function setScenarioButtonsDisabled(app: HTMLElement, disabled: boolean) {
  app.querySelectorAll<HTMLButtonElement>('[data-provider-scenario]').forEach((button) => {
    button.disabled = disabled
  })
}

function renderLeads(app: HTMLElement, leads: LeadSummary[], onOpenLead: (id: string) => void) {
  const body = required<HTMLTableSectionElement>(app, '[data-lead-table-body]')
  const template = required<HTMLTemplateElement>(app, '[data-lead-row-template]')
  body.replaceChildren()
  setText(app, '[data-lead-count]', String(leads.length))

  if (leads.length === 0) {
    const row = document.createElement('tr')
    const cell = document.createElement('td')
    cell.colSpan = 6
    cell.textContent = 'No confirmed customer requests have arrived yet.'
    row.append(cell)
    body.append(row)
    return
  }

  for (const lead of leads) {
    const row = template.content.firstElementChild?.cloneNode(true) as HTMLTableRowElement | undefined
    if (!row) continue
    setText(row, '[data-lead-receipt]', lead.receiptCode)
    setText(row, '[data-lead-action]', actionLabel(lead.actionType))
    setText(row, '[data-lead-vehicle]', lead.vehicle)
    const status = required<HTMLElement>(row, '[data-lead-status]')
    status.textContent = statusLabel(lead.status)
    status.dataset.status = lead.status
    setText(row, '[data-lead-created]', formatDateTime(lead.createdAt))
    const open = required<HTMLButtonElement>(row, '[data-open-lead]')
    open.setAttribute('aria-label', `Open request ${lead.receiptCode}`)
    open.addEventListener('click', () => onOpenLead(lead.id))
    body.append(row)
  }
}

async function openLead(app: HTMLElement, apiBaseUrl: string, id: string) {
  clearAlert(app)
  try {
    const response = await fetchApi<{ item: LeadDetail }>(apiBaseUrl, `/api/staff/leads/${encodeURIComponent(id)}`, { method: 'GET' })
    const detail = response.item
    setText(app, '[data-lead-dialog-receipt]', detail.lead.receiptCode)
    renderDefinitionList(required<HTMLElement>(app, '[data-lead-summary]'), {
      Request: actionLabel(detail.lead.actionType),
      Vehicle: detail.lead.vehicle,
      Status: statusLabel(detail.lead.status),
      Created: formatDateTime(detail.lead.createdAt),
    })
    renderDefinitionList(required<HTMLElement>(app, '[data-lead-contact]'), detail.contact)
    required<HTMLSelectElement>(app, '[data-lead-status-form] select[name="status"]').value = detail.lead.status
    required<HTMLDialogElement>(app, '[data-lead-dialog]').showModal()
  } catch (error) {
    showAlert(app, messageOf(error))
  }
}

function renderDefinitionList(list: HTMLElement, values: Record<string, unknown>) {
  list.replaceChildren()
  for (const [label, raw] of Object.entries(values)) {
    const group = document.createElement('div')
    const term = document.createElement('dt')
    const description = document.createElement('dd')
    term.textContent = titleCase(label)
    description.textContent = raw == null || raw === '' ? 'Not supplied' : String(raw)
    group.append(term, description)
    list.append(group)
  }
}

async function csrfHeaders(apiBaseUrl: string) {
  const response = await fetchApi<{ headerName: string; token: string }>(apiBaseUrl, '/api/public/security/csrf', { method: 'GET' })
  if (!response.headerName || !response.token) throw new Error('The dealership CSRF contract was unavailable.')
  return { [response.headerName]: response.token }
}

async function fetchApi<T = unknown>(apiBaseUrl: string, path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    credentials: 'include',
    cache: 'no-store',
    headers: { Accept: 'application/json', ...init.headers },
  })
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message = data && typeof data.message === 'string' ? data.message : `Request failed with HTTP ${response.status}.`
    throw new StaffApiError(response.status, message)
  }
  return data as T
}

class StaffApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
  }
}

function isUnauthorized(error: unknown) {
  return error instanceof StaffApiError && (error.status === 401 || error.status === 403)
}

async function resolveApiBaseUrl(app: HTMLElement) {
  try {
    const response = await fetch('/runtime-config/dealership-demo.json', { cache: 'no-store' })
    if (response.ok) {
      const config = await response.json() as { ready?: boolean; apiBaseUrl?: string | null }
      if (config.ready && config.apiBaseUrl) return normalizeBaseUrl(config.apiBaseUrl)
    }
  } catch {
    // Astro development uses the explicit public fallback below.
  }
  const fallback = app.dataset.developmentApiBaseUrl
  if (fallback) return normalizeBaseUrl(fallback)
  throw new Error('The dealership demo backend is not configured.')
}

function normalizeBaseUrl(value: string) {
  const parsed = new URL(value)
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('The dealership API URL is invalid.')
  return parsed.toString().replace(/\/$/, '')
}

function actionLabel(value: string) {
  if (value === 'dealership_request_test_drive') return 'Test drive'
  if (value === 'dealership_request_callback') return 'Callback'
  return titleCase(value.replace(/^dealership_/, '').replaceAll('_', ' '))
}

function statusLabel(value: string) {
  return titleCase(value.toLowerCase())
}

function titleCase(value: string) {
  return value.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (letter) => letter.toUpperCase())
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Time unavailable'
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function showAlert(app: HTMLElement, message: string) {
  const alert = required<HTMLElement>(app, '[data-staff-alert]')
  required<HTMLElement>(alert, 'span').textContent = message
  alert.hidden = false
}

function clearAlert(app: HTMLElement) {
  required<HTMLElement>(app, '[data-staff-alert]').hidden = true
}

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : 'The staff request could not be completed.'
}

function disableForm(form: HTMLFormElement) {
  form.querySelectorAll<HTMLInputElement | HTMLButtonElement>('input, button').forEach((control) => {
    control.disabled = true
  })
}

function required<T extends Element>(rootElement: ParentNode, selector: string): T {
  const element = rootElement.querySelector<T>(selector)
  if (!element) throw new Error(`Required staff workspace element is missing: ${selector}`)
  return element
}

function setText(rootElement: ParentNode, selector: string, value: string) {
  required<HTMLElement>(rootElement, selector).textContent = value
}

export {}
