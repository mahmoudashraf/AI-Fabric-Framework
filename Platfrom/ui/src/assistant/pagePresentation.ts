import type { AssistantEditorContext, AssistantScope, AssistantSection, AssistantTurn, ReusableSource } from './types'

type Page = { title: string; command: string; welcome: string }
export const assistantPages: Record<AssistantSection, Page> = {
  ACTIONS: { title: 'Actions', command: 'Prepare actions', welcome: 'Share API documentation to prepare actions, parameters and endpoint routing. Add a hint to focus on part of the API.' },
  CONNECTIONS: { title: 'Connections', command: 'Prepare connections', welcome: 'Share connection documentation to prepare endpoints and existing secure binding references. Keep credentials in the secure connection form.' },
  KNOWLEDGE: { title: 'Knowledge', command: 'Prepare knowledge', welcome: 'Share source documentation to prepare this Brain\'s knowledge settings. Discovery, indexing and approval stay separate.' },
  DOCUMENTS: { title: 'Documents', command: 'Prepare document sources', welcome: 'Share document-source requirements to prepare source settings. Imported references do not become indexed knowledge.' },
  LIVE_DATA: { title: 'Live data', command: 'Prepare live data', welcome: 'Share API or source documentation to prepare live-data settings and mappings. No external API is called.' },
  STORAGE: { title: 'Storage', command: 'Prepare storage', welcome: 'Share storage requirements to prepare supported document-storage settings. Uploads and provisioning stay separate.' },
  SEARCH: { title: 'Search settings', command: 'Prepare search settings', welcome: 'Prepare embedding, vector-service and search settings for this Brain. Existing connections and reindexing stay separate.' },
  BEHAVIOUR: { title: 'AI responses', command: 'Prepare responses', welcome: 'Share tone or response examples to prepare supported behaviour profiles for this Brain.' },
  INSTRUCTIONS: { title: 'Instructions', command: 'Prepare instructions', welcome: 'Share response guidelines to prepare this Brain\'s instructions. Add an optional focus hint.' },
  MODELS: { title: 'AI models', command: 'Prepare model settings', welcome: 'Prepare supported response-model settings using the current configuration and your optional requirements. Funding stays separate.' },
  HUMAN_SUPPORT: { title: 'Human support', command: 'Prepare handover', welcome: 'Prepare supported handover settings using this Brain\'s installed capabilities. Agent assignments stay separate.' },
  ACTION_REVIEWS: { title: 'Action reviews', command: 'Explain action reviews', welcome: 'Get guidance on this Brain\'s action-review setup. Review decisions and live configuration are not changed here.' },
  SPECIALISTS: { title: 'Specialists', command: 'Prepare specialists', welcome: 'Prepare supported specialist settings using installed workers and their declared contracts. No new worker code is created.' },
  KIT: { title: 'Kit setup', command: 'Explain Kit setup', welcome: 'Get the next setup steps for this Kit and its Brain. Open the linked resource editors when you are ready to make changes.' },
  DATA_PLUGIN: { title: 'Data plugin', command: 'Prepare Data plugin', welcome: 'Share source documentation or examples to prepare this private plugin\'s mappings and schema. Publishing and installation stay separate.' },
  ENGAGE: { title: 'Engage appearance', command: 'Prepare appearance', welcome: 'Share brand or workspace requirements to prepare supported app settings. Active apps remain preview-only until explicitly edited.' },
}

export function editorContext(scope: AssistantScope): AssistantEditorContext {
  return scope.editorContext ?? { section: scope.task }
}

export function assistantPage(scope: AssistantScope): Page {
  return assistantPages[editorContext(scope).section]
}

export function userTurnText(turn: AssistantTurn, scope: AssistantScope): string {
  if (turn.command !== 'PREPARE') return turn.query
  const page = assistantPages[turn.editorContext?.section ?? scope.task]
  return page.command + (turn.hint ? `\n${turn.hint}` : '')
}

export function sameResourceSources(sources: ReusableSource[], scope: AssistantScope): ReusableSource[] {
  return sources.filter(source => source.resourceKind === scope.resourceKind && source.resourceId === scope.resourceId)
}
