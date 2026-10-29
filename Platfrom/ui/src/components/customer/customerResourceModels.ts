import type { AIWorkspaceInstallationSummary, CustomerDeploymentSummary, CustomerOfferSummary, DeploymentReleaseSummary } from '../../api/platformApi'

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

export function deploymentResource(deployment: CustomerDeploymentSummary, offer?: CustomerOfferSummary): CustomerResource {
  return {
    id: deployment.deploymentId, kind: 'deployment', name: deployment.name,
    subtitle: offer?.name ?? 'Deployment configuration', status: deployment.status,
    severity: deployment.status === 'DRAFT' ? 'info' : 'default', issues: [],
    deploymentId: deployment.deploymentId, releaseId: null, updatedAt: deployment.createdAt,
    facts: [['Configuration state', deployment.status], ['Initial environment', deployment.environmentName],
      ['Reviewed template', offer?.name ?? 'Not available']],
    configurationFacts: [['Template', offer?.name ?? 'Not available'], ['Reviewed targets', offer ? String(offer.targets.length) : 'Not available'],
      ['Connection slots', offer ? String(offer.bindingSlots.length) : 'Not available']],
    advancedFacts: [['Deployment ID', deployment.deploymentId], ['Published version', deployment.activeVersionId ?? 'Not published'],
      ['Offer ID', deployment.offerId], ['Bootstrap target', deployment.bootstrapTargetProfileId]],
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
      app.deliveryMode === 'embedded' ? ['Website origins', app.allowedOrigins.join(', ') || 'Not configured']
        : ['App address', app.appUrl ?? 'Not configured'], ['Connection', app.connectionMode],
      ['Visitor access', app.interactionPolicy]],
    configurationFacts: [['Delivery', app.deliveryMode], ['Experience', app.experiencePackCode], ['Visitor access', app.interactionPolicy]],
    advancedFacts: [['Installation ID', app.installationId], ['Experience version', app.experiencePackVersion],
      ['Connection profile', app.connectionProfileCode], ['Connection version', app.connectionProfileVersion],
      ['Assigned release', app.releaseId ?? 'Not assigned']],
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
