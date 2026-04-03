import AddRoundedIcon from '@mui/icons-material/AddRounded'
import ApartmentRoundedIcon from '@mui/icons-material/ApartmentRounded'
import DeleteSweepRoundedIcon from '@mui/icons-material/DeleteSweepRounded'
import EditRoundedIcon from '@mui/icons-material/EditRounded'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import PeopleAltRoundedIcon from '@mui/icons-material/PeopleAltRounded'
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
  DialogTitle,
  FormControlLabel,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  createPlatformCustomer,
  createPlatformTenant,
  fetchPlatformCustomers,
  fetchPlatformTenantSharedVectorHandles,
  purgePlatformTenantSharedVectorHandles,
  type PlatformCustomerSummary,
  type PlatformTenantSharedVectorHandleSummary,
  type PlatformTenantSummary,
  type PurgePlatformTenantSharedVectorHandlesSummary,
  updatePlatformCustomer,
  updatePlatformTenant,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

type CustomerFormState = {
  name: string
  description: string
}

type TenantFormState = {
  name: string
  description: string
}

function formatTimestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '—'
}

function tenantSharedVectorColor(status: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  if (!status) {
    return 'default'
  }
  switch (status.toUpperCase()) {
    case 'ACTIVE':
    case 'READY':
      return 'success'
    case 'DETACHED':
    case 'WARNING':
      return 'warning'
    case 'BLOCKED':
    case 'FAILED':
      return 'error'
    default:
      return 'default'
  }
}

const PURGE_SHARED_HANDLE_CONFIRMATION = 'PURGE DETACHED HANDLES'

export function CustomersPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const canManageUsers = auth.session?.enabled ? auth.session.canManageUsers : true
  const [notice, setNotice] = useState<string | null>(null)
  const [customerForm, setCustomerForm] = useState<CustomerFormState>({ name: '', description: '' })
  const [editingCustomer, setEditingCustomer] = useState<PlatformCustomerSummary | null>(null)
  const [editCustomerForm, setEditCustomerForm] = useState<CustomerFormState>({ name: '', description: '' })
  const [createTenantCustomer, setCreateTenantCustomer] = useState<PlatformCustomerSummary | null>(null)
  const [tenantForm, setTenantForm] = useState<TenantFormState>({ name: '', description: '' })
  const [editingTenant, setEditingTenant] = useState<PlatformTenantSummary | null>(null)
  const [editTenantForm, setEditTenantForm] = useState<TenantFormState>({ name: '', description: '' })
  const [sharedHandleTenant, setSharedHandleTenant] = useState<PlatformTenantSummary | null>(null)
  const [selectedSharedHandleIds, setSelectedSharedHandleIds] = useState<string[]>([])
  const [sharedHandleConfirmationText, setSharedHandleConfirmationText] = useState('')
  const [sharedHandleReason, setSharedHandleReason] = useState('')
  const [sharedHandleProviderDeleteConfirmed, setSharedHandleProviderDeleteConfirmed] = useState(false)
  const [sharedHandleNotice, setSharedHandleNotice] = useState<PurgePlatformTenantSharedVectorHandlesSummary | null>(null)

  const customersQuery = useQuery({
    queryKey: ['platform-customers'],
    queryFn: fetchPlatformCustomers,
    enabled: canManageUsers,
  })

  const sharedHandlesQuery = useQuery({
    queryKey: ['platform-tenant-shared-vector-handles', sharedHandleTenant?.id],
    queryFn: () => fetchPlatformTenantSharedVectorHandles(sharedHandleTenant!.id),
    enabled: canManageUsers && sharedHandleTenant != null,
  })

  useEffect(() => {
    if (editingCustomer) {
      setEditCustomerForm({
        name: editingCustomer.name,
        description: editingCustomer.description ?? '',
      })
    }
  }, [editingCustomer])

  useEffect(() => {
    if (editingTenant) {
      setEditTenantForm({
        name: editingTenant.name,
        description: editingTenant.description ?? '',
      })
    }
  }, [editingTenant])

  useEffect(() => {
    setSelectedSharedHandleIds([])
    setSharedHandleConfirmationText('')
    setSharedHandleReason('')
    setSharedHandleProviderDeleteConfirmed(false)
  }, [sharedHandleTenant?.id])

  const customers = customersQuery.data ?? []
  const metrics = useMemo(() => ({
    customers: customers.length,
    tenants: customers.reduce((sum, customer) => sum + customer.tenantCount, 0),
    boundDeployments: customers.reduce((sum, customer) => sum + customer.deploymentCount, 0),
  }), [customers])

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['platform-customers'] }),
      queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-workspace'] }),
    ])
  }

  const createCustomerMutation = useMutation({
    mutationFn: () => createPlatformCustomer({
      name: customerForm.name.trim(),
      description: customerForm.description.trim() || undefined,
    }),
    onSuccess: async (customer) => {
      setNotice(`Customer ${customer.name} created.`)
      setCustomerForm({ name: '', description: '' })
      await invalidate()
    },
  })

  const updateCustomerMutation = useMutation({
    mutationFn: () => {
      if (!editingCustomer) {
        throw new Error('No customer selected.')
      }
      return updatePlatformCustomer(editingCustomer.id, {
        name: editCustomerForm.name.trim(),
        description: editCustomerForm.description.trim() || undefined,
      })
    },
    onSuccess: async (customer) => {
      setNotice(`Customer ${customer.name} updated.`)
      setEditingCustomer(null)
      await invalidate()
    },
  })

  const createTenantMutation = useMutation({
    mutationFn: () => {
      if (!createTenantCustomer) {
        throw new Error('No customer selected.')
      }
      return createPlatformTenant(createTenantCustomer.id, {
        name: tenantForm.name.trim(),
        description: tenantForm.description.trim() || undefined,
      })
    },
    onSuccess: async (tenant) => {
      setNotice(`Tenant ${tenant.name} created under ${tenant.customerName}.`)
      setCreateTenantCustomer(null)
      setTenantForm({ name: '', description: '' })
      await invalidate()
    },
  })

  const updateTenantMutation = useMutation({
    mutationFn: () => {
      if (!editingTenant) {
        throw new Error('No tenant selected.')
      }
      return updatePlatformTenant(editingTenant.id, {
        name: editTenantForm.name.trim(),
        description: editTenantForm.description.trim() || undefined,
      })
    },
    onSuccess: async (tenant) => {
      setNotice(`Tenant ${tenant.name} updated.`)
      setEditingTenant(null)
      await invalidate()
    },
  })

  const purgeSharedHandlesMutation = useMutation({
    mutationFn: async () => {
      if (!sharedHandleTenant) {
        throw new Error('No tenant selected.')
      }
      return purgePlatformTenantSharedVectorHandles(sharedHandleTenant.id, {
        handleIds: selectedSharedHandleIds,
        purgeAllDetached: false,
        providerDeleteConfirmed: sharedHandleProviderDeleteConfirmed,
        confirmationText: sharedHandleConfirmationText.trim(),
        reason: sharedHandleReason.trim(),
      })
    },
    onSuccess: async (summary) => {
      setSharedHandleNotice(summary)
      setNotice(summary.message)
      setSelectedSharedHandleIds([])
      setSharedHandleConfirmationText('')
      setSharedHandleReason('')
      setSharedHandleProviderDeleteConfirmed(false)
      await Promise.all([
        invalidate(),
        queryClient.invalidateQueries({ queryKey: ['platform-tenant-shared-vector-handles', summary.tenantId] }),
      ])
    },
  })

  const sharedHandles = sharedHandlesQuery.data ?? []
  const activeSharedHandles = sharedHandles.filter((handle) => handle.resourceStatus.toUpperCase() === 'ACTIVE')
  const detachedSharedHandles = sharedHandles.filter((handle) => handle.cleanupEligible)
  const allDetachedSelected = detachedSharedHandles.length > 0
    && detachedSharedHandles.every((handle) => selectedSharedHandleIds.includes(handle.id))
  const canPurgeDetachedHandles = Boolean(
    sharedHandleTenant
    && detachedSharedHandles.length > 0
    && activeSharedHandles.length === 0
    && selectedSharedHandleIds.length > 0
    && sharedHandleProviderDeleteConfirmed
    && sharedHandleConfirmationText.trim() === PURGE_SHARED_HANDLE_CONFIRMATION
    && sharedHandleReason.trim().length >= 10
    && !purgeSharedHandlesMutation.isPending,
  )

  const closeSharedHandleDialog = () => {
    if (purgeSharedHandlesMutation.isPending) {
      return
    }
    setSharedHandleTenant(null)
    setSharedHandleNotice(null)
    setSelectedSharedHandleIds([])
    setSharedHandleConfirmationText('')
    setSharedHandleReason('')
    setSharedHandleProviderDeleteConfirmed(false)
  }

  const toggleSharedHandleSelection = (handleId: string) => {
    setSelectedSharedHandleIds((current) => (
      current.includes(handleId)
        ? current.filter((value) => value !== handleId)
        : [...current, handleId]
    ))
  }

  const toggleSelectAllDetached = (checked: boolean) => {
    setSelectedSharedHandleIds(checked ? detachedSharedHandles.map((handle) => handle.id) : [])
  }

  if (!canManageUsers) {
    return (
      <Stack spacing={2}>
        <Chip label="Customers" color="primary" sx={{ alignSelf: 'flex-start', fontWeight: 700 }} />
        <Alert severity="warning">
          This screen requires the <code>PLATFORM_ADMIN</code> role.
        </Alert>
      </Stack>
    )
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Customers" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Customer and tenant boundaries
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Customers are the top-level enterprise ownership boundary. Tenants live inside a customer,
          and every deployment is bound to exactly one tenant. This page manages those durable
          platform records, tenant bindings, and the shared tenant-scoped vector handles registered
          for enterprise storage posture.
        </Typography>
      </Box>

      <Grid container spacing={2.5}>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline" color="text.secondary">Customers</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.customers}</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline" color="text.secondary">Tenants</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.tenants}</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline" color="text.secondary">Bound deployments</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.boundDeployments}</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {notice ? <Alert severity="success">{notice}</Alert> : null}

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={4}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Create customer</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Create a durable customer account boundary before assigning deployments and tenants.
                  </Typography>
                </Box>
                <TextField
                  label="Customer name"
                  value={customerForm.name}
                  onChange={(event) => setCustomerForm((current) => ({ ...current, name: event.target.value }))}
                />
                <TextField
                  label="Description"
                  multiline
                  minRows={3}
                  value={customerForm.description}
                  onChange={(event) => setCustomerForm((current) => ({ ...current, description: event.target.value }))}
                />
                {createCustomerMutation.isError ? (
                  <Alert severity="error">
                    {createCustomerMutation.error instanceof Error
                      ? createCustomerMutation.error.message
                      : 'Failed to create customer.'}
                  </Alert>
                ) : null}
                <Button
                  variant="contained"
                  startIcon={<AddRoundedIcon />}
                  disabled={customerForm.name.trim().length < 2 || createCustomerMutation.isPending}
                  onClick={() => createCustomerMutation.mutate()}
                >
                  {createCustomerMutation.isPending ? 'Creating…' : 'Create customer'}
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={8}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Customer registry</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Review customer ownership, tenants, and which deployments are already bound.
                  </Typography>
                </Box>

                {customersQuery.isLoading ? (
                  <Typography color="text.secondary">Loading customers…</Typography>
                ) : customersQuery.isError ? (
                  <Alert severity="error">
                    {customersQuery.error instanceof Error ? customersQuery.error.message : 'Failed to load customers.'}
                  </Alert>
                ) : customers.length === 0 ? (
                  <Alert severity="info">No customers found.</Alert>
                ) : (
                  <Stack spacing={1.75}>
                    {customers.map((customer) => (
                      <Card
                        key={customer.id}
                        sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', bgcolor: 'background.default' }}
                      >
                        <CardContent>
                          <Stack spacing={1.5}>
                            <Stack direction={{ xs: 'column', lg: 'row' }} spacing={1.5} justifyContent="space-between">
                              <Box>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                    {customer.name}
                                  </Typography>
                                  <Chip size="small" label={customer.slug} variant="outlined" />
                                  <Chip
                                    size="small"
                                    label={customer.platformManaged ? 'Platform managed' : 'Customer managed'}
                                    color={customer.platformManaged ? 'warning' : 'primary'}
                                    variant="outlined"
                                  />
                                </Stack>
                                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                                  {customer.description || 'No description provided.'}
                                </Typography>
                              </Box>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                <Chip size="small" label={`${customer.tenantCount} tenant(s)`} variant="outlined" />
                                <Chip size="small" label={`${customer.deploymentCount} deployment(s)`} variant="outlined" />
                                {!customer.platformManaged ? (
                                  <Button
                                    variant="outlined"
                                    startIcon={<EditRoundedIcon />}
                                    onClick={() => setEditingCustomer(customer)}
                                  >
                                    Edit customer
                                  </Button>
                                ) : null}
                                <Button
                                  variant="outlined"
                                  startIcon={<AddRoundedIcon />}
                                  onClick={() => setCreateTenantCustomer(customer)}
                                >
                                  Add tenant
                                </Button>
                              </Stack>
                            </Stack>

                            <Typography variant="caption" color="text.secondary">
                              Created {formatTimestamp(customer.createdAt)} · Updated {formatTimestamp(customer.updatedAt)}
                            </Typography>

                            {customer.tenants.length === 0 ? (
                              <Alert severity="info">No tenants created for this customer yet.</Alert>
                            ) : (
                              <Grid container spacing={1.5}>
                                {customer.tenants.map((tenant) => (
                                  <Grid item xs={12} md={6} key={tenant.id}>
                                    <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                                      <CardContent>
                                        <Stack spacing={1.25}>
                                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                                            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                              {tenant.name}
                                            </Typography>
                                            <Chip size="small" label={tenant.slug} variant="outlined" />
                                            {tenant.platformManaged ? (
                                              <Chip size="small" label="Platform managed" color="warning" variant="outlined" />
                                            ) : null}
                                          </Stack>
                                          <Typography variant="body2" color="text.secondary">
                                            {tenant.description || 'No description provided.'}
                                          </Typography>
                                          {tenant.boundDeploymentId ? (
                                            <Alert severity="success" icon={<PeopleAltRoundedIcon fontSize="inherit" />}>
                                              Bound to deployment <strong>{tenant.boundDeploymentName}</strong> ({tenant.boundDeploymentEnvironment}).
                                            </Alert>
                                          ) : (
                                            <Alert severity="info">No deployment is currently bound to this tenant.</Alert>
                                          )}
                                          {tenant.sharedVector ? (
                                            <Stack spacing={0.75}>
                                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                                <Chip
                                                  size="small"
                                                  label={`${tenant.sharedVector.activeHandleCount} active shared handle(s)`}
                                                  color={tenantSharedVectorColor(tenant.sharedVector.latestStatus)}
                                                  variant="outlined"
                                                />
                                                <Chip
                                                  size="small"
                                                  label={`${tenant.sharedVector.historicalHandleCount} historical`}
                                                  variant="outlined"
                                                />
                                                {tenant.sharedVector.latestVectorStrategy ? (
                                                  <Chip
                                                    size="small"
                                                    label={tenant.sharedVector.latestVectorStrategy}
                                                    variant="outlined"
                                                  />
                                                ) : null}
                                                <Chip
                                                  size="small"
                                                  label={`Cleanup ${tenant.sharedVector.cleanupReadinessStatus}`}
                                                  color={tenantSharedVectorColor(tenant.sharedVector.cleanupReadinessStatus)}
                                                  variant="outlined"
                                                />
                                              </Stack>
                                              <Typography variant="body2" color="text.secondary">
                                                {tenant.sharedVector.latestSummary}
                                              </Typography>
                                              <Typography variant="body2" color="text.secondary">
                                                {tenant.sharedVector.cleanupReadinessMessage}
                                              </Typography>
                                              {tenant.sharedVector.latestScopePattern ? (
                                                <Typography variant="body2">
                                                  Latest scope: <strong>{tenant.sharedVector.latestScopePattern}</strong>
                                                </Typography>
                                              ) : null}
                                              {tenant.sharedVector.latestUpdatedAt ? (
                                                <Typography variant="caption" color="text.secondary">
                                                  Last shared-handle update {formatTimestamp(tenant.sharedVector.latestUpdatedAt)}
                                                </Typography>
                                              ) : null}
                                            </Stack>
                                          ) : null}
                                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                            {tenant.boundDeploymentId ? (
                                              <Button
                                                component={RouterLink}
                                                to={`/deployments`}
                                                variant="text"
                                                startIcon={<OpenInNewRoundedIcon />}
                                              >
                                                Review deployments
                                              </Button>
                                            ) : null}
                                            {!tenant.platformManaged ? (
                                              <Button
                                                variant="outlined"
                                                startIcon={<EditRoundedIcon />}
                                                onClick={() => setEditingTenant(tenant)}
                                              >
                                                Edit tenant
                                              </Button>
                                            ) : null}
                                            {tenant.sharedVector ? (
                                              <Button
                                                variant="outlined"
                                                startIcon={<DeleteSweepRoundedIcon />}
                                                onClick={() => {
                                                  setSharedHandleNotice(null)
                                                  setSharedHandleTenant(tenant)
                                                }}
                                              >
                                                Review shared handles
                                              </Button>
                                            ) : null}
                                          </Stack>
                                        </Stack>
                                      </CardContent>
                                    </Card>
                                  </Grid>
                                ))}
                              </Grid>
                            )}
                          </Stack>
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Dialog
        open={editingCustomer != null}
        onClose={() => {
          if (!updateCustomerMutation.isPending) {
            setEditingCustomer(null)
          }
        }}
      >
        <DialogTitle>Edit customer</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1, minWidth: { xs: 280, sm: 420 } }}>
            <TextField
              label="Customer name"
              value={editCustomerForm.name}
              onChange={(event) => setEditCustomerForm((current) => ({ ...current, name: event.target.value }))}
            />
            <TextField
              label="Description"
              multiline
              minRows={3}
              value={editCustomerForm.description}
              onChange={(event) => setEditCustomerForm((current) => ({ ...current, description: event.target.value }))}
            />
            {updateCustomerMutation.isError ? (
              <Alert severity="error">
                {updateCustomerMutation.error instanceof Error
                  ? updateCustomerMutation.error.message
                  : 'Failed to update customer.'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditingCustomer(null)} disabled={updateCustomerMutation.isPending}>Cancel</Button>
          <Button
            variant="contained"
            disabled={editCustomerForm.name.trim().length < 2 || updateCustomerMutation.isPending}
            onClick={() => updateCustomerMutation.mutate()}
          >
            {updateCustomerMutation.isPending ? 'Saving…' : 'Save customer'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={createTenantCustomer != null}
        onClose={() => {
          if (!createTenantMutation.isPending) {
            setCreateTenantCustomer(null)
            setTenantForm({ name: '', description: '' })
          }
        }}
      >
        <DialogTitle>Create tenant</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1, minWidth: { xs: 280, sm: 420 } }}>
            <Alert severity="info" icon={<ApartmentRoundedIcon fontSize="inherit" />}>
              New tenant will be created under <strong>{createTenantCustomer?.name}</strong>.
            </Alert>
            <TextField
              label="Tenant name"
              value={tenantForm.name}
              onChange={(event) => setTenantForm((current) => ({ ...current, name: event.target.value }))}
            />
            <TextField
              label="Description"
              multiline
              minRows={3}
              value={tenantForm.description}
              onChange={(event) => setTenantForm((current) => ({ ...current, description: event.target.value }))}
            />
            {createTenantMutation.isError ? (
              <Alert severity="error">
                {createTenantMutation.error instanceof Error
                  ? createTenantMutation.error.message
                  : 'Failed to create tenant.'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setCreateTenantCustomer(null)
              setTenantForm({ name: '', description: '' })
            }}
            disabled={createTenantMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={tenantForm.name.trim().length < 2 || createTenantMutation.isPending}
            onClick={() => createTenantMutation.mutate()}
          >
            {createTenantMutation.isPending ? 'Creating…' : 'Create tenant'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={editingTenant != null}
        onClose={() => {
          if (!updateTenantMutation.isPending) {
            setEditingTenant(null)
          }
        }}
      >
        <DialogTitle>Edit tenant</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1, minWidth: { xs: 280, sm: 420 } }}>
            <TextField
              label="Tenant name"
              value={editTenantForm.name}
              onChange={(event) => setEditTenantForm((current) => ({ ...current, name: event.target.value }))}
            />
            <TextField
              label="Description"
              multiline
              minRows={3}
              value={editTenantForm.description}
              onChange={(event) => setEditTenantForm((current) => ({ ...current, description: event.target.value }))}
            />
            {updateTenantMutation.isError ? (
              <Alert severity="error">
                {updateTenantMutation.error instanceof Error
                  ? updateTenantMutation.error.message
                  : 'Failed to update tenant.'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditingTenant(null)} disabled={updateTenantMutation.isPending}>Cancel</Button>
          <Button
            variant="contained"
            disabled={editTenantForm.name.trim().length < 2 || updateTenantMutation.isPending}
            onClick={() => updateTenantMutation.mutate()}
          >
            {updateTenantMutation.isPending ? 'Saving…' : 'Save tenant'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={sharedHandleTenant != null}
        onClose={closeSharedHandleDialog}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Tenant shared vector handles</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="info" icon={<DeleteSweepRoundedIcon fontSize="inherit" />}>
              Review the tenant-scoped shared vector handles registered for <strong>{sharedHandleTenant?.name}</strong>.
              Purge only detached history after the provider-side delete or retention decision is already complete.
            </Alert>

            {sharedHandleTenant?.sharedVector ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip
                  size="small"
                  label={`${sharedHandleTenant.sharedVector.activeHandleCount} active`}
                  color={tenantSharedVectorColor(sharedHandleTenant.sharedVector.latestStatus)}
                  variant="outlined"
                />
                <Chip
                  size="small"
                  label={`${sharedHandleTenant.sharedVector.historicalHandleCount} detached`}
                  variant="outlined"
                />
                <Chip
                  size="small"
                  label={`Cleanup ${sharedHandleTenant.sharedVector.cleanupReadinessStatus}`}
                  color={tenantSharedVectorColor(sharedHandleTenant.sharedVector.cleanupReadinessStatus)}
                  variant="outlined"
                />
                {sharedHandleTenant.sharedVector.latestVectorStrategy ? (
                  <Chip size="small" label={sharedHandleTenant.sharedVector.latestVectorStrategy} variant="outlined" />
                ) : null}
              </Stack>
            ) : null}

            {sharedHandleTenant?.sharedVector?.cleanupReadinessMessage ? (
              <Typography variant="body2" color="text.secondary">
                {sharedHandleTenant.sharedVector.cleanupReadinessMessage}
              </Typography>
            ) : null}

            {sharedHandleNotice ? <Alert severity="success">{sharedHandleNotice.message}</Alert> : null}

            {activeSharedHandles.length > 0 ? (
              <Alert severity="warning">
                Purge is blocked while this tenant still has {activeSharedHandles.length} active shared handle(s).
                Detach or migrate the active deployment-backed handles before removing historical records.
              </Alert>
            ) : null}

            {sharedHandlesQuery.isLoading ? (
              <Typography color="text.secondary">Loading shared handles…</Typography>
            ) : sharedHandlesQuery.isError ? (
              <Alert severity="error">
                {sharedHandlesQuery.error instanceof Error
                  ? sharedHandlesQuery.error.message
                  : 'Failed to load tenant shared handles.'}
              </Alert>
            ) : sharedHandles.length === 0 ? (
              <Alert severity="info">No shared handles are registered for this tenant.</Alert>
            ) : (
              <>
                {detachedSharedHandles.length > 0 ? (
                  <Stack spacing={1}>
                    <FormControlLabel
                      control={(
                        <Checkbox
                          checked={allDetachedSelected}
                          indeterminate={!allDetachedSelected && selectedSharedHandleIds.length > 0}
                          onChange={(event) => toggleSelectAllDetached(event.target.checked)}
                          disabled={purgeSharedHandlesMutation.isPending || activeSharedHandles.length > 0}
                        />
                      )}
                      label="Select all detached handles eligible for purge"
                    />
                    <Typography variant="body2" color="text.secondary">
                      Selected {selectedSharedHandleIds.length} of {detachedSharedHandles.length} detached handle(s).
                    </Typography>
                  </Stack>
                ) : (
                  <Alert severity="info">This tenant has no detached shared-handle history to purge.</Alert>
                )}

                <Stack spacing={1.25}>
                  {sharedHandles.map((handle) => (
                    <Card
                      key={handle.id}
                      sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}
                    >
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="space-between">
                            <Stack direction="row" spacing={1} alignItems="flex-start">
                              <Checkbox
                                checked={selectedSharedHandleIds.includes(handle.id)}
                                disabled={!handle.cleanupEligible || purgeSharedHandlesMutation.isPending || activeSharedHandles.length > 0}
                                onChange={() => toggleSharedHandleSelection(handle.id)}
                              />
                              <Box>
                                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                  {handle.scopePattern || handle.tenantHandle || handle.id}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {handle.id} · Updated {formatTimestamp(handle.updatedAt)}
                                </Typography>
                              </Box>
                            </Stack>
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <Chip
                                size="small"
                                label={handle.resourceStatus}
                                color={tenantSharedVectorColor(handle.resourceStatus)}
                                variant="outlined"
                              />
                              <Chip size="small" label={handle.vectorStrategy} variant="outlined" />
                              <Chip size="small" label={handle.vectorProvisioningMode} variant="outlined" />
                              <Chip size="small" label={handle.vectorStoragePosture} variant="outlined" />
                            </Stack>
                          </Stack>

                          <Grid container spacing={1.25}>
                            <Grid item xs={12} md={6}>
                              <Typography variant="body2">
                                Vendor: <strong>{handle.vendor || 'Unknown'}</strong>
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="body2">
                                Scope type: <strong>{handle.scopeType || 'Unknown'}</strong>
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="body2">
                                Root: <strong>{handle.rootResourceLabel || '—'}</strong>
                                {handle.rootResourceValue ? ` (${handle.rootResourceValue})` : ''}
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="body2">
                                Lifecycle owner: <strong>{handle.lifecycleOwner || '—'}</strong>
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="body2">
                                Deployment: <strong>{handle.deploymentId || 'Detached history'}</strong>
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="body2">
                                Tenant handle: <strong>{handle.tenantHandle || handle.scopePrefix || '—'}</strong>
                              </Typography>
                            </Grid>
                          </Grid>

                          {handle.summaryMessage ? (
                            <Alert severity={handle.summaryStatus?.toUpperCase() === 'FAILED' ? 'error' : 'info'}>
                              {handle.summaryMessage}
                            </Alert>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                </Stack>

                {detachedSharedHandles.length > 0 ? (
                  <Stack spacing={1.5} sx={{ pt: 1 }}>
                    <FormControlLabel
                      control={(
                        <Checkbox
                          checked={sharedHandleProviderDeleteConfirmed}
                          onChange={(event) => setSharedHandleProviderDeleteConfirmed(event.target.checked)}
                          disabled={purgeSharedHandlesMutation.isPending || activeSharedHandles.length > 0}
                        />
                      )}
                      label="I confirm the provider-side delete or retention decision is already complete."
                    />
                    <TextField
                      label={`Type '${PURGE_SHARED_HANDLE_CONFIRMATION}' to confirm`}
                      value={sharedHandleConfirmationText}
                      onChange={(event) => setSharedHandleConfirmationText(event.target.value)}
                      disabled={purgeSharedHandlesMutation.isPending || activeSharedHandles.length > 0}
                    />
                    <TextField
                      label="Reason"
                      multiline
                      minRows={3}
                      value={sharedHandleReason}
                      onChange={(event) => setSharedHandleReason(event.target.value)}
                      disabled={purgeSharedHandlesMutation.isPending || activeSharedHandles.length > 0}
                      helperText="Explain why detached shared-handle history can now be removed."
                    />
                    {purgeSharedHandlesMutation.isError ? (
                      <Alert severity="error">
                        {purgeSharedHandlesMutation.error instanceof Error
                          ? purgeSharedHandlesMutation.error.message
                          : 'Failed to purge detached shared handles.'}
                      </Alert>
                    ) : null}
                  </Stack>
                ) : null}
              </>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeSharedHandleDialog} disabled={purgeSharedHandlesMutation.isPending}>
            Close
          </Button>
          <Button
            variant="contained"
            color="error"
            startIcon={<DeleteSweepRoundedIcon />}
            disabled={!canPurgeDetachedHandles}
            onClick={() => purgeSharedHandlesMutation.mutate()}
          >
            {purgeSharedHandlesMutation.isPending ? 'Purging…' : 'Purge detached history'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
