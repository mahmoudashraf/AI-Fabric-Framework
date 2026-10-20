import { Alert, Button, Checkbox, FormControlLabel, MenuItem, Stack, TextField, Typography } from '@mui/material'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { bindCustomerAiStateStorage, fetchCustomerAiStateStorage,
  type CustomerDeploymentSummary, type CustomerOfferSummary, type DeploymentVersionSummary } from '../../api/platformApi'
import { checkedAiStateBinding, customerAiStateBindingRequest, customerAiStateBindingScope, customerAiStateSlot,
  type AiStateBindingScope, type AiStateConnectionForm } from './customerAiStateStorage'

const emptyForm = (): AiStateConnectionForm => ({ host: '', port: 5432, databaseName: '', runtimeSchema: '',
  connectorSchema: '', username: '', password: '', migrationAuthorized: false })

export function CustomerAiStateStorageBinding({ actorId, customerId, offer, deployment, version }: {
  actorId: string; customerId: string; offer: CustomerOfferSummary
  deployment: CustomerDeploymentSummary; version: DeploymentVersionSummary
}) {
  const targets = offer.targets.filter((target) => target.capabilities.includes('READ') && target.capabilities.includes('BINDING_REPLACE'))
  const [targetId, setTargetId] = useState(deployment.bootstrapTargetProfileId)
  const selected = targets.find((target) => target.id === targetId)
  if (!customerAiStateSlot(offer)) return <Alert severity="warning">No reviewed customer PostgreSQL binding slot is available.</Alert>
  const scope: AiStateBindingScope = { customerId, offer, deployment, version, targetProfileId: selected?.id ?? '' }
  const key = JSON.stringify([actorId, customerId, offer.id, offer.fingerprint, offer.entitlement.revision,
    deployment.deploymentId, version.id, version.configHash, selected?.id])
  return <Stack spacing={2}>
    <Typography variant="subtitle2">Customer AI-state PostgreSQL</Typography>
    <TextField select label="AI-state target" value={selected?.id ?? ''} onChange={(event) => setTargetId(event.target.value)}>
      {targets.map((target) => <MenuItem key={target.id} value={target.id}>{target.id} · {target.intent}</MenuItem>)}
    </TextField>
    {selected ? <BindingForm key={key} scope={scope} scopeKey={key} /> : <Alert severity="warning">Select an entitled binding target.</Alert>}
  </Stack>
}

function BindingForm({ scope, scopeKey }: { scope: AiStateBindingScope; scopeKey: string }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState(emptyForm)
  const command = useRef<{ identity: string; key: string } | null>(null)
  const queryKey = ['customer-ai-state-storage', scopeKey]
  const binding = useQuery({ queryKey, retry: false,
    queryFn: async () => checkedAiStateBinding(scope, await fetchCustomerAiStateStorage(scope.offer.id,
      scope.deployment.deploymentId, customerAiStateBindingScope(scope))) })
  const save = useMutation({ gcTime: 0,
    mutationFn: async () => {
      const fields = { ...form }
      const identity = JSON.stringify(fields)
      if (command.current?.identity !== identity) command.current = { identity, key: crypto.randomUUID() }
      const result = await bindCustomerAiStateStorage(scope.offer.id, scope.deployment.deploymentId,
        customerAiStateBindingRequest(scope, fields, command.current.key))
      return checkedAiStateBinding(scope, result.binding)
    },
    onSuccess: async (result) => {
      command.current = null
      setForm(emptyForm())
      queryClient.setQueryData(queryKey, result)
    },
  })
  const field = (name: keyof AiStateConnectionForm, value: string | number | boolean) => setForm((current) => ({ ...current, [name]: value }))
  return <Stack spacing={2}>
    <Typography variant="caption">{scope.version.versionLabel} · {scope.version.id}</Typography>
    {binding.data ? <Alert severity="success">PostgreSQL binding verified · {binding.data.bindingId} · retained externally</Alert> : null}
    {binding.error ? <Alert severity="info">No verified PostgreSQL binding was read back for this target and version.</Alert> : null}
    {save.isError ? <Alert severity="error">{save.error instanceof Error ? save.error.message : 'PostgreSQL binding failed.'}</Alert> : null}
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <TextField fullWidth label="PostgreSQL host" value={form.host} onChange={(e) => field('host', e.target.value)} disabled={save.isPending} />
      <TextField label="PostgreSQL port" type="number" value={form.port} onChange={(e) => field('port', Number(e.target.value))} disabled={save.isPending} />
    </Stack>
    <TextField label="AI-state database" value={form.databaseName} onChange={(e) => field('databaseName', e.target.value)} disabled={save.isPending} />
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <TextField fullWidth label="Runtime AI schema" value={form.runtimeSchema} onChange={(e) => field('runtimeSchema', e.target.value)} disabled={save.isPending} />
      <TextField fullWidth label="Connector AI schema" value={form.connectorSchema} onChange={(e) => field('connectorSchema', e.target.value)} disabled={save.isPending} />
    </Stack>
    <TextField label="PostgreSQL username" value={form.username} onChange={(e) => field('username', e.target.value)} autoComplete="off" disabled={save.isPending} />
    <TextField label="PostgreSQL password" type="password" value={form.password} onChange={(e) => field('password', e.target.value)} autoComplete="new-password" disabled={save.isPending} />
    <FormControlLabel label="Authorize migrations to dedicated AI schemas" control={<Checkbox checked={form.migrationAuthorized}
      onChange={(e) => field('migrationAuthorized', e.target.checked)} disabled={save.isPending} />} />
    <Button startIcon={<SaveRoundedIcon />} variant="outlined" disabled={save.isPending || !form.host || !form.databaseName
      || !form.runtimeSchema || !form.connectorSchema || !form.username || !form.password || !form.migrationAuthorized}
      onClick={() => save.mutate()}>Bind AI-state PostgreSQL</Button>
  </Stack>
}
