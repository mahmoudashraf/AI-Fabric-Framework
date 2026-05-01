import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import FolderZipOutlinedIcon from '@mui/icons-material/FolderZipOutlined'
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined'
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined'
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined'
import StorefrontOutlinedIcon from '@mui/icons-material/StorefrontOutlined'
import SyncOutlinedIcon from '@mui/icons-material/SyncOutlined'
import WorkspacePremiumOutlinedIcon from '@mui/icons-material/WorkspacePremiumOutlined'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { createStoreEvidenceBundle, listStoreEvidenceBundles } from '../api/evidence'
import { createEscalation, listEscalations } from '../api/escalations'
import { listClientImplementations } from '../api/implementations'
import { createStoreNote, listStoreNotes } from '../api/notes'
import { activatePackageTrial, deactivatePackageTrial, getProductControls, updateProductSourceSettings, updateProductSupportProfile, updateProductWidgetSettings } from '../api/productControls'
import type { PartnerClientImplementation, PartnerEscalation, PartnerEvidenceBundle, PartnerProductControl, PartnerStore, PartnerTemplateApplication, PartnerVerificationRun, PartnerVerificationStep } from '../api/schemas'
import { getPartnerStore } from '../api/stores'
import { listTemplateApplications } from '../api/templates'
import { listStoreThinkerSessions } from '../api/thinker'
import { completeVerificationStep, getStoreVerificationPack, listStoreVerificationRuns, runStoreVerification } from '../api/verification'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { DataTable, type DataColumn } from '../components/DataTable'
import { PageHeader } from '../components/PageHeader'
import { PartnerMaxWidgetLiveTest } from '../components/PartnerMaxWidgetLiveTest'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime, titleize } from '../utils/format'

const escalationFormSchema = z.object({
  title: z.string().min(3, 'Title is required.'),
  severity: z.string().min(2),
  description: z.string().min(10, 'Describe the blocker.'),
  reproductionSteps: z.string().optional(),
  expectedBehavior: z.string().optional(),
  actualBehavior: z.string().optional(),
  impact: z.string().optional(),
  nextAction: z.string().optional(),
  evidenceBundleIdsText: z.string().optional(),
})

type EscalationForm = z.infer<typeof escalationFormSchema>

const STOREFRONT_SURFACES = [
  'ai-search',
  'contextual-pill',
  'product-insight',
  'policy-strip',
  'product-faq',
  'comparison',
]

const CONVERSATION_MODES = ['navigator', 'navigator_deep', 'cart_assistant', 'executor']

const SOURCE_CATEGORIES = [
  { key: 'productsEnabled', label: 'Products' },
  { key: 'collectionsEnabled', label: 'Collections' },
  { key: 'pagesEnabled', label: 'Pages' },
  { key: 'policiesEnabled', label: 'Policies' },
  { key: 'articlesEnabled', label: 'Articles' },
  { key: 'metaobjectsEnabled', label: 'Metaobjects' },
] as const

export function StoreWorkspacePage() {
  const { storeId = '' } = useParams()
  const navigate = useNavigate()
  const { api } = useSupabaseAuth()
  const [tab, setTab] = useState(0)
  const [dialogOpen, setDialogOpen] = useState(false)
  const storeQuery = useQuery({ queryKey: ['store', storeId], queryFn: () => getPartnerStore(api, storeId), enabled: Boolean(storeId) })
  const runsQuery = useQuery({ queryKey: ['store-verification-runs', storeId], queryFn: () => listStoreVerificationRuns(api, storeId), enabled: Boolean(storeId) })
  const bundlesQuery = useQuery({ queryKey: ['store-evidence-bundles', storeId], queryFn: () => listStoreEvidenceBundles(api, storeId), enabled: Boolean(storeId) })
  const implementationsQuery = useQuery({ queryKey: ['implementations'], queryFn: () => listClientImplementations(api), enabled: Boolean(storeId) })
  const escalationsQuery = useQuery({ queryKey: ['escalations'], queryFn: () => listEscalations(api), enabled: Boolean(storeId) })
  const templateApplicationsQuery = useQuery({ queryKey: ['template-applications'], queryFn: () => listTemplateApplications(api), enabled: Boolean(storeId) })

  if (storeQuery.isLoading) {
    return <LinearProgress />
  }
  if (storeQuery.isError || !storeQuery.data) {
    return <Alert severity="error">This store is not assigned to your partner workspace.</Alert>
  }

  const store = storeQuery.data
  const runs = runsQuery.data ?? []
  const evidenceBundles = bundlesQuery.data ?? []
  const implementations = (implementationsQuery.data ?? []).filter((item) => item.storeConnectionId === store.storeConnectionId || item.shopDomain === store.shopDomain)
  const escalations = (escalationsQuery.data ?? []).filter((item) => item.storeAssignmentId === store.id || item.shopDomain === store.shopDomain)
  const appliedTemplates = (templateApplicationsQuery.data ?? []).filter((item) => item.storeAssignmentId === store.id || item.shopDomain === store.shopDomain)
  const latestRun = runs[0]
  const latestEvidence = evidenceBundles[0]
  return (
    <>
      <PageHeader
        title={store.merchantName}
        subtitle={store.shopDomain}
        breadcrumbs={[{ label: 'Client stores', to: '/stores' }, { label: store.merchantName }]}
        actions={
          <Button variant="contained" startIcon={<ReportProblemOutlinedIcon />} onClick={() => setDialogOpen(true)}>
            Escalate blocker
          </Button>
        }
      />
      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }} justifyContent="space-between">
          <Stack spacing={0.75}>
            <StatusChip status={store.status} />
            <Typography color="text.secondary">{store.topBlocker}</Typography>
          </Stack>
          <Stack direction="row" spacing={3} flexWrap="wrap">
            <Metric label="Plan" value={store.plan} />
            <Metric label="Knowledge Sync" value={titleize(store.knowledgeSyncStatus)} />
            <Metric label="Readiness" value={titleize(store.readinessStatus)} />
            <Metric label="Last activity" value={formatDateTime(store.lastActivityAt)} />
          </Stack>
        </Stack>
      </Paper>
      <StoreCommandCenter
        store={store}
        latestRun={latestRun}
        latestEvidence={latestEvidence}
        appliedTemplateCount={appliedTemplates.length}
        openEscalationCount={escalations.filter((item) => !['RESOLVED', 'CLOSED'].includes(item.status)).length}
      />
      <Paper sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={(_event, value) => setTab(value)} variant="scrollable" scrollButtons="auto">
          <Tab label="Overview" />
          <Tab label="Product controls" />
          <Tab label="Setup checklist" />
          <Tab label="Verification" />
          <Tab label="Evidence" />
          <Tab label="Thinker" />
          <Tab label="Escalations" />
          <Tab label="Notes" />
        </Tabs>
      </Paper>
      {tab === 0 ? (
        <OverviewTab
          store={store}
          implementations={implementations}
          templates={appliedTemplates}
          escalations={escalations}
          latestRun={latestRun}
          latestEvidence={latestEvidence}
        />
      ) : null}
      {tab === 1 ? <ProductControlsTab storeId={store.id} /> : null}
      {tab === 2 ? <SetupTab store={store} latestRun={latestRun} templates={appliedTemplates} /> : null}
      {tab === 3 ? <VerificationTab storeId={store.id} /> : null}
      {tab === 4 ? <EvidenceTab storeId={store.id} /> : null}
      {tab === 5 ? <ThinkerTab storeId={store.id} /> : null}
      {tab === 6 ? (
        <Paper sx={{ p: 2 }}>
          <Typography variant="h3">Support center</Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Escalations for this store are handled in the support queue with partner-visible replies only.
          </Typography>
          <Button sx={{ mt: 2 }} onClick={() => navigate('/support')}>Open support</Button>
        </Paper>
      ) : null}
      {tab === 7 ? <NotesTab storeId={store.id} /> : null}
      <EscalationDialog open={dialogOpen} onClose={() => setDialogOpen(false)} storeId={store.id} />
    </>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography fontWeight={700}>{value}</Typography>
    </Box>
  )
}

function SectionHeading({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <Box sx={{ width: 32, height: 32, borderRadius: 1.5, bgcolor: 'action.hover', display: 'grid', placeItems: 'center', color: 'primary.main' }}>
        {icon}
      </Box>
      <Typography variant="h3">{title}</Typography>
    </Stack>
  )
}

function InfoTile({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ p: 1.25, border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography fontWeight={800} sx={{ mt: 0.25 }}>{value}</Typography>
    </Box>
  )
}

function ChipRow({ values, empty }: { values: string[]; empty: string }) {
  return (
    <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1 }}>
      {values.map((value) => <Chip key={value} size="small" label={titleize(value)} variant="outlined" />)}
      {values.length === 0 ? <Typography color="text.secondary">{empty}</Typography> : null}
    </Stack>
  )
}

function TimelineRow({ title, subtitle, status, meta }: { title: string; subtitle: string; status: string; meta?: string }) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="space-between" alignItems={{ sm: 'center' }}>
      <Box>
        <Typography fontWeight={800}>{title}</Typography>
        <Typography variant="caption" color="text.secondary">{subtitle}</Typography>
      </Box>
      <Stack direction="row" spacing={1} alignItems="center">
        <StatusChip status={status} label={statusLabel(status)} />
        {meta ? <Typography variant="caption" color="text.secondary">{meta}</Typography> : null}
      </Stack>
    </Stack>
  )
}

function statusLabel(status: string) {
  switch (status.toUpperCase()) {
    case 'APPLIED':
      return 'Applied'
    case 'NOT_RUN':
      return 'Not run'
    case 'PASSED':
      return 'Passed'
    case 'NEEDS_SETUP':
      return 'Needs setup'
    default:
      return undefined
  }
}

function toggleValue(values: string[], value: string) {
  return values.includes(value) ? values.filter((item) => item !== value) : [...values, value]
}

function uniqueStrings(values: string[]) {
  return Array.from(new Set(values.filter((value) => value.trim().length > 0)))
}

function trimNullable(value: string) {
  const trimmed = value.trim()
  return trimmed.length === 0 ? null : trimmed
}

function compactStringRecord(value: Record<string, string>) {
  return Object.entries(value).reduce<Record<string, string>>((result, [key, current]) => {
    if (key.trim().length > 0 && current.trim().length > 0) {
      result[key.trim()] = current.trim()
    }
    return result
  }, {})
}

function StoreCommandCenter({
  store,
  latestRun,
  latestEvidence,
  appliedTemplateCount,
  openEscalationCount,
}: {
  store: PartnerStore
  latestRun?: PartnerVerificationRun
  latestEvidence?: PartnerEvidenceBundle
  appliedTemplateCount: number
  openEscalationCount: number
}) {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1.15fr 1fr' }, gap: 2, mb: 2 }}>
      <Paper sx={{ p: 2 }}>
        <Stack spacing={2}>
          <SectionHeading icon={<StorefrontOutlinedIcon />} title="Store access profile" />
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 1.5 }}>
            <InfoTile label="Install" value={titleize(store.installStatus)} />
            <InfoTile label="Assignment" value={titleize(store.assignmentStatus)} />
            <InfoTile label="Source" value={titleize(store.assignmentSource ?? 'merchant approval')} />
            <InfoTile label="Approved" value={formatDateTime(store.approvedAt)} />
          </Box>
          <Divider />
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {store.permissions.map((permission) => <Chip key={permission} size="small" label={titleize(permission)} icon={<KeyOutlinedIcon />} />)}
            {store.permissions.length === 0 ? <Typography color="text.secondary">No partner permissions are currently exposed.</Typography> : null}
          </Stack>
          <Divider />
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 1.5 }}>
            <InfoTile label="Package" value={store.packageProfile?.packageKey ? titleize(store.packageProfile.packageKey) : store.plan} />
            <InfoTile label="Tier" value={store.packageProfile?.tierKey ? titleize(store.packageProfile.tierKey) : 'Merchant selected'} />
            <InfoTile label="Cost posture" value={store.packageProfile?.costPosture ? titleize(store.packageProfile.costPosture) : 'Merchant configured'} />
            <InfoTile label="Configured" value={formatDateTime(store.packageProfile?.lastConfiguredAt)} />
          </Box>
          {store.packageProfile ? (
            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip size="small" variant="outlined" label={`Tier ${titleize(store.packageProfile.tierKey ?? 'merchant selected')}`} />
              <Chip size="small" variant="outlined" label={`Pack ${store.packageProfile.verificationPackId ?? 'default'}`} />
            </Stack>
          ) : null}
          <Typography variant="caption" color="text.secondary">
            Partner workspace is scoped to merchant-approved setup, verification, evidence, and support data. Operator diagnostics, provider credentials, billing internals, and deployment secrets are not exposed here.
          </Typography>
        </Stack>
      </Paper>
      <Paper sx={{ p: 2 }}>
        <Stack spacing={2}>
          <SectionHeading icon={<FactCheckOutlinedIcon />} title="Launch proof snapshot" />
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 1.5 }}>
            <InfoTile label="Latest pack" value={latestRun ? latestRun.packName : 'Not run'} />
            <InfoTile label="Checks" value={latestRun ? `${latestRun.passedSteps}/${latestRun.totalSteps} passed` : '0/0 passed'} />
            <InfoTile label="Evidence" value={latestEvidence ? titleize(latestEvidence.status) : 'None'} />
            <InfoTile label="Escalations" value={String(openEscalationCount)} />
          </Box>
          <Stack direction="row" spacing={1} flexWrap="wrap">
            <Chip size="small" color="success" variant="outlined" label={`${appliedTemplateCount} playbook${appliedTemplateCount === 1 ? '' : 's'} applied`} />
            <Chip size="small" variant="outlined" label={`Widget ${titleize(store.widgetStatus)}`} />
            <Chip size="small" variant="outlined" label={`Last sync ${formatDateTime(store.lastSyncAt)}`} />
          </Stack>
        </Stack>
      </Paper>
    </Box>
  )
}

function OverviewTab({
  store,
  implementations,
  templates,
  escalations,
  latestRun,
  latestEvidence,
}: {
  store: PartnerStore
  implementations: PartnerClientImplementation[]
  templates: PartnerTemplateApplication[]
  escalations: PartnerEscalation[]
  latestRun?: PartnerVerificationRun
  latestEvidence?: PartnerEvidenceBundle
}) {
  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 2 }}>
        <Paper sx={{ p: 2 }}>
          <SectionHeading icon={<SyncOutlinedIcon />} title="Readiness and source coverage" />
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)' }, gap: 1.5, mt: 2 }}>
            <InfoTile label="Knowledge sync" value={titleize(store.knowledgeSyncStatus)} />
            <InfoTile label="Readiness" value={titleize(store.readinessStatus)} />
            <InfoTile label="Last webhook" value={formatDateTime(store.lastWebhookAt)} />
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>Enabled source categories</Typography>
          <ChipRow values={store.enabledSourceCategories} empty="No enabled source categories were reported by the store." />
        </Paper>
        <Paper sx={{ p: 2 }}>
          <SectionHeading icon={<ShieldOutlinedIcon />} title="Partner-visible boundary" />
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            This workspace shows merchant-approved implementation data only. It can guide setup, run checks, export evidence, and open support; it cannot reveal platform secrets, provider keys, billing internals, or operator-only remediation logs.
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>Approved partner actions</Typography>
          <ChipRow values={store.permissions} empty="No active partner actions are available." />
        </Paper>
      </Box>
      <Paper sx={{ p: 2 }}>
        <SectionHeading icon={<StorefrontOutlinedIcon />} title="Storefront surfaces" />
        <ChipRow values={store.enabledSurfaces} empty="No storefront surfaces are currently enabled." />
      </Paper>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 2 }}>
        <Paper sx={{ p: 2 }}>
          <SectionHeading icon={<HistoryOutlinedIcon />} title="Implementation and access requests" />
          <Stack spacing={1.25} sx={{ mt: 1.5 }}>
            {implementations.map((item) => (
              <TimelineRow key={item.id} title={item.clientName} subtitle={`${item.shopDomain} · ${item.requestedTier}`} status={item.status} meta={formatDateTime(item.updatedAt)} />
            ))}
            {implementations.length === 0 ? <Typography color="text.secondary">No implementation request is linked to this store.</Typography> : null}
          </Stack>
        </Paper>
        <Paper sx={{ p: 2 }}>
          <SectionHeading icon={<CheckCircleOutlineIcon />} title="Current proof assets" />
          <Stack spacing={1.25} sx={{ mt: 1.5 }}>
            <TimelineRow title={latestRun?.packName ?? 'Verification pack'} subtitle={latestRun ? `${latestRun.passedSteps}/${latestRun.totalSteps} checks passed` : 'No verification run yet'} status={latestRun?.status ?? 'NOT_RUN'} meta={latestRun ? formatDateTime(latestRun.startedAt) : 'Run a pack to create proof'} />
            <TimelineRow title={latestEvidence?.bundleName ?? 'Evidence bundle'} subtitle={latestEvidence ? titleize(latestEvidence.bundleKind) : 'No evidence bundle yet'} status={latestEvidence?.status ?? 'NOT_RUN'} meta={latestEvidence ? formatDateTime(latestEvidence.generatedAt) : 'Export launch evidence after verification'} />
          </Stack>
        </Paper>
      </Box>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 2 }}>
        <Paper sx={{ p: 2 }}>
          <SectionHeading icon={<FactCheckOutlinedIcon />} title="Applied playbooks" />
          <Stack spacing={1.25} sx={{ mt: 1.5 }}>
            {templates.map((item) => (
              <TimelineRow key={item.id} title={item.templateName} subtitle={`${titleize(item.category)} · ${item.checklist.length} checklist items`} status="APPLIED" meta={formatDateTime(item.appliedAt)} />
            ))}
            {templates.length === 0 ? <Typography color="text.secondary">No playbooks applied to this store yet.</Typography> : null}
          </Stack>
        </Paper>
        <Paper sx={{ p: 2 }}>
          <SectionHeading icon={<ReportProblemOutlinedIcon />} title="Support posture" />
          <Stack spacing={1.25} sx={{ mt: 1.5 }}>
            {escalations.map((item) => (
              <TimelineRow key={item.id} title={item.title} subtitle={item.nextAction ?? item.description} status={item.status} meta={formatDateTime(item.updatedAt)} />
            ))}
            {escalations.length === 0 ? <Typography color="text.secondary">No support escalations are open for this store.</Typography> : null}
          </Stack>
        </Paper>
      </Box>
    </Stack>
  )
}

function ProductControlsTab({ storeId }: { storeId: string }) {
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const controlsQuery = useQuery({ queryKey: ['product-controls', storeId], queryFn: () => getProductControls(api, storeId) })
  const [launcherLabel, setLauncherLabel] = useState('')
  const [welcomeMessage, setWelcomeMessage] = useState('')
  const [shellModeProfile, setShellModeProfile] = useState('SHOPIFY_COMPANION')
  const [debugEnabled, setDebugEnabled] = useState(false)
  const [enabledSurfaces, setEnabledSurfaces] = useState<string[]>([])
  const [defaultConversationMode, setDefaultConversationMode] = useState('navigator')
  const [allowedConversationModes, setAllowedConversationModes] = useState<string[]>(['navigator'])
  const [pageModeMappings, setPageModeMappings] = useState<Record<string, string>>({})
  const [sourceSettings, setSourceSettings] = useState<Record<(typeof SOURCE_CATEGORIES)[number]['key'], boolean>>({
    productsEnabled: true,
    collectionsEnabled: true,
    pagesEnabled: true,
    policiesEnabled: true,
    articlesEnabled: true,
    metaobjectsEnabled: true,
  })
  const [contactEmail, setContactEmail] = useState('')
  const [contactUrl, setContactUrl] = useState('')
  const [helpCenterUrl, setHelpCenterUrl] = useState('')
  const [orderLookupPageUrl, setOrderLookupPageUrl] = useState('')
  const [supportPolicyNote, setSupportPolicyNote] = useState('')
  const [trialTier, setTrialTier] = useState('STARTER')
  const [trialDays, setTrialDays] = useState(7)
  const [trialReason, setTrialReason] = useState('')
  const [deactivationReason, setDeactivationReason] = useState('')

  useEffect(() => {
    if (!controlsQuery.data) {
      return
    }
    const controls = controlsQuery.data
    setLauncherLabel(controls.widgetSettings.launcherLabel)
    setWelcomeMessage(controls.widgetSettings.welcomeMessage)
    setShellModeProfile(controls.widgetSettings.shellModeProfile)
    setDebugEnabled(controls.widgetSettings.debugEnabled ?? false)
    setEnabledSurfaces(controls.widgetSettings.enabledSurfaces)
    setDefaultConversationMode(controls.widgetSettings.defaultConversationMode)
    setAllowedConversationModes(controls.widgetSettings.allowedConversationModes)
    setPageModeMappings(controls.widgetSettings.pageModeMappings)
    setSourceSettings({
      productsEnabled: controls.sourceSettings.productsEnabled,
      collectionsEnabled: controls.sourceSettings.collectionsEnabled,
      pagesEnabled: controls.sourceSettings.pagesEnabled,
      policiesEnabled: controls.sourceSettings.policiesEnabled,
      articlesEnabled: controls.sourceSettings.articlesEnabled,
      metaobjectsEnabled: controls.sourceSettings.metaobjectsEnabled,
    })
    setContactEmail(controls.supportProfile.contactEmail ?? '')
    setContactUrl(controls.supportProfile.contactUrl ?? '')
    setHelpCenterUrl(controls.supportProfile.helpCenterUrl ?? '')
    setOrderLookupPageUrl(controls.supportProfile.orderLookupPageUrl ?? '')
    setSupportPolicyNote(controls.supportProfile.supportPolicyNote ?? '')
    setTrialTier(controls.trialActivationTiers[0] ?? 'STARTER')
    setTrialDays(Math.min(7, controls.maxTrialDays ?? 30))
  }, [controlsQuery.data])

  const applyProductControlUpdate = async (updated: PartnerProductControl) => {
    queryClient.setQueryData(['product-controls', storeId], updated)
    await queryClient.invalidateQueries({ queryKey: ['store', storeId] })
    await queryClient.invalidateQueries({ queryKey: ['activity'] })
  }

  const widgetMutation = useMutation({
    mutationFn: () => updateProductWidgetSettings(api, storeId, {
      launcherLabel,
      welcomeMessage,
      shellModeProfile,
      debugEnabled,
      enabledSurfaces,
      defaultConversationMode,
      allowedConversationModes: uniqueStrings([...allowedConversationModes, defaultConversationMode]),
      pageModeMappings: compactStringRecord(pageModeMappings),
    }),
    onSuccess: applyProductControlUpdate,
  })
  const sourceMutation = useMutation({
    mutationFn: () => updateProductSourceSettings(api, storeId, sourceSettings),
    onSuccess: applyProductControlUpdate,
  })
  const supportMutation = useMutation({
    mutationFn: () => updateProductSupportProfile(api, storeId, {
      contactEmail: trimNullable(contactEmail),
      contactUrl: trimNullable(contactUrl),
      helpCenterUrl: trimNullable(helpCenterUrl),
      orderLookupPageUrl: trimNullable(orderLookupPageUrl),
      supportPolicyNote: trimNullable(supportPolicyNote),
    }),
    onSuccess: applyProductControlUpdate,
  })
  const activateTrialMutation = useMutation({
    mutationFn: () => activatePackageTrial(api, storeId, {
      tierKey: trialTier,
      trialDays,
      reason: trimNullable(trialReason),
    }),
    onSuccess: async (updated) => {
      setTrialReason('')
      await applyProductControlUpdate(updated)
    },
  })
  const deactivateTrialMutation = useMutation({
    mutationFn: () => deactivatePackageTrial(api, storeId, controlsQuery.data?.activePackageTrial?.id ?? '', {
      reason: trimNullable(deactivationReason),
    }),
    onSuccess: async (updated) => {
      setDeactivationReason('')
      await applyProductControlUpdate(updated)
    },
  })

  if (controlsQuery.isLoading) {
    return <LinearProgress />
  }
  if (controlsQuery.isError || !controlsQuery.data) {
    return <Alert severity="error">{controlsQuery.error instanceof Error ? controlsQuery.error.message : 'Product controls could not be loaded.'}</Alert>
  }

  const controls = controlsQuery.data
  const canChangeWidget = controls.capabilities.includes('STOREFRONT_SURFACE_CONTROL')
  const canChangeSources = controls.capabilities.includes('KNOWLEDGE_SOURCE_CONTROL')
  const canChangeSupport = controls.capabilities.includes('SUPPORT_MANAGE')
  const canActivatePackageTrial = controls.capabilities.includes('PACKAGE_TRIAL_ACTIVATE')
  const activeTrial = controls.activePackageTrial
  const trialBlocked = Boolean(activeTrial && activeTrial.status !== 'DEACTIVATED')
  const anySourceEnabled = Object.values(sourceSettings).some(Boolean)
  const pageKeys = ['landing', 'product', 'collection', 'search', 'cart', 'account', 'content']

  return (
    <Stack spacing={2}>
      <Paper sx={{ p: 2 }}>
        <SectionHeading icon={<StorefrontOutlinedIcon />} title="Product control state" />
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(5, 1fr)' }, gap: 1.5, mt: 2 }}>
          <InfoTile label="Install" value={titleize(controls.installStatus)} />
          <InfoTile label="Widget" value={titleize(controls.widgetStatus)} />
          <InfoTile label="Knowledge" value={titleize(controls.knowledgeSyncStatus)} />
          <InfoTile label="Readiness" value={titleize(controls.readinessStatus)} />
          <InfoTile label="Updated" value={formatDateTime(controls.updatedAt)} />
        </Box>
        <ChipRow values={controls.capabilities} empty="No product control capabilities are active." />
      </Paper>

      <PartnerMaxWidgetLiveTest controls={controls} />

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', xl: '1.2fr 0.8fr' }, gap: 2 }}>
        <Paper sx={{ p: 2 }}>
          <SectionHeading icon={<StorefrontOutlinedIcon />} title="Storefront surfaces" />
          {widgetMutation.isError ? <Alert sx={{ mt: 2 }} severity="error">{widgetMutation.error instanceof Error ? widgetMutation.error.message : 'Widget settings update failed.'}</Alert> : null}
          <Stack spacing={1.5} sx={{ mt: 2 }}>
            <TextField label="Launcher label" value={launcherLabel} onChange={(event) => setLauncherLabel(event.target.value)} disabled={!canChangeWidget || widgetMutation.isPending} />
            <TextField label="Welcome message" minRows={3} multiline value={welcomeMessage} onChange={(event) => setWelcomeMessage(event.target.value)} disabled={!canChangeWidget || widgetMutation.isPending} />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 1.5 }}>
              <TextField select label="Shell profile" value={shellModeProfile} onChange={(event) => setShellModeProfile(event.target.value)} disabled={!canChangeWidget || widgetMutation.isPending}>
                {['SHOPIFY_COMPANION', 'GUIDED_COMMERCE', 'GUIDED_SUPPORT'].map((profile) => <MenuItem key={profile} value={profile}>{titleize(profile)}</MenuItem>)}
              </TextField>
              <TextField select label="Default mode" value={defaultConversationMode} onChange={(event) => setDefaultConversationMode(event.target.value)} disabled={!canChangeWidget || widgetMutation.isPending}>
                {CONVERSATION_MODES.map((mode) => <MenuItem key={mode} value={mode}>{titleize(mode)}</MenuItem>)}
              </TextField>
            </Box>
            <FormControlLabel
              control={<Checkbox checked={debugEnabled} onChange={(event) => setDebugEnabled(event.target.checked)} />}
              label="Debug inspector"
              disabled={!canChangeWidget || widgetMutation.isPending}
            />
            <Divider />
            <Typography variant="caption" color="text.secondary">Requested surfaces</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: 'repeat(3, 1fr)' }, gap: 0.5 }}>
              {STOREFRONT_SURFACES.map((surface) => (
                <FormControlLabel
                  key={surface}
                  control={<Checkbox checked={enabledSurfaces.includes(surface)} onChange={() => setEnabledSurfaces(toggleValue(enabledSurfaces, surface))} />}
                  label={titleize(surface)}
                  disabled={!canChangeWidget || widgetMutation.isPending}
                />
              ))}
            </Box>
            <Typography variant="caption" color="text.secondary">Conversation modes</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 0.5 }}>
              {CONVERSATION_MODES.map((mode) => (
                <FormControlLabel
                  key={mode}
                  control={<Checkbox checked={allowedConversationModes.includes(mode) || defaultConversationMode === mode} onChange={() => setAllowedConversationModes(toggleValue(allowedConversationModes, mode))} />}
                  label={titleize(mode)}
                  disabled={!canChangeWidget || widgetMutation.isPending || defaultConversationMode === mode}
                />
              ))}
            </Box>
            <Typography variant="caption" color="text.secondary">Page mode mappings</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: 'repeat(3, 1fr)' }, gap: 1.5 }}>
              {pageKeys.map((key) => (
                <TextField
                  key={key}
                  select
                  label={titleize(key)}
                  value={pageModeMappings[key] ?? ''}
                  onChange={(event) => setPageModeMappings({ ...pageModeMappings, [key]: event.target.value })}
                  disabled={!canChangeWidget || widgetMutation.isPending}
                >
                  <MenuItem value="">Default</MenuItem>
                  {CONVERSATION_MODES.map((mode) => <MenuItem key={mode} value={mode}>{titleize(mode)}</MenuItem>)}
                </TextField>
              ))}
            </Box>
            <Button
              variant="contained"
              sx={{ alignSelf: 'flex-end' }}
              onClick={() => widgetMutation.mutate()}
              disabled={!canChangeWidget || widgetMutation.isPending || enabledSurfaces.length === 0 || launcherLabel.trim().length === 0 || welcomeMessage.trim().length === 0}
            >
              Save storefront controls
            </Button>
          </Stack>
        </Paper>

        <Stack spacing={2}>
          <Paper sx={{ p: 2 }}>
            <SectionHeading icon={<WorkspacePremiumOutlinedIcon />} title="Package trial" />
            {activateTrialMutation.isError ? <Alert sx={{ mt: 2 }} severity="error">{activateTrialMutation.error instanceof Error ? activateTrialMutation.error.message : 'Package trial activation failed.'}</Alert> : null}
            {deactivateTrialMutation.isError ? <Alert sx={{ mt: 2 }} severity="error">{deactivateTrialMutation.error instanceof Error ? deactivateTrialMutation.error.message : 'Package trial deactivation failed.'}</Alert> : null}
            <Stack spacing={1.5} sx={{ mt: 2 }}>
              {activeTrial ? (
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' }, gap: 1 }}>
                  <InfoTile label="Trial" value={titleize(activeTrial.tierKey)} />
                  <InfoTile label="Status" value={titleize(activeTrial.status)} />
                  <InfoTile label="Ends" value={formatDateTime(activeTrial.trialEndsAt)} />
                  <InfoTile label="Job" value={activeTrial.activationProvisioningJobId ?? 'Queued'} />
                </Box>
              ) : (
                <Alert severity={canActivatePackageTrial ? 'info' : 'warning'}>
                  {canActivatePackageTrial ? 'No active package trial for this store.' : 'Platform has not granted package trial activation to your member.'}
                </Alert>
              )}
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.5 }}>
                <TextField select label="Trial package" value={trialTier} onChange={(event) => setTrialTier(event.target.value)} disabled={!canActivatePackageTrial || trialBlocked || activateTrialMutation.isPending}>
                  {controls.trialActivationTiers.map((tier) => <MenuItem key={tier} value={tier}>{titleize(tier)}</MenuItem>)}
                </TextField>
                <TextField select label="Trial period" value={String(trialDays)} onChange={(event) => setTrialDays(Number(event.target.value))} disabled={!canActivatePackageTrial || trialBlocked || activateTrialMutation.isPending}>
                  {[1, 7, 14, 30].filter((value) => value <= (controls.maxTrialDays ?? 30)).map((value) => <MenuItem key={value} value={String(value)}>{value} days</MenuItem>)}
                </TextField>
              </Box>
              <TextField label="Activation reason" value={trialReason} onChange={(event) => setTrialReason(event.target.value)} disabled={!canActivatePackageTrial || trialBlocked || activateTrialMutation.isPending} />
              <Button
                variant="contained"
                onClick={() => activateTrialMutation.mutate()}
                disabled={!canActivatePackageTrial || trialBlocked || activateTrialMutation.isPending}
              >
                Activate trial
              </Button>
              {activeTrial ? (
                <>
                  <Divider />
                  <TextField label="Deactivation reason" value={deactivationReason} onChange={(event) => setDeactivationReason(event.target.value)} disabled={!activeTrial.deactivationEligible || deactivateTrialMutation.isPending} />
                  <Button
                    variant="outlined"
                    color="warning"
                    onClick={() => deactivateTrialMutation.mutate()}
                    disabled={!activeTrial.deactivationEligible || deactivateTrialMutation.isPending || deactivationReason.trim().length === 0}
                  >
                    Deactivate past-due trial
                  </Button>
                </>
              ) : null}
              {controls.packageTrialHistory.length > 0 ? (
                <>
                  <Divider />
                  <Typography variant="caption" color="text.secondary">Recent trial activity</Typography>
                  <Stack spacing={1}>
                    {controls.packageTrialHistory.map((trial) => (
                      <Box
                        key={trial.id}
                        sx={{
                          display: 'grid',
                          gridTemplateColumns: { xs: '1fr', sm: '1fr auto' },
                          gap: 1,
                          alignItems: 'center',
                          border: '1px solid',
                          borderColor: 'divider',
                          borderRadius: 1,
                          p: 1,
                        }}
                      >
                        <Box>
                          <Typography sx={{ fontWeight: 700 }}>{titleize(trial.tierKey)} trial</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {formatDateTime(trial.trialStartsAt)} to {formatDateTime(trial.trialEndsAt)}
                          </Typography>
                        </Box>
                        <Stack direction="row" spacing={1} flexWrap="wrap" justifyContent={{ xs: 'flex-start', sm: 'flex-end' }}>
                          <StatusChip status={trial.status} />
                          <Chip size="small" variant="outlined" label={trial.deactivationProvisioningJobId ? 'Deactivation queued' : trial.activationProvisioningJobId ? 'Activation queued' : 'Pending job'} />
                        </Stack>
                      </Box>
                    ))}
                  </Stack>
                </>
              ) : null}
            </Stack>
          </Paper>

          <Paper sx={{ p: 2 }}>
            <SectionHeading icon={<SyncOutlinedIcon />} title="Knowledge sources" />
            {sourceMutation.isError ? <Alert sx={{ mt: 2 }} severity="error">{sourceMutation.error instanceof Error ? sourceMutation.error.message : 'Source settings update failed.'}</Alert> : null}
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 0.5, mt: 2 }}>
              {SOURCE_CATEGORIES.map((item) => (
                <FormControlLabel
                  key={item.key}
                  control={<Checkbox checked={sourceSettings[item.key]} onChange={() => setSourceSettings({ ...sourceSettings, [item.key]: !sourceSettings[item.key] })} />}
                  label={item.label}
                  disabled={!canChangeSources || sourceMutation.isPending}
                />
              ))}
            </Box>
            <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1 }}>
              <Chip size="small" variant="outlined" label={`Preflight ${formatDateTime(controls.sourceSettings.lastSourcePreflightAt)}`} />
              <Chip size="small" variant="outlined" label={`Sync ${formatDateTime(controls.sourceSettings.lastSyncAt)}`} />
            </Stack>
            <Button
              variant="contained"
              sx={{ mt: 2, float: 'right' }}
              onClick={() => sourceMutation.mutate()}
              disabled={!canChangeSources || sourceMutation.isPending || !anySourceEnabled}
            >
              Save source controls
            </Button>
          </Paper>

          <Paper sx={{ p: 2 }}>
            <SectionHeading icon={<ReportProblemOutlinedIcon />} title="Merchant handoff" />
            {supportMutation.isError ? <Alert sx={{ mt: 2 }} severity="error">{supportMutation.error instanceof Error ? supportMutation.error.message : 'Support profile update failed.'}</Alert> : null}
            <Stack spacing={1.5} sx={{ mt: 2 }}>
              <TextField label="Contact email" value={contactEmail} onChange={(event) => setContactEmail(event.target.value)} disabled={!canChangeSupport || supportMutation.isPending} />
              <TextField label="Contact URL" value={contactUrl} onChange={(event) => setContactUrl(event.target.value)} disabled={!canChangeSupport || supportMutation.isPending} />
              <TextField label="Help center URL" value={helpCenterUrl} onChange={(event) => setHelpCenterUrl(event.target.value)} disabled={!canChangeSupport || supportMutation.isPending} />
              <TextField label="Order lookup page" value={orderLookupPageUrl} onChange={(event) => setOrderLookupPageUrl(event.target.value)} disabled={!canChangeSupport || supportMutation.isPending} />
              <TextField label="Policy note" minRows={3} multiline value={supportPolicyNote} onChange={(event) => setSupportPolicyNote(event.target.value)} disabled={!canChangeSupport || supportMutation.isPending} />
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                <StatusChip status={controls.supportProfile.merchantHandoffConfigured ? 'READY' : 'NEEDS_SETUP'} label={controls.supportProfile.merchantHandoffConfigured ? 'Handoff ready' : 'Needs handoff'} />
                <Button variant="contained" onClick={() => supportMutation.mutate()} disabled={!canChangeSupport || supportMutation.isPending}>
                  Save handoff
                </Button>
              </Stack>
            </Stack>
          </Paper>
        </Stack>
      </Box>
    </Stack>
  )
}

function SetupTab({ store, latestRun, templates }: { store: PartnerStore; latestRun?: PartnerVerificationRun; templates: PartnerTemplateApplication[] }) {
  const checklist = [
    { label: 'Companion install is active', status: store.installStatus === 'INSTALLED' ? 'PASSED' : 'NEEDS_SETUP', detail: titleize(store.installStatus) },
    { label: 'Merchant-approved access is active', status: store.assignmentStatus === 'ACTIVE' ? 'PASSED' : store.assignmentStatus, detail: formatDateTime(store.approvedAt) },
    { label: 'Store content is synced', status: store.knowledgeSyncStatus === 'SYNCED' ? 'PASSED' : store.knowledgeSyncStatus, detail: formatDateTime(store.lastSyncAt) },
    { label: 'Source readiness is green', status: store.readinessStatus === 'READY' ? 'PASSED' : store.readinessStatus, detail: store.topBlocker },
    { label: 'Storefront widget is enabled', status: store.widgetStatus === 'ENABLED' ? 'PASSED' : store.widgetStatus, detail: `${store.enabledSurfaces.length} configured surfaces` },
    { label: 'Playbook is applied', status: templates.length > 0 ? 'PASSED' : 'NEEDS_SETUP', detail: `${templates.length} applied playbook${templates.length === 1 ? '' : 's'}` },
    { label: 'Launch verification has proof', status: latestRun?.status ?? 'NOT_RUN', detail: latestRun ? `${latestRun.passedSteps}/${latestRun.totalSteps} passed` : 'Run Starter launch readiness' },
  ]
  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h3">Implementation checklist</Typography>
      <Stack spacing={1.25} sx={{ mt: 2 }}>
        {checklist.map((item) => (
          <TimelineRow key={item.label} title={item.label} subtitle={item.detail} status={item.status} />
        ))}
      </Stack>
    </Paper>
  )
}

function VerificationTab({ storeId }: { storeId: string }) {
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const packQuery = useQuery({ queryKey: ['store-verification-pack', storeId], queryFn: () => getStoreVerificationPack(api, storeId) })
  const runsQuery = useQuery({ queryKey: ['store-verification-runs', storeId], queryFn: () => listStoreVerificationRuns(api, storeId) })
  const [manualStatus, setManualStatus] = useState('PASSED')
  const [manualNote, setManualNote] = useState('')
  const runMutation = useMutation({
    mutationFn: () => runStoreVerification(api, storeId, packQuery.data?.id ?? 'starter-launch-readiness'),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['store-verification-runs', storeId] })
      await queryClient.invalidateQueries({ queryKey: ['store-evidence-bundles', storeId] })
    },
  })
  const manualMutation = useMutation({
    mutationFn: () => completeVerificationStep(api, storeId, 'partner-manual-review', { status: manualStatus, evidenceNote: manualNote }),
    onSuccess: async () => {
      setManualNote('')
      await queryClient.invalidateQueries({ queryKey: ['store-verification-runs', storeId] })
    },
  })
  return (
    <Stack spacing={2}>
      <Paper sx={{ p: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
          <Stack spacing={1}>
            <Typography variant="h3">{packQuery.data?.name ?? 'Store verification pack'}</Typography>
            <Typography color="text.secondary">{packQuery.data?.description ?? 'Loading verification contract.'}</Typography>
          </Stack>
          <Button variant="contained" startIcon={<FactCheckOutlinedIcon />} onClick={() => runMutation.mutate()} disabled={runMutation.isPending || packQuery.isLoading}>
            Run verification
          </Button>
        </Stack>
      </Paper>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h3">Manual verification step</Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mt: 1.5 }}>
          <TextField select label="Status" value={manualStatus} onChange={(event) => setManualStatus(event.target.value)} sx={{ minWidth: 180 }}>
            {['PASSED', 'FAILED', 'BLOCKED', 'PARTIAL'].map((status) => <MenuItem key={status} value={status}>{titleize(status)}</MenuItem>)}
          </TextField>
          <TextField label="Evidence note" value={manualNote} onChange={(event) => setManualNote(event.target.value)} fullWidth />
          <Button onClick={() => manualMutation.mutate()} disabled={manualMutation.isPending || manualNote.trim().length === 0}>Mark step</Button>
        </Stack>
      </Paper>
      <VerificationRunList runs={runsQuery.data ?? []} loading={runsQuery.isLoading} />
    </Stack>
  )
}

function VerificationRunList({ runs, loading }: { runs: PartnerVerificationRun[]; loading: boolean }) {
  if (loading) {
    return <LinearProgress />
  }
  if (runs.length === 0) {
    return <Paper sx={{ p: 2 }}><Typography color="text.secondary">No verification runs yet.</Typography></Paper>
  }
  return (
    <Stack spacing={1.5}>
      {runs.map((run) => (
        <VerificationRunProof key={run.id} run={run} />
      ))}
    </Stack>
  )
}

function VerificationRunProof({ run }: { run: PartnerVerificationRun }) {
  return (
    <Accordion>
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} justifyContent="space-between" sx={{ width: '100%', pr: 1 }}>
          <Box>
            <Typography fontWeight={800}>{run.packName}</Typography>
            <Typography variant="caption" color="text.secondary">{formatDateTime(run.startedAt)}</Typography>
          </Box>
          <Stack direction="row" spacing={1} alignItems="center">
            <StatusChip status={run.status} />
            <Typography>{run.passedSteps}/{run.totalSteps} passed</Typography>
          </Stack>
        </Stack>
      </AccordionSummary>
      <AccordionDetails>
        <Stack spacing={1.25}>
          {run.steps.map((step) => <VerificationStepProof key={step.stepId} step={step} />)}
        </Stack>
      </AccordionDetails>
    </Accordion>
  )
}

function VerificationStepProof({ step }: { step: PartnerVerificationStep }) {
  return (
    <Box sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="space-between">
        <Box>
          <Typography fontWeight={800}>{step.label}</Typography>
          <Typography color="text.secondary">{step.partnerSafeMessage}</Typography>
        </Box>
        <StatusChip status={step.status} />
      </Stack>
      {step.suggestedFix ? <Typography variant="caption" color="text.secondary">{step.suggestedFix}</Typography> : null}
      {step.evidence.length > 0 ? (
        <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1 }}>
          {step.evidence.map((item) => <Chip key={item} size="small" label={item} variant="outlined" />)}
        </Stack>
      ) : null}
    </Box>
  )
}

function EvidenceTab({ storeId }: { storeId: string }) {
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const bundlesQuery = useQuery({ queryKey: ['store-evidence-bundles', storeId], queryFn: () => listStoreEvidenceBundles(api, storeId) })
  const createMutation = useMutation({
    mutationFn: () => createStoreEvidenceBundle(api, storeId, { bundleKind: 'LAUNCH_PACKET' }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['store-evidence-bundles', storeId] })
      await queryClient.invalidateQueries({ queryKey: ['evidence-bundles'] })
    },
  })
  const columns: DataColumn<PartnerEvidenceBundle>[] = [
    { key: 'name', header: 'Bundle', render: (row) => <Typography>{row.bundleName}</Typography> },
    { key: 'kind', header: 'Kind', render: (row) => <Typography>{titleize(row.bundleKind)}</Typography> },
    { key: 'status', header: 'Status', render: (row) => <StatusChip status={row.status} /> },
    { key: 'generated', header: 'Generated', render: (row) => <Typography color="text.secondary">{formatDateTime(row.generatedAt)}</Typography> },
  ]
  return (
    <Stack spacing={2}>
      <Paper sx={{ p: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
          <Box>
            <Typography variant="h3">Evidence bundles</Typography>
            <Typography color="text.secondary">Generated bundles are immutable merchant-safe snapshots.</Typography>
          </Box>
          <Button variant="contained" startIcon={<FolderZipOutlinedIcon />} onClick={() => createMutation.mutate()} disabled={createMutation.isPending}>
            Export launch evidence
          </Button>
        </Stack>
      </Paper>
      <DataTable columns={columns} rows={bundlesQuery.data ?? []} getRowKey={(row) => row.id} loading={bundlesQuery.isLoading} />
    </Stack>
  )
}

function ThinkerTab({ storeId }: { storeId: string }) {
  const { api } = useSupabaseAuth()
  const navigate = useNavigate()
  const sessionsQuery = useQuery({ queryKey: ['thinker-sessions', storeId], queryFn: () => listStoreThinkerSessions(api, storeId) })
  return (
    <Stack spacing={2}>
      <Paper sx={{ p: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'center' }}>
          <Box>
            <Typography variant="h3">Thinker diagnosis</Typography>
            <Typography color="text.secondary">Read-only diagnostic sessions and partner-safe evidence for this assigned store.</Typography>
          </Box>
          <Button variant="contained" onClick={() => navigate(`/thinker?storeId=${encodeURIComponent(storeId)}`)}>
            Open Thinker queue
          </Button>
        </Stack>
      </Paper>
      {sessionsQuery.isLoading ? <LinearProgress /> : null}
      {(sessionsQuery.data ?? []).length === 0 ? (
        <Alert severity="info">No Thinker sessions are captured for this store yet.</Alert>
      ) : (
        <Paper sx={{ p: 2 }}>
          <Stack spacing={1.25}>
            {(sessionsQuery.data ?? []).slice(0, 6).map((session) => (
              <TimelineRow
                key={session.id}
                title={titleize(session.issueCategory)}
                subtitle={`${session.evidenceCount} evidence items - ${session.userQuestion}`}
                status={session.status}
                meta={formatDateTime(session.startedAt)}
              />
            ))}
          </Stack>
        </Paper>
      )}
    </Stack>
  )
}

function NotesTab({ storeId }: { storeId: string }) {
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const notesQuery = useQuery({ queryKey: ['store-notes', storeId], queryFn: () => listStoreNotes(api, storeId) })
  const createMutation = useMutation({
    mutationFn: () => createStoreNote(api, storeId, body),
    onSuccess: async () => {
      setBody('')
      await queryClient.invalidateQueries({ queryKey: ['store-notes', storeId] })
    },
  })
  return (
    <Stack spacing={2}>
      <Paper sx={{ p: 2 }}>
        <Stack spacing={1.5}>
          <Typography variant="h3">Partner notes</Typography>
          <TextField label="Add note" minRows={4} multiline value={body} onChange={(event) => setBody(event.target.value)} />
          <Button sx={{ alignSelf: 'flex-end' }} variant="contained" onClick={() => createMutation.mutate()} disabled={body.trim().length === 0 || createMutation.isPending}>
            Add note
          </Button>
        </Stack>
      </Paper>
      <Stack spacing={1.5}>
        {(notesQuery.data ?? []).map((note) => (
          <Paper key={note.id} sx={{ p: 2 }}>
            <Typography sx={{ whiteSpace: 'pre-wrap' }}>{note.bodyMarkdown}</Typography>
            <Typography variant="caption" color="text.secondary">{note.authorName} · {formatDateTime(note.createdAt)}</Typography>
          </Paper>
        ))}
        {(notesQuery.data ?? []).length === 0 && !notesQuery.isLoading ? <Typography color="text.secondary">No partner notes yet.</Typography> : null}
      </Stack>
    </Stack>
  )
}

function Checklist({ title, items }: { title: string; items: string[] }) {
  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h3">{title}</Typography>
      <Stack component="ol" spacing={1} sx={{ pl: 3 }}>
        {items.map((item) => (
          <Typography component="li" key={item}>{item}</Typography>
        ))}
      </Stack>
    </Paper>
  )
}

function EscalationDialog({ open, onClose, storeId }: { open: boolean; onClose: () => void; storeId: string }) {
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const form = useForm<EscalationForm>({
    resolver: zodResolver(escalationFormSchema),
    defaultValues: { title: '', severity: 'MEDIUM', description: '', reproductionSteps: '', expectedBehavior: '', actualBehavior: '', impact: '', nextAction: '', evidenceBundleIdsText: '' },
  })
  const mutation = useMutation({
    mutationFn: ({ evidenceBundleIdsText, ...values }: EscalationForm) => createEscalation(api, storeId, {
      ...values,
      evidenceBundleIds: evidenceBundleIdsText?.split(',').map((item) => item.trim()).filter(Boolean) ?? [],
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['escalations'] })
      onClose()
      form.reset()
    },
  })

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Escalate blocker</DialogTitle>
      <DialogContent>
        <Stack component="form" id="escalation-form" spacing={1.5} sx={{ mt: 1 }} onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
          {mutation.isError ? <Alert severity="error">{mutation.error instanceof Error ? mutation.error.message : 'Escalation failed.'}</Alert> : null}
          <TextField label="Title" {...form.register('title')} error={Boolean(form.formState.errors.title)} helperText={form.formState.errors.title?.message} />
          <TextField label="Severity" {...form.register('severity')} />
          <TextField label="Description" minRows={4} multiline {...form.register('description')} error={Boolean(form.formState.errors.description)} helperText={form.formState.errors.description?.message} />
          <TextField label="Reproduction steps" minRows={3} multiline {...form.register('reproductionSteps')} />
          <TextField label="Expected behavior" {...form.register('expectedBehavior')} />
          <TextField label="Actual behavior" {...form.register('actualBehavior')} />
          <TextField label="Impact" {...form.register('impact')} />
          <TextField label="Next action" {...form.register('nextAction')} />
          <TextField label="Evidence bundle IDs" {...form.register('evidenceBundleIdsText')} placeholder="peb-... , peb-..." />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={mutation.isPending}>Cancel</Button>
        <Button type="submit" form="escalation-form" variant="contained" disabled={mutation.isPending}>Create escalation</Button>
      </DialogActions>
    </Dialog>
  )
}
