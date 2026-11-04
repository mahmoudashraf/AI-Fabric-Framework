import { useQuery } from '@tanstack/react-query'
import { Alert, MenuItem, Stack, TextField } from '@mui/material'
import { useLocation, useNavigate } from 'react-router-dom'
import { fetchCustomerDeploymentPortfolio, fetchDeploymentReleases, fetchDeploymentTargetOptions } from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { HumanReviewPage } from './HumanReviewPage'

export function CustomerActionReviewsPage() {
  const { session } = usePlatformAuth()
  if (!isCustomerSession(session)) {
    return <Alert severity="error">A customer administrator session is required.</Alert>
  }
  return <CustomerActionReviewReads key={`${session.actorId}:${session.customerId}`} customerId={session.customerId} />
}

function CustomerActionReviewReads({ customerId }: { customerId: string }) {
  const location = useLocation()
  const navigate = useNavigate()
  const portfolio = useQuery({ queryKey: ['customer-deployment-portfolio', customerId], queryFn: fetchCustomerDeploymentPortfolio })
  const params = new URLSearchParams(location.search)
  const requestedId = params.get('deploymentId')
  const deployment = portfolio.data?.find(item => item.deploymentId === requestedId)
  const releases = useQuery({ queryKey: ['deployment-releases', deployment?.deploymentId],
    queryFn: () => fetchDeploymentReleases(deployment!.deploymentId), enabled: Boolean(deployment) })
  const targets = useQuery({ queryKey: ['deployment-target-options', deployment?.deploymentId],
    queryFn: () => fetchDeploymentTargetOptions(deployment!.deploymentId), enabled: Boolean(deployment) })
  const available = (targets.data ?? []).filter(target => target.active && (releases.data ?? []).some(release =>
    release.targetProfileId === target.id && release.provisioningStatus === 'ACTIVE'
    && release.status === 'APPLIED_VERIFIED' && release.verificationStatus === 'PASSED'))
  const target = available.find(item => item.id === params.get('targetProfileId'))
  return <Stack spacing={3}>
    <TextField select size="small" label="Review deployment" value={deployment?.deploymentId ?? ''} onChange={event => {
      navigate({ pathname: location.pathname, search: new URLSearchParams({ deploymentId: event.target.value }).toString() })
    }} sx={{ maxWidth: 420 }}>
      <MenuItem value="">Select deployment</MenuItem>{(portfolio.data ?? []).map(item => <MenuItem key={item.deploymentId} value={item.deploymentId}>{item.name}</MenuItem>)}
    </TextField>
    {deployment ? <TextField select size="small" label="Review environment" value={target?.id ?? ''}
      disabled={targets.isPending || releases.isPending} onChange={event => {
        const next = new URLSearchParams(location.search)
        if (event.target.value) next.set('targetProfileId', event.target.value)
        else next.delete('targetProfileId')
        navigate({ pathname: location.pathname, search: next.toString() })
      }} sx={{ maxWidth: 420 }}>
      <MenuItem value="">Select environment</MenuItem>{available.map(item => <MenuItem key={item.id} value={item.id}>
        {item.environmentName === 'production' ? 'Production' : item.environmentName === 'staging' ? 'Staging' : item.environmentName}
        {available.filter(other => other.environmentName === item.environmentName).length > 1 ? ` / ${item.name}` : ''}
      </MenuItem>)}
    </TextField> : null}
    {portfolio.isError ? <Alert severity="error">Review scope could not be loaded.</Alert> : null}
    {targets.isError || releases.isError ? <Alert severity="error">Review environments could not be loaded.</Alert> : null}
    {requestedId && portfolio.isSuccess && !deployment ? <Alert severity="error">The requested review deployment is unavailable. No other deployment was selected.</Alert> : null}
    {!requestedId ? <Alert severity="info">Select a deployment to review its governed action proposals.</Alert> : null}
    {deployment && targets.isSuccess && releases.isSuccess && !available.length ? <Alert severity="info">No verified environment is available for this deployment.</Alert> : null}
    {deployment && params.get('targetProfileId') && targets.isSuccess && releases.isSuccess && !target ? <Alert severity="error">The requested review environment is unavailable. No other environment was selected.</Alert> : null}
    {deployment && target ? <HumanReviewPage key={`${customerId}:${deployment.deploymentId}:${target.id}`} title="Action reviews" targetProfileId={target.id} /> : null}
  </Stack>
}
