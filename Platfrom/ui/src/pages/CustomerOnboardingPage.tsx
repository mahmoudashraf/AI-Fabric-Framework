import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, CircularProgress, Stack, TextField, Typography } from '@mui/material'
import ArrowBack from '@mui/icons-material/ArrowBack'
import HubOutlined from '@mui/icons-material/HubOutlined'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { productStartPath, selectedProduct } from '../auth/customerAuthReturn'
import { SupabaseIdentityForm } from '../auth/SupabaseIdentityForm'
import { CustomerConsoleTheme } from '../theme/CustomerConsoleTheme'
import { createCustomerWorkspace, enrollPublicCustomerTemplates, fetchCustomerDeploymentPortfolio, fetchCustomerOnboarding, fetchCustomerSelfServiceCatalogue } from '../api/platformApi'

export function CustomerEntryLayout({ children }: { children: ReactNode }) {
  return <CustomerConsoleTheme><Box sx={{ minHeight: '100vh', bgcolor: 'background.paper', px: { xs: 2.5, md: 7 }, py: 3 }}>
    <Button component={Link} to="/" startIcon={<ArrowBack />} sx={{ mb: 5 }}>Back to products</Button>
    <Stack spacing={3} sx={{ maxWidth: 470, mx: 'auto', pb: 6 }}>
      <HubOutlined color="primary" sx={{ fontSize: 34 }} />{children}
    </Stack>
  </Box></CustomerConsoleTheme>
}

export function CustomerSignInPage({ register = false }: { register?: boolean }) {
  const auth = usePlatformAuth()
  const location = useLocation()
  const product = selectedProduct(location.search)
  return <CustomerEntryLayout>
    <Box><Typography variant="h4" component="h1">{register ? 'Create your LoomAI account' : 'Welcome to LoomAI'}</Typography>
      <Typography color="text.secondary" sx={{ mt: 1 }}>{product ? `Continue with LoomAI ${product === 'platform' ? 'Platform' : product === 'engage' ? 'Engage' : 'Desk'}.` : 'One account for Console and LoomAI Desk.'}</Typography></Box>
    {auth.customerConfig?.enabled ? <SupabaseIdentityForm key={register ? 'register' : 'login'} allowRegistration initialMode={register ? 'register' : 'login'} />
      : <Alert severity="warning">Customer sign-in is currently unavailable. Please try again later.</Alert>}
    {auth.error ? <Alert severity="error">{auth.error.message}</Alert> : null}
    <Button component={Link} to="/staff/sign-in" sx={{ alignSelf: 'flex-start' }}>Staff access</Button>
  </CustomerEntryLayout>
}

export function CustomerOnboardingPage() {
  const auth = usePlatformAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [workspaceName, setWorkspaceName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const state = useQuery({ queryKey: ['customer-onboarding', auth.supabaseSession?.user.id], queryFn: fetchCustomerOnboarding,
    enabled: Boolean(auth.supabaseSession), retry: false })
  const create = useMutation({ mutationFn: () => createCustomerWorkspace({ workspaceName: workspaceName.trim(), displayName: displayName.trim() }),
    onSuccess: async () => { await auth.refreshSession(); navigate(productStartPath(location.search), { replace: true }) } })
  useEffect(() => {
    if (state.data?.status === 'READY') {
      void auth.refreshSession().then(() => navigate(productStartPath(location.search), { replace: true }))
    }
  }, [state.data?.status, location.search])
  const submit = (event: FormEvent) => { event.preventDefault(); create.mutate() }
  return <CustomerEntryLayout>
    <Box><Typography variant="h4" component="h1">Create your workspace</Typography>
      <Typography color="text.secondary" sx={{ mt: 1 }}>Your products, team and configuration in one place.</Typography></Box>
    {state.isPending ? <CircularProgress size={24} /> : state.isError ? <Alert severity="error">{state.error.message}</Alert>
      : state.data?.status === 'LINK_REQUIRED' ? <Stack spacing={2}><Alert severity="info">This identity matches an existing Console account. Connect that account using its existing sign-in, or ask your workspace administrator for help. It will not be linked automatically.</Alert>
        <Button component={Link} to="/staff/sign-in?link=customer">Connect existing account</Button></Stack>
      : state.data?.status === 'REGISTRATION_CLOSED' ? <Alert severity="info">New workspaces are not available right now. Contact your workspace administrator for access.</Alert>
      : state.data?.status === 'CREATE_WORKSPACE' ? <Stack component="form" spacing={2.5} onSubmit={submit}>
        <TextField label="Your name" required autoComplete="name" value={displayName} onChange={e => setDisplayName(e.target.value)} slotProps={{ htmlInput: { maxLength: 120 } }} />
        <TextField label="Workspace name" required autoComplete="organization" value={workspaceName} onChange={e => setWorkspaceName(e.target.value)} slotProps={{ htmlInput: { maxLength: 120 } }} />
        <Typography variant="body2" color="text.secondary">Creating a workspace does not deploy services or incur AI usage. You choose what to configure next.</Typography>
        {create.isError ? <Alert severity="error">{create.error.message}</Alert> : null}
        <Button type="submit" variant="contained" disabled={create.isPending || !workspaceName.trim() || !displayName.trim()}>{create.isPending ? 'Creating workspace...' : 'Create workspace'}</Button>
      </Stack> : null}
    <Button onClick={() => { void auth.signOut() }}>Sign out</Button>
  </CustomerEntryLayout>
}

export function CustomerProductStartPage() {
  const auth = usePlatformAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const product = selectedProduct(location.search) ?? 'platform'
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const deployments = useQuery({ queryKey: ['customer-deployment-portfolio', auth.session?.customerId], queryFn: fetchCustomerDeploymentPortfolio })
  const start = async () => {
    setBusy(true); setError(null)
    try {
      if (product === 'platform' || !deployments.data?.length) {
        await enrollPublicCustomerTemplates()
        const catalogue = await fetchCustomerSelfServiceCatalogue()
        if (!catalogue.length) throw new Error('No self-service template is available for this workspace yet. Your account is ready; contact LoomAI to enable an appropriate template. No deployment has been created.')
        await queryClient.invalidateQueries({ queryKey: ['customer-self-service-catalogue'] })
        navigate('/deployments?view=setup&step=template')
      } else navigate(product === 'engage' ? '/ai-workspaces?view=manage' : '/human-support')
    } catch (ex) { setError(ex instanceof Error ? ex.message : 'Setup could not be opened.') }
    finally { setBusy(false) }
  }
  const needsDeployment = product !== 'platform' && deployments.data?.length === 0
  return <Stack spacing={3} sx={{ maxWidth: 650 }}>
    <Box><Typography variant="h4" component="h1">{product === 'platform' ? 'Set up your AI deployment' : product === 'engage' ? 'Create your Engage workspace' : 'Connect LoomAI Desk'}</Typography>
      <Typography color="text.secondary" sx={{ mt: 1 }}>Workspace: {auth.session?.customerName}</Typography></Box>
    <Typography>{product === 'platform' ? 'Choose a template, connect your systems, then test your configuration before going live.'
      : product === 'engage' ? 'Connect a deployment, choose your branding, and publish a standalone or embedded AI experience.'
        : 'Enable Desk for a deployment, then assign agents to continue conversations and review actions.'}</Typography>
    {deployments.isPending ? <CircularProgress size={24} /> : deployments.isError ? <Alert severity="error">{deployments.error.message}</Alert> : <>
      {needsDeployment ? <Alert severity="info">First create an AI deployment. Then return here to {product === 'engage' ? 'create your Engage app' : 'enable human support'}.</Alert> : null}
      {error ? <Alert severity="warning">{error}</Alert> : null}
      <Button variant="contained" onClick={() => { void start() }} disabled={busy}>{busy ? 'Opening setup...' : product === 'platform' || needsDeployment ? 'Choose a deployment template' : product === 'engage' ? 'Create Engage app' : 'Configure human support'}</Button>
    </>}
    <Button component={Link} to="/home">Open your console</Button>
    <Button component={Link} to="/products" startIcon={<ArrowBack />}>Back to products</Button>
  </Stack>
}
