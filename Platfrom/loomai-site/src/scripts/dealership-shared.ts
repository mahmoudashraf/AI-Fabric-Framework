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
  pageKind: 'inventory' | 'vehicle-detail'
  rootSelector: string
  maxChars: number
  contextLabel: string
  subjectLabel?: string
  welcomeMessage: string
  placeholder: string
  emptyMessage: string
  starterSuggestions: string[]
  onRuntimeState: (
    state: 'checking' | 'ready' | 'unavailable',
    title: string,
    detail: string,
  ) => void
}

type DealershipExperienceBrowserApi = {
  mount: (config: Record<string, unknown>) => Promise<unknown>
  attachVehicle: (vehicle: Vehicle) => boolean
  sendMessage: (message: string, requestContext?: Record<string, unknown>) => boolean
}

declare global {
  interface Window {
    LoomAIDealershipExperience?: DealershipExperienceBrowserApi
  }
}

const DEMO_IMAGE_FALLBACKS: Record<string, string> = {
  'DEMO-1001': '/assets/demos/dealership/vehicle-01.webp',
  'DEMO-1002': '/assets/demos/dealership/vehicle-02.webp',
  'DEMO-1003': '/assets/demos/dealership/vehicle-03.webp',
  'DEMO-1004': '/assets/demos/dealership/vehicle-04.webp',
  'DEMO-1005': '/assets/demos/dealership/vehicle-05.webp',
  'DEMO-1006': '/assets/demos/dealership/vehicle-04.webp',
  'DEMO-1099': '/assets/demos/dealership/vehicle-03.webp',
}

const DEMO_DETAIL_SLUGS: Record<string, string> = {
  'DEMO-1001': 'aster-e1-motion',
  'DEMO-1002': 'northstar-s4-touring',
  'DEMO-1003': 'morrow-c2-city',
  'DEMO-1004': 'caldera-x6-adventure',
  'DEMO-1005': 'arden-v3-executive',
  'DEMO-1006': 'aster-e2-sport',
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
  await loadDealershipExperienceBundle()
  if (!window.LoomAIDealershipExperience) {
    throw new Error('The LoomAI dealership experience did not load.')
  }
  await window.LoomAIDealershipExperience.mount({
    backendBaseUrl: apiBaseUrl,
    widget: {
      manifestUrl: '/vendor/max-mode-widget-manifest.json',
    },
    dealer: {
      id: 'dealer-demo-001',
      assistantLabel: 'Northfield AI',
      sourceMode: 'DEMONSTRATION_INVENTORY',
    },
    page: {
      kind: options.pageKind,
      rootSelector: options.rootSelector,
      maxChars: options.maxChars,
      maxPages: 3,
      maxTotalChars: 10000,
      contextLabel: options.contextLabel,
      subjectLabel: options.subjectLabel,
    },
    capabilities: {
      comparison: true,
      testDrive: true,
      callback: true,
    },
    copy: {
      welcomeMessage: options.welcomeMessage,
      placeholder: options.placeholder,
      emptyMessage: options.emptyMessage,
      starterSuggestions: options.starterSuggestions,
    },
    presentation: {
      detailBasePath: '/demos/dealership-ai/vehicles/',
      imageHostAllowlist: [
        'external-vehicle-provider-simulator.46.224.145.148.sslip.io',
        'm.atcdn.co.uk',
      ],
      imageFallbacks: DEMO_IMAGE_FALLBACKS,
      detailSlugs: DEMO_DETAIL_SLUGS,
    },
    onRuntimeState: options.onRuntimeState,
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

async function loadDealershipExperienceBundle() {
  if (window.LoomAIDealershipExperience) return
  const existing = document.querySelector<HTMLScriptElement>('script[data-dealership-experience-bundle]')
  if (existing) {
    await waitForScript(existing, () => Boolean(window.LoomAIDealershipExperience))
    return
  }
  const manifestResponse = await fetch('/vendor/dealership-experience-manifest.json', {
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  })
  if (!manifestResponse.ok) {
    throw new Error('The LoomAI dealership experience manifest could not be loaded.')
  }
  const manifest = await manifestResponse.json() as {
    schemaVersion?: string
    file?: string
    sha256?: string
  }
  if (
    manifest.schemaVersion !== 'loomai-dealership-experience-bundle-v1'
    || !/^dealership-experience\.[a-f0-9]{16}\.iife\.js$/.test(manifest.file || '')
    || !/^[a-f0-9]{64}$/.test(manifest.sha256 || '')
    || !manifest.file?.includes(manifest.sha256!.slice(0, 16))
  ) {
    throw new Error('The LoomAI dealership experience manifest is invalid.')
  }
  const bundleFile = manifest.file as string
  const bundleSha256 = manifest.sha256 as string
  const script = document.createElement('script')
  script.src = `/vendor/${bundleFile}`
  script.async = true
  script.crossOrigin = 'anonymous'
  script.integrity = `sha256-${hexToBase64(bundleSha256)}`
  script.dataset.dealershipExperienceBundle = 'true'
  script.dataset.dealershipExperienceBundleSha256 = bundleSha256
  document.head.append(script)
  await waitForScript(script, () => Boolean(window.LoomAIDealershipExperience))
}

function waitForScript(script: HTMLScriptElement, ready: () => boolean) {
  return new Promise<void>((resolve, reject) => {
    if (ready()) {
      resolve()
      return
    }
    script.addEventListener('load', () => {
      if (ready()) resolve()
      else reject(new Error('The LoomAI dealership experience loaded without its browser API.'))
    }, { once: true })
    script.addEventListener('error', () => reject(new Error('The LoomAI dealership experience could not be loaded.')), { once: true })
  })
}

function hexToBase64(value: string) {
  const bytes = value.match(/.{2}/g)?.map((pair) => Number.parseInt(pair, 16)) || []
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary)
}
