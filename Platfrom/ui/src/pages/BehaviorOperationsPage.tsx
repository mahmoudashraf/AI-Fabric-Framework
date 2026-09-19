import BoltRoundedIcon from '@mui/icons-material/BoltRounded'
import CancelRoundedIcon from '@mui/icons-material/CancelRounded'
import HubRoundedIcon from '@mui/icons-material/HubRounded'
import PlayArrowRoundedIcon from '@mui/icons-material/PlayArrowRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import ReplayRoundedIcon from '@mui/icons-material/ReplayRounded'
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  cancelDeploymentAgenticExecution,
  cancelDeploymentSmartBrainOperation,
  fetchDeploymentAgenticExecution,
  fetchDeploymentDraft,
  fetchDeploymentSmartBrainOperation,
  replayDeploymentAgenticExecution,
  replayDeploymentSmartBrainOperation,
  submitDeploymentAgenticExecution,
  triggerDeploymentSmartBrain,
  type DeploymentAgenticExecution,
  type DeploymentSmartBrainOperation,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'REJECTED'])

type SmartBrainTrigger = {
  code: string
  name: string
  eventTypes: string[]
}

type AgenticReplayContext = {
  executionId: string
  question: string
  idempotencyKey: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function smartBrainTriggers(behaviorConfig: unknown): SmartBrainTrigger[] {
  if (!isRecord(behaviorConfig) || !isRecord(behaviorConfig.smartBrain)) return []
  const triggers = behaviorConfig.smartBrain.triggers
  if (!Array.isArray(triggers)) return []
  return triggers.filter(isRecord).flatMap((trigger) => {
    if (typeof trigger.code !== 'string') return []
    return [{
      code: trigger.code,
      name: typeof trigger.name === 'string' ? trigger.name : trigger.code,
      eventTypes: Array.isArray(trigger.eventTypes)
        ? trigger.eventTypes.filter((value): value is string => typeof value === 'string')
        : [],
    }]
  })
}

function runtimeUrl(baseUrl: string | null | undefined, path: string): string {
  if (!baseUrl) return path
  return `${baseUrl.replace(/\/$/, '')}${path}`
}

function statusColor(status: string | undefined): 'success' | 'warning' | 'error' | 'default' {
  const normalized = (status ?? '').toUpperCase()
  if (normalized === 'COMPLETED') return 'success'
  if (['FAILED', 'CANCELLED', 'TIMED_OUT', 'REJECTED'].includes(normalized)) return 'error'
  if (normalized) return 'warning'
  return 'default'
}

function jsonText(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2)
}

function newEventId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `event-${Date.now()}`
}

function readAgenticReplayContext(storageKey: string): AgenticReplayContext | null {
  try {
    const stored = localStorage.getItem(storageKey)
    if (!stored) return null
    const value: unknown = JSON.parse(stored)
    if (!isRecord(value)) return null
    if (typeof value.executionId !== 'string' || typeof value.question !== 'string' || typeof value.idempotencyKey !== 'string') {
      return null
    }
    return {
      executionId: value.executionId,
      question: value.question,
      idempotencyKey: value.idempotencyKey,
    }
  } catch {
    return null
  }
}

export function BehaviorOperationsPage() {
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const behaviorType = selectedDeploymentSummary?.behaviorType ?? ''
  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  if (!selectedDeploymentId) {
    return <Alert severity="info">Select a deployment to open behavior operations.</Alert>
  }
  if (behaviorType === 'AGENTIC_SPECIALIST_TEAM') {
    return (
      <AgenticOperations
        deploymentId={selectedDeploymentId}
        runtimeBaseUrl={selectedDeploymentSummary?.runtimeBaseUrl}
        canOperate={workspace?.access.canOperate ?? false}
      />
    )
  }
  if (behaviorType === 'SMART_BRAIN') {
    return (
      <SmartBrainOperations
        deploymentId={selectedDeploymentId}
        runtimeBaseUrl={selectedDeploymentSummary?.runtimeBaseUrl}
        canOperate={workspace?.access.canOperate ?? false}
        triggers={smartBrainTriggers(draftQuery.data?.behaviorConfig)}
      />
    )
  }
  return (
    <Alert severity="info">
      Durable behavior operations are available for Agentic Specialist Team and Smart Brain deployments.
    </Alert>
  )
}

function AgenticOperations({
  deploymentId,
  runtimeBaseUrl,
  canOperate,
}: {
  deploymentId: string
  runtimeBaseUrl: string | null | undefined
  canOperate: boolean
}) {
  const queryClient = useQueryClient()
  const storageKey = `loomai.agentic.replayContext.${deploymentId}`
  const [question, setQuestion] = useState('Analyze this deployment and summarize verified knowledge and runtime state.')
  const [replayContext, setReplayContext] = useState<AgenticReplayContext | null>(() => readAgenticReplayContext(storageKey))
  const [executionId, setExecutionId] = useState(() => readAgenticReplayContext(storageKey)?.executionId ?? '')

  useEffect(() => {
    const stored = readAgenticReplayContext(storageKey)
    setReplayContext(stored)
    setExecutionId(stored?.executionId ?? '')
  }, [storageKey])

  const executionQuery = useQuery({
    queryKey: ['deployment-agentic-execution', deploymentId, executionId],
    queryFn: () => fetchDeploymentAgenticExecution(deploymentId, executionId),
    enabled: executionId.length > 0,
    refetchInterval: (query) => {
      const status = (query.state.data as DeploymentAgenticExecution | undefined)?.status
      return status && TERMINAL_STATUSES.has(status.toUpperCase()) ? false : 2_000
    },
  })

  const rememberExecution = (execution: DeploymentAgenticExecution) => {
    setExecutionId(execution.executionId)
    queryClient.setQueryData(['deployment-agentic-execution', deploymentId, execution.executionId], execution)
  }
  const submitMutation = useMutation({
    mutationFn: () => submitDeploymentAgenticExecution(deploymentId, { question }),
    onSuccess: (execution) => {
      rememberExecution(execution)
      if (execution.idempotencyKey) {
        const context = { executionId: execution.executionId, question, idempotencyKey: execution.idempotencyKey }
        setReplayContext(context)
        localStorage.setItem(storageKey, JSON.stringify(context))
      }
    },
  })
  const cancelMutation = useMutation({
    mutationFn: () => cancelDeploymentAgenticExecution(deploymentId, executionId),
    onSuccess: rememberExecution,
  })
  const replayMutation = useMutation({
    mutationFn: () => {
      if (!replayContext || replayContext.executionId !== executionId) {
        throw new Error('The original idempotent request is unavailable for this execution.')
      }
      return replayDeploymentAgenticExecution(deploymentId, executionId, {
        question: replayContext.question,
        idempotencyKey: replayContext.idempotencyKey,
      })
    },
    onSuccess: rememberExecution,
  })
  const execution = executionQuery.data
  const operationError = submitMutation.error ?? cancelMutation.error ?? replayMutation.error ?? executionQuery.error

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={1.25} alignItems="center">
        <HubRoundedIcon color="primary" />
        <Box>
          <Typography variant="h4">Agentic Executions</Typography>
          <Typography color="text.secondary">Deployment Intelligence Specialist Team</Typography>
        </Box>
      </Stack>

      <Alert severity={runtimeBaseUrl ? 'info' : 'warning'}>
        Direct integration endpoint: {runtimeUrl(runtimeBaseUrl, '/api/agentic/v1/executions')}
      </Alert>

      <Box component="section">
        <Typography variant="h6" sx={{ mb: 1.5 }}>Submit work</Typography>
        <TextField
          fullWidth
          multiline
          minRows={4}
          label="Bounded task"
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          inputProps={{ maxLength: 2000 }}
          disabled={!canOperate}
        />
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1.5 }}>
          <Button
            variant="contained"
            startIcon={<PlayArrowRoundedIcon />}
            disabled={!canOperate || question.trim().length === 0 || submitMutation.isPending}
            onClick={() => submitMutation.mutate()}
          >
            Submit durable execution
          </Button>
          <TextField
            size="small"
            label="Execution ID"
            value={executionId}
            onChange={(event) => setExecutionId(event.target.value.trim())}
            sx={{ flex: 1, minWidth: 260 }}
          />
          <Button startIcon={<RefreshRoundedIcon />} disabled={!executionId} onClick={() => executionQuery.refetch()}>
            Refresh
          </Button>
          <Button
            startIcon={<ReplayRoundedIcon />}
            disabled={!canOperate || !executionId || replayContext?.executionId !== executionId || replayMutation.isPending}
            onClick={() => replayMutation.mutate()}
          >
            Replay
          </Button>
          <Button color="error" startIcon={<CancelRoundedIcon />} disabled={!canOperate || !executionId || cancelMutation.isPending} onClick={() => cancelMutation.mutate()}>
            Cancel
          </Button>
        </Stack>
      </Box>

      {operationError ? <Alert severity="error">{operationError instanceof Error ? operationError.message : 'Agentic operation failed.'}</Alert> : null}
      {!canOperate ? <Alert severity="warning">Operator access is required to submit, replay, or cancel executions.</Alert> : null}

      <Divider />
      <AgenticExecutionView execution={execution} loading={executionQuery.isFetching} />
    </Stack>
  )
}

function AgenticExecutionView({ execution, loading }: { execution?: DeploymentAgenticExecution; loading: boolean }) {
  if (!execution) {
    return <Alert severity="info">Submit an execution or enter an execution ID to inspect durable state.</Alert>
  }
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
        <Typography variant="h6">Execution {execution.executionId}</Typography>
        <Chip label={execution.status} color={statusColor(execution.status)} />
        <Chip label={execution.chain} variant="outlined" />
        {execution.replayed ? <Chip label="Replay" variant="outlined" /> : null}
        {loading ? <Chip label="Refreshing" color="warning" variant="outlined" /> : null}
      </Stack>
      {execution.message ? <Alert severity="success">{execution.message}</Alert> : null}
      {execution.failure ? <Alert severity="error">{execution.failure.message} ({execution.failure.reason})</Alert> : null}
      {execution.results.map((result) => (
        <Box key={result.specialist} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 2 }}>
          <Typography variant="subtitle1" fontWeight={700}>{result.specialist}</Typography>
          <Typography sx={{ mt: 0.5 }}>{result.summary}</Typography>
          <Box component="pre" sx={{ mt: 1, p: 1.5, bgcolor: 'action.hover', overflowX: 'auto', fontSize: 12 }}>
            {jsonText(result.facts)}
          </Box>
          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
            {result.evidenceReferenceIds.map((reference) => <Chip key={reference} size="small" label={reference} variant="outlined" />)}
          </Stack>
        </Box>
      ))}
      {execution.steps.length > 0 ? (
        <Box component="section">
          <Typography variant="subtitle2" sx={{ mb: 1 }}>Execution trace</Typography>
          <Stack spacing={1}>
            {execution.steps.map((step) => (
              <Box key={`${step.decisionIndex}-${step.directiveType}`} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 1 }}>
                <Typography variant="body2" fontWeight={700}>{step.decisionIndex}. {step.directiveType}</Typography>
                <Typography variant="body2" color="text.secondary">{step.reason}</Typography>
                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{ mt: 0.75 }}>
                  {step.workers.map((worker) => (
                    <Chip key={`${worker.specialist}-${worker.relationship}`} size="small" label={`${worker.specialist}: ${worker.status}`} variant="outlined" />
                  ))}
                </Stack>
              </Box>
            ))}
          </Stack>
        </Box>
      ) : null}
    </Stack>
  )
}

function SmartBrainOperations({
  deploymentId,
  runtimeBaseUrl,
  canOperate,
  triggers,
}: {
  deploymentId: string
  runtimeBaseUrl: string | null | undefined
  canOperate: boolean
  triggers: SmartBrainTrigger[]
}) {
  const queryClient = useQueryClient()
  const storageKey = `loomai.smartBrain.lastOperation.${deploymentId}`
  const [triggerCode, setTriggerCode] = useState('')
  const [eventType, setEventType] = useState('com.loomai.smart-brain.analysis.requested')
  const [eventSource, setEventSource] = useState('/platform/operator-console')
  const [eventData, setEventData] = useState('{\n  "subject": "deployment",\n  "request": "Analyze current signals"\n}')
  const [operationId, setOperationId] = useState(() => localStorage.getItem(storageKey) ?? '')
  const [inputError, setInputError] = useState<string | null>(null)

  useEffect(() => {
    setOperationId(localStorage.getItem(storageKey) ?? '')
  }, [storageKey])
  useEffect(() => {
    if (!triggerCode && triggers.length > 0) {
      setTriggerCode(triggers[0].code)
      if (triggers[0].eventTypes[0]) setEventType(triggers[0].eventTypes[0])
    }
  }, [triggerCode, triggers])

  const operationQuery = useQuery({
    queryKey: ['deployment-smart-brain-operation', deploymentId, operationId],
    queryFn: () => fetchDeploymentSmartBrainOperation(deploymentId, operationId),
    enabled: operationId.length > 0,
    refetchInterval: (query) => {
      const status = (query.state.data as DeploymentSmartBrainOperation | undefined)?.status
      return status && TERMINAL_STATUSES.has(status.toUpperCase()) ? false : 2_000
    },
  })
  const remember = (operation: DeploymentSmartBrainOperation) => {
    setOperationId(operation.operationId)
    localStorage.setItem(storageKey, operation.operationId)
    queryClient.setQueryData(['deployment-smart-brain-operation', deploymentId, operation.operationId], operation)
  }
  const triggerMutation = useMutation({
    mutationFn: () => {
      let data: unknown
      try {
        data = JSON.parse(eventData)
      } catch {
        throw new Error('Event data must be valid JSON.')
      }
      return triggerDeploymentSmartBrain(deploymentId, triggerCode, {
        cloudEvent: {
          specversion: '1.0',
          id: newEventId(),
          source: eventSource.trim(),
          type: eventType.trim(),
          time: new Date().toISOString(),
          datacontenttype: 'application/json',
          data,
        },
      })
    },
    onMutate: () => setInputError(null),
    onSuccess: remember,
    onError: (error) => setInputError(error instanceof Error ? error.message : 'Trigger failed.'),
  })
  const cancelMutation = useMutation({
    mutationFn: () => cancelDeploymentSmartBrainOperation(deploymentId, operationId),
    onSuccess: remember,
  })
  const replayMutation = useMutation({
    mutationFn: () => replayDeploymentSmartBrainOperation(deploymentId, operationId),
    onSuccess: remember,
  })
  const operation = operationQuery.data
  const operationError = cancelMutation.error ?? replayMutation.error ?? operationQuery.error

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={1.25} alignItems="center">
        <BoltRoundedIcon color="primary" />
        <Box>
          <Typography variant="h4">Smart Brain Operations</Typography>
          <Typography color="text.secondary">Trusted CloudEvent ingress and durable result lifecycle</Typography>
        </Box>
      </Stack>

      <Alert severity={runtimeBaseUrl ? 'info' : 'warning'}>
        Direct integration endpoint: {runtimeUrl(runtimeBaseUrl, `/api/smart-brain/v1/triggers/${triggerCode || '{triggerCode}'}`)}
      </Alert>

      <Box component="section">
        <Typography variant="h6" sx={{ mb: 1.5 }}>Submit test event</Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={4}>
            <TextField select fullWidth label="Trigger" value={triggerCode} onChange={(event) => {
              const next = event.target.value
              setTriggerCode(next)
              const trigger = triggers.find((item) => item.code === next)
              if (trigger?.eventTypes[0]) setEventType(trigger.eventTypes[0])
            }}>
              {triggers.map((trigger) => <MenuItem key={trigger.code} value={trigger.code}>{trigger.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth label="CloudEvent type" value={eventType} onChange={(event) => setEventType(event.target.value)} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth label="CloudEvent source" value={eventSource} onChange={(event) => setEventSource(event.target.value)} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth multiline minRows={6} label="Event data (JSON)" value={eventData} onChange={(event) => setEventData(event.target.value)} />
          </Grid>
        </Grid>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1.5 }}>
          <Button
            variant="contained"
            startIcon={<PlayArrowRoundedIcon />}
            disabled={!canOperate || !triggerCode || !eventType.trim() || !eventSource.trim() || triggerMutation.isPending}
            onClick={() => triggerMutation.mutate()}
          >
            Trigger operation
          </Button>
          <TextField
            size="small"
            label="Operation ID"
            value={operationId}
            onChange={(event) => setOperationId(event.target.value.trim())}
            sx={{ flex: 1, minWidth: 260 }}
          />
          <Button startIcon={<RefreshRoundedIcon />} disabled={!operationId} onClick={() => operationQuery.refetch()}>
            Refresh
          </Button>
          <Button startIcon={<ReplayRoundedIcon />} disabled={!canOperate || !operationId || replayMutation.isPending} onClick={() => replayMutation.mutate()}>
            Replay
          </Button>
          <Button color="error" startIcon={<CancelRoundedIcon />} disabled={!canOperate || !operationId || cancelMutation.isPending} onClick={() => cancelMutation.mutate()}>
            Cancel
          </Button>
        </Stack>
      </Box>

      {inputError ? <Alert severity="error">{inputError}</Alert> : null}
      {operationError ? <Alert severity="error">{operationError instanceof Error ? operationError.message : 'Smart Brain operation failed.'}</Alert> : null}
      {!canOperate ? <Alert severity="warning">Operator access is required to trigger, replay, or cancel operations.</Alert> : null}

      <Divider />
      <SmartBrainOperationView operation={operation} loading={operationQuery.isFetching} />
    </Stack>
  )
}

function SmartBrainOperationView({ operation, loading }: { operation?: DeploymentSmartBrainOperation; loading: boolean }) {
  if (!operation) {
    return <Alert severity="info">Trigger an event or enter an operation ID to inspect durable state.</Alert>
  }
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
        <Typography variant="h6">Operation {operation.operationId}</Typography>
        <Chip label={operation.status} color={statusColor(operation.status)} />
        <Chip label={operation.triggerCode} variant="outlined" />
        <Chip label={operation.cloudEventType} variant="outlined" />
        {operation.replayed ? <Chip label="Replay" variant="outlined" /> : null}
        {loading ? <Chip label="Refreshing" color="warning" variant="outlined" /> : null}
      </Stack>
      {operation.failure ? <Alert severity="error">{operation.failure.message} ({operation.failure.code})</Alert> : null}
      <Box component="pre" sx={{ p: 2, bgcolor: 'action.hover', overflowX: 'auto', fontSize: 12, minHeight: 120 }}>
        {jsonText(operation.result)}
      </Box>
      <Typography variant="caption" color="text.secondary">
        Event {operation.cloudEventId} | Updated {operation.updatedAt}
      </Typography>
    </Stack>
  )
}
