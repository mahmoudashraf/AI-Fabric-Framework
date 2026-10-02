import {
  attachDealershipVehicle,
  fetchDealershipJson,
  formatDealershipDateTime,
  formatDealershipMoney,
  formatDealershipNumber,
  initializeDealershipAssistant,
  resolveDealershipApiBaseUrl,
  safeDealershipImagePath,
  sendDealershipAssistantMessage,
  type InventoryResponse,
  type Vehicle,
} from './dealership-shared'

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
      const response = await fetchDealershipJson<InventoryResponse>(`${apiBaseUrl}/api/public/vehicles?${query}`)
      if (sequence !== requestSequence) return
      if (!response.success || !Array.isArray(response.items)) {
        throw new Error('The dealership returned an invalid inventory response.')
      }
      populateFacets(form, response.facets)
      renderInventory(app, grid, cardTemplate, response.items, comparison, assistantReady, {
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

  app.querySelector('[data-ask-comparison]')?.addEventListener('click', () => {
    const vehicles = [...comparison.values()]
    if (vehicles.length < 2) return
    closeDialog(app, '[data-compare-dialog]')
    for (const vehicle of vehicles) {
      attachDealershipVehicle(vehicle)
    }
    const names = vehicles.map((vehicle) => `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}`).join(', ')
    sendAssistantMessage(app, `Compare these selected vehicles using current dealership facts: ${names}. Explain the meaningful trade-offs and do not invent unavailable specifications.`, {
      selectedVehicleIds: vehicles.map((vehicle) => vehicle.id),
    })
  })

  try {
    apiBaseUrl = await resolveDealershipApiBaseUrl(app)
    await Promise.all([
      loadInventory(),
      initializeDealershipAssistant(apiBaseUrl, {
        rootSelector: '#main-content',
        maxChars: 1800,
        contextLabel: 'Current fictional dealership inventory',
        welcomeMessage: 'I can search and compare Northfield demo inventory using indexed vehicle evidence and current dealership facts.',
        placeholder: 'Ask about a vehicle, feature, budget or comparison...',
        emptyMessage: 'Ask about current vehicles, compare options, or start a confirmed callback or test-drive request.',
        toolGroups: {
          initialScope: 'default',
          default: {
            label: 'Browse stock',
            icon: 'search',
            tools: [
              { label: 'Search stock', query: 'Show me the current dealership inventory and help me narrow it down.', position: 'search', mode: 'executor', icon: 'search' },
              { label: 'Electric cars', query: 'Show me electric cars in current stock.', position: 'search', mode: 'executor', icon: 'sparkles' },
              { label: 'Family options', query: 'Which current vehicles are practical for a family? Use current dealership evidence.', position: 'search', mode: 'executor', icon: 'shield' },
              { label: 'Compare cars', query: 'Help me choose two current vehicles and compare their dealership facts.', position: 'search', mode: 'executor', icon: 'compare' },
            ],
          },
          contextual: {
            label: 'This vehicle',
            icon: 'details',
            tools: [
              { label: 'Live details', query: 'Load the authoritative current details for the vehicle in my current context.', position: 'search', mode: 'executor', icon: 'details' },
              { label: 'Everyday use', query: 'Is the vehicle in my current context suitable for everyday driving? Use current dealership facts.', position: 'search', mode: 'executor', icon: 'shield' },
              { label: 'Trade-offs', query: 'Explain the important trade-offs for the vehicle in my current context.', position: 'search', mode: 'executor', icon: 'compare' },
              { label: 'Location', query: 'Which showroom currently holds the vehicle in my current context? Use current dealership facts.', position: 'search', mode: 'executor', icon: 'location' },
              { label: 'Test drive', query: 'Help me request a test drive for the vehicle in my current context. Ask only for required details before confirmation.', position: 'search', mode: 'executor', icon: 'calendar' },
              { label: 'Callback', query: 'Help me request a dealership callback about the vehicle in my current context. Ask for contact details and consent before confirmation.', position: 'search', mode: 'executor', icon: 'phone' },
            ],
          },
        },
        starterSuggestions: [
          'Compare electric cars',
          'What is under £30,000?',
          'Which car has the best luggage space?',
        ],
        onRuntimeState: (state, title, detail) => setRuntimeState(app, state, title, detail),
      }).then(() => {
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

function renderInventory(
  app: HTMLElement,
  grid: HTMLElement,
  template: HTMLTemplateElement,
  vehicles: Vehicle[],
  comparison: Map<string, Vehicle>,
  assistantReady: boolean,
  handlers: {
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
    setText(card, '[data-card-price]', formatDealershipMoney(vehicle.priceGbp, vehicle.currency))
    setText(card, '[data-card-fuel]', vehicle.fuelType)
    setText(card, '[data-card-mileage]', `${formatDealershipNumber(vehicle.mileage)} miles`)
    setText(card, '[data-card-transmission]', vehicle.transmission)
    setText(card, '[data-card-location]', vehicle.location)
    setText(card, '[data-card-summary]', vehicle.summary)
    const image = required<HTMLImageElement>(card, '[data-card-image]')
    image.src = safeDealershipImagePath(vehicle.imagePath)
    image.alt = `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}, representative demo image`
    const ask = required<HTMLButtonElement>(card, '[data-card-ask]')
    ask.disabled = !assistantReady
    ask.addEventListener('click', () => handlers.onAsk(vehicle))
    required<HTMLAnchorElement>(card, '[data-card-details]').href = `/demos/dealership-ai/vehicles/${encodeURIComponent(vehicle.slug)}`
    const compare = required<HTMLInputElement>(card, '[data-card-compare]')
    compare.checked = comparison.has(vehicle.id)
    compare.setAttribute('aria-label', `Compare ${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}`)
    compare.addEventListener('change', () => handlers.onCompare(vehicle, compare.checked, compare))
    fragment.append(card)
  }
  grid.append(fragment)
  setAssistantButtons(app, assistantReady)
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
    image.src = safeDealershipImagePath(vehicle.imagePath)
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
    ['Price', (vehicle) => formatDealershipMoney(vehicle.priceGbp, vehicle.currency)],
    ['Derivative', (vehicle) => vehicle.derivative],
    ['Mileage', (vehicle) => `${formatDealershipNumber(vehicle.mileage)} miles`],
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
  attachDealershipVehicle(vehicle)
  sendAssistantMessage(app, prompt, { selectedVehicleId: vehicle.id })
}

function sendAssistantMessage(app: HTMLElement, prompt: string, requestContext: Record<string, unknown>) {
  if (!sendDealershipAssistantMessage(prompt, requestContext)) {
    showToast(app, 'The assigned LoomAI deployment is not available right now.')
  }
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
  setText(app, '[data-results-source]', `${response.source.label} · last refreshed ${formatDealershipDateTime(response.source.refreshedAt)}`)
  setText(app, '[data-inventory-freshness]', `Inventory refreshed ${formatDealershipDateTime(response.source.refreshedAt)}`)
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
  app.querySelectorAll<HTMLButtonElement>('[data-card-ask], [data-ask-comparison]')
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

function required<T extends Element>(rootElement: ParentNode, selector: string): T {
  const element = rootElement.querySelector<T>(selector)
  if (!element) throw new Error(`Required dealership demo element is missing: ${selector}`)
  return element
}

function setText(rootElement: ParentNode, selector: string, value: string) {
  required<HTMLElement>(rootElement, selector).textContent = value
}

export {}
