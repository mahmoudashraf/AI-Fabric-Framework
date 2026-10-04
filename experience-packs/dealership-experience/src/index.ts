import {
  dealershipActionPresentationConfig,
  registerDealershipActionPresentationElements,
} from './action-presentations'
import { createDealershipToolGroups } from './tool-groups'
import type {
  DealershipExperienceConfig,
  DealershipExperienceController,
  DealershipRuntimeDescriptor,
  DealershipRuntimeState,
  DealershipVehicleContext,
  MaxModeBrowserApi,
} from './types'

export * from './types'
export { createDealershipToolGroups } from './tool-groups'
export {
  dealershipActionPresentationConfig,
  registerDealershipActionPresentationElements,
} from './action-presentations'

export const version = '1.0.0'

let activeController: DealershipExperienceController | undefined

export async function mount(
  input: DealershipExperienceConfig,
): Promise<DealershipExperienceController> {
  const config = validateConfig(input)
  notifyRuntimeState(config, 'checking', 'Connecting assistant', 'Checking the assigned LoomAI deployment')

  try {
    const descriptorUrl = resolveUrl(
      config.runtimeDescriptorPath || '/api/public/runtime-descriptor',
      config.backendBaseUrl,
    )
    const descriptor = await fetchJson<DealershipRuntimeDescriptor>(descriptorUrl)
    assertRuntimeDescriptor(descriptor)
    await loadMaxModeBundle(config.widget.manifestUrl)
    const maxMode = requireMaxMode()

    activeController?.destroy()
    registerDealershipActionPresentationElements()

    const chatBaseUrl = normalizeHttpUrl(descriptor.chatBaseUrl)
    const routes = descriptor.runtimeRoutes
    const capabilities = {
      comparison: config.capabilities?.comparison !== false,
      testDrive: config.capabilities?.testDrive === true,
      callback: config.capabilities?.callback === true,
    }
    const toolGroups = config.toolGroups || createDealershipToolGroups({
      pageKind: config.page.kind,
      subjectLabel: config.page.subjectLabel,
      capabilities,
    })
    const copy = resolveCopy(config)

    maxMode.init({
      integrationMode: 'public-runtime-anonymous',
      apiConfig: {
        chatBaseUrl,
        runtimeRoutes: {
          chatQueryUrl: absoluteRuntimeUrl(chatBaseUrl, routes.queryUrl),
          suggestionsUrl: absoluteRuntimeUrl(chatBaseUrl, routes.suggestionsUrl),
          authContextUrl: absoluteRuntimeUrl(chatBaseUrl, routes.authContextUrl),
          shellConfigUrl: absoluteRuntimeUrl(chatBaseUrl, routes.shellConfigUrl),
          conversationsUrl: absoluteRuntimeUrl(chatBaseUrl, routes.conversationsUrl),
          conversationItemUrlTemplate: absoluteRuntimeTemplateUrl(
            chatBaseUrl,
            routes.conversationItemUrlTemplate,
          ),
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
        primaryColor: config.theme?.primaryColor || '#123b35',
        borderRadius: config.theme?.borderRadius || '0.5rem',
        fontFamily: config.theme?.fontFamily || 'Inter, system-ui, sans-serif',
        darkMode: config.theme?.darkMode ?? false,
      },
      launcher: false,
      host: {
        assistantLabel: config.dealer.assistantLabel,
        welcomeMessage: copy.welcomeMessage,
        toolGroups,
        starterSuggestions: copy.starterSuggestions,
        requestContext: {
          ...config.requestContext,
          dealershipId: config.dealer.id,
          preferredVectorSpaces: descriptor.retrievalVectorSpaces,
          sourceMode: config.dealer.sourceMode || 'DEALERSHIP_INVENTORY',
        },
        defaultConversationMode: 'executor',
        effectiveConversationMode: 'executor',
        allowedConversationModes: ['executor'],
        actionPresentation: dealershipActionPresentationConfig({
          ...config.presentation,
          capabilities,
        }),
        showUtilityPanel: false,
        companionDock: true,
        currentPageAttachment: {
          enabled: true,
          maxChars: config.page.maxChars || 1800,
          maxPages: config.page.maxPages || 3,
          maxTotalChars: config.page.maxTotalChars || 10000,
          rootSelector: config.page.rootSelector,
          invalidateOnNavigation: false,
        },
        companionContextLabel: config.page.contextLabel,
        companionModeLabel: copy.companionModeLabel,
        companionPlaceholder: copy.placeholder,
        companionEmptyMessage: copy.emptyMessage,
      },
      onEvent(event: { type?: string; data?: unknown; timestamp?: string }) {
        config.onEvent?.(event)
        if (event?.type === 'error') {
          notifyRuntimeState(
            config,
            'unavailable',
            'Assistant needs attention',
            'The deployment returned an operational error',
          )
        }
      },
    })

    const controller = createController(maxMode, descriptor)
    activeController = controller
    notifyRuntimeState(
      config,
      'ready',
      'Assistant ready',
      'Connected directly to the assigned LoomAI deployment',
    )
    return controller
  } catch (error) {
    notifyRuntimeState(
      config,
      'unavailable',
      'Assistant unavailable',
      error instanceof Error ? error.message : 'The assigned deployment could not be reached.',
    )
    throw error
  }
}

export function attachVehicle(vehicle: DealershipVehicleContext) {
  if (!activeController) return false
  activeController.attachVehicle(vehicle)
  return true
}

export function sendMessage(
  message: string,
  requestContext: Record<string, unknown> = {},
) {
  if (!activeController) return false
  activeController.sendMessage(message, requestContext)
  return true
}

export function destroy() {
  activeController?.destroy()
  activeController = undefined
}

export async function autoMountFromCurrentScript() {
  const script = document.currentScript as HTMLScriptElement | null
  const bootstrapUrl = script?.dataset.bootstrapUrl?.trim()
  if (!bootstrapUrl) return undefined
  const config = await fetchJson<DealershipExperienceConfig>(
    new URL(bootstrapUrl, document.baseURI).toString(),
  )
  return mount(config)
}

function createController(
  maxMode: MaxModeBrowserApi,
  descriptor: DealershipRuntimeDescriptor,
): DealershipExperienceController {
  let destroyed = false
  return {
    descriptor,
    attachVehicle(vehicle) {
      if (destroyed) return
      const contextLabel = vehicleLabel(vehicle)
      maxMode.attachItem({
        type: 'vehicle',
        contextLabel,
        data: vehicleAttachment(vehicle, descriptor.inventoryVectorSpace, contextLabel),
      })
    },
    sendMessage(message, requestContext = {}) {
      if (destroyed || !message.trim()) return
      maxMode.sendMessage(message, {
        open: true,
        position: 'search',
        mode: 'executor',
        requestContext,
      })
    },
    destroy() {
      if (destroyed) return
      destroyed = true
      maxMode.destroy()
      if (activeController === this) activeController = undefined
    },
  }
}

function vehicleAttachment(
  vehicle: DealershipVehicleContext,
  vectorSpace: string,
  contextLabel: string,
) {
  return {
    id: vehicle.id,
    vectorSpace,
    entityType: vectorSpace,
    stockId: vehicle.stockId,
    name: contextLabel,
    content: [contextLabel, vehicle.derivative, vehicle.summary].filter(Boolean).join('. '),
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
  }
}

function vehicleLabel(vehicle: DealershipVehicleContext) {
  return [vehicle.registrationYear, vehicle.make, vehicle.model]
    .map((value) => String(value || '').trim())
    .filter(Boolean)
    .join(' ')
}

function resolveCopy(config: DealershipExperienceConfig) {
  const subject = config.page.subjectLabel?.trim()
  const detailPage = config.page.kind === 'vehicle-detail'
  const defaultSuggestions = detailPage
    ? [
        'Which features stand out?',
        'How does its mileage compare?',
        ...(config.capabilities?.testDrive === true ? ['Request a test drive'] : []),
      ]
    : [
        ...(config.capabilities?.comparison !== false ? ['Compare electric cars'] : []),
        'What is under £30,000?',
        'Which car has the best luggage space?',
      ]
  return {
    welcomeMessage: config.copy?.welcomeMessage || (detailPage && subject
      ? `I can answer questions about this ${subject} using current indexed dealership evidence and live facts.`
      : 'I can search and compare current inventory using indexed dealership evidence and live facts.'),
    placeholder: config.copy?.placeholder || (detailPage
      ? 'Ask about this vehicle...'
      : 'Ask about a vehicle, feature, budget or comparison...'),
    emptyMessage: config.copy?.emptyMessage || (detailPage
      ? 'Attach this page for its visible details, or ask a grounded question about this vehicle.'
      : 'Ask about current vehicles, compare options, or start an available dealership request.'),
    companionModeLabel: config.copy?.companionModeLabel || 'Vehicle assistant',
    starterSuggestions: config.copy?.starterSuggestions || defaultSuggestions,
  }
}

function validateConfig(input: DealershipExperienceConfig) {
  if (!input || typeof input !== 'object') throw new Error('Dealership experience configuration is required.')
  normalizeHttpUrl(input.backendBaseUrl)
  if (!input.widget?.manifestUrl?.trim()) throw new Error('A Max Mode widget manifest URL is required.')
  if (!input.dealer?.id?.trim()) throw new Error('A dealership identifier is required.')
  if (!input.dealer?.assistantLabel?.trim()) throw new Error('An assistant label is required.')
  if (!['inventory', 'vehicle-detail'].includes(input.page?.kind)) {
    throw new Error('The dealership page kind must be inventory or vehicle-detail.')
  }
  if (!input.page?.rootSelector?.trim()) throw new Error('A page content root selector is required.')
  if (!input.page?.contextLabel?.trim()) throw new Error('A visible page context label is required.')
  if (input.toolGroups) assertToolGroups(input.toolGroups)
  return input
}

function assertToolGroups(groups: DealershipExperienceConfig['toolGroups']) {
  if (!groups) return
  if (!['default', 'contextual'].includes(groups.initialScope)) {
    throw new Error('Tool groups must use a default or contextual initial scope.')
  }
  for (const group of [groups.default, groups.contextual]) {
    if (!group?.label?.trim() || !Array.isArray(group.tools) || group.tools.length === 0) {
      throw new Error('Each dealership tool group requires a label and at least one tool.')
    }
  }
}

function assertRuntimeDescriptor(descriptor: DealershipRuntimeDescriptor) {
  if (!descriptor?.success || !descriptor.ready) {
    throw new Error('The assigned public runtime is not ready.')
  }
  if (descriptor.integrationMode !== 'public-runtime-anonymous') {
    throw new Error('The assigned runtime does not expose the required anonymous browser contract.')
  }
  if (!descriptor.inventoryVectorSpace?.trim()) {
    throw new Error('The runtime descriptor has no inventory vector space.')
  }
  if (!Array.isArray(descriptor.retrievalVectorSpaces)
    || descriptor.retrievalVectorSpaces.length === 0
    || descriptor.retrievalVectorSpaces.some((value) => !value?.trim())) {
    throw new Error('The runtime descriptor has no valid retrieval vector spaces.')
  }
  normalizeHttpUrl(descriptor.chatBaseUrl)
  const routes = descriptor.runtimeRoutes
  for (const key of [
    'bootstrapUrl',
    'renewUrl',
    'queryUrl',
    'suggestionsUrl',
    'authContextUrl',
    'shellConfigUrl',
    'conversationsUrl',
    'conversationItemUrlTemplate',
  ] as const) {
    if (!routes?.[key]?.trim()) throw new Error(`The runtime descriptor is missing ${key}.`)
  }
}

function notifyRuntimeState(
  config: DealershipExperienceConfig,
  state: DealershipRuntimeState,
  title: string,
  detail: string,
) {
  config.onRuntimeState?.(state, title, detail)
}

async function fetchJson<T>(url: string): Promise<T> {
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

async function loadMaxModeBundle(manifestUrlValue: string) {
  if (window.MaxMode) return
  const existing = document.querySelector<HTMLScriptElement>('script[data-max-mode-bundle]')
  if (existing) {
    await waitForScript(existing, () => Boolean(window.MaxMode))
    return
  }

  const manifestUrl = new URL(manifestUrlValue, document.baseURI)
  const manifest = await fetchJson<{
    schemaVersion?: string
    file?: string
    sha256?: string
  }>(manifestUrl.toString())
  if (
    manifest.schemaVersion !== 'loomai-widget-bundle-v1'
    || !/^max-mode-widget\.[a-f0-9]{16}\.iife\.js$/.test(manifest.file || '')
    || !/^[a-f0-9]{64}$/.test(manifest.sha256 || '')
    || !manifest.file?.includes(manifest.sha256!.slice(0, 16))
  ) {
    throw new Error('The LoomAI chat bundle manifest is invalid.')
  }
  const bundleFile = manifest.file as string
  const bundleSha256 = manifest.sha256 as string

  const script = document.createElement('script')
  script.src = new URL(bundleFile, manifestUrl).toString()
  script.async = true
  script.crossOrigin = 'anonymous'
  script.integrity = `sha256-${hexToBase64(bundleSha256)}`
  script.dataset.maxModeBundle = 'true'
  script.dataset.maxModeBundleSha256 = bundleSha256
  document.head.append(script)
  await waitForScript(script, () => Boolean(window.MaxMode))
}

function waitForScript(script: HTMLScriptElement, ready: () => boolean) {
  return new Promise<void>((resolve, reject) => {
    if (ready()) {
      resolve()
      return
    }
    script.addEventListener('load', () => {
      if (ready()) resolve()
      else reject(new Error('The LoomAI chat bundle loaded without its browser API.'))
    }, { once: true })
    script.addEventListener('error', () => {
      reject(new Error('The LoomAI chat bundle could not be loaded.'))
    }, { once: true })
  })
}

function requireMaxMode() {
  if (!window.MaxMode) throw new Error('The LoomAI chat surface did not load.')
  return window.MaxMode
}

function normalizeHttpUrl(value: string) {
  const parsed = new URL(value, document.baseURI)
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('The integration URL is invalid.')
  return parsed.toString().replace(/\/$/, '')
}

function resolveUrl(value: string, baseUrl: string) {
  if (/^https?:\/\//i.test(value)) return normalizeHttpUrl(value)
  return new URL(value.startsWith('/') ? value : `/${value}`, `${normalizeHttpUrl(baseUrl)}/`).toString()
}

function absoluteRuntimeUrl(baseUrl: string, value: string) {
  if (/^https?:\/\//i.test(value)) return normalizeHttpUrl(value)
  const base = new URL(baseUrl)
  return new URL(value.startsWith('/') ? value : `/${value}`, base.origin).toString()
}

function absoluteRuntimeTemplateUrl(baseUrl: string, value: string) {
  return absoluteRuntimeUrl(baseUrl, value)
    .replaceAll('%7BconversationId%7D', '{conversationId}')
    .replaceAll('%7bconversationId%7d', '{conversationId}')
}

function hexToBase64(value: string) {
  const bytes = value.match(/.{2}/g)?.map((pair) => Number.parseInt(pair, 16)) || []
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary)
}
