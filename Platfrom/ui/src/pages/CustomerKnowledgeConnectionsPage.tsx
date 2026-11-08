import { deploymentTerm } from '../deploymentTerminology'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Alert, Box, Button, Chip, CircularProgress, FormControlLabel, Stack, Switch, Typography } from '@mui/material'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import OpenInNewOutlined from '@mui/icons-material/OpenInNewOutlined'
import { fetchDeploymentDesk, enableDeploymentDesk, type DeploymentTargetReference } from '../api/platformApi'
import { safeCustomerAppUrl } from '../components/customer/customerResourceModels'
import { CustomerBrainTaskRedirect } from '../brain/CustomerBrainTaskRedirect'

// Legacy entry points are navigation adapters, not a second set of editors.
export function CustomerKnowledgeConnectionsPage({ mode: _mode }: { mode: 'knowledge' | 'connections' | 'human' }) {
  return <CustomerBrainTaskRedirect />
}

export function CustomerDeskActivation({ target, onReady, onBusyChange }: { target: DeploymentTargetReference; onReady: (releaseId: string) => void; onBusyChange?: (busy: boolean) => void }) {
  const queryClient = useQueryClient()
  const [enableReviews, setEnableReviews] = useState(false)
  const key = ['deployment-desk', target.deploymentId, target.targetProfileId, target.releaseId]
  const desk = useQuery({ queryKey: key, queryFn: () => fetchDeploymentDesk(target), retry: false,
    refetchInterval: query => ['PREPARING', 'APPLYING'].includes(query.state.data?.status ?? '') ? 5000 : false })
  const activate = useMutation({ mutationFn: (scope: DeploymentTargetReference) => enableDeploymentDesk(scope, `desk-${crypto.randomUUID()}`, enableReviews),
    onSuccess: (value, scope) => {
      queryClient.setQueryData(['deployment-desk', scope.deploymentId, scope.targetProfileId, scope.releaseId], value)
      void queryClient.invalidateQueries({ queryKey: ['deployment-releases', scope.deploymentId] })
    } })
  const value = desk.data
  useEffect(() => {
    if (value?.status === 'ENABLED' && value.releaseId && value.releaseId !== target.releaseId) {
      void queryClient.invalidateQueries({ queryKey: ['deployment-releases', target.deploymentId] })
      onReady(value.releaseId)
    }
  }, [value?.status, value?.releaseId, target.releaseId, target.deploymentId, queryClient, onReady])
  const busy = activate.isPending || value?.status === 'PREPARING' || value?.status === 'APPLYING'
  useEffect(() => {
    onBusyChange?.(busy)
    return () => onBusyChange?.(false)
  }, [onBusyChange, busy])
  const error = activate.error ?? desk.error
  const portal = safeCustomerAppUrl(value?.portalUrl ?? null)
  return <Box component="section" aria-label="LoomAI Desk" sx={{ borderBottom: 1, borderColor: 'divider', py: 2.5, mb: 2 }}>
    <Stack direction="row" alignItems="center" gap={1.5} flexWrap="wrap">
      <ForumOutlined color="primary" />
      <Box sx={{ flex: 1, minWidth: 220 }}><Typography variant="h6">LoomAI Desk</Typography>
        <Typography variant="body2" color="text.secondary">Human conversations and action reviews, with registered agents assigned to this {deploymentTerm.lowercase}.</Typography></Box>
      {value ? <Chip size="small" label={value.status === 'NOT_ENABLED' ? 'Not enabled' : value.status.split('_').join(' ')}
        color={value.status === 'ENABLED' ? 'success' : value.status === 'FAILED' ? 'error' : 'default'} /> : null}
      {portal && value?.status === 'ENABLED' ? <Button component="a" href={portal} target="_blank" rel="noopener noreferrer"
        variant="contained" startIcon={<OpenInNewOutlined />}>Open LoomAI Desk</Button> : null}
      {value?.status === 'NOT_ENABLED' ? <Button variant="contained" startIcon={busy ? <CircularProgress size={16} /> : <ForumOutlined />}
        disabled={!value.available || busy} onClick={() => activate.mutate({ ...target })}>Enable LoomAI Desk</Button> : null}
    </Stack>
    {value?.status === 'NOT_ENABLED' ? <FormControlLabel sx={{ mt: 1 }}
      control={<Switch checked={enableReviews} disabled={busy} onChange={event => setEnableReviews(event.target.checked)} />}
      label="Enable action reviews for separately assigned reviewers" /> : null}
    {busy ? <Alert severity="info" sx={{ mt: 2 }}>Applying and verifying the support integration. Your {deploymentTerm.lowercase} and app address stay the same.</Alert> : null}
    {value && !value.available ? <Alert severity="info" sx={{ mt: 2 }}>LoomAI Desk is not configured for this target environment yet.</Alert> : null}
    {error ? <Alert severity="error" sx={{ mt: 2 }}>{error instanceof Error ? error.message : 'Desk could not be loaded.'}</Alert> : null}
    {value?.errorMessage ? <Alert severity="error" sx={{ mt: 2 }}>{value.errorMessage}</Alert> : null}
  </Box>
}
