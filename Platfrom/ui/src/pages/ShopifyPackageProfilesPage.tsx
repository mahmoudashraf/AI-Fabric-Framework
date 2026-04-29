import BlockRoundedIcon from '@mui/icons-material/BlockRounded'
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import PlayCircleOutlineRoundedIcon from '@mui/icons-material/PlayCircleOutlineRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import TuneRoundedIcon from '@mui/icons-material/TuneRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  fetchDeploymentTemplates,
  fetchMarketplacePlugins,
  fetchPlatformVerificationSuiteDefinitions,
  fetchShopifyPackageProfiles,
  type DeploymentTemplateSummary,
  type MarketplacePluginSummary,
  type PlatformVerificationSuiteDefinitionSummary,
  type ShopifyCompanionPackageProfileSummary,
  type UpsertShopifyCompanionPackageProfileRequest,
  updateShopifyPackageProfileStatus,
  upsertShopifyPackageProfile,
} from '../api/platformApi'

type ProfileFormState = UpsertShopifyCompanionPackageProfileRequest & {
  profileKey: string
  detailsJson: string
  reason: string
}

const statusOptions = ['ACTIVE', 'DRAFT', 'DISABLED']
const packageOptions = ['FREE', 'STARTER', 'ELITE', 'ENTERPRISE']
const tierOptions = ['FREE', 'STARTER', 'ELITE', 'ENTERPRISE']
const costPostureOptions = ['LOW', 'STANDARD', 'QUALITY', 'HIGH', 'ENTERPRISE']
const vectorProvisioningModes = ['EXTERNAL_EXISTING', 'PLATFORM_MANAGED', 'LOCAL_MANAGED']
const vectorStoragePostures = ['SHARED', 'DEDICATED', 'EMBEDDED']
const runtimeProfileDefaults = ['LOW_COST', 'BALANCED', 'HIGH_QUALITY', 'ENTERPRISE_DEDICATED']
const vectorProfileDefaults = ['QDRANT_SHARED', 'QDRANT_DEDICATED', 'PINECONE_SHARED', 'WEAVIATE_SHARED', 'ZILLIZ_DEDICATED']
const vectorStrategyDefaults = ['qdrant', 'pinecone', 'weaviate', 'milvus', 'zilliz', 'pgvector']
const templatePluginDefaults = ['mkp-template-shopify-companion']
const deploymentTemplateDefaults = ['dev-openai-qdrant']
const inferencePluginDefaults = ['mkp-inference-shared-embeddings', 'mkp-inference-premium-hybrid']
const verificationPackDefaults = [
  'starter-launch-readiness',
  'shopify-companion-starter-readiness',
  'shopify-companion-elite-readiness',
]

type ProfileBlueprint = {
  key: string
  label: string
  form: Partial<ProfileFormState>
}

type SelectionOption = {
  value: string
  label: string
  helper?: string
}

const profileBlueprints: ProfileBlueprint[] = [
  {
    key: 'FREE_LOW_COST',
    label: 'Free / low cost shared Qdrant',
    form: {
      profileKey: 'LOW_COST',
      packageKey: 'FREE',
      tierKey: 'FREE',
      runtimeProfileKey: 'LOW_COST',
      vectorProfileKey: 'QDRANT_SHARED',
      displayName: 'Low cost',
      description: 'Entry package profile using shared inference and shared Qdrant.',
      costPosture: 'LOW',
      inferencePluginId: 'mkp-inference-shared-embeddings',
      vectorStrategy: 'qdrant',
      vectorProvisioningMode: 'EXTERNAL_EXISTING',
      vectorStoragePosture: 'SHARED',
      verificationPackId: 'starter-launch-readiness',
    },
  },
  {
    key: 'STARTER_BALANCED',
    label: 'Starter / balanced shared Qdrant',
    form: {
      profileKey: 'BALANCED',
      packageKey: 'STARTER',
      tierKey: 'STARTER',
      runtimeProfileKey: 'BALANCED',
      vectorProfileKey: 'QDRANT_SHARED',
      displayName: 'Balanced',
      description: 'Starter profile for managed Shopify Companion launch readiness.',
      costPosture: 'STANDARD',
      inferencePluginId: 'mkp-inference-shared-embeddings',
      vectorStrategy: 'qdrant',
      vectorProvisioningMode: 'EXTERNAL_EXISTING',
      vectorStoragePosture: 'SHARED',
      verificationPackId: 'shopify-companion-starter-readiness',
    },
  },
  {
    key: 'ELITE_HIGH_QUALITY',
    label: 'Elite / high quality shared Qdrant',
    form: {
      profileKey: 'HIGH_QUALITY',
      packageKey: 'ELITE',
      tierKey: 'ELITE',
      runtimeProfileKey: 'HIGH_QUALITY',
      vectorProfileKey: 'QDRANT_SHARED',
      displayName: 'High quality',
      description: 'Elite Shopify Companion profile with premium inference and shared managed vector storage.',
      costPosture: 'QUALITY',
      inferencePluginId: 'mkp-inference-premium-hybrid',
      vectorStrategy: 'qdrant',
      vectorProvisioningMode: 'EXTERNAL_EXISTING',
      vectorStoragePosture: 'SHARED',
      verificationPackId: 'shopify-companion-elite-readiness',
    },
  },
  {
    key: 'ENTERPRISE_DEDICATED',
    label: 'Enterprise / dedicated vector posture',
    form: {
      profileKey: 'ENTERPRISE_DEDICATED',
      packageKey: 'ENTERPRISE',
      tierKey: 'ENTERPRISE',
      runtimeProfileKey: 'ENTERPRISE_DEDICATED',
      vectorProfileKey: 'QDRANT_DEDICATED',
      displayName: 'Enterprise dedicated',
      description: 'Enterprise package profile for dedicated vector isolation and higher quality inference.',
      costPosture: 'ENTERPRISE',
      inferencePluginId: 'mkp-inference-premium-hybrid',
      vectorStrategy: 'qdrant',
      vectorProvisioningMode: 'PLATFORM_MANAGED',
      vectorStoragePosture: 'DEDICATED',
      verificationPackId: 'shopify-companion-elite-readiness',
    },
  },
]

const emptyProfileForm: ProfileFormState = {
  profileKey: '',
  packageKey: 'STARTER',
  tierKey: 'STARTER',
  runtimeProfileKey: 'BALANCED',
  vectorProfileKey: 'QDRANT_SHARED',
  displayName: '',
  description: '',
  costPosture: 'STANDARD',
  templatePluginId: 'mkp-template-shopify-companion',
  templatePluginVersion: '',
  deploymentTemplateId: 'dev-openai-qdrant',
  inferencePluginId: 'mkp-inference-shared-embeddings',
  vectorStrategy: 'qdrant',
  vectorProvisioningMode: 'EXTERNAL_EXISTING',
  vectorStoragePosture: 'SHARED',
  verificationPackId: 'starter-launch-readiness',
  status: 'DRAFT',
  detailsJson: '{}',
  reason: '',
}

function statusColor(status: string | null | undefined): 'success' | 'warning' | 'default' | 'error' {
  switch ((status ?? '').toUpperCase()) {
    case 'ACTIVE':
      return 'success'
    case 'DRAFT':
      return 'warning'
    case 'DISABLED':
      return 'default'
    default:
      return 'error'
  }
}

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '-'
}

function profileToForm(profile: ShopifyCompanionPackageProfileSummary): ProfileFormState {
  return {
    profileKey: profile.profileKey,
    packageKey: profile.packageKey,
    tierKey: profile.tierKey,
    runtimeProfileKey: profile.runtimeProfileKey,
    vectorProfileKey: profile.vectorProfileKey,
    displayName: profile.displayName,
    description: profile.description ?? '',
    costPosture: profile.costPosture ?? 'STANDARD',
    templatePluginId: profile.templatePluginId ?? '',
    templatePluginVersion: profile.templatePluginVersion ?? '',
    deploymentTemplateId: profile.deploymentTemplateId ?? '',
    inferencePluginId: profile.inferencePluginId ?? '',
    vectorStrategy: profile.vectorStrategy ?? 'qdrant',
    vectorProvisioningMode: profile.vectorProvisioningMode ?? 'EXTERNAL_EXISTING',
    vectorStoragePosture: profile.vectorStoragePosture ?? 'SHARED',
    verificationPackId: profile.verificationPackId ?? '',
    status: profile.status,
    detailsJson: profile.detailsJson ?? '{}',
    reason: '',
  }
}

function buildPayload(form: ProfileFormState): UpsertShopifyCompanionPackageProfileRequest {
  return {
    packageKey: form.packageKey,
    tierKey: form.tierKey,
    runtimeProfileKey: form.runtimeProfileKey,
    vectorProfileKey: form.vectorProfileKey,
    displayName: form.displayName,
    description: form.description,
    costPosture: form.costPosture,
    templatePluginId: form.templatePluginId,
    templatePluginVersion: form.templatePluginVersion,
    deploymentTemplateId: form.deploymentTemplateId,
    inferencePluginId: form.inferencePluginId,
    vectorStrategy: form.vectorStrategy,
    vectorProvisioningMode: form.vectorProvisioningMode,
    vectorStoragePosture: form.vectorStoragePosture,
    verificationPackId: form.verificationPackId,
    status: form.status,
    detailsJson: form.detailsJson,
    reason: form.reason,
  }
}

function validateDetailsJson(value: string): string | null {
  if (!value.trim()) {
    return null
  }
  try {
    const parsed = JSON.parse(value)
    if (parsed == null || Array.isArray(parsed) || typeof parsed !== 'object') {
      return 'Details must be a JSON object.'
    }
    return null
  } catch {
    return 'Details must be valid JSON.'
  }
}

function uniqueValues(values: Array<string | null | undefined>): string[] {
  return Array.from(
    new Set(
      values
        .map((value) => value?.trim())
        .filter((value): value is string => Boolean(value)),
    ),
  )
}

function mergeOptionValues(...groups: Array<Array<string | null | undefined>>): string[] {
  return uniqueValues(groups.flat())
}

function optionsFromValues(values: string[]): SelectionOption[] {
  return values.map((value) => ({ value, label: value }))
}

function includeCurrentOption(options: SelectionOption[], currentValue: string | null | undefined): SelectionOption[] {
  const value = currentValue?.trim()
  if (!value || options.some((option) => option.value === value)) {
    return options
  }
  return [...options, { value, label: value, helper: 'Current saved value' }]
}

function marketplacePluginOptions(
  plugins: MarketplacePluginSummary[],
  currentValues: Array<string | null | undefined>,
  defaults: string[],
  predicate: (plugin: MarketplacePluginSummary) => boolean,
): SelectionOption[] {
  const pluginOptions = plugins
    .filter(predicate)
    .map((plugin) => ({
      value: plugin.id,
      label: plugin.displayName ? `${plugin.displayName} (${plugin.id})` : plugin.id,
      helper: [plugin.pluginType, plugin.latestVersion ? `latest ${plugin.latestVersion}` : null].filter(Boolean).join(' · '),
    }))
  const knownValues = mergeOptionValues(defaults, currentValues, pluginOptions.map((option) => option.value))
  return knownValues.map((value) => pluginOptions.find((option) => option.value === value) ?? { value, label: value })
}

function deploymentTemplateOptions(
  templates: DeploymentTemplateSummary[],
  profiles: ShopifyCompanionPackageProfileSummary[],
  currentValue: string,
): SelectionOption[] {
  const templateOptions = templates.map((template) => ({
    value: template.id,
    label: `${template.name} (${template.id})`,
    helper: `${template.runtimeProfile} · ${template.vectorStrategy}`,
  }))
  const knownValues = mergeOptionValues(deploymentTemplateDefaults, profiles.map((profile) => profile.deploymentTemplateId), [currentValue], templateOptions.map((option) => option.value))
  return knownValues.map((value) => templateOptions.find((option) => option.value === value) ?? { value, label: value })
}

function verificationPackOptions(
  suites: PlatformVerificationSuiteDefinitionSummary[],
  profiles: ShopifyCompanionPackageProfileSummary[],
  currentValue: string,
): SelectionOption[] {
  const suiteOptions = suites.map((suite) => ({
    value: suite.key,
    label: `${suite.label} (${suite.key})`,
    helper: suite.releaseBlocking ? 'Release blocking' : 'Non-blocking',
  }))
  const knownValues = mergeOptionValues(verificationPackDefaults, profiles.map((profile) => profile.verificationPackId), [currentValue], suiteOptions.map((option) => option.value))
  return knownValues.map((value) => suiteOptions.find((option) => option.value === value) ?? { value, label: value })
}

function isTemplatePlugin(plugin: MarketplacePluginSummary): boolean {
  const haystack = `${plugin.id} ${plugin.displayName} ${plugin.pluginType}`.toLowerCase()
  return haystack.includes('template') || haystack.includes('companion')
}

function isInferencePlugin(plugin: MarketplacePluginSummary): boolean {
  const haystack = `${plugin.id} ${plugin.displayName} ${plugin.pluginType}`.toLowerCase()
  return haystack.includes('inference') || (plugin.contributions?.inferenceProfileIds?.length ?? 0) > 0
}

function selectInput(
  label: string,
  value: string,
  options: SelectionOption[],
  onChange: (value: string) => void,
  helperText?: string,
) {
  return (
    <TextField select label={label} value={value} onChange={(event) => onChange(event.target.value)} helperText={helperText ?? ' '} fullWidth>
      {includeCurrentOption(options, value).map((option) => (
        <MenuItem key={option.value} value={option.value}>
          <Stack spacing={0.25}>
            <Typography variant="body2">{option.label}</Typography>
            {option.helper ? (
              <Typography variant="caption" color="text.secondary">
                {option.helper}
              </Typography>
            ) : null}
          </Stack>
        </MenuItem>
      ))}
    </TextField>
  )
}

export function ShopifyPackageProfilesPage() {
  const queryClient = useQueryClient()
  const [selectedProfileKey, setSelectedProfileKey] = useState<string>('')
  const [draftMode, setDraftMode] = useState(false)
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [packageFilter, setPackageFilter] = useState<string>('ALL')
  const [searchTerm, setSearchTerm] = useState('')
  const [form, setForm] = useState<ProfileFormState>(emptyProfileForm)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const profilesQuery = useQuery({
    queryKey: ['shopify-package-profiles', 'all'],
    queryFn: () => fetchShopifyPackageProfiles(false),
  })

  const deploymentTemplatesQuery = useQuery({
    queryKey: ['deployment-templates'],
    queryFn: fetchDeploymentTemplates,
  })

  const marketplacePluginsQuery = useQuery({
    queryKey: ['marketplace-plugins'],
    queryFn: fetchMarketplacePlugins,
  })

  const verificationSuitesQuery = useQuery({
    queryKey: ['verification-suites', 'definitions'],
    queryFn: fetchPlatformVerificationSuiteDefinitions,
  })

  const profiles = profilesQuery.data ?? []
  const selectedProfile = profiles.find((profile) => profile.profileKey === selectedProfileKey) ?? null

  useEffect(() => {
    if (!draftMode && !selectedProfileKey && profiles.length > 0) {
      setSelectedProfileKey(profiles[0].profileKey)
    }
  }, [draftMode, profiles, selectedProfileKey])

  useEffect(() => {
    if (!draftMode && selectedProfile) {
      setForm(profileToForm(selectedProfile))
    }
  }, [draftMode, selectedProfile])

  const detailsJsonError = useMemo(() => validateDetailsJson(form.detailsJson), [form.detailsJson])

  const filteredProfiles = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLowerCase()
    return profiles.filter((profile) => {
      const statusMatches = statusFilter === 'ALL' || profile.status === statusFilter
      const packageMatches = packageFilter === 'ALL' || profile.packageKey === packageFilter
      const textMatches = !normalizedSearch
        || [profile.profileKey, profile.displayName, profile.inferencePluginId, profile.deploymentTemplateId, profile.verificationPackId]
          .filter(Boolean)
          .some((value) => value!.toLowerCase().includes(normalizedSearch))
      return statusMatches && packageMatches && textMatches
    })
  }, [packageFilter, profiles, searchTerm, statusFilter])

  const activeProfiles = profiles.filter((profile) => profile.status === 'ACTIVE')
  const selectedActiveConflict = form.status === 'ACTIVE'
    ? activeProfiles.find(
        (profile) =>
          profile.profileKey !== form.profileKey
          && profile.packageKey === form.packageKey
          && profile.tierKey === form.tierKey,
      )
    : null

  const profileKeyOptions = useMemo(
    () => {
      const existingKeys = new Set(profiles.map((profile) => profile.profileKey))
      const selectableKeys = draftMode
        ? profileBlueprints.map((blueprint) => blueprint.form.profileKey).filter((key) => key && !existingKeys.has(key))
        : profiles.map((profile) => profile.profileKey)
      return optionsFromValues(mergeOptionValues(selectableKeys, [form.profileKey]))
    },
    [draftMode, form.profileKey, profiles],
  )
  const runtimeProfileOptions = useMemo(
    () =>
      optionsFromValues(
        mergeOptionValues(
          runtimeProfileDefaults,
          profileBlueprints.map((blueprint) => blueprint.form.runtimeProfileKey),
          profiles.map((profile) => profile.runtimeProfileKey),
          (deploymentTemplatesQuery.data ?? []).map((template) => template.runtimeProfile),
          [form.runtimeProfileKey],
        ),
      ),
    [deploymentTemplatesQuery.data, form.runtimeProfileKey, profiles],
  )
  const vectorProfileOptions = useMemo(
    () =>
      optionsFromValues(
        mergeOptionValues(
          vectorProfileDefaults,
          profileBlueprints.map((blueprint) => blueprint.form.vectorProfileKey),
          profiles.map((profile) => profile.vectorProfileKey),
          [form.vectorProfileKey],
        ),
      ),
    [form.vectorProfileKey, profiles],
  )
  const vectorStrategyOptions = useMemo(
    () =>
      optionsFromValues(
        mergeOptionValues(
          vectorStrategyDefaults,
          profiles.map((profile) => profile.vectorStrategy),
          (deploymentTemplatesQuery.data ?? []).map((template) => template.vectorStrategy),
          [form.vectorStrategy],
        ),
      ),
    [deploymentTemplatesQuery.data, form.vectorStrategy, profiles],
  )
  const templatePluginOptions = useMemo(
    () =>
      marketplacePluginOptions(
        marketplacePluginsQuery.data ?? [],
        profiles.map((profile) => profile.templatePluginId).concat(form.templatePluginId),
        templatePluginDefaults,
        isTemplatePlugin,
      ),
    [form.templatePluginId, marketplacePluginsQuery.data, profiles],
  )
  const templateVersionOptions = useMemo(() => {
    const selectedPlugin = (marketplacePluginsQuery.data ?? []).find((plugin) => plugin.id === form.templatePluginId)
    return [
      { value: '', label: 'Use latest published version' },
      ...optionsFromValues(mergeOptionValues([selectedPlugin?.latestVersion, form.templatePluginVersion])),
    ]
  }, [form.templatePluginId, form.templatePluginVersion, marketplacePluginsQuery.data])
  const deploymentTemplateSelectOptions = useMemo(
    () => deploymentTemplateOptions(deploymentTemplatesQuery.data ?? [], profiles, form.deploymentTemplateId),
    [deploymentTemplatesQuery.data, form.deploymentTemplateId, profiles],
  )
  const inferencePluginOptions = useMemo(
    () =>
      marketplacePluginOptions(
        marketplacePluginsQuery.data ?? [],
        profiles.map((profile) => profile.inferencePluginId).concat(form.inferencePluginId),
        inferencePluginDefaults,
        isInferencePlugin,
      ),
    [form.inferencePluginId, marketplacePluginsQuery.data, profiles],
  )
  const verificationPackSelectOptions = useMemo(
    () => verificationPackOptions(verificationSuitesQuery.data ?? [], profiles, form.verificationPackId),
    [form.verificationPackId, profiles, verificationSuitesQuery.data],
  )

  const upsertMutation = useMutation({
    mutationFn: (payload: ProfileFormState) => upsertShopifyPackageProfile(payload.profileKey, buildPayload(payload)),
    onSuccess: async (profile) => {
      setMessage({ type: 'success', text: `Saved ${profile.profileKey}.` })
      setDraftMode(false)
      setSelectedProfileKey(profile.profileKey)
      await queryClient.invalidateQueries({ queryKey: ['shopify-package-profiles'] })
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to save package profile.' })
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ profileKey, status }: { profileKey: string; status: string }) =>
      updateShopifyPackageProfileStatus(profileKey, {
        status,
        reason: form.reason || `Set profile ${status.toLowerCase()}`,
      }),
    onSuccess: async (profile) => {
      setMessage({ type: 'success', text: `${profile.profileKey} is now ${profile.status}.` })
      setSelectedProfileKey(profile.profileKey)
      await queryClient.invalidateQueries({ queryKey: ['shopify-package-profiles'] })
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to update profile status.' })
    },
  })

  const updateForm = (patch: Partial<ProfileFormState>) => setForm((current) => ({ ...current, ...patch }))

  const applyBlueprint = (blueprintKey: string) => {
    const blueprint = profileBlueprints.find((candidate) => candidate.key === blueprintKey)
    if (!blueprint) {
      return
    }
    const nextProfileKey = draftMode
      ? blueprint.form.profileKey ?? form.profileKey
      : form.profileKey
    setForm((current) => ({
      ...current,
      ...blueprint.form,
      profileKey: nextProfileKey,
      status: current.status,
      templatePluginId: current.templatePluginId || emptyProfileForm.templatePluginId,
      templatePluginVersion: current.templatePluginVersion,
      deploymentTemplateId: current.deploymentTemplateId || emptyProfileForm.deploymentTemplateId,
      reason: current.reason || `Apply ${blueprint.label}`,
      detailsJson: current.detailsJson,
    }))
  }

  const startNewProfile = () => {
    const usedProfileKeys = new Set(profiles.map((profile) => profile.profileKey))
    const blueprint = profileBlueprints.find((candidate) => !usedProfileKeys.has(candidate.form.profileKey ?? '')) ?? profileBlueprints[1]
    const generatedKey = `SHOPIFY_PROFILE_${Date.now().toString(36).toUpperCase()}`
    setSelectedProfileKey('')
    setDraftMode(true)
    setForm({
      ...emptyProfileForm,
      ...blueprint.form,
      profileKey: usedProfileKeys.has(blueprint.form.profileKey ?? '') ? generatedKey : blueprint.form.profileKey ?? generatedKey,
      status: 'DRAFT',
      reason: `Create ${blueprint.label}`,
    })
  }

  const duplicateSelectedProfile = () => {
    const source = selectedProfile ?? profiles.find((profile) => profile.profileKey === form.profileKey)
    if (!source) {
      return
    }
    setSelectedProfileKey('')
    setDraftMode(true)
    setForm({
      ...profileToForm(source),
      profileKey: `${source.profileKey}_COPY`,
      displayName: `${source.displayName} copy`,
      status: 'DRAFT',
      reason: `Duplicate ${source.profileKey}`,
    })
  }

  const resetForm = () => {
    setForm(!draftMode && selectedProfile ? profileToForm(selectedProfile) : { ...emptyProfileForm, status: 'DRAFT' })
  }

  const canSave = form.profileKey.trim().length > 0
    && form.displayName.trim().length > 0
    && form.templatePluginId.trim().length > 0
    && form.deploymentTemplateId.trim().length > 0
    && form.inferencePluginId.trim().length > 0
    && form.verificationPackId.trim().length > 0
    && !detailsJsonError

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', lg: 'center' }}>
        <div>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Shopify Profiles
          </Typography>
          <Typography color="text.secondary">
            Package, runtime, inference, vector, template, and verification catalog for Shopify Companion provisioning.
          </Typography>
        </div>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Button component={Link} to="/shopify-stores" variant="outlined" startIcon={<StorefrontRoundedIcon />}>
            Shopify stores
          </Button>
          <Button component={Link} to="/marketplace" variant="outlined">
            Marketplace
          </Button>
          <Button component={Link} to="/inference-services" variant="outlined">
            Inference
          </Button>
          <Button component={Link} to="/verification-ops" variant="outlined" startIcon={<FactCheckRoundedIcon />}>
            Verification
          </Button>
          <Button variant="contained" startIcon={<TuneRoundedIcon />} onClick={startNewProfile}>
            New profile
          </Button>
        </Stack>
      </Stack>

      {message ? <Alert severity={message.type}>{message.text}</Alert> : null}

      <Grid container spacing={2}>
        {[
          ['Total profiles', profiles.length],
          ['Active', activeProfiles.length],
          ['Draft', profiles.filter((profile) => profile.status === 'DRAFT').length],
          ['Disabled', profiles.filter((profile) => profile.status === 'DISABLED').length],
        ].map(([label, value]) => (
          <Grid item xs={12} sm={6} md={3} key={label}>
            <Card variant="outlined">
              <CardContent>
                <Typography variant="caption" color="text.secondary">
                  {label}
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 700 }}>
                  {value}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Alert severity="info">
        Active catalog edits apply to future provisioning. Existing stores pick up a new package profile through explicit store reconciliation.
      </Alert>

      <Grid container spacing={3}>
        <Grid item xs={12} lg={4}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                  <Typography sx={{ fontWeight: 700 }}>Catalog</Typography>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<RefreshRoundedIcon />}
                    onClick={() => profilesQuery.refetch()}
                    disabled={profilesQuery.isFetching}
                  >
                    Refresh
                  </Button>
                </Stack>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                  <TextField
                    size="small"
                    label="Search"
                    value={searchTerm}
                    onChange={(event) => setSearchTerm(event.target.value)}
                    fullWidth
                  />
                  <TextField
                    size="small"
                    select
                    label="Status"
                    value={statusFilter}
                    onChange={(event) => setStatusFilter(event.target.value)}
                    sx={{ minWidth: 128 }}
                  >
                    <MenuItem value="ALL">All</MenuItem>
                    {statusOptions.map((status) => (
                      <MenuItem key={status} value={status}>
                        {status}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    size="small"
                    select
                    label="Package"
                    value={packageFilter}
                    onChange={(event) => setPackageFilter(event.target.value)}
                    sx={{ minWidth: 140 }}
                  >
                    <MenuItem value="ALL">All</MenuItem>
                    {packageOptions.map((option) => (
                      <MenuItem key={option} value={option}>
                        {option}
                      </MenuItem>
                    ))}
                  </TextField>
                </Stack>
                {profilesQuery.isError ? (
                  <Alert severity="error">
                    {profilesQuery.error instanceof Error ? profilesQuery.error.message : 'Failed to load package profiles.'}
                  </Alert>
                ) : profilesQuery.isLoading ? (
                  <Typography variant="body2" color="text.secondary">
                    Loading package profiles...
                  </Typography>
                ) : filteredProfiles.length === 0 ? (
                  <Alert severity="info">No profiles match the current filters.</Alert>
                ) : (
                  <List disablePadding>
	                    {filteredProfiles.map((profile) => (
	                      <ListItemButton
	                        key={profile.profileKey}
	                        selected={profile.profileKey === selectedProfileKey}
	                        onClick={() => {
	                          setDraftMode(false)
	                          setSelectedProfileKey(profile.profileKey)
	                        }}
	                        sx={{ borderRadius: 2, mb: 0.5 }}
	                      >
                        <ListItemText
                          primary={
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography sx={{ fontWeight: 700 }}>{profile.profileKey}</Typography>
                              <Chip size="small" label={profile.status} color={statusColor(profile.status)} />
                            </Stack>
                          }
                          secondary={`${profile.packageKey}/${profile.tierKey} · ${profile.runtimeProfileKey} · ${profile.vectorProfileKey}`}
                        />
                      </ListItemButton>
                    ))}
                  </List>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={8}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={2.5}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
                  <Stack spacing={0.5}>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                      <Typography variant="h5" sx={{ fontWeight: 700 }}>
                        {form.profileKey || 'Profile draft'}
                      </Typography>
                      <Chip size="small" label={form.status} color={statusColor(form.status)} />
                      {selectedActiveConflict ? <Chip size="small" color="error" label={`Conflicts with ${selectedActiveConflict.profileKey}`} /> : null}
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      Created {formatTimestamp(selectedProfile?.createdAt)} · Updated {formatTimestamp(selectedProfile?.updatedAt)}
                    </Typography>
                  </Stack>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Tooltip title="Duplicate selected profile as draft">
                      <span>
                        <Button
                          variant="outlined"
                          startIcon={<ContentCopyRoundedIcon />}
                          onClick={duplicateSelectedProfile}
                          disabled={!selectedProfile}
                        >
                          Duplicate
                        </Button>
                      </span>
                    </Tooltip>
                    <Button variant="outlined" onClick={resetForm}>
                      Reset
                    </Button>
                    <Button
                      variant="outlined"
                      color="warning"
                      startIcon={<PlayCircleOutlineRoundedIcon />}
                      onClick={() => statusMutation.mutate({ profileKey: form.profileKey, status: 'ACTIVE' })}
                      disabled={!selectedProfile || form.status === 'ACTIVE' || statusMutation.isPending}
                    >
                      Activate
                    </Button>
                    <Button
                      variant="outlined"
                      color="error"
                      startIcon={<BlockRoundedIcon />}
                      onClick={() => statusMutation.mutate({ profileKey: form.profileKey, status: 'DISABLED' })}
                      disabled={!selectedProfile || form.status === 'DISABLED' || statusMutation.isPending}
                    >
                      Disable
                    </Button>
                    <Button
                      variant="contained"
                      startIcon={<SaveRoundedIcon />}
                      onClick={() => upsertMutation.mutate(form)}
                      disabled={!canSave || Boolean(selectedActiveConflict) || upsertMutation.isPending}
                    >
                      Save
                    </Button>
                  </Stack>
                </Stack>

	                <Grid container spacing={2}>
	                  <Grid item xs={12} md={4}>
	                    {selectInput(
	                      'Profile blueprint',
	                      profileBlueprints.find((blueprint) => blueprint.form.profileKey === form.profileKey)?.key ?? '',
	                      [{ value: '', label: 'Manual field selection' }].concat(profileBlueprints.map((blueprint) => ({ value: blueprint.key, label: blueprint.label }))),
	                      applyBlueprint,
	                      draftMode ? 'Selects package, runtime, vector, inference, and verification defaults.' : 'Applies field defaults without changing the saved profile key.',
	                    )}
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    <TextField
	                      select
	                      label="Profile key"
	                      value={form.profileKey}
	                      onChange={(event) => updateForm({ profileKey: event.target.value })}
	                      helperText={draftMode ? 'Choose the new catalog key.' : 'Saved profile keys are immutable from this editor.'}
	                      disabled={!draftMode}
	                      fullWidth
	                    >
	                      {includeCurrentOption(profileKeyOptions, form.profileKey).map((option) => (
	                        <MenuItem key={option.value} value={option.value}>
	                          {option.label}
	                        </MenuItem>
	                      ))}
	                    </TextField>
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    <TextField label="Display name" value={form.displayName} onChange={(event) => updateForm({ displayName: event.target.value })} helperText="Short operator-facing name." fullWidth />
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    {selectInput('Status', form.status, optionsFromValues(statusOptions), (value) => updateForm({ status: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    {selectInput('Package', form.packageKey, optionsFromValues(packageOptions), (value) => updateForm({ packageKey: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    {selectInput('Tier', form.tierKey, optionsFromValues(tierOptions), (value) => updateForm({ tierKey: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    {selectInput('Cost posture', form.costPosture ?? 'STANDARD', optionsFromValues(costPostureOptions), (value) => updateForm({ costPosture: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    {selectInput('Runtime profile', form.runtimeProfileKey, runtimeProfileOptions, (value) => updateForm({ runtimeProfileKey: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    {selectInput('Vector profile', form.vectorProfileKey, vectorProfileOptions, (value) => updateForm({ vectorProfileKey: value }))}
	                  </Grid>
	                  <Grid item xs={12}>
	                    <TextField
	                      label="Description"
                      value={form.description}
                      onChange={(event) => updateForm({ description: event.target.value })}
                      minRows={2}
                      multiline
                      fullWidth
                    />
                  </Grid>
                </Grid>

	                <Divider />

	                <Grid container spacing={2}>
	                  <Grid item xs={12} md={6}>
	                    {selectInput('Template plugin', form.templatePluginId, templatePluginOptions, (value) => updateForm({ templatePluginId: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={3}>
	                    {selectInput('Template version', form.templatePluginVersion ?? '', templateVersionOptions, (value) => updateForm({ templatePluginVersion: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={3}>
	                    {selectInput('Deployment template', form.deploymentTemplateId, deploymentTemplateSelectOptions, (value) => updateForm({ deploymentTemplateId: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={6}>
	                    {selectInput('Inference plugin', form.inferencePluginId, inferencePluginOptions, (value) => updateForm({ inferencePluginId: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={6}>
	                    {selectInput('Verification pack', form.verificationPackId, verificationPackSelectOptions, (value) => updateForm({ verificationPackId: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    {selectInput('Vector strategy', form.vectorStrategy, vectorStrategyOptions, (value) => updateForm({ vectorStrategy: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    {selectInput('Vector provisioning', form.vectorProvisioningMode, optionsFromValues(vectorProvisioningModes), (value) => updateForm({ vectorProvisioningMode: value }))}
	                  </Grid>
	                  <Grid item xs={12} md={4}>
	                    {selectInput('Vector storage', form.vectorStoragePosture, optionsFromValues(vectorStoragePostures), (value) => updateForm({ vectorStoragePosture: value }))}
	                  </Grid>
	                </Grid>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={8}>
                    <TextField
                      label="Details JSON"
                      value={form.detailsJson}
                      onChange={(event) => updateForm({ detailsJson: event.target.value })}
                      error={Boolean(detailsJsonError)}
                      helperText={detailsJsonError ?? ' '}
                      minRows={6}
                      multiline
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <Stack spacing={1.5}>
                      <TextField
                        label="Audit reason"
                        value={form.reason}
                        onChange={(event) => updateForm({ reason: event.target.value })}
                        minRows={3}
                        multiline
                        fullWidth
                      />
                      <Card variant="outlined">
                        <CardContent>
                          <Stack spacing={1}>
                            <Typography sx={{ fontWeight: 700 }}>Resolution preview</Typography>
                            {[
                              ['Package', `${form.packageKey} / ${form.tierKey}`],
                              ['Runtime', form.runtimeProfileKey],
                              ['Vector', `${form.vectorStrategy} · ${form.vectorProfileKey}`],
                              ['Provisioning', `${form.vectorProvisioningMode} · ${form.vectorStoragePosture}`],
                              ['Inference', form.inferencePluginId],
                              ['Template', `${form.templatePluginId} · ${form.deploymentTemplateId}`],
                              ['Verification', form.verificationPackId],
                            ].map(([label, value]) => (
                              <Box key={label}>
                                <Typography variant="caption" color="text.secondary">
                                  {label}
                                </Typography>
                                <Typography variant="body2">{value}</Typography>
                              </Box>
                            ))}
                          </Stack>
                        </CardContent>
                      </Card>
                    </Stack>
                  </Grid>
                </Grid>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  )
}
