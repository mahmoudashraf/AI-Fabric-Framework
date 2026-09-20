import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import Inventory2RoundedIcon from '@mui/icons-material/Inventory2Rounded'
import PublishRoundedIcon from '@mui/icons-material/PublishRounded'
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
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import {
  createDeploymentSourceArtifact,
  fetchDeploymentBehaviors,
  fetchDeploymentExecutionExtensions,
  fetchDeploymentSourceArtifacts,
  promoteDeploymentSourceArtifact,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

type ArtifactForm = {
  serviceName: string
  imageRepository: string
  imageTag: string
  imageDigest: string
  gitCommitSha: string
  buildRunId: string
  sbomRef: string
  aiFabricVersion: string
  supportedBehaviorTypes: string[]
  supportedActivationSources: string[]
  supportedChannelBindings: string[]
  supportedExecutionExtensions: string[]
  capabilities: string[]
  endpointClasses: string[]
  migrationIds: string[]
  verificationPackIds: string[]
  attested: boolean
}

const INITIAL_FORM: ArtifactForm = {
  serviceName: 'ai-fabric-runtime',
  imageRepository: '',
  imageTag: '',
  imageDigest: '',
  gitCommitSha: '',
  buildRunId: '',
  sbomRef: '',
  aiFabricVersion: '0.7.1',
  supportedBehaviorTypes: [],
  supportedActivationSources: [],
  supportedChannelBindings: [],
  supportedExecutionExtensions: [],
  capabilities: [],
  endpointClasses: [],
  migrationIds: [],
  verificationPackIds: [],
  attested: false,
}

function toggle(values: string[], value: string): string[] {
  return values.includes(value) ? values.filter((item) => item !== value) : [...values, value]
}

function union(...groups: string[][]): string[] {
  return Array.from(new Set(groups.flat())).sort()
}

function shortHash(value: string | null): string {
  return value ? value.slice(0, 12) : 'none'
}

function SelectionGroup({
  title,
  values,
  selected,
  disabled,
  onToggle,
}: {
  title: string
  values: string[]
  selected: string[]
  disabled?: boolean
  onToggle: (value: string) => void
}) {
  return (
    <Box component="section">
      <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{title}</Typography>
      {values.length === 0 ? (
        <Typography variant="body2" color="text.secondary">None declared.</Typography>
      ) : (
        <Grid container spacing={0.5}>
          {values.map((value) => (
            <Grid item xs={12} md={6} key={value}>
              <FormControlLabel
                control={(
                  <Checkbox
                    size="small"
                    checked={selected.includes(value)}
                    disabled={disabled}
                    onChange={() => onToggle(value)}
                  />
                )}
                label={<Typography variant="body2">{value}</Typography>}
              />
            </Grid>
          ))}
        </Grid>
      )}
    </Box>
  )
}

export function RuntimeArtifactsPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<ArtifactForm>(INITIAL_FORM)
  const [promotionChannels, setPromotionChannels] = useState<Record<string, string>>({})
  const isPlatformAdmin = auth.session?.enabled ? auth.session.role === 'PLATFORM_ADMIN' : true

  const artifactsQuery = useQuery({
    queryKey: ['deployment-source-artifacts'],
    queryFn: () => fetchDeploymentSourceArtifacts(),
    enabled: isPlatformAdmin,
  })
  const behaviorsQuery = useQuery({
    queryKey: ['deployment-behaviors'],
    queryFn: fetchDeploymentBehaviors,
    enabled: isPlatformAdmin,
  })
  const extensionsQuery = useQuery({
    queryKey: ['deployment-execution-extensions'],
    queryFn: fetchDeploymentExecutionExtensions,
    enabled: isPlatformAdmin,
  })

  const selectedBehaviors = useMemo(
    () => (behaviorsQuery.data ?? []).filter((behavior) => form.supportedBehaviorTypes.includes(behavior.code)),
    [behaviorsQuery.data, form.supportedBehaviorTypes],
  )
  const selectedExtensions = useMemo(
    () => (extensionsQuery.data ?? []).filter((extension) => form.supportedExecutionExtensions.includes(extension.code)),
    [extensionsQuery.data, form.supportedExecutionExtensions],
  )
  const candidates = useMemo(() => ({
    activationSources: union(...selectedBehaviors.map((behavior) => behavior.activationSources)),
    channelBindings: union(...selectedBehaviors.map((behavior) => behavior.channelBindings)),
    executionExtensions: union(...selectedBehaviors.map((behavior) => behavior.allowedExecutionExtensions)),
    capabilities: union(
      ...selectedBehaviors.map((behavior) => behavior.requiredRuntimeCapabilities),
      ...selectedExtensions.map((extension) => extension.requiredRuntimeCapabilities),
    ),
    endpointClasses: union(
      ...selectedBehaviors.map((behavior) => behavior.requiredRuntimeEndpointClasses),
      ...selectedExtensions.map((extension) => extension.requiredRuntimeEndpointClasses),
    ),
    migrationIds: union(
      ...selectedBehaviors.map((behavior) => behavior.requiredRuntimeMigrationIds),
      ...selectedExtensions.map((extension) => extension.requiredRuntimeMigrationIds),
    ),
    verificationPackIds: union(
      ...selectedBehaviors.map((behavior) => behavior.baselineVerificationPackIds),
      ...selectedExtensions.map((extension) => extension.verificationPackIds),
    ),
    specialistBundles: Array.from(new Map(
      selectedBehaviors
        .flatMap((behavior) => behavior.requiredSpecialistBundles)
        .map((bundle) => [bundle.bundleId, bundle] as const),
    ).values()).sort((left, right) => left.bundleId.localeCompare(right.bundleId)),
  }), [selectedBehaviors, selectedExtensions])

  const fillRequirements = () => setForm((current) => ({
    ...current,
    supportedActivationSources: candidates.activationSources,
    supportedChannelBindings: candidates.channelBindings,
    capabilities: candidates.capabilities,
    endpointClasses: candidates.endpointClasses,
    migrationIds: candidates.migrationIds,
    verificationPackIds: candidates.verificationPackIds,
  }))

  const createMutation = useMutation({
    mutationFn: () => createDeploymentSourceArtifact({
      serviceName: form.serviceName.trim(),
      artifactType: 'DOCKER_IMAGE',
      imageRepository: form.imageRepository.trim(),
      imageTag: form.imageTag.trim(),
      imageDigest: form.imageDigest.trim(),
      gitCommitSha: form.gitCommitSha.trim() || undefined,
      buildRunId: form.buildRunId.trim() || undefined,
      sbomRef: form.sbomRef.trim() || undefined,
      capabilityManifest: {
        schemaVersion: 'loomai-runtime-capabilities-v1',
        aiFabricVersion: form.aiFabricVersion.trim(),
        supportedBehaviorTypes: form.supportedBehaviorTypes,
        supportedActivationSources: form.supportedActivationSources,
        supportedChannelBindings: form.supportedChannelBindings,
        supportedExecutionExtensions: form.supportedExecutionExtensions,
        capabilities: form.capabilities,
        endpointClasses: form.endpointClasses,
        migrationIds: form.migrationIds,
        verificationPackIds: form.verificationPackIds,
        specialistBundles: candidates.specialistBundles,
      },
    }),
    onSuccess: async () => {
      setForm(INITIAL_FORM)
      await queryClient.invalidateQueries({ queryKey: ['deployment-source-artifacts'] })
    },
  })
  const promoteMutation = useMutation({
    mutationFn: ({ artifactId, channel }: { artifactId: string; channel: string }) => (
      promoteDeploymentSourceArtifact(artifactId, channel)
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['deployment-source-artifacts'] })
    },
  })

  const createDisabled = !form.attested
    || !form.serviceName.trim()
    || !form.imageRepository.trim()
    || !form.imageTag.trim()
    || !/^sha256:[a-f0-9]{64}$/.test(form.imageDigest.trim())
    || form.supportedBehaviorTypes.length === 0
    || createMutation.isPending

  if (!isPlatformAdmin) {
    return <Alert severity="error">Runtime artifact attestation is restricted to Platform administrators.</Alert>
  }

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={1.25} alignItems="center">
        <Inventory2RoundedIcon color="primary" />
        <Box>
          <Typography variant="h4">Runtime Artifacts</Typography>
          <Typography color="text.secondary">
            Register immutable private runtime images and promote only capability manifests verified against the packaged build.
          </Typography>
        </Box>
      </Stack>

      <Alert severity="warning">
        A manifest is an operator attestation, not a feature switch. Select only capabilities, endpoints, and migrations that are present in the exact image digest.
      </Alert>

      <Box component="section">
        <Typography variant="h6" sx={{ mb: 2 }}>Register immutable build</Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={4}>
            <TextField fullWidth label="Service name" value={form.serviceName} onChange={(event) => setForm({ ...form, serviceName: event.target.value })} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth label="Image repository" value={form.imageRepository} onChange={(event) => setForm({ ...form, imageRepository: event.target.value })} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth label="Immutable image tag" value={form.imageTag} onChange={(event) => setForm({ ...form, imageTag: event.target.value })} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth label="Image digest" placeholder="sha256:…" value={form.imageDigest} onChange={(event) => setForm({ ...form, imageDigest: event.target.value })} />
          </Grid>
          <Grid item xs={12} md={3}>
            <TextField fullWidth label="Source commit" value={form.gitCommitSha} onChange={(event) => setForm({ ...form, gitCommitSha: event.target.value })} />
          </Grid>
          <Grid item xs={12} md={3}>
            <TextField fullWidth label="AI Fabric version" value={form.aiFabricVersion} onChange={(event) => setForm({ ...form, aiFabricVersion: event.target.value })} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth label="Build run ID" value={form.buildRunId} onChange={(event) => setForm({ ...form, buildRunId: event.target.value })} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth label="SBOM reference" value={form.sbomRef} onChange={(event) => setForm({ ...form, sbomRef: event.target.value })} />
          </Grid>
        </Grid>

        <Divider sx={{ my: 3 }} />
        <SelectionGroup
          title="Supported deployment behaviors"
          values={(behaviorsQuery.data ?? []).map((behavior) => behavior.code)}
          selected={form.supportedBehaviorTypes}
          onToggle={(value) => setForm((current) => ({
            ...current,
            supportedBehaviorTypes: toggle(current.supportedBehaviorTypes, value),
            attested: false,
          }))}
        />
        <Stack direction="row" spacing={1} sx={{ my: 2 }}>
          <Button variant="outlined" startIcon={<AutoFixHighRoundedIcon />} onClick={fillRequirements} disabled={selectedBehaviors.length === 0}>
            Fill contract requirements
          </Button>
        </Stack>
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <SelectionGroup title="Activation sources" values={candidates.activationSources} selected={form.supportedActivationSources} onToggle={(value) => setForm((current) => ({ ...current, supportedActivationSources: toggle(current.supportedActivationSources, value), attested: false }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <SelectionGroup title="Channel bindings" values={candidates.channelBindings} selected={form.supportedChannelBindings} onToggle={(value) => setForm((current) => ({ ...current, supportedChannelBindings: toggle(current.supportedChannelBindings, value), attested: false }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <SelectionGroup title="Execution extensions" values={candidates.executionExtensions} selected={form.supportedExecutionExtensions} onToggle={(value) => setForm((current) => ({ ...current, supportedExecutionExtensions: toggle(current.supportedExecutionExtensions, value), attested: false }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <SelectionGroup title="Runtime capabilities" values={candidates.capabilities} selected={form.capabilities} onToggle={(value) => setForm((current) => ({ ...current, capabilities: toggle(current.capabilities, value), attested: false }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <SelectionGroup title="Endpoint classes" values={candidates.endpointClasses} selected={form.endpointClasses} onToggle={(value) => setForm((current) => ({ ...current, endpointClasses: toggle(current.endpointClasses, value), attested: false }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <SelectionGroup title="Database migrations" values={candidates.migrationIds} selected={form.migrationIds} onToggle={(value) => setForm((current) => ({ ...current, migrationIds: toggle(current.migrationIds, value), attested: false }))} />
          </Grid>
          <Grid item xs={12}>
            <SelectionGroup title="Verification packs" values={candidates.verificationPackIds} selected={form.verificationPackIds} onToggle={(value) => setForm((current) => ({ ...current, verificationPackIds: toggle(current.verificationPackIds, value), attested: false }))} />
          </Grid>
          <Grid item xs={12}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>Packaged specialist bundles</Typography>
            {candidates.specialistBundles.length === 0 ? (
              <Typography variant="body2" color="text.secondary">None required by the selected behaviors.</Typography>
            ) : (
              <Stack spacing={1}>
                {candidates.specialistBundles.map((bundle) => (
                  <Box key={bundle.bundleId} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 1 }}>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip size="small" label={bundle.bundleId} color="primary" variant="outlined" />
                      <Chip size="small" label={bundle.contentHash} variant="outlined" sx={{ maxWidth: '100%' }} />
                    </Stack>
                    <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                      {bundle.resourceLocations.join(' · ')}
                    </Typography>
                  </Box>
                ))}
              </Stack>
            )}
          </Grid>
        </Grid>
        <FormControlLabel
          sx={{ mt: 2 }}
          control={<Checkbox checked={form.attested} onChange={(event) => setForm({ ...form, attested: event.target.checked })} />}
          label="I verified this manifest against the exact packaged image digest, migrations, endpoints, and release evidence."
        />
        <Stack direction="row" spacing={2} alignItems="center" sx={{ mt: 1 }}>
          <Button variant="contained" disabled={createDisabled} onClick={() => createMutation.mutate()}>
            {createMutation.isPending ? 'Registering…' : 'Register build'}
          </Button>
          {createMutation.isError ? <Alert severity="error">{createMutation.error instanceof Error ? createMutation.error.message : 'Registration failed.'}</Alert> : null}
        </Stack>
      </Box>

      <Divider />

      <Box component="section">
        <Typography variant="h6" sx={{ mb: 2 }}>Registered builds</Typography>
        {artifactsQuery.isError ? (
          <Alert severity="error">{artifactsQuery.error instanceof Error ? artifactsQuery.error.message : 'Artifacts could not be loaded.'}</Alert>
        ) : null}
        <Grid container spacing={2}>
          {(artifactsQuery.data ?? []).map((artifact) => (
            <Grid item xs={12} lg={6} key={artifact.id}>
              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={1.5}>
                    <Stack direction="row" justifyContent="space-between" spacing={2} alignItems="flex-start">
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="subtitle1" fontWeight={700}>{artifact.serviceName}</Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                          {artifact.imageReference}
                        </Typography>
                      </Box>
                      <Chip label={artifact.promotionChannel ?? 'UNPROMOTED'} color={artifact.promotedAt ? 'success' : 'default'} />
                    </Stack>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip size="small" label={artifact.id} variant="outlined" />
                      <Chip size="small" label={`manifest ${shortHash(artifact.capabilityManifestHash)}`} variant="outlined" />
                      {artifact.gitCommitSha ? <Chip size="small" label={`commit ${shortHash(artifact.gitCommitSha)}`} variant="outlined" /> : null}
                    </Stack>
                    {!artifact.promotedAt ? (
                      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                        <TextField
                          select
                          size="small"
                          label="Promotion channel"
                          value={promotionChannels[artifact.id] ?? 'staging'}
                          onChange={(event) => setPromotionChannels((current) => ({ ...current, [artifact.id]: event.target.value }))}
                          sx={{ minWidth: 180 }}
                        >
                          <MenuItem value="staging">staging</MenuItem>
                          <MenuItem value="production">production</MenuItem>
                        </TextField>
                        <Button
                          variant="outlined"
                          startIcon={<PublishRoundedIcon />}
                          disabled={promoteMutation.isPending}
                          onClick={() => promoteMutation.mutate({
                            artifactId: artifact.id,
                            channel: promotionChannels[artifact.id] ?? 'staging',
                          })}
                        >
                          Promote
                        </Button>
                      </Stack>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
        {promoteMutation.isError ? (
          <Alert severity="error" sx={{ mt: 2 }}>
            {promoteMutation.error instanceof Error ? promoteMutation.error.message : 'Promotion failed.'}
          </Alert>
        ) : null}
      </Box>
    </Stack>
  )
}
