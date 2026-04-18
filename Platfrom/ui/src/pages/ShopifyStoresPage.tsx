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
  fetchShopifyStore,
  fetchShopifyStores,
  recordShopifyStoreSourcePreflight,
  type RecordShopifyStoreSourcePreflightRequest,
  type ShopifyStoreBootstrapSummary,
  type ShopifyStoreSourcePreflightCategorySummary,
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
      return 'warning'
    default:
      return 'default'
  }
}

type StoreFormState = UpsertShopifyStoreConnectionRequest
type PreflightCategoryStatus = 'READY' | 'PENDING' | 'BLOCKED' | 'FAILED'
type PreflightFormCategory = ShopifyStoreSourcePreflightCategorySummary & { itemCountText: string }

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
}

function buildPreflightCategories(store: ShopifyStoreConnectionSummary): PreflightFormCategory[] {
  const existing = new Map((store.sourcePreflight?.categories ?? []).map((category) => [category.category, category]))
  return [
    { category: 'products', enabled: store.productsEnabled },
    { category: 'collections', enabled: store.collectionsEnabled },
    { category: 'pages', enabled: store.pagesEnabled },
    { category: 'policies', enabled: store.policiesEnabled },
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

export function ShopifyStoresPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [preflightDialogOpen, setPreflightDialogOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const [deleteForce, setDeleteForce] = useState(false)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [form, setForm] = useState<StoreFormState>(emptyForm)
  const [preflightCategories, setPreflightCategories] = useState<PreflightFormCategory[]>([])

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
  })

  const selectedStore = selectedStoreQuery.data ?? selectedSummary

  useEffect(() => {
    if (selectedStore && preflightDialogOpen) {
      setPreflightCategories(buildPreflightCategories(selectedStore))
    }
  }, [preflightDialogOpen, selectedStore])

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
                onClick={() => {
                  setPreflightCategories(buildPreflightCategories(selectedStore))
                  setPreflightDialogOpen(true)
                }}
                disabled={preflightMutation.isPending}
              >
                Record source preflight
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
                      ['Consumer', selectedStore.consumerDisplayName ?? selectedStore.consumerId],
                      ['Onboarding', selectedStore.onboardingStatus],
                      ['Credentials', selectedStore.credentials?.status],
                      ['Products enabled', selectedStore.productsEnabled ? 'Yes' : 'No'],
                      ['Collections enabled', selectedStore.collectionsEnabled ? 'Yes' : 'No'],
                      ['Pages enabled', selectedStore.pagesEnabled ? 'Yes' : 'No'],
                      ['Policies enabled', selectedStore.policiesEnabled ? 'Yes' : 'No'],
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
    </Stack>
  )
}
