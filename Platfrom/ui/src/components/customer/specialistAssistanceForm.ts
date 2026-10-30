import type {
  DeploymentDraftResponse, DeploymentMarketplaceInstallSummary, MarketplacePluginVersionSummary,
} from '../../api/platformApi'

export type SpecialistArea = {
  id: string; name: string; purpose: string; instructions: string; vectorSpaces: string[]; readActions: string[]
}
export type SpecialistResolution = {
  id: string; name: string; objective: string; areaIds: string[]; allowParallel: boolean
}
export type SpecialistAssistance = {
  enabled: boolean; areas: SpecialistArea[]; resolutions: SpecialistResolution[]
  limits: { maxDecisions: number; maxWorkerInvocations: number; maxParallelWorkers: number; deadlineSeconds: number }
}
export type SpecialistChoice = { id: string; label: string; plugin: string }
export type SpecialistAuthority = { sources: SpecialistChoice[]; readTools: SpecialistChoice[] }

export function assistanceRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
const text = (value: unknown) => typeof value === 'string' ? value : ''
function strings(value: unknown): string[] {
  if (!Array.isArray(value) || value.some(item => typeof item !== 'string')) throw new Error('The saved specialist selection is not an array of identifiers.')
  return [...value] as string[]
}

export function emptySpecialistAssistance(): SpecialistAssistance {
  return { enabled: false, areas: [], resolutions: [],
    limits: { maxDecisions: 4, maxWorkerInvocations: 2, maxParallelWorkers: 2, deadlineSeconds: 75 } }
}

export function readSpecialistAssistance(behavior: unknown): SpecialistAssistance | null {
  if (!assistanceRecord(behavior)) throw new Error('The saved behavior configuration is unavailable.')
  const saved = behavior.specialistAssistance
  if (saved == null) return null
  if (!assistanceRecord(saved) || typeof saved.enabled !== 'boolean') throw new Error('The saved specialist assistance configuration is invalid.')
  const config = saved.enabled ? saved : { ...emptySpecialistAssistance(), ...saved }
  if (!assistanceRecord(config) || typeof config.enabled !== 'boolean'
    || !Array.isArray(config.areas) || !Array.isArray(config.resolutions) || !assistanceRecord(config.limits)) {
    throw new Error('The saved specialist assistance configuration cannot be edited with this form.')
  }
  const areas = config.areas.map(value => {
    if (!assistanceRecord(value)) throw new Error('The saved support area is invalid.')
    return { id: text(value.id), name: text(value.name), purpose: text(value.purpose), instructions: text(value.instructions),
      vectorSpaces: strings(value.vectorSpaces), readActions: strings(value.readActions) }
  })
  const resolutions = config.resolutions.map(value => {
    if (!assistanceRecord(value) || typeof value.allowParallel !== 'boolean') throw new Error('The saved resolution plan is invalid.')
    return { id: text(value.id), name: text(value.name), objective: text(value.objective),
      areaIds: strings(value.areaIds), allowParallel: value.allowParallel }
  })
  const limits = config.limits
  if (['maxDecisions', 'maxWorkerInvocations', 'maxParallelWorkers', 'deadlineSeconds'].some(key => typeof limits[key] !== 'number')) {
    throw new Error('The saved execution limits are invalid.')
  }
  // compiled/contentHash belong to immutable publication, never to the editable form.
  return { enabled: config.enabled, areas, resolutions, limits: {
    maxDecisions: limits.maxDecisions as number, maxWorkerInvocations: limits.maxWorkerInvocations as number,
    maxParallelWorkers: limits.maxParallelWorkers as number, deadlineSeconds: limits.deadlineSeconds as number,
  } }
}

function belongsTo(node: Record<string, unknown>, install: DeploymentMarketplaceInstallSummary): boolean {
  return node.marketplaceManaged === true && node.marketplaceInstallId === install.id
    && node.marketplacePluginId === install.pluginId && node.marketplacePluginVersion === install.pluginVersion
}

/** Mirror the server's installed-resource catalogue for choices; publication remains authoritative. */
export function specialistAuthority(deploymentId: string, draft: DeploymentDraftResponse,
  installs: DeploymentMarketplaceInstallSummary[], versions: MarketplacePluginVersionSummary[]): SpecialistAuthority {
  if (draft.deploymentId !== deploymentId || installs.some(install => install.deploymentId !== deploymentId)) {
    throw new Error('The installed capability catalogue belongs to another deployment.')
  }
  const sources = new Map<string, SpecialistChoice>(), readTools = new Map<string, SpecialistChoice>()
  const actions = assistanceRecord(draft.actionsConfig) && Array.isArray(draft.actionsConfig.actions) ? draft.actionsConfig.actions : []
  const knowledge = assistanceRecord(draft.knowledgeSourceConfig) && Array.isArray(draft.knowledgeSourceConfig.sources) ? draft.knowledgeSourceConfig.sources : []
  for (const install of installs) {
    if (!['ENABLED', 'BOOTSTRAPPED'].includes(install.status.toUpperCase()) || !install.entitlement?.entitledForCompilation) continue
    const version = versions.find(item => item.id === install.pluginVersionId)
    if (!version || version.pluginId !== install.pluginId || version.version !== install.pluginVersion || version.status !== 'PUBLISHED') {
      throw new Error(`The approved manifest for ${install.pluginDisplayName} is unavailable.`)
    }
    const manifest = assistanceRecord(version.manifest) ? version.manifest : {}
    const contributions = assistanceRecord(manifest.contributions) ? manifest.contributions : {}
    const declaredActions = Array.isArray(contributions.actions) ? contributions.actions.filter(assistanceRecord) : []
    const declaredSources = Array.isArray(contributions.knowledgeSources) ? contributions.knowledgeSources.filter(assistanceRecord) : []
    const datasets = Array.isArray(contributions.datasets) ? contributions.datasets.filter(assistanceRecord) : []
    for (const action of actions.filter(assistanceRecord)) {
      const id = text(action.name)
      if (!id || id === 'consult_specialist' || action.accessMode !== 'READ' || !belongsTo(action, install)) continue
      if (!declaredActions.some(item => text(item.actionId ?? item.id) === id && (item.readOnly === true || item.accessMode === 'READ'))) continue
      readTools.set(id, { id, label: text(action.displayName) || text(action.label) || id, plugin: install.pluginDisplayName })
    }
    for (const source of knowledge.filter(assistanceRecord)) {
      const id = text(source.id), space = text(source.entityType)
      if (!id || !space || source.enabled === false || !belongsTo(source, install)) continue
      const declared = declaredSources.find(item => text(item.id ?? item.sourceKey) === id)
      if (!declared) continue
      const dataset = datasets.find(item => text(item.datasetId ?? item.id) === declared.datasetRef)
      if ((text(declared.entityType) || text(dataset?.entityType)) !== space) continue
      sources.set(space, { id: space, label: text(source.displayName) || text(source.name) || id, plugin: install.pluginDisplayName })
    }
  }
  return { sources: [...sources.values()], readTools: [...readTools.values()] }
}

export function specialistAssistanceError(value: SpecialistAssistance, authority: SpecialistAuthority): string | null {
  if (!value.enabled) return null
  if (!value.areas.length || value.areas.length > 8) return 'Configure between 1 and 8 support areas.'
  if (value.resolutions.length > 8) return 'Configure at most 8 resolution plans.'
  const allIds = [...value.areas, ...value.resolutions].map(item => item.id)
  if (allIds.some(id => !/^[a-z][a-z0-9-]{0,39}$/.test(id)) || new Set(allIds).size !== allIds.length) return 'Area and plan IDs must be unique resource identifiers.'
  const sources = new Set(authority.sources.map(item => item.id)), tools = new Set(authority.readTools.map(item => item.id))
  for (const area of value.areas) {
    if (!area.name.trim() || area.name.length > 120 || !area.purpose.trim() || area.purpose.length > 500
      || !area.instructions.trim() || area.instructions.length > 4000) return 'Each area needs a name, purpose and instructions within the displayed limits.'
    if (area.vectorSpaces.length > 16 || area.readActions.length > 16
      || new Set(area.vectorSpaces).size !== area.vectorSpaces.length || new Set(area.readActions).size !== area.readActions.length
      || area.vectorSpaces.some(id => !sources.has(id)) || area.readActions.some(id => !tools.has(id))) {
      return `Review the installed knowledge and READ tools selected for ${area.name}. Some selections are no longer available.`
    }
  }
  const areas = new Set(value.areas.map(item => item.id))
  for (const plan of value.resolutions) {
    if (!plan.name.trim() || plan.name.length > 120 || !plan.objective.trim() || plan.objective.length > 1000) return 'Each resolution plan needs a name and objective.'
    if (!plan.areaIds.length || plan.areaIds.length > 8 || new Set(plan.areaIds).size !== plan.areaIds.length
      || plan.areaIds.some(id => !areas.has(id))) return 'Select configured support areas for each resolution plan.'
    if (plan.allowParallel && plan.areaIds.length < 2) return 'Parallel checks require at least two independent areas.'
  }
  const limits = value.limits
  for (const [key, min, max] of [['maxDecisions', 1, 8], ['maxWorkerInvocations', 1, 8], ['maxParallelWorkers', 1, 4], ['deadlineSeconds', 5, 120]] as const) {
    if (!Number.isInteger(limits[key]) || limits[key] < min || limits[key] > max) return `${key} must be between ${min} and ${max}.`
  }
  return limits.maxParallelWorkers > limits.maxWorkerInvocations ? 'Parallel workers cannot exceed the worker invocation limit.' : null
}

export function specialistBehaviorUpdate(current: unknown, value: SpecialistAssistance): Record<string, unknown> {
  if (!assistanceRecord(current) || current.type !== 'CONVERSATIONAL' || !Array.isArray(current.executionExtensions)) {
    throw new Error('Specialist assistance requires the current Conversational behavior contract.')
  }
  const extensions = strings(current.executionExtensions).filter(item => item !== 'SPECIALIST_ASSISTANCE')
  if (value.enabled) extensions.push('SPECIALIST_ASSISTANCE')
  return { ...structuredClone(current), executionExtensions: extensions, specialistAssistance: structuredClone(value) }
}

export function compiledSpecialistAssistance(manifest: unknown, deploymentId: string, versionId: string) {
  if (!assistanceRecord(manifest) || manifest.deploymentId !== deploymentId || manifest.versionId !== versionId) {
    throw new Error('The published capability snapshot belongs to another deployment or version.')
  }
  const behavior = assistanceRecord(manifest.behaviorConfig) ? manifest.behaviorConfig : {}
  const value = assistanceRecord(behavior.specialistAssistance) ? behavior.specialistAssistance : {}
  if (value.enabled !== true) return null
  const compiled = value.compiled
  if (!assistanceRecord(compiled) || compiled.contractVersion !== 'LOOMAI_SPECIALIST_ASSISTANCE_V1'
    || !Array.isArray(compiled.resources) || !Array.isArray(compiled.invocations) || typeof value.contentHash !== 'string') {
    throw new Error('The published specialist resources are unavailable. No exact references were inferred.')
  }
  const invocations = compiled.invocations
  if (invocations.some(item => !assistanceRecord(item) || !text(item.id) || !text(item.name)
    || !['SPECIALIST', 'CHAIN'].includes(text(item.targetType)) || !text(item.targetRef))) {
    throw new Error('The published specialist references are incomplete. No references were inferred.')
  }
  return { contentHash: value.contentHash, resources: compiled.resources,
    invocations: invocations.filter(assistanceRecord).map(item => ({ id: text(item.id), name: text(item.name), targetType: text(item.targetType), targetRef: text(item.targetRef) })) }
}
