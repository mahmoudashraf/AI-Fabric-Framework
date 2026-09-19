import AccountTreeRoundedIcon from '@mui/icons-material/AccountTreeRounded'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  Grid,
  IconButton,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  fetchDeploymentBehaviors,
  fetchDeploymentDraft,
  fetchDeploymentExecutionExtensions,
  updateDeploymentDraft,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'

type BehaviorSelections = {
  activationSources: string[]
  channelBindings: string[]
  executionExtensions: string[]
  smartBrain: SmartBrainSettings | null
}

type SmartBrainTrigger = {
  code: string
  name: string
  eventTypes: string
  enabled: boolean
}

type SmartBrainSchedule = {
  code: string
  triggerCode: string
  cron: string
  zoneId: string
  enabled: boolean
}

type SmartBrainSettings = {
  maxEventBytes: number
  triggers: SmartBrainTrigger[]
  schedules: SmartBrainSchedule[]
  deliveryMode: 'POLL' | 'SIGNED_WEBHOOK'
  callbackUrl: string
}

const EMPTY_SELECTIONS: BehaviorSelections = {
  activationSources: [],
  channelBindings: [],
  executionExtensions: [],
  smartBrain: null,
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((entry): entry is string => typeof entry === 'string')
    : []
}

function readSelections(config: unknown): BehaviorSelections {
  const root = isRecord(config) ? config : {}
  const activation = isRecord(root.activation) ? root.activation : {}
  return {
    activationSources: stringArray(activation.sources),
    channelBindings: stringArray(root.channelBindings),
    executionExtensions: stringArray(root.executionExtensions),
    smartBrain: readSmartBrain(root.smartBrain),
  }
}

function readSmartBrain(value: unknown): SmartBrainSettings | null {
  if (!isRecord(value)) return null
  const delivery = isRecord(value.delivery) ? value.delivery : {}
  const triggers = Array.isArray(value.triggers) ? value.triggers : []
  const schedules = Array.isArray(value.schedules) ? value.schedules : []
  return {
    maxEventBytes: typeof value.maxEventBytes === 'number' ? value.maxEventBytes : 262144,
    triggers: triggers.filter(isRecord).map((trigger) => ({
      code: typeof trigger.code === 'string' ? trigger.code : '',
      name: typeof trigger.name === 'string' ? trigger.name : '',
      eventTypes: stringArray(trigger.eventTypes).join(', '),
      enabled: typeof trigger.enabled !== 'boolean' || trigger.enabled,
    })),
    schedules: schedules.filter(isRecord).map((schedule) => ({
      code: typeof schedule.code === 'string' ? schedule.code : '',
      triggerCode: typeof schedule.triggerCode === 'string' ? schedule.triggerCode : '',
      cron: typeof schedule.cron === 'string' ? schedule.cron : '',
      zoneId: typeof schedule.zoneId === 'string' ? schedule.zoneId : 'UTC',
      enabled: typeof schedule.enabled !== 'boolean' || schedule.enabled,
    })),
    deliveryMode: delivery.mode === 'SIGNED_WEBHOOK' ? 'SIGNED_WEBHOOK' : 'POLL',
    callbackUrl: typeof delivery.callbackUrl === 'string' ? delivery.callbackUrl : '',
  }
}

function selectionsEqual(left: BehaviorSelections, right: BehaviorSelections): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}

function toggleValue(values: string[], value: string): string[] {
  return values.includes(value)
    ? values.filter((entry) => entry !== value)
    : [...values, value]
}

function buildBehaviorConfig(config: unknown, selections: BehaviorSelections): unknown {
  if (!isRecord(config)) {
    throw new Error('The saved deployment behavior contract is unavailable.')
  }
  const next = JSON.parse(JSON.stringify(config)) as Record<string, unknown>
  const activation = isRecord(next.activation) ? next.activation : {}
  activation.sources = selections.activationSources
  next.activation = activation
  next.channelBindings = selections.channelBindings
  next.executionExtensions = selections.executionExtensions
  if (selections.smartBrain) {
    const currentSmartBrain = isRecord(next.smartBrain) ? next.smartBrain : {}
    next.smartBrain = {
      ...currentSmartBrain,
      contractVersion: 'LOOMAI_SMART_BRAIN_CONFIG_V1',
      maxEventBytes: selections.smartBrain.maxEventBytes,
      triggers: selections.smartBrain.triggers.map((trigger) => ({
        code: trigger.code.trim(),
        name: trigger.name.trim(),
        eventTypes: trigger.eventTypes.split(',').map((value) => value.trim()).filter(Boolean),
        specialistRef: 'smart-brain-event-analyst@1',
        enabled: trigger.enabled,
      })),
      schedules: selections.smartBrain.schedules.map((schedule) => ({
        code: schedule.code.trim(),
        triggerCode: schedule.triggerCode.trim(),
        cron: schedule.cron.trim(),
        zoneId: schedule.zoneId.trim(),
        enabled: schedule.enabled,
      })),
      delivery: {
        mode: selections.smartBrain.deliveryMode,
        callbackUrl: selections.smartBrain.deliveryMode === 'SIGNED_WEBHOOK'
          ? selections.smartBrain.callbackUrl.trim()
          : null,
      },
    }
  }
  return next
}

function readableCode(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function readSpecialistBundles(config: unknown): Array<Record<string, unknown>> {
  if (!isRecord(config) || !Array.isArray(config.specialistBundles)) return []
  return config.specialistBundles.filter(isRecord)
}

export function BehaviorPage() {
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [selections, setSelections] = useState<BehaviorSelections>(EMPTY_SELECTIONS)

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const behaviorQuery = useQuery({
    queryKey: ['deployment-behaviors'],
    queryFn: fetchDeploymentBehaviors,
  })
  const extensionQuery = useQuery({
    queryKey: ['deployment-execution-extensions'],
    queryFn: fetchDeploymentExecutionExtensions,
  })

  useEffect(() => {
    setSelections(draftQuery.data ? readSelections(draftQuery.data.behaviorConfig) : EMPTY_SELECTIONS)
  }, [draftQuery.data])

  const behavior = useMemo(
    () => (behaviorQuery.data ?? []).find((item) => item.code === selectedDeploymentSummary?.behaviorType) ?? null,
    [behaviorQuery.data, selectedDeploymentSummary?.behaviorType],
  )
  const compatibleExtensions = useMemo(
    () => (extensionQuery.data ?? []).filter((extension) => (
      extension.compatibleBehaviorTypes.includes(selectedDeploymentSummary?.behaviorType ?? '')
    )),
    [extensionQuery.data, selectedDeploymentSummary?.behaviorType],
  )
  const savedSelections = useMemo(
    () => readSelections(draftQuery.data?.behaviorConfig),
    [draftQuery.data?.behaviorConfig],
  )
  const selectedSpecialistBundles = useMemo(
    () => readSpecialistBundles(draftQuery.data?.behaviorConfig),
    [draftQuery.data?.behaviorConfig],
  )
  const dirty = draftQuery.data ? !selectionsEqual(selections, savedSelections) : false
  const canEdit = workspace?.access.canEdit ?? false
  const selectionError = selections.activationSources.length === 0
    ? 'Select at least one activation source.'
    : selections.channelBindings.length === 0
      ? 'Select at least one channel binding.'
      : smartBrainError(selections.smartBrain, selectedDeploymentSummary?.behaviorType)

  useDeploymentWorkspaceEditorState(selectedDeploymentId ? {
    dirty,
    label: 'Behavior contract',
    description: dirty
      ? 'Behavior choices have unsaved browser-only changes.'
      : 'Behavior choices match the saved deployment draft.',
  } : null)

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!draftQuery.data) {
        throw new Error('Deployment draft is unavailable.')
      }
      return updateDeploymentDraft(draftQuery.data.id, {
        behaviorConfig: buildBehaviorConfig(draftQuery.data.behaviorConfig, selections),
      })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
      ])
    },
  })

  if (!selectedDeploymentId) {
    return <Alert severity="info">Select a deployment to configure its behavior contract.</Alert>
  }

  if (draftQuery.isLoading || behaviorQuery.isLoading || extensionQuery.isLoading) {
    return <Alert severity="info">Loading deployment behavior contract…</Alert>
  }

  if (draftQuery.isError || behaviorQuery.isError || extensionQuery.isError || !behavior) {
    const error = draftQuery.error ?? behaviorQuery.error ?? extensionQuery.error
    return (
      <Alert severity="error">
        {error instanceof Error ? error.message : 'The deployment behavior contract could not be loaded.'}
      </Alert>
    )
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Stack spacing={0.75}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <AccountTreeRoundedIcon color="primary" />
            <Typography variant="h4">{behavior.name}</Typography>
            <Chip label={behavior.maturity} color={behavior.maturity === 'HOSTED_PROVEN' ? 'success' : 'warning'} />
          </Stack>
          <Typography color="text.secondary" sx={{ maxWidth: 900 }}>
            {behavior.description}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Behavior type is immutable for this deployment. Create another deployment to choose a different behavior.
          </Typography>
        </Stack>
        <Button
          variant="contained"
          startIcon={<SaveRoundedIcon />}
          disabled={!canEdit || !dirty || Boolean(selectionError) || saveMutation.isPending}
          onClick={() => saveMutation.mutate()}
          sx={{ alignSelf: { xs: 'stretch', md: 'flex-start' } }}
        >
          {saveMutation.isPending ? 'Saving…' : 'Save behavior'}
        </Button>
      </Stack>

      <Alert severity={behavior.releaseRequiresCapabilityManifest ? 'warning' : 'success'}>
        {behavior.availabilityMessage}
      </Alert>
      {selectionError ? <Alert severity="error">{selectionError}</Alert> : null}
      {saveMutation.isError ? (
        <Alert severity="error">
          {saveMutation.error instanceof Error ? saveMutation.error.message : 'Behavior save failed.'}
        </Alert>
      ) : null}
      {saveMutation.isSuccess ? <Alert severity="success">Behavior draft saved. Publish a new revision before apply.</Alert> : null}

      <Box component="section">
        <Typography variant="h6">Activation</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Choose which trusted request classes may start this deployment behavior.
        </Typography>
        <Grid container spacing={1}>
          {behavior.activationSources.map((source) => (
            <Grid item xs={12} md={4} key={source}>
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={selections.activationSources.includes(source)}
                    onChange={() => setSelections((current) => ({
                      ...current,
                      activationSources: toggleValue(current.activationSources, source),
                    }))}
                    disabled={!canEdit}
                  />
                )}
                label={readableCode(source)}
              />
            </Grid>
          ))}
        </Grid>
      </Box>

      <Divider />

      {selectedDeploymentSummary?.behaviorType === 'SMART_BRAIN' && selections.smartBrain ? (
        <SmartBrainConfiguration
          value={selections.smartBrain}
          disabled={!canEdit}
          onChange={(smartBrain) => setSelections((current) => ({ ...current, smartBrain }))}
        />
      ) : null}

      {selectedDeploymentSummary?.behaviorType === 'SMART_BRAIN' ? <Divider /> : null}

      <Box component="section">
        <Typography variant="h6">Channels</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Select only the product surfaces this deployment will expose.
        </Typography>
        <Grid container spacing={1}>
          {behavior.channelBindings.map((channel) => (
            <Grid item xs={12} md={4} key={channel}>
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={selections.channelBindings.includes(channel)}
                    onChange={() => setSelections((current) => ({
                      ...current,
                      channelBindings: toggleValue(current.channelBindings, channel),
                    }))}
                    disabled={!canEdit}
                  />
                )}
                label={readableCode(channel)}
              />
            </Grid>
          ))}
        </Grid>
      </Box>

      <Divider />

      <Box component="section">
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1.5}>
          <Box>
            <Typography variant="h6">Specialist Bundles</Typography>
            <Typography variant="body2" color="text.secondary">
              Exact reviewed bundles are installed through Marketplace and remain bound to their plugin version and source hash.
            </Typography>
          </Box>
          {behavior.requiredSpecialistBundles.length > 0 ? (
            <Button
              variant="outlined"
              startIcon={<StorefrontRoundedIcon />}
              onClick={() => navigate(`/marketplace?targetDeploymentId=${encodeURIComponent(selectedDeploymentId)}&category=specialist`)}
              sx={{ alignSelf: { xs: 'stretch', md: 'flex-start' } }}
            >
              Open Specialists
            </Button>
          ) : null}
        </Stack>
        {behavior.requiredSpecialistBundles.length === 0 ? (
          <Alert severity="info" sx={{ mt: 1.5 }}>This behavior does not require a specialist bundle.</Alert>
        ) : (
          <Stack spacing={1.5} sx={{ mt: 1.5 }}>
            {behavior.requiredSpecialistBundles.map((required) => {
              const selected = selectedSpecialistBundles.find((bundle) => bundle.bundleId === required.bundleId)
              const installed = selected?.contentHash === required.contentHash
              return (
                <Box key={required.bundleId} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 1.5 }}>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                    <Typography variant="subtitle2">{required.bundleId}</Typography>
                    <Chip size="small" label={installed ? 'Installed' : 'Required'} color={installed ? 'success' : 'warning'} />
                    {typeof selected?.marketplacePluginVersion === 'string' ? (
                      <Chip size="small" label={`Plugin ${selected.marketplacePluginVersion}`} variant="outlined" />
                    ) : null}
                  </Stack>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5, overflowWrap: 'anywhere' }}>
                    {required.contentHash}
                  </Typography>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{ mt: 0.75 }}>
                    {required.specialistRefs.map((ref) => <Chip key={ref} size="small" label={ref} variant="outlined" />)}
                    {required.chainRefs.map((ref) => <Chip key={ref} size="small" label={ref} color="secondary" variant="outlined" />)}
                  </Stack>
                </Box>
              )
            })}
          </Stack>
        )}
      </Box>

      <Divider />

      <Box component="section">
        <Typography variant="h6">Execution Extensions</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Extensions add separately verified execution lifecycles. Selecting one makes a matching reviewed runtime artifact mandatory.
        </Typography>
        {compatibleExtensions.length === 0 ? (
          <Alert severity="info">This behavior has no compatible execution extensions.</Alert>
        ) : (
          <Stack spacing={2}>
            {compatibleExtensions.map((extension) => (
              <Box key={extension.code} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 2 }}>
                <FormControlLabel
                  control={(
                    <Checkbox
                      checked={selections.executionExtensions.includes(extension.code)}
                      onChange={() => setSelections((current) => ({
                        ...current,
                        executionExtensions: toggleValue(current.executionExtensions, extension.code),
                      }))}
                      disabled={!canEdit}
                    />
                  )}
                  label={extension.name}
                />
                <Typography variant="body2" color="text.secondary">{extension.description}</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
                  <Chip size="small" label={extension.maturity} color="warning" variant="outlined" />
                  {extension.verificationPackIds.map((pack) => <Chip size="small" key={pack} label={pack} variant="outlined" />)}
                </Stack>
                <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
                  {extension.availabilityMessage}
                </Typography>
              </Box>
            ))}
          </Stack>
        )}
      </Box>

      <Divider />

      <Box component="section">
        <Typography variant="h6">Immutable Runtime Contract</Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1.5 }}>
          <Chip label={`Schema ${behavior.schemaVersion}`} variant="outlined" />
          <Chip label={`Contract v${behavior.contractVersion}`} variant="outlined" />
          {behavior.requiredRuntimeCapabilities.map((capability) => (
            <Chip key={capability} label={capability} color="primary" variant="outlined" />
          ))}
          {behavior.requiredRuntimeMigrationIds.map((migration) => (
            <Chip key={migration} label={migration} color="secondary" variant="outlined" />
          ))}
        </Stack>
      </Box>
    </Stack>
  )
}

function SmartBrainConfiguration({
  value,
  disabled,
  onChange,
}: {
  value: SmartBrainSettings
  disabled: boolean
  onChange: (value: SmartBrainSettings) => void
}) {
  const updateTrigger = (index: number, patch: Partial<SmartBrainTrigger>) => {
    onChange({
      ...value,
      triggers: value.triggers.map((trigger, current) => current === index ? { ...trigger, ...patch } : trigger),
    })
  }
  const updateSchedule = (index: number, patch: Partial<SmartBrainSchedule>) => {
    onChange({
      ...value,
      schedules: value.schedules.map((schedule, current) => current === index ? { ...schedule, ...patch } : schedule),
    })
  }

  return (
    <Box component="section">
      <Typography variant="h6">Smart Brain Inputs and Delivery</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Register trusted CloudEvent types, durable schedules, and the server-owned result delivery path for this deployment.
      </Typography>

      <TextField
        label="Maximum event bytes"
        type="number"
        size="small"
        value={value.maxEventBytes}
        disabled={disabled}
        inputProps={{ min: 1024, max: 1048576, step: 1024 }}
        onChange={(event) => onChange({ ...value, maxEventBytes: Number(event.target.value) })}
        sx={{ width: 220, mb: 3 }}
      />

      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mb: 1.5 }}>
        <Typography variant="subtitle1">Registered triggers</Typography>
        <Button
          size="small"
          startIcon={<AddRoundedIcon />}
          disabled={disabled || value.triggers.length >= 32}
          onClick={() => onChange({
            ...value,
            triggers: [...value.triggers, {
              code: '',
              name: '',
              eventTypes: '',
              enabled: true,
            }],
          })}
        >
          Add trigger
        </Button>
      </Stack>
      <Stack spacing={2}>
        {value.triggers.map((trigger, index) => (
          <Grid container spacing={1.5} alignItems="center" key={`${trigger.code}-${index}`}>
            <Grid item xs={12} md={2.5}>
              <TextField fullWidth size="small" label="Trigger code" value={trigger.code} disabled={disabled} onChange={(event) => updateTrigger(index, { code: event.target.value })} />
            </Grid>
            <Grid item xs={12} md={2.5}>
              <TextField fullWidth size="small" label="Display name" value={trigger.name} disabled={disabled} onChange={(event) => updateTrigger(index, { name: event.target.value })} />
            </Grid>
            <Grid item xs={12} md={5}>
              <TextField fullWidth size="small" label="Allowed CloudEvent types" value={trigger.eventTypes} disabled={disabled} onChange={(event) => updateTrigger(index, { eventTypes: event.target.value })} />
            </Grid>
            <Grid item xs={8} md={1.25}>
              <FormControlLabel control={<Switch checked={trigger.enabled} disabled={disabled} onChange={(_, checked) => updateTrigger(index, { enabled: checked })} />} label="On" />
            </Grid>
            <Grid item xs={4} md={0.75}>
              <Tooltip title="Remove trigger">
                <span>
                  <IconButton disabled={disabled || value.triggers.length === 1} onClick={() => onChange({ ...value, triggers: value.triggers.filter((_, current) => current !== index) })}>
                    <DeleteOutlineRoundedIcon />
                  </IconButton>
                </span>
              </Tooltip>
            </Grid>
          </Grid>
        ))}
      </Stack>

      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mt: 4, mb: 1.5 }}>
        <Typography variant="subtitle1">Schedules</Typography>
        <Button
          size="small"
          startIcon={<AddRoundedIcon />}
          disabled={disabled || value.schedules.length >= 16}
          onClick={() => onChange({
            ...value,
            schedules: [...value.schedules, {
              code: '',
              triggerCode: value.triggers[0]?.code ?? '',
              cron: '0 0 * * * ?',
              zoneId: 'UTC',
              enabled: true,
            }],
          })}
        >
          Add schedule
        </Button>
      </Stack>
      {value.schedules.length === 0 ? (
        <Alert severity="info">No schedule is registered. HTTPS CloudEvent triggers remain available.</Alert>
      ) : (
        <Stack spacing={2}>
          {value.schedules.map((schedule, index) => (
            <Grid container spacing={1.5} alignItems="center" key={`${schedule.code}-${index}`}>
              <Grid item xs={12} md={2}>
                <TextField fullWidth size="small" label="Schedule code" value={schedule.code} disabled={disabled} onChange={(event) => updateSchedule(index, { code: event.target.value })} />
              </Grid>
              <Grid item xs={12} md={2.5}>
                <TextField select fullWidth size="small" label="Trigger" value={schedule.triggerCode} disabled={disabled} onChange={(event) => updateSchedule(index, { triggerCode: event.target.value })}>
                  {value.triggers.map((trigger) => <MenuItem key={trigger.code} value={trigger.code}>{trigger.name || trigger.code}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid item xs={12} md={3}>
                <TextField fullWidth size="small" label="Quartz cron" value={schedule.cron} disabled={disabled} onChange={(event) => updateSchedule(index, { cron: event.target.value })} />
              </Grid>
              <Grid item xs={12} md={2}>
                <TextField fullWidth size="small" label="Time zone" value={schedule.zoneId} disabled={disabled} onChange={(event) => updateSchedule(index, { zoneId: event.target.value })} />
              </Grid>
              <Grid item xs={8} md={1.5}>
                <FormControlLabel control={<Switch checked={schedule.enabled} disabled={disabled} onChange={(_, checked) => updateSchedule(index, { enabled: checked })} />} label="On" />
              </Grid>
              <Grid item xs={4} md={1}>
                <Tooltip title="Remove schedule">
                  <span><IconButton disabled={disabled} onClick={() => onChange({ ...value, schedules: value.schedules.filter((_, current) => current !== index) })}><DeleteOutlineRoundedIcon /></IconButton></span>
                </Tooltip>
              </Grid>
            </Grid>
          ))}
        </Stack>
      )}

      <Typography variant="subtitle1" sx={{ mt: 4, mb: 1.5 }}>Result delivery</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <Select
          size="small"
          value={value.deliveryMode}
          disabled={disabled}
          onChange={(event) => onChange({ ...value, deliveryMode: event.target.value as SmartBrainSettings['deliveryMode'] })}
          sx={{ width: { xs: '100%', md: 220 } }}
        >
          <MenuItem value="POLL">Operation polling</MenuItem>
          <MenuItem value="SIGNED_WEBHOOK">Signed webhook</MenuItem>
        </Select>
        {value.deliveryMode === 'SIGNED_WEBHOOK' ? (
          <TextField
            fullWidth
            size="small"
            label="HTTPS callback URL"
            value={value.callbackUrl}
            disabled={disabled}
            onChange={(event) => onChange({ ...value, callbackUrl: event.target.value })}
          />
        ) : null}
      </Stack>
    </Box>
  )
}

function smartBrainError(settings: SmartBrainSettings | null, behaviorType: string | undefined): string | null {
  if (behaviorType !== 'SMART_BRAIN') return null
  if (!settings || settings.triggers.length === 0) return 'Register at least one Smart Brain trigger.'
  const codes = settings.triggers.map((trigger) => trigger.code.trim())
  if (codes.some((code) => !/^[a-z][a-z0-9-]{1,63}$/.test(code)) || new Set(codes).size !== codes.length) {
    return 'Trigger codes must be unique lower-kebab-case values.'
  }
  if (settings.triggers.some((trigger) => !trigger.name.trim() || !trigger.eventTypes.split(',').some((value) => value.trim()))) {
    return 'Every trigger requires a name and at least one CloudEvent type.'
  }
  if (settings.maxEventBytes < 1024 || settings.maxEventBytes > 1048576) {
    return 'Maximum event bytes must be between 1,024 and 1,048,576.'
  }
  if (settings.schedules.some((schedule) => (
    !/^[a-z][a-z0-9-]{1,63}$/.test(schedule.code.trim())
    || !codes.includes(schedule.triggerCode)
    || !schedule.cron.trim()
    || !schedule.zoneId.trim()
  ))) return 'Every schedule requires a valid code, trigger, cron expression, and time zone.'
  if (settings.deliveryMode === 'SIGNED_WEBHOOK' && !/^https:\/\/[^\s]+$/i.test(settings.callbackUrl.trim())) {
    return 'Signed webhook delivery requires an HTTPS callback URL.'
  }
  return null
}
