import type { AIWorkspaceDeploymentUpdateReview, DeploymentReleaseSummary, DeploymentVersionSummary } from '../../api/platformApi'

type UpdateApi = {
  authorize: () => Promise<void>
  assertCurrent: () => void
  publish: (review: AIWorkspaceDeploymentUpdateReview) => Promise<DeploymentVersionSummary>
  compatible: (deploymentId: string, versionId: string) => Promise<Array<{ id: string }>>
  apply: (deploymentId: string, versionId: string, targetId: string, sourceId: string | undefined, key: string) => Promise<DeploymentReleaseSummary>
}

/** Compose the existing publication and apply commands, retaining the reviewed target and image. */
export async function applyReviewedEngageUpdate(review: AIWorkspaceDeploymentUpdateReview, operationKey: string, api: UpdateApi) {
  await api.authorize()
  const version = await api.publish(review)
  api.assertCurrent()
  if (version.deploymentId !== review.deploymentId || version.sourceDraftId !== review.draftId) throw new Error('The published version does not match this review.')
  if (review.sourceArtifactId) {
    const compatible = await api.compatible(review.deploymentId, version.id)
    api.assertCurrent()
    if (!compatible.some(item => item.id === review.sourceArtifactId)) throw new Error('The current runtime image cannot run this update. The version is published; select a compatible image in deployment settings.')
  }
  await api.authorize()
  const release = await api.apply(review.deploymentId, version.id, review.targetProfileId, review.sourceArtifactId ?? undefined, operationKey)
  api.assertCurrent()
  if (release.deploymentId !== review.deploymentId || release.deploymentVersionId !== version.id
    || release.targetProfileId !== review.targetProfileId) throw new Error('The submitted release does not match this review.')
  return release
}
