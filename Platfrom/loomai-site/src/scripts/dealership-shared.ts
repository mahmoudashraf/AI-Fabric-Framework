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

type AssistantOptions = {
  onRuntimeState: (
    state: 'checking' | 'ready' | 'unavailable',
    title: string,
    detail: string,
  ) => void
}

type DealershipExperienceBrowserApi = {
  attachVehicle: (vehicle: Vehicle) => boolean
  sendMessage: (message: string, requestContext?: Record<string, unknown>) => boolean
}

type AIWorkspaceBrowserApi = {
  getController: () => unknown
  refresh: () => Promise<void>
}

declare global {
  interface Window {
    LoomAIDealershipExperience?: DealershipExperienceBrowserApi
    LoomAIWorkspace?: AIWorkspaceBrowserApi
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
  options: AssistantOptions,
) {
  options.onRuntimeState('checking', 'Connecting workspace', 'Resolving the current assigned LoomAI deployment')
  if (window.LoomAIWorkspace?.getController()) {
    options.onRuntimeState('ready', 'Workspace ready', 'Connected directly to the assigned LoomAI deployment')
    return
  }

  await new Promise<void>((resolve, reject) => {
    let settled = false
    const timeout = window.setTimeout(() => finish(
      new Error('The Platform-hosted AI Workspace did not become ready in time.'),
    ), 20_000)

    const onRuntimeState = (event: Event) => {
      const detail = (event as CustomEvent<{
        state?: 'checking' | 'ready' | 'unavailable'
        title?: string
        detail?: string
      }>).detail || {}
      if (!detail.state) return
      options.onRuntimeState(
        detail.state,
        detail.title || 'AI Workspace',
        detail.detail || 'The workspace state changed.',
      )
      if (detail.state === 'ready') finish()
      if (detail.state === 'unavailable') finish(new Error(detail.detail || 'The AI Workspace is unavailable.'))
    }
    const onReady = () => {
      options.onRuntimeState('ready', 'Workspace ready', 'Connected directly to the assigned LoomAI deployment')
      finish()
    }
    const onError = (event: Event) => {
      const detail = (event as CustomEvent<{ message?: string }>).detail
      finish(new Error(detail?.message || 'The Platform-hosted AI Workspace could not be installed.'))
    }
    function finish(error?: Error) {
      if (settled) return
      settled = true
      window.clearTimeout(timeout)
      window.removeEventListener('loomai:workspace-runtime-state', onRuntimeState)
      window.removeEventListener('loomai:workspace-ready', onReady)
      window.removeEventListener('loomai:workspace-error', onError)
      if (error) reject(error)
      else resolve()
    }

    window.addEventListener('loomai:workspace-runtime-state', onRuntimeState)
    window.addEventListener('loomai:workspace-ready', onReady)
    window.addEventListener('loomai:workspace-error', onError)

    if (window.LoomAIWorkspace?.getController()) onReady()
  })
}

export function attachDealershipVehicle(vehicle: Vehicle) {
  return window.LoomAIDealershipExperience?.attachVehicle(vehicle) === true
}

export function sendDealershipAssistantMessage(
  prompt: string,
  requestContext: Record<string, unknown>,
) {
  return window.LoomAIDealershipExperience?.sendMessage(prompt, requestContext) === true
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
