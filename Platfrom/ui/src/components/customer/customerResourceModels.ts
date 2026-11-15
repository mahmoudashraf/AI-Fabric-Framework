import { deploymentTerm } from '../../deploymentTerminology'
import type { AIWorkspaceInstallationSummary, CustomerDeploymentSummary, CustomerOfferSummary, DeploymentReleaseSummary, DeploymentTargetProfileSummary } from '../../api/platformApi'

export type CustomerResourceIssue = { code: string; message: string; severity: 'error' | 'warning' | 'info' }
export type CustomerResource = {
  id: string
  kind: 'deployment' | 'app'
  name: string
  subtitle: string
  status: string
  severity: 'success' | 'warning' | 'error' | 'info' | 'default'
  issues: CustomerResourceIssue[]
  deploymentId: string | null
  releaseId: string | null
  facts: Array<[string, string]>
  configurationFacts: Array<[string, string]>
  advancedFacts: Array<[string, string]>
  updatedAt: string | null
}

export function deploymentResource(deployment: CustomerDeploymentSummary, offer?: CustomerOfferSummary, apps: AIWorkspaceInstallationSummary[] = []): CustomerResource {
  const connectedApps = apps.filter(app => app.deploymentId === deployment.deploymentId && app.status === 'ACTIVE' && app.releaseId)
  return {
    id: deployment.deploymentId, kind: 'deployment', name: deployment.name,
    subtitle: offer?.name ?? `${deploymentTerm.singular} configuration`,
    status: connectedApps.length ? 'App connected' : deployment.status === 'DRAFT' ? 'Draft configuration' : deployment.status,
    severity: connectedApps.length || deployment.status === 'DRAFT' ? 'info' : 'default', issues: [],
    deploymentId: deployment.deploymentId, releaseId: null, updatedAt: deployment.createdAt,
    facts: [['Configuration state', deployment.status], ['Active app assignments', String(connectedApps.length)], ['Initial environment', deployment.environmentName],
      ['Reviewed template', offer?.name ?? 'Not available']],
    configurationFacts: [['Template', offer?.name ?? 'Not available'], ['Reviewed targets', offer ? String(offer.targets.length) : 'Not available'],
      ['Connection slots', offer ? String(offer.bindingSlots.length) : 'Not available']],
    advancedFacts: [[`${deploymentTerm.singular} ID`, deployment.deploymentId], ['Published version', deployment.activeVersionId ?? 'Not published'],
      ['Offer ID', deployment.offerId], ['Bootstrap target', deployment.bootstrapTargetProfileId]],
  }
}

export function resourceReleaseSelection(resource: CustomerResource | undefined, releases: DeploymentReleaseSummary[],
  apps: AIWorkspaceInstallationSummary[], selectedReleaseId: string | null) {
  const relevant = resource ? releases.filter(release => release.deploymentId === resource.deploymentId) : []
  const assignedIds = new Set(apps.filter(app => app.deploymentId === resource?.deploymentId
    && (resource?.kind === 'app' ? app.installationId === resource.id : app.status === 'ACTIVE'))
    .flatMap(app => app.releaseId ? [app.releaseId] : []))
  const visible = selectedReleaseId ? relevant.filter(release => release.id === selectedReleaseId)
    : assignedIds.size ? relevant.filter(release => assignedIds.has(release.id)) : relevant
  const visibleIds = new Set(visible.map(release => release.id))
  return {
    visible,
    history: relevant.filter(release => !visibleIds.has(release.id)),
    missingAssignmentIds: [...assignedIds].filter(id => !relevant.some(release => release.id === id)),
    assigned: assignedIds.size > 0,
  }
}

export function appResource(app: AIWorkspaceInstallationSummary): CustomerResource {
  const issues: CustomerResourceIssue[] = app.readinessChecks.filter(check => check.status !== 'PASSED')
    .map(check => ({ code: check.code, message: check.message, severity: check.status === 'BLOCKED' ? 'error' : 'warning' }))
  for (const domain of app.domains) {
    if (domain.lastVerificationError) issues.push({ code: `domain:${domain.hostname}`, message: `${domain.hostname}: ${domain.lastVerificationError}`, severity: 'warning' })
  }
  return {
    id: app.installationId, kind: 'app', name: app.displayName,
    subtitle: `${app.experiencePackCode} / ${app.deliveryMode}`,
    status: app.status, severity: app.status === 'DISABLED' ? 'default' : issues.length ? 'warning' : app.ready ? 'success' : 'info',
    issues, deploymentId: app.deploymentId, releaseId: app.releaseId, updatedAt: app.updatedAt,
    facts: [['Installation state', app.status], ['Readiness', app.ready ? 'Passed recorded checks' : 'Not ready'],
      ...(app.deliveryMode === 'standalone' ? [['App address', app.appUrl ?? 'Not configured'] as [string, string]] : []),
      ['Allowed websites', app.allowedOrigins.join(', ') || 'Not configured'], ['Connection', app.connectionMode],
      ['Visitor access', app.interactionPolicy]],
    configurationFacts: [['Delivery', app.deliveryMode], ['Experience', app.experiencePackCode], ['Visitor access', app.interactionPolicy]],
    advancedFacts: [['Installation ID', app.installationId], ['Experience version', app.experiencePackVersion],
      ['Connection profile', app.connectionProfileCode], ['Connection version', app.connectionProfileVersion],
      ['Connection ID', app.consumerId ?? 'Not connected'], ['Assigned release', app.releaseId ?? 'Not assigned']],
  }
}

export function releaseEnvironment(release: DeploymentReleaseSummary, offer?: CustomerOfferSummary): string {
  const target = offer?.targets.find(item => item.id === release.targetProfileId)
  if (target?.intent === 'CUSTOMER_STAGING') return 'Customer staging'
  if (target?.intent === 'CUSTOMER_PRODUCTION') return 'Production'
  return release.targetProfileId ?? 'Target not recorded'
}

export function releaseIssues(release: DeploymentReleaseSummary): CustomerResourceIssue[] {
  const issues: CustomerResourceIssue[] = []
  if (release.errorMessage) issues.push({ code: 'release-error', message: release.errorMessage, severity: 'error' })
  if (release.verificationStatus !== 'PASSED') issues.push({ code: 'verification', message: `Verification: ${release.verificationStatus || 'Unknown'}`, severity: release.verificationStatus === 'FAILED' ? 'error' : 'info' })
  return issues
}

export function safeCustomerAppUrl(value: string | null): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null
  } catch { return null }
}

export type BrainLibraryFilter = 'all' | 'drafts' | 'live'
export type BrainReleaseEvidence = 'unavailable' | 'loading' | 'error' | 'ready'
export function brainLibraryQueryEvidence(query: { isError: boolean; isFetching: boolean; data?: unknown } | undefined): BrainReleaseEvidence {
  return !query ? 'unavailable' : query.isError ? 'error' : query.isFetching ? 'loading' : query.data !== undefined ? 'ready' : 'unavailable'
}

export type BrainLibraryState = {
  label: string
  color: CustomerResource['severity']
  draft: boolean
  live: boolean
  liveUnknown: boolean
  liveEvidence: BrainReleaseEvidence
  liveLabel: string
  connectionLabel: string
}

export function brainLibraryLatestRelease(releases: DeploymentReleaseSummary[], deploymentId: string, targetProfileId: string): DeploymentReleaseSummary | null {
  return releases.filter(release => release.deploymentId === deploymentId && release.targetProfileId === targetProfileId)
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))[0] ?? null
}

export function brainLibraryState(deployment: CustomerDeploymentSummary, apps: AIWorkspaceInstallationSummary[],
  releases: DeploymentReleaseSummary[], profiles: DeploymentTargetProfileSummary[] | undefined, evidence: BrainReleaseEvidence,
  appsAvailable = true, profileEvidence: BrainReleaseEvidence = profiles ? 'ready' : 'unavailable'): BrainLibraryState {
  const connected = apps.filter(app => app.deploymentId === deployment.deploymentId)
  const active = connected.filter(app => app.status === 'ACTIVE')
  const relevant = releases.filter(release => release.deploymentId === deployment.deploymentId)
  const liveEvidence = evidence === 'error' || profileEvidence === 'error' ? 'error'
    : evidence === 'loading' || profileEvidence === 'loading' ? 'loading'
      : evidence === 'ready' && profileEvidence === 'ready' ? 'ready' : 'unavailable'
  const production = (profiles ?? []).filter(profile => profile.environmentName === 'production')
  // Select before checking status: a newer failed or inactive record must not inherit a historical pass.
  const live = liveEvidence === 'ready' && production.some(profile => {
    const latest = brainLibraryLatestRelease(relevant, deployment.deploymentId, profile.id)
    return latest && brainLibraryReleaseVerified(latest)
  })
  const unknownEnvironment = relevant.some(release => !profiles?.some(target => target.id === release.targetProfileId))
  const unknownRelease = relevant.some(release => brainLibraryReleaseLabel(release) === 'Release status unavailable')
  const uncertain = liveEvidence !== 'ready' || unknownEnvironment || unknownRelease
  const draft = ['DRAFT', 'CONFIGURATION_ONLY'].includes(deployment.status) && !live
  const configuration: Record<string, [string, CustomerResource['severity']]> = {
    DRAFT: ['Draft', 'warning'], CONFIGURATION_ONLY: ['Draft', 'warning'],
    VERSION_PUBLISHED: ['Version saved', 'success'], ACTIVE: ['Version applied', 'info'],
    APPLY_REQUESTED: ['Updating', 'info'], PROVISIONING: ['Updating', 'info'],
    APPLY_FAILED: ['Update failed', 'error'], ARCHIVED: ['Archived', 'default'],
  }
  const [label, color] = live ? ['Production verified', 'success'] as const : configuration[deployment.status] ?? ['Status unavailable', 'default']
  return {
    label, color, draft, live, liveUnknown: !live && uncertain, liveEvidence,
    liveLabel: live ? 'Production verified' : liveEvidence === 'loading' ? 'Loading live status'
      : liveEvidence === 'error' ? 'Live status could not be loaded' : uncertain ? 'Live status unavailable' : 'Not live',
    connectionLabel: !appsAvailable ? 'Engage unavailable' : active.some(app => app.releaseId) ? 'Engage connected'
      : connected.some(app => app.status !== 'DISABLED') ? 'Engage not assigned' : connected.length ? 'Engage inactive' : 'Engage not connected',
  }
}

export function brainLibraryReleaseVerified(release: DeploymentReleaseSummary): boolean {
  return release.status === 'APPLIED_VERIFIED' && release.provisioningStatus === 'ACTIVE'
    && release.verificationStatus === 'PASSED' && !release.errorMessage
}

export function brainLibraryEnvironment(release: DeploymentReleaseSummary, profiles: DeploymentTargetProfileSummary[]): string {
  const environment = profiles.find(profile => profile.id === release.targetProfileId)?.environmentName
  return environment === 'production' ? 'Production' : environment === 'staging' ? 'Staging' : 'Environment unavailable'
}

export function brainLibraryAppState(app: AIWorkspaceInstallationSummary, releases: DeploymentReleaseSummary[],
  profiles: DeploymentTargetProfileSummary[], evidence: BrainReleaseEvidence, profileEvidence: BrainReleaseEvidence) {
  if (app.status !== 'ACTIVE') return { live: false, label: app.status === 'DISABLED' ? 'Disabled app' : app.status === 'DRAFT' ? 'Draft app' : 'App status unavailable' }
  if (!app.ready) return { live: false, label: 'App needs attention' }
  const release = releases.find(item => item.id === app.releaseId && item.deploymentId === app.deploymentId)
  if (evidence !== 'ready' || !release || brainLibraryReleaseLabel(release) === 'Release status unavailable') return { live: false, label: 'Experience status unavailable' }
  if (!brainLibraryReleaseVerified(release)) return { live: false, label: 'Experience not live' }
  if (profileEvidence !== 'ready') return { live: false, label: 'Experience status unavailable' }
  const environment = brainLibraryEnvironment(release, profiles)
  return { live: environment === 'Production', label: environment === 'Production' ? 'Live experience'
    : environment === 'Staging' ? 'Staging experience' : 'Experience status unavailable' }
}

export function brainLibraryReleaseLabel(release: DeploymentReleaseSummary): string {
  if (['DELETED', 'STOPPED'].includes(release.provisioningStatus)) return 'Infrastructure inactive'
  if (release.errorMessage || ['FAILED', 'PRE_APPLY_BLOCKED'].includes(release.status)) return 'Release needs attention'
  if (release.verificationStatus === 'FAILED' || release.status === 'APPLIED_VERIFICATION_FAILED') return 'Verification failed'
  if (release.status === 'VERIFYING' || release.verificationStatus === 'RUNNING') return 'Verification in progress'
  if (['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING'].includes(release.status)) return 'Release in progress'
  if (brainLibraryReleaseVerified(release)) return 'Verified release'
  return 'Release status unavailable'
}

export function filterBrainLibrary<T extends { resource: CustomerResource; state: BrainLibraryState }>(rows: T[], search: string, filter: BrainLibraryFilter): T[] {
  const term = search.trim().toLocaleLowerCase()
  return rows.filter(({ resource, state }) => resource.name.toLocaleLowerCase().includes(term)
    && (filter === 'all' || (filter === 'drafts' ? state.draft : state.live)))
}
