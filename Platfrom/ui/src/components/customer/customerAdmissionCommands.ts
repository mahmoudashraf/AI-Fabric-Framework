import { fetchDeploymentOperationsStatus } from '../../api/platformApi'
import type {
  CustomerAdmissionReservationRequest,
  CustomerAdmissionReservationSummary,
  DeploymentOperationsStatus,
  DeploymentReleaseSummary,
} from '../../api/platformApi'

export function releaseNeedsProgressPolling(status: string) {
  return ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes(status)
}

export function admissionNeedsProgressPolling(state: string) {
  return ['RESERVED', 'PROVISIONING', 'OUTCOME_UNKNOWN'].includes(state)
}

export function currentCustomerAdmission(local: CustomerAdmissionReservationSummary | null,
  observed: CustomerAdmissionReservationSummary[], deploymentId: string, targetProfileId: string) {
  const scoped = observed.filter(value => value.deploymentId === deploymentId && value.targetProfileId === targetProfileId)
  const retained = local?.deploymentId === deploymentId && local.targetProfileId === targetProfileId ? local : null
  const fresh = retained ? scoped.find(value => value.id === retained.id && value.revision >= retained.revision) : null
  return fresh ?? retained ?? (scoped.length === 1 ? scoped[0] : null)
}

export function canApplyReviewedVersion(versionId: string | null | undefined, release: DeploymentReleaseSummary | null) {
  if (!versionId) return false
  if (!release || ['FAILED', 'PRE_APPLY_BLOCKED'].includes(release.status)) return true
  if (release.status === 'APPLIED_VERIFICATION_FAILED' && release.provisioningStatus === 'ACTIVE') return true
  return release.status === 'APPLIED_VERIFIED' && release.verificationStatus === 'PASSED'
    && release.provisioningStatus === 'ACTIVE' && release.deploymentVersionId !== versionId
}

type AdmissionApi = {
  status?: () => Promise<DeploymentOperationsStatus>
  probe?: (offerId: string, targetProfileId: string) => Promise<unknown>
  list?: (offerId: string, deploymentId: string, targetProfileId: string) => Promise<CustomerAdmissionReservationSummary[]>
  reserve?: (offerId: string, deploymentId: string, request: CustomerAdmissionReservationRequest) => Promise<CustomerAdmissionReservationSummary>
}

/** Policy assessment belongs to core apply, not a browser-created capacity reservation. */
export class CustomerAdmissionCommands {
  private readonly requests = new Map<string, string>()

  constructor(private readonly api: AdmissionApi = {}) {}

  async authorize(offerId: string, deploymentId: string, request: CustomerAdmissionReservationRequest): Promise<CustomerAdmissionReservationSummary | null> {
    const identity = JSON.stringify({ offerId, deploymentId, request })
    const prepared = this.requests.get(request.operationKey)
    if (prepared && prepared !== identity) throw new Error('The admission command changed. Review it again.')
    this.requests.set(request.operationKey, identity)
    const status = await (this.api.status ?? fetchDeploymentOperationsStatus)()
    if (!status || !['DISABLED', 'OBSERVE', 'ENFORCE'].includes(status.mode)
      || status.manualDeploymentsAllowed !== true || typeof status.serviceConfigured !== 'boolean'
      || !['FUNDED', 'TEAM_AUTHORIZED', 'CUSTOMER_KEY'].includes(status.providerUsageMode)
      || status.decisionSource !== 'EXTERNAL_SERVICE') {
      throw new Error('Deployment operations policy status is invalid. Refresh before continuing.')
    }
    return null
  }

  complete(operationKey: string) {
    this.requests.delete(operationKey)
  }
}
