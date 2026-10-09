import {
  dealershipActionPresentationConfig,
  registerDealershipActionPresentationElements,
} from './action-presentations'
import { createDealershipToolGroups } from './tool-groups'
import { createDealershipToolRail } from './tool-rail'
import type {
  AIWorkspacePackMountContext,
  DealershipExperienceConfig,
  DealershipExperienceController,
  DealershipRuntimeState,
  DealershipVehicleContext,
  MaxModeBrowserApi,
} from './types'

export * from './types'
export { createDealershipToolGroups } from './tool-groups'
export { createDealershipToolRail } from './tool-rail'
export {
  dealershipActionPresentationConfig,
  registerDealershipActionPresentationElements,
} from './action-presentations'

export const code = 'dealership'
export const version = '1.1.0'

let activeController: DealershipExperienceController | undefined
const queuedVehicles: DealershipVehicleContext[] = []
const queuedMessages: Array<{ message: string; requestContext: Record<string, unknown> }> = []

export async function mountInstallation(
  context: AIWorkspacePackMountContext,
): Promise<DealershipExperienceController> {
  const config = validateConfig(context.configuration as unknown as DealershipExperienceConfig)
  const page = resolvePage(config)
  notifyRuntimeState(config, 'checking', 'Connecting workspace', 'Preparing the assigned LoomAI deployment')

  try {
    activeController?.destroy()
    registerDealershipActionPresentationElements()
    const maxMode = context.maxMode
    const capabilities = {
      comparison: config.capabilities?.comparison !== false,
      testDrive: config.capabilities?.testDrive === true,
      callback: config.capabilities?.callback === true,
    }
    const toolGroups = config.toolGroups || createDealershipToolGroups({
      pageKind: page.kind,
      subjectLabel: page.subjectLabel,
      capabilities,
    })
    const toolRail = config.toolRail || createDealershipToolRail()
    const copy = resolveCopy(config, page.kind, page.subjectLabel)
    maxMode.init({
      ...context.widgetConfig,
      features: {
        ...((context.widgetConfig.features as Record<string, unknown> | undefined) || {}),
        cart: false,
        debug: false,
        conversations: (context.widgetConfig.features as Record<string, unknown> | undefined)?.conversations !== false,
        quickActions: true,
      },
      theme: {
        primaryColor: config.theme?.primaryColor || '#123b35',
        borderRadius: config.theme?.borderRadius || '0.5rem',
        fontFamily: config.theme?.fontFamily || 'Inter, system-ui, sans-serif',
        darkMode: config.theme?.darkMode ?? false,
      },
      launcher: false,
      beforeNewConversation: context.refreshAssignment,
      host: {
        assistantLabel: config.dealer.assistantLabel,
        welcomeMessage: copy.welcomeMessage,
        toolGroups,
        toolRail,
        starterSuggestions: copy.starterSuggestions,
        requestContext: {
          ...config.requestContext,
          dealershipId: config.dealer.id,
          sourceMode: config.dealer.sourceMode || 'DEALERSHIP_INVENTORY',
          workspaceInstallationId: context.installationId,
          assignmentRevision: context.assignmentRevision,
        },
        defaultConversationMode: 'executor',
        effectiveConversationMode: 'executor',
        allowedConversationModes: ['executor'],
        actionPresentation: dealershipActionPresentationConfig({ ...config.presentation, capabilities }),
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
        companionContextLabel: page.contextLabel,
        companionModeLabel: copy.companionModeLabel,
        companionPlaceholder: copy.placeholder,
        companionEmptyMessage: copy.emptyMessage,
      },
      onEvent(event: { type?: string; data?: unknown; timestamp?: string }) {
        config.onEvent?.(event)
        window.dispatchEvent(new CustomEvent('loomai:dealership-experience-event', { detail: event }))
        if (event?.type === 'error') {
          notifyRuntimeState(config, 'unavailable', 'Workspace needs attention', 'The deployment returned an operational error')
        }
      },
    })

    const controller = createController(maxMode, context, config)
    activeController = controller
    for (const vehicle of queuedVehicles.splice(0)) controller.attachVehicle(vehicle)
    for (const pending of queuedMessages.splice(0)) controller.sendMessage(pending.message, pending.requestContext)
    notifyRuntimeState(config, 'ready', 'Workspace ready', 'Connected directly to the assigned LoomAI deployment')
    return controller
  } catch (error) {
    notifyRuntimeState(config, 'unavailable', 'Workspace unavailable',
      error instanceof Error ? error.message : 'The assigned deployment could not be reached.')
    throw error
  }
}

export function registerExperiencePack() {
  if (!window.LoomAIWorkspace) {
    throw new Error('The Platform AI Workspace installer is required before the dealership experience pack.')
  }
  window.LoomAIWorkspace.registerExperiencePack({ code, version, mount: mountInstallation })
}

export function attachVehicle(vehicle: DealershipVehicleContext) {
  if (!activeController) {
    queuedVehicles.splice(0, queuedVehicles.length, vehicle)
    return true
  }
  activeController.attachVehicle(vehicle)
  return true
}

export function sendMessage(message: string, requestContext: Record<string, unknown> = {}) {
  if (!message.trim()) return false
  if (!activeController) {
    queuedMessages.push({ message, requestContext })
    return true
  }
  activeController.sendMessage(message, requestContext)
  return true
}

export function destroy() {
  activeController?.destroy()
  activeController = undefined
  queuedVehicles.length = 0
  queuedMessages.length = 0
}

function createController(
  maxMode: MaxModeBrowserApi,
  context: AIWorkspacePackMountContext,
  config: DealershipExperienceConfig,
): DealershipExperienceController {
  let destroyed = false
  const inventoryVectorSpace = config.knowledge?.inventoryVectorSpace?.trim() || 'dealer-vehicle'
  return {
    installationId: context.installationId,
    connectionMode: context.connectionMode,
    attachVehicle(vehicle) {
      if (destroyed) return
      const contextLabel = vehicleLabel(vehicle)
      maxMode.attachItem({
        type: 'vehicle',
        contextLabel,
        data: vehicleAttachment(vehicle, inventoryVectorSpace, contextLabel),
      })
    },
    sendMessage(message, requestContext = {}) {
      if (destroyed || !message.trim()) return
      maxMode.sendMessage(message, { open: true, position: 'search', mode: 'executor', requestContext })
    },
    destroy() {
      if (destroyed) return
      destroyed = true
      maxMode.destroy()
      if (activeController === this) activeController = undefined
    },
  }
}

function vehicleAttachment(vehicle: DealershipVehicleContext, vectorSpace: string, contextLabel: string) {
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
    .map((value) => String(value || '').trim()).filter(Boolean).join(' ')
}

function resolvePage(config: DealershipExperienceConfig) {
  const inferredDetail = /\/vehicles\//.test(window.location.pathname)
    || document.querySelector('[data-dealership-vehicle-detail]') !== null
  const kind = config.page.kind === 'auto' ? (inferredDetail ? 'vehicle-detail' : 'inventory') : config.page.kind
  const heading = document.querySelector(config.page.rootSelector)?.querySelector('h1')?.textContent?.trim()
  const subjectLabel = config.page.subjectLabel?.trim() || (kind === 'vehicle-detail' ? heading : undefined)
  return {
    kind,
    subjectLabel,
    contextLabel: kind === 'vehicle-detail' && subjectLabel ? subjectLabel : config.page.contextLabel,
  } as const
}

function resolveCopy(config: DealershipExperienceConfig, kind: 'inventory' | 'vehicle-detail', subject?: string) {
  const detailPage = kind === 'vehicle-detail'
  const defaultSuggestions = detailPage
    ? ['Which features stand out?', 'How does its mileage compare?', ...(config.capabilities?.testDrive === true ? ['Request a test drive'] : [])]
    : [...(config.capabilities?.comparison !== false ? ['Compare electric cars'] : []), 'What is under £30,000?', 'Which car has the best luggage space?']
  return {
    welcomeMessage: config.copy?.welcomeMessage || (detailPage && subject
      ? `I can answer questions about this ${subject} using current indexed dealership evidence and live facts.`
      : 'I can search and compare current inventory using indexed dealership evidence and live facts.'),
    placeholder: config.copy?.placeholder || (detailPage ? 'Ask about this vehicle...' : 'Ask about a vehicle, feature, budget or comparison...'),
    emptyMessage: config.copy?.emptyMessage || (detailPage
      ? 'Attach this page for its visible details, or ask a grounded question about this vehicle.'
      : 'Ask about current vehicles, compare options, or start an available dealership request.'),
    companionModeLabel: config.copy?.companionModeLabel || 'Vehicle assistant',
    starterSuggestions: config.copy?.starterSuggestions || defaultSuggestions,
  }
}

function validateConfig(input: DealershipExperienceConfig) {
  if (!input || typeof input !== 'object') throw new Error('Dealership experience configuration is required.')
  if (!input.dealer?.id?.trim()) throw new Error('A dealership identifier is required.')
  if (!input.dealer?.assistantLabel?.trim()) throw new Error('An assistant label is required.')
  if (!['inventory', 'vehicle-detail', 'auto'].includes(input.page?.kind)) {
    throw new Error('The dealership page kind must be inventory, vehicle-detail or auto.')
  }
  if (!input.page?.rootSelector?.trim()) throw new Error('A page content root selector is required.')
  if (!input.page?.contextLabel?.trim()) throw new Error('A visible page context label is required.')
  if (input.toolGroups) assertToolGroups(input.toolGroups)
  if (input.toolRail) assertToolRail(input.toolRail)
  return input
}

function assertToolGroups(groups: DealershipExperienceConfig['toolGroups']) {
  if (!groups) return
  if (!['default', 'contextual'].includes(groups.initialScope)) throw new Error('Tool groups require a valid initial scope.')
  for (const group of [groups.default, groups.contextual]) {
    if (!group?.label?.trim() || !Array.isArray(group.tools) || group.tools.length === 0) {
      throw new Error('Each dealership tool group requires a label and at least one tool.')
    }
  }
}

function assertToolRail(rail: DealershipExperienceConfig['toolRail']) {
  if (!rail || !Array.isArray(rail.items) || rail.items.length > 6) {
    throw new Error('The dealership tool rail requires at most six configured items.')
  }
  const ids = new Set<string>()
  for (const item of rail.items) {
    if (!item?.id?.trim() || !item.label?.trim() || ids.has(item.id.trim())) {
      throw new Error('Each dealership tool rail item requires a unique id and visible label.')
    }
    ids.add(item.id.trim())
    if (!['open-tools', 'open-documents', 'prompt'].includes(item.action)) {
      throw new Error('A dealership tool rail item uses an unsupported action.')
    }
    if (item.action === 'prompt' && !item.query?.trim()) throw new Error('Prompt tool rail items require a query.')
  }
}

function notifyRuntimeState(
  config: DealershipExperienceConfig,
  state: DealershipRuntimeState,
  title: string,
  detail: string,
) {
  config.onRuntimeState?.(state, title, detail)
  window.dispatchEvent(new CustomEvent('loomai:workspace-runtime-state', { detail: { state, title, detail } }))
}
