import type { DeploymentReleaseSummary, DeploymentTargetProfileSummary, PlatformConsumerSummary } from '../../api/platformApi'

export function initialDeploymentConnection(targets: DeploymentTargetProfileSummary[], releases: DeploymentReleaseSummary[],
  consumers: PlatformConsumerSummary[], customerId: string, deploymentId: string,
  currentConsumerId?: string | null, bootstrapTargetId?: string | null) {
  const available = targets.filter(item => item.active)
  const current = consumers.find(item => item.consumerId === currentConsumerId && item.customerId === customerId
    && item.boundDeploymentId === deploymentId)
  const assignments = current ? [current] : consumers.filter(item => item.customerId === customerId
    && item.boundDeploymentId === deploymentId && item.status === 'ACTIVE')
  const distinct = new Set(assignments.map(item => JSON.stringify([item.boundTargetProfileId, item.boundReleaseId ?? null])))
  if (distinct.size > 1) return { targetId: '', releaseId: '', issue: 'This deployment has multiple connections. Choose the connection you want to use.' }
  const assigned = assignments[0]
  if (assigned) {
    const target = available.find(item => item.id === assigned.boundTargetProfileId)
    const release = releases.find(item => item.id === assigned.boundReleaseId && item.targetProfileId === target?.id
      && item.deploymentId === deploymentId)
    return { targetId: target?.id ?? '', releaseId: assigned.boundReleaseId ?? '',
      issue: assigned.status !== 'ACTIVE' || !target || (assigned.boundReleaseId && !release)
        ? 'The assigned connection is unavailable. Choose another connection explicitly.' : null }
  }
  const deployed = releases.filter(item => item.deploymentId === deploymentId
    && available.some(target => target.id === item.targetProfileId))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const environments = new Set(deployed.map(item => available.find(target => target.id === item.targetProfileId)!.environmentName))
  if (environments.size > 1) return { targetId: '', releaseId: '', issue: 'This deployment has releases in multiple environments. Choose the connection you want to use.' }
  if (deployed.length) return { targetId: deployed[0].targetProfileId!, releaseId: deployed[0].id, issue: null }
  const targetId = initialConnectionTarget(targets, null, bootstrapTargetId)
  return { targetId, releaseId: '', issue: targetId ? null : 'Choose a connection for this deployment.' }
}

export function connectionEnvironments(targets: DeploymentTargetProfileSummary[]) {
  const names = [...new Set(targets.filter(item => item.active).map(item => item.environmentName))]
  return names.sort((a, b) => a.localeCompare(b)).map(name => ({
    name,
    label: name === 'staging' ? 'Staging' : name === 'production' ? 'Production' : name,
  }))
}

export function connectionEnvironmentSelection(environment: string, targets: DeploymentTargetProfileSummary[],
  releases: DeploymentReleaseSummary[], current?: PlatformConsumerSummary, bootstrapTargetId?: string | null) {
  const available = targets.filter(item => item.active && item.environmentName === environment)
  if (current && available.some(item => item.id === current.boundTargetProfileId)) {
    return { targetId: current.boundTargetProfileId!,
      releaseId: initialConnectionRelease(releases, current.boundTargetProfileId!, current) }
  }
  // A release determines its exact target, not the first profile with the same environment name.
  const latest = releases.filter(item => available.some(target => target.id === item.targetProfileId))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]
  if (latest) return { targetId: latest.targetProfileId!, releaseId: connectableRelease(latest) ? latest.id : '' }
  const bootstrap = targets.find(item => item.id === bootstrapTargetId)
  const exact = available.find(item => item.id === bootstrapTargetId)
  if (exact) return { targetId: exact.id, releaseId: '' }
  const compatible = bootstrap ? available.filter(item => item.providerType === bootstrap.providerType
    && item.sourceStrategy === bootstrap.sourceStrategy) : available
  const preferred = compatible.length === 1 ? compatible[0]
    : compatible.filter(item => item.defaultForRuntime).length === 1
      ? compatible.find(item => item.defaultForRuntime) : undefined
  return { targetId: preferred?.id ?? '', releaseId: '' }
}

export function initialConnectionTarget(targets: DeploymentTargetProfileSummary[], currentTargetId?: string | null,
  bootstrapTargetId?: string | null) {
  const available = targets.filter(item => item.active)
  // Preserve explicit environment intent; never substitute staging for an unavailable production target.
  if (currentTargetId) return available.some(item => item.id === currentTargetId) ? currentTargetId : ''
  if (bootstrapTargetId) return available.some(item => item.id === bootstrapTargetId) ? bootstrapTargetId : ''
  return available.length === 1 ? available[0].id : ''
}

export function matchingEngageConnections(consumers: PlatformConsumerSummary[], customerId: string,
  deploymentId: string, targetProfileId: string, releaseId: string | null) {
  return consumers.filter(item => item.customerId === customerId && item.status === 'ACTIVE'
    && item.boundDeploymentId === deploymentId && item.boundTargetProfileId === targetProfileId
    && (item.boundReleaseId ?? null) === releaseId)
}

export function connectableRelease(release: DeploymentReleaseSummary) {
  return release.status === 'APPLIED_VERIFIED' && release.verificationStatus === 'PASSED'
}

export function initialConnectionRelease(releases: DeploymentReleaseSummary[], targetProfileId: string,
  current?: PlatformConsumerSummary) {
  if (current?.boundTargetProfileId === targetProfileId && current.boundReleaseId) {
    return releases.some(item => item.id === current.boundReleaseId && item.targetProfileId === targetProfileId)
      ? current.boundReleaseId : ''
  }
  const latest = releases.filter(item => item.targetProfileId === targetProfileId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]
  return latest && connectableRelease(latest) ? latest.id : ''
}
