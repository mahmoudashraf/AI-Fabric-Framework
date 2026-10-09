export * from './index'

import {
  attachVehicle,
  destroy,
  mountInstallation,
  registerExperiencePack,
  sendMessage,
} from './index'

window.LoomAIDealershipExperience = {
  mountInstallation,
  attachVehicle,
  sendMessage,
  destroy,
}

try {
  registerExperiencePack()
} catch (error) {
  window.dispatchEvent(new CustomEvent('loomai:workspace-error', {
    detail: {
      code: 'EXPERIENCE_PACK_REGISTRATION_FAILED',
      message: error instanceof Error ? error.message : 'The dealership experience pack could not register.',
    },
  }))
}
