import type { AIWorkspaceBrowserApi, AIWorkspacePackMountContext, DealershipExperienceController, DealershipVehicleContext, MaxModeBrowserApi } from './types'

declare global {
  interface Window {
    MaxMode?: MaxModeBrowserApi
    LoomAIWorkspace?: AIWorkspaceBrowserApi
    LoomAIDealershipExperience?: {
      mountInstallation(context: AIWorkspacePackMountContext): Promise<DealershipExperienceController>
      attachVehicle(vehicle: DealershipVehicleContext): boolean
      sendMessage(message: string, requestContext?: Record<string, unknown>): boolean
      destroy(): void
    }
  }
}
