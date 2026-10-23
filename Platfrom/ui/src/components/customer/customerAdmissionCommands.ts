import type {
  CustomerAdmissionReservationRequest,
  CustomerAdmissionReservationSummary,
  DeploymentReleaseSummary,
} from '../../api/platformApi'

export function canApplyReviewedVersion(versionId: string | null | undefined, release: DeploymentReleaseSummary | null) {
  if (!versionId) return false
  if (!release || ['FAILED', 'PRE_APPLY_BLOCKED'].includes(release.status)) return true
  if (release.status === 'APPLIED_VERIFICATION_FAILED' && release.provisioningStatus === 'ACTIVE') return true
  return release.status === 'APPLIED_VERIFIED' && release.verificationStatus === 'PASSED'
    && release.provisioningStatus === 'ACTIVE' && release.deploymentVersionId !== versionId
}

type AdmissionApi = {
  probe: (offerId: string, targetProfileId: string) => Promise<unknown>
  list: (offerId: string, deploymentId: string, targetProfileId: string) => Promise<CustomerAdmissionReservationSummary[]>
  reserve: (offerId: string, deploymentId: string, request: CustomerAdmissionReservationRequest) => Promise<CustomerAdmissionReservationSummary>
}

/** Keep the original allocation choice when a response is lost after the server accepted a command. */
export class CustomerAdmissionCommands {
  private readonly requests = new Map<string, {
    identity: string
    request: Promise<CustomerAdmissionReservationRequest>
  }>()

  constructor(private readonly api: AdmissionApi) {}

  async authorize(offerId: string, deploymentId: string, request: CustomerAdmissionReservationRequest) {
    const identity = JSON.stringify({ offerId, deploymentId, request })
    let prepared = this.requests.get(request.operationKey)
    if (prepared && prepared.identity !== identity) throw new Error('The admission command changed. Review it again.')
    const reusedChoice = Boolean(prepared)
    if (!prepared) {
      const choice = this.api.probe(offerId, request.targetProfileId)
        .then(() => this.api.list(offerId, deploymentId, request.targetProfileId)).then((allocations) => {
        if (allocations.length > 1) throw new Error('Capacity allocations require reconciliation before this operation.')
        if (request.releaseId && allocations.length !== 1) throw new Error('The release has no current capacity allocation.')
        return { ...request, allocationId: allocations[0]?.id }
      }).catch((error: unknown) => {
        this.requests.delete(request.operationKey)
        throw error
      })
      prepared = { identity, request: choice }
      this.requests.set(request.operationKey, prepared)
    }
    const command = await prepared.request
    if (reusedChoice) await this.api.probe(offerId, request.targetProfileId)
    return this.api.reserve(offerId, deploymentId, command)
  }

  complete(operationKey: string) {
    this.requests.delete(operationKey)
  }
}
