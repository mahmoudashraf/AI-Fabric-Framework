import type { AIWorkspaceInstallationSummary, DeploymentReleaseSummary, DeploymentWorkspaceSummary } from '../api/platformApi'

type OverviewInput = {
  workspace: DeploymentWorkspaceSummary | null
  release: DeploymentReleaseSummary | null
  latestRelease: DeploymentReleaseSummary | null
  apps: AIWorkspaceInstallationSummary[]
  loading: boolean
  unavailable: boolean
  hasTarget: boolean
}

export function brainOverviewState(input: OverviewInput) {
  const { workspace, release, latestRelease, apps } = input
  const verified = release?.status === 'APPLIED_VERIFIED' && release.verificationStatus === 'PASSED'
  const matchingApps = release ? apps.filter(app => app.releaseId === release.id) : []
  const activeApps = matchingApps.filter(app => app.status === 'ACTIVE' && app.ready)
  const changes = Boolean(workspace && (!workspace.lifecycle.savedDraftMatchesLatestPublished
    || (workspace.latestVersion && workspace.latestVersion.id !== release?.deploymentVersionId)))
  const next = input.loading || input.unavailable || !workspace || !input.hasTarget ? null
    : latestRelease && ['FAILED', 'APPLIED_VERIFICATION_FAILED'].includes(latestRelease.status)
      ? { title: 'Your latest update needs attention', description: 'Review the failed update before making another change.', label: 'Review update', section: 'updates', warning: true }
    : !release ? { title: 'Bring your AI online', description: 'Review your saved setup, then publish and apply it to this environment.', label: 'Prepare environment', section: 'updates', warning: false }
    : !verified ? { title: 'Verify your applied release', description: 'Complete verification before using this release with customers.', label: 'Review verification', section: 'updates', warning: true }
    : changes ? { title: 'Your saved changes are not applied', description: 'Review and publish them when you are ready. Your running AI is unchanged.', label: 'Review changes', section: 'updates', warning: false }
    : !activeApps.length ? { title: 'Connect your customer experience', description: 'Connect or review an Engage app for this exact release.', label: 'Connect Engage app', section: 'channels', warning: false }
    : { title: 'Explore your customer experience', description: 'Try your connected AI before sharing it with customers.', label: 'Try your AI', section: 'try', warning: false }
  return { verified, matchingApps, activeApps, changes, next }
}
