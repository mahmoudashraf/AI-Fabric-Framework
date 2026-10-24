import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import { Alert, Button, Stack } from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { probeCustomerOfferTargetPlacement, rerunDeploymentVerification,
  type CustomerOfferSummary, type DeploymentReleaseSummary, type PlatformAuthSessionSummary } from '../../api/platformApi'
import { useScopedOneTimeValue } from '../../hooks/useScopedOneTimeValue'

export function CustomerReleaseVerificationButton({ session, offer, deploymentId, release, label, onVerified }: {
  session: PlatformAuthSessionSummary
  offer: CustomerOfferSummary
  deploymentId: string
  release: DeploymentReleaseSummary | null
  label: string
  onVerified: () => Promise<void>
}) {
  const guard = useScopedOneTimeValue<{ severity: 'success' | 'warning' | 'error'; message: string }>(JSON.stringify([
    session.actorId, session.authenticationMode, session.customerId, offer.id, offer.fingerprint,
    offer.entitlement.revision, deploymentId, release?.id, release?.targetProfileId, release?.deploymentVersionId,
  ]))
  const eligible = Boolean(release?.deploymentId === deploymentId && release.targetProfileId
    && release.provisioningStatus === 'ACTIVE'
    && ['APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(release.status))
  const verify = useMutation({
    retry: false,
    mutationFn: async ({ token }: { token: symbol }) => {
      if (!eligible || !release?.targetProfileId || !guard.isCurrent(token)) throw new Error('Review a provisioned release first.')
      const snapshot = { offerId: offer.id, deploymentId, releaseId: release.id, target: release.targetProfileId,
        versionId: release.deploymentVersionId }
      await probeCustomerOfferTargetPlacement(snapshot.offerId, snapshot.target)
      if (!guard.isCurrent(token)) throw new Error('The selected account or release changed. Review it before verifying.')
      const result = await rerunDeploymentVerification(snapshot.deploymentId, snapshot.releaseId)
      if (result.deploymentId !== snapshot.deploymentId || result.releaseId !== snapshot.releaseId
        || result.deploymentVersionId !== snapshot.versionId) throw new Error('Verification response did not match the selected release.')
      return result
    },
    onSuccess: async (result, { token }) => {
      if (!guard.isCurrent(token)) return
      guard.publish(token, { severity: result.status === 'PASSED' ? 'success' : 'warning',
        message: result.status === 'PASSED' ? 'Release verification passed.' : `Verification needs attention: ${result.summaryMessage}` })
      await onVerified()
    },
    onError: (error, { token }) => {
      guard.publish(token, { severity: 'error', message: error instanceof Error ? error.message : 'Release verification failed.' })
    },
  })
  return <Stack spacing={1} sx={{ alignItems: 'flex-start' }}>
    <Button startIcon={<FactCheckOutlinedIcon />} disabled={!eligible || verify.isPending}
      onClick={() => verify.mutate({ token: guard.capture() })}>{verify.isPending ? 'Verifying release' : label}</Button>
    {guard.value ? <Alert severity={guard.value.severity}>{guard.value.message}</Alert> : null}
  </Stack>
}
