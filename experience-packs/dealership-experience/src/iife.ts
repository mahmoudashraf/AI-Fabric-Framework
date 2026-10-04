export * from './index'

import { autoMountFromCurrentScript } from './index'

void autoMountFromCurrentScript().catch((error) => {
  window.dispatchEvent(new CustomEvent('loomai:dealership-experience-error', {
    detail: {
      message: error instanceof Error ? error.message : 'The dealership experience could not start.',
    },
  }))
})
