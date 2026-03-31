import { zodResolver } from '@hookform/resolvers/zod'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import ArchiveRoundedIcon from '@mui/icons-material/ArchiveRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import DeleteForeverRoundedIcon from '@mui/icons-material/DeleteForeverRounded'
import HistoryRoundedIcon from '@mui/icons-material/HistoryRounded'
import InsightsRoundedIcon from '@mui/icons-material/InsightsRounded'
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded'
import PendingRoundedIcon from '@mui/icons-material/PendingRounded'
import UnarchiveRoundedIcon from '@mui/icons-material/UnarchiveRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Controller, useForm } from 'react-hook-form'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import {
  archiveDeployment,
  bulkDeploymentAction,
  createDeployment,
  deleteDeployment,
  fetchDeploymentCuratedModules,
  fetchDeploymentOverviews,
  fetchDeploymentTemplates,
  fetchPlatformUserPreferences,
  restoreDeployment,
  updatePlatformUserPreferences,
  type BulkDeploymentActionResponse,
  type CreateDeploymentRequest,
  type DeploymentCuratedModuleSummary,
  type DeploymentListViewPreferences,
  type DeploymentOverviewSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

const schema = z.object({
  name: z.string().min(3, 'Name must be at least 3 characters'),
  environment: z.string().min(2, 'Environment is required'),
  templateId: z.string().min(1, 'Choose a deployment template'),
  curatedModuleId: z.string().min(1, 'Choose a curated module'),
})

type FormValues = z.infer<typeof schema>

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function normalizedText(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase()
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
    case 'PRE_APPLY_VERIFYING':
    case 'PROVISIONING':
    case 'VERIFYING':
      return 'info'
    case 'APPLIED_VERIFICATION_FAILED':
      return 'warning'
    case 'PRE_APPLY_BLOCKED':
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
      ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes(release.status)
      || ['QUEUED', 'RUNNING'].includes(release.provisioningStatus)
      || release.verificationStatus === 'RUNNING'
    )
}

function assignmentRoleLabel(role: string): string {
  switch (role) {
    case 'DEPLOYMENT_ADMIN':
      return 'Deployment Admin'
    case 'DEPLOYMENT_EDITOR':
      return 'Deployment Editor'
    case 'DEPLOYMENT_OPERATOR':
      return 'Deployment Operator'
    case 'DEPLOYMENT_VIEWER':
      return 'Deployment Viewer'
    default:
      return 'No assignment'
  }
}

function assignmentRoleColor(
  role: string,
): 'success' | 'warning' | 'error' | 'info' | 'default' | 'secondary' {
  switch (role) {
    case 'DEPLOYMENT_ADMIN':
      return 'secondary'
    case 'DEPLOYMENT_EDITOR':
      return 'success'
    case 'DEPLOYMENT_OPERATOR':
      return 'info'
    case 'DEPLOYMENT_VIEWER':
      return 'default'
    default:
      return 'default'
  }
}

function roleCapabilitySummary(deployment: DeploymentOverviewSummary): string {
  if (deployment.access.canAdmin) {
    return 'Can configure, release, operate, manage access, and run destructive actions.'
  }
  if (deployment.access.canEdit) {
    return 'Can edit drafts, publish versions, and operate the deployment, but cannot manage access or destructive actions.'
  }
  if (deployment.access.canOperate) {
    return 'Can apply published versions, run verification, and use the POC workspace, but cannot edit draft configuration.'
  }
  return 'Read-only access. Review deployment state, diagnostics, and release history without changing it.'
}

function listViewEquals(
  saved: DeploymentListViewPreferences | null | undefined,
  current: DeploymentListViewPreferences,
): boolean {
  if (!saved) {
    return false
  }
  return saved.showArchived === current.showArchived
    && saved.searchTerm === current.searchTerm
    && saved.healthFilter === current.healthFilter
    && saved.roleFilter === current.roleFilter
    && saved.templateFilter === current.templateFilter
}

function primaryActionForDeployment(deployment: DeploymentOverviewSummary): {
  label: string
  description: string
  to: string
} {
  const deploymentId = encodeURIComponent(deployment.id)
  if (isReleaseInProgress(deployment)) {
    return {
      label: 'Track rollout',
      description: 'Follow apply and verification progress while the current release is still running.',
      to: `/diagnostics?deploymentId=${deploymentId}`,
    }
  }
  if (deployment.healthStatus === 'ATTENTION') {
    return {
      label: 'Review diagnostics',
      description: 'This deployment needs attention. Start with verification evidence and latest release details.',
      to: `/diagnostics?deploymentId=${deploymentId}`,
    }
  }
  if (deployment.access.canEdit && (deployment.activeVersion == null || deployment.activeVersion === 'draft' || deployment.status === 'DRAFT')) {
    return {
      label: 'Continue configuration',
      description: 'The deployment is still draft-led. Continue editing configuration before the next publish.',
      to: `/actions?deploymentId=${deploymentId}`,
    }
  }
  if (deployment.access.canOperate && deployment.latestRelease == null) {
    return {
      label: 'Prepare first release',
      description: 'A deployment exists but has not been applied yet. Review versions and apply when ready.',
      to: `/revisions?deploymentId=${deploymentId}`,
    }
  }
  if (deployment.access.canOperate) {
    return {
      label: 'Run POC checks',
      description: 'Use the embedded POC workspace to validate grounded answers, prompts, and data freshness.',
      to: `/poc?deploymentId=${deploymentId}`,
    }
  }
  return {
    label: 'Open workspace',
    description: 'Review deployment configuration, releases, and diagnostics in read-only mode.',
    to: `/overview?deploymentId=${deploymentId}`,
  }
}

export function DeploymentsPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [showArchived, setShowArchived] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [healthFilter, setHealthFilter] = useState('ALL')
  const [roleFilter, setRoleFilter] = useState('ALL')
  const [templateFilter, setTemplateFilter] = useState('ALL')
  const [archiveTarget, setArchiveTarget] = useState<DeploymentOverviewSummary | null>(null)
  const [archiveConfirmationText, setArchiveConfirmationText] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<DeploymentOverviewSummary | null>(null)
  const [deleteConfirmationText, setDeleteConfirmationText] = useState('')
  const [selectedDeploymentIds, setSelectedDeploymentIds] = useState<string[]>([])
  const [bulkTarget, setBulkTarget] = useState<{ action: 'ARCHIVE' | 'RESTORE' | 'DELETE'; deploymentIds: string[] } | null>(null)
  const [bulkConfirmationText, setBulkConfirmationText] = useState('')
  const [bulkNotice, setBulkNotice] = useState<BulkDeploymentActionResponse | null>(null)
  const canManageBulk = auth.session?.enabled ? auth.session.canManageUsers : true
  const listViewInitializedRef = useRef(false)
  const listViewHydrationRef = useRef(false)

  const templatesQuery = useQuery({
    queryKey: ['deployment-templates'],
    queryFn: fetchDeploymentTemplates,
  })
  const curatedModulesQuery = useQuery({
    queryKey: ['deployment-curated-modules'],
    queryFn: fetchDeploymentCuratedModules,
  })
  const overviewsQuery = useQuery({
    queryKey: ['deployment-overviews', showArchived],
    queryFn: () => fetchDeploymentOverviews(showArchived),
  })
  const preferencesQuery = useQuery({
    queryKey: ['platform-preferences'],
    queryFn: fetchPlatformUserPreferences,
  })

  const updatePreferencesMutation = useMutation({
    mutationFn: updatePlatformUserPreferences,
    onSuccess: (data) => {
      queryClient.setQueryData(['platform-preferences'], data)
    },
  })

  useEffect(() => {
    if (listViewInitializedRef.current || !preferencesQuery.isSuccess) {
      return
    }
    const savedListView = preferencesQuery.data?.deploymentListView
    if (savedListView) {
      setShowArchived(savedListView.showArchived)
      setSearchTerm(savedListView.searchTerm ?? '')
      setHealthFilter(savedListView.healthFilter ?? 'ALL')
      setRoleFilter(savedListView.roleFilter ?? 'ALL')
      setTemplateFilter(savedListView.templateFilter ?? 'ALL')
    }
    listViewHydrationRef.current = true
    listViewInitializedRef.current = true
  }, [preferencesQuery.data, preferencesQuery.isSuccess])

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      environment: 'dev',
      templateId: '',
      curatedModuleId: 'default',
    },
  })

  const createMutation = useMutation({
    mutationFn: (payload: CreateDeploymentRequest) => createDeployment(payload),
    onSuccess: async () => {
      form.reset({
        name: '',
        environment: 'dev',
        templateId: '',
        curatedModuleId: 'default',
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

  const restoreMutation = useMutation({
    mutationFn: (deploymentId: string) => restoreDeployment(deploymentId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
      ])
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (deploymentId: string) => deleteDeployment(deploymentId),
    onSuccess: async () => {
      setDeleteTarget(null)
      setDeleteConfirmationText('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases'] }),
      ])
    },
  })

  const bulkMutation = useMutation({
    mutationFn: (payload: { action: string; deploymentIds: string[] }) => bulkDeploymentAction(payload),
    onSuccess: async (response) => {
      setBulkNotice(response)
      setSelectedDeploymentIds([])
      setBulkTarget(null)
      setBulkConfirmationText('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases'] }),
      ])
    },
  })

  const templates = templatesQuery.data ?? []
  const curatedModules = curatedModulesQuery.data ?? []
  const overviews = overviewsQuery.data ?? []
  const selectedTemplateId = form.watch('templateId')
  const selectedCuratedModuleId = form.watch('curatedModuleId')
  const selectedTemplate = useMemo(
    () => templates.find((template) => template.id === selectedTemplateId) ?? null,
    [selectedTemplateId, templates],
  )
  const selectedCuratedModule = useMemo<DeploymentCuratedModuleSummary | null>(
    () => curatedModules.find((module) => module.id === selectedCuratedModuleId) ?? null,
    [curatedModules, selectedCuratedModuleId],
  )
  const listViewPreferences = useMemo<DeploymentListViewPreferences>(() => ({
    showArchived,
    searchTerm,
    healthFilter,
    roleFilter,
    templateFilter,
  }), [healthFilter, roleFilter, searchTerm, showArchived, templateFilter])
  const listViewMatchesSaved = useMemo(
    () => listViewEquals(preferencesQuery.data?.deploymentListView, listViewPreferences),
    [listViewPreferences, preferencesQuery.data?.deploymentListView],
  )
  const activeDeployments = overviews.filter((deployment) => deployment.archivedAt == null)
  const archivedDeployments = overviews.filter((deployment) => deployment.archivedAt != null)
  const templateOptions = useMemo(
    () => Array.from(new Set(overviews.map((deployment) => deployment.templateId))).sort(),
    [overviews],
  )
  const filteredActiveDeployments = useMemo(
    () => activeDeployments.filter((deployment) => {
      const query = normalizedText(searchTerm)
      const matchesSearch = query.length === 0
        || normalizedText(deployment.name).includes(query)
        || normalizedText(deployment.id).includes(query)
        || normalizedText(deployment.environment).includes(query)
      const matchesHealth = healthFilter === 'ALL' || deployment.healthStatus === healthFilter
      const matchesRole = roleFilter === 'ALL' || deployment.access.assignmentRole === roleFilter
      const matchesTemplate = templateFilter === 'ALL' || deployment.templateId === templateFilter
      return matchesSearch && matchesHealth && matchesRole && matchesTemplate
    }),
    [activeDeployments, healthFilter, roleFilter, searchTerm, templateFilter],
  )
  const filteredArchivedDeployments = useMemo(
    () => archivedDeployments.filter((deployment) => {
      const query = normalizedText(searchTerm)
      const matchesSearch = query.length === 0
        || normalizedText(deployment.name).includes(query)
        || normalizedText(deployment.id).includes(query)
        || normalizedText(deployment.environment).includes(query)
      const matchesHealth = healthFilter === 'ALL' || deployment.healthStatus === healthFilter
      const matchesRole = roleFilter === 'ALL' || deployment.access.assignmentRole === roleFilter
      const matchesTemplate = templateFilter === 'ALL' || deployment.templateId === templateFilter
      return matchesSearch && matchesHealth && matchesRole && matchesTemplate
    }),
    [archivedDeployments, healthFilter, roleFilter, searchTerm, templateFilter],
  )
  const selectedDeploymentSet = useMemo(() => new Set(selectedDeploymentIds), [selectedDeploymentIds])

  const metrics = useMemo(() => {
    const active = activeDeployments.length
    const healthy = activeDeployments.filter((deployment) => deployment.healthStatus === 'HEALTHY').length
    const provisioning = activeDeployments.filter(isReleaseInProgress).length
    const attention = activeDeployments.filter((deployment) => deployment.healthStatus === 'ATTENTION').length
    return { active, healthy, provisioning, attention }
  }, [activeDeployments])

  const archiveConfirmationValid = archiveTarget != null
    && archiveConfirmationText.trim() === archiveTarget.name
  const deleteConfirmationValid = deleteTarget != null
    && deleteConfirmationText.trim() === deleteTarget.name
  const bulkConfirmationValid = bulkTarget != null
    && bulkConfirmationText.trim().toUpperCase() === bulkTarget.action

  const selectedActiveDeployments = useMemo(
    () => filteredActiveDeployments.filter((deployment) => selectedDeploymentSet.has(deployment.id)),
    [filteredActiveDeployments, selectedDeploymentSet],
  )
  const selectedArchivedDeployments = useMemo(
    () => filteredArchivedDeployments.filter((deployment) => selectedDeploymentSet.has(deployment.id)),
    [filteredArchivedDeployments, selectedDeploymentSet],
  )
  const visibleDeploymentIds = useMemo(
    () => (showArchived ? [...filteredActiveDeployments, ...filteredArchivedDeployments] : filteredActiveDeployments)
      .map((deployment) => deployment.id),
    [filteredActiveDeployments, filteredArchivedDeployments, showArchived],
  )

  useEffect(() => {
    if (!preferencesQuery.isSuccess || !listViewInitializedRef.current) {
      return
    }
    if (listViewHydrationRef.current) {
      listViewHydrationRef.current = false
      return
    }
    if (listViewMatchesSaved) {
      return
    }
    const handle = window.setTimeout(() => {
      updatePreferencesMutation.mutate({ deploymentListView: listViewPreferences })
    }, 600)
    return () => window.clearTimeout(handle)
  }, [
    listViewMatchesSaved,
    listViewPreferences,
    preferencesQuery.isSuccess,
    updatePreferencesMutation,
  ])

  useEffect(() => {
    const visibleIds = new Set(visibleDeploymentIds)
    setSelectedDeploymentIds((current) => current.filter((deploymentId) => visibleIds.has(deploymentId)))
  }, [visibleDeploymentIds])

  const toggleDeploymentSelection = (deploymentId: string) => {
    setSelectedDeploymentIds((current) => (
      current.includes(deploymentId)
        ? current.filter((id) => id !== deploymentId)
        : [...current, deploymentId]
    ))
  }

  const selectVisibleDeployments = () => {
    setSelectedDeploymentIds(visibleDeploymentIds)
  }

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
                                {template.managedVectorProvisioningDefault ? (
                                  <Typography variant="caption" color="text.secondary">
                                    {template.managedVectorProvisioningSummary}
                                  </Typography>
                                ) : null}
                                <Stack direction="row" spacing={1} flexWrap="wrap">
                                  <Chip size="small" label={template.llmProvider} />
                                  <Chip size="small" label={template.embeddingProvider} variant="outlined" />
                                  <Chip size="small" label={template.vectorStrategy} />
                                  {template.managedVectorProvisioningDefault ? (
                                    <Chip
                                      size="small"
                                      label={template.managedVectorProvisioningMode === 'MANAGED_INDEX'
                                        ? 'Managed index'
                                        : 'Managed collections'}
                                      color="secondary"
                                      variant="outlined"
                                    />
                                  ) : null}
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      )
                    })}
                  </Grid>
                </Stack>

                <Stack spacing={1.25}>
                  <Typography variant="subtitle2">2. Choose curated module</Typography>
                  <Grid container spacing={1.5}>
                    {curatedModules.map((module) => {
                      const selected = selectedCuratedModuleId === module.id
                      return (
                        <Grid item xs={12} md={6} key={module.id}>
                          <Card
                            onClick={() => form.setValue('curatedModuleId', module.id, { shouldValidate: true })}
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
                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                    {module.name}
                                  </Typography>
                                  <Chip size="small" label={`Preset: ${module.promptPresetId}`} variant="outlined" />
                                  {module.runtimeCuratedPack ? (
                                    <Chip size="small" label={`Runtime pack: ${module.runtimeCuratedPack}`} />
                                  ) : null}
                                </Stack>
                                <Typography variant="body2" color="text.secondary">
                                  {module.description}
                                </Typography>
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
                    <Typography variant="subtitle2">3. Name the environment</Typography>
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
                        {selectedTemplate.llmProvider} for LLM, {selectedTemplate.embeddingProvider} for embeddings,
                        and {selectedTemplate.vectorStrategy} for vector storage.
                        {selectedTemplate.managedVectorProvisioningDefault ? (
                          <>
                            {' '}It also enables <strong>{selectedTemplate.managedVectorProvisioningMode === 'MANAGED_INDEX'
                              ? 'platform-managed index provisioning'
                              : 'platform-managed collection provisioning'}</strong> by default.{' '}
                            {selectedTemplate.managedVectorProvisioningSummary}
                          </>
                        ) : null}
                        {selectedCuratedModule ? (
                          <>
                            {' '}The initial prompt bundle will be seeded from <strong>{selectedCuratedModule.name}</strong>.
                          </>
                        ) : null}
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
                      disabled={createMutation.isPending || templatesQuery.isLoading || curatedModulesQuery.isLoading}
                    >
                      {createMutation.isPending ? 'Creating…' : '4. Create deployment'}
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

            {bulkNotice ? (
              <Alert severity={bulkNotice.failedCount > 0 ? 'warning' : 'success'}>
                Bulk {bulkNotice.action.toLowerCase()} completed: {bulkNotice.succeededCount} succeeded, {bulkNotice.failedCount} failed.
                {bulkNotice.failedCount > 0 ? ` Failed deployments: ${bulkNotice.results.filter((item) => item.status === 'FAILED').map((item) => item.deploymentName).join(', ')}.` : ''}
              </Alert>
            ) : null}

            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', bgcolor: 'background.default' }}>
              <CardContent>
                <Stack spacing={2}>
                  <Stack
                    direction={{ xs: 'column', lg: 'row' }}
                    spacing={1.5}
                    justifyContent="space-between"
                    alignItems={{ xs: 'flex-start', lg: 'center' }}
                  >
                    <Box>
                      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                        Filter the deployment grid
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Search by name, id, or environment, then narrow by health, assignment role, or template.
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={`Visible active: ${filteredActiveDeployments.length}`} variant="outlined" />
                      {showArchived ? (
                        <Chip label={`Visible archived: ${filteredArchivedDeployments.length}`} variant="outlined" />
                      ) : null}
                    </Stack>
                  </Stack>

                  <Grid container spacing={1.5}>
                    <Grid item xs={12} md={4}>
                      <TextField
                        fullWidth
                        label="Search deployments"
                        value={searchTerm}
                        onChange={(event) => setSearchTerm(event.target.value)}
                        helperText="Matches deployment name, id, or environment"
                      />
                    </Grid>
                    <Grid item xs={12} md={2.5}>
                      <TextField
                        fullWidth
                        select
                        label="Health"
                        value={healthFilter}
                        onChange={(event) => setHealthFilter(event.target.value)}
                      >
                        <MenuItem value="ALL">All health</MenuItem>
                        <MenuItem value="HEALTHY">Healthy</MenuItem>
                        <MenuItem value="PROVISIONING">Provisioning</MenuItem>
                        <MenuItem value="ATTENTION">Needs attention</MenuItem>
                      </TextField>
                    </Grid>
                    <Grid item xs={12} md={2.5}>
                      <TextField
                        fullWidth
                        select
                        label="Role"
                        value={roleFilter}
                        onChange={(event) => setRoleFilter(event.target.value)}
                      >
                        <MenuItem value="ALL">All roles</MenuItem>
                        <MenuItem value="DEPLOYMENT_ADMIN">Deployment Admin</MenuItem>
                        <MenuItem value="DEPLOYMENT_EDITOR">Deployment Editor</MenuItem>
                        <MenuItem value="DEPLOYMENT_OPERATOR">Deployment Operator</MenuItem>
                        <MenuItem value="DEPLOYMENT_VIEWER">Deployment Viewer</MenuItem>
                      </TextField>
                    </Grid>
                    <Grid item xs={12} md={3}>
                      <TextField
                        fullWidth
                        select
                        label="Template"
                        value={templateFilter}
                        onChange={(event) => setTemplateFilter(event.target.value)}
                      >
                        <MenuItem value="ALL">All templates</MenuItem>
                        {templateOptions.map((templateId) => (
                          <MenuItem key={templateId} value={templateId}>
                            {templateId}
                          </MenuItem>
                        ))}
                      </TextField>
                    </Grid>
                  </Grid>

                  <Stack
                    direction={{ xs: 'column', md: 'row' }}
                    spacing={1}
                    justifyContent="space-between"
                    alignItems={{ xs: 'flex-start', md: 'center' }}
                  >
                    <Typography variant="caption" color="text.secondary">
                      {preferencesQuery.isSuccess
                        ? 'View state is saved automatically for your operator account.'
                        : 'View state sync is unavailable. Filters stay local to this session.'}
                    </Typography>
                    {updatePreferencesMutation.isPending ? (
                      <Chip label="Saving view…" size="small" color="info" />
                    ) : (preferencesQuery.isSuccess && listViewMatchesSaved ? (
                      <Chip label="View synced" size="small" color="success" />
                    ) : null)}
                  </Stack>

                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Button
                      variant="outlined"
                      disabled={searchTerm === '' && healthFilter === 'ALL' && roleFilter === 'ALL' && templateFilter === 'ALL'}
                      onClick={() => {
                        setSearchTerm('')
                        setHealthFilter('ALL')
                        setRoleFilter('ALL')
                        setTemplateFilter('ALL')
                      }}
                    >
                      Clear filters
                    </Button>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>

            {canManageBulk ? (
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', bgcolor: 'background.default' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack
                      direction={{ xs: 'column', lg: 'row' }}
                      spacing={1.5}
                      justifyContent="space-between"
                      alignItems={{ xs: 'flex-start', lg: 'center' }}
                    >
                      <Box>
                        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                          Bulk administration
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Select deployments from the grid, then archive, restore, or permanently delete them with one guarded action.
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={`Selected: ${selectedDeploymentIds.length}`} variant="outlined" />
                        <Chip label={`Active selected: ${selectedActiveDeployments.length}`} variant="outlined" />
                        <Chip label={`Archived selected: ${selectedArchivedDeployments.length}`} variant="outlined" />
                      </Stack>
                    </Stack>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Button
                        variant="outlined"
                        onClick={selectVisibleDeployments}
                        disabled={visibleDeploymentIds.length === 0 || bulkMutation.isPending}
                      >
                        Select visible
                      </Button>
                      <Button
                        variant="outlined"
                        onClick={() => setSelectedDeploymentIds([])}
                        disabled={selectedDeploymentIds.length === 0 || bulkMutation.isPending}
                      >
                        Clear selection
                      </Button>
                      <Button
                        color="warning"
                        variant="outlined"
                        startIcon={<ArchiveRoundedIcon />}
                        disabled={selectedActiveDeployments.length === 0 || bulkMutation.isPending}
                        onClick={() => {
                          setBulkNotice(null)
                          setBulkTarget({ action: 'ARCHIVE', deploymentIds: selectedActiveDeployments.map((deployment) => deployment.id) })
                          setBulkConfirmationText('')
                        }}
                      >
                        Archive selected
                      </Button>
                      <Button
                        variant="outlined"
                        startIcon={<UnarchiveRoundedIcon />}
                        disabled={selectedArchivedDeployments.length === 0 || bulkMutation.isPending}
                        onClick={() => {
                          setBulkNotice(null)
                          setBulkTarget({ action: 'RESTORE', deploymentIds: selectedArchivedDeployments.map((deployment) => deployment.id) })
                          setBulkConfirmationText('')
                        }}
                      >
                        Restore selected
                      </Button>
                      <Button
                        color="error"
                        variant="outlined"
                        startIcon={<DeleteForeverRoundedIcon />}
                        disabled={selectedArchivedDeployments.length === 0 || bulkMutation.isPending}
                        onClick={() => {
                          setBulkNotice(null)
                          setBulkTarget({ action: 'DELETE', deploymentIds: selectedArchivedDeployments.map((deployment) => deployment.id) })
                          setBulkConfirmationText('')
                        }}
                      >
                        Delete selected
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            ) : null}

            {overviewsQuery.isLoading ? (
              <Typography color="text.secondary">Loading deployments…</Typography>
            ) : activeDeployments.length === 0 ? (
              <Alert severity="info">
                No active deployments yet. Create one above to start the draft, publish, and apply lifecycle.
              </Alert>
            ) : filteredActiveDeployments.length === 0 ? (
              <Alert severity="info">
                No active deployments match the current filters. Clear the filters or broaden the search to see more results.
              </Alert>
            ) : (
              <Grid container spacing={2}>
                {filteredActiveDeployments.map((deployment) => {
                  const runtimeSwaggerUrl = swaggerUiUrl(deployment.runtimeBaseUrl)
                  const connectorSwaggerUrl = swaggerUiUrl(deployment.connectorBaseUrl)
                  const primaryAction = primaryActionForDeployment(deployment)

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
                            <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center" useFlexGap>
                              {canManageBulk ? (
                                <Checkbox
                                  checked={selectedDeploymentSet.has(deployment.id)}
                                  onChange={() => toggleDeploymentSelection(deployment.id)}
                                  inputProps={{ 'aria-label': `Select deployment ${deployment.name}` }}
                                />
                              ) : null}
                              <Chip
                                label={assignmentRoleLabel(deployment.access.assignmentRole)}
                                color={assignmentRoleColor(deployment.access.assignmentRole)}
                                variant="outlined"
                              />
                              <Chip label={deployment.healthStatus} color={healthChipColor(deployment.healthStatus)} />
                              <Chip label={deployment.status} variant="outlined" />
                              <Chip
                                label={`Version: ${deployment.activeVersion ?? 'draft'}`}
                                variant="outlined"
                              />
                              {deployment.source.overrideActive ? (
                                <Chip label="Source override" color="warning" variant="outlined" />
                              ) : null}
                            </Stack>
                          </Stack>

                          <Stack direction="row" spacing={1.25} alignItems="center">
                            {renderHealthIcon(deployment.healthStatus)}
                            <Typography variant="body2" color="text.secondary">
                              {deployment.healthSummary}
                            </Typography>
                          </Stack>

                          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', bgcolor: 'background.default' }}>
                            <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                              <Stack spacing={1.25}>
                                <Typography variant="subtitle2">What you can do now</Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {primaryAction.description}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {roleCapabilitySummary(deployment)}
                                </Typography>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Button
                                    variant="contained"
                                    color="secondary"
                                    startIcon={<LaunchRoundedIcon />}
                                    onClick={() => navigate(primaryAction.to)}
                                  >
                                    {primaryAction.label}
                                  </Button>
                                  <Button
                                    variant="outlined"
                                    startIcon={<HistoryRoundedIcon />}
                                    onClick={() => navigate(`/revisions?deploymentId=${deployment.id}`)}
                                  >
                                    Releases
                                  </Button>
                                  <Button
                                    variant="outlined"
                                    startIcon={<InsightsRoundedIcon />}
                                    onClick={() => navigate(`/diagnostics?deploymentId=${deployment.id}`)}
                                  >
                                    Diagnostics
                                  </Button>
                                  {deployment.access.canOperate ? (
                                    <Button
                                      variant="outlined"
                                      onClick={() => navigate(`/poc?deploymentId=${deployment.id}`)}
                                    >
                                      POC
                                    </Button>
                                  ) : null}
                                  {deployment.access.canEdit ? (
                                    <Button
                                      variant="outlined"
                                      onClick={() => navigate(`/prompts?deploymentId=${deployment.id}`)}
                                    >
                                      Prompts
                                    </Button>
                                  ) : null}
                                  {deployment.access.canAdmin ? (
                                    <Button
                                      variant="outlined"
                                      onClick={() => navigate(`/access?deploymentId=${deployment.id}`)}
                                    >
                                      Access
                                    </Button>
                                  ) : null}
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>

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
                              variant="outlined"
                              onClick={() => navigate(`/overview?deploymentId=${deployment.id}`)}
                            >
                              Workspace
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
                              disabled={archiveMutation.isPending || isReleaseInProgress(deployment) || !deployment.access.canAdmin}
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
                {restoreMutation.isError ? (
                  <Alert severity="error">
                    {restoreMutation.error instanceof Error
                      ? restoreMutation.error.message
                      : 'Failed to restore deployment'}
                  </Alert>
                ) : null}
                {filteredArchivedDeployments.length === 0 ? (
                  <Typography color="text.secondary">
                    {archivedDeployments.length === 0
                      ? 'No archived deployments.'
                      : 'No archived deployments match the current filters.'}
                  </Typography>
                ) : (
                  <Grid container spacing={2}>
                    {filteredArchivedDeployments.map((deployment) => (
                      <Grid item xs={12} md={6} key={deployment.id}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                                <Box>
                                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                    {deployment.name}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    {deployment.environment} · Archived {formatTimestamp(deployment.archivedAt)}
                                  </Typography>
                                </Box>
                                <Stack direction="row" spacing={1} alignItems="center">
                                  {canManageBulk ? (
                                    <Checkbox
                                      checked={selectedDeploymentSet.has(deployment.id)}
                                      onChange={() => toggleDeploymentSelection(deployment.id)}
                                      inputProps={{ 'aria-label': `Select archived deployment ${deployment.name}` }}
                                    />
                                  ) : null}
                                  <Chip
                                    label={assignmentRoleLabel(deployment.access.assignmentRole)}
                                    color={assignmentRoleColor(deployment.access.assignmentRole)}
                                    variant="outlined"
                                  />
                                  <Chip label="ARCHIVED" variant="outlined" />
                                </Stack>
                              </Stack>
                              <Typography variant="body2" color="text.secondary">
                                {deployment.healthSummary}
                              </Typography>
                              <Stack direction="row" spacing={1} flexWrap="wrap">
                                <Button
                                  variant="outlined"
                                  startIcon={<UnarchiveRoundedIcon />}
                                  disabled={restoreMutation.isPending || deleteMutation.isPending || !deployment.access.canAdmin}
                                  onClick={() => restoreMutation.mutate(deployment.id)}
                                >
                                  {restoreMutation.isPending ? 'Restoring…' : 'Restore'}
                                </Button>
                                <Button
                                  color="error"
                                  variant="outlined"
                                  startIcon={<DeleteForeverRoundedIcon />}
                                  disabled={deleteMutation.isPending || restoreMutation.isPending || !deployment.access.canAdmin}
                                  onClick={() => {
                                    if (!canManageBulk && deployment.approvalRequiredForDelete) {
                                      navigate(`/approvals?deploymentId=${encodeURIComponent(deployment.id)}&action=DELETE_DEPLOYMENT`)
                                      return
                                    }
                                    setDeleteTarget(deployment)
                                    setDeleteConfirmationText('')
                                  }}
                                >
                                  {!canManageBulk && deployment.approvalRequiredForDelete
                                    ? 'Request delete approval'
                                    : 'Delete permanently'}
                                </Button>
                              </Stack>
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

      <Dialog
        open={deleteTarget != null}
        onClose={() => {
          if (!deleteMutation.isPending) {
            setDeleteTarget(null)
            setDeleteConfirmationText('')
          }
        }}
      >
        <DialogTitle>Delete deployment permanently</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <DialogContentText>
              This permanently removes the deployment record, drafts, versions, releases, and
              verification history from the platform. To continue, type the deployment name exactly.
            </DialogContentText>
            {deleteTarget ? (
              <Alert severity="error">
                You are deleting <strong>{deleteTarget.name}</strong>. This cannot be undone.
              </Alert>
            ) : null}
            <TextField
              autoFocus
              label="Type deployment name"
              value={deleteConfirmationText}
              onChange={(event) => setDeleteConfirmationText(event.target.value)}
              inputProps={{ 'data-testid': 'delete-confirmation-input' }}
            />
            {deleteMutation.isError ? (
              <Alert severity="error">
                {deleteMutation.error instanceof Error
                  ? deleteMutation.error.message
                  : 'Failed to delete deployment'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setDeleteTarget(null)
              setDeleteConfirmationText('')
            }}
            disabled={deleteMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            color="error"
            variant="contained"
            startIcon={<DeleteForeverRoundedIcon />}
            disabled={!deleteConfirmationValid || deleteMutation.isPending || deleteTarget == null}
            onClick={() => {
              if (deleteTarget) {
                deleteMutation.mutate(deleteTarget.id)
              }
            }}
          >
            {deleteMutation.isPending ? 'Deleting…' : 'Confirm delete'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={bulkTarget != null}
        onClose={() => {
          if (!bulkMutation.isPending) {
            setBulkTarget(null)
            setBulkConfirmationText('')
          }
        }}
      >
        <DialogTitle>Confirm bulk {bulkTarget?.action.toLowerCase() ?? 'action'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <DialogContentText>
              This bulk action will run against {bulkTarget?.deploymentIds.length ?? 0} deployments. Type{' '}
              <strong>{bulkTarget?.action ?? 'ACTION'}</strong> to confirm.
            </DialogContentText>
            {bulkTarget ? (
              <Alert severity={bulkTarget.action === 'DELETE' ? 'error' : 'warning'}>
                {bulkTarget.action === 'DELETE'
                  ? 'Permanent delete only succeeds for deployments that are already archived.'
                  : 'Bulk actions return per-deployment success or failure details after execution.'}
              </Alert>
            ) : null}
            <TextField
              autoFocus
              label="Type action name"
              value={bulkConfirmationText}
              onChange={(event) => setBulkConfirmationText(event.target.value)}
            />
            {bulkMutation.isError ? (
              <Alert severity="error">
                {bulkMutation.error instanceof Error
                  ? bulkMutation.error.message
                  : 'Bulk deployment action failed.'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setBulkTarget(null)
              setBulkConfirmationText('')
            }}
            disabled={bulkMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            color={bulkTarget?.action === 'DELETE' ? 'error' : bulkTarget?.action === 'ARCHIVE' ? 'warning' : 'primary'}
            variant="contained"
            disabled={!bulkConfirmationValid || bulkMutation.isPending || bulkTarget == null}
            onClick={() => {
              if (bulkTarget) {
                bulkMutation.mutate(bulkTarget)
              }
            }}
          >
            {bulkMutation.isPending ? 'Running…' : `Confirm ${bulkTarget?.action.toLowerCase() ?? 'action'}`}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
