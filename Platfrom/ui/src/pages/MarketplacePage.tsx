import { deploymentEnumLabel, deploymentTerm } from '../deploymentTerminology'
import { CommercialContent } from '../uiProfile'
import { ConfigurationSurface } from '../components/ConfigurationSurface'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import CloudUploadRoundedIcon from '@mui/icons-material/CloudUploadRounded'
import DownloadRoundedIcon from '@mui/icons-material/DownloadRounded'
import ExtensionRoundedIcon from '@mui/icons-material/ExtensionRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded'
import ManageAccountsRoundedIcon from '@mui/icons-material/ManageAccountsRounded'
import PublishRoundedIcon from '@mui/icons-material/PublishRounded'
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  bootstrapMarketplaceTemplatePlugin,
  createMarketplacePublisher,
  createMarketplacePublisherSubmission,
  createDeploymentMarketplaceInstall,
  deleteDeploymentMarketplaceInstall,
  fetchDeploymentDraft,
  fetchDeployments,
  fetchDeploymentMarketplaceImpact,
  fetchDeploymentMarketplaceInstalls,
  fetchDeploymentTemplates,
  fetchDeploymentWorkspace,
  fetchDocumentStorageBindings,
  fetchMarketplaceCategories,
  fetchMarketplaceInferenceServices,
  fetchMarketplacePlugin,
  fetchMarketplacePlugins,
  fetchMarketplacePublisher,
  fetchMarketplacePublishers,
  publishMarketplaceSubmission,
  reconcileMarketplaceInferenceService,
  rejectMarketplaceSubmission,
  scaleMarketplaceInferenceService,
  resolveDeploymentMarketplaceInstall,
  updateDeploymentMarketplaceInstall,
  updateDeploymentMarketplaceInstallEntitlement,
  updateMarketplacePublisherVerification,
  validateMarketplaceSubmission,
  type CreateMarketplacePublisherRequest,
  type CreateMarketplacePublisherSubmissionRequest,
  type CreateMarketplaceTemplateBootstrapRequest,
  type DeploymentDraftResponse,
  type DeploymentSummary,
  type DeploymentMarketplaceInstallSummary,
  type MarketplacePluginDetailSummary,
  type MarketplacePluginSummary,
  type MarketplacePluginVersionSummary,
  type PlatformManagedInferenceServiceSummary,
  type MarketplacePublisherSummary,
} from '../api/platformApi'
import { useBrainEditor } from '../brain/BrainWorkspaceContext'
import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded'
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded'
import { useOptionalBrainWorkspace } from '../brain/BrainWorkspaceContext'
import { exactNumber, isExactNumber, parseExactObject, stringifyExactJson } from '../lib/losslessJson'
import { Link as RouterLink } from 'react-router-dom'

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function normalizeText(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase()
}

function prettifyJson(value: unknown): string {
  return stringifyExactJson(value ?? {}, 2)
}

function parseJsonObject(value: string, label: string): Record<string, unknown> {
  const trimmed = value.trim()
  if (trimmed.length === 0) {
    return {}
  }
  let parsed: unknown
  try {
    parsed = parseExactObject(trimmed, label)
  } catch {
    throw new Error(`${label} must be valid JSON.`)
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error(`${label} must be a JSON object.`)
  }
  return parsed as Record<string, unknown>
}

type MarketplaceInstallField = MarketplacePluginVersionSummary['installForm'][number]

function asRecord(value: unknown): Record<string, unknown> {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }
  return {}
}

function readRecordString(record: Record<string, unknown>, key: string): string {
  const value = record[key]
  return typeof value === 'string' ? value : ''
}

function readInstallFieldValue(
  field: MarketplaceInstallField,
  config: Record<string, unknown>,
  secretRefs: Record<string, unknown>,
): string {
  const source = field.type === 'secretRef' ? secretRefs : config
  const value = source[field.id]
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false'
  }
  if (typeof value === 'number' || isExactNumber(value)) {
    return `${value}`
  }
  if (typeof value === 'string') {
    return value
  }
  return ''
}

function updateInstallFieldValue(
  field: MarketplaceInstallField,
  rawValue: string,
  config: Record<string, unknown>,
  secretRefs: Record<string, unknown>,
): { config: Record<string, unknown>; secretRefs: Record<string, unknown> } {
  const nextConfig = { ...config }
  const nextSecretRefs = { ...secretRefs }
  const target = field.type === 'secretRef' ? nextSecretRefs : nextConfig

  if (rawValue.trim().length === 0) {
    delete target[field.id]
    return { config: nextConfig, secretRefs: nextSecretRefs }
  }

  switch (field.type) {
    case 'boolean':
      target[field.id] = rawValue === 'true'
      break
    case 'number':
      target[field.id] = exactNumber(rawValue)
      break
    default:
      target[field.id] = rawValue
      break
  }
  return { config: nextConfig, secretRefs: nextSecretRefs }
}

function validateInstallFields(
  fields: MarketplaceInstallField[],
  config: Record<string, unknown>,
  secretRefs: Record<string, unknown>,
): string[] {
  return fields.flatMap((field) => {
    const value = readInstallFieldValue(field, config, secretRefs)
    if (field.required && value.trim().length === 0) {
      return `${field.label} (${field.id})`
    }
    if (field.type === 'number' && value.trim().length > 0) {
      try { exactNumber(value) } catch { return `${field.label} (${field.id}) must be numeric` }
    }
    return []
  })
}

function CapabilityMarketplace({ brainId, pluginId, pluginVersion }: CapabilityMarketplaceView) {
  const installs = useQuery({ queryKey: ['deployment-marketplace-installs', brainId], queryFn: () => fetchDeploymentMarketplaceInstalls(brainId) })
  const workspace = useQuery({ queryKey: ['deployment-workspace', brainId], queryFn: () => fetchDeploymentWorkspace(brainId) })
  const customerId = workspace.data?.deployment.binding?.customerId ?? ''
  const plugin = useQuery({ queryKey: ['marketplace-plugin', pluginId, customerId], queryFn: () => fetchMarketplacePlugin(pluginId, customerId), enabled: workspace.isSuccess })
  const error = plugin.error ?? installs.error ?? workspace.error
  if (error) return <Alert severity="error">{error.message}</Alert>
  if (!plugin.data || !installs.data || !workspace.data) return <Typography color="text.secondary">Loading connection...</Typography>
  return <CapabilityInstallForm brainId={brainId} detail={plugin.data} requestedVersion={pluginVersion}
    install={installs.data.find((install) => install.pluginId === pluginId) ?? null} canEdit={workspace.data.access.canEdit} />
}

function CapabilityInstallForm({ brainId, detail, install, canEdit, requestedVersion }: {
  brainId: string; detail: MarketplacePluginDetailSummary; install: DeploymentMarketplaceInstallSummary | null; canEdit: boolean; requestedVersion?: string
}) {
  const client = useQueryClient()
  const brain = useOptionalBrainWorkspace()
  const [step, setStep] = useState(0)
  const tasks = ['Connection', 'Information & access', 'Review']
  const initialVersion = install?.pluginVersion ?? detail.versions[0]?.version ?? ''
  const [version, setVersion] = useState(requestedVersion || initialVersion)
  const [config, setConfig] = useState<Record<string, unknown>>(() => asRecord(install?.config))
  const [refs, setRefs] = useState<Record<string, unknown>>(() => asRecord(install?.secretRefs))
  const [baseline, setBaseline] = useState(() => ({ version: initialVersion, config: prettifyJson(install?.config), refs: prettifyJson(install?.secretRefs), updatedAt: install?.updatedAt ?? null }))
  const [rawConfig, setRawConfig] = useState<string | null>(null)
  const [rawRefs, setRawRefs] = useState<string | null>(null)
  const [numericInputs, setNumericInputs] = useState<Record<string, string>>({})
  const [removeOpen, setRemoveOpen] = useState(false)
  const [message, setMessage] = useState<string>()
  const selectedVersion = detail.versions.find((candidate) => candidate.version === version)
  const fields = selectedVersion?.installForm ?? []
  const datasets = asRecord(asRecord(selectedVersion?.manifest).contributions).datasets
  const storageFields = new Set((Array.isArray(datasets) ? datasets : []).flatMap(value => {
    const connector = asRecord(asRecord(value).sourceConnector)
    return connector.connectorType === 'S3_COMPATIBLE_OBJECT_STORAGE' && typeof connector.bindingRefField === 'string' ? [connector.bindingRefField] : []
  }))
  const bindings = useQuery({ queryKey: ['document-storage-bindings', brainId], queryFn: () => fetchDocumentStorageBindings(brainId), enabled: storageFields.size > 0 })
  const numericErrors = Object.entries(numericInputs).flatMap(([id, value]) => {
    try { if (value.trim()) exactNumber(value); return [] } catch { return [`${fields.find(field => field.id === id)?.label ?? id} must be numeric`] }
  })
  let reviewConfig = config, reviewRefs = refs, sourceError: string | null = null
  try {
    if (rawConfig !== null) reviewConfig = parseExactObject(rawConfig, 'Plugin configuration')
    if (rawRefs !== null) reviewRefs = parseExactObject(rawRefs, 'Secret references')
  } catch (error) { sourceError = error instanceof Error ? error.message : 'Invalid JSON. Your source is retained.' }
  const fieldErrors = [...validateInstallFields(fields, reviewConfig, reviewRefs), ...numericErrors]
  const dirty = Object.keys(numericInputs).length > 0 || version !== baseline.version || (rawConfig ?? prettifyJson(config)) !== baseline.config || (rawRefs ?? prettifyJson(refs)) !== baseline.refs
  const conflict = dirty && (install?.updatedAt ?? null) !== baseline.updatedAt
  const supported = detail.plugin.pluginType === 'ACTION' || detail.plugin.pluginType === 'DATA' || detail.plugin.pluginType === 'INTEGRATION'

  const adopt = (saved: DeploymentMarketplaceInstallSummary | null) => {
    const nextVersion = saved?.pluginVersion ?? detail.versions[0]?.version ?? ''
    setVersion(nextVersion)
    setConfig(asRecord(saved?.config))
    setRefs(asRecord(saved?.secretRefs))
    setRawConfig(null)
    setRawRefs(null)
    setNumericInputs({})
    setBaseline({ version: nextVersion, config: prettifyJson(saved?.config), refs: prettifyJson(saved?.secretRefs), updatedAt: saved?.updatedAt ?? null })
  }
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['deployment-marketplace-installs', brainId] }),
      client.invalidateQueries({ queryKey: ['deployment-marketplace-impact', brainId] }),
      client.invalidateQueries({ queryKey: ['deployment-draft', brainId] }),
      client.invalidateQueries({ queryKey: ['deployment-workspace', brainId] }),
      client.invalidateQueries({ queryKey: ['deployment-validation'] }),
    ])
  }
  const operation = useMutation({
    mutationFn: async (command: 'save' | 'toggle' | 'resolve' | 'remove') => {
      if (!canEdit || !supported) throw new Error('Editing this connection requires Brain editor permission.')
      const currentInstalls = await fetchDeploymentMarketplaceInstalls(brainId)
      const current = currentInstalls.find((candidate) => candidate.pluginId === detail.plugin.id) ?? null
      if ((current?.updatedAt ?? null) !== baseline.updatedAt) {
        client.setQueryData(['deployment-marketplace-installs', brainId], currentInstalls)
        throw new Error('This connection changed. Your edits are retained; discard to load the current connection before retrying.')
      }
      if (command === 'save') {
        if (!selectedVersion) throw new Error('Select an available plugin version before saving.')
        if (numericErrors.length) throw new Error(numericErrors.join(', '))
        const nextConfig = rawConfig === null ? config : parseJsonObject(rawConfig, 'Plugin configuration')
        const nextRefs = rawRefs === null ? refs : parseJsonObject(rawRefs, 'Secret references')
        const errors = validateInstallFields(fields, nextConfig, nextRefs)
        if (errors.length) throw new Error(`Complete the required install fields: ${errors.join(', ')}`)
        return current ? updateDeploymentMarketplaceInstall(brainId, current.id, { expectedUpdatedAt: baseline.updatedAt ?? undefined, pluginVersion: version, status: current.status, config: nextConfig, secretRefs: nextRefs })
          : createDeploymentMarketplaceInstall(brainId, { pluginId: detail.plugin.id, pluginVersion: version, config: nextConfig, secretRefs: nextRefs })
      }
      if (!current) throw new Error('This connection is no longer installed.')
      if (command === 'toggle') return updateDeploymentMarketplaceInstall(brainId, current.id, { expectedUpdatedAt: baseline.updatedAt ?? undefined, status: current.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' })
      if (command === 'resolve') return resolveDeploymentMarketplaceInstall(brainId, current.id)
      await deleteDeploymentMarketplaceInstall(brainId, current.id, baseline.updatedAt ?? undefined)
      return null
    },
    onSuccess: async (saved, command) => {
      // Every install command already persists the shared draft; no second draft save follows it.
      if (command === 'save' || command === 'toggle') adopt(saved as DeploymentMarketplaceInstallSummary)
      if (command === 'remove') adopt(null)
      setMessage('Draft updated; live configuration unchanged.')
      setRemoveOpen(false)
      await refresh()
    },
  })
  useEffect(() => {
    if (!dirty && install && install.updatedAt !== baseline.updatedAt && !operation.isPending) adopt(install)
  }, [install?.updatedAt])
  useBrainEditor({ dirty, busy: operation.isPending,
    canSave: canEdit && supported && Boolean(selectedVersion) && !conflict && (step < 2 || !sourceError && !fieldErrors.length && (dirty || !install)),
    save: canEdit && supported ? () => step < 2 ? setStep(step + 1) : operation.mutateAsync('save').catch(() => undefined) : undefined,
    discard: canEdit ? () => { adopt(install); operation.reset(); setMessage(undefined) } : undefined,
    primaryLabel: step < 2 ? 'Continue' : install ? 'Save connection' : 'Add to Brain draft',
    message: message && !dirty ? message : !canEdit ? 'Read-only access. Brain editor permission is required to make changes.' : undefined,
    error: operation.error?.message,
  })
  const disabled = !canEdit || operation.isPending || !supported
  const editField = (field: MarketplaceInstallField, value: string) => {
    if (field.type === 'number') setNumericInputs(current => ({ ...current, [field.id]: value }))
    let next
    try { next = updateInstallFieldValue(field, value, config, refs) } catch { return }
    setConfig(next.config); setRefs(next.secretRefs)
    setRawConfig(null); setRawRefs(null); setMessage(undefined)
  }
  const connectionField = (field: MarketplaceInstallField) => field.type === 'secretRef' || field.type === 'url' || storageFields.has(field.id)
  const visibleFields = fields.filter(field => step === 0 ? connectionField(field) : !connectionField(field))
  return <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: '190px minmax(0,1fr)' }, gap: 3, height: '100%', minHeight: 0 }}>
    <Box component="nav" aria-label="Connection setup steps" sx={{ borderRight: { md: 1 }, borderColor: 'divider', pr: { md: 2 } }}>
      <List disablePadding>{tasks.map((task, index) => <ListItemButton key={task} selected={step === index} onClick={() => setStep(index)} disabled={operation.isPending} sx={{ mb: 1, borderRadius: 1, alignItems: 'flex-start', gap: 1 }}>
        <Chip size="small" label={index + 1} color={step === index ? 'primary' : 'default'} sx={{ width: 24, height: 24, '& .MuiChip-label': { px: 0 } }} /><ListItemText primary={task} primaryTypographyProps={{ variant: 'body2' }} />
      </ListItemButton>)}</List>
    </Box>
    <Stack spacing={2.5} sx={{ pb: 2, overflow: 'auto', minWidth: 0, pr: 1 }}>
    <Typography component="h2" sx={{ fontSize: 21, fontWeight: 650 }}>{tasks[step]}</Typography>
    {!canEdit ? <Alert severity="info">Read-only access. Brain editor permission is required to make changes.</Alert> : null}
    {!supported ? <Alert severity="info">This plugin is managed in its owning technical workspace.</Alert> : null}
    {conflict ? <Alert severity="warning">This connection changed while you were editing. Discard to load its current settings.</Alert> : null}
    {operation.isError ? <Alert severity="error">{operation.error.message}</Alert> : null}
    {sourceError ? <Alert severity="error">{sourceError}</Alert> : null}
    {step === 0 ? <><TextField select fullWidth size="small" label="Plugin version" value={version} disabled={disabled} onChange={(event) => { setVersion(event.target.value); setMessage(undefined) }}>
      {!selectedVersion && version ? <MenuItem value={version}>{version} (unavailable)</MenuItem> : null}
      {detail.versions.map((candidate) => <MenuItem key={candidate.id} value={candidate.version}>{candidate.version}</MenuItem>)}
    </TextField><Typography variant="caption" color="text.secondary">Connection inputs are saved in the shared Brain draft. Storage bindings remain environment-specific; changing this version does not upgrade a live deployment.</Typography></> : null}
    {!selectedVersion ? <Alert severity="warning">The selected version is not available in the catalogue. Existing configuration is retained.</Alert> : null}
    <Typography variant="body2" color="text.secondary">{detail.plugin.publisherDisplayName}<CommercialContent> - {pricingLabel(selectedVersion?.pricing)}</CommercialContent></Typography>
    {selectedVersion?.pricing.requiresEntitlement ? <Alert severity="warning">Paid capabilities require an active entitlement before they can compile into the draft.</Alert> : null}
    {storageFields.size > 0 && step === 0 ? <>
      {bindings.isError ? <Alert severity="error">{bindings.error.message}</Alert> : bindings.isPending ? <Typography variant="body2" color="text.secondary">Loading storage connections...</Typography> : null}
      {brain ? <Button component={RouterLink} to={brain.href('knowledge/storage')} sx={{ alignSelf: 'flex-start' }}>Manage document storage</Button> : null}
    </> : null}
    {step < 2 ? visibleFields.map((field) => storageFields.has(field.id) ? <TextField key={field.id} select fullWidth size="small" label={field.label} value={readInstallFieldValue(field, config, refs)}
      disabled={disabled || rawConfig !== null || rawRefs !== null} onChange={event => editField(field, event.target.value)} helperText="Target-bound storage uses its own environment credentials; no credential is copied into this definition.">
      <MenuItem value="">Choose a storage connection</MenuItem><MenuItem value="slot:document-storage">Use this environment's document storage binding</MenuItem>
      {(bindings.data ?? []).filter(binding => binding.targetProfileId === brain?.targetProfileId).map(binding => <MenuItem key={binding.bindingRef} value={binding.bindingRef}>{binding.bindingRef} / {binding.status}</MenuItem>)}
      {readInstallFieldValue(field, config, refs) && readInstallFieldValue(field, config, refs) !== 'slot:document-storage' && !(bindings.data ?? []).some(binding => binding.bindingRef === readInstallFieldValue(field, config, refs) && binding.targetProfileId === brain?.targetProfileId)
        ? <MenuItem value={readInstallFieldValue(field, config, refs)}>{readInstallFieldValue(field, config, refs)} (not confirmed in this environment)</MenuItem> : null}
    </TextField> : field.type === 'boolean' ? <FormControlLabel key={field.id} label={field.label}
      control={<Checkbox disabled={disabled || rawConfig !== null || rawRefs !== null} indeterminate={readInstallFieldValue(field, config, refs) === ''} checked={readInstallFieldValue(field, config, refs) === 'true'} onChange={(_, checked) => editField(field, String(checked))} />} /> :
      <TextField key={field.id} fullWidth size="small" select={field.type === 'select'} required={field.required}
        type={field.type === 'url' ? 'url' : 'text'} label={field.label}
        value={numericInputs[field.id] ?? readInstallFieldValue(field, config, refs)} onChange={(event) => editField(field, event.target.value)} disabled={disabled || rawConfig !== null || rawRefs !== null}
        helperText={field.description ?? (field.type === 'secretRef' ? 'Platform secret reference, never a raw credential.' : undefined)}>
        {field.type === 'select' ? field.options.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>) : null}
      </TextField>) : null}
    {step < 2 && visibleFields.length === 0 && selectedVersion ? <Typography variant="body2" color="text.secondary">{step === 0 ? 'No connection inputs are declared for this version. Check its source settings in the next step.' : 'No additional source inputs are declared by this version.'}</Typography> : null}
    {step === 1 ? <Typography variant="body2" color="text.secondary">{detail.plugin.pluginType === 'DATA' ? 'Your source remains the source of truth. This Data connection does not grant write access or replace content approval.' : 'Access and confirmation requirements remain governed by the declared capability contract.'}</Typography> : null}
    {step === 2 ? <>
      <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'minmax(100px,.35fr) minmax(0,1fr)', gap: 1.5, '& dt': { color: 'text.secondary' }, '& dd': { m: 0, overflowWrap: 'anywhere' } }}>
        <Typography component="dt" variant="body2">Plugin</Typography><Typography component="dd" variant="body2">{detail.plugin.displayName} / {version}</Typography>
        <Typography component="dt" variant="body2">Saved to</Typography><Typography component="dd" variant="body2">Shared Brain draft</Typography>
        {fields.map(field => <Box key={field.id} sx={{ display: 'contents' }}><Typography component="dt" variant="body2">{field.label}</Typography><Typography component="dd" variant="body2">{readInstallFieldValue(field, reviewConfig, reviewRefs) || 'Not provided'}</Typography></Box>)}
      </Box>
      {install && version !== baseline.version ? <Alert severity="warning">Definition version changes from {baseline.version} to {version}. Existing input values are retained; check the new version's requirements before saving.</Alert> : null}
      <Alert severity="info">Saving checks the declared configuration and recompiles the draft. It does not test the provider, start indexing, or update a live environment.</Alert>
      {message && brain ? <Button component={RouterLink} to={brain.href('updates')} sx={{ alignSelf: 'flex-start' }}>Review Brain changes</Button> : null}
    </> : null}
    {fieldErrors.length ? <Alert severity="warning">Complete these inputs: {fieldErrors.join(', ')}</Alert> : null}
    {install ? <>
      <Divider />
      <Typography variant="body2" color="text.secondary">Draft status: {install.status}. Readiness: {install.readinessStatus}. Entitlement: {install.entitlement.status}.</Typography>
      {install.warnings.length ? <Alert severity="warning">{install.warnings.join(' ')}</Alert> : null}
    </> : null}
    <Accordion disableGutters sx={{ background: 'none', boxShadow: 'none', borderTop: 1, borderColor: 'divider', '&:before': { display: 'none' } }}>
      <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />} sx={{ px: 0 }}><Typography variant="subtitle2">Technical connection settings</Typography></AccordionSummary>
      <AccordionDetails sx={{ px: 0 }}><Stack spacing={2}>
        <TextField label="Plugin configuration JSON" multiline minRows={5} maxRows={12} value={rawConfig ?? prettifyJson(config)} disabled={disabled}
          onChange={(event) => { setRawConfig(event.target.value); setNumericInputs({}); setMessage(undefined) }} InputProps={{ sx: { fontFamily: 'monospace', fontSize: 13 } }} />
        <TextField label="Secret references JSON" multiline minRows={3} maxRows={8} value={rawRefs ?? prettifyJson(refs)} disabled={disabled}
          helperText="References only. Stored credentials are not displayed."
          onChange={(event) => { setRawRefs(event.target.value); setMessage(undefined) }} InputProps={{ sx: { fontFamily: 'monospace', fontSize: 13 } }} />
        {rawConfig !== null || rawRefs !== null ? <Button sx={{ alignSelf: 'flex-start' }} disabled={disabled} onClick={() => {
          try { const nextConfig = parseExactObject(rawConfig ?? prettifyJson(config)); const nextRefs = parseExactObject(rawRefs ?? prettifyJson(refs)); setConfig(nextConfig); setRefs(nextRefs); setRawConfig(null); setRawRefs(null); setNumericInputs({}); setMessage(undefined); operation.reset() }
          catch (error) { setMessage(error instanceof Error ? error.message : 'Invalid JSON. Your source is retained.') }
        }}>Use source in form</Button> : null}
        {install && canEdit ? <Stack direction="row" gap={1} flexWrap="wrap">
          <Button variant="outlined" disabled={disabled || dirty} onClick={() => operation.mutate('toggle')}>{install.status === 'ENABLED' ? 'Disable' : 'Enable'}</Button>
          <Button variant="outlined" startIcon={<SyncRoundedIcon />} disabled={disabled || dirty} onClick={() => operation.mutate('resolve')}>Recompile draft</Button>
          <Button color="error" disabled={disabled || dirty} onClick={() => setRemoveOpen(true)}>Remove connection</Button>
        </Stack> : null}
      </Stack></AccordionDetails>
    </Accordion>
    {message ? <Typography variant="body2" color="text.secondary">{message}</Typography> : null}
    <Stack direction="row" justifyContent="space-between" gap={1}>
      <Button startIcon={<ArrowBackRoundedIcon />} disabled={step === 0 || operation.isPending} onClick={() => setStep(step - 1)}>Previous step</Button>
      {step < 2 ? <Button endIcon={<ArrowForwardRoundedIcon />} onClick={() => setStep(step + 1)}>Review next step</Button> : null}
    </Stack>
    <Dialog open={removeOpen} onClose={() => { if (!operation.isPending) setRemoveOpen(false) }}>
      <DialogTitle>Remove {detail.plugin.displayName}?</DialogTitle>
      <DialogContent>This removes the connection from the shared draft. Live configuration is unchanged until published and applied.</DialogContent>
      <DialogActions><Button disabled={operation.isPending} onClick={() => setRemoveOpen(false)}>Cancel</Button><Button color="error" disabled={operation.isPending} onClick={() => operation.mutate('remove')}>Remove connection</Button></DialogActions>
    </Dialog>
  </Stack></Box>
}

function providerDraftSummary(draft: DeploymentDraftResponse | undefined): {
  llmProvider: string
  embeddingProvider: string
  missingBaseline: boolean
} {
  const providerConfig = asRecord(draft?.providerConfig)
  const llmProvider = readRecordString(providerConfig, 'llmProvider').trim()
  const embeddingProvider = readRecordString(providerConfig, 'embeddingProvider').trim()
  return {
    llmProvider,
    embeddingProvider,
    missingBaseline: llmProvider.length === 0 || embeddingProvider.length === 0,
  }
}

function pluginTypeColor(pluginType: string): 'primary' | 'secondary' | 'success' | 'warning' {
  switch (pluginType) {
    case 'TEMPLATE':
      return 'secondary'
    case 'ACTION':
      return 'primary'
    case 'DATA':
      return 'success'
    case 'AUTOMATION':
      return 'warning'
    case 'INFERENCE_PROFILE':
      return 'primary'
    case 'SPECIALIST':
      return 'secondary'
    case 'INTEGRATION':
      return 'success'
    default:
      return 'warning'
  }
}

function categoryLabel(plugin: MarketplacePluginSummary): string {
  switch (plugin.pluginType) {
    case 'TEMPLATE':
      return 'Templates'
    case 'ACTION':
      return 'Actions'
    case 'DATA':
      return 'Data'
    case 'AUTOMATION':
      return 'Automation'
    case 'INFERENCE_PROFILE':
      return 'Inference Profiles'
    case 'SPECIALIST':
      return 'Specialists'
    case 'INTEGRATION':
      return 'Integrations'
    default:
      return plugin.pluginType
  }
}

function categoryToPluginType(categoryId: string): string {
  return categoryId.toUpperCase().replace(/-/g, '_')
}

function contributionList(values: string[]): string {
  return values.length > 0 ? values.join(', ') : '—'
}

function readinessColor(value: string): 'success' | 'warning' | 'default' {
  switch (value) {
    case 'READY':
    case 'LIVE':
      return 'success'
    case 'BOOTSTRAPPED':
      return 'default'
    default:
      return 'warning'
  }
}

function entitlementColor(value: string): 'success' | 'warning' | 'error' | 'default' {
  switch (value) {
    case 'ACTIVE':
      return 'success'
    case 'PAST_DUE':
    case 'PENDING':
      return 'warning'
    case 'SUSPENDED':
    case 'CANCELLED':
      return 'error'
    default:
      return 'default'
  }
}

function publisherVerificationColor(value: string): 'success' | 'warning' | 'error' | 'default' {
  switch (value) {
    case 'VERIFIED':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'REJECTED':
      return 'error'
    default:
      return 'default'
  }
}

function publisherStatusColor(value: string): 'success' | 'warning' | 'error' | 'default' {
  switch (value) {
    case 'ACTIVE':
      return 'success'
    case 'SUSPENDED':
      return 'error'
    default:
      return 'default'
  }
}

function submissionStatusColor(value: string): 'success' | 'warning' | 'error' | 'default' {
  switch (value) {
    case 'PUBLISHED':
      return 'success'
    case 'VALIDATED':
      return 'warning'
    case 'REJECTED':
      return 'error'
    default:
      return 'default'
  }
}

function pricingLabel(
  pricing: { pricingModel: string; amount: number | null; currency: string | null; billingInterval: string | null } | null | undefined,
): string {
  if (!pricing) {
    return 'Free'
  }
  if (pricing.pricingModel === 'FREE') {
    return 'Free'
  }
  const amount = pricing.amount == null ? '—' : `${pricing.currency ?? ''} ${pricing.amount.toFixed(2)}`.trim()
  if (pricing.pricingModel === 'ONE_OFF') {
    return `${amount} one-off`
  }
  return `${amount} / ${normalizeText(pricing.billingInterval ?? 'monthly')}`
}

function toDatetimeLocal(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  const pad = (part: number) => `${part}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toIsoOrNull(value: string): string | null {
  return value.trim().length > 0 ? new Date(value).toISOString() : null
}

function defaultBootstrapName(detail: MarketplacePluginDetailSummary | null): string {
  if (!detail) {
    return 'Marketplace Deployment'
  }
  return `${detail.plugin.displayName} Deployment`
}

type CapabilityMarketplaceView = { brainId: string; pluginId: string; pluginVersion?: string }

export function MarketplacePage({ deploymentScoped = false, capabilityView }: { deploymentScoped?: boolean; capabilityView?: CapabilityMarketplaceView }) {
  return capabilityView ? <CapabilityMarketplace key={`${capabilityView.brainId}:${capabilityView.pluginId}:${capabilityView.pluginVersion ?? ''}`} {...capabilityView} /> : <LegacyMarketplacePage deploymentScoped={deploymentScoped} />
}

function LegacyMarketplacePage({ deploymentScoped = false }: { deploymentScoped?: boolean }) {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [selectedType, setSelectedType] = useState<'ALL' | string>(() => {
    const requestedCategory = new URLSearchParams(location.search).get('category')
    return requestedCategory ? categoryToPluginType(requestedCategory) : 'ALL'
  })
  const [searchText, setSearchText] = useState('')
  const [selectedPluginId, setSelectedPluginId] = useState('')
  const [selectedVersion, setSelectedVersion] = useState('')
  const [installConfigState, setInstallConfigState] = useState<Record<string, unknown>>({})
  const [installSecretRefsState, setInstallSecretRefsState] = useState<Record<string, unknown>>({})
  const [templateName, setTemplateName] = useState('Marketplace Deployment')
  const [templateEnvironment, setTemplateEnvironment] = useState('dev')
  const [templateId, setTemplateId] = useState('')
  const [templateVectorProvisioningMode, setTemplateVectorProvisioningMode] = useState('')
  const [templateCustomerId, setTemplateCustomerId] = useState('')
  const [templateTenantId, setTemplateTenantId] = useState('')
  const [entitlementStatus, setEntitlementStatus] = useState('PENDING')
  const [entitlementGraceEndsAt, setEntitlementGraceEndsAt] = useState('')
  const [entitlementAccessEndsAt, setEntitlementAccessEndsAt] = useState('')
  const [entitlementNote, setEntitlementNote] = useState('')
  const [selectedPublisherId, setSelectedPublisherId] = useState('')
  const [publisherSlug, setPublisherSlug] = useState('')
  const [publisherDisplayName, setPublisherDisplayName] = useState('')
  const [publisherContactEmail, setPublisherContactEmail] = useState('')
  const [publisherVerificationStatus, setPublisherVerificationStatus] = useState('PENDING')
  const [publisherStatus, setPublisherStatus] = useState('ACTIVE')
  const [serviceReplicaInputs, setServiceReplicaInputs] = useState<Record<string, string>>({})
  const [submissionPluginSlug, setSubmissionPluginSlug] = useState('')
  const [submissionReleaseChannel, setSubmissionReleaseChannel] = useState('DRAFT')
  const [submissionManifestJson, setSubmissionManifestJson] = useState('{\n  "schemaVersion": 1,\n  "pluginId": "",\n  "version": "1.0.0",\n  "pluginType": "TEMPLATE",\n  "displayName": "",\n  "description": "",\n  "compatibility": {\n    "requiredCapabilities": ["templates", "shellConfig"]\n  },\n  "pricing": {\n    "pricingModel": "FREE"\n  },\n  "permissions": {\n    "contributesTemplate": true,\n    "contributesShellPresentation": true\n  },\n  "contributions": {\n    "template": {\n      "curatedModuleId": "commerce",\n      "shell": {\n        "enabledModuleIds": ["docs", "actions"]\n      }\n    }\n  }\n}')
  const [submissionReviewNotes, setSubmissionReviewNotes] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const requestedTargetDeploymentId = useMemo(() => {
    const searchParams = new URLSearchParams(location.search)
    return deploymentScoped ? searchParams.get('deploymentId') ?? ''
      : searchParams.get('targetDeploymentId') ?? searchParams.get('deploymentId') ?? ''
  }, [location.search, deploymentScoped])

  const pluginsQuery = useQuery({
    queryKey: ['marketplace-plugins'],
    queryFn: () => fetchMarketplacePlugins(),
  })
  const categoriesQuery = useQuery({
    queryKey: ['marketplace-categories'],
    queryFn: fetchMarketplaceCategories,
  })
  const publishersQuery = useQuery({
    queryKey: ['marketplace-publishers'],
    queryFn: fetchMarketplacePublishers,
    enabled: !deploymentScoped,
  })
  const inferenceServicesQuery = useQuery({
    queryKey: ['marketplace-inference-services'],
    queryFn: fetchMarketplaceInferenceServices,
    enabled: !deploymentScoped,
  })
  const deploymentTemplatesQuery = useQuery({
    queryKey: ['deployment-templates'],
    queryFn: fetchDeploymentTemplates,
    enabled: !deploymentScoped,
  })
  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
  })
  const targetDeploymentSummary = useMemo<DeploymentSummary | null>(
    () => (deploymentsQuery.data ?? []).find((deployment) => deployment.id === requestedTargetDeploymentId) ?? null,
    [deploymentsQuery.data, requestedTargetDeploymentId],
  )
  const targetDeploymentId = targetDeploymentSummary?.id ?? ''
  const targetWorkspaceQuery = useQuery({
    queryKey: ['deployment-workspace', targetDeploymentId],
    queryFn: () => fetchDeploymentWorkspace(targetDeploymentId),
    enabled: targetDeploymentId.length > 0,
  })
  const targetWorkspace = targetWorkspaceQuery.data ?? null
  const canEdit = targetWorkspace?.access.canEdit ?? false
  const draftQuery = useQuery({
    queryKey: ['deployment-draft', targetDeploymentId],
    queryFn: () => fetchDeploymentDraft(targetDeploymentId),
    enabled: targetDeploymentId.length > 0,
  })
  const installsQuery = useQuery({
    queryKey: ['deployment-marketplace-installs', targetDeploymentId],
    queryFn: () => fetchDeploymentMarketplaceInstalls(targetDeploymentId),
    enabled: targetDeploymentId.length > 0,
  })
  const impactQuery = useQuery({
    queryKey: ['deployment-marketplace-impact', targetDeploymentId],
    queryFn: () => fetchDeploymentMarketplaceImpact(targetDeploymentId),
    enabled: targetDeploymentId.length > 0,
  })
  const publisherDetailQuery = useQuery({
    queryKey: ['marketplace-publisher', selectedPublisherId],
    queryFn: () => fetchMarketplacePublisher(selectedPublisherId),
    enabled: !deploymentScoped && selectedPublisherId.length > 0,
  })
  const reconcileInferenceServiceMutation = useMutation({
    mutationFn: (serviceRef: string) => reconcileMarketplaceInferenceService(serviceRef),
    onSuccess: (service) => {
      setSuccessMessage(`Managed inference service reconciled: ${service.displayName}`)
      void queryClient.invalidateQueries({ queryKey: ['marketplace-inference-services'] })
    },
    onError: (error: unknown) => {
      setFormError(error instanceof Error ? error.message : 'Failed to reconcile managed inference service.')
    },
  })
  const scaleInferenceServiceMutation = useMutation({
    mutationFn: ({ serviceRef, desiredReplicas }: { serviceRef: string; desiredReplicas: number }) =>
      scaleMarketplaceInferenceService(serviceRef, { desiredReplicas }),
    onSuccess: (service) => {
      setSuccessMessage(`Replica target updated for ${service.displayName}`)
      void queryClient.invalidateQueries({ queryKey: ['marketplace-inference-services'] })
    },
    onError: (error: unknown) => {
      setFormError(error instanceof Error ? error.message : 'Failed to update managed inference service scale.')
    },
  })

  const filteredPlugins = useMemo(() => {
    const needle = normalizeText(searchText)
    return (pluginsQuery.data ?? []).filter((plugin) => {
      if (deploymentScoped && plugin.pluginType === 'TEMPLATE') return false
      if (selectedType !== 'ALL' && plugin.pluginType !== selectedType) {
        return false
      }
      if (needle.length === 0) {
        return true
      }
      const haystack = [
        plugin.displayName,
        plugin.shortDescription,
        plugin.publisherDisplayName,
        plugin.slug,
      ].map(normalizeText).join(' ')
      return haystack.includes(needle)
    })
  }, [pluginsQuery.data, searchText, selectedType, deploymentScoped])

  useEffect(() => {
    if (filteredPlugins.length === 0) {
      setSelectedPluginId('')
      return
    }
    if (!filteredPlugins.some((plugin) => plugin.id === selectedPluginId)) {
      setSelectedPluginId(filteredPlugins[0].id)
    }
  }, [filteredPlugins, selectedPluginId])

  useEffect(() => {
    const publishers = publishersQuery.data ?? []
    if (publishers.length === 0) {
      setSelectedPublisherId('')
      return
    }
    if (!publishers.some((publisher) => publisher.id === selectedPublisherId)) {
      setSelectedPublisherId(publishers[0].id)
    }
  }, [publishersQuery.data, selectedPublisherId])

  const pluginDetailQuery = useQuery({
    queryKey: ['marketplace-plugin', selectedPluginId],
    queryFn: () => fetchMarketplacePlugin(selectedPluginId),
    enabled: selectedPluginId.length > 0,
  })

  const selectedPlugin = pluginDetailQuery.data?.plugin ?? null
  const selectedInstall = useMemo(() => {
    if (!selectedPlugin) {
      return null
    }
    return (installsQuery.data ?? []).find((install) => install.pluginId === selectedPlugin.id) ?? null
  }, [installsQuery.data, selectedPlugin])
  const selectedPublisher = publisherDetailQuery.data?.publisher ?? null

  const selectedVersionSummary = pluginDetailQuery.data?.versions.find((version) => version.version === selectedVersion)
    ?? pluginDetailQuery.data?.versions[0]
    ?? null

  useEffect(() => {
    const detail = pluginDetailQuery.data
    if (!detail) {
      return
    }
    const preferredVersion = selectedInstall?.pluginVersion ?? detail.versions[0]?.version ?? ''
    setSelectedVersion((current) => {
      if (detail.versions.some((version) => version.version === current)) {
        return current
      }
      return preferredVersion
    })
    setTemplateName((current) => (current.trim().length > 0 ? current : defaultBootstrapName(detail)))
  }, [pluginDetailQuery.data, selectedInstall?.pluginVersion])

  useEffect(() => {
    const publisher = publisherDetailQuery.data?.publisher
    if (!publisher) {
      return
    }
    setPublisherVerificationStatus(publisher.verificationStatus)
    setPublisherStatus(publisher.status)
  }, [publisherDetailQuery.data?.publisher])

  useEffect(() => {
    setInstallConfigState(asRecord(selectedInstall?.config))
    setInstallSecretRefsState(asRecord(selectedInstall?.secretRefs))
  }, [selectedInstall?.id, selectedPlugin?.id, selectedVersion])

  useEffect(() => {
    if (selectedInstall) {
      setEntitlementStatus(selectedInstall.entitlement.status)
      setEntitlementGraceEndsAt(toDatetimeLocal(selectedInstall.entitlement.graceEndsAt))
      setEntitlementAccessEndsAt(toDatetimeLocal(selectedInstall.entitlement.accessEndsAt))
      setEntitlementNote(selectedInstall.entitlement.note ?? '')
      return
    }
    setEntitlementStatus(selectedVersionSummary?.pricing.requiresEntitlement ? 'PENDING' : 'ACTIVE')
    setEntitlementGraceEndsAt('')
    setEntitlementAccessEndsAt('')
    setEntitlementNote('')
  }, [selectedInstall?.id, selectedInstall?.entitlement.status, selectedVersionSummary?.pricing.requiresEntitlement])

  useEffect(() => {
    if (targetWorkspace?.deployment.binding) {
      setTemplateCustomerId((current) => current || targetWorkspace.deployment.binding?.customerId || '')
      setTemplateTenantId((current) => current || targetWorkspace.deployment.binding?.tenantId || '')
    }
  }, [targetWorkspace?.deployment.binding])

  useEffect(() => {
    const templates = deploymentTemplatesQuery.data ?? []
    if (templates.length === 0) {
      return
    }
    setTemplateId((current) => current || templates[0].id)
  }, [deploymentTemplatesQuery.data])

  const setTargetDeploymentId = (deploymentId: string) => {
    const nextParams = new URLSearchParams(location.search)
    nextParams.delete('deploymentId')
    if (deploymentId.trim().length === 0) {
      nextParams.delete('targetDeploymentId')
    } else {
      nextParams.set('targetDeploymentId', deploymentId)
    }
    const nextSearch = nextParams.toString()
    navigate(
      {
        pathname: location.pathname,
        search: nextSearch.length > 0 ? `?${nextSearch}` : '',
      },
      { replace: false },
    )
  }

  const invalidateDeploymentState = async () => {
    if (!targetDeploymentId) {
      await queryClient.invalidateQueries({ queryKey: ['deployments'] })
      return
    }
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-installs', targetDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-impact', targetDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-draft', targetDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-workspace', targetDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployments'] }),
    ])
  }

  const invalidatePublisherState = async (publisherId?: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['marketplace-publishers'] }),
      queryClient.invalidateQueries({ queryKey: ['marketplace-plugins'] }),
      queryClient.invalidateQueries({ queryKey: ['marketplace-plugin'] }),
      ...(publisherId ? [queryClient.invalidateQueries({ queryKey: ['marketplace-publisher', publisherId] })] : []),
    ])
  }

  const installFields = selectedVersionSummary?.installForm ?? []
  const configInstallFields = useMemo(
    () => installFields.filter((field) => field.type !== 'secretRef'),
    [installFields],
  )
  const secretInstallFields = useMemo(
    () => installFields.filter((field) => field.type === 'secretRef'),
    [installFields],
  )
  const installFieldErrors = useMemo(
    () => validateInstallFields(installFields, installConfigState, installSecretRefsState),
    [installConfigState, installFields, installSecretRefsState],
  )
  const providerSummary = useMemo(() => providerDraftSummary(draftQuery.data), [draftQuery.data])
  const requiresDeploymentProviderBaseline = Boolean(
    targetDeploymentId
    && selectedPlugin
    && selectedPlugin.pluginType !== 'INFERENCE_PROFILE'
    && providerSummary.missingBaseline,
  )

  const setInstallField = (field: MarketplaceInstallField, rawValue: string) => {
    const next = updateInstallFieldValue(field, rawValue, installConfigState, installSecretRefsState)
    setInstallConfigState(next.config)
    setInstallSecretRefsState(next.secretRefs)
  }

  const installMutation = useMutation({
    mutationFn: async () => {
      if (!selectedPlugin || targetDeploymentId.length === 0) {
        throw new Error(`Select a target ${deploymentTerm.lowercase} and a marketplace plugin first.`)
      }
      if (requiresDeploymentProviderBaseline) {
        throw new Error(
          `This ${deploymentTerm.lowercase} is missing provider configuration. Open Providers and set LLM and embedding providers before installing this plugin.`,
        )
      }
      if (installFieldErrors.length > 0) {
        throw new Error(`Complete the required install fields first: ${installFieldErrors.join(', ')}`)
      }
      const payload = {
        pluginId: selectedPlugin.id,
        pluginVersion: selectedVersion,
        config: installConfigState,
        secretRefs: installSecretRefsState,
      }
      if (selectedInstall) {
        return updateDeploymentMarketplaceInstall(targetDeploymentId, selectedInstall.id, {
          expectedUpdatedAt: selectedInstall.updatedAt,
          pluginVersion: payload.pluginVersion,
          status: selectedInstall.status,
          config: payload.config,
          secretRefs: payload.secretRefs,
        })
      }
      return createDeploymentMarketplaceInstall(targetDeploymentId, payload)
    },
    onSuccess: async (install) => {
      setFormError(null)
      setSuccessMessage(`${install.pluginDisplayName} is now ${selectedInstall ? 'updated' : 'installed'} for ${targetDeploymentId}.`)
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const statusMutation = useMutation({
    mutationFn: async (status: string) => {
      if (!selectedInstall || targetDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      return updateDeploymentMarketplaceInstall(targetDeploymentId, selectedInstall.id, {
        expectedUpdatedAt: selectedInstall.updatedAt,
        status,
      })
    },
    onSuccess: async (install) => {
      setFormError(null)
      setSuccessMessage(`${install.pluginDisplayName} is now ${install.status}.`)
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const resolveMutation = useMutation({
    mutationFn: async () => {
      if (!selectedInstall || targetDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      return resolveDeploymentMarketplaceInstall(targetDeploymentId, selectedInstall.id)
    },
    onSuccess: async () => {
      setFormError(null)
      setSuccessMessage('Marketplace install recompiled into the active draft.')
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const entitlementMutation = useMutation({
    mutationFn: async () => {
      if (!selectedInstall || targetDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      return updateDeploymentMarketplaceInstallEntitlement(targetDeploymentId, selectedInstall.id, {
        status: entitlementStatus,
        graceEndsAt: toIsoOrNull(entitlementGraceEndsAt),
        accessEndsAt: toIsoOrNull(entitlementAccessEndsAt),
        note: entitlementNote.trim().length > 0 ? entitlementNote.trim() : null,
      })
    },
    onSuccess: async (install) => {
      setFormError(null)
      setSuccessMessage(`Entitlement for ${install.pluginDisplayName} is now ${install.entitlement.status}.`)
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: async () => {
      if (!selectedInstall || targetDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      await deleteDeploymentMarketplaceInstall(targetDeploymentId, selectedInstall.id, selectedInstall.updatedAt)
    },
    onSuccess: async () => {
      setFormError(null)
      setSuccessMessage(`Marketplace install removed from the ${deploymentTerm.lowercase}.`)
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const bootstrapMutation = useMutation({
    mutationFn: async () => {
      if (!selectedPlugin) {
        throw new Error('Select a template plugin first.')
      }
      const payload: CreateMarketplaceTemplateBootstrapRequest = {
        pluginVersion: selectedVersion || undefined,
        name: templateName.trim(),
        environment: templateEnvironment.trim(),
        templateId: templateId.trim() || undefined,
        vectorProvisioningMode: templateVectorProvisioningMode.trim() || undefined,
        customerId: templateCustomerId.trim() || undefined,
        tenantId: templateTenantId.trim() || undefined,
      }
      if (!payload.name) {
        throw new Error(`${deploymentTerm.singular} name is required.`)
      }
      if (!payload.environment) {
        throw new Error('Environment is required.')
      }
      return bootstrapMarketplaceTemplatePlugin(selectedPlugin.id, payload)
    },
    onSuccess: async (deployment) => {
      setFormError(null)
      setSuccessMessage(`Template bootstrapped ${deploymentTerm.lowercase} ${deployment.name}.`)
      await queryClient.invalidateQueries({ queryKey: ['deployments'] })
      navigate(`/overview?deploymentId=${encodeURIComponent(deployment.id)}`)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const createPublisherMutation = useMutation({
    mutationFn: async () => {
      const payload: CreateMarketplacePublisherRequest = {
        slug: publisherSlug.trim(),
        displayName: publisherDisplayName.trim(),
        contactEmail: publisherContactEmail.trim(),
      }
      if (!payload.slug || !payload.displayName || !payload.contactEmail) {
        throw new Error('Publisher slug, display name, and contact email are required.')
      }
      return createMarketplacePublisher(payload)
    },
    onSuccess: async (publisher) => {
      setFormError(null)
      setSuccessMessage(`Publisher ${publisher.displayName} created.`)
      setSelectedPublisherId(publisher.id)
      await invalidatePublisherState(publisher.id)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const updatePublisherVerificationMutation = useMutation({
    mutationFn: async () => {
      if (!selectedPublisherId) {
        throw new Error('Select a publisher first.')
      }
      return updateMarketplacePublisherVerification(selectedPublisherId, {
        verificationStatus: publisherVerificationStatus,
        status: publisherStatus,
      })
    },
    onSuccess: async (publisher) => {
      setFormError(null)
      setSuccessMessage(`Publisher ${publisher.displayName} is now ${publisher.verificationStatus} / ${publisher.status}.`)
      await invalidatePublisherState(publisher.id)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const createSubmissionMutation = useMutation({
    mutationFn: async () => {
      if (!selectedPublisherId) {
        throw new Error('Select a publisher first.')
      }
      const payload: CreateMarketplacePublisherSubmissionRequest = {
        pluginSlug: submissionPluginSlug.trim() || undefined,
        releaseChannel: submissionReleaseChannel.trim() || undefined,
        manifest: parseJsonObject(submissionManifestJson, 'Submission manifest'),
      }
      return createMarketplacePublisherSubmission(selectedPublisherId, payload)
    },
    onSuccess: async (submission) => {
      setFormError(null)
      setSuccessMessage(`Submission ${submission.pluginId}@${submission.version} created.`)
      await invalidatePublisherState(selectedPublisherId)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const reviewSubmissionMutation = useMutation({
    mutationFn: async ({ pluginVersionId, action }: { pluginVersionId: string; action: 'validate' | 'publish' | 'reject' }) => {
      const payload = {
        reviewNotes: submissionReviewNotes.trim().length > 0 ? submissionReviewNotes.trim() : null,
      }
      if (action === 'validate') {
        return validateMarketplaceSubmission(pluginVersionId, payload)
      }
      if (action === 'publish') {
        return publishMarketplaceSubmission(pluginVersionId, payload)
      }
      return rejectMarketplaceSubmission(pluginVersionId, payload)
    },
    onSuccess: async (submission) => {
      setFormError(null)
      setSuccessMessage(`Submission ${submission.pluginId}@${submission.version} is now ${submission.status}.`)
      await invalidatePublisherState(submission.publisherId)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  return (
    <ConfigurationSurface enabled={deploymentScoped}><Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: deploymentScoped ? 650 : 800, letterSpacing: deploymentScoped ? 0 : -0.5 }}>
            {deploymentScoped ? 'Plugins' : 'Marketplace'}
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75, maxWidth: 900 }}>
            {deploymentScoped ? `Manage plugins in this ${deploymentTerm.lowercase} draft. Publish and apply the draft to update the runtime.`
              : `Browse first-party marketplace plugins, bootstrap template-based ${deploymentTerm.pluralLowercase}, and assign action, data, or inference-profile plugins into an explicitly selected ${deploymentTerm.lowercase} draft through the normal publish and apply lifecycle.`}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Chip icon={<StorefrontRoundedIcon />} label={`${pluginsQuery.data?.length ?? 0} plugins`} variant="outlined" />
          <Chip
            icon={<ExtensionRoundedIcon />}
            label={targetDeploymentId ? `Target: ${targetDeploymentId}` : `No target ${deploymentTerm.lowercase} selected`}
            color={targetDeploymentId ? 'primary' : 'default'}
          />
          <Chip label={`${deploymentsQuery.data?.length ?? 0} accessible ${deploymentTerm.pluralLowercase}`} variant="outlined" />
          {impactQuery.data ? (
            <Chip icon={<SyncRoundedIcon />} label={`${impactQuery.data.totalInstalls} active install records`} variant="outlined" />
          ) : null}
        </Stack>
      </Stack>

      {formError ? <Alert severity="error">{formError}</Alert> : null}
      {successMessage ? <Alert severity="success">{successMessage}</Alert> : null}
      {!targetDeploymentId ? (
        <Alert severity="info">
          Marketplace is global. Template plugins can create new {deploymentTerm.pluralLowercase} directly, and action, data, or inference-profile plugins install into the target {deploymentTerm.lowercase} you choose below.
        </Alert>
      ) : null}

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Target {deploymentTerm.lowercase}
                </Typography>
                <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                  {deploymentScoped ? `Plugin changes belong to the selected ${deploymentTerm.lowercase} draft.`
                    : `Marketplace installs and entitlement changes are assigned to a ${deploymentTerm.lowercase} you select here. This page no longer inherits the ${deploymentTerm.lowercase} workspace selection.`}
                </Typography>
              </Box>
              {targetDeploymentId ? (
                <Button
                  variant="outlined"
                  onClick={() => navigate(deploymentScoped ? `/revisions?deploymentId=${encodeURIComponent(targetDeploymentId)}`
                    : `/overview?deploymentId=${encodeURIComponent(targetDeploymentId)}`)}
                >
                  Open target workspace
                </Button>
              ) : null}
            </Stack>
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField
                  select
                  fullWidth
                  label={`Target ${deploymentTerm.lowercase}`}
                  value={targetDeploymentId}
                  onChange={(event) => setTargetDeploymentId(event.target.value)}
                  disabled={deploymentScoped || deploymentsQuery.isLoading || (deploymentsQuery.data ?? []).length === 0}
                  helperText={targetWorkspace
                    ? `${targetWorkspace.deployment.name} · ${deploymentEnumLabel(targetWorkspace.access.assignmentRole)}`
                    : `Select the ${deploymentTerm.lowercase} that should receive installs and subscriptions.`}
                >
                  <MenuItem value="">No target {deploymentTerm.lowercase}</MenuItem>
                  {(deploymentsQuery.data ?? []).map((deployment) => (
                    <MenuItem key={deployment.id} value={deployment.id}>
                      {deployment.name} · {deployment.environment} · {deployment.id}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={6}>
                {targetDeploymentId ? (
                  targetWorkspaceQuery.isLoading ? (
                    <Alert severity="info">Loading target {deploymentTerm.lowercase} access…</Alert>
                  ) : !canEdit ? (
                    <Alert severity="warning">Your current assignment role is read-only for marketplace changes on this {deploymentTerm.lowercase}.</Alert>
                  ) : (
                    <Alert severity="success">Marketplace changes will compile into the active draft for this {deploymentTerm.lowercase}.</Alert>
                  )
                ) : (
                  <Alert severity="info">Choose a target {deploymentTerm.lowercase} when you want to install or subscribe to a plugin.</Alert>
                )}
              </Grid>
            </Grid>
          </Stack>
        </CardContent>
      </Card>

      {!deploymentScoped ? <Card>
        <CardContent>
          <Stack spacing={2}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Managed inference services
                </Typography>
                <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                  Shared inference-profile plugins depend on these platform-managed services being reconciled and active before installs can compile into {deploymentTerm.lowercase} provider configuration.
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip
                  icon={<ManageAccountsRoundedIcon />}
                  label={`${inferenceServicesQuery.data?.length ?? 0} services`}
                  variant="outlined"
                />
                <Button
                  variant="outlined"
                  onClick={() => navigate('/inference-services')}
                >
                  Open admin
                </Button>
              </Stack>
            </Stack>
            {inferenceServicesQuery.isLoading ? (
              <Typography color="text.secondary">Loading managed inference services…</Typography>
            ) : (
              <Grid container spacing={2}>
                {(inferenceServicesQuery.data ?? []).map((service) => (
                  <Grid item xs={12} md={6} key={service.id}>
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>{service.displayName}</Typography>
                            <Chip size="small" label={service.status} color={service.status === 'ACTIVE' ? 'success' : service.status === 'FAILED' ? 'error' : 'warning'} />
                            <Chip size="small" label={service.serviceKind} variant="outlined" />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            {service.serviceRef} · {service.providerType} · desired replicas {service.desiredReplicas ?? 1}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Endpoints: {service.endpoints.map((endpoint) => `${endpoint.endpointPurpose ?? 'UNKNOWN'}:${endpoint.profileRef}`).join(', ') || '—'}
                          </Typography>
                          {service.baseUrl ? (
                            <Typography variant="body2" color="text.secondary">
                              Base URL: {service.baseUrl}
                            </Typography>
                          ) : null}
                          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                            <Button
                              variant="outlined"
                              startIcon={<SyncRoundedIcon />}
                              onClick={() => {
                                setFormError(null)
                                setSuccessMessage(null)
                                reconcileInferenceServiceMutation.mutate(service.serviceRef)
                              }}
                              disabled={reconcileInferenceServiceMutation.isPending}
                            >
                              Reconcile
                            </Button>
                            <TextField
                              size="small"
                              label="Desired replicas"
                              value={serviceReplicaInputs[service.serviceRef] ?? `${service.desiredReplicas ?? 1}`}
                              onChange={(event) =>
                                setServiceReplicaInputs((current) => ({
                                  ...current,
                                  [service.serviceRef]: event.target.value,
                                }))
                              }
                              sx={{ maxWidth: 180 }}
                            />
                            <Button
                              variant="contained"
                              onClick={() => {
                                const raw = serviceReplicaInputs[service.serviceRef] ?? `${service.desiredReplicas ?? 1}`
                                const desiredReplicas = Number(raw)
                                if (!Number.isInteger(desiredReplicas) || desiredReplicas <= 0) {
                                  setFormError('Desired replicas must be a positive integer.')
                                  return
                                }
                                setFormError(null)
                                setSuccessMessage(null)
                                scaleInferenceServiceMutation.mutate({ serviceRef: service.serviceRef, desiredReplicas })
                              }}
                              disabled={scaleInferenceServiceMutation.isPending}
                            >
                              Scale
                            </Button>
                            {service.baseUrl ? (
                              <Button
                                variant="text"
                                endIcon={<LaunchRoundedIcon />}
                                onClick={() => window.open(service.baseUrl ?? '', '_blank', 'noopener,noreferrer')}
                              >
                                Open
                              </Button>
                            ) : null}
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            )}
          </Stack>
        </CardContent>
      </Card> : null}

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Catalog
                </Typography>
                <TextField
                  label="Search plugins"
                  value={searchText}
                  onChange={(event) => setSearchText(event.target.value)}
                  placeholder="Search by name, slug, or publisher"
                />
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip
                    label="All"
                    color={selectedType === 'ALL' ? 'primary' : 'default'}
                    onClick={() => setSelectedType('ALL')}
                  />
                  {(categoriesQuery.data ?? []).map((category) => (
                    <Chip
                      key={category.id}
                      label={`${category.label} (${category.pluginCount})`}
                      color={selectedType === categoryToPluginType(category.id) ? 'primary' : 'default'}
                      onClick={() => setSelectedType(categoryToPluginType(category.id))}
                    />
                  ))}
                </Stack>
                <Divider />
                {pluginsQuery.isLoading ? (
                  <Typography color="text.secondary">Loading marketplace catalog…</Typography>
                ) : filteredPlugins.length === 0 ? (
                  <Alert severity="info">No plugins match the current filter.</Alert>
                ) : (
                  <List disablePadding>
                    {filteredPlugins.map((plugin) => (
                      <ListItemButton
                        key={plugin.id}
                        selected={plugin.id === selectedPluginId}
                        onClick={() => {
                          setSelectedPluginId(plugin.id)
                          setFormError(null)
                          setSuccessMessage(null)
                        }}
                        sx={{ borderRadius: 2, mb: 1, alignItems: 'flex-start' }}
                      >
                        <ListItemText
                          primary={
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography sx={{ fontWeight: 700 }}>{plugin.displayName}</Typography>
                              <Chip size="small" label={categoryLabel(plugin)} color={pluginTypeColor(plugin.pluginType)} />
                            </Stack>
                          }
                          secondary={
                            <Stack spacing={0.75} sx={{ mt: 0.75 }}>
                              <Typography variant="body2" color="text.secondary">
                                {plugin.shortDescription}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {plugin.publisherDisplayName} · latest {plugin.latestVersion ?? '—'}<CommercialContent> · {pricingLabel(plugin.pricing)}</CommercialContent>
                              </Typography>
                            </Stack>
                          }
                        />
                      </ListItemButton>
                    ))}
                  </List>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          <Stack spacing={3}>
            <Card>
              <CardContent>
                {!selectedPlugin ? (
                  <Alert severity="info">Select a marketplace plugin to review its contributions and available versions.</Alert>
                ) : pluginDetailQuery.isLoading ? (
                  <Typography color="text.secondary">Loading plugin details…</Typography>
                ) : (
                  <Stack spacing={2.5}>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
                      <Box>
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                          <Typography variant="h5" sx={{ fontWeight: 800 }}>
                            {selectedPlugin.displayName}
                          </Typography>
                          <Chip label={selectedPlugin.pluginType} color={pluginTypeColor(selectedPlugin.pluginType)} />
                          <CommercialContent><Chip label={pricingLabel(selectedVersionSummary?.pricing ?? selectedPlugin.pricing)} variant="outlined" /></CommercialContent>
                          {selectedInstall ? (
                            <Chip icon={<CheckCircleRoundedIcon />} label={`Installed · ${selectedInstall.status}`} color="success" variant="outlined" />
                          ) : null}
                        </Stack>
                        <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                          {selectedPlugin.shortDescription}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                          Publisher: {selectedPlugin.publisherDisplayName} · Updated {formatTimestamp(selectedPlugin.updatedAt)}
                        </Typography>
                      </Box>
                      <TextField
                        select
                        label="Version"
                        value={selectedVersion}
                        onChange={(event) => setSelectedVersion(event.target.value)}
                        sx={{ minWidth: { xs: '100%', md: 220 } }}
                      >
                        {(pluginDetailQuery.data?.versions ?? []).map((version) => (
                          <MenuItem key={version.id} value={version.version}>
                            {version.version} · {version.releaseChannel}
                          </MenuItem>
                        ))}
                      </TextField>
                    </Stack>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Action contributions
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {contributionList(selectedVersionSummary?.contributions.actionIds ?? [])}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      {selectedVersionSummary?.contributions.templateDeploymentBehaviorType ? (
                        <Grid item xs={12}>
                          <Card variant="outlined">
                            <CardContent>
                              <Stack spacing={1}>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                                  <Typography variant="subtitle2" color="text.secondary">
                                    {deploymentTerm.singular} behavior contract
                                  </Typography>
                                  <Chip
                                    size="small"
                                    label={deploymentEnumLabel(selectedVersionSummary.contributions.templateDeploymentBehaviorType, selectedVersionSummary.contributions.templateDeploymentBehaviorType.replace(/_/g, ' '))}
                                    color="primary"
                                    variant="outlined"
                                  />
                                  <Chip
                                    size="small"
                                    label={`Contract v${selectedVersionSummary.contributions.templateDeploymentBehaviorContractVersion}`}
                                    variant="outlined"
                                  />
                                </Stack>
                                <Typography variant="body2">
                                  Runtime capabilities: {contributionList(selectedVersionSummary.contributions.templateRequiredRuntimeCapabilityIds)}
                                </Typography>
                                <Typography variant="body2">
                                  Channels: {contributionList(selectedVersionSummary.contributions.templateAllowedChannelBindings)}
                                </Typography>
                                <Typography variant="body2">
                                  Execution extensions: {contributionList(selectedVersionSummary.contributions.templateAllowedExecutionExtensions)}
                                </Typography>
                                <Typography variant="body2">
                                  Verification packs: {contributionList(selectedVersionSummary.contributions.templateVerificationPackIds)}
                                </Typography>
                                <Typography variant="body2">
                                  Required plugins: {contributionList(selectedVersionSummary.contributions.templateRequiredPluginRefs)}
                                </Typography>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      ) : null}
                      {selectedVersionSummary?.contributions.specialistBundleRefs.length ? (
                        <Grid item xs={12}>
                          <Card variant="outlined">
                            <CardContent>
                              <Stack spacing={1.5}>
                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                  <Typography variant="subtitle2" color="text.secondary">Source-attested specialist contract</Typography>
                                  <Chip size="small" label={selectedVersionSummary.contributions.specialistContractVersion ?? 'Unknown contract'} variant="outlined" />
                                  {selectedVersionSummary.contributions.specialistCompatibleBehaviorTypes.map((behaviorType) => (
                                    <Chip key={behaviorType} size="small" label={deploymentEnumLabel(behaviorType, behaviorType.replace(/_/g, ' '))} color="secondary" variant="outlined" />
                                  ))}
                                </Stack>
                                {selectedVersionSummary.contributions.specialistBundleRefs.map((bundle) => (
                                  <Box key={bundle.bundleId} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 1 }}>
                                    <Typography variant="subtitle2">{bundle.bundleId}</Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{bundle.contentHash}</Typography>
                                    <Typography variant="body2" sx={{ mt: 0.5 }}>Specialists: {contributionList(bundle.specialistRefs)}</Typography>
                                    <Typography variant="body2">Chains: {contributionList(bundle.chainRefs)}</Typography>
                                  </Box>
                                ))}
                                <Typography variant="body2">
                                  Runtime capabilities: {contributionList(selectedVersionSummary.contributions.specialistRequiredRuntimeCapabilityIds)}
                                </Typography>
                                <Typography variant="body2">
                                  Migrations: {contributionList(selectedVersionSummary.contributions.specialistRequiredMigrationIds)}
                                </Typography>
                                <Alert severity="info">
                                  {selectedVersionSummary.contributions.specialistUnsupportedClaims.join(' ')}
                                </Alert>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      ) : null}
                      {selectedVersionSummary?.contributions.conversationChannel ? (
                        <Grid item xs={12}>
                          <Card variant="outlined">
                            <CardContent>
                              <Stack spacing={1.5}>
                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Conversation channel contract
                                  </Typography>
                                  <Chip
                                    size="small"
                                    label={selectedVersionSummary.contributions.conversationChannel.bindingId}
                                    color="success"
                                    variant="outlined"
                                  />
                                  <Chip
                                    size="small"
                                    label={`Contract ${selectedVersionSummary.contributions.conversationChannel.contractVersion}`}
                                    variant="outlined"
                                  />
                                </Stack>
                                <Typography variant="body2">
                                  Adapter: {selectedVersionSummary.contributions.conversationChannel.adapterImplementationId}
                                  {' '}({selectedVersionSummary.contributions.conversationChannel.adapterVersion})
                                </Typography>
                                <Typography variant="body2">
                                  Compatible behaviors: {contributionList(selectedVersionSummary.contributions.conversationChannel.compatibleBehaviorTypes)}
                                </Typography>
                                <Typography variant="body2">
                                  Channel capabilities: {contributionList(selectedVersionSummary.contributions.conversationChannel.capabilities)}
                                </Typography>
                                <Typography variant="body2">
                                  Runtime capabilities: {contributionList(selectedVersionSummary.contributions.conversationChannel.requiredRuntimeCapabilityIds)}
                                </Typography>
                                <Typography variant="body2">
                                  Migrations: {contributionList(selectedVersionSummary.contributions.conversationChannel.requiredMigrationIds)}
                                </Typography>
                                <Typography variant="body2">
                                  Verification packs: {contributionList(selectedVersionSummary.contributions.conversationChannel.verificationPackIds)}
                                </Typography>
                                <Alert severity="info">
                                  Provider URLs and {deploymentTerm.lowercase}-bound secret references are collected by the generated install form. Secret values are not stored in this manifest.
                                </Alert>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      ) : null}
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Knowledge sources
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {contributionList(selectedVersionSummary?.contributions.knowledgeSourceIds ?? [])}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Shell modules
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {contributionList(selectedVersionSummary?.contributions.shellModuleIds ?? [])}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Curated template module
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {selectedVersionSummary?.contributions.templateCuratedModuleId ?? '—'}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Automation workflows
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {contributionList(selectedVersionSummary?.contributions.automationWorkflowIds ?? [])}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Inference profiles
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {contributionList(selectedVersionSummary?.contributions.inferenceProfileIds ?? [])}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Endpoint profiles
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {contributionList(selectedVersionSummary?.contributions.inferenceEndpointProfileRefs ?? [])}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Compatibility
                            </Typography>
                            <Stack spacing={1} sx={{ mt: 1 }}>
                              <Typography variant="body2">
                                {deploymentTerm.singular} targets: {contributionList(selectedVersionSummary?.compatibility.supportedDeploymentTargets ?? [])}
                              </Typography>
                              <Typography variant="body2">
                                Auth modes: {contributionList(selectedVersionSummary?.compatibility.supportedAuthModes ?? [])}
                              </Typography>
                              <Typography variant="body2">
                                Provider modes: {contributionList(selectedVersionSummary?.compatibility.supportedProviderModes ?? [])}
                              </Typography>
                              <Typography variant="body2">
                                Required capabilities: {contributionList(selectedVersionSummary?.compatibility.requiredCapabilities ?? [])}
                              </Typography>
                              <CommercialContent><Typography variant="body2">
                                Pricing: {pricingLabel(selectedVersionSummary?.pricing)}
                              </Typography></CommercialContent>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Install requirements
                            </Typography>
                            {selectedVersionSummary && selectedVersionSummary.installForm.length > 0 ? (
                              <Stack spacing={1} sx={{ mt: 1 }}>
                                {selectedVersionSummary.installForm.map((field) => (
                                  <Typography key={field.id} variant="body2">
                                    {field.label} ({field.type}){field.required ? ' required' : ' optional'}
                                    {field.options.length > 0 ? ` · ${field.options.join(', ')}` : ''}
                                  </Typography>
                                ))}
                              </Stack>
                            ) : (
                              <Typography sx={{ mt: 1 }}>No operator input is required for this version.</Typography>
                            )}
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>
                    {selectedVersionSummary?.recommendedPluginIds.length ? (
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="subtitle2" color="text.secondary">
                            Recommended add-ons
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
                            {selectedVersionSummary.recommendedPluginIds.map((pluginId) => (
                              <Chip
                                key={pluginId}
                                label={pluginId}
                                variant="outlined"
                                onClick={() => setSelectedPluginId(pluginId)}
                              />
                            ))}
                          </Stack>
                        </CardContent>
                      </Card>
                    ) : null}
                  </Stack>
                )}
              </CardContent>
            </Card>

            {selectedPlugin?.pluginType === 'TEMPLATE' && !deploymentScoped ? (
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <LaunchRoundedIcon color="secondary" />
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Bootstrap {deploymentTerm.lowercase} from template plugin
                      </Typography>
                    </Stack>
                    <Typography color="text.secondary">
                      Template plugins create a new {deploymentTerm.lowercase} through the standard control-plane create flow. They are not installed into an existing live {deploymentTerm.lowercase}.
                    </Typography>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <TextField
                          fullWidth
                          label={`${deploymentTerm.singular} name`}
                          value={templateName}
                          onChange={(event) => setTemplateName(event.target.value)}
                        />
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <TextField
                          fullWidth
                          label="Environment"
                          value={templateEnvironment}
                          onChange={(event) => setTemplateEnvironment(event.target.value)}
                        />
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <TextField
                          select
                          fullWidth
                          label="Base template"
                          value={templateId}
                          onChange={(event) => setTemplateId(event.target.value)}
                        >
                          {(deploymentTemplatesQuery.data ?? []).map((template) => (
                            <MenuItem key={template.id} value={template.id}>
                              {template.name}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Vector provisioning mode"
                          value={templateVectorProvisioningMode}
                          onChange={(event) => setTemplateVectorProvisioningMode(event.target.value)}
                          placeholder="Optional override"
                        />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Customer id"
                          value={templateCustomerId}
                          onChange={(event) => setTemplateCustomerId(event.target.value)}
                          placeholder="Optional"
                        />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Tenant id"
                          value={templateTenantId}
                          onChange={(event) => setTemplateTenantId(event.target.value)}
                          placeholder="Optional"
                        />
                      </Grid>
                    </Grid>
                    <Stack direction="row" spacing={1}>
                      <Button
                        variant="contained"
                        startIcon={<DownloadRoundedIcon />}
                        onClick={() => {
                          setFormError(null)
                          setSuccessMessage(null)
                          bootstrapMutation.mutate()
                        }}
                        disabled={bootstrapMutation.isPending || !selectedPlugin}
                      >
                        Bootstrap {deploymentTerm.lowercase}
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            ) : (
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <ExtensionRoundedIcon color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Install into target {deploymentTerm.lowercase}
                      </Typography>
                    </Stack>
                    <Typography color="text.secondary">
                      Action, data, inference-profile, specialist, and integration plugins compile into the target {deploymentTerm.lowercase} draft. Publish and apply are still required before they affect the live runtime.
                    </Typography>
                    {selectedPlugin?.pluginType === 'SPECIALIST' ? (
                      <Alert severity="info">
                        This installs exact references to specialists already packaged and verified in a promoted private runtime image. It does not upload executable specialist definitions.
                      </Alert>
                    ) : null}
                    {selectedPlugin?.pluginType === 'INTEGRATION' ? (
                      <Alert severity="info">
                        This installs a reviewed integration contract and allowlisted adapter. Provider credentials remain {deploymentTerm.lowercase}-bound secret references and are never exposed to the browser.
                      </Alert>
                    ) : null}
                    {!targetDeploymentId ? (
                      <Alert severity="info">Choose a target {deploymentTerm.lowercase} above to install or update this plugin.</Alert>
                    ) : !canEdit ? (
                      <Alert severity="warning">Your current assignment role is read-only for marketplace install changes on this {deploymentTerm.lowercase}.</Alert>
                    ) : null}
                    {targetDeploymentId ? (
                      draftQuery.isLoading ? (
                        <Alert severity="info">Loading target draft prerequisites…</Alert>
                      ) : (
                        <Alert
                          severity={providerSummary.missingBaseline ? 'warning' : 'success'}
                          action={providerSummary.missingBaseline ? (
                            <Button
                              color="inherit"
                              size="small"
                              onClick={() => navigate(`/providers?deploymentId=${encodeURIComponent(targetDeploymentId)}`)}
                            >
                              Open Providers
                            </Button>
                          ) : undefined}
                        >
                          Current {deploymentTerm.lowercase} providers: LLM {providerSummary.llmProvider || 'Not configured'} · Embeddings {providerSummary.embeddingProvider || 'Not configured'}.
                          {providerSummary.missingBaseline
                            ? ` This ${deploymentTerm.lowercase} still needs provider configuration before non-inference plugins can compile into the draft.`
                            : ' Provider baseline is present.'}
                        </Alert>
                      )
                    ) : null}
                    {selectedPlugin?.pluginType === 'INFERENCE_PROFILE' ? (
                      <Alert severity="info">
                        Inference-profile plugins write {deploymentTerm.lowercase} provider configuration themselves. Use this when you want the marketplace install to supply or override model endpoint settings.
                      </Alert>
                    ) : null}
                    {installFields.length > 0 ? (
                      <Alert severity="info">
                        This plugin declares install fields. Use the generated form below instead of hand-editing JSON.
                        Required fields: {installFields.filter((field) => field.required).map((field) => `${field.label} (${field.type})`).join(', ')}
                      </Alert>
                    ) : null}
                    <Alert severity={selectedVersionSummary?.pricing.requiresEntitlement ? 'warning' : 'info'}>
                      <CommercialContent>Pricing: {pricingLabel(selectedVersionSummary?.pricing)}. </CommercialContent>
                      {selectedVersionSummary?.pricing.requiresEntitlement
                        ? ` Paid marketplace installs remain out of the ${deploymentTerm.lowercase} draft until entitlement is activated.`
                        : ' Free marketplace installs are entitled immediately.'}
                    </Alert>
                    {installFieldErrors.length > 0 ? (
                      <Alert severity="warning">
                        Complete these fields before saving the install: {installFieldErrors.join(', ')}
                      </Alert>
                    ) : null}
                    {installFields.length > 0 ? (
                      <Grid container spacing={2}>
                        {configInstallFields.length > 0 ? (
                          <Grid item xs={12} md={6}>
                            <Card variant="outlined" sx={{ height: '100%' }}>
                              <CardContent>
                                <Stack spacing={2}>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Plugin config
                                  </Typography>
                                  {configInstallFields.map((field) => (
                                    <TextField
                                      key={field.id}
                                      select={field.type === 'select' || field.type === 'boolean'}
                                      fullWidth
                                      type={field.type === 'number' ? 'number' : field.type === 'url' ? 'url' : 'text'}
                                      label={field.label}
                                      value={readInstallFieldValue(field, installConfigState, installSecretRefsState)}
                                      onChange={(event) => setInstallField(field, event.target.value)}
                                      disabled={!canEdit || !targetDeploymentId}
                                      required={field.required}
                                      helperText={field.description ?? (
                                        field.type === 'url'
                                          ? 'Absolute URL expected when provided.'
                                          : field.type === 'boolean'
                                            ? 'Select true or false.'
                                            : undefined
                                      )}
                                    >
                                      {field.type === 'boolean' ? (
                                        [
                                          <MenuItem key="blank" value="">
                                            Not set
                                          </MenuItem>,
                                          <MenuItem key="true" value="true">
                                            True
                                          </MenuItem>,
                                          <MenuItem key="false" value="false">
                                            False
                                          </MenuItem>,
                                        ]
                                      ) : null}
                                      {field.type === 'select'
                                        ? field.options.map((option) => (
                                          <MenuItem key={option} value={option}>
                                            {option}
                                          </MenuItem>
                                        ))
                                        : null}
                                    </TextField>
                                  ))}
                                </Stack>
                              </CardContent>
                            </Card>
                          </Grid>
                        ) : null}
                        {secretInstallFields.length > 0 ? (
                          <Grid item xs={12} md={6}>
                            <Card variant="outlined" sx={{ height: '100%' }}>
                              <CardContent>
                                <Stack spacing={2}>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Secret references
                                  </Typography>
                                  {secretInstallFields.map((field) => (
                                    <TextField
                                      key={field.id}
                                      fullWidth
                                      label={field.label}
                                      value={readInstallFieldValue(field, installConfigState, installSecretRefsState)}
                                      onChange={(event) => setInstallField(field, event.target.value)}
                                      disabled={!canEdit || !targetDeploymentId}
                                      required={field.required}
                                      helperText={field.description ?? 'Enter the platform secret name, not the raw secret value.'}
                                    />
                                  ))}
                                </Stack>
                              </CardContent>
                            </Card>
                          </Grid>
                        ) : null}
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            multiline
                            minRows={8}
                            label="Generated config preview"
                            value={prettifyJson(installConfigState)}
                            InputProps={{ readOnly: true }}
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            multiline
                            minRows={8}
                            label="Generated secret refs preview"
                            value={prettifyJson(installSecretRefsState)}
                            InputProps={{ readOnly: true }}
                          />
                        </Grid>
                      </Grid>
                    ) : (
                      <Alert severity="info">This plugin does not require operator install inputs.</Alert>
                    )}
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {selectedInstall ? (
                        <Chip label={`Live state: ${selectedInstall.liveState}`} variant="outlined" />
                      ) : null}
                      {selectedInstall ? (
                        <Chip label={`Readiness: ${selectedInstall.readinessStatus}`} color={readinessColor(selectedInstall.readinessStatus)} variant="outlined" />
                      ) : null}
                      {selectedInstall ? (
                        <Chip
                          label={`Entitlement: ${selectedInstall.entitlement.status}`}
                          color={entitlementColor(selectedInstall.entitlement.status)}
                          variant="outlined"
                        />
                      ) : null}
                      <Button
                        variant="contained"
                        onClick={() => {
                          setFormError(null)
                          setSuccessMessage(null)
                          installMutation.mutate()
                        }}
                        disabled={!canEdit || !targetDeploymentId || installMutation.isPending || requiresDeploymentProviderBaseline || installFieldErrors.length > 0}
                      >
                        {selectedInstall ? 'Save install' : 'Install plugin'}
                      </Button>
                      {selectedInstall ? (
                        <>
                          <Button
                            variant="outlined"
                            onClick={() => {
                              setFormError(null)
                              setSuccessMessage(null)
                              statusMutation.mutate(selectedInstall.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')
                            }}
                            disabled={!canEdit || statusMutation.isPending}
                          >
                            {selectedInstall.status === 'ENABLED' ? 'Disable' : 'Enable'}
                          </Button>
                          <Button
                            variant="outlined"
                            onClick={() => {
                              setFormError(null)
                              setSuccessMessage(null)
                              resolveMutation.mutate()
                            }}
                            disabled={!targetDeploymentId || resolveMutation.isPending || requiresDeploymentProviderBaseline}
                          >
                            Recompile draft
                          </Button>
                          <Button
                            variant="outlined"
                            color="error"
                            onClick={() => {
                              setFormError(null)
                              setSuccessMessage(null)
                              deleteMutation.mutate()
                            }}
                            disabled={!canEdit || deleteMutation.isPending}
                          >
                            Remove install
                          </Button>
                        </>
                      ) : null}
                    </Stack>
                    {selectedInstall?.entitlement.requiresEntitlement && !deploymentScoped ? (
                      <Card variant="outlined">
                        <CardContent>
                          <Stack spacing={2}>
                            <Typography variant="subtitle2" color="text.secondary">
                              Entitlement lifecycle
                            </Typography>
                            <Grid container spacing={2}>
                              <Grid item xs={12} md={3}>
                                <TextField
                                  select
                                  fullWidth
                                  label="Entitlement status"
                                  value={entitlementStatus}
                                  onChange={(event) => setEntitlementStatus(event.target.value)}
                                  disabled={!canEdit}
                                >
                                  {['PENDING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED'].map((value) => (
                                    <MenuItem key={value} value={value}>
                                      {value}
                                    </MenuItem>
                                  ))}
                                </TextField>
                              </Grid>
                              <Grid item xs={12} md={3}>
                                <TextField
                                  fullWidth
                                  label="Grace ends"
                                  type="datetime-local"
                                  value={entitlementGraceEndsAt}
                                  onChange={(event) => setEntitlementGraceEndsAt(event.target.value)}
                                  InputLabelProps={{ shrink: true }}
                                  disabled={!canEdit}
                                />
                              </Grid>
                              <Grid item xs={12} md={3}>
                                <TextField
                                  fullWidth
                                  label="Access ends"
                                  type="datetime-local"
                                  value={entitlementAccessEndsAt}
                                  onChange={(event) => setEntitlementAccessEndsAt(event.target.value)}
                                  InputLabelProps={{ shrink: true }}
                                  disabled={!canEdit}
                                />
                              </Grid>
                              <Grid item xs={12} md={3}>
                                <TextField
                                  fullWidth
                                  label="Note"
                                  value={entitlementNote}
                                  onChange={(event) => setEntitlementNote(event.target.value)}
                                  disabled={!canEdit}
                                />
                              </Grid>
                            </Grid>
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <CommercialContent><Chip label={pricingLabel(selectedInstall.entitlement)} variant="outlined" /></CommercialContent>
                              <Chip
                                label={`Compiles into draft: ${selectedInstall.entitlement.entitledForCompilation ? 'yes' : 'no'}`}
                                color={selectedInstall.entitlement.entitledForCompilation ? 'success' : 'warning'}
                                variant="outlined"
                              />
                              <Button
                                variant="outlined"
                                onClick={() => {
                                  setFormError(null)
                                  setSuccessMessage(null)
                                  entitlementMutation.mutate()
                                }}
                                disabled={!canEdit || entitlementMutation.isPending}
                              >
                                Save entitlement
                              </Button>
                            </Stack>
                          </Stack>
                        </CardContent>
                      </Card>
                    ) : null}
                    {selectedInstall?.warnings.length ? (
                      <Alert severity="warning">
                        {selectedInstall.warnings.join(' ')}
                      </Alert>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            )}

            {targetDeploymentId ? (
              <>
                <Card>
                  <CardContent>
                    <Stack spacing={2}>
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        {deploymentTerm.singular} installs
                      </Typography>
                      {installsQuery.isLoading ? (
                        <Typography color="text.secondary">Loading install records…</Typography>
                      ) : (installsQuery.data ?? []).length === 0 ? (
                        <Alert severity="info">No marketplace installs are recorded for the selected target {deploymentTerm.lowercase} yet.</Alert>
                      ) : (
                        <List disablePadding>
                          {(installsQuery.data ?? []).map((install) => (
                            <ListItemButton
                              key={install.id}
                              onClick={() => setSelectedPluginId(install.pluginId)}
                              sx={{ borderRadius: 2, mb: 1 }}
                            >
                              <ListItemText
                                primary={
                                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                    <Typography sx={{ fontWeight: 700 }}>{install.pluginDisplayName}</Typography>
                                    <Chip size="small" label={install.pluginType} color={pluginTypeColor(install.pluginType)} />
                                    <Chip size="small" label={install.status} variant="outlined" />
                                    <Chip size="small" label={install.readinessStatus} color={readinessColor(install.readinessStatus)} variant="outlined" />
                                    <Chip size="small" label={install.liveState} color={install.liveState === 'LIVE' ? 'success' : 'default'} variant="outlined" />
                                  </Stack>
                                }
                                secondary={
                                  <Typography variant="body2" color="text.secondary">
                                    v{install.pluginVersion} · updated {formatTimestamp(install.updatedAt)}
                                  </Typography>
                                }
                              />
                            </ListItemButton>
                          ))}
                        </List>
                      )}
                    </Stack>
                  </CardContent>
                </Card>

                <Card>
                  <CardContent>
                    <Stack spacing={2}>
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        {deploymentTerm.singular} marketplace impact
                      </Typography>
                      {impactQuery.isLoading ? (
                        <Typography color="text.secondary">Loading {deploymentTerm.lowercase} impact…</Typography>
                      ) : impactQuery.data ? (
                        <>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip label={`${impactQuery.data.totalInstalls} installs`} variant="outlined" />
                            <Chip label={`${impactQuery.data.actionPluginCount} action plugins`} variant="outlined" />
                            <Chip label={`${impactQuery.data.dataPluginCount} data plugins`} variant="outlined" />
                            <Chip label={`${impactQuery.data.templatePluginCount} template plugins`} variant="outlined" />
                            <Chip label={`${impactQuery.data.automationPluginCount} automation plugins`} variant="outlined" />
                            <Chip label={`${impactQuery.data.inferenceProfilePluginCount} inference plugins`} variant="outlined" />
                            <Chip label={`${impactQuery.data.specialistPluginCount} specialist plugins`} variant="outlined" />
                          </Stack>
                          <Grid container spacing={2}>
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Action ids
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.actionIds)}
                                  </Typography>
                                </CardContent>
                              </Card>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Knowledge sources
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.knowledgeSourceIds)}
                                  </Typography>
                                </CardContent>
                              </Card>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Shell modules
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.shellModuleIds)}
                                  </Typography>
                                </CardContent>
                              </Card>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Automation workflows
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.automationWorkflowIds)}
                                  </Typography>
                                </CardContent>
                              </Card>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Inference profiles
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.inferenceProfileIds)}
                                  </Typography>
                                </CardContent>
                              </Card>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Endpoint profiles
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.inferenceEndpointProfileRefs)}
                                  </Typography>
                                </CardContent>
                              </Card>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Specialist bundles
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.specialistBundleIds)}
                                  </Typography>
                                </CardContent>
                              </Card>
                            </Grid>
                          </Grid>
                          {impactQuery.data.warnings.length > 0 ? (
                            <Alert severity="warning" icon={<WarningAmberRoundedIcon />}>
                              {impactQuery.data.warnings.join(' ')}
                            </Alert>
                          ) : null}
                          {impactQuery.data.recommendedPluginIds.length > 0 ? (
                            <Alert severity="info">
                              Recommended add-ons from template plugins: {impactQuery.data.recommendedPluginIds.join(', ')}
                            </Alert>
                          ) : null}
                        </>
                      ) : (
                        <Alert severity="info">Impact preview will appear once a target {deploymentTerm.lowercase} is selected.</Alert>
                      )}
                    </Stack>
                  </CardContent>
                </Card>
              </>
            ) : null}
          </Stack>
        </Grid>
      </Grid>

      {!deploymentScoped ? <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <ManageAccountsRoundedIcon color="primary" />
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    Publisher workspace
                  </Typography>
                </Stack>
                <Typography color="text.secondary">
                  Manage publisher identities, review posture, and external plugin submissions. Backend governance still enforces owner and admin boundaries.
                </Typography>
                {publishersQuery.isLoading ? (
                  <Typography color="text.secondary">Loading publishers…</Typography>
                ) : (publishersQuery.data ?? []).length === 0 ? (
                  <Alert severity="info">No publisher accounts exist yet. Create one to start submitting external plugins.</Alert>
                ) : (
                  <List disablePadding>
                    {(publishersQuery.data ?? []).map((publisher: MarketplacePublisherSummary) => (
                      <ListItemButton
                        key={publisher.id}
                        selected={publisher.id === selectedPublisherId}
                        onClick={() => setSelectedPublisherId(publisher.id)}
                        sx={{ borderRadius: 2, mb: 1 }}
                      >
                        <ListItemText
                          primary={
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography sx={{ fontWeight: 700 }}>{publisher.displayName}</Typography>
                              <Chip size="small" label={publisher.verificationStatus} color={publisherVerificationColor(publisher.verificationStatus)} />
                            </Stack>
                          }
                          secondary={
                            <Typography variant="body2" color="text.secondary">
                              {publisher.slug} · {publisher.submissionCount} submissions
                            </Typography>
                          }
                        />
                      </ListItemButton>
                    ))}
                  </List>
                )}
                <Divider />
                <Typography variant="subtitle2" color="text.secondary">
                  Create publisher
                </Typography>
                <TextField
                  fullWidth
                  label="Slug"
                  value={publisherSlug}
                  onChange={(event) => setPublisherSlug(event.target.value)}
                  placeholder="acme-marketplace"
                />
                <TextField
                  fullWidth
                  label="Display name"
                  value={publisherDisplayName}
                  onChange={(event) => setPublisherDisplayName(event.target.value)}
                  placeholder="Acme Marketplace"
                />
                <TextField
                  fullWidth
                  label="Contact email"
                  value={publisherContactEmail}
                  onChange={(event) => setPublisherContactEmail(event.target.value)}
                  placeholder="support@acme.example"
                />
                <Button
                  variant="contained"
                  startIcon={<CloudUploadRoundedIcon />}
                  onClick={() => {
                    setFormError(null)
                    setSuccessMessage(null)
                    createPublisherMutation.mutate()
                  }}
                  disabled={createPublisherMutation.isPending}
                >
                  Create publisher
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          <Stack spacing={3}>
            <Card>
              <CardContent>
                {!selectedPublisher ? (
                  <Alert severity="info">Select a publisher to review submissions or create a new one.</Alert>
                ) : publisherDetailQuery.isLoading ? (
                  <Typography color="text.secondary">Loading publisher detail…</Typography>
                ) : (
                  <Stack spacing={2}>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
                      <Box>
                        <Typography variant="h5" sx={{ fontWeight: 800 }}>
                          {selectedPublisher.displayName}
                        </Typography>
                        <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                          {selectedPublisher.slug} · {selectedPublisher.contactEmail}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                          Owner: {selectedPublisher.ownerUserId} · Created {formatTimestamp(selectedPublisher.createdAt)}
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={selectedPublisher.verificationStatus} color={publisherVerificationColor(selectedPublisher.verificationStatus)} />
                        <Chip label={selectedPublisher.status} color={publisherStatusColor(selectedPublisher.status)} variant="outlined" />
                        <Chip label={`${publisherDetailQuery.data?.submissions.length ?? 0} submissions`} variant="outlined" />
                      </Stack>
                    </Stack>
                    <Alert severity="info">
                      Admin review is required for verification changes and submission state transitions. Non-admin users can still create publishers and submit versions they own.
                    </Alert>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={4}>
                        <TextField
                          select
                          fullWidth
                          label="Verification"
                          value={publisherVerificationStatus}
                          onChange={(event) => setPublisherVerificationStatus(event.target.value)}
                        >
                          {['PENDING', 'VERIFIED', 'REJECTED'].map((value) => (
                            <MenuItem key={value} value={value}>
                              {value}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          select
                          fullWidth
                          label="Publisher status"
                          value={publisherStatus}
                          onChange={(event) => setPublisherStatus(event.target.value)}
                        >
                          {['ACTIVE', 'SUSPENDED'].map((value) => (
                            <MenuItem key={value} value={value}>
                              {value}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <Button
                          fullWidth
                          variant="outlined"
                          startIcon={<FactCheckRoundedIcon />}
                          onClick={() => {
                            setFormError(null)
                            setSuccessMessage(null)
                            updatePublisherVerificationMutation.mutate()
                          }}
                          disabled={updatePublisherVerificationMutation.isPending}
                          sx={{ height: '100%' }}
                        >
                          Save review posture
                        </Button>
                      </Grid>
                    </Grid>
                  </Stack>
                )}
              </CardContent>
            </Card>

            {selectedPublisher ? (
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <CloudUploadRoundedIcon color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Submit plugin version
                      </Typography>
                    </Stack>
                    <Typography color="text.secondary">
                      Submit an external plugin manifest for review. The catalog only exposes versions after admin validation and publication.
                    </Typography>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Plugin slug override"
                          value={submissionPluginSlug}
                          onChange={(event) => setSubmissionPluginSlug(event.target.value)}
                          placeholder="Optional"
                        />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          select
                          fullWidth
                          label="Release channel"
                          value={submissionReleaseChannel}
                          onChange={(event) => setSubmissionReleaseChannel(event.target.value)}
                        >
                          {['DRAFT', 'BETA', 'GA'].map((value) => (
                            <MenuItem key={value} value={value}>
                              {value}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Review notes"
                          value={submissionReviewNotes}
                          onChange={(event) => setSubmissionReviewNotes(event.target.value)}
                          placeholder="Optional admin notes for validate/publish/reject"
                        />
                      </Grid>
                    </Grid>
                    <TextField
                      fullWidth
                      multiline
                      minRows={16}
                      label="Manifest JSON"
                      value={submissionManifestJson}
                      onChange={(event) => setSubmissionManifestJson(event.target.value)}
                    />
                    <Button
                      variant="contained"
                      startIcon={<CloudUploadRoundedIcon />}
                      onClick={() => {
                        setFormError(null)
                        setSuccessMessage(null)
                        createSubmissionMutation.mutate()
                      }}
                      disabled={createSubmissionMutation.isPending}
                    >
                      Submit version
                    </Button>
                  </Stack>
                </CardContent>
              </Card>
            ) : null}

            {selectedPublisher ? (
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <PublishRoundedIcon color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Submission review queue
                      </Typography>
                    </Stack>
                    {(publisherDetailQuery.data?.submissions ?? []).length === 0 ? (
                      <Alert severity="info">No submissions exist for this publisher yet.</Alert>
                    ) : (
                      <Stack spacing={2}>
                        {(publisherDetailQuery.data?.submissions ?? []).map((submission) => (
                          <Card key={submission.pluginVersionId} variant="outlined">
                            <CardContent>
                              <Stack spacing={2}>
                                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
                                  <Box>
                                    <Typography sx={{ fontWeight: 700 }}>
                                      {submission.pluginDisplayName} · {submission.version}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                                      {submission.pluginType} · {submission.pluginSlug} · {submission.releaseChannel}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                                      Submitted {formatTimestamp(submission.submittedAt)} by {submission.submittedByActorId ?? '—'}
                                      {submission.reviewedAt ? ` · reviewed ${formatTimestamp(submission.reviewedAt)} by ${submission.reviewedByActorId ?? '—'}` : ''}
                                    </Typography>
                                  </Box>
                                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                    <Chip label={submission.status} color={submissionStatusColor(submission.status)} />
                                    {submission.bundleSha256 ? <Chip label={submission.bundleSha256.slice(0, 12)} variant="outlined" /> : null}
                                  </Stack>
                                </Stack>
                                {submission.reviewNotes ? (
                                  <Alert severity="info">{submission.reviewNotes}</Alert>
                                ) : null}
                                <TextField
                                  fullWidth
                                  multiline
                                  minRows={8}
                                  label="Submission manifest"
                                  value={prettifyJson(submission.manifest)}
                                  InputProps={{ readOnly: true }}
                                />
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Button
                                    variant="outlined"
                                    startIcon={<FactCheckRoundedIcon />}
                                    onClick={() => {
                                      setFormError(null)
                                      setSuccessMessage(null)
                                      reviewSubmissionMutation.mutate({
                                        pluginVersionId: submission.pluginVersionId,
                                        action: 'validate',
                                      })
                                    }}
                                    disabled={reviewSubmissionMutation.isPending || !['SUBMITTED', 'REJECTED'].includes(submission.status)}
                                  >
                                    Validate
                                  </Button>
                                  <Button
                                    variant="contained"
                                    startIcon={<PublishRoundedIcon />}
                                    onClick={() => {
                                      setFormError(null)
                                      setSuccessMessage(null)
                                      reviewSubmissionMutation.mutate({
                                        pluginVersionId: submission.pluginVersionId,
                                        action: 'publish',
                                      })
                                    }}
                                    disabled={reviewSubmissionMutation.isPending || submission.status !== 'VALIDATED'}
                                  >
                                    Publish
                                  </Button>
                                  <Button
                                    variant="outlined"
                                    color="error"
                                    onClick={() => {
                                      setFormError(null)
                                      setSuccessMessage(null)
                                      reviewSubmissionMutation.mutate({
                                        pluginVersionId: submission.pluginVersionId,
                                        action: 'reject',
                                      })
                                    }}
                                    disabled={reviewSubmissionMutation.isPending || !['SUBMITTED', 'VALIDATED'].includes(submission.status)}
                                  >
                                    Reject
                                  </Button>
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            ) : null}
          </Stack>
        </Grid>
      </Grid> : null}
    </Stack></ConfigurationSurface>
  )
}
