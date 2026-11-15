import type { AIWorkspaceInstallationSummary as App, AIWorkspaceReadinessCheck } from '../../api/platformApi'

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
  if (step === 'connection') { params.set('view', 'manage'); params.set('connectInstallationId', app.installationId) }
  else if (step === 'appearance') { params.set('view', 'manage'); params.set('previewInstallationId', app.installationId) }
  else {
    params.set('resourceKind', 'app'); params.set('resourceId', app.installationId)
    if (app.deploymentId) params.set('deploymentId', app.deploymentId)
    params.set('editor', 'configuration'); params.set('appSection', step)
  }
  return `/ai-workspaces?${params}`
}
