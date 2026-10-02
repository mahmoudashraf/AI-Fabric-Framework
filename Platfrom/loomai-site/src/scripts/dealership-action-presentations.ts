type SafeRecord = Record<string, unknown>

type PresentationReference = {
  key: string
  label: string
  scope?: string
  lookupValue?: string
  sourceMessageId: string
  sourceActionName: string
  safeData: Readonly<SafeRecord>
}

type PresentationInput = {
  actionName: string
  rendererId: string
  schemaVersion: string
  presentationData: Readonly<SafeRecord>
  resultReferences: readonly PresentationReference[]
  selectedResultKeys: readonly string[]
  context: Readonly<Record<string, string | number | boolean>>
}

type PresentationCommands = {
  ask(input: { query: string; resultReferenceKeys?: string[] }): Promise<void>
  attachResult(referenceKey: string): void
  detachResult(referenceKey: string): void
  navigate(input: { url: string; target?: 'same-window' | 'new-window' }): void
}

type ActionPresentationConfig = {
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
    projection: SafeRecord
    rendererContext?: Record<string, string | number | boolean>
  }>
}

const INVENTORY_ELEMENT = 'loomai-dealership-inventory'
const DETAIL_ELEMENT = 'loomai-dealership-vehicle-detail'
const COMPARISON_ELEMENT = 'loomai-dealership-vehicle-comparison'
const VEHICLE_FIELDS = [
  'stockId',
  'slug',
  'make',
  'model',
  'derivative',
  'registrationYear',
  'priceGbp',
  'priceFormatted',
  'currency',
  'mileage',
  'fuelType',
  'transmission',
  'bodyType',
  'exteriorColour',
  'doors',
  'seats',
  'electricRangeMiles',
  'location',
  'lifecycleState',
  'summary',
  'features',
  'imagePath',
  'sourceLabel',
  'sourceUpdatedAt',
  'sourceVersion',
]

export function registerDealershipActionPresentationElements() {
  defineElement(INVENTORY_ELEMENT, DealershipInventoryPresentation)
  defineElement(DETAIL_ELEMENT, DealershipVehicleDetailPresentation)
  defineElement(COMPARISON_ELEMENT, DealershipVehicleComparisonPresentation)
}

export function dealershipActionPresentationConfig(): ActionPresentationConfig {
  return {
    renderers: [
      {
        id: 'loomai.vehicle-inventory.v1',
        kind: 'custom-element',
        elementName: INVENTORY_ELEMENT,
        schemaVersions: ['loomai.vehicle-list.v1'],
      },
      {
        id: 'loomai.vehicle-detail.v1',
        kind: 'custom-element',
        elementName: DETAIL_ELEMENT,
        schemaVersions: ['loomai.vehicle-detail.v1'],
      },
      {
        id: 'loomai.vehicle-comparison.v1',
        kind: 'custom-element',
        elementName: COMPARISON_ELEMENT,
        schemaVersions: ['loomai.vehicle-comparison.v1'],
      },
    ],
    mappings: [
      {
        actionName: 'dealership_search_inventory',
        rendererId: 'loomai.vehicle-inventory.v1',
        schemaVersion: 'loomai.vehicle-list.v1',
        projection: {
          fields: [
            { sourcePath: '_count', target: 'count' },
            { sourcePath: 'total' },
            { sourcePath: 'dataNotice' },
          ],
          objects: [
            { sourcePath: 'source', target: 'source', includeFields: ['label', 'refreshedAt'] },
            {
              sourcePath: 'appliedFilters',
              target: 'appliedFilters',
              includeFields: ['q', 'make', 'fuelType', 'bodyType', 'minPriceGbp', 'maxPriceGbp', 'maxMileage', 'sort'],
            },
          ],
          collections: [
            {
              sourcePath: '_items',
              target: 'items',
              includeFields: VEHICLE_FIELDS,
              maxItems: 12,
              reference: {
                lookupField: 'stockId',
                labelFields: ['registrationYear', 'make', 'model'],
                scope: 'dealer-vehicle',
              },
            },
          ],
        },
        rendererContext: { detailBasePath: '/demos/dealership-ai/vehicles/' },
      },
      {
        actionName: 'dealership_get_vehicle',
        rendererId: 'loomai.vehicle-detail.v1',
        schemaVersion: 'loomai.vehicle-detail.v1',
        projection: {
          fields: [{ sourcePath: 'dataNotice' }],
          objects: [
            {
              sourcePath: 'vehicle',
              target: 'vehicle',
              includeFields: VEHICLE_FIELDS,
              reference: {
                lookupField: 'stockId',
                labelFields: ['registrationYear', 'make', 'model'],
                scope: 'dealer-vehicle',
              },
            },
            { sourcePath: 'source', target: 'source', includeFields: ['label', 'refreshedAt'] },
          ],
        },
        rendererContext: { detailBasePath: '/demos/dealership-ai/vehicles/' },
      },
      {
        actionName: 'dealership_compare_vehicles',
        rendererId: 'loomai.vehicle-comparison.v1',
        schemaVersion: 'loomai.vehicle-comparison.v1',
        projection: {
          fields: [{ sourcePath: '_count', target: 'count' }],
          objects: [
            { sourcePath: 'source', target: 'source', includeFields: ['label', 'refreshedAt'] },
          ],
          collections: [
            {
              sourcePath: '_items',
              target: 'vehicles',
              includeFields: VEHICLE_FIELDS,
              maxItems: 4,
              reference: {
                lookupField: 'stockId',
                labelFields: ['registrationYear', 'make', 'model'],
                scope: 'dealer-vehicle',
              },
            },
          ],
        },
        rendererContext: { detailBasePath: '/demos/dealership-ai/vehicles/' },
      },
    ],
  }
}

abstract class DealershipPresentationElement extends HTMLElement {
  protected root: ShadowRoot
  protected input?: PresentationInput
  protected commandApi?: PresentationCommands
  protected compareKeys = new Set<string>()

  constructor() {
    super()
    this.root = this.attachShadow({ mode: 'open' })
  }

  set presentation(value: PresentationInput | undefined) {
    this.input = value
    const allowedKeys = new Set(value?.resultReferences.map((reference) => reference.key) || [])
    this.compareKeys = new Set([...this.compareKeys].filter((key) => allowedKeys.has(key)))
    this.renderSafely()
  }

  get presentation() {
    return this.input
  }

  set commands(value: PresentationCommands | undefined) {
    this.commandApi = value
    this.renderSafely()
  }

  get commands() {
    return this.commandApi
  }

  connectedCallback() {
    this.renderSafely()
  }

  protected abstract renderContent(container: HTMLElement): void

  protected referenceFor(key: string | undefined) {
    return this.input?.resultReferences.find((reference) => reference.key === key)
  }

  protected isAttached(key: string) {
    return Boolean(this.input?.selectedResultKeys.includes(key))
  }

  protected ask(query: string, keys: string[] = []) {
    if (!this.commandApi) return
    void this.commandApi.ask({ query, resultReferenceKeys: keys }).catch(() => {
      this.showStatus('The assistant could not start that request. Please try again.')
    })
  }

  protected toggleAttachment(reference: PresentationReference) {
    if (!this.commandApi) return
    if (this.isAttached(reference.key)) this.commandApi.detachResult(reference.key)
    else this.commandApi.attachResult(reference.key)
  }

  protected openDetail(vehicle: SafeRecord) {
    const slug = textValue(vehicle.slug)
    const basePath = textValue(this.input?.context.detailBasePath) || '/demos/dealership-ai/vehicles/'
    if (!slug || !this.commandApi) return
    this.commandApi.navigate({ url: `${basePath}${encodeURIComponent(slug)}` })
  }

  protected showStatus(message: string) {
    const status = this.root.querySelector<HTMLElement>('[data-presentation-status]')
    if (status) status.textContent = message
  }

  private renderSafely() {
    if (!this.isConnected || !this.input || !this.commandApi) return
    try {
      const frame = document.createElement('section')
      frame.className = 'workspace'
      frame.setAttribute('aria-label', 'Dealership action result')
      frame.append(createStyles())
      const status = document.createElement('p')
      status.className = 'sr-only'
      status.dataset.presentationStatus = 'true'
      status.setAttribute('role', 'status')
      status.setAttribute('aria-live', 'polite')
      frame.append(status)
      this.renderContent(frame)
      this.root.replaceChildren(frame)
    } catch {
      this.root.replaceChildren()
      throw new Error('Dealership action presentation could not be rendered.')
    }
  }
}

class DealershipInventoryPresentation extends DealershipPresentationElement {
  protected renderContent(container: HTMLElement) {
    const data = this.input?.presentationData || {}
    const items = recordArray(data.items)
    const heading = createHeader(
      'Current inventory',
      `${numberValue(data.total) ?? numberValue(data.count) ?? items.length} matching vehicle${items.length === 1 ? '' : 's'}`,
      sourceLine(data),
    )
    const filters = filterLabels(recordValue(data.appliedFilters))
    if (filters.length > 0) heading.append(createPills(filters))
    container.append(heading)

    if (items.length === 0) {
      container.append(createEmptyState('No vehicles matched these filters.', 'Try changing the make, budget, mileage, fuel type, or body style.'))
      return
    }

    const grid = document.createElement('div')
    grid.className = 'vehicle-grid'
    for (const vehicle of items) {
      const reference = this.referenceFor(textValue(vehicle.presentationKey))
      grid.append(this.vehicleCard(vehicle, reference))
    }
    container.append(grid)

    const toolbar = document.createElement('div')
    toolbar.className = 'compare-toolbar'
    const selected = document.createElement('p')
    selected.textContent = this.compareKeys.size === 0
      ? 'Select two to four vehicles for a grounded comparison.'
      : `${this.compareKeys.size} vehicle${this.compareKeys.size === 1 ? '' : 's'} selected`
    const compare = createButton('Compare selected', 'primary')
    compare.disabled = this.compareKeys.size < 2 || this.compareKeys.size > 4
    compare.addEventListener('click', () => {
      const references = [...this.compareKeys]
        .map((key) => this.referenceFor(key))
        .filter((reference): reference is PresentationReference => Boolean(reference))
      const labels = references.map((reference) => reference.label).join(', ')
      this.ask(`Compare these selected current vehicles using dealership facts: ${labels}. Explain meaningful trade-offs without inventing specifications.`, references.map((reference) => reference.key))
    })
    toolbar.append(selected, compare)
    container.append(toolbar)
  }

  private vehicleCard(vehicle: SafeRecord, reference?: PresentationReference) {
    const card = document.createElement('article')
    card.className = 'vehicle-card'
    const image = vehicleImage(vehicle)
    if (image) card.append(image)

    const content = document.createElement('div')
    content.className = 'vehicle-card__content'
    const eyebrow = document.createElement('p')
    eyebrow.className = 'eyebrow'
    eyebrow.textContent = [textValue(vehicle.fuelType), textValue(vehicle.bodyType)].filter(Boolean).join(' · ') || 'Current stock'
    const title = document.createElement('h3')
    title.textContent = vehicleLabel(vehicle)
    const derivative = document.createElement('p')
    derivative.className = 'muted clamp-two'
    derivative.textContent = textValue(vehicle.derivative) || 'Derivative not supplied'
    const price = document.createElement('strong')
    price.className = 'price'
    price.textContent = vehiclePrice(vehicle)
    const facts = createFacts([
      ['Mileage', formatMileage(vehicle.mileage)],
      ['Gearbox', textValue(vehicle.transmission) || 'Unknown'],
      ['Location', textValue(vehicle.location) || 'Unknown'],
      ['Status', lifecycleLabel(vehicle.lifecycleState)],
    ])
    content.append(eyebrow, title, derivative, price, facts)

    if (reference) {
      const compareLabel = document.createElement('label')
      compareLabel.className = 'selection-control'
      const checkbox = document.createElement('input')
      checkbox.type = 'checkbox'
      checkbox.checked = this.compareKeys.has(reference.key)
      checkbox.setAttribute('aria-label', `Compare ${reference.label}`)
      checkbox.addEventListener('change', () => {
        if (checkbox.checked && this.compareKeys.size >= 4) {
          checkbox.checked = false
          this.showStatus('Choose no more than four vehicles.')
          return
        }
        if (checkbox.checked) this.compareKeys.add(reference.key)
        else this.compareKeys.delete(reference.key)
        this.renderContentAgain()
      })
      const label = document.createElement('span')
      label.textContent = 'Compare'
      compareLabel.append(checkbox, label)
      content.append(compareLabel)
    }

    const actions = document.createElement('div')
    actions.className = 'card-actions'
    const details = createButton('View details', 'secondary')
    details.disabled = !textValue(vehicle.slug)
    details.addEventListener('click', () => this.openDetail(vehicle))
    actions.append(details)
    if (reference) {
      const ask = createButton('Ask about this', 'secondary')
      ask.addEventListener('click', () => this.ask(`Tell me about ${reference.label} using its current dealership facts.`, [reference.key]))
      const attach = createButton(this.isAttached(reference.key) ? 'Remove context' : 'Keep in context', 'quiet')
      attach.setAttribute('aria-pressed', String(this.isAttached(reference.key)))
      attach.addEventListener('click', () => this.toggleAttachment(reference))
      const testDrive = createButton('Request test drive', 'primary')
      testDrive.addEventListener('click', () => this.ask(`I would like to request a test drive for ${reference.label}.`, [reference.key]))
      actions.append(ask, attach, testDrive)
    }
    content.append(actions)
    card.append(content)
    return card
  }

  private renderContentAgain() {
    const workspace = this.root.querySelector<HTMLElement>('.workspace')
    if (!workspace) return
    const status = workspace.querySelector('[data-presentation-status]')
    workspace.replaceChildren(createStyles())
    if (status) workspace.append(status)
    this.renderContent(workspace)
  }
}

class DealershipVehicleDetailPresentation extends DealershipPresentationElement {
  protected renderContent(container: HTMLElement) {
    const data = this.input?.presentationData || {}
    const vehicle = recordValue(data.vehicle)
    if (!vehicle) {
      container.append(createEmptyState('Vehicle details are unavailable.', 'The generic action result remains available.'))
      return
    }
    const reference = this.referenceFor(textValue(vehicle.presentationKey))
    container.append(createHeader('Vehicle details', vehicleLabel(vehicle), sourceLine(data, vehicle)))

    const layout = document.createElement('div')
    layout.className = 'detail-layout'
    const media = document.createElement('div')
    media.className = 'detail-media'
    const image = vehicleImage(vehicle)
    if (image) media.append(image)
    const price = document.createElement('strong')
    price.className = 'detail-price'
    price.textContent = vehiclePrice(vehicle)
    const derivative = document.createElement('p')
    derivative.className = 'muted'
    derivative.textContent = textValue(vehicle.derivative) || 'Derivative not supplied'
    media.append(price, derivative)

    const details = document.createElement('div')
    details.className = 'detail-content'
    details.append(createFacts([
      ['Mileage', formatMileage(vehicle.mileage)],
      ['Fuel', textValue(vehicle.fuelType) || 'Unknown'],
      ['Transmission', textValue(vehicle.transmission) || 'Unknown'],
      ['Body style', textValue(vehicle.bodyType) || 'Unknown'],
      ['Colour', textValue(vehicle.exteriorColour) || 'Unknown'],
      ['Range', optionalMiles(vehicle.electricRangeMiles)],
      ['Seats', textValue(vehicle.seats) || 'Unknown'],
      ['Location', textValue(vehicle.location) || 'Unknown'],
    ], 'facts-grid'))
    const summary = document.createElement('p')
    summary.className = 'summary'
    summary.textContent = textValue(vehicle.summary) || 'No additional summary is available.'
    details.append(summary)
    const features = stringArray(vehicle.features)
    if (features.length > 0) {
      const list = document.createElement('ul')
      list.className = 'feature-list'
      for (const feature of features.slice(0, 8)) {
        const item = document.createElement('li')
        item.textContent = feature
        list.append(item)
      }
      details.append(list)
    }
    layout.append(media, details)
    container.append(layout)

    if (reference) {
      const actions = document.createElement('div')
      actions.className = 'workspace-actions'
      const suitability = createButton('Everyday suitability', 'secondary')
      suitability.addEventListener('click', () => this.ask(`Is ${reference.label} suitable for everyday driving? Explain using current facts and identify unknowns.`, [reference.key]))
      const tradeoffs = createButton('Explain trade-offs', 'secondary')
      tradeoffs.addEventListener('click', () => this.ask(`Explain the important trade-offs for ${reference.label} using current dealership facts.`, [reference.key]))
      const testDrive = createButton('Request test drive', 'primary')
      testDrive.addEventListener('click', () => this.ask(`I would like to request a test drive for ${reference.label}.`, [reference.key]))
      const callback = createButton('Request callback', 'secondary')
      callback.addEventListener('click', () => this.ask(`I would like the dealership to call me about ${reference.label}.`, [reference.key]))
      const attach = createButton(this.isAttached(reference.key) ? 'Remove context' : 'Keep in context', 'quiet')
      attach.setAttribute('aria-pressed', String(this.isAttached(reference.key)))
      attach.addEventListener('click', () => this.toggleAttachment(reference))
      actions.append(suitability, tradeoffs, testDrive, callback, attach)
      container.append(actions)
    }
  }
}

class DealershipVehicleComparisonPresentation extends DealershipPresentationElement {
  protected renderContent(container: HTMLElement) {
    const data = this.input?.presentationData || {}
    const vehicles = recordArray(data.vehicles)
    container.append(createHeader(
      'Vehicle comparison',
      `${vehicles.length} current vehicles`,
      sourceLine(data),
    ))
    if (vehicles.length < 2) {
      container.append(createEmptyState('A comparison needs at least two vehicles.', 'Select distinct current vehicles and try again.'))
      return
    }

    const scroller = document.createElement('div')
    scroller.className = 'comparison-scroll'
    const table = document.createElement('table')
    table.className = 'comparison-table'
    const header = document.createElement('thead')
    const headerRow = document.createElement('tr')
    const empty = document.createElement('th')
    empty.scope = 'col'
    empty.textContent = 'Current facts'
    headerRow.append(empty)
    for (const vehicle of vehicles) {
      const cell = document.createElement('th')
      cell.scope = 'col'
      const image = vehicleImage(vehicle)
      if (image) cell.append(image)
      const name = document.createElement('strong')
      name.textContent = vehicleLabel(vehicle)
      const price = document.createElement('span')
      price.textContent = vehiclePrice(vehicle)
      cell.append(name, price)
      headerRow.append(cell)
    }
    header.append(headerRow)
    table.append(header)

    const body = document.createElement('tbody')
    const rows: Array<[string, (vehicle: SafeRecord) => string]> = [
      ['Mileage', (vehicle) => formatMileage(vehicle.mileage)],
      ['Fuel', (vehicle) => textValue(vehicle.fuelType) || 'Unknown'],
      ['Transmission', (vehicle) => textValue(vehicle.transmission) || 'Unknown'],
      ['Body style', (vehicle) => textValue(vehicle.bodyType) || 'Unknown'],
      ['Range', (vehicle) => optionalMiles(vehicle.electricRangeMiles)],
      ['Seats', (vehicle) => textValue(vehicle.seats) || 'Unknown'],
      ['Location', (vehicle) => textValue(vehicle.location) || 'Unknown'],
      ['Status', (vehicle) => lifecycleLabel(vehicle.lifecycleState)],
      ['Source updated', (vehicle) => formatDate(vehicle.sourceUpdatedAt)],
    ]
    for (const [label, formatter] of rows) {
      const row = document.createElement('tr')
      const heading = document.createElement('th')
      heading.scope = 'row'
      heading.textContent = label
      row.append(heading)
      for (const vehicle of vehicles) {
        const cell = document.createElement('td')
        cell.textContent = formatter(vehicle)
        row.append(cell)
      }
      body.append(row)
    }
    table.append(body)
    scroller.append(table)
    container.append(scroller)

    const mobileCards = document.createElement('div')
    mobileCards.className = 'comparison-mobile'
    for (const vehicle of vehicles) {
      const card = document.createElement('article')
      card.className = 'comparison-mobile__card'
      const image = vehicleImage(vehicle)
      if (image) card.append(image)
      const title = document.createElement('h3')
      title.textContent = vehicleLabel(vehicle)
      const price = document.createElement('strong')
      price.className = 'price'
      price.textContent = vehiclePrice(vehicle)
      card.append(title, price, createFacts(rows.map(([label, formatter]) => [label, formatter(vehicle)])))
      mobileCards.append(card)
    }
    container.append(mobileCards)

    const selection = document.createElement('div')
    selection.className = 'comparison-selection'
    for (const vehicle of vehicles) {
      const reference = this.referenceFor(textValue(vehicle.presentationKey))
      if (!reference) continue
      const group = document.createElement('div')
      const label = document.createElement('strong')
      label.textContent = reference.label
      const choose = createButton(this.isAttached(reference.key) ? 'Selected' : 'Select vehicle', this.isAttached(reference.key) ? 'primary' : 'secondary')
      choose.setAttribute('aria-pressed', String(this.isAttached(reference.key)))
      choose.addEventListener('click', () => this.toggleAttachment(reference))
      const details = createButton('View', 'quiet')
      details.addEventListener('click', () => this.openDetail(vehicle))
      group.append(label, choose, details)
      selection.append(group)
    }
    container.append(selection)

    const allKeys = this.input?.resultReferences.map((reference) => reference.key) || []
    const actions = document.createElement('div')
    actions.className = 'workspace-actions'
    const value = createButton('Compare value', 'secondary')
    value.addEventListener('click', () => this.ask('Compare the value trade-offs between these current vehicles. Ask for my priorities before naming a best option.', allKeys))
    const practical = createButton('Compare practicality', 'secondary')
    practical.addEventListener('click', () => this.ask('Compare the everyday practicality of these current vehicles using only available facts and clearly identify unknowns.', allKeys))
    const running = createButton('Running-cost trade-offs', 'secondary')
    running.addEventListener('click', () => this.ask('Explain likely running-cost trade-offs between these vehicles without inventing unavailable efficiency or finance figures.', allKeys))
    actions.append(value, practical, running)
    container.append(actions)
  }
}

function defineElement(name: string, constructor: CustomElementConstructor) {
  if (!customElements.get(name)) customElements.define(name, constructor)
}

function createHeader(kicker: string, titleText: string, sourceText: string) {
  const header = document.createElement('header')
  header.className = 'workspace-header'
  const copy = document.createElement('div')
  const eyebrow = document.createElement('p')
  eyebrow.className = 'eyebrow'
  eyebrow.textContent = kicker
  const title = document.createElement('h2')
  title.textContent = titleText
  copy.append(eyebrow, title)
  const source = document.createElement('p')
  source.className = 'source'
  source.textContent = sourceText
  header.append(copy, source)
  return header
}

function createPills(labels: string[]) {
  const list = document.createElement('div')
  list.className = 'filter-pills'
  for (const label of labels) {
    const item = document.createElement('span')
    item.textContent = label
    list.append(item)
  }
  return list
}

function createFacts(entries: Array<[string, string]>, className = 'facts') {
  const list = document.createElement('dl')
  list.className = className
  for (const [term, description] of entries) {
    const group = document.createElement('div')
    const dt = document.createElement('dt')
    const dd = document.createElement('dd')
    dt.textContent = term
    dd.textContent = description
    group.append(dt, dd)
    list.append(group)
  }
  return list
}

function createButton(label: string, variant: 'primary' | 'secondary' | 'quiet') {
  const button = document.createElement('button')
  button.type = 'button'
  button.className = `button button--${variant}`
  button.textContent = label
  return button
}

function createEmptyState(titleText: string, detailText: string) {
  const empty = document.createElement('div')
  empty.className = 'empty-state'
  const title = document.createElement('strong')
  title.textContent = titleText
  const detail = document.createElement('p')
  detail.textContent = detailText
  empty.append(title, detail)
  return empty
}

function vehicleImage(vehicle: SafeRecord) {
  const path = textValue(vehicle.imagePath)
  if (!path || !/^\/assets\/demos\/dealership\/vehicle-[0-9]{2}\.webp$/.test(path)) return null
  const image = document.createElement('img')
  image.className = 'vehicle-image'
  image.src = path
  image.alt = ''
  image.loading = 'lazy'
  image.decoding = 'async'
  return image
}

function vehicleLabel(vehicle: SafeRecord) {
  return [textValue(vehicle.registrationYear), textValue(vehicle.make), textValue(vehicle.model)]
    .filter(Boolean)
    .join(' ') || 'Current vehicle'
}

function vehiclePrice(vehicle: SafeRecord) {
  const formatted = textValue(vehicle.priceFormatted)
  if (formatted) return formatted
  const amount = numberValue(vehicle.priceGbp)
  if (amount === undefined) return 'Price unavailable'
  return new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP', maximumFractionDigits: 0 }).format(amount)
}

function formatMileage(value: unknown) {
  const mileage = numberValue(value)
  return mileage === undefined ? 'Unknown' : `${new Intl.NumberFormat('en-GB').format(mileage)} miles`
}

function optionalMiles(value: unknown) {
  const miles = numberValue(value)
  return miles === undefined ? 'Not supplied' : `${new Intl.NumberFormat('en-GB').format(miles)} miles`
}

function lifecycleLabel(value: unknown) {
  const text = textValue(value)
  if (!text) return 'Unknown'
  return text.replaceAll('_', ' ').toLowerCase().replace(/^./, (character) => character.toUpperCase())
}

function formatDate(value: unknown) {
  const text = textValue(value)
  if (!text) return 'Unknown'
  const date = new Date(text)
  if (Number.isNaN(date.getTime())) return 'Unknown'
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function sourceLine(data: Readonly<SafeRecord>, vehicle?: SafeRecord) {
  const source = recordValue(data.source)
  const label = textValue(source?.label) || textValue(vehicle?.sourceLabel) || 'Dealership source'
  const updatedAt = source?.refreshedAt || vehicle?.sourceUpdatedAt
  return `${label} · refreshed ${formatDate(updatedAt)}`
}

function filterLabels(filters?: SafeRecord) {
  if (!filters) return []
  const labels: string[] = []
  const plain = (label: string, key: string) => {
    const value = textValue(filters[key])
    if (value) labels.push(`${label}: ${value}`)
  }
  plain('Search', 'q')
  plain('Make', 'make')
  plain('Fuel', 'fuelType')
  plain('Body', 'bodyType')
  const minimum = numberValue(filters.minPriceGbp)
  const maximum = numberValue(filters.maxPriceGbp)
  const mileage = numberValue(filters.maxMileage)
  if (minimum !== undefined) labels.push(`From £${new Intl.NumberFormat('en-GB').format(minimum)}`)
  if (maximum !== undefined) labels.push(`Up to £${new Intl.NumberFormat('en-GB').format(maximum)}`)
  if (mileage !== undefined) labels.push(`Up to ${new Intl.NumberFormat('en-GB').format(mileage)} miles`)
  plain('Sort', 'sort')
  return labels
}

function textValue(value: unknown) {
  if (typeof value === 'string') return value.trim()
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return ''
}

function numberValue(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() && Number.isFinite(Number(value))) return Number(value)
  return undefined
}

function recordValue(value: unknown): SafeRecord | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? value as SafeRecord : undefined
}

function recordArray(value: unknown) {
  return Array.isArray(value) ? value.map(recordValue).filter((item): item is SafeRecord => Boolean(item)) : []
}

function stringArray(value: unknown) {
  return Array.isArray(value) ? value.map(textValue).filter(Boolean) : []
}

function createStyles() {
  const style = document.createElement('style')
  style.textContent = `
    :host { display: block; color: #102a26; font-family: Inter, system-ui, sans-serif; }
    * { box-sizing: border-box; }
    button, input { font: inherit; }
    .workspace { border: 1px solid #cddbd8; border-radius: 8px; background: #f8fbfa; overflow: hidden; }
    .workspace-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; padding: 20px; border-bottom: 1px solid #dbe6e3; background: #fff; }
    .workspace-header h2 { margin: 3px 0 0; color: #102a26; font-size: 20px; line-height: 1.25; letter-spacing: 0; }
    .eyebrow { margin: 0; color: #167464; font-size: 11px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
    .source, .muted { margin: 0; color: #60736f; font-size: 12px; line-height: 1.45; }
    .source { max-width: 320px; text-align: right; }
    .filter-pills { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
    .filter-pills span { border: 1px solid #cddbd8; border-radius: 999px; background: #f3f8f6; padding: 5px 8px; color: #34534d; font-size: 11px; font-weight: 700; }
    .vehicle-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 235px), 1fr)); gap: 12px; padding: 16px; }
    .vehicle-card { display: flex; min-width: 0; flex-direction: column; border: 1px solid #d7e1df; border-radius: 8px; background: #fff; overflow: hidden; box-shadow: 0 8px 22px rgba(17, 52, 46, .06); }
    .vehicle-image { display: block; width: 100%; aspect-ratio: 16 / 9; object-fit: cover; background: #edf3f1; }
    .vehicle-card__content { display: flex; flex: 1; flex-direction: column; gap: 9px; padding: 14px; }
    .vehicle-card h3 { margin: 0; color: #102a26; font-size: 17px; line-height: 1.3; letter-spacing: 0; }
    .clamp-two { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
    .price, .detail-price { color: #9e3b2c; font-size: 19px; line-height: 1.2; }
    .facts { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin: 2px 0 0; }
    .facts div, .facts-grid div { min-width: 0; }
    .facts dt, .facts-grid dt { color: #778983; font-size: 10px; font-weight: 800; text-transform: uppercase; }
    .facts dd, .facts-grid dd { margin: 2px 0 0; color: #263f3a; font-size: 12px; font-weight: 700; overflow-wrap: anywhere; }
    .selection-control { display: inline-flex; width: fit-content; min-height: 32px; align-items: center; gap: 7px; color: #34534d; font-size: 12px; font-weight: 750; cursor: pointer; }
    .selection-control input { width: 17px; height: 17px; accent-color: #167464; }
    .card-actions, .workspace-actions { display: flex; flex-wrap: wrap; gap: 7px; }
    .card-actions { margin-top: auto; padding-top: 3px; }
    .button { min-height: 36px; border: 1px solid transparent; border-radius: 6px; padding: 8px 11px; font-size: 12px; font-weight: 800; line-height: 1.2; cursor: pointer; transition: background .16s ease, border-color .16s ease, color .16s ease; }
    .button:focus-visible { outline: 3px solid rgba(30, 125, 233, .32); outline-offset: 2px; }
    .button:disabled { cursor: not-allowed; opacity: .45; }
    .button--primary { background: #146c5e; color: #fff; }
    .button--primary:hover:not(:disabled) { background: #0f574c; }
    .button--secondary { border-color: #b8cbc6; background: #fff; color: #183c35; }
    .button--secondary:hover:not(:disabled) { border-color: #6e9c91; background: #f1f7f5; }
    .button--quiet { background: transparent; color: #47645e; }
    .button--quiet:hover:not(:disabled) { background: #e9f1ef; color: #183c35; }
    .compare-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 14px 16px; border-top: 1px solid #dbe6e3; background: #fff; }
    .compare-toolbar p { margin: 0; color: #47645e; font-size: 12px; font-weight: 700; }
    .detail-layout { display: grid; grid-template-columns: minmax(220px, .8fr) minmax(280px, 1.2fr); gap: 18px; padding: 18px; }
    .detail-media, .detail-content { min-width: 0; }
    .detail-media .vehicle-image { margin-bottom: 14px; border-radius: 6px; }
    .detail-price { display: block; margin-bottom: 4px; }
    .facts-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin: 0 0 16px; }
    .facts-grid div { padding: 10px; border-left: 3px solid #80b7aa; background: #fff; }
    .summary { margin: 0; color: #2d4741; font-size: 14px; line-height: 1.6; }
    .feature-list { display: grid; grid-template-columns: 1fr 1fr; gap: 7px 16px; margin: 16px 0 0; padding: 0; list-style: none; }
    .feature-list li { position: relative; padding-left: 17px; color: #38564f; font-size: 12px; line-height: 1.45; }
    .feature-list li::before { position: absolute; left: 0; color: #167464; content: '✓'; font-weight: 900; }
    .workspace-actions { padding: 0 18px 18px; }
    .comparison-scroll { max-width: 100%; overflow-x: auto; padding: 16px; }
    .comparison-mobile { display: none; }
    .comparison-table { width: 100%; min-width: 680px; border-collapse: collapse; background: #fff; }
    .comparison-table th, .comparison-table td { padding: 11px; border: 1px solid #dbe6e3; text-align: left; vertical-align: top; font-size: 12px; }
    .comparison-table thead th { min-width: 170px; background: #f4f8f7; }
    .comparison-table thead th:first-child { min-width: 120px; }
    .comparison-table thead strong, .comparison-table thead span { display: block; margin-top: 6px; }
    .comparison-table thead span { color: #9e3b2c; font-size: 14px; }
    .comparison-table tbody th { color: #526b65; background: #f8fbfa; }
    .comparison-table .vehicle-image { max-width: 190px; border-radius: 5px; }
    .comparison-selection { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; padding: 0 16px 16px; }
    .comparison-selection > div { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; border: 1px solid #dbe6e3; border-radius: 7px; background: #fff; padding: 10px; }
    .comparison-selection strong { width: 100%; font-size: 12px; }
    .comparison-mobile__card { border: 1px solid #dbe6e3; border-radius: 8px; background: #fff; overflow: hidden; }
    .comparison-mobile__card h3, .comparison-mobile__card > strong, .comparison-mobile__card > .facts { margin-right: 12px; margin-left: 12px; }
    .comparison-mobile__card h3 { margin-top: 12px; margin-bottom: 6px; font-size: 16px; letter-spacing: 0; }
    .comparison-mobile__card > .facts { margin-top: 14px; margin-bottom: 14px; }
    .empty-state { margin: 16px; border: 1px dashed #aabdb8; border-radius: 8px; background: #fff; padding: 24px; text-align: center; }
    .empty-state strong { display: block; margin-bottom: 5px; }
    .empty-state p { margin: 0; color: #60736f; font-size: 13px; }
    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
    @media (max-width: 680px) {
      .workspace-header { align-items: flex-start; flex-direction: column; gap: 8px; padding: 15px; }
      .source { max-width: none; text-align: left; }
      .vehicle-grid { grid-template-columns: 1fr; padding: 12px; }
      .detail-layout { grid-template-columns: 1fr; padding: 12px; }
      .feature-list, .facts-grid { grid-template-columns: 1fr; }
      .workspace-actions { padding: 0 12px 12px; }
      .compare-toolbar { align-items: stretch; flex-direction: column; }
      .compare-toolbar .button { width: 100%; }
      .comparison-scroll { display: none; }
      .comparison-mobile { display: grid; gap: 10px; padding: 12px; }
      .comparison-selection { grid-template-columns: 1fr; padding: 0 12px 12px; }
    }
    @media (prefers-reduced-motion: reduce) {
      .button { transition: none; }
    }
  `
  return style
}
