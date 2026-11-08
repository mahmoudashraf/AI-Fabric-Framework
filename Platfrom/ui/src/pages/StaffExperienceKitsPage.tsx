import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Alert, Box, Button, CircularProgress, Divider, MenuItem, Stack, TextField, Typography, useTheme } from '@mui/material'
import AddRounded from '@mui/icons-material/AddRounded'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import EditRounded from '@mui/icons-material/EditRounded'
import PeopleAltRounded from '@mui/icons-material/PeopleAltRounded'
import PersonAddAltRounded from '@mui/icons-material/PersonAddAltRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { fetchPlatformCustomers } from '../api/platformApi'
import {
  createStaffExperienceKit, fetchStaffExperienceKit, fetchStaffExperienceKitCatalogue, fetchStaffExperienceKits,
  prepareStaffExperienceKits, staffExperienceKitKeys, updateStaffExperienceKit, type ExperienceKitSummary,
} from '../api/experienceKitsApi'
import { ExperienceKitSetup, type ExperienceKitSetupTransport } from '../components/kits/ExperienceKitSetup'
import { KitExperienceFields } from '../components/kits/KitExperienceFields'
import { kitDisplayName, kitExperienceErrors, kitFormFromSummary, kitUpdateRequest } from '../components/kits/kitModels'
import { CustomerAccessDialog } from '../components/customer/CustomerAccessDialog'
import './ExperienceKits.css'
import { isDeveloperEntry, isDeveloperKit } from '../developer/developerEntry'

export const staffKitsPath = (customerId: string) => `/customers/${encodeURIComponent(customerId)}/kits`

const errorText = (error: unknown) => error instanceof Error ? error.message : 'The kit operation could not be completed.'

export function StaffCustomerWorkspacesPage() {
  const { session } = usePlatformAuth()
  const allowed = session?.authenticated && ['PLATFORM_ADMIN', 'PLATFORM_OPERATOR'].includes(session.role ?? '')
  const customers = useQuery({ queryKey: ['staff-kit-customers', session?.actorId],
    queryFn: fetchPlatformCustomers, enabled: Boolean(allowed), retry: false })
  if (!allowed) return <Alert severity="warning">A staff session is required.</Alert>
  return <Stack spacing={2}>
    <Typography variant="h5" component="h1">Customer workspaces</Typography>
    {customers.isPending && <CircularProgress size={24} aria-label="Loading customer workspaces" />}
    {customers.isError && <Alert severity="error" action={<Button onClick={() => void customers.refetch()}>Retry</Button>}>{errorText(customers.error)}</Alert>}
    {customers.isSuccess && !customers.data.length && <Alert severity="info">No customer has granted you workspace access.</Alert>}
    {customers.data?.map(customer => <Stack key={customer.id} direction="row" alignItems="center" justifyContent="space-between" spacing={2}
      sx={{ borderBottom: 1, borderColor: 'divider', py: 1.5 }}>
      <Typography sx={{ overflowWrap: 'anywhere', minWidth: 0 }}>{customer.name}</Typography>
      <Button component={Link} to={staffKitsPath(customer.id)} startIcon={<EditRounded />} sx={{ flexShrink: 0 }}>Open kits</Button>
    </Stack>)}
  </Stack>
}

export function StaffExperienceKitsPage() {
  const auth = usePlatformAuth()
  const { customerId = '', code, id } = useParams<{ customerId: string; code: string; id: string }>()
  if (auth.isLoading) return <CircularProgress size={24} aria-label="Checking staff access" />
  if (!auth.session?.authenticated || !['PLATFORM_ADMIN', 'PLATFORM_OPERATOR'].includes(auth.session.role ?? '') || !auth.session.actorId) {
    return <Alert severity="warning">Staff access is required to set up a customer kit.</Alert>
  }
  return <StaffCustomerKits key={`${auth.session.actorId}:${customerId}`} actorId={auth.session.actorId} customerId={customerId} code={code} id={id} />
}

function StaffCustomerKits({ actorId, customerId, code, id }: { actorId: string; customerId: string; code?: string; id?: string }) {
  const { session } = usePlatformAuth()
  const isAdmin = session?.role === 'PLATFORM_ADMIN'
  const customersPath = isAdmin ? '/customers' : '/customer-workspaces'
  const navigate = useNavigate()
  const developerEntry = isDeveloperEntry(useLocation().search) && isDeveloperKit(code)
  const queryClient = useQueryClient()
  const { palette } = useTheme()
  const [accessOpen, setAccessOpen] = useState(false)
  const [accessRevision, setAccessRevision] = useState(0)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const customers = useQuery({ queryKey: ['staff-kit-customers', actorId], queryFn: fetchPlatformCustomers, retry: false })
  const customer = customers.data?.find(item => item.id === customerId)
  const basePath = staffKitsPath(customerId)
  const transport = useMemo<ExperienceKitSetupTransport>(() => ({
    prepare: () => prepareStaffExperienceKits(customerId),
    fetchCatalogue: signal => fetchStaffExperienceKitCatalogue(customerId, signal),
    create: body => createStaffExperienceKit(customerId, body),
    queryScope: staffExperienceKitKeys.customer(actorId, customerId),
  }), [actorId, customerId])
  const accessPrepared = () => {
    if (!mounted.current) return
    setAccessRevision(value => value + 1)
    void queryClient.invalidateQueries({ queryKey: staffExperienceKitKeys.catalogue(actorId, customerId) })
  }
  if (customers.isPending) return <CircularProgress size={24} aria-label="Loading customer workspace" />
  if (customers.isError) return <Alert severity="error" action={<Button onClick={() => void customers.refetch()}>Retry</Button>}>{errorText(customers.error)}</Alert>
  if (!customer) return <Alert severity="error">The selected customer workspace is unavailable.<Button component={Link} to={customersPath}>Customer workspaces</Button></Alert>
  return <Stack spacing={3} sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
    <Box>
      <Button component={Link} to={customersPath} startIcon={<ArrowBackRounded />}>Customer workspaces</Button>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'center' }}>
        <Box sx={{ minWidth: 0 }}><Typography variant="h5" component="h1">Kits for {customer.name}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{customer.slug} / {customer.id}</Typography></Box>
        {isAdmin && <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Button variant="outlined" startIcon={<PersonAddAltRounded />} onClick={() => setAccessOpen(true)}>Give customer access</Button>
          <Button component={Link} to="/users" startIcon={<PeopleAltRounded />}>Customer accounts</Button>
        </Stack>}
      </Stack>
      <TextField select fullWidth size="small" label="Customer / company" value={customerId} sx={{ mt: 2, maxWidth: 520 }}
        onChange={event => navigate(staffKitsPath(event.target.value))}>
        {customers.data.map(item => <MenuItem key={item.id} value={item.id}>{item.name} ({item.slug})</MenuItem>)}
      </TextField>
    </Box>
    <Alert severity="info">This kit belongs to {customer.name}. An active customer administrator account is required before setup can be prepared.</Alert>
    {isAdmin && <CustomerAccessDialog customerId={customerId} customerName={customer.name} open={accessOpen}
      onClose={() => setAccessOpen(false)} onPrepared={accessPrepared} />}
    {code ? <Box className="ek-page ek-console-page" data-kit-theme={palette.mode}>
      <ExperienceKitSetup customerId={customerId} actorId={actorId} code={code} transport={transport} basePath={basePath} preparationRevision={accessRevision} entryPoint={developerEntry ? 'DEVELOPER' : undefined} />
    </Box> : id ? <StaffKitOverview key={id} customerId={customerId} actorId={actorId} id={id} />
      : <StaffKitsList key={accessRevision} customerId={customerId} actorId={actorId} />}
  </Stack>
}

function StaffKitsList({ customerId, actorId }: { customerId: string; actorId: string }) {
  const queryClient = useQueryClient()
  const kits = useQuery({ queryKey: staffExperienceKitKeys.list(actorId, customerId),
    queryFn: ({ signal }) => fetchStaffExperienceKits(customerId, signal), retry: false })
  const catalogue = useQuery({ queryKey: staffExperienceKitKeys.catalogue(actorId, customerId),
    queryFn: ({ signal }) => fetchStaffExperienceKitCatalogue(customerId, signal), retry: false })
  const prepare = useMutation({ mutationFn: () => prepareStaffExperienceKits(customerId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: staffExperienceKitKeys.catalogue(actorId, customerId) }) })
  return <Stack spacing={2}>
    <Typography variant="h6" component="h2">Customer kits</Typography>
    {kits.isPending && <CircularProgress size={24} aria-label="Loading customer kits" />}
    {kits.isError && <Alert severity="error" action={<Button onClick={() => void kits.refetch()}>Retry</Button>}>{errorText(kits.error)}</Alert>}
    {kits.isSuccess && !kits.data.length && <Typography color="text.secondary">No kits have been created in this workspace.</Typography>}
    {kits.data?.map(kit => <Box key={kit.id} sx={{ py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="space-between" alignItems={{ sm: 'center' }}>
        <Box sx={{ minWidth: 0 }}><Typography fontWeight={700}>{kit.name}</Typography>
          <Typography variant="body2" color="text.secondary">{kitDisplayName(kit.kitCode)}</Typography></Box>
        <Button component={Link} to={`${staffKitsPath(customerId)}/${encodeURIComponent(kit.id)}`} startIcon={<EditRounded />}>Open kit setup</Button>
      </Stack>
    </Box>)}
    <Divider />
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="space-between" alignItems={{ sm: 'center' }}>
      <Typography variant="h6" component="h2">Set up a kit</Typography>
      <Button startIcon={<RefreshRounded />} disabled={prepare.isPending} onClick={() => prepare.mutate()}>
        {prepare.isPending ? 'Preparing templates...' : 'Prepare available kits'}
      </Button>
    </Stack>
    {prepare.isError && <Alert severity="error">{errorText(prepare.error)}<Button component={Link} to="/users">Customer accounts</Button></Alert>}
    {catalogue.isPending && <CircularProgress size={24} aria-label="Loading kit catalogue" />}
    {catalogue.isError && <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{errorText(catalogue.error)}</Alert>}
    {catalogue.isSuccess && !catalogue.data.length && <Alert severity="info">No kit templates are available for this customer. Prepare available kits after a customer administrator account is active.</Alert>}
    {catalogue.data?.map(({ definition }) => <Box key={definition.code} sx={{ py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="space-between" alignItems={{ sm: 'center' }}>
        <Box sx={{ minWidth: 0 }}><Typography fontWeight={700}>{definition.name}</Typography>
          <Typography variant="body2" color="text.secondary">{definition.summary}</Typography></Box>
        <Button component={Link} to={`${staffKitsPath(customerId)}/new/${encodeURIComponent(definition.code)}`} startIcon={<AddRounded />}>Set up kit</Button>
      </Stack>
    </Box>)}
  </Stack>
}

function StaffKitOverview({ customerId, actorId, id }: { customerId: string; actorId: string; id: string }) {
  const kit = useQuery({ queryKey: staffExperienceKitKeys.detail(actorId, customerId, id),
    queryFn: ({ signal }) => fetchStaffExperienceKit(customerId, id, signal), retry: false })
  if (kit.isPending) return <CircularProgress size={24} aria-label="Loading kit configuration" />
  if (kit.isError) return <Alert severity="error" action={<Button onClick={() => void kit.refetch()}>Retry</Button>}>{errorText(kit.error)}</Alert>
  if (kit.data.id !== id || [kit.data.workspace, kit.data.embeddedWorkspace].some(app => app && app.customerId !== customerId)) {
    return <Alert severity="error">The returned kit does not match the selected customer workspace.</Alert>
  }
  return <StaffKitConfiguration key={`${id}:${kit.data.revision}`} customerId={customerId} actorId={actorId} kit={kit.data} />
}

function StaffKitConfiguration({ customerId, actorId, kit }: { customerId: string; actorId: string; kit: ExperienceKitSummary }) {
  const queryClient = useQueryClient()
  const { palette } = useTheme()
  const [form, setForm] = useState(() => kitFormFromSummary(kit))
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const catalogue = useQuery({ queryKey: staffExperienceKitKeys.catalogue(actorId, customerId),
    queryFn: ({ signal }) => fetchStaffExperienceKitCatalogue(customerId, signal), retry: false })
  const entry = catalogue.data?.find(item => item.definition.code === kit.kitCode)
  const supportExperience = kit.kitCode === 'ai-support-experience'
  const missing = !kit.workspace || (kit.deliveryMode === 'both' && !kit.embeddedWorkspace)
  const update = useMutation({ mutationFn: (body: ReturnType<typeof kitUpdateRequest>) => updateStaffExperienceKit(customerId, kit.id, body) })
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (missing || update.isPending || !catalogue.isSuccess) return
    const errors = kitExperienceErrors(form, supportExperience ? entry?.ticketProviders ?? [] : undefined)
    if (errors.length) { setError(errors[0]); return }
    setError(null); setSaved(false)
    try {
      await update.mutateAsync(kitUpdateRequest(form, kit.revision, kit.workspace!.rowVersion, kit.embeddedWorkspace?.rowVersion ?? null))
      if (!mounted.current) return
      setSaved(true)
      await queryClient.invalidateQueries({ queryKey: staffExperienceKitKeys.customer(actorId, customerId) })
      await queryClient.invalidateQueries({ queryKey: ['ai-workspace-installations', customerId] })
    } catch (failure) { if (mounted.current) setError(errorText(failure)) }
  }
  return <Stack spacing={2}>
    <Button component={Link} to={staffKitsPath(customerId)} startIcon={<ArrowBackRounded />} sx={{ alignSelf: 'flex-start' }}>Back to customer kits</Button>
    <Box><Typography variant="h6" component="h2">{kit.name}</Typography><Typography color="text.secondary">{kitDisplayName(kit.kitCode)}</Typography></Box>
    <Alert severity="info">Kit configuration is saved. Connection, verification and live status are managed in the product editors.</Alert>
    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
      <Button component={Link} to={`/overview?${new URLSearchParams({ deploymentId: kit.deploymentId })}`} startIcon={<EditRounded />}>Open Brain editor</Button>
      <Button component={Link} to={`/ai-workspaces?${new URLSearchParams({ customerId,
        ...(kit.installationId ? { installationId: kit.installationId } : {}) })}`} startIcon={<EditRounded />}>Open Engage editor</Button>
      {kit.embeddedInstallationId && <Button component={Link} to={`/ai-workspaces?${new URLSearchParams({ customerId,
        installationId: kit.embeddedInstallationId })}`} startIcon={<EditRounded />}>Open website embed editor</Button>}
    </Stack>
    <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>Brain: {kit.deploymentId} / Engage: {kit.installationId ?? 'Missing'}{kit.embeddedInstallationId ? ` / Website embed: ${kit.embeddedInstallationId}` : ''}</Typography>
    {missing && <Alert severity="warning">An Engage installation is missing. Review it in the existing product editor before changing this kit.</Alert>}
    {catalogue.isError && <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{errorText(catalogue.error)}</Alert>}
    {saved && <Alert severity="success">Kit settings saved.</Alert>}
    {error && <Alert severity="error">{error}</Alert>}
    {!missing && <Box component="form" noValidate onSubmit={event => void submit(event)} className="ek-page ek-console-page" data-kit-theme={palette.mode} sx={{ maxWidth: 680 }}>
      <KitExperienceFields value={form} onChange={value => { setForm(value); setSaved(false) }} disabled={update.isPending}
        includeName supportExperience={supportExperience} ticketProviders={entry?.ticketProviders ?? []} />
      <Button type="submit" variant="contained" startIcon={<SaveRounded />} disabled={update.isPending || catalogue.isPending || catalogue.isError} sx={{ mt: 2 }}>
        {update.isPending ? 'Saving...' : 'Save kit settings'}
      </Button>
    </Box>}
  </Stack>
}
