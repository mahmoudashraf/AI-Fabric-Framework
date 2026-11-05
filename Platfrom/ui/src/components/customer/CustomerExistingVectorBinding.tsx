import { deploymentTerm } from '../../deploymentTerminology'
import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, CircularProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import StorageOutlined from '@mui/icons-material/StorageOutlined'
import LinkRounded from '@mui/icons-material/LinkRounded'
import {
  bindCustomerExistingVector, fetchCustomerExistingVectorBinding, fetchDeploymentReleases, fetchDeploymentTargetOptions,
  fetchDeploymentVersions, fetchDeploymentVersionManifest, fetchDeploymentWorkspace, fetchPlatformAuthSession,
  PlatformApiError, type DeploymentVersionSummary, type PlatformAuthSessionSummary,
} from '../../api/platformApi'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { isCustomerSession } from '../../auth/customerSession'
import { checkedExistingVectorBinding, existingVectorAuth, existingVectorRequest, type ExistingVectorForm } from './existingVectorBindingForm'
import { connectionEnvironments, connectionEnvironmentSelection } from './customerEngageConnection'

const emptyForm = (): ExistingVectorForm => ({ clusterId: '', projectId: '', regionId: '', expectedDeploymentOption: 'Free', token: '', username: '', password: '' })
const errorText = (error: unknown) => error instanceof Error ? error.message : 'The vector binding could not be read or saved.'

export function CustomerExistingVectorBinding({ deploymentId }: { deploymentId: string }) {
  const { session } = usePlatformAuth()
  if (!isCustomerSession(session) || !deploymentId) return null
  const scope = JSON.stringify([session.actorId, session.customerId, session.authenticationMode, deploymentId])
  return <ExistingVectorScope key={scope} scope={scope} session={session} deploymentId={deploymentId} />
}

function ExistingVectorScope({ scope, session, deploymentId }: { scope: string; session: PlatformAuthSessionSummary; deploymentId: string }) {
  const [open, setOpen] = useState(false)
  return <Box component="section" aria-label="Existing vector service" sx={{ pb: 3, borderBottom: 1, borderColor: 'divider' }}>
    <Button startIcon={<StorageOutlined />} aria-expanded={open} onClick={() => setOpen(value => !value)}>Existing vector service</Button>
    {open ? <VectorChoices scope={scope} session={session} deploymentId={deploymentId} /> : null}
  </Box>
}

function VectorChoices({ scope, session, deploymentId }: { scope: string; session: PlatformAuthSessionSummary; deploymentId: string }) {
  const [environment, setEnvironment] = useState('')
  const [versionId, setVersionId] = useState('')
  const workspace = useQuery({ queryKey: ['customer-existing-vector', scope, 'workspace'], retry: false,
    queryFn: async () => {
      const result = await fetchDeploymentWorkspace(deploymentId)
      if (result.deployment.id !== deploymentId || result.deployment.binding?.customerId !== session.customerId) {
        throw new Error(`The ${deploymentTerm.lowercase} is not available in the current customer account.`)
      }
      return result
    } })
  const targets = useQuery({ queryKey: ['customer-existing-vector', scope, 'targets'], retry: false,
    enabled: workspace.isSuccess, queryFn: () => fetchDeploymentTargetOptions(deploymentId) })
  const releases = useQuery({ queryKey: ['customer-existing-vector', scope, 'releases'], retry: false,
    enabled: workspace.isSuccess, queryFn: () => fetchDeploymentReleases(deploymentId) })
  const versions = useQuery({ queryKey: ['customer-existing-vector', scope, 'versions'], retry: false,
    enabled: workspace.isSuccess, queryFn: () => fetchDeploymentVersions(deploymentId) })
  const selected = versions.data?.find(value => value.id === versionId && value.deploymentId === deploymentId && value.status === 'PUBLISHED')
  const environments = connectionEnvironments(targets.data ?? [])
  const choices = ['staging', 'production'].map(name => ({ name,
    label: environments.find(value => value.name === name)?.label ?? (name === 'staging' ? 'Staging' : 'Production') }))
  const selection = connectionEnvironmentSelection(environment, targets.data ?? [],
    (releases.data ?? []).filter(value => value.deploymentId === deploymentId))
  const target = targets.isSuccess && releases.isSuccess
    ? targets.data.find(value => value.id === selection.targetId && value.active && value.environmentName === environment) : undefined
  const selectionIssue = environment && targets.isSuccess && releases.isSuccess && !target
    ? targets.data.some(value => value.active && value.environmentName === environment)
      ? `The vector target for this environment is ambiguous. An exact ${deploymentTerm.lowercase} target must be resolved before binding.`
      : 'No active vector target is available for this environment.' : ''
  const manifest = useQuery({ queryKey: ['customer-existing-vector', scope, 'manifest', selected?.id, selected?.configHash], retry: false,
    enabled: Boolean(selected), queryFn: () => fetchDeploymentVersionManifest(deploymentId, selected!.id) })
  const error = [workspace, targets, releases, versions, manifest].find(value => value.isError)?.error
  let authType: 'TOKEN' | 'USERNAME_PASSWORD' | null = null, configError = ''
  if (manifest.data && selected) {
    try { authType = existingVectorAuth(manifest.data, deploymentId, selected.id) }
    catch (error) { configError = errorText(error) }
  }
  return <Stack spacing={2} sx={{ mt: 2, minWidth: 0 }}>
    <Typography variant="subtitle2">Existing Milvus / Zilliz connection</Typography>
    <Typography variant="body2" color="text.secondary">Retained external cluster. Database: default. Management readback uses the Platform's configured Zilliz access.</Typography>
    {error ? <Alert severity="error">{errorText(error)}</Alert> : null}
    {workspace.isPending ? <CircularProgress size={20} aria-label="Loading vector binding scope" /> : null}
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <TextField fullWidth select label="Vector target" value={environment} onChange={event => setEnvironment(event.target.value)} disabled={!targets.isSuccess || !releases.isSuccess}>
        {choices.map(value => <MenuItem key={value.name} value={value.name}>{value.label}</MenuItem>)}
      </TextField>
      <TextField fullWidth select label="Published vector version" value={selected?.id ?? ''} onChange={event => setVersionId(event.target.value)} disabled={!versions.isSuccess}>
        {(versions.data ?? []).filter(value => value.deploymentId === deploymentId && value.status === 'PUBLISHED').map(value => <MenuItem key={value.id} value={value.id}>{value.versionLabel}</MenuItem>)}
      </TextField>
    </Stack>
    {selectionIssue ? <Alert severity="warning">{selectionIssue}</Alert> : null}
    {versions.isSuccess && !versions.data.some(value => value.deploymentId === deploymentId && value.status === 'PUBLISHED') ? <Alert severity="info">No published version is available.</Alert> : null}
    {configError ? <Alert severity="error">{configError}</Alert> : null}
    {selected && manifest.isSuccess && !authType && !configError ? <Alert severity="info">This published version does not require a typed existing Milvus binding.</Alert> : null}
    {selected && target && authType && !error ? <VectorForm key={`${scope}:${target.id}:${selected.id}:${selected.configHash}:${authType}`}
      scope={scope} session={session} deploymentId={deploymentId} targetId={target.id} version={selected}
      environment={environment} authType={authType} canBind={workspace.data?.access.canAdmin ?? false} /> : null}
  </Stack>
}

function VectorForm({ scope, session, deploymentId, targetId, environment, version, authType, canBind }: {
  scope: string; session: PlatformAuthSessionSummary; deploymentId: string; targetId: string
  environment: string
  version: DeploymentVersionSummary; authType: 'TOKEN' | 'USERNAME_PASSWORD'; canBind: boolean
}) {
  const [form, setForm] = useState(emptyForm)
  const client = useQueryClient()
  const alive = useRef(true)
  useEffect(() => { alive.current = true; return () => { alive.current = false } }, [])
  const queryKey = ['customer-existing-vector', scope, 'binding', targetId]
  const binding = useQuery({ queryKey, retry: false, queryFn: async () => {
    try { return checkedExistingVectorBinding(await fetchCustomerExistingVectorBinding(deploymentId, targetId), deploymentId, targetId) }
    catch (error) { if (error instanceof PlatformApiError && error.status === 404) return null; throw error }
  } })
  const save = useMutation({ retry: false, gcTime: 0,
    mutationFn: async () => {
      const fields = { ...form }
      const [identity, workspace, targets, releases, versions] = await Promise.all([fetchPlatformAuthSession(), fetchDeploymentWorkspace(deploymentId),
        fetchDeploymentTargetOptions(deploymentId), fetchDeploymentReleases(deploymentId), fetchDeploymentVersions(deploymentId)])
      if (!alive.current || !isCustomerSession(identity) || identity.actorId !== session.actorId || identity.customerId !== session.customerId
        || identity.authenticationMode !== session.authenticationMode || workspace.deployment.id !== deploymentId
        || workspace.deployment.binding?.customerId !== session.customerId || !workspace.access.canAdmin
        || !targets.some(value => value.id === targetId && value.active && value.environmentName === environment)
        || connectionEnvironmentSelection(environment, targets, releases.filter(value => value.deploymentId === deploymentId)).targetId !== targetId
        || !versions.some(value => value.id === version.id && value.deploymentId === deploymentId && value.status === 'PUBLISHED' && value.configHash === version.configHash)) {
        throw new Error('The customer, target or published version changed. Reopen the connection before binding.')
      }
      const result = checkedExistingVectorBinding(await bindCustomerExistingVector(deploymentId, targetId,
        existingVectorRequest(fields, version.id, authType)), deploymentId, targetId)
      if (result.versionId !== version.id || result.authType !== authType || result.clusterId !== fields.clusterId.trim()) {
        throw new Error('The saved vector binding does not match the submitted connection. Reload its recorded state.')
      }
      return result
    },
    onSuccess: result => { if (alive.current) { setForm(emptyForm()); client.setQueryData(queryKey, result) } },
  })
  const disabled = !canBind || save.isPending || !binding.isSuccess || Boolean(binding.data)
  const field = (name: keyof ExistingVectorForm, value: string) => setForm(current => ({ ...current, [name]: value }))
  let complete = true
  try { existingVectorRequest(form, version.id, authType) } catch { complete = false }
  return <Stack spacing={2}>
    {binding.isPending ? <CircularProgress size={20} aria-label="Reading existing vector binding" /> : null}
    {binding.isError ? <Alert severity="error">{errorText(binding.error)}</Alert> : null}
    {binding.isSuccess && !binding.data ? <Alert severity="info">No vector binding is recorded for this target.</Alert> : null}
    {binding.data ? <Alert severity="info"><Typography variant="body2">Stored vector binding: {binding.data.bindingId}</Typography>
      <Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>{binding.data.endpointHost} / {binding.data.collectionPrefix}</Typography>
      <Typography variant="caption" display="block">Approved version: {binding.data.versionId}. Last checked: {binding.data.checkedAt}. This readback is not a new provider probe.</Typography></Alert> : null}
    {!canBind ? <Alert severity="info">This connection is read-only for your account.</Alert> : null}
    {save.isError ? <Alert severity="error">{errorText(save.error)}</Alert> : null}
    {!binding.data ? <>
      <TextField fullWidth label="Zilliz cluster ID" value={form.clusterId} disabled={disabled} onChange={event => field('clusterId', event.target.value)} />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField fullWidth label="Zilliz project ID" value={form.projectId} disabled={disabled} onChange={event => field('projectId', event.target.value)} />
        <TextField fullWidth label="Zilliz region ID" value={form.regionId} disabled={disabled} onChange={event => field('regionId', event.target.value)} />
      </Stack>
      <TextField fullWidth select label={`Existing ${deploymentTerm.lowercase} option`} value={form.expectedDeploymentOption} disabled={disabled}
        onChange={event => field('expectedDeploymentOption', event.target.value)}>
        {['Free', 'Serverless', 'Dedicated'].map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}
      </TextField>
      {authType === 'TOKEN' ? <TextField fullWidth label="Milvus serving token" type="password" autoComplete="new-password" value={form.token} disabled={disabled} onChange={event => field('token', event.target.value)} /> : <>
        <TextField fullWidth label="Milvus username" autoComplete="off" value={form.username} disabled={disabled} onChange={event => field('username', event.target.value)} />
        <TextField fullWidth label="Milvus password" type="password" autoComplete="new-password" value={form.password} disabled={disabled} onChange={event => field('password', event.target.value)} />
      </>}
      <Button startIcon={<LinkRounded />} variant="outlined" disabled={disabled || !complete} onClick={() => save.mutate()}>
        {save.isPending ? 'Checking and binding...' : 'Check and bind existing vector service'}
      </Button>
    </> : null}
  </Stack>
}
