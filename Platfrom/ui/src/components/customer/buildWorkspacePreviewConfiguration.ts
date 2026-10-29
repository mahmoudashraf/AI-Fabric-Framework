import { resolveGenericWorkspacePresentation } from '../../../../../experience-packs/generic-workspace-experience/src/presentation'
import { resolveDealershipPresentation } from '../../../../../experience-packs/dealership-experience/src/presentation'
import { previewObject, sanitizeWorkspacePreview, type PreviewCapabilities, type PreviewInteractionPolicy } from '../../../../../max-mode-widget/src/preview/configuration'
import type { WorkspaceAppearance } from '../../pages/aiWorkspaceAppearance'

export type { PreviewCapabilities, PreviewInteractionPolicy }

const resolvers = { 'generic-workspace': resolveGenericWorkspacePresentation, dealership: resolveDealershipPresentation }
export function resolveWorkspacePackPresentation(configuration: Record<string, unknown>, packCode: string) {
  return Object.prototype.hasOwnProperty.call(resolvers, packCode) ? resolvers[packCode as keyof typeof resolvers](configuration) : undefined
}

/** Never send raw pack/runtime/auth configuration to the preview frame. */
export function buildWorkspacePreviewConfiguration({ configuration, packCode, interactionPolicy, capabilities, appearance }: {
  configuration: Record<string, unknown>; packCode: string; interactionPolicy: PreviewInteractionPolicy;
  capabilities: PreviewCapabilities; appearance?: WorkspaceAppearance;
}) {
  const copy = { ...previewObject(configuration.copy) }
  if (appearance) {
    for (const key of ['welcomeMessage', 'placeholder'] as const) {
      if (appearance[key].trim()) copy[key] = appearance[key].trim()
      else if (copy[key] !== '') delete copy[key]
    }
  }
  const resolved = resolveWorkspacePackPresentation({ ...configuration, copy }, packCode)
  const limitations: string[] = [...(resolved?.limitations ?? [])]
  const handover = previewObject(capabilities?.handover)
  const runtimeKnown = typeof handover.runtimeConfigured === 'boolean'
  const explicitHandover = previewObject(configuration.capabilities).handover === true
  const safeCapabilities = { ...capabilities, handover: { ...handover,
    enabled: handover.runtimeConfigured === false ? false : runtimeKnown ? handover.enabled : handover.enabled && explicitHandover } }
  if (runtimeKnown && previewObject(configuration.capabilities).handover === false && handover.enabled) {
    limitations.push('Human support follows the assigned runtime contract. The pack handover flag does not disable that runtime capability.')
  }
  if (!runtimeKnown) limitations.push('Runtime handover availability is unknown. Any handover sample represents explicit configuration only, not a connected support service.')
  if (!resolved) limitations.push('This experience pack has no bundled preview resolver or fixtures.')
  if (Array.isArray(configuration.toolGroups)) limitations.push('Tool groups must use the supported default/contextual object contract.')
  const host = previewObject(configuration.host)
  if (configuration.actionPresentation || host.actionPresentation || configuration.extensions) limitations.push('Custom presentation extensions require a trusted bundled renderer and preview fixture.')
  if (Object.keys(previewObject(configuration.presentation)).length) limitations.push('Custom result mappings, navigation and image overrides are not executed. Samples use bundled renderer fixtures.')
  const rawRail = previewObject(resolved?.toolRail)
  if (Array.isArray(rawRail.items) && rawRail.items.length > 6) limitations.push('The live mobile rail supports at most six items; additional rail items are not rendered.')
  const railItems = Array.isArray(rawRail.items) ? rawRail.items.slice(0, 6) : []
  if (railItems.some(item => !['open-tools', 'open-documents', 'prompt'].includes(previewObject(item).action))) limitations.push('An unsupported rail action has no preview implementation.')
  const rawGroups = previewObject(resolved?.toolGroups)
  const projectGroup = (value: unknown) => {
    const group = previewObject(value)
    return { ...group, tools: Array.isArray(group.tools) ? group.tools.filter(tool => typeof tool?.query === 'string' && tool.query.trim()) : [] }
  }
  const theme = { ...resolved?.theme }
  if (appearance) Object.assign(theme, { primaryColor: appearance.primaryColor, logoUrl: appearance.logoUrl,
    darkMode: appearance.darkMode === 'auto' ? 'auto' : appearance.darkMode === 'dark' })
  return sanitizeWorkspacePreview({
    packCode, assistantLabel: appearance?.assistantLabel || resolved?.assistantLabel || 'Your assistant', theme,
    copy: resolved?.copy, toolGroups: { initialScope: rawGroups.initialScope, default: projectGroup(rawGroups.default), contextual: projectGroup(rawGroups.contextual) },
    toolRail: { ...rawRail, items: railItems.filter(item => item.action !== 'prompt' || (typeof item.query === 'string' && item.query.trim())) },
    currentPage: { ...previewObject(configuration.page), enabled: resolved?.currentPage === true, contextLabel: resolved?.contextLabel },
    launcher: resolved?.launcher, rendererIds: resolved?.rendererIds ?? [], rendererContext: resolved?.rendererContext, interactionPolicy, capabilities: safeCapabilities, limitations,
  })
}

