import CloudOutlinedIcon from '@mui/icons-material/CloudOutlined'
import DeleteSweepRoundedIcon from '@mui/icons-material/DeleteSweepRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import DescriptionRoundedIcon from '@mui/icons-material/DescriptionRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import FolderOpenRoundedIcon from '@mui/icons-material/FolderOpenRounded'
import PlayArrowRoundedIcon from '@mui/icons-material/PlayArrowRounded'
import PreviewRoundedIcon from '@mui/icons-material/PreviewRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Grid,
  IconButton,
  Link,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  bindCustomerDocumentStorage,
  deleteDocumentStorageBinding,
  cleanupDocumentRetention,
  discoverDocumentSources,
  fetchCustomerDeploymentPortfolio,
  fetchCustomerSelfServiceCatalogue,
  fetchCustomerSelfServiceOffer,
  fetchPlatformAuthSession,
  fetchDeploymentDraft,
  fetchDeploymentReleases,
  fetchDeploymentTargetContext,
  fetchDeploymentMarketplaceInstalls,
  fetchMarketplacePluginVersion,
  fetchDeploymentTargetProfiles,
  fetchDocumentConnectorStatus,
  fetchDocumentRetentionStatus,
  fetchDocumentSourceDetail,
  fetchDocumentSources,
  fetchDocumentStorageBindings,
  indexDocumentSource,
  previewDocumentSource,
  reconcileDocumentSource,
  refreshDocumentSource,
  registerDocumentSource,
  removeDocumentIndex,
  revokeCustomerDocumentStorage,
  resolveDeploymentMarketplaceInstall,
  updateDeploymentMarketplaceInstall,
  upsertDocumentStorageBinding,
  verifyDocumentRetrieval,
  type DeploymentMarketplaceInstallSummary,
  type DeploymentTargetReference,
  type DiscoveredDocumentSource,
  type DocumentSourceSummary,
  type UpsertDocumentStorageBindingRequest,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { CustomerAdvancedSection, useCustomerEditorSection } from '../components/customer/CustomerDetailSections'
import { customerDocumentStorageScope, requireFreshCustomerDocumentStorageScope, hasReviewedDocumentStorageInstall,
  type CustomerDocumentStorageScope } from '../components/customer/customerDocumentStorageScope'
import { hasApprovedAnonymousDocumentSource } from './documentKnowledgeAudience'

const DOCUMENT_PLUGIN_IDS = new Set([
  'mkp-data-document-knowledge-s3',
  'mkp-data-document-knowledge-mounted-demo',
  'mkp-data-dealership-knowledge-v1',
  'mkp-data-dealership-knowledge-mounted-demo-v1',
])

type BindingForm = {
  connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE' | 'MOUNTED_FOLDER'
  targetProfileId: string
  endpoint: string
  region: string
  bucket: string
  prefix: string
  accessKey: string
  secretKey: string
  sessionToken: string
  pathStyleAccess: boolean
  objectVersioningAvailable: boolean
  allowInsecureEndpoint: boolean
}

const emptyBindingForm: BindingForm = {
  connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE',
  targetProfileId: '',
  endpoint: '',
  region: 'us-east-1',
  bucket: '',
  prefix: '',
  accessKey: '',
  secretKey: '',
  sessionToken: '',
  pathStyleAccess: true,
  objectVersioningAvailable: false,
  allowInsecureEndpoint: false,
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function asRecord(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {}
}

function documentDataset(config: unknown): Record<string, unknown> | null {
  const datasets = asRecord(config).datasets
  if (!Array.isArray(datasets)) return null
  return datasets.find((value) => (
    isRecord(value) && value.ingestionMode === 'EXTERNAL_DOCUMENT_STORAGE'
  )) as Record<string, unknown> | undefined ?? null
}

function documentInstall(
  installs: DeploymentMarketplaceInstallSummary[],
  dataset: Record<string, unknown> | null,
): DeploymentMarketplaceInstallSummary | null {
  const installId = typeof dataset?.marketplaceInstallId === 'string' ? dataset.marketplaceInstallId : ''
  return installs.find((install) => install.id === installId)
    ?? installs.find((install) => DOCUMENT_PLUGIN_IDS.has(install.pluginId))
    ?? null
}

function statusColor(status: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  const value = (status ?? '').toUpperCase()
  if (['READY', 'ACTIVE', 'COMPLETED', 'SUCCEEDED', 'DELETED'].includes(value)) return 'success'
  if (['FAILED', 'DELETE_FAILED', 'ERROR', 'UNAVAILABLE'].includes(value)) return 'error'
  if (value) return 'warning'
  return 'default'
}

function formatBytes(value: number): string {
  if (!Number.isFinite(value) || value < 0) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : 'Operation failed.'
}

function configWithBinding(config: unknown, bindingRef: string): Record<string, unknown> {
  return { ...asRecord(config), documentStorageBindingRef: bindingRef }
}

function sameTarget(target: DeploymentTargetReference | undefined, current: DeploymentTargetReference): boolean {
  return target?.deploymentId === current.deploymentId
    && target?.targetProfileId === current.targetProfileId
    && target?.releaseId === current.releaseId
}

export function DocumentKnowledgePage({ initialReleaseId = '', initialSourceId = '', lockReleaseSelection = false }: { initialReleaseId?: string; initialSourceId?: string; lockReleaseSelection?: boolean } = {}) {
  const auth = usePlatformAuth()
  const detailSection = useCustomerEditorSection()
  const queryClient = useQueryClient()
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const customerSelfService = auth.session?.role === 'CUSTOMER_ADMIN'
  const customerId = auth.session?.customerId ?? ''
  const [useTargetBindingSlot, setUseTargetBindingSlot] = useState(false)
  const [customerStorageProfileId, setCustomerStorageProfileId] = useState('')
  const [bindingForm, setBindingForm] = useState<BindingForm>(emptyBindingForm)
  const [selectedSourceId, setSelectedSourceId] = useState(initialSourceId)
  const [selectedReleaseId, setSelectedReleaseId] = useState(initialReleaseId)
  const releasesQuery = useQuery({
    queryKey: ['deployment-releases', selectedDeploymentId],
    queryFn: () => fetchDeploymentReleases(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const availableReleases = (releasesQuery.data ?? []).filter((release) =>
    release.targetProfileId && release.provisioningStatus === 'ACTIVE'
    && ['VERIFYING', 'APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(release.status))
  const selectedRelease = availableReleases.find((release) => release.id === selectedReleaseId)
  const targetReference = {
    deploymentId: selectedDeploymentId,
    targetProfileId: selectedRelease?.targetProfileId ?? '',
    releaseId: selectedRelease?.id ?? '',
  }
  const targetQuery = useQuery({
    queryKey: ['deployment-target-context', selectedDeploymentId, targetReference.targetProfileId, selectedReleaseId],
    queryFn: () => fetchDeploymentTargetContext(targetReference),
    enabled: !!selectedRelease,
    retry: false,
  })
  useEffect(() => {
    setSelectedReleaseId(initialReleaseId)
  }, [selectedDeploymentId, initialReleaseId])
  useEffect(() => {
    setSelectedSourceId(initialSourceId)
  }, [selectedDeploymentId, targetReference.targetProfileId, selectedReleaseId, initialSourceId])
  const [retrievalQuery, setRetrievalQuery] = useState('Summarize the approved document evidence.')
  const [removalSelection, setRemovalSelection] = useState<{
    target: DeploymentTargetReference; source: DocumentSourceSummary
  } | null>(null)
  const [registrationSelection, setRegistrationSelection] = useState<{
    target: DeploymentTargetReference; source: DiscoveredDocumentSource
  } | null>(null)
  const removeSource = sameTarget(removalSelection?.target, targetReference) ? removalSelection?.source : null
  const registrationSource = sameTarget(registrationSelection?.target, targetReference) ? registrationSelection?.source : null
  const [registrationCategory, setRegistrationCategory] = useState('')
  const [registrationVisibility, setRegistrationVisibility] = useState('tenant')
  const [registrationApproved, setRegistrationApproved] = useState(false)
  // Completion callbacks may run after a different target or dialog has been selected.
  const currentSelection = useRef({ target: targetReference, registrationSelection, removalSelection })
  currentSelection.current = { target: targetReference, registrationSelection, removalSelection }

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const installsQuery = useQuery({
    queryKey: ['deployment-marketplace-installs', selectedDeploymentId],
    queryFn: () => fetchDeploymentMarketplaceInstalls(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const profilesQuery = useQuery({
    queryKey: ['deployment-target-profiles'],
    queryFn: () => fetchDeploymentTargetProfiles(),
    // Customer target authority comes from the reviewed offer. The provider
    // catalogue remains a staff-only control-plane surface.
    enabled: selectedDeploymentId.length > 0 && !customerSelfService,
  })
  const bindingsQuery = useQuery({
    queryKey: ['document-storage-bindings', selectedDeploymentId],
    queryFn: () => fetchDocumentStorageBindings(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const customerPortfolioQuery = useQuery({
    queryKey: ['customer-deployment-portfolio', customerId, auth.session?.actorId, auth.session?.authenticationMode],
    queryFn: fetchCustomerDeploymentPortfolio,
    enabled: customerSelfService && Boolean(customerId),
  })
  const customerCatalogueQuery = useQuery({
    queryKey: ['customer-self-service-catalogue', customerId, auth.session?.actorId, auth.session?.authenticationMode],
    queryFn: fetchCustomerSelfServiceCatalogue,
    enabled: customerSelfService && Boolean(customerId),
  })

  const dataset = useMemo(
    () => documentDataset(draftQuery.data?.marketplaceDatasetConfig),
    [draftQuery.data?.marketplaceDatasetConfig],
  )
  const datasetId = targetQuery.data?.documentDatasetIds[0]
    ?? (typeof dataset?.datasetId === 'string' ? dataset.datasetId : 'document-knowledge')
  const install = useMemo(
    () => documentInstall(installsQuery.data ?? [], dataset),
    [dataset, installsQuery.data],
  )
  const documentVersionQuery = useQuery({
    queryKey: ['document-knowledge-plugin-version', customerId, selectedDeploymentId, install?.pluginId, install?.pluginVersion],
    queryFn: () => fetchMarketplacePluginVersion(install!.pluginId, install!.pluginVersion),
    enabled: Boolean(install),
    retry: false,
  })
  const documentContractReady = Boolean(install && documentVersionQuery.isSuccess
    && documentVersionQuery.data.id === install.pluginVersionId)
  const publicAudiencePackage = documentContractReady
    && hasApprovedAnonymousDocumentSource(documentVersionQuery.data?.manifest, datasetId)
  const configuredBindingRef = asRecord(install?.config).documentStorageBindingRef
  const customerDeployment = (customerPortfolioQuery.data ?? [])
    .find((deployment) => deployment.deploymentId === selectedDeploymentId) ?? null
  const customerOffer = (customerCatalogueQuery.data ?? [])
    .find((offer) => offer.id === customerDeployment?.offerId) ?? null
  const customerStorageSlot = customerOffer?.bindingSlots.find((slot) => slot.kind === 'CUSTOMER_S3') ?? null
  const bindingAuthorityKey = JSON.stringify([auth.session?.actorId, auth.session?.authenticationMode, customerId,
    selectedDeploymentId, selectedDeploymentSummary?.binding, customerDeployment, customerOffer?.id,
    customerOffer?.fingerprint, customerOffer?.entitlement, customerStorageSlot])
  const bindingAuthority = useRef({ key: bindingAuthorityKey, target: bindingForm.targetProfileId })
  bindingAuthority.current = { key: bindingAuthorityKey, target: bindingForm.targetProfileId }
  const currentCustomerStorageScope = (targetProfileId: string, operation: CustomerDocumentStorageScope['operation'], selectedProfileId?: string) => {
    if (!customerPortfolioQuery.isSuccess || !customerCatalogueQuery.isSuccess
      || customerPortfolioQuery.isFetching || customerCatalogueQuery.isFetching) return null
    try {
      return customerDocumentStorageScope({ session: auth.session, deployment: customerDeployment, selectedDeployment: selectedDeploymentSummary,
        selectedDeploymentId, offer: customerOffer, targetProfileId, bindingSlotId: customerStorageSlot?.id ?? '', operation, selectedProfileId })
    } catch { return null }
  }
  const customerBindScope = currentCustomerStorageScope(bindingForm.targetProfileId, 'BINDING_REPLACE', customerStorageProfileId)
  useEffect(() => {
    setUseTargetBindingSlot(customerSelfService || configuredBindingRef === 'slot:document-storage')
  }, [customerSelfService, selectedDeploymentId, install?.id, configuredBindingRef])
  useEffect(() => {
    if (!customerSelfService) return
    const profiles = customerStorageSlot?.supportedProfileIds ?? []
    if (!profiles.includes(customerStorageProfileId)) setCustomerStorageProfileId(profiles[0] ?? '')
  }, [customerSelfService, customerStorageProfileId, customerStorageSlot])
  const matchingProfiles = useMemo(() => {
    if (customerSelfService) {
      return (customerOffer?.targets ?? []).map((target) => ({
        id: target.id,
        name: target.intent === 'CUSTOMER_STAGING' ? 'Customer staging' : 'Customer production',
        providerType: 'Reviewed offer',
      }))
    }
    return (profilesQuery.data ?? []).filter((profile) => profile.active)
  }, [customerOffer?.targets, customerSelfService, profilesQuery.data])

  const documentConfigured = (workspace?.documentKnowledgeConfigured ?? dataset != null)
    || (customerSelfService && hasReviewedDocumentStorageInstall(customerOffer, installsQuery.data ?? []))
  const documentLive = !!selectedRelease && !!targetQuery.data?.documentDatasetIds.length
  const canAdmin = workspace?.access.canAdmin ?? false
  const canOperate = workspace?.access.canOperate ?? false

  const connectorQuery = useQuery({
    queryKey: ['document-connector-status', selectedDeploymentId, targetReference.targetProfileId, selectedReleaseId],
    queryFn: () => fetchDocumentConnectorStatus(targetReference),
    enabled: selectedDeploymentId.length > 0 && documentLive,
    retry: false,
  })
  const sourcesQuery = useQuery({
    queryKey: ['document-sources', selectedDeploymentId, targetReference.targetProfileId, selectedReleaseId],
    queryFn: () => fetchDocumentSources(targetReference),
    enabled: selectedDeploymentId.length > 0 && documentLive,
    refetchInterval: (query) => {
      const sources = query.state.data as DocumentSourceSummary[] | undefined
      return sources?.some((source) => ['INDEXING', 'REPLACING', 'DELETE_PENDING', 'DELETING'].includes(source.status))
        ? 3_000
        : false
    },
  })
  const retentionQuery = useQuery({
    queryKey: ['document-retention-status', selectedDeploymentId, targetReference.targetProfileId, selectedReleaseId],
    queryFn: () => fetchDocumentRetentionStatus(targetReference),
    enabled: selectedDeploymentId.length > 0 && documentLive,
    retry: false,
  })
  const detailQuery = useQuery({
    queryKey: ['document-source-detail', selectedDeploymentId, targetReference.targetProfileId, selectedReleaseId, selectedSourceId],
    queryFn: () => fetchDocumentSourceDetail(targetReference, selectedSourceId),
    enabled: selectedDeploymentId.length > 0 && selectedSourceId.length > 0 && documentLive,
    refetchInterval: 4_000,
  })

  const invalidateDocumentState = async (target: DeploymentTargetReference) => {
    const scope = [target.deploymentId, target.targetProfileId, target.releaseId]
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['document-sources', ...scope] }),
      queryClient.invalidateQueries({ queryKey: ['document-source-detail', ...scope] }),
      queryClient.invalidateQueries({ queryKey: ['document-connector-status', ...scope] }),
    ])
  }

  const saveBindingMutation = useMutation({
    mutationFn: async (input: {
      deploymentId: string; form: BindingForm; useTargetBindingSlot: boolean
      install: DeploymentMarketplaceInstallSummary | null
      operationKey: string; bindingSlotId?: string; selectedProfileId?: string
      customerScope?: CustomerDocumentStorageScope; authorityKey: string
    }) => {
      const { deploymentId, form, install, useTargetBindingSlot } = input
      if (input.customerScope || customerSelfService) {
        const scope = input.customerScope
        if (!scope) {
          throw new Error('The reviewed customer document-storage profile is unavailable.')
        }
        await requireFreshCustomerDocumentStorageScope(scope, { session: fetchPlatformAuthSession,
          offer: fetchCustomerSelfServiceOffer, deployments: fetchCustomerDeploymentPortfolio },
        () => bindingAuthority.current.key === input.authorityKey && bindingAuthority.current.target === scope.targetProfileId)
        const result = await bindCustomerDocumentStorage(scope.offerId, deploymentId, {
          expectedEntitlementRevision: scope.entitlementRevision,
          operationKey: input.operationKey,
          targetProfileId: scope.targetProfileId,
          bindingSlotId: scope.bindingSlotId,
          selectedProfileId: scope.selectedProfileId!,
          endpoint: form.endpoint,
          region: form.region,
          bucket: form.bucket,
          prefix: form.prefix,
          accessKey: form.accessKey,
          secretKey: form.secretKey,
          sessionToken: form.sessionToken || undefined,
          pathStyleAccess: form.pathStyleAccess,
          objectVersioningAvailable: form.objectVersioningAvailable,
        })
        if (!result.binding) throw new Error('The customer storage binding was not returned.')
        return result.binding
      }
      const payload: UpsertDocumentStorageBindingRequest = form.connectorType === 'MOUNTED_FOLDER'
        ? {
            targetProfileId: form.targetProfileId,
            connectorType: 'MOUNTED_FOLDER',
            mountedRoot: '/app/document-sources',
          }
        : {
            targetProfileId: form.targetProfileId,
            connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE',
            endpoint: form.endpoint,
            region: form.region,
            bucket: form.bucket,
            prefix: form.prefix,
            accessKey: form.accessKey,
            secretKey: form.secretKey,
            sessionToken: form.sessionToken || undefined,
            pathStyleAccess: form.pathStyleAccess,
            objectVersioningAvailable: form.objectVersioningAvailable,
            allowInsecureEndpoint: form.allowInsecureEndpoint,
          }
      const binding = await upsertDocumentStorageBinding(deploymentId, payload, {
        operationKey: input.operationKey,
        bindingSlotId: input.bindingSlotId,
        selectedProfileId: input.selectedProfileId,
      })
      const compositionBindingRef = useTargetBindingSlot ? 'slot:document-storage' : binding.bindingRef
      if (install && asRecord(install.config).documentStorageBindingRef !== compositionBindingRef) {
        await updateDeploymentMarketplaceInstall(deploymentId, install.id, {
          config: configWithBinding(install.config, compositionBindingRef),
        })
        await resolveDeploymentMarketplaceInstall(deploymentId, install.id)
      }
      return binding
    },
    onSuccess: async (_, input) => {
      if (currentSelection.current.target.deploymentId === input.deploymentId && bindingAuthority.current.key === input.authorityKey) {
        setBindingForm((current) => current === input.form
          ? { ...current, accessKey: '', secretKey: '', sessionToken: '' }
          : current)
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['document-storage-bindings', input.deploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-installs', input.deploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', input.deploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', input.deploymentId] }),
      ])
    },
  })

  const deleteBindingMutation = useMutation({
    mutationFn: async (input: {
      deploymentId: string
      bindingRef: string
      targetProfileId: string
      operationKey: string
      bindingSlotId?: string
      customerScope?: CustomerDocumentStorageScope; authorityKey: string
    }) => {
      if (input.customerScope || customerSelfService) {
        const scope = input.customerScope
        if (!scope) {
          throw new Error('The reviewed customer document-storage profile is unavailable.')
        }
        await requireFreshCustomerDocumentStorageScope(scope, { session: fetchPlatformAuthSession,
          offer: fetchCustomerSelfServiceOffer, deployments: fetchCustomerDeploymentPortfolio },
        () => bindingAuthority.current.key === input.authorityKey)
        await revokeCustomerDocumentStorage(scope.offerId, input.deploymentId, {
          expectedEntitlementRevision: scope.entitlementRevision,
          operationKey: input.operationKey,
          targetProfileId: input.targetProfileId,
          bindingSlotId: scope.bindingSlotId,
        })
        return
      }
      await deleteDocumentStorageBinding(input.deploymentId, input.bindingRef, {
        operationKey: input.operationKey, bindingSlotId: input.bindingSlotId,
      })
    },
    onSuccess: (_, input) => queryClient.invalidateQueries({ queryKey: ['document-storage-bindings', input.deploymentId] }),
  })
  const discoveryMutation = useMutation({
    mutationFn: (input: { target: DeploymentTargetReference; datasetId: string; cursor?: string }) => discoverDocumentSources(input.target, {
      datasetId: input.datasetId,
      cursor: input.cursor,
      limit: 50,
    }),
  })
  const registerMutation = useMutation({
    mutationFn: (input: {
      target: DeploymentTargetReference; datasetId: string; publicAudiencePackage: boolean
      selection: NonNullable<typeof registrationSelection>
      objectReference: string; sourceCategory: string; visibility: string
      operationKey: string
    }) => registerDocumentSource(input.target, {
      datasetId: input.datasetId,
      objectReference: input.objectReference,
      visibility: input.visibility,
      metadata: {
        sourceCategory: input.sourceCategory,
        ...(input.publicAudiencePackage ? { publicationStatus: 'PUBLIC_APPROVED' } : {}),
      },
    }, input.operationKey),
    onSuccess: async (source, input) => {
      if (sameTarget(input.target, currentSelection.current.target)
        && currentSelection.current.registrationSelection === input.selection) {
        setSelectedSourceId(source.sourceId)
        setRegistrationSelection(null)
        setRegistrationCategory('')
        setRegistrationVisibility('tenant')
        setRegistrationApproved(false)
      }
      await invalidateDocumentState(input.target)
    },
  })
  const previewMutation = useMutation({
    mutationFn: (input: { target: DeploymentTargetReference; sourceId: string }) => previewDocumentSource(input.target, input.sourceId),
  })
  const commandMutation = useMutation({
    mutationFn: ({ target, sourceId, command, operationKey }: { target: DeploymentTargetReference; sourceId: string; command: 'refresh' | 'index' | 'reconcile'; operationKey: string }) => {
      if (command === 'refresh') return refreshDocumentSource(target, sourceId, operationKey)
      if (command === 'index') return indexDocumentSource(target, sourceId, operationKey)
      return reconcileDocumentSource(target, sourceId, operationKey)
    },
    onSuccess: (_, input) => invalidateDocumentState(input.target),
  })
  const removeMutation = useMutation({
    mutationFn: (input: { target: DeploymentTargetReference; sourceId: string; selection: NonNullable<typeof removalSelection>; operationKey: string }) => removeDocumentIndex(input.target, input.sourceId, input.operationKey),
    onSuccess: async (_, input) => {
      if (sameTarget(input.target, currentSelection.current.target)
        && currentSelection.current.removalSelection === input.selection) {
        setRemovalSelection(null)
      }
      await invalidateDocumentState(input.target)
    },
  })
  const retrievalMutation = useMutation({
    mutationFn: (input: { target: DeploymentTargetReference; query: string }) => verifyDocumentRetrieval(input.target, { query: input.query, limit: 8 }),
  })
  const retentionMutation = useMutation({
    mutationFn: (input: { target: DeploymentTargetReference; operationKey: string }) => cleanupDocumentRetention(input.target, input.operationKey),
    onSuccess: (_, input) => queryClient.invalidateQueries({
      queryKey: ['document-retention-status', input.target.deploymentId, input.target.targetProfileId, input.target.releaseId],
    }),
  })

  const bindingSaveCurrent = saveBindingMutation.variables?.deploymentId === selectedDeploymentId
    && saveBindingMutation.variables.authorityKey === bindingAuthorityKey
    && saveBindingMutation.variables.form.targetProfileId === bindingForm.targetProfileId
  const discoveryCurrent = documentLive && sameTarget(discoveryMutation.variables?.target, targetReference)
  const discoveryCursor = discoveryCurrent ? discoveryMutation.data?.nextCursor : undefined
  const registrationCurrent = sameTarget(registerMutation.variables?.target, targetReference)
    && registerMutation.variables?.selection === registrationSelection
  const previewCurrent = documentLive && sameTarget(previewMutation.variables?.target, targetReference)
    && previewMutation.variables?.sourceId === selectedSourceId
  const commandCurrent = documentLive && sameTarget(commandMutation.variables?.target, targetReference)
    && commandMutation.variables?.sourceId === selectedSourceId
  const retrievalCurrent = documentLive && sameTarget(retrievalMutation.variables?.target, targetReference)
  const retentionCurrent = documentLive && sameTarget(retentionMutation.variables?.target, targetReference)

  if (!selectedDeploymentId) {
    return <Alert severity="info">Select a deployment to open Document Knowledge.</Alert>
  }
  if (draftQuery.isLoading || installsQuery.isLoading) {
    return <Stack alignItems="center" sx={{ py: 8 }}><CircularProgress /></Stack>
  }
  if (!documentConfigured) {
    return (
      <Alert severity="info">
        This deployment does not include the Document Knowledge DATA capability.
        {!customerSelfService ? <> <Link component={RouterLink} to="/marketplace">Open Marketplace</Link></> : null}
      </Alert>
    )
  }

  const selectedSource = detailQuery.data?.source
    ?? (sourcesQuery.data ?? []).find((source) => source.sourceId === selectedSourceId)
    ?? null

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800 }}>Document Knowledge</Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5 }}>
            Files remain in your storage. LoomAI stores derived indexing state and active retrieval evidence.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip label={documentLive ? 'Live capability' : 'Configuration pending release'} color={documentLive ? 'success' : 'warning'} />
          <Chip label={datasetId} variant="outlined" />
        </Stack>
      </Stack>

      <TextField select fullWidth size="small" label="Target release" value={selectedReleaseId} disabled={lockReleaseSelection}
        onChange={(event) => {
          if (lockReleaseSelection) return
          setSelectedReleaseId(event.target.value)
          setSelectedSourceId('')
          setRegistrationSelection(null)
          setRemovalSelection(null)
        }}>
        <MenuItem value="">Select a provisioned target release</MenuItem>
        {availableReleases.map((release) => (
          <MenuItem key={release.id} value={release.id}>
            {release.targetProfileId} · {release.id} · {release.status}
          </MenuItem>
        ))}
      </TextField>
      {targetQuery.error ? <Alert severity="error">{errorText(targetQuery.error)}</Alert> : null}

      {!documentLive ? (
        <Alert severity="warning">
          Select an explicitly provisioned target release before using document operations.
        </Alert>
      ) : null}

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2.5}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>Source binding</Typography>
              <Typography variant="body2" color="text.secondary">Customer-owned storage, read-only connector authority.</Typography>
            </Box>
            {customerSelfService ? (
              <Chip icon={<CloudOutlinedIcon />} label="Customer-owned S3-compatible storage" color="primary" variant="outlined" />
            ) : (
              <ToggleButtonGroup
                exclusive
                size="small"
                value={bindingForm.connectorType}
                onChange={(_, value) => value && setBindingForm((current) => ({ ...current, connectorType: value }))}
                disabled={!canAdmin}
              >
                <ToggleButton value="S3_COMPATIBLE_OBJECT_STORAGE"><CloudOutlinedIcon sx={{ mr: 1 }} />S3 compatible</ToggleButton>
                <ToggleButton value="MOUNTED_FOLDER"><FolderOpenRoundedIcon sx={{ mr: 1 }} />Mounted demo</ToggleButton>
              </ToggleButtonGroup>
            )}
          </Stack>
          {!customerSelfService ? (
            <FormControlLabel
              control={<Checkbox checked={useTargetBindingSlot} onChange={(event) => setUseTargetBindingSlot(event.target.checked)}
                disabled={!canAdmin || saveBindingMutation.isPending} />}
              label="Use target-scoped document storage slot"
            />
          ) : null}

          {customerSelfService && customerStorageSlot ? (
            <TextField select fullWidth size="small" label="Approved storage profile" value={customerStorageProfileId}
              onChange={(event) => setCustomerStorageProfileId(event.target.value)}>
              {customerStorageSlot.supportedProfileIds.map((profileId) => (
                <MenuItem key={profileId} value={profileId}>{profileId}</MenuItem>
              ))}
            </TextField>
          ) : null}
          {customerSelfService && !customerStorageSlot && !customerCatalogueQuery.isLoading ? (
            <Alert severity="error">This reviewed offer does not allow a customer-owned document-storage binding.</Alert>
          ) : null}
          {bindingForm.connectorType === 'MOUNTED_FOLDER' ? (
            <Alert severity="warning">Demo / small data only. The read-only mount path is fixed at /app/document-sources.</Alert>
          ) : null}

          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <TextField
                select fullWidth size="small" label="Target profile"
                value={bindingForm.targetProfileId}
                onChange={(event) => setBindingForm((current) => ({ ...current, targetProfileId: event.target.value }))}
                disabled={!canAdmin}
              >
                {matchingProfiles.map((profile) => (
                  <MenuItem key={profile.id} value={profile.id}>{profile.name} · {profile.providerType}</MenuItem>
                ))}
              </TextField>
            </Grid>
            {bindingForm.connectorType === 'S3_COMPATIBLE_OBJECT_STORAGE' ? (
              <>
                <Grid item xs={12} md={8}>
                  <TextField fullWidth size="small" label="Endpoint origin" placeholder="https://s3.example.com"
                    value={bindingForm.endpoint} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, endpoint: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" label="Region" value={bindingForm.region} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, region: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" label="Bucket" value={bindingForm.bucket} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, bucket: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" label="Allowed prefix" value={bindingForm.prefix} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, prefix: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" label="Access key" value={bindingForm.accessKey} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, accessKey: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" type="password" label="Secret key" value={bindingForm.secretKey} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, secretKey: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" type="password" label="Session token (optional)" value={bindingForm.sessionToken} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, sessionToken: event.target.value }))} />
                </Grid>
                <Grid item xs={12} sx={{ display: detailSection === 'configuration' ? 'none' : undefined }}>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                    <FormControlLabel control={<Checkbox checked={bindingForm.pathStyleAccess} onChange={(event) => setBindingForm((current) => ({ ...current, pathStyleAccess: event.target.checked }))} />} label="Path-style access" />
                    <FormControlLabel control={<Checkbox checked={bindingForm.objectVersioningAvailable} onChange={(event) => setBindingForm((current) => ({ ...current, objectVersioningAvailable: event.target.checked }))} />} label="Object versioning available" />
                    <FormControlLabel control={<Checkbox checked={bindingForm.allowInsecureEndpoint} onChange={(event) => setBindingForm((current) => ({ ...current, allowInsecureEndpoint: event.target.checked }))} />} label="Allow HTTP outside production" />
                  </Stack>
                </Grid>
              </>
            ) : null}
          </Grid>

          {bindingSaveCurrent && saveBindingMutation.isError ? <Alert severity="error">{errorText(saveBindingMutation.error)}</Alert> : null}
          {bindingSaveCurrent && saveBindingMutation.isSuccess ? <Alert severity="success">Storage binding saved.</Alert> : null}
          {!install ? <Alert severity="warning">The Document Knowledge DATA install is missing; binding configuration cannot be compiled into the deployment draft.</Alert> : null}
          <Stack direction="row" justifyContent="flex-end">
            <Button
              variant="contained" startIcon={<SaveRoundedIcon />}
              disabled={!canAdmin || !bindingForm.targetProfileId || saveBindingMutation.isPending || (customerSelfService && !customerBindScope)}
              onClick={() => saveBindingMutation.mutate({
                deploymentId: selectedDeploymentId, form: bindingForm, install, useTargetBindingSlot,
                operationKey: crypto.randomUUID(),
                customerScope: customerBindScope ?? undefined, authorityKey: bindingAuthorityKey,
                bindingSlotId: useTargetBindingSlot ? (customerStorageSlot?.id ?? 'document-storage') : undefined,
                selectedProfileId: customerSelfService ? customerStorageProfileId : undefined,
              })}
            >
              Save binding
            </Button>
          </Stack>

          {(bindingsQuery.data ?? []).length > 0 ? <Divider /> : null}
          {(bindingsQuery.data ?? []).map((binding) => (
            <Stack key={binding.bindingRef} direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} justifyContent="space-between">
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={binding.connectorType.replace(/_/g, ' ')} variant="outlined" />
                <Chip label={binding.targetProfileId} variant="outlined" />
                <Chip label={binding.credentialsPresent ? 'Credentials bound' : 'Credentials missing'} color={binding.credentialsPresent ? 'success' : 'error'} />
                <Chip label={binding.status} color={statusColor(binding.status)} />
              </Stack>
              <Tooltip title="Remove binding reference and managed credentials">
                <span>
                  <IconButton
                    aria-label="Remove document storage binding"
                    disabled={!canAdmin || deleteBindingMutation.isPending
                      || (customerSelfService && !currentCustomerStorageScope(binding.targetProfileId, 'BINDING_REVOKE'))}
                    onClick={() => {
                      if (globalThis.confirm('Remove this binding and its managed credentials? Customer source objects will not be changed.')) {
                        deleteBindingMutation.mutate({
                          deploymentId: selectedDeploymentId, bindingRef: binding.bindingRef,
                          targetProfileId: binding.targetProfileId,
                          operationKey: crypto.randomUUID(),
                          customerScope: currentCustomerStorageScope(binding.targetProfileId, 'BINDING_REVOKE') ?? undefined,
                          authorityKey: bindingAuthorityKey,
                          bindingSlotId: useTargetBindingSlot ? (customerStorageSlot?.id ?? 'document-storage') : undefined,
                        })
                      }
                    }}
                  ><DeleteOutlineRoundedIcon /></IconButton>
                </span>
              </Tooltip>
            </Stack>
          ))}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2.5}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>Connector and discovery</Typography>
              <Typography variant="body2" color="text.secondary">Eligible .txt and configured .json objects inside the approved scope.</Typography>
            </Box>
            <Stack direction="row" spacing={1}>
              <Chip
                label={connectorQuery.data?.ready ? 'Connector ready' : documentLive ? 'Connector unavailable' : 'Not live'}
                color={connectorQuery.data?.ready ? 'success' : 'warning'}
              />
              {connectorQuery.data?.connectorType ? <Chip label={connectorQuery.data.connectorType.replace(/_/g, ' ')} variant="outlined" /> : null}
              <Tooltip title="Refresh connector status"><span><IconButton disabled={!documentLive} onClick={() => connectorQuery.refetch()}><RefreshRoundedIcon /></IconButton></span></Tooltip>
            </Stack>
          </Stack>
          {connectorQuery.isError ? <Alert severity="error">{errorText(connectorQuery.error)}</Alert> : null}
          {connectorQuery.data?.errorCode ? <Alert severity="error">{connectorQuery.data.errorCode}</Alert> : null}
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined" startIcon={<SearchRoundedIcon />}
              disabled={!documentLive || !connectorQuery.data?.ready || discoveryMutation.isPending}
              onClick={() => discoveryMutation.mutate({ target: targetReference, datasetId })}
            >Discover sources</Button>
            {discoveryCursor ? (
              <Button variant="text" disabled={discoveryMutation.isPending} onClick={() => discoveryMutation.mutate({ target: targetReference, datasetId, cursor: discoveryCursor })}>Next page</Button>
            ) : null}
          </Stack>
          {discoveryCurrent && discoveryMutation.isError ? <Alert severity="error">{errorText(discoveryMutation.error)}</Alert> : null}
          {discoveryCurrent && discoveryMutation.data ? (
            <TableContainer>
              <Table size="small">
                <TableHead><TableRow><TableCell>Source</TableCell><TableCell>Type</TableCell><TableCell>Size</TableCell><TableCell>Modified</TableCell><TableCell>Status</TableCell><TableCell align="right">Action</TableCell></TableRow></TableHead>
                <TableBody>
                  {discoveryMutation.data.sources.map((source) => (
                    <TableRow key={source.objectReference}>
                      <TableCell>{source.displayName}</TableCell>
                      <TableCell>{source.mediaType}</TableCell>
                      <TableCell>{formatBytes(source.contentLength)}</TableCell>
                      <TableCell>{formatTime(source.lastModified)}</TableCell>
                      <TableCell><Chip size="small" label={source.registeredSourceId ? 'Registered' : 'Eligible'} color={source.registeredSourceId ? 'success' : 'default'} /></TableCell>
                      <TableCell align="right">
                        <Button
                          size="small" startIcon={<DescriptionRoundedIcon />}
                          disabled={!!source.registeredSourceId || !canOperate || registerMutation.isPending || !documentContractReady}
                          onClick={() => {
                            setRegistrationSelection({ target: targetReference, source })
                            setRegistrationCategory('')
                            setRegistrationVisibility('tenant')
                            setRegistrationApproved(false)
                          }}
                        >Register</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : null}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2.5}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>Registered sources</Typography>
              <Typography variant="body2" color="text.secondary">Queue acceptance and completed vector indexing remain distinct states.</Typography>
            </Box>
            <Tooltip title="Refresh sources"><span><IconButton disabled={!documentLive} onClick={() => sourcesQuery.refetch()}><RefreshRoundedIcon /></IconButton></span></Tooltip>
          </Stack>
          {sourcesQuery.isError ? <Alert severity="error">{errorText(sourcesQuery.error)}</Alert> : null}
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow><TableCell>Source</TableCell><TableCell>Status</TableCell><TableCell>Type</TableCell><TableCell>Size</TableCell><TableCell>Active</TableCell><TableCell>Candidate</TableCell><TableCell>Updated</TableCell></TableRow></TableHead>
              <TableBody>
                {(sourcesQuery.data ?? []).map((source) => (
                  <TableRow
                    hover key={source.sourceId} selected={source.sourceId === selectedSourceId}
                    onClick={() => setSelectedSourceId(source.sourceId)}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell>{source.displayName}</TableCell>
                    <TableCell><Chip size="small" label={source.status} color={statusColor(source.status)} /></TableCell>
                    <TableCell>{source.mediaType}</TableCell>
                    <TableCell>{formatBytes(source.contentLength)}</TableCell>
                    <TableCell>{source.activeVersion ?? '—'}</TableCell>
                    <TableCell>{source.candidateVersion}</TableCell>
                    <TableCell>{formatTime(source.updatedAt)}</TableCell>
                  </TableRow>
                ))}
                {(sourcesQuery.data ?? []).length === 0 ? <TableRow><TableCell colSpan={7}>No registered document sources.</TableCell></TableRow> : null}
              </TableBody>
            </Table>
          </TableContainer>

          {selectedSource ? (
            <>
              <Divider />
              <Stack direction={{ xs: 'column', lg: 'row' }} justifyContent="space-between" spacing={2}>
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>{selectedSource.displayName}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
                    <Chip size="small" label={selectedSource.status} color={statusColor(selectedSource.status)} />
                    <Chip size="small" label={`Active v${selectedSource.activeVersion ?? '—'}`} variant="outlined" />
                    <Chip size="small" label={`Candidate v${selectedSource.candidateVersion}`} variant="outlined" />
                    <Chip size="small" label={`Revision ${selectedSource.providerRevisionFingerprint.slice(0, 12)}`} variant="outlined" />
                  </Stack>
                </Box>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Button size="small" variant="outlined" startIcon={<PreviewRoundedIcon />} disabled={!documentLive || previewMutation.isPending} onClick={() => previewMutation.mutate({ target: targetReference, sourceId: selectedSource.sourceId })}>Preview</Button>
                  <Button size="small" variant="contained" startIcon={<PlayArrowRoundedIcon />} disabled={!documentLive || !canOperate || commandMutation.isPending} onClick={() => commandMutation.mutate({ target: targetReference, sourceId: selectedSource.sourceId, command: 'index', operationKey: crypto.randomUUID() })}>Index</Button>
                  <Button size="small" variant="outlined" startIcon={<SyncRoundedIcon />} disabled={!documentLive || !canOperate || commandMutation.isPending} onClick={() => commandMutation.mutate({ target: targetReference, sourceId: selectedSource.sourceId, command: 'refresh', operationKey: crypto.randomUUID() })}>Refresh revision</Button>
                  <Button size="small" variant="outlined" startIcon={<FactCheckRoundedIcon />} disabled={!documentLive || !canOperate || commandMutation.isPending} onClick={() => commandMutation.mutate({ target: targetReference, sourceId: selectedSource.sourceId, command: 'reconcile', operationKey: crypto.randomUUID() })}>Reconcile</Button>
                  <Button size="small" color="error" variant="outlined" startIcon={<DeleteOutlineRoundedIcon />} disabled={!documentLive || !canOperate || removeMutation.isPending} onClick={() => setRemovalSelection({ target: targetReference, source: selectedSource })}>Remove index</Button>
                </Stack>
              </Stack>
              {selectedSource.failureCode ? <Alert severity="error">{selectedSource.failureCode}: {selectedSource.failureMessage}</Alert> : null}
              {commandCurrent && commandMutation.isError ? <Alert severity="error">{errorText(commandMutation.error)}</Alert> : null}
              {commandCurrent && commandMutation.data ? <Alert severity="success">Operation: {commandMutation.data.outcome}</Alert> : null}

              {previewCurrent && previewMutation.isError ? <Alert severity="error">{errorText(previewMutation.error)}</Alert> : null}
              {previewCurrent && previewMutation.data ? (
                <Stack spacing={1.5}>
                  <Typography variant="subtitle2">Bounded preview · {previewMutation.data.chunkCount} chunks · {previewMutation.data.totalContentLength} characters</Typography>
                  {previewMutation.data.chunks.map((chunk) => (
                    <Box key={chunk.chunkId} sx={{ borderLeft: '3px solid', borderColor: 'primary.main', pl: 2, py: 0.5 }}>
                      <Typography variant="caption" color="text.secondary">Chunk {chunk.chunkIndex + 1}/{chunk.chunkCount}</Typography>
                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{chunk.contentPreview}</Typography>
                    </Box>
                  ))}
                </Stack>
              ) : null}

              {detailSection ? (detailQuery.data?.manifests ?? []).filter(manifest => manifest.failureCode).map(manifest => <Alert key={manifest.manifestId} severity="error">Document version {manifest.sourceVersion}: {manifest.failureCode}{manifest.failureMessage ? ` / ${manifest.failureMessage}` : ''}</Alert>) : null}
              {(detailQuery.data?.manifests ?? []).length > 0 ? <CustomerAdvancedSection>
                <TableContainer>
                  <Table size="small">
                    <TableHead><TableRow><TableCell>Version</TableCell><TableCell>State</TableCell><TableCell>Chunks</TableCell><TableCell>Index work</TableCell><TableCell>Delete work</TableCell><TableCell>Failure</TableCell></TableRow></TableHead>
                    <TableBody>
                      {(detailQuery.data?.manifests ?? []).map((manifest) => (
                        <TableRow key={manifest.manifestId}>
                          <TableCell>v{manifest.sourceVersion}</TableCell>
                          <TableCell><Chip size="small" label={manifest.state} color={statusColor(manifest.state)} /></TableCell>
                          <TableCell>{manifest.chunkCount}</TableCell>
                          <TableCell>{manifest.work.filter((work) => work.operation === 'INDEX' && work.successful).length}/{manifest.acceptedIndexWorkCount}</TableCell>
                          <TableCell>{manifest.work.filter((work) => work.operation === 'DELETE' && work.successful).length}/{manifest.acceptedDeleteWorkCount}</TableCell>
                          <TableCell>{manifest.failureCode ?? '—'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CustomerAdvancedSection> : null}
            </>
          ) : null}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 750 }}>Retrieval proof</Typography>
            <Typography variant="body2" color="text.secondary">Tenant-scoped active source, version, and chunk evidence.</Typography>
          </Box>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
            <TextField fullWidth size="small" label="Proof query" value={retrievalQuery} onChange={(event) => setRetrievalQuery(event.target.value)} />
            <Button variant="contained" startIcon={<SearchRoundedIcon />} disabled={!documentLive || !retrievalQuery.trim() || retrievalMutation.isPending} onClick={() => retrievalMutation.mutate({ target: targetReference, query: retrievalQuery })}>Verify</Button>
          </Stack>
          {retrievalCurrent && retrievalMutation.isError ? <Alert severity="error">{errorText(retrievalMutation.error)}</Alert> : null}
          {retrievalCurrent && retrievalMutation.data ? (
            <Stack spacing={1.5}>
              <Alert severity={retrievalMutation.data.evidenceCount > 0 ? 'success' : 'warning'}>
                {retrievalMutation.data.evidenceCount} active evidence result{retrievalMutation.data.evidenceCount === 1 ? '' : 's'} in {retrievalMutation.data.processingTimeMs} ms.
              </Alert>
              {retrievalMutation.data.evidence.map((item) => (
                <Box key={item.entityId} sx={{ borderLeft: '3px solid', borderColor: 'success.main', pl: 2 }}>
                  <Typography variant="subtitle2">{item.sourceName} · v{item.sourceVersion} · chunk {item.chunkIndex + 1}</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{item.content}</Typography>
                </Box>
              ))}
            </Stack>
          ) : null}
        </Stack>
      </Paper>

      {detailSection === 'configuration' && (retentionQuery.isError || (retentionCurrent && retentionMutation.isError)) ? <Alert severity="error">{errorText(retentionQuery.error || retentionMutation.error)}</Alert> : null}
      <Paper variant="outlined" sx={{ p: 2.5, display: detailSection === 'configuration' ? 'none' : undefined }}>
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1.5}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>Runtime evidence retention</Typography>
              <Typography variant="body2" color="text.secondary">
                Terminal LoomAI lifecycle evidence only. Customer source files are never removed.
              </Typography>
            </Box>
            <Button
              variant="outlined"
              startIcon={<DeleteSweepRoundedIcon />}
              disabled={!documentLive || !canOperate || retentionMutation.isPending}
              onClick={() => retentionMutation.mutate({ target: targetReference, operationKey: crypto.randomUUID() })}
            >
              Run cleanup
            </Button>
          </Stack>
          {retentionQuery.isError ? <Alert severity="error">{errorText(retentionQuery.error)}</Alert> : null}
          {retentionCurrent && retentionMutation.isError ? <Alert severity="error">{errorText(retentionMutation.error)}</Alert> : null}
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Chip label={`Manifest evidence ${retentionQuery.data?.evidenceRetention ?? '—'}`} variant="outlined" />
            <Chip label={`Idempotency evidence ${retentionQuery.data?.commandRetention ?? '—'}`} variant="outlined" />
            <Chip label={`Batch ${retentionQuery.data?.batchSize ?? '—'}`} variant="outlined" />
            <Chip
              label={`Last sweep ${formatTime(retentionQuery.data?.lastCleanup?.completedAt)}`}
              color={retentionQuery.data?.lastCleanup ? 'success' : 'default'}
            />
          </Stack>
          {retentionCurrent && retentionMutation.data ? (
            <Alert severity="success">
              Removed {retentionMutation.data.manifestsDeleted} expired manifests, {retentionMutation.data.workDeleted} work records, and {retentionMutation.data.commandsDeleted} command records. Customer files changed: no.
            </Alert>
          ) : null}
        </Stack>
      </Paper>

      <Dialog
        open={registrationSource != null}
        onClose={() => !registerMutation.isPending && setRegistrationSelection(null)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Register approved document source</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity={publicAudiencePackage ? 'warning' : 'info'}>
              {publicAudiencePackage
                ? 'This package can ground anonymous answers. Register only material approved for public visitors.'
                : 'Confirm the source audience and category before creating derived indexing evidence.'}
            </Alert>
            {!documentContractReady ? <Alert severity="warning">Document audience contract is unavailable. Registration is disabled until it can be verified.</Alert> : null}
            <TextField
              fullWidth
              size="small"
              label="Source"
              value={registrationSource?.displayName ?? ''}
              disabled
            />
            <TextField
              fullWidth
              required
              size="small"
              label="Source category"
              placeholder="policy, operations, support"
              value={registrationCategory}
              onChange={(event) => setRegistrationCategory(event.target.value)}
              inputProps={{ maxLength: 64 }}
              helperText="A short stable label included with retrieval evidence."
            />
            <TextField
              select
              fullWidth
              size="small"
              label="Visibility"
              value={registrationVisibility}
              disabled={publicAudiencePackage}
              onChange={(event) => setRegistrationVisibility(event.target.value)}
            >
              <MenuItem value="tenant">Tenant</MenuItem>
              <MenuItem value="private">Private</MenuItem>
              <MenuItem value="internal">Internal</MenuItem>
            </TextField>
            <FormControlLabel
              control={(
                <Checkbox
                  checked={registrationApproved}
                  onChange={(event) => setRegistrationApproved(event.target.checked)}
                />
              )}
              label={publicAudiencePackage
                ? 'I confirm this file is approved for anonymous public answers.'
                : 'I confirm this source and audience are approved for indexing.'}
            />
            {registrationCurrent && registerMutation.isError ? <Alert severity="error">{errorText(registerMutation.error)}</Alert> : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button disabled={registerMutation.isPending} onClick={() => setRegistrationSelection(null)}>Cancel</Button>
          <Button
            variant="contained"
            startIcon={<DescriptionRoundedIcon />}
            disabled={
              registerMutation.isPending
              || !documentContractReady
              || !registrationSource
              || !registrationCategory.trim()
              || !registrationApproved
            }
            onClick={() => documentContractReady && registrationSource && registrationSelection && registerMutation.mutate({
              target: registrationSelection.target,
              datasetId,
              publicAudiencePackage,
              selection: registrationSelection,
              objectReference: registrationSource.objectReference,
              sourceCategory: registrationCategory.trim(),
              visibility: publicAudiencePackage ? 'tenant' : registrationVisibility,
              operationKey: crypto.randomUUID(),
            })}
          >
            Register source
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={removeSource != null} onClose={() => setRemovalSelection(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Remove indexed document evidence?</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mt: 1 }}>
            This deletes the exact indexed chunks and LoomAI registration for {removeSource?.displayName}. The customer source object is not deleted or changed.
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRemovalSelection(null)}>Cancel</Button>
          <Button color="error" variant="contained" startIcon={<DeleteOutlineRoundedIcon />} disabled={removeMutation.isPending} onClick={() => removeSource && removalSelection && removeMutation.mutate({ target: removalSelection.target, sourceId: removeSource.sourceId, selection: removalSelection, operationKey: crypto.randomUUID() })}>Remove index</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
