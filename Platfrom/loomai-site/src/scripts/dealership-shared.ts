export type FacetValue = {
  value: string
  count: number
}

export type Vehicle = {
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

export type InventoryResponse = {
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

export type VehicleDetailResponse = {
  success: boolean
  vehicle: Vehicle
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

type StarterPrompt = {
  label: string
  query: string
  position: 'landing' | 'catalog' | 'search' | 'cart'
  mode: 'executor'
}

type AssistantOptions = {
  rootSelector: string
  maxChars: number
  contextLabel: string
  welcomeMessage: string
  placeholder: string
  emptyMessage: string
  starterPrompts: StarterPrompt[]
  starterSuggestions: string[]
  onRuntimeState: (
    state: 'checking' | 'ready' | 'unavailable',
    title: string,
    detail: string,
  ) => void
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

export async function resolveDealershipApiBaseUrl(app: HTMLElement) {
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

export async function initializeDealershipAssistant(
  apiBaseUrl: string,
  options: AssistantOptions,
) {
  options.onRuntimeState('checking', 'Connecting assistant', 'Checking the assigned LoomAI deployment')
  const descriptor = await fetchDealershipJson<RuntimeDescriptor>(`${apiBaseUrl}/api/public/runtime-descriptor`)
  if (!descriptor.success || !descriptor.ready || descriptor.integrationMode !== 'public-runtime-anonymous') {
    throw new Error('The assigned public runtime is not ready.')
  }
  const chatBaseUrl = normalizeBaseUrl(descriptor.chatBaseUrl)
  await loadWidgetBundle()
  if (!window.MaxMode) throw new Error('The LoomAI chat surface did not load.')
  registerDealershipActionPresentationElements()

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
      welcomeMessage: options.welcomeMessage,
      starterPrompts: options.starterPrompts,
      starterSuggestions: options.starterSuggestions,
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
      actionPresentation: dealershipActionPresentationConfig(),
      showUtilityPanel: false,
      companionDock: true,
      currentPageAttachment: {
        enabled: true,
        maxChars: options.maxChars,
        maxPages: 3,
        maxTotalChars: 10000,
        rootSelector: options.rootSelector,
        invalidateOnNavigation: false,
      },
      companionContextLabel: options.contextLabel,
      companionModeLabel: 'Vehicle assistant',
      companionPlaceholder: options.placeholder,
      companionEmptyMessage: options.emptyMessage,
    },
    onEvent(event: { type?: string }) {
      if (event?.type === 'error') {
        options.onRuntimeState('unavailable', 'Assistant needs attention', 'The deployment returned an operational error')
      }
    },
  })

  options.onRuntimeState('ready', 'Assistant ready', 'Connected directly to the assigned LoomAI deployment')
}

export function attachDealershipVehicle(vehicle: Vehicle) {
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

export function sendDealershipAssistantMessage(
  prompt: string,
  requestContext: Record<string, unknown>,
) {
  if (!window.MaxMode) return false
  window.setTimeout(() => {
    window.MaxMode?.sendMessage(prompt, {
      open: true,
      position: 'search',
      mode: 'executor',
      requestContext,
    })
  }, 0)
  return true
}

export async function fetchDealershipJson<T>(url: string): Promise<T> {
  const response = await fetch(url, {
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  })
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message = data && typeof data.message === 'string'
      ? data.message
      : `Request failed with HTTP ${response.status}.`
    throw new Error(message)
  }
  return data as T
}

export function safeDealershipImagePath(value: string) {
  if (/^\/assets\/demos\/dealership\/vehicle-[0-9]{2}\.webp$/.test(value)) return value
  return '/assets/demos/dealership/vehicle-01.webp'
}

export function formatDealershipMoney(major: number, currency: string) {
  return new Intl.NumberFormat('en-GB', {
    style: 'currency',
    currency: currency || 'GBP',
    maximumFractionDigits: 0,
  }).format(major)
}

export function formatDealershipNumber(value: number) {
  return new Intl.NumberFormat('en-GB').format(value)
}

export function formatDealershipDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'time unavailable'
  return new Intl.DateTimeFormat('en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function normalizeBaseUrl(value: string) {
  const parsed = new URL(value)
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('The integration URL is invalid.')
  return parsed.toString().replace(/\/$/, '')
}

async function loadWidgetBundle() {
  if (window.MaxMode) return
  const existing = document.querySelector<HTMLScriptElement>('script[data-max-mode-bundle]')
  if (existing) {
    await waitForScript(existing)
    return
  }
  const manifestResponse = await fetch('/vendor/max-mode-widget-manifest.json', {
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  })
  if (!manifestResponse.ok) {
    throw new Error('The LoomAI chat bundle manifest could not be loaded.')
  }
  const manifest = await manifestResponse.json() as {
    schemaVersion?: string
    file?: string
    sha256?: string
  }
  if (
    manifest.schemaVersion !== 'loomai-widget-bundle-v1'
    || !/^max-mode-widget\.[a-f0-9]{16}\.iife\.js$/.test(manifest.file || '')
    || !/^[a-f0-9]{64}$/.test(manifest.sha256 || '')
    || !manifest.file?.includes(manifest.sha256!.slice(0, 16))
  ) {
    throw new Error('The LoomAI chat bundle manifest is invalid.')
  }
  const script = document.createElement('script')
  script.src = `/vendor/${manifest.file}`
  script.async = true
  script.dataset.maxModeBundle = 'true'
  script.dataset.maxModeBundleSha256 = manifest.sha256
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
import {
  dealershipActionPresentationConfig,
  registerDealershipActionPresentationElements,
} from './dealership-action-presentations'
