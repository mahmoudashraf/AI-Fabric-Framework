import { useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, FormControlLabel, IconButton, List, ListItemButton, ListItemText, MenuItem,
  Stack, Switch, Tab, Tabs, TextField, Tooltip, Typography } from '@mui/material'
import AddOutlined from '@mui/icons-material/AddOutlined'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import PlayArrowOutlined from '@mui/icons-material/PlayArrowOutlined'
import RefreshOutlined from '@mui/icons-material/RefreshOutlined'
import ContentCopyOutlined from '@mui/icons-material/ContentCopyOutlined'
import { fetchDeploymentDraft, fetchDeploymentMarketplaceInstalls, fetchDeploymentWorkspace,
  fetchMarketplacePluginVersion, updateDeploymentDraft, type DeploymentDraftResponse } from '../api/platformApi'
import { executeManagedTask, fetchAppliedManagedTasks, type ManagedTaskResult } from '../api/managedTasksApi'
import { isJsonObject, type JsonObject } from '../actions/actionDefinitionModel'
import { parseExactObject, stringifyExactJson } from '../lib/losslessJson'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { specialistAuthority, type SpecialistAuthority } from '../components/customer/specialistAssistanceForm'
import { useBrainEditor, useBrainWorkspace } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'
import { editSchemaField, fieldTypes, managedTasksConfigIdentity, newTask, parseTaskSchema, readTasksEditor, TASK_LIMIT,
  taskBehaviorPatch, taskCodeValid, taskErrors, taskRuntimeExample, tasksErrors,
  tasksIdentity, taskTestBlock, type TaskEditor, type TasksEditor } from './managedTasks'

type View = 'outcome' | 'resources' | 'try' | 'use'
const jsonSx = { fontFamily: 'monospace', fontSize: 12, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', m: 0 } as const

export function BrainTasksPage() {
  const { brainId, targetProfileId, release, href } = useBrainWorkspace()
  const { workspace } = useDeploymentWorkspace()
  const location = useLocation()
  const navigate = useNavigate()
  const client = useQueryClient()
  const [view, setView] = useState<View>('outcome')
  const [loaded, setLoaded] = useState<DeploymentDraftResponse | null>(null)
  const [editor, setEditor] = useState<TasksEditor>({ enabled: false, tasks: [] })
  const [baseline, setBaseline] = useState('')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [conflicting, setConflicting] = useState<DeploymentDraftResponse | null>(null)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [remove, setRemove] = useState(false)
  const localEdits = useRef(false)
  const dirty = Boolean(loaded && tasksIdentity(editor) !== baseline)
  const canEdit = workspace?.access.canEdit === true && import.meta.env.VITE_SMART_BRAIN_REVIEW !== 'true'
  const canOperate = workspace?.access.canOperate === true && import.meta.env.VITE_SMART_BRAIN_REVIEW !== 'true'
  const draft = useQuery({ queryKey: ['deployment-draft', brainId], queryFn: () => fetchDeploymentDraft(brainId), retry: false })
  const installs = useQuery({ queryKey: ['deployment-marketplace-installs', brainId],
    queryFn: () => fetchDeploymentMarketplaceInstalls(brainId), enabled: view === 'resources' && Boolean(loaded), retry: false })
  const installed = (installs.data ?? []).filter(install => ['ENABLED', 'BOOTSTRAPPED'].includes(install.status.toUpperCase()) && install.entitlement?.entitledForCompilation)
  const versions = useQueries({ queries: installed.map(install => ({
    queryKey: ['managed-task-resource-version', brainId, install.pluginVersionId],
    queryFn: () => fetchMarketplacePluginVersion(install.pluginId, install.pluginVersion, workspace?.deployment.binding?.customerId),
    enabled: view === 'resources', retry: false,
  })) })
  const resourceLoading = installs.isPending || versions.some(query => query.isPending)
  let resourceError = installs.error?.message ?? versions.find(query => query.isError)?.error?.message ?? null
  let authority: SpecialistAuthority = { sources: [], readTools: [] }
  if (loaded && installs.data && !resourceLoading && !resourceError) {
    try { authority = specialistAuthority(brainId, loaded, installs.data, versions.flatMap(query => query.data ? [query.data] : [])) }
    catch (error) { resourceError = (error as Error).message }
  }
  let routeCode = ''
  try { routeCode = decodeURIComponent(location.pathname.split('/')[4] ?? '') } catch { routeCode = 'invalid-route' }
  const selected = editor.tasks.find(task => task.key === selectedKey)
    ?? editor.tasks.find(task => task.code === routeCode) ?? (!routeCode ? editor.tasks[0] : undefined)

  function adopt(value: DeploymentDraftResponse, selectedCode = selected?.code) {
    try {
      const next = readTasksEditor(value.behaviorConfig)
      setEditor(next); setBaseline(tasksIdentity(next)); setLoaded(value); setLoadError(null)
      setSelectedKey(next.tasks.find(task => task.code === selectedCode)?.key ?? null); setConflicting(null); localEdits.current = false
    } catch (error) { setLoadError((error as Error).message) }
  }
  useEffect(() => { if (draft.data && !localEdits.current) adopt(draft.data) }, [draft.data])
  useEffect(() => { setSelectedKey(null); setView('outcome') }, [location.pathname])
  const save = useMutation({
    retry: false,
    mutationFn: async () => {
      if (!canEdit || !loaded) throw new Error('Draft editing permission is required.')
      const current = await fetchDeploymentDraft(brainId)
      // revisionNumber identifies the draft, not each save. This pre-read catches existing
      // task edits; the shared draft API still has a read/PUT race, not an atomic CAS contract.
      if (current.id !== loaded.id || current.revisionNumber !== loaded.revisionNumber
        || managedTasksConfigIdentity(current.behaviorConfig) !== managedTasksConfigIdentity(loaded.behaviorConfig)) {
        setConflicting(current)
        throw new Error('The saved draft changed. Your edits are retained. Discard to load the latest draft before editing again.')
      }
      return updateDeploymentDraft(current.id, { behaviorConfig: taskBehaviorPatch(current.behaviorConfig, editor) })
    },
    onSuccess: value => {
      adopt(value); client.setQueryData(['deployment-draft', brainId], value)
      void client.invalidateQueries({ queryKey: ['deployment-workspace', brainId] })
      void client.invalidateQueries({ queryKey: ['deployment-validation'] })
    },
  })
  const [discardError, setDiscardError] = useState<string | null>(null)
  const [discarding, setDiscarding] = useState(false)
  async function discard() {
    setDiscarding(true); setDiscardError(null)
    try {
      const current = conflicting ?? await fetchDeploymentDraft(brainId)
      adopt(current); client.setQueryData(['deployment-draft', brainId], current); save.reset()
    } catch (error) { setDiscardError((error as Error).message) }
    finally { setDiscarding(false) }
  }
  const [result, setResult] = useState<{ scope: string; value: ManagedTaskResult } | null>(null)
  const [input, setInput] = useState('{}')
  const [correlationId, setCorrelationId] = useState('')
  const [copied, setCopied] = useState(false)
  const controller = useRef<AbortController | null>(null)
  const scope = `${brainId}:${targetProfileId}:${release?.id}:${selected?.key ?? ''}`
  const activeScope = useRef(scope)
  activeScope.current = scope
  useEffect(() => { setInput('{}'); setCorrelationId(''); setCopied(false); setResult(null) }, [scope])
  useEffect(() => () => controller.current?.abort(), [])
  const runtimeView = view === 'try' || view === 'use'
  const applied = useQuery({ queryKey: ['managed-tasks-applied', brainId, targetProfileId, release?.id],
    queryFn: ({ signal }) => fetchAppliedManagedTasks(brainId, targetProfileId, signal),
    enabled: runtimeView && Boolean(targetProfileId && release), retry: false,
    refetchOnWindowFocus: false, refetchOnReconnect: false, staleTime: 0 })
  const appliedTask = applied.data?.tasks.find(task => task.code === selected?.code)
  const gate = taskTestBlock({ dirty, targetProfileId,
    releaseVersionId: release?.deploymentVersionId, hasAppliedTask: Boolean(appliedTask) })
  const pendingPublishedChanges = Boolean(workspace && release && (workspace.lifecycle.savedDraftMatchesLatestPublished === false
    || workspace.lifecycle.latestPublishedVersionId !== release.deploymentVersionId))
  let parsedInput: JsonObject | null = null
  let inputError: string | null = null
  try { parsedInput = parseExactObject(input, 'Task input') } catch (error) { inputError = (error as Error).message }
  const execute = useMutation({
    retry: false,
    mutationFn: async () => {
      if (!canOperate || gate || !appliedTask || !parsedInput || !loaded || !applied.data) throw new Error(gate ?? 'An applied task and valid input are required.')
      const requestScope = scope
      controller.current = new AbortController()
      // Recheck at the explicit command boundary; opening an editor never contacts a runtime.
      const current = await fetchDeploymentDraft(brainId)
      const currentWorkspace = await fetchDeploymentWorkspace(brainId)
      const currentApplied = await fetchAppliedManagedTasks(brainId, targetProfileId, controller.current.signal)
      if (current.revisionNumber !== loaded.revisionNumber || current.id !== loaded.id
        || managedTasksConfigIdentity(current.behaviorConfig) !== managedTasksConfigIdentity(loaded.behaviorConfig)) {
        throw new Error('The saved task configuration changed. Reload it before testing the applied version.')
      }
      const block = taskTestBlock({ dirty, targetProfileId, releaseVersionId: release?.deploymentVersionId,
        hasAppliedTask: currentApplied.tasks.some(task => task.code === appliedTask.code) })
      if (block || !currentWorkspace.access.canOperate || currentApplied.configurationVersion !== release?.deploymentVersionId
        || currentApplied.configurationVersion !== applied.data.configurationVersion
        || currentApplied.taskConfigurationHash !== applied.data.taskConfigurationHash) throw new Error(block ?? 'The applied configuration or your access changed. Refresh the applied tasks before running again.')
      const value = await executeManagedTask(brainId, appliedTask.code, targetProfileId,
        { input: parsedInput, ...(correlationId.trim() ? { correlationId: correlationId.trim() } : {}) },
        appliedTask.deadlineSeconds, controller.current.signal)
      return { scope: requestScope, value }
    },
    onSuccess: value => { if (value.scope === activeScope.current) setResult(value) },
  })
  const errors = useMemo(() => tasksErrors(editor), [editor])
  const busy = save.isPending || discarding || execute.isPending
  const locked = !canEdit || !loaded || busy
  useBrainEditor({ dirty, busy,
    canSave: canEdit && dirty && errors.length === 0 && !conflicting && !loadError,
    save: canEdit ? () => save.mutateAsync().catch(() => undefined) : undefined,
    discard: canEdit ? discard : undefined,
    message: !canEdit ? 'Read-only task configuration' : dirty ? 'Unsaved task changes' : save.isSuccess ? 'Draft saved; apply an update to use it' : 'Editing shared draft; applied tasks are tested separately',
    error: save.isError ? save.error.message : discardError,
    retainedNavigationPaths: [href('tasks').split('?')[0], ...editor.tasks.filter(task => taskCodeValid(task.code)).map(task => href(`tasks/${encodeURIComponent(task.code)}`).split('?')[0])] })
  function change(next: TasksEditor) {
    if (locked) return
    localEdits.current = tasksIdentity(next) !== baseline
    setEditor(next); save.reset(); setResult(null); setDiscardError(null)
  }
  function patch(patch: Partial<TaskEditor>) {
    if (selected) { setSelectedKey(selected.key); change({ ...editor, tasks: editor.tasks.map(task => task.key === selected.key ? { ...task, ...patch } : task) }) }
  }
  const resourceDisabled = locked || resourceLoading || Boolean(resourceError)
  const currentResult = result?.scope === scope ? result.value : null
  const example = taskRuntimeExample(applied.data?.runtimeBaseUrl ?? null, appliedTask?.code ?? selected?.code ?? '<task-code>', parsedInput ?? {})

  return <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0, minWidth: 0 }}>
    <BrainPageHeader stackOnMobile title="Managed Tasks" description="Give your services a focused AI outcome with a defined input and answer."
      action={<Button variant="contained" startIcon={<AddOutlined />} disabled={locked || editor.tasks.length >= TASK_LIMIT || Boolean(loadError)} onClick={() => {
        const task = newTask(); change({ ...editor, tasks: [...editor.tasks, task] }); setSelectedKey(task.key); setView('outcome')
      }}>Add task</Button>} />
    {draft.isPending && <CircularProgress size={24} aria-label="Loading tasks" />}
    {draft.isError && <Alert severity="error" action={<Button onClick={() => void draft.refetch()}>Retry</Button>}>{draft.error.message}</Alert>}
    {loadError && <Alert severity="error">{loadError} <Button component={RouterLink} to={href('advanced/configuration')}>Open configuration</Button></Alert>}
    {conflicting && <Alert severity="warning" sx={{ mb: 1 }}>Your local edits are retained. Discard loads the newer saved draft.</Alert>}
    {loaded && !loadError && <>
      <Stack direction="row" alignItems="center" gap={2} flexWrap="wrap" sx={{ pb: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <FormControlLabel control={<Switch inputProps={{ role: 'switch' }} checked={editor.enabled} disabled={locked} onChange={(_, enabled) => change({ ...editor, enabled })} />} label="Enable Managed Tasks" />
        <Typography variant="caption" color="text.secondary">{editor.tasks.length} / {TASK_LIMIT} tasks · Uses this Brain's AI model · Read-only resources</Typography>
      </Stack>
      {editor.enabled && editor.tasks.length === 0 && <Alert severity="info" sx={{ mt: 1 }}>Add at least one task before saving Managed Tasks as enabled.</Alert>}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: '235px minmax(0, 1fr)' }, flex: 1, minHeight: 0 }}>
        <Box component="nav" aria-label="Tasks" sx={{ ...brainPaneSx, borderRight: { md: 1 }, borderBottom: { xs: 1, md: 0 }, borderColor: 'divider', pr: { md: 2 }, py: 1, maxHeight: { xs: 180, md: 'none' } }}>
          {!editor.tasks.length && <Typography color="text.secondary" variant="body2" sx={{ py: 2 }}>No tasks yet. Add one to define what your service needs.</Typography>}
          <List disablePadding>{editor.tasks.map(task => <ListItemButton key={task.key} selected={selected?.key === task.key} disabled={busy} onClick={() => {
            setSelectedKey(task.key); setView('outcome'); execute.reset()
            if (taskCodeValid(task.code)) navigate({ pathname: href(`tasks/${encodeURIComponent(task.code)}`).split('?')[0], search: location.search })
          }} sx={{ px: 1.25, borderRadius: 1, mb: .5 }}>
            <ListItemText primary={task.name || 'Untitled task'} secondary={task.code || 'Choose a task code'}
              primaryTypographyProps={{ fontSize: 13, fontWeight: 600, sx: { overflowWrap: 'anywhere' } }} secondaryTypographyProps={{ fontSize: 11, sx: { overflowWrap: 'anywhere' } }} />
            {taskErrors(task, editor.tasks).length > 0 && <Chip size="small" variant="outlined" color="warning" label="Edit" sx={{ ml: .5 }} />}
          </ListItemButton>)}</List>
        </Box>
        <Box sx={{ minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column', pl: { md: 3 }, pt: 1.5 }}>
          {!selected ? <Typography variant="body2" color="text.secondary" sx={{ py: 3 }}>{routeCode ? 'This task is not in the saved draft. Select another task, or add one.' : 'Define a reusable task, then publish and apply it through Updates.'}</Typography> : <>
            <Stack direction="row" gap={1} alignItems="center" sx={{ mb: 1 }}>
              <Typography component="h2" variant="subtitle1" fontWeight={650} sx={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}>{selected.name || 'New task'}</Typography>
              <Tooltip title="Remove task from draft"><span><IconButton aria-label="Remove task" disabled={locked} onClick={() => setRemove(true)}><DeleteOutline fontSize="small" /></IconButton></span></Tooltip>
            </Stack>
            <Tabs value={view} onChange={(_, value) => { setView(value); execute.reset(); setCopied(false) }} variant="scrollable" scrollButtons="auto" aria-label="Task sections" sx={{ mb: 2, borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}>
              <Tab value="outcome" label="Outcome" disabled={busy} /><Tab value="resources" label="Resources" disabled={busy} /><Tab value="try" label="Try" disabled={busy} /><Tab value="use" label="Use in your app" disabled={busy} />
            </Tabs>
            <Box sx={{ ...brainPaneSx, flex: 1, pt: 1, pb: 2 }}>
              {view === 'outcome' && <Stack gap={2.5}>
                {!canEdit && <Alert severity="info">You can view tasks. Changes require draft editing permission.</Alert>}
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 2 }}>
                  <TextField required label="Task name" size="small" value={selected.name} disabled={locked} inputProps={{ maxLength: 120 }} onChange={e => patch({ name: e.target.value })} placeholder="Assess a support request" />
                  <TextField required label="Task code" size="small" value={selected.code} disabled={locked} inputProps={{ maxLength: 40 }} onChange={e => patch({ code: e.target.value })} placeholder="assess-request" helperText="Start with a lowercase letter; use lowercase letters, numbers and single hyphens. Used in your API URL." />
                </Box>
                <TextField required label="Purpose" size="small" value={selected.description} disabled={locked} inputProps={{ maxLength: 500 }} onChange={e => patch({ description: e.target.value })} multiline minRows={2} helperText="What should this task help your service decide or produce?" />
                <TextField required label="Instructions" value={selected.instructions} disabled={locked} multiline minRows={4} maxRows={10} inputProps={{ maxLength: 8000 }} onChange={e => patch({ instructions: e.target.value })} helperText="Describe the outcome, how to use the input, and what to do when information is missing. Instructions are saved on this Brain." />
                <TaskSchemaEditor key={`input-${selected.key}`} label="Input" value={selected.inputSchemaText} disabled={locked} onChange={inputSchemaText => patch({ inputSchemaText })} />
                <TextField select label="Return an answer as" size="small" value={selected.outputMode} disabled={locked} onChange={e => patch({ outputMode: e.target.value as 'JSON' | 'TEXT' })} sx={{ maxWidth: 320 }}>
                  <MenuItem value="JSON">Structured JSON</MenuItem><MenuItem value="TEXT">Text</MenuItem>
                </TextField>
                {selected.outputMode === 'JSON' ? <TaskSchemaEditor key={`output-${selected.key}`} label="Output" value={selected.outputSchemaText} disabled={locked} onChange={outputSchemaText => patch({ outputSchemaText })} />
                  : <Typography variant="body2" color="text.secondary">The runtime returns a text answer using its internal answer schema. Your JSON output schema is retained while editing if you switch back, but is not sent in TEXT mode.</Typography>}
                <Box component="details" sx={{ borderTop: 1, borderColor: 'divider', pt: 1.5 }}>
                  <Typography component="summary" variant="body2" fontWeight={600} sx={{ cursor: 'pointer' }}>Advanced execution limits</Typography>
                  <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} sx={{ mt: 2 }}>
                    <TextField label="Deadline (seconds)" required type="number" size="small" value={selected.deadlineSeconds} disabled={locked} inputProps={{ min: 5, max: 120, step: 1 }} onChange={e => patch({ deadlineSeconds: Number(e.target.value) })} helperText="5–120 seconds" />
                    <TextField label="Maximum output tokens" required type="number" size="small" value={selected.maxOutputTokens} disabled={locked} inputProps={{ min: 128, max: 4096, step: 1 }} onChange={e => patch({ maxOutputTokens: Number(e.target.value) })} helperText="128–4,096 tokens" />
                  </Stack>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>Uses the Brain's model and structured-output policy. No separate task model, internet search or automatic extra retry.</Typography>
                </Box>
                {taskErrors(selected, editor.tasks).length > 0 && <Alert severity="warning"><Typography variant="body2" fontWeight={600}>Before saving</Typography>{taskErrors(selected, editor.tasks).map(error => <Typography variant="body2" key={error}>{error}</Typography>)}</Alert>}
              </Stack>}
              {view === 'resources' && <Stack gap={3}>
                <Typography variant="body2" color="text.secondary">Allow only the existing Brain resources this task needs. Both lists are optional. No write actions are available.</Typography>
                {resourceLoading && <CircularProgress size={20} aria-label="Loading task resources" />}
                {resourceError && <Alert severity="error">{resourceError} Saved selections are retained; no resource authority was inferred.</Alert>}
                {!resourceLoading && !resourceError && <>
                  <ResourceSelection label="Knowledge sources" values={selected.vectorSpaces} options={authority.sources.map(source => ({ code: source.id, label: `${source.label} · ${source.plugin}` }))} disabled={resourceDisabled} onChange={vectorSpaces => patch({ vectorSpaces })} />
                  <ResourceSelection label="Read actions" values={selected.readActions} options={authority.readTools.map(action => ({ code: action.id, label: `${action.label} · ${action.plugin}` }))} disabled={resourceDisabled} onChange={readActions => patch({ readActions })} />
                </>}
                <Button component={RouterLink} to={href('capabilities')} sx={{ alignSelf: 'flex-start' }}>Manage Brain capabilities</Button>
              </Stack>}
              {runtimeView && <Stack gap={2}>
                <Alert severity="info">{view === 'try' ? 'Tests run the applied task, not your draft. Provider usage applies.' : 'These integration details describe the applied release, not unsaved changes.'} The summary is saved release configuration, not a live runtime probe.</Alert>
                {pendingPublishedChanges && <Alert severity="warning">This view uses the applied version. Pending changes are not used.</Alert>}
                {gate && <Alert severity="warning" action={<Button component={RouterLink} to={href('updates')}>Open Updates</Button>}>{gate}</Alert>}
                {!canOperate && view === 'try' && <Alert severity="info">Running a task requires permission to operate this Brain.</Alert>}
                {targetProfileId && release && <Stack direction="row" gap={1} alignItems="center" justifyContent="space-between">
                  <Typography variant="body2" sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>{applied.data ? `Applied version: ${applied.data.configurationVersion}` : 'Applied tasks'}</Typography>
                  <Tooltip title="Refresh applied tasks"><span><IconButton aria-label="Refresh applied tasks" disabled={applied.isFetching || execute.isPending} onClick={() => void applied.refetch()}><RefreshOutlined fontSize="small" /></IconButton></span></Tooltip>
                </Stack>}
                {applied.isFetching && <CircularProgress size={20} aria-label="Loading applied tasks" />}
                {applied.isError && <Alert severity="error">{applied.error.message}</Alert>}
                {view === 'try' ? <>
                  <TextField label="Task input JSON" multiline minRows={5} maxRows={14} value={input} disabled={execute.isPending} onChange={e => { setInput(e.target.value); execute.reset(); setResult(null) }} error={Boolean(inputError)} helperText={inputError ?? 'An object matching the applied input schema. No request is sent until you select Run task.'} inputProps={{ style: { fontFamily: 'monospace', fontSize: 13 } }} />
                  {appliedTask && <Box component="details"><Typography component="summary" variant="body2" sx={{ cursor: 'pointer' }}>Applied input schema</Typography><Box component="pre" sx={{ ...jsonSx, mt: 1 }}>{stringifyExactJson(appliedTask.inputSchema, 2)}</Box></Box>}
                  <TextField label="Correlation ID (optional)" size="small" value={correlationId} disabled={execute.isPending} inputProps={{ maxLength: 160 }} onChange={e => setCorrelationId(e.target.value)} helperText="A reference for tracing this request; not an idempotency guarantee." />
                  <Stack direction="row" gap={1}>
                    <Button variant="contained" startIcon={execute.isPending ? <CircularProgress size={16} /> : <PlayArrowOutlined />} disabled={!canOperate || Boolean(gate) || !appliedTask || applied.isFetching || applied.isError || Boolean(inputError) || execute.isPending} onClick={() => { setResult(null); execute.mutate() }}>{execute.isPending ? 'Running task...' : 'Run task'}</Button>
                    {execute.isPending && <Button onClick={() => controller.current?.abort()}>Stop waiting</Button>}
                  </Stack>
                  {execute.isError && <Alert severity="error">{execute.error.message}</Alert>}
                  {currentResult && <Box component="section" aria-label="Task result" sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }}>
                    <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center"><Typography component="h3" variant="subtitle2">Result</Typography><Chip size="small" variant="outlined" color={currentResult.status === 'SUCCEEDED' && !currentResult.failure ? 'success' : 'warning'} label={currentResult.status} /></Stack>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', my: 1 }}>Run {currentResult.runId} · Version {currentResult.configurationVersion}</Typography>
                    {currentResult.failure && <Alert severity="warning" sx={{ mb: 1 }}>{currentResult.failure.code}: {currentResult.failure.message}</Alert>}
                    <Box component="pre" sx={jsonSx}>{typeof currentResult.output === 'string' ? currentResult.output : stringifyExactJson(currentResult.output, 2)}</Box>
                    <Box component="details" sx={{ mt: 2 }}><Typography component="summary" variant="body2" sx={{ cursor: 'pointer' }}>Full response and evidence</Typography><Box component="pre" sx={{ ...jsonSx, mt: 1 }}>{stringifyExactJson(currentResult, 2)}</Box></Box>
                  </Box>}
                </> : <>
                  <Typography component="h3" variant="subtitle2">Call this task from your backend</Typography>
                  <Typography variant="body2" color="text.secondary">Keep runtime credentials on your server. Use your existing private authentication with scope <Box component="code">task:{appliedTask?.code ?? (selected.code || '<task-code>')}:execute</Box>. This example contains placeholders only.</Typography>
                  <Box sx={{ bgcolor: 'action.hover', border: 1, borderColor: 'divider', borderRadius: 1, p: 2, minWidth: 0 }}><Box component="pre" sx={jsonSx}>{example}</Box></Box>
                  <Button variant="outlined" startIcon={<ContentCopyOutlined />} sx={{ alignSelf: 'flex-start' }} disabled={!appliedTask || Boolean(gate)} onClick={async () => {
                    try { await navigator.clipboard.writeText(example); setCopied(true) } catch { setCopied(false) }
                  }}>{copied ? 'Copied' : 'Copy request'}</Button>
                  <Typography variant="body2" color="text.secondary">Synchronous response: run ID, task code, status, version/hash, output, failure and evidence. No background jobs, event callbacks or business writes in this release. JSON shape is validated; AI conclusions still need your application's judgement.</Typography>
                </>}
              </Stack>}
            </Box>
          </>}
        </Box>
      </Box>
    </>}
    <Dialog open={remove} onClose={() => setRemove(false)} maxWidth="xs" fullWidth>
      <DialogTitle>Remove {selected?.name || 'this task'}?</DialogTitle>
      <DialogContent>This removes the task from your draft only. The applied version keeps working until you explicitly apply an update.</DialogContent>
      <DialogActions><Button onClick={() => setRemove(false)}>Cancel</Button><Button color="error" variant="contained" disabled={locked} onClick={() => {
        change({ ...editor, tasks: editor.tasks.filter(task => task.key !== selected?.key) }); setSelectedKey(null); setRemove(false)
        navigate({ pathname: href('tasks').split('?')[0], search: location.search })
      }}>Remove task</Button></DialogActions>
    </Dialog>
  </Box>
}

function ResourceSelection({ label, options, values, disabled, onChange }: {
  label: string; options: { code: string; label: string }[]; values: string[]; disabled: boolean; onChange: (value: string[]) => void
}) {
  const missing = values.filter(value => !options.some(option => option.code === value))
  return <Box component="section">
    <Typography component="h3" variant="subtitle2" sx={{ mb: 1 }}>{label} <Typography component="span" variant="caption" color="text.secondary">(optional)</Typography></Typography>
    {!options.length && <Typography variant="body2" color="text.secondary">None configured on this Brain. A task can run without {label.toLowerCase()}.</Typography>}
    <Stack>{[...options, ...missing.map(code => ({ code, label: `${code} (not in saved catalogue)` }))].map(option => <FormControlLabel key={option.code}
      control={<Checkbox size="small" checked={values.includes(option.code)} disabled={disabled || missing.includes(option.code) && !values.includes(option.code)}
        onChange={(_, checked) => onChange(checked ? [...values, option.code] : values.filter(value => value !== option.code))} />}
      label={<Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{option.label}</Typography>} />)}</Stack>
    {missing.length > 0 && <Alert severity="warning" sx={{ mt: 1 }}>Some saved references are no longer in the Brain's catalogue. They are retained until you remove or repair them.</Alert>}
  </Box>
}

function TaskSchemaEditor({ label, value, disabled, onChange }: { label: string; value: string; disabled: boolean; onChange: (value: string) => void }) {
  const [advanced, setAdvanced] = useState(false)
  const [name, setName] = useState('')
  const [type, setType] = useState('string')
  const [error, setError] = useState<string | null>(null)
  let schema: JsonObject | null = null
  let parseError: string | null = null
  try { schema = parseTaskSchema(value, `${label} schema`) } catch (caught) { parseError = (caught as Error).message }
  const properties = schema && isJsonObject(schema.properties) ? schema.properties : {}
  const required = Array.isArray(schema?.required) ? schema.required : []
  function edit(field: string, operation: Parameters<typeof editSchemaField>[2]) {
    try { onChange(editSchemaField(value, field, operation)); setError(null); if (operation.add) setName('') }
    catch (caught) { setError((caught as Error).message) }
  }
  return <Box component="section" aria-label={`${label} fields`} sx={{ borderTop: 1, borderColor: 'divider', pt: 2 }}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} sx={{ mb: 1.5 }}>
      <Typography component="h3" variant="subtitle2">{label} fields</Typography>
      <FormControlLabel sx={{ mr: 0 }} control={<Switch size="small" inputProps={{ role: 'switch' }} checked={advanced} onChange={(_, checked) => setAdvanced(checked)} />} label={<Typography variant="caption">Advanced JSON</Typography>} />
    </Stack>
    {error && <Alert severity="warning" sx={{ mb: 1 }}>{error}</Alert>}
    {advanced ? <TextField fullWidth label={`${label} schema JSON`} value={value} disabled={disabled} multiline minRows={7} maxRows={18} onChange={e => onChange(e.target.value)}
      error={Boolean(parseError)} helperText={parseError ?? 'Object schema, up to 16 KiB. Local #/ references and advanced constraints are preserved. External/dynamic references, $id identifiers and literal $ref fields/data are not supported.'}
      inputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }} /> : parseError ? <Alert severity="warning">{parseError} Open Advanced JSON to repair the schema. Your text is retained.</Alert> : <>
      {Object.entries(properties).length === 0 && <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>No fields defined. An empty object is allowed unless the schema says otherwise.</Typography>}
      {Object.entries(properties).map(([field, definition]) => {
        const simple = isJsonObject(definition) && typeof definition.type === 'string' && (fieldTypes as readonly string[]).includes(definition.type)
        return <Box key={field} sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr) 130px', sm: 'minmax(0, 1fr) 140px 100px 36px' }, gap: 1, alignItems: 'center', mb: 1 }}>
          <Typography variant="body2" component="code" sx={{ overflowWrap: 'anywhere' }}>{field}</Typography>
          <TextField select size="small" value={simple ? String(definition.type) : 'advanced'} disabled={disabled || !simple}
            inputProps={{ 'aria-label': `${label} ${field} type` }} onChange={e => edit(field, { type: e.target.value })}>
            {!simple && <MenuItem value="advanced">Advanced</MenuItem>}{fieldTypes.map(type => <MenuItem key={type} value={type}>{type}</MenuItem>)}
          </TextField>
          <FormControlLabel sx={{ m: 0 }} control={<Checkbox size="small" checked={required.includes(field)} disabled={disabled} onChange={(_, required) => edit(field, { required })} />} label={<Typography variant="caption">Required</Typography>} />
          <Tooltip title={`Remove ${field} and its constraints`}><span><IconButton size="small" disabled={disabled} aria-label={`Remove ${label.toLowerCase()} field ${field}`} onClick={() => edit(field, { remove: true })}><DeleteOutline fontSize="small" /></IconButton></span></Tooltip>
        </Box>
      })}
      <Stack direction={{ xs: 'column', sm: 'row' }} gap={1} sx={{ mt: 1.5 }}>
        <TextField label={`${label} field name`} size="small" value={name} disabled={disabled} onChange={e => setName(e.target.value)} sx={{ flex: 1 }} placeholder="message" />
        <TextField select label={`${label} field type`} size="small" value={type} disabled={disabled} sx={{ minWidth: 140 }} onChange={e => setType(e.target.value)}>{fieldTypes.map(type => <MenuItem key={type} value={type}>{type}</MenuItem>)}</TextField>
        <Button startIcon={<AddOutlined />} disabled={disabled || !name.trim()} onClick={() => edit(name, { add: true, type })}>Add field</Button>
      </Stack>
    </>}
  </Box>
}
