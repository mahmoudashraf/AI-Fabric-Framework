import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, Drawer, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import AddRounded from '@mui/icons-material/AddRounded'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckRounded from '@mui/icons-material/CheckRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import TuneRounded from '@mui/icons-material/TuneRounded'
import FormatListBulletedRounded from '@mui/icons-material/FormatListBulletedRounded'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { changeSupportConsent, changeSupportStatus, getSupportCase, listSupportCases, startSupportConversation,
  updateSupportResources, type SupportCase, type SupportDelivery, type SupportResource } from '../api/supportApi'
import { SupportResourcePicker, useSupportResources } from '../components/customer/SupportResourcePicker'
import { useSupportStaffAccess } from '../components/customer/useSupportStaffAccess'
import { useCustomerSupport } from '../components/customer/CustomerSupportContext'
import { SupportConsent } from '../components/customer/SupportRequestForm'
import { SupportTeam } from '../components/customer/SupportTeam'

const deliveryLabels: Record<SupportDelivery, string> = {
  PENDING: 'Request saved · conversation not started', UNKNOWN: 'Request saved · delivery not yet confirmed',
  USER_MESSAGE_PERSISTED: 'Message saved · AI response pending', COMPLETED: 'Conversation started', FAILED: 'Request saved · AI turn failed',
}
const date = (value: string) => new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
const errorText = (error: unknown) => error instanceof Error ? error.message : 'This operation could not be completed.'

export function CustomerSupportPage({ view = 'assistant' }: { view?: 'assistant' | 'requests' }) {
  const auth = usePlatformAuth()
  if (!isCustomerSession(auth.session)) return <Alert severity="error">Sign in to your customer account to request support.</Alert>
  return <CustomerSupport view={view} />
}

function CustomerSupport({ view }: { view: 'assistant' | 'requests' }) {
  const support = useCustomerSupport()
  const { caseId } = useParams()
  const location = useLocation()
  const staffAccess = useSupportStaffAccess(true, 0, 1)
  useEffect(() => {
    if (caseId) { support.selectCase(caseId); support.setMinimized(false) }
  }, [caseId, support.selectCase])
  useEffect(() => {
    if (new URLSearchParams(location.search).get('new') === '1') support.setRequestOpen(true)
  }, [location.search, support.setRequestOpen])
  const selected = caseId || support.caseId
  return <Stack spacing={2.5}>
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }} justifyContent="space-between">
      <Box><Typography variant="overline" color="secondary.main">LoomAI support</Typography>
        <Typography variant="h4">{view === 'requests' ? 'My requests' : 'Integration support'}</Typography>
        <Typography color="text.secondary" variant="body2" sx={{ mt: 0.5 }}>A little guidance. A clear next step.</Typography></Box>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        {staffAccess.isSuccess && !staffAccess.isFetching && <Button component={Link} to="/support/team-intake" size="small">Team intake</Button>}
        <Button component={Link} to={view === 'requests' ? '/support' : '/support/requests'} variant="outlined"
          onClick={() => { if (view !== 'requests') support.setMinimized(true) }}
          startIcon={view === 'requests' ? <ArrowBackRounded /> : <FormatListBulletedRounded />}>{view === 'requests' ? 'Back to support' : 'My requests'}</Button>
        <Button variant="contained" startIcon={<AddRounded />} disabled={support.saving} onClick={() => support.setRequestOpen(true)}>Request integration help</Button>
      </Stack>
    </Stack>
    <SupportTeam />
    {view === 'requests' ? <SupportRequestList /> : selected ? <SupportCaseDetails key={selected} caseId={selected} /> : null}
  </Stack>
}

function SupportRequestList() {
  const support = useCustomerSupport()
  const [page, setPage] = useState(0)
  const requests = useQuery({ queryKey: ['support-cases', support.customerId, support.actorId, page], queryFn: () => listSupportCases(page), retry: false })
  return <Stack spacing={1.5}>
    <Stack direction="row" alignItems="center" justifyContent="space-between"><Typography variant="h6">Your conversations and requests</Typography>
      <Tooltip title="Refresh requests"><IconButton aria-label="Refresh support requests" onClick={() => { void requests.refetch() }}><RefreshRounded fontSize="small" /></IconButton></Tooltip></Stack>
    {requests.isPending && <CircularProgress size={24} />}
    {requests.isError && <Alert severity="error">{errorText(requests.error)}</Alert>}
    {requests.data?.length === 0 && <Box sx={{ py: 5 }}><Typography color="text.secondary">No requests yet.</Typography></Box>}
    <Box>{requests.data?.map(request => <Box key={request.id} component={Link} to={`/support/${encodeURIComponent(request.id)}`}
      sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 2.25, borderBottom: 1, borderColor: 'divider', color: 'inherit', textDecoration: 'none', '&:hover': { bgcolor: 'action.hover' } }}>
      <Box sx={{ flex: 1, minWidth: 0 }}><Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
        <Typography variant="caption" color="text.secondary">{request.reference}</Typography><Chip size="small" variant="outlined" label={request.status === 'OPEN' ? 'Open' : request.status === 'RESOLVED' ? 'Resolved' : 'Cancelled'} />
      </Stack><Typography fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>{request.summary}</Typography>
        <Typography variant="caption" color="text.secondary">{deliveryLabels[request.initialMessageDelivery]} · {date(request.updatedAt)}</Typography></Box><ArrowForwardRounded fontSize="small" />
    </Box>)}</Box>
    <Stack direction="row" justifyContent="flex-end" spacing={1}>
      <Button disabled={page === 0 || requests.isFetching} onClick={() => setPage(value => value - 1)} startIcon={<ArrowBackRounded />}>Previous</Button>
      <Button disabled={(requests.data?.length || 0) < 25 || requests.isFetching} onClick={() => setPage(value => value + 1)} endIcon={<ArrowForwardRounded />}>Next</Button>
    </Stack>
  </Stack>
}

function SupportCaseDetails({ caseId }: { caseId: string }) {
  const support = useCustomerSupport()
  const cache = useQueryClient()
  const key = ['support-case', support.customerId, support.actorId, caseId]
  const query = useQuery({ queryKey: key, queryFn: () => getSupportCase(caseId), retry: false })
  const supportCase = query.data
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [action, setAction] = useState<'resolve' | 'cancel' | null>(null)
  const [editingResources, setEditingResources] = useState(false)
  const [resources, setResources] = useState<SupportResource[]>([])
  const labels = useSupportResources(support.customerId)
  const update = (next: SupportCase) => {
    cache.setQueryData(key, next)
    void cache.invalidateQueries({ queryKey: ['support-cases', support.customerId, support.actorId] })
  }
  const consent = useMutation({ mutationFn: (granted: boolean) => changeSupportConsent(supportCase!, granted), onSuccess: update })
  const status = useMutation({ mutationFn: (command: 'resolve' | 'cancel' | 'reopen') => changeSupportStatus(supportCase!, command), onSuccess: next => {
    update(next); setAction(null)
    void cache.invalidateQueries({ queryKey: ['support-workspace', support.customerId, support.actorId, caseId] })
  } })
  const saveResources = useMutation({ mutationFn: () => updateSupportResources(supportCase!, resources), onSuccess: next => { update(next); setEditingResources(false) } })
  const start = useMutation({ mutationFn: () => startSupportConversation(caseId), onSettled: () => {
    void cache.invalidateQueries({ queryKey: key })
    void cache.invalidateQueries({ queryKey: ['support-workspace', support.customerId, support.actorId, caseId] })
  } })
  if (query.isPending) return <CircularProgress size={20} />
  if (!supportCase) return <Alert severity="error">{errorText(query.error)} No conversation was opened.</Alert>
  const pending = consent.isPending || status.isPending || saveResources.isPending || start.isPending
  return <>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} justifyContent="space-between">
      <Box sx={{ minWidth: 0 }}><Stack direction="row" spacing={1} alignItems="center"><Typography variant="caption" color="text.secondary">{supportCase.reference}</Typography>
        <Chip size="small" variant="outlined" label={supportCase.status} /></Stack>
        <Typography variant="body2" fontWeight={600} sx={{ mt: 0.5, overflowWrap: 'anywhere' }}>{supportCase.summary}</Typography></Box>
      <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0 }}>
        <Tooltip title="Refresh request"><IconButton aria-label="Refresh support request" size="small" onClick={() => { void query.refetch() }}><RefreshRounded fontSize="small" /></IconButton></Tooltip>
        <Button size="small" startIcon={<TuneRounded />} onClick={() => setDetailsOpen(true)}>Request details</Button>
        {supportCase.status === 'OPEN' ? <Button size="small" startIcon={<CheckRounded />} disabled={pending} onClick={() => setAction('resolve')}>Resolve</Button>
          : <Button size="small" startIcon={<RefreshRounded />} disabled={pending} onClick={() => status.mutate('reopen')}>Reopen request</Button>}
        {supportCase.status === 'OPEN' && supportCase.episodes[supportCase.episodes.length - 1]?.terminal && <Button size="small" disabled={pending} onClick={() => status.mutate('reopen')}>Start new conversation</Button>}
      </Stack>
    </Stack>
    {[consent.error, status.error, saveResources.error, start.error].filter(Boolean).map((error, index) => <Alert key={index} severity="error">{errorText(error)}</Alert>)}
    {supportCase.initialMessageDelivery === 'PENDING' && <Alert severity="info" action={<Button disabled={pending || supportCase.status !== 'OPEN'} onClick={() => start.mutate()}>Start conversation</Button>}>Your request is saved. Its initial message has not been delivered yet.</Alert>}
    {supportCase.initialMessageDelivery === 'UNKNOWN' && <Alert severity="warning" action={<Button disabled={pending || supportCase.status !== 'OPEN'} onClick={() => start.mutate()}>Check same delivery</Button>}>Initial delivery is unconfirmed. Any retry uses the same stored message and operation key.</Alert>}
    {supportCase.initialMessageDelivery === 'FAILED' && <Alert severity="warning">The initial AI turn failed. Your request is saved; no replacement AI turn has been sent.</Alert>}
    <Drawer anchor="right" open={detailsOpen} onClose={() => setDetailsOpen(false)} PaperProps={{ sx: { width: { xs: '100%', sm: 420 }, maxWidth: '100%' } }}>
      <Stack spacing={2.5} sx={{ p: 3 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between"><Typography variant="h6">Request details</Typography><IconButton aria-label="Close request details" onClick={() => setDetailsOpen(false)}><CloseRounded /></IconButton></Stack>
        <Typography variant="caption" color="text.secondary">{supportCase.reference} · {deliveryLabels[supportCase.initialMessageDelivery]}</Typography>
        <Box><Typography variant="subtitle2" sx={{ mb: 1 }}>Request context</Typography><Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{supportCase.details || 'No additional details.'}</Typography>
          {supportCase.topic && <Typography variant="caption" color="text.secondary">{supportCase.topic}</Typography>}</Box>
        <Divider /><Stack spacing={1}><Typography variant="subtitle2">Selected resources</Typography>
          {supportCase.resources.map(ref => <Typography variant="body2" key={`${ref.type}:${ref.id}`} sx={{ overflowWrap: 'anywhere' }}>{labels.label(ref)}</Typography>)}
          {supportCase.resources.length === 0 && <Typography variant="body2" color="text.secondary">None selected</Typography>}
          <Button size="small" sx={{ alignSelf: 'flex-start' }} disabled={pending || supportCase.status !== 'OPEN'} onClick={() => { setResources(supportCase.resources); setEditingResources(true) }}>Edit resources</Button>
        </Stack>
        <Divider /><SupportConsent checked={supportCase.consent.granted} disabled={pending || supportCase.status !== 'OPEN'} onChange={granted => consent.mutate(granted)} />
        <Divider /><Box><Typography variant="subtitle2" sx={{ mb: 0.75 }}>Activity</Typography>
          <Typography variant="body2" color="text.secondary">Request saved {date(supportCase.createdAt)}</Typography>
          {supportCase.deliveryObservedAt && <Typography variant="body2" color="text.secondary">Delivery observed {date(supportCase.deliveryObservedAt)}</Typography>}
          <Typography variant="body2" color="text.secondary">Updated {date(supportCase.updatedAt)}</Typography>
          {supportCase.episodes.length > 1 && <Typography variant="caption" color="text.secondary">{supportCase.episodes.length} conversation episodes</Typography>}</Box>
        {supportCase.status === 'OPEN' && <Button color="error" disabled={pending} onClick={() => setAction('cancel')}>Cancel request</Button>}
      </Stack>
    </Drawer>
    <Dialog open={Boolean(action)} onClose={() => { if (!status.isPending) setAction(null) }} maxWidth="xs" fullWidth>
      <DialogTitle>{action === 'resolve' ? 'Resolve this request?' : 'Cancel this request?'}</DialogTitle>
      <DialogContent>Case status is separate from returning to AI or closing a human conversation. You can explicitly reopen this request later.</DialogContent>
      <DialogActions><Button disabled={status.isPending} onClick={() => setAction(null)}>Keep open</Button><Button disabled={status.isPending} onClick={() => { if (action) status.mutate(action) }}>{action === 'resolve' ? 'Resolve request' : 'Cancel request'}</Button></DialogActions>
    </Dialog>
    <Drawer anchor="right" open={editingResources} onClose={() => { if (!saveResources.isPending) setEditingResources(false) }} PaperProps={{ sx: { width: { xs: '100%', sm: 480 }, maxWidth: '100%' } }}>
      <Stack spacing={2.5} sx={{ p: 3 }}><Stack direction="row" alignItems="center" justifyContent="space-between"><Typography variant="h6">Request resources</Typography><IconButton aria-label="Close request resources" onClick={() => setEditingResources(false)}><CloseRounded /></IconButton></Stack>
        <SupportResourcePicker customerId={support.customerId} value={resources} onChange={setResources} disabled={saveResources.isPending} />
        {saveResources.error && <Alert severity="error">{errorText(saveResources.error)}</Alert>}
        <Button variant="contained" disabled={saveResources.isPending} onClick={() => saveResources.mutate()}>Save resources</Button>
      </Stack>
    </Drawer>
  </>
}
