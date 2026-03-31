import { zodResolver } from '@hookform/resolvers/zod'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import ArchiveRoundedIcon from '@mui/icons-material/ArchiveRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import HistoryRoundedIcon from '@mui/icons-material/HistoryRounded'
import InsightsRoundedIcon from '@mui/icons-material/InsightsRounded'
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded'
import PendingRoundedIcon from '@mui/icons-material/PendingRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Grid,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Controller, useForm } from 'react-hook-form'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import {
  archiveDeployment,
  createDeployment,
  fetchDeploymentOverviews,
  fetchDeploymentTemplates,
  type CreateDeploymentRequest,
  type DeploymentOverviewSummary,
} from '../api/platformApi'

const schema = z.object({
  name: z.string().min(3, 'Name must be at least 3 characters'),
  environment: z.string().min(2, 'Environment is required'),
  templateId: z.string().min(1, 'Choose a deployment template'),
})

type FormValues = z.infer<typeof schema>

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function swaggerUiUrl(baseUrl: string | null | undefined): string | null {
  if (!baseUrl || baseUrl.trim().length === 0) {
    return null
  }
  return `${baseUrl.replace(/\/$/, '')}/swagger-ui/index.html`
}

function healthChipColor(
  status: string,
): 'success' | 'warning' | 'error' | 'info' | 'default' {
  switch (status) {
    case 'HEALTHY':
      return 'success'
    case 'PROVISIONING':
      return 'info'
    case 'ATTENTION':
      return 'warning'
    case 'ARCHIVED':
      return 'default'
    case 'DRAFT':
    case 'READY_TO_APPLY':
      return 'default'
    default:
      return 'default'
  }
}

function releaseChipColor(
  status: string,
): 'success' | 'warning' | 'error' | 'info' | 'default' {
  switch (status) {
    case 'APPLIED_VERIFIED':
      return 'success'
    case 'APPLY_REQUESTED':
    case 'PROVISIONING':
    case 'VERIFYING':
      return 'info'
    case 'APPLIED_VERIFICATION_FAILED':
      return 'warning'
    case 'FAILED':
      return 'error'
    default:
      return 'default'
  }
}

function renderHealthIcon(status: string) {
  switch (status) {
    case 'HEALTHY':
      return <CheckCircleRoundedIcon color="success" />
    case 'PROVISIONING':
      return <PendingRoundedIcon color="info" />
    case 'ATTENTION':
      return <WarningAmberRoundedIcon color="warning" />
    default:
      return <PendingRoundedIcon color="disabled" />
  }
}

function isReleaseInProgress(deployment: DeploymentOverviewSummary): boolean {
  const release = deployment.latestRelease
  return release != null
    && (
      ['APPLY_REQUESTED', 'PROVISIONING', 'VERIFYING'].includes(release.status)
      || ['QUEUED', 'RUNNING'].includes(release.provisioningStatus)
      || release.verificationStatus === 'RUNNING'
    )
}

export function DeploymentsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [showArchived, setShowArchived] = useState(false)
  const [archiveTarget, setArchiveTarget] = useState<DeploymentOverviewSummary | null>(null)
  const [archiveConfirmationText, setArchiveConfirmationText] = useState('')

  const templatesQuery = useQuery({
    queryKey: ['deployment-templates'],
    queryFn: fetchDeploymentTemplates,
  })
  const overviewsQuery = useQuery({
    queryKey: ['deployment-overviews', showArchived],
    queryFn: () => fetchDeploymentOverviews(showArchived),
  })

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      environment: 'dev',
      templateId: '',
    },
  })

  const createMutation = useMutation({
    mutationFn: (payload: CreateDeploymentRequest) => createDeployment(payload),
    onSuccess: async () => {
      form.reset({
        name: '',
        environment: 'dev',
        templateId: '',
      })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
      ])
    },
  })

  const archiveMutation = useMutation({
    mutationFn: (deploymentId: string) => archiveDeployment(deploymentId),
    onSuccess: async () => {
      setArchiveTarget(null)
      setArchiveConfirmationText('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases'] }),
      ])
    },
  })

  const templates = templatesQuery.data ?? []
  const overviews = overviewsQuery.data ?? []
  const selectedTemplateId = form.watch('templateId')
  const selectedTemplate = useMemo(
    () => templates.find((template) => template.id === selectedTemplateId) ?? null,
    [selectedTemplateId, templates],
  )
  const activeDeployments = overviews.filter((deployment) => deployment.archivedAt == null)
  const archivedDeployments = overviews.filter((deployment) => deployment.archivedAt != null)

  const metrics = useMemo(() => {
    const active = activeDeployments.length
    const healthy = activeDeployments.filter((deployment) => deployment.healthStatus === 'HEALTHY').length
    const provisioning = activeDeployments.filter(isReleaseInProgress).length
    const attention = activeDeployments.filter((deployment) => deployment.healthStatus === 'ATTENTION').length
    return { active, healthy, provisioning, attention }
  }, [activeDeployments])

  const archiveConfirmationValid = archiveTarget != null
    && archiveConfirmationText.trim() === archiveTarget.name

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Deployments" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Customer deployment lifecycle
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 960 }}>
          Create a deployment, understand whether it is healthy, then move into revisions or
          diagnostics only when you need deeper control. The default view hides most platform
          internals and focuses on safe customer operations.
        </Typography>
      </Box>

      <Grid container spacing={2.5}>
        <Grid item xs={12} md={3}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline" color="text.secondary">
                  Active deployments
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>
                  {metrics.active}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline" color="text.secondary">
                  Healthy
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>
                  {metrics.healthy}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline" color="text.secondary">
                  Provisioning
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>
                  {metrics.provisioning}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline" color="text.secondary">
                  Needs attention
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>
                  {metrics.attention}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={7}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Box>
                  <Typography variant="h6">Create deployment</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Choose a starting template, give the environment a clear name, and the platform
                    will create the editable draft lifecycle behind it.
                  </Typography>
                </Box>

                <Stack spacing={1.25}>
                  <Typography variant="subtitle2">1. Choose template</Typography>
                  <Grid container spacing={1.5}>
                    {templates.map((template) => {
                      const selected = selectedTemplateId === template.id
                      return (
                        <Grid item xs={12} md={4} key={template.id}>
                          <Card
                            onClick={() => form.setValue('templateId', template.id, { shouldValidate: true })}
                            sx={{
                              cursor: 'pointer',
                              height: '100%',
                              border: '1px solid',
                              borderColor: selected ? 'primary.main' : 'divider',
                              boxShadow: 'none',
                              bgcolor: selected ? 'rgba(75, 156, 211, 0.08)' : 'background.paper',
                            }}
                          >
                            <CardContent>
                              <Stack spacing={1.25}>
                                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                  {template.name}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {template.description}
                                </Typography>
                                <Stack direction="row" spacing={1} flexWrap="wrap">
                                  <Chip size="small" label={template.llmProvider} />
                                  <Chip size="small" label={template.vectorStrategy} />
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      )
                    })}
                  </Grid>
                </Stack>

                <form
                  onSubmit={form.handleSubmit((values) => createMutation.mutate(values))}
                  noValidate
                >
                  <Stack spacing={2}>
                    <Typography variant="subtitle2">2. Name the environment</Typography>
                    <Controller
                      name="name"
                      control={form.control}
                      render={({ field, fieldState }) => (
                        <TextField
                          {...field}
                          label="Deployment name"
                          error={!!fieldState.error}
                          helperText={fieldState.error?.message ?? 'For example: Acme Commerce Dev'}
                        />
                      )}
                    />

                    <Controller
                      name="environment"
                      control={form.control}
                      render={({ field, fieldState }) => (
                        <TextField
                          {...field}
                          label="Environment"
                          error={!!fieldState.error}
                          helperText={fieldState.error?.message ?? 'For example: dev, stage, prod'}
                        />
                      )}
                    />

                    {selectedTemplate ? (
                      <Alert severity="info">
                        This deployment will start with <strong>{selectedTemplate.name}</strong>, using{' '}
                        {selectedTemplate.llmProvider} and {selectedTemplate.vectorStrategy}.
                      </Alert>
                    ) : null}

                    {createMutation.isError ? (
                      <Alert severity="error">
                        {createMutation.error instanceof Error
                          ? createMutation.error.message
                          : 'Failed to create deployment'}
                      </Alert>
                    ) : null}

                    <Button
                      type="submit"
                      variant="contained"
                      startIcon={<AddRoundedIcon />}
                      disabled={createMutation.isPending || templatesQuery.isLoading}
                    >
                      {createMutation.isPending ? 'Creating…' : '3. Create deployment'}
                    </Button>
                  </Stack>
                </form>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={5}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">What happens next</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    New deployments start as editable drafts. Publish and apply remain available in
                    Revisions, while Diagnostics shows verification evidence and deployment health.
                  </Typography>
                </Box>

                <Stack spacing={1.5}>
                  <Alert severity="success" icon={<CheckCircleRoundedIcon fontSize="inherit" />}>
                    <strong>Healthy</strong> means the latest verification passed.
                  </Alert>
                  <Alert severity="info" icon={<PendingRoundedIcon fontSize="inherit" />}>
                    <strong>Provisioning</strong> means apply or verification is still running.
                  </Alert>
                  <Alert severity="warning" icon={<WarningAmberRoundedIcon fontSize="inherit" />}>
                    <strong>Needs attention</strong> means the latest verification failed or needs review.
                  </Alert>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              spacing={2}
              justifyContent="space-between"
              alignItems={{ xs: 'flex-start', md: 'center' }}
            >
              <Box>
                <Typography variant="h6">Deployment overview</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                  This is the customer-safe lifecycle view. Open Revisions or Diagnostics only when
                  you need deeper release control.
                </Typography>
              </Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={showArchived}
                    onChange={(event) => setShowArchived(event.target.checked)}
                  />
                }
                label="Show archived"
              />
            </Stack>

            {overviewsQuery.isLoading ? (
              <Typography color="text.secondary">Loading deployments…</Typography>
            ) : activeDeployments.length === 0 ? (
              <Alert severity="info">
                No active deployments yet. Create one above to start the draft, publish, and apply lifecycle.
              </Alert>
            ) : (
              <Grid container spacing={2}>
                {activeDeployments.map((deployment) => {
                  const runtimeSwaggerUrl = swaggerUiUrl(deployment.runtimeBaseUrl)
                  const connectorSwaggerUrl = swaggerUiUrl(deployment.connectorBaseUrl)

                  return (
                  <Grid item xs={12} xl={6} key={deployment.id}>
                    <Card
                      data-testid={`deployment-card-${deployment.id}`}
                      sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}
                    >
                      <CardContent>
                        <Stack spacing={2.25}>
                          <Stack
                            direction={{ xs: 'column', md: 'row' }}
                            spacing={1.5}
                            justifyContent="space-between"
                            alignItems={{ xs: 'flex-start', md: 'flex-start' }}
                          >
                            <Box>
                              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                                {deployment.name}
                              </Typography>
                              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                {deployment.environment} environment · {deployment.templateId}
                              </Typography>
                            </Box>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Chip label={deployment.healthStatus} color={healthChipColor(deployment.healthStatus)} />
                              <Chip label={deployment.status} variant="outlined" />
                              <Chip
                                label={`Version: ${deployment.activeVersion ?? 'draft'}`}
                                variant="outlined"
                              />
                            </Stack>
                          </Stack>

                          <Stack direction="row" spacing={1.25} alignItems="center">
                            {renderHealthIcon(deployment.healthStatus)}
                            <Typography variant="body2" color="text.secondary">
                              {deployment.healthSummary}
                            </Typography>
                          </Stack>

                          <Grid container spacing={1.5}>
                            <Grid item xs={12} md={6}>
                              <Stack spacing={0.75}>
                                <Typography variant="subtitle2">Latest release</Typography>
                                {deployment.latestRelease ? (
                                  <>
                                    <Chip
                                      label={deployment.latestRelease.status}
                                      color={releaseChipColor(deployment.latestRelease.status)}
                                      sx={{ alignSelf: 'flex-start' }}
                                    />
                                    <Typography variant="body2" color="text.secondary">
                                      {deployment.latestRelease.currentStepDescription ?? 'No progress recorded'}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      Updated {formatTimestamp(deployment.latestRelease.updatedAt)}
                                    </Typography>
                                  </>
                                ) : (
                                  <Typography variant="body2" color="text.secondary">
                                    No apply has been run yet.
                                  </Typography>
                                )}
                              </Stack>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Stack spacing={0.75}>
                                <Typography variant="subtitle2">Verification</Typography>
                                {deployment.latestVerification ? (
                                  <>
                                    <Chip
                                      label={deployment.latestVerification.status}
                                      color={healthChipColor(
                                        deployment.latestVerification.status === 'FAILED'
                                          ? 'ATTENTION'
                                          : deployment.latestVerification.status,
                                      )}
                                      sx={{ alignSelf: 'flex-start' }}
                                    />
                                    <Typography variant="body2" color="text.secondary">
                                      {deployment.latestVerification.summaryMessage}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      {deployment.latestVerification.passedChecks} passed ·{' '}
                                      {deployment.latestVerification.failedChecks} failed ·{' '}
                                      {deployment.latestVerification.warningChecks} warnings
                                    </Typography>
                                  </>
                                ) : (
                                  <Typography variant="body2" color="text.secondary">
                                    No verification run is stored yet.
                                  </Typography>
                                )}
                              </Stack>
                            </Grid>
                          </Grid>

                          <Stack direction="row" spacing={1} flexWrap="wrap">
                            <Button
                              variant="contained"
                              startIcon={<HistoryRoundedIcon />}
                              onClick={() => navigate(`/revisions?deploymentId=${deployment.id}`)}
                            >
                              Manage releases
                            </Button>
                            <Button
                              variant="outlined"
                              startIcon={<InsightsRoundedIcon />}
                              onClick={() => navigate(`/diagnostics?deploymentId=${deployment.id}`)}
                            >
                              View diagnostics
                            </Button>
                            {deployment.runtimeBaseUrl ? (
                              <Button
                                variant="text"
                                startIcon={<LaunchRoundedIcon />}
                                href={deployment.runtimeBaseUrl}
                                target="_blank"
                                rel="noreferrer"
                              >
                                Runtime
                              </Button>
                            ) : null}
                            {runtimeSwaggerUrl ? (
                              <Button
                                variant="text"
                                startIcon={<LaunchRoundedIcon />}
                                href={runtimeSwaggerUrl}
                                target="_blank"
                                rel="noreferrer"
                              >
                                Runtime Swagger
                              </Button>
                            ) : null}
                            {deployment.connectorBaseUrl ? (
                              <Button
                                variant="text"
                                startIcon={<LaunchRoundedIcon />}
                                href={deployment.connectorBaseUrl}
                                target="_blank"
                                rel="noreferrer"
                              >
                                Connector
                              </Button>
                            ) : null}
                            {connectorSwaggerUrl ? (
                              <Button
                                variant="text"
                                startIcon={<LaunchRoundedIcon />}
                                href={connectorSwaggerUrl}
                                target="_blank"
                                rel="noreferrer"
                              >
                                Connector Swagger
                              </Button>
                            ) : null}
                            <Button
                              color="warning"
                              variant="outlined"
                              startIcon={<ArchiveRoundedIcon />}
                              disabled={archiveMutation.isPending || isReleaseInProgress(deployment)}
                              onClick={() => {
                                setArchiveTarget(deployment)
                                setArchiveConfirmationText('')
                              }}
                            >
                              Archive
                            </Button>
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                )})}
              </Grid>
            )}

            {showArchived ? (
              <Stack spacing={1.5}>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                  Archived deployments
                </Typography>
                {archivedDeployments.length === 0 ? (
                  <Typography color="text.secondary">No archived deployments.</Typography>
                ) : (
                  <Grid container spacing={2}>
                    {archivedDeployments.map((deployment) => (
                      <Grid item xs={12} md={6} key={deployment.id}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1}>
                              <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                                <Box>
                                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                    {deployment.name}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    {deployment.environment} · Archived {formatTimestamp(deployment.archivedAt)}
                                  </Typography>
                                </Box>
                                <Chip label="ARCHIVED" variant="outlined" />
                              </Stack>
                              <Typography variant="body2" color="text.secondary">
                                {deployment.healthSummary}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    ))}
                  </Grid>
                )}
              </Stack>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      <Dialog
        open={archiveTarget != null}
        onClose={() => {
          if (!archiveMutation.isPending) {
            setArchiveTarget(null)
            setArchiveConfirmationText('')
          }
        }}
      >
        <DialogTitle>Archive deployment</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <DialogContentText>
              Archiving removes the deployment from active customer workflows, but preserves release
              history and audit evidence. To confirm, type the deployment name exactly.
            </DialogContentText>
            {archiveTarget ? (
              <Alert severity="warning">
                You are archiving <strong>{archiveTarget.name}</strong>.
              </Alert>
            ) : null}
            <TextField
              autoFocus
              label="Type deployment name"
              value={archiveConfirmationText}
              onChange={(event) => setArchiveConfirmationText(event.target.value)}
              inputProps={{ 'data-testid': 'archive-confirmation-input' }}
            />
            {archiveMutation.isError ? (
              <Alert severity="error">
                {archiveMutation.error instanceof Error
                  ? archiveMutation.error.message
                  : 'Failed to archive deployment'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setArchiveTarget(null)
              setArchiveConfirmationText('')
            }}
            disabled={archiveMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            color="warning"
            variant="contained"
            startIcon={<ArchiveRoundedIcon />}
            disabled={!archiveConfirmationValid || archiveMutation.isPending || archiveTarget == null}
            onClick={() => {
              if (archiveTarget) {
                archiveMutation.mutate(archiveTarget.id)
              }
            }}
          >
            {archiveMutation.isPending ? 'Archiving…' : 'Confirm archive'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
