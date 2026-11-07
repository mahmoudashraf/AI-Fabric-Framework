import { Alert, Button, CircularProgress } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { fetchCustomerDeploymentPortfolio, fetchDeploymentReleases } from '../../api/platformApi'
import { brainPath } from '../../brain/brainNavigation'

export function customerBrainManagePath(brainId: string, search: string): string {
  const source = new URLSearchParams(search)
  const params = new URLSearchParams()
  for (const key of ['targetProfileId', 'releaseId']) {
    if (source.has(key)) params.set(key, source.get(key)!)
  }
  return `${brainPath(brainId)}${params.size ? `?${params}` : ''}`
}

export function CustomerBrainManageRedirect({ customerId, brainId }: { customerId: string; brainId: string }) {
  const location = useLocation()
  const params = new URLSearchParams(location.search)
  const releaseId = params.get('releaseId')
  const portfolio = useQuery({ queryKey: ['customer-deployment-portfolio', customerId], queryFn: fetchCustomerDeploymentPortfolio, retry: false })
  const owned = portfolio.data?.some(item => item.deploymentId === brainId)
  const resolveReleaseTarget = Boolean(owned && releaseId && !params.get('targetProfileId'))
  const releases = useQuery({ queryKey: ['deployment-releases', brainId], queryFn: () => fetchDeploymentReleases(brainId), enabled: resolveReleaseTarget, retry: false })
  if (portfolio.isPending || (resolveReleaseTarget && releases.isPending)) return <CircularProgress aria-label="Opening Smart Brain workspace" />
  if (portfolio.isError || (resolveReleaseTarget && releases.isError)) return <Alert severity="error">{(portfolio.error ?? releases.error)?.message}<Button onClick={() => { void portfolio.refetch(); if (resolveReleaseTarget) void releases.refetch() }}>Retry</Button></Alert>
  if (!portfolio.data.some(item => item.deploymentId === brainId)) return <Alert severity="error">
    This Smart Brain is not available in your customer workspace. <Button component={Link} to="/deployments">Back to Smart Brains</Button>
  </Alert>
  if (resolveReleaseTarget) {
    const release = releases.data?.find(item => item.id === releaseId && item.deploymentId === brainId)
    if (!release?.targetProfileId) return <Alert severity="error">The requested release is unavailable. No alternative release was selected.</Alert>
    params.set('targetProfileId', release.targetProfileId)
  }
  return <Navigate to={customerBrainManagePath(brainId, params.toString())} replace />
}
