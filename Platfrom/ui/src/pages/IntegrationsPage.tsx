import CableRoundedIcon from '@mui/icons-material/CableRounded'
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded'
import ReplayRoundedIcon from '@mui/icons-material/ReplayRounded'
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  expireConversationHandover,
  fetchConversationHandoverOperations,
  fetchDeploymentIntegrations,
  fetchDeploymentReleases,
  fetchDeploymentTargetContext,
  reconcileDeploymentIntegrationSource,
  replayDeploymentIntegrationWebhook,
  retryConversationHandoverDispatch,
  type ConversationHandoverOperationsOverview,
  type DeploymentTargetReference,
  type IntegrationWebhookEvent,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { CustomerAdvancedSection, useCustomerEditorSection } from '../components/customer/CustomerDetailSections'

function statusColor(value: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  const status = (value ?? '').toUpperCase()
  if (['ACTIVE', 'COMPLETED', 'READY', 'ACCEPTED', 'RECONCILIATION_QUEUED'].includes(status)) return 'success'
  if (['FAILED', 'AUTH_FAILED', 'REJECTED', 'DEAD_LETTER', 'PARTIAL'].includes(status)) return 'error'
  if (status) return 'warning'
  return 'default'
}

function formatTime(value: string | null | undefined): string {
  if (!value) return 'Never'
  const timestamp = new Date(value)
  return Number.isNaN(timestamp.getTime()) ? 'Unknown' : timestamp.toLocaleString()
}

function errorText(value: unknown): string {
  return value instanceof Error ? value.message : 'Integration operation failed.'
}

export function IntegrationsPage({ initialReleaseId = '', handoverOnly = false, lockReleaseSelection = false }: { initialReleaseId?: string; handoverOnly?: boolean; lockReleaseSelection?: boolean } = {}) {
  const auth = usePlatformAuth()
  const detailSection = useCustomerEditorSection()
  const queryClient = useQueryClient()
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const [selectedReleaseId, setSelectedReleaseId] = useState(initialReleaseId)
  const customerSelfService = auth.session?.role === 'CUSTOMER_ADMIN'
  const canOperate = workspace?.access.canOperate ?? false
  const releasesQuery = useQuery({
    queryKey: ['deployment-releases', selectedDeploymentId],
    queryFn: () => fetchDeploymentReleases(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const availableReleases = useMemo(() => (releasesQuery.data ?? []).filter((release) =>
    release.targetProfileId && release.provisioningStatus === 'ACTIVE'
    && ['VERIFYING', 'APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(release.status)),
  [releasesQuery.data])
  const selectedRelease = availableReleases.find((release) => release.id === selectedReleaseId)
  const target: DeploymentTargetReference = {
    deploymentId: selectedDeploymentId,
    targetProfileId: selectedRelease?.targetProfileId ?? '',
    releaseId: selectedRelease?.id ?? '',
  }
  useEffect(() => {
    setSelectedReleaseId(initialReleaseId)
  }, [selectedDeploymentId, initialReleaseId])
  useEffect(() => {
    if (!initialReleaseId && !selectedReleaseId && availableReleases.length === 1) {
      setSelectedReleaseId(availableReleases[0].id)
    }
  }, [availableReleases, selectedReleaseId, initialReleaseId])

  const targetQuery = useQuery({
    queryKey: ['deployment-target-context', selectedDeploymentId, target.targetProfileId, target.releaseId],
    queryFn: () => fetchDeploymentTargetContext(target),
    enabled: !!selectedRelease,
    retry: false,
  })
  const dataLive = !handoverOnly && (targetQuery.data?.externalIntegrationConfigured ?? false)
  const handoverLive = targetQuery.data?.conversationHandoverConfigured ?? false
  const live = dataLive || handoverLive

  const overview = useQuery({
    queryKey: ['deployment-integrations', selectedDeploymentId, target.targetProfileId, target.releaseId],
    queryFn: () => fetchDeploymentIntegrations(target),
    enabled: !!selectedRelease && dataLive,
    retry: false,
    refetchInterval: 10_000,
  })
  const handover = useQuery({
    queryKey: ['conversation-handover-operations', selectedDeploymentId, target.targetProfileId, target.releaseId],
    queryFn: () => fetchConversationHandoverOperations(target),
    enabled: !!selectedRelease && handoverLive,
    retry: false,
    refetchInterval: 10_000,
  })

  const reconcile = useMutation({
    mutationFn: ({ sourceId, operationKey }: { sourceId: string; operationKey: string }) => (
      reconcileDeploymentIntegrationSource(target, sourceId, operationKey)
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['deployment-integrations', selectedDeploymentId, target.targetProfileId, target.releaseId] })
    },
  })
  const replay = useMutation({
    mutationFn: ({ sourceId, eventId, operationKey }: { sourceId: string; eventId: string; operationKey: string }) => (
      replayDeploymentIntegrationWebhook(target, sourceId, eventId, operationKey)
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['deployment-integrations', selectedDeploymentId, target.targetProfileId, target.releaseId] })
    },
  })
  const retryDispatch = useMutation({
    mutationFn: ({ dispatchId, operationKey }: { dispatchId: string; operationKey: string }) => (
      retryConversationHandoverDispatch(target, dispatchId, operationKey)
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['conversation-handover-operations', selectedDeploymentId, target.targetProfileId, target.releaseId] })
    },
  })
  const expireHandover = useMutation({
    mutationFn: ({ handoverId, operationKey }: { handoverId: string; operationKey: string }) => (
      expireConversationHandover(target, handoverId, operationKey)
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['conversation-handover-operations', selectedDeploymentId, target.targetProfileId, target.releaseId] })
    },
  })

  if (!releasesQuery.isLoading && availableReleases.length === 0
    && !(workspace?.externalIntegrationConfigured || workspace?.conversationHandoverConfigured)) {
    return (
      <Stack spacing={3}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 750 }}>{handoverOnly ? 'Human support' : 'External integrations'}</Typography>
          <Typography color="text.secondary">
            Deployment-local provider connections, trusted resource bindings, live synchronization, and signed inbound events.
          </Typography>
        </Box>
        <Alert
          severity="info"
          action={!customerSelfService ? (
            <Button component={RouterLink} to="/marketplace" color="inherit" startIcon={<StorefrontRoundedIcon />}>
              Marketplace
            </Button>
          ) : undefined}
        >
          This deployment has no external DATA or conversation-channel integration in its reviewed configuration.
        </Alert>
      </Stack>
    )
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 750 }}>{handoverOnly ? 'Human support' : 'External integrations'}</Typography>
          <Typography color="text.secondary">
            Safe operational state from the selected deployment. Credentials and provider payloads are never returned here.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip
            icon={<CableRoundedIcon />}
            label={targetQuery.isLoading ? 'Reading selected release' : live ? 'Selected release live' : 'No live integration selected'}
            color={live ? 'success' : 'warning'}
          />
          <Tooltip title="Refresh integration state">
            <span>
              <IconButton
                onClick={() => {
                  if (dataLive) void overview.refetch()
                  if (handoverLive) void handover.refetch()
                }}
                disabled={!live || overview.isFetching || handover.isFetching}
              >
                <SyncRoundedIcon />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 2 }}>
        <TextField
          select
          fullWidth
          label="Target release"
          value={selectedReleaseId}
          onChange={(event) => { if (!lockReleaseSelection) setSelectedReleaseId(event.target.value) }}
          disabled={releasesQuery.isLoading || lockReleaseSelection}
          helperText="Integration commands and status are scoped to this exact provisioned target and release."
        >
          <MenuItem value="">Select a provisioned target release</MenuItem>
          {availableReleases.map((release) => (
            <MenuItem key={release.id} value={release.id}>
              {release.targetProfileId} · {release.id} · {release.status}
            </MenuItem>
          ))}
        </TextField>
      </Paper>

      {releasesQuery.error ? <Alert severity="error">{errorText(releasesQuery.error)}</Alert> : null}
      {targetQuery.error ? <Alert severity="error">{errorText(targetQuery.error)}</Alert> : null}
      {!releasesQuery.isLoading && availableReleases.length === 0 ? (
        <Alert severity="warning">
          No provisioned target release is available for integration administration.
        </Alert>
      ) : null}
      {availableReleases.length > 1 && !selectedRelease ? (
        <Alert severity="info">Select the target release whose integration state you want to inspect or operate.</Alert>
      ) : null}
      {selectedRelease && targetQuery.isSuccess && !live ? (
        <Alert severity="warning">
          The selected release does not contain an external data or conversation-handover integration.
        </Alert>
      ) : null}
      {targetQuery.isLoading || overview.isLoading || handover.isLoading ? (
        <Box sx={{ py: 8, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>
      ) : null}
      {overview.error ? <Alert severity="error">{errorText(overview.error)}</Alert> : null}
      {handover.error ? <Alert severity="error">{errorText(handover.error)}</Alert> : null}
      {reconcile.error ? <Alert severity="error">{errorText(reconcile.error)}</Alert> : null}
      {replay.error ? <Alert severity="error">{errorText(replay.error)}</Alert> : null}
      {retryDispatch.error ? <Alert severity="error">{errorText(retryDispatch.error)}</Alert> : null}
      {expireHandover.error ? <Alert severity="error">{errorText(expireHandover.error)}</Alert> : null}
      {detailSection ? overview.data?.sources.filter(source => source.state.errorClass).map(source => <Alert key={source.sourceId} severity="error">{source.sourceId}: {source.state.errorMessage ?? source.state.errorClass}</Alert>) : null}
      {detailSection ? overview.data?.webhooks.filter(webhook => webhook.counts.deadLetter > 0 || webhook.counts.rejected > 0).map(webhook => <Alert key={webhook.sourceId} severity="warning">{webhook.sourceId}: {webhook.counts.rejected} rejected events / {webhook.counts.deadLetter} dead-letter events. Review inbound events in Advanced.</Alert>) : null}

      {handover.data ? (
        <ConversationHandoverOperations
          value={handover.data}
          canOperate={canOperate}
          busy={retryDispatch.isPending || expireHandover.isPending}
          onRetry={(dispatchId) => retryDispatch.mutate({ dispatchId, operationKey: crypto.randomUUID() })}
          onExpire={(handoverId) => expireHandover.mutate({ handoverId, operationKey: crypto.randomUUID() })}
        />
      ) : null}

      {overview.data ? (
        <>
          <CustomerAdvancedSection>
          <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 2 }}>
            <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} divider={<Divider flexItem orientation="vertical" />}>
              <Box sx={{ minWidth: 180 }}>
                <Typography variant="overline" color="text.secondary">Persistence</Typography>
                <Typography variant="h6">{overview.data.persistence.enabled ? 'Durable' : 'Disabled'}</Typography>
                <Typography variant="body2" color="text.secondary">Schema: {overview.data.persistence.schema}</Typography>
              </Box>
              <Box sx={{ minWidth: 180 }}>
                <Typography variant="overline" color="text.secondary">Runtime indexing channel</Typography>
                <Typography variant="h6">{overview.data.runtimeDataSync.enabled ? 'Enabled' : 'Disabled'}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {overview.data.runtimeDataSync.serviceCredentialConfigured ? 'Scoped service identity present' : 'Service identity missing'}
                </Typography>
              </Box>
              <Box sx={{ flex: 1 }}>
                <Typography variant="overline" color="text.secondary">Token posture</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {Object.entries(overview.data.tokenPosture).map(([profile, posture]) => (
                    <Tooltip
                      key={profile}
                      title={posture.lastErrorClass
                        ? `Last error: ${posture.lastErrorClass}`
                        : posture.expiresAt ? `Expires ${formatTime(posture.expiresAt)}` : 'No token expiry recorded'}
                    >
                      <Chip size="small" label={`${profile}: ${posture.status}`} color={statusColor(posture.status)} variant="outlined" />
                    </Tooltip>
                  ))}
                  {Object.keys(overview.data.tokenPosture).length === 0 ? <Chip size="small" label="No token profiles" variant="outlined" /> : null}
                </Stack>
              </Box>
            </Stack>
          </Paper>

          <Box>
            <Typography variant="h6" sx={{ mb: 1.5 }}>Connection and authority</Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead><TableRow><TableCell>Profile</TableCell><TableCell>Environment</TableCell><TableCell>Authentication</TableCell><TableCell>Approved host</TableCell><TableCell>Capabilities</TableCell></TableRow></TableHead>
                <TableBody>
                  {overview.data.connectionProfiles.map((profile) => (
                    <TableRow key={profile.profileId}>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{profile.profileId}</TableCell>
                      <TableCell>{profile.environment}</TableCell>
                      <TableCell>{profile.authStrategy}</TableCell>
                      <TableCell>{profile.approvedHosts.join(', ')}</TableCell>
                      <TableCell>{profile.capabilityGrants.join(', ') || 'None declared'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TableContainer component={Paper} variant="outlined" sx={{ mt: 1.5 }}>
              <Table size="small">
                <TableHead><TableRow><TableCell>Protected binding</TableCell><TableCell>Type</TableCell><TableCell>Display</TableCell><TableCell>Fingerprint</TableCell><TableCell>Capabilities</TableCell></TableRow></TableHead>
                <TableBody>
                  {overview.data.protectedResources.map((resource) => (
                    <TableRow key={resource.bindingId}>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{resource.bindingId}</TableCell>
                      <TableCell>{resource.resourceType}</TableCell>
                      <TableCell>{resource.displayValue ?? 'Policy-hidden'}</TableCell>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{resource.fingerprint}</TableCell>
                      <TableCell>{resource.capabilityGrants.join(', ') || 'None declared'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>

          </CustomerAdvancedSection>

          <Box>
            <Typography variant="h6" sx={{ mb: 1.5 }}>Live data sources</Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead><TableRow><TableCell>Source</TableCell><TableCell>Status</TableCell><TableCell>Entity / vector space</TableCell><TableCell>Source</TableCell><TableCell>Indexed</TableCell><TableCell>Failed work</TableCell><TableCell>Provider call</TableCell><TableCell>Last success</TableCell><TableCell align="right">Command</TableCell></TableRow></TableHead>
                <TableBody>
                  {overview.data.sources.map((source) => (
                    <TableRow key={source.sourceId}>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{source.sourceId}</Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                          {source.state.sourceVersion ? `${source.state.sourceVersion.slice(0, 12)}...` : 'Unversioned'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                          <Chip size="small" label={source.state.status} color={statusColor(source.state.status)} />
                          <Chip size="small" label={source.freshnessState} color={statusColor(source.freshnessState)} variant="outlined" />
                          <Chip size="small" label={`Preflight ${source.preflightState}`} color={statusColor(source.preflightState)} variant="outlined" />
                        </Stack>
                      </TableCell>
                      <TableCell>{source.entityType} / {source.vectorSpace}</TableCell>
                      <TableCell>{source.state.counts.sourceCount}</TableCell>
                      <TableCell>{source.state.counts.indexedCount}</TableCell>
                      <TableCell>{source.state.counts.failedWorkCount}</TableCell>
                      <TableCell sx={{ maxWidth: 160 }}>
                        <Tooltip title={source.state.providerCorrelationValue ?? 'No provider correlation ID recorded'}>
                          <Typography variant="body2" noWrap sx={{ fontFamily: 'monospace' }}>
                            {source.state.providerCorrelationValue ?? '—'}
                          </Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell>{formatTime(source.state.lastSuccessAt)}</TableCell>
                      <TableCell align="right">
                        <Tooltip title="Run a trusted baseline reconciliation">
                          <span>
                            <IconButton
                              size="small"
                              disabled={!canOperate || reconcile.isPending}
                              onClick={() => reconcile.mutate({ sourceId: source.sourceId, operationKey: crypto.randomUUID() })}
                            ><SyncRoundedIcon /></IconButton>
                          </span>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>

          <CustomerAdvancedSection><Box>
            <Typography variant="h6" sx={{ mb: 1.5 }}>Inbound events</Typography>
            {overview.data.webhooks.length === 0 ? (
              <Alert severity="info">This composition does not declare an inbound provider webhook.</Alert>
            ) : overview.data.webhooks.map((webhook) => (
              <Paper key={webhook.sourceId} variant="outlined" sx={{ mb: 2 }}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ md: 'center' }} sx={{ px: 2, py: 1.5 }}>
                  <Typography sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{webhook.sourceId}</Typography>
                  <Chip size="small" label={webhook.registrationState} color={statusColor(webhook.registrationState)} />
                  <Chip size="small" label={webhook.method} variant="outlined" />
                  <Typography variant="body2" color="text.secondary">Signature: {webhook.signatureHeader}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Received {webhook.counts.received} · Rejected {webhook.counts.rejected} · Duplicate {webhook.counts.duplicate} · Dead letter {webhook.counts.deadLetter}
                  </Typography>
                </Stack>
                {webhook.publicUrl ? (
                  <Stack direction="row" spacing={0.5} alignItems="center" sx={{ px: 2, pb: 1.5 }}>
                    <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere' }}>
                      {webhook.publicUrl}
                    </Typography>
                    <Tooltip title="Copy webhook URL">
                      <IconButton
                        size="small"
                        onClick={() => void navigator.clipboard.writeText(webhook.publicUrl ?? '')}
                      >
                        <ContentCopyRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                ) : null}
                <Divider />
                <WebhookEvents
                  events={webhook.recentEvents}
                  canOperate={canOperate}
                  manualReplayEnabled={webhook.manualReplayEnabled}
                  replaying={replay.isPending}
                  onReplay={(event) => replay.mutate({
                    sourceId: webhook.sourceId,
                    eventId: event.eventId,
                    operationKey: crypto.randomUUID(),
                  })}
                />
              </Paper>
            ))}
          </Box></CustomerAdvancedSection>
        </>
      ) : null}
    </Stack>
  )
}

export function ConversationHandoverDeliveryNotices({ value }: { value: ConversationHandoverOperationsOverview }) {
  return <Stack spacing={1}>
    {Object.entries(value.runtime.dispatchCounts).filter(([status, count]) => count > 0 && ['FAILED', 'DEAD', 'REJECTED', 'DEAD_LETTER'].includes(status)).map(([status, count]) => <Alert key={status} severity="warning">Human-support delivery: {count} {status}. Review delivery evidence in Advanced.</Alert>)}
    {value.runtime.recentDispatches.filter(dispatch => dispatch.failureCode).map(dispatch => <Alert key={dispatch.dispatchId} severity="warning">{dispatch.dispatchType}: {dispatch.failureCode}. Delivery evidence and recovery controls are in Advanced.</Alert>)}
    {value.connector.status?.rejectedCallbackCount ? <Alert severity="warning">Human-support provider callbacks: {value.connector.status.rejectedCallbackCount} rejected.</Alert> : null}
    {value.connector.status?.recentCallbacks.filter(callback => callback.errorClass).map(callback => <Alert key={callback.eventId} severity="warning">Conversation callback {callback.eventType}: {callback.errorClass}</Alert>)}
  </Stack>
}

export function ConversationHandoverOperations({
  value,
  canOperate,
  busy,
  onRetry,
  onExpire,
}: {
  value: ConversationHandoverOperationsOverview
  canOperate: boolean
  busy: boolean
  onRetry: (dispatchId: string) => void
  onExpire: (handoverId: string) => void
}) {
  const callbackStatus = value.connector.status
  const detailSection = useCustomerEditorSection()
  return (
    <Box>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1.5} sx={{ mb: 1.5 }}>
        <Box>
          <Typography variant="h6">Conversation handover</Typography>
          <Typography variant="body2" color="text.secondary">
            Deployment-owned conversation authority and delivery evidence. Message and intake content are not projected here.
          </Typography>
        </Box>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2.5, mb: 1.5 }}>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Chip label={`Binding ${value.runtime.channelBindingId}`} variant="outlined" />
          <Chip
            label={value.connector.reachable ? 'Connector reachable' : 'Connector unavailable'}
            color={value.connector.reachable ? 'success' : 'error'}
          />
          {Object.entries(value.runtime.handoverCounts).map(([status, count]) => (
            <Chip key={status} size="small" label={`${status} ${count}`} color={statusColor(status)} variant="outlined" />
          ))}
          <Chip
            size="small"
            label={`Oldest queued ${formatTime(value.runtime.oldestPendingDispatchAt)}`}
            variant="outlined"
          />
        </Stack>
      </Paper>

      {detailSection ? <ConversationHandoverDeliveryNotices value={value} /> : null}
      {detailSection && !value.connector.reachable ? <Alert severity="error">The conversation connector is unavailable.</Alert> : null}
      <CustomerAdvancedSection>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>Delivery queue</Typography>
      <TableContainer component={Paper} variant="outlined" sx={{ mb: 2 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Dispatch</TableCell><TableCell>Type</TableCell><TableCell>Status</TableCell>
              <TableCell>Attempts</TableCell><TableCell>Updated</TableCell><TableCell>Error</TableCell>
              <TableCell align="right">Recovery</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {value.runtime.recentDispatches.map((dispatch) => (
              <TableRow key={dispatch.dispatchId}>
                <TableCell sx={{ fontFamily: 'monospace' }}>{dispatch.dispatchId}</TableCell>
                <TableCell>{dispatch.dispatchType}</TableCell>
                <TableCell><Chip size="small" label={dispatch.status} color={statusColor(dispatch.status)} /></TableCell>
                <TableCell>{dispatch.attemptCount}</TableCell>
                <TableCell>{formatTime(dispatch.updatedAt)}</TableCell>
                <TableCell>{dispatch.failureCode ?? '—'}</TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                    <Tooltip title="Retry this dead delivery">
                      <span>
                        <IconButton
                          size="small"
                          disabled={!canOperate || busy || !dispatch.retryEligible}
                          onClick={() => onRetry(dispatch.dispatchId)}
                        ><ReplayRoundedIcon /></IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Expire this pending handover; the user must then retry or explicitly continue with AI">
                      <span>
                        <Button
                          size="small"
                          color="warning"
                          disabled={!canOperate || busy || dispatch.dispatchType !== 'OPEN_HANDOVER'
                            || !['PENDING', 'PROCESSING', 'DEAD'].includes(dispatch.status)}
                          onClick={() => onExpire(dispatch.handoverId)}
                        >
                          Expire
                        </Button>
                      </span>
                    </Tooltip>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {value.runtime.recentDispatches.length === 0 ? (
              <TableRow><TableCell colSpan={7}>No handover deliveries have been created.</TableCell></TableRow>
            ) : null}
          </TableBody>
        </Table>
      </TableContainer>

      <Typography variant="subtitle1" sx={{ mb: 1 }}>Provider callbacks</Typography>
      {!value.connector.reachable || !callbackStatus ? (
        <Alert severity="warning">Callback diagnostics are unavailable. Provider-owned delivery retries remain authoritative.</Alert>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow><TableCell>Event</TableCell><TableCell>Type</TableCell><TableCell>Status</TableCell><TableCell>Attempts</TableCell><TableCell>Received</TableCell><TableCell>Error</TableCell></TableRow>
            </TableHead>
            <TableBody>
              {callbackStatus.recentCallbacks.map((callback) => (
                <TableRow key={callback.eventId}>
                  <TableCell sx={{ fontFamily: 'monospace' }}>{callback.eventId}</TableCell>
                  <TableCell>{callback.eventType}</TableCell>
                  <TableCell><Chip size="small" label={callback.status} color={statusColor(callback.status)} /></TableCell>
                  <TableCell>{callback.attemptCount}</TableCell>
                  <TableCell>{formatTime(callback.receivedAt)}</TableCell>
                  <TableCell>{callback.errorClass ?? '—'}</TableCell>
                </TableRow>
              ))}
              {callbackStatus.recentCallbacks.length === 0 ? (
                <TableRow><TableCell colSpan={6}>No signed provider callbacks have been received.</TableCell></TableRow>
              ) : null}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      </CustomerAdvancedSection>
    </Box>
  )
}

function WebhookEvents({
  events,
  canOperate,
  manualReplayEnabled,
  replaying,
  onReplay,
}: {
  events: IntegrationWebhookEvent[]
  canOperate: boolean
  manualReplayEnabled: boolean
  replaying: boolean
  onReplay: (event: IntegrationWebhookEvent) => void
}) {
  if (events.length === 0) {
    return <Typography color="text.secondary" sx={{ p: 2 }}>No verified events have reached this deployment.</Typography>
  }
  return (
    <TableContainer>
      <Table size="small">
        <TableHead><TableRow><TableCell>Event</TableCell><TableCell>Type</TableCell><TableCell>Status</TableCell><TableCell>Received</TableCell><TableCell>Error</TableCell><TableCell align="right">Command</TableCell></TableRow></TableHead>
        <TableBody>
          {events.map((event) => (
            <TableRow key={event.eventId}>
              <TableCell sx={{ fontFamily: 'monospace' }}>{event.eventId}</TableCell>
              <TableCell>{event.eventType}</TableCell>
              <TableCell><Chip size="small" label={event.status} color={statusColor(event.status)} /></TableCell>
              <TableCell>{formatTime(event.receivedAt)}</TableCell>
              <TableCell>{event.errorClass ?? '—'}</TableCell>
              <TableCell align="right">
                <Tooltip title={manualReplayEnabled
                  ? 'Replay reconciliation for this durable event'
                  : 'Manual replay is disabled by the installed integration contract'}>
                  <span>
                    <IconButton
                      size="small"
                      disabled={!canOperate || !manualReplayEnabled || replaying || event.status === 'REJECTED'}
                      onClick={() => onReplay(event)}
                    >
                      <ReplayRoundedIcon />
                    </IconButton>
                  </span>
                </Tooltip>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
