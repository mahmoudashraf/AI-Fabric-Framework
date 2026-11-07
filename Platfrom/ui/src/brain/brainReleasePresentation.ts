import type { DeploymentReleaseSummary, DeploymentTargetProfileSummary } from '../api/platformApi'
import { isUsableRelease } from './brainNavigation'

export function environmentLabel(value: string) {
  return value ? value.charAt(0).toUpperCase() + value.slice(1) : 'Environment'
}

export function releaseInProgress(release: DeploymentReleaseSummary) {
  return ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes(release.status)
    || ['QUEUED', 'RUNNING', 'AWAITING_CONFIRMATION'].includes(release.provisioningStatus)
    || release.verificationStatus === 'RUNNING'
}

export function targetReleaseState(releases: DeploymentReleaseSummary[], targetId: string) {
  const history = releases.filter(item => item.targetProfileId === targetId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const active = history.find(isUsableRelease) ?? null
  const latest = history[0] ?? null
  const pending = latest && releaseInProgress(latest) ? latest : null
  const failed = latest && ['FAILED', 'PRE_APPLY_BLOCKED'].includes(latest.status) ? latest : null
  const verificationFailed = active?.status === 'APPLIED_VERIFICATION_FAILED' || active?.verificationStatus === 'FAILED'
  const verified = active?.status === 'APPLIED_VERIFIED' && active.verificationStatus === 'PASSED'
  const label = pending ? pending.status === 'VERIFYING' || pending.verificationStatus === 'RUNNING' ? 'Verifying' : 'Updating'
    : failed ? 'Update failed' : verificationFailed ? 'Verification failed' : verified ? 'Verified'
      : active ? 'Applied' : 'Not deployed'
  const color = failed ? 'error' : verificationFailed ? 'warning' : verified ? 'success' : pending ? 'info' : 'default'
  return { active, latest, pending, failed, label, color } as const
}

export function groupEnvironmentTargets(profiles: DeploymentTargetProfileSummary[]) {
  const groups = new Map<string, DeploymentTargetProfileSummary[]>()
  for (const profile of profiles) {
    const rows = groups.get(profile.environmentName) ?? []
    rows.push(profile)
    groups.set(profile.environmentName, rows)
  }
  return [...groups].sort(([a], [b]) => {
    const order = (name: string) => name === 'staging' ? 0 : name === 'production' ? 1 : 2
    return order(a) - order(b) || a.localeCompare(b)
  })
}
