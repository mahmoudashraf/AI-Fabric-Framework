import { useCallback, useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, IconButton,
  Drawer, Stack, TextField, Tooltip, Typography } from '@mui/material'
import CloseRounded from '@mui/icons-material/CloseRounded'
import PersonAddRounded from '@mui/icons-material/PersonAddRounded'
import PersonRemoveRounded from '@mui/icons-material/PersonRemoveRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { isCustomerSession } from '../../auth/customerSession'
import { customerStaffAccessKeys, fetchCustomerStaffAccess, grantCustomerStaffAccess, revokeCustomerStaffAccess,
  type CustomerStaffAccessSummary } from '../../api/customerStaffAccessApi'
import { useAccountEditor } from '../../account/AccountEditorContext'

const errorText = (error: unknown) => error instanceof Error ? error.message : 'Staff access could not be updated.'
const accessDate = (value: string | null) => {
  if (!value) return 'Not recorded'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

export function CustomerStaffAccessPanel({ workspacePresentation = false }: { workspacePresentation?: boolean } = {}) {
  const { session, isLoading } = usePlatformAuth()
  const scope = !isLoading && isCustomerSession(session)
    ? JSON.stringify([session.actorId, session.customerId, session.authenticationMode]) : null
  const currentScope = useRef(scope)
  currentScope.current = scope
  if (isLoading) return <CircularProgress size={24} aria-label="Checking customer administrator access" />
  if (!isCustomerSession(session)) return <Alert severity="warning">A customer administrator session is required to manage LoomAI staff access.</Alert>
  return <StaffAccessScope key={scope} actorId={session.actorId} customerId={session.customerId} authenticationMode={session.authenticationMode}
    isCurrent={() => currentScope.current === scope} workspacePresentation={workspacePresentation} />
}

function StaffAccessScope({ actorId, customerId, authenticationMode, isCurrent, workspacePresentation }: {
  actorId: string; customerId: string; authenticationMode: string; isCurrent: () => boolean; workspacePresentation: boolean
}) {
  const queryClient = useQueryClient()
  const titleId = useId()
  const dialogTitleId = useId()
  const [email, setEmail] = useState('')
  const [grantOpen, setGrantOpen] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [busy, setBusy] = useState<'grant' | 'revoke' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [confirmation, setConfirmation] = useState<CustomerStaffAccessSummary | null>(null)
  const active = useRef(true)
  const generation = useRef(0)
  const request = useRef<AbortController | null>(null)
  const current = () => active.current && isCurrent()
  const discard = useCallback(() => { setEmail(''); setGrantOpen(false); setError(null); setDiscardOpen(false) }, [])
  useAccountEditor(Boolean(email.trim()), Boolean(busy), discard)
  const closeGrant = () => { if (!busy) { if (email.trim()) setDiscardOpen(true); else discard() } }
  useEffect(() => {
    active.current = true
    return () => { active.current = false; generation.current++; request.current?.abort() }
  }, [])
  const queryKey = customerStaffAccessKeys.list(actorId, customerId, authenticationMode)
  const access = useQuery({ queryKey, retry: false, queryFn: async ({ signal }) => {
    if (!current()) throw new Error('The customer account changed. Refresh before continuing.')
    const response = await fetchCustomerStaffAccess(customerId, signal)
    if (!current()) throw new Error('The customer account changed. Refresh before continuing.')
    return response
  } })
  const selected = access.data?.find(item => item.id === confirmation?.id && item.userId === confirmation.userId && item.status === 'ACTIVE')

  const saveResult = async (result: CustomerStaffAccessSummary, attempt: number) => {
    if (!current() || attempt !== generation.current) return false
    await queryClient.cancelQueries({ queryKey, exact: true })
    if (!current() || attempt !== generation.current) return false
    queryClient.setQueryData<CustomerStaffAccessSummary[]>(queryKey, previous => [
      ...(previous ?? []).filter(item => item.id !== result.id), result,
    ])
    void queryClient.invalidateQueries({ queryKey, exact: true })
    return true
  }

  const grant = async (event: FormEvent) => {
    event.preventDefault()
    if (!current() || request.current || !email.trim()) return
    const controller = new AbortController()
    const attempt = ++generation.current
    request.current = controller
    setBusy('grant'); setError(null); setNotice(null)
    try {
      const result = await grantCustomerStaffAccess(customerId, email.trim(), controller.signal)
      if (await saveResult(result, attempt)) {
        setEmail('')
        setGrantOpen(false)
        setNotice(`Access granted to ${result.email}. No invitation email was sent.`)
      }
    } catch (failure) {
      if (current() && attempt === generation.current && !controller.signal.aborted) setError(errorText(failure))
    } finally {
      if (current() && attempt === generation.current) { request.current = null; setBusy(null) }
    }
  }

  const revoke = async () => {
    if (!current() || request.current || !selected) return
    const controller = new AbortController()
    const attempt = ++generation.current
    const target = selected
    request.current = controller
    setBusy('revoke'); setError(null); setNotice(null)
    try {
      const result = await revokeCustomerStaffAccess(customerId, target.id, controller.signal)
      if (result.userId !== target.userId) throw new Error('The returned staff member does not match the revoke request. Refresh before continuing.')
      if (await saveResult(result, attempt)) {
        setConfirmation(null)
        setNotice(`Collaboration access revoked for ${result.email}.`)
      }
    } catch (failure) {
      if (current() && attempt === generation.current && !controller.signal.aborted) setError(errorText(failure))
    } finally {
      if (current() && attempt === generation.current) { request.current = null; setBusy(null) }
    }
  }

  const grantForm = <Box component="form" onSubmit={event => void grant(event)}>
    <Stack direction={workspacePresentation ? 'column' : { xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'flex-start' }}>
      <TextField label="LoomAI staff email" type="email" required value={email} autoComplete="off" size="small" fullWidth disabled={Boolean(busy)}
        onChange={event => setEmail(event.target.value)} inputProps={{ maxLength: 255 }}
        helperText="Use the exact email of an existing LoomAI operator. No account is created and no invitation email is sent." />
      <Button type="submit" variant={workspacePresentation ? 'contained' : 'outlined'} startIcon={busy === 'grant' ? <CircularProgress size={16} /> : <PersonAddRounded />}
        disabled={Boolean(busy) || !email.trim()} sx={{ minHeight: 40, flexShrink: 0 }}>Grant access</Button>
      {workspacePresentation && error && <Alert severity="error">{error}</Alert>}
    </Stack>
  </Box>
  return <Box component="section" aria-labelledby={titleId} sx={{ minWidth: 0, overflowWrap: 'anywhere', '& .MuiAlert-message': { minWidth: 0 } }}>
    <Stack spacing={2}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
        <Typography id={titleId} component="h2" variant="h6">{workspacePresentation ? 'Your collaborators' : 'LoomAI staff access'}</Typography>
        {workspacePresentation && <Button startIcon={<PersonAddRounded />} variant="contained" disabled={Boolean(busy)} onClick={() => { setError(null); setGrantOpen(true) }}>Add collaborator</Button>}
        <Tooltip title="Refresh staff access"><span><IconButton aria-label="Refresh staff access" disabled={access.isFetching || Boolean(busy)}
          onClick={() => { if (current()) void access.refetch() }}><RefreshRounded /></IconButton></span></Tooltip>
      </Stack>
      <Typography variant="body2" color="text.secondary">Allow an existing LoomAI operator to manage this workspace's Kits, Smart Brains and Engage configuration, including creating from templates and release operations permitted for their operator role. This does not grant access to staff or customer accounts, user directories, global infrastructure or secrets, or Platform-admin powers.</Typography>
      {!workspacePresentation && grantForm}
      {error && <Alert severity="error">{error}</Alert>}
      {notice && <Alert severity="success">{notice}</Alert>}
      {access.isPending && <Stack direction="row" spacing={1} alignItems="center" role="status"><CircularProgress size={20} /><Typography variant="body2">Loading staff access...</Typography></Stack>}
      {access.isError && <Alert severity="error" action={<Button onClick={() => { if (current()) void access.refetch() }}>Retry</Button>}>{errorText(access.error)}</Alert>}
      {access.isSuccess && !access.data.length && <Typography variant="body2" color="text.secondary">No customer-granted LoomAI staff access.</Typography>}
      {access.data && <Box role="list" aria-label="LoomAI collaboration grants">{access.data.map(item => <Box key={item.id} role="listitem" sx={{ py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="space-between" alignItems={{ sm: 'center' }}>
          <Box sx={{ minWidth: 0 }}><Typography variant="subtitle2">{item.displayName}</Typography><Typography variant="body2" color="text.secondary">{item.email}</Typography>
            <Typography variant="caption" color="text.secondary">Granted: <time dateTime={item.grantedAt}>{accessDate(item.grantedAt)}</time>
              {item.status === 'REVOKED' && <> / Revoked: <time dateTime={item.revokedAt ?? undefined}>{accessDate(item.revokedAt)}</time></>}</Typography></Box>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ alignSelf: { xs: 'flex-start', sm: 'center' } }}>
            <Chip size="small" variant="outlined" label={item.status === 'ACTIVE' ? 'Active' : 'Revoked'} color={item.status === 'ACTIVE' ? 'success' : 'default'} />
            {item.status === 'ACTIVE' && <Tooltip title={`Revoke access for ${item.email}`}><span><IconButton aria-label={`Revoke access for ${item.email}`} color="error"
              disabled={Boolean(busy) || access.isFetching} onClick={() => { if (current()) { setError(null); setConfirmation(item) } }}><PersonRemoveRounded /></IconButton></span></Tooltip>}
          </Stack>
        </Stack>
      </Box>)}</Box>}
      <Typography variant="body2" color="text.secondary">Revocation blocks future collaboration requests, even through earlier deployment assignments. Work already in flight may finish. Global Platform administrator access is separate and cannot be revoked here.</Typography>
    </Stack>
    {workspacePresentation && <Drawer anchor="right" open={grantOpen} onClose={closeGrant} PaperProps={{ sx: { width: { xs: '100%', sm: 430 }, maxWidth: '100%' } }}>
      <Stack gap={3} sx={{ p: 3 }}><Stack direction="row" alignItems="center" justifyContent="space-between"><Typography component="h2" fontSize={22} fontWeight={650}>Add LoomAI collaborator</Typography><IconButton aria-label="Close collaborator editor" disabled={Boolean(busy)} onClick={closeGrant}><CloseRounded /></IconButton></Stack>
        <Typography variant="body2" color="text.secondary">Give a LoomAI operator access to help configure your workspace. You can revoke it here at any time.</Typography>{grantForm}</Stack>
    </Drawer>}
    <Dialog open={discardOpen} onClose={() => setDiscardOpen(false)} maxWidth="xs" fullWidth><DialogTitle>Discard collaborator details?</DialogTitle><DialogContent>No access has been granted. Your unsaved email will be cleared.</DialogContent><DialogActions><Button onClick={() => setDiscardOpen(false)}>Keep editing</Button><Button color="warning" onClick={discard}>Discard input</Button></DialogActions></Dialog>
    <Dialog open={Boolean(confirmation)} onClose={() => { if (!busy) setConfirmation(null) }} fullWidth maxWidth="sm" aria-labelledby={dialogTitleId}>
      <DialogTitle id={dialogTitleId}>Revoke LoomAI staff access?</DialogTitle>
      <DialogContent><Stack spacing={1}><Typography sx={{ overflowWrap: 'anywhere' }}>{confirmation?.displayName} ({confirmation?.email})</Typography>
        <Typography variant="body2">Future collaboration requests for this workspace will be blocked, including access through earlier deployment assignments. Work already in flight may finish. Global Platform administrator access is unchanged.</Typography>
        {!selected && <Alert severity="info">This collaboration grant is no longer active. Refresh staff access to review it.</Alert>}
        {error && <Alert severity="error">{error}</Alert>}</Stack></DialogContent>
      <DialogActions><Button disabled={Boolean(busy)} onClick={() => setConfirmation(null)}>Cancel</Button>
        <Button color="error" variant="contained" startIcon={<PersonRemoveRounded />} disabled={Boolean(busy) || !selected} onClick={() => void revoke()}>
          {busy === 'revoke' ? 'Revoking...' : 'Revoke access'}</Button></DialogActions>
    </Dialog>
  </Box>
}
