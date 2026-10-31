import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, Drawer, FormControlLabel, IconButton, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material'
import AddRounded from '@mui/icons-material/AddRounded'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckRounded from '@mui/icons-material/CheckRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import ShieldOutlined from '@mui/icons-material/ShieldOutlined'
import SupportAgentOutlined from '@mui/icons-material/SupportAgentOutlined'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { changeSupportConsent, changeSupportStatus, createSupportCase, getSupportCase, getSupportWorkspace,
  listSupportCases, startSupportConversation, updateSupportResources, type CreateSupportCase, type SupportCase,
  type SupportDelivery, type SupportResource } from '../api/supportApi'
import { SupportResourcePicker, useSupportResources } from '../components/customer/SupportResourcePicker'
import { SupportWorkspace } from '../components/customer/SupportWorkspace'
import { useSupportStaffAccess } from '../components/customer/useSupportStaffAccess'
import type { GenericWorkspaceState } from '../../../../experience-packs/generic-workspace-experience/src/types'

const deliveryLabels: Record<SupportDelivery, string> = {
  PENDING: 'Request saved · conversation not started', UNKNOWN: 'Request saved · delivery not yet confirmed',
  USER_MESSAGE_PERSISTED: 'Message saved · AI response pending', COMPLETED: 'Conversation started', FAILED: 'Request saved · AI turn failed',
}
const controlLabels: Record<string, string> = { AI_ACTIVE: 'AI assisting', HUMAN_PENDING: 'Waiting for integration team',
  HUMAN_ACTIVE: 'Human support active', AI_RESUME_PENDING: 'Returning to AI', CLOSED: 'Conversation closed' }
const date = (value: string) => new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
const errorText = (error: unknown) => error instanceof Error ? error.message : 'This operation could not be completed.'
const emptyRequest = (): CreateSupportCase => ({ summary: '', details: '', topic: '', resources: [], diagnosticConsent: false })

export function CustomerSupportPage() {
  const auth = usePlatformAuth()
  if (!isCustomerSession(auth.session)) return <Alert severity="error">Sign in to your customer account to request support.</Alert>
  return <CustomerSupport key={`${auth.session!.customerId}:${auth.session!.actorId}`} customerId={auth.session!.customerId!} actorId={auth.session!.actorId!} />
}

function CustomerSupport({ customerId, actorId }: { customerId: string; actorId: string }) {
  const { caseId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const cache = useQueryClient()
  const [newRequest, setNewRequest] = useState(false)
  const [page, setPage] = useState(0)
  const [draft, setDraft] = useState<CreateSupportCase>(emptyRequest)
  const submission = useRef<{ key: string; body: CreateSupportCase } | null>(null)
  const creating = useRef(false)
  const active = useRef(true)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState<SupportCase | null>(null)
  const [createError, setCreateError] = useState<string | null>(null)
  const requests = useQuery({ queryKey: ['support-cases', customerId, actorId, page], queryFn: () => listSupportCases(page), enabled: !caseId, retry: false })
  const staffAccess = useSupportStaffAccess(!caseId, 0, 1)
  useEffect(() => { if (new URLSearchParams(location.search).get('new') === '1') setNewRequest(true) }, [location.search])
  const showNewRequest = () => { setNewRequest(true); setCreateError(null) }
  const closeNewRequest = () => {
    if (saving) return
    setNewRequest(false)
    if (new URLSearchParams(location.search).has('new')) navigate(location.pathname, { replace: true })
  }
  const submit = async () => {
    if (creating.current || !draft.summary.trim()) return
    creating.current = true; setSaving(true); setCreateError(null)
    if (!submission.current) submission.current = { key: crypto.randomUUID(), body: structuredClone(draft) }
    let created: SupportCase | null = null
    let deliveryError: string | null = null
    try {
      created = await createSupportCase(submission.current.body, submission.current.key)
      if (!active.current) return
      setSaved(created)
      cache.setQueryData(['support-case', customerId, actorId, created.id], created)
      await cache.invalidateQueries({ queryKey: ['support-cases', customerId, actorId] })
      if (!active.current) return
      // Initial delivery happens only in this explicit submit handler, never in a mount effect.
      const delivery = await startSupportConversation(created.id)
      cache.setQueryData(['support-case', customerId, actorId, created.id], delivery.supportCase)
      if (delivery.runtimeStatus >= 400) deliveryError = `The runtime returned ${delivery.runtimeStatus}. No replacement message was sent.`
    } catch (error) {
      if (!created) { if (active.current) setCreateError(errorText(error)); return }
      deliveryError = errorText(error)
    } finally {
      creating.current = false
      if (active.current) setSaving(false)
      if (created && active.current) {
        setNewRequest(false); setSaved(null); setDraft(emptyRequest()); submission.current = null
        void cache.invalidateQueries({ queryKey: ['support-case', customerId, actorId, created.id] })
        navigate(`/support/${encodeURIComponent(created.id)}`, { state: { supportDeliveryError: deliveryError } })
      }
    }
  }

  return <Stack spacing={3}>
    {caseId ? <SupportCaseDetail key={`${customerId}:${actorId}:${caseId}`} caseId={caseId} customerId={customerId} actorId={actorId} /> : <>
      <Stack direction="row" spacing={2} alignItems="flex-start" justifyContent="space-between">
        <Box><Typography variant="overline" color="primary.main">Help</Typography><Typography variant="h4">Integration support</Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75 }}>Bring your setup questions to LoomAI and our integration team.</Typography></Box>
        <Stack direction="row" spacing={1}>
          {staffAccess.isSuccess && !staffAccess.isFetching && <Button component={Link} to="/support/team-intake">Team intake</Button>}
          <Button variant="contained" startIcon={<AddRounded />} onClick={showNewRequest}>New request</Button>
        </Stack>
      </Stack>
      <Stack direction="row" alignItems="center" justifyContent="space-between"><Typography variant="h6">My requests</Typography>
        <Tooltip title="Refresh requests"><IconButton aria-label="Refresh support requests" onClick={() => { void requests.refetch() }}><RefreshRounded fontSize="small" /></IconButton></Tooltip></Stack>
      {requests.isPending && <CircularProgress size={24} />}
      {requests.isError && <Alert severity="error">{errorText(requests.error)}</Alert>}
      {requests.data?.length === 0 && <Stack spacing={1.5} sx={{ py: 6, borderTop: 1, borderBottom: 1, borderColor: 'divider', alignItems: 'flex-start' }}>
        <SupportAgentOutlined color="primary" sx={{ fontSize: 30 }} /><Typography variant="h6">How can we help?</Typography>
        <Typography color="text.secondary">No requests yet. You can get help before creating your first deployment.</Typography>
        <Button startIcon={<AddRounded />} onClick={showNewRequest}>Request integration support</Button>
      </Stack>}
      <Box>{requests.data?.map(request => <Box key={request.id} component={Link} to={`/support/${encodeURIComponent(request.id)}`}
        sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 2.25, borderBottom: 1, borderColor: 'divider', color: 'inherit', textDecoration: 'none', '&:hover': { bgcolor: 'action.hover' } }}>
        <Box sx={{ flex: 1, minWidth: 0 }}><Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
          <Typography variant="caption" color="text.secondary">{request.reference}</Typography><Chip size="small" variant="outlined" label={request.status === 'OPEN' ? 'Open' : request.status === 'RESOLVED' ? 'Resolved' : 'Cancelled'} />
        </Stack><Typography fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>{request.summary}</Typography>
          <Typography variant="caption" color="text.secondary">{deliveryLabels[request.initialMessageDelivery]} · {date(request.updatedAt)}</Typography></Box><ArrowForwardRounded fontSize="small" color="action" />
      </Box>)}</Box>
      <Stack direction="row" justifyContent="flex-end" spacing={1}>
        <Button disabled={page === 0 || requests.isFetching} onClick={() => setPage(value => value - 1)} startIcon={<ArrowBackRounded />}>Previous</Button>
        <Button disabled={(requests.data?.length || 0) < 25 || requests.isFetching} onClick={() => setPage(value => value + 1)} endIcon={<ArrowForwardRounded />}>Next</Button>
      </Stack>
    </>}
    {caseId && <Box><Button startIcon={<AddRounded />} onClick={showNewRequest}>New support request</Button></Box>}
    <Drawer anchor="right" open={newRequest} onClose={closeNewRequest} PaperProps={{ sx: { width: { xs: '100%', sm: 560 }, maxWidth: '100%' } }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ p: 2.5, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6">Request integration support</Typography><IconButton aria-label="Close support request" disabled={saving} onClick={closeNewRequest}><CloseRounded /></IconButton>
      </Stack>
      <Box component="form" onSubmit={event => { event.preventDefault(); void submit() }} sx={{ p: 3 }}>
        <Stack spacing={2.5}>
          <Typography color="text.secondary">Your request starts an AI-assisted support conversation. You can request human support. Please keep passwords, tokens and private customer records out of this request.</Typography>
          {saved && <Alert severity="success">Request saved: {saved.reference}. Starting its conversation...</Alert>}
          {createError && <Alert severity="error">{createError} Your submission is retained; retry sends the same request.</Alert>}
          <TextField label="Summary" required value={draft.summary} disabled={saving || Boolean(submission.current)} inputProps={{ maxLength: 240 }}
            onChange={event => setDraft(value => ({ ...value, summary: event.target.value }))} />
          <TextField label="Details (optional)" multiline minRows={5} value={draft.details} disabled={saving || Boolean(submission.current)} inputProps={{ maxLength: 12000 }}
            onChange={event => setDraft(value => ({ ...value, details: event.target.value }))} />
          <TextField select label="Product (optional)" value={draft.topic} disabled={saving || Boolean(submission.current)} onChange={event => setDraft(value => ({ ...value, topic: event.target.value }))}>
            <MenuItem value="">Not sure yet</MenuItem>{['Platform', 'Engage', 'Desk', 'Knowledge and data'].map(topic => <MenuItem key={topic} value={topic}>{topic}</MenuItem>)}
          </TextField>
          <SupportResourcePicker customerId={customerId} value={draft.resources} disabled={saving || Boolean(submission.current)} onChange={resources => setDraft(value => ({ ...value, resources }))} />
          <ConsentDescription checked={draft.diagnosticConsent} disabled={saving || Boolean(submission.current)} onChange={diagnosticConsent => setDraft(value => ({ ...value, diagnosticConsent }))} />
          <Button type="submit" variant="contained" startIcon={saving ? <CircularProgress size={15} color="inherit" /> : <ArrowForwardRounded />}
            disabled={saving || !draft.summary.trim()}>{saving ? (saved ? 'Starting conversation' : 'Saving request') : submission.current ? 'Retry same submission' : 'Submit request'}</Button>
        </Stack>
      </Box>
    </Drawer>
  </Stack>
}

function SupportCaseDetail({ caseId, customerId, actorId }: { caseId: string; customerId: string; actorId: string }) {
  const location = useLocation()
  const cache = useQueryClient()
  const key = ['support-case', customerId, actorId, caseId]
  const query = useQuery({ queryKey: key, queryFn: () => getSupportCase(caseId), retry: false,
    refetchInterval: data => ['UNKNOWN', 'USER_MESSAGE_PERSISTED'].includes(data.state.data?.initialMessageDelivery || '') ? 5000 : false })
  const supportCase = query.data
  const [workspaceState, setWorkspaceState] = useState<GenericWorkspaceState | null>(null)
  useEffect(() => {
    if (workspaceState?.controlState === 'CLOSED') void cache.invalidateQueries({ queryKey: key })
  }, [workspaceState?.controlState, caseId])
  const [action, setAction] = useState<'resolve' | 'cancel' | null>(null)
  const [editingResources, setEditingResources] = useState(false)
  const [resources, setResources] = useState<SupportResource[]>([])
  const labels = useSupportResources(customerId)
  const workspace = useQuery({ queryKey: ['support-workspace', customerId, actorId, caseId], queryFn: () => getSupportWorkspace(caseId),
    enabled: Boolean(supportCase && supportCase.initialMessageDelivery !== 'PENDING'), retry: false, refetchOnWindowFocus: false })
  const update = (next: SupportCase) => { cache.setQueryData(key, next); void cache.invalidateQueries({ queryKey: ['support-cases', customerId, actorId] }) }
  const consent = useMutation({ mutationFn: (granted: boolean) => changeSupportConsent(supportCase!, granted), onSuccess: update })
  const status = useMutation({ mutationFn: (command: 'resolve' | 'cancel' | 'reopen') => changeSupportStatus(supportCase!, command), onSuccess: next => {
    update(next); setAction(null); void cache.invalidateQueries({ queryKey: ['support-workspace', customerId, actorId, caseId] })
  } })
  const saveResources = useMutation({ mutationFn: () => updateSupportResources(supportCase!, resources), onSuccess: next => { update(next); setEditingResources(false) } })
  const start = useMutation({ mutationFn: () => startSupportConversation(caseId), onSettled: () => {
    void cache.invalidateQueries({ queryKey: key }); void cache.invalidateQueries({ queryKey: ['support-workspace', customerId, actorId, caseId] })
  } })
  if (query.isPending) return <CircularProgress size={24} />
  if (!supportCase) return <Stack spacing={2}><Button component={Link} to="/support" startIcon={<ArrowBackRounded />}>My requests</Button>
    <Alert severity="error">{errorText(query.error)} No conversation was opened.</Alert></Stack>
  const pending = consent.isPending || status.isPending || saveResources.isPending || start.isPending
  return <Stack spacing={2.5}>
    <Box><Button component={Link} to="/support" startIcon={<ArrowBackRounded />}>My requests</Button></Box>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'flex-start' }}>
      <Box sx={{ minWidth: 0 }}><Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
        <Typography variant="caption" color="text.secondary">{supportCase.reference}</Typography><Chip size="small" variant="outlined" label={supportCase.status} />
        {workspaceState?.controlState && <Chip size="small" color={workspaceState.controlState === 'HUMAN_ACTIVE' ? 'success' : 'default'} label={controlLabels[workspaceState.controlState]} />}
      </Stack><Typography variant="h5" sx={{ mt: 1, overflowWrap: 'anywhere' }}>{supportCase.summary}</Typography>
        <Typography variant="caption" color="text.secondary">{deliveryLabels[supportCase.initialMessageDelivery]}</Typography></Box>
      <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
        <Tooltip title="Refresh request"><IconButton aria-label="Refresh support request" onClick={() => { void query.refetch() }}><RefreshRounded fontSize="small" /></IconButton></Tooltip>
        {supportCase.status === 'OPEN' ? <><Button startIcon={<CheckRounded />} disabled={pending} onClick={() => setAction('resolve')}>Resolve</Button><Button disabled={pending} onClick={() => setAction('cancel')}>Cancel request</Button></>
          : <Button startIcon={<RefreshRounded />} disabled={pending} onClick={() => status.mutate('reopen')}>Reopen request</Button>}
        {supportCase.status === 'OPEN' && supportCase.episodes[supportCase.episodes.length - 1]?.terminal
          && <Button startIcon={<RefreshRounded />} disabled={pending} onClick={() => status.mutate('reopen')}>Start new conversation</Button>}
      </Stack>
    </Stack>
    {[consent.error, status.error, saveResources.error, start.error].filter(Boolean).map((error, index) => <Alert key={index} severity="error">{errorText(error)}</Alert>)}
    {location.state?.supportDeliveryError && supportCase.initialMessageDelivery !== 'COMPLETED'
      && <Alert severity="warning">Request saved. Initial conversation delivery returned: {String(location.state.supportDeliveryError)}</Alert>}
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'minmax(0,1fr) 290px' }, gap: 3, alignItems: 'start' }}>
      <Box sx={{ minWidth: 0 }}>
        {supportCase.initialMessageDelivery === 'PENDING' && <Alert severity="info" action={<Button disabled={pending || supportCase.status !== 'OPEN'} onClick={() => start.mutate()}>Start conversation</Button>}>Your request is saved. Its initial message has not been delivered yet.</Alert>}
        {supportCase.initialMessageDelivery === 'UNKNOWN' && <Alert severity="warning" sx={{ mb: 2 }} action={<Button disabled={pending || supportCase.status !== 'OPEN'} onClick={() => start.mutate()}>Check same delivery</Button>}>Initial delivery is unconfirmed. Any retry uses the same stored message and operation key.</Alert>}
        {supportCase.initialMessageDelivery === 'FAILED' && <Alert severity="warning" sx={{ mb: 2 }}>The initial AI turn failed. Your request is saved; no replacement AI turn has been sent.</Alert>}
        {workspace.isPending && workspace.fetchStatus === 'fetching' && <CircularProgress size={22} />}
        {workspace.isError && <Alert severity="warning" action={<Button onClick={() => { void workspace.refetch() }}>Retry workspace</Button>}>{errorText(workspace.error)} Your request remains saved.</Alert>}
        {workspace.data && supportCase.initialMessageDelivery !== 'PENDING'
          && workspace.data.conversationId === supportCase.episodes[supportCase.episodes.length - 1]?.conversationId
          && <SupportWorkspace caseId={caseId} bootstrap={workspace.data} readOnly={supportCase.status !== 'OPEN'} onStateChange={setWorkspaceState} />}
      </Box>
      <Stack spacing={2.5} sx={{ borderLeft: { lg: 1 }, borderColor: { lg: 'divider' }, pl: { lg: 2.5 }, minWidth: 0 }}>
        <Box><Typography variant="subtitle2" sx={{ mb: 1 }}>Request context</Typography><Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{supportCase.details || 'No additional details.'}</Typography>
          {supportCase.topic && <Typography variant="caption" color="text.secondary">{supportCase.topic}</Typography>}</Box>
        <Divider /><Stack spacing={1}><Typography variant="subtitle2">Selected resources</Typography>
          {supportCase.resources.map(ref => <Typography variant="body2" key={`${ref.type}:${ref.id}`} sx={{ overflowWrap: 'anywhere' }}>{labels.label(ref)}</Typography>)}
          {supportCase.resources.length === 0 && <Typography variant="body2" color="text.secondary">None selected</Typography>}
          <Button size="small" sx={{ alignSelf: 'flex-start' }} disabled={pending || supportCase.status !== 'OPEN'} onClick={() => { setResources(supportCase.resources); setEditingResources(true) }}>Edit resources</Button></Stack>
        <Divider /><ConsentDescription checked={supportCase.consent.granted} disabled={pending || supportCase.status !== 'OPEN'} onChange={granted => consent.mutate(granted)} />
        <Divider /><Box><Typography variant="subtitle2" sx={{ mb: 0.75 }}>Activity</Typography>
          <Typography variant="body2" color="text.secondary">Request saved {date(supportCase.createdAt)}</Typography>
          {supportCase.deliveryObservedAt && <Typography variant="body2" color="text.secondary">Delivery observed {date(supportCase.deliveryObservedAt)}</Typography>}
          <Typography variant="body2" color="text.secondary">Updated {date(supportCase.updatedAt)}</Typography>
          {supportCase.episodes.length > 1 && <Typography variant="caption" color="text.secondary">{supportCase.episodes.length} conversation episodes</Typography>}</Box>
      </Stack>
    </Box>
    <Dialog open={Boolean(action)} onClose={() => { if (!status.isPending) setAction(null) }} maxWidth="xs" fullWidth>
      <DialogTitle>{action === 'resolve' ? 'Resolve this request?' : 'Cancel this request?'}</DialogTitle>
      <DialogContent>Case status is separate from returning to AI or closing a human conversation. You can explicitly reopen this request later.</DialogContent>
      <DialogActions><Button disabled={status.isPending} onClick={() => setAction(null)}>Keep open</Button><Button disabled={status.isPending} onClick={() => { if (action) status.mutate(action) }}>{action === 'resolve' ? 'Resolve request' : 'Cancel request'}</Button></DialogActions>
    </Dialog>
    <Drawer anchor="right" open={editingResources} onClose={() => { if (!saveResources.isPending) setEditingResources(false) }} PaperProps={{ sx: { width: { xs: '100%', sm: 480 }, maxWidth: '100%' } }}>
      <Stack spacing={2.5} sx={{ p: 3 }}><Stack direction="row" alignItems="center" justifyContent="space-between"><Typography variant="h6">Request resources</Typography><IconButton aria-label="Close request resources" onClick={() => setEditingResources(false)}><CloseRounded /></IconButton></Stack>
        <SupportResourcePicker customerId={customerId} value={resources} onChange={setResources} disabled={saveResources.isPending} />
        {saveResources.error && <Alert severity="error">{errorText(saveResources.error)}</Alert>}
        <Button variant="contained" disabled={saveResources.isPending} onClick={() => saveResources.mutate()}>Save resources</Button>
      </Stack>
    </Drawer>
  </Stack>
}

function ConsentDescription({ checked, disabled, onChange }: { checked: boolean; disabled: boolean; onChange(value: boolean): void }) {
  return <Stack spacing={0.75}>
    <Stack direction="row" spacing={0.75} alignItems="center"><ShieldOutlined fontSize="small" color="secondary" /><Typography variant="subtitle2">Status-check permission</Typography></Stack>
    <FormControlLabel sx={{ alignItems: 'flex-start', m: 0 }} control={<Checkbox size="small" checked={checked} disabled={disabled} onChange={(_, value) => onChange(value)} />}
      label={<Typography variant="body2" sx={{ pt: 0.85 }}>Allow AI and the integration team to check these selected resources for this request</Typography>} />
    <Typography variant="caption" color="text.secondary">Release and verification state, app readiness, and connection/indexing status only. No credentials, raw logs or business records. Documentation help remains available without permission.</Typography>
  </Stack>
}
