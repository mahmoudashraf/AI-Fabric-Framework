import { zodResolver } from '@hookform/resolvers/zod'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import ArchiveRoundedIcon from '@mui/icons-material/ArchiveRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import DeleteForeverRoundedIcon from '@mui/icons-material/DeleteForeverRounded'
import HistoryRoundedIcon from '@mui/icons-material/HistoryRounded'
import InsightsRoundedIcon from '@mui/icons-material/InsightsRounded'
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded'
import PendingRoundedIcon from '@mui/icons-material/PendingRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import UnarchiveRoundedIcon from '@mui/icons-material/UnarchiveRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import ApartmentRoundedIcon from '@mui/icons-material/ApartmentRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
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
  cleanupDeploymentVerificationRollouts,
  createDeploymentTenantMigration,
  createDeployment,
  deleteDeployment,
  dispatchDeploymentHostedVerification,
  executeRailwayWorkspaceCleanup,
  fetchDeploymentCuratedModules,
  fetchDeploymentOverviews,
  fetchDeploymentTemplates,
  fetchDeploymentVerificationRollouts,
  fetchPlatformCustomers,
  fetchPlatformUserPreferences,
  fetchRailwayWorkspaceCleanup,
  hardResetDeploymentVerificationRollouts,
  recreateDeploymentVerificationRollouts,
  restoreDeployment,
  previewDeploymentTenantMigration,
  rolloutEcommerceDemoDeployment,
  updateDeploymentTenantBinding,
  updatePlatformUserPreferences,
  type BulkDeploymentActionResponse,
  type CreateDeploymentRequest,
  type DeploymentTenantMigrationExecutionSummary,
  type DeploymentTenantMigrationPreviewSummary,
  type DeploymentCuratedModuleSummary,
  type DeploymentHostedVerificationDispatchSummary,
  type DeploymentDeletionOperationSummary,
  type DeploymentListViewPreferences,
  type DeploymentOverviewSummary,
  type PlatformCustomerSummary,
  type DeploymentVerificationRolloutSummary,
  type RailwayWorkspaceCleanupExecutionSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

const schema = z.object({
  name: z.string().min(3, 'Name must be at least 3 characters'),
  environment: z.string().min(2, 'Environment is required'),
  templateId: z.string().min(1, 'Choose a starting stack preset'),
  curatedModuleId: z.string().min(1, 'Choose a curated module'),
  vectorProvisioningMode: z.string().min(1, 'Choose how vector storage should be managed'),
  customerId: z.string().optional(),
  tenantId: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

type VectorProvisioningOption = {
  value: string
  label: string
  description: string
}

type TemplateSelectionSummary = {
  id: string
  name: string
  description: string
  llmProvider: string
  embeddingProvider: string
  vectorStrategy: string
  managedVectorProvisioningDefault: boolean
  managedVectorProvisioningMode: string
  managedVectorProvisioningSummary: string
}

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function normalizedText(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase()
}

function vectorProvisioningLabel(value: string): string {
  switch (value) {
    case 'LOCAL_MANAGED':
      return 'Local runtime-managed'
    case 'EXTERNAL_EXISTING':
      return 'Bring your own'
    case 'PLATFORM_MANAGED':
      return 'Platform-managed'
    default:
      return value
  }
}

function managedVectorDefaultLabel(value: string): string {
  switch (value) {
    case 'MANAGED_SERVERLESS_INDEX':
      return 'Managed serverless index'
    case 'MANAGED_CLOUD_CLUSTER':
      return 'Managed cloud cluster'
    case 'MANAGED_COLLECTIONS':
      return 'Managed collections'
    default:
      return 'Managed vector'
  }
}

function bindingChangeColor(value: string): 'default' | 'info' | 'warning' | 'success' {
  switch (normalizedText(value)) {
    case 'editable':
      return 'info'
    case 'migration_required':
      return 'warning'
    default:
      return 'default'
  }
}

function isCustomStarterPreset(templateId: string): boolean {
  return templateId === 'custom-start-from-scratch'
}

function isVerifiedOpenAiStack(template: TemplateSelectionSummary): boolean {
  return template.llmProvider === 'openai'
    && template.embeddingProvider === 'openai'
    && ['lucene', 'memory', 'qdrant', 'pinecone', 'weaviate', 'milvus'].includes(template.vectorStrategy)
}

function vectorProvisioningOptionsForTemplate(template: { vectorStrategy: string; managedVectorProvisioningDefault: boolean } | null): VectorProvisioningOption[] {
  if (!template) {
    return []
  }
  switch (template.vectorStrategy) {
    case 'lucene':
    case 'memory':
      return [{
        value: 'LOCAL_MANAGED',
        label: 'Local runtime-managed',
        description: 'Use the runtime-local vector backend for low-friction dev, demo, and validation environments.',
      }]
    case 'pinecone':
      return [
        {
          value: 'PLATFORM_MANAGED',
          label: 'Platform-managed',
          description: 'The platform creates or reconciles the Pinecone serverless index and binds its resolved host back into the deployment automatically.',
        },
        {
          value: 'EXTERNAL_EXISTING',
          label: 'Bring your own',
          description: 'Use an existing Pinecone target and keep endpoint ownership outside the platform.',
        },
      ]
    case 'qdrant':
      return [
        {
          value: 'PLATFORM_MANAGED',
          label: 'Platform-managed',
          description: 'The platform creates or reuses a Qdrant Cloud cluster, issues a deployment-scoped database key, and reconciles collections automatically.',
        },
        {
          value: 'EXTERNAL_EXISTING',
          label: 'Bring your own',
          description: 'Use an existing Qdrant endpoint and keep provider-side ownership outside the platform.',
        },
      ]
    case 'weaviate':
      return [{
        value: 'EXTERNAL_EXISTING',
        label: 'Bring your own',
        description: 'Use an existing Weaviate Cloud or other operator-managed Weaviate endpoint and keep provider-side ownership outside the platform.',
      }]
    case 'milvus':
      return [
        {
          value: 'PLATFORM_MANAGED',
          label: 'Platform-managed',
          description: 'The platform creates or reuses a Zilliz Cloud cluster, binds deployment-scoped Milvus runtime credentials, and keeps the cluster attached to the deployment lifecycle.',
        },
        {
          value: 'EXTERNAL_EXISTING',
          label: 'Bring your own',
          description: 'Use an existing Milvus or Zilliz endpoint and keep provider-side ownership outside the platform.',
        },
      ]
    default:
      return []
  }
}

function defaultVectorProvisioningModeForTemplate(template: { vectorStrategy: string; managedVectorProvisioningDefault: boolean } | null): string {
  const options = vectorProvisioningOptionsForTemplate(template)
  if (options.length === 0) {
    return ''
  }
  if (template?.managedVectorProvisioningDefault && options.some((option) => option.value === 'PLATFORM_MANAGED')) {
    return 'PLATFORM_MANAGED'
  }
  return options[0].value
}

function vectorVendorCapabilityMessage(template: { id?: string; vectorStrategy: string } | null): { severity: 'info' | 'warning' | 'success'; message: string } | null {
  if (!template) {
    return null
  }
  if (template.id === 'custom-start-from-scratch') {
    return {
      severity: 'info',
      message: 'This neutral starter preset seeds a safe runtime-local baseline. Change LLM, embeddings, vector backend, and security after create in the deployment workspaces.',
    }
  }
  switch (template.vectorStrategy) {
    case 'pinecone':
      return {
        severity: 'success',
        message: 'Pinecone is the current formal platform-managed vector vendor. The platform can provision and bind Pinecone serverless indexes automatically.',
      }
    case 'qdrant':
      return {
        severity: 'success',
        message: 'Qdrant now supports both bring-your-own and platform-managed provisioning. The platform can create or reuse a Qdrant Cloud cluster and issue a deployment-scoped database key automatically.',
      }
    case 'weaviate':
      return {
        severity: 'info',
        message: 'Weaviate is deployment-verified as a bring-your-own managed-service target. Use an existing Weaviate Cloud endpoint and let the platform bind it into the runtime.',
      }
    case 'milvus':
      return {
        severity: 'success',
        message: 'Milvus is deployment-verified through platform-managed Zilliz Cloud provisioning. The platform can create or reuse the managed cluster and bind deployment-scoped runtime credentials automatically.',
      }
    default:
      return {
        severity: 'info',
        message: 'This vector backend is runtime-local, so the platform does not provision an external vector service.',
      }
  }
}

function swaggerUiUrl(baseUrl: string | null | undefined): string | null {
  if (!baseUrl || baseUrl.trim().length === 0) {
    return null
  }
  return `${baseUrl.replace(/\/$/, '')}/swagger-ui/index.html`
}

function joinUrl(baseUrl: string | null | undefined, path: string): string | null {
  if (!baseUrl || baseUrl.trim().length === 0) {
    return null
  }
  return `${baseUrl.replace(/\/$/, '')}${path.startsWith('/') ? path : `/${path}`}`
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

function deletionChipColor(
  status: string | null | undefined,
): 'default' | 'info' | 'warning' | 'error' {
  switch (status) {
    case 'QUEUED':
      return 'info'
    case 'RUNNING':
      return 'warning'
    case 'FAILED':
      return 'error'
    default:
      return 'default'
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
  const [deleteHardDelete, setDeleteHardDelete] = useState(false)
  const [deleteHardDeleteReason, setDeleteHardDeleteReason] = useState('')
  const [selectedDeploymentIds, setSelectedDeploymentIds] = useState<string[]>([])
  const [bulkTarget, setBulkTarget] = useState<{ action: 'ARCHIVE' | 'RESTORE' | 'DELETE'; deploymentIds: string[] } | null>(null)
  const [bulkConfirmationText, setBulkConfirmationText] = useState('')
  const [bulkNotice, setBulkNotice] = useState<BulkDeploymentActionResponse | null>(null)
  const [selectedOrphanProjectIds, setSelectedOrphanProjectIds] = useState<string[]>([])
  const [selectedOrphanServiceIds, setSelectedOrphanServiceIds] = useState<string[]>([])
  const [orphanCleanupDialogOpen, setOrphanCleanupDialogOpen] = useState(false)
  const [orphanCleanupConfirmationText, setOrphanCleanupConfirmationText] = useState('')
  const [orphanCleanupReason, setOrphanCleanupReason] = useState('')
  const [orphanCleanupNotice, setOrphanCleanupNotice] = useState<RailwayWorkspaceCleanupExecutionSummary | null>(null)
  const [verificationRolloutWriteMode, setVerificationRolloutWriteMode] = useState(false)
  const [verificationRolloutNotice, setVerificationRolloutNotice] = useState<DeploymentHostedVerificationDispatchSummary | null>(null)
  const [selectedVerificationRolloutKeys, setSelectedVerificationRolloutKeys] = useState<string[]>([])
  const [rolloutCleanupDialogOpen, setRolloutCleanupDialogOpen] = useState(false)
  const [rolloutCleanupConfirmationText, setRolloutCleanupConfirmationText] = useState('')
  const [rolloutHardResetDialogOpen, setRolloutHardResetDialogOpen] = useState(false)
  const [rolloutHardResetConfirmationText, setRolloutHardResetConfirmationText] = useState('')
  const [rolloutActionNotice, setRolloutActionNotice] = useState<string | null>(null)
  const [ecommerceRolloutNotice, setEcommerceRolloutNotice] = useState<DeploymentOverviewSummary | null>(null)
  const [deleteNotice, setDeleteNotice] = useState<DeploymentDeletionOperationSummary | null>(null)
  const [bindingTarget, setBindingTarget] = useState<DeploymentOverviewSummary | null>(null)
  const [bindingCustomerId, setBindingCustomerId] = useState('')
  const [bindingTenantId, setBindingTenantId] = useState('')
  const [bindingMigrationName, setBindingMigrationName] = useState('')
  const [bindingMigrationEnvironment, setBindingMigrationEnvironment] = useState('')
  const [bindingMigrationReason, setBindingMigrationReason] = useState('')
  const [bindingMigrationNotice, setBindingMigrationNotice] = useState<DeploymentTenantMigrationExecutionSummary | null>(null)
  const canManageBulk = auth.session?.enabled ? auth.session.canManageUsers : true
  const canManageVerificationRollouts = auth.session?.enabled ? auth.session.canManageUsers : true
  const canManageCustomers = auth.session?.enabled ? auth.session.canManageCustomers : true
  const customerScopeLocked = auth.session?.enabled
    ? !auth.session.canManageUsers && Boolean(auth.session.customerId)
    : false
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
  const customersQuery = useQuery({
    queryKey: ['platform-customers'],
    queryFn: fetchPlatformCustomers,
    enabled: canManageCustomers,
  })
  const railwayWorkspaceCleanupQuery = useQuery({
    queryKey: ['railway-workspace-cleanup'],
    queryFn: fetchRailwayWorkspaceCleanup,
    enabled: canManageBulk,
  })
  const verificationRolloutsQuery = useQuery({
    queryKey: ['deployment-verification-rollouts'],
    queryFn: fetchDeploymentVerificationRollouts,
    enabled: canManageVerificationRollouts,
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
      vectorProvisioningMode: '',
      customerId: '',
      tenantId: '',
    },
  })

  useEffect(() => {
    if (!customerScopeLocked || !auth.session?.customerId) {
      return
    }
    if ((form.getValues('customerId') ?? '') === auth.session.customerId) {
      return
    }
    form.setValue('customerId', auth.session.customerId, { shouldValidate: false })
    form.setValue('tenantId', '', { shouldValidate: false })
  }, [auth.session?.customerId, customerScopeLocked, form])

  const createMutation = useMutation({
    mutationFn: (payload: CreateDeploymentRequest) => createDeployment(payload),
    onSuccess: async () => {
      form.reset({
        name: '',
        environment: 'dev',
        templateId: '',
        curatedModuleId: 'default',
        vectorProvisioningMode: '',
        customerId: '',
        tenantId: '',
      })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['platform-customers'] }),
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
    mutationFn: (payload: { deploymentId: string; hardDelete: boolean; reason?: string }) => deleteDeployment(
      payload.deploymentId,
      payload.hardDelete ? { hardDelete: true, reason: payload.reason } : undefined,
    ),
    onSuccess: async (response) => {
      setDeleteTarget(null)
      setDeleteConfirmationText('')
      setDeleteHardDelete(false)
      setDeleteHardDeleteReason('')
      setDeleteNotice(response)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-deletion-notifications'] }),
      ])
    },
  })

  const orphanCleanupMutation = useMutation({
    mutationFn: (payload: { reason: string; projectIds: string[]; serviceIds: string[] }) => executeRailwayWorkspaceCleanup({
      confirm: true,
      reason: payload.reason,
      projectIds: payload.projectIds,
      serviceIds: payload.serviceIds,
    }),
    onSuccess: async (response) => {
      setOrphanCleanupNotice(response)
      setSelectedOrphanProjectIds([])
      setSelectedOrphanServiceIds([])
      setOrphanCleanupDialogOpen(false)
      setOrphanCleanupConfirmationText('')
      setOrphanCleanupReason('')
      await queryClient.invalidateQueries({ queryKey: ['railway-workspace-cleanup'] })
    },
  })

  const recreateVerificationRolloutsMutation = useMutation({
    mutationFn: (rolloutKeys: string[]) => recreateDeploymentVerificationRollouts(rolloutKeys),
    onSuccess: async (response) => {
      setVerificationRolloutNotice(null)
      setRolloutActionNotice(response.summaryMessage)
      queryClient.setQueryData<DeploymentVerificationRolloutSummary>(['deployment-verification-rollouts'], response)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-deletion-notifications'] }),
      ])
    },
  })

  const cleanupVerificationRolloutsMutation = useMutation({
    mutationFn: (rolloutKeys: string[]) => cleanupDeploymentVerificationRollouts(rolloutKeys),
    onSuccess: async (response) => {
      setVerificationRolloutNotice(null)
      setRolloutCleanupDialogOpen(false)
      setRolloutCleanupConfirmationText('')
      setRolloutActionNotice(response.summaryMessage)
      queryClient.setQueryData<DeploymentVerificationRolloutSummary>(['deployment-verification-rollouts'], response)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases'] }),
      ])
    },
  })

  const hardResetVerificationRolloutsMutation = useMutation({
    mutationFn: (rolloutKeys: string[]) => hardResetDeploymentVerificationRollouts(rolloutKeys),
    onSuccess: async (response) => {
      setVerificationRolloutNotice(null)
      setRolloutHardResetDialogOpen(false)
      setRolloutHardResetConfirmationText('')
      setRolloutActionNotice(response.summaryMessage)
      queryClient.setQueryData<DeploymentVerificationRolloutSummary>(['deployment-verification-rollouts'], response)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-deletion-notifications'] }),
      ])
    },
  })

  const ecommerceDemoRolloutMutation = useMutation({
    mutationFn: rolloutEcommerceDemoDeployment,
    onSuccess: async (response) => {
      setEcommerceRolloutNotice(response)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases'] }),
      ])
    },
  })

  const rolloutVerificationMutation = useMutation({
    mutationFn: (payload: { deploymentId: string; profile: string; verifyWrite: boolean }) =>
      dispatchDeploymentHostedVerification(payload.deploymentId, {
        profile: payload.profile,
        verifyWrite: payload.verifyWrite,
      }),
    onSuccess: async (response) => {
      setVerificationRolloutNotice(response)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-verification-rollouts'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-hosted-verification-runs', response.deploymentId] }),
      ])
    },
  })

  const updateBindingMutation = useMutation({
    mutationFn: (payload: { deploymentId: string; customerId?: string; tenantId?: string }) =>
      updateDeploymentTenantBinding(payload.deploymentId, {
        customerId: payload.customerId,
        tenantId: payload.tenantId,
      }),
    onSuccess: async () => {
      setBindingTarget(null)
      setBindingCustomerId('')
      setBindingTenantId('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
        queryClient.invalidateQueries({ queryKey: ['platform-customers'] }),
      ])
    },
  })

  const createBindingMigrationMutation = useMutation({
    mutationFn: (payload: {
      deploymentId: string
      customerId?: string
      tenantId?: string
      proposedDeploymentName?: string
      proposedEnvironmentName?: string
      reason: string
    }) =>
      createDeploymentTenantMigration(payload.deploymentId, {
        customerId: payload.customerId,
        tenantId: payload.tenantId,
        proposedDeploymentName: payload.proposedDeploymentName,
        proposedEnvironmentName: payload.proposedEnvironmentName,
        reason: payload.reason,
      }),
    onSuccess: async (response) => {
      setBindingMigrationNotice(response)
      setBindingTarget(null)
      setBindingCustomerId('')
      setBindingTenantId('')
      setBindingMigrationName('')
      setBindingMigrationEnvironment('')
      setBindingMigrationReason('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
        queryClient.invalidateQueries({ queryKey: ['platform-customers'] }),
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
  const customers = customersQuery.data ?? []
  const curatedModules = curatedModulesQuery.data ?? []
  const overviews = overviewsQuery.data ?? []
  const verifiedOpenAiTemplates = useMemo(
    () => templates.filter((template) => isVerifiedOpenAiStack(template)),
    [templates],
  )
  const otherTemplates = useMemo(
    () => templates.filter((template) => !isVerifiedOpenAiStack(template)),
    [templates],
  )
  const templateMetadataById = useMemo(
    () => new Map(templates.map((template) => [template.id, template])),
    [templates],
  )
  const selectedTemplateId = form.watch('templateId')
  const selectedCuratedModuleId = form.watch('curatedModuleId')
  const selectedVectorProvisioningMode = form.watch('vectorProvisioningMode')
  const selectedCustomerId = form.watch('customerId') ?? ''
  const selectedTenantId = form.watch('tenantId') ?? ''
  const selectedTemplate = useMemo(
    () => templates.find((template) => template.id === selectedTemplateId) ?? null,
    [selectedTemplateId, templates],
  )
  const selectedCuratedModule = useMemo<DeploymentCuratedModuleSummary | null>(
    () => curatedModules.find((module) => module.id === selectedCuratedModuleId) ?? null,
    [curatedModules, selectedCuratedModuleId],
  )
  const vectorProvisioningOptions = useMemo(
    () => vectorProvisioningOptionsForTemplate(selectedTemplate),
    [selectedTemplate],
  )
  const selectedVectorProvisioningOption = useMemo(
    () => vectorProvisioningOptions.find((option) => option.value === selectedVectorProvisioningMode) ?? null,
    [selectedVectorProvisioningMode, vectorProvisioningOptions],
  )
  const selectedCustomer = useMemo<PlatformCustomerSummary | null>(
    () => customers.find((customer) => customer.id === selectedCustomerId) ?? null,
    [customers, selectedCustomerId],
  )
  const availableTenantsForSelectedCustomer = selectedCustomer?.tenants ?? []
  const selectedBindingCustomer = useMemo<PlatformCustomerSummary | null>(
    () => customers.find((customer) => customer.id === bindingCustomerId) ?? null,
    [bindingCustomerId, customers],
  )
  const availableBindingTenants = selectedBindingCustomer?.tenants ?? []
  const bindingRequiresMigration = bindingTarget?.binding?.mutable === false
  const bindingMigrationPreviewQuery = useQuery({
    queryKey: [
      'deployment-tenant-migration-preview',
      bindingTarget?.id ?? '',
      bindingCustomerId,
      bindingTenantId,
      bindingMigrationName,
      bindingMigrationEnvironment,
    ],
    queryFn: () =>
      previewDeploymentTenantMigration(bindingTarget!.id, {
        customerId: bindingCustomerId || undefined,
        tenantId: bindingTenantId || undefined,
        proposedDeploymentName: bindingMigrationName.trim() || undefined,
        proposedEnvironmentName: bindingMigrationEnvironment.trim() || undefined,
      }),
    enabled: canManageCustomers
      && bindingTarget != null
      && bindingRequiresMigration
      && bindingCustomerId.trim().length > 0
      && !createBindingMigrationMutation.isPending,
  })
  const vectorCapability = useMemo(
    () => vectorVendorCapabilityMessage(selectedTemplate),
    [selectedTemplate],
  )
  useEffect(() => {
    if (!selectedTemplate) {
      return
    }
    const currentValue = form.getValues('vectorProvisioningMode')
    const currentIsSupported = vectorProvisioningOptions.some((option) => option.value === currentValue)
    if (!currentIsSupported) {
      form.setValue('vectorProvisioningMode', defaultVectorProvisioningModeForTemplate(selectedTemplate), {
        shouldValidate: true,
      })
    }
  }, [form, selectedTemplate, vectorProvisioningOptions])
  useEffect(() => {
    if (!canManageCustomers) {
      return
    }
    if (!selectedTenantId) {
      return
    }
    const stillValid = availableTenantsForSelectedCustomer.some((tenant) => tenant.id === selectedTenantId)
    if (!stillValid) {
      form.setValue('tenantId', '', { shouldValidate: false })
    }
  }, [availableTenantsForSelectedCustomer, canManageCustomers, form, selectedTenantId])
  useEffect(() => {
    if (!bindingTarget) {
      return
    }
    setBindingCustomerId(bindingTarget.binding?.customerId ?? '')
    setBindingTenantId(bindingTarget.binding?.tenantId ?? '')
    setBindingMigrationName(`${bindingTarget.name} - Tenant Migration`)
    setBindingMigrationEnvironment(bindingTarget.environment)
    setBindingMigrationReason('')
    setBindingMigrationNotice(null)
  }, [bindingTarget])
  useEffect(() => {
    if (!bindingTenantId) {
      return
    }
    const stillValid = availableBindingTenants.some((tenant) => tenant.id === bindingTenantId)
    if (!stillValid) {
      setBindingTenantId('')
    }
  }, [availableBindingTenants, bindingTenantId])
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
  const activeDeployments = overviews.filter(
    (deployment) => deployment.archivedAt == null || deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING',
  )
  const archivedDeployments = overviews.filter(
    (deployment) => deployment.archivedAt != null && deployment.deletion?.status !== 'QUEUED' && deployment.deletion?.status !== 'RUNNING',
  )
  const verificationRolloutSummary = verificationRolloutsQuery.data ?? null
  const selectedVerificationRolloutItems = useMemo(
    () => verificationRolloutSummary?.items.filter((item) => selectedVerificationRolloutKeys.includes(item.key)) ?? [],
    [selectedVerificationRolloutKeys, verificationRolloutSummary],
  )
  const selectedVerificationRolloutSet = useMemo(
    () => new Set(selectedVerificationRolloutKeys),
    [selectedVerificationRolloutKeys],
  )
  const templateOptions = useMemo(
    () => Array.from(new Set(overviews.map((deployment) => deployment.templateId)))
      .map((templateId) => ({
        id: templateId,
        label: templateMetadataById.get(templateId)?.name ?? templateId,
      }))
      .sort((left, right) => left.label.localeCompare(right.label)),
    [overviews, templateMetadataById],
  )

  useEffect(() => {
    if (!verificationRolloutSummary) {
      return
    }
    const availableKeys = verificationRolloutSummary.items.map((item) => item.key)
    setSelectedVerificationRolloutKeys((current) => {
      const filtered = current.filter((key) => availableKeys.includes(key))
      return filtered.length > 0 ? filtered : availableKeys
    })
  }, [verificationRolloutSummary])
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
    && (!deleteHardDelete || deleteHardDeleteReason.trim().length >= 8)
  const bulkConfirmationValid = bulkTarget != null
    && bulkConfirmationText.trim().toUpperCase() === bulkTarget.action
  const orphanCleanupConfirmationValid = orphanCleanupConfirmationText.trim().toUpperCase() === 'DELETE ORPHANS'
    && orphanCleanupReason.trim().length >= 8
  const rolloutCleanupConfirmationValid = rolloutCleanupConfirmationText.trim().toUpperCase() === 'CLEANUP ROLLOUTS'
    && selectedVerificationRolloutKeys.length > 0
  const rolloutHardResetConfirmationValid = rolloutHardResetConfirmationText.trim().toUpperCase() === 'RESET ROLLOUTS'
    && selectedVerificationRolloutKeys.length > 0

  const orphanProjects = railwayWorkspaceCleanupQuery.data?.projects ?? []
  const availableOrphanProjectIds = useMemo(
    () => orphanProjects.filter((project) => project.deletable).map((project) => project.projectId),
    [orphanProjects],
  )
  const availableOrphanServiceIds = useMemo(
    () => orphanProjects.flatMap((project) => project.orphanServices.filter((service) => service.deletable).map((service) => service.serviceId)),
    [orphanProjects],
  )
  const selectedOrphanProjectSet = useMemo(() => new Set(selectedOrphanProjectIds), [selectedOrphanProjectIds])
  const selectedOrphanServiceSet = useMemo(() => new Set(selectedOrphanServiceIds), [selectedOrphanServiceIds])
  const selectedOrphanCount = selectedOrphanProjectIds.length + selectedOrphanServiceIds.length

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

  useEffect(() => {
    const projectIds = new Set(availableOrphanProjectIds)
    const serviceIds = new Set(availableOrphanServiceIds)
    setSelectedOrphanProjectIds((current) => current.filter((projectId) => projectIds.has(projectId)))
    setSelectedOrphanServiceIds((current) => current.filter((serviceId) => serviceIds.has(serviceId)))
  }, [availableOrphanProjectIds, availableOrphanServiceIds])

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

  const toggleOrphanProjectSelection = (projectId: string) => {
    setSelectedOrphanProjectIds((current) => (
      current.includes(projectId)
        ? current.filter((id) => id !== projectId)
        : [...current, projectId]
    ))
    setSelectedOrphanServiceIds((current) => current.filter((serviceId) => {
      const project = orphanProjects.find((item) => item.projectId === projectId)
      if (!project) {
        return true
      }
      return !project.orphanServices.some((service) => service.serviceId === serviceId)
    }))
  }

  const toggleOrphanServiceSelection = (serviceId: string) => {
    setSelectedOrphanServiceIds((current) => (
      current.includes(serviceId)
        ? current.filter((id) => id !== serviceId)
        : [...current, serviceId]
    ))
  }

  const toggleVerificationRolloutSelection = (rolloutKey: string) => {
    setSelectedVerificationRolloutKeys((current) => (
      current.includes(rolloutKey)
        ? current.filter((key) => key !== rolloutKey)
        : [...current, rolloutKey]
    ))
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

      {bindingMigrationNotice ? (
        <Alert severity="success">
          {bindingMigrationNotice.message} New deployment: <strong>{bindingMigrationNotice.deploymentName}</strong> (
          {bindingMigrationNotice.deploymentId}) bound to <strong>{bindingMigrationNotice.customerName}</strong> /{' '}
          <strong>{bindingMigrationNotice.tenantName}</strong>.
        </Alert>
      ) : null}

      {canManageVerificationRollouts ? (
        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Box>
                <Typography variant="h6">Canonical verification rollouts</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 980 }}>
                  Use this admin-only surface to explicitly create, apply, verify, and clean up the platform-owned
                  canonical verification deployments. You can target only the presets you want instead of recreating
                  the whole matrix every time.
                </Typography>
              </Box>

              <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} alignItems={{ lg: 'center' }}>
                <Button
                  variant="contained"
                  startIcon={<RefreshRoundedIcon />}
                  disabled={
                    recreateVerificationRolloutsMutation.isPending
                    || cleanupVerificationRolloutsMutation.isPending
                    || hardResetVerificationRolloutsMutation.isPending
                    || selectedVerificationRolloutKeys.length === 0
                  }
                  onClick={() => recreateVerificationRolloutsMutation.mutate(selectedVerificationRolloutKeys)}
                >
                  {recreateVerificationRolloutsMutation.isPending ? 'Applying…' : 'Create and apply selected rollouts'}
                </Button>
                <Button
                  variant="contained"
                  color="error"
                  startIcon={<DeleteForeverRoundedIcon />}
                  disabled={
                    hardResetVerificationRolloutsMutation.isPending
                    || cleanupVerificationRolloutsMutation.isPending
                    || recreateVerificationRolloutsMutation.isPending
                    || selectedVerificationRolloutKeys.length === 0
                  }
                  onClick={() => setRolloutHardResetDialogOpen(true)}
                >
                  {hardResetVerificationRolloutsMutation.isPending ? 'Cleaning…' : 'Force hard cleanup selected rollouts'}
                </Button>
                <Button
                  variant="outlined"
                  color="error"
                  startIcon={<DeleteForeverRoundedIcon />}
                  disabled={
                    cleanupVerificationRolloutsMutation.isPending
                    || recreateVerificationRolloutsMutation.isPending
                    || hardResetVerificationRolloutsMutation.isPending
                    || selectedVerificationRolloutKeys.length === 0
                  }
                  onClick={() => setRolloutCleanupDialogOpen(true)}
                >
                  {cleanupVerificationRolloutsMutation.isPending ? 'Cleaning…' : 'Cleanup selected rollouts'}
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<LaunchRoundedIcon />}
                  disabled={ecommerceDemoRolloutMutation.isPending}
                  onClick={() => ecommerceDemoRolloutMutation.mutate()}
                >
                  {ecommerceDemoRolloutMutation.isPending ? 'Rolling out…' : 'Roll out ecommerce demo deployment'}
                </Button>
                <FormControlLabel
                  control={(
                    <Switch
                      checked={verificationRolloutWriteMode}
                      onChange={(event) => setVerificationRolloutWriteMode(event.target.checked)}
                    />
                  )}
                  label="Write mode for hosted verification"
                />
                <Typography variant="body2" color="text.secondary">
                  Keep this off for read-only checks. Turn it on only when you want the dedicated rollout deployment
                  scripts to execute their create/upsert/delete verification paths.
                </Typography>
              </Stack>

              <Stack direction={{ xs: 'column', lg: 'row' }} spacing={1.5} alignItems={{ lg: 'center' }}>
                <Button
                  variant="text"
                  onClick={() => setSelectedVerificationRolloutKeys(verificationRolloutSummary?.items.map((item) => item.key) ?? [])}
                  disabled={!verificationRolloutSummary}
                >
                  Select all
                </Button>
                <Button
                  variant="text"
                  onClick={() => setSelectedVerificationRolloutKeys([])}
                  disabled={selectedVerificationRolloutKeys.length === 0}
                >
                  Clear selection
                </Button>
                <Typography variant="body2" color="text.secondary">
                  Selected presets: <strong>{selectedVerificationRolloutKeys.length}</strong>
                </Typography>
              </Stack>

              {verificationRolloutWriteMode ? (
                <Alert severity="warning">
                  Write mode is restricted to the canonical rollout deployments in this panel. It is intended for
                  dedicated verification stacks, not customer production deployments.
                </Alert>
              ) : null}
              {rolloutActionNotice ? (
                <Alert severity="success">{rolloutActionNotice}</Alert>
              ) : null}
              {deleteNotice ? (
                <Alert severity={deleteNotice.status === 'FAILED' ? 'error' : 'info'}>
                  Deletion request for <strong>{deleteNotice.deploymentName}</strong> is {deleteNotice.status.toLowerCase()}.
                  {' '}
                  {deleteNotice.statusMessage}
                </Alert>
              ) : null}
              {ecommerceRolloutNotice ? (
                <Alert severity="success">
                  Ecommerce demo rollout targeted <strong>{ecommerceRolloutNotice.name}</strong> ({ecommerceRolloutNotice.id}).
                </Alert>
              ) : null}

              {verificationRolloutsQuery.isLoading ? (
                <Typography color="text.secondary">Loading canonical rollout state…</Typography>
              ) : verificationRolloutsQuery.isError ? (
                <Alert severity="error">
                  {verificationRolloutsQuery.error instanceof Error
                    ? verificationRolloutsQuery.error.message
                    : 'Failed to load the canonical rollout set.'}
                </Alert>
              ) : verificationRolloutSummary ? (
                <>
                  <Alert severity="info">{verificationRolloutSummary.summaryMessage}</Alert>
                  {recreateVerificationRolloutsMutation.isError ? (
                    <Alert severity="error">
                      {recreateVerificationRolloutsMutation.error instanceof Error
                        ? recreateVerificationRolloutsMutation.error.message
                        : 'Failed to create and apply the selected canonical rollouts.'}
                    </Alert>
                  ) : null}
                  {cleanupVerificationRolloutsMutation.isError ? (
                    <Alert severity="error">
                      {cleanupVerificationRolloutsMutation.error instanceof Error
                        ? cleanupVerificationRolloutsMutation.error.message
                        : 'Failed to clean up the selected canonical rollouts.'}
                    </Alert>
                  ) : null}
                  {hardResetVerificationRolloutsMutation.isError ? (
                    <Alert severity="error">
                      {hardResetVerificationRolloutsMutation.error instanceof Error
                        ? hardResetVerificationRolloutsMutation.error.message
                        : 'Failed to force-clean the selected canonical rollouts.'}
                    </Alert>
                  ) : null}
                  {ecommerceDemoRolloutMutation.isError ? (
                    <Alert severity="error">
                      {ecommerceDemoRolloutMutation.error instanceof Error
                        ? ecommerceDemoRolloutMutation.error.message
                        : 'Failed to roll out the ecommerce demo deployment.'}
                    </Alert>
                  ) : null}
                  {verificationRolloutNotice ? (
                    <Alert severity="success">
                      {verificationRolloutNotice.summaryMessage}
                    </Alert>
                  ) : null}
                  {rolloutVerificationMutation.isError ? (
                    <Alert severity="error">
                      {rolloutVerificationMutation.error instanceof Error
                        ? rolloutVerificationMutation.error.message
                        : 'Failed to queue hosted verification for the selected rollout deployment.'}
                    </Alert>
                  ) : null}

                  <Stack spacing={1.5}>
                    {verificationRolloutSummary.items.map((item) => {
                      const launchDisabled = !item.deploymentId || !item.verificationReady || rolloutVerificationMutation.isPending
                      return (
                        <Card
                          key={item.key}
                          sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}
                        >
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Stack direction={{ xs: 'column', lg: 'row' }} spacing={1.5} justifyContent="space-between">
                                <Box>
                                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                      {item.displayName}
                                    </Typography>
                                    <Chip
                                      size="small"
                                      label={item.exists ? (item.archived ? 'Archived' : 'Present') : 'Missing'}
                                      color={item.verificationReady ? 'success' : item.exists ? 'warning' : 'default'}
                                      variant={item.verificationReady ? 'filled' : 'outlined'}
                                    />
                                    <Chip size="small" label={item.verificationProfile} variant="outlined" />
                                    <Chip size="small" label={item.environment} variant="outlined" />
                                  </Stack>
                                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                                    {item.description}
                                  </Typography>
                                </Box>

                                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
                                  <FormControlLabel
                                    control={(
                                      <Checkbox
                                        checked={selectedVerificationRolloutSet.has(item.key)}
                                        onChange={() => toggleVerificationRolloutSelection(item.key)}
                                      />
                                    )}
                                    label="Select preset"
                                  />
                                  {item.deploymentId ? (
                                    <Button
                                      variant="outlined"
                                      onClick={() => navigate(`/verification?deploymentId=${encodeURIComponent(item.deploymentId as string)}`)}
                                    >
                                      Open verification
                                    </Button>
                                  ) : null}
                                  <Button
                                    variant="contained"
                                    startIcon={<CheckCircleRoundedIcon />}
                                    disabled={launchDisabled}
                                    onClick={() => {
                                      if (!item.deploymentId) {
                                        return
                                      }
                                      rolloutVerificationMutation.mutate({
                                        deploymentId: item.deploymentId,
                                        profile: item.verificationProfile,
                                        verifyWrite: verificationRolloutWriteMode && item.writeVerificationSupported,
                                      })
                                    }}
                                  >
                                    {rolloutVerificationMutation.isPending ? 'Queueing…' : `Run ${verificationRolloutWriteMode ? 'write' : 'read-only'} verification`}
                                  </Button>
                                </Stack>
                              </Stack>

                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                {item.deploymentId ? <Chip size="small" label={item.deploymentId} variant="outlined" /> : null}
                                {item.deploymentStatus ? <Chip size="small" label={`Deployment: ${item.deploymentStatus}`} variant="outlined" /> : null}
                                {item.latestReleaseStatus ? <Chip size="small" label={`Release: ${item.latestReleaseStatus}`} variant="outlined" /> : null}
                                {item.latestProvisioningStatus ? <Chip size="small" label={`Provisioning: ${item.latestProvisioningStatus}`} variant="outlined" /> : null}
                                {item.latestVerificationStatus ? <Chip size="small" label={`Verification: ${item.latestVerificationStatus}`} variant="outlined" /> : null}
                              </Stack>

                              {item.missingPrerequisites.length > 0 ? (
                                <Alert severity="warning">
                                  {item.readinessMessage}
                                </Alert>
                              ) : !item.verificationReady ? (
                                <Alert severity="info">
                                  {item.readinessMessage}
                                </Alert>
                              ) : (
                                <Alert severity="success">
                                  {item.readinessMessage}
                                </Alert>
                              )}
                            </Stack>
                          </CardContent>
                        </Card>
                      )
                    })}
                  </Stack>
                </>
              ) : null}
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={7}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Box>
                  <Typography variant="h6">Create deployment</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Choose a starting stack preset, give the environment a clear name, and the platform
                    will create the editable draft lifecycle behind it.
                  </Typography>
                </Box>

                <Stack spacing={1.25}>
                  <Typography variant="subtitle2">1. Choose starting stack</Typography>
                  <Alert severity="info">
                    The list prioritizes the OpenAI deployment stacks the platform has already verified across the vector backends we currently support. The full preset catalog stays available below.
                  </Alert>
                  <Box
                    sx={{
                      maxHeight: 460,
                      overflowY: 'auto',
                      pr: 0.5,
                    }}
                  >
                    <Stack spacing={1.5}>
                      {verifiedOpenAiTemplates.length > 0 ? (
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Verified OpenAI Stacks
                          </Typography>
                          {verifiedOpenAiTemplates.map((template) => {
                            const selected = selectedTemplateId === template.id
                            return (
                              <Card
                                key={template.id}
                                onClick={() => form.setValue('templateId', template.id, { shouldValidate: true })}
                                sx={{
                                  cursor: 'pointer',
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
                                        {template.name}
                                      </Typography>
                                      <Chip size="small" label="Deployment-verified" color="success" />
                                      {template.managedVectorProvisioningDefault ? (
                                        <Chip
                                          size="small"
                                          label={managedVectorDefaultLabel(template.managedVectorProvisioningMode)}
                                          color="secondary"
                                          variant="outlined"
                                        />
                                      ) : null}
                                    </Stack>
                                    <Typography variant="body2" color="text.secondary">
                                      {template.description}
                                    </Typography>
                                    {template.managedVectorProvisioningDefault ? (
                                      <Typography variant="caption" color="text.secondary">
                                        {template.managedVectorProvisioningSummary}
                                      </Typography>
                                    ) : null}
                                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                      <Chip size="small" label={template.llmProvider} />
                                      <Chip size="small" label={template.embeddingProvider} variant="outlined" />
                                      <Chip size="small" label={template.vectorStrategy} />
                                    </Stack>
                                  </Stack>
                                </CardContent>
                              </Card>
                            )
                          })}
                        </Stack>
                      ) : null}

                      {otherTemplates.length > 0 ? (
                        <>
                          <Divider flexItem />
                          <Stack spacing={1.25}>
                            <Typography variant="overline" color="text.secondary">
                              Other Presets
                            </Typography>
                            {otherTemplates.map((template) => {
                              const selected = selectedTemplateId === template.id
                              const customStarter = isCustomStarterPreset(template.id)
                              return (
                                <Card
                                  key={template.id}
                                  onClick={() => form.setValue('templateId', template.id, { shouldValidate: true })}
                                  sx={{
                                    cursor: 'pointer',
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
                                          {template.name}
                                        </Typography>
                                        {customStarter ? (
                                          <Chip size="small" label="Editable defaults" />
                                        ) : null}
                                        {template.managedVectorProvisioningDefault ? (
                                          <Chip
                                            size="small"
                                            label={managedVectorDefaultLabel(template.managedVectorProvisioningMode)}
                                            color="secondary"
                                            variant="outlined"
                                          />
                                        ) : null}
                                      </Stack>
                                      <Typography variant="body2" color="text.secondary">
                                        {template.description}
                                      </Typography>
                                      {customStarter ? (
                                        <Typography variant="caption" color="text.secondary">
                                          The platform seeds editable defaults so you can switch providers and vector backend after create without starting from a branded preset.
                                        </Typography>
                                      ) : null}
                                      {template.managedVectorProvisioningDefault ? (
                                        <Typography variant="caption" color="text.secondary">
                                          {template.managedVectorProvisioningSummary}
                                        </Typography>
                                      ) : null}
                                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                        {customStarter ? (
                                          <Chip size="small" label="Runtime-local baseline" variant="outlined" />
                                        ) : (
                                          <>
                                            <Chip size="small" label={template.llmProvider} />
                                            <Chip size="small" label={template.embeddingProvider} variant="outlined" />
                                            <Chip size="small" label={template.vectorStrategy} />
                                          </>
                                        )}
                                      </Stack>
                                    </Stack>
                                  </CardContent>
                                </Card>
                              )
                            })}
                          </Stack>
                        </>
                      ) : null}
                    </Stack>
                  </Box>
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
                  onSubmit={form.handleSubmit((values) => createMutation.mutate({
                    name: values.name,
                    environment: values.environment,
                    templateId: values.templateId,
                    curatedModuleId: values.curatedModuleId,
                    vectorProvisioningMode: values.vectorProvisioningMode,
                    ...(canManageCustomers && values.customerId?.trim()
                      ? { customerId: values.customerId.trim() }
                      : {}),
                    ...(canManageCustomers && values.tenantId?.trim()
                      ? { tenantId: values.tenantId.trim() }
                      : {}),
                  }))}
                  noValidate
                >
                  <Stack spacing={2}>
                    <Typography variant="subtitle2">3. Choose vector management mode</Typography>
                    {selectedTemplate ? (
                      <Grid container spacing={1.5}>
                        {vectorProvisioningOptions.map((option) => {
                          const selected = selectedVectorProvisioningMode === option.value
                          return (
                            <Grid item xs={12} md={6} key={option.value}>
                              <Card
                                onClick={() => form.setValue('vectorProvisioningMode', option.value, { shouldValidate: true })}
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
                                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                                      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                        {option.label}
                                      </Typography>
                                      <Chip size="small" label={option.value} variant="outlined" />
                                    </Stack>
                                    <Typography variant="body2" color="text.secondary">
                                      {option.description}
                                    </Typography>
                                  </Stack>
                                </CardContent>
                              </Card>
                            </Grid>
                          )
                        })}
                      </Grid>
                    ) : (
                      <Alert severity="info">
                        Choose a template first so the platform can show the supported vector management modes.
                      </Alert>
                    )}
                    {vectorCapability ? (
                      <Alert severity={vectorCapability.severity}>
                        {vectorCapability.message}
                      </Alert>
                    ) : null}

                    {canManageCustomers ? (
                      <Stack spacing={1.5}>
                        <Typography variant="subtitle2">4. Bind customer and tenant</Typography>
                        <Alert severity="info" icon={<ApartmentRoundedIcon fontSize="inherit" />}>
                          Customer and tenant binding is admin-controlled. Leave both fields empty to place the
                          deployment under the platform internal customer with an auto-created tenant. Select a
                          customer with no tenant to auto-create a dedicated tenant under that customer.
                        </Alert>
                        <TextField
                          select
                          label="Customer"
                          value={selectedCustomerId}
                          onChange={(event) => form.setValue('customerId', event.target.value, { shouldValidate: false })}
                          helperText={customerScopeLocked
                            ? 'Customer admins are locked to their own customer boundary.'
                            : 'Optional. Choose a customer boundary, or leave blank for the platform internal customer.'}
                          disabled={customerScopeLocked}
                        >
                          {!customerScopeLocked ? (
                            <MenuItem value="">Platform internal / auto-create tenant</MenuItem>
                          ) : null}
                          {customers.map((customer) => (
                            <MenuItem key={customer.id} value={customer.id}>
                              {customer.name} ({customer.slug})
                            </MenuItem>
                          ))}
                        </TextField>
                        <TextField
                          select
                          label="Tenant"
                          value={selectedTenantId}
                          onChange={(event) => form.setValue('tenantId', event.target.value, { shouldValidate: false })}
                          helperText={selectedCustomer
                            ? 'Optional. Pick an existing tenant or leave blank to auto-create a dedicated tenant under the selected customer.'
                            : 'Choose a customer first to reuse an existing tenant. Otherwise the platform will auto-create one.'}
                          disabled={!selectedCustomer}
                        >
                          <MenuItem value="">Auto-create dedicated tenant</MenuItem>
                          {availableTenantsForSelectedCustomer.map((tenant) => (
                            <MenuItem key={tenant.id} value={tenant.id}>
                              {tenant.name} ({tenant.slug}){tenant.boundDeploymentId ? ' · already bound' : ''}
                            </MenuItem>
                          ))}
                        </TextField>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                          <Button
                            variant="outlined"
                            onClick={() => navigate('/customers')}
                          >
                            Manage customers
                          </Button>
                          {selectedCustomer ? (
                            <Chip
                              size="small"
                              label={`${selectedCustomer.tenantCount} tenant(s) · ${selectedCustomer.deploymentCount} deployment(s)`}
                              variant="outlined"
                            />
                          ) : null}
                        </Stack>
                      </Stack>
                    ) : null}

                    <Typography variant="subtitle2">{canManageCustomers ? '5. Name the environment' : '4. Name the environment'}</Typography>
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
                        {isCustomStarterPreset(selectedTemplate.id)
                          ? (
                            <>
                              This deployment will start with <strong>{selectedTemplate.name}</strong>. The platform seeds
                              editable runtime-local defaults so you can change LLM, embeddings, vector backend, and
                              security immediately after create.
                            </>
                          )
                          : (
                            <>
                              This deployment will start with <strong>{selectedTemplate.name}</strong>, using{' '}
                              {selectedTemplate.llmProvider} for LLM, {selectedTemplate.embeddingProvider} for embeddings,
                              and {selectedTemplate.vectorStrategy} for vector storage.
                            </>
                          )}
                        {selectedVectorProvisioningOption ? (
                          <>
                            {' '}Vector management mode will be <strong>{selectedVectorProvisioningOption.label}</strong>.{' '}
                            {selectedVectorProvisioningOption.description}
                          </>
                        ) : null}
                        {selectedTemplate.managedVectorProvisioningDefault ? (
                          <>
                            {' '}It also enables <strong>{managedVectorDefaultLabel(selectedTemplate.managedVectorProvisioningMode).toLowerCase()}</strong> by default.{' '}
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
                      {createMutation.isPending ? 'Creating…' : `${canManageCustomers ? '6' : '5'}. Create deployment`}
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
                        label="Preset"
                        value={templateFilter}
                        onChange={(event) => setTemplateFilter(event.target.value)}
                      >
                        <MenuItem value="ALL">All stack presets</MenuItem>
                        {templateOptions.map((template) => (
                          <MenuItem key={template.id} value={template.id}>
                            {template.label}
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
                          Railway workspace cleanup
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Review orphan Railway projects and services that are no longer referenced by current platform deployments. Only resources that still match the platform-managed profile are deletable here.
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={`Selected cleanup items: ${selectedOrphanCount}`} variant="outlined" />
                        {railwayWorkspaceCleanupQuery.data ? (
                          <>
                            <Chip label={`Orphan projects: ${railwayWorkspaceCleanupQuery.data.orphanProjectCount}`} variant="outlined" />
                            <Chip label={`Orphan services: ${railwayWorkspaceCleanupQuery.data.orphanServiceCount}`} variant="outlined" />
                          </>
                        ) : null}
                      </Stack>
                    </Stack>

                    {orphanCleanupNotice ? (
                      <Alert severity={orphanCleanupNotice.status === 'COMPLETED' ? 'success' : orphanCleanupNotice.status === 'PARTIAL' ? 'warning' : 'error'}>
                        {orphanCleanupNotice.message}
                      </Alert>
                    ) : null}

                    {railwayWorkspaceCleanupQuery.isLoading ? (
                      <Typography color="text.secondary">Loading Railway workspace inventory…</Typography>
                    ) : railwayWorkspaceCleanupQuery.isError ? (
                      <Alert severity="error">
                        {railwayWorkspaceCleanupQuery.error instanceof Error
                          ? railwayWorkspaceCleanupQuery.error.message
                          : 'Failed to load Railway workspace cleanup inventory'}
                      </Alert>
                    ) : railwayWorkspaceCleanupQuery.data && !railwayWorkspaceCleanupQuery.data.available ? (
                      <Alert severity="warning">{railwayWorkspaceCleanupQuery.data.summaryMessage}</Alert>
                    ) : railwayWorkspaceCleanupQuery.data && railwayWorkspaceCleanupQuery.data.projects.length === 0 ? (
                      <Alert severity="success">{railwayWorkspaceCleanupQuery.data.summaryMessage}</Alert>
                    ) : railwayWorkspaceCleanupQuery.data ? (
                      <Stack spacing={1.5}>
                        {railwayWorkspaceCleanupQuery.data.projects.map((project) => {
                          const projectSelected = selectedOrphanProjectSet.has(project.projectId)
                          return (
                            <Card key={project.projectId} sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                              <CardContent>
                                <Stack spacing={1.25}>
                                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.25} justifyContent="space-between">
                                    <Box>
                                      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                        {project.projectName}
                                      </Typography>
                                      <Typography variant="body2" color="text.secondary">
                                        {project.summaryMessage}
                                      </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                      <Chip label={project.ownershipState} color={project.ownershipState === 'ORPHAN' ? 'warning' : 'info'} variant="outlined" />
                                      <Chip label={`${project.totalServiceCount} service(s)`} variant="outlined" />
                                      {project.deletable ? (
                                        <FormControlLabel
                                          control={(
                                            <Checkbox
                                              checked={projectSelected}
                                              onChange={() => toggleOrphanProjectSelection(project.projectId)}
                                            />
                                          )}
                                          label="Delete project"
                                        />
                                      ) : null}
                                    </Stack>
                                  </Stack>
                                  {project.orphanServices.length > 0 ? (
                                    <Stack spacing={1}>
                                      {project.orphanServices.map((service) => (
                                        <Stack
                                          key={service.serviceId}
                                          direction={{ xs: 'column', md: 'row' }}
                                          spacing={1}
                                          justifyContent="space-between"
                                          sx={{ p: 1.25, borderRadius: 1, bgcolor: 'background.default' }}
                                        >
                                          <Box>
                                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                              {service.serviceName}
                                            </Typography>
                                            <Typography variant="caption" color="text.secondary">
                                              {service.summaryMessage}
                                            </Typography>
                                            {service.sourceRepository ? (
                                              <Typography variant="caption" color="text.secondary" display="block">
                                                Source: {service.sourceRepository}{service.sourceBranch ? ` @ ${service.sourceBranch}` : ''}
                                              </Typography>
                                            ) : null}
                                          </Box>
                                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                            <Chip label={service.platformManagedCandidate ? 'Platform profile' : 'Unknown profile'} variant="outlined" />
                                            {service.deletable ? (
                                              <FormControlLabel
                                                control={(
                                                  <Checkbox
                                                    checked={selectedOrphanServiceSet.has(service.serviceId)}
                                                    onChange={() => toggleOrphanServiceSelection(service.serviceId)}
                                                    disabled={projectSelected}
                                                  />
                                                )}
                                                label="Delete service"
                                              />
                                            ) : null}
                                          </Stack>
                                        </Stack>
                                      ))}
                                    </Stack>
                                  ) : null}
                                </Stack>
                              </CardContent>
                            </Card>
                          )
                        })}

                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Button
                            variant="outlined"
                            onClick={() => {
                              setSelectedOrphanProjectIds([])
                              setSelectedOrphanServiceIds([])
                            }}
                            disabled={selectedOrphanCount === 0 || orphanCleanupMutation.isPending}
                          >
                            Clear cleanup selection
                          </Button>
                          <Button
                            color="error"
                            variant="outlined"
                            startIcon={<DeleteForeverRoundedIcon />}
                            disabled={selectedOrphanCount === 0 || orphanCleanupMutation.isPending}
                            onClick={() => {
                              setOrphanCleanupDialogOpen(true)
                              setOrphanCleanupConfirmationText('')
                              setOrphanCleanupReason('')
                            }}
                          >
                            Delete selected orphan resources
                          </Button>
                        </Stack>
                      </Stack>
                    ) : null}
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
                  const connectorAdminUrl = deployment.runtimeBaseUrl
                    ? joinUrl(deployment.runtimeBaseUrl, '/api/admin/connector/overview')
                    : null
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
                                {deployment.environment} environment · {templateMetadataById.get(deployment.templateId)?.name ?? deployment.templateId}
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
                              {deployment.deletion ? (
                                <Chip
                                  label={`Deletion ${deployment.deletion.status}`}
                                  color={deletionChipColor(deployment.deletion.status)}
                                  variant="outlined"
                                />
                              ) : null}
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

                          {deployment.deletion ? (
                            <Alert severity={deployment.deletion.status === 'FAILED' ? 'error' : 'info'}>
                              {deployment.deletion.message}
                            </Alert>
                          ) : null}

                          {deployment.binding ? (
                            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', bgcolor: 'background.default' }}>
                              <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                                <Stack spacing={1.25}>
                                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} justifyContent="space-between">
                                    <Box>
                                      <Typography variant="subtitle2">Customer and tenant binding</Typography>
                                      <Typography variant="body2" color="text.secondary">
                                        {deployment.binding.customerName} / {deployment.binding.tenantName}
                                      </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                      <Chip size="small" label={`Customer: ${deployment.binding.customerSlug ?? deployment.binding.customerId ?? 'unknown'}`} variant="outlined" />
                                      <Chip size="small" label={`Tenant: ${deployment.binding.tenantSlug ?? deployment.binding.tenantId ?? 'unknown'}`} variant="outlined" />
                                      <Chip
                                        size="small"
                                        label={deployment.binding.bindingChangeStatus.replace(/_/g, ' ')}
                                        color={bindingChangeColor(deployment.binding.bindingChangeStatus)}
                                        variant="outlined"
                                      />
                                      <Chip size="small" label={`${deployment.binding.publishedVersionCount} published`} variant="outlined" />
                                      <Chip size="small" label={`${deployment.binding.releaseCount} releases`} variant="outlined" />
                                    </Stack>
                                  </Stack>
                                  <Typography variant="body2" color="text.secondary">
                                    {deployment.binding.bindingChangeMessage}
                                  </Typography>
                                  {canManageCustomers ? (
                                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                      <Button
                                        variant="outlined"
                                        disabled={!deployment.binding.mutable}
                                        onClick={() => setBindingTarget(deployment)}
                                      >
                                        Change binding
                                      </Button>
                                      <Button
                                        variant="text"
                                        onClick={() => navigate('/customers')}
                                      >
                                        Open customers
                                      </Button>
                                    </Stack>
                                  ) : null}
                                </Stack>
                              </CardContent>
                            </Card>
                          ) : null}

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
                                    disabled={deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
                                    onClick={() => navigate(primaryAction.to)}
                                  >
                                    {primaryAction.label}
                                  </Button>
                                  <Button
                                    variant="outlined"
                                    startIcon={<HistoryRoundedIcon />}
                                    disabled={deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
                                    onClick={() => navigate(`/revisions?deploymentId=${deployment.id}`)}
                                  >
                                    Releases
                                  </Button>
                                  <Button
                                    variant="outlined"
                                    startIcon={<InsightsRoundedIcon />}
                                    disabled={deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
                                    onClick={() => navigate(`/diagnostics?deploymentId=${deployment.id}`)}
                                  >
                                    Diagnostics
                                  </Button>
                                  {deployment.access.canOperate ? (
                                    <Button
                                      variant="outlined"
                                      disabled={deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
                                      onClick={() => navigate(`/poc?deploymentId=${deployment.id}`)}
                                    >
                                      POC
                                    </Button>
                                  ) : null}
                                  {deployment.access.canEdit ? (
                                    <Button
                                      variant="outlined"
                                      disabled={deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
                                      onClick={() => navigate(`/prompts?deploymentId=${deployment.id}`)}
                                    >
                                      Prompts
                                    </Button>
                                  ) : null}
                                  {deployment.access.canAdmin ? (
                                    <Button
                                      variant="outlined"
                                      disabled={deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
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
                              disabled={deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
                              onClick={() => navigate(`/overview?deploymentId=${deployment.id}`)}
                            >
                              Workspace
                            </Button>
                            {deployment.access.canOperate && deployment.runtimeBaseUrl ? (
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
                            {deployment.access.canOperate && runtimeSwaggerUrl ? (
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
                            {deployment.access.canOperate && connectorAdminUrl ? (
                              <Button
                                variant="text"
                                startIcon={<LaunchRoundedIcon />}
                                href={connectorAdminUrl}
                                target="_blank"
                                rel="noreferrer"
                              >
                                Connector admin via runtime
                              </Button>
                            ) : null}
                            <Button
                              color="warning"
                              variant="outlined"
                              startIcon={<ArchiveRoundedIcon />}
                              disabled={archiveMutation.isPending || isReleaseInProgress(deployment) || !deployment.access.canAdmin || deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
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
                                  {deployment.deletion ? (
                                    <Chip
                                      label={`Deletion ${deployment.deletion.status}`}
                                      color={deletionChipColor(deployment.deletion.status)}
                                      variant="outlined"
                                    />
                                  ) : null}
                                </Stack>
                              </Stack>
                              <Typography variant="body2" color="text.secondary">
                                {deployment.healthSummary}
                              </Typography>
                              {deployment.deletion ? (
                                <Alert severity={deployment.deletion.status === 'FAILED' ? 'error' : 'info'}>
                                  {deployment.deletion.message}
                                </Alert>
                              ) : null}
                              {deployment.binding ? (
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Chip size="small" label={`Customer: ${deployment.binding.customerName}`} variant="outlined" />
                                  <Chip size="small" label={`Tenant: ${deployment.binding.tenantName}`} variant="outlined" />
                                </Stack>
                              ) : null}
                              <Stack direction="row" spacing={1} flexWrap="wrap">
                                <Button
                                  variant="outlined"
                                  startIcon={<UnarchiveRoundedIcon />}
                                  disabled={restoreMutation.isPending || deleteMutation.isPending || !deployment.access.canAdmin || deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
                                  onClick={() => restoreMutation.mutate(deployment.id)}
                                >
                                  {restoreMutation.isPending ? 'Restoring…' : 'Restore'}
                                </Button>
                                <Button
                                  color="error"
                                  variant="outlined"
                                  startIcon={<DeleteForeverRoundedIcon />}
                                  disabled={deleteMutation.isPending || restoreMutation.isPending || !deployment.access.canAdmin || deployment.deletion?.status === 'QUEUED' || deployment.deletion?.status === 'RUNNING'}
                                  onClick={() => {
                                    if (!canManageBulk && deployment.approvalRequiredForDelete) {
                                      navigate(`/approvals?deploymentId=${encodeURIComponent(deployment.id)}&action=DELETE_DEPLOYMENT`)
                                      return
                                    }
                                    setDeleteTarget(deployment)
                                    setDeleteConfirmationText('')
                                    setDeleteHardDelete(false)
                                    setDeleteHardDeleteReason('')
                                  }}
                                >
                                  {!canManageBulk && deployment.approvalRequiredForDelete
                                    ? 'Request delete approval'
                                    : deployment.deletion?.status === 'FAILED'
                                      ? 'Retry delete'
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
        open={bindingTarget != null}
        onClose={() => {
          if (!updateBindingMutation.isPending && !createBindingMigrationMutation.isPending) {
            setBindingTarget(null)
            setBindingCustomerId('')
            setBindingTenantId('')
            setBindingMigrationName('')
            setBindingMigrationEnvironment('')
            setBindingMigrationReason('')
          }
        }}
      >
        <DialogTitle>Change customer and tenant binding</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1, minWidth: { xs: 280, sm: 460 } }}>
            <DialogContentText>
              Deployment binding is only mutable before any version is published or any release exists.
              Choose a customer and optionally an existing tenant. Leaving tenant empty creates a new
              dedicated tenant under the selected customer.
            </DialogContentText>
            {bindingTarget?.binding ? (
              <Alert severity="info">
                Current binding: <strong>{bindingTarget.binding.customerName}</strong> / <strong>{bindingTarget.binding.tenantName}</strong>
              </Alert>
            ) : null}
            {bindingTarget?.binding ? (
              <Alert severity={bindingTarget.binding.mutable ? 'info' : 'warning'}>
                {bindingTarget.binding.bindingChangeMessage}
              </Alert>
            ) : null}
            {bindingRequiresMigration ? (
              <Alert severity="warning">
                Historical deployment ownership will not be mutated in place. The governed flow creates a new
                tenant-bound draft deployment from the source deployment&apos;s current draft, then leaves the
                source deployment unchanged for rollback and audit.
              </Alert>
            ) : null}
            <TextField
              select
              label="Customer"
              value={bindingCustomerId}
              onChange={(event) => setBindingCustomerId(event.target.value)}
              helperText={customerScopeLocked
                ? 'Customer admins can only rebind deployments inside their own customer boundary.'
                : undefined}
              disabled={customerScopeLocked}
            >
              {customers.map((customer) => (
                <MenuItem key={customer.id} value={customer.id}>
                  {customer.name} ({customer.slug})
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Tenant"
              value={bindingTenantId}
              onChange={(event) => setBindingTenantId(event.target.value)}
              disabled={!selectedBindingCustomer}
              helperText={selectedBindingCustomer
                ? 'Optional. Leave empty to auto-create a dedicated tenant under this customer.'
                : 'Choose a customer first.'}
            >
              <MenuItem value="">Auto-create dedicated tenant</MenuItem>
              {availableBindingTenants.map((tenant) => {
                const alreadyBoundToOther = tenant.boundDeploymentId != null && tenant.boundDeploymentId !== bindingTarget?.id
                return (
                  <MenuItem key={tenant.id} value={tenant.id} disabled={alreadyBoundToOther}>
                    {tenant.name} ({tenant.slug}){alreadyBoundToOther ? ' · already bound' : ''}
                  </MenuItem>
                )
              })}
            </TextField>
            {bindingRequiresMigration ? (
              <>
                <TextField
                  label="New deployment name"
                  value={bindingMigrationName}
                  onChange={(event) => setBindingMigrationName(event.target.value)}
                  helperText="The migration flow creates a new deployment. Edit the proposed name if you want a more specific tenant-bound rollout name."
                />
                <TextField
                  label="Environment"
                  value={bindingMigrationEnvironment}
                  onChange={(event) => setBindingMigrationEnvironment(event.target.value)}
                  helperText="Defaults to the source deployment environment."
                />
                <TextField
                  label="Migration reason"
                  value={bindingMigrationReason}
                  onChange={(event) => setBindingMigrationReason(event.target.value)}
                  required
                  multiline
                  minRows={2}
                  helperText="Required for audit. Describe why this deployment is being migrated to a different tenant."
                />
                {bindingMigrationPreviewQuery.isLoading ? (
                  <Alert severity="info">Preparing tenant migration preview…</Alert>
                ) : null}
                {bindingMigrationPreviewQuery.data ? (
                  <Alert severity={bindingMigrationPreviewQuery.data.status === 'READY' ? 'info' : 'warning'}>
                    <strong>{bindingMigrationPreviewQuery.data.message}</strong>
                    <br />
                    Proposed deployment: {bindingMigrationPreviewQuery.data.proposedDeploymentName} (
                    {bindingMigrationPreviewQuery.data.proposedEnvironmentName})
                    <br />
                    History carried forward by reference: {bindingMigrationPreviewQuery.data.publishedVersionCount} published version(s),{' '}
                    {bindingMigrationPreviewQuery.data.releaseCount} release(s).
                    <br />
                    {bindingMigrationPreviewQuery.data.sourceConfigStrategy}
                    <br />
                    {bindingMigrationPreviewQuery.data.sharedVectorMessage}
                    <br />
                    {bindingMigrationPreviewQuery.data.rollbackPosture}
                  </Alert>
                ) : null}
                {bindingMigrationPreviewQuery.isError ? (
                  <Alert severity="error">
                    {bindingMigrationPreviewQuery.error instanceof Error
                      ? bindingMigrationPreviewQuery.error.message
                      : 'Failed to prepare tenant migration preview.'}
                  </Alert>
                ) : null}
              </>
            ) : null}
            {updateBindingMutation.isError ? (
              <Alert severity="error">
                {updateBindingMutation.error instanceof Error
                  ? updateBindingMutation.error.message
                  : 'Failed to update deployment binding.'}
              </Alert>
            ) : null}
            {createBindingMigrationMutation.isError ? (
              <Alert severity="error">
                {createBindingMigrationMutation.error instanceof Error
                  ? createBindingMigrationMutation.error.message
                  : 'Failed to create the tenant migration deployment.'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setBindingTarget(null)
              setBindingCustomerId('')
              setBindingTenantId('')
              setBindingMigrationName('')
              setBindingMigrationEnvironment('')
              setBindingMigrationReason('')
            }}
            disabled={updateBindingMutation.isPending || createBindingMigrationMutation.isPending}
          >
            Cancel
          </Button>
          {bindingRequiresMigration ? (
            <Button
              variant="contained"
              disabled={
                !bindingTarget
                || !bindingCustomerId
                || !bindingMigrationReason.trim()
                || createBindingMigrationMutation.isPending
                || bindingMigrationPreviewQuery.isLoading
                || bindingMigrationPreviewQuery.isError
                || !bindingMigrationPreviewQuery.data
              }
              onClick={() => {
                if (!bindingTarget) {
                  return
                }
                createBindingMigrationMutation.mutate({
                  deploymentId: bindingTarget.id,
                  customerId: bindingCustomerId,
                  tenantId: bindingTenantId || undefined,
                  proposedDeploymentName: bindingMigrationName.trim() || undefined,
                  proposedEnvironmentName: bindingMigrationEnvironment.trim() || undefined,
                  reason: bindingMigrationReason.trim(),
                })
              }}
            >
              {createBindingMigrationMutation.isPending ? 'Creating migration…' : 'Create migration deployment'}
            </Button>
          ) : (
            <Button
              variant="contained"
              disabled={!bindingTarget || !bindingCustomerId || updateBindingMutation.isPending}
              onClick={() => {
                if (!bindingTarget) {
                  return
                }
                updateBindingMutation.mutate({
                  deploymentId: bindingTarget.id,
                  customerId: bindingCustomerId,
                  tenantId: bindingTenantId || undefined,
                })
              }}
            >
              {updateBindingMutation.isPending ? 'Saving…' : 'Save binding'}
            </Button>
          )}
        </DialogActions>
      </Dialog>

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
            setDeleteHardDelete(false)
            setDeleteHardDeleteReason('')
          }
        }}
      >
        <DialogTitle>Delete deployment permanently</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <DialogContentText>
              This queues a permanent delete request. The platform will mark the deployment as subject to deletion completion,
              then finish record removal and optional infrastructure cleanup asynchronously. To continue, type the deployment name exactly.
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
            {canManageBulk ? (
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={deleteHardDelete}
                    onChange={(event) => setDeleteHardDelete(event.target.checked)}
                  />
                )}
                label="Also hard delete Railway services/project and managed vector resources"
              />
            ) : null}
            {deleteHardDelete ? (
              <>
                <Alert severity="warning">
                  Hard delete is restricted to platform administrators. The platform will queue Railway and provider-side cleanup first, then remove platform records after teardown finishes.
                </Alert>
                <TextField
                  label="Hard delete reason"
                  value={deleteHardDeleteReason}
                  onChange={(event) => setDeleteHardDeleteReason(event.target.value)}
                  helperText="Required for infrastructure teardown. Describe why Railway and managed provider resources should be removed."
                />
              </>
            ) : null}
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
              setDeleteHardDelete(false)
              setDeleteHardDeleteReason('')
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
                deleteMutation.mutate({
                  deploymentId: deleteTarget.id,
                  hardDelete: deleteHardDelete,
                  reason: deleteHardDelete ? deleteHardDeleteReason.trim() : undefined,
                })
              }
            }}
          >
            {deleteMutation.isPending ? 'Queueing…' : 'Queue delete'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={orphanCleanupDialogOpen}
        onClose={() => {
          if (!orphanCleanupMutation.isPending) {
            setOrphanCleanupDialogOpen(false)
            setOrphanCleanupConfirmationText('')
            setOrphanCleanupReason('')
          }
        }}
      >
        <DialogTitle>Delete orphan Railway resources</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <DialogContentText>
              This only deletes selected orphan Railway projects and services that are no longer referenced by current platform deployments and still match the platform-managed profile. Type <strong>DELETE ORPHANS</strong> and provide a reason to continue.
            </DialogContentText>
            <Alert severity="warning">
              Selected items: <strong>{selectedOrphanCount}</strong>. Live deployments are never targeted here.
            </Alert>
            <TextField
              autoFocus
              label="Type DELETE ORPHANS"
              value={orphanCleanupConfirmationText}
              onChange={(event) => setOrphanCleanupConfirmationText(event.target.value)}
            />
            <TextField
              label="Cleanup reason"
              value={orphanCleanupReason}
              onChange={(event) => setOrphanCleanupReason(event.target.value)}
              helperText="Required for audit. Explain why these Railway resources are safe to remove."
            />
            {orphanCleanupMutation.isError ? (
              <Alert severity="error">
                {orphanCleanupMutation.error instanceof Error
                  ? orphanCleanupMutation.error.message
                  : 'Failed to clean up orphan Railway resources'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setOrphanCleanupDialogOpen(false)
              setOrphanCleanupConfirmationText('')
              setOrphanCleanupReason('')
            }}
            disabled={orphanCleanupMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            color="error"
            variant="contained"
            startIcon={<DeleteForeverRoundedIcon />}
            disabled={!orphanCleanupConfirmationValid || selectedOrphanCount === 0 || orphanCleanupMutation.isPending}
            onClick={() => orphanCleanupMutation.mutate({
              reason: orphanCleanupReason.trim(),
              projectIds: selectedOrphanProjectIds,
              serviceIds: selectedOrphanServiceIds,
            })}
          >
            {orphanCleanupMutation.isPending ? 'Deleting…' : 'Confirm orphan cleanup'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={rolloutCleanupDialogOpen}
        onClose={() => {
          if (!cleanupVerificationRolloutsMutation.isPending) {
            setRolloutCleanupDialogOpen(false)
            setRolloutCleanupConfirmationText('')
          }
        }}
      >
        <DialogTitle>Clean up selected canonical rollouts</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1, minWidth: { xs: 280, sm: 520 } }}>
            <DialogContentText>
              This permanently deletes the selected canonical verification deployments and tears down the Railway and managed
              provider artifacts linked through the platform hard-delete path. Type <strong>CLEANUP ROLLOUTS</strong> to continue.
            </DialogContentText>
            <Alert severity="warning">
              Selected presets: <strong>{selectedVerificationRolloutItems.length}</strong>
              {selectedVerificationRolloutItems.length > 0
                ? ` · ${selectedVerificationRolloutItems.map((item) => item.displayName).join(', ')}`
                : ''}
            </Alert>
            <TextField
              autoFocus
              label="Type CLEANUP ROLLOUTS"
              value={rolloutCleanupConfirmationText}
              onChange={(event) => setRolloutCleanupConfirmationText(event.target.value)}
            />
            {cleanupVerificationRolloutsMutation.isError ? (
              <Alert severity="error">
                {cleanupVerificationRolloutsMutation.error instanceof Error
                  ? cleanupVerificationRolloutsMutation.error.message
                  : 'Failed to clean up the selected canonical rollouts.'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setRolloutCleanupDialogOpen(false)
              setRolloutCleanupConfirmationText('')
            }}
            disabled={cleanupVerificationRolloutsMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            color="error"
            variant="contained"
            startIcon={<DeleteForeverRoundedIcon />}
            disabled={!rolloutCleanupConfirmationValid || cleanupVerificationRolloutsMutation.isPending}
            onClick={() => cleanupVerificationRolloutsMutation.mutate(selectedVerificationRolloutKeys)}
          >
            {cleanupVerificationRolloutsMutation.isPending ? 'Cleaning…' : 'Confirm rollout cleanup'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={rolloutHardResetDialogOpen}
        onClose={() => {
          if (!hardResetVerificationRolloutsMutation.isPending) {
            setRolloutHardResetDialogOpen(false)
            setRolloutHardResetConfirmationText('')
          }
        }}
      >
        <DialogTitle>Force hard cleanup selected canonical rollouts</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1, minWidth: { xs: 280, sm: 560 } }}>
            <DialogContentText>
              This archives and hard deletes the selected canonical verification deployments where possible. It does not
              recreate replacements. Use the separate create/apply action after cleanup if you want fresh rollouts.
              Any cleanup that is already queued or running continues in the background. Type <strong>RESET ROLLOUTS</strong>
              to continue.
            </DialogContentText>
            <Alert severity="error">
              This is the recovery path for stuck canonical presets. It is intentionally destructive and can leave
              background teardown running for superseded rollouts.
            </Alert>
            <Alert severity="warning">
              Selected presets: <strong>{selectedVerificationRolloutItems.length}</strong>
              {selectedVerificationRolloutItems.length > 0
                ? ` · ${selectedVerificationRolloutItems.map((item) => item.displayName).join(', ')}`
                : ''}
            </Alert>
            <TextField
              autoFocus
              label="Type RESET ROLLOUTS"
              value={rolloutHardResetConfirmationText}
              onChange={(event) => setRolloutHardResetConfirmationText(event.target.value)}
            />
            {hardResetVerificationRolloutsMutation.isError ? (
              <Alert severity="error">
                {hardResetVerificationRolloutsMutation.error instanceof Error
                  ? hardResetVerificationRolloutsMutation.error.message
                  : 'Failed to force-clean the selected canonical rollouts.'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setRolloutHardResetDialogOpen(false)
              setRolloutHardResetConfirmationText('')
            }}
            disabled={hardResetVerificationRolloutsMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            color="error"
            variant="contained"
            startIcon={<DeleteForeverRoundedIcon />}
            disabled={!rolloutHardResetConfirmationValid || hardResetVerificationRolloutsMutation.isPending}
            onClick={() => hardResetVerificationRolloutsMutation.mutate(selectedVerificationRolloutKeys)}
          >
            {hardResetVerificationRolloutsMutation.isPending ? 'Cleaning…' : 'Confirm hard cleanup'}
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
