import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  fetchDeploymentWebhookOverview,
  fetchDeploymentDraft,
  retryDeploymentWebhookDelivery,
  updateDeploymentDraft,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'

type ActionPreview = {
  name: string
  description: string
  category: string
  accessMode: string
  anonymousAllowed: boolean
  requiredParameters: number
}

type RouteEditorState = {
  mode: 'path' | 'url'
  method: string
  target: string
  queryJson: string
  bodyJson: string
  successStatuses: string
  message: string
}

type WebhookTargetPreview = {
  id: string
  urlSecretRef: string
  signingSecretRef: string
  timeoutMs: number | null
  maxAttempts: number | null
  linkedActions: string[]
}

type ActionWebhookPolicyPreview = {
  actionName: string
  index: number
  type: string
  targetRef: string
  eventType: string
  targetExists: boolean
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value ?? null)) as T
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function normalizeActionsConfigForEdit(config: unknown): Record<string, unknown> {
  const root = cloneJson(asRecord(config))
  root.actions = asArray(root.actions)
  root.webhookTargets = asArray(root.webhookTargets)
  return root
}

function readOptionalPositiveInteger(record: Record<string, unknown>, key: string): number | null {
  const value = record[key]
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
}

function setOptionalPositiveInteger(record: Record<string, unknown>, key: string, rawValue: string) {
  const trimmed = rawValue.trim()
  if (trimmed.length === 0) {
    delete record[key]
    return
  }
  const parsed = Number(trimmed)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${key} must be a positive integer.`)
  }
  record[key] = parsed
}

function nextWebhookTargetId(config: unknown): string {
  const existingIds = new Set(
    asArray(asRecord(config).webhookTargets)
      .flatMap((candidate) => (isRecord(candidate) && typeof candidate.id === 'string' ? [candidate.id] : [])),
  )
  let counter = 1
  while (existingIds.has(`webhook_target_${counter}`)) {
    counter += 1
  }
  return `webhook_target_${counter}`
}

function extractWebhookTargets(config: unknown): WebhookTargetPreview[] {
  const root = asRecord(config)
  const actionLinks = new Map<string, Set<string>>()

  for (const action of asArray(root.actions)) {
    if (!isRecord(action) || typeof action.name !== 'string') {
      continue
    }
    for (const policy of asArray(action.postPolicies)) {
      if (!isRecord(policy) || typeof policy.targetRef !== 'string') {
        continue
      }
      const linked = actionLinks.get(policy.targetRef) ?? new Set<string>()
      linked.add(action.name)
      actionLinks.set(policy.targetRef, linked)
    }
  }

  return asArray(root.webhookTargets).flatMap((candidate) => {
    if (!isRecord(candidate)) {
      return []
    }
    const id = typeof candidate.id === 'string' ? candidate.id : 'unnamed_target'
    return [{
      id,
      urlSecretRef: typeof candidate.urlSecretRef === 'string' ? candidate.urlSecretRef : '',
      signingSecretRef: typeof candidate.signingSecretRef === 'string' ? candidate.signingSecretRef : '',
      timeoutMs: readOptionalPositiveInteger(candidate, 'timeoutMs'),
      maxAttempts: readOptionalPositiveInteger(candidate, 'maxAttempts'),
      linkedActions: Array.from(actionLinks.get(id) ?? []).sort(),
    }]
  })
}

function extractActionWebhookPolicies(config: unknown, actionName: string): ActionWebhookPolicyPreview[] {
  const root = asRecord(config)
  const targetIds = new Set(extractWebhookTargets(config).map((target) => target.id))

  for (const action of asArray(root.actions)) {
    if (!isRecord(action) || action.name !== actionName) {
      continue
    }
    return asArray(action.postPolicies).flatMap((candidate, index) => {
      if (!isRecord(candidate)) {
        return []
      }
      const type = typeof candidate.type === 'string' ? candidate.type : 'webhook'
      const targetRef = typeof candidate.targetRef === 'string' ? candidate.targetRef : ''
      const eventType = typeof candidate.eventType === 'string' ? candidate.eventType : ''
      return [{
        actionName,
        index,
        type,
        targetRef,
        eventType,
        targetExists: targetIds.has(targetRef),
      }]
    })
  }

  return []
}

function extractActions(config: unknown): ActionPreview[] {
  if (!isRecord(config)) {
    return []
  }

  const actions = config.actions
  if (!Array.isArray(actions)) {
    return []
  }

  return actions.flatMap((candidate) => {
    if (!isRecord(candidate)) {
      return []
    }

    const name = typeof candidate.name === 'string' ? candidate.name : 'unnamed_action'
    const description =
      typeof candidate.description === 'string' ? candidate.description : 'No description provided.'
    const category = typeof candidate.category === 'string' ? candidate.category : 'uncategorized'
    const accessMode = typeof candidate.accessMode === 'string' ? candidate.accessMode : 'UNKNOWN'
    const anonymousAllowed = candidate.anonymousAllowed === true
    const requiredParameters = Array.isArray(candidate.requiredParameters)
      ? candidate.requiredParameters.length
      : Array.isArray(candidate.params)
        ? candidate.params.filter((param) => isRecord(param) && param.required === true).length
        : 0

    return [{ name, description, category, accessMode, anonymousAllowed, requiredParameters }]
  })
}

function updateActionAnonymousAllowed(config: unknown, actionName: string, anonymousAllowed: boolean): unknown {
  if (!isRecord(config) || !Array.isArray(config.actions)) {
    return config
  }

  const next = cloneJson(config)
  if (!isRecord(next) || !Array.isArray(next.actions)) {
    return config
  }

  next.actions = next.actions.map((candidate) => {
    if (!isRecord(candidate) || candidate.name !== actionName) {
      return candidate
    }
    return {
      ...candidate,
      anonymousAllowed,
    }
  })

  return next
}

function actionNamesFromConfig(config: unknown): string[] {
  return extractActions(config).map((action) => action.name)
}

function asRecord(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {}
}

function ensureRecord(parent: Record<string, unknown>, key: string): Record<string, unknown> {
  const current = asRecord(parent[key])
  const cloned = cloneJson(current)
  parent[key] = cloned
  return cloned
}

function normalizeRoutingConfig(config: unknown): Record<string, unknown> {
  const root = cloneJson(asRecord(config))
  const connector = ensureRecord(root, 'connector')
  const inboundAuth = ensureRecord(connector, 'inbound-auth')
  ensureRecord(inboundAuth, 'api-key')
  const upstream = ensureRecord(connector, 'upstream')
  ensureRecord(upstream, 'auth')
  ensureRecord(connector, 'http')
  ensureRecord(connector, 'idempotency')
  const authz = ensureRecord(root, 'authz')
  ensureRecord(authz, 'upstream')
  ensureRecord(ensureRecord(authz, 'upstream'), 'auth')
  ensureRecord(authz, 'http')
  ensureRecord(root, 'actions')
  return root
}

function readStringValue(record: Record<string, unknown>, key: string, fallback = ''): string {
  const value = record[key]
  return typeof value === 'string' ? value : fallback
}

function readBooleanValue(record: Record<string, unknown>, key: string, fallback = false): boolean {
  const value = record[key]
  return typeof value === 'boolean' ? value : fallback
}

function readStatusValues(record: Record<string, unknown>): number[] {
  const successHttpStatus = record['success-http-status']
  if (Array.isArray(successHttpStatus)) {
    return successHttpStatus.flatMap((value) => (typeof value === 'number' ? [value] : []))
  }

  const camelCase = record.successHttpStatus
  if (Array.isArray(camelCase)) {
    return camelCase.flatMap((value) => (typeof value === 'number' ? [value] : []))
  }

  return []
}

function routeRecordForAction(routingConfig: Record<string, unknown>, actionName: string): Record<string, unknown> {
  const actions = asRecord(routingConfig.actions)
  return asRecord(actions[actionName])
}

function inlineRouteRecordForAction(actionsConfig: unknown, actionName: string): Record<string, unknown> {
  if (!isRecord(actionsConfig) || !Array.isArray(actionsConfig.actions)) {
    return {}
  }

  for (const candidate of actionsConfig.actions) {
    if (!isRecord(candidate) || candidate.name !== actionName) {
      continue
    }
    return cloneJson(asRecord(candidate.route))
  }

  return {}
}

function mergeRouteRecords(baseRoute: Record<string, unknown>, overrideRoute: Record<string, unknown>): Record<string, unknown> {
  const merged = cloneJson(baseRoute)

  const mergeInto = (target: Record<string, unknown>, source: Record<string, unknown>) => {
    for (const [key, value] of Object.entries(source)) {
      if (isRecord(value) && isRecord(target[key])) {
        mergeInto(target[key] as Record<string, unknown>, value)
      } else {
        target[key] = cloneJson(value)
      }
    }
  }

  mergeInto(merged, overrideRoute)

  const url = typeof merged.url === 'string' ? merged.url.trim() : ''
  const path = typeof merged.path === 'string' ? merged.path.trim() : ''
  if (url.length > 0) {
    delete merged.path
  } else if (path.length > 0) {
    delete merged.url
  }

  return merged
}

function resolvedRouteRecordForAction(
  routingConfig: Record<string, unknown>,
  actionsConfig: unknown,
  actionName: string,
): Record<string, unknown> {
  const inlineRoute = inlineRouteRecordForAction(actionsConfig, actionName)
  const explicitRoute = routeRecordForAction(routingConfig, actionName)
  return mergeRouteRecords(inlineRoute, explicitRoute)
}

function createRouteEditor(route: unknown): RouteEditorState {
  const routeRecord = asRecord(route)
  const request = asRecord(routeRecord.request)
  const response = asRecord(routeRecord.response)

  return {
    mode: readStringValue(routeRecord, 'url').trim().length > 0 ? 'url' : 'path',
    method: readStringValue(routeRecord, 'method', 'POST'),
    target: readStringValue(routeRecord, 'url') || readStringValue(routeRecord, 'path'),
    queryJson: JSON.stringify(asRecord(request.query), null, 2),
    bodyJson: JSON.stringify(
      Object.prototype.hasOwnProperty.call(request, 'body') ? request.body : null,
      null,
      2,
    ),
    successStatuses: readStatusValues(response).join(', '),
    message: readStringValue(response, 'message'),
  }
}

function buildRouteEditors(
  actionNames: string[],
  routingConfig: Record<string, unknown>,
  actionsConfig: unknown,
): Record<string, RouteEditorState> {
  return actionNames.reduce<Record<string, RouteEditorState>>((accumulator, actionName) => {
    accumulator[actionName] = createRouteEditor(resolvedRouteRecordForAction(routingConfig, actionsConfig, actionName))
    return accumulator
  }, {})
}

function summarizeRoute(editor: RouteEditorState | null) {
  if (!editor) {
    return {
      configured: false,
      method: '—',
      target: 'No route selected',
    }
  }

  return {
    configured: editor.target.trim().length > 0,
    method: editor.method.trim() || 'POST',
    target: editor.target.trim() || 'No route configured',
  }
}

function parseSuccessStatuses(value: string, actionName: string): number[] {
  if (value.trim().length === 0) {
    return []
  }

  return value
    .split(',')
    .map((candidate) => candidate.trim())
    .filter((candidate) => candidate.length > 0)
    .map((candidate) => {
      const parsed = Number(candidate)
      if (!Number.isInteger(parsed) || parsed < 100 || parsed > 599) {
        throw new Error(`Success statuses for ${actionName} must be comma-separated HTTP status codes.`)
      }
      return parsed
    })
}

export function ActionsPage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [editorValue, setEditorValue] = useState('{\n  "actions": []\n}')
  const [parseError, setParseError] = useState<string | null>(null)
  const [routingConfig, setRoutingConfig] = useState<Record<string, unknown>>(normalizeRoutingConfig(null))
  const [routeEditors, setRouteEditors] = useState<Record<string, RouteEditorState>>({})
  const [selectedRouteActionName, setSelectedRouteActionName] = useState('')
  const [selectedPolicyActionName, setSelectedPolicyActionName] = useState('')
  const [selectedWebhookTargetId, setSelectedWebhookTargetId] = useState('')
  const [routingError, setRoutingError] = useState<string | null>(null)
  const canEdit = workspace?.access.canEdit ?? false
  const canOperate = workspace?.access.canOperate ?? false

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    if (!draftQuery.data) {
      return
    }

    setEditorValue(JSON.stringify(draftQuery.data.actionsConfig, null, 2))
    setParseError(null)

    const nextRoutingConfig = normalizeRoutingConfig(draftQuery.data.routingConfig)
    const draftActionNames = actionNamesFromConfig(draftQuery.data.actionsConfig)
    const draftWebhookTargets = extractWebhookTargets(draftQuery.data.actionsConfig)
    setRoutingConfig(nextRoutingConfig)
    setRouteEditors(buildRouteEditors(draftActionNames, nextRoutingConfig, draftQuery.data.actionsConfig))
    setSelectedRouteActionName((current) =>
      draftActionNames.includes(current) ? current : (draftActionNames[0] ?? ''),
    )
    setSelectedPolicyActionName((current) =>
      draftActionNames.includes(current) ? current : (draftActionNames[0] ?? ''),
    )
    setSelectedWebhookTargetId((current) =>
      draftWebhookTargets.some((target) => target.id === current) ? current : (draftWebhookTargets[0]?.id ?? ''),
    )
    setRoutingError(null)
  }, [draftQuery.data])

  const actionPreview = useMemo(() => {
    try {
      return extractActions(JSON.parse(editorValue))
    } catch {
      return []
    }
  }, [editorValue])

  const routingActionNames = useMemo(() => {
    try {
      return actionNamesFromConfig(JSON.parse(editorValue))
    } catch {
      return draftQuery.data ? actionNamesFromConfig(draftQuery.data.actionsConfig) : []
    }
  }, [draftQuery.data, editorValue])

  const effectiveActionsConfig = useMemo(() => {
    try {
      return JSON.parse(editorValue) as unknown
    } catch {
      return draftQuery.data?.actionsConfig ?? {}
    }
  }, [draftQuery.data, editorValue])

  const webhookTargets = useMemo(
    () => extractWebhookTargets(effectiveActionsConfig),
    [effectiveActionsConfig],
  )
  const selectedActionForPolicies = selectedPolicyActionName || actionPreview[0]?.name || ''
  const selectedActionWebhookPolicies = useMemo(
    () => extractActionWebhookPolicies(effectiveActionsConfig, selectedActionForPolicies),
    [effectiveActionsConfig, selectedActionForPolicies],
  )
  const selectedWebhookTarget = useMemo(
    () => webhookTargets.find((target) => target.id === selectedWebhookTargetId) ?? null,
    [selectedWebhookTargetId, webhookTargets],
  )

  useEffect(() => {
    if (webhookTargets.length === 0) {
      if (selectedWebhookTargetId !== '') {
        setSelectedWebhookTargetId('')
      }
      return
    }
    if (!webhookTargets.some((target) => target.id === selectedWebhookTargetId)) {
      setSelectedWebhookTargetId(webhookTargets[0]?.id ?? '')
    }
  }, [selectedWebhookTargetId, webhookTargets])

  useEffect(() => {
    setRouteEditors((previous) => {
      const next: Record<string, RouteEditorState> = {}
      for (const actionName of routingActionNames) {
        next[actionName] =
          previous[actionName] ?? createRouteEditor(resolvedRouteRecordForAction(routingConfig, effectiveActionsConfig, actionName))
      }
      return next
    })

    if (routingActionNames.length === 0) {
      if (selectedRouteActionName !== '') {
        setSelectedRouteActionName('')
      }
      return
    }

    if (!routingActionNames.includes(selectedRouteActionName)) {
      setSelectedRouteActionName(routingActionNames[0])
    }
  }, [effectiveActionsConfig, routingActionNames, routingConfig, selectedRouteActionName])

  useEffect(() => {
    if (routingActionNames.length === 0) {
      if (selectedPolicyActionName !== '') {
        setSelectedPolicyActionName('')
      }
      return
    }

    if (!routingActionNames.includes(selectedPolicyActionName)) {
      setSelectedPolicyActionName(routingActionNames[0])
    }
  }, [routingActionNames, selectedPolicyActionName])

  const connector = asRecord(routingConfig.connector)
  const inboundAuth = asRecord(connector['inbound-auth'])
  const connectorApiKey = asRecord(inboundAuth['api-key'])
  const connectorUpstream = asRecord(connector.upstream)
  const connectorUpstreamAuth = asRecord(connectorUpstream.auth)
  const authz = asRecord(routingConfig.authz)
  const authzUpstream = asRecord(authz.upstream)
  const authzUpstreamAuth = asRecord(authzUpstream.auth)

  const selectedRouteEditor = selectedRouteActionName
    ? routeEditors[selectedRouteActionName] ?? createRouteEditor(resolvedRouteRecordForAction(routingConfig, effectiveActionsConfig, selectedRouteActionName))
    : null

  const selectedRouteSummary = summarizeRoute(selectedRouteEditor)
  const savedActionsJson = useMemo(
    () => (draftQuery.data ? JSON.stringify(draftQuery.data.actionsConfig, null, 2) : ''),
    [draftQuery.data],
  )
  const actionsDraftDirty = useMemo(
    () => (draftQuery.data ? editorValue !== savedActionsJson : false),
    [draftQuery.data, editorValue, savedActionsJson],
  )
  const routingDraftDirty = useMemo(() => {
    if (!draftQuery.data) {
      return false
    }
    try {
      const currentRouting = buildRoutingConfigForSave()
      const savedRouting = normalizeRoutingConfig(draftQuery.data.routingConfig)
      return JSON.stringify(currentRouting) !== JSON.stringify(savedRouting)
    } catch {
      return true
    }
  }, [draftQuery.data, routeEditors, routingActionNames, routingConfig])
  const draftDirty = actionsDraftDirty || routingDraftDirty
  const editorState = useMemo(
    () => ({
      dirty: draftDirty,
      label: 'Actions and routing',
      description: draftDirty
        ? 'Action catalog or routing edits exist only in the current browser buffer until you save the deployment draft.'
        : 'Actions and routing editors match the saved deployment draft.',
    }),
    [draftDirty],
  )
  useDeploymentWorkspaceEditorState(selectedDeploymentId ? editorState : null)

  const saveMutation = useMutation({
    mutationFn: ({ draftId, actionsConfig }: { draftId: string; actionsConfig: unknown }) =>
      updateDeploymentDraft(draftId, { actionsConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      ])
    },
  })

  const saveRoutingMutation = useMutation({
    mutationFn: ({ draftId, nextRoutingConfig }: { draftId: string; nextRoutingConfig: unknown }) =>
      updateDeploymentDraft(draftId, { routingConfig: nextRoutingConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      ])
    },
  })

  const webhookOverviewQuery = useQuery({
    queryKey: ['deployment-webhooks-overview', selectedDeploymentId],
    queryFn: () => fetchDeploymentWebhookOverview(selectedDeploymentId, 50),
    enabled: selectedDeploymentId.length > 0 && canOperate && Boolean(workspace?.deployment.runtimeBaseUrl),
    refetchInterval: 10_000,
  })

  const retryWebhookMutation = useMutation({
    mutationFn: (deliveryId: string) => retryDeploymentWebhookDelivery(selectedDeploymentId, deliveryId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['deployment-webhooks-overview', selectedDeploymentId] })
    },
  })

  const handleSaveActions = () => {
    if (!draftQuery.data) {
      return
    }

    try {
      const parsed = JSON.parse(editorValue) as unknown
      setParseError(null)
      saveMutation.mutate({
        draftId: draftQuery.data.id,
        actionsConfig: parsed,
      })
    } catch (error) {
      setParseError(error instanceof Error ? error.message : 'Invalid JSON')
    }
  }

  const handleToggleAnonymousAllowed = (actionName: string, anonymousAllowed: boolean) => {
    try {
      const parsed = JSON.parse(editorValue) as unknown
      const updated = updateActionAnonymousAllowed(parsed, actionName, anonymousAllowed)
      setEditorValue(JSON.stringify(updated, null, 2))
      setParseError(null)
    } catch (error) {
      setParseError(error instanceof Error ? error.message : 'Invalid JSON')
    }
  }

  const applyActionsConfigMutation = (mutator: (next: Record<string, unknown>) => void) => {
    try {
      const baseConfig = (() => {
        try {
          return JSON.parse(editorValue) as unknown
        } catch {
          return draftQuery.data?.actionsConfig ?? {}
        }
      })()
      const nextConfig = normalizeActionsConfigForEdit(baseConfig)
      mutator(nextConfig)
      setEditorValue(JSON.stringify(nextConfig, null, 2))
      setParseError(null)
    } catch (error) {
      setParseError(error instanceof Error ? error.message : 'Invalid webhook configuration')
    }
  }

  const handleAddWebhookTarget = () => {
    applyActionsConfigMutation((nextConfig) => {
      const webhookTargets = asArray(nextConfig.webhookTargets)
      const targetId = nextWebhookTargetId(nextConfig)
      webhookTargets.push({
        id: targetId,
        urlSecretRef: '',
        signingSecretRef: '',
      })
      nextConfig.webhookTargets = webhookTargets
      setSelectedWebhookTargetId(targetId)
    })
  }

  const handleUpdateWebhookTargetField = (
    targetId: string,
    key: 'id' | 'urlSecretRef' | 'signingSecretRef' | 'timeoutMs' | 'maxAttempts',
    value: string,
  ) => {
    applyActionsConfigMutation((nextConfig) => {
      const webhookTargets = asArray(nextConfig.webhookTargets)
      const target = webhookTargets.find((candidate) => isRecord(candidate) && candidate.id === targetId)
      if (!isRecord(target)) {
        throw new Error(`Webhook target ${targetId} was not found.`)
      }

      if (key === 'id') {
        const nextId = value.trim()
        if (!nextId) {
          throw new Error('Webhook target id is required.')
        }
        if (nextId !== targetId && webhookTargets.some((candidate) => isRecord(candidate) && candidate.id === nextId)) {
          throw new Error(`Webhook target id ${nextId} is already in use.`)
        }
        target.id = nextId
        for (const action of asArray(nextConfig.actions)) {
          if (!isRecord(action)) {
            continue
          }
          action.postPolicies = asArray(action.postPolicies).map((policy) => {
            if (!isRecord(policy) || policy.targetRef !== targetId) {
              return policy
            }
            return {
              ...policy,
              targetRef: nextId,
            }
          })
        }
        setSelectedWebhookTargetId(nextId)
        return
      }

      if (key === 'timeoutMs' || key === 'maxAttempts') {
        setOptionalPositiveInteger(target, key, value)
        return
      }

      target[key] = value
    })
  }

  const handleDeleteWebhookTarget = (targetId: string) => {
    applyActionsConfigMutation((nextConfig) => {
      nextConfig.webhookTargets = asArray(nextConfig.webhookTargets).filter(
        (candidate) => !(isRecord(candidate) && candidate.id === targetId),
      )
      nextConfig.actions = asArray(nextConfig.actions).map((action) => {
        if (!isRecord(action)) {
          return action
        }
        return {
          ...action,
          postPolicies: asArray(action.postPolicies).filter(
            (policy) => !(isRecord(policy) && policy.targetRef === targetId),
          ),
        }
      })
    })
  }

  const handleAddWebhookPolicy = (actionName: string) => {
    if (!actionName) {
      return
    }
    applyActionsConfigMutation((nextConfig) => {
      const defaultTargetRef = extractWebhookTargets(nextConfig)[0]?.id ?? ''
      if (!defaultTargetRef) {
        throw new Error('Create at least one webhook target before adding a post-action webhook.')
      }
      const action = asArray(nextConfig.actions).find((candidate) => isRecord(candidate) && candidate.name === actionName)
      if (!isRecord(action)) {
        throw new Error(`Action ${actionName} was not found.`)
      }
      const postPolicies = asArray(action.postPolicies)
      postPolicies.push({
        type: 'webhook',
        targetRef: defaultTargetRef,
        eventType: '',
      })
      action.postPolicies = postPolicies
    })
  }

  const handleUpdateWebhookPolicyField = (
    actionName: string,
    index: number,
    key: 'targetRef' | 'eventType',
    value: string,
  ) => {
    applyActionsConfigMutation((nextConfig) => {
      const action = asArray(nextConfig.actions).find((candidate) => isRecord(candidate) && candidate.name === actionName)
      if (!isRecord(action)) {
        throw new Error(`Action ${actionName} was not found.`)
      }
      const postPolicies = asArray(action.postPolicies)
      const policy = postPolicies[index]
      if (!isRecord(policy)) {
        throw new Error(`Webhook policy ${index + 1} for ${actionName} was not found.`)
      }
      policy[key] = value
    })
  }

  const handleDeleteWebhookPolicy = (actionName: string, index: number) => {
    applyActionsConfigMutation((nextConfig) => {
      const action = asArray(nextConfig.actions).find((candidate) => isRecord(candidate) && candidate.name === actionName)
      if (!isRecord(action)) {
        return
      }
      action.postPolicies = asArray(action.postPolicies).filter((_, candidateIndex) => candidateIndex !== index)
    })
  }

  const updateRoutingConfig = (mutator: (next: Record<string, unknown>) => void) => {
    setRoutingConfig((previous) => {
      const next = normalizeRoutingConfig(previous)
      mutator(next)
      return next
    })
  }

  const updateSelectedRoute = (patch: Partial<RouteEditorState>) => {
    if (!selectedRouteActionName) {
      return
    }

    setRoutingError(null)
    setRouteEditors((previous) => ({
      ...previous,
      [selectedRouteActionName]: {
        ...(previous[selectedRouteActionName] ?? createRouteEditor(routeRecordForAction(routingConfig, selectedRouteActionName))),
        ...patch,
      },
    }))
  }

  const buildRoutingConfigForSave = (): Record<string, unknown> => {
    const nextRoutingConfig = normalizeRoutingConfig(routingConfig)
    const nextActions: Record<string, unknown> = {}

    for (const actionName of routingActionNames) {
      const editor = routeEditors[actionName]
      if (!editor) {
        continue
      }

      const target = editor.target.trim()
      if (!target) {
        continue
      }

      const existingRoute = cloneJson(routeRecordForAction(nextRoutingConfig, actionName))
      existingRoute.method = editor.method.trim() || 'POST'

      if (editor.mode === 'url') {
        existingRoute.url = target
        delete existingRoute.path
      } else {
        existingRoute.path = target
        delete existingRoute.url
      }

      const request = asRecord(existingRoute.request)
      const parsedQuery = editor.queryJson.trim().length === 0 ? {} : JSON.parse(editor.queryJson)
      if (!isRecord(parsedQuery)) {
        throw new Error(`Route query for ${actionName} must be a JSON object.`)
      }
      request.query = parsedQuery
      request.body = editor.bodyJson.trim().length === 0 ? null : JSON.parse(editor.bodyJson)
      existingRoute.request = request

      const response = asRecord(existingRoute.response)
      response['success-http-status'] = parseSuccessStatuses(editor.successStatuses, actionName)
      if (editor.message.trim().length > 0) {
        response.message = editor.message.trim()
      } else {
        delete response.message
      }
      existingRoute.response = response
      nextActions[actionName] = existingRoute
    }

    nextRoutingConfig.actions = nextActions
    return nextRoutingConfig
  }

  const handleSaveRouting = () => {
    if (!draftQuery.data) {
      return
    }

    try {
      const nextRoutingConfig = buildRoutingConfigForSave()
      setRoutingError(null)
      saveRoutingMutation.mutate({
        draftId: draftQuery.data.id,
        nextRoutingConfig,
      })
    } catch (error) {
      setRoutingError(error instanceof Error ? error.message : 'Failed to build routing config')
    }
  }

  const resetRoutingEditor = () => {
    if (!draftQuery.data) {
      return
    }

    const nextRoutingConfig = normalizeRoutingConfig(draftQuery.data.routingConfig)
    const draftActionNames = actionNamesFromConfig(draftQuery.data.actionsConfig)
    setRoutingConfig(nextRoutingConfig)
    setRouteEditors(buildRouteEditors(draftActionNames, nextRoutingConfig, draftQuery.data.actionsConfig))
    setSelectedRouteActionName((current) =>
      draftActionNames.includes(current) ? current : (draftActionNames[0] ?? ''),
    )
    setRoutingError(null)
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Actions" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Action catalog and connector routing
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          This screen now covers both sides of action enablement: the runtime action catalog and the
          REST connector mapping that actually executes those actions against a customer app.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment workspace</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                The selected deployment now comes from the shared workspace header, so action and routing edits
                stay in one persistent context while you move across platform sections.
              </Typography>
            </Box>

            {workspace ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={workspace.deployment.name} variant="outlined" />
                <Chip label={workspace.deployment.environment} variant="outlined" />
                <Chip label={workspace.deployment.status} color="primary" />
                <Chip label={workspace.template.name} variant="outlined" />
              </Stack>
            ) : null}
            {!canEdit && workspace ? (
              <Alert severity="info">
                Saving actions or routing config requires deployment editor access or higher.
              </Alert>
            ) : null}

            {draftQuery.data ? (
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label={`Draft: ${draftQuery.data.id}`} variant="outlined" />
                <Chip label={`Revision ${draftQuery.data.revisionNumber}`} variant="outlined" />
                <Chip label={draftQuery.data.status} color="primary" />
              </Stack>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      {selectedDeploymentId ? (
        <>
          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={7}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Raw actions config</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        The catalog remains editable as raw JSON so the full action contract stays visible.
                      </Typography>
                    </Box>

                    {draftQuery.isLoading ? (
                      <Typography color="text.secondary">Loading draft actions...</Typography>
                    ) : draftQuery.isError ? (
                      <Alert severity="error">
                        {draftQuery.error instanceof Error
                          ? draftQuery.error.message
                          : 'Failed to load draft actions'}
                      </Alert>
                    ) : (
                      <>
                        <TextField
                          multiline
                          minRows={24}
                          fullWidth
                          value={editorValue}
                          onChange={(event) => setEditorValue(event.target.value)}
                          InputProps={{
                            sx: {
                              fontFamily: 'ui-monospace, SFMono-Regular, SFMono-Regular, Menlo, monospace',
                              fontSize: 13,
                            },
                          }}
                        />

                        {parseError ? <Alert severity="error">{parseError}</Alert> : null}
                        {saveMutation.isError ? (
                          <Alert severity="error">
                            {saveMutation.error instanceof Error
                              ? saveMutation.error.message
                              : 'Failed to save actions config'}
                          </Alert>
                        ) : null}
                        {saveMutation.isSuccess ? (
                          <Alert severity="success">Actions draft saved.</Alert>
                        ) : null}

                        <Stack direction="row" spacing={1.5}>
                          <Button
                            variant="contained"
                            startIcon={<SaveRoundedIcon />}
                            onClick={handleSaveActions}
                            disabled={!canEdit || saveMutation.isPending || draftQuery.isLoading}
                          >
                            {saveMutation.isPending ? 'Saving...' : 'Save actions config'}
                          </Button>
                          <Button
                            variant="outlined"
                            onClick={() => {
                              if (draftQuery.data) {
                                setEditorValue(JSON.stringify(draftQuery.data.actionsConfig, null, 2))
                                setParseError(null)
                              }
                            }}
                          >
                            Reset editor
                          </Button>
                        </Stack>
                      </>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={5}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Parsed action preview</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Local parsing shows what the runtime catalog will look like when the draft is published.
                      </Typography>
                    </Box>

                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={`${actionPreview.length} actions detected`} color="primary" />
                      <Chip
                        label={`${actionPreview.reduce((count, action) => count + action.requiredParameters, 0)} required params`}
                        variant="outlined"
                      />
                      <Chip
                        label={`${actionPreview.filter((action) => action.anonymousAllowed).length} anonymous-enabled`}
                        variant="outlined"
                      />
                      <Chip label={`${webhookTargets.length} webhook targets`} variant="outlined" />
                      <Chip
                        label={`${actionPreview.reduce((count, action) => count + extractActionWebhookPolicies(effectiveActionsConfig, action.name).length, 0)} post-action webhooks`}
                        variant="outlined"
                      />
                    </Stack>

                    <Divider />

                    <Alert severity="info">
                      Use the <strong>Anonymous</strong> checkbox to stamp <code>anonymousAllowed</code> into the raw
                      action JSON. Save the draft after editing.
                    </Alert>

                    {actionPreview.length === 0 ? (
                      <Alert severity="info">No actions were detected in the current editor value.</Alert>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Name</TableCell>
                            <TableCell>Category</TableCell>
                            <TableCell>Access</TableCell>
                            <TableCell>Anonymous</TableCell>
                            <TableCell>Required params</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {actionPreview.map((action) => (
                            <TableRow key={action.name} hover>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                    {action.name}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {action.description}
                                  </Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>{action.category}</TableCell>
                              <TableCell>{action.accessMode}</TableCell>
                              <TableCell>
                                <Checkbox
                                  size="small"
                                  checked={action.anonymousAllowed}
                                  disabled={!canEdit || draftQuery.isLoading || saveMutation.isPending}
                                  onChange={(event) =>
                                    handleToggleAnonymousAllowed(action.name, event.target.checked)
                                  }
                                />
                              </TableCell>
                              <TableCell>{action.requiredParameters}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={6}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Webhook targets</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Define the reusable outbound targets once, then attach them to action post-policies. URLs stay secret-backed.
                      </Typography>
                    </Box>

                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={`${webhookTargets.length} targets`} color="primary" />
                      <Chip
                        label={`${webhookTargets.filter((target) => target.linkedActions.length > 0).length} connected`}
                        variant="outlined"
                      />
                    </Stack>

                    {webhookTargets.length === 0 ? (
                      <Alert severity="info">
                        No webhook targets are configured yet. Add one here instead of hand-editing <code>webhookTargets</code>.
                      </Alert>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Target</TableCell>
                            <TableCell>URL secret</TableCell>
                            <TableCell>Linked actions</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {webhookTargets.map((target) => (
                            <TableRow
                              key={target.id}
                              hover
                              selected={target.id === selectedWebhookTargetId}
                              onClick={() => setSelectedWebhookTargetId(target.id)}
                              sx={{ cursor: 'pointer' }}
                            >
                              <TableCell sx={{ fontWeight: 600 }}>{target.id}</TableCell>
                              <TableCell>{target.urlSecretRef || 'Unset'}</TableCell>
                              <TableCell>{target.linkedActions.length}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}

                    <Stack direction="row" spacing={1.5}>
                      <Button variant="outlined" onClick={handleAddWebhookTarget} disabled={!canEdit}>
                        Add target
                      </Button>
                      <Button
                        color="error"
                        variant="outlined"
                        onClick={() => selectedWebhookTarget && handleDeleteWebhookTarget(selectedWebhookTarget.id)}
                        disabled={!canEdit || !selectedWebhookTarget}
                      >
                        Delete target
                      </Button>
                    </Stack>

                    {selectedWebhookTarget ? (
                      <Grid container spacing={2}>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Target id"
                            value={selectedWebhookTarget.id}
                            onChange={(event) => handleUpdateWebhookTargetField(selectedWebhookTarget.id, 'id', event.target.value)}
                            helperText="Stable reference used by action post-policies."
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="URL secret ref"
                            value={selectedWebhookTarget.urlSecretRef}
                            onChange={(event) => handleUpdateWebhookTargetField(selectedWebhookTarget.id, 'urlSecretRef', event.target.value)}
                            helperText="Runtime resolves the real target URL from this secret."
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Signing secret ref"
                            value={selectedWebhookTarget.signingSecretRef}
                            onChange={(event) => handleUpdateWebhookTargetField(selectedWebhookTarget.id, 'signingSecretRef', event.target.value)}
                            helperText="Optional HMAC signing secret."
                          />
                        </Grid>
                        <Grid item xs={12} md={3}>
                          <TextField
                            fullWidth
                            type="number"
                            label="Timeout ms"
                            value={selectedWebhookTarget.timeoutMs ?? ''}
                            onChange={(event) => handleUpdateWebhookTargetField(selectedWebhookTarget.id, 'timeoutMs', event.target.value)}
                          />
                        </Grid>
                        <Grid item xs={12} md={3}>
                          <TextField
                            fullWidth
                            type="number"
                            label="Max attempts"
                            value={selectedWebhookTarget.maxAttempts ?? ''}
                            onChange={(event) => handleUpdateWebhookTargetField(selectedWebhookTarget.id, 'maxAttempts', event.target.value)}
                          />
                        </Grid>
                      </Grid>
                    ) : null}

                    <Alert severity="info">
                      These controls update the draft action JSON. Use <strong>Save actions config</strong> to persist them.
                    </Alert>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={6}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Selected action post-action webhooks</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Attach async webhooks to the currently selected action without dropping into raw JSON.
                      </Typography>
                    </Box>

                    <TextField
                      select
                      fullWidth
                      label="Action"
                      value={selectedActionForPolicies}
                      onChange={(event) => setSelectedPolicyActionName(event.target.value)}
                      disabled={routingActionNames.length === 0}
                      helperText="Choose which action receives post-action webhook deliveries."
                    >
                      {routingActionNames.map((actionName) => (
                        <MenuItem key={actionName} value={actionName}>
                          {actionName}
                        </MenuItem>
                      ))}
                    </TextField>

                    {selectedActionForPolicies ? (
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={selectedActionForPolicies} color="primary" />
                        <Chip label={`${selectedActionWebhookPolicies.length} webhook policies`} variant="outlined" />
                      </Stack>
                    ) : null}

                    {!selectedActionForPolicies ? (
                      <Alert severity="info">Add or select an action to attach post-action webhooks.</Alert>
                    ) : selectedActionWebhookPolicies.length === 0 ? (
                      <Alert severity="info">
                        No post-action webhooks are configured for <strong>{selectedActionForPolicies}</strong> yet.
                      </Alert>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Target</TableCell>
                            <TableCell>Event type</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell align="right">Remove</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {selectedActionWebhookPolicies.map((policy) => (
                            <TableRow key={`${policy.actionName}-${policy.index}`}>
                              <TableCell sx={{ minWidth: 220 }}>
                                <TextField
                                  select
                                  fullWidth
                                  size="small"
                                  value={policy.targetRef}
                                  onChange={(event) =>
                                    handleUpdateWebhookPolicyField(policy.actionName, policy.index, 'targetRef', event.target.value)
                                  }
                                >
                                  {webhookTargets.map((target) => (
                                    <MenuItem key={target.id} value={target.id}>
                                      {target.id}
                                    </MenuItem>
                                  ))}
                                </TextField>
                              </TableCell>
                              <TableCell sx={{ minWidth: 240 }}>
                                <TextField
                                  fullWidth
                                  size="small"
                                  value={policy.eventType}
                                  onChange={(event) =>
                                    handleUpdateWebhookPolicyField(policy.actionName, policy.index, 'eventType', event.target.value)
                                  }
                                  placeholder="order.cancelled"
                                />
                              </TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  label={policy.targetExists ? 'Target found' : 'Missing target'}
                                  color={policy.targetExists ? 'success' : 'warning'}
                                  variant={policy.targetExists ? 'filled' : 'outlined'}
                                />
                              </TableCell>
                              <TableCell align="right">
                                <Button
                                  color="error"
                                  size="small"
                                  onClick={() => handleDeleteWebhookPolicy(policy.actionName, policy.index)}
                                  disabled={!canEdit}
                                >
                                  Remove
                                </Button>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}

                    <Stack direction="row" spacing={1.5}>
                      <Button
                        variant="outlined"
                        onClick={() => handleAddWebhookPolicy(selectedActionForPolicies)}
                        disabled={!canEdit || !selectedActionForPolicies || webhookTargets.length === 0}
                      >
                        Add webhook policy
                      </Button>
                      <Button
                        variant="contained"
                        startIcon={<SaveRoundedIcon />}
                        onClick={handleSaveActions}
                        disabled={!canEdit || saveMutation.isPending || draftQuery.isLoading}
                      >
                        {saveMutation.isPending ? 'Saving...' : 'Save webhook changes'}
                      </Button>
                    </Stack>

                    {webhookTargets.length === 0 ? (
                      <Alert severity="warning">
                        Create a webhook target first. Post-action policies only reference target ids; they do not carry raw URLs inline.
                      </Alert>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Live webhook deliveries</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Inspect async delivery state on the active runtime and retry failed webhook jobs from the platform.
                  </Typography>
                </Box>

                {!workspace?.deployment.runtimeBaseUrl ? (
                  <Alert severity="info">
                    Apply the deployment before expecting live webhook delivery operations. This panel reads the runtime admin surface.
                  </Alert>
                ) : !canOperate ? (
                  <Alert severity="info">
                    Viewing live webhook deliveries requires deployment operator access or higher.
                  </Alert>
                ) : webhookOverviewQuery.isLoading ? (
                  <Alert severity="info">Loading live webhook deliveries…</Alert>
                ) : webhookOverviewQuery.isError ? (
                  <Alert severity="error">
                    {webhookOverviewQuery.error instanceof Error
                      ? webhookOverviewQuery.error.message
                      : 'Failed to load live webhook deliveries'}
                  </Alert>
                ) : webhookOverviewQuery.data ? (
                  <>
                    <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap alignItems="center">
                      {Object.entries(webhookOverviewQuery.data.counts).map(([status, count]) => (
                        <Chip key={status} label={`${status}: ${count}`} variant="outlined" />
                      ))}
                      <Chip label={`Retry eligible: ${webhookOverviewQuery.data.retryEligibleCount}`} color="primary" />
                      <Button
                        variant="outlined"
                        onClick={() => webhookOverviewQuery.refetch()}
                        disabled={webhookOverviewQuery.isFetching}
                      >
                        {webhookOverviewQuery.isFetching ? 'Refreshing...' : 'Refresh deliveries'}
                      </Button>
                    </Stack>

                    {retryWebhookMutation.isError ? (
                      <Alert severity="error">
                        {retryWebhookMutation.error instanceof Error
                          ? retryWebhookMutation.error.message
                          : 'Failed to retry webhook delivery'}
                      </Alert>
                    ) : null}
                    {retryWebhookMutation.isSuccess ? (
                      <Alert severity="success">{retryWebhookMutation.data.summaryMessage}</Alert>
                    ) : null}

                    {webhookOverviewQuery.data.recentDeliveries.length === 0 ? (
                      <Alert severity="info">No webhook delivery rows have been recorded by the active runtime yet.</Alert>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Delivery</TableCell>
                            <TableCell>Action</TableCell>
                            <TableCell>Target</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Attempts</TableCell>
                            <TableCell>Last error</TableCell>
                            <TableCell align="right">Retry</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {webhookOverviewQuery.data.recentDeliveries.map((delivery) => (
                            <TableRow key={delivery.id}>
                              <TableCell sx={{ fontWeight: 600 }}>
                                <Stack spacing={0.25}>
                                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                    {delivery.eventType}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {delivery.id}
                                  </Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>{delivery.actionName}</TableCell>
                              <TableCell>{delivery.targetRef}</TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  label={delivery.status}
                                  color={
                                    delivery.status === 'DELIVERED'
                                      ? 'success'
                                      : delivery.status === 'FAILED'
                                        ? 'error'
                                        : delivery.status === 'IN_PROGRESS'
                                          ? 'warning'
                                          : 'default'
                                  }
                                  variant={delivery.status === 'DELIVERED' ? 'filled' : 'outlined'}
                                />
                              </TableCell>
                              <TableCell>{delivery.attemptCount} / {delivery.maxAttempts}</TableCell>
                              <TableCell>
                                <Typography variant="body2" color="text.secondary">
                                  {delivery.lastError || '—'}
                                </Typography>
                              </TableCell>
                              <TableCell align="right">
                                <Button
                                  size="small"
                                  variant="outlined"
                                  onClick={() => retryWebhookMutation.mutate(delivery.id)}
                                  disabled={!delivery.retryEligible || retryWebhookMutation.isPending}
                                >
                                  Retry
                                </Button>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Box>
                  <Typography variant="h6">Connector wiring</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    These settings define how the REST connector reaches the customer app and how runtime reaches
                    remote authz through the connector.
                  </Typography>
                </Box>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Connector upstream base URL"
                      value={readStringValue(connectorUpstream, 'base-url')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(next, 'connector'), 'upstream')['base-url'] = event.target.value
                        })
                      }
                      helperText="Used by path-based action routes."
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      select
                      fullWidth
                      label="Upstream auth type"
                      value={readStringValue(connectorUpstreamAuth, 'type', 'NONE') || 'NONE'}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'upstream'), 'auth').type = event.target.value
                        })
                      }
                    >
                      <MenuItem value="NONE">NONE</MenuItem>
                      <MenuItem value="API_KEY">API_KEY</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Upstream auth header"
                      value={readStringValue(connectorUpstreamAuth, 'header', 'Authorization')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'upstream'), 'auth').header = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Upstream auth value"
                      value={readStringValue(connectorUpstreamAuth, 'value')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'upstream'), 'auth').value = event.target.value
                        })
                      }
                      helperText="Example: Bearer token or secret placeholder."
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={readBooleanValue(inboundAuth, 'allow-unauthenticated')}
                          onChange={(event) =>
                            updateRoutingConfig((next) => {
                              ensureRecord(ensureRecord(next, 'connector'), 'inbound-auth')['allow-unauthenticated'] = event.target.checked
                            })
                          }
                        />
                      }
                      label="Allow unauthenticated inbound requests"
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={readBooleanValue(connectorApiKey, 'enabled', true)}
                          onChange={(event) =>
                            updateRoutingConfig((next) => {
                              ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'inbound-auth'), 'api-key').enabled = event.target.checked
                            })
                          }
                        />
                      }
                      label="Enable connector API key protection"
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Connector API key header"
                      value={readStringValue(connectorApiKey, 'header', 'X-AIFABRIC-API-KEY')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'inbound-auth'), 'api-key').header = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Connector API key value"
                      value={readStringValue(connectorApiKey, 'value')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'inbound-auth'), 'api-key').value = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={readBooleanValue(authz, 'enabled')}
                          onChange={(event) =>
                            updateRoutingConfig((next) => {
                              ensureRecord(next, 'authz').enabled = event.target.checked
                            })
                          }
                        />
                      }
                      label="Enable authz proxy endpoint"
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Authz path"
                      value={readStringValue(authz, 'path', '/api/authz/check')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(next, 'authz').path = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Authz upstream base URL"
                      value={readStringValue(authzUpstream, 'base-url')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(next, 'authz'), 'upstream')['base-url'] = event.target.value
                        })
                      }
                      helperText="Optional if authz shares the connector upstream service."
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      select
                      fullWidth
                      label="Authz upstream auth type"
                      value={readStringValue(authzUpstreamAuth, 'type', 'NONE') || 'NONE'}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'authz'), 'upstream'), 'auth').type = event.target.value
                        })
                      }
                    >
                      <MenuItem value="NONE">NONE</MenuItem>
                      <MenuItem value="API_KEY">API_KEY</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Authz upstream auth header"
                      value={readStringValue(authzUpstreamAuth, 'header', 'Authorization')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'authz'), 'upstream'), 'auth').header = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Authz upstream auth value"
                      value={readStringValue(authzUpstreamAuth, 'value')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'authz'), 'upstream'), 'auth').value = event.target.value
                        })
                      }
                    />
                  </Grid>
                </Grid>
              </Stack>
            </CardContent>
          </Card>

          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={5}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Action route list</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Every runtime action should map to one connector route.
                      </Typography>
                    </Box>

                    {parseError ? (
                      <Alert severity="warning">
                        The current actions JSON is invalid, so routing is using the last valid draft action list.
                      </Alert>
                    ) : null}

                    {routingActionNames.length === 0 ? (
                      <Alert severity="info">Add at least one action before defining routes.</Alert>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Action</TableCell>
                            <TableCell>Method</TableCell>
                            <TableCell>Target</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {routingActionNames.map((actionName) => {
                            const routeSummary = summarizeRoute(routeEditors[actionName] ?? null)
                            return (
                              <TableRow
                                key={actionName}
                                hover
                                selected={actionName === selectedRouteActionName}
                                onClick={() => setSelectedRouteActionName(actionName)}
                                sx={{ cursor: 'pointer' }}
                              >
                                <TableCell sx={{ fontWeight: 600 }}>{actionName}</TableCell>
                                <TableCell>{routeSummary.method}</TableCell>
                                <TableCell>
                                  <Typography
                                    variant="body2"
                                    color={routeSummary.configured ? 'text.primary' : 'text.secondary'}
                                  >
                                    {routeSummary.target}
                                  </Typography>
                                </TableCell>
                              </TableRow>
                            )
                          })}
                        </TableBody>
                      </Table>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={7}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Selected route editor</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Edit the request template and response mapping for the selected action route.
                      </Typography>
                    </Box>

                    {selectedRouteActionName ? (
                      <>
                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          <Chip label={selectedRouteActionName} color="primary" />
                          <Chip label={selectedRouteSummary.method} variant="outlined" />
                          <Chip
                            label={selectedRouteSummary.configured ? 'Configured' : 'Not configured'}
                            color={selectedRouteSummary.configured ? 'success' : 'default'}
                          />
                        </Stack>

                        <Grid container spacing={2}>
                          <Grid item xs={12} md={4}>
                            <TextField
                              select
                              fullWidth
                              label="Route mode"
                              value={selectedRouteEditor?.mode ?? 'path'}
                              onChange={(event) =>
                                updateSelectedRoute({
                                  mode: event.target.value as 'path' | 'url',
                                })
                              }
                            >
                              <MenuItem value="path">Relative path</MenuItem>
                              <MenuItem value="url">Absolute URL</MenuItem>
                            </TextField>
                          </Grid>
                          <Grid item xs={12} md={4}>
                            <TextField
                              select
                              fullWidth
                              label="Method"
                              value={selectedRouteEditor?.method ?? 'POST'}
                              onChange={(event) => updateSelectedRoute({ method: event.target.value })}
                            >
                              {['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map((method) => (
                                <MenuItem key={method} value={method}>
                                  {method}
                                </MenuItem>
                              ))}
                            </TextField>
                          </Grid>
                          <Grid item xs={12} md={4}>
                            <TextField
                              fullWidth
                              label={selectedRouteEditor?.mode === 'url' ? 'Absolute URL' : 'Relative path'}
                              value={selectedRouteEditor?.target ?? ''}
                              onChange={(event) => updateSelectedRoute({ target: event.target.value })}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <TextField
                              fullWidth
                              label="Success HTTP statuses"
                              value={selectedRouteEditor?.successStatuses ?? ''}
                              onChange={(event) => updateSelectedRoute({ successStatuses: event.target.value })}
                              helperText="Comma-separated list. Leave empty for default 2xx handling."
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <TextField
                              fullWidth
                              label="Success message"
                              value={selectedRouteEditor?.message ?? ''}
                              onChange={(event) => updateSelectedRoute({ message: event.target.value })}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <TextField
                              multiline
                              minRows={10}
                              fullWidth
                              label="Query template JSON"
                              value={selectedRouteEditor?.queryJson ?? '{}'}
                              onChange={(event) => updateSelectedRoute({ queryJson: event.target.value })}
                              InputProps={{
                                sx: {
                                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                                  fontSize: 13,
                                },
                              }}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <TextField
                              multiline
                              minRows={10}
                              fullWidth
                              label="Body template JSON"
                              value={selectedRouteEditor?.bodyJson ?? 'null'}
                              onChange={(event) => updateSelectedRoute({ bodyJson: event.target.value })}
                              InputProps={{
                                sx: {
                                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                                  fontSize: 13,
                                },
                              }}
                            />
                          </Grid>
                        </Grid>

                        {routingError ? <Alert severity="error">{routingError}</Alert> : null}
                        {saveRoutingMutation.isError ? (
                          <Alert severity="error">
                            {saveRoutingMutation.error instanceof Error
                              ? saveRoutingMutation.error.message
                              : 'Failed to save routing config'}
                          </Alert>
                        ) : null}
                        {saveRoutingMutation.isSuccess ? (
                          <Alert severity="success">Routing config draft saved.</Alert>
                        ) : null}

                        <Stack direction="row" spacing={1.5}>
                          <Button
                            variant="contained"
                            startIcon={<SaveRoundedIcon />}
                            onClick={handleSaveRouting}
                            disabled={!canEdit || saveRoutingMutation.isPending || draftQuery.isLoading}
                          >
                            {saveRoutingMutation.isPending ? 'Saving...' : 'Save routing config'}
                          </Button>
                          <Button variant="outlined" onClick={resetRoutingEditor}>
                            Reset routing editor
                          </Button>
                        </Stack>
                      </>
                    ) : (
                      <Alert severity="info">Select an action to configure its route.</Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </>
      ) : null}
    </Stack>
  )
}
