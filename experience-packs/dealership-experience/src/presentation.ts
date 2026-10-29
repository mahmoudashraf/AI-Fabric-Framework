import { createDealershipToolGroups } from './tool-groups'
import { createDealershipToolRail } from './tool-rail'
import type { DealershipExperienceConfig } from './types'

export type DealershipPresentationPage = {
  kind: 'inventory' | 'vehicle-detail'
  subjectLabel?: string
  contextLabel?: string
}

/** DOM inference stays in live mounting; previews use the configured page kind. */
export function resolveDealershipPresentation(config: Partial<DealershipExperienceConfig>, inferredPage?: DealershipPresentationPage) {
  const page = inferredPage ?? {
    kind: config.page?.kind === 'vehicle-detail' ? 'vehicle-detail' : 'inventory',
    subjectLabel: config.page?.subjectLabel,
    contextLabel: config.page?.contextLabel,
  }
  const capabilities = {
    comparison: config.capabilities?.comparison !== false,
    testDrive: config.capabilities?.testDrive === true,
    callback: config.capabilities?.callback === true,
  }
  const detailPage = page.kind === 'vehicle-detail'
  const defaultSuggestions = detailPage
    ? ['Which features stand out?', 'How does its mileage compare?', ...(capabilities.testDrive ? ['Request a test drive'] : [])]
    : [...(capabilities.comparison ? ['Compare electric cars'] : []), 'What is under £30,000?', 'Which car has the best luggage space?']
  return {
    identityKey: 'dealer',
    limitations: config.page?.kind === 'auto' ? ['Automatic page detection uses the inventory page in preview; the host page is not inspected.'] : [],
    assistantLabel: config.dealer?.assistantLabel ?? 'Your assistant',
    capabilities,
    theme: {
      primaryColor: config.theme?.primaryColor ?? '#123b35',
      logoUrl: config.theme?.logoUrl,
      borderRadius: config.theme?.borderRadius ?? '0.5rem',
      fontFamily: config.theme?.fontFamily ?? 'Inter, system-ui, sans-serif',
      darkMode: config.theme?.darkMode ?? false,
    },
    toolGroups: config.toolGroups === undefined ? createDealershipToolGroups({ pageKind: page.kind, subjectLabel: page.subjectLabel, capabilities }) : config.toolGroups,
    toolRail: config.toolRail === undefined ? createDealershipToolRail() : config.toolRail,
    copy: {
      welcomeMessage: config.copy?.welcomeMessage ?? (detailPage && page.subjectLabel
        ? `I can answer questions about this ${page.subjectLabel} using current indexed dealership evidence and live facts.`
        : 'I can search and compare current inventory using indexed dealership evidence and live facts.'),
      placeholder: config.copy?.placeholder ?? (detailPage ? 'Ask about this vehicle...' : 'Ask about a vehicle, feature, budget or comparison...'),
      emptyMessage: config.copy?.emptyMessage ?? (detailPage
        ? 'Attach this page for its visible details, or ask a grounded question about this vehicle.'
        : 'Ask about current vehicles, compare options, or start an available dealership request.'),
      companionModeLabel: config.copy?.companionModeLabel ?? 'Vehicle assistant',
      starterSuggestions: config.copy?.starterSuggestions ?? defaultSuggestions,
    },
    contextLabel: detailPage && page.subjectLabel ? page.subjectLabel : page.contextLabel,
    currentPage: true,
    launcher: false,
    rendererContext: { comparisonEnabled: capabilities.comparison, testDriveEnabled: capabilities.testDrive, callbackEnabled: capabilities.callback },
    rendererIds: [
      'loomai.vehicle-inventory.v1', 'loomai.vehicle-detail.v1',
      ...(capabilities.comparison ? ['loomai.vehicle-comparison.v1'] : []),
      ...(capabilities.testDrive || capabilities.callback ? ['loomai.dealership-request-receipt.v1'] : []),
    ],
  }
}
