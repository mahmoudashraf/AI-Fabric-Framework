import { useQuery } from '@tanstack/react-query'
import { Alert, MenuItem, Stack, TextField } from '@mui/material'
import { useLocation, useNavigate } from 'react-router-dom'
import { fetchCustomerDeploymentPortfolio } from '../api/platformApi'
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
  return <Stack spacing={3}>
    <TextField select size="small" label="Review deployment" value={deployment?.deploymentId ?? ''} onChange={event => {
      navigate({ pathname: location.pathname, search: new URLSearchParams({ deploymentId: event.target.value }).toString() })
    }} sx={{ maxWidth: 420 }}>
      <MenuItem value="">Select deployment</MenuItem>{(portfolio.data ?? []).map(item => <MenuItem key={item.deploymentId} value={item.deploymentId}>{item.name}</MenuItem>)}
    </TextField>
    {portfolio.isError ? <Alert severity="error">Review scope could not be loaded.</Alert> : null}
    {requestedId && portfolio.isSuccess && !deployment ? <Alert severity="error">The requested review deployment is unavailable. No other deployment was selected.</Alert> : null}
    {!requestedId ? <Alert severity="info">Select a deployment to review its governed action proposals.</Alert> : null}
    {deployment ? <HumanReviewPage key={`${customerId}:${deployment.deploymentId}`} title="Action reviews" /> : null}
  </Stack>
}
