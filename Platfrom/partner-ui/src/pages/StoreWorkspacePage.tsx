import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import FolderZipOutlinedIcon from '@mui/icons-material/FolderZipOutlined'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { createStoreEvidenceBundle, listStoreEvidenceBundles } from '../api/evidence'
import { createEscalation } from '../api/escalations'
import { createStoreNote, listStoreNotes } from '../api/notes'
import type { PartnerEvidenceBundle, PartnerVerificationRun } from '../api/schemas'
import { getPartnerStore } from '../api/stores'
import { completeVerificationStep, getStoreVerificationPack, listStoreVerificationRuns, runStoreVerification } from '../api/verification'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { DataTable, type DataColumn } from '../components/DataTable'
import { PageHeader } from '../components/PageHeader'
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

export function StoreWorkspacePage() {
  const { storeId = '' } = useParams()
  const navigate = useNavigate()
  const { api } = useSupabaseAuth()
  const [tab, setTab] = useState(0)
  const [dialogOpen, setDialogOpen] = useState(false)
  const storeQuery = useQuery({ queryKey: ['store', storeId], queryFn: () => getPartnerStore(api, storeId), enabled: Boolean(storeId) })

  if (storeQuery.isLoading) {
    return <LinearProgress />
  }
  if (storeQuery.isError || !storeQuery.data) {
    return <Alert severity="error">This store is not assigned to your partner workspace.</Alert>
  }

  const store = storeQuery.data
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
      <Paper sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={(_event, value) => setTab(value)} variant="scrollable" scrollButtons="auto">
          <Tab label="Overview" />
          <Tab label="Setup checklist" />
          <Tab label="Verification" />
          <Tab label="Evidence" />
          <Tab label="Escalations" />
          <Tab label="Notes" />
        </Tabs>
      </Paper>
      {tab === 0 ? <OverviewTab surfaces={store.enabledSurfaces} /> : null}
      {tab === 1 ? <SetupTab /> : null}
      {tab === 2 ? <VerificationTab storeId={store.id} /> : null}
      {tab === 3 ? <EvidenceTab storeId={store.id} /> : null}
      {tab === 4 ? (
        <Paper sx={{ p: 2 }}>
          <Typography variant="h3">Support center</Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Escalations for this store are handled in the support queue with partner-visible replies only.
          </Typography>
          <Button sx={{ mt: 2 }} onClick={() => navigate('/support')}>Open support</Button>
        </Paper>
      ) : null}
      {tab === 5 ? <NotesTab storeId={store.id} /> : null}
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

function OverviewTab({ surfaces }: { surfaces: string[] }) {
  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h3">Store configured surfaces</Typography>
      <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1.5 }}>
        {surfaces.map((surface) => (
          <Box key={surface} sx={{ px: 1, py: 0.5, border: 1, borderColor: 'divider', borderRadius: 2, fontSize: '0.8125rem' }}>
            {titleize(surface)}
          </Box>
        ))}
      </Stack>
    </Paper>
  )
}

function SetupTab() {
  return <Checklist title="Setup checklist" items={['Confirm Companion install', 'Enable app embed', 'Run Knowledge Sync', 'Confirm store configured surfaces', 'Capture storefront screenshots']} />
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
  const columns: DataColumn<PartnerVerificationRun>[] = [
    { key: 'pack', header: 'Pack', render: (row) => <Typography>{row.packName}</Typography> },
    { key: 'status', header: 'Status', render: (row) => <StatusChip status={row.status} /> },
    { key: 'steps', header: 'Steps', render: (row) => <Typography>{row.passedSteps}/{row.totalSteps} passed</Typography> },
    { key: 'started', header: 'Started', render: (row) => <Typography color="text.secondary">{formatDateTime(row.startedAt)}</Typography> },
  ]
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
      <DataTable columns={columns} rows={runsQuery.data ?? []} getRowKey={(row) => row.id} loading={runsQuery.isLoading} />
    </Stack>
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
