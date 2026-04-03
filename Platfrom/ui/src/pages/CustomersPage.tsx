import AddRoundedIcon from '@mui/icons-material/AddRounded'
import ApartmentRoundedIcon from '@mui/icons-material/ApartmentRounded'
import EditRoundedIcon from '@mui/icons-material/EditRounded'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import PeopleAltRoundedIcon from '@mui/icons-material/PeopleAltRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
  type PlatformCustomerSummary,
  type PlatformTenantSummary,
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

  const customersQuery = useQuery({
    queryKey: ['platform-customers'],
    queryFn: fetchPlatformCustomers,
    enabled: canManageUsers,
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
                                              </Stack>
                                              <Typography variant="body2" color="text.secondary">
                                                {tenant.sharedVector.latestSummary}
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
    </Stack>
  )
}
