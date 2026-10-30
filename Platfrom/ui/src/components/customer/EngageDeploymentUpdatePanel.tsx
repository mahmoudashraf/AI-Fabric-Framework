import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Stack, Typography } from '@mui/material'
import SystemUpdateAltRounded from '@mui/icons-material/SystemUpdateAltRounded'
import { useQuery } from '@tanstack/react-query'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { isCustomerAuthenticationMode } from '../../auth/customerSession'
import { releaseNeedsProgressPolling } from './customerAdmissionCommands'
import { applyReviewedEngageUpdate } from './engageDeploymentUpdate'
import { applyDeploymentVersion, fetchCompatibleDeploymentSourceArtifacts, fetchDeploymentReleases, fetchPlatformAuthSession,
  publishAIWorkspaceDeploymentUpdate, reviewAIWorkspaceDeploymentUpdate, updatePlatformConsumerBinding,
  type AIWorkspaceDeploymentUpdateReview, type AIWorkspaceInstallationSummary, type DeploymentReleaseSummary } from '../../api/platformApi'

export function EngageDeploymentUpdatePanel({ installation, refresh }: {
  installation: AIWorkspaceInstallationSummary; refresh: () => Promise<void>
}) {
  const { session } = usePlatformAuth()
  const [open, setOpen] = useState(false)
  const [review, setReview] = useState<AIWorkspaceDeploymentUpdateReview | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [release, setRelease] = useState<DeploymentReleaseSummary | null>(null)
  const keys = useRef({ apply: crypto.randomUUID(), binding: crypto.randomUUID() })
  const active = useRef({ customerId: installation.customerId, installationId: installation.installationId, actorId: session?.actorId, mounted: true })
  active.current = { ...active.current, customerId: installation.customerId, installationId: installation.installationId, actorId: session?.actorId }
  useEffect(() => { active.current.mounted = true; return () => { active.current.mounted = false } }, [])
  const scope = JSON.stringify([installation.customerId, installation.installationId, session?.actorId])
  const liveScope = useRef(scope)
  liveScope.current = scope
  const requireCurrentScope = () => {
    if (!active.current.mounted || liveScope.current !== scope) throw new Error('The app account changed. Reopen this review.')
  }
  useEffect(() => { setOpen(false); setReview(null); setRelease(null); setConfirmed(false); setError(null)
    keys.current = { apply: crypto.randomUUID(), binding: crypto.randomUUID() }
  }, [scope])
  const blocked = installation.readinessChecks.some(check => check.status === 'BLOCKED'
    && (check.code === 'RUNTIME_CORS' || check.code.startsWith('EXTERNAL_IDENTITY_')))
  const observed = useQuery({ queryKey: ['engage-update-release', review?.deploymentId, release?.id],
    queryFn: async () => {
      const result = (await fetchDeploymentReleases(review!.deploymentId)).find(item => item.id === release!.id)
      if (!result) throw new Error('The submitted release is unavailable. Review deployment history before retrying.')
      return result
    }, enabled: Boolean(review && release && open), retry: false,
    refetchInterval: query => {
      const status = query.state.data?.status
      return !status || releaseNeedsProgressPolling(status) ? 4000 : false
    } })
  const currentRelease = observed.data ?? release
  const verified = currentRelease?.status === 'APPLIED_VERIFIED' && currentRelease.verificationStatus === 'PASSED'
  const authorize = async () => {
    requireCurrentScope()
    const before = { ...active.current }
    const account = await fetchPlatformAuthSession()
    requireCurrentScope()
    if (!before.mounted || !active.current.mounted || before.customerId !== active.current.customerId
      || before.installationId !== active.current.installationId || before.actorId !== active.current.actorId
      || !account.authenticated || account.actorId !== before.actorId
      || (account.role !== 'PLATFORM_ADMIN' && (account.role !== 'CUSTOMER_ADMIN'
        || !isCustomerAuthenticationMode(account.authenticationMode) || account.customerId !== before.customerId))) {
      throw new Error('The app account changed. Reopen this review in the current account.')
    }
  }
  const prepare = async () => {
    setOpen(true); setPending(true); setError(null)
    try {
      await authorize()
      const result = await reviewAIWorkspaceDeploymentUpdate(installation.customerId, installation.installationId)
      requireCurrentScope()
      if (result.installationId !== installation.installationId || result.customerId !== installation.customerId
        || result.deploymentId !== installation.deploymentId || result.consumerId !== installation.consumerId) throw new Error('The app assignment changed. Refresh and review again.')
      setReview(result); setConfirmed(false)
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'The update could not be reviewed.') }
    finally { setPending(false) }
  }
  const apply = async () => {
    if (!review || !confirmed || release) return
    setPending(true); setError(null)
    try {
      const result = await applyReviewedEngageUpdate(review, keys.current.apply, {
        authorize, assertCurrent: requireCurrentScope, publish: publishAIWorkspaceDeploymentUpdate,
        compatible: fetchCompatibleDeploymentSourceArtifacts, apply: applyDeploymentVersion,
      })
      setRelease(result)
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'The update was interrupted. Check deployment history before changing the request.') }
    finally { setPending(false) }
  }
  const connect = async () => {
    if (!review || !currentRelease || !verified) return
    setPending(true); setError(null)
    try {
      await authorize()
      await updatePlatformConsumerBinding(review.customerId, review.consumerId, {
        deploymentId: review.deploymentId, releaseId: currentRelease.id, targetProfileId: review.targetProfileId,
        expectedBindingRevision: review.expectedBindingRevision, operationKey: keys.current.binding,
        reason: 'Customer approved the verified Engage domain and identity configuration update.',
      })
      requireCurrentScope()
      await refresh(); setOpen(false)
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'The verified update could not be connected.') }
    finally { setPending(false) }
  }
  if (!blocked && !release) return null
  return <>
    <Button startIcon={<SystemUpdateAltRounded />} size="small" disabled={pending} onClick={() => review || release ? setOpen(true) : void prepare()}>
      {release ? 'View deployment update' : 'Apply required deployment update'}
    </Button>
    <Dialog open={open} onClose={pending ? undefined : () => setOpen(false)} fullWidth maxWidth="sm">
      <DialogTitle>Review deployment update</DialogTitle>
      <DialogContent dividers><Stack spacing={2}>
        {pending && !review ? <Typography>Preparing configuration review...</Typography> : null}
        {review ? <>
          <Typography variant="subtitle2">{review.deploymentName} · {review.environment}</Typography>
          <Typography variant="body2">Changed sections: {review.changedSections.join(', ') || 'None'}</Typography>
          {review.changedSecurityFields.length ? <Typography variant="body2">Security settings: {review.changedSecurityFields.join(', ')}</Typography> : null}
          {review.addedOrigins.map(origin => <Typography key={origin} variant="body2" sx={{ overflowWrap: 'anywhere' }}>Allow website: {origin}</Typography>)}
          {review.removedOrigins.map(origin => <Typography key={origin} variant="body2" sx={{ overflowWrap: 'anywhere' }}>Remove website: {origin}</Typography>)}
          {review.unrelatedChanges ? <Alert severity="warning">The draft also contains changes outside Engage origins and identity. These changes will be included in the deployment update.</Alert> : null}
          <Button component="a" href={`/revisions?deploymentId=${encodeURIComponent(review.deploymentId)}`} target="_blank" rel="noopener noreferrer" size="small" sx={{ alignSelf: 'flex-start' }}>Inspect deployment changes</Button>
          <Typography variant="body2">Apps using this runtime: {review.affectedApps.join(', ')}.</Typography>
          <Alert severity="info">Applying may restart this runtime. App activation remains separate. External OAuth or email-link redirect settings must be configured with your identity provider if used.</Alert>
          {!release ? <FormControlLabel control={<Checkbox checked={confirmed} onChange={event => setConfirmed(event.target.checked)} disabled={pending} />} label="I approve publishing and applying these changes to this deployment." /> : null}
        </> : null}
        {currentRelease ? <Alert severity={verified ? 'success' : currentRelease.status === 'FAILED' ? 'error' : 'info'}>
          {verified ? 'Deployment update verified. Use it for the shared app connection when ready.' : `${currentRelease.status} · ${currentRelease.currentStepDescription ?? 'Waiting for deployment verification.'}`}
        </Alert> : null}
        {error || observed.error ? <Alert severity="error">{error ?? (observed.error instanceof Error ? observed.error.message : 'Release status unavailable.')}</Alert> : null}
      </Stack></DialogContent>
      <DialogActions><Button disabled={pending} onClick={() => setOpen(false)}>Close</Button>
        {verified ? <Button variant="contained" disabled={pending} onClick={() => void connect()}>Use verified update</Button>
          : !release ? <Button variant="contained" disabled={pending || !review || !confirmed} onClick={() => void apply()}>Publish and apply update</Button> : null}
      </DialogActions>
    </Dialog>
  </>
}
