import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Chip, Divider, FormControlLabel, MenuItem, Stack, Switch, TextField, Typography } from '@mui/material'
import SaveRounded from '@mui/icons-material/SaveRounded'
import { fetchDeploymentDraft, fetchDeploymentWorkspace, fetchPlatformAuthSession, fetchWorkflowActions, updateDeploymentDraft, type DeploymentDraftResponse } from '../../api/platformApi'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { useCustomerEditorSection } from './CustomerDetailSections'
import { effectiveQuickLimits, goalPolicyFields, newGoalPolicy, segmentPolicyFields, validGoalPolicy, type WorkflowGoalPolicy } from '../../assistant/workflowGoalPolicy'

type Profile = { id: string; name: string; purpose: string; instructions: string; readActions: string[]; writeActions: string[]; delegatedProfiles?: string[]; [key: string]: unknown }
type Policy = { maxActionSteps: number; maxWriteSteps: number; maxModelCalls: number; maxCorrections: number; deadlineSeconds: number; [key: string]: unknown }
type Workflow = { enabled: boolean; profiles: Profile[]; policy: Policy; goal?: WorkflowGoalPolicy | null; defaultProfileId: string; taskBindings: { task: string; profileId: string }[]; [key: string]: unknown }
const record = (value: unknown): value is Record<string, unknown> => Boolean(value && typeof value === 'object' && !Array.isArray(value))
function read(behavior: unknown): Workflow | null {
  if (!record(behavior) || !record(behavior.workflowAssistance)) return null
  const value = behavior.workflowAssistance
  if (typeof value.enabled !== 'boolean' || !Array.isArray(value.profiles) || !record(value.policy)
    || typeof value.defaultProfileId !== 'string' || !Array.isArray(value.taskBindings)) throw new Error('The workflow configuration cannot be edited with this form.')
  if (value.profiles.some(profile => !record(profile) || ['id', 'name', 'purpose', 'instructions'].some(field => typeof profile[field] !== 'string')
    || !Array.isArray(profile.readActions) || !Array.isArray(profile.writeActions)
    || profile.delegatedProfiles !== undefined && (!Array.isArray(profile.delegatedProfiles) || profile.delegatedProfiles.some(id => typeof id !== 'string')))) throw new Error('A workflow profile is incomplete. The saved configuration is unchanged.')
  if (value.goal != null && (!record(value.goal) || typeof value.goal.enabled !== 'boolean')) throw new Error('The goal policy cannot be edited with this form. The saved configuration is unchanged.')
  return structuredClone(value) as Workflow
}

export function CustomerWorkflowAssistance({ draft, canEdit }: { draft: DeploymentDraftResponse; canEdit: boolean }) {
  let value: Workflow | null
  try { value = read(draft.behaviorConfig) } catch (error) { return <Alert severity="error">{(error as Error).message}</Alert> }
  return value ? <WorkflowForm key={draft.id} initial={draft} workflow={value} canEdit={canEdit} /> : null
}

function WorkflowForm({ initial, workflow, canEdit }: { initial: DeploymentDraftResponse; workflow: Workflow; canEdit: boolean }) {
  const { session } = usePlatformAuth()
  const client = useQueryClient()
  const depth = useCustomerEditorSection() ?? 'advanced'
  const tools = useQuery({ queryKey: ['workflow-actions', initial.deploymentId, session?.actorId, session?.customerId, session?.authenticationMode],
    queryFn: async () => {
      const result = await fetchWorkflowActions(initial.deploymentId)
      if (result.deploymentId !== initial.deploymentId) throw new Error('The tool catalogue has a different Smart Brain scope.')
      return result
    }, retry: false })
  const [baseline, setBaseline] = useState(workflow)
  const [value, setValue] = useState(workflow)
  const [profileId, setProfileId] = useState(workflow.defaultProfileId)
  const profile = value.profiles.find(item => item.id === profileId)
  const dirty = JSON.stringify(value) !== JSON.stringify(baseline)
  const save = useMutation({ retry: false, mutationFn: async () => {
    const identity = await fetchPlatformAuthSession()
    const workspace = await fetchDeploymentWorkspace(initial.deploymentId)
    if (!session || identity.actorId !== session.actorId || identity.role !== session.role || identity.customerId !== session.customerId
      || identity.authenticationMode !== session.authenticationMode || !workspace.access.canEdit) throw new Error('Access changed. Reopen this Smart Brain before saving.')
    const current = await fetchDeploymentDraft(initial.deploymentId)
    if (current.id !== initial.id || JSON.stringify(read(current.behaviorConfig)) !== JSON.stringify(baseline))
      throw new Error('The saved workflow changed. Reload before editing it again.')
    const proposed = structuredClone(value); delete proposed.compiled; delete proposed.contentHash
    const updated = await updateDeploymentDraft(current.id, { behaviorConfig: { ...(current.behaviorConfig as Record<string, unknown>), workflowAssistance: proposed } })
    if (updated.deploymentId !== initial.deploymentId || updated.id !== current.id) throw new Error('The saved response has a different resource scope.')
    return read(updated.behaviorConfig)!
  }, onSuccess: async saved => {
    setBaseline(saved); setValue(saved)
    await client.invalidateQueries({ queryKey: ['customer-specialist-assistance'] })
  } })
  const updateProfile = (patch: Partial<Profile>) => setValue(current => ({ ...current, profiles: current.profiles.map(item => item.id === profileId ? { ...item, ...patch } : item) }))
  const limitFields = segmentPolicyFields(Boolean(value.goal))
  const quickLimits = effectiveQuickLimits(value.policy)
  const invalid = value.profiles.some(item => !item.name.trim() || !item.purpose.trim() || !item.instructions.trim())
    || limitFields.some(([key, , min, max]) => !Number.isInteger(value.policy[key]) || value.policy[key] < min || value.policy[key] > max)
    || value.policy.maxWriteSteps > value.policy.maxActionSteps
    || !validGoalPolicy(value.goal)
  const disabled = !canEdit || save.isPending
  if (depth !== 'advanced') return <Box sx={{ mb: 3 }}><Typography variant="subtitle2">Configuration workflows</Typography>
    <Typography variant="body2" color="text.secondary">{value.profiles.length} configured specialists · {value.enabled ? 'Enabled' : 'Disabled'}</Typography></Box>
  return <Box component="section" aria-label="Configuration workflows" sx={{ mb: 3, minWidth: 0 }}>
    <Stack direction="row" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap" sx={{ mb: 2 }}>
      <Typography variant="h6" sx={{ minWidth: 0 }}>Configuration workflows</Typography>
      <Button startIcon={<SaveRounded />} variant="contained" disabled={disabled || !dirty || invalid} onClick={() => save.mutate()}>Save workflow draft</Button>
    </Stack>
    {save.isError && <Alert severity="error" sx={{ mb: 2 }}>{(save.error as Error).message}</Alert>}
    {save.isSuccess && !dirty && <Alert severity="success" sx={{ mb: 2 }}>Workflow draft saved. Publish and apply to update the assistant.</Alert>}
    {invalid && <Alert severity="warning" sx={{ mb: 2 }}>Complete each profile and keep limits within the displayed ranges. Write attempts cannot exceed tool steps.</Alert>}
    <FormControlLabel label="Enable workflow assistance" control={<Switch checked={value.enabled} disabled={disabled} onChange={event => setValue(current => ({ ...current, enabled: event.target.checked }))} />} />
    <Box component="section" aria-label="Published goal policy" sx={{ my: 2 }}>
      <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap"><Typography variant="subtitle2">Goal policy</Typography>{value.goal == null && <Chip size="small" variant="outlined" label="Not configured" />}</Stack>
      <FormControlLabel label="Enable autonomous goals" control={<Switch checked={Boolean(value.goal?.enabled)} disabled={disabled} onChange={event => {
        const enabled = event.target.checked
        setValue(current => ({ ...current, goal: { ...newGoalPolicy, ...current.goal, enabled } }))
      }} />} />
      {value.goal && <Stack direction={{ xs: 'column', md: 'row' }} gap={2} sx={{ mt: 1, flexWrap: 'wrap' }}>
        {goalPolicyFields.map(([key, label, min, max]) => <TextField key={key} size="small" type="number" label={label} value={value.goal?.[key] ?? ''} disabled={disabled || !value.goal?.enabled}
          inputProps={{ min, max, step: 1 }} onChange={event => setValue(current => ({ ...current, goal: { ...current.goal!, [key]: event.target.value === '' ? undefined : Number(event.target.value) } }))} sx={{ width: { xs: '100%', md: 230 } }} />)}
      </Stack>}
    </Box>
    <Typography variant="subtitle2">{value.goal ? value.goal.enabled ? 'Execution segment limits' : 'Stored segment limits' : 'Quick edit limits'}</Typography>
    <Stack direction={{ xs: 'column', md: 'row' }} gap={2} sx={{ my: 2, flexWrap: 'wrap' }}>
      {limitFields.map(([key, label, min, max]) => {
        const outOfRange = !Number.isInteger(value.policy[key]) || value.policy[key] < min || value.policy[key] > max
        const writesExceedSteps = key === 'maxWriteSteps' && value.policy.maxWriteSteps > value.policy.maxActionSteps
        return <TextField key={key} size="small" type="number" label={label} value={value.policy[key]} disabled={disabled} error={outOfRange || writesExceedSteps}
          helperText={outOfRange ? `${value.goal ? 'Stored segment' : 'Quick edit'} range: ${min}-${max}. Choose a value in this range to save.` : writesExceedSteps ? 'Write attempts cannot exceed tool steps.' : undefined}
          inputProps={{ min, max, step: 1 }} onChange={event => setValue(current => ({ ...current, policy: { ...current.policy, [key]: Number(event.target.value) } }))} sx={{ width: { xs: '100%', md: 170 } }} />
      })}
    </Stack>
    {value.goal && !value.goal.enabled && <Box component="section" aria-label="Effective Quick edit limits" sx={{ my: 2 }}>
      <Typography variant="subtitle2">Effective Quick edit limits</Typography>
      <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 1, my: 1 }}>
        {segmentPolicyFields(false).map(([key, label]) => <Box key={key}>
          <Typography component="dt" variant="caption" color="text.secondary">{label}</Typography>
          <Typography component="dd" variant="body2" sx={{ m: 0 }}>{quickLimits[key] ?? 'Unavailable'}</Typography>
        </Box>)}
      </Box>
    </Box>}
    <Divider sx={{ my: 2 }} />
    <TextField select size="small" fullWidth label="Specialist profile" value={profileId} onChange={event => setProfileId(event.target.value)} sx={{ mb: 2 }}>
      {value.profiles.map(item => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
    </TextField>
    {profile && <Stack gap={2}>
      <TextField label="Name" value={profile.name} disabled={disabled} inputProps={{ maxLength: 120 }} onChange={event => updateProfile({ name: event.target.value })} />
      <TextField label="Outcome" value={profile.purpose} disabled={disabled} inputProps={{ maxLength: 500 }} onChange={event => updateProfile({ purpose: event.target.value })} />
      <TextField multiline minRows={5} maxRows={14} label="Instructions" value={profile.instructions} disabled={disabled} inputProps={{ maxLength: 8000 }}
        helperText={`${profile.instructions.length} / 8000 characters`} onChange={event => updateProfile({ instructions: event.target.value })} />
      <TextField select size="small" label={value.goal && !value.goal.enabled ? 'Stored delegated profiles' : 'Delegated profiles'} value={profile.delegatedProfiles ?? []} disabled={disabled || !value.goal?.enabled && !profile.delegatedProfiles?.length}
        helperText={value.goal && !value.goal.enabled && profile.delegatedProfiles?.length ? 'Inactive in Quick edit.' : undefined}
        SelectProps={{ multiple: true, displayEmpty: true, renderValue: selected => (selected as string[]).map(id => value.profiles.find(item => item.id === id)?.name ?? id).join(', ') || 'None' }}
        InputLabelProps={{ shrink: true }} sx={{ '& .MuiSelect-select': { whiteSpace: 'normal', overflowWrap: 'anywhere' } }}
        onChange={event => { const ids = event.target.value as unknown; if (Array.isArray(ids)) updateProfile({ delegatedProfiles: ids }) }}>
        {[...new Set([...value.profiles.filter(item => item.id !== profile.id).map(item => item.id), ...profile.delegatedProfiles ?? []])].map(id => <MenuItem key={id} value={id} sx={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{value.profiles.find(item => item.id === id)?.name ?? id}</MenuItem>)}
      </TextField>
      <Typography variant="subtitle2">Permitted tools</Typography>
      {tools.isError && <Alert severity="error">The available tools could not be loaded. Existing choices are preserved.</Alert>}
      <Stack direction="row" flexWrap="wrap" gap={1}>
        {(['readActions', 'writeActions'] as const).flatMap(kind => [...new Set([...(tools.data?.[kind] ?? []), ...baseline.profiles.find(item => item.id === profileId)![kind], ...profile[kind]])].map(action =>
          <FormControlLabel key={`${kind}:${action}`} label={<Stack direction="row" gap={.5} alignItems="center"><Chip size="small" variant="outlined" label={kind === 'readActions' ? 'Read' : 'Write'} /><Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>{action}</Typography></Stack>}
            control={<Switch size="small" checked={profile[kind].includes(action)} disabled={disabled || !profile[kind].includes(action) && (!tools.isSuccess || !tools.data[kind].includes(action))} onChange={event => updateProfile({ [kind]: event.target.checked ? [...profile[kind], action] : profile[kind].filter(item => item !== action) })} />} />))}
      </Stack>
    </Stack>}
    <Divider sx={{ my: 2 }} />
    <TextField select size="small" label="General questions" value={value.defaultProfileId} disabled={disabled} onChange={event => setValue(current => ({ ...current, defaultProfileId: event.target.value }))} sx={{ minWidth: 240, mb: 2 }}>
      {value.profiles.map(item => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
    </TextField>
    <Stack gap={1}>{value.taskBindings.map((binding, index) => <TextField key={binding.task} select size="small" label={binding.task.split('_').join(' ')} value={binding.profileId} disabled={disabled}
      onChange={event => setValue(current => ({ ...current, taskBindings: current.taskBindings.map((item, at) => at === index ? { ...item, profileId: event.target.value } : item) }))}>
      {value.profiles.map(item => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
    </TextField>)}</Stack>
  </Box>
}
