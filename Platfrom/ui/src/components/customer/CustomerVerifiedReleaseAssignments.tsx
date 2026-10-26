import { useRef, useState } from 'react'
import { Alert, Button, Link, MenuItem, Stack, TextField, Typography } from '@mui/material'
import PublishedWithChangesOutlined from '@mui/icons-material/PublishedWithChangesOutlined'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchAIWorkspaceInstallations, fetchPlatformConsumers, updatePlatformConsumerBinding,
  type DeploymentReleaseSummary, type PlatformConsumerSummary } from '../../api/platformApi'
import { safeCustomerAppUrl } from './customerResourceModels'

export function CustomerVerifiedReleaseAssignments({ customerId, deploymentId, targetProfileId, release }: {
  customerId: string
  deploymentId: string
  targetProfileId: string
  release: DeploymentReleaseSummary | null
}) {
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState('')
  const intent = useRef<{ scope: string; operationKey: string } | null>(null)
  const consumers = useQuery({ queryKey: ['platform-consumers', customerId],
    queryFn: () => fetchPlatformConsumers(customerId), enabled: Boolean(customerId && targetProfileId), retry: false })
  const apps = useQuery({ queryKey: ['ai-workspace-installations', customerId],
    queryFn: () => fetchAIWorkspaceInstallations(customerId), enabled: Boolean(customerId && targetProfileId), retry: false })
  const assignments = (consumers.data ?? []).filter(consumer => consumer.customerId === customerId
    && consumer.boundDeploymentId === deploymentId)
  const selected = selectedId ? assignments.find(consumer => consumer.consumerId === selectedId)
    : assignments.length === 1 ? assignments[0] : undefined
  const verified = Boolean(release && release.deploymentId === deploymentId && release.targetProfileId === targetProfileId
    && release.status === 'APPLIED_VERIFIED' && release.verificationStatus === 'PASSED')
  const useRelease = useMutation({
    retry: false,
    mutationFn: async ({ consumer, verifiedRelease, operationKey }: {
      consumer: PlatformConsumerSummary; verifiedRelease: DeploymentReleaseSummary; operationKey: string
    }) => {
      const result = await updatePlatformConsumerBinding(customerId, consumer.consumerId, {
        deploymentId, targetProfileId, releaseId: verifiedRelease.id,
        expectedBindingRevision: consumer.bindingRevision, operationKey,
        reason: 'Customer selected a verified release for the existing app assignment.',
      })
      if (result.customerId !== customerId || result.consumerId !== consumer.consumerId
        || result.boundDeploymentId !== deploymentId || result.boundTargetProfileId !== targetProfileId
        || result.boundReleaseId !== verifiedRelease.id) throw new Error('App assignment readback did not match the selected release. Refresh before continuing.')
      return result
    },
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['platform-consumers', customerId] }),
        queryClient.invalidateQueries({ queryKey: ['ai-workspace-installations', customerId] }),
        queryClient.invalidateQueries({ queryKey: ['customer-deployment-portfolio', customerId] }),
      ])
    },
  })
  return <Stack component="section" aria-label="Existing app release assignment" spacing={1.5}>
    <Typography variant="h6">Existing apps</Typography>
    {!targetProfileId ? <Typography color="text.secondary">Select an apply target to view its app assignments.</Typography>
      : consumers.isLoading ? <Typography>Reading app assignments...</Typography>
      : consumers.isError ? <Alert severity="error">{consumers.error instanceof Error ? consumers.error.message : 'App assignments could not be read.'}</Alert>
      : !assignments.length ? <Typography color="text.secondary">No existing app assignment is bound to this deployment.</Typography>
      : <>
        <TextField select fullWidth label="App assignment" value={selected?.consumerId ?? ''}
          disabled={useRelease.isPending} onChange={event => { setSelectedId(event.target.value); useRelease.reset() }}>
          <MenuItem value="">Select an assignment</MenuItem>
          {assignments.map(consumer => <MenuItem key={consumer.consumerId} value={consumer.consumerId}>{consumer.displayName}</MenuItem>)}
        </TextField>
        {selected ? <Typography variant="body2">Current target: {selected.boundTargetProfileId ?? 'Unassigned'} / {selected.boundReleaseId ?? 'No release'}</Typography> : null}
        <Typography variant="body2">Selected target: {targetProfileId}</Typography>
        {release ? <Typography variant="body2">Release: {release.id} / {release.status} / {release.verificationStatus}</Typography> : null}
        {selected && selected.boundTargetProfileId !== targetProfileId ? <Alert severity="warning">Using this release switches the app to the selected environment. Existing conversations stay on their original deployment.</Alert> : null}
        {!verified ? <Alert severity="info">The target release must pass verification before an app can use it.</Alert> : null}
        {selected?.boundReleaseId === release?.id && verified ? <Alert severity="success">This app assignment already uses the verified release.</Alert> : null}
        <Button variant="outlined" startIcon={<PublishedWithChangesOutlined />}
          disabled={!verified || !selected || selected.boundReleaseId === release?.id || consumers.isFetching || useRelease.isPending}
          onClick={() => {
            if (!verified || !release || !selected) return
            const scope = JSON.stringify([customerId, deploymentId, targetProfileId, release.id, selected.consumerId, selected.bindingRevision])
            if (intent.current?.scope !== scope) intent.current = { scope, operationKey: crypto.randomUUID() }
            useRelease.mutate({ consumer: selected, verifiedRelease: release, operationKey: intent.current.operationKey })
          }}>Use verified release</Button>
      </>}
    {useRelease.isError ? <Alert severity="error">{useRelease.error instanceof Error ? useRelease.error.message : 'App assignment update failed.'}</Alert> : null}
    {useRelease.isSuccess ? <Alert severity="success">App assignment now uses verified release {useRelease.data.boundReleaseId}. The existing app address is unchanged.</Alert> : null}
    {apps.isError ? <Alert severity="warning">App addresses could not be refreshed.</Alert> : null}
    {selected ? (apps.data ?? []).filter(app => app.customerId === customerId && app.consumerId === selected.consumerId)
      .flatMap(app => { const url = safeCustomerAppUrl(app.appUrl); return url ? [<Link key={app.installationId} href={url} target="_blank" rel="noopener noreferrer">{url}</Link>] : [] }) : null}
  </Stack>
}
