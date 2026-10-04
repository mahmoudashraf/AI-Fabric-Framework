import type {
  DealershipCapabilities,
  DealershipPageKind,
  DealershipTool,
  DealershipToolGroups,
} from './types'

export interface DealershipToolGroupOptions {
  pageKind: DealershipPageKind
  subjectLabel?: string
  capabilities?: DealershipCapabilities
}

export function createDealershipToolGroups(
  options: DealershipToolGroupOptions,
): DealershipToolGroups {
  const capabilities = {
    comparison: options.capabilities?.comparison !== false,
    testDrive: options.capabilities?.testDrive === true,
    callback: options.capabilities?.callback === true,
  }
  const subjectLabel = cleanText(options.subjectLabel)
  const contextReference = subjectLabel || 'the vehicle in my current context'
  const contextualTools: DealershipTool[] = [
    tool('Live details', `Load the authoritative current details for ${contextReference}.`, 'details'),
    tool('Everyday use', `Is ${contextReference} suitable for everyday driving? Use current dealership facts.`, 'shield'),
    tool('Trade-offs', `Explain the important trade-offs for ${contextReference}.`, 'compare'),
    tool('Location', `Which showroom currently holds ${contextReference}? Use current dealership facts.`, 'location'),
  ]
  if (capabilities.testDrive) {
    contextualTools.push(tool(
      'Test drive',
      `Help me request a test drive for ${contextReference}. Ask only for required details before confirmation.`,
      'calendar',
    ))
  }
  if (capabilities.callback) {
    contextualTools.push(tool(
      'Callback',
      `Help me request a dealership callback about ${contextReference}. Ask for contact details and consent before confirmation.`,
      'phone',
    ))
  }

  const browseTools: DealershipTool[] = [
    tool('Search stock', 'Show me the current dealership inventory and help me narrow it down.', 'search'),
    tool('Electric cars', 'Show me electric cars in current stock.', 'sparkles'),
    tool('Family options', 'Which current vehicles are practical for a family? Use current dealership evidence.', 'shield'),
  ]
  if (capabilities.comparison) {
    browseTools.push(tool(
      'Compare cars',
      'Help me choose two current vehicles and compare their dealership facts.',
      'compare',
    ))
  }

  const detailPage = options.pageKind === 'vehicle-detail'
  return {
    initialScope: detailPage ? 'contextual' : 'default',
    default: {
      label: 'Browse stock',
      icon: 'search',
      tools: browseTools,
    },
    contextual: {
      label: 'This vehicle',
      icon: 'details',
      contextLabel: subjectLabel || undefined,
      availableWithoutAttachments: detailPage,
      tools: contextualTools,
    },
  }
}

function tool(
  label: string,
  query: string,
  icon: DealershipTool['icon'],
): DealershipTool {
  return { label, query, position: 'search', mode: 'executor', icon }
}

function cleanText(value: string | undefined) {
  return typeof value === 'string' ? value.trim() : ''
}
