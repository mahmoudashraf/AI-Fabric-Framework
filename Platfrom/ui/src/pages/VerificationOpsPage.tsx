import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useMemo } from 'react'
import { Link } from 'react-router-dom'
import {
  dispatchDeploymentHostedVerification,
  fetchDeploymentHostedVerificationRuns,
  fetchDeploymentSecretUsage,
  fetchDeploymentVerificationRollouts,
  fetchMarketplaceInferenceServiceHealth,
  fetchMarketplaceInferenceServices,
  recreateDeploymentVerificationRollouts,
  reconcileMarketplaceInferenceService,
  type DeploymentHostedVerificationRunSummary,
  type DeploymentSecretUsageItemSummary,
  type DeploymentSecretUsageSummary,
  type DeploymentVerificationRolloutItemSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { HostedVerificationRunHistory } from '../components/HostedVerificationRunHistory'

const SHARED_INFERENCE_SERVICE_REF = 'shared-ollama-orchestration'
const ROLLOUT_RUN_ORDER = ['marketplace', 'ecommerce', 'qdrant', 'pinecone', 'milvus', 'weaviate'] as const

function verificationStatusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (['PASSED', 'READY', 'ACTIVE', 'SUCCESS'].includes(status)) {
    return 'success'
  }
  if (['RUNNING', 'QUEUED'].includes(status)) {
    return 'info'
  }
  if (['WARNING', 'REQUIRED'].includes(status)) {
    return 'warning'
  }
  if (['FAILED', 'BLOCKED', 'MISSING', 'SECRETS'].includes(status)) {
    return 'error'
  }
  return 'default'
}

function hostedProfileOptionLabel(profile: string): string {
  if (profile === 'marketplace-runtime') {
    return 'Marketplace runtime'
  }
  if (profile === 'ecommerce') {
    return 'Ecommerce deployment'
  }
  return 'Vector deployment'
}

function requiredSecretItems(summary: DeploymentSecretUsageSummary | undefined): DeploymentSecretUsageItemSummary[] {
  return (summary?.secrets ?? []).filter((item) => item.required)
}

function missingRequiredSecretItems(summary: DeploymentSecretUsageSummary | undefined): DeploymentSecretUsageItemSummary[] {
  return requiredSecretItems(summary).filter((item) => !item.present)
}

function sharedInferenceReady(status: string | null | undefined): boolean {
  return ['ACTIVE', 'READY', 'PASSED', 'SUCCESS'].includes((status ?? '').toUpperCase())
}

function rolloutRunBlockReason(
  rollout: DeploymentVerificationRolloutItemSummary,
  secretUsage: DeploymentSecretUsageSummary | undefined,
  secretQueryState: 'loading' | 'error' | 'ready' | 'none',
): string | null {
  if (!rollout.exists || !rollout.deploymentId) {
    return 'Canonical deployment is missing.'
  }
  if (rollout.archived) {
    return 'Canonical deployment is archived.'
  }
  if (!rollout.runtimeBaseUrl) {
    return 'Runtime URL is not available yet.'
  }
  if (secretQueryState === 'loading') {
    return 'Platform secret readiness is still loading.'
  }
  if (secretQueryState === 'error') {
    return 'Platform secret readiness could not be loaded.'
  }
  if (secretQueryState === 'none') {
    return 'Platform secret readiness is not available yet.'
  }
  const missingRequiredSecrets = missingRequiredSecretItems(secretUsage)
  if (missingRequiredSecrets.length > 0) {
    return `Missing required secrets: ${missingRequiredSecrets.map((item) => item.secretName).join(', ')}`
  }
  return null
}

function rolloutOperatorStatus(
  rollout: DeploymentVerificationRolloutItemSummary,
  blockReason: string | null,
): string {
  if (blockReason?.startsWith('Missing required secrets:')) {
    return 'SECRETS'
  }
  if (!rollout.exists || !rollout.deploymentId) {
    return 'MISSING'
  }
  if (rollout.archived) {
    return 'ARCHIVED'
  }
  if (rollout.verificationReady && !blockReason) {
    return 'READY'
  }
  if (!blockReason) {
    return 'RUNNABLE'
  }
  return 'BLOCKED'
}

function rolloutHealthColor(
  rollout: DeploymentVerificationRolloutItemSummary,
  blockReason: string | null,
): 'success' | 'warning' | 'error' | 'default' {
  if (blockReason?.startsWith('Missing required secrets:')) {
    return 'error'
  }
  if (rollout.verificationReady && !blockReason) {
    return 'success'
  }
  if (!rollout.exists || rollout.archived) {
    return 'error'
  }
  if (rollout.latestReleaseStatus === 'APPLIED_VERIFIED' && rollout.latestVerificationStatus === 'PASSED' && !blockReason) {
    return 'success'
  }
  if (rollout.latestReleaseStatus === 'PRE_APPLY_BLOCKED' || rollout.latestVerificationStatus === 'FAILED') {
    return 'error'
  }
  return 'warning'
}

function sortRuns(runs: DeploymentHostedVerificationRunSummary[]): DeploymentHostedVerificationRunSummary[] {
  return [...runs].sort((left, right) => {
    const leftTime = new Date(left.createdAt).getTime()
    const rightTime = new Date(right.createdAt).getTime()
    return rightTime - leftTime
  })
}

function normalizeStatusValue(value: string | null | undefined): string {
  const normalized = (value ?? '').trim().toUpperCase()
  return normalized.length > 0 ? normalized : 'UNKNOWN'
}

function summarizeStatusCounts(values: Array<string | null | undefined>): Array<{ label: string; count: number }> {
  const counts = new Map<string, number>()
  values.forEach((value) => {
    const key = normalizeStatusValue(value)
    counts.set(key, (counts.get(key) ?? 0) + 1)
  })
  return [...counts.entries()]
    .map(([label, count]) => ({ label, count }))
    .sort((left, right) => {
      if (right.count !== left.count) {
        return right.count - left.count
      }
      return left.label.localeCompare(right.label)
    })
}

function serviceStatusColor(status: string): 'success' | 'warning' | 'error' | 'default' {
  const normalized = normalizeStatusValue(status)
  if (['ACTIVE', 'READY', 'NO_DRIFT'].includes(normalized)) {
    return 'success'
  }
  if (['FAILED', 'BLOCKED', 'ERROR', 'RAILWAY_RESOURCE_DRIFT', 'ENDPOINT_DRIFT', 'SECRET_DRIFT'].includes(normalized)) {
    return 'error'
  }
  if (['DEGRADED', 'PROVISIONING', 'CREATED', 'SCALE_DRIFT', 'SKIPPED', 'WARNING'].includes(normalized)) {
    return 'warning'
  }
  return 'default'
}

function SummarySectionCard(props: {
  title: string
  subtitle: string
  children: ReactNode
}) {
  return (
    <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
      <CardContent>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6">{props.title}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {props.subtitle}
            </Typography>
          </Box>
          {props.children}
        </Stack>
      </CardContent>
    </Card>
  )
}

export function VerificationOpsPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const canManageHostedVerification = auth.session?.enabled ? auth.session.canManageUsers : true

  const verificationRolloutsQuery = useQuery({
    queryKey: ['deployment-verification-rollouts'],
    queryFn: fetchDeploymentVerificationRollouts,
    enabled: canManageHostedVerification,
  })

  const sharedInferenceHealthQuery = useQuery({
    queryKey: ['marketplace', 'inference-services', SHARED_INFERENCE_SERVICE_REF, 'health'],
    queryFn: () => fetchMarketplaceInferenceServiceHealth(SHARED_INFERENCE_SERVICE_REF),
    enabled: canManageHostedVerification,
  })

  const inferenceServicesQuery = useQuery({
    queryKey: ['marketplace', 'inference-services'],
    queryFn: fetchMarketplaceInferenceServices,
    enabled: canManageHostedVerification,
  })

  const orderedRollouts = useMemo(() => {
    const items = verificationRolloutsQuery.data?.items ?? []
    return ROLLOUT_RUN_ORDER
      .map((key) => items.find((item) => item.key === key))
      .filter((item): item is DeploymentVerificationRolloutItemSummary => item != null)
  }, [verificationRolloutsQuery.data])

  const rolloutTargets = useMemo(
    () => orderedRollouts.filter((rollout) => Boolean(rollout.deploymentId)),
    [orderedRollouts],
  )

  const rolloutHostedRunsQueries = useQueries({
    queries: canManageHostedVerification
      ? rolloutTargets.map((rollout) => ({
        queryKey: ['deployment-hosted-verification-runs', rollout.deploymentId],
        queryFn: () => fetchDeploymentHostedVerificationRuns(rollout.deploymentId!),
        enabled: Boolean(rollout.deploymentId),
        refetchInterval: (query: { state: { data?: DeploymentHostedVerificationRunSummary[] } }) => {
          const runs = query.state.data ?? []
          return runs.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING') ? 4000 : false
        },
      }))
      : [],
  })

  const rolloutSecretUsageQueries = useQueries({
    queries: canManageHostedVerification
      ? rolloutTargets.map((rollout) => ({
        queryKey: ['deployment-secret-usage', rollout.deploymentId],
        queryFn: () => fetchDeploymentSecretUsage(rollout.deploymentId!),
        enabled: Boolean(rollout.deploymentId),
        staleTime: 30_000,
      }))
      : [],
  })

  const rolloutHostedRuns = useMemo(
    () => sortRuns(rolloutHostedRunsQueries.flatMap((query) => query.data ?? [])),
    [rolloutHostedRunsQueries],
  )

  const latestRolloutHostedRunByDeploymentId = useMemo(() => {
    const lookup = new Map<string, DeploymentHostedVerificationRunSummary>()
    rolloutHostedRuns.forEach((run) => {
      if (!lookup.has(run.deploymentId)) {
        lookup.set(run.deploymentId, run)
      }
    })
    return lookup
  }, [rolloutHostedRuns])

  const deploymentStatusSummary = useMemo(() => ({
    deployment: summarizeStatusCounts(orderedRollouts.map((rollout) => rollout.deploymentStatus)),
    release: summarizeStatusCounts(orderedRollouts.map((rollout) => rollout.latestReleaseStatus)),
    verification: summarizeStatusCounts(orderedRollouts.map((rollout) => rollout.latestVerificationStatus)),
  }), [orderedRollouts])

  const inferenceServices = inferenceServicesQuery.data ?? []
  const sharedInferenceService = useMemo(
    () => inferenceServices.find((service) => service.serviceRef === SHARED_INFERENCE_SERVICE_REF) ?? null,
    [inferenceServices],
  )
  const inferenceServiceSummary = useMemo(() => {
    const total = inferenceServices.length
    const active = inferenceServices.filter((service) => ['ACTIVE', 'READY'].includes(normalizeStatusValue(service.status))).length
    const failed = inferenceServices.filter((service) =>
      ['FAILED', 'BLOCKED', 'ERROR'].includes(normalizeStatusValue(service.status))
      || ['RAILWAY_RESOURCE_DRIFT', 'ENDPOINT_DRIFT', 'SECRET_DRIFT'].includes(normalizeStatusValue(service.driftStatus))).length
    const degraded = inferenceServices.filter((service) =>
      ['DEGRADED', 'PROVISIONING', 'CREATED'].includes(normalizeStatusValue(service.status))
      || ['SCALE_DRIFT'].includes(normalizeStatusValue(service.driftStatus))).length
    const withActiveDependents = inferenceServices.filter((service) => service.dependentActiveDeploymentsCount > 0).length
    return { total, active, failed, degraded, withActiveDependents }
  }, [inferenceServices])

  const secretUsageStateByDeploymentId = useMemo(() => {
    const states = new Map<string, {
      queryState: 'loading' | 'error' | 'ready' | 'none'
      summary?: DeploymentSecretUsageSummary
      error?: string
    }>()
    rolloutTargets.forEach((rollout, index) => {
      const query = rolloutSecretUsageQueries[index]
      if (!rollout.deploymentId) {
        return
      }
      if (!query || query.isLoading) {
        states.set(rollout.deploymentId, { queryState: 'loading' })
        return
      }
      if (query.isError) {
        states.set(rollout.deploymentId, {
          queryState: 'error',
          error: query.error instanceof Error ? query.error.message : 'Failed to load secret readiness.',
        })
        return
      }
      states.set(rollout.deploymentId, {
        queryState: query.data ? 'ready' : 'none',
        summary: query.data,
      })
    })
    return states
  }, [rolloutSecretUsageQueries, rolloutTargets])

  const rolloutReadiness = useMemo(() => orderedRollouts.map((rollout) => {
    const secretState = rollout.deploymentId
      ? secretUsageStateByDeploymentId.get(rollout.deploymentId)
      : undefined
    const blockReason = rolloutRunBlockReason(
      rollout,
      secretState?.summary,
      secretState?.queryState ?? 'none',
    )
    return {
      rollout,
      blockReason,
    }
  }), [orderedRollouts, secretUsageStateByDeploymentId])

  const canonicalRolloutSummary = useMemo(() => {
    const total = orderedRollouts.length
    const ready = rolloutReadiness.filter((entry) => entry.rollout.verificationReady && entry.blockReason == null).length
    const runnable = rolloutReadiness.filter((entry) => entry.blockReason == null).length
    const blocked = rolloutReadiness.filter((entry) => entry.blockReason != null).length
    const missing = orderedRollouts.filter((rollout) => !rollout.exists || !rollout.deploymentId).length
    const archived = orderedRollouts.filter((rollout) => rollout.archived).length
    return { total, ready, runnable, blocked, missing, archived }
  }, [orderedRollouts, rolloutReadiness])

  const recreateRolloutsMutation = useMutation({
    mutationFn: () => recreateDeploymentVerificationRollouts([...ROLLOUT_RUN_ORDER]),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['deployment-verification-rollouts'] })
    },
  })

  const reconcileSharedInferenceMutation = useMutation({
    mutationFn: () => reconcileMarketplaceInferenceService(SHARED_INFERENCE_SERVICE_REF),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['marketplace', 'inference-services'] })
      await queryClient.invalidateQueries({ queryKey: ['marketplace', 'inference-services', SHARED_INFERENCE_SERVICE_REF] })
      await queryClient.invalidateQueries({ queryKey: ['marketplace', 'inference-services', SHARED_INFERENCE_SERVICE_REF, 'health'] })
      await queryClient.invalidateQueries({ queryKey: ['marketplace', 'inference-services', SHARED_INFERENCE_SERVICE_REF, 'activity'] })
    },
  })

  const runRolloutHostedVerificationMutation = useMutation({
    mutationFn: async (rollout: DeploymentVerificationRolloutItemSummary) => {
      if (!rollout.deploymentId) {
        throw new Error(`${rollout.displayName} does not have a canonical deployment yet.`)
      }
      const secretState = secretUsageStateByDeploymentId.get(rollout.deploymentId)
      const blockReason = rolloutRunBlockReason(rollout, secretState?.summary, secretState?.queryState ?? 'none')
      if (blockReason) {
        throw new Error(`${rollout.displayName}: ${blockReason}`)
      }
      return dispatchDeploymentHostedVerification(rollout.deploymentId, {
        profile: rollout.verificationProfile,
      })
    },
    onSuccess: async (_, rollout) => {
      await queryClient.invalidateQueries({ queryKey: ['deployment-hosted-verification-runs', rollout.deploymentId] })
      await queryClient.invalidateQueries({ queryKey: ['deployment-verification-rollouts'] })
    },
  })

  const runOrderedHostedVerificationMutation = useMutation({
    mutationFn: async () => {
      if (!sharedInferenceReady(sharedInferenceHealthQuery.data?.status)) {
        throw new Error('Shared inference service is not ready. Reconcile it first, then queue the ordered verification run map.')
      }

      const readiness = orderedRollouts.map((rollout) => {
        const secretState = rollout.deploymentId
          ? secretUsageStateByDeploymentId.get(rollout.deploymentId)
          : undefined
        const blockReason = rolloutRunBlockReason(
          rollout,
          secretState?.summary,
          secretState?.queryState ?? 'none',
        )
        return {
          rollout,
          blockReason,
        }
      })

      const runnable = readiness.filter((entry) => entry.blockReason == null)
      if (runnable.length === 0) {
        throw new Error('No canonical rollout is currently runnable. Recreate the rollouts, wait for secret readiness to load, then resolve any missing prerequisites shown on this page.')
      }

      const queued: string[] = []
      const skipped = readiness
        .filter((entry) => entry.blockReason != null)
        .map((entry) => `${entry.rollout.displayName} (${entry.blockReason})`)

      for (const entry of runnable) {
        await dispatchDeploymentHostedVerification(entry.rollout.deploymentId!, {
          profile: entry.rollout.verificationProfile,
        })
        queued.push(entry.rollout.displayName)
      }

      return {
        queued,
        skipped,
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['deployment-verification-rollouts'] })
      await Promise.all(rolloutTargets.map((rollout) =>
        queryClient.invalidateQueries({ queryKey: ['deployment-hosted-verification-runs', rollout.deploymentId] })))
    },
  })

  if (!canManageHostedVerification) {
    return (
      <Stack spacing={3}>
        <Box>
          <Chip label="Verification Ops" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
            Platform-admin verification orchestration
          </Typography>
        </Box>
        <Alert severity="info">
          Platform admin access is required to recreate canonical verification rollouts, inspect platform-visible secret readiness, and queue hosted verification in fleet order.
        </Alert>
      </Stack>
    )
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Verification Ops" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Platform-admin verification orchestration
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 1080 }}>
          This page is the control-plane run map for rebuilding the verification fleet after cleanup or drift.
          It runs in platform order, shows live hosted-verification output, and exposes platform-visible secret readiness
          so operators can see whether each rollout is runnable before queueing it.
        </Typography>
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={4}>
          <SummarySectionCard
            title="Canonical fleet summary"
            subtitle="High-level readiness for the six canonical verification deployments."
          >
            {verificationRolloutsQuery.isLoading ? (
              <Typography color="text.secondary">Loading canonical fleet summary…</Typography>
            ) : verificationRolloutsQuery.isError ? (
              <Alert severity="error">
                {verificationRolloutsQuery.error instanceof Error
                  ? verificationRolloutsQuery.error.message
                  : 'Failed to load canonical fleet summary.'}
              </Alert>
            ) : (
              <Stack spacing={1.5}>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={`Total ${canonicalRolloutSummary.total}`} variant="outlined" />
                  <Chip label={`Ready ${canonicalRolloutSummary.ready}`} color="success" variant="outlined" />
                  <Chip label={`Runnable ${canonicalRolloutSummary.runnable}`} color="primary" variant="outlined" />
                  <Chip label={`Blocked ${canonicalRolloutSummary.blocked}`} color={canonicalRolloutSummary.blocked > 0 ? 'warning' : 'default'} variant="outlined" />
                </Stack>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={`Missing ${canonicalRolloutSummary.missing}`} color={canonicalRolloutSummary.missing > 0 ? 'error' : 'default'} variant="outlined" />
                  <Chip label={`Archived ${canonicalRolloutSummary.archived}`} color={canonicalRolloutSummary.archived > 0 ? 'error' : 'default'} variant="outlined" />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {verificationRolloutsQuery.data?.summaryMessage ?? 'Canonical rollout summary is ready.'}
                </Typography>
              </Stack>
            )}
          </SummarySectionCard>
        </Grid>

        <Grid item xs={12} lg={4}>
          <SummarySectionCard
            title="Inference services summary"
            subtitle="Shared inference health plus aggregate platform-managed inference-service state."
          >
            {inferenceServicesQuery.isLoading || sharedInferenceHealthQuery.isLoading ? (
              <Typography color="text.secondary">Loading inference-service summary…</Typography>
            ) : inferenceServicesQuery.isError ? (
              <Alert severity="error">
                {inferenceServicesQuery.error instanceof Error
                  ? inferenceServicesQuery.error.message
                  : 'Failed to load inference services.'}
              </Alert>
            ) : sharedInferenceHealthQuery.isError ? (
              <Alert severity="error">
                {sharedInferenceHealthQuery.error instanceof Error
                  ? sharedInferenceHealthQuery.error.message
                  : 'Failed to load shared inference service health.'}
              </Alert>
            ) : (
              <Stack spacing={1.5}>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={`Services ${inferenceServiceSummary.total}`} variant="outlined" />
                  <Chip label={`Active ${inferenceServiceSummary.active}`} color="success" variant="outlined" />
                  <Chip label={`Degraded ${inferenceServiceSummary.degraded}`} color={inferenceServiceSummary.degraded > 0 ? 'warning' : 'default'} variant="outlined" />
                  <Chip label={`Failed ${inferenceServiceSummary.failed}`} color={inferenceServiceSummary.failed > 0 ? 'error' : 'default'} variant="outlined" />
                  <Chip label={`Dependents ${inferenceServiceSummary.withActiveDependents}`} color="warning" variant="outlined" />
                </Stack>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip
                    label={`Shared ${sharedInferenceHealthQuery.data?.status ?? 'UNKNOWN'}`}
                    color={verificationStatusColor(sharedInferenceHealthQuery.data?.status ?? 'UNKNOWN')}
                    variant="outlined"
                  />
                  {sharedInferenceService ? (
                    <Chip
                      label={`Fleet ${sharedInferenceService.status}`}
                      color={serviceStatusColor(sharedInferenceService.status)}
                      variant="outlined"
                    />
                  ) : null}
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {sharedInferenceHealthQuery.data?.driftMessage
                    ?? sharedInferenceService?.driftMessage
                    ?? sharedInferenceHealthQuery.data?.lastProbeMessage
                    ?? 'Shared inference service is healthy.'}
                </Typography>
              </Stack>
            )}
          </SummarySectionCard>
        </Grid>

        <Grid item xs={12} lg={4}>
          <SummarySectionCard
            title="Deployment status summary"
            subtitle="Aggregated canonical deployment, latest release, and verification status counts."
          >
            {verificationRolloutsQuery.isLoading ? (
              <Typography color="text.secondary">Loading deployment status summary…</Typography>
            ) : verificationRolloutsQuery.isError ? (
              <Alert severity="error">
                {verificationRolloutsQuery.error instanceof Error
                  ? verificationRolloutsQuery.error.message
                  : 'Failed to load deployment status summary.'}
              </Alert>
            ) : (
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.75 }}>
                    Deployment status
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {deploymentStatusSummary.deployment.map((entry) => (
                      <Chip key={`deployment-${entry.label}`} label={`${entry.label} ${entry.count}`} variant="outlined" />
                    ))}
                  </Stack>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.75 }}>
                    Latest release
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {deploymentStatusSummary.release.map((entry) => (
                      <Chip key={`release-${entry.label}`} label={`${entry.label} ${entry.count}`} variant="outlined" />
                    ))}
                  </Stack>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.75 }}>
                    Latest verification
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {deploymentStatusSummary.verification.map((entry) => (
                      <Chip key={`verification-${entry.label}`} label={`${entry.label} ${entry.count}`} variant="outlined" />
                    ))}
                  </Stack>
                </Box>
              </Stack>
            )}
          </SummarySectionCard>
        </Grid>
      </Grid>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Alert severity="info">
              Recovery order: 1. shared inference service health, 2. canonical rollout recreation, 3. hosted verification in order:
              marketplace, ecommerce, qdrant, pinecone, milvus, weaviate.
            </Alert>

            <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2}>
              <Button
                variant="contained"
                startIcon={<RefreshRoundedIcon />}
                disabled={recreateRolloutsMutation.isPending}
                onClick={() => recreateRolloutsMutation.mutate()}
              >
                {recreateRolloutsMutation.isPending ? 'Recreating rollouts…' : 'Recreate canonical rollouts'}
              </Button>
              <Button
                variant="outlined"
                startIcon={<RefreshRoundedIcon />}
                disabled={runOrderedHostedVerificationMutation.isPending || orderedRollouts.length === 0}
                onClick={() => runOrderedHostedVerificationMutation.mutate()}
              >
                {runOrderedHostedVerificationMutation.isPending ? 'Queueing hosted verification…' : 'Queue ordered hosted verification'}
              </Button>
              <Button component={Link} to="/inference-services" variant="text">
                Open inference services
              </Button>
            </Stack>

            {recreateRolloutsMutation.isError ? (
              <Alert severity="error">
                {recreateRolloutsMutation.error instanceof Error
                  ? recreateRolloutsMutation.error.message
                  : 'Failed to recreate canonical rollouts.'}
              </Alert>
            ) : null}
            {recreateRolloutsMutation.isSuccess ? (
              <Alert severity="success">{recreateRolloutsMutation.data.summaryMessage}</Alert>
            ) : null}
            {runOrderedHostedVerificationMutation.isError ? (
              <Alert severity="error">
                {runOrderedHostedVerificationMutation.error instanceof Error
                  ? runOrderedHostedVerificationMutation.error.message
                  : 'Failed to queue ordered hosted verification.'}
              </Alert>
            ) : null}
            {runOrderedHostedVerificationMutation.isSuccess ? (
              <Alert severity="success">
                Queued hosted verification for {runOrderedHostedVerificationMutation.data.queued.join(', ') || 'no rollouts'}.
                {runOrderedHostedVerificationMutation.data.skipped.length > 0
                  ? ` Skipped: ${runOrderedHostedVerificationMutation.data.skipped.join('; ')}.`
                  : ''}
              </Alert>
            ) : null}
            {verificationRolloutsQuery.isLoading ? (
              <Alert severity="info">Loading canonical verification rollouts…</Alert>
            ) : null}
            {verificationRolloutsQuery.isError ? (
              <Alert severity="error">
                {verificationRolloutsQuery.error instanceof Error
                  ? verificationRolloutsQuery.error.message
                  : 'Failed to load canonical verification rollouts.'}
              </Alert>
            ) : null}

            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Order</TableCell>
                  <TableCell>Step</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Current signal</TableCell>
                  <TableCell>Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                <TableRow hover>
                  <TableCell>1</TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      Shared inference service
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {SHARED_INFERENCE_SERVICE_REF}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {sharedInferenceHealthQuery.isLoading ? (
                      <Chip size="small" label="LOADING" variant="outlined" />
                    ) : sharedInferenceHealthQuery.isError ? (
                      <Chip size="small" label="FAILED" color="error" variant="outlined" />
                    ) : (
                      <Chip
                        size="small"
                        label={sharedInferenceHealthQuery.data?.status ?? 'UNKNOWN'}
                        color={verificationStatusColor(sharedInferenceHealthQuery.data?.status ?? 'UNKNOWN')}
                        variant="outlined"
                      />
                    )}
                  </TableCell>
                  <TableCell sx={{ maxWidth: 460 }}>
                    {sharedInferenceHealthQuery.isError ? (
                      sharedInferenceHealthQuery.error instanceof Error
                        ? sharedInferenceHealthQuery.error.message
                        : 'Failed to load shared inference service health.'
                    ) : (
                      sharedInferenceHealthQuery.data?.driftMessage
                        ?? sharedInferenceHealthQuery.data?.lastProbeMessage
                        ?? 'Shared inference service is healthy.'
                    )}
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={1}>
                      <Button
                        size="small"
                        variant="outlined"
                        disabled={reconcileSharedInferenceMutation.isPending}
                        onClick={() => reconcileSharedInferenceMutation.mutate()}
                      >
                        {reconcileSharedInferenceMutation.isPending ? 'Reconciling…' : 'Reconcile'}
                      </Button>
                      <Button component={Link} to="/inference-services" size="small" variant="text">
                        Open
                      </Button>
                    </Stack>
                  </TableCell>
                </TableRow>

                {orderedRollouts.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography color="text.secondary">
                        No canonical rollout inventory is available yet. Recreate the rollouts to repopulate the run map.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : orderedRollouts.map((rollout, index) => {
                  const secretState = rollout.deploymentId
                    ? secretUsageStateByDeploymentId.get(rollout.deploymentId)
                    : undefined
                  const blockReason = rolloutRunBlockReason(
                    rollout,
                    secretState?.summary,
                    secretState?.queryState ?? 'none',
                  )
                  const latestHostedRun = rollout.deploymentId
                    ? latestRolloutHostedRunByDeploymentId.get(rollout.deploymentId)
                    : undefined
                  return (
                    <TableRow key={rollout.key} hover>
                      <TableCell>{index + 2}</TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {rollout.displayName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {hostedProfileOptionLabel(rollout.verificationProfile)} · {rollout.deploymentId ?? 'no deployment'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={rolloutOperatorStatus(rollout, blockReason)}
                          color={rolloutHealthColor(rollout, blockReason)}
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 460 }}>
                        <Typography variant="body2">{blockReason ?? rollout.readinessMessage}</Typography>
                        {rollout.latestReleaseStatus || rollout.latestVerificationStatus ? (
                          <Typography variant="caption" color="text.secondary" display="block">
                            Release {rollout.latestReleaseStatus ?? '—'} · verification {rollout.latestVerificationStatus ?? '—'}
                          </Typography>
                        ) : null}
                        {latestHostedRun ? (
                          <Typography variant="caption" color="text.secondary" display="block">
                            Hosted verification {latestHostedRun.status.toLowerCase()} · {latestHostedRun.summaryMessage}
                          </Typography>
                        ) : null}
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1}>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={blockReason != null || runRolloutHostedVerificationMutation.isPending}
                            onClick={() => runRolloutHostedVerificationMutation.mutate(rollout)}
                          >
                            {runRolloutHostedVerificationMutation.isPending ? 'Queueing…' : 'Run hosted verification'}
                          </Button>
                          {rollout.deploymentId ? (
                            <Button
                              component={Link}
                              size="small"
                              variant="text"
                              to={`/verification?deploymentId=${encodeURIComponent(rollout.deploymentId)}`}
                            >
                              Open deployment
                            </Button>
                          ) : null}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>

            {reconcileSharedInferenceMutation.isError ? (
              <Alert severity="error">
                {reconcileSharedInferenceMutation.error instanceof Error
                  ? reconcileSharedInferenceMutation.error.message
                  : 'Failed to reconcile shared inference service.'}
              </Alert>
            ) : null}
            {reconcileSharedInferenceMutation.isSuccess ? (
              <Alert severity="success">
                Reconciled {reconcileSharedInferenceMutation.data.displayName}.
              </Alert>
            ) : null}
            {runRolloutHostedVerificationMutation.isError ? (
              <Alert severity="error">
                {runRolloutHostedVerificationMutation.error instanceof Error
                  ? runRolloutHostedVerificationMutation.error.message
                  : 'Failed to queue rollout hosted verification.'}
              </Alert>
            ) : null}
            {runRolloutHostedVerificationMutation.isSuccess ? (
              <Alert severity="success">{runRolloutHostedVerificationMutation.data.summaryMessage}</Alert>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Platform-visible secret readiness</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Only secret names and readiness state are shown here. Secret values stay hidden, but operators can still see whether each rollout has the required platform-managed inputs to run verification.
              </Typography>
            </Box>

            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Rollout</TableCell>
                  <TableCell>Required secrets</TableCell>
                  <TableCell>Missing required</TableCell>
                  <TableCell>Summary</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {orderedRollouts.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={4}>
                      <Typography color="text.secondary">
                        Secret readiness will appear here after the canonical rollout inventory is recreated.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : orderedRollouts.map((rollout) => {
                  const secretState = rollout.deploymentId
                    ? secretUsageStateByDeploymentId.get(rollout.deploymentId)
                    : undefined
                  const requiredSecrets = requiredSecretItems(secretState?.summary)
                  const missingRequiredSecrets = missingRequiredSecretItems(secretState?.summary)
                  return (
                    <TableRow key={`${rollout.key}-secrets`} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {rollout.displayName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {rollout.deploymentId ?? 'no deployment'}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 420 }}>
                        {secretState?.queryState === 'loading' ? (
                          <Typography color="text.secondary">Loading…</Typography>
                        ) : secretState?.queryState === 'error' ? (
                          <Typography color="error.main">{secretState.error}</Typography>
                        ) : requiredSecrets.length === 0 ? (
                          <Typography color="text.secondary">No required deployment secrets.</Typography>
                        ) : (
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            {requiredSecrets.map((item) => (
                              <Chip
                                key={`${rollout.key}-${item.secretName}`}
                                size="small"
                                label={`${item.displayName} (${item.secretName})`}
                                color={item.present ? 'success' : 'error'}
                                variant="outlined"
                              />
                            ))}
                          </Stack>
                        )}
                      </TableCell>
                      <TableCell sx={{ maxWidth: 320 }}>
                        {secretState?.queryState === 'loading' ? (
                          <Typography color="text.secondary">Waiting for platform inspection…</Typography>
                        ) : missingRequiredSecrets.length === 0 ? (
                          <Typography color="text.secondary">None.</Typography>
                        ) : (
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            {missingRequiredSecrets.map((item) => (
                              <Chip
                                key={`${rollout.key}-${item.secretName}-missing`}
                                size="small"
                                label={item.secretName}
                                color="error"
                                variant="outlined"
                              />
                            ))}
                          </Stack>
                        )}
                      </TableCell>
                      <TableCell sx={{ maxWidth: 420 }}>
                        {secretState?.queryState === 'error'
                          ? secretState.error
                          : secretState?.summary?.summaryMessage ?? 'No secret readiness data yet.'}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Ordered hosted verification progress and output</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Hosted verification runs stream the same step diagnostics and raw log output that the platform-hosted scripts produce. Use this surface instead of shell output when you need fleet progress from the admin UI.
              </Typography>
            </Box>

            {rolloutHostedRunsQueries.some((query) => query.isLoading) ? (
              <Typography color="text.secondary">Loading rollout hosted verification runs…</Typography>
            ) : rolloutHostedRunsQueries.some((query) => query.isError) ? (
              <Alert severity="error">
                At least one rollout hosted verification history query failed. Refresh the page or open the deployment-scoped verification page for a single rollout.
              </Alert>
            ) : rolloutHostedRuns.length === 0 ? (
              <Alert severity="info">
                No rollout hosted verification runs have been recorded yet. Queue the ordered run map or a single rollout to see live progress and raw script output here.
              </Alert>
            ) : (
              <HostedVerificationRunHistory runs={rolloutHostedRuns.slice(0, 8)} showDeploymentId />
            )}
          </Stack>
        </CardContent>
      </Card>

      <Alert severity="info" icon={<OpenInNewRoundedIcon fontSize="inherit" />}>
        Deployment-scoped release gating stays on the existing verification page. Use this admin page only for canonical rollout recreation, fleet-order verification, and platform-visible readiness checks.
      </Alert>
    </Stack>
  )
}
