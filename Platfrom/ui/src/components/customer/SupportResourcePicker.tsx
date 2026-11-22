import { deploymentTerm } from '../../deploymentTerminology'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Autocomplete, Box, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { fetchAIWorkspaceInstallations, fetchDeploymentOverviews, fetchVectorizationOverview } from '../../api/platformApi'
import type { SupportResource } from '../../api/supportApi'

type Choice = SupportResource & { label: string; detail?: string }
const kind = { DEPLOYMENT: deploymentTerm.singular, ENGAGE_APP: 'Engage app', CONNECTION: 'Connection' }
const appAddress = (value: string | null) => {
  try { return value ? new URL(value).hostname : '' } catch { return '' }
}
export function useSupportResources(customerId: string) {
  const deployments = useQuery({ queryKey: ['support-resource-deployments', customerId],
    queryFn: async () => (await fetchDeploymentOverviews()).filter(item => item.archivedAt == null) })
  const apps = useQuery({ queryKey: ['ai-workspace-installations', customerId], queryFn: () => fetchAIWorkspaceInstallations(customerId) })
  const currentDeploymentIds = new Set((deployments.data || []).map(item => item.id))
  const choices: Choice[] = [
    ...(deployments.data || []).map(item => ({ type: 'DEPLOYMENT' as const, id: item.id, label: item.name,
      detail: [kind.DEPLOYMENT, item.environment].filter(Boolean).join(' - ') })),
    ...(apps.data || []).filter(app => app.customerId === customerId
      && (!app.deploymentId || currentDeploymentIds.has(app.deploymentId)))
      .map(item => ({ type: 'ENGAGE_APP' as const, id: item.installationId, label: item.displayName,
        detail: [item.deliveryMode === 'embedded' ? 'Website widget' : item.deliveryMode === 'standalone' ? 'Standalone app' : kind.ENGAGE_APP,
          item.status === 'ACTIVE' ? 'Active' : item.status === 'DRAFT' ? 'Draft' : item.status === 'DISABLED' ? 'Disabled' : '',
          appAddress(item.appUrl), deployments.data?.find(brain => brain.id === item.deploymentId)?.name].filter(Boolean).join(' - ') })),
  ]
  return { deployments, apps, choices, label: (ref: SupportResource) => choices.find(item => sameResource(item, ref))?.label || ref.id }
}
export const sameResource = (a: SupportResource, b: SupportResource) => a.type === b.type && a.id === b.id
const group = { DEPLOYMENT: deploymentTerm.plural, ENGAGE_APP: 'Engage apps', CONNECTION: 'Connections' }

export function SupportResourcePicker({ customerId, value, onChange, disabled = false }: {
  customerId: string; value: SupportResource[]; onChange(value: SupportResource[]): void; disabled?: boolean
}) {
  const resources = useSupportResources(customerId)
  const [connectionDeployment, setConnectionDeployment] = useState('')
  const connection = useQuery({ queryKey: ['support-resource-connection', customerId, connectionDeployment],
    queryFn: () => fetchVectorizationOverview(connectionDeployment), enabled: Boolean(connectionDeployment) })
  const source = connection.data?.sourceConnection
  const options = [...resources.choices, ...(source ? [{ type: 'CONNECTION' as const, id: source.id, label: source.name }] : [])]
  const selected = value.map(ref => options.find(option => sameResource(option, ref)) || { ...ref, label: ref.id })
  const duplicateName = (option: Choice) => options.filter(item => item.label === option.label).length > 1
  const choiceLabel = (option: Choice) => duplicateName(option)
    ? `${option.label} (${kind[option.type]} - ${option.id.slice(-8)})` : option.label
  return <Stack spacing={1.5}>
    <Autocomplete<Choice, true, false, false> multiple options={options} value={selected} disabled={disabled}
      isOptionEqualToValue={sameResource} getOptionLabel={choiceLabel} groupBy={option => group[option.type]}
      renderOption={(props, option) => {
        const { key: _key, ...attributes } = props
        return <Box component="li" {...attributes} key={`${option.type}:${option.id}`} aria-label={choiceLabel(option)}
          aria-description={option.detail || kind[option.type]}
          data-support-resource-id={option.id} data-support-resource-type={option.type}>
          <Box sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
            <Typography variant="body2">{option.label}</Typography>
            <Typography variant="caption" color="text.secondary">{option.detail || kind[option.type]}
              {duplicateName(option) ? ` - ${option.id.slice(-8)}` : ''}</Typography>
          </Box>
        </Box>
      }}
      getOptionDisabled={option => value.length >= 8 && !value.some(item => sameResource(item, option))}
      onChange={(_, next) => onChange(next.map(({ type, id }) => ({ type, id })))}
      renderInput={params => <TextField {...params} label="Affected resources (optional)" size="small" />} />
    {(resources.deployments.data?.length || 0) > 0 && <TextField select size="small" label={`Connections from ${deploymentTerm.lowercase}`} value={connectionDeployment}
      disabled={disabled} onChange={event => setConnectionDeployment(event.target.value)}>
      <MenuItem value="">None selected</MenuItem>
      {resources.deployments.data?.map(item => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
    </TextField>}
    {(resources.deployments.isError || resources.apps.isError || connection.isError) && <Alert severity="warning">Some resource choices are unavailable. You can still request documentation help.</Alert>}
  </Stack>
}
