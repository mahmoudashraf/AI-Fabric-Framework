import { platformRequest } from './platformApi'

export type SetupCheckMode = 'ALL' | 'CHANGED' | 'FAILED'
export type SetupCheckService = {
  id: string
  label: string
  status: 'VERIFIED' | 'FAILED' | 'NOT_TESTED'
  message: string
  checkedAt: string | null
  usedBy: string[]
  httpStatus: number | null
}
export type DeploymentSetupChecks = {
  deploymentId: string
  draftId: string
  revisionNumber: number
  checkedAt: string | null
  services: SetupCheckService[]
  verifiedCount: number
  failedCount: number
  notTestedCount: number
}

const path = (deploymentId: string) => `/api/deployments/${encodeURIComponent(deploymentId)}/setup-checks`

// This read returns recorded results; only the explicit POST may contact services.
export const fetchDeploymentSetupChecks = (deploymentId: string, signal?: AbortSignal) =>
  platformRequest<DeploymentSetupChecks>(path(deploymentId), { signal })

export const runDeploymentSetupChecks = (deploymentId: string, mode: SetupCheckMode, signal?: AbortSignal) =>
  platformRequest<DeploymentSetupChecks>(path(deploymentId), { method: 'POST', body: JSON.stringify({ mode }), signal })
