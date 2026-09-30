type FacetValue = {
  value: string
  count: number
}

type Vehicle = {
  id: string
  stockId: string
  slug: string
  make: string
  model: string
  derivative: string
  registrationYear: number
  priceGbp: number
  priceFormatted: string
  currency: string
  mileage: number
  fuelType: string
  transmission: string
  bodyType: string
  exteriorColour: string
  doors: number
  seats: number
  electricRangeMiles: number | null
  location: string
  lifecycleState: string
  summary: string
  features: string[]
  imagePath: string
  sourceLabel: string
  sourceUpdatedAt: string
  sourceVersion: number
}

type InventoryResponse = {
  success: boolean
  items: Vehicle[]
  total: number
  facets: Record<'makes' | 'fuelTypes' | 'bodyTypes', FacetValue[]>
  source: {
    label: string
    refreshedAt: string
  }
  dataNotice: string
}

type RuntimeDescriptor = {
  success: boolean
  ready: boolean
  integrationMode: 'public-runtime-anonymous'
  chatBaseUrl: string
  runtimeRoutes: {
    bootstrapUrl: string
    renewUrl: string
    queryUrl: string
    suggestionsUrl: string
    authContextUrl: string
    shellConfigUrl: string
    conversationsUrl: string
    conversationItemUrlTemplate: string
  }
  vectorSpace: string
}

type MaxModeBrowserApi = {
  init: (config: Record<string, unknown>) => void
  attachItem: (item: { type: string; data: Record<string, unknown> }) => void
  sendMessage: (message: string, options?: Record<string, unknown>) => void
}

declare global {
  interface Window {
    MaxMode?: MaxModeBrowserApi
  }
}

const root = document.querySelector<HTMLElement>('[data-dealership-demo]')

if (root) {
  void startDealershipDemo(root)
}

async function startDealershipDemo(app: HTMLElement) {
  const form = required<HTMLFormElement>(app, '[data-inventory-form]')
  const sort = required<HTMLSelectElement>(app, '[data-sort-select]')
  const grid = required<HTMLElement>(app, '[data-vehicle-grid]')
  const cardTemplate = required<HTMLTemplateElement>(app, '[data-vehicle-card-template]')
  const comparison = new Map<string, Vehicle>()
  let apiBaseUrl = ''
  let assistantReady = false
  let activeVehicle: Vehicle | null = null
  let requestSequence = 0
  let searchTimer: number | undefined

  bindDialogs(app)
  bindFilterPanel(app)

  const loadInventory = async () => {
    const sequence = ++requestSequence
    setInventoryLoading(app, true)
    hideInventoryMessage(app)
    try {
      if (!apiBaseUrl) {
        throw new Error('The dealership backend URL is not configured.')
      }
      const query = inventoryQuery(form, sort)
      const response = await fetchJson<InventoryResponse>(`${apiBaseUrl}/api/public/vehicles?${query}`)
      if (sequence !== requestSequence) return
      if (!response.success || !Array.isArray(response.items)) {
        throw new Error('The dealership returned an invalid inventory response.')
      }
      populateFacets(form, response.facets)
      renderInventory(app, grid, cardTemplate, response.items, comparison, assistantReady, {
        onDetails(vehicle) {
          activeVehicle = vehicle
          openVehicleDialog(app, vehicle, assistantReady)
        },
        onAsk(vehicle) {
          askAboutVehicle(app, vehicle, `Tell me whether the ${vehicle.registrationYear} ${vehicle.make} ${vehicle.model} fits an everyday driver, using current dealership facts.`)
        },
        onCompare(vehicle, checked, input) {
          if (checked && comparison.size >= 3 && !comparison.has(vehicle.id)) {
            input.checked = false
            showToast(app, 'Choose up to three vehicles for one comparison.')
            return
          }
          if (checked) comparison.set(vehicle.id, vehicle)
          else comparison.delete(vehicle.id)
          updateComparisonBar(app, comparison)
        },
      })
      renderActiveFilters(app, form, sort, loadInventory)
      updateInventorySummary(app, response)
      setInventoryLoading(app, false)
    } catch (error) {
      if (sequence !== requestSequence) return
      setInventoryLoading(app, false)
      grid.replaceChildren()
      showInventoryMessage(
        app,
        'Inventory unavailable',
        error instanceof Error ? error.message : 'The dealership service could not be reached.',
      )
    }
  }

  app.querySelector('[data-retry-inventory]')?.addEventListener('click', () => void loadInventory())
  app.querySelector('[data-refresh-inventory]')?.addEventListener('click', () => void loadInventory())
  app.querySelector('[data-clear-filters]')?.addEventListener('click', () => {
    form.reset()
    sort.value = 'recommended'
    void loadInventory()
  })

  form.addEventListener('input', (event) => {
    const target = event.target as HTMLInputElement | HTMLSelectElement
    if (target.name === 'q') {
      window.clearTimeout(searchTimer)
      searchTimer = window.setTimeout(() => void loadInventory(), 280)
      return
    }
    void loadInventory()
  })
  sort.addEventListener('change', () => void loadInventory())

  app.querySelector('[data-clear-comparison]')?.addEventListener('click', () => {
    comparison.clear()
    app.querySelectorAll<HTMLInputElement>('[data-card-compare]').forEach((input) => {
      input.checked = false
    })
    updateComparisonBar(app, comparison)
  })

  app.querySelector('[data-open-comparison]')?.addEventListener('click', () => {
    if (comparison.size < 2) {
      showToast(app, 'Choose at least two vehicles to compare.')
      return
    }
    openComparisonDialog(app, [...comparison.values()], assistantReady)
  })

  app.querySelector('[data-dialog-ask]')?.addEventListener('click', () => {
    if (!activeVehicle) return
    closeDialog(app, '[data-vehicle-dialog]')
    askAboutVehicle(app, activeVehicle, `Give me a grounded summary of the ${activeVehicle.registrationYear} ${activeVehicle.make} ${activeVehicle.model}, including price, mileage, useful features and any important trade-offs.`)
  })

  app.querySelector('[data-dialog-test-drive]')?.addEventListener('click', () => {
    if (!activeVehicle) return
    closeDialog(app, '[data-vehicle-dialog]')
    askAboutVehicle(app, activeVehicle, `I would like to request a test drive for the ${activeVehicle.registrationYear} ${activeVehicle.make} ${activeVehicle.model}.`)
  })

  app.querySelector('[data-ask-comparison]')?.addEventListener('click', () => {
    const vehicles = [...comparison.values()]
    if (vehicles.length < 2) return
    closeDialog(app, '[data-compare-dialog]')
    for (const vehicle of vehicles) {
      attachVehicle(vehicle)
    }
    const names = vehicles.map((vehicle) => `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}`).join(', ')
    sendAssistantMessage(app, `Compare these selected vehicles using current dealership facts: ${names}. Explain the meaningful trade-offs and do not invent unavailable specifications.`, {
      selectedVehicleIds: vehicles.map((vehicle) => vehicle.id),
    })
  })

  try {
    apiBaseUrl = await resolveApiBaseUrl(app)
    await Promise.all([
      loadInventory(),
      initializeAssistant(app, apiBaseUrl).then(() => {
        assistantReady = true
        setAssistantButtons(app, true)
      }),
    ])
  } catch (error) {
    if (apiBaseUrl) {
      void loadInventory()
    } else {
      setInventoryLoading(app, false)
      grid.replaceChildren()
      showInventoryMessage(app, 'Demo configuration unavailable', 'The public dealership backend URL has not been configured for this environment.')
    }
    setRuntimeState(
      app,
      'unavailable',
      'Assistant unavailable',
      error instanceof Error ? error.message : 'The assigned deployment could not be reached.',
    )
    setAssistantButtons(app, false)
  }
}

async function resolveApiBaseUrl(app: HTMLElement) {
  try {
    const response = await fetch('/runtime-config/dealership-demo.json', {
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    })
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

async function initializeAssistant(app: HTMLElement, apiBaseUrl: string) {
  setRuntimeState(app, 'checking', 'Connecting assistant', 'Checking the assigned LoomAI deployment')
  const descriptor = await fetchJson<RuntimeDescriptor>(`${apiBaseUrl}/api/public/runtime-descriptor`)
  if (!descriptor.success || !descriptor.ready || descriptor.integrationMode !== 'public-runtime-anonymous') {
    throw new Error('The assigned public runtime is not ready.')
  }
  const chatBaseUrl = normalizeBaseUrl(descriptor.chatBaseUrl)
  await loadWidgetBundle()
  if (!window.MaxMode) throw new Error('The LoomAI chat surface did not load.')

  const routes = descriptor.runtimeRoutes
  window.MaxMode.init({
    integrationMode: 'public-runtime-anonymous',
    apiConfig: {
      chatBaseUrl,
      runtimeRoutes: {
        chatQueryUrl: absoluteRuntimeUrl(chatBaseUrl, routes.queryUrl),
        suggestionsUrl: absoluteRuntimeUrl(chatBaseUrl, routes.suggestionsUrl),
        authContextUrl: absoluteRuntimeUrl(chatBaseUrl, routes.authContextUrl),
        shellConfigUrl: absoluteRuntimeUrl(chatBaseUrl, routes.shellConfigUrl),
        conversationsUrl: absoluteRuntimeUrl(chatBaseUrl, routes.conversationsUrl),
        conversationItemUrlTemplate: absoluteRuntimeTemplateUrl(chatBaseUrl, routes.conversationItemUrlTemplate),
      },
      runtimeAuth: {
        bootstrapUrl: absoluteRuntimeUrl(chatBaseUrl, routes.bootstrapUrl),
        renewUrl: absoluteRuntimeUrl(chatBaseUrl, routes.renewUrl),
        authContextUrl: absoluteRuntimeUrl(chatBaseUrl, routes.authContextUrl),
        probeAuthContextOnOpen: true,
      },
      probeShellConfigOnOpen: true,
    },
    features: {
      cart: false,
      debug: false,
      conversations: true,
      quickActions: true,
    },
    theme: {
      primaryColor: '#123b35',
      borderRadius: '0.5rem',
      fontFamily: 'Inter, system-ui, sans-serif',
      darkMode: false,
    },
    launcher: false,
    host: {
      assistantLabel: 'Northfield AI',
      welcomeMessage: 'I can search and compare Northfield demo inventory using indexed vehicle evidence and current dealership facts.',
      starterPrompts: [
        { label: 'Find an electric car', query: 'Show me electric cars in current stock.', position: 'search', mode: 'executor' },
        { label: 'Best family options', query: 'Which current vehicles are practical for a family?', position: 'search', mode: 'executor' },
        { label: 'Low-mileage stock', query: 'Find current vehicles with less than 10,000 miles.', position: 'search', mode: 'executor' },
      ],
      starterSuggestions: [
        'Compare electric cars',
        'What is under £30,000?',
        'Which car has the best luggage space?',
      ],
      requestContext: {
        dealershipId: 'dealer-demo-001',
        vectorSpace: descriptor.vectorSpace,
        entityType: descriptor.vectorSpace,
        preferredVectorSpaces: [descriptor.vectorSpace],
        sourceMode: 'DEMONSTRATION_INVENTORY',
      },
      defaultConversationMode: 'executor',
      effectiveConversationMode: 'executor',
      allowedConversationModes: ['executor'],
      showUtilityPanel: false,
      companionDock: true,
      companionContextLabel: 'Current fictional dealership inventory',
      companionModeLabel: 'Vehicle assistant',
      companionPlaceholder: 'Ask about a vehicle, feature, budget or comparison...',
      companionEmptyMessage: 'Ask about current vehicles, compare options, or start a confirmed callback or test-drive request.',
    },
    onEvent(event: { type?: string }) {
      if (event?.type === 'error') {
        setRuntimeState(app, 'unavailable', 'Assistant needs attention', 'The deployment returned an operational error')
      }
    },
  })

  setRuntimeState(app, 'ready', 'Assistant ready', 'Connected directly to the assigned LoomAI deployment')
}

function renderInventory(
  app: HTMLElement,
  grid: HTMLElement,
  template: HTMLTemplateElement,
  vehicles: Vehicle[],
  comparison: Map<string, Vehicle>,
  assistantReady: boolean,
  handlers: {
    onDetails: (vehicle: Vehicle) => void
    onAsk: (vehicle: Vehicle) => void
    onCompare: (vehicle: Vehicle, checked: boolean, input: HTMLInputElement) => void
  },
) {
  grid.replaceChildren()
  grid.setAttribute('aria-busy', 'false')
  if (vehicles.length === 0) {
    const empty = document.createElement('div')
    empty.className = 'inventory-message'
    const copy = document.createElement('div')
    const title = document.createElement('strong')
    title.textContent = 'No vehicles match these filters'
    const detail = document.createElement('p')
    detail.textContent = 'Adjust one or more filters to see the current fictional inventory.'
    copy.append(title, detail)
    empty.append(copy)
    grid.append(empty)
    return
  }

  const fragment = document.createDocumentFragment()
  for (const vehicle of vehicles) {
    const card = template.content.firstElementChild?.cloneNode(true) as HTMLElement | undefined
    if (!card) continue
    setText(card, '[data-card-title]', `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}`)
    setText(card, '[data-card-derivative]', vehicle.derivative)
    setText(card, '[data-card-price]', formatMoney(vehicle.priceGbp, vehicle.currency))
    setText(card, '[data-card-fuel]', vehicle.fuelType)
    setText(card, '[data-card-mileage]', `${formatNumber(vehicle.mileage)} miles`)
    setText(card, '[data-card-transmission]', vehicle.transmission)
    setText(card, '[data-card-location]', vehicle.location)
    setText(card, '[data-card-summary]', vehicle.summary)
    const image = required<HTMLImageElement>(card, '[data-card-image]')
    image.src = safeImagePath(vehicle.imagePath)
    image.alt = `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}, representative demo image`
    const ask = required<HTMLButtonElement>(card, '[data-card-ask]')
    ask.disabled = !assistantReady
    ask.addEventListener('click', () => handlers.onAsk(vehicle))
    required<HTMLButtonElement>(card, '[data-card-details]').addEventListener('click', () => handlers.onDetails(vehicle))
    const compare = required<HTMLInputElement>(card, '[data-card-compare]')
    compare.checked = comparison.has(vehicle.id)
    compare.setAttribute('aria-label', `Compare ${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}`)
    compare.addEventListener('change', () => handlers.onCompare(vehicle, compare.checked, compare))
    fragment.append(card)
  }
  grid.append(fragment)
  setAssistantButtons(app, assistantReady)
}

function openVehicleDialog(app: HTMLElement, vehicle: Vehicle, assistantReady: boolean) {
  const dialog = required<HTMLDialogElement>(app, '[data-vehicle-dialog]')
  const image = required<HTMLImageElement>(dialog, '[data-dialog-image]')
  image.src = safeImagePath(vehicle.imagePath)
  image.alt = `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}, representative demo image`
  setText(dialog, '[data-dialog-title]', `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}`)
  setText(dialog, '[data-dialog-derivative]', vehicle.derivative)
  setText(dialog, '[data-dialog-price]', formatMoney(vehicle.priceGbp, vehicle.currency))
  setText(dialog, '[data-dialog-summary]', vehicle.summary)
  setText(dialog, '[data-dialog-source]', `${vehicle.sourceLabel} · refreshed ${formatDateTime(vehicle.sourceUpdatedAt)} · confirm availability with the dealership.`)

  const facts = required<HTMLElement>(dialog, '[data-dialog-facts]')
  facts.replaceChildren()
  const values = [
    ['Mileage', `${formatNumber(vehicle.mileage)} miles`],
    ['Fuel', vehicle.fuelType],
    ['Transmission', vehicle.transmission],
    ['Body', vehicle.bodyType],
    ['Colour', vehicle.exteriorColour],
    ['Location', vehicle.location],
  ]
  for (const [label, value] of values) {
    const group = document.createElement('div')
    const term = document.createElement('dt')
    const description = document.createElement('dd')
    term.textContent = label
    description.textContent = value
    group.append(term, description)
    facts.append(group)
  }

  const features = required<HTMLUListElement>(dialog, '[data-dialog-features]')
  features.replaceChildren(...vehicle.features.map((feature) => {
    const item = document.createElement('li')
    item.textContent = feature
    return item
  }))
  required<HTMLButtonElement>(dialog, '[data-dialog-ask]').disabled = !assistantReady
  required<HTMLButtonElement>(dialog, '[data-dialog-test-drive]').disabled = !assistantReady
  dialog.showModal()
}

function openComparisonDialog(app: HTMLElement, vehicles: Vehicle[], assistantReady: boolean) {
  const dialog = required<HTMLDialogElement>(app, '[data-compare-dialog]')
  const content = required<HTMLElement>(dialog, '[data-compare-content]')
  content.replaceChildren(buildComparisonTable(vehicles))
  required<HTMLButtonElement>(dialog, '[data-ask-comparison]').disabled = !assistantReady
  dialog.showModal()
}

function buildComparisonTable(vehicles: Vehicle[]) {
  const table = document.createElement('table')
  table.className = 'compare-table'
  const head = document.createElement('thead')
  const headRow = document.createElement('tr')
  const blank = document.createElement('th')
  blank.scope = 'col'
  headRow.append(blank)
  for (const vehicle of vehicles) {
    const cell = document.createElement('th')
    cell.scope = 'col'
    const image = document.createElement('img')
    image.src = safeImagePath(vehicle.imagePath)
    image.alt = ''
    const name = document.createElement('span')
    name.textContent = `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}`
    cell.append(image, name)
    headRow.append(cell)
  }
  head.append(headRow)
  table.append(head)

  const body = document.createElement('tbody')
  const rows: Array<[string, (vehicle: Vehicle) => string]> = [
    ['Price', (vehicle) => formatMoney(vehicle.priceGbp, vehicle.currency)],
    ['Derivative', (vehicle) => vehicle.derivative],
    ['Mileage', (vehicle) => `${formatNumber(vehicle.mileage)} miles`],
    ['Fuel', (vehicle) => vehicle.fuelType],
    ['Transmission', (vehicle) => vehicle.transmission],
    ['Body style', (vehicle) => vehicle.bodyType],
    ['Electric range', (vehicle) => vehicle.electricRangeMiles ? `${vehicle.electricRangeMiles} miles` : 'Not listed'],
    ['Location', (vehicle) => vehicle.location],
  ]
  for (const [label, value] of rows) {
    const row = document.createElement('tr')
    const heading = document.createElement('th')
    heading.scope = 'row'
    heading.textContent = label
    row.append(heading)
    for (const vehicle of vehicles) {
      const cell = document.createElement('td')
      cell.textContent = value(vehicle)
      row.append(cell)
    }
    body.append(row)
  }
  table.append(body)
  return table
}

function askAboutVehicle(app: HTMLElement, vehicle: Vehicle, prompt: string) {
  attachVehicle(vehicle)
  sendAssistantMessage(app, prompt, { selectedVehicleId: vehicle.id })
}

function attachVehicle(vehicle: Vehicle) {
  window.MaxMode?.attachItem({
    type: 'vehicle',
    data: {
      id: vehicle.id,
      vectorSpace: 'dealer-vehicle',
      entityType: 'dealer-vehicle',
      stockId: vehicle.stockId,
      name: `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}`,
      content: `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model} ${vehicle.derivative}. ${vehicle.summary}`,
      derivative: vehicle.derivative,
      priceGbp: vehicle.priceGbp,
      priceFormatted: vehicle.priceFormatted,
      currency: vehicle.currency,
      mileage: vehicle.mileage,
      fuelType: vehicle.fuelType,
      bodyType: vehicle.bodyType,
      lifecycleState: vehicle.lifecycleState,
      sourceLabel: vehicle.sourceLabel,
      sourceUpdatedAt: vehicle.sourceUpdatedAt,
      metadata: {
        stockId: vehicle.stockId,
        make: vehicle.make,
        model: vehicle.model,
        derivative: vehicle.derivative,
        priceGbp: vehicle.priceGbp,
        priceFormatted: vehicle.priceFormatted,
        mileage: vehicle.mileage,
        fuelType: vehicle.fuelType,
        bodyType: vehicle.bodyType,
        lifecycleState: vehicle.lifecycleState,
        sourceLabel: vehicle.sourceLabel,
        sourceUpdatedAt: vehicle.sourceUpdatedAt,
      },
    },
  })
}

function sendAssistantMessage(app: HTMLElement, prompt: string, requestContext: Record<string, unknown>) {
  const maxMode = window.MaxMode
  if (!maxMode) {
    showToast(app, 'The assigned LoomAI deployment is not available right now.')
    return
  }
  window.setTimeout(() => {
    maxMode.sendMessage(prompt, {
      open: true,
      position: 'search',
      mode: 'executor',
      requestContext,
    })
  }, 0)
}

function inventoryQuery(form: HTMLFormElement, sort: HTMLSelectElement) {
  const parameters = new URLSearchParams()
  parameters.set('dealershipId', 'dealer-demo-001')
  const formData = new FormData(form)
  for (const [key, raw] of formData.entries()) {
    const value = raw.toString().trim()
    if (value) parameters.set(key, value)
  }
  parameters.set('sort', sort.value)
  parameters.set('limit', '24')
  parameters.set('offset', '0')
  return parameters.toString()
}

function populateFacets(form: HTMLFormElement, facets: InventoryResponse['facets']) {
  for (const key of ['makes', 'fuelTypes', 'bodyTypes'] as const) {
    const select = form.querySelector<HTMLSelectElement>(`[data-facet-select="${key}"]`)
    if (!select || !Array.isArray(facets[key])) continue
    const selected = select.value
    const initial = select.options[0]?.cloneNode(true) as HTMLOptionElement
    select.replaceChildren(initial)
    for (const facet of facets[key]) {
      const option = document.createElement('option')
      option.value = facet.value
      option.textContent = `${facet.value} (${facet.count})`
      option.selected = facet.value === selected
      select.append(option)
    }
  }
}

function renderActiveFilters(app: HTMLElement, form: HTMLFormElement, sort: HTMLSelectElement, reload: () => Promise<void>) {
  const container = required<HTMLElement>(app, '[data-active-filters]')
  container.replaceChildren()
  for (const control of [...form.elements]) {
    if (!(control instanceof HTMLInputElement || control instanceof HTMLSelectElement) || !control.value) continue
    const label = control.closest('label')?.querySelector(':scope > span:first-child')?.textContent?.trim() || control.name
    const display = control instanceof HTMLSelectElement
      ? control.selectedOptions[0]?.textContent?.replace(/\s+\(\d+\)$/, '') || control.value
      : control.value
    const chip = document.createElement('button')
    chip.type = 'button'
    chip.className = 'filter-chip'
    chip.setAttribute('aria-label', `Remove ${label} filter: ${display}`)
    const text = document.createElement('span')
    text.textContent = `${label}: ${display}`
    const close = document.createElement('span')
    close.textContent = 'x'
    close.setAttribute('aria-hidden', 'true')
    chip.append(text, close)
    chip.addEventListener('click', () => {
      control.value = ''
      void reload()
    })
    container.append(chip)
  }
  if (sort.value !== 'recommended') {
    const chip = document.createElement('button')
    chip.type = 'button'
    chip.className = 'filter-chip'
    chip.textContent = `Sort: ${sort.selectedOptions[0]?.textContent || sort.value} x`
    chip.setAttribute('aria-label', 'Reset inventory sort')
    chip.addEventListener('click', () => {
      sort.value = 'recommended'
      void reload()
    })
    container.append(chip)
  }
}

function updateInventorySummary(app: HTMLElement, response: InventoryResponse) {
  setText(app, '[data-results-count]', `${response.total} vehicle${response.total === 1 ? '' : 's'} available`)
  setText(app, '[data-results-source]', `${response.source.label} · last refreshed ${formatDateTime(response.source.refreshedAt)}`)
  setText(app, '[data-inventory-freshness]', `Inventory refreshed ${formatDateTime(response.source.refreshedAt)}`)
}

function updateComparisonBar(app: HTMLElement, comparison: Map<string, Vehicle>) {
  const bar = required<HTMLElement>(app, '[data-compare-bar]')
  bar.hidden = comparison.size === 0
  setText(bar, '[data-compare-count]', String(comparison.size))
  const open = required<HTMLButtonElement>(bar, '[data-open-comparison]')
  open.disabled = comparison.size < 2
}

function bindFilterPanel(app: HTMLElement) {
  const panel = required<HTMLElement>(app, '[data-filter-panel]')
  const toggle = required<HTMLButtonElement>(app, '[data-filter-toggle]')
  const close = () => {
    panel.dataset.open = 'false'
    toggle.setAttribute('aria-expanded', 'false')
  }
  toggle.addEventListener('click', () => {
    const open = panel.dataset.open !== 'true'
    panel.dataset.open = String(open)
    toggle.setAttribute('aria-expanded', String(open))
  })
  app.querySelector('[data-filter-close]')?.addEventListener('click', close)
  panel.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close()
  })
}

function bindDialogs(app: HTMLElement) {
  app.querySelector('[data-close-vehicle-dialog]')?.addEventListener('click', () => closeDialog(app, '[data-vehicle-dialog]'))
  app.querySelector('[data-close-comparison]')?.addEventListener('click', () => closeDialog(app, '[data-compare-dialog]'))
  app.querySelectorAll<HTMLDialogElement>('dialog').forEach((dialog) => {
    dialog.addEventListener('click', (event) => {
      if (event.target === dialog) dialog.close()
    })
  })
}

function closeDialog(app: HTMLElement, selector: string) {
  const dialog = app.querySelector<HTMLDialogElement>(selector)
  if (dialog?.open) dialog.close()
}

function setInventoryLoading(app: HTMLElement, loading: boolean) {
  const grid = required<HTMLElement>(app, '[data-vehicle-grid]')
  grid.setAttribute('aria-busy', String(loading))
  if (loading && grid.querySelector('.vehicle-card')) {
    grid.style.opacity = '0.58'
  } else {
    grid.style.removeProperty('opacity')
  }
}

function showInventoryMessage(app: HTMLElement, title: string, detail: string) {
  const message = required<HTMLElement>(app, '[data-inventory-message]')
  message.hidden = false
  setText(message, '[data-inventory-message-title]', title)
  setText(message, '[data-inventory-message-detail]', detail)
}

function hideInventoryMessage(app: HTMLElement) {
  required<HTMLElement>(app, '[data-inventory-message]').hidden = true
}

function setRuntimeState(app: HTMLElement, state: 'checking' | 'ready' | 'unavailable', title: string, detail: string) {
  const element = required<HTMLElement>(app, '[data-runtime-state]')
  element.dataset.state = state
  setText(element, '[data-runtime-state-title]', title)
  setText(element, '[data-runtime-state-detail]', detail)
}

function setAssistantButtons(app: HTMLElement, enabled: boolean) {
  app.querySelectorAll<HTMLButtonElement>('[data-card-ask], [data-dialog-ask], [data-dialog-test-drive], [data-ask-comparison]')
    .forEach((button) => {
      button.disabled = !enabled
      if (!enabled) button.title = 'The assigned LoomAI deployment is unavailable'
      else button.removeAttribute('title')
    })
}

function showToast(app: HTMLElement, message: string) {
  const toast = required<HTMLElement>(app, '[data-dealer-toast]')
  const copy = required<HTMLElement>(toast, 'span')
  copy.textContent = message
  toast.hidden = false
  window.setTimeout(() => {
    toast.hidden = true
  }, 3200)
}

async function loadWidgetBundle() {
  if (window.MaxMode) return
  const existing = document.querySelector<HTMLScriptElement>('script[data-max-mode-bundle]')
  if (existing) {
    await waitForScript(existing)
    return
  }
  const script = document.createElement('script')
  script.src = '/vendor/max-mode-widget.iife.js'
  script.async = true
  script.dataset.maxModeBundle = 'true'
  document.head.append(script)
  await waitForScript(script)
}

function waitForScript(script: HTMLScriptElement) {
  return new Promise<void>((resolve, reject) => {
    if (window.MaxMode) {
      resolve()
      return
    }
    script.addEventListener('load', () => resolve(), { once: true })
    script.addEventListener('error', () => reject(new Error('The LoomAI chat bundle could not be loaded.')), { once: true })
  })
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url, {
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  })
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message = data && typeof data.message === 'string' ? data.message : `Request failed with HTTP ${response.status}.`
    throw new Error(message)
  }
  return data as T
}

function normalizeBaseUrl(value: string) {
  const parsed = new URL(value)
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('The integration URL is invalid.')
  return parsed.toString().replace(/\/$/, '')
}

function absoluteRuntimeUrl(baseUrl: string, value: string) {
  if (/^https?:\/\//i.test(value)) return normalizeBaseUrl(value)
  const base = new URL(baseUrl)
  return new URL(value.startsWith('/') ? value : `/${value}`, base.origin).toString()
}

function absoluteRuntimeTemplateUrl(baseUrl: string, value: string) {
  return absoluteRuntimeUrl(baseUrl, value)
    .replaceAll('%7BconversationId%7D', '{conversationId}')
    .replaceAll('%7bconversationId%7d', '{conversationId}')
}

function safeImagePath(value: string) {
  if (/^\/assets\/demos\/dealership\/vehicle-[0-9]{2}\.webp$/.test(value)) return value
  return '/assets/demos/dealership/vehicle-01.webp'
}

function formatMoney(major: number, currency: string) {
  return new Intl.NumberFormat('en-GB', {
    style: 'currency',
    currency: currency || 'GBP',
    maximumFractionDigits: 0,
  }).format(major)
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('en-GB').format(value)
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'time unavailable'
  return new Intl.DateTimeFormat('en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function required<T extends Element>(rootElement: ParentNode, selector: string): T {
  const element = rootElement.querySelector<T>(selector)
  if (!element) throw new Error(`Required dealership demo element is missing: ${selector}`)
  return element
}

function setText(rootElement: ParentNode, selector: string, value: string) {
  required<HTMLElement>(rootElement, selector).textContent = value
}

export {}
