import { deploymentTerm } from '../../deploymentTerminology'
import { Alert, Box, Chip, IconButton, Link, Stack, Tooltip, Typography } from '@mui/material'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { useQuery } from '@tanstack/react-query'
import { Link as RouterLink } from 'react-router-dom'
import { fetchCustomerEnvironmentQuota } from '../../api/platformApi'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'

export function CustomerEnvironmentQuota() {
  const { session } = usePlatformAuth()
  const query = useQuery({ queryKey: ['customer-environment-quota', session?.customerId],
    queryFn: fetchCustomerEnvironmentQuota, staleTime: 0, refetchInterval: 30000 })
  const quota = query.data
  if (query.isError) return <Alert severity="warning">Infrastructure allowance could not be loaded. Starting an environment is still checked by the server.</Alert>
  if (!quota) return <Typography variant="body2" color="text.secondary">Loading infrastructure allowance...</Typography>
  if (quota.customerId !== session?.customerId) return <Alert severity="error">Infrastructure allowance does not match this workspace.</Alert>
  return <Box component="section" aria-label="Infrastructure allowance" sx={{ py: 1.5, borderTop: 1, borderBottom: 1, borderColor: 'divider' }}>
    <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap">
      <Typography variant="subtitle2">Infrastructure environments</Typography>
      <Chip size="small" variant="outlined" color={quota.available === 0 ? 'warning' : 'default'} label={`${quota.used} / ${quota.limit} in use`} />
      <Tooltip title="Refresh infrastructure allowance"><IconButton aria-label="Refresh infrastructure allowance" size="small" disabled={query.isFetching} onClick={() => void query.refetch()}><RefreshRounded fontSize="small" /></IconButton></Tooltip>
    </Stack>
    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
      {quota.available === 0 ? 'Update an existing environment, or remove its owned infrastructure before starting another. ' : 'Staging and production each use one environment. '}
      Upgrades reuse their slot. Drafts and shared Engage apps do not count. Stopped infrastructure counts until removed.
    </Typography>
    {quota.environments.length ? <Box component="details" sx={{ mt: 1 }}>
      <Typography component="summary" variant="body2" sx={{ cursor: 'pointer' }}>View environments in use</Typography>
      <Stack component="ul" spacing={1} sx={{ my: 1, pl: 2.5 }}>{quota.environments.map(item => <Box component="li" key={`${item.deploymentId}:${item.targetProfileId}`}>
        <Link component={RouterLink} to={`/revisions?${new URLSearchParams({ deploymentId: item.deploymentId, targetProfileId: item.targetProfileId })}`}>
          {item.deploymentName} · {item.environment || item.targetProfileId}
        </Link>
        <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>{item.ownedResourcesPresent ? 'Infrastructure retained' : `${deploymentTerm.singular} reserved`}</Typography>
      </Box>)}</Stack>
    </Box> : null}
  </Box>
}
