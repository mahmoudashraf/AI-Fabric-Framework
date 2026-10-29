import type { GenericWorkspaceMountContext } from './types'

declare global {
  interface Window {
    LoomAIWorkspace?: {
      registerExperiencePack(registration: {
        code: string
        version: string
        mount(context: GenericWorkspaceMountContext): Promise<unknown> | unknown
      }): void
    }
  }
}
