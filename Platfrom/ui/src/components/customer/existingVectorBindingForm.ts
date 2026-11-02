import type { ExistingVectorBindingRequest, ExistingVectorBindingSummary } from '../../api/platformApi'

export function existingVectorAuth(manifest: unknown, deploymentId: string, versionId: string): 'TOKEN' | 'USERNAME_PASSWORD' | null {
  const value = manifest as { deploymentId?: string; versionId?: string; providerConfig?: Record<string, unknown> } | null
  if (value?.deploymentId !== deploymentId || value.versionId !== versionId) throw new Error('The published vector configuration scope changed.')
  const config = value.providerConfig
  const text = (value: unknown) => typeof value === 'string' ? value.trim().toUpperCase() : ''
  const mode = text(config?.vectorProvisioningMode)
  if (text(config?.vectorStrategy) !== 'MILVUS' || ['LOCAL_MANAGED', 'PLATFORM_MANAGED'].includes(mode)
    || text(config?.vectorStoragePosture) !== 'SHARED' || config?.milvusExternalBindingRequired !== true) return null
  const auth = config.milvusAuthenticationType === undefined ? 'USERNAME_PASSWORD' : config.milvusAuthenticationType
  if (auth !== 'TOKEN' && auth !== 'USERNAME_PASSWORD') throw new Error('The published vector authentication type is unsupported.')
  return auth
}

export function checkedExistingVectorBinding(value: ExistingVectorBindingSummary, deploymentId: string, targetId: string) {
  if (!value || value.deploymentId !== deploymentId || value.targetProfileId !== targetId || !value.bindingId
    || !value.versionId || value.lifecycle !== 'RETAIN_EXTERNAL' || value.databaseName !== 'default') {
    throw new Error('The vector binding response has a different or incomplete scope.')
  }
  return value
}

export type ExistingVectorForm = {
  clusterId: string; projectId: string; regionId: string
  expectedDeploymentOption: ExistingVectorBindingRequest['expectedDeploymentOption']
  token: string; username: string; password: string
}

export function existingVectorRequest(form: ExistingVectorForm, versionId: string, authType: ExistingVectorBindingRequest['authType']): ExistingVectorBindingRequest {
  if (!versionId || !form.clusterId.trim() || !form.projectId.trim() || !form.regionId.trim()
    || (authType === 'TOKEN' ? !form.token : !form.username || !form.password)) throw new Error('Complete the existing vector connection fields.')
  return { versionId, clusterId: form.clusterId.trim(), projectId: form.projectId.trim(), regionId: form.regionId.trim(),
    expectedDeploymentOption: form.expectedDeploymentOption, databaseName: 'default', authType,
    ...(authType === 'TOKEN' ? { token: form.token } : { username: form.username, password: form.password }) }
}
