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
  type Vehicle,
  type VehicleDetailResponse,
} from './dealership-shared'

const root = document.querySelector<HTMLElement>('[data-dealership-vehicle-detail]')

if (root) {
  void startVehicleDetail(root)
}

async function startVehicleDetail(app: HTMLElement) {
  const slug = app.dataset.vehicleSlug?.trim()
  if (!slug) {
    showDetailError(app, 'Vehicle unavailable', 'The vehicle route does not identify a current record.')
    return
  }

  try {
    const apiBaseUrl = await resolveDealershipApiBaseUrl(app)
    const response = await fetchDealershipJson<VehicleDetailResponse>(
      `${apiBaseUrl}/api/public/vehicles/${encodeURIComponent(slug)}`,
    )
    if (!response.success || !response.vehicle) {
      throw new Error('The dealership returned an invalid vehicle record.')
    }

    renderVehicle(app, response)
    bindVehicleActions(app, response.vehicle)

    try {
      await initializeDealershipAssistant({
        onRuntimeState: (state, title, detail) => setRuntimeState(app, state, title, detail),
      })
      await window.LoomAIWorkspace?.refresh()
      setActionButtons(app, true)
    } catch (error) {
      setRuntimeState(
        app,
        'unavailable',
        'Assistant unavailable',
        error instanceof Error ? error.message : 'The assigned deployment could not be reached.',
      )
      setActionButtons(app, false)
    }
  } catch (error) {
    showDetailError(
      app,
      'Vehicle unavailable',
      error instanceof Error ? error.message : 'The current dealership record could not be loaded.',
    )
    setRuntimeState(app, 'unavailable', 'Assistant unavailable', 'Vehicle context could not be loaded')
  }
}

function renderVehicle(app: HTMLElement, response: VehicleDetailResponse) {
  const vehicle = response.vehicle
  const label = `${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}`
  document.title = `${label} | Northfield Motor House demo | Loom AI Labs`
  document.querySelector('meta[name="description"]')?.setAttribute(
    'content',
    `${label} ${vehicle.derivative}: current fictional dealership details and LoomAI-assisted vehicle questions.`,
  )

  const image = required<HTMLImageElement>(app, '[data-detail-image]')
  image.src = safeDealershipImagePath(vehicle.imagePath)
  image.alt = `${label}, representative demonstration image`
  setText(app, '[data-detail-fuel]', vehicle.fuelType)
  setText(app, '[data-detail-derivative]', vehicle.derivative)
  setText(app, '[data-detail-title]', label)
  setText(app, '[data-detail-price]', formatDealershipMoney(vehicle.priceGbp, vehicle.currency))
  setText(app, '[data-detail-summary]', vehicle.summary)
  setText(
    app,
    '[data-detail-source]',
    `${vehicle.sourceLabel}. Vehicle record refreshed ${formatDealershipDateTime(vehicle.sourceUpdatedAt)}. Source version ${vehicle.sourceVersion}.`,
  )
  setText(app, '[data-detail-notice]', response.dataNotice)

  const facts = required<HTMLElement>(app, '[data-detail-facts]')
  facts.replaceChildren(...[
    ['Mileage', `${formatDealershipNumber(vehicle.mileage)} miles`],
    ['Fuel', vehicle.fuelType],
    ['Transmission', vehicle.transmission],
    ['Body style', vehicle.bodyType],
    ['Colour', vehicle.exteriorColour],
    ['Location', vehicle.location],
  ].map(([term, description]) => {
    const group = document.createElement('div')
    const dt = document.createElement('dt')
    const dd = document.createElement('dd')
    dt.textContent = term
    dd.textContent = description
    group.append(dt, dd)
    return group
  }))

  const features = required<HTMLUListElement>(app, '[data-detail-features]')
  features.replaceChildren(...vehicle.features.map((feature) => {
    const item = document.createElement('li')
    const marker = document.createElement('span')
    marker.className = 'vehicle-feature-marker'
    marker.setAttribute('aria-hidden', 'true')
    marker.textContent = '✓'
    const text = document.createElement('span')
    text.textContent = feature
    item.append(marker, text)
    return item
  }))

  required<HTMLElement>(app, '[data-vehicle-evidence]').setAttribute('aria-busy', 'false')
}

function bindVehicleActions(app: HTMLElement, vehicle: Vehicle) {
  required<HTMLButtonElement>(app, '[data-detail-ask]').addEventListener('click', () => {
    askAboutVehicle(
      app,
      vehicle,
      `Give me a grounded summary of the ${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}, including price, mileage, useful features and important trade-offs.`,
    )
  })
  required<HTMLButtonElement>(app, '[data-detail-test-drive]').addEventListener('click', () => {
    askAboutVehicle(
      app,
      vehicle,
      `I would like to request a test drive for the ${vehicle.registrationYear} ${vehicle.make} ${vehicle.model}.`,
    )
  })
}

function askAboutVehicle(app: HTMLElement, vehicle: Vehicle, prompt: string) {
  attachDealershipVehicle(vehicle)
  if (!sendDealershipAssistantMessage(prompt, { selectedVehicleId: vehicle.id })) {
    setRuntimeState(app, 'unavailable', 'Assistant unavailable', 'The assigned deployment is not available right now')
  }
}

function setActionButtons(app: HTMLElement, enabled: boolean) {
  app.querySelectorAll<HTMLButtonElement>('[data-detail-ask], [data-detail-test-drive]').forEach((button) => {
    button.disabled = !enabled
    if (!enabled) button.title = 'The assigned LoomAI deployment is unavailable'
    else button.removeAttribute('title')
  })
}

function setRuntimeState(
  app: HTMLElement,
  state: 'checking' | 'ready' | 'unavailable',
  title: string,
  detail: string,
) {
  const element = required<HTMLElement>(app, '[data-runtime-state]')
  element.dataset.state = state
  setText(element, '[data-runtime-state-title]', title)
  setText(element, '[data-runtime-state-detail]', detail)
}

function showDetailError(app: HTMLElement, title: string, message: string) {
  required<HTMLElement>(app, '[data-vehicle-evidence]').hidden = true
  const error = required<HTMLElement>(app, '[data-detail-error]')
  error.hidden = false
  setText(error, '[data-detail-error-title]', title)
  setText(error, '[data-detail-error-message]', message)
}

function required<T extends Element>(rootElement: ParentNode, selector: string): T {
  const element = rootElement.querySelector<T>(selector)
  if (!element) throw new Error(`Required vehicle detail element is missing: ${selector}`)
  return element
}

function setText(rootElement: ParentNode, selector: string, value: string) {
  required<HTMLElement>(rootElement, selector).textContent = value
}

export {}
