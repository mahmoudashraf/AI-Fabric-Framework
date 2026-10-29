import type { DeploymentReleaseSummary, PlatformConsumerSummary } from '../../api/platformApi'

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
