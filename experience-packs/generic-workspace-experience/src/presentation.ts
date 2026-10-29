import type { GenericWorkspaceConfig } from './types'

/** Pure presentation shared by live mounting and the isolated Console preview. */
export function resolveGenericWorkspacePresentation(config: Partial<GenericWorkspaceConfig>) {
  return {
    identityKey: 'app',
    limitations: [] as string[],
    assistantLabel: config.app?.assistantLabel ?? 'Your assistant',
    theme: {
      primaryColor: config.theme?.primaryColor ?? '#155eef',
      logoUrl: config.theme?.logoUrl,
      borderRadius: config.theme?.borderRadius ?? '0.5rem',
      fontFamily: config.theme?.fontFamily ?? 'Inter, system-ui, sans-serif',
      darkMode: config.theme?.darkMode ?? false,
    },
    copy: {
      welcomeMessage: config.copy?.welcomeMessage ?? 'How can I help using the approved knowledge and services available to this workspace?',
      starterSuggestions: config.copy?.starterSuggestions ?? [
        'What can you help me with?', 'Search the available knowledge', 'Show the sources used in your answer',
      ],
      companionModeLabel: config.copy?.companionModeLabel ?? 'AI workspace',
      placeholder: config.copy?.placeholder ?? 'Ask a question...',
      emptyMessage: config.copy?.emptyMessage ?? 'Ask about approved knowledge or an available service.',
    },
    toolGroups: config.toolGroups === undefined ? defaultToolGroups() : config.toolGroups,
    toolRail: config.toolRail === undefined ? { items: [], initiallyCollapsed: true } : config.toolRail,
    contextLabel: config.page?.contextLabel ?? 'Current page',
    currentPage: config.capabilities?.currentPage !== false,
    launcher: true,
    rendererContext: {},
    rendererIds: [] as string[],
  }
}

function defaultToolGroups() {
  return {
    initialScope: 'default',
    default: {
      label: 'Explore', icon: 'search',
      tools: [
        { label: 'Search knowledge', query: 'Search the approved knowledge for information relevant to my question.', position: 'search', mode: 'executor', icon: 'search' },
        { label: 'Available help', query: 'What approved information and actions are available in this workspace?', position: 'search', mode: 'executor', icon: 'sparkles' },
      ],
    },
    contextual: {
      label: 'Current context', icon: 'documents', availableWithoutAttachments: false,
      tools: [
        { label: 'Summarize context', query: 'Summarize the attached context and identify the important points.', position: 'search', mode: 'executor', icon: 'documents' },
        { label: 'Explain details', query: 'Explain the attached context clearly and cite the relevant evidence.', position: 'search', mode: 'executor', icon: 'details' },
      ],
    },
  }
}
