import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Chip, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listPartnerStores } from '../api/stores'
import type { PartnerVerificationRun, PartnerVerificationStep } from '../api/schemas'
import { listVerificationPacks, listVerificationRuns, runStoreVerification } from '../api/verification'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { EmptyState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime } from '../utils/format'

export function VerificationPacksPage() {
  const { api } = useSupabaseAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const packsQuery = useQuery({ queryKey: ['verification-packs'], queryFn: () => listVerificationPacks(api) })
  const storesQuery = useQuery({ queryKey: ['stores'], queryFn: () => listPartnerStores(api) })
  const runsQuery = useQuery({ queryKey: ['verification-runs'], queryFn: () => listVerificationRuns(api) })
  const [storeId, setStoreId] = useState('')
  const [packId, setPackId] = useState('')
  const packs = packsQuery.data ?? []
  const stores = storesQuery.data ?? []
  const selectedPackId = packId || packs[0]?.id || ''
  const selectedStoreId = storeId || stores[0]?.id || ''
  const selectedPack = useMemo(() => packs.find((pack) => pack.id === selectedPackId), [packs, selectedPackId])
  const runMutation = useMutation({
    mutationFn: () => runStoreVerification(api, selectedStoreId, selectedPackId),
    onSuccess: async (run) => {
      await queryClient.invalidateQueries({ queryKey: ['verification-runs'] })
      await queryClient.invalidateQueries({ queryKey: ['evidence-bundles'] })
      navigate(`/verification/${encodeURIComponent(run.id)}`)
    },
  })

  return (
    <>
      <PageHeader title="Verification packs" subtitle="Run partner-safe store verification and preserve immutable evidence for launch and support." />
      {runMutation.isError ? <Alert severity="error" sx={{ mb: 2 }}>{runMutation.error instanceof Error ? runMutation.error.message : 'Verification run failed.'}</Alert> : null}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Stack spacing={2}>
          <Typography variant="h2">Run verification</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr auto' }, gap: 2, alignItems: 'center' }}>
            <TextField select label="Approved store" value={selectedStoreId} onChange={(event) => setStoreId(event.target.value)} disabled={storesQuery.isLoading || stores.length === 0}>
              {stores.map((store) => (
                <MenuItem key={store.id} value={store.id}>{store.shopDomain}</MenuItem>
              ))}
            </TextField>
            <TextField select label="Pack" value={selectedPackId} onChange={(event) => setPackId(event.target.value)} disabled={packsQuery.isLoading || packs.length === 0}>
              {packs.map((pack) => (
                <MenuItem key={pack.id} value={pack.id}>{pack.name}</MenuItem>
              ))}
            </TextField>
            <Button variant="contained" disabled={!selectedStoreId || !selectedPackId || runMutation.isPending} onClick={() => runMutation.mutate()}>
              Run pack
            </Button>
          </Box>
          {selectedPack ? (
            <Stack spacing={0.75}>
              <Typography variant="h3">{selectedPack.name}</Typography>
              <Typography color="text.secondary">{selectedPack.description}</Typography>
              <Stack component="ol" spacing={0.5} sx={{ pl: 3 }}>
                {selectedPack.steps.map((step) => (
                  <Typography component="li" key={step.stepId}>{step.label}</Typography>
                ))}
              </Stack>
            </Stack>
          ) : null}
        </Stack>
      </Paper>
      <VerificationRunHistory runs={runsQuery.data ?? []} loading={runsQuery.isLoading} onOpen={(runId) => navigate(`/verification/${encodeURIComponent(runId)}`)} />
    </>
  )
}

function VerificationRunHistory({ runs, loading, onOpen }: { runs: PartnerVerificationRun[]; loading: boolean; onOpen: (runId: string) => void }) {
  if (loading) {
    return <Paper sx={{ p: 2 }}><Typography color="text.secondary">Loading verification runs...</Typography></Paper>
  }
  if (runs.length === 0) {
    return <EmptyState icon={FactCheckOutlinedIcon} title="No verification runs yet" body="Run a pack on an approved store to create a report and evidence bundle." />
  }
  return (
    <Stack spacing={1.5}>
      {runs.map((run) => (
        <Accordion key={run.id}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} justifyContent="space-between" sx={{ width: '100%', pr: 1 }}>
              <Box>
                <Button onClick={(event) => { event.stopPropagation(); onOpen(run.id) }} sx={{ justifyContent: 'flex-start', p: 0, minWidth: 0 }}>{run.packName}</Button>
                <Typography variant="caption" color="text.secondary">{run.shopDomain ?? run.storeAssignmentId} · {formatDateTime(run.startedAt)}</Typography>
              </Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <StatusChip status={run.status} />
                <Typography>{run.passedSteps}/{run.totalSteps} passed</Typography>
              </Stack>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={1.25}>
              {run.steps.map((step) => <VerificationStepLine key={step.stepId} step={step} />)}
            </Stack>
          </AccordionDetails>
        </Accordion>
      ))}
    </Stack>
  )
}

function VerificationStepLine({ step }: { step: PartnerVerificationStep }) {
  return (
    <Box sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="space-between">
        <Box>
          <Typography fontWeight={800}>{step.label}</Typography>
          <Typography color="text.secondary">{step.partnerSafeMessage}</Typography>
        </Box>
        <StatusChip status={step.status} />
      </Stack>
      {step.evidence.length > 0 ? (
        <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1 }}>
          {step.evidence.map((item) => <Chip key={item} size="small" label={item} variant="outlined" />)}
        </Stack>
      ) : null}
    </Box>
  )
}
