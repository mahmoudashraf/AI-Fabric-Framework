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
  fetchShopifyPackageProfileOptions,
  fetchShopifyPackageProfiles,
  type ShopifyCompanionPackageProfileBlueprintSummary,
  type ShopifyCompanionPackageProfileChoiceSummary,
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

type SelectionOption = ShopifyCompanionPackageProfileChoiceSummary

const emptyProfileForm: ProfileFormState = {
  profileKey: '',
  packageKey: '',
  tierKey: '',
  runtimeProfileKey: '',
  vectorProfileKey: '',
  displayName: '',
  description: '',
  costPosture: '',
  templatePluginId: '',
  templatePluginVersion: '',
  deploymentTemplateId: '',
  inferencePluginId: '',
  vectorStrategy: '',
  vectorProvisioningMode: '',
  vectorStoragePosture: '',
  verificationPackId: '',
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

function includeCurrentOption(options: SelectionOption[], currentValue: string | null | undefined): SelectionOption[] {
  const value = currentValue?.trim()
  if (!value || options.some((option) => option.value === value)) {
    return options
  }
  return [...options, simpleOption(value, value, 'Current saved value')]
}

function filterOptions(options: SelectionOption[], allowedValues: string[] | undefined, currentValue: string): SelectionOption[] {
  if (!allowedValues || allowedValues.length === 0) {
    return includeCurrentOption(options, currentValue)
  }
  const allowed = new Set(allowedValues.map((value) => value.toLowerCase()))
  return includeCurrentOption(options.filter((option) => allowed.has(option.value.toLowerCase())), currentValue)
}

function blueprintToFormPatch(blueprint: ShopifyCompanionPackageProfileBlueprintSummary): Partial<ProfileFormState> {
  return {
    profileKey: blueprint.profileKey,
    packageKey: blueprint.packageKey,
    tierKey: blueprint.tierKey,
    runtimeProfileKey: blueprint.runtimeProfileKey,
    vectorProfileKey: blueprint.vectorProfileKey,
    displayName: blueprint.displayName,
    description: blueprint.description,
    costPosture: blueprint.costPosture,
    templatePluginId: blueprint.templatePluginId,
    templatePluginVersion: blueprint.templatePluginVersion ?? '',
    deploymentTemplateId: blueprint.deploymentTemplateId,
    inferencePluginId: blueprint.inferencePluginId,
    vectorStrategy: blueprint.vectorStrategy,
    vectorProvisioningMode: blueprint.vectorProvisioningMode,
    vectorStoragePosture: blueprint.vectorStoragePosture,
    verificationPackId: blueprint.verificationPackId,
  }
}

function optionForBlankTemplateVersion(): SelectionOption {
  return simpleOption('', 'Use latest published version', 'Leave blank to let provisioning use the latest compatible published template plugin version.')
}

function simpleOption(value: string, label: string, description?: string | null): SelectionOption {
  return {
    value,
    label,
    description: description ?? null,
    source: 'UI_SYNTHETIC',
    status: null,
    recommended: false,
    packageKeys: [],
    tierKeys: [],
    runtimeProfileKeys: [],
    vectorProfileKeys: [],
    vectorStrategies: [],
    vectorProvisioningModes: [],
    vectorStoragePostures: [],
  }
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
            {option.description || option.status ? (
              <Typography variant="caption" color="text.secondary">
                {[option.description, option.status].filter(Boolean).join(' · ')}
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

  const optionsQuery = useQuery({
    queryKey: ['shopify-package-profiles', 'options'],
    queryFn: fetchShopifyPackageProfileOptions,
  })

  const profiles = profilesQuery.data ?? []
  const profileOptions = optionsQuery.data
  const profileBlueprints = profileOptions?.profileBlueprints ?? []
  const compatibilityRules = profileOptions?.compatibilityRules ?? []
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
  const selectedCompatibilityRule = compatibilityRules.find(
    (rule) => rule.packageKey === form.packageKey && rule.tierKey === form.tierKey,
  ) ?? null
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
        ? profileBlueprints
          .map((blueprint) => blueprint.profileKey)
          .filter((key): key is string => Boolean(key) && !existingKeys.has(key))
        : profiles.map((profile) => profile.profileKey)
      const optionMap = new Map((profileOptions?.profileKeys ?? []).map((option) => [option.value, option]))
      return selectableKeys
        .map((key) => optionMap.get(key) ?? includeCurrentOption([], key)[0])
        .concat(includeCurrentOption([], form.profileKey).filter((option) => !selectableKeys.includes(option.value)))
    },
    [draftMode, form.profileKey, profileBlueprints, profileOptions?.profileKeys, profiles],
  )
  const runtimeProfileOptions = useMemo(
    () => filterOptions(profileOptions?.runtimeProfiles ?? [], selectedCompatibilityRule?.allowedRuntimeProfileKeys, form.runtimeProfileKey),
    [form.runtimeProfileKey, profileOptions?.runtimeProfiles, selectedCompatibilityRule],
  )
  const vectorProfileOptions = useMemo(
    () => filterOptions(profileOptions?.vectorProfiles ?? [], selectedCompatibilityRule?.allowedVectorProfileKeys, form.vectorProfileKey),
    [form.vectorProfileKey, profileOptions?.vectorProfiles, selectedCompatibilityRule],
  )
  const vectorStrategyOptions = useMemo(
    () => filterOptions(profileOptions?.vectorStrategies ?? [], selectedCompatibilityRule?.allowedVectorStrategies, form.vectorStrategy),
    [form.vectorStrategy, profileOptions?.vectorStrategies, selectedCompatibilityRule],
  )
  const templatePluginOptions = useMemo(() => includeCurrentOption(profileOptions?.templatePlugins ?? [], form.templatePluginId), [form.templatePluginId, profileOptions?.templatePlugins])
  const templateVersionOptions = useMemo(() => {
    return includeCurrentOption([optionForBlankTemplateVersion(), ...(profileOptions?.templateVersions ?? [])], form.templatePluginVersion ?? '')
  }, [form.templatePluginVersion, profileOptions?.templateVersions])
  const deploymentTemplateSelectOptions = useMemo(
    () => filterOptions(profileOptions?.deploymentTemplates ?? [], selectedCompatibilityRule?.allowedDeploymentTemplateIds, form.deploymentTemplateId),
    [form.deploymentTemplateId, profileOptions?.deploymentTemplates, selectedCompatibilityRule],
  )
  const inferencePluginOptions = useMemo(
    () => filterOptions(profileOptions?.inferencePlugins ?? [], selectedCompatibilityRule?.allowedInferencePluginIds, form.inferencePluginId),
    [form.inferencePluginId, profileOptions?.inferencePlugins, selectedCompatibilityRule],
  )
  const verificationPackSelectOptions = useMemo(
    () => filterOptions(profileOptions?.verificationPacks ?? [], selectedCompatibilityRule?.allowedVerificationPackIds, form.verificationPackId),
    [form.verificationPackId, profileOptions?.verificationPacks, selectedCompatibilityRule],
  )
  const vectorProvisioningOptions = useMemo(
    () => filterOptions(profileOptions?.vectorProvisioningModes ?? [], selectedCompatibilityRule?.allowedVectorProvisioningModes, form.vectorProvisioningMode),
    [form.vectorProvisioningMode, profileOptions?.vectorProvisioningModes, selectedCompatibilityRule],
  )
  const vectorStorageOptions = useMemo(
    () => filterOptions(profileOptions?.vectorStoragePostures ?? [], selectedCompatibilityRule?.allowedVectorStoragePostures, form.vectorStoragePosture),
    [form.vectorStoragePosture, profileOptions?.vectorStoragePostures, selectedCompatibilityRule],
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

  const updatePackageTier = (patch: Partial<Pick<ProfileFormState, 'packageKey' | 'tierKey'>>) => {
    setForm((current) => {
      const next = { ...current, ...patch }
      const rule = compatibilityRules.find((candidate) => candidate.packageKey === next.packageKey && candidate.tierKey === next.tierKey)
      if (!rule) {
        return next
      }
      return {
        ...next,
        costPosture: rule.costPosture,
        runtimeProfileKey: rule.defaultRuntimeProfileKey,
        vectorProfileKey: rule.defaultVectorProfileKey,
        inferencePluginId: rule.defaultInferencePluginId,
        vectorStrategy: rule.defaultVectorStrategy,
        vectorProvisioningMode: rule.defaultVectorProvisioningMode,
        vectorStoragePosture: rule.defaultVectorStoragePosture,
        deploymentTemplateId: rule.defaultDeploymentTemplateId,
        verificationPackId: rule.defaultVerificationPackId,
      }
    })
  }

  const applyBlueprint = (blueprintKey: string) => {
    const blueprint = profileBlueprints.find((candidate) => candidate.key === blueprintKey)
    if (!blueprint) {
      return
    }
    const nextProfileKey = draftMode
      ? blueprint.profileKey ?? form.profileKey
      : form.profileKey
    setForm((current) => ({
      ...current,
      ...blueprintToFormPatch(blueprint),
      profileKey: nextProfileKey,
      status: current.status,
      reason: current.reason || `Apply ${blueprint.label}`,
      detailsJson: current.detailsJson,
    }))
  }

  const startNewProfile = () => {
    const usedProfileKeys = new Set(profiles.map((profile) => profile.profileKey))
    const blueprint = profileBlueprints.find((candidate) => !usedProfileKeys.has(candidate.profileKey)) ?? profileBlueprints[0]
    if (!blueprint) {
      setMessage({ type: 'error', text: 'Package profile options are still loading. Refresh and try again.' })
      return
    }
    const generatedKey = `SHOPIFY_PROFILE_${Date.now().toString(36).toUpperCase()}`
    setSelectedProfileKey('')
    setDraftMode(true)
    setForm({
      ...emptyProfileForm,
      ...blueprintToFormPatch(blueprint),
      profileKey: usedProfileKeys.has(blueprint.profileKey) ? generatedKey : blueprint.profileKey,
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
    if (!draftMode && selectedProfile) {
      setForm(profileToForm(selectedProfile))
      return
    }
    const blueprint = profileBlueprints[0]
    setForm(blueprint ? { ...emptyProfileForm, ...blueprintToFormPatch(blueprint), status: 'DRAFT' } : { ...emptyProfileForm, status: 'DRAFT' })
  }

  const canSave = form.profileKey.trim().length > 0
    && form.displayName.trim().length > 0
    && form.templatePluginId.trim().length > 0
    && form.deploymentTemplateId.trim().length > 0
    && form.inferencePluginId.trim().length > 0
    && form.verificationPackId.trim().length > 0
    && Boolean(selectedCompatibilityRule)
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
          <Button variant="contained" startIcon={<TuneRoundedIcon />} onClick={startNewProfile} disabled={optionsQuery.isLoading || profileBlueprints.length === 0}>
            New profile
          </Button>
        </Stack>
      </Stack>

      {message ? <Alert severity={message.type}>{message.text}</Alert> : null}
      {optionsQuery.isError ? (
        <Alert severity="error">
          {optionsQuery.error instanceof Error ? optionsQuery.error.message : 'Failed to load approved package profile options.'}
        </Alert>
      ) : null}

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
                    onClick={() => {
                      profilesQuery.refetch()
                      optionsQuery.refetch()
                    }}
                    disabled={profilesQuery.isFetching || optionsQuery.isFetching}
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
                    {(profileOptions?.statuses ?? []).map((status) => (
                      <MenuItem key={status.value} value={status.value}>
                        {status.label}
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
                    {(profileOptions?.packages ?? []).map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
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

                {!selectedCompatibilityRule && form.packageKey && form.tierKey ? (
                  <Alert severity="warning">
                    Package/tier combination is not in the approved Shopify Companion profile catalog.
                  </Alert>
                ) : null}

                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    {selectInput(
                      'Profile blueprint',
                      profileBlueprints.find((blueprint) => blueprint.profileKey === form.profileKey)?.key ?? '',
                      [simpleOption('', 'Manual field selection')].concat(profileBlueprints.map((blueprint) => simpleOption(blueprint.key, blueprint.label, blueprint.description))),
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
                    {selectInput('Status', form.status, profileOptions?.statuses ?? [], (value) => updateForm({ status: value }))}
                  </Grid>
                  <Grid item xs={12} md={4}>
                    {selectInput('Package', form.packageKey, profileOptions?.packages ?? [], (value) => updatePackageTier({ packageKey: value }))}
                  </Grid>
                  <Grid item xs={12} md={4}>
                    {selectInput('Tier', form.tierKey, profileOptions?.tiers ?? [], (value) => updatePackageTier({ tierKey: value }))}
                  </Grid>
                  <Grid item xs={12} md={4}>
                    {selectInput('Cost posture', form.costPosture ?? '', profileOptions?.costPostures ?? [], (value) => updateForm({ costPosture: value }))}
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
                    {selectInput('Vector provisioning', form.vectorProvisioningMode, vectorProvisioningOptions, (value) => updateForm({ vectorProvisioningMode: value }))}
                  </Grid>
                  <Grid item xs={12} md={4}>
                    {selectInput('Vector storage', form.vectorStoragePosture, vectorStorageOptions, (value) => updateForm({ vectorStoragePosture: value }))}
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
