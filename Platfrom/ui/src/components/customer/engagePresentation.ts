import type { AIWorkspaceInstallationSummary as App, AIWorkspaceReadinessCheck, CustomerDeploymentSummary, PlatformConsumerSummary } from '../../api/platformApi'

export type EngageConnectionEvidence = 'loading' | 'error' | 'ready' | 'unavailable'
export function engageConnectionInfo(app: App, brains: CustomerDeploymentSummary[], connections: PlatformConsumerSummary[] = [], evidence: EngageConnectionEvidence = 'unavailable') {
  const brainLabel = app.deploymentId ? brains.find(brain => brain.deploymentId === app.deploymentId)?.name ?? 'Brain unavailable' : 'Not connected'
  const connection = connections.find(item => item.customerId === app.customerId && item.consumerId === app.consumerId)
  // Only the exact assigned release identifies the environment; never use the Brain's bootstrap environment.
  const matching = evidence === 'ready' && connection?.boundDeploymentId === app.deploymentId && connection?.boundReleaseId === app.releaseId
  const environmentLabel = !app.deploymentId || !app.releaseId ? 'Not assigned'
    : evidence === 'loading' ? 'Loading...'
      : !matching ? 'Unavailable'
        : connection?.boundDeploymentEnvironment === 'staging' ? 'Staging'
          : connection?.boundDeploymentEnvironment === 'production' ? 'Production' : 'Unavailable'
  return { brainLabel, environmentLabel }
}

export function engageReadinessInfo(app: App) {
  const checks = app.readinessChecks.filter(check => check.status !== 'PASSED')
  const domainErrors = app.domains.filter(domain => domain.lastVerificationError)
  const first = checks.find(check => check.status === 'BLOCKED') ?? checks[0]
  if (first) return { label: first.status === 'BLOCKED' ? 'Blocked' : 'Needs review', color: 'warning' as const,
    reason: first.message, additional: checks.length + domainErrors.length - 1 }
  if (domainErrors.length) return { label: 'Needs attention', color: 'warning' as const,
    reason: `${domainErrors[0].hostname}: ${domainErrors[0].lastVerificationError}`, additional: domainErrors.length - 1 }
  return app.ready ? { label: 'Ready', color: 'success' as const, reason: 'Recorded checks passed', additional: 0 }
    : { label: 'Not ready', color: 'warning' as const, reason: 'Review launch checks for this app', additional: 0 }
}

export type EngageStep = 'connection' | 'appearance' | 'delivery' | 'readiness'
export type EngageFilter = 'all' | 'setup' | 'active'
const connectionCodes = new Set(['CONSUMER', 'ASSIGNMENT', 'CONNECTION_PROFILE'])
const deliveryCodes = new Set(['ORIGINS', 'APP_DOMAIN', 'APP_DELIVERY'])
export function engageCheckStep(check: AIWorkspaceReadinessCheck): EngageStep {
  return connectionCodes.has(check.code) ? 'connection' : deliveryCodes.has(check.code) ? 'delivery' : 'readiness'
}
export function engageState(app: App) {
  const attention = !app.ready || app.readinessChecks.some(check => check.status !== 'PASSED') || app.domains.some(domain => domain.lastVerificationError)
  if (app.status === 'DISABLED') return { label: 'Disabled', color: 'default' as const }
  if (app.status === 'ACTIVE') return { label: attention ? 'Needs attention' : 'Active', color: attention ? 'warning' as const : 'success' as const }
  if (app.status === 'DRAFT') return { label: attention ? 'Setup needed' : 'Ready to activate', color: attention ? 'warning' as const : 'success' as const }
  return { label: app.status || 'Status unavailable', color: 'default' as const }
}
export function engageNextStep(app: App): EngageStep {
  const remaining = app.readinessChecks.filter(check => check.status !== 'PASSED')
  if (!app.deploymentId || !app.releaseId || remaining.some(check => engageCheckStep(check) === 'connection')) return 'connection'
  if (remaining.some(check => engageCheckStep(check) === 'delivery') || app.domains.some(domain => domain.lastVerificationError)) return 'delivery'
  if (!app.ready || remaining.length || app.status !== 'ACTIVE') return 'readiness'
  return 'appearance'
}
export function engageStepPath(app: App, step: EngageStep, source?: URLSearchParams) {
  const params = new URLSearchParams()
  for (const key of ['kitId', 'fromBrainId', 'fromBrainReleaseId']) {
    const value = source?.get(key)
    if (value) params.set(key, value)
  }
  params.set('resourceKind', 'app'); params.set('resourceId', app.installationId)
  if (app.deploymentId) params.set('deploymentId', app.deploymentId)
  if (step === 'connection' || step === 'appearance') params.set('appTool', step)
  else {
    params.set('editor', 'configuration'); params.set('appSection', step)
  }
  return `/ai-workspaces?${params}`
}

export function normalizeLegacyEngageRoute(source: URLSearchParams): URLSearchParams | null {
  if (!['manage', 'setup'].includes(source.get('view') ?? '')) return null
  const params = new URLSearchParams(source)
  const preview = params.get('previewInstallationId'), connection = params.get('connectInstallationId'), copy = params.get('sourceInstallationId')
  const appId = preview ?? connection ?? copy
  params.delete('view'); params.delete('previewInstallationId'); params.delete('connectInstallationId')
  params.set('appTool', preview ? 'appearance' : connection || params.has('connectDeploymentId') ? 'connection' : 'create')
  if (appId) { params.set('resourceKind', 'app'); params.set('resourceId', appId) }
  return params
}

export function closeEngageToolRoute(source: URLSearchParams, saved?: App): URLSearchParams {
  const params = new URLSearchParams(source)
  for (const key of ['appTool', 'sourceInstallationId', 'connectDeploymentId', 'consumerId', 'delivery']) params.delete(key)
  if (saved) {
    if (saved.installationId !== params.get('resourceId')) params.delete('kitId')
    if (saved.deploymentId !== params.get('fromBrainId')) { params.delete('fromBrainId'); params.delete('fromBrainReleaseId') }
    params.set('resourceKind', 'app'); params.set('resourceId', saved.installationId)
    if (saved.deploymentId) params.set('deploymentId', saved.deploymentId)
    else params.delete('deploymentId')
  }
  return params
}
