import { useEffect, useRef, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Box, Button, Checkbox, Chip, CircularProgress, Divider, Drawer, FormControlLabel,
  IconButton, MenuItem, Stack, Switch, TextField, Tooltip, Typography,
} from '@mui/material'
import AddRounded from '@mui/icons-material/AddRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import EditOutlined from '@mui/icons-material/EditOutlined'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import AccountTreeOutlined from '@mui/icons-material/AccountTreeOutlined'
import {
  fetchDeploymentDraft, fetchDeploymentMarketplaceInstalls, fetchDeploymentVersionManifest,
  fetchDeploymentVersions, fetchDeploymentWorkspace, fetchMarketplacePluginVersion,
  fetchPlatformAuthSession, updateDeploymentDraft,
  type DeploymentDraftResponse, type DeploymentMarketplaceInstallSummary, type PlatformAuthSessionSummary,
} from '../../api/platformApi'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { isCustomerSession } from '../../auth/customerSession'
import { useCustomerEditorSection } from './CustomerDetailSections'
import {
  compiledSpecialistAssistance, emptySpecialistAssistance, readSpecialistAssistance,
  specialistAssistanceError, specialistAuthority, specialistBehaviorUpdate,
  type SpecialistArea, type SpecialistAssistance, type SpecialistAuthority, type SpecialistChoice, type SpecialistResolution,
} from './specialistAssistanceForm'

const errorText = (error: unknown) => error instanceof Error ? error.message : 'The capability configuration could not be loaded.'
const activeInstalls = (rows: DeploymentMarketplaceInstallSummary[]) => rows.filter(row =>
  ['ENABLED', 'BOOTSTRAPPED'].includes(row.status.toUpperCase()) && row.entitlement?.entitledForCompilation)

export function CustomerSpecialistAssistance({ deploymentId }: { deploymentId: string }) {
  const { session } = usePlatformAuth()
  if (!isCustomerSession(session) || !deploymentId) return null
  const scope = JSON.stringify([session.actorId, session.customerId, session.authenticationMode, deploymentId])
  return <SpecialistAssistanceScope key={scope} scope={scope} session={session} deploymentId={deploymentId} />
}

function SpecialistAssistanceScope({ scope, session, deploymentId }: {
  scope: string; session: PlatformAuthSessionSummary; deploymentId: string
}) {
  const workspace = useQuery({ queryKey: ['customer-specialist-assistance', scope, 'workspace'], retry: false,
    queryFn: async () => {
      const result = await fetchDeploymentWorkspace(deploymentId)
      if (result.deployment.id !== deploymentId || result.deployment.binding?.customerId !== session.customerId) {
        throw new Error('This deployment is not available in the current customer account.')
      }
      return result
    } })
  const draft = useQuery({ queryKey: ['customer-specialist-assistance', scope, 'draft'], retry: false,
    enabled: workspace.isSuccess && workspace.data.deployment.behaviorType === 'CONVERSATIONAL',
    queryFn: async () => {
      const result = await fetchDeploymentDraft(deploymentId)
      if (result.deploymentId !== deploymentId) throw new Error('The deployment draft scope changed.')
      return result
    } })
  if (workspace.isError) return <Alert severity="error">{errorText(workspace.error)}</Alert>
  if (workspace.isPending) return <CircularProgress size={20} aria-label="Loading support capabilities" />
  if (workspace.data.deployment.behaviorType !== 'CONVERSATIONAL') return null
  if (draft.isError) return <Alert severity="error">{errorText(draft.error)}</Alert>
  if (!draft.data) return <CircularProgress size={20} aria-label="Loading specialist draft" />
  let value: SpecialistAssistance
  try { value = readSpecialistAssistance(draft.data.behaviorConfig) ?? emptySpecialistAssistance() }
  catch (error) { return <Alert severity="error">{errorText(error)}</Alert> }
  return <AssistanceForm key={draft.data.id} initialDraft={draft.data} initialValue={value}
    scope={scope} session={session} canEdit={workspace.data.access.canEdit} />
}

function AssistanceForm({ initialDraft, initialValue, scope, session, canEdit }: {
  initialDraft: DeploymentDraftResponse; initialValue: SpecialistAssistance; scope: string
  session: PlatformAuthSessionSummary; canEdit: boolean
}) {
  const client = useQueryClient()
  const depth = useCustomerEditorSection() ?? 'configuration'
  const deploymentId = initialDraft.deploymentId
  const [baseline, setBaseline] = useState(initialDraft)
  const [value, setValue] = useState(() => structuredClone(initialValue))
  const [area, setArea] = useState<SpecialistArea | null>(null)
  const [plan, setPlan] = useState<SpecialistResolution | null>(null)
  const [notice, setNotice] = useState('')
  const [reloadError, setReloadError] = useState('')
  const alive = useRef(true)
  useEffect(() => { alive.current = true; return () => { alive.current = false } }, [])
  const installs = useQuery({ queryKey: ['customer-specialist-assistance', scope, 'installs'], retry: false,
    queryFn: () => fetchDeploymentMarketplaceInstalls(deploymentId), enabled: value.enabled })
  const installed = activeInstalls(installs.data ?? [])
  const versions = useQueries({ queries: installed.map(install => ({
    queryKey: ['customer-specialist-assistance', scope, 'plugin', install.pluginVersionId], retry: false,
    queryFn: () => fetchMarketplacePluginVersion(install.pluginId, install.pluginVersion), enabled: value.enabled,
  })) })
  const loadingCatalogue = value.enabled && (installs.isPending || versions.some(query => query.isPending))
  let authority: SpecialistAuthority = { sources: [], readTools: [] }
  let catalogueError = installs.isError ? errorText(installs.error) : versions.find(query => query.isError)?.error
    ? errorText(versions.find(query => query.isError)!.error) : ''
  if (!loadingCatalogue && !catalogueError && value.enabled && installs.data) {
    try { authority = specialistAuthority(deploymentId, baseline, installs.data, versions.flatMap(query => query.data ? [query.data] : [])) }
    catch (error) { catalogueError = errorText(error) }
  }
  const savedValue = readSpecialistAssistance(baseline.behaviorConfig) ?? emptySpecialistAssistance()
  const dirty = JSON.stringify(savedValue) !== JSON.stringify(value)
  const validationError = value.enabled && !loadingCatalogue && !catalogueError ? specialistAssistanceError(value, authority) : null
  const save = useMutation({ retry: false, gcTime: 0,
    mutationFn: async () => {
      const proposed = structuredClone(value)
      const [identity, workspace, current] = await Promise.all([
        fetchPlatformAuthSession(), fetchDeploymentWorkspace(deploymentId), fetchDeploymentDraft(deploymentId),
      ])
      if (!alive.current || !isCustomerSession(identity) || identity.actorId !== session.actorId
        || identity.customerId !== session.customerId || identity.authenticationMode !== session.authenticationMode
        || workspace.deployment.id !== deploymentId || workspace.deployment.binding?.customerId !== session.customerId
        || !workspace.access.canEdit) throw new Error('Customer access changed. Reopen this deployment before saving.')
      if (current.deploymentId !== deploymentId || current.id !== baseline.id || current.revisionNumber !== baseline.revisionNumber
        || JSON.stringify(current.behaviorConfig) !== JSON.stringify(baseline.behaviorConfig)) {
        throw new Error('The behavior draft changed. Reload the saved capabilities before saving your changes.')
      }
      if (proposed.enabled) {
        const rows = await fetchDeploymentMarketplaceInstalls(deploymentId)
        const manifests = await Promise.all(activeInstalls(rows).map(install => fetchMarketplacePluginVersion(install.pluginId, install.pluginVersion)))
        const message = specialistAssistanceError(proposed, specialistAuthority(deploymentId, current, rows, manifests))
        if (message) throw new Error(message)
      }
      if (!alive.current) throw new Error('The selected deployment changed; no draft was saved.')
      const result = await updateDeploymentDraft(current.id, { behaviorConfig: specialistBehaviorUpdate(current.behaviorConfig, proposed) })
      if (result.deploymentId !== deploymentId || result.id !== current.id) throw new Error('The saved capability response has a different deployment scope.')
      if (!readSpecialistAssistance(result.behaviorConfig)) throw new Error('The saved capability response is incomplete. Reload the draft to check its state.')
      return result
    }, onSuccess: async result => {
      if (!alive.current) return
      setBaseline(result)
      setValue(readSpecialistAssistance(result.behaviorConfig) ?? emptySpecialistAssistance())
      setNotice('Support capabilities saved to the draft. Publish and apply a version to update the deployment.')
      await client.invalidateQueries({ predicate: query => query.queryKey.includes(deploymentId)
        || query.queryKey.includes(scope) || query.queryKey[0] === 'customer-deployment-portfolio' })
    } })
  const editable = canEdit && !save.isPending
  const selectionDisabled = !editable || loadingCatalogue || Boolean(catalogueError)
  const reload = async () => {
    setNotice('')
    setReloadError('')
    const current = await fetchDeploymentDraft(deploymentId)
    if (!alive.current || current.deploymentId !== deploymentId) return
    setBaseline(current); setValue(readSpecialistAssistance(current.behaviorConfig) ?? emptySpecialistAssistance())
  }
  return <Stack spacing={2.5} component="section" aria-label="Support capabilities" sx={{ pb: 3, borderBottom: 1, borderColor: 'divider' }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }}>
      <Stack direction="row" spacing={1} alignItems="center"><AccountTreeOutlined color="primary" />
        <Typography variant="h6">Support capabilities</Typography>{dirty ? <Chip size="small" label="Unsaved" /> : null}
      </Stack>
      <FormControlLabel label="Specialist assistance" control={<Switch checked={value.enabled} disabled={!editable}
        onChange={(_, enabled) => { setValue(current => ({ ...current, enabled })); setNotice('') }} />} />
    </Stack>
    {notice ? <Alert severity="success">{notice}</Alert> : null}
    {save.isError ? <Alert severity="error">{errorText(save.error)}</Alert> : null}
    {reloadError ? <Alert severity="error">{reloadError}</Alert> : null}
    {!canEdit ? <Alert severity="info">This deployment is read-only for your account.</Alert> : null}
    {!value.enabled ? <Typography variant="body2" color="text.secondary">Optional specialist assistance is disabled.</Typography> : <>
      {loadingCatalogue ? <Stack direction="row" gap={1}><CircularProgress size={18} /><Typography variant="body2">Loading installed knowledge and READ tools...</Typography></Stack> : null}
      {catalogueError ? <Alert severity="error">{catalogueError}</Alert> : null}
      {depth === 'configuration' ? <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1fr) 290px' }, gap: 3 }}>
        <Stack spacing={3} sx={{ minWidth: 0 }}>
          <Box><SectionHeading title="Support areas" addLabel="Add support area" disabled={!editable || value.areas.length >= 8}
            onAdd={() => setArea({ id: `area-${crypto.randomUUID().slice(0, 12)}`, name: '', purpose: '', instructions: '', vectorSpaces: [], readActions: [] })} />
            {!value.areas.length ? <Typography color="text.secondary" variant="body2" sx={{ py: 2 }}>No support areas configured.</Typography> : value.areas.map(item => {
              const used = value.resolutions.some(resolution => resolution.areaIds.includes(item.id))
              return <CapabilityRow key={item.id} name={item.name} description={item.purpose} secondary={`${item.vectorSpaces.length} knowledge sources / ${item.readActions.length} READ tools`}
                disabled={!editable} onEdit={() => setArea(structuredClone(item))} removeDisabled={used}
                removeHint={used ? 'Remove this area from its resolution plans first' : `Remove ${item.name}`}
                onRemove={() => setValue(current => ({ ...current, areas: current.areas.filter(row => row.id !== item.id) }))} />
            })}
          </Box>
          <Box><SectionHeading title="Resolution plans" addLabel="Add resolution plan" disabled={!editable || !value.areas.length || value.resolutions.length >= 8}
            onAdd={() => setPlan({ id: `plan-${crypto.randomUUID().slice(0, 12)}`, name: '', objective: '', areaIds: [], allowParallel: false })} />
            {!value.resolutions.length ? <Typography color="text.secondary" variant="body2" sx={{ py: 2 }}>No coordinated resolution plans configured.</Typography> : value.resolutions.map(item => <CapabilityRow key={item.id} name={item.name} description={item.objective}
              secondary={item.areaIds.length === 1 ? 'Single-area check' : `${item.areaIds.length} areas / ${item.allowParallel ? 'Adaptive checks, parallel allowed' : 'Adaptive sequential checks'}`} disabled={!editable}
              onEdit={() => setPlan(structuredClone(item))} onRemove={() => setValue(current => ({ ...current, resolutions: current.resolutions.filter(row => row.id !== item.id) }))} />)}
          </Box>
        </Stack>
        <SyntheticCheckPreview value={value} />
      </Box> : <>
        <Typography variant="subtitle2">Execution limits</Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0,1fr))' }, gap: 2 }}>
          {([['maxDecisions', 'Manager decisions', 1, 8], ['maxWorkerInvocations', 'Worker invocations', 1, 8],
            ['maxParallelWorkers', 'Parallel workers', 1, 4], ['deadlineSeconds', 'Deadline (seconds)', 5, 120]] as const).map(([key, label, min, max]) =>
            <TextField key={key} label={label} type="number" size="small" value={value.limits[key]} disabled={!editable}
              inputProps={{ min, max, step: 1 }} onChange={event => setValue(current => ({ ...current, limits: { ...current.limits, [key]: Number(event.target.value) } }))} />)}
        </Box>
      </>}
      {validationError ? <Alert severity="warning">{validationError}</Alert> : null}
    </>}
    {depth === 'advanced' ? <PublishedSpecialistPreview deploymentId={deploymentId} scope={scope} /> : null}
    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
      <Button variant="outlined" startIcon={<SaveRounded />} disabled={!editable || !dirty || Boolean(validationError)
        || value.enabled && (loadingCatalogue || Boolean(catalogueError))} onClick={() => { setNotice(''); save.mutate() }}>
        {save.isPending ? 'Saving...' : 'Save support capabilities'}
      </Button>
      <Button startIcon={<RefreshRounded />} disabled={save.isPending || !dirty} onClick={() => { void reload().catch(error => setReloadError(errorText(error))) }}>Discard local changes</Button>
      <Button component={RouterLink} to={`/revisions?${new URLSearchParams({ deploymentId })}`}>Versions & updates</Button>
    </Stack>
    {area ? <AreaEditor key={area.id} initial={area} authority={authority} disabled={selectionDisabled} onClose={() => setArea(null)} onUse={next => {
      setValue(current => ({ ...current, areas: current.areas.some(item => item.id === next.id) ? current.areas.map(item => item.id === next.id ? next : item) : [...current.areas, next] })); setArea(null)
    }} /> : null}
    {plan ? <ResolutionEditor key={plan.id} initial={plan} areas={value.areas} disabled={!editable} onClose={() => setPlan(null)} onUse={next => {
      setValue(current => ({ ...current, resolutions: current.resolutions.some(item => item.id === next.id) ? current.resolutions.map(item => item.id === next.id ? next : item) : [...current.resolutions, next] })); setPlan(null)
    }} /> : null}
  </Stack>
}

function SectionHeading({ title, addLabel, disabled, onAdd }: { title: string; addLabel: string; disabled: boolean; onAdd: () => void }) {
  return <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1}>
    <Typography variant="subtitle1" fontWeight={600}>{title}</Typography><Button size="small" startIcon={<AddRounded />} disabled={disabled} onClick={onAdd}>{addLabel}</Button>
  </Stack>
}
function CapabilityRow({ name, description, secondary, disabled, onEdit, onRemove, removeDisabled, removeHint }: {
  name: string; description: string; secondary: string; disabled: boolean; onEdit: () => void; onRemove: () => void; removeDisabled?: boolean; removeHint?: string
}) {
  return <Stack direction="row" justifyContent="space-between" gap={1} sx={{ py: 2, borderBottom: 1, borderColor: 'divider', minWidth: 0 }}>
    <Box sx={{ minWidth: 0, overflowWrap: 'anywhere' }}><Typography fontWeight={600} variant="body2">{name}</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>{description}</Typography><Typography variant="caption" color="text.secondary">{secondary}</Typography></Box>
    <Stack direction="row" alignItems="flex-start"><Tooltip title={`Edit ${name}`}><span><IconButton size="small" aria-label={`Edit ${name}`} disabled={disabled} onClick={onEdit}><EditOutlined fontSize="small" /></IconButton></span></Tooltip>
      <Tooltip title={removeHint ?? `Remove ${name}`}><span><IconButton size="small" aria-label={`Remove ${name}`} disabled={disabled || removeDisabled} onClick={onRemove}><DeleteOutlineRounded fontSize="small" /></IconButton></span></Tooltip></Stack>
  </Stack>
}

function EditorDrawer({ title, onClose, children, onUse, disabled }: { title: string; onClose: () => void; children: React.ReactNode; onUse: () => void; disabled: boolean }) {
  return <Drawer anchor="right" open onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 520 }, maxWidth: '100%' } }}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ p: 2.5, borderBottom: 1, borderColor: 'divider' }}><Typography variant="h6">{title}</Typography><Tooltip title="Close editor"><IconButton aria-label="Close capability editor" onClick={onClose}><CloseRounded /></IconButton></Tooltip></Stack>
    <Stack spacing={2.5} sx={{ p: 3, flex: 1 }}>{children}</Stack>
    <Stack direction="row" justifyContent="flex-end" gap={1} sx={{ p: 2, borderTop: 1, borderColor: 'divider' }}><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={disabled} onClick={onUse}>Use changes</Button></Stack>
  </Drawer>
}

function ChoicePicker({ label, value, choices, disabled, onChange }: { label: string; value: string[]; choices: SpecialistChoice[]; disabled: boolean; onChange: (ids: string[]) => void }) {
  const missing = value.filter(id => !choices.some(choice => choice.id === id))
  return <TextField fullWidth select size="small" label={label} value={value} disabled={disabled}
    SelectProps={{ multiple: true, renderValue: selected => (selected as string[]).map(id => choices.find(item => item.id === id)?.label ?? `${id} (unavailable)`).join(', ') }}
    onChange={event => {
      const selected = typeof event.target.value === 'string' ? event.target.value.split(',') : event.target.value as string[]
      onChange(selected.filter(id => choices.some(choice => choice.id === id) || value.includes(id)))
    }}>
    {choices.map(choice => <MenuItem key={choice.id} value={choice.id}><Checkbox size="small" checked={value.includes(choice.id)} /><Box sx={{ whiteSpace: 'normal' }}><Typography variant="body2">{choice.label}</Typography><Typography variant="caption" color="text.secondary">{choice.plugin} / {choice.id}</Typography></Box></MenuItem>)}
    {missing.map(id => <MenuItem key={id} value={id}><Checkbox size="small" checked /><Typography variant="body2" color="warning.main">{id} (no longer available)</Typography></MenuItem>)}
    {!choices.length && !missing.length ? <MenuItem disabled>No installed choices</MenuItem> : null}
  </TextField>
}

function AreaEditor({ initial, authority, disabled, onClose, onUse }: { initial: SpecialistArea; authority: SpecialistAuthority; disabled: boolean; onClose: () => void; onUse: (value: SpecialistArea) => void }) {
  const [area, setArea] = useState(initial)
  const incomplete = !area.name.trim() || !area.purpose.trim() || !area.instructions.trim()
  return <EditorDrawer title={initial.name ? 'Edit support area' : 'New support area'} onClose={onClose} disabled={disabled || incomplete} onUse={() => onUse(area)}>
    <TextField autoFocus label="Area name" value={area.name} inputProps={{ maxLength: 120 }} onChange={e => setArea(current => ({ ...current, name: e.target.value }))} disabled={disabled} />
    <TextField label="Purpose" multiline minRows={2} value={area.purpose} inputProps={{ maxLength: 500 }} onChange={e => setArea(current => ({ ...current, purpose: e.target.value }))} disabled={disabled} />
    <TextField label="Instructions" multiline minRows={5} value={area.instructions} helperText={`${area.instructions.length} / 4000 characters`} inputProps={{ maxLength: 4000 }} onChange={e => setArea(current => ({ ...current, instructions: e.target.value }))} disabled={disabled} />
    <ChoicePicker label="Approved knowledge sources" value={area.vectorSpaces} choices={authority.sources} disabled={disabled} onChange={vectorSpaces => setArea(current => ({ ...current, vectorSpaces }))} />
    <ChoicePicker label="Installed READ tools" value={area.readActions} choices={authority.readTools} disabled={disabled} onChange={readActions => setArea(current => ({ ...current, readActions }))} />
    <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>Area ID: {area.id}</Typography>
  </EditorDrawer>
}

function ResolutionEditor({ initial, areas, disabled, onClose, onUse }: { initial: SpecialistResolution; areas: SpecialistArea[]; disabled: boolean; onClose: () => void; onUse: (value: SpecialistResolution) => void }) {
  const [plan, setPlan] = useState(initial)
  const invalidParallel = plan.allowParallel && plan.areaIds.length < 2
  return <EditorDrawer title={initial.name ? 'Edit resolution plan' : 'New resolution plan'} onClose={onClose} disabled={disabled || !plan.name.trim() || !plan.objective.trim() || !plan.areaIds.length || invalidParallel} onUse={() => onUse(plan)}>
    <TextField autoFocus label="Plan name" value={plan.name} inputProps={{ maxLength: 120 }} onChange={e => setPlan(current => ({ ...current, name: e.target.value }))} disabled={disabled} />
    <TextField label="Objective" multiline minRows={4} value={plan.objective} inputProps={{ maxLength: 1000 }} onChange={e => setPlan(current => ({ ...current, objective: e.target.value }))} disabled={disabled} />
    <ChoicePicker label="Support areas" value={plan.areaIds} choices={areas.map(item => ({ id: item.id, label: item.name, plugin: 'This deployment' }))} disabled={disabled}
      onChange={areaIds => setPlan(current => ({ ...current, areaIds }))} />
    <FormControlLabel label="Allow independent parallel checks" control={<Checkbox checked={plan.allowParallel} disabled={disabled || !plan.allowParallel && plan.areaIds.length < 2}
      onChange={e => setPlan(current => ({ ...current, allowParallel: e.target.checked }))} />} />
    {invalidParallel ? <Alert severity="warning">Select at least two independent areas or disable parallel checks.</Alert> : null}
    <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>Plan ID: {plan.id}</Typography>
  </EditorDrawer>
}

function SyntheticCheckPreview({ value }: { value: SpecialistAssistance }) {
  return <Box component="aside" aria-label="Synthetic capability preview" sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 2, alignSelf: 'start', minWidth: 0 }}>
    <Stack direction="row" alignItems="center" gap={1} sx={{ mb: 2 }}><Typography variant="subtitle2">Answer preview</Typography><Chip size="small" label="Sample" variant="outlined" /></Stack>
    <Typography variant="body2" sx={{ mb: 2 }}>Guidance and current information will appear together here.</Typography>
    <Typography variant="overline" color="text.secondary">Resolution checks</Typography>
    {value.areas.slice(0, 3).map(area => <Stack key={area.id} direction="row" justifyContent="space-between" gap={1} sx={{ borderBottom: 1, borderColor: 'divider', py: 1.5 }}><Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{area.name || 'Untitled area'}</Typography><Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>Not run</Typography></Stack>)}
    {!value.areas.length ? <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>No areas selected</Typography> : null}
    <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 2 }}>Synthetic preview. No AI requests or resource checks run.</Typography>
  </Box>
}

function PublishedSpecialistPreview({ deploymentId, scope }: { deploymentId: string; scope: string }) {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const versions = useQuery({ queryKey: ['customer-specialist-assistance', scope, 'published-versions'], retry: false,
    queryFn: () => fetchDeploymentVersions(deploymentId) })
  const choices = (versions.data ?? []).filter(item => item.deploymentId === deploymentId && item.status === 'PUBLISHED')
  const selected = selectedId === null ? choices[0] : choices.find(item => item.id === selectedId)
  const manifest = useQuery({ queryKey: ['customer-specialist-assistance', scope, 'compiled', selected?.id], retry: false,
    enabled: Boolean(selected), queryFn: async () => compiledSpecialistAssistance(await fetchDeploymentVersionManifest(deploymentId, selected!.id), deploymentId, selected!.id) })
  return <Box component="section" aria-label="Published specialist resources">
    <Divider sx={{ mb: 2 }} /><Typography variant="subtitle2" sx={{ mb: 2 }}>Immutable published resources</Typography>
    {versions.isError || manifest.isError ? <Alert severity="error">{errorText(versions.error ?? manifest.error)}</Alert> : null}
    {versions.isPending || selected && manifest.isPending ? <CircularProgress size={20} aria-label="Loading published specialist resources" /> : null}
    {!versions.isPending && !versions.isError && !choices.length ? <Alert severity="info">Exact references are generated when a version is published. No references are inferred from this draft.</Alert> : null}
    {choices.length ? <TextField select size="small" label="Published version" value={selected?.id ?? ''} onChange={e => setSelectedId(e.target.value)} sx={{ minWidth: 240, mb: 2 }}>{choices.map(item => <MenuItem key={item.id} value={item.id}>{item.versionLabel}</MenuItem>)}</TextField> : null}
    {selectedId !== null && !selected && versions.isSuccess ? <Alert severity="warning">The selected published version is unavailable. No other version was selected.</Alert> : null}
    {selected && !manifest.isError && manifest.data ? <Stack spacing={1.5}>
      <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{selected.id} / {manifest.data.contentHash}</Typography>
      {manifest.data.invocations.map(item => <Box key={item.id} sx={{ borderBottom: 1, borderColor: 'divider', pb: 1 }}><Typography variant="body2" fontWeight={600}>{item.name}</Typography><Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{item.targetType} / {item.targetRef}</Typography></Box>)}
      <Box component="details"><Typography component="summary" variant="body2" sx={{ cursor: 'pointer' }}>Compiled resource definitions</Typography><Box component="pre" sx={{ m: 0, mt: 1, p: 2, bgcolor: 'action.hover', borderRadius: 1, maxHeight: 320, overflow: 'auto', fontSize: 11 }}>{JSON.stringify(manifest.data.resources, null, 2)}</Box></Box>
    </Stack> : selected && manifest.isSuccess && !manifest.data ? <Alert severity="info">Specialist assistance is not enabled in this published version.</Alert> : null}
  </Box>
}
