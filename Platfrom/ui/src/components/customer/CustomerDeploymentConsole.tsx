import AddRoundedIcon from '@mui/icons-material/AddRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import CloudDoneRoundedIcon from '@mui/icons-material/CloudDoneRounded'
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded'
import DescriptionRoundedIcon from '@mui/icons-material/DescriptionRounded'
import ForumRoundedIcon from '@mui/icons-material/ForumRounded'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import PublishedWithChangesRoundedIcon from '@mui/icons-material/PublishedWithChangesRounded'
import RocketLaunchRoundedIcon from '@mui/icons-material/RocketLaunchRounded'
import ScheduleRoundedIcon from '@mui/icons-material/ScheduleRounded'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Checkbox,
  CircularProgress,
  Divider,
  Grid,
  FormControlLabel,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Step,
  StepLabel,
  Stepper,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import {
  acceptCustomerSelfServiceInvitation,
  activateCustomerDeploymentStagingConsumer,
  createCustomerDeploymentStagingConsumer,
  configureCustomerDeploymentStagingConsumer,
  activateDeploymentPracticalProductionConsumer,
  applyCustomerDeploymentStagingVersion,
  bootstrapCustomerDeployment,
  fetchCustomerDeploymentPortfolio,
  fetchCustomerDeploymentAdmissions,
  fetchCustomerDeploymentGoLiveReadiness,
  fetchCustomerSelfServiceCatalogue,
  fetchCustomerSelfServiceOffer,
  fetchPlatformAuthSession,
  fetchCustomerSelfServiceInvitations,
  fetchDeploymentReleases,
  fetchDeploymentVersions,
  fetchDeploymentDraft,
  fetchPlatformConsumers,
  fetchAIWorkspaceInstallations,
  fetchPlatformUserPreferences,
  fetchCustomerTargetRetirements,
  planDeploymentPracticalPromotion,
  probeCustomerOfferTargetPlacement,
  publishCustomerDeploymentVersion,
  planCustomerPreparedFailureRecovery,
  recoverCustomerPreparedFailure,
  planCustomerFailedApplyRetry,
  retryCustomerFailedApply,
  planCustomerPostProvisionResume,
  resumeCustomerPostProvisioning,
  requestDeploymentPracticalProductionApply,
  reserveCustomerDeploymentAdmission,
  rollbackDeploymentPracticalProductionConsumer,
  extendCustomerDeploymentStaging,
  updatePlatformUserPreferences,
  type CustomerAdmissionReservationSummary,
  type CustomerDeploymentSummary,
  type CustomerGoLiveReadinessItem,
  type CustomerOfferSummary,
  type CustomerTargetRetirementSummary,
  type CustomerStagingConsumerConfigurationRequest,
  type CustomerPreparedFailureRecoveryPlanRequest,
  type CustomerPreparedFailureRecoveryPlan,
  type CustomerPreparedFailureRecoverySummary,
  type CustomerFailedApplyRetryPlanRequest,
  type CustomerFailedApplyRetryPlan,
  type CustomerFailedApplyRetrySummary,
  type CustomerPostProvisionResumePlanRequest,
  type CustomerPostProvisionResumePlan,
  type CustomerPostProvisionResumeSummary,
  type DeploymentPracticalPromotionSummary,
  type DeploymentReleaseSummary,
  type DeploymentVersionSummary,
  type PlatformAuthSessionSummary,
  type PlatformConsumerSummary,
} from '../../api/platformApi'
import { canApplyReviewedVersion, CustomerAdmissionCommands } from './customerAdmissionCommands'
import { KnownNoEffectSettlementPanel } from './KnownNoEffectSettlementPanel'
import { createDurableCommandHolds } from './durableCommandHolds'
import { createPostProvisionResumeHolds } from './postProvisionResumeHolds'
import { selectableCustomerAssignments, canReviewCustomerAssignment, canConfigureExistingCustomerAssignment, customerAssignmentDraftExpectations,
  customerAssignmentConfigurationRequest, configureExistingCustomerAssignment, assignmentConfigurationSnapshot,
  prepareKnownAssignmentReconciliation, confirmKnownAssignmentReconciliation,
  customerAssignmentReviewedWorkspaceProfiles,
  type CustomerAssignmentConfigurationScope, type CustomerAssignmentWorkspaceProfileChoices, type KnownAssignmentReconciliation } from './customerDeploymentAssignments'
import { createAssignmentConfigurationIntents, canStartAssignmentIntent, type AssignmentConfigurationIntent } from './customerAssignmentConfigurationIntents'
import { useDeploymentWorkspace } from '../../workspace/DeploymentWorkspaceContext'
import { useScopedOneTimeValue } from '../../hooks/useScopedOneTimeValue'
import { CustomerAiStateStorageBinding } from './CustomerAiStateStorageBinding'
import { CustomerExternalIntegrationBindings } from './CustomerExternalIntegrationBindings'
import { aiStateStorageLabels, aiStateStorageModes, customerStorageBootstrapRequest,
  publishStoredAiStateStorage, storedAiStateStorageMode } from './customerAiStateStorage'
import { CustomerAdvancedSection, useCustomerEditorSection } from './CustomerDetailSections'

type Notice = { severity: 'success' | 'info' | 'warning' | 'error'; message: string }
type ConfigureAssignmentInput = { scope: CustomerAssignmentConfigurationScope
  request: CustomerStagingConsumerConfigurationRequest; token: symbol }

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : 'The operation failed.'
}

function timestamp(value: string | null | undefined): string {
  if (!value) return 'Not recorded'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? 'Unknown' : parsed.toLocaleString()
}

function releaseReady(release: DeploymentReleaseSummary | null | undefined): boolean {
  return release?.status === 'APPLIED_VERIFIED'
    && release.verificationStatus === 'PASSED'
    && release.provisioningStatus === 'ACTIVE'
}

function targetSelectable(readiness: string): boolean {
  return ['READY', 'PLACEMENT_VERIFIED_ADMISSION_REQUIRED', 'PLACEMENT_RECHECK_REQUIRED'].includes(readiness)
}

function lifecycleStep(
  deployment: CustomerDeploymentSummary,
  versions: DeploymentVersionSummary[],
  releases: DeploymentReleaseSummary[],
  consumers: PlatformConsumerSummary[],
  stagingTargetProfileId: string,
  productionTargetProfileIds: Set<string>,
): number {
  const staging = releases.find((release) => release.targetProfileId === stagingTargetProfileId)
  const production = releases.find((release) => release.targetProfileId != null
    && productionTargetProfileIds.has(release.targetProfileId))
  const boundProduction = consumers.some((consumer) => consumer.boundDeploymentId === deployment.deploymentId
    && consumer.boundReleaseId === production?.id)
  if (boundProduction) return 5
  if (releaseReady(production)) return 4
  if (releaseReady(staging)) return 3
  if (releases.length > 0) return 2
  if (versions.length > 0) return 1
  return 0
}

const journeySteps = [
  { id: 'template', label: 'Template' },
  { id: 'configure', label: 'Configure' },
  { id: 'staging', label: 'Customer staging' },
  { id: 'production', label: 'Production' },
  { id: 'retention', label: 'Staging retention' },
] as const

export function CustomerDeploymentConsole({ session, guided = false }: { session: PlatformAuthSessionSummary; guided?: boolean }) {
  const queryClient = useQueryClient()
  const detailSection = useCustomerEditorSection()
  const location = useLocation()
  const navigate = useNavigate()
  const journeyParams = new URLSearchParams(location.search)
  const requestedOfferId = journeyParams.get('offerId') ?? ''
  const requestedStep = journeyParams.get('step')
  const step = journeySteps.find(item => item.id === requestedStep)?.id ?? (journeyParams.has('deploymentId') ? 'configure' : 'template')
  const selectStep = (id: string) => { const next = new URLSearchParams(location.search); next.set('step', id); navigate({ pathname: location.pathname, search: next.toString() }) }
  const showStep = (id: string) => !guided || step === id
  const { requestedDeploymentId, setSelectedDeploymentId } = useDeploymentWorkspace()
  const operationKeys = useRef(new Map<string, { identity: string; key: string }>())
  const admissionCommands = useRef(new CustomerAdmissionCommands({
    probe: probeCustomerOfferTargetPlacement,
    list: fetchCustomerDeploymentAdmissions,
    reserve: reserveCustomerDeploymentAdmission,
  }))
  const operationKey = (scope: string, payload: unknown) => {
    const identity = JSON.stringify(payload)
    const existing = operationKeys.current.get(scope)
    if (existing?.identity === identity) return existing.key
    const key = crypto.randomUUID()
    operationKeys.current.set(scope, { identity, key })
    return key
  }
  const completeOperation = (...scopes: string[]) => scopes.forEach((scope) => {
    const key = operationKeys.current.get(scope)?.key
    if (key) admissionCommands.current.complete(key)
    operationKeys.current.delete(scope)
  })
  const customerId = session.customerId ?? ''
  const [selectedOfferId, setSelectedOfferId] = useState(requestedOfferId)
  const [selectedTargetProfileId, setSelectedTargetProfileId] = useState('')
  const [selectedProductionTargetProfileId, setSelectedProductionTargetProfileId] = useState('')
  const [deploymentName, setDeploymentName] = useState('')
  const [storageChoice, setStorageChoice] = useState<{ scope: string; mode: string } | null>(null)
  const [consumerId, setConsumerId] = useState('')
  const [consumerName, setConsumerName] = useState('')
  const [selectedConsumerId, setSelectedConsumerId] = useState('')
  const [creatingNewAssignment, setCreatingNewAssignment] = useState(false)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [placementEvidence, setPlacementEvidence] = useState<string | null>(null)
  const [admission, setAdmission] = useState<CustomerAdmissionReservationSummary | null>(null)
  const [promotion, setPromotion] = useState<DeploymentPracticalPromotionSummary | null>(null)
  const [reviewedCandidateScope, setReviewedCandidateScope] = useState('')
  const configurationIntents = useRef(createAssignmentConfigurationIntents())
  const configurationBusy = useRef(false)
  const [reviewedRejection, setReviewedRejection] = useState<{ token: symbol; prepared: KnownAssignmentReconciliation } | null>(null)
  const [rejectionConfirmed, setRejectionConfirmed] = useState<{ token: symbol; checked: boolean } | null>(null)

  const catalogueQuery = useQuery({
    queryKey: ['customer-self-service-catalogue', customerId],
    queryFn: fetchCustomerSelfServiceCatalogue,
    enabled: Boolean(customerId),
  })
  const invitationsQuery = useQuery({
    queryKey: ['customer-self-service-invitations', customerId],
    queryFn: fetchCustomerSelfServiceInvitations,
    enabled: Boolean(customerId),
  })
  const portfolioQuery = useQuery({
    queryKey: ['customer-deployment-portfolio', customerId],
    queryFn: fetchCustomerDeploymentPortfolio,
    enabled: Boolean(customerId),
  })
  const consumersQuery = useQuery({
    queryKey: ['platform-consumers', customerId],
    queryFn: () => fetchPlatformConsumers(customerId),
    enabled: Boolean(customerId),
  })
  const preferencesQuery = useQuery({
    queryKey: ['platform-preferences'],
    queryFn: fetchPlatformUserPreferences,
    enabled: Boolean(customerId),
  })
  const saveSelection = useMutation({
    mutationFn: updatePlatformUserPreferences,
    scope: { id: 'customer-workspace-selection' },
    onSuccess: (preferences) => queryClient.setQueryData(['platform-preferences'], preferences),
  })
  const persistedSelection = useRef('')

  const offers = catalogueQuery.data ?? []
  const deployments = portfolioQuery.data ?? []
  const consumers = consumersQuery.data ?? []
  const selectedDeployment = deployments.find((deployment) => deployment.deploymentId === requestedDeploymentId) ?? null
  const selectedDeploymentId = selectedDeployment?.deploymentId ?? ''
  const creationOffer = offers.find((offer) => offer.id === selectedOfferId) ?? null
  const selectedOffer = selectedDeployment
    ? offers.find((offer) => offer.id === selectedDeployment.offerId) ?? null
    : null
  const creationStagingTargets = creationOffer?.targets.filter((target) => target.intent === 'CUSTOMER_STAGING') ?? []
  const creationTarget = creationStagingTargets.find((target) => target.id === selectedTargetProfileId)
  const placementScope = useRef('')
  placementScope.current = JSON.stringify([selectedOfferId, selectedTargetProfileId])
  const productionTargets = selectedOffer?.targets.filter((target) => target.intent === 'CUSTOMER_PRODUCTION') ?? []
  const stagingTargetProfileId = selectedDeployment?.bootstrapTargetProfileId ?? selectedTargetProfileId
  const storageChoiceScope = JSON.stringify([session.actorId, session.authenticationMode, customerId, creationOffer?.id, creationOffer?.fingerprint,
    creationOffer?.entitlement.revision, selectedTargetProfileId])
  const selectedStorageMode = storageChoice?.scope === storageChoiceScope ? storageChoice.mode : ''
  let creationStorageModes: ReturnType<typeof aiStateStorageModes> = null
  let storageChoiceError: string | null = null
  try { if (creationOffer) creationStorageModes = aiStateStorageModes(creationOffer) }
  catch (error) { storageChoiceError = errorText(error) }
  const creationGuard = useScopedOneTimeValue<boolean>(JSON.stringify([storageChoiceScope, selectedStorageMode, deploymentName]))
  const requiresStorageChoice = selectedOffer?.settings.some((rule) => rule.setting === 'AI_STATE_STORAGE_MODE') ?? false
  const storageDraft = useQuery({
    queryKey: ['customer-storage-draft', customerId, selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: requiresStorageChoice && Boolean(selectedDeploymentId),
  })
  let savedStorageMode: ReturnType<typeof storedAiStateStorageMode> = undefined
  let storedStorageError: string | null = null
  try {
    if (requiresStorageChoice && selectedOffer && storageDraft.data) {
      if (storageDraft.data.deploymentId !== selectedDeploymentId) throw new Error('The deployment storage scope changed.')
      savedStorageMode = storedAiStateStorageMode(selectedOffer, storageDraft.data)
    }
  } catch (error) { storedStorageError = errorText(error) }
  const publishGuard = useScopedOneTimeValue<boolean>(JSON.stringify([session.actorId, session.authenticationMode, customerId, selectedDeploymentId,
    selectedOffer?.id, selectedOffer?.fingerprint, selectedOffer?.entitlement.revision,
    selectedDeployment?.bootstrapTargetProfileId, storageDraft.data?.id, storageDraft.data?.updatedAt]))

  useEffect(() => {
    if (!selectedOfferId && !requestedOfferId && offers.length > 0) setSelectedOfferId(offers[0].id)
  }, [offers, selectedOfferId, requestedOfferId])

  useEffect(() => {
    if (guided && step === 'template') return
    if (requestedDeploymentId || !portfolioQuery.isSuccess || !deployments.length
      || !(preferencesQuery.isSuccess || preferencesQuery.isError)) return
    const preferred = preferencesQuery.data?.deploymentWorkspace?.lastDeploymentId
    const initial = deployments.find((deployment) => deployment.deploymentId === preferred)
      ?? deployments.find((deployment) => deployment.status !== 'DRAFT') ?? deployments[0]
    setSelectedDeploymentId(initial.deploymentId)
  }, [guided, step, deployments, requestedDeploymentId, portfolioQuery.isSuccess, preferencesQuery.data,
    preferencesQuery.isSuccess, preferencesQuery.isError, setSelectedDeploymentId])

  useEffect(() => {
    if (!selectedDeploymentId || persistedSelection.current === selectedDeploymentId
      || !(preferencesQuery.isSuccess || preferencesQuery.isError)) return
    persistedSelection.current = selectedDeploymentId
    // /deployments is not an editor-scoped route, so persist its shared URL selection here.
    saveSelection.mutate({ deploymentWorkspace: {
      lastDeploymentId: selectedDeploymentId,
      lastSection: preferencesQuery.data?.deploymentWorkspace?.lastSection ?? null,
    } })
  }, [selectedDeploymentId, preferencesQuery.isSuccess, preferencesQuery.isError, preferencesQuery.data, saveSelection])

  useEffect(() => {
    if (!creationOffer) return
    const eligible = creationOffer.targets.filter((target) => target.intent === 'CUSTOMER_STAGING')
    if (!eligible.some((target) => target.id === selectedTargetProfileId)) {
      setSelectedTargetProfileId(eligible[0]?.id ?? '')
    }
  }, [creationOffer, selectedTargetProfileId])

  useEffect(() => {
    if (!selectedOffer) return
    const eligible = selectedOffer.targets.filter((target) => target.intent === 'CUSTOMER_PRODUCTION')
    if (!eligible.some((target) => target.id === selectedProductionTargetProfileId)) {
      setSelectedProductionTargetProfileId(eligible.length === 1 ? eligible[0].id : '')
    }
  }, [selectedOffer, selectedProductionTargetProfileId])

  useEffect(() => {
    setPlacementEvidence(null)
    setAdmission(null)
    setPromotion(null)
  }, [selectedDeploymentId])

  const versionsQuery = useQuery({
    queryKey: ['deployment-versions', selectedDeploymentId],
    queryFn: () => fetchDeploymentVersions(selectedDeploymentId),
    enabled: Boolean(selectedDeploymentId),
  })
  const releasesQuery = useQuery({
    queryKey: ['deployment-releases', selectedDeploymentId],
    queryFn: () => fetchDeploymentReleases(selectedDeploymentId),
    enabled: Boolean(selectedDeploymentId),
    refetchInterval: (query) => {
      const releases = (query.state.data as DeploymentReleaseSummary[] | undefined) ?? []
      return releases.some((release) => ['APPLY_REQUESTED', 'APPLYING', 'VERIFYING'].includes(release.status)) ? 4000 : false
    },
  })

  const admissionsQuery = useQuery({
    queryKey: ['customer-admission-reservations', selectedOffer?.id, selectedDeploymentId, stagingTargetProfileId],
    queryFn: () => fetchCustomerDeploymentAdmissions(selectedOffer!.id, selectedDeploymentId, stagingTargetProfileId),
    enabled: Boolean(selectedOffer && selectedDeploymentId && stagingTargetProfileId),
  })
  const retirementsQuery = useQuery({
    queryKey: ['customer-target-retirements', selectedOffer?.id, selectedDeploymentId, stagingTargetProfileId],
    queryFn: () => fetchCustomerTargetRetirements(selectedOffer!.id, selectedDeploymentId, stagingTargetProfileId),
    enabled: Boolean(selectedOffer && selectedDeploymentId && stagingTargetProfileId),
    refetchInterval: (query) => {
      const values = (query.state.data as CustomerTargetRetirementSummary[] | undefined) ?? []
      return values.some((value) => ['QUEUED', 'RUNNING', 'WAITING', 'CANCEL_REQUESTED'].includes(value.state)) ? 4000 : false
    },
  })

  const versions = versionsQuery.data ?? []
  const releases = releasesQuery.data ?? []
  const latestVersion = versions[0] ?? null
  const stagingRelease = releases.find((release) => release.targetProfileId === stagingTargetProfileId) ?? null
  const productionRelease = releases.find((release) => release.targetProfileId === selectedProductionTargetProfileId) ?? null
  const deploymentConsumers = selectableCustomerAssignments(consumers, customerId, selectedDeploymentId)
  const selectedConsumer = creatingNewAssignment ? null : deploymentConsumers.find((consumer) => consumer.consumerId === selectedConsumerId)
    ?? deploymentConsumers[0]
    ?? null
  const readinessGuard = useScopedOneTimeValue<boolean>(JSON.stringify([
    session.actorId, session.authenticationMode, customerId, selectedOffer?.id,
    selectedOffer?.fingerprint, selectedOffer?.entitlement.revision, selectedDeploymentId,
    productionRelease?.id, productionRelease?.deploymentVersionId, productionRelease?.targetProfileId,
    selectedConsumer?.consumerId, selectedConsumer?.bindingRevision,
  ]))
  const configurationCandidate: CustomerAssignmentConfigurationScope | null = selectedOffer && selectedDeployment && selectedConsumer
    ? { actorId: session.actorId ?? '', authenticationMode: session.authenticationMode, customerId,
      offer: selectedOffer, deployment: selectedDeployment, consumer: selectedConsumer } : null
  const assignmentDraft = useQuery({
    queryKey: ['customer-assignment-draft', session.actorId, session.authenticationMode, customerId, selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId), retry: false,
    enabled: Boolean(configurationCandidate && versionsQuery.isSuccess && releasesQuery.isSuccess
      && canReviewCustomerAssignment(configurationCandidate, versions, releases)),
  })
  const configurationScope = configurationCandidate && assignmentDraft.data ? { ...configurationCandidate, draft: assignmentDraft.data } : null
  const configurationEligible = Boolean(configurationScope && versionsQuery.isSuccess && releasesQuery.isSuccess
    && assignmentDraft.isSuccess && !assignmentDraft.isFetching && canConfigureExistingCustomerAssignment(configurationScope, versions, releases))
  const assignmentWorkspaces = useQuery({
    queryKey: ['ai-workspace-installations', customerId, session.actorId, session.authenticationMode],
    queryFn: () => fetchAIWorkspaceInstallations(customerId), enabled: configurationEligible, retry: false,
  })
  let configurationDrafts: ReturnType<typeof customerAssignmentDraftExpectations> | null = null
  let configurationDraftError: string | null = null
  let configurationProfiles: ReturnType<typeof customerAssignmentReviewedWorkspaceProfiles> = []
  try {
    if (configurationEligible && configurationScope && assignmentWorkspaces.data) {
      configurationDrafts = customerAssignmentDraftExpectations(configurationScope, assignmentWorkspaces.data)
      configurationProfiles = customerAssignmentReviewedWorkspaceProfiles(configurationScope)
    }
  } catch (error) { configurationDraftError = errorText(error) }
  const profileChoiceGuard = useScopedOneTimeValue<CustomerAssignmentWorkspaceProfileChoices>(JSON.stringify([
    session.actorId, session.authenticationMode, customerId, selectedOffer?.id, selectedOffer?.fingerprint,
    selectedOffer?.entitlement.revision, selectedOffer?.workspaceProfiles, selectedDeploymentId, stagingTargetProfileId,
    selectedConsumer?.consumerId, selectedConsumer?.bindingRevision, configurationDrafts,
  ]))
  const configurationProfileChoices = profileChoiceGuard.value ?? {}
  let configurationReviewedDrafts = configurationDrafts
  try {
    if (configurationScope && assignmentWorkspaces.data && configurationDrafts) {
      configurationReviewedDrafts = customerAssignmentDraftExpectations(configurationScope, assignmentWorkspaces.data, configurationProfileChoices)
    }
  } catch (error) { configurationDraftError = errorText(error) }
  const configurationGuard = useScopedOneTimeValue<boolean>(JSON.stringify([session.actorId, session.authenticationMode,
    customerId, selectedOffer?.id, selectedOffer?.fingerprint, selectedOffer?.entitlement.revision, selectedDeploymentId,
    stagingTargetProfileId, selectedDeployment?.status, selectedDeployment?.activeVersionId, selectedConsumer,
    versions.map((value) => value.id), releases.map((value) => value.id), assignmentDraft.data?.id,
    assignmentDraft.data?.revisionNumber, assignmentDraft.data?.updatedAt, configurationReviewedDrafts, selectedOffer?.workspaceProfiles]))
  let configurationProtectionError: string | null = null
  let configurationIntent: AssignmentConfigurationIntent | null = null
  let configurationHeld = false
  try {
    if (configurationScope && configurationDrafts) {
      configurationIntent = configurationIntents.current.read(customerId, configurationScope.consumer.consumerId)
      configurationHeld = !canStartAssignmentIntent(configurationIntent, assignmentConfigurationSnapshot(configurationScope, configurationDrafts))
    }
  } catch (error) { configurationProtectionError = errorText(error) }
  const currentRejectionReview = reviewedRejection?.token === configurationGuard.capture() ? reviewedRejection.prepared : null
  const currentRejectionConfirmed = rejectionConfirmed?.token === configurationGuard.capture() && rejectionConfirmed.checked
  const activeAdmission = admission?.deploymentId === selectedDeploymentId
    && admission.targetProfileId === stagingTargetProfileId ? admission : admissionsQuery.data?.[0] ?? null
  const recoveryResult = useScopedOneTimeValue<CustomerPreparedFailureRecoverySummary>(JSON.stringify([
    session.actorId, session.authenticationMode, customerId, selectedOffer?.id, selectedOffer?.fingerprint,
    selectedDeploymentId, stagingTargetProfileId, activeAdmission?.id,
  ]))
  const failedRetryResult = useScopedOneTimeValue<CustomerFailedApplyRetrySummary>(JSON.stringify([
    session.actorId, session.authenticationMode, customerId, selectedOffer?.id, selectedOffer?.fingerprint,
    selectedDeploymentId, stagingTargetProfileId, activeAdmission?.id, stagingRelease?.id,
  ]))
  const setupResumeResult = useScopedOneTimeValue<CustomerPostProvisionResumeSummary>(JSON.stringify([
    session.actorId, session.authenticationMode, customerId, selectedOffer?.id, selectedOffer?.fingerprint,
    selectedDeploymentId, stagingTargetProfileId, activeAdmission?.id, stagingRelease?.id,
  ]))
  const candidateScope = JSON.stringify({ deploymentId: selectedDeploymentId,
    targetProfileId: selectedProductionTargetProfileId, stagingReleaseId: stagingRelease?.id,
    consumerId: selectedConsumer?.consumerId, bindingRevision: selectedConsumer?.bindingRevision })
  const reviewedPromotion = reviewedCandidateScope === candidateScope ? promotion : null
  const retirement = retirementsQuery.data?.find((value) => value.state !== 'CANCELLED') ?? null
  const productionActive = Boolean(releaseReady(productionRelease)
    && selectedConsumer?.boundReleaseId === productionRelease?.id
    && selectedConsumer?.boundTargetProfileId === productionRelease?.targetProfileId)
  const admissionRetirementStarted = Boolean(activeAdmission
    && ['CLAIMED', 'HANDED_OFF', 'RELEASED'].includes(activeAdmission.expiryState))
  const retirementStarted = admissionRetirementStarted
    || Boolean(retirement && !['PLANNED', 'CANCELLED'].includes(retirement.state))
  const activeStep = selectedDeployment
    ? lifecycleStep(selectedDeployment, versions, releases, consumers, selectedDeployment.bootstrapTargetProfileId,
        new Set(productionTargets.map((target) => target.id)))
    : 0
  const goLiveReadinessQuery = useQuery({
    queryKey: ['customer-go-live-readiness', selectedOffer?.id, selectedDeploymentId,
      productionRelease?.id, selectedConsumer?.consumerId],
    queryFn: () => fetchCustomerDeploymentGoLiveReadiness(
      selectedOffer!.id, selectedDeploymentId, productionRelease!.id, selectedConsumer!.consumerId,
    ),
    enabled: Boolean(selectedOffer && selectedDeploymentId && productionRelease && selectedConsumer),
    refetchInterval: (query) => query.state.data?.activationReady && query.state.data?.postActivationComplete ? false : 5000,
  })
  const goLiveReadiness = goLiveReadinessQuery.data ?? null

  useEffect(() => {
    if (!deploymentConsumers.some((consumer) => consumer.consumerId === selectedConsumerId)) {
      setSelectedConsumerId(deploymentConsumers[0]?.consumerId ?? '')
    }
  }, [deploymentConsumers, selectedConsumerId])

  const invalidateCustomerState = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['customer-self-service-catalogue', customerId] }),
      queryClient.invalidateQueries({ queryKey: ['customer-self-service-invitations', customerId] }),
      queryClient.invalidateQueries({ queryKey: ['customer-deployment-portfolio', customerId] }),
      queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-versions', selectedDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-releases', selectedDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['platform-consumers', customerId] }),
      queryClient.invalidateQueries({ queryKey: ['customer-admission-reservations'] }),
      queryClient.invalidateQueries({ queryKey: ['customer-target-retirements'] }),
      queryClient.invalidateQueries({ queryKey: ['customer-go-live-readiness'] }),
    ])
  }

  const acceptInvitation = useMutation({
    mutationFn: ({ id, revision }: { id: string; revision: number }) => acceptCustomerSelfServiceInvitation(id, {
      expectedRevision: revision,
      operationKey: operationKey(`accept-invitation:${id}`, { revision }),
      reason: 'Accepted by the customer owner in LoomAI Platform.',
    }),
    onSuccess: async (_, variables) => {
      completeOperation(`accept-invitation:${variables.id}`)
      setNotice({ severity: 'success', message: 'The reviewed product offer is now active for your customer account.' })
      await invalidateCustomerState()
    },
  })

  const placementProbe = useMutation({
    mutationFn: ({ offerId, targetProfileId }: { offerId: string; targetProfileId: string }) =>
      probeCustomerOfferTargetPlacement(offerId, targetProfileId),
    onSuccess: async (evidence, input) => {
      if (placementScope.current === JSON.stringify([input.offerId, input.targetProfileId])) {
        setPlacementEvidence(evidence.id)
        setNotice({ severity: 'success', message: `Placement verified for ${evidence.intent.toLowerCase().replace(/_/g, ' ')}.` })
      }
      await queryClient.invalidateQueries({ queryKey: ['customer-self-service-catalogue', customerId] })
    },
  })

  const bootstrap = useMutation({
    mutationFn: ({ offer, targetProfileId, name, mode }: {
      offer: CustomerOfferSummary; targetProfileId: string; name: string; mode: string; token: symbol
    }) => {
      const identity = { offerId: offer.id, fingerprint: offer.fingerprint, targetProfileId,
        name: name.trim(), revision: offer.entitlement.revision, aiStateStorageMode: mode }
      return bootstrapCustomerDeployment(offer.id, customerStorageBootstrapRequest(offer, customerId,
        targetProfileId, name, mode, operationKey('bootstrap-deployment', identity)))
    },
    onSuccess: async (result, input) => {
      completeOperation('bootstrap-deployment')
      if (!creationGuard.publish(input.token, true)) {
        await queryClient.invalidateQueries({ queryKey: ['customer-deployment-portfolio', input.offer.entitlement.customerId] })
        return
      }
      setSelectedDeploymentId(result.deploymentId)
      setDeploymentName('')
      setPlacementEvidence(null)
      setNotice({ severity: 'success', message: 'Customer staging configuration created from the reviewed dealership offer.' })
      await invalidateCustomerState()
    },
  })

  const publish = useMutation({
    mutationFn: async ({ token }: { token: symbol }) => {
      if (!selectedOffer || !selectedDeployment?.bootstrapTargetProfileId || !publishGuard.publish(token, true)) {
        throw new Error('Select the current reviewed deployment before publishing.')
      }
      const identity = { deploymentId: selectedDeployment!.deploymentId,
        targetProfileId: selectedDeployment!.bootstrapTargetProfileId,
        revision: selectedOffer!.entitlement.revision, draftId: storageDraft.data?.id,
        draftRevision: storageDraft.data?.revisionNumber, storageMode: savedStorageMode }
      const key = operationKey('publish-version', identity)
      await probeCustomerOfferTargetPlacement(selectedOffer.id, selectedDeployment.bootstrapTargetProfileId)
      if (!publishGuard.publish(token, true)) throw new Error('Configuration changed. Refresh before publishing.')
      const result = requiresStorageChoice
        ? await publishStoredAiStateStorage({ draft: fetchDeploymentDraft, publish: publishCustomerDeploymentVersion },
          selectedOffer!, customerId, selectedDeployment!, storageDraft.data!, key, () => publishGuard.publish(token, true))
        : await publishCustomerDeploymentVersion(selectedOffer!.id, selectedDeployment!.deploymentId, {
          targetProfileId: selectedDeployment!.bootstrapTargetProfileId,
          expectedEntitlementRevision: selectedOffer!.entitlement.revision, operationKey: key,
        })
      return { result, token }
    },
    onSuccess: async ({ result, token }) => {
      completeOperation('publish-version')
      if (publishGuard.publish(token, true)) setNotice({ severity: 'success', message: `Immutable version ${result.versionLabel} published.` })
      await queryClient.invalidateQueries({ queryKey: ['customer-storage-draft', customerId, result.deploymentId] })
      await invalidateCustomerState()
    },
  })

  const stagingApply = useMutation({
    mutationFn: async () => {
      if (!selectedDeployment || !selectedOffer || !latestVersion || !selectedDeployment.bootstrapTargetProfileId) {
        throw new Error('A published version and entitled customer-staging target are required.')
      }
      const targetProfileId = selectedDeployment.bootstrapTargetProfileId
      const key = operationKey('stage-apply', {
        deploymentId: selectedDeployment.deploymentId, targetProfileId, versionId: latestVersion.id,
        revision: selectedOffer.entitlement.revision,
      })
      const reserved = await admissionCommands.current.authorize(selectedOffer.id, selectedDeployment.deploymentId, {
        targetProfileId,
        versionId: latestVersion.id,
        operation: 'APPLY',
        operationKey: key,
      })
      setAdmission(reserved)
      return applyCustomerDeploymentStagingVersion(selectedOffer.id, selectedDeployment.deploymentId, {
        versionId: latestVersion.id,
        expectedEntitlementRevision: selectedOffer.entitlement.revision,
        operationKey: key,
      })
    },
    onSuccess: async (release) => {
      completeOperation('stage-apply')
      setNotice({ severity: 'info', message: `Staging release ${release.id} is queued. Verification status will update here.` })
      await invalidateCustomerState()
    },
  })

  const createConsumer = useMutation({
    mutationFn: async () => {
      if (!selectedOffer || !selectedDeployment) {
        throw new Error('A reviewed offer and customer-staging deployment are required.')
      }
      const offer = selectedOffer
      const release = releaseReady(stagingRelease) ? stagingRelease : null
      const key = operationKey('create-staging-assignment', {
        deploymentId: selectedDeploymentId, consumerId: consumerId.trim(), displayName: consumerName.trim(),
        releaseId: release?.id, revision: offer.entitlement.revision,
      })
      if (release?.targetProfileId) {
        await admissionCommands.current.authorize(offer.id, selectedDeploymentId, {
          targetProfileId: release.targetProfileId,
          versionId: release.deploymentVersionId, releaseId: release.id,
          operation: 'ACTIVATE', operationKey: key,
        })
      }
      return createCustomerDeploymentStagingConsumer(offer.id, selectedDeploymentId, {
        consumerId: consumerId.trim(),
        displayName: consumerName.trim(),
        description: 'Customer-managed LoomAI Engage assignment.',
        releaseId: release?.id,
        expectedEntitlementRevision: offer.entitlement.revision,
        operationKey: key,
      })
    },
    onSuccess: async (consumer) => {
      completeOperation('create-staging-assignment')
      setConsumerId('')
      setConsumerName('')
      setNotice({ severity: 'success', message: consumer.boundReleaseId
        ? 'App assignment created for the verified customer deployment.'
        : 'App assignment configured. Its staging release is awaiting activation.' })
      await invalidateCustomerState()
      setSelectedConsumerId(consumer.consumerId)
      setCreatingNewAssignment(false)
    },
  })

  const configurationApi = {
    session: fetchPlatformAuthSession, offer: fetchCustomerSelfServiceOffer, deployments: fetchCustomerDeploymentPortfolio,
    installations: fetchAIWorkspaceInstallations, consumers: fetchPlatformConsumers,
    versions: fetchDeploymentVersions, releases: fetchDeploymentReleases, draft: fetchDeploymentDraft,
    configure: configureCustomerDeploymentStagingConsumer,
  }
  const readRejectedAssignment = useMutation({
    retry: false,
    mutationFn: (input: { scope: CustomerAssignmentConfigurationScope; token: symbol }) => prepareKnownAssignmentReconciliation(
      configurationApi, input.scope, () => configurationGuard.publish(input.token, true), configurationIntents.current),
    onSuccess: (prepared, input) => {
      if (configurationGuard.publish(input.token, true)) {
        setReviewedRejection({ token: input.token, prepared }); setRejectionConfirmed(null)
      }
    },
  })
  const reconcileRejectedAssignment = useMutation({
    retry: false,
    mutationFn: (input: { scope: CustomerAssignmentConfigurationScope; token: symbol; prepared: KnownAssignmentReconciliation }) =>
      confirmKnownAssignmentReconciliation(configurationApi, input.scope, input.prepared,
        () => configurationGuard.publish(input.token, true), configurationIntents.current),
    onSuccess: (_, input) => {
      if (configurationGuard.publish(input.token, true)) {
        completeOperation('configure-existing-assignment')
        setReviewedRejection(null); setRejectionConfirmed(null)
        profileChoiceGuard.publish(profileChoiceGuard.capture(), {})
        setNotice({ severity: 'info', message: 'Known rejection reconciled. No assignment binding was changed.' })
      }
    },
  })
  const configureAssignment = useMutation({
    retry: false,
    mutationFn: (input: ConfigureAssignmentInput) => configureExistingCustomerAssignment(configurationApi,
      input.scope, input.request, () => configurationGuard.publish(input.token, true), configurationIntents.current),
    onSuccess: async (result, input) => {
      completeOperation('configure-existing-assignment')
      queryClient.setQueryData<PlatformConsumerSummary[]>(['platform-consumers', input.scope.customerId],
        (rows) => rows?.map((row) => row.consumerId === result.consumerId ? result : row))
      if (configurationGuard.publish(input.token, true)) {
        setNotice({ severity: 'success', message: 'Existing assignment configured. No release was activated.' })
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['platform-consumers', input.scope.customerId], exact: true }),
        queryClient.invalidateQueries({ queryKey: ['ai-workspace-installations', input.scope.customerId] }),
        queryClient.invalidateQueries({ queryKey: ['customer-deployment-portfolio', input.scope.customerId], exact: true }),
      ])
    },
    onSettled: async (_, error, input) => {
      configurationBusy.current = false
      if (error && input) {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ['platform-consumers', input.scope.customerId], exact: true }),
          queryClient.invalidateQueries({ queryKey: ['ai-workspace-installations', input.scope.customerId] }),
        ])
      }
    },
  })

  const bindConsumerToStaging = useMutation({
    mutationFn: async () => {
      if (!selectedOffer || !selectedDeploymentId || !selectedConsumer || !stagingRelease?.targetProfileId) {
        throw new Error('Select a reviewed offer, deployment, app assignment, and verified customer-staging release.')
      }
      const key = operationKey('bind-staging-assignment', {
        deploymentId: selectedDeploymentId, consumerId: selectedConsumer.consumerId,
        releaseId: stagingRelease.id, bindingRevision: selectedConsumer.bindingRevision,
        entitlementRevision: selectedOffer.entitlement.revision,
      })
      await admissionCommands.current.authorize(selectedOffer.id, selectedDeploymentId, {
        targetProfileId: stagingRelease.targetProfileId,
        versionId: stagingRelease.deploymentVersionId, releaseId: stagingRelease.id,
        operation: 'ACTIVATE', operationKey: key,
      })
      return activateCustomerDeploymentStagingConsumer(selectedOffer.id, selectedDeploymentId, {
        consumerId: selectedConsumer.consumerId,
        releaseId: stagingRelease.id,
        expectedBindingRevision: selectedConsumer.bindingRevision,
        expectedEntitlementRevision: selectedOffer.entitlement.revision,
        operationKey: key,
      })
    },
    onSuccess: async (consumer) => {
      completeOperation('bind-staging-assignment')
      setSelectedConsumerId(consumer.consumerId)
      setPromotion(null)
      setNotice({ severity: 'success', message: 'App assignment now resolves to the latest verified staging release.' })
      await invalidateCustomerState()
    },
  })

  const planPromotion = useMutation({
    mutationFn: async () => {
      const identity = { deploymentId: selectedDeploymentId, consumerId: selectedConsumer!.consumerId,
        bindingRevision: selectedConsumer!.bindingRevision, stagingReleaseId: stagingRelease!.id,
        productionTargetProfileId: selectedProductionTargetProfileId }
      const key = operationKey('plan-production', identity)
      await admissionCommands.current.authorize(selectedOffer!.id, selectedDeploymentId, {
        targetProfileId: selectedProductionTargetProfileId,
        versionId: stagingRelease!.deploymentVersionId,
        operation: 'PROMOTION_PLAN', operationKey: key,
      })
      const result = await planDeploymentPracticalPromotion(selectedDeploymentId, {
        consumerId: selectedConsumer!.consumerId,
        expectedBindingRevision: selectedConsumer!.bindingRevision,
        stagingReleaseId: stagingRelease!.id,
        productionTargetProfileId: selectedProductionTargetProfileId,
        operationKey: key,
      })
      return { result, candidateScope }
    },
    onSuccess: async ({ result, candidateScope: reviewedScope }) => {
      completeOperation('plan-production')
      setReviewedCandidateScope(reviewedScope)
      setPromotion(result)
      setNotice({ severity: 'info', message: result.message })
      await invalidateCustomerState()
    },
  })

  const applyProduction = useMutation({
    mutationFn: async () => {
      const identity = { deploymentId: selectedDeploymentId, consumerId: selectedConsumer!.consumerId,
        bindingRevision: selectedConsumer!.bindingRevision, stagingReleaseId: stagingRelease!.id,
        productionTargetProfileId: selectedProductionTargetProfileId,
        candidateFingerprint: reviewedPromotion!.candidateFingerprint }
      const key = operationKey('apply-production', identity)
      await admissionCommands.current.authorize(selectedOffer!.id, selectedDeploymentId, {
        targetProfileId: selectedProductionTargetProfileId,
        versionId: stagingRelease!.deploymentVersionId,
        operation: 'PROMOTION_APPLY', operationKey: key,
      })
      return requestDeploymentPracticalProductionApply(selectedDeploymentId, {
        consumerId: selectedConsumer!.consumerId,
        expectedBindingRevision: selectedConsumer!.bindingRevision,
        stagingReleaseId: stagingRelease!.id,
        productionTargetProfileId: selectedProductionTargetProfileId,
        candidateFingerprint: reviewedPromotion!.candidateFingerprint ?? undefined,
        operationKey: key,
      })
    },
    onSuccess: async (result) => {
      completeOperation('apply-production')
      setPromotion(result)
      setNotice({ severity: 'info', message: result.message })
      await invalidateCustomerState()
    },
  })

  const refreshProductionReadiness = useMutation({
    mutationFn: async ({ token }: { token: symbol }) => {
      if (!readinessGuard.publish(token, true) || !selectedOffer || !productionRelease
        || !selectedConsumer || !releaseReady(productionRelease)) {
        throw new Error('Select the current verified production release and customer app before refreshing readiness.')
      }
      const key = operationKey('refresh-production-readiness', {
        deploymentId: selectedDeploymentId, releaseId: productionRelease.id,
        consumerId: selectedConsumer.consumerId, bindingRevision: selectedConsumer.bindingRevision,
      })
      return admissionCommands.current.authorize(selectedOffer.id, selectedDeploymentId, {
        targetProfileId: productionRelease.targetProfileId!,
        versionId: productionRelease.deploymentVersionId, releaseId: productionRelease.id,
        operation: 'ACTIVATE', operationKey: key,
      })
    },
    onSuccess: async (_, input) => {
      completeOperation('refresh-production-readiness')
      if (readinessGuard.publish(input.token, true)) {
        setNotice({ severity: 'success', message: 'Production connection evidence refreshed. No app assignment was changed.' })
      }
      await invalidateCustomerState()
    },
  })

  const activateProduction = useMutation({
    mutationFn: async () => {
      const identity = { deploymentId: selectedDeploymentId, releaseId: productionRelease!.id,
        consumerId: selectedConsumer!.consumerId, bindingRevision: selectedConsumer!.bindingRevision }
      const key = operationKey('activate-production', identity)
      await admissionCommands.current.authorize(selectedOffer!.id, selectedDeploymentId, {
        targetProfileId: productionRelease!.targetProfileId!,
        versionId: productionRelease!.deploymentVersionId, releaseId: productionRelease!.id,
        operation: 'ACTIVATE', operationKey: key,
      })
      return activateDeploymentPracticalProductionConsumer(selectedDeploymentId, {
        productionReleaseId: productionRelease!.id,
        consumerId: selectedConsumer!.consumerId,
        markStagingSuperseded: true,
        reason: 'Customer approved production activation after verified readiness.',
        expectedBindingRevision: selectedConsumer!.bindingRevision,
        operationKey: key,
      })
    },
    onSuccess: async (result) => {
      completeOperation('activate-production')
      setPromotion(result)
      setNotice({ severity: 'success', message: result.message })
      await invalidateCustomerState()
    },
  })

  const rollbackProduction = useMutation({
    mutationFn: async () => {
      const identity = { deploymentId: selectedDeploymentId, consumerId: selectedConsumer!.consumerId,
        rollbackReleaseId: stagingRelease!.id, bindingRevision: selectedConsumer!.bindingRevision }
      const key = operationKey('rollback-production', identity)
      await admissionCommands.current.authorize(selectedOffer!.id, selectedDeploymentId, {
        targetProfileId: stagingRelease!.targetProfileId!,
        versionId: stagingRelease!.deploymentVersionId, releaseId: stagingRelease!.id,
        operation: 'ROLLBACK', operationKey: key,
      })
      return rollbackDeploymentPracticalProductionConsumer(selectedDeploymentId, {
        consumerId: selectedConsumer!.consumerId,
        rollbackDeploymentId: selectedDeploymentId,
        rollbackReleaseId: stagingRelease!.id,
        rollbackTargetProfileId: stagingRelease!.targetProfileId ?? undefined,
        reason: 'Customer requested rollback to the last verified staging assignment.',
        expectedBindingRevision: selectedConsumer!.bindingRevision,
        operationKey: key,
      })
    },
    onSuccess: async (result) => {
      completeOperation('rollback-production')
      setPromotion(result)
      setNotice({ severity: 'warning', message: result.message })
      await invalidateCustomerState()
    },
  })

  const extendStaging = useMutation({
    mutationFn: () => {
      if (!selectedOffer || !selectedDeployment || !activeAdmission) {
        throw new Error('An active customer-staging reservation is required.')
      }
      return extendCustomerDeploymentStaging(selectedOffer.id, selectedDeployment.deploymentId, {
        targetProfileId: selectedDeployment.bootstrapTargetProfileId,
        allocationId: activeAdmission.id,
        expectedRevision: activeAdmission.revision,
        operationKey: operationKey('extend-staging', {
          deploymentId: selectedDeployment.deploymentId, allocationId: activeAdmission.id,
          revision: activeAdmission.revision, productionActive,
        }),
        reason: productionActive
          ? 'Customer retained verified staging for a bounded rollback window.'
          : 'Customer extended the active staging evaluation window.',
        keepAfterPromotion: productionActive,
      })
    },
    onSuccess: async (result) => {
      completeOperation('extend-staging')
      setAdmission(result)
      setNotice({ severity: 'success', message: `Staging availability is retained until ${timestamp(result.expiresAt)}.` })
      await invalidateCustomerState()
    },
  })

  const readinessItems: CustomerGoLiveReadinessItem[] = goLiveReadiness?.items ?? []

  const activeError = acceptInvitation.error || placementProbe.error
    || (readRejectedAssignment.variables?.token === configurationGuard.capture() ? readRejectedAssignment.error : null)
    || (reconcileRejectedAssignment.variables?.token === configurationGuard.capture() ? reconcileRejectedAssignment.error : null)
    || (bootstrap.variables?.token === creationGuard.capture() ? bootstrap.error : null)
    || (publish.variables?.token === publishGuard.capture() ? publish.error : null)
    || (configureAssignment.variables?.token === configurationGuard.capture() ? configureAssignment.error : null)
    || stagingApply.error || createConsumer.error || bindConsumerToStaging.error || planPromotion.error || applyProduction.error
    || (refreshProductionReadiness.variables?.token === readinessGuard.capture() ? refreshProductionReadiness.error : null)
    || activateProduction.error || rollbackProduction.error || extendStaging.error

  if (catalogueQuery.isLoading || portfolioQuery.isLoading || invitationsQuery.isLoading) {
    return <Box sx={{ minHeight: 360, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Customer workspace" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800 }}>{guided ? 'Deployment setup' : 'My deployments'}</Typography>
        <Typography color="text.secondary" sx={{ mt: 1, maxWidth: 900 }}>
          Create an isolated customer-staging deployment from a reviewed product offer, verify it, and promote the same immutable version to production.
        </Typography>
      </Box>

      {guided ? <>
        <Tabs value={step} onChange={(_, value: string) => selectStep(value)} variant="scrollable" scrollButtons="auto" aria-label="Deployment setup stages">
          {journeySteps.map(item => <Tab key={item.id} value={item.id} label={item.label} disabled={item.id !== 'template' && !selectedDeployment} />)}
        </Tabs>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between">
          <TextField select size="small" label="Deployment being configured" value={selectedDeploymentId} onChange={event => setSelectedDeploymentId(event.target.value)} sx={{ minWidth: 260 }}>
            <MenuItem value="">Choose an existing deployment</MenuItem>{deployments.map(item => <MenuItem key={item.deploymentId} value={item.deploymentId}>{item.name}</MenuItem>)}
          </TextField>
          {!detailSection ? <Button component={RouterLink} to={`${location.pathname}?${new URLSearchParams({ view: 'manage', ...(selectedDeploymentId ? { deploymentId: selectedDeploymentId } : {}) })}`}>All setup controls</Button> : null}
        </Stack>
        {requestedStep && !journeySteps.some(item => item.id === requestedStep) ? <Alert severity="warning">The requested setup stage is not supported. No operation was started.</Alert> : null}
      </> : null}

      {notice ? <Alert severity={notice.severity} onClose={() => setNotice(null)}>{notice.message}</Alert> : null}
      {activeError ? <Alert severity="error">{errorText(activeError)}</Alert> : null}
      {preferencesQuery.error || saveSelection.error ? (
        <Alert severity="warning" action={saveSelection.isError ? (
          <Button color="inherit" disabled={!selectedDeploymentId || saveSelection.isPending}
            onClick={() => saveSelection.mutate({ deploymentWorkspace: {
              lastDeploymentId: selectedDeploymentId,
              lastSection: preferencesQuery.data?.deploymentWorkspace?.lastSection ?? null,
            } })}>Retry selection save</Button>
        ) : undefined}>Deployment selection could not be persisted. {errorText(preferencesQuery.error || saveSelection.error)}</Alert>
      ) : null}
      {requestedDeploymentId && portfolioQuery.isSuccess && !selectedDeployment ? (
        <Alert severity="error">The requested deployment is unavailable in your customer portfolio. Select an owned deployment to continue.</Alert>
      ) : null}
      {!customerId ? <Alert severity="error">This account has no customer ownership scope.</Alert> : null}

      {(invitationsQuery.data ?? []).filter((invitation) => invitation.status === 'INVITED').map((invitation) => (
        <Alert
          key={invitation.id}
          severity="info"
          action={(
            <Button
              color="inherit"
              disabled={acceptInvitation.isPending}
              onClick={() => acceptInvitation.mutate({ id: invitation.id, revision: invitation.revision })}
            >
              Accept offer
            </Button>
          )}
        >
          Product offer <strong>{invitation.offerId}</strong> is ready for review. Invitation expires {timestamp(invitation.expiresAt)}.
        </Alert>
      ))}

      <Paper variant="outlined" sx={{ p: 2.5, display: showStep('template') ? undefined : 'none' }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 750 }}>Create from a reviewed offer</Typography>
            <Typography variant="body2" color="text.secondary">
              Only entitled templates, settings, targets, and resource limits are available here.
            </Typography>
          </Box>
          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <TextField
                select fullWidth label="Product offer" value={selectedOfferId}
                onChange={(event) => { setSelectedOfferId(event.target.value); setStorageChoice(null); setPlacementEvidence(null) }}
              >
                {offers.map((offer) => <MenuItem key={offer.id} value={offer.id}>{offer.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField
                select fullWidth label="Customer staging target" value={selectedTargetProfileId}
                onChange={(event) => { setSelectedTargetProfileId(event.target.value); setStorageChoice(null); setPlacementEvidence(null) }}
              >
                {creationStagingTargets.map((target) => (
                  <MenuItem key={target.id} value={target.id} disabled={!targetSelectable(target.readiness)}>
                    {target.id} · {target.readiness}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField fullWidth label="Deployment name" value={deploymentName}
                onChange={(event) => setDeploymentName(event.target.value)} />
            </Grid>
            {creationStorageModes ? <Grid item xs={12} md={4}>
              <TextField select fullWidth label="AI-state storage" value={selectedStorageMode}
                onChange={(event) => setStorageChoice({ scope: storageChoiceScope, mode: event.target.value })}>
                <MenuItem value="" disabled>Select storage</MenuItem>
                {creationStorageModes.map((mode) => <MenuItem key={mode} value={mode}>{aiStateStorageLabels[mode]}</MenuItem>)}
              </TextField>
            </Grid> : null}
          </Grid>
          {storageChoiceError ? <Alert severity="error">{storageChoiceError}</Alert> : null}
          {requestedOfferId && catalogueQuery.isSuccess && !creationOffer ? <Alert severity="error">The requested template offer is not available in this account. No alternative offer was selected.</Alert> : null}
          {creationOffer?.blockers.length ? <Alert severity="warning">{creationOffer.blockers.join(' · ')}</Alert> : null}
          {creationTarget?.blockers.length ? (
            <Alert severity="warning">{creationTarget.readiness} · {creationTarget.blockers.join(' · ')}</Alert>
          ) : null}
          {creationOffer ? <CustomerAdvancedSection>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label={`Template ${creationOffer.templateId}`} variant="outlined" />
              <Chip label={`${creationOffer.targets.length} reviewed targets`} variant="outlined" />
              <Chip label={`${creationOffer.bindingSlots.length} binding slots`} variant="outlined" />
              <Chip label={`Staging expiry ${creationStagingTargets[0]?.limits.stagingExpiryHours ?? 0}h`} variant="outlined" />
            </Stack>
          </CustomerAdvancedSection> : null}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="flex-end">
            <Button
              variant="outlined" startIcon={<CloudDoneRoundedIcon />}
              disabled={!creationOffer || !selectedTargetProfileId || placementProbe.isPending}
              onClick={() => placementProbe.mutate({ offerId: creationOffer!.id, targetProfileId: selectedTargetProfileId })}
            >
              Verify placement
            </Button>
            <Button
              variant="contained" startIcon={<AddRoundedIcon />}
              disabled={!creationOffer || !selectedTargetProfileId || deploymentName.trim().length < 3
                || !placementEvidence || catalogueQuery.isFetching || bootstrap.isPending
                || Boolean(storageChoiceError) || Boolean(creationStorageModes && !creationStorageModes.includes(selectedStorageMode as 'MANAGED_POSTGRES' | 'CUSTOMER_POSTGRES'))
                || !creationTarget || !['READY', 'PLACEMENT_VERIFIED_ADMISSION_REQUIRED'].includes(creationTarget.readiness)}
              onClick={() => bootstrap.mutate({ offer: creationOffer!, targetProfileId: selectedTargetProfileId,
                name: deploymentName, mode: selectedStorageMode, token: creationGuard.capture() })}
            >
              Create staging deployment
            </Button>
          </Stack>
        </Stack>
      </Paper>

      <Grid container spacing={2} sx={{ display: guided ? 'none' : undefined }}>
        {deployments.map((deployment) => (
          <Grid item xs={12} md={6} key={deployment.deploymentId}>
            <Card
              variant="outlined"
              sx={{ height: '100%', borderColor: deployment.deploymentId === selectedDeploymentId ? 'primary.main' : 'divider' }}
            >
              <CardContent>
                <Stack spacing={1.5}>
                  <Stack direction="row" justifyContent="space-between" spacing={1}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 750 }}>{deployment.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{deployment.deploymentId}</Typography>
                    </Box>
                    <Chip label={deployment.status} color={deployment.status === 'ACTIVE' ? 'success' : 'default'} />
                  </Stack>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip size="small" label={deployment.environmentName} variant="outlined" />
                    <Chip size="small" label={deployment.offerId} variant="outlined" />
                    <Chip size="small" label={deployment.activeVersionId ? 'Version active' : 'Configuration only'} variant="outlined" />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">Created {timestamp(deployment.createdAt)}</Typography>
                  <Button variant={deployment.deploymentId === selectedDeploymentId ? 'contained' : 'outlined'}
                    onClick={() => setSelectedDeploymentId(deployment.deploymentId)}>
                    Manage deployment
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {selectedDeployment && selectedOffer ? (
        <Paper variant="outlined" sx={{ p: guided ? 0 : 2.5, border: guided ? 0 : undefined, bgcolor: guided ? 'transparent' : undefined, display: guided && step === 'template' ? 'none' : undefined }}>
          <Stack spacing={3}>
            <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>{selectedDeployment.name}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Reviewed offer {selectedOffer.name} · entitlement revision {selectedOffer.entitlement.revision}
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Button component={RouterLink} to={`/document-knowledge?deploymentId=${encodeURIComponent(selectedDeploymentId)}`}
                  startIcon={<DescriptionRoundedIcon />} variant="outlined">Knowledge</Button>
                <Button component={RouterLink} to="/ai-workspaces" startIcon={<ForumRoundedIcon />} variant="outlined">Engage apps</Button>
                <Button component={RouterLink} to={`/integrations?deploymentId=${encodeURIComponent(selectedDeploymentId)}`}
                  startIcon={<OpenInNewRoundedIcon />} variant="outlined">Integrations</Button>
              </Stack>
            </Stack>

            {!guided ? <Stepper activeStep={activeStep} alternativeLabel>
              {['Set up', 'Publish', 'Stage', 'Verify', 'Deploy', 'Activate'].map((label) => (
                <Step key={label} sx={{ minWidth: 0, px: 0.5 }}>
                  <StepLabel sx={{ '& .MuiStepLabel-label': { fontSize: 12, overflowWrap: 'anywhere' } }}>{label}</StepLabel>
                </Step>
              ))}
            </Stepper> : null}

            {guided && step === 'configure' ? <Box>
              <Typography variant="h6" sx={{ mb: 1.5 }}>Required connections</Typography>
              {selectedOffer.bindingSlots.length ? selectedOffer.bindingSlots.map(slot => <Stack key={slot.id} direction="row" alignItems="center" justifyContent="space-between" spacing={2} sx={{ py: 1.25, borderBottom: 1, borderColor: 'divider' }}>
                <Box><Typography variant="body2" fontWeight={600}>{slot.kind.replace(/_/g, ' ')}</Typography><Typography variant="caption" color="text.secondary">{slot.id} / {slot.supportedProfileIds.length} reviewed profiles</Typography></Box>
                {slot.kind === 'EXTERNAL_SERVICE' ? <Button href="#approved-connections">Set up</Button> : ['CUSTOMER_S3', 'INBOX_OPERATOR'].includes(slot.kind)
                  ? <Button component={RouterLink} to={`${slot.kind === 'CUSTOMER_S3' ? '/document-knowledge' : '/integrations'}?deploymentId=${encodeURIComponent(selectedDeploymentId)}&view=manage`}>Review</Button>
                  : <Typography variant="caption" color="text.secondary">Reviewed offer binding</Typography>}
              </Stack>) : <Typography variant="body2" color="text.secondary">No connection slots are declared by this offer.</Typography>}
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>Required connections are declared by the offer. Their presence here is not proof of readiness; publication and release verification remain authoritative.</Typography>
            </Box> : null}

            <Box sx={{ display: guided && !['configure', 'staging'].includes(step) ? 'none' : undefined }}>
              <CustomerExternalIntegrationBindings key={JSON.stringify([session.actorId, customerId, selectedOffer.id,
                selectedOffer.fingerprint, selectedOffer.entitlement.revision, selectedDeploymentId])}
                session={session} offer={selectedOffer} deployment={selectedDeployment} version={latestVersion} />
            </Box>

            <Box sx={{ display: showStep('production') ? undefined : 'none' }}>
              <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1} sx={{ mb: 0.5 }}>
                <Typography variant="h6">Production readiness and go-live</Typography>
                {goLiveReadiness ? (
                  <Chip size="small" label={productionActive ? 'Production active' : goLiveReadiness.activationReady ? 'Ready to activate' : 'Activation blocked'}
                    color={productionActive || goLiveReadiness.activationReady ? 'success' : 'warning'} />
                ) : null}
              </Stack>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Candidate-bound evidence is calculated by the Platform. It cannot be completed manually or reused for a different release.
              </Typography>
              {releaseReady(productionRelease) && selectedConsumer ? (
                <Button size="small" variant="outlined" startIcon={<SyncRoundedIcon />} sx={{ mb: 2 }}
                  disabled={!selectedOffer || refreshProductionReadiness.isPending || activateProduction.isPending}
                  onClick={() => refreshProductionReadiness.mutate({ token: readinessGuard.capture() })}>
                  Refresh production readiness
                </Button>
              ) : null}
              {!productionRelease ? (
                <Alert severity="info">Deploy the reviewed production candidate to create its go-live evidence record.</Alert>
              ) : goLiveReadinessQuery.isLoading ? (
                <Stack direction="row" spacing={1.25} alignItems="center"><CircularProgress size={20} />
                  <Typography variant="body2">Loading exact-release evidence...</Typography></Stack>
              ) : goLiveReadinessQuery.error ? (
                <Alert severity="error">{errorText(goLiveReadinessQuery.error)}</Alert>
              ) : (
                <Grid container spacing={1.5}>
                  {readinessItems.map((item) => (
                    <Grid item xs={12} md={6} key={item.code}>
                      <Stack direction="row" spacing={1.25} alignItems="flex-start" sx={{ py: 0.75 }}>
                        {item.status === 'PASSED' || item.status === 'NOT_APPLICABLE'
                          ? <CheckCircleRoundedIcon color={item.status === 'PASSED' ? 'success' : 'disabled'} fontSize="small" />
                          : item.status === 'PENDING'
                            ? <ScheduleRoundedIcon color="disabled" fontSize="small" />
                            : <WarningAmberRoundedIcon color="warning" fontSize="small" />}
                        <Stack spacing={0.5} sx={{ minWidth: 0 }}>
                          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap alignItems="center">
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.label}</Typography>
                            <Chip size="small" variant="outlined" label={item.status.replace(/_/g, ' ').toLowerCase()} />
                            {item.phase === 'POST_ACTIVATION' ? <Chip size="small" variant="outlined" label="After activation" /> : null}
                          </Stack>
                          <Typography variant="caption" color="text.secondary">Owner: {item.owner}</Typography>
                          {item.evidenceReference ? (
                            <Tooltip title={item.evidenceReference}>
                              <Typography variant="caption" color="primary.main" noWrap>Evidence: {item.evidenceReference}</Typography>
                            </Tooltip>
                          ) : null}
                          {item.checkedAt ? (
                            <Typography variant="caption" color="text.secondary">
                              Checked {timestamp(item.checkedAt)}{item.expiresAt ? ` · expires ${timestamp(item.expiresAt)}` : ''}
                            </Typography>
                          ) : null}
                          {item.blocker ? <Typography variant="caption" color="warning.main">{item.blocker}</Typography> : null}
                          {item.nextAction ? <Typography variant="caption">Next: {item.nextAction}</Typography> : null}
                        </Stack>
                      </Stack>
                    </Grid>
                  ))}
                </Grid>
              )}
            </Box>

            {!guided ? <Divider /> : null}
            <Grid container spacing={3}>
              <Grid item xs={12} lg={guided ? 12 : 6} sx={{ display: showStep('staging') ? undefined : 'none' }}>
                <Stack spacing={2}>
                  <Typography variant="h6">Staging release</Typography>
                  {requiresStorageChoice ? <>
                    {storageDraft.isLoading ? <Typography variant="body2">Reading stored AI-state storage...</Typography> : null}
                    {storageDraft.error || storedStorageError ? <Alert severity="error">{storedStorageError ?? errorText(storageDraft.error)}</Alert> : null}
                    {savedStorageMode ? <Chip label={aiStateStorageLabels[savedStorageMode]} variant="outlined" sx={{ alignSelf: 'flex-start' }} /> : null}
                  </> : null}
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip label={latestVersion ? latestVersion.versionLabel : 'No published version'}
                      color={latestVersion ? 'success' : 'warning'} variant="outlined" />
                    <Chip label={stagingRelease?.status ?? 'Not deployed'}
                      color={releaseReady(stagingRelease) ? 'success' : 'default'} variant="outlined" />
                    {activeAdmission ? <Chip label={`Admission ${activeAdmission.state}`} color="info" variant="outlined" /> : null}
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    Publishing freezes configuration. Deploying reserves measured customer-staging capacity before provider effects begin.
                  </Typography>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                    <Button variant="outlined" startIcon={<PublishedWithChangesRoundedIcon />}
                      disabled={publish.isPending || !selectedDeployment.bootstrapTargetProfileId || (requiresStorageChoice
                        && (storageDraft.isFetching || !storageDraft.data || Boolean(storageDraft.error || storedStorageError) || !savedStorageMode))}
                      onClick={() => publish.mutate({ token: publishGuard.capture() })}>
                      Publish current configuration
                    </Button>
                    <Button variant="contained" startIcon={<RocketLaunchRoundedIcon />}
                      disabled={!canApplyReviewedVersion(latestVersion?.id, stagingRelease) || stagingApply.isPending
                        || retirementStarted || retirement?.state === 'COMPLETE'}
                      onClick={() => stagingApply.mutate()}>
                      Deploy to customer staging
                    </Button>
                    {activeAdmission ? (
                      <Button variant="text" startIcon={<ScheduleRoundedIcon />}
                        disabled={extendStaging.isPending || retirementStarted || retirement?.state === 'COMPLETE'}
                        onClick={() => extendStaging.mutate()}>
                        {productionActive ? 'Keep staging available' : 'Extend staging window'}
                      </Button>
                    ) : null}
                  </Stack>
                  {savedStorageMode === 'CUSTOMER_POSTGRES' && latestVersion ? <CustomerAiStateStorageBinding
                    key={JSON.stringify([session.actorId, customerId, selectedOffer.id, selectedOffer.fingerprint,
                      selectedOffer.entitlement.revision, selectedDeploymentId, latestVersion.id])}
                    actorId={session.actorId ?? ''} customerId={customerId} offer={selectedOffer}
                    deployment={selectedDeployment} version={latestVersion} /> : null}
                  {stagingRelease ? (
                    <Alert severity={releaseReady(stagingRelease) ? 'success' : stagingRelease.errorMessage ? 'error' : 'info'}>
                      {stagingRelease.currentStepDescription ?? stagingRelease.status}
                      {stagingRelease.errorMessage ? ` ${stagingRelease.errorMessage}` : ''}
                    </Alert>
                  ) : null}
                  <PreparedFailureRecoveryPanel
                    session={session} offer={selectedOffer} deploymentId={selectedDeploymentId}
                    targetProfileId={stagingTargetProfileId} reservation={activeAdmission} release={stagingRelease}
                    available={!stagingApply.isPending && !retirementStarted && admissionsQuery.isSuccess && !admissionsQuery.isFetching
                      && releasesQuery.isSuccess && !releasesQuery.isFetching && catalogueQuery.isSuccess && !catalogueQuery.isFetching}
                    onRecovered={async (receipt, scope) => {
                      recoveryResult.publish(recoveryResult.capture(), receipt)
                      setAdmission(current => current?.id === scope.reservationId ? null : current)
                      await Promise.all([
                        queryClient.invalidateQueries({ queryKey: ['customer-admission-reservations', scope.offerId,
                          scope.deploymentId, scope.targetProfileId], exact: true }),
                        queryClient.invalidateQueries({ queryKey: ['deployment-releases', scope.deploymentId], exact: true }),
                      ])
                    }}
                  />
                  {recoveryResult.value ? <Alert severity="success" sx={{ overflowWrap: 'anywhere' }}>
                    Prepared failure reconciled. Receipt {recoveryResult.value.receiptId}. Prepared resources and allowance are retained; the original release remains FAILED. No provider retry or financial mutation was performed.
                  </Alert> : null}
                  <FailedApplyRetryPanel session={session} offer={selectedOffer} deploymentId={selectedDeploymentId}
                    targetProfileId={stagingTargetProfileId} reservation={activeAdmission} release={stagingRelease}
                    available={!stagingApply.isPending && !retirementStarted && admissionsQuery.isSuccess && !admissionsQuery.isFetching
                      && releasesQuery.isSuccess && !releasesQuery.isFetching && catalogueQuery.isSuccess && !catalogueQuery.isFetching}
                    onAuthorized={async (receipt, scope) => {
                      failedRetryResult.publish(failedRetryResult.capture(), receipt)
                      setAdmission(current => current?.id === scope.reservationId ? null : current)
                      await Promise.all([
                        queryClient.invalidateQueries({ queryKey: ['customer-admission-reservations', scope.offerId,
                          scope.deploymentId, scope.targetProfileId], exact: true }),
                        queryClient.invalidateQueries({ queryKey: ['deployment-releases', scope.deploymentId], exact: true }),
                      ])
                    }} />
                  {failedRetryResult.value ? <Alert severity="info" sx={{ overflowWrap: 'anywhere' }}>
                    Owned-service redeploy authorized. Receipt {failedRetryResult.value.receiptId}. Runtime and connector redeploy on the same release and reservation, retaining resources and allowance. Deployment completion is not yet verified.
                  </Alert> : null}
                  <PostProvisionResumePanel session={session} offer={selectedOffer} deploymentId={selectedDeploymentId}
                    targetProfileId={stagingTargetProfileId} reservation={activeAdmission} release={stagingRelease}
                    available={!stagingApply.isPending && !retirementStarted && admissionsQuery.isSuccess && !admissionsQuery.isFetching
                      && releasesQuery.isSuccess && !releasesQuery.isFetching && catalogueQuery.isSuccess && !catalogueQuery.isFetching}
                    onAuthorized={async (receipt, scope) => {
                      setupResumeResult.publish(setupResumeResult.capture(), receipt)
                      setAdmission(current => current?.id === scope.reservationId ? null : current)
                      await Promise.all([
                        queryClient.invalidateQueries({ queryKey: ['customer-admission-reservations', scope.offerId,
                          scope.deploymentId, scope.targetProfileId], exact: true }),
                        queryClient.invalidateQueries({ queryKey: ['deployment-releases', scope.deploymentId], exact: true }),
                      ])
                    }} />
                  {setupResumeResult.value ? <Alert severity="info" sx={{ overflowWrap: 'anywhere' }}>
                    Setup continuation authorized. Receipt {setupResumeResult.value.receiptId}. Remaining dependency provisioning, source sync and post-apply verification continue on the same release and reservation. No runtime redeploy is required; resources and allowance are retained. Setup completion is not yet verified.
                  </Alert> : null}
                  <KnownNoEffectSettlementPanel
                    key={JSON.stringify([session.actorId, session.authenticationMode, customerId])}
                    authority={{ actorId: session.actorId ?? '', customerId, authenticationMode: session.authenticationMode }}
                    targetProfileId={stagingTargetProfileId}
                    offerId={selectedOffer.id} deploymentId={selectedDeploymentId} reservation={activeAdmission}
                    candidateFailed={stagingRelease?.status === 'FAILED'}
                    onSettled={async (_, scope) => {
                      setAdmission((current) => current?.id === scope.reservationId ? null : current)
                      await Promise.all([
                        queryClient.invalidateQueries({ queryKey: ['customer-admission-reservations', scope.offerId,
                          scope.deploymentId, scope.targetProfileId], exact: true }),
                        queryClient.invalidateQueries({ queryKey: ['deployment-releases', scope.deploymentId], exact: true }),
                        queryClient.invalidateQueries({ queryKey: ['platform-consumers', scope.authority.customerId], exact: true }),
                      ])
                    }}
                  />
                </Stack>
              </Grid>

              <Grid item xs={12} lg={guided ? 12 : 6} sx={{ display: showStep('configure') ? undefined : 'none' }}>
                <Stack spacing={2}>
                  <Typography variant="h6">Engage assignment</Typography>
                  {deploymentConsumers.length > 0 ? (
                    <TextField select label="Customer app assignment"
                      value={creatingNewAssignment ? '__new_assignment__' : selectedConsumer?.consumerId ?? ''}
                      disabled={createConsumer.isPending}
                      onChange={(event) => {
                        const isNew = event.target.value === '__new_assignment__'
                        setCreatingNewAssignment(isNew)
                        if (!isNew) setSelectedConsumerId(event.target.value)
                      }}>
                      {deploymentConsumers.map((consumer) => (
                        <MenuItem key={consumer.consumerId} value={consumer.consumerId}>
                          {consumer.displayName} · revision {consumer.bindingRevision}{consumer.boundDeploymentId === null ? ' · Unassigned' : ''}
                        </MenuItem>
                      ))}
                      <MenuItem value="__new_assignment__">Create new assignment</MenuItem>
                    </TextField>
                  ) : null}
                  {!selectedConsumer ? (
                    <>
                      <Alert severity="info">Configure the app assignment and Engage app before publishing. Activate the verified release when staging is ready.</Alert>
                      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                        <TextField fullWidth label="Assignment ID" value={consumerId}
                          helperText="Lowercase public lookup key"
                          onChange={(event) => setConsumerId(event.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))} />
                        <TextField fullWidth label="Display name" value={consumerName}
                          onChange={(event) => setConsumerName(event.target.value)} />
                      </Stack>
                      <Button variant="outlined" startIcon={<AddRoundedIcon />}
                        disabled={consumerId.length < 3 || consumerName.trim().length < 2 || createConsumer.isPending}
                        onClick={() => createConsumer.mutate()}>
                        Create staging assignment
                      </Button>
                    </>
                  ) : null}
                  {selectedConsumer ? (
                    <Stack spacing={1}>
                      <Button component={RouterLink} to={`/ai-workspaces?${new URLSearchParams({ view: 'manage', consumerId: selectedConsumer.consumerId })}`}
                        startIcon={<ForumRoundedIcon />} sx={{ alignSelf: 'flex-start' }}>Manage Engage apps for this assignment</Button>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={`Revision ${selectedConsumer.bindingRevision}`} variant="outlined" />
                        <Chip label={selectedConsumer.boundTargetProfileId ?? 'No target'} variant="outlined" />
                        <Chip label={selectedConsumer.boundReleaseStatus ?? 'No verified release'}
                          color={selectedConsumer.boundReleaseStatus === 'APPLIED_VERIFIED' ? 'success' : 'warning'} variant="outlined" />
                      </Stack>
                      {configurationEligible ? <>
                        {assignmentWorkspaces.isLoading ? <Typography variant="body2">Reading owned DRAFT workspaces...</Typography> : null}
                        {assignmentWorkspaces.error || configurationDraftError ? <Alert severity="warning">
                          {configurationDraftError ?? errorText(assignmentWorkspaces.error)}
                        </Alert> : null}
                        {configurationProtectionError ? <Alert severity="warning">{configurationProtectionError}</Alert> : null}
                        {configurationDrafts ? <Stack spacing={0.5} sx={{ overflowWrap: 'anywhere', minWidth: 0 }}>
                          <Typography variant="body2">DRAFT workspaces retained: {configurationDrafts.length}</Typography>
                          {configurationDrafts.map((draft) => <Stack key={draft.installationEntityId} spacing={0.5}>
                            <Typography variant="caption">
                              {draft.installationEntityId} &middot; row version {draft.expectedRowVersion}
                            </Typography>
                            <TextField select fullWidth size="small" label="App settings"
                              InputLabelProps={{ shrink: true }}
                              SelectProps={{ displayEmpty: true, SelectDisplayProps: {
                                'aria-label': `Settings for ${draft.installationEntityId}`, 'aria-labelledby': undefined,
                              } }}
                              value={configurationProfileChoices[draft.installationEntityId] ?? ''}
                              disabled={configureAssignment.isPending || reconcileRejectedAssignment.isPending || configurationHeld || Boolean(configurationDraftError || configurationProtectionError)}
                              onChange={(event) => profileChoiceGuard.publish(profileChoiceGuard.capture(), {
                                ...configurationProfileChoices, [draft.installationEntityId]: event.target.value || null,
                              })}>
                              <MenuItem value="">Keep current settings</MenuItem>
                              {configurationProfiles.map((profile) => <MenuItem key={profile.id} value={profile.id}>{profile.label}</MenuItem>)}
                            </TextField>
                          </Stack>)}
                        </Stack> : null}
                        {configurationHeld ? <Alert severity="warning">
                          Assignment intent is held. Reconcile the original command before any further action.
                        </Alert> : null}
                        {configurationHeld && !configurationProtectionError && configurationIntent
                          && ['REJECTED', 'REVIEWED'].includes(configurationIntent.status) ? <Stack spacing={1}>
                          <Typography variant="body2">Known rejection: SETTING_FORBIDDEN &middot; {configurationIntent.attempt?.operationKey}</Typography>
                          <Button variant="outlined" startIcon={<FactCheckOutlinedIcon />} sx={{ alignSelf: 'flex-start' }}
                            disabled={readRejectedAssignment.isPending || reconcileRejectedAssignment.isPending || !configurationScope}
                            onClick={() => {
                              if (configurationScope) readRejectedAssignment.mutate({ scope: structuredClone(configurationScope), token: configurationGuard.capture() })
                            }}>Review rejected assignment</Button>
                          {currentRejectionReview ? <>
                            {currentRejectionReview.snapshot.draftWorkspaces.map(app => <Typography variant="caption" key={app.installationEntityId}>
                              Current DRAFT: {app.installationEntityId} &middot; row version {app.expectedRowVersion}
                            </Typography>)}
                            <FormControlLabel label="I reviewed the rejected assignment and current DRAFT apps." control={<Checkbox
                              checked={Boolean(currentRejectionConfirmed)} disabled={reconcileRejectedAssignment.isPending}
                              onChange={event => setRejectionConfirmed({ token: configurationGuard.capture(), checked: event.target.checked })} />} />
                            <Button variant="outlined" startIcon={<CheckCircleRoundedIcon />} sx={{ alignSelf: 'flex-start' }}
                              disabled={!currentRejectionConfirmed || reconcileRejectedAssignment.isPending || !configurationScope}
                              onClick={() => {
                                if (configurationScope && currentRejectionReview) reconcileRejectedAssignment.mutate({
                                  scope: structuredClone(configurationScope), token: configurationGuard.capture(), prepared: structuredClone(currentRejectionReview) })
                              }}>Allow new reviewed intent</Button>
                          </> : null}
                        </Stack> : null}
                        <Button variant="outlined" startIcon={<SyncRoundedIcon />} sx={{ alignSelf: 'flex-start' }}
                          disabled={configureAssignment.isPending || reconcileRejectedAssignment.isPending || configurationHeld || assignmentWorkspaces.isFetching
                            || !assignmentWorkspaces.isSuccess || !configurationDrafts
                            || Boolean(configurationDraftError || assignmentWorkspaces.error || configurationProtectionError)}
                          onClick={() => {
                            if (!configurationScope || !assignmentWorkspaces.data || configurationBusy.current || configurationHeld) return
                            try {
                              const key = operationKey('configure-existing-assignment', { scope: configurationScope,
                                expectedDraftWorkspaces: configurationReviewedDrafts })
                              const request = customerAssignmentConfigurationRequest(configurationScope, assignmentWorkspaces.data, key,
                                configurationProfileChoices)
                              const input = { scope: structuredClone(configurationScope), request, token: configurationGuard.capture() }
                              configurationBusy.current = true
                              configureAssignment.mutate(input)
                            } catch (error) {
                              configurationBusy.current = false
                              setNotice({ severity: 'error', message: errorText(error) })
                            }
                          }}>
                          Configure existing assignment
                        </Button>
                      </> : null}
                      {releaseReady(stagingRelease) && selectedConsumer.boundReleaseId !== stagingRelease?.id && !productionActive ? (
                        <Button variant="outlined" startIcon={<SyncRoundedIcon />} sx={{ alignSelf: 'flex-start' }}
                          disabled={bindConsumerToStaging.isPending}
                          onClick={() => bindConsumerToStaging.mutate()}>
                          Use latest verified staging release
                        </Button>
                      ) : null}
                    </Stack>
                  ) : null}
                </Stack>
              </Grid>
            </Grid>

            {!guided ? <Divider /> : null}
            <Stack spacing={2} sx={{ display: showStep('production') ? undefined : 'none' }}>
              <Typography variant="h6">Production promotion</Typography>
              <Typography variant="body2" color="text.secondary">
                The app remains on staging until the production release passes verification and you explicitly activate it.
              </Typography>
              {productionTargets.length > 1 ? (
                <TextField select label="Production target" value={selectedProductionTargetProfileId}
                  onChange={(event) => { setSelectedProductionTargetProfileId(event.target.value); setPromotion(null) }}>
                  {productionTargets.map((target) => (
                    <MenuItem key={target.id} value={target.id} disabled={!targetSelectable(target.readiness)}>
                      {target.id} · {target.readiness}
                    </MenuItem>
                  ))}
                </TextField>
              ) : null}
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={selectedProductionTargetProfileId || 'Select a production target'} variant="outlined" />
                <Chip label={productionRelease?.status ?? 'Production not deployed'}
                  color={releaseReady(productionRelease) ? 'success' : 'default'} variant="outlined" />
                {reviewedPromotion?.candidateFingerprint ? (
                  <Tooltip title={reviewedPromotion.candidateFingerprint}>
                    <Chip icon={<ContentCopyRoundedIcon />} label="Candidate frozen" color="info" variant="outlined" />
                  </Tooltip>
                ) : null}
              </Stack>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} flexWrap="wrap" useFlexGap>
                <Button variant="outlined" startIcon={<CheckCircleRoundedIcon />}
                  disabled={!releaseReady(stagingRelease) || !selectedConsumer || !selectedProductionTargetProfileId || planPromotion.isPending}
                  onClick={() => planPromotion.mutate()}>
                  Review production candidate
                </Button>
                <Button variant="contained" startIcon={<RocketLaunchRoundedIcon />}
                  disabled={!reviewedPromotion?.candidateFingerprint || applyProduction.isPending
                    || !canApplyReviewedVersion(stagingRelease?.deploymentVersionId, productionRelease)}
                  onClick={() => applyProduction.mutate()}>
                  Deploy production candidate
                </Button>
                <Button color="success" variant="contained" startIcon={<PublishedWithChangesRoundedIcon />}
                  disabled={!releaseReady(productionRelease) || !selectedConsumer || activateProduction.isPending
                    || !goLiveReadiness?.activationReady || selectedConsumer.boundReleaseId === productionRelease?.id}
                  onClick={() => activateProduction.mutate()}>
                  Activate production
                </Button>
                {!retirementStarted && retirement?.state !== 'COMPLETE' ? (
                  <Button color="warning" variant="outlined" startIcon={<SyncRoundedIcon />}
                    disabled={!selectedConsumer || !stagingRelease || selectedConsumer.boundReleaseId !== productionRelease?.id
                      || rollbackProduction.isPending || retirement?.state === 'PLANNED'}
                    onClick={() => rollbackProduction.mutate()}>
                    Roll back assignment
                  </Button>
                ) : null}
              </Stack>
              {reviewedPromotion ? <Alert severity={reviewedPromotion.status.includes('FAILED') ? 'error' : 'info'}>{reviewedPromotion.message}</Alert> : null}
            </Stack>

            {!guided ? <Divider /> : null}
            <Stack spacing={2} sx={{ display: showStep('retention') ? undefined : 'none' }}>
              <Typography variant="h6">Customer-staging closure</Typography>
              <Typography variant="body2" color="text.secondary">
                Production activation schedules the admitted staging environment for bounded, auditable retirement. Retain it for an entitled recovery window when rollback access is still required; the admission lifecycle owns drain and resource reconciliation.
              </Typography>
              {activeAdmission ? (
                <>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip label={`Admission ${activeAdmission.state}`} color="info" variant="outlined" />
                    <Chip label={`Lifecycle ${activeAdmission.expiryState}`} variant="outlined" />
                    <Chip label={`Scheduled ${timestamp(activeAdmission.expiresAt)}`} variant="outlined" />
                    {activeAdmission.keepUntil ? (
                      <Chip label={`Retained until ${timestamp(activeAdmission.keepUntil)}`} color="success" variant="outlined" />
                    ) : null}
                  </Stack>
                  {productionActive && !admissionRetirementStarted ? (
                    <Button variant="outlined" startIcon={<ScheduleRoundedIcon />} sx={{ alignSelf: 'flex-start' }}
                      disabled={extendStaging.isPending || retirement?.state === 'COMPLETE'}
                      onClick={() => extendStaging.mutate()}>
                      Retain staging for recovery
                    </Button>
                  ) : null}
                </>
              ) : null}
              {retirement ? (
                <>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip label={retirement.state} color={retirement.state === 'COMPLETE' ? 'success' : 'info'} />
                    <Chip label={retirement.step ?? 'No active step'} variant="outlined" />
                    <Chip label={`Drain deadline ${timestamp(retirement.drainDeadline)}`} variant="outlined" />
                  </Stack>
                  {retirement.blockers.length > 0 ? (
                    <Alert severity="warning">
                      Automated staging retirement is blocked: {retirement.blockers.join(' · ')}. LoomAI operations must resolve the recorded lifecycle evidence; no second customer retirement is started.
                    </Alert>
                  ) : null}
                </>
              ) : null}
              {!productionActive ? (
                <Alert severity="info">Activate the verified production assignment before selecting an optional staging recovery window.</Alert>
              ) : !activeAdmission && !retirement ? (
                <Alert severity="error">No admission or retirement lifecycle evidence is available for this staging target. Promotion is not considered closed.</Alert>
              ) : null}
            </Stack>

            {!guided ? <Divider /> : null}
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} justifyContent="space-between" alignItems={{ md: 'center' }} sx={{ display: showStep('retention') ? undefined : 'none' }}>
              <Stack direction="row" spacing={1} alignItems="center">
                {releaseReady(productionRelease) && selectedConsumer?.boundReleaseId === productionRelease?.id
                  ? <CheckCircleRoundedIcon color="success" />
                  : <WarningAmberRoundedIcon color="warning" />}
                <Box>
                  <Typography sx={{ fontWeight: 700 }}>Go-live state</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Production activation and admission-owned customer-staging retirement remain separate auditable steps.
                  </Typography>
                </Box>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                Release and lifecycle evidence remains attached to this deployment and is visible in the status sections above.
              </Typography>
            </Stack>
          </Stack>
        </Paper>
      ) : null}

      {deployments.length === 0 && offers.length === 0 ? (
        <Alert severity="warning">No active reviewed product offer is available for this customer.</Alert>
      ) : null}
      {guided ? <Stack direction="row" justifyContent="space-between" spacing={2}>
        <Button disabled={step === 'template'} onClick={() => selectStep(journeySteps[Math.max(0, journeySteps.findIndex(item => item.id === step) - 1)].id)}>Previous</Button>
        <Button variant="outlined" disabled={!selectedDeployment || step === 'retention'} onClick={() => selectStep(journeySteps[Math.min(journeySteps.length - 1, journeySteps.findIndex(item => item.id === step) + 1)].id)}>Next stage</Button>
      </Stack> : null}
      {detailSection && selectedDeployment ? <CustomerAdvancedSection><Stack spacing={1} sx={{ borderTop: 1, borderColor: 'divider', pt: 2 }}>
        <Typography variant="h6">Deployment references</Typography>
        {[
          ['Deployment', selectedDeploymentId], ['Offer', selectedDeployment.offerId],
          ['Offer revision', selectedDeployment.offerFingerprint], ['Published version', latestVersion?.id ?? 'Not published'],
          ['Staging release', stagingRelease?.id ?? 'Not deployed'], ['Production release', productionRelease?.id ?? 'Not deployed'],
        ].map(([label,value]) => <Typography key={label} variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{label}: {value}</Typography>)}
      </Stack></CustomerAdvancedSection> : null}
    </Stack>
  )
}

export type PreparedFailureRecoveryScope = {
  actorId: string; customerId: string; authenticationMode: 'SESSION'
  offerId: string; offerFingerprint: string; entitlementRevision: number
  deploymentId: string; targetProfileId: string; reservationId: string; revision: number; releaseId: string
}
type PreparedFailureReview = { scope: PreparedFailureRecoveryScope; plan: CustomerPreparedFailureRecoveryPlan; commandBody: string }
const recoveryText = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0
const recoveryRevision = (value: unknown): value is number => Number.isSafeInteger(value) && Number(value) >= 0 && Number(value) < Number.MAX_SAFE_INTEGER
export const preparedFailureRecoveryHoldScope = (scope: PreparedFailureRecoveryScope) =>
  JSON.stringify(['PREPARED_FAILURE_RECOVERY', scope.customerId, scope.reservationId])

export function prepareCustomerFailureRecovery(scope: PreparedFailureRecoveryScope,
  request: CustomerPreparedFailureRecoveryPlanRequest, response: CustomerPreparedFailureRecoveryPlan): PreparedFailureReview {
  const command = response?.command
  if (!scope || scope.authenticationMode !== 'SESSION' || ![scope.actorId, scope.customerId, scope.offerId, scope.offerFingerprint,
    scope.deploymentId, scope.targetProfileId, scope.reservationId, scope.releaseId].every(recoveryText)
    || !recoveryRevision(scope.revision) || !recoveryRevision(scope.entitlementRevision)
    || !/^[A-Za-z0-9._:-]{1,128}$/.test(request.operationKey) || !recoveryText(request.reason) || request.reason.length > 255
    || !recoveryText(response?.boundary) || !command?.fence || command.fence.reservationId !== scope.reservationId
    || command.fence.revision !== scope.revision || !recoveryText(command.fence.generation)
    || command.releaseId !== scope.releaseId || request.releaseId !== scope.releaseId
    || command.operationKey !== request.operationKey || command.reason !== request.reason
    || !/^[a-f0-9]{64}$/.test(command.proofFingerprint)) {
    throw new Error('The recovery plan does not match this failed release and reservation. Refresh and review current state.')
  }
  const plan = structuredClone(response)
  return { scope: structuredClone(scope), plan, commandBody: JSON.stringify(plan.command) }
}

export function confirmedCustomerFailureRecovery(scope: PreparedFailureRecoveryScope, prepared: PreparedFailureReview, confirmed: boolean) {
  if (!confirmed || JSON.stringify(scope) !== JSON.stringify(prepared.scope)
    || JSON.stringify(prepared.plan.command) !== prepared.commandBody) throw new Error('Confirm the current unchanged recovery plan before submitting.')
  return prepared.plan.command
}

export function checkedCustomerFailureRecoveryReceipt(prepared: PreparedFailureReview,
  receipt: CustomerPreparedFailureRecoverySummary): CustomerPreparedFailureRecoverySummary {
  const command = prepared.plan.command
  if (!receipt || !recoveryText(receipt.receiptId) || receipt.reservationId !== prepared.scope.reservationId
    || receipt.generation !== command.fence.generation || receipt.fromRevision !== prepared.scope.revision
    || receipt.revision !== prepared.scope.revision + 1 || receipt.releaseId !== prepared.scope.releaseId
    || receipt.state !== 'RESERVED' || receipt.providerRetryPerformed !== false || receipt.financialMutationPerformed !== false
    || !Array.isArray(receipt.retainedAllocationIds) || !receipt.retainedAllocationIds.every(recoveryText)
    || new Set(receipt.retainedAllocationIds).size !== receipt.retainedAllocationIds.length
    || !recoveryText(receipt.recoveredAt) || !Number.isFinite(Date.parse(receipt.recoveredAt))) {
    throw new Error('Recovery response is unconfirmed. Do not resubmit; reconcile the original command.')
  }
  return receipt
}

type PreparedFailurePanelProps = {
  session: PlatformAuthSessionSummary; offer: CustomerOfferSummary; deploymentId: string; targetProfileId: string
  reservation: CustomerAdmissionReservationSummary | null; release: DeploymentReleaseSummary | null; available: boolean
  onRecovered: (receipt: CustomerPreparedFailureRecoverySummary, scope: PreparedFailureRecoveryScope) => void | Promise<void>
}

export function PreparedFailureRecoveryPanel(props: PreparedFailurePanelProps) {
  const { session, offer, deploymentId, targetProfileId, reservation, release } = props
  if (!props.available || !session.authenticated || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION'
    || !recoveryText(session.actorId) || !recoveryText(session.customerId) || offer.entitlement.customerId !== session.customerId
    || offer.entitlement.status !== 'ACTIVE' || !offer.targets.some(target => target.id === targetProfileId && target.capabilities.includes('APPLY'))
    || !reservation || reservation.deploymentId !== deploymentId || reservation.targetProfileId !== targetProfileId
    || reservation.state !== 'OUTCOME_UNKNOWN' || reservation.expiryState !== 'CURRENT' || !release
    || release.deploymentId !== deploymentId || release.targetProfileId !== targetProfileId || release.status !== 'FAILED') return null
  const scope: PreparedFailureRecoveryScope = { actorId: session.actorId, customerId: session.customerId, authenticationMode: 'SESSION',
    offerId: offer.id, offerFingerprint: offer.fingerprint, entitlementRevision: offer.entitlement.revision,
    deploymentId, targetProfileId, reservationId: reservation.id, revision: reservation.revision, releaseId: release.id }
  return <PreparedFailureReview key={JSON.stringify(scope)} scope={scope} onRecovered={props.onRecovered} />
}

function PreparedFailureReview({ scope, onRecovered }: { scope: PreparedFailureRecoveryScope; onRecovered: PreparedFailurePanelProps['onRecovered'] }) {
  const queryClient = useQueryClient()
  const guard = useScopedOneTimeValue<boolean>(JSON.stringify(scope))
  const token = guard.capture()
  const current = () => guard.publish(token, true)
  const holds = useRef(createDurableCommandHolds())
  const request = useRef<CustomerPreparedFailureRecoveryPlanRequest | null>(null)
  const busy = useRef(false)
  const [reason, setReason] = useState('')
  const [prepared, setPrepared] = useState<PreparedFailureReview | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const [pending, setPending] = useState<'plan' | 'recover' | null>(null)
  const [error, setError] = useState<string | null>(null)
  let held = false, protectionError: string | null = null
  try { held = Boolean(holds.current.read(preparedFailureRecoveryHoldScope(scope))) }
  catch (failure) { protectionError = errorText(failure) }
  const ownerStillCurrent = async () => {
    const session = await fetchPlatformAuthSession()
    if (!current() || !session.authenticated || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION'
      || session.actorId !== scope.actorId || session.customerId !== scope.customerId) throw new Error('Recovery authority changed. Review current state.')
  }
  async function plan() {
    if (busy.current || held || protectionError || !reason.trim() || !current()) return
    busy.current = true; setPending('plan'); setError(null); setPrepared(null); setConfirmed(false)
    request.current ??= { releaseId: scope.releaseId, operationKey: crypto.randomUUID(), reason: reason.trim() }
    const captured = request.current
    try {
      await ownerStillCurrent()
      const response = await planCustomerPreparedFailureRecovery(scope.offerId, scope.deploymentId, scope.reservationId, captured)
      const review = prepareCustomerFailureRecovery(scope, captured, response)
      if (current()) setPrepared(review)
    } catch (failure) {
      if (current()) setError(failure instanceof Error && 'status' in failure && [403, 409].includes(Number(failure.status))
        ? `Recovery is not eligible: ${errorText(failure)}` : errorText(failure))
    } finally { busy.current = false; if (current()) setPending(null) }
  }
  async function recover() {
    if (busy.current || held || protectionError || !prepared || !confirmed || !current()) return
    busy.current = true; setPending('recover'); setError(null)
    let dispatched = false
    let receipt: CustomerPreparedFailureRecoverySummary | null = null
    try {
      const command = confirmedCustomerFailureRecovery(scope, prepared, confirmed)
      await ownerStillCurrent()
      await holds.current.claim(preparedFailureRecoveryHoldScope(scope), command.operationKey, prepared.commandBody, current)
      if (!current()) return
      dispatched = true
      receipt = checkedCustomerFailureRecoveryReceipt(prepared,
        await recoverCustomerPreparedFailure(scope.offerId, scope.deploymentId, scope.reservationId, command))
    } catch (failure) {
      if (current()) setError(dispatched ? 'Recovery response is unconfirmed. Do not resubmit; reconcile the original command.' : errorText(failure))
    } finally {
      busy.current = false
      if (current()) setPending(null)
      if (dispatched) void queryClient.invalidateQueries({ queryKey: ['customer-admission-reservations', scope.offerId,
        scope.deploymentId, scope.targetProfileId], exact: true }).catch(() => undefined)
    }
    if (receipt) await onRecovered(receipt, structuredClone(scope))
  }
  return <Stack spacing={1} sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
    <Typography variant="subtitle2">Prepared failure recovery</Typography>
    <Typography variant="body2">Failed release: {scope.releaseId}. Recovery retains prepared resources and allowance. Deployment remains a separate explicit action.</Typography>
    <TextField label="Recovery reason" size="small" fullWidth value={reason} inputProps={{ maxLength: 255 }}
      disabled={Boolean(request.current) || pending !== null || held || Boolean(protectionError)} onChange={event => setReason(event.target.value)} />
    {error || protectionError ? <Alert severity="warning">{error ?? protectionError}</Alert> : null}
    {held ? <Alert severity="warning">A recovery intent is recorded. Reconcile the original command before any further recovery action.</Alert> : null}
    <Button variant="outlined" startIcon={<FactCheckOutlinedIcon />} sx={{ alignSelf: 'flex-start' }}
      disabled={pending !== null || held || Boolean(protectionError) || !reason.trim()} onClick={() => void plan()}>Preview prepared failure recovery</Button>
    {prepared ? <>
      <Typography variant="body2">Reservation {scope.reservationId} &middot; revision {prepared.plan.command.fence.revision}</Typography>
      <Typography variant="caption">Boundary: {prepared.plan.boundary}</Typography>
      <FormControlLabel label="I confirm recovery only for this failed release and reservation." control={<Checkbox checked={confirmed}
        disabled={pending !== null || held || Boolean(protectionError)} onChange={event => setConfirmed(event.target.checked)} />} />
      <Button variant="outlined" startIcon={<CheckCircleRoundedIcon />} sx={{ alignSelf: 'flex-start' }}
        disabled={!confirmed || pending !== null || held || Boolean(protectionError)} onClick={() => void recover()}>Recover prepared failure</Button>
    </> : null}
  </Stack>
}

type FailedApplyRetryReview = { scope: PreparedFailureRecoveryScope; plan: CustomerFailedApplyRetryPlan; commandBody: string }
export const failedApplyRetryHoldScope = (scope: PreparedFailureRecoveryScope) => JSON.stringify(['FAILED_APPLY_RETRY', scope.customerId, scope.reservationId])

export function prepareCustomerFailedApplyRetry(scope: PreparedFailureRecoveryScope,
  request: CustomerFailedApplyRetryPlanRequest, response: CustomerFailedApplyRetryPlan): FailedApplyRetryReview {
  if (response?.boundary !== 'INITIAL_COOLIFY_CONNECTOR_STARTUP_FAILURE_V1') throw new Error('Unsupported owned-service retry boundary.')
  if (request.acceptOwnedServiceRedeploy !== true || response?.command?.acceptOwnedServiceRedeploy !== true
    || !Array.isArray(response.redeployedServiceRoles) || JSON.stringify([...response.redeployedServiceRoles].sort()) !== '["connector","runtime"]') {
    throw new Error('Explicit redeploy acceptance and both owned runtime and connector roles are required.')
  }
  const reviewed = prepareCustomerFailureRecovery(scope, request, response)
  return { ...reviewed, plan: structuredClone(response) }
}

export function confirmedCustomerFailedApplyRetry(scope: PreparedFailureRecoveryScope, prepared: FailedApplyRetryReview, confirmed: boolean) {
  confirmedCustomerFailureRecovery(scope, prepared, confirmed)
  return prepared.plan.command
}

export function checkedCustomerFailedApplyRetryReceipt(prepared: FailedApplyRetryReview,
  receipt: CustomerFailedApplyRetrySummary): CustomerFailedApplyRetrySummary {
  if (!receipt || !recoveryText(receipt.receiptId) || receipt.reservationId !== prepared.scope.reservationId
    || receipt.generation !== prepared.plan.command.fence.generation || receipt.fromRevision !== prepared.scope.revision
    || receipt.revision !== prepared.scope.revision + 1 || receipt.releaseId !== prepared.scope.releaseId
    || receipt.status !== 'APPLY_REQUESTED' || receipt.financialMutationPerformed !== false
    || !Array.isArray(receipt.retainedAllocationIds) || !receipt.retainedAllocationIds.every(recoveryText)
    || receipt.retainedAllocationIds.length !== 1
    || new Set(receipt.retainedAllocationIds).size !== receipt.retainedAllocationIds.length
    || !recoveryText(receipt.authorizedAt) || !Number.isFinite(Date.parse(receipt.authorizedAt))) {
    throw new Error('Redeploy authorization response is unconfirmed. Do not resubmit; reconcile the original command.')
  }
  return receipt
}

type FailedApplyRetryPanelProps = Omit<PreparedFailurePanelProps, 'onRecovered'> & {
  onAuthorized: (receipt: CustomerFailedApplyRetrySummary, scope: PreparedFailureRecoveryScope) => void | Promise<void>
}
export function FailedApplyRetryPanel(props: FailedApplyRetryPanelProps) {
  const { session, offer, deploymentId, targetProfileId, reservation, release } = props
  if (!props.available || !session.authenticated || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION'
    || !recoveryText(session.actorId) || !recoveryText(session.customerId) || offer.entitlement.customerId !== session.customerId
    || offer.entitlement.status !== 'ACTIVE' || !offer.targets.some(target => target.id === targetProfileId && target.capabilities.includes('APPLY'))
    || !reservation || reservation.deploymentId !== deploymentId || reservation.targetProfileId !== targetProfileId
    || reservation.state !== 'OUTCOME_UNKNOWN' || reservation.expiryState !== 'CURRENT' || !release
    || release.deploymentId !== deploymentId || release.targetProfileId !== targetProfileId || release.status !== 'FAILED') return null
  const scope: PreparedFailureRecoveryScope = { actorId: session.actorId, customerId: session.customerId, authenticationMode: 'SESSION',
    offerId: offer.id, offerFingerprint: offer.fingerprint, entitlementRevision: offer.entitlement.revision,
    deploymentId, targetProfileId, reservationId: reservation.id, revision: reservation.revision, releaseId: release.id }
  return <FailedApplyRetryReview key={JSON.stringify(scope)} scope={scope} onAuthorized={props.onAuthorized} />
}

function FailedApplyRetryReview({ scope, onAuthorized }: { scope: PreparedFailureRecoveryScope; onAuthorized: FailedApplyRetryPanelProps['onAuthorized'] }) {
  const queryClient = useQueryClient()
  const guard = useScopedOneTimeValue<boolean>(JSON.stringify(scope)), token = guard.capture()
  const current = () => guard.publish(token, true)
  const holds = useRef(createDurableCommandHolds()), request = useRef<CustomerFailedApplyRetryPlanRequest | null>(null), busy = useRef(false)
  const [reason, setReason] = useState(''), [accepted, setAccepted] = useState(false), [confirmed, setConfirmed] = useState(false)
  const [prepared, setPrepared] = useState<FailedApplyRetryReview | null>(null)
  const [pending, setPending] = useState<'plan' | 'retry' | null>(null), [error, setError] = useState<string | null>(null)
  let held = false, protectionError: string | null = null
  try { held = Boolean(holds.current.read(failedApplyRetryHoldScope(scope))) }
  catch (failure) { protectionError = errorText(failure) }
  const ownerStillCurrent = async () => {
    const session = await fetchPlatformAuthSession()
    if (!current() || !session.authenticated || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION'
      || session.actorId !== scope.actorId || session.customerId !== scope.customerId) throw new Error('Redeploy authority changed. Review current state.')
  }
  async function plan() {
    if (busy.current || held || protectionError || !accepted || !reason.trim() || !current()) return
    busy.current = true; setPending('plan'); setError(null); setPrepared(null); setConfirmed(false)
    request.current ??= { releaseId: scope.releaseId, operationKey: crypto.randomUUID(), reason: reason.trim(), acceptOwnedServiceRedeploy: true }
    try {
      await ownerStillCurrent()
      const review = prepareCustomerFailedApplyRetry(scope, request.current,
        await planCustomerFailedApplyRetry(scope.offerId, scope.deploymentId, scope.reservationId, request.current))
      if (current()) setPrepared(review)
    } catch (failure) {
      if (current()) setError(failure instanceof Error && 'status' in failure && [403, 409].includes(Number(failure.status))
        ? `Redeploy retry is not eligible: ${errorText(failure)}` : errorText(failure))
    } finally { busy.current = false; if (current()) setPending(null) }
  }
  async function retry() {
    if (busy.current || held || protectionError || !prepared || !confirmed || !current()) return
    busy.current = true; setPending('retry'); setError(null)
    let dispatched = false, receipt: CustomerFailedApplyRetrySummary | null = null
    try {
      const command = confirmedCustomerFailedApplyRetry(scope, prepared, confirmed)
      await ownerStillCurrent()
      await holds.current.claim(failedApplyRetryHoldScope(scope), command.operationKey, prepared.commandBody, current)
      if (!current()) return
      dispatched = true
      receipt = checkedCustomerFailedApplyRetryReceipt(prepared,
        await retryCustomerFailedApply(scope.offerId, scope.deploymentId, scope.reservationId, command))
    } catch (failure) {
      if (current()) setError(dispatched ? 'Redeploy authorization response is unconfirmed. Do not resubmit; reconcile the original command.' : errorText(failure))
    } finally {
      busy.current = false
      if (current()) setPending(null)
      if (dispatched) void queryClient.invalidateQueries({ queryKey: ['customer-admission-reservations', scope.offerId,
        scope.deploymentId, scope.targetProfileId], exact: true }).catch(() => undefined)
    }
    if (receipt) await onAuthorized(receipt, structuredClone(scope))
  }
  return <Stack spacing={1} sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
    <Typography variant="subtitle2">Retry failed owned services</Typography>
    <Alert severity="warning">This authorizes real redeploys of both runtime and connector, not an effect-free recovery or a connector-only retry. The same release, reservation, resources and allowance are retained.</Alert>
    <TextField label="Redeploy reason" size="small" fullWidth value={reason} inputProps={{ maxLength: 255 }}
      disabled={Boolean(request.current) || pending !== null || held || Boolean(protectionError)} onChange={event => setReason(event.target.value)} />
    <FormControlLabel label="I accept redeployment of both owned runtime and connector services." control={<Checkbox checked={accepted}
      disabled={Boolean(request.current) || pending !== null || held || Boolean(protectionError)} onChange={event => setAccepted(event.target.checked)} />} />
    {error || protectionError ? <Alert severity="warning">{error ?? protectionError}</Alert> : null}
    {held ? <Alert severity="warning">A redeploy intent is recorded. Reconcile the original command before any further retry.</Alert> : null}
    <Button variant="outlined" startIcon={<FactCheckOutlinedIcon />} sx={{ alignSelf: 'flex-start' }}
      disabled={!accepted || !reason.trim() || pending !== null || held || Boolean(protectionError)} onClick={() => void plan()}>Preview owned-service retry</Button>
    {prepared ? <>
      <Typography variant="body2">Release {scope.releaseId} &middot; reservation {scope.reservationId} &middot; revision {prepared.plan.command.fence.revision}</Typography>
      <Typography variant="body2">Redeployed roles: {prepared.plan.redeployedServiceRoles.join(', ')}</Typography>
      <Typography variant="caption">Boundary: {prepared.plan.boundary}</Typography>
      <FormControlLabel label="I confirm this plan authorizes real runtime and connector redeploys." control={<Checkbox checked={confirmed}
        disabled={pending !== null || held || Boolean(protectionError)} onChange={event => setConfirmed(event.target.checked)} />} />
      <Button variant="outlined" startIcon={<SyncRoundedIcon />} sx={{ alignSelf: 'flex-start' }}
        disabled={!confirmed || pending !== null || held || Boolean(protectionError)} onClick={() => void retry()}>Authorize owned-service redeploy</Button>
    </> : null}
  </Stack>
}

type PostProvisionResumeReview = { scope: PreparedFailureRecoveryScope; plan: CustomerPostProvisionResumePlan; commandBody: string }
export const postProvisionResumeHoldScope = (scope: PreparedFailureRecoveryScope) => JSON.stringify(['POST_PROVISION_RESUME', scope.customerId, scope.reservationId])

export function prepareCustomerPostProvisionResume(scope: PreparedFailureRecoveryScope,
  request: CustomerPostProvisionResumePlanRequest, response: CustomerPostProvisionResumePlan): PostProvisionResumeReview {
  if (response?.boundary !== 'COOLIFY_DEPENDENCY_ADMISSION_FAILED_AFTER_PROVISIONING_V1'
    || request.acceptRemainingDependencyProvisioning !== true || response?.command?.acceptRemainingDependencyProvisioning !== true
    || response.runtimeRedeployRequired !== false
    || JSON.stringify(response.remainingPhases) !== '["managed_dependencies","source_sync","post_apply_verification"]'
    || !Array.isArray(response.retainedAllocationIds) || response.retainedAllocationIds.length !== 1 || !response.retainedAllocationIds.every(recoveryText)) {
    throw new Error('The setup plan must preserve one allocation, require no runtime redeploy and include all remaining phases for the supported boundary.')
  }
  const reviewed = prepareCustomerFailureRecovery(scope, request, response)
  return { ...reviewed, plan: structuredClone(response) }
}

export function confirmedCustomerPostProvisionResume(scope: PreparedFailureRecoveryScope, prepared: PostProvisionResumeReview, confirmed: boolean) {
  confirmedCustomerFailureRecovery(scope, prepared, confirmed)
  return prepared.plan.command
}

export function checkedCustomerPostProvisionResumeReceipt(prepared: PostProvisionResumeReview,
  receipt: CustomerPostProvisionResumeSummary): CustomerPostProvisionResumeSummary {
  if (!receipt || !recoveryText(receipt.receiptId) || receipt.reservationId !== prepared.scope.reservationId
    || receipt.generation !== prepared.plan.command.fence.generation || receipt.fromRevision !== prepared.scope.revision
    || receipt.revision !== prepared.scope.revision + 1 || receipt.releaseId !== prepared.scope.releaseId
    || receipt.status !== 'APPLY_REQUESTED' || receipt.financialMutationPerformed !== false || receipt.runtimeRedeployRequired !== false
    || JSON.stringify(receipt.retainedAllocationIds) !== JSON.stringify(prepared.plan.retainedAllocationIds)
    || !recoveryText(receipt.authorizedAt) || !Number.isFinite(Date.parse(receipt.authorizedAt))) {
    throw new Error('Setup continuation response is unconfirmed. Do not resubmit; reconcile the original command.')
  }
  return receipt
}

type PostProvisionResumePanelProps = Omit<PreparedFailurePanelProps, 'onRecovered'> & {
  onAuthorized: (receipt: CustomerPostProvisionResumeSummary, scope: PreparedFailureRecoveryScope) => void | Promise<void>
}
export function PostProvisionResumePanel(props: PostProvisionResumePanelProps) {
  const { session, offer, deploymentId, targetProfileId, reservation, release } = props
  if (!props.available || !session.authenticated || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION'
    || !recoveryText(session.actorId) || !recoveryText(session.customerId) || offer.entitlement.customerId !== session.customerId
    || offer.entitlement.status !== 'ACTIVE' || !offer.targets.some(target => target.id === targetProfileId && target.capabilities.includes('APPLY'))
    || !reservation || reservation.deploymentId !== deploymentId || reservation.targetProfileId !== targetProfileId
    || reservation.state !== 'OUTCOME_UNKNOWN' || reservation.expiryState !== 'CURRENT' || !release
    || release.deploymentId !== deploymentId || release.targetProfileId !== targetProfileId || release.status !== 'FAILED') return null
  const scope: PreparedFailureRecoveryScope = { actorId: session.actorId, customerId: session.customerId, authenticationMode: 'SESSION',
    offerId: offer.id, offerFingerprint: offer.fingerprint, entitlementRevision: offer.entitlement.revision,
    deploymentId, targetProfileId, reservationId: reservation.id, revision: reservation.revision, releaseId: release.id }
  return <PostProvisionResumeReview key={JSON.stringify(scope)} scope={scope} releaseStatus={release.status} onAuthorized={props.onAuthorized} />
}

function PostProvisionResumeReview({ scope, releaseStatus, onAuthorized }: { scope: PreparedFailureRecoveryScope; releaseStatus: string; onAuthorized: PostProvisionResumePanelProps['onAuthorized'] }) {
  const queryClient = useQueryClient()
  const guard = useScopedOneTimeValue<boolean>(JSON.stringify(scope)), token = guard.capture()
  const current = () => guard.publish(token, true)
  const holds = useRef(createPostProvisionResumeHolds()), request = useRef<CustomerPostProvisionResumePlanRequest | null>(null), busy = useRef(false)
  const holdContext = { ...scope, releaseStatus }
  const [reason, setReason] = useState(''), [accepted, setAccepted] = useState(false), [confirmed, setConfirmed] = useState(false)
  const [prepared, setPrepared] = useState<PostProvisionResumeReview | null>(null)
  const [pending, setPending] = useState<'plan' | 'resume' | null>(null), [error, setError] = useState<string | null>(null)
  let held = false, protectionError: string | null = null
  try { held = holds.current.read(holdContext).held }
  catch (failure) { protectionError = errorText(failure) }
  const ownerStillCurrent = async () => {
    const session = await fetchPlatformAuthSession()
    if (!current() || !session.authenticated || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION'
      || session.actorId !== scope.actorId || session.customerId !== scope.customerId) throw new Error('Setup authority changed. Review current state.')
  }
  async function plan() {
    if (busy.current || held || protectionError || !accepted || !reason.trim() || !current()) return
    busy.current = true; setPending('plan'); setError(null); setPrepared(null); setConfirmed(false)
    request.current ??= { releaseId: scope.releaseId, operationKey: crypto.randomUUID(), reason: reason.trim(), acceptRemainingDependencyProvisioning: true }
    try {
      await ownerStillCurrent()
      if (holds.current.read(holdContext).held) throw new Error('The original setup intent remains held. Reconcile its outcome before a new review.')
      const review = prepareCustomerPostProvisionResume(scope, request.current,
        await planCustomerPostProvisionResume(scope.offerId, scope.deploymentId, scope.reservationId, request.current))
      holds.current.review(holdContext, review.plan.command, review.plan.retainedAllocationIds[0])
      if (current()) setPrepared(review)
    } catch (failure) {
      if (current()) setError(failure instanceof Error && 'status' in failure && [403, 409].includes(Number(failure.status))
        ? `Setup continuation is not eligible: ${errorText(failure)}` : errorText(failure))
    } finally { busy.current = false; if (current()) setPending(null) }
  }
  async function resume() {
    if (busy.current || held || protectionError || !prepared || !confirmed || !current()) return
    busy.current = true; setPending('resume'); setError(null)
    let dispatched = false, receipt: CustomerPostProvisionResumeSummary | null = null
    try {
      const command = confirmedCustomerPostProvisionResume(scope, prepared, confirmed)
      await ownerStillCurrent()
      await holds.current.claim(holdContext, command, prepared.plan.retainedAllocationIds[0], current)
      if (!current()) return
      dispatched = true
      const acceptedReceipt = checkedCustomerPostProvisionResumeReceipt(prepared,
        await resumeCustomerPostProvisioning(scope.offerId, scope.deploymentId, scope.reservationId, command))
      await holds.current.acknowledge(holdContext, command, acceptedReceipt)
      receipt = acceptedReceipt
    } catch (failure) {
      if (current()) setError(dispatched ? 'Setup continuation response is unconfirmed. Do not resubmit; reconcile the original command.' : errorText(failure))
    } finally {
      busy.current = false
      if (current()) setPending(null)
      if (dispatched) void queryClient.invalidateQueries({ queryKey: ['customer-admission-reservations', scope.offerId,
        scope.deploymentId, scope.targetProfileId], exact: true }).catch(() => undefined)
    }
    if (receipt) await onAuthorized(receipt, structuredClone(scope))
  }
  return <Stack spacing={1} sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
    <Typography variant="subtitle2">Resume setup</Typography>
    <Alert severity="info">Continue remaining dependency provisioning, source sync and post-apply verification. No runtime redeploy is required. Existing resources and allowance are retained; setup may perform remaining provisioning work.</Alert>
    <TextField label="Setup continuation reason" size="small" fullWidth value={reason} inputProps={{ maxLength: 255 }}
      disabled={Boolean(request.current) || pending !== null || held || Boolean(protectionError)} onChange={event => setReason(event.target.value)} />
    <FormControlLabel label="I accept the remaining dependency provisioning work." control={<Checkbox checked={accepted}
      disabled={Boolean(request.current) || pending !== null || held || Boolean(protectionError)} onChange={event => setAccepted(event.target.checked)} />} />
    {error || protectionError ? <Alert severity="warning">{error ?? protectionError}</Alert> : null}
    {held ? <Alert severity="warning">A setup continuation intent is recorded. Reconcile the original command before any further resume.</Alert> : null}
    <Button variant="outlined" startIcon={<FactCheckOutlinedIcon />} sx={{ alignSelf: 'flex-start' }}
      disabled={!accepted || !reason.trim() || pending !== null || held || Boolean(protectionError)} onClick={() => void plan()}>Preview setup continuation</Button>
    {prepared ? <>
      <Typography variant="body2">Release {scope.releaseId} &middot; reservation {scope.reservationId} &middot; revision {prepared.plan.command.fence.revision}</Typography>
      <Typography variant="body2">Remaining phases: {prepared.plan.remainingPhases.join(', ')}</Typography>
      <Typography variant="caption">Boundary: {prepared.plan.boundary}</Typography>
      <FormControlLabel label="I confirm this setup plan and its remaining provisioning work." control={<Checkbox checked={confirmed}
        disabled={pending !== null || held || Boolean(protectionError)} onChange={event => setConfirmed(event.target.checked)} />} />
      <Button variant="outlined" startIcon={<SyncRoundedIcon />} sx={{ alignSelf: 'flex-start' }}
        disabled={!confirmed || pending !== null || held || Boolean(protectionError)} onClick={() => void resume()}>Resume setup</Button>
    </> : null}
  </Stack>
}
