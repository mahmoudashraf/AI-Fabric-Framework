import AddRoundedIcon from '@mui/icons-material/AddRounded'
import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import StoreRoundedIcon from '@mui/icons-material/StoreRounded'
import {
  Alert,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  FormGroup,
  Grid,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  bootstrapShopifyStore,
  deleteShopifyStore,
  fetchProductServices,
  runProductServiceStoreSourcePreflight,
  fetchShopifyStoreVectorization,
  fetchShopifyStoreBinding,
  fetchShopifyStore,
  fetchShopifyStores,
  goLiveShopifyStore,
  indexAllShopifyStoreVectorization,
  reconcileShopifyStoreVectorization,
  recordShopifyStoreSourcePreflight,
  replayShopifyStoreVectorizationEvent,
  reindexAllShopifyStoreVectorization,
  reindexSelectedShopifyStoreVectorization,
  retryLastFailedShopifyStoreVectorizationAutoRun,
  type RecordShopifyStoreSourcePreflightRequest,
  type ShopifyStoreBootstrapSummary,
  type ShopifyStoreSourcePreflightCategorySummary,
  type ShopifyStoreVectorizationSummary,
  type ShopifyStoreVectorizationSourcePolicyInput,
  type ShopifyStoreVectorizationSourcePolicySummary,
  type ShopifyStoreVectorizationSelectedEntitiesRequest,
  type UpdateShopifyStoreVectorizationPolicyRequest,
  updateShopifyStoreVectorizationPolicy,
  upsertShopifyStore,
  type ShopifyStoreConnectionSummary,
  type UpsertShopifyStoreConnectionRequest,
} from '../api/platformApi'

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function chipColor(value: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  switch ((value ?? '').toUpperCase()) {
    case 'INSTALLED':
    case 'SYNCED':
    case 'READY':
    case 'ENABLED':
    case 'LIVE':
    case 'PREFLIGHT_READY':
      return 'success'
    case 'FAILED':
    case 'BLOCKED':
    case 'DISCONNECTED':
      return 'error'
    case 'NOT_SYNCED':
    case 'NOT_ENABLED':
    case 'NOT_RUN':
    case 'NOT_STARTED':
    case 'INSTALL_IDENTITY_READY':
    case 'PLATFORM_BOOTSTRAPPED':
    case 'GO_LIVE_REQUESTED':
      return 'warning'
    default:
      return 'default'
  }
}

function isReleaseInProgress(status: string | null | undefined): boolean {
  return ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes((status ?? '').toUpperCase())
}

type StoreFormState = UpsertShopifyStoreConnectionRequest
type PreflightCategoryStatus = 'READY' | 'PENDING' | 'BLOCKED' | 'FAILED'
type PreflightFormCategory = ShopifyStoreSourcePreflightCategorySummary & { itemCountText: string }
type VectorizationPolicyDraft = UpdateShopifyStoreVectorizationPolicyRequest

const emptyForm: StoreFormState = {
  shopDomain: '',
  displayName: null,
  productServiceRef: '',
  customerId: null,
  deploymentId: null,
  consumerId: null,
  installStatus: 'INSTALLED',
  syncStatus: 'NOT_SYNCED',
  sourceReadinessStatus: 'NOT_RUN',
  widgetStatus: 'NOT_ENABLED',
  onboardingStatus: 'NOT_STARTED',
  productsEnabled: true,
  collectionsEnabled: true,
  pagesEnabled: true,
  policiesEnabled: true,
  articlesEnabled: true,
}

function buildPreflightCategories(store: ShopifyStoreConnectionSummary): PreflightFormCategory[] {
  const existing = new Map((store.sourcePreflight?.categories ?? []).map((category) => [category.category, category]))
  return [
    { category: 'products', enabled: store.productsEnabled },
    { category: 'collections', enabled: store.collectionsEnabled },
    { category: 'pages', enabled: store.pagesEnabled },
    { category: 'policies', enabled: store.policiesEnabled },
    { category: 'articles', enabled: store.articlesEnabled },
  ]
    .filter((entry) => entry.enabled)
    .map((entry) => {
      const current = existing.get(entry.category)
      return {
        category: entry.category,
        enabled: true,
        status: ((current?.status ?? 'PENDING').toUpperCase() as PreflightCategoryStatus),
        itemCount: current?.itemCount ?? 0,
        itemCountText: `${current?.itemCount ?? 0}`,
        message: current?.message ?? null,
      }
    })
}

function buildVectorizationPolicyDraft(summary: ShopifyStoreVectorizationSummary): VectorizationPolicyDraft {
  return {
    policyVersion: summary.policy.policyVersion,
    sourcePolicies: summary.policy.sourcePolicies.map((policy) => ({
      sourceCategory: policy.sourceCategory,
      autoIndexingEnabled: policy.autoIndexingEnabled,
      createTriggerEnabled: policy.createTriggerEnabled,
      deleteTriggerEnabled: policy.deleteTriggerEnabled,
      updateTriggerMode: policy.updateTriggerMode,
      selectedIndexedFields: [...policy.selectedIndexedFields],
      debounceWindowSeconds: policy.debounceWindowSeconds,
      minimumRunIntervalSeconds: policy.minimumRunIntervalSeconds,
    })),
  }
}

function sourceFieldOptions(
  summary: ShopifyStoreVectorizationSummary | undefined,
  sourceCategory: string,
): string[] {
  return (summary?.effectiveIndexedFields ?? [])
    .filter((field) => field.sourceCategory === sourceCategory && field.selectableForTriggerPolicy)
    .map((field) => field.fieldKey)
}

export function ShopifyStoresPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [preflightDialogOpen, setPreflightDialogOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [bindingDialogOpen, setBindingDialogOpen] = useState(false)
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const [deleteForce, setDeleteForce] = useState(false)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [form, setForm] = useState<StoreFormState>(emptyForm)
  const [preflightCategories, setPreflightCategories] = useState<PreflightFormCategory[]>([])
  const [selectedReindexEntityTypes, setSelectedReindexEntityTypes] = useState<string[]>([])
  const [vectorizationPolicyDraft, setVectorizationPolicyDraft] = useState<VectorizationPolicyDraft | null>(null)

  const servicesQuery = useQuery({
    queryKey: ['product-services'],
    queryFn: fetchProductServices,
  })

  const storesQuery = useQuery({
    queryKey: ['shopify-stores'],
    queryFn: fetchShopifyStores,
  })

  const selectedShopDomain = searchParams.get('shop') ?? ''

  useEffect(() => {
    if (!form.productServiceRef && (servicesQuery.data ?? []).length > 0) {
      setForm((current) => ({ ...current, productServiceRef: servicesQuery.data![0].serviceRef }))
    }
  }, [form.productServiceRef, servicesQuery.data])

  useEffect(() => {
    const stores = storesQuery.data ?? []
    if (stores.length === 0) {
      return
    }
    const selectedStillVisible = stores.some((store) => store.shopDomain === selectedShopDomain)
    if (!selectedStillVisible) {
      setSearchParams({ shop: stores[0].shopDomain }, { replace: true })
    }
  }, [selectedShopDomain, setSearchParams, storesQuery.data])

  const selectedSummary = useMemo(
    () => (storesQuery.data ?? []).find((store) => store.shopDomain === selectedShopDomain) ?? null,
    [selectedShopDomain, storesQuery.data],
  )

  const selectedStoreQuery = useQuery({
    queryKey: ['shopify-stores', selectedShopDomain],
    queryFn: () => fetchShopifyStore(selectedShopDomain),
    enabled: selectedShopDomain.length > 0,
    refetchInterval: (query) => {
      const store = (query.state.data as ShopifyStoreConnectionSummary | undefined) ?? selectedSummary
      return isReleaseInProgress(store?.latestRelease?.status) ? 5000 : false
    },
  })

  const selectedStore = selectedStoreQuery.data ?? selectedSummary

  const selectedVectorizationQuery = useQuery({
    queryKey: ['shopify-stores', selectedShopDomain, 'vectorization'],
    queryFn: () => fetchShopifyStoreVectorization(selectedShopDomain),
    enabled: selectedShopDomain.length > 0,
  })

  const selectedVectorization = selectedVectorizationQuery.data

  const bindingQuery = useQuery({
    queryKey: ['shopify-stores', selectedShopDomain, 'binding'],
    queryFn: () => fetchShopifyStoreBinding(selectedShopDomain),
    enabled: bindingDialogOpen && selectedShopDomain.length > 0,
  })

  useEffect(() => {
    if (selectedStore && preflightDialogOpen) {
      setPreflightCategories(buildPreflightCategories(selectedStore))
    }
  }, [preflightDialogOpen, selectedStore])

  useEffect(() => {
    if (selectedVectorization) {
      setSelectedReindexEntityTypes(selectedVectorization.selectedEntityTypes)
      setVectorizationPolicyDraft(buildVectorizationPolicyDraft(selectedVectorization))
    } else {
      setSelectedReindexEntityTypes([])
      setVectorizationPolicyDraft(null)
    }
  }, [selectedVectorization])

  const upsertMutation = useMutation({
    mutationFn: upsertShopifyStore,
    onSuccess: async (store) => {
      setMessage({ type: 'success', text: `Saved ${store.shopDomain}.` })
      setDialogOpen(false)
      setForm((current) => ({ ...emptyForm, productServiceRef: current.productServiceRef }))
      setSearchParams({ shop: store.shopDomain }, { replace: true })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['shopify-stores'] }),
        queryClient.invalidateQueries({ queryKey: ['shopify-stores', store.shopDomain] }),
        queryClient.invalidateQueries({ queryKey: ['product-services'] }),
      ])
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to save Shopify store mapping.' })
    },
  })

  const bootstrapMutation = useMutation({
    mutationFn: (shopDomain: string) => bootstrapShopifyStore(shopDomain, {}),
    onSuccess: async (result: ShopifyStoreBootstrapSummary) => {
      const created: string[] = []
      if (result.createdCustomer) created.push('customer')
      if (result.createdDeployment) created.push('deployment')
      if (result.createdConsumer) created.push('consumer')
      const createdSummary = created.length > 0 ? ` Created ${created.join(', ')}.` : ' Reused existing platform objects.'
      setMessage({
        type: 'success',
        text: `Bootstrapped ${result.shopDomain}.${createdSummary} Installed/ensured plugins: ${result.installedPluginIds.join(', ') || 'none'}.`,
      })
      setSearchParams({ shop: result.shopDomain }, { replace: true })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['shopify-stores'] }),
        queryClient.invalidateQueries({ queryKey: ['shopify-stores', result.shopDomain] }),
        queryClient.invalidateQueries({ queryKey: ['product-services'] }),
      ])
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to bootstrap Shopify store.' })
    },
  })

  const preflightMutation = useMutation({
    mutationFn: ({ shopDomain, payload }: { shopDomain: string; payload: RecordShopifyStoreSourcePreflightRequest }) =>
      recordShopifyStoreSourcePreflight(shopDomain, payload),
    onSuccess: async (store) => {
      setMessage({ type: 'success', text: `Recorded source preflight for ${store.shopDomain}.` })
      setPreflightDialogOpen(false)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['shopify-stores'] }),
        queryClient.invalidateQueries({ queryKey: ['shopify-stores', store.shopDomain] }),
        queryClient.invalidateQueries({ queryKey: ['product-services'] }),
      ])
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to record Shopify source preflight.' })
    },
  })

  const runLivePreflightMutation = useMutation({
    mutationFn: ({ serviceRef, shopDomain }: { serviceRef: string; shopDomain: string }) =>
      runProductServiceStoreSourcePreflight(serviceRef, shopDomain),
    onSuccess: async (store) => {
      setMessage({ type: 'success', text: `Ran live source preflight for ${store.shopDomain}.` })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['shopify-stores'] }),
        queryClient.invalidateQueries({ queryKey: ['shopify-stores', store.shopDomain] }),
        queryClient.invalidateQueries({ queryKey: ['product-services'] }),
      ])
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to run live Shopify source preflight.' })
    },
  })

  const goLiveMutation = useMutation({
    mutationFn: (shopDomain: string) => goLiveShopifyStore(shopDomain),
    onSuccess: async (store) => {
      setMessage({
        type: 'success',
        text: `Requested publish/apply for ${store.shopDomain}. Latest release ${store.latestRelease?.id ?? '—'} is now ${store.latestRelease?.status ?? 'queued'}.`,
      })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['shopify-stores'] }),
        queryClient.invalidateQueries({ queryKey: ['shopify-stores', store.shopDomain] }),
        queryClient.invalidateQueries({ queryKey: ['product-services'] }),
      ])
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to request Shopify Companion go-live.' })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: ({ shopDomain, force }: { shopDomain: string; force: boolean }) => deleteShopifyStore(shopDomain, force),
    onSuccess: async (_, variables) => {
      setMessage({ type: 'success', text: `Removed Shopify store mapping ${variables.shopDomain}.` })
      setDeleteDialogOpen(false)
      setDeleteConfirmation('')
      setDeleteForce(false)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['shopify-stores'] }),
        queryClient.invalidateQueries({ queryKey: ['product-services'] }),
      ])
      setSearchParams({}, { replace: true })
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to delete Shopify store mapping.' })
    },
  })

  const refreshVectorizationState = async (shopDomain: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['shopify-stores'] }),
      queryClient.invalidateQueries({ queryKey: ['shopify-stores', shopDomain] }),
      queryClient.invalidateQueries({ queryKey: ['shopify-stores', shopDomain, 'vectorization'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services'] }),
    ])
  }

  const reconcileVectorizationMutation = useMutation({
    mutationFn: (shopDomain: string) => reconcileShopifyStoreVectorization(shopDomain),
    onSuccess: async (summary) => {
      setMessage({ type: 'success', text: `Reconciled indexing support for ${summary.shopDomain}.` })
      await refreshVectorizationState(summary.shopDomain)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to reconcile Shopify indexing support.' })
    },
  })

  const indexAllVectorizationMutation = useMutation({
    mutationFn: (shopDomain: string) => indexAllShopifyStoreVectorization(shopDomain),
    onSuccess: async (summary) => {
      setMessage({ type: 'success', text: `Queued indexing for all enabled data on ${summary.shopDomain}.` })
      await refreshVectorizationState(summary.shopDomain)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to start Shopify indexing.' })
    },
  })

  const reindexAllVectorizationMutation = useMutation({
    mutationFn: (shopDomain: string) => reindexAllShopifyStoreVectorization(shopDomain),
    onSuccess: async (summary) => {
      setMessage({ type: 'success', text: `Queued full reindex for ${summary.shopDomain}.` })
      await refreshVectorizationState(summary.shopDomain)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to start Shopify reindex.' })
    },
  })

  const reindexSelectedVectorizationMutation = useMutation({
    mutationFn: ({ shopDomain, payload }: { shopDomain: string; payload: ShopifyStoreVectorizationSelectedEntitiesRequest }) =>
      reindexSelectedShopifyStoreVectorization(shopDomain, payload),
    onSuccess: async (summary) => {
      setMessage({ type: 'success', text: `Queued selected reindex for ${summary.shopDomain}.` })
      await refreshVectorizationState(summary.shopDomain)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to start selected Shopify reindex.' })
    },
  })

  const saveVectorizationPolicyMutation = useMutation({
    mutationFn: ({ shopDomain, payload }: { shopDomain: string; payload: UpdateShopifyStoreVectorizationPolicyRequest }) =>
      updateShopifyStoreVectorizationPolicy(shopDomain, payload),
    onSuccess: async (summary) => {
      setMessage({ type: 'success', text: `Saved live update policy for ${summary.shopDomain}.` })
      await refreshVectorizationState(summary.shopDomain)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to save Shopify live update policy.' })
    },
  })

  const replayVectorizationEventMutation = useMutation({
    mutationFn: ({ shopDomain, eventId }: { shopDomain: string; eventId: string }) =>
      replayShopifyStoreVectorizationEvent(shopDomain, eventId),
    onSuccess: async (summary) => {
      setMessage({ type: 'success', text: `Replayed live update event for ${summary.shopDomain}.` })
      await refreshVectorizationState(summary.shopDomain)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to replay Shopify live update event.' })
    },
  })

  const retryFailedAutoRunMutation = useMutation({
    mutationFn: (shopDomain: string) => retryLastFailedShopifyStoreVectorizationAutoRun(shopDomain),
    onSuccess: async (summary) => {
      setMessage({ type: 'success', text: `Requeued failed live auto indexing work for ${summary.shopDomain}.` })
      await refreshVectorizationState(summary.shopDomain)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to requeue the last failed Shopify live auto indexing run.' })
    },
  })

  const submitPreflight = () => {
    if (!selectedStore) {
      return
    }
    const payload: RecordShopifyStoreSourcePreflightRequest = {
      categories: preflightCategories.map((category) => ({
        category: category.category,
        enabled: category.enabled,
        status: category.status,
        itemCount: Math.max(Number.parseInt(category.itemCountText || '0', 10) || 0, 0),
        message: category.message?.trim() ? category.message.trim() : null,
      })),
    }
    preflightMutation.mutate({ shopDomain: selectedStore.shopDomain, payload })
  }

  const updatePolicyDraftSource = (
    sourceCategory: string,
    mutator: (current: ShopifyStoreVectorizationSourcePolicyInput) => ShopifyStoreVectorizationSourcePolicyInput,
  ) => {
    setVectorizationPolicyDraft((current) => {
      if (!current) {
        return current
      }
      return {
        ...current,
        sourcePolicies: current.sourcePolicies.map((policy) =>
          policy.sourceCategory === sourceCategory ? mutator(policy) : policy,
        ),
      }
    })
  }

  const vectorizationBusy =
    reconcileVectorizationMutation.isPending ||
    indexAllVectorizationMutation.isPending ||
    reindexAllVectorizationMutation.isPending ||
    reindexSelectedVectorizationMutation.isPending ||
    saveVectorizationPolicyMutation.isPending ||
    replayVectorizationEventMutation.isPending ||
    retryFailedAutoRunMutation.isPending

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
        <div>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Shopify Stores
          </Typography>
          <Typography color="text.secondary">
            Platform drill-through for shop, customer, deployment, consumer, and sync-state mappings.
          </Typography>
        </div>
        <Stack direction="row" spacing={1}>
          {selectedStore ? (
            <>
              <Button
                variant="outlined"
                startIcon={<FactCheckRoundedIcon />}
                onClick={() =>
                  selectedStore.productServiceRef
                    ? runLivePreflightMutation.mutate({
                        serviceRef: selectedStore.productServiceRef,
                        shopDomain: selectedStore.shopDomain,
                      })
                    : undefined
                }
                disabled={runLivePreflightMutation.isPending || !selectedStore.productServiceRef}
              >
                Run live source preflight
              </Button>
              <Button
                variant="outlined"
                startIcon={<FactCheckRoundedIcon />}
                onClick={() => {
                  setPreflightCategories(buildPreflightCategories(selectedStore))
                  setPreflightDialogOpen(true)
                }}
                disabled={preflightMutation.isPending}
              >
                Record preflight override
              </Button>
              <Button
                variant="outlined"
                startIcon={<AutoFixHighRoundedIcon />}
                onClick={() => bootstrapMutation.mutate(selectedStore.shopDomain)}
                disabled={bootstrapMutation.isPending}
              >
                Bootstrap platform
              </Button>
              <Button
                variant="outlined"
                onClick={() => goLiveMutation.mutate(selectedStore.shopDomain)}
                disabled={goLiveMutation.isPending || !selectedStore.readiness?.goLiveEligible || isReleaseInProgress(selectedStore.latestRelease?.status)}
              >
                Publish and apply
              </Button>
              <Button variant="outlined" onClick={() => setBindingDialogOpen(true)} disabled={!selectedStore}>
                Inspect platform bindings
              </Button>
              <Button
                variant="outlined"
                color="error"
                onClick={() => setDeleteDialogOpen(true)}
                disabled={deleteMutation.isPending}
              >
                Delete mapping
              </Button>
            </>
          ) : null}
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setDialogOpen(true)}>
            Register store mapping
          </Button>
        </Stack>
      </Stack>

      {message ? <Alert severity={message.type}>{message.text}</Alert> : null}

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Typography sx={{ fontWeight: 700 }}>Bound stores</Typography>
                {(storesQuery.data ?? []).length === 0 ? (
                  <Alert severity="info">No Shopify store mappings exist yet.</Alert>
                ) : (
                  <List disablePadding>
                    {(storesQuery.data ?? []).map((store) => (
                      <ListItemButton
                        key={store.id}
                        selected={store.shopDomain === selectedShopDomain}
                        onClick={() => setSearchParams({ shop: store.shopDomain }, { replace: true })}
                        sx={{ borderRadius: 2, mb: 0.5 }}
                      >
                        <ListItemText
                          primary={store.shopDomain}
                          secondary={`${store.productServiceDisplayName} · ${store.customerName ?? 'no customer bound'}`}
                        />
                        <Chip size="small" label={store.syncStatus} color={chipColor(store.syncStatus)} />
                      </ListItemButton>
                    ))}
                  </List>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          {!selectedStore ? (
            <Alert severity="info">Select a Shopify store to inspect its mapping and sync state.</Alert>
          ) : (
            <Card variant="outlined">
              <CardContent>
                <Stack spacing={2}>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                    <StoreRoundedIcon color="primary" />
                    <Typography variant="h5" sx={{ fontWeight: 700 }}>
                      {selectedStore.shopDomain}
                    </Typography>
                    {selectedStore.readiness ? (
                      <Chip size="small" label={selectedStore.readiness.overallStatus} color={chipColor(selectedStore.readiness.overallStatus)} />
                    ) : null}
                    <Chip size="small" label={selectedStore.onboardingStatus} color={chipColor(selectedStore.onboardingStatus)} />
                    <Chip size="small" label={selectedStore.installStatus} color={chipColor(selectedStore.installStatus)} />
                    <Chip size="small" label={selectedStore.syncStatus} color={chipColor(selectedStore.syncStatus)} />
                    <Chip size="small" label={selectedStore.sourceReadinessStatus} color={chipColor(selectedStore.sourceReadinessStatus)} />
                    <Chip size="small" label={selectedStore.widgetStatus} color={chipColor(selectedStore.widgetStatus)} />
                  </Stack>

                  <Grid container spacing={2}>
                    {[
                      ['Display name', selectedStore.displayName],
                      ['Product service', selectedStore.productServiceDisplayName],
                      ['Customer', selectedStore.customerName ?? selectedStore.customerId],
                      ['Deployment', selectedStore.deploymentName ?? selectedStore.deploymentId],
                      ['Deployment status', selectedStore.deploymentStatus],
                      ['Latest version', selectedStore.latestVersion ? `${selectedStore.latestVersion.versionLabel} (${selectedStore.latestVersion.status})` : null],
                      [
                        'Latest release',
                        selectedStore.latestRelease
                          ? `${selectedStore.latestRelease.status} / ${selectedStore.latestRelease.verificationStatus}`
                          : null,
                      ],
                      ['Go-live eligible', selectedStore.readiness ? (selectedStore.readiness.goLiveEligible ? 'Yes' : 'No') : null],
                      ['Storefront ready', selectedStore.readiness ? (selectedStore.readiness.storefrontReady ? 'Yes' : 'No') : null],
                      ['Consumer', selectedStore.consumerDisplayName ?? selectedStore.consumerId],
                      ['Onboarding', selectedStore.onboardingStatus],
                      ['Credentials', selectedStore.credentials?.status],
                      ['Products enabled', selectedStore.productsEnabled ? 'Yes' : 'No'],
                      ['Collections enabled', selectedStore.collectionsEnabled ? 'Yes' : 'No'],
                      ['Pages enabled', selectedStore.pagesEnabled ? 'Yes' : 'No'],
                      ['Policies enabled', selectedStore.policiesEnabled ? 'Yes' : 'No'],
                      ['Articles enabled', selectedStore.articlesEnabled ? 'Yes' : 'No'],
                      ['Actions', selectedStore.capabilities ? `${selectedStore.capabilities.actionCount}` : null],
                      ['Knowledge sources', selectedStore.capabilities ? `${selectedStore.capabilities.knowledgeSourceCount}` : null],
                      ['Datasets', selectedStore.capabilities ? `${selectedStore.capabilities.marketplaceDatasetCount}` : null],
                      ['Shell modules', selectedStore.capabilities ? `${selectedStore.capabilities.shellModuleCount}` : null],
                      ['Last preflight', formatTimestamp(selectedStore.lastSourcePreflightAt)],
                      ['Last sync', formatTimestamp(selectedStore.lastSyncAt)],
                      ['Last webhook', formatTimestamp(selectedStore.lastWebhookAt)],
                    ].map(([label, value]) => (
                      <Grid item xs={12} md={4} key={label}>
                        <Typography variant="caption" color="text.secondary">
                          {label}
                        </Typography>
                        <Typography variant="body2">{value ?? '—'}</Typography>
                      </Grid>
                    ))}
                  </Grid>

                  {selectedStore.readiness ? (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>Readiness</Typography>
                            <Chip size="small" label={selectedStore.readiness.overallStatus} color={chipColor(selectedStore.readiness.overallStatus)} />
                            <Chip
                              size="small"
                              variant="outlined"
                              label={`Go-live ${selectedStore.readiness.goLiveEligible ? 'ready' : 'blocked'}`}
                              color={selectedStore.readiness.goLiveEligible ? 'success' : 'warning'}
                            />
                            <Chip
                              size="small"
                              variant="outlined"
                              label={`Storefront ${selectedStore.readiness.storefrontReady ? 'ready' : 'not ready'}`}
                              color={selectedStore.readiness.storefrontReady ? 'success' : 'warning'}
                            />
                          </Stack>
                          {selectedStore.readiness.goLiveBlockingReasons.length > 0 ? (
                            <Alert severity="warning">
                              <strong>Go-live blockers</strong>
                              <List dense>
                                {selectedStore.readiness.goLiveBlockingReasons.map((reason) => (
                                  <li key={reason}>{reason}</li>
                                ))}
                              </List>
                            </Alert>
                          ) : null}
                          {selectedStore.readiness.storefrontBlockingReasons.length > 0 ? (
                            <Alert severity="error">
                              <strong>Storefront blockers</strong>
                              <List dense>
                                {selectedStore.readiness.storefrontBlockingReasons.map((reason) => (
                                  <li key={reason}>{reason}</li>
                                ))}
                              </List>
                            </Alert>
                          ) : null}
                          {selectedStore.readiness.nextActions.length > 0 ? (
                            <Alert severity="info">
                              <strong>Next actions</strong>
                              <List dense>
                                {selectedStore.readiness.nextActions.map((action) => (
                                  <li key={action}>{action}</li>
                                ))}
                              </List>
                            </Alert>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : null}

                  {selectedStore.sourcePreflight ? (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>Source preflight</Typography>
                            <Chip size="small" label={selectedStore.sourcePreflight.overallStatus} color={chipColor(selectedStore.sourcePreflight.overallStatus)} />
                            <Typography variant="caption" color="text.secondary">
                              Checked {formatTimestamp(selectedStore.sourcePreflight.checkedAt)}
                            </Typography>
                          </Stack>
                          <Grid container spacing={1.5}>
                            {selectedStore.sourcePreflight.categories.map((category) => (
                              <Grid item xs={12} md={6} key={category.category}>
                                <Card variant="outlined">
                                  <CardContent>
                                    <Stack spacing={1}>
                                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                        <Typography sx={{ fontWeight: 700, textTransform: 'capitalize' }}>{category.category}</Typography>
                                        <Chip size="small" label={category.status} color={chipColor(category.status)} />
                                        <Chip size="small" variant="outlined" label={`${category.itemCount} items`} />
                                      </Stack>
                                      {category.message ? (
                                        <Typography variant="body2" color="text.secondary">
                                          {category.message}
                                        </Typography>
                                      ) : null}
                                    </Stack>
                                  </CardContent>
                                </Card>
                              </Grid>
                            ))}
                          </Grid>
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : null}

                  {selectedStore.credentials ? (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>Stored credentials</Typography>
                            <Chip size="small" label={selectedStore.credentials.status} color={chipColor(selectedStore.credentials.status)} />
                            <Typography variant="caption" color="text.secondary">
                              Checked {formatTimestamp(selectedStore.credentials.checkedAt)}
                            </Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            Access token {selectedStore.credentials.accessTokenPresent ? 'ready' : 'missing'} · Refresh token{' '}
                            {selectedStore.credentials.refreshTokenPresent ? 'ready' : 'missing'} · Expiring{' '}
                            {selectedStore.credentials.expiring ? 'yes' : 'no'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Scope {selectedStore.credentials.scopesText ?? '—'} · Access secret {selectedStore.credentials.accessTokenSecretRef ?? '—'} · Refresh secret{' '}
                            {selectedStore.credentials.refreshTokenSecretRef ?? '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Access expiry {formatTimestamp(selectedStore.credentials.accessTokenExpiresAt)} · Refresh expiry{' '}
                            {formatTimestamp(selectedStore.credentials.refreshTokenExpiresAt)}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Typography sx={{ fontWeight: 700 }}>Stored credentials</Typography>
                          <Typography variant="body2" color="text.secondary">
                            No Shopify credentials have been persisted for this store yet.
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  )}

                  {selectedStore.syncDetail ? (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>Latest sync</Typography>
                            <Chip size="small" label={selectedStore.syncDetail.status} color={chipColor(selectedStore.syncDetail.status)} />
                            <Typography variant="caption" color="text.secondary">
                              Checked {formatTimestamp(selectedStore.syncDetail.checkedAt)}
                            </Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            Mode {selectedStore.syncDetail.mode ?? '—'} · Documents {selectedStore.syncDetail.documentCount}
                          </Typography>
                          {selectedStore.syncDetail.message ? (
                            <Typography variant="body2" color="text.secondary">
                              {selectedStore.syncDetail.message}
                            </Typography>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : null}

                  {selectedStore.capabilities ? (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>Deployment capabilities</Typography>
                            <Chip size="small" label={`${selectedStore.capabilities.actionCount} actions`} />
                            <Chip size="small" label={`${selectedStore.capabilities.knowledgeSourceCount} knowledge sources`} />
                            <Chip size="small" label={`${selectedStore.capabilities.marketplaceDatasetCount} datasets`} />
                            <Chip size="small" label={`${selectedStore.capabilities.shellModuleCount} shell modules`} />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            Action names {selectedStore.capabilities.actionNames.join(' · ') || '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Knowledge sources {selectedStore.capabilities.knowledgeSourceIds.join(' · ') || '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Shell modules {selectedStore.capabilities.shellModuleIds.join(' · ') || '—'} · Marketplace datasets{' '}
                            {selectedStore.capabilities.marketplaceDatasetIds.join(' · ') || '—'}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : null}

                  {selectedVectorizationQuery.isError ? (
                    <Alert severity="error">
                      {selectedVectorizationQuery.error instanceof Error
                        ? selectedVectorizationQuery.error.message
                        : 'Failed to load Shopify indexing summary.'}
                    </Alert>
                  ) : null}

                  {selectedVectorization ? (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={2}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>Indexing and live updates</Typography>
                            <Chip
                              size="small"
                              label={selectedVectorization.readyToRun ? 'Ready' : 'Blocked'}
                              color={selectedVectorization.readyToRun ? 'success' : 'warning'}
                            />
                            <Chip size="small" label={selectedVectorization.syncState ?? 'NO_PLAN'} color={chipColor(selectedVectorization.syncState)} />
                            <Chip
                              size="small"
                              variant="outlined"
                              label={`Runner ${selectedVectorization.runnerRegistrationStatus ?? 'NOT_READY'}`}
                              color={chipColor(selectedVectorization.runnerRegistrationStatus)}
                            />
                            <Chip
                              size="small"
                              variant="outlined"
                              label={`Auto ${selectedVectorization.automation.autoIndexingHealthy ? 'healthy' : 'degraded'}`}
                              color={selectedVectorization.automation.autoIndexingHealthy ? 'success' : 'warning'}
                            />
                          </Stack>

                          <Typography variant="body2" color="text.secondary">
                            Entity families {selectedVectorization.selectedEntityTypes.join(' · ') || '—'} · Source categories{' '}
                            {selectedVectorization.selectedCategories.join(' · ') || '—'} · Plan {selectedVectorization.planId ?? '—'} · Connection{' '}
                            {selectedVectorization.sourceConnectionId ?? '—'}
                          </Typography>

                          {selectedVectorization.blockingReasons.length > 0 ? (
                            <Alert severity="warning">
                              <strong>Indexing blockers</strong>
                              <List dense>
                                {selectedVectorization.blockingReasons.map((reason) => (
                                  <li key={reason}>{reason}</li>
                                ))}
                              </List>
                            </Alert>
                          ) : null}

                          {selectedVectorization.automation.degradedReasons.length > 0 ? (
                            <Alert severity="warning">
                              <strong>Live updates need attention</strong>
                              <List dense>
                                {selectedVectorization.automation.degradedReasons.map((reason) => (
                                  <li key={reason}>{reason}</li>
                                ))}
                              </List>
                            </Alert>
                          ) : null}

                          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} flexWrap="wrap" useFlexGap>
                            <Button
                              variant="outlined"
                              onClick={() => reconcileVectorizationMutation.mutate(selectedStore.shopDomain)}
                              disabled={vectorizationBusy}
                            >
                              Reconcile indexing support
                            </Button>
                            <Button
                              variant="contained"
                              onClick={() => indexAllVectorizationMutation.mutate(selectedStore.shopDomain)}
                              disabled={vectorizationBusy || !selectedVectorization.readyToRun}
                            >
                              Index all enabled data
                            </Button>
                            <Button
                              variant="outlined"
                              onClick={() => reindexAllVectorizationMutation.mutate(selectedStore.shopDomain)}
                              disabled={vectorizationBusy || !selectedVectorization.readyToRun}
                            >
                              Reindex all enabled data
                            </Button>
                            <Button
                              variant="outlined"
                              onClick={() =>
                                reindexSelectedVectorizationMutation.mutate({
                                  shopDomain: selectedStore.shopDomain,
                                  payload: { entityTypes: selectedReindexEntityTypes },
                                })
                              }
                              disabled={
                                vectorizationBusy ||
                                !selectedVectorization.readyToRun ||
                                selectedReindexEntityTypes.length === 0
                              }
                            >
                              Reindex selected families
                            </Button>
                            <Button
                              variant="outlined"
                              onClick={() => retryFailedAutoRunMutation.mutate(selectedStore.shopDomain)}
                              disabled={
                                vectorizationBusy ||
                                !selectedVectorization.automation.lastAutoRunId ||
                                !selectedVectorization.automation.lastFailedAutoIndexAt
                              }
                            >
                              Retry last failed auto run
                            </Button>
                          </Stack>

                          <FormGroup row>
                            {selectedVectorization.selectedEntityTypes.map((entityType) => (
                              <FormControlLabel
                                key={entityType}
                                control={
                                  <Checkbox
                                    checked={selectedReindexEntityTypes.includes(entityType)}
                                    onChange={(event) =>
                                      setSelectedReindexEntityTypes((current) =>
                                        event.target.checked
                                          ? [...current, entityType]
                                          : current.filter((value) => value !== entityType),
                                      )
                                    }
                                  />
                                }
                                label={`Reindex ${entityType}`}
                              />
                            ))}
                          </FormGroup>

                          <Grid container spacing={2}>
                            <Grid item xs={12} md={4}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Stack spacing={1}>
                                    <Typography sx={{ fontWeight: 700 }}>Automation counters</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                      Queued {selectedVectorization.automation.queuedEvents} · Leased {selectedVectorization.automation.leasedEvents} ·
                                      Dispatched {selectedVectorization.automation.dispatchedEvents}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                      Failed {selectedVectorization.automation.failedEvents} · Dead-lettered{' '}
                                      {selectedVectorization.automation.deadLetteredEvents}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                      Last success {formatTimestamp(selectedVectorization.automation.lastSuccessfulAutoIndexAt)}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                      Last failure {formatTimestamp(selectedVectorization.automation.lastFailedAutoIndexAt)}
                                    </Typography>
                                  </Stack>
                                </CardContent>
                              </Card>
                            </Grid>

                            <Grid item xs={12} md={8}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Stack spacing={1.5}>
                                    <Typography sx={{ fontWeight: 700 }}>Effective indexed fields</Typography>
                                    {selectedVectorization.effectiveIndexedFields.length === 0 ? (
                                      <Typography variant="body2" color="text.secondary">
                                        No effective indexed fields are available until the deployment vectorization plan is active.
                                      </Typography>
                                    ) : (
                                      <Grid container spacing={1}>
                                        {selectedVectorization.effectiveIndexedFields.map((field) => (
                                          <Grid item xs={12} md={6} key={field.fieldKey}>
                                            <Typography variant="body2" color="text.secondary">
                                              {field.sourceCategory} · {field.label} ({field.fieldKey})
                                            </Typography>
                                          </Grid>
                                        ))}
                                      </Grid>
                                    )}
                                  </Stack>
                                </CardContent>
                              </Card>
                            </Grid>
                          </Grid>

                          {vectorizationPolicyDraft ? (
                            <Card variant="outlined">
                              <CardContent>
                                <Stack spacing={2}>
                                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                    <Typography sx={{ fontWeight: 700 }}>Live update policy</Typography>
                                    <Chip size="small" label={`Version ${selectedVectorization.policy.policyVersion}`} />
                                    <Typography variant="caption" color="text.secondary">
                                      Updated by {selectedVectorization.policy.updatedBy ?? 'system'} · {formatTimestamp(selectedVectorization.policy.updatedAt)}
                                    </Typography>
                                  </Stack>

                                  {selectedVectorization.policy.sourcePolicies.map((policy) => {
                                    const draft =
                                      vectorizationPolicyDraft.sourcePolicies.find((current) => current.sourceCategory === policy.sourceCategory) ?? policy
                                    const selectableFields = sourceFieldOptions(selectedVectorization, policy.sourceCategory)
                                    return (
                                      <Card key={policy.sourceCategory} variant="outlined">
                                        <CardContent>
                                          <Stack spacing={1.5}>
                                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                              <Typography sx={{ fontWeight: 700, textTransform: 'capitalize' }}>
                                                {policy.sourceCategory}
                                              </Typography>
                                              <Chip
                                                size="small"
                                                label={policy.enabled ? 'Enabled' : 'Disabled'}
                                                color={policy.enabled ? 'success' : 'default'}
                                              />
                                              <Chip
                                                size="small"
                                                variant="outlined"
                                                label={draft.autoIndexingEnabled ? 'Auto on' : 'Auto off'}
                                                color={draft.autoIndexingEnabled ? 'success' : 'warning'}
                                              />
                                            </Stack>

                                            <FormGroup row>
                                              <FormControlLabel
                                                control={
                                                  <Checkbox
                                                    checked={Boolean(draft.autoIndexingEnabled)}
                                                    onChange={(event) =>
                                                      updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                                        ...current,
                                                        autoIndexingEnabled: event.target.checked,
                                                      }))
                                                    }
                                                  />
                                                }
                                                label="Live auto indexing"
                                              />
                                              <FormControlLabel
                                                control={
                                                  <Checkbox
                                                    checked={Boolean(draft.createTriggerEnabled)}
                                                    onChange={(event) =>
                                                      updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                                        ...current,
                                                        createTriggerEnabled: event.target.checked,
                                                      }))
                                                    }
                                                  />
                                                }
                                                label="Create trigger"
                                              />
                                              <FormControlLabel
                                                control={
                                                  <Checkbox
                                                    checked={Boolean(draft.deleteTriggerEnabled)}
                                                    onChange={(event) =>
                                                      updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                                        ...current,
                                                        deleteTriggerEnabled: event.target.checked,
                                                      }))
                                                    }
                                                  />
                                                }
                                                label="Delete trigger"
                                              />
                                            </FormGroup>

                                            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                                              <TextField
                                                select
                                                label="Update sensitivity"
                                                value={draft.updateTriggerMode ?? 'INDEXED_FIELDS_ONLY'}
                                                onChange={(event) =>
                                                  updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                                    ...current,
                                                    updateTriggerMode: event.target.value,
                                                  }))
                                                }
                                                fullWidth
                                              >
                                                <MenuItem value="NONE">NONE</MenuItem>
                                                <MenuItem value="ANY_UPDATE">ANY_UPDATE</MenuItem>
                                                <MenuItem value="INDEXED_FIELDS_ONLY">INDEXED_FIELDS_ONLY</MenuItem>
                                                <MenuItem value="SELECTED_INDEXED_FIELDS">SELECTED_INDEXED_FIELDS</MenuItem>
                                              </TextField>
                                              <TextField
                                                label="Debounce seconds"
                                                type="number"
                                                value={draft.debounceWindowSeconds ?? 0}
                                                onChange={(event) =>
                                                  updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                                    ...current,
                                                    debounceWindowSeconds: Number.parseInt(event.target.value, 10) || 0,
                                                  }))
                                                }
                                                fullWidth
                                              />
                                              <TextField
                                                label="Minimum run interval seconds"
                                                type="number"
                                                value={draft.minimumRunIntervalSeconds ?? 0}
                                                onChange={(event) =>
                                                  updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                                    ...current,
                                                    minimumRunIntervalSeconds: Number.parseInt(event.target.value, 10) || 0,
                                                  }))
                                                }
                                                fullWidth
                                              />
                                            </Stack>

                                            {draft.updateTriggerMode === 'SELECTED_INDEXED_FIELDS' ? (
                                              <FormGroup row>
                                                {selectableFields.map((fieldKey) => (
                                                  <FormControlLabel
                                                    key={fieldKey}
                                                    control={
                                                      <Checkbox
                                                        checked={Boolean(draft.selectedIndexedFields?.includes(fieldKey))}
                                                        onChange={(event) =>
                                                          updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                                            ...current,
                                                            selectedIndexedFields: event.target.checked
                                                              ? [...(current.selectedIndexedFields ?? []), fieldKey]
                                                              : (current.selectedIndexedFields ?? []).filter((value) => value !== fieldKey),
                                                          }))
                                                        }
                                                      />
                                                    }
                                                    label={fieldKey}
                                                  />
                                                ))}
                                              </FormGroup>
                                            ) : null}
                                          </Stack>
                                        </CardContent>
                                      </Card>
                                    )
                                  })}

                                  <Stack direction="row" spacing={1}>
                                    <Button
                                      variant="contained"
                                      onClick={() =>
                                        selectedStore &&
                                        vectorizationPolicyDraft &&
                                        saveVectorizationPolicyMutation.mutate({
                                          shopDomain: selectedStore.shopDomain,
                                          payload: vectorizationPolicyDraft,
                                        })
                                      }
                                      disabled={vectorizationBusy || !selectedStore || !vectorizationPolicyDraft}
                                    >
                                      Save live update policy
                                    </Button>
                                  </Stack>
                                </Stack>
                              </CardContent>
                            </Card>
                          ) : null}

                          <Card variant="outlined">
                            <CardContent>
                              <Stack spacing={1.5}>
                                <Typography sx={{ fontWeight: 700 }}>Recent live update events</Typography>
                                {selectedVectorization.recentEvents.length === 0 ? (
                                  <Typography variant="body2" color="text.secondary">
                                    No Shopify live update events have been recorded yet.
                                  </Typography>
                                ) : (
                                  selectedVectorization.recentEvents.map((event) => (
                                    <Card key={event.id} variant="outlined">
                                      <CardContent>
                                        <Stack spacing={1}>
                                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                            <Typography sx={{ fontWeight: 700 }}>
                                              {event.sourceCategory} {event.operation}
                                            </Typography>
                                            <Chip size="small" label={event.status} color={chipColor(event.status)} />
                                            {event.failureCode ? <Chip size="small" variant="outlined" label={event.failureCode} /> : null}
                                            <Typography variant="caption" color="text.secondary">
                                              {formatTimestamp(event.occurredAt)}
                                            </Typography>
                                          </Stack>
                                          <Typography variant="body2" color="text.secondary">
                                            Topic {event.shopifyTopic ?? '—'} · Entity {event.entityType} · Object {event.sourceObjectId ?? '—'} · Run{' '}
                                            {event.coalescedRunId ?? '—'}
                                          </Typography>
                                          {event.notes ? (
                                            <Typography variant="body2" color="text.secondary">
                                              {event.notes}
                                            </Typography>
                                          ) : null}
                                          <Stack direction="row" spacing={1}>
                                            <Button
                                              variant="outlined"
                                              size="small"
                                              onClick={() =>
                                                replayVectorizationEventMutation.mutate({
                                                  shopDomain: selectedStore.shopDomain,
                                                  eventId: event.id,
                                                })
                                              }
                                              disabled={vectorizationBusy}
                                            >
                                              Replay event
                                            </Button>
                                          </Stack>
                                        </Stack>
                                      </CardContent>
                                    </Card>
                                  ))
                                )}
                              </Stack>
                            </CardContent>
                          </Card>
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : selectedVectorizationQuery.isLoading ? (
                    <Alert severity="info">Loading Shopify indexing state…</Alert>
                  ) : null}

                  {selectedStore.webhookDetail ? (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>Latest webhook</Typography>
                            <Chip
                              size="small"
                              label={selectedStore.webhookDetail.eventType ?? 'RECEIVED'}
                              color={chipColor(selectedStore.webhookDetail.eventType ?? 'UNKNOWN')}
                            />
                            <Typography variant="caption" color="text.secondary">
                              Received {formatTimestamp(selectedStore.webhookDetail.receivedAt)}
                            </Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            Topic {selectedStore.webhookDetail.topic ?? '—'} · Source {selectedStore.webhookDetail.sourceCategory ?? '—'}
                            {selectedStore.webhookDetail.invalidateSync ? ' · Sync invalidated' : ''}
                          </Typography>
                          {selectedStore.webhookDetail.message ? (
                            <Typography variant="body2" color="text.secondary">
                              {selectedStore.webhookDetail.message}
                            </Typography>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : null}

                  {selectedStore.latestRelease ? (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>Go-live release</Typography>
                            <Chip size="small" label={selectedStore.latestRelease.status} color={chipColor(selectedStore.latestRelease.status)} />
                            <Chip
                              size="small"
                              variant="outlined"
                              label={`Verification ${selectedStore.latestRelease.verificationStatus}`}
                              color={chipColor(selectedStore.latestRelease.verificationStatus)}
                            />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            Version {selectedStore.latestVersion?.versionLabel ?? selectedStore.latestRelease.deploymentVersionId} · Provisioning{' '}
                            {selectedStore.latestRelease.provisioningStatus}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Step {selectedStore.latestRelease.currentStepKey ?? '—'} · {selectedStore.latestRelease.currentStepDescription ?? 'No current step description.'}
                          </Typography>
                          {selectedStore.latestRelease.errorMessage ? (
                            <Typography variant="body2" color="error.main">
                              {selectedStore.latestRelease.errorMessage}
                            </Typography>
                          ) : null}
                          <Typography variant="body2" color="text.secondary">
                            Created {formatTimestamp(selectedStore.latestRelease.createdAt)} · Applied {formatTimestamp(selectedStore.latestRelease.appliedAt)} · Updated{' '}
                            {formatTimestamp(selectedStore.latestRelease.updatedAt)}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : null}

                  {selectedStore.widgetDetail ? (
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>Storefront widget</Typography>
                            <Chip size="small" label={selectedStore.widgetDetail.status} color={chipColor(selectedStore.widgetDetail.status)} />
                            <Typography variant="caption" color="text.secondary">
                              Checked {formatTimestamp(selectedStore.widgetDetail.checkedAt)}
                            </Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            Channel {selectedStore.widgetDetail.channel ?? '—'}
                          </Typography>
                          {selectedStore.widgetDetail.settings ? (
                            <Typography variant="body2" color="text.secondary">
                              Launcher {selectedStore.widgetDetail.settings.launcherLabel ?? 'Ask the store assistant'} · Welcome message{' '}
                              {selectedStore.widgetDetail.settings.welcomeMessage ?? 'Store assistant is ready. Ask about products, policies, or collections.'} · Profile{' '}
                              {selectedStore.widgetDetail.settings.shellModeProfile ?? 'SHOPIFY_COMPANION'} · Surfaces{' '}
                              {(selectedStore.widgetDetail.settings.enabledSurfaces?.length
                                ? selectedStore.widgetDetail.settings.enabledSurfaces
                                : ['ai-search', 'contextual-pill', 'product-insight', 'policy-strip', 'product-faq', 'comparison']
                              ).join(' · ')}
                            </Typography>
                          ) : null}
                          {selectedStore.widgetDetail.message ? (
                            <Typography variant="body2" color="text.secondary">
                              {selectedStore.widgetDetail.message}
                            </Typography>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : null}
                </Stack>
              </CardContent>
            </Card>
          )}
        </Grid>
      </Grid>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Register Shopify Store Mapping</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField label="Shop domain" value={form.shopDomain} onChange={(event) => setForm((current) => ({ ...current, shopDomain: event.target.value }))} fullWidth />
            <TextField label="Display name" value={form.displayName ?? ''} onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value || null }))} fullWidth />
            <TextField
              select
              label="Product service"
              value={form.productServiceRef}
              onChange={(event) => setForm((current) => ({ ...current, productServiceRef: event.target.value }))}
              fullWidth
            >
              {(servicesQuery.data ?? []).map((service) => (
                <MenuItem key={service.serviceRef} value={service.serviceRef}>
                  {service.displayName} ({service.serviceRef})
                </MenuItem>
              ))}
            </TextField>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField label="Customer ID" value={form.customerId ?? ''} onChange={(event) => setForm((current) => ({ ...current, customerId: event.target.value || null }))} fullWidth />
              <TextField label="Deployment ID" value={form.deploymentId ?? ''} onChange={(event) => setForm((current) => ({ ...current, deploymentId: event.target.value || null }))} fullWidth />
            </Stack>
            <TextField label="Consumer ID" value={form.consumerId ?? ''} onChange={(event) => setForm((current) => ({ ...current, consumerId: event.target.value || null }))} fullWidth />
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField select label="Install status" value={form.installStatus ?? 'INSTALLED'} onChange={(event) => setForm((current) => ({ ...current, installStatus: event.target.value }))} fullWidth>
                <MenuItem value="INSTALLED">INSTALLED</MenuItem>
                <MenuItem value="PENDING">PENDING</MenuItem>
                <MenuItem value="FAILED">FAILED</MenuItem>
              </TextField>
              <TextField select label="Sync status" value={form.syncStatus ?? 'NOT_SYNCED'} onChange={(event) => setForm((current) => ({ ...current, syncStatus: event.target.value }))} fullWidth>
                <MenuItem value="NOT_SYNCED">NOT_SYNCED</MenuItem>
                <MenuItem value="SYNCED">SYNCED</MenuItem>
                <MenuItem value="FAILED">FAILED</MenuItem>
              </TextField>
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField select label="Source readiness" value={form.sourceReadinessStatus ?? 'NOT_RUN'} onChange={(event) => setForm((current) => ({ ...current, sourceReadinessStatus: event.target.value }))} fullWidth>
                <MenuItem value="NOT_RUN">NOT_RUN</MenuItem>
                <MenuItem value="READY">READY</MenuItem>
                <MenuItem value="BLOCKED">BLOCKED</MenuItem>
              </TextField>
              <TextField select label="Widget status" value={form.widgetStatus ?? 'NOT_ENABLED'} onChange={(event) => setForm((current) => ({ ...current, widgetStatus: event.target.value }))} fullWidth>
                <MenuItem value="NOT_ENABLED">NOT_ENABLED</MenuItem>
                <MenuItem value="ENABLED">ENABLED</MenuItem>
                <MenuItem value="FAILED">FAILED</MenuItem>
              </TextField>
            </Stack>
            <TextField
              select
              label="Onboarding status"
              value={form.onboardingStatus ?? 'NOT_STARTED'}
              onChange={(event) => setForm((current) => ({ ...current, onboardingStatus: event.target.value }))}
              fullWidth
            >
              <MenuItem value="NOT_STARTED">NOT_STARTED</MenuItem>
              <MenuItem value="INSTALL_IDENTITY_READY">INSTALL_IDENTITY_READY</MenuItem>
              <MenuItem value="PLATFORM_BOOTSTRAPPED">PLATFORM_BOOTSTRAPPED</MenuItem>
              <MenuItem value="PREFLIGHT_READY">PREFLIGHT_READY</MenuItem>
              <MenuItem value="GO_LIVE_REQUESTED">GO_LIVE_REQUESTED</MenuItem>
              <MenuItem value="LIVE">LIVE</MenuItem>
              <MenuItem value="BLOCKED">BLOCKED</MenuItem>
            </TextField>
            <FormGroup row>
              <FormControlLabel
                control={<Checkbox checked={Boolean(form.productsEnabled)} onChange={(event) => setForm((current) => ({ ...current, productsEnabled: event.target.checked }))} />}
                label="Products"
              />
              <FormControlLabel
                control={<Checkbox checked={Boolean(form.collectionsEnabled)} onChange={(event) => setForm((current) => ({ ...current, collectionsEnabled: event.target.checked }))} />}
                label="Collections"
              />
              <FormControlLabel
                control={<Checkbox checked={Boolean(form.pagesEnabled)} onChange={(event) => setForm((current) => ({ ...current, pagesEnabled: event.target.checked }))} />}
                label="Pages"
              />
              <FormControlLabel
                control={<Checkbox checked={Boolean(form.policiesEnabled)} onChange={(event) => setForm((current) => ({ ...current, policiesEnabled: event.target.checked }))} />}
                label="Policies"
              />
              <FormControlLabel
                control={<Checkbox checked={Boolean(form.articlesEnabled)} onChange={(event) => setForm((current) => ({ ...current, articlesEnabled: event.target.checked }))} />}
                label="Articles"
              />
            </FormGroup>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => upsertMutation.mutate(form)} disabled={upsertMutation.isPending}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={preflightDialogOpen} onClose={() => setPreflightDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Record Source Preflight</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {selectedStore ? (
              <Alert severity="info">
                Record preflight results for enabled Shopify sources on <strong>{selectedStore.shopDomain}</strong>. This is source readiness only; real vectorized
                readiness still happens during publish/apply.
              </Alert>
            ) : null}
            {preflightCategories.length === 0 ? (
              <Alert severity="warning">No enabled source categories are currently configured for this store.</Alert>
            ) : (
              preflightCategories.map((category, index) => (
                <Card key={category.category} variant="outlined">
                  <CardContent>
                    <Stack spacing={2}>
                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                        <Typography sx={{ fontWeight: 700, textTransform: 'capitalize' }}>{category.category}</Typography>
                        <Chip size="small" label={category.enabled ? 'Enabled' : 'Disabled'} color={category.enabled ? 'success' : 'default'} />
                      </Stack>
                      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                        <TextField
                          select
                          label="Status"
                          value={category.status}
                          onChange={(event) =>
                            setPreflightCategories((current) =>
                              current.map((item, itemIndex) => (itemIndex === index ? { ...item, status: event.target.value as PreflightCategoryStatus } : item)),
                            )
                          }
                          fullWidth
                        >
                          <MenuItem value="PENDING">PENDING</MenuItem>
                          <MenuItem value="READY">READY</MenuItem>
                          <MenuItem value="BLOCKED">BLOCKED</MenuItem>
                          <MenuItem value="FAILED">FAILED</MenuItem>
                        </TextField>
                        <TextField
                          label="Item count"
                          value={category.itemCountText}
                          onChange={(event) =>
                            setPreflightCategories((current) =>
                              current.map((item, itemIndex) => (itemIndex === index ? { ...item, itemCountText: event.target.value } : item)),
                            )
                          }
                          fullWidth
                        />
                      </Stack>
                      <TextField
                        label="Message"
                        value={category.message ?? ''}
                        onChange={(event) =>
                          setPreflightCategories((current) =>
                            current.map((item, itemIndex) => (itemIndex === index ? { ...item, message: event.target.value || null } : item)),
                          )
                        }
                        fullWidth
                        multiline
                        minRows={2}
                      />
                    </Stack>
                  </CardContent>
                </Card>
              ))
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreflightDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={submitPreflight} disabled={preflightMutation.isPending || preflightCategories.length === 0}>
            Save preflight
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Delete Shopify Store Mapping</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="warning">
              This removes the platform mapping record only. It does not delete the underlying customer, deployment, or consumer objects.
            </Alert>
            {selectedStore ? (
              <Typography variant="body2">
                Type <strong>{selectedStore.shopDomain}</strong> to confirm deletion.
              </Typography>
            ) : null}
            <TextField
              label="Shop domain confirmation"
              value={deleteConfirmation}
              onChange={(event) => setDeleteConfirmation(event.target.value)}
              fullWidth
            />
            <FormControlLabel
              control={<Checkbox checked={deleteForce} onChange={(event) => setDeleteForce(event.target.checked)} />}
              label="Force delete even if customer, deployment, or consumer bindings still exist"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button
            color="error"
            variant="contained"
            onClick={() => selectedStore && deleteMutation.mutate({ shopDomain: selectedStore.shopDomain, force: deleteForce })}
            disabled={!selectedStore || deleteConfirmation.trim().toLowerCase() !== selectedStore.shopDomain.toLowerCase() || deleteMutation.isPending}
          >
            Delete mapping
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={bindingDialogOpen} onClose={() => setBindingDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Platform Binding Inspection</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Store {selectedStore?.shopDomain ?? '—'}
            </Typography>
            {bindingQuery.isLoading ? (
              <Alert severity="info">Loading platform binding inspection…</Alert>
            ) : bindingQuery.isError ? (
              <Alert severity="error">
                {bindingQuery.error instanceof Error ? bindingQuery.error.message : 'Failed to load platform binding inspection.'}
              </Alert>
            ) : bindingQuery.data ? (
              <Stack spacing={1.5}>
                {bindingQuery.data.warnings.length > 0 ? (
                  <Alert severity="warning">
                    <strong>Binding warnings</strong>
                    <List dense>
                      {bindingQuery.data.warnings.map((warning) => (
                        <li key={warning}>{warning}</li>
                      ))}
                    </List>
                  </Alert>
                ) : (
                  <Alert severity="success">Customer, deployment, and consumer bindings are aligned.</Alert>
                )}
                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Typography sx={{ fontWeight: 700 }}>Customer</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {bindingQuery.data.customer?.name ?? '—'} · {bindingQuery.data.customer?.id ?? '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Slug {bindingQuery.data.customer?.slug ?? '—'} · status {bindingQuery.data.customer?.status ?? '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Platform managed {bindingQuery.data.customer?.platformManaged ? 'yes' : 'no'}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Created {formatTimestamp(bindingQuery.data.customer?.createdAt)} · Updated {formatTimestamp(bindingQuery.data.customer?.updatedAt)}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Typography sx={{ fontWeight: 700 }}>Deployment</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {bindingQuery.data.deployment?.name ?? '—'} · {bindingQuery.data.deployment?.id ?? '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Env {bindingQuery.data.deployment?.environment ?? '—'} · status {bindingQuery.data.deployment?.status ?? '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Active version {bindingQuery.data.deployment?.activeVersionId ?? '—'} · template {bindingQuery.data.deployment?.templateId ?? '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Runtime {bindingQuery.data.deployment?.runtimeBaseUrl ?? '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Connector {bindingQuery.data.deployment?.connectorBaseUrl ?? '—'}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Created {formatTimestamp(bindingQuery.data.deployment?.createdAt)} · Updated {formatTimestamp(bindingQuery.data.deployment?.updatedAt)}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Typography sx={{ fontWeight: 700 }}>Consumer</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {bindingQuery.data.consumer?.displayName ?? '—'} · {bindingQuery.data.consumer?.consumerId ?? '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Status {bindingQuery.data.consumer?.status ?? '—'} · customer {bindingQuery.data.consumer?.customerId ?? '—'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Bound deployment {bindingQuery.data.consumer?.boundDeploymentName ?? '—'} · {bindingQuery.data.consumer?.boundDeploymentId ?? '—'}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Last bound {formatTimestamp(bindingQuery.data.consumer?.lastBoundAt)} · Updated {formatTimestamp(bindingQuery.data.consumer?.updatedAt)}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                </Grid>
                <Typography variant="body2" color="text.secondary">
                  Latest version {bindingQuery.data.latestVersion?.versionLabel ?? '—'} · status {bindingQuery.data.latestVersion?.status ?? '—'} · published{' '}
                  {formatTimestamp(bindingQuery.data.latestVersion?.publishedAt)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Latest release {bindingQuery.data.latestRelease?.status ?? '—'} · verification {bindingQuery.data.latestRelease?.verificationStatus ?? '—'} · provisioning{' '}
                  {bindingQuery.data.latestRelease?.provisioningStatus ?? '—'}
                </Typography>
              </Stack>
            ) : (
              <Alert severity="info">Platform binding inspection is not available for this store yet.</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBindingDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
