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

const root = document.querySelector<HTMLElement>('[data-dealership-staff]')

if (root) {
  void startStaffWorkspace(root)
}

async function startStaffWorkspace(app: HTMLElement) {
  let apiBaseUrl = ''
  let activeLeadId = ''
  const loginPanel = required<HTMLElement>(app, '[data-login-panel]')
  const workspace = required<HTMLElement>(app, '[data-staff-workspace]')
  const loginForm = required<HTMLFormElement>(app, '[data-login-form]')
  const statusForm = required<HTMLFormElement>(app, '[data-lead-status-form]')

  try {
    apiBaseUrl = await resolveApiBaseUrl(app)
  } catch (error) {
    showAlert(app, messageOf(error))
    disableForm(loginForm)
    return
  }

  const showLogin = () => {
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
