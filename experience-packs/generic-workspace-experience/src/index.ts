import type { GenericWorkspaceConfig, GenericWorkspaceController, GenericWorkspaceMountContext } from './types'

export * from './types'

export const code = 'generic-workspace'
export const version = '1.0.0'

let activeController: GenericWorkspaceController | undefined

export async function mountInstallation(context: GenericWorkspaceMountContext): Promise<GenericWorkspaceController> {
  const config = validateConfig(context.configuration)
  activeController?.destroy()
  const standalone = context.deliveryMode === 'standalone'
  const page = config.page || {}
  context.maxMode.init({
    ...context.widgetConfig,
    features: {
      ...(context.widgetConfig.features || {}),
      cart: false,
      debug: false,
      conversations: context.widgetConfig.features?.conversations !== false,
      quickActions: true,
    },
    theme: {
      primaryColor: config.theme?.primaryColor || '#155eef',
      logoUrl: config.theme?.logoUrl,
      borderRadius: config.theme?.borderRadius || '0.5rem',
      fontFamily: config.theme?.fontFamily || 'Inter, system-ui, sans-serif',
      darkMode: config.theme?.darkMode ?? false,
    },
    launcher: !standalone,
    beforeNewConversation: context.refreshAssignment,
    host: {
      ...(context.widgetConfig.host || {}),
      assistantLabel: config.app.assistantLabel,
      welcomeMessage: config.copy?.welcomeMessage || 'How can I help using the approved knowledge and services available to this workspace?',
      starterSuggestions: config.copy?.starterSuggestions || [
        'What can you help me with?',
        'Search the available knowledge',
        'Show the sources used in your answer',
      ],
      toolGroups: config.toolGroups || defaultToolGroups(),
      toolRail: config.toolRail || { items: [], initiallyCollapsed: true },
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
      currentPageAttachment: config.capabilities?.currentPage === false ? undefined : {
        enabled: true,
        maxChars: page.maxChars || 1800,
        maxPages: page.maxPages || 3,
        maxTotalChars: page.maxTotalChars || 10000,
        rootSelector: page.rootSelector || 'main',
        invalidateOnNavigation: false,
      },
      companionContextLabel: page.contextLabel || 'Current page',
      companionModeLabel: config.copy?.companionModeLabel || 'AI workspace',
      companionPlaceholder: config.copy?.placeholder || 'Ask a question...',
      companionEmptyMessage: config.copy?.emptyMessage || 'Ask about approved knowledge or an available service.',
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

function defaultToolGroups() {
  return {
    initialScope: 'default',
    default: {
      label: 'Explore',
      icon: 'search',
      tools: [
        { label: 'Search knowledge', query: 'Search the approved knowledge for information relevant to my question.', position: 'search', mode: 'executor', icon: 'search' },
        { label: 'Available help', query: 'What approved information and actions are available in this workspace?', position: 'search', mode: 'executor', icon: 'sparkles' },
      ],
    },
    contextual: {
      label: 'Current context',
      icon: 'documents',
      availableWithoutAttachments: false,
      tools: [
        { label: 'Summarize context', query: 'Summarize the attached context and identify the important points.', position: 'search', mode: 'executor', icon: 'documents' },
        { label: 'Explain details', query: 'Explain the attached context clearly and cite the relevant evidence.', position: 'search', mode: 'executor', icon: 'details' },
      ],
    },
  }
}
