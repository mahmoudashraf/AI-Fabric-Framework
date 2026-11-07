import { Alert, Button, CircularProgress, Stack, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { Link, Navigate } from 'react-router-dom'
import { fetchCustomerDeploymentPortfolio, fetchDeploymentTargetOptions, type DeploymentTargetProfileSummary } from '../../api/platformApi'
import { brainPath } from '../../brain/brainNavigation'

export function customerReleaseSetupPath(brainId: string, step: string, stagingTargetId: string,
  targets: DeploymentTargetProfileSummary[]): string | null {
  const candidates = targets.filter(target => target.active && (step === 'staging'
    ? target.environmentName === 'staging' && target.id === stagingTargetId
    : step === 'production' && target.environmentName === 'production'))
  return candidates.length === 1 ? `${brainPath(brainId, 'updates', candidates[0].id)}${step === 'production' ? '&panel=promotion' : ''}` : null
}

export function CustomerReleaseSetupRedirect({ customerId, brainId, step }: { customerId: string; brainId: string; step: 'staging' | 'production' }) {
  const portfolio = useQuery({ queryKey: ['customer-deployment-portfolio', customerId], queryFn: fetchCustomerDeploymentPortfolio, retry: false })
  const brain = portfolio.data?.find(item => item.deploymentId === brainId)
  const targets = useQuery({ queryKey: ['brain-release-destinations', brainId], queryFn: () => fetchDeploymentTargetOptions(brainId), enabled: Boolean(brain), retry: false })
  if (portfolio.isPending || (brain && targets.isPending)) return <CircularProgress aria-label="Opening Smart Brain updates" />
  if (portfolio.isError || targets.isError) return <Alert severity="error">{(portfolio.error || targets.error)?.message}<Button onClick={() => { void portfolio.refetch(); if (brain) void targets.refetch() }}>Retry</Button></Alert>
  if (!brain) return <Alert severity="error">This Smart Brain is not available in your customer workspace.</Alert>
  const path = customerReleaseSetupPath(brainId, step, brain.bootstrapTargetProfileId, targets.data ?? [])
  if (path) return <Navigate to={path} replace />
  const production = (targets.data ?? []).filter(target => target.active && target.environmentName === 'production')
  return <Stack spacing={2}><Typography variant="h5">Choose where to go live</Typography>
    {step === 'production' && production.length > 1 ? production.map(target => <Button key={target.id} component={Link}
      to={`${brainPath(brainId, 'updates', target.id)}&panel=promotion`}>{target.name}</Button>)
      : <Alert severity="warning">The authorized {step} destination is unavailable. No alternative environment was selected.</Alert>}
    <Button component={Link} to={brainPath(brainId)}>Back to Smart Brain</Button>
  </Stack>
}
