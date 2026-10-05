import type { DealershipToolRailConfig } from './types'

export function createDealershipToolRail(): DealershipToolRailConfig {
  return {
    items: [
      {
        id: 'browse-stock',
        label: 'Stock',
        icon: 'search',
        tone: 'primary',
        action: 'open-tools',
        scope: 'default',
      },
      {
        id: 'current-vehicle',
        label: 'Vehicle',
        icon: 'details',
        tone: 'teal',
        action: 'open-tools',
        scope: 'contextual',
      },
      {
        id: 'sources',
        label: 'Sources',
        icon: 'documents',
        tone: 'violet',
        action: 'open-documents',
      },
    ],
  }
}
