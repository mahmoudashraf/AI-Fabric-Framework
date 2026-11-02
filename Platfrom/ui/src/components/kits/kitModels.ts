import type { AIWorkspaceInstallationSummary, AIWorkspaceReadinessCheck, CustomerOfferSummary, DeploymentReleaseSummary } from '../../api/platformApi'
import type { ExperienceKitDeliveryMode, ExperienceKitSummary, UpdateExperienceKitRequest } from '../../api/experienceKitsApi'
import { readWorkspaceAppearance } from '../../pages/aiWorkspaceAppearance'
import { connectableRelease } from '../customer/customerEngageConnection'

export type KitExperienceForm = {
  name: string
  workspaceName: string
  brandColor: string
  standalone: boolean
  embedded: boolean
  appSlug: string
  websiteOrigin: string
  humanSupportEnabled: boolean
}

export const kitBrandColors = ['#28bfa6', '#409aff', '#9bafff', '#efb060'] as const

export function kitDeliveryMode(form: Pick<KitExperienceForm, 'standalone' | 'embedded'>): ExperienceKitDeliveryMode | null {
  return form.standalone && form.embedded ? 'both' : form.standalone ? 'standalone' : form.embedded ? 'embedded' : null
}

export function kitExperienceErrors(form: KitExperienceForm): string[] {
  const errors: string[] = []
  if (!form.name.trim()) errors.push('Enter a name for your kit.')
  if (!form.workspaceName.trim()) errors.push('Enter a workspace name.')
  if (!/^#[0-9a-f]{6}$/i.test(form.brandColor)) errors.push('Choose a brand color using the color picker.')
  if (!kitDeliveryMode(form)) errors.push('Choose your website, a standalone app, or both.')
  if (form.standalone && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(form.appSlug)) errors.push('Use lowercase letters, numbers and single hyphens for the app address.')
  if (form.embedded) {
    try {
      const value = form.websiteOrigin.trim()
      const url = new URL(value)
      if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash
        || (url.pathname !== '' && url.pathname !== '/') || url.origin !== value.replace(/\/$/, '')) throw new Error()
    } catch { errors.push('Enter your website address, for example https://www.example.com, without a page path.') }
  }
  return errors
}

export function kitFormFromSummary(kit: ExperienceKitSummary): KitExperienceForm {
  const workspace = kit.workspace
  const appearance = workspace ? readWorkspaceAppearance(workspace.configuration, workspace.experiencePackCode) : null
  return {
    name: kit.name,
    workspaceName: workspace?.displayName ?? '',
    brandColor: appearance?.primaryColor ?? '#28bfa6',
    standalone: kit.deliveryMode === 'standalone' || kit.deliveryMode === 'both',
    embedded: kit.deliveryMode === 'embedded' || kit.deliveryMode === 'both',
    appSlug: workspace?.appSlug ?? '',
    websiteOrigin: (kit.embeddedWorkspace ?? workspace)?.allowedOrigins[0] ?? '',
    humanSupportEnabled: kit.humanSupportEnabled,
  }
}

export function kitUpdateRequest(form: KitExperienceForm, expectedRevision: number, expectedWorkspaceRevision: number,
  expectedEmbeddedWorkspaceRevision: number | null = null): UpdateExperienceKitRequest {
  const errors = kitExperienceErrors(form)
  if (errors.length) throw new Error(errors[0])
  return { name: form.name.trim(), workspaceName: form.workspaceName.trim(), brandColor: form.brandColor,
    deliveryMode: kitDeliveryMode(form)!,
    ...(form.standalone ? { appSlug: form.appSlug } : {}),
    ...(form.embedded ? { websiteOrigin: new URL(form.websiteOrigin.trim()).origin } : {}),
    humanSupportEnabled: form.humanSupportEnabled, expectedRevision, expectedWorkspaceRevision, expectedEmbeddedWorkspaceRevision }
}

export function kitStagingTargets(offer?: CustomerOfferSummary) {
  return offer?.targets.filter(target => target.intent === 'CUSTOMER_STAGING') ?? []
}

/** An assigned release is authoritative. Never substitute a newer release for a missing assignment. */
export function kitRelease(kit: ExperienceKitSummary, releases: DeploymentReleaseSummary[]) {
  const owned = releases.filter(release => release.deploymentId === kit.deploymentId)
  if (kit.workspace?.releaseId) return owned.find(release => release.id === kit.workspace?.releaseId) ?? null
  return null
}

export function kitLatestAttempt(kit: ExperienceKitSummary, releases: DeploymentReleaseSummary[]) {
  return releases.filter(release => release.deploymentId === kit.deploymentId && release.targetProfileId === kit.targetProfileId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0] ?? null
}

export function kitAppPresentation(workspace: AIWorkspaceInstallationSummary | null | undefined, deploymentId: string,
  releases: DeploymentReleaseSummary[]) {
  if (!workspace) return { label: 'Setup needed', ready: false, active: false }
  if (workspace.status === 'DISABLED') return { label: 'Paused', ready: false, active: false }
  const assigned = workspace.releaseId && workspace.deploymentId === deploymentId
    ? releases.find(release => release.id === workspace.releaseId && release.deploymentId === deploymentId) : null
  if (!assigned || !connectableRelease(assigned)) return { label: 'Not live yet', ready: false, active: false }
  if (!workspace.ready) return { label: 'Setup needed', ready: false, active: false }
  const active = workspace.status === 'ACTIVE'
  return { label: active ? 'Live' : 'Ready to go live', ready: true, active }
}

// Presentation only: readiness codes/statuses and activation rules remain authoritative.
export function kitReadinessText(check: AIWorkspaceReadinessCheck): string {
  const messages: Record<string, [string, string]> = {
    ASSIGNMENT: ['Your AI service connection is verified.', 'Test and connect your AI assistant before this workspace goes live.'],
    ASSETS: ['Your workspace files are verified.', 'Your workspace files need attention. Contact LoomAI support.'],
    EXPERIENCE_PACK: ['Your chosen experience is available.', 'Your chosen experience needs attention before you can go live.'],
    CONNECTION_PROFILE: ['Your workspace connection settings are compatible.', 'Finish setting up how your workspace connects to your AI assistant.'],
    ORIGINS: ['Your website addresses are configured.', 'Add the website addresses where customers will use your workspace.'],
    AUTHENTICATION_PROFILE: ['Your customer sign-in settings are compatible.', 'Review customer sign-in settings before going live.'],
    APP_DOMAIN: ['Your workspace web address is verified.', 'Verify your workspace web address before going live.'],
    APP_DELIVERY: ['Your workspace website is reachable.', 'Your workspace web address is not ready yet.'],
  }
  const copy = Object.prototype.hasOwnProperty.call(messages, check.code) ? messages[check.code] : undefined
  return copy && (check.status === 'PASSED' || check.status === 'BLOCKED')
    ? copy[check.status === 'PASSED' ? 0 : 1] : check.message
}

export function kitEditorPath(kit: ExperienceKitSummary, preview = false, embedded = false) {
  const installationId = embedded ? kit.embeddedInstallationId : kit.installationId
  if (!installationId) throw new Error('The requested delivery channel is not available.')
  return `/ai-workspaces?${new URLSearchParams(preview ? { view: 'manage', previewInstallationId: installationId }
    : { resourceKind: 'app', resourceId: installationId, deploymentId: kit.deploymentId, editor: 'configuration' })}`
}

export function kitDeploymentPath(kit: ExperienceKitSummary) {
  return `/deployments?${new URLSearchParams({ view: 'setup', step: 'configure', deploymentId: kit.deploymentId })}`
}

export function safeKitExternalUrl(value: string | null | undefined) {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null
  } catch { return null }
}
