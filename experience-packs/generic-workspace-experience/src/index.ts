import type { GenericWorkspaceConfig, GenericWorkspaceController, GenericWorkspaceMountContext } from './types'
import { resolveGenericWorkspacePresentation } from './presentation'
import './browser-globals'

export * from './types'
export { resolveGenericWorkspacePresentation } from './presentation'

export const code = 'generic-workspace'
export const version = '1.0.0'

let activeController: GenericWorkspaceController | undefined

export async function mountInstallation(context: GenericWorkspaceMountContext): Promise<GenericWorkspaceController> {
  const config = validateConfig(context.configuration)
  activeController?.destroy()
  const standalone = context.deliveryMode === 'standalone'
  const page = config.page || {}
  const presentation = resolveGenericWorkspacePresentation(config)
  context.maxMode.init({
    ...context.widgetConfig,
    features: {
      ...(context.widgetConfig.features || {}),
      cart: false,
      debug: false,
      conversations: context.widgetConfig.features?.conversations !== false,
      quickActions: true,
    },
    theme: presentation.theme,
    launcher: !standalone,
    beforeNewConversation: context.refreshAssignment,
    host: {
      ...(context.widgetConfig.host || {}),
      assistantLabel: presentation.assistantLabel,
      welcomeMessage: presentation.copy.welcomeMessage,
      starterSuggestions: presentation.copy.starterSuggestions,
      toolGroups: presentation.toolGroups,
      toolRail: presentation.toolRail,
      requestContext: {
        ...(config.requestContext || {}),
        workspaceInstallationId: context.installationId,
        assignmentRevision: context.assignmentRevision,
      },
      defaultConversationMode: 'executor',
      effectiveConversationMode: 'executor',
      allowedConversationModes: ['executor'],
      showUtilityPanel: false,
      companionDock: !standalone,
      showCloseButton: !standalone,
      currentPageAttachment: !presentation.currentPage ? undefined : {
        enabled: true,
        maxChars: page.maxChars || 1800,
        maxPages: page.maxPages || 3,
        maxTotalChars: page.maxTotalChars || 10000,
        rootSelector: page.rootSelector || 'main',
        invalidateOnNavigation: false,
      },
      companionContextLabel: presentation.contextLabel,
      companionModeLabel: presentation.copy.companionModeLabel,
      companionPlaceholder: presentation.copy.placeholder,
      companionEmptyMessage: presentation.copy.emptyMessage,
    },
  })
  if (standalone) context.maxMode.open()
  const controller: GenericWorkspaceController = {
    installationId: context.installationId,
    destroy() {
      context.maxMode.destroy()
      if (activeController === controller) activeController = undefined
    },
  }
  activeController = controller
  return controller
}

export function registerExperiencePack() {
  if (!window.LoomAIWorkspace) throw new Error('The Platform AI Workspace installer is required.')
  window.LoomAIWorkspace.registerExperiencePack({ code, version, mount: mountInstallation })
}

function validateConfig(value: Record<string, unknown>): GenericWorkspaceConfig {
  const config = value as unknown as GenericWorkspaceConfig
  if (!config?.app || typeof config.app.assistantLabel !== 'string' || !config.app.assistantLabel.trim()) {
    throw new Error('Generic AI Workspace configuration requires app.assistantLabel.')
  }
  return config
}
