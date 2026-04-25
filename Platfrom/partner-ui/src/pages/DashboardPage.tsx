import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import AddTaskOutlinedIcon from '@mui/icons-material/AddTaskOutlined'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import StorefrontOutlinedIcon from '@mui/icons-material/StorefrontOutlined'
import SupportAgentOutlinedIcon from '@mui/icons-material/SupportAgentOutlined'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listEscalations } from '../api/escalations'
import { completePartnerSignup } from '../api/session'
import type { PartnerEscalation, PartnerSession, PartnerStore } from '../api/schemas'
import { listPartnerStores } from '../api/stores'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { DataTable, type DataColumn } from '../components/DataTable'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { formatDate, firstName } from '../utils/format'

export function DashboardPage({ session }: { session: PartnerSession }) {
  if (session.signupRequired) {
    return <SignupPanel />
  }
  return <ProvisionedDashboard session={session} />
}

function SignupPanel() {
  const { api, user } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const [workspaceName, setWorkspaceName] = useState('')
  const mutation = useMutation({
    mutationFn: () => completePartnerSignup(api, workspaceName),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['partner-session'] })
    },
  })

  return (
    <>
      <PageHeader
        title={`Welcome, ${firstName(user?.user_metadata.full_name ?? user?.email)}`}
        subtitle="Create your empty partner workspace. Client stores appear only after merchant approval or assignment."
      />
      <Paper sx={{ p: 3, maxWidth: 640 }}>
        <Stack spacing={2}>
          <Typography variant="h2">Set up your workspace</Typography>
          <Typography color="text.secondary">
            This creates the partner account and makes you the workspace admin. It does not grant access to any store.
          </Typography>
          {mutation.isError ? <Alert severity="error">{mutation.error instanceof Error ? mutation.error.message : 'Workspace setup failed.'}</Alert> : null}
          <TextField
            label="Workspace name"
            value={workspaceName}
            onChange={(event) => setWorkspaceName(event.target.value)}
            placeholder="Agency or implementation team name"
          />
          <Stack direction="row" justifyContent="flex-end">
            <Button variant="contained" disabled={workspaceName.trim().length < 2 || mutation.isPending} onClick={() => mutation.mutate()}>
              Create workspace
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </>
  )
}

function ProvisionedDashboard({ session }: { session: PartnerSession }) {
  const navigate = useNavigate()
  const { api } = useSupabaseAuth()
  const storesQuery = useQuery({ queryKey: ['stores'], queryFn: () => listPartnerStores(api) })
  const escalationQuery = useQuery({ queryKey: ['escalations'], queryFn: () => listEscalations(api) })
  const stores = storesQuery.data ?? []
  const escalations = escalationQuery.data ?? []
  const openEscalations = escalations.filter((item) => !['RESOLVED', 'CLOSED'].includes(item.status))

  if (stores.length === 0) {
    return <EmptyWorkspace session={session} />
  }

  const attentionRows = stores.filter((store) => store.status !== 'READY')
  const columns: DataColumn<PartnerStore>[] = [
    {
      key: 'store',
      header: 'Store',
      render: (store) => (
        <Button onClick={() => navigate(`/stores/${encodeURIComponent(store.id)}`)} sx={{ justifyContent: 'flex-start' }}>
          {store.merchantName}
        </Button>
      ),
    },
    { key: 'status', header: 'Status', render: (store) => <StatusChip status={store.status} /> },
    { key: 'blocker', header: 'Blocker', render: (store) => <Typography>{store.topBlocker}</Typography> },
    { key: 'due', header: 'Last activity', render: (store) => <Typography color="text.secondary">{formatDate(store.lastActivityAt)}</Typography> },
    { key: 'open', header: '', align: 'right', render: (store) => <Button onClick={() => navigate(`/stores/${encodeURIComponent(store.id)}`)}>Open</Button> },
  ]

  const escalationColumns: DataColumn<PartnerEscalation>[] = [
    { key: 'title', header: 'Escalation', render: (row) => <Button onClick={() => navigate(`/support/${row.id}`)}>{row.title}</Button> },
    { key: 'store', header: 'Store', render: (row) => <Typography color="text.secondary">{row.shopDomain ?? 'Store scoped'}</Typography> },
    { key: 'status', header: 'Status', render: (row) => <StatusChip status={row.status} /> },
  ]

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Implementation health across approved client stores." />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(4, 1fr)' }, gap: 2, mb: 3 }}>
        <KpiTile label="Stores assigned" value={String(stores.length)} />
        <KpiTile label="Open escalations" value={String(openEscalations.length)} />
        <KpiTile label="Starter surfaces" value={String(stores.reduce((count, store) => count + store.enabledSurfaces.length, 0))} />
        <KpiTile label="Pending merchant approvals" value="0" />
      </Box>
      <Stack spacing={3}>
        <Box>
          <Typography variant="h2" sx={{ mb: 1.5 }}>Needs your attention</Typography>
          <DataTable columns={columns} rows={attentionRows} getRowKey={(row) => row.id} loading={storesQuery.isLoading} empty={<Typography color="text.secondary">Nothing needs action right now.</Typography>} />
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '7fr 5fr' }, gap: 3 }}>
          <Box>
            <Typography variant="h2" sx={{ mb: 1.5 }}>Open escalations</Typography>
            <DataTable columns={escalationColumns} rows={openEscalations} getRowKey={(row) => row.id} loading={escalationQuery.isLoading} />
          </Box>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h2">Quick actions</Typography>
            <Stack spacing={1.25} sx={{ mt: 2 }}>
              <Button variant="outlined" startIcon={<AddTaskOutlinedIcon />} onClick={() => navigate('/implementations/new')}>New implementation</Button>
              <Button variant="outlined" startIcon={<FactCheckOutlinedIcon />} onClick={() => navigate('/verification')}>Run verification pack</Button>
              <Button variant="outlined" startIcon={<SupportAgentOutlinedIcon />} onClick={() => navigate('/support')}>Open escalation center</Button>
              <Button variant="outlined" startIcon={<AutoAwesomeOutlinedIcon />} onClick={() => navigate('/catalog')}>Browse catalog</Button>
            </Stack>
          </Paper>
        </Box>
      </Stack>
    </>
  )
}

function EmptyWorkspace({ session }: { session: PartnerSession }) {
  const navigate = useNavigate()
  return (
    <>
      <PageHeader
        title={`Welcome to your workspace, ${firstName(session.member?.displayName ?? session.member?.email)}`}
        subtitle="You do not have any client stores yet. Start from the catalog, templates, or a merchant-approved implementation request."
      />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '8fr 4fr' }, gap: 3 }}>
        <Stack spacing={1.5}>
          <ActionRow icon={<AutoAwesomeOutlinedIcon />} index="1" title="Browse the intelligence catalog" body="Review Free and Starter-safe surfaces before proposing work." action="Open catalog" onClick={() => navigate('/catalog')} />
          <ActionRow icon={<FactCheckOutlinedIcon />} index="2" title="Review verification packs" body="Use product-safe checks before launch and when gathering evidence." action="Open verification" onClick={() => navigate('/verification')} />
          <ActionRow icon={<AddTaskOutlinedIcon />} index="3" title="Start a client implementation request" body="Generate a merchant approval link for a scoped client store assignment." action="New implementation" contained onClick={() => navigate('/implementations/new')} />
        </Stack>
        <Paper sx={{ p: 2 }}>
          <Typography variant="h2">Resources</Typography>
          <Stack spacing={1.25} sx={{ mt: 2 }}>
            <Button onClick={() => navigate('/docs')} sx={{ justifyContent: 'flex-start' }}>Implementation guide</Button>
            <Button onClick={() => navigate('/verification')} sx={{ justifyContent: 'flex-start' }}>Verification pack reference</Button>
            <Typography variant="caption" color="text.secondary">Vertical playbooks</Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              {['Fashion', 'Electronics', 'Health/Beauty', 'Home'].map((label) => (
                <Box key={label} sx={{ px: 1, py: 0.5, border: 1, borderColor: 'divider', borderRadius: 2, fontSize: '0.75rem' }}>
                  {label}
                </Box>
              ))}
            </Stack>
          </Stack>
        </Paper>
      </Box>
      <Accordion sx={{ mt: 3 }}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography>Pending approvals (0)</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Typography color="text.secondary">Merchant approvals created from implementation requests will appear here after the backend exposes list history.</Typography>
        </AccordionDetails>
      </Accordion>
    </>
  )
}

function KpiTile({ label, value }: { label: string; value: string }) {
  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="h1" sx={{ mt: 0.75 }}>{value}</Typography>
    </Paper>
  )
}

function ActionRow({
  icon,
  index,
  title,
  body,
  action,
  contained,
  onClick,
}: {
  icon: ReactNode
  index: string
  title: string
  body: string
  action: string
  contained?: boolean
  onClick: () => void
}) {
  return (
    <Paper sx={{ p: 2 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }} justifyContent="space-between">
        <Stack direction="row" spacing={1.5} alignItems="flex-start">
          <Box sx={{ width: 32, height: 32, borderRadius: 2, bgcolor: 'action.hover', display: 'grid', placeItems: 'center', color: 'primary.main' }}>{icon}</Box>
          <Box>
            <Typography variant="caption" color="text.secondary">Step {index}</Typography>
            <Typography variant="h3">{title}</Typography>
            <Typography color="text.secondary" sx={{ mt: 0.5 }}>{body}</Typography>
          </Box>
        </Stack>
        <Button variant={contained ? 'contained' : 'outlined'} onClick={onClick}>{action}</Button>
      </Stack>
      <Divider sx={{ display: 'none' }} />
    </Paper>
  )
}
