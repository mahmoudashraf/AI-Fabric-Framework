import AddRoundedIcon from '@mui/icons-material/AddRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import EditRoundedIcon from '@mui/icons-material/EditRounded'
import PauseCircleOutlineRoundedIcon from '@mui/icons-material/PauseCircleOutlineRounded'
import PlayCircleOutlineRoundedIcon from '@mui/icons-material/PlayCircleOutlineRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import CodeRoundedIcon from '@mui/icons-material/CodeRounded'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import PaletteOutlinedIcon from '@mui/icons-material/PaletteOutlined'
import LinkRoundedIcon from '@mui/icons-material/LinkRounded'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
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
  TextField,
  Tooltip,
  ToggleButton,
  ToggleButtonGroup,
  Tabs,
  Tab,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  activateAIWorkspaceInstallation,
  createAIWorkspaceInstallation,
  deleteAIWorkspaceInstallation,
  disableAIWorkspaceInstallation,
  fetchAIWorkspaceCatalog,
  fetchAIWorkspaceInstallations,
  fetchAIWorkspacePreviewCapabilities,
  fetchCustomerDeploymentPortfolio,
  fetchCustomerSelfServiceCatalogue,
  fetchPlatformAuthSession,
  fetchPlatformCustomers,
  getPlatformApiBaseUrl,
  type AIWorkspaceInstallationSummary,
  type PlatformCustomerSummary,
  type SaveAIWorkspaceInstallationRequest,
  updateAIWorkspaceInstallation,
  verifyAIWorkspaceDomain,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { resolveReviewedWorkspaceProfiles, reviewedWorkspaceRequest } from './aiWorkspaceReviewedProfiles'
import { CustomerAdvancedSection, CustomerEditorDepthSwitch, CustomerEditorNotices, useCustomerEditorSection } from '../components/customer/CustomerDetailSections'
import { customerEngageSelection } from '../components/customer/customerEngageSelection'
import { prepareCustomerEngageCommand, type CustomerEngageCommandContext } from '../components/customer/customerEngageCommands'
import { EngageAppearanceFields } from '../components/customer/EngageAppearanceFields'
import { EngageAppearancePreview } from '../components/customer/EngageAppearancePreview'
import { CustomerEngageConnectionPanel } from '../components/customer/CustomerEngageConnectionPanel'
import { EngageDomainInstructions } from '../components/customer/EngageDomainInstructions'
import { EngageDeploymentUpdatePanel } from '../components/customer/EngageDeploymentUpdatePanel'
import { applyWorkspaceAppearance, readWorkspaceAppearance, workspaceAppearanceError, type WorkspaceAppearance } from './aiWorkspaceAppearance'
import type { PreviewInteractionPolicy } from '../components/customer/buildWorkspacePreviewConfiguration'

type FormState = {
  packCode: string
  deliveryMode: string
  appSlug: string
  customDomains: string
  interactionPolicy: string
  authProfileCode: string
  supabaseProjectUrl: string
  supabasePublishableKey: string
  supabaseEmailPassword: boolean
  supabaseMagicLink: boolean
  displayName: string
  consumerId: string
  connectionMode: string
  profileCode: string
  origins: string
  dealerId: string
  assistantLabel: string
  sourceMode: string
  rootSelector: string
  contextLabel: string
  primaryColor: string
  comparison: boolean
  testDrive: boolean
  callback: boolean
  imageHosts: string
  detailBasePath: string
  detailSlugs: string
  shopDomain: string
}

const defaultForm: FormState = {
  packCode: 'generic-workspace',
  deliveryMode: 'standalone',
  appSlug: '',
  customDomains: '',
  interactionPolicy: 'public-with-protected-capabilities',
  authProfileCode: 'supabase',
  supabaseProjectUrl: '',
  supabasePublishableKey: '',
  supabaseEmailPassword: true,
  supabaseMagicLink: true,
  displayName: '',
  consumerId: '',
  connectionMode: 'public-runtime-anonymous',
  profileCode: 'runtime-anonymous-direct',
  origins: '',
  dealerId: '',
  assistantLabel: 'LoomAI Workspace',
  sourceMode: 'DEALERSHIP_INVENTORY',
  rootSelector: 'main',
  contextLabel: 'Current dealership page',
  primaryColor: '#123b35',
  comparison: true,
  testDrive: true,
  callback: true,
  imageHosts: '',
  detailBasePath: '/vehicles/',
  detailSlugs: '',
  shopDomain: '',
}

function statusColor(status: string): 'success' | 'warning' | 'default' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DISABLED') return 'warning'
  return 'default'
}

function errorText(error: unknown) {
  return error instanceof Error ? error.message : 'The operation failed.'
}

export function AIWorkspacesPage({ initialInstallationId, initialConsumerId, sourceInstallationId, initialDeliveryMode, autoPreview, connectDeploymentId, connectInstallationId }: {
  initialInstallationId?: string; initialConsumerId?: string; sourceInstallationId?: string
  initialDeliveryMode?: 'embedded' | 'standalone'
  autoPreview?: boolean
  connectDeploymentId?: string; connectInstallationId?: string
} = {}) {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const customerScopeLocked = auth.session?.role === 'CUSTOMER_ADMIN'
  const detailSection = useCustomerEditorSection()
  const focused = Boolean(initialInstallationId && customerScopeLocked && detailSection)
  const [customerId, setCustomerId] = useState(() => customerScopeLocked ? (auth.session?.customerId ?? '') : '')
  const [selected, setSelected] = useState<AIWorkspaceInstallationSummary | null>(null)
  const [editing, setEditing] = useState<AIWorkspaceInstallationSummary | 'new' | null>(null)
  const [form, setForm] = useState<FormState>(defaultForm)
  const [appearance, setAppearance] = useState<WorkspaceAppearance>(() => readWorkspaceAppearance({}, defaultForm.packCode))
  const [appearanceEdited, setAppearanceEdited] = useState(false)
  const appearanceSource = useRef('')
  const initialPreview = useRef('')
  const [editorTab, setEditorTab] = useState('settings')
  const [notice, setNotice] = useState<string | null>(null)
  const [reviewedProfileId, setReviewedProfileId] = useState('')
  const [configurationError, setConfigurationError] = useState<string | null>(null)
  const [pendingDestructive, setPendingDestructive] = useState<{ command: 'disable' | 'delete'; installation: AIWorkspaceInstallationSummary; actorId: string | null | undefined } | null>(null)
  const initialSelection = useRef('')
  const initialCreate = useRef('')
  const [selectionError, setSelectionError] = useState<string | null>(null)
  const [connectionPanel, setConnectionPanel] = useState<{ fromEditor: boolean; deploymentId?: string; consumerId?: string; app?: AIWorkspaceInstallationSummary | null; chooseApp?: boolean } | null>(null)
  const initialConnect = useRef('')
  const saveCommand = useRef<{ identity: string; key: string } | null>(null)
  const commandContext = useRef<CustomerEngageCommandContext | null>(null)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])

  const customersQuery = useQuery({ queryKey: ['platform-customers'], queryFn: fetchPlatformCustomers })
  const catalogQuery = useQuery({ queryKey: ['ai-workspace-catalog'], queryFn: fetchAIWorkspaceCatalog })
  const installationsQuery = useQuery({
    queryKey: ['ai-workspace-installations', customerId],
    queryFn: () => fetchAIWorkspaceInstallations(customerId),
    enabled: Boolean(customerId),
    refetchInterval: query => query.state.data?.some(item => item.domains.some(domain => domain.status === 'ROUTING_PENDING')) ? 4000 : false,
  })
  const offersQuery = useQuery({
    queryKey: ['customer-self-service-catalogue', customerId],
    queryFn: fetchCustomerSelfServiceCatalogue,
    enabled: customerScopeLocked && Boolean(customerId),
  })
  const portfolioQuery = useQuery({
    queryKey: ['customer-deployment-portfolio', customerId],
    queryFn: fetchCustomerDeploymentPortfolio,
    enabled: customerScopeLocked && Boolean(customerId),
  })

  const customers = customersQuery.data ?? []
  const customer = customers.find((item) => item.id === customerId) ?? null
  commandContext.current = customerScopeLocked && portfolioQuery.isSuccess
    && customersQuery.isSuccess && !portfolioQuery.isFetching && !customersQuery.isFetching
    ? { actorId: auth.session?.actorId, customerId, consumers: customer?.consumers ?? [],
      deployments: portfolioQuery.data } : null
  const prepareCommand = async (consumerIds: string | string[], requireRelease = false) => {
    if (customerScopeLocked) await prepareCustomerEngageCommand(
      () => mounted.current ? commandContext.current : null, consumerIds, fetchPlatformAuthSession, { requireRelease },
    )
  }
  const installations = installationsQuery.data ?? []
  useEffect(() => {
    if (!initialInstallationId || !installationsQuery.isSuccess) return
    const scope = JSON.stringify([customerId, initialInstallationId])
    if (initialSelection.current === scope) return
    initialSelection.current = scope
    const requested = installations.find(item => item.installationId === initialInstallationId
      && item.customerId === customerId)
    setSelected(requested ?? null)
    setSelectionError(requested ? null : 'The requested app is not available in this customer account. No other app was selected.')
  }, [customerId, initialInstallationId, installations, installationsQuery.isSuccess])
  const catalog = catalogQuery.data
  const compatibleProfiles = useMemo(
    () => (catalog?.connectionProfiles ?? []).filter((profile) => profile.mode === form.connectionMode),
    [catalog?.connectionProfiles, form.connectionMode],
  )
  const detailSlugMappings = useMemo(() => parseMappings(form.detailSlugs), [form.detailSlugs])
  const consumer = customer?.consumers.find((item) => item.consumerId === form.consumerId)
  const reviewedDeployment = portfolioQuery.data?.find((item) => item.deploymentId === consumer?.boundDeploymentId)
  const reviewedOffer = offersQuery.data?.find((item) => item.id === reviewedDeployment?.offerId
    && item.fingerprint === reviewedDeployment.offerFingerprint
    && item.entitlement.customerId === customerId && item.entitlement.status === 'ACTIVE')
  const reviewedScopeReady = Boolean(consumer?.customerId === customerId && consumer.status === 'ACTIVE'
    && reviewedDeployment && consumer.boundTargetProfileId)
  const reviewedProfilesQuery = useQuery({
    queryKey: ['ai-workspace-reviewed-profiles', customerId, reviewedOffer?.id, reviewedOffer?.fingerprint,
      reviewedOffer?.settings, reviewedOffer?.workspaceProfiles,
      installations.map((item) => [item.installationId, item.rowVersion])],
    queryFn: () => resolveReviewedWorkspaceProfiles(reviewedOffer ?? null, customerId, installations),
    enabled: customerScopeLocked && editing != null && reviewedScopeReady
      && installationsQuery.isSuccess,
    retry: false,
  })
  const reviewedProfiles = reviewedScopeReady ? (reviewedProfilesQuery.data ?? []) : []
  const reviewedProfile = reviewedProfiles.find((item) => item.id === reviewedProfileId) ?? null
  const effectiveDeliveryMode = form.deliveryMode
  const appearanceError = workspaceAppearanceError(appearance)
  const editingActive = editing !== 'new' && editing?.status === 'ACTIVE'
  const experienceConfiguration = customerScopeLocked ? reviewedProfile?.configuration.configuration ?? {}
    : editing !== 'new' && editing ? editing.configuration : {}
  const experiencePackCode = customerScopeLocked ? reviewedProfile?.configuration.experiencePackCode ?? form.packCode : form.packCode
  const previewCapabilitiesQuery = useQuery({
    queryKey: ['ai-workspace-preview-capabilities', customerId, consumer?.consumerId, consumer?.bindingRevision],
    queryFn: () => fetchAIWorkspacePreviewCapabilities(customerId, consumer!.consumerId),
    enabled: editing != null && Boolean(customerId && consumer?.consumerId), retry: false,
  })
  const previewInteractionPolicy = (customerScopeLocked ? reviewedProfile?.configuration.interactionPolicy ?? form.interactionPolicy
    : form.interactionPolicy) as PreviewInteractionPolicy
  const previewAuthProfile = customerScopeLocked ? reviewedProfile?.configuration.authProfileCode ?? form.authProfileCode : form.authProfileCode
  const previewRuntime = previewCapabilitiesQuery.data
  const previewCapabilities = {
    history: true, attachments: true, sources: true, currentPage: true,
    authentication: { signInAvailable: previewInteractionPolicy !== 'public' && previewAuthProfile !== 'none' },
    handover: { enabled: previewRuntime?.handover.enabled === true, runtimeConfigured: previewRuntime?.runtimeConfigured ?? null,
      userCanCancelPending: previewRuntime?.handover.userCanCancelPending === true,
      userCanResumeAi: previewRuntime?.handover.userCanResumeAi === true, intake: previewRuntime?.handover.intake },
  }

  useEffect(() => {
    if (!customerScopeLocked || !reviewedProfile || appearanceSource.current === reviewedProfile.id) return
    appearanceSource.current = reviewedProfile.id
    if (appearanceEdited) return
    setAppearance(readWorkspaceAppearance(reviewedProfile.configuration.configuration, reviewedProfile.configuration.experiencePackCode))
    setAppearanceEdited(false)
  }, [customerScopeLocked, reviewedProfile, appearanceEdited])

  useEffect(() => {
    if (!customerScopeLocked || editing == null || !reviewedProfilesQuery.isSuccess) return
    setReviewedProfileId((current) => reviewedProfiles.some((item) => item.id === current) ? current
      : editing === 'new' ? reviewedProfiles[0]?.id ?? ''
        : reviewedProfiles.find((item) => item.id === editing.installationId)?.id ?? '')
  }, [customerScopeLocked, editing, reviewedProfilesQuery.data, reviewedProfilesQuery.isSuccess, reviewedScopeReady])

  useEffect(() => {
    if (customerScopeLocked && auth.session?.customerId && customerId !== auth.session.customerId) {
      setCustomerId(auth.session.customerId)
      return
    }
    if (!customerId && customers.length > 0) setCustomerId(customers[0].id)
  }, [auth.session?.customerId, customerId, customerScopeLocked, customers])

  useEffect(() => {
    if (selected) {
      const refreshed = installations.find((item) => item.installationId === selected.installationId)
      setSelected(refreshed ?? null)
    }
  }, [installations]) // eslint-disable-line react-hooks/exhaustive-deps

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['ai-workspace-installations', customerId] })
  }

  const saveMutation = useMutation({
    mutationFn: async ({ payload, operationKey }: { payload: SaveAIWorkspaceInstallationRequest; operationKey: string }) => {
      const installationId = editing === 'new' ? null : editing?.installationId
      if (editing == null) throw new Error('Open an app configuration before saving.')
      await prepareCommand(editing !== 'new' && editing.consumerId
        ? [editing.consumerId, payload.consumerId] : payload.consumerId)
      return installationId ? updateAIWorkspaceInstallation(customerId, installationId, payload, operationKey)
        : createAIWorkspaceInstallation(customerId, payload, operationKey)
    },
    onSuccess: async (installation) => {
      saveCommand.current = null
      setNotice(`AI Workspace ${installation.displayName} saved.`)
      setEditing(null)
      setSelected(installation)
      await invalidate()
    },
  })

  const activateMutation = useMutation({
    mutationFn: async ({ installation, operationKey }: { installation: AIWorkspaceInstallationSummary; operationKey: string }) => {
      await prepareCommand(installation.consumerId ?? '', true)
      return activateAIWorkspaceInstallation(installation.customerId, installation.installationId, operationKey)
    },
    onSuccess: async (installation) => {
      setNotice(`AI Workspace ${installation.displayName} activated.`)
      setSelected(installation)
      await invalidate()
    },
  })

  const disableMutation = useMutation({
    mutationFn: async ({ installation, operationKey }: { installation: AIWorkspaceInstallationSummary; operationKey: string }) => {
      await prepareCommand(installation.consumerId ?? '')
      return disableAIWorkspaceInstallation(installation.customerId, installation.installationId, operationKey)
    },
    onSuccess: async (installation) => {
      setNotice(`AI Workspace ${installation.displayName} disabled.`)
      setSelected(installation)
      setEditing(current => current !== 'new' && current?.installationId === installation.installationId ? installation : current)
      await invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: async ({ installation, operationKey }: { installation: AIWorkspaceInstallationSummary; operationKey: string }) => {
      await prepareCommand(installation.consumerId ?? '')
      return deleteAIWorkspaceInstallation(installation.customerId, installation.installationId, operationKey)
    },
    onSuccess: async () => {
      setNotice('Draft AI Workspace deleted.')
      setSelected(null)
      await invalidate()
    },
  })

  const openCreate = (source?: AIWorkspaceInstallationSummary, deliveryMode = initialDeliveryMode ?? 'standalone') => {
    saveMutation.reset()
    saveCommand.current = null
    const firstProfile = catalog?.connectionProfiles.find((profile) => profile.mode === defaultForm.connectionMode)
    const selection = customerScopeLocked ? customerEngageSelection(customer?.consumers ?? [], customerId, source?.consumerId ?? initialConsumerId)
      : { consumerId: customer?.consumers.find(consumer => consumer.status === 'ACTIVE')?.consumerId ?? '', error: null }
    setForm({
      ...defaultForm,
      deliveryMode,
      displayName: source ? `${source.displayName} - ${deliveryMode === 'embedded' ? 'website' : 'app'}`.slice(0, 255) : '',
      consumerId: selection.consumerId,
      profileCode: firstProfile?.code ?? defaultForm.profileCode,
    })
    setEditing('new')
    setAppearance(readWorkspaceAppearance(source?.configuration ?? {}, source?.experiencePackCode ?? defaultForm.packCode))
    appearanceSource.current = source?.installationId ?? ''
    setAppearanceEdited(false)
    setEditorTab('settings')
    setReviewedProfileId(source?.installationId ?? '')
    setConfigurationError(selection.error)
  }

  useEffect(() => {
    if (!sourceInstallationId || !customer || !catalog?.assetsReady || !installationsQuery.isSuccess) return
    const request = JSON.stringify([customerId, sourceInstallationId, initialDeliveryMode])
    if (initialCreate.current === request) return
    initialCreate.current = request
    const source = installations.find(item => item.installationId === sourceInstallationId && item.customerId === customerId)
    if (!source) {
      setSelectionError('The source Engage app is not available in this account. No other configuration was selected.')
      return
    }
    openCreate(source)
  }, [sourceInstallationId, initialDeliveryMode, customerId, Boolean(customer), catalog?.assetsReady, installationsQuery.isSuccess])

  useEffect(() => {
    if ((!connectDeploymentId && !connectInstallationId) || !customerScopeLocked || !customer
      || !portfolioQuery.isSuccess || !installationsQuery.isSuccess) return
    const key = JSON.stringify([customerId, connectDeploymentId, connectInstallationId])
    if (initialConnect.current === key) return
    initialConnect.current = key
    const app = connectInstallationId ? installations.find(item => item.installationId === connectInstallationId) : null
    if (connectInstallationId && !app) { setSelectionError('The requested app is not available in this account.'); return }
    if (connectDeploymentId && !portfolioQuery.data.some(item => item.deploymentId === connectDeploymentId)) {
      setSelectionError('The requested deployment is not available in this account.'); return
    }
    setConnectionPanel({ fromEditor: false, deploymentId: connectDeploymentId ?? app?.deploymentId ?? undefined,
      consumerId: app?.consumerId ?? undefined, app, chooseApp: !connectInstallationId })
  }, [connectDeploymentId, connectInstallationId, customerId, customerScopeLocked, Boolean(customer), portfolioQuery.isSuccess, installationsQuery.isSuccess])

  const openEdit = (installation: AIWorkspaceInstallationSummary) => {
    saveMutation.reset()
    saveCommand.current = null
    const config = installation.configuration ?? {}
    const dealer = config.dealer ?? {}
    const page = config.page ?? {}
    const capabilities = config.capabilities ?? {}
    const presentation = config.presentation ?? {}
    const theme = config.theme ?? {}
    setAppearance(readWorkspaceAppearance(config, installation.experiencePackCode))
    appearanceSource.current = installation.installationId
    setAppearanceEdited(false)
    setEditorTab('settings')
    setForm({
      packCode: installation.experiencePackCode,
      deliveryMode: installation.deliveryMode,
      appSlug: installation.appSlug ?? '',
      customDomains: installation.domains
        .filter((domain) => domain.type === 'CUSTOMER_CUSTOM')
        .map((domain) => domain.hostname)
        .join('\n'),
      interactionPolicy: installation.interactionPolicy,
      authProfileCode: installation.authProfileCode,
      supabaseProjectUrl: String(installation.authConfiguration.projectUrl ?? ''),
      supabasePublishableKey: String(installation.authConfiguration.publishableKey ?? ''),
      supabaseEmailPassword: Array.isArray(installation.authConfiguration.methods)
        && installation.authConfiguration.methods.includes('email-password'),
      supabaseMagicLink: Array.isArray(installation.authConfiguration.methods)
        && installation.authConfiguration.methods.includes('magic-link'),
      displayName: installation.displayName,
      consumerId: installation.consumerId ?? '',
      connectionMode: installation.connectionMode,
      profileCode: installation.connectionProfileCode,
      origins: installation.allowedOrigins.join('\n'),
      dealerId: dealer.id ?? '',
      assistantLabel: dealer.assistantLabel ?? 'LoomAI Workspace',
      sourceMode: dealer.sourceMode ?? 'DEALERSHIP_INVENTORY',
      rootSelector: page.rootSelector ?? 'main',
      contextLabel: page.contextLabel ?? 'Current dealership page',
      primaryColor: theme.primaryColor ?? '#123b35',
      comparison: capabilities.comparison !== false,
      testDrive: capabilities.testDrive === true,
      callback: capabilities.callback === true,
      imageHosts: Array.isArray(presentation.imageHostAllowlist) ? presentation.imageHostAllowlist.join('\n') : '',
      detailBasePath: presentation.detailBasePath ?? '/vehicles/',
      detailSlugs: formatMappings(presentation.detailSlugs),
      shopDomain: String(installation.connectionConfiguration.shopDomain ?? ''),
    })
    setEditing(installation)
    setReviewedProfileId(installation.installationId)
    setConfigurationError(null)
  }

  useEffect(() => {
    if (!autoPreview || !initialInstallationId || !selected || selected.installationId !== initialInstallationId) return
    const key = `${customerId}:${initialInstallationId}`
    if (initialPreview.current === key) return
    initialPreview.current = key
    openEdit(selected)
  }, [autoPreview, initialInstallationId, selected, customerId])

  const saveWorkspace = (payload: SaveAIWorkspaceInstallationRequest) => {
    const identity = JSON.stringify({ customerId, installationId: editing === 'new' ? null : editing?.installationId, payload })
    if (saveCommand.current?.identity !== identity) saveCommand.current = { identity, key: crypto.randomUUID() }
    saveMutation.mutate({ payload, operationKey: saveCommand.current.key })
  }

  const submit = () => {
    setConfigurationError(null)
    if (editingActive) { setConfigurationError('Disable this installation before saving. The live app has not changed.'); return }
    if (appearanceError) { setConfigurationError(appearanceError); return }
    if (customerScopeLocked) {
      if (!reviewedScopeReady || !reviewedProfile) {
        setConfigurationError('Select an available starter or existing app configuration before saving.')
        return
      }
      try {
        const payload = reviewedWorkspaceRequest(reviewedOffer ?? null, reviewedProfile, {
          consumerId: form.consumerId,
          displayName: form.displayName,
          deliveryMode: effectiveDeliveryMode as 'standalone' | 'embedded',
          appSlug: effectiveDeliveryMode === 'standalone' ? form.appSlug.trim() : undefined,
          customDomains: effectiveDeliveryMode === 'standalone' ? lines(form.customDomains) : [],
          allowedOrigins: lines(form.origins),
          ...(editing !== 'new' && editing ? { rowVersion: editing.rowVersion } : {}),
        })
        if (appearanceEdited) payload.configuration = applyWorkspaceAppearance(payload.configuration, payload.experiencePackCode, appearance)
        saveWorkspace(payload)
      } catch (error) {
        setConfigurationError(errorText(error))
      }
      return
    }
    const profile = catalog?.connectionProfiles.find((item) => item.code === form.profileCode)
    const pack = catalog?.experiencePacks.find((item) => item.code === form.packCode)
    const authProfile = catalog?.authenticationProfiles.find((item) => item.code === form.authProfileCode)
    const genericConfiguration = {
      app: { assistantLabel: form.assistantLabel.trim() },
      page: {
        rootSelector: form.rootSelector.trim(),
        contextLabel: form.contextLabel.trim(),
        maxChars: 1800,
        maxPages: 3,
        maxTotalChars: 10000,
      },
      capabilities: { currentPage: true, handover: true },
      theme: {
        primaryColor: form.primaryColor,
        borderRadius: '0.5rem',
        fontFamily: 'Inter, system-ui, sans-serif',
        darkMode: false,
      },
    }
    const dealershipConfiguration = {
      dealer: {
        id: form.dealerId.trim(),
        assistantLabel: form.assistantLabel.trim(),
        sourceMode: form.sourceMode.trim(),
      },
      page: {
        kind: 'auto',
        rootSelector: form.rootSelector.trim(),
        contextLabel: form.contextLabel.trim(),
        maxChars: 1800,
        maxPages: 3,
        maxTotalChars: 10000,
      },
      knowledge: { inventoryVectorSpace: 'dealer-vehicle' },
      capabilities: { comparison: form.comparison, testDrive: form.testDrive, callback: form.callback },
      presentation: {
        detailBasePath: form.detailBasePath.trim(),
        imageHostAllowlist: lines(form.imageHosts),
        detailSlugs: detailSlugMappings.values,
      },
      theme: {
        primaryColor: form.primaryColor,
        borderRadius: '0.5rem',
        fontFamily: 'Inter, system-ui, sans-serif',
        darkMode: false,
      },
    }
    const payload: SaveAIWorkspaceInstallationRequest = {
      consumerId: form.consumerId,
      displayName: form.displayName.trim(),
      experiencePackCode: form.packCode,
      experiencePackVersion: pack?.version ?? '1.0.0',
      connectionMode: form.connectionMode,
      connectionProfileCode: form.profileCode,
      connectionProfileVersion: profile?.version ?? '1.0.0',
      connectionConfiguration: form.connectionMode === 'backend-mediated-private-runtime'
        ? { shopDomain: form.shopDomain.trim() }
        : {},
      allowedOrigins: lines(form.origins),
      configuration: form.packCode === 'dealership' ? dealershipConfiguration : genericConfiguration,
      deliveryMode: form.deliveryMode,
      appSlug: form.deliveryMode === 'standalone' ? form.appSlug.trim() : undefined,
      customDomains: form.deliveryMode === 'standalone' ? lines(form.customDomains) : [],
      interactionPolicy: form.interactionPolicy,
      authProfileCode: form.authProfileCode,
      authProfileVersion: authProfile?.version ?? '1.0.0',
      authConfiguration: form.authProfileCode === 'supabase' ? {
        projectUrl: form.supabaseProjectUrl.trim(),
        publishableKey: form.supabasePublishableKey.trim(),
        methods: [
          ...(form.supabaseEmailPassword ? ['email-password'] : []),
          ...(form.supabaseMagicLink ? ['magic-link'] : []),
        ],
      } : {},
      ...(editing !== 'new' && editing ? { rowVersion: editing.rowVersion } : {}),
    }
    payload.configuration = applyWorkspaceAppearance(payload.configuration, payload.experiencePackCode, appearance)
    saveWorkspace(payload)
  }

  const operationError = saveMutation.error || activateMutation.error || disableMutation.error || deleteMutation.error
  const appCommandPending = saveMutation.isPending || activateMutation.isPending || disableMutation.isPending || deleteMutation.isPending
  const requestDestructive = (command: 'disable' | 'delete', installation: AIWorkspaceInstallationSummary) => {
    if (customerScopeLocked) setPendingDestructive({ command, installation, actorId: auth.session?.actorId })
    else if (command === 'disable') disableMutation.mutate({ installation, operationKey: crypto.randomUUID() })
    else deleteMutation.mutate({ installation, operationKey: crypto.randomUUID() })
  }
  const pendingInstallation = installations.find(item => item.installationId === pendingDestructive?.installation.installationId
    && item.customerId === customerId && item.rowVersion === pendingDestructive.installation.rowVersion
    && item.status === pendingDestructive.installation.status)
  const pendingCurrent = Boolean(pendingInstallation && pendingDestructive?.actorId === auth.session?.actorId)
  const installationActions = (installation: AIWorkspaceInstallationSummary) => <>
    <Tooltip title="Edit"><span><IconButton aria-label="Edit app" size="small" disabled={appCommandPending || installation.status === 'ACTIVE'} onClick={() => openEdit(installation)}><EditRoundedIcon /></IconButton></span></Tooltip>
    {installation.status === 'ACTIVE' ? (
      detailSection !== 'configuration' ? <Tooltip title="Disable"><span><IconButton aria-label="Disable app" size="small" disabled={appCommandPending} onClick={() => requestDestructive('disable', installation)}><PauseCircleOutlineRoundedIcon /></IconButton></span></Tooltip> : null
    ) : <Tooltip title="Activate"><span><IconButton aria-label="Activate app" size="small" disabled={appCommandPending || !installation.ready} onClick={() => activateMutation.mutate({ installation, operationKey: crypto.randomUUID() })}><PlayCircleOutlineRoundedIcon /></IconButton></span></Tooltip>}
    {installation.status === 'DRAFT' && detailSection !== 'configuration' ? <Tooltip title="Delete draft"><span><IconButton aria-label="Delete draft app" size="small" color="error" disabled={appCommandPending} onClick={() => requestDestructive('delete', installation)}><DeleteOutlineRoundedIcon /></IconButton></span></Tooltip> : null}
  </>

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800 }}>Engage apps</Typography>
          <Typography color="text.secondary">
            Embed on your website, publish a standalone app, or use both with the same deployment.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ maxWidth: '100%' }}>
          {customerScopeLocked ? (
            <Chip label={auth.session?.customerName ?? customer?.name ?? 'My customer account'} variant="outlined" />
          ) : (
            <TextField
              select
              size="small"
              label="Customer"
              value={customerId}
              onChange={(event) => { setCustomerId(event.target.value); setSelected(null) }}
              sx={{ minWidth: 260, maxWidth: '100%' }}
            >
              {customers.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
            </TextField>
          )}
          {!focused ? <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => openCreate()} disabled={appCommandPending || !customer || !catalog?.assetsReady}>
            Create Engage app
          </Button> : null}
        </Stack>
      </Stack>

      {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
      {selectionError ? <Alert severity="error">{selectionError}</Alert> : null}
      {initialInstallationId && installationsQuery.isError ? <Alert severity="error">{errorText(installationsQuery.error)}</Alert> : null}
      {operationError && !(editing != null && operationError === saveMutation.error) && <Alert severity="error">{errorText(operationError)}</Alert>}
      {catalog && !catalog.assetsReady && <Alert severity="warning">Workspace assets are not release-ready: {catalog.assetStatus}</Alert>}

      {focused && selected ? <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}><Typography fontWeight={600}>{selected.displayName}</Typography><Box>{installationActions(selected)}</Box></Stack> : null}
      <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1, display: focused ? 'none' : undefined }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Delivery</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>{customerScopeLocked ? 'AI deployment' : 'Consumer'}</TableCell>
              <TableCell>Connection</TableCell>
              <TableCell>Assignment</TableCell>
              <TableCell>Origins</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {installations.map((installation) => (
              <TableRow
                hover
                key={installation.installationId}
                selected={selected?.installationId === installation.installationId}
                onClick={() => setSelected(installation)}
                sx={{ cursor: 'pointer' }}
              >
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{installation.displayName}</Typography>
                  <CustomerAdvancedSection><Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                    {installation.installationId}
                  </Typography></CustomerAdvancedSection>
                </TableCell>
                <TableCell>
                  <Typography variant="body2">{installation.deliveryMode === 'standalone' ? 'Standalone app' : 'Embedded'}</Typography>
                  <Typography variant="caption" color="text.secondary">{installation.appUrl ?? installation.experiencePackCode}</Typography>
                </TableCell>
                <TableCell><Chip size="small" label={installation.status} color={statusColor(installation.status)} /></TableCell>
                <TableCell>{customerScopeLocked ? portfolioQuery.data?.find(item => item.deploymentId === installation.deploymentId)?.name ?? 'Not connected' : installation.consumerId ?? 'Unavailable'}</TableCell>
                <TableCell>
                  <Typography variant="body2">{connectionLabel(installation.connectionMode)}</Typography>
                  <Typography variant="caption" color="text.secondary">{installation.connectionProfileCode}</Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    icon={installation.ready ? <CheckCircleRoundedIcon /> : <WarningAmberRoundedIcon />}
                    label={installation.ready ? 'Ready' : 'Blocked'}
                    color={installation.ready ? 'success' : 'warning'}
                    variant="outlined"
                  />
                </TableCell>
                <TableCell>{installation.allowedOrigins.length}</TableCell>
                <TableCell align="right" onClick={(event) => event.stopPropagation()}>
                  {installationActions(installation)}
                </TableCell>
              </TableRow>
            ))}
            {!installationsQuery.isLoading && installations.length === 0 && (!initialInstallationId || installationsQuery.isSuccess) && (
              <TableRow><TableCell colSpan={8}><Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>No AI Workspace installations for this customer.</Typography></TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {selected && <WorkspaceDetails installation={selected} refresh={invalidate}
        customize={() => openEdit(selected)}
        deploymentName={portfolioQuery.data?.find(item => item.deploymentId === selected.deploymentId)?.name}
        connect={customerScopeLocked ? () => setConnectionPanel({ fromEditor: false, deploymentId: selected.deploymentId ?? undefined, consumerId: selected.consumerId ?? undefined, app: selected }) : undefined}
        addDelivery={(mode) => openCreate(selected, mode)} addDeliveryDisabled={appCommandPending || !catalog?.assetsReady} />}

      <Dialog open={editing != null} onClose={appCommandPending ? undefined : () => setEditing(null)} maxWidth={false} fullWidth
        PaperProps={{ sx: { width: 'min(1440px, calc(100vw - 32px))', maxWidth: 'none', m: 2, height: 'calc(100dvh - 32px)' } }}>
        <DialogTitle>{editing === 'new' ? 'Create Engage app' : 'Appearance & preview'}</DialogTitle>
        <DialogContent dividers>
          <CustomerEditorNotices />
          <CustomerEditorDepthSwitch label="App configuration depth" />
          <Tabs value={editorTab} onChange={(_, value) => setEditorTab(value)} aria-label="Engage editor view" sx={{ display: { xs: 'flex', lg: 'none' }, mb: 2 }}>
            <Tab value="settings" label="Settings" /><Tab value="preview" label="Preview" />
          </Tabs>
          <Box sx={{ display: { lg: 'grid' }, gridTemplateColumns: 'minmax(320px, 0.85fr) minmax(420px, 1.15fr)', gap: 3, alignItems: 'start' }}>
          <Stack component="fieldset" disabled={appCommandPending} spacing={2.5} sx={{ border: 0, p: 0, m: 0, minWidth: 0, display: { xs: editorTab === 'settings' ? 'flex' : 'none', lg: 'flex' } }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField fullWidth label="Workspace name" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
              {!customerScopeLocked ? <TextField select fullWidth label="Consumer" value={form.consumerId} onChange={(e) => { setForm({ ...form, consumerId: e.target.value }); setConfigurationError(null) }}>
                {(customer?.consumers ?? []).filter((consumer) => consumer.status === 'ACTIVE').map((consumer) => (
                  <MenuItem key={consumer.consumerId} value={consumer.consumerId}>{consumer.displayName} ({consumer.consumerId})</MenuItem>
                ))}
              </TextField> : null}
            </Stack>
            {customerScopeLocked ? <Stack spacing={1}>
              <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1}>
                <Box><Typography variant="subtitle2">AI deployment</Typography><Typography variant="body2" color="text.secondary">{reviewedDeployment?.name ?? 'Not connected'}</Typography>
                  {consumer?.boundTargetProfileId ? <Typography variant="caption" color="text.secondary">{consumer.boundDeploymentEnvironment ?? 'Selected environment'} / {consumer.boundReleaseId ? 'Runtime connected' : 'Configuration draft'}</Typography> : null}</Box>
                <Button startIcon={<LinkRoundedIcon />} disabled={editingActive} onClick={() => setConnectionPanel({ fromEditor: true,
                  deploymentId: consumer?.boundDeploymentId ?? undefined, consumerId: form.consumerId,
                  app: editing !== 'new' ? editing : null })}>{consumer ? 'Change deployment' : 'Connect deployment'}</Button>
              </Stack>
              <CustomerAdvancedSection><Typography variant="caption">Assignment: {form.consumerId || 'Not connected'} / revision {consumer?.bindingRevision ?? '-'}</Typography></CustomerAdvancedSection>
            </Stack> : null}
            <EngageAppearanceFields value={appearance} error={appearanceEdited ? appearanceError : null}
              onChange={value => { setAppearance(value); setAppearanceEdited(true) }}
              onReset={() => { setAppearance(readWorkspaceAppearance(experienceConfiguration, experiencePackCode)); setAppearanceEdited(false) }} />
            <Divider />
            {customerScopeLocked ? (
              <Stack spacing={1.5}>
                <TextField select fullWidth label="Experience configuration" value={reviewedProfile?.id ?? ''}
                  disabled={reviewedProfilesQuery.isFetching || reviewedProfiles.length === 0}
                  onChange={(event) => { setReviewedProfileId(event.target.value); setConfigurationError(null) }}>
                  {reviewedProfiles.map((profile) => (
                    <MenuItem key={profile.id} value={profile.id}>
                      {profile.label} · {profile.configuration.experiencePackCode}
                    </MenuItem>
                  ))}
                </TextField>
                {portfolioQuery.isFetching || reviewedProfilesQuery.isFetching ? (
                  <Typography variant="body2" color="text.secondary">Loading experience configurations...</Typography>
                ) : portfolioQuery.error || reviewedProfilesQuery.error || installationsQuery.error ? (
                  <Alert severity="error">{errorText(portfolioQuery.error || reviewedProfilesQuery.error || installationsQuery.error)}</Alert>
                ) : !reviewedScopeReady ? (
                  <Alert severity="info" action={<Button onClick={() => setConnectionPanel({ fromEditor: true })}>Connect deployment</Button>}>Connect one of your deployments to save this app. Appearance preview is available now.</Alert>
                ) : reviewedProfiles.length === 0 ? (
                  <Alert severity="warning">
                    No starter or existing app configuration is available for this deployment.
                  </Alert>
                ) : !reviewedProfile ? (
                  <Alert severity="warning">Select a starter or existing app configuration before saving.</Alert>
                ) : null}
                {offersQuery.isError ? <Alert severity="warning">Starter configurations could not be loaded. Your existing app configurations remain available.</Alert> : null}
                {reviewedProfile ? (
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Tooltip title={reviewedProfile.interactionProfileId}><Chip label={reviewedProfile.configuration.experiencePackCode} variant="outlined" /></Tooltip>
                    <Tooltip title={reviewedProfile.authProfileId}><Chip label={reviewedProfile.configuration.interactionPolicy} variant="outlined" /></Tooltip>
                    <Tooltip title={reviewedProfile.themeProfileId}><Chip label="Saved theme" variant="outlined" /></Tooltip>
                  </Stack>
                ) : null}
                {reviewedProfile ? <CustomerAdvancedSection><Stack spacing={0.5}>
                  <Typography variant="caption" color="text.secondary">Interaction profile: {reviewedProfile.interactionProfileId}</Typography>
                  <Typography variant="caption" color="text.secondary">Authentication profile: {reviewedProfile.authProfileId}</Typography>
                  <Typography variant="caption" color="text.secondary">Theme profile: {reviewedProfile.themeProfileId}</Typography>
                </Stack></CustomerAdvancedSection> : null}
              </Stack>
            ) : null}
            {!customerScopeLocked ? (
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                <TextField select fullWidth label="Experience pack" value={form.packCode} onChange={(e) => setForm({ ...form, packCode: e.target.value })}>
                  {(catalog?.experiencePacks ?? []).map((pack) => <MenuItem key={pack.code} value={pack.code} disabled={!pack.enabled}>{pack.name}</MenuItem>)}
                </TextField>
              </Stack>
            ) : null}
            <Stack spacing={1}>
              <Typography variant="subtitle2">Delivery</Typography>
              <ToggleButtonGroup exclusive fullWidth value={form.deliveryMode} aria-label="Engage delivery"
                onChange={(_, mode: string | null) => { if (mode) setForm({ ...form, deliveryMode: mode }) }}>
                <ToggleButton value="embedded" sx={{ gap: 1 }}><CodeRoundedIcon fontSize="small" />Embed on my website</ToggleButton>
                <ToggleButton value="standalone" sx={{ gap: 1 }}><OpenInNewRoundedIcon fontSize="small" />Standalone app</ToggleButton>
              </ToggleButtonGroup>
              <Typography variant="caption" color="text.secondary">Add both as separate Engage installations connected to the same deployment.</Typography>
            </Stack>
            {effectiveDeliveryMode === 'standalone' && (
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                <TextField fullWidth label="App slug" helperText="Creates <slug>.apps.loomai.pro" value={form.appSlug} onChange={(e) => setForm({ ...form, appSlug: e.target.value.toLowerCase() })} />
                <TextField fullWidth multiline minRows={2} label="Custom domains" helperText="Optional exact hostnames, one per line" value={form.customDomains} onChange={(e) => setForm({ ...form, customDomains: e.target.value })} />
              </Stack>
            )}
            {(!customerScopeLocked || effectiveDeliveryMode === 'embedded') ? (
              <TextField required={effectiveDeliveryMode === 'embedded'} multiline minRows={2} label="Allowed website origins" helperText="Exact HTTPS origins, for example https://www.example.com. No paths or wildcards." value={form.origins} onChange={(e) => setForm({ ...form, origins: e.target.value })} />
            ) : null}
            {!customerScopeLocked ? (
              <>
                <Divider />
                <Typography variant="subtitle2">Interaction and identity</Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField
                    select fullWidth label="Interaction policy" value={form.interactionPolicy}
                    onChange={(e) => {
                      const policy = e.target.value
                      const authenticated = policy === 'login-required'
                      const isPublic = policy === 'public'
                      const mode = authenticated ? 'public-runtime-authenticated' : 'public-runtime-anonymous'
                      const profile = catalog?.connectionProfiles.find((item) => item.mode === mode && item.enabled)
                      setForm({ ...form, interactionPolicy: policy, authProfileCode: isPublic ? 'none' : 'supabase', connectionMode: mode, profileCode: profile?.code ?? '' })
                    }}
                  >
                    <MenuItem value="public-with-protected-capabilities">Public with protected capabilities</MenuItem>
                    <MenuItem value="login-required">Login required</MenuItem>
                    <MenuItem value="public">Public only</MenuItem>
                  </TextField>
                  <TextField select fullWidth label="Authentication profile" value={form.authProfileCode} disabled={form.interactionPolicy === 'public'} onChange={(e) => setForm({ ...form, authProfileCode: e.target.value })}>
                    {(catalog?.authenticationProfiles ?? []).map((profile) => <MenuItem key={profile.code} value={profile.code} disabled={!profile.enabled || (form.interactionPolicy !== 'public' && profile.code === 'none')}>{profile.name}</MenuItem>)}
                  </TextField>
                </Stack>
                {form.authProfileCode === 'supabase' && (
                  <>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                      <TextField fullWidth label="Supabase project URL" value={form.supabaseProjectUrl} onChange={(e) => setForm({ ...form, supabaseProjectUrl: e.target.value })} />
                      <TextField fullWidth label="Supabase publishable key" value={form.supabasePublishableKey} onChange={(e) => setForm({ ...form, supabasePublishableKey: e.target.value })} />
                    </Stack>
                    <Stack direction="row" spacing={2} flexWrap="wrap">
                      <FormControlLabel control={<Checkbox checked={form.supabaseEmailPassword} onChange={(e) => setForm({ ...form, supabaseEmailPassword: e.target.checked })} />} label="Email and password" />
                      <FormControlLabel control={<Checkbox checked={form.supabaseMagicLink} onChange={(e) => setForm({ ...form, supabaseMagicLink: e.target.checked })} />} label="Magic link" />
                    </Stack>
                  </>
                )}
                <Divider />
                <Typography variant="subtitle2">Connection</Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField
                    select fullWidth label="Connection mode" value={form.connectionMode}
                    onChange={(e) => {
                      const mode = e.target.value
                      const profile = catalog?.connectionProfiles.find((item) => item.mode === mode && item.enabled)
                      setForm({ ...form, connectionMode: mode, profileCode: profile?.code ?? '' })
                    }}
                  >
                    <MenuItem value="public-runtime-anonymous">Public anonymous runtime</MenuItem>
                    <MenuItem value="public-runtime-authenticated">Public authenticated runtime</MenuItem>
                    <MenuItem value="backend-mediated-private-runtime">Backend-mediated private runtime</MenuItem>
                  </TextField>
                  <TextField select fullWidth label="Reviewed profile" value={form.profileCode} onChange={(e) => setForm({ ...form, profileCode: e.target.value })}>
                    {compatibleProfiles.map((profile) => (
                      <MenuItem key={profile.code} value={profile.code} disabled={!profile.enabled}>
                        {profile.name}{profile.enabled ? '' : ` - ${profile.availabilityMessage}`}
                      </MenuItem>
                    ))}
                  </TextField>
                </Stack>
                {form.connectionMode === 'backend-mediated-private-runtime' && (
                  <TextField label="Shopify store domain" helperText="Reviewed Shopify Bridge profile only" value={form.shopDomain} onChange={(e) => setForm({ ...form, shopDomain: e.target.value })} />
                )}
                <Divider />
                <Typography variant="subtitle2">{form.packCode === 'dealership' ? 'Dealership experience' : 'Generic experience'}</Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  {form.packCode === 'dealership' && <TextField fullWidth label="Dealership ID" value={form.dealerId} onChange={(e) => setForm({ ...form, dealerId: e.target.value })} />}
                  {form.packCode === 'dealership' && <TextField fullWidth label="Source mode" value={form.sourceMode} onChange={(e) => setForm({ ...form, sourceMode: e.target.value })} />}
                </Stack>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField fullWidth label="Page content selector" value={form.rootSelector} onChange={(e) => setForm({ ...form, rootSelector: e.target.value })} />
                  <TextField fullWidth label="Default context label" value={form.contextLabel} onChange={(e) => setForm({ ...form, contextLabel: e.target.value })} />
                </Stack>
                {form.packCode === 'dealership' && <Stack direction="row" spacing={2} flexWrap="wrap">
                  <FormControlLabel control={<Checkbox checked={form.comparison} onChange={(e) => setForm({ ...form, comparison: e.target.checked })} />} label="Vehicle comparison" />
                  <FormControlLabel control={<Checkbox checked={form.testDrive} onChange={(e) => setForm({ ...form, testDrive: e.target.checked })} />} label="Test-drive request" />
                  <FormControlLabel control={<Checkbox checked={form.callback} onChange={(e) => setForm({ ...form, callback: e.target.checked })} />} label="Callback request" />
                </Stack>}
                {form.packCode === 'dealership' && <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField fullWidth label="Vehicle detail path" value={form.detailBasePath} onChange={(e) => setForm({ ...form, detailBasePath: e.target.value })} />
                  <TextField fullWidth multiline minRows={2} label="Approved image hosts" helperText="One hostname per line" value={form.imageHosts} onChange={(e) => setForm({ ...form, imageHosts: e.target.value })} />
                </Stack>}
                {form.packCode === 'dealership' && <TextField
                  fullWidth
                  multiline
                  minRows={3}
                  label="Detail page mappings"
                  helperText={detailSlugMappings.error || 'One source record ID=page slug per line. The source record stays provider-owned; the host owns its page route.'}
                  error={Boolean(detailSlugMappings.error)}
                  value={form.detailSlugs}
                  onChange={(e) => setForm({ ...form, detailSlugs: e.target.value })}
                />}
              </>
            ) : null}
            {configurationError ? <Alert severity="error">{configurationError}</Alert> : null}
            {saveMutation.error ? <Alert severity="error">{errorText(saveMutation.error)}</Alert> : null}
          </Stack>
          <Box sx={{ minWidth: 0, display: { xs: editorTab === 'preview' ? 'block' : 'none', lg: 'block' } }}>
            <EngageAppearancePreview appearance={appearance} appName={form.displayName} deliveryMode={effectiveDeliveryMode}
              configuration={experienceConfiguration} packCode={experiencePackCode} interactionPolicy={previewInteractionPolicy}
              capabilities={previewCapabilities} showBrandingError={Boolean(appearanceError)} />
            {previewCapabilitiesQuery.isError ? <Alert severity="warning" sx={{ mt: 1 }}>Runtime capability visibility could not be loaded. Human support is not represented as enabled in this preview.</Alert> : null}
            {(customerScopeLocked ? reviewedProfile?.configuration.connectionMode : form.connectionMode) === 'backend-mediated-private-runtime'
              ? <Alert severity="info" sx={{ mt: 1 }}>Private adapter responses are not invoked by this preview. Adapter-controlled history and support availability require a live integration check.</Alert> : null}
          </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          {editingActive ? <Typography variant="caption" color="text.secondary" sx={{ mr: 'auto', px: 1 }}>Preview only. Your active app is unchanged.</Typography> : null}
          {editingActive && editing ? <Button color="warning" disabled={appCommandPending} onClick={() => requestDestructive('disable', editing)}>Disable for editing</Button> : null}
          <Button disabled={appCommandPending} onClick={() => setEditing(null)}>{editingActive ? 'Close preview' : 'Cancel'}</Button>
          <Button variant="contained" onClick={submit} disabled={appCommandPending || editingActive || Boolean(appearanceError) || !form.displayName.trim() || !form.consumerId
            || (effectiveDeliveryMode === 'standalone' && !form.appSlug.trim())
            || (effectiveDeliveryMode === 'embedded' && lines(form.origins).length === 0)
            || (customerScopeLocked
              ? !reviewedProfile || !reviewedScopeReady || portfolioQuery.isFetching || reviewedProfilesQuery.isFetching
                || portfolioQuery.isError || reviewedProfilesQuery.isError || installationsQuery.isError
              : !form.profileCode || (form.packCode === 'dealership' && Boolean(detailSlugMappings.error))
                || (form.authProfileCode === 'supabase' && (!form.supabaseProjectUrl.trim() || !form.supabasePublishableKey.trim()
                  || (!form.supabaseEmailPassword && !form.supabaseMagicLink))))}>Save draft</Button>
        </DialogActions>
      </Dialog>
      {customerScopeLocked ? <CustomerEngageConnectionPanel open={Boolean(connectionPanel)} onClose={() => setConnectionPanel(null)}
        customerId={customerId} actorId={auth.session?.actorId} deployments={portfolioQuery.data ?? []}
        consumers={customer?.consumers ?? []} apps={installations}
        initialDeploymentId={connectionPanel?.deploymentId} currentConsumerId={connectionPanel?.consumerId}
        currentApp={connectionPanel?.app} chooseApp={connectionPanel?.chooseApp}
        onManageApp={app => { setConnectionPanel(null); setSelected(app); openEdit(app) }}
        onConnected={(connection, app) => {
          if (!connectionPanel?.fromEditor) { if (app) openEdit(app); else openCreate() }
          setForm(current => ({ ...current, consumerId: connection.consumerId }))
          setConfigurationError(null); setConnectionPanel(null)
        }} /> : null}
      {customerScopeLocked ? <Dialog open={Boolean(pendingDestructive)} onClose={() => setPendingDestructive(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{pendingDestructive?.command === 'delete' ? 'Delete draft app?' : 'Disable app?'}</DialogTitle>
        <DialogContent><Stack spacing={2}><Typography>{pendingDestructive?.installation.displayName}</Typography><Typography color="text.secondary">{pendingDestructive?.command === 'delete' ? 'This removes the selected draft installation. It does not delete its deployment or customer data.' : 'This disables the selected app installation. It does not stop or delete its deployment.'}</Typography>{!pendingCurrent ? <Alert severity="warning">The app or account scope changed. Close this dialog and review the current app.</Alert> : null}</Stack></DialogContent>
        <DialogActions><Button onClick={() => setPendingDestructive(null)}>Cancel</Button><Button color="error" disabled={!pendingCurrent || disableMutation.isPending || deleteMutation.isPending} onClick={() => {
          if (!pendingCurrent || !pendingInstallation || !pendingDestructive) return
          if (pendingDestructive.command === 'delete') deleteMutation.mutate({ installation: pendingInstallation, operationKey: crypto.randomUUID() })
          else disableMutation.mutate({ installation: pendingInstallation, operationKey: crypto.randomUUID() })
          setPendingDestructive(null)
        }}>Confirm</Button></DialogActions>
      </Dialog> : null}
    </Stack>
  )
}

function WorkspaceDetails({ installation, refresh, addDelivery, addDeliveryDisabled, customize, connect, deploymentName }: {
  installation: AIWorkspaceInstallationSummary; refresh: () => Promise<void>
  addDelivery: (mode: 'embedded' | 'standalone') => void; addDeliveryDisabled: boolean
  customize: () => void
  connect?: () => void; deploymentName?: string
}) {
  const customerView = usePlatformAuth().session?.role === 'CUSTOMER_ADMIN'
  const [copied, setCopied] = useState(false)
  const [copyError, setCopyError] = useState<string | null>(null)
  useEffect(() => { setCopied(false); setCopyError(null) }, [installation.installationId])
  const verifyMutation = useMutation({
    mutationFn: ({ hostname, operationKey }: { hostname: string; operationKey: string }) =>
      verifyAIWorkspaceDomain(installation.customerId, installation.installationId, hostname, operationKey),
    onSuccess: () => void refresh(),
  })
  const prepareDomains = useMutation({
    mutationFn: async () => {
      for (const domain of installation.domains.filter(item => item.status !== 'VERIFIED')) {
        await verifyAIWorkspaceDomain(installation.customerId, installation.installationId, domain.hostname, crypto.randomUUID())
      }
    }, onSuccess: () => void refresh(),
  })
  const snippet = `<script\n  async\n  src="${getPlatformApiBaseUrl()}/api/public/ai-workspace/install.js"\n  data-installation-id="${installation.installationId}">\n</script>`
  const copyInstallation = async () => {
    setCopyError(null)
    try {
      await navigator.clipboard.writeText(installation.deliveryMode === 'standalone' ? (installation.appUrl ?? '') : snippet)
      setCopied(true)
    } catch {
      setCopied(false)
      setCopyError('Clipboard access is unavailable. Select and copy the installation details below.')
    }
  }
  return (
    <Box sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2} sx={{ mb: 2 }}>
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 750 }}>{installation.displayName}</Typography>
          {customerView ? <Typography variant="body2" color="text.secondary">AI deployment: {deploymentName ?? 'Not connected'}</Typography> : null}
          <CustomerAdvancedSection><Typography variant="body2" color="text.secondary">
            Assigned deployment {installation.deploymentId ?? 'unavailable'} · release {installation.releaseId ?? 'unavailable'}
          </Typography></CustomerAdvancedSection>
        </Box>
        <Stack direction="row" gap={1} flexWrap="wrap">
          <Button startIcon={<PaletteOutlinedIcon />} onClick={customize}>Appearance & preview</Button>
          {connect ? <Button startIcon={<LinkRoundedIcon />} onClick={connect}>Change deployment</Button> : null}
          {customerView ? <Button startIcon={installation.deliveryMode === 'standalone' ? <CodeRoundedIcon /> : <OpenInNewRoundedIcon />}
            disabled={addDeliveryDisabled} onClick={() => addDelivery(installation.deliveryMode === 'standalone' ? 'embedded' : 'standalone')}>
            {installation.deliveryMode === 'standalone' ? 'Add website embed' : 'Add standalone app'}
          </Button> : null}
          <Button startIcon={<RefreshRoundedIcon />} onClick={() => void refresh()}>Refresh readiness</Button>
        </Stack>
      </Stack>
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={3} alignItems="stretch">
        <Paper variant="outlined" sx={{ p: 2.5, flex: 1, minWidth: 0, borderRadius: 1 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle2">{installation.deliveryMode === 'standalone' ? 'AI app address' : 'Installation script'}</Typography>
            <Tooltip title={installation.deliveryMode === 'standalone' ? 'Copy app address' : 'Copy installation script'}>
              <IconButton size="small" aria-label={installation.deliveryMode === 'standalone' ? 'Copy app address' : 'Copy installation script'}
                disabled={installation.deliveryMode === 'standalone' && !installation.appUrl}
                onClick={() => void copyInstallation()}><ContentCopyRoundedIcon /></IconButton>
            </Tooltip>
          </Stack>
          {installation.deliveryMode === 'standalone' ? (
            <>
              <Typography component="a" href={installation.appUrl ?? undefined} target="_blank" rel="noreferrer" sx={{ fontWeight: 750 }}>{installation.appUrl ?? 'App address unavailable'}</Typography>
              <Button size="small" startIcon={<CheckCircleRoundedIcon />} disabled={prepareDomains.isPending || verifyMutation.isPending}
                onClick={() => prepareDomains.mutate()} sx={{ alignSelf: 'flex-start', mt: 1 }}>Verify and prepare</Button>
              {prepareDomains.error ? <Alert severity="error">{prepareDomains.error instanceof Error ? prepareDomains.error.message : 'Domain preparation failed.'}</Alert> : null}
              <Stack spacing={1.5} sx={{ mt: 2 }}>
                {installation.domains.map((domain) => (
                  <EngageDomainInstructions key={domain.hostname} domain={domain} pending={verifyMutation.isPending}
                    verify={() => verifyMutation.mutate({ hostname: domain.hostname, operationKey: crypto.randomUUID() })} />
                ))}
              </Stack>
            </>
          ) : (
            <>
              <Box component="pre" sx={{ m: 0, p: 2, bgcolor: customerView ? 'action.hover' : 'grey.950', color: customerView ? 'text.primary' : 'grey.100', borderRadius: 1, overflowX: 'auto', fontSize: 12 }}>{snippet}</Box>
              <Typography variant="caption" color={copied ? 'success.main' : 'text.secondary'}>{copied ? 'Copied.' : 'Add once before the closing </body> tag on your website.'}</Typography>
              <Stack component="ol" spacing={1} sx={{ pl: 2.5, my: 2 }}>
                <Typography component="li" variant="body2">Allow your exact website origin in this installation.</Typography>
                <Typography component="li" variant="body2">If readiness reports missing runtime origins or identity settings, publish and apply the deployment update, then refresh readiness.</Typography>
                <Typography component="li" variant="body2">Activate this installation, add the script to your site layout, and open your website to try the workspace.</Typography>
              </Stack>
              <Typography variant="subtitle2">Allowed websites</Typography>
              {installation.allowedOrigins.map(origin => <Typography key={origin} variant="body2" sx={{ fontFamily: 'monospace', mt: 0.5 }}>{origin}</Typography>)}
              {installation.status !== 'ACTIVE' ? <Alert severity="info" sx={{ mt: 2 }}>The script will not load a workspace until this installation is active and ready.</Alert> : null}
            </>
          )}
          {copyError ? <Alert severity="warning" sx={{ mt: 1 }}>{copyError}</Alert> : null}
          {copied && installation.deliveryMode === 'standalone' ? <Typography variant="caption" color="success.main">Copied.</Typography> : null}
        </Paper>
        <Paper variant="outlined" sx={{ p: 2.5, flex: 1, minWidth: 0, borderRadius: 1 }}>
          <Typography variant="subtitle2" sx={{ mb: 1.5 }}>Readiness</Typography>
          <EngageDeploymentUpdatePanel key={installation.installationId} installation={installation} refresh={refresh} />
          <Stack spacing={1}>
            {installation.readinessChecks.map((check) => (
              <Stack key={check.code} direction="row" spacing={1} alignItems="flex-start">
                {check.status === 'PASSED' ? <CheckCircleRoundedIcon color="success" fontSize="small" /> : <WarningAmberRoundedIcon color="warning" fontSize="small" />}
                <Box><Typography variant="body2" sx={{ fontWeight: 700 }}>{check.code}</Typography><Typography variant="caption" color="text.secondary">{check.message}</Typography></Box>
              </Stack>
            ))}
          </Stack>
        </Paper>
      </Stack>
      <CustomerAdvancedSection><Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5, fontFamily: 'monospace' }}>
        Assignment revision: {installation.assignmentRevision ?? 'unavailable'}
      </Typography></CustomerAdvancedSection>
    </Box>
  )
}

function lines(value: string) {
  return [...new Set(value.split(/\r?\n|,/).map((item) => item.trim()).filter(Boolean))]
}

function parseMappings(value: string): { values: Record<string, string>; error: string | null } {
  const values: Record<string, string> = {}
  const entries = value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean)
  for (const [index, entry] of entries.entries()) {
    const separator = entry.indexOf('=')
    const key = separator > 0 ? entry.slice(0, separator).trim() : ''
    const mappedValue = separator > 0 ? entry.slice(separator + 1).trim() : ''
    if (!key || !mappedValue) {
      return { values: {}, error: `Line ${index + 1} must use source-record-id=page-slug.` }
    }
    if (key in values) {
      return { values: {}, error: `Line ${index + 1} repeats source record ID ${key}.` }
    }
    values[key] = mappedValue
  }
  return { values, error: null }
}

function formatMappings(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return ''
  return Object.entries(value as Record<string, unknown>)
    .filter((entry): entry is [string, string] => typeof entry[1] === 'string' && Boolean(entry[1].trim()))
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, mappedValue]) => `${key}=${mappedValue}`)
    .join('\n')
}

function connectionLabel(mode: string) {
  if (mode === 'public-runtime-anonymous') return 'Anonymous direct'
  if (mode === 'public-runtime-authenticated') return 'Authenticated direct'
  return 'Private adapter'
}
