import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Autocomplete, MenuItem, Stack, TextField } from '@mui/material'
import { fetchAIWorkspaceInstallations, fetchCustomerDeploymentPortfolio, fetchVectorizationOverview } from '../../api/platformApi'
import type { SupportResource } from '../../api/supportApi'

type Choice = SupportResource & { label: string }
export function useSupportResources(customerId: string) {
  const deployments = useQuery({ queryKey: ['customer-deployment-portfolio', customerId], queryFn: fetchCustomerDeploymentPortfolio })
  const apps = useQuery({ queryKey: ['ai-workspace-installations', customerId], queryFn: () => fetchAIWorkspaceInstallations(customerId) })
  const choices: Choice[] = [
    ...(deployments.data || []).map(item => ({ type: 'DEPLOYMENT' as const, id: item.deploymentId, label: item.name })),
    ...(apps.data || []).filter(app => app.customerId === customerId).map(item => ({ type: 'ENGAGE_APP' as const, id: item.installationId, label: item.displayName })),
  ]
  return { deployments, apps, choices, label: (ref: SupportResource) => choices.find(item => sameResource(item, ref))?.label || ref.id }
}
export const sameResource = (a: SupportResource, b: SupportResource) => a.type === b.type && a.id === b.id
const group = { DEPLOYMENT: 'Deployments', ENGAGE_APP: 'Engage apps', CONNECTION: 'Connections' }

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
  return <Stack spacing={1.5}>
    <Autocomplete<Choice, true, false, false> multiple options={options} value={selected} disabled={disabled}
      isOptionEqualToValue={sameResource} getOptionLabel={option => option.label} groupBy={option => group[option.type]}
      getOptionDisabled={option => value.length >= 8 && !value.some(item => sameResource(item, option))}
      onChange={(_, next) => onChange(next.map(({ type, id }) => ({ type, id })))}
      renderInput={params => <TextField {...params} label="Affected resources (optional)" size="small" />} />
    {(resources.deployments.data?.length || 0) > 0 && <TextField select size="small" label="Connections from deployment" value={connectionDeployment}
      disabled={disabled} onChange={event => setConnectionDeployment(event.target.value)}>
      <MenuItem value="">None selected</MenuItem>
      {resources.deployments.data?.map(item => <MenuItem key={item.deploymentId} value={item.deploymentId}>{item.name}</MenuItem>)}
    </TextField>}
    {(resources.deployments.isError || resources.apps.isError || connection.isError) && <Alert severity="warning">Some resource choices are unavailable. You can still request documentation help.</Alert>}
  </Stack>
}
