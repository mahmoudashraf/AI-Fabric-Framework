import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { createEscalation } from '../api/escalations'
import { getPartnerStore } from '../api/stores'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
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
          <Tab label="Support" />
        </Tabs>
      </Paper>
      {tab === 0 ? <OverviewTab surfaces={store.enabledSurfaces} /> : null}
      {tab === 1 ? <SetupTab /> : null}
      {tab === 2 ? <VerificationTab /> : null}
      {tab === 3 ? <EvidenceTab /> : null}
      {tab === 4 ? (
        <Paper sx={{ p: 2 }}>
          <Typography variant="h3">Support center</Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Escalations for this store are handled in the support queue with partner-visible replies only.
          </Typography>
          <Button sx={{ mt: 2 }} onClick={() => navigate('/support')}>Open support</Button>
        </Paper>
      ) : null}
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

function VerificationTab() {
  return <Checklist title="Verification pack" items={['Free exposes AI search only', 'Starter surfaces are read-only', 'No Starter order lookup appears', 'Answers use store content', 'Evidence bundle excludes internals']} />
}

function EvidenceTab() {
  return <Checklist title="Evidence bundle shell" items={['Storefront URL', 'Surface screenshot', 'Question and answer pair', 'Verification result', 'Merchant-safe summary']} />
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
    defaultValues: { title: '', severity: 'MEDIUM', description: '', reproductionSteps: '', expectedBehavior: '', actualBehavior: '', impact: '', nextAction: '' },
  })
  const mutation = useMutation({
    mutationFn: (values: EscalationForm) => createEscalation(api, storeId, values),
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
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={mutation.isPending}>Cancel</Button>
        <Button type="submit" form="escalation-form" variant="contained" disabled={mutation.isPending}>Create escalation</Button>
      </DialogActions>
    </Dialog>
  )
}
