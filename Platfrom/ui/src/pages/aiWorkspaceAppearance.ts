import { resolveWorkspacePackPresentation } from '../components/customer/buildWorkspacePreviewConfiguration'
import { resolveWorkspaceStylePreset, type WorkspaceStylePresetId } from '../../../../max-mode-widget/src/stylePresets'

export type WorkspaceAppearance = {
  assistantLabel: string
  logoUrl: string
  primaryColor: string
  primaryColorSource?: 'configured' | 'preset' | 'default'
  currentPrimaryColor?: string
  darkMode: 'light' | 'dark' | 'auto'
  stylePreset: WorkspaceStylePresetId
  welcomeMessage: string
  placeholder: string
}

const object = (value: unknown): Record<string, unknown> =>
  value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
const text = (value: unknown) => typeof value === 'string' ? value : ''

export function readWorkspaceAppearance(configuration: Record<string, unknown>, packCode: string): WorkspaceAppearance {
  const presentation = resolveWorkspacePackPresentation(configuration, packCode)
  const identity = object(configuration[presentation?.identityKey ?? ''])
  const theme = object(configuration.theme)
  const style = resolveWorkspaceStylePreset(theme.stylePreset)
  const currentPresentation = resolveWorkspacePackPresentation({ ...configuration, theme: { ...theme, primaryColor: undefined, stylePreset: 'current' } }, packCode)
  const currentPrimaryColor = currentPresentation?.theme.primaryColor || '#155eef'
  const copy = object(configuration.copy)
  return {
    assistantLabel: text(identity.assistantLabel) || 'Your assistant',
    logoUrl: text(theme.logoUrl),
    primaryColor: text(theme.primaryColor) || (style.id === 'current' ? currentPrimaryColor : style.light.accent),
    primaryColorSource: text(theme.primaryColor) ? 'configured' : style.id === 'current' ? 'default' : 'preset',
    currentPrimaryColor,
    darkMode: theme.darkMode === 'auto' ? 'auto' : theme.darkMode === true ? 'dark' : 'light',
    stylePreset: style.id,
    welcomeMessage: text(copy.welcomeMessage),
    placeholder: text(copy.placeholder),
  }
}

export function selectWorkspaceAppearanceStyle(value: WorkspaceAppearance, next: WorkspaceStylePresetId): WorkspaceAppearance {
  const style = resolveWorkspaceStylePreset(next)
  if (value.primaryColorSource === 'configured' || value.primaryColorSource === undefined) return { ...value, stylePreset: style.id }
  return { ...value, stylePreset: style.id, primaryColorSource: style.id === 'current' ? 'default' : 'preset',
    primaryColor: style.id === 'current' ? value.currentPrimaryColor || '#155eef' : style.light.accent }
}

export function workspaceAppearanceError(value: WorkspaceAppearance): string | null {
  if (!value.assistantLabel.trim() || value.assistantLabel.trim().length > 128) return 'Assistant name must contain 1 to 128 characters.'
  if (!/^#[0-9a-f]{6}$/i.test(value.primaryColor)) return 'Choose a six-digit hex brand colour.'
  if (!['light', 'dark', 'auto'].includes(value.darkMode)) return 'Choose a supported appearance.'
  if (value.welcomeMessage.length > 1000 || value.placeholder.length > 1000) return 'Welcome text and placeholder must be at most 1,000 characters.'
  if (value.logoUrl.trim()) {
    try {
      const raw = value.logoUrl.trim()
      if (!/^https:\/\//i.test(raw) || /[\u0000-\u0020\\\u007f]/.test(raw)) throw new Error()
      const url = new URL(raw)
      if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || url.hash || url.href.length > 2048) throw new Error()
    } catch { return 'Logo must use an HTTPS image URL without embedded credentials or a fragment.' }
  }
  return null
}

/** Only presentation fields change; tools, business capabilities, identity and routing stay untouched. */
export function applyWorkspaceAppearance(
  configuration: Record<string, unknown>, packCode: string, value: WorkspaceAppearance,
): Record<string, unknown> {
  const error = workspaceAppearanceError(value)
  if (error) throw new Error(error)
  const identityKey = resolveWorkspacePackPresentation(configuration, packCode)?.identityKey
  if (!identityKey) throw new Error('This experience pack does not expose a branding editor.')
  const result = structuredClone(configuration)
  result[identityKey] = { ...object(result[identityKey]), assistantLabel: value.assistantLabel.trim() }
  const theme = { ...object(result.theme), primaryColor: value.primaryColor,
    darkMode: value.darkMode === 'auto' ? 'auto' : value.darkMode === 'dark' }
  if (value.primaryColorSource === 'default' || value.primaryColorSource === 'preset') delete (theme as Record<string, unknown>).primaryColor
  const stylePreset = resolveWorkspaceStylePreset(value.stylePreset).id
  if (stylePreset === 'current') delete (theme as Record<string, unknown>).stylePreset
  else {
    Object.assign(theme, { stylePreset })
    // Choosing a style replaces inherited legacy defaults, not genuinely custom branding.
    if (resolveWorkspaceStylePreset(object(configuration.theme).stylePreset).id === 'current') {
      const inherited = theme as Record<string, unknown>
      if (inherited.fontFamily === 'Inter, system-ui, sans-serif') delete inherited.fontFamily
      if (inherited.borderRadius === '0.5rem') delete inherited.borderRadius
    }
  }
  if (value.logoUrl.trim()) Object.assign(theme, { logoUrl: value.logoUrl.trim() })
  else delete (theme as Record<string, unknown>).logoUrl
  result.theme = theme
  const copy = { ...object(result.copy) }
  for (const key of ['welcomeMessage', 'placeholder'] as const) {
    if (value[key].trim()) copy[key] = value[key].trim()
    else delete copy[key]
  }
  if (Object.keys(copy).length) result.copy = copy
  else delete result.copy
  return result
}

export function workspacePreviewTools(configuration: Record<string, unknown>): string[] {
  const groups = object(configuration.toolGroups)
  return ['default', 'contextual'].flatMap(scope => {
    const tools = object(groups[scope]).tools
    return Array.isArray(tools) ? tools.map(tool => text(object(tool).label)).filter(Boolean) : []
  })
}
