import { useState } from 'react'
import { useLocation } from 'react-router-dom'
import { Alert } from '@mui/material'
import type { DeploymentReleaseSummary, DeploymentTargetProfileSummary, DeploymentTargetReference } from '../api/platformApi'
import { HumanReviewPage } from '../pages/HumanReviewPage'
import { BrainPageHeader } from './BrainPageLayout'
import { useBrainEditor, useBrainWorkspace } from './BrainWorkspaceContext'

function isReviewEnvironmentEligible(brainId: string, targetProfileId: string, targetProfile: DeploymentTargetProfileSummary | null,
  release: DeploymentReleaseSummary | null, target: DeploymentTargetReference | null, requestedReleaseId: string | null) {
  // Review authority belongs to this exact active, verified environment, not a nearby usable release.
  return Boolean(brainId && targetProfileId && targetProfile?.id === targetProfileId && targetProfile.active
    && target?.deploymentId === brainId && target.targetProfileId === targetProfileId
    && release?.deploymentId === brainId && release.targetProfileId === targetProfileId
    && release.id === target.releaseId && (requestedReleaseId === null || requestedReleaseId === release.id)
    && release.provisioningStatus === 'ACTIVE' && release.status === 'APPLIED_VERIFIED' && release.verificationStatus === 'PASSED')
}

export function BrainActionReviewsPage() {
  const { brainId, targetProfileId, targetProfile, release, target } = useBrainWorkspace()
  const location = useLocation()
  const [busy, setBusy] = useState(false)
  const requestedReleaseId = new URLSearchParams(location.search).get('releaseId')
  const eligible = isReviewEnvironmentEligible(brainId, targetProfileId, targetProfile, release, target, requestedReleaseId)
  useBrainEditor({ dirty: false, busy, message: 'Live action reviews for this environment.' })

  return <>
    <BrainPageHeader title="Action reviews" />
    {!eligible ? <Alert severity="warning">Action reviews require this exact environment's active, verified release. No other environment or release was selected.</Alert>
      : <HumanReviewPage key={`${brainId}:${targetProfileId}:${release!.id}`} title="Action reviews" deploymentId={brainId}
        targetProfileId={targetProfileId} releaseId={release!.id} workspaceView onBusyChange={setBusy} />}
  </>
}
