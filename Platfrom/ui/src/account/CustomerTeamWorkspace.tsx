import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Avatar, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, Drawer, IconButton, InputAdornment, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography } from '@mui/material'
import SearchRounded from '@mui/icons-material/SearchRounded'
import PersonAddOutlined from '@mui/icons-material/PersonAddOutlined'
import EditOutlined from '@mui/icons-material/EditOutlined'
import PersonOffOutlined from '@mui/icons-material/PersonOffOutlined'
import CloseRounded from '@mui/icons-material/CloseRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import LockResetOutlined from '@mui/icons-material/LockResetOutlined'
import { createPlatformUser, fetchPlatformUserAccessOverview, resetPlatformUserPassword, updatePlatformUser,
  type PlatformAuthSessionSummary, type PlatformUserAccessSummary } from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { deploymentTerm } from '../deploymentTerminology'
import { useAccountEditor } from './AccountEditorContext'
import { accountDate, filterMembers, isCurrentMember, memberCreatePayload, memberInitials, memberUpdatePayload, unchangedMember, workspaceMembers } from './customerTeamModel'

type Props = { session: PlatformAuthSessionSummary & { customerId: string; actorId: string } }
const blank = { email: '', displayName: '', password: '' }
const failureText = (error: unknown) => error instanceof Error ? error.message : 'The team could not be updated.'

export function CustomerTeamWorkspace({ session }: Props) {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const alive = useRef(true)
  useEffect(() => { alive.current = true; return () => { alive.current = false } }, [])
  const queryKey = ['platform-user-access-overview', 'customer-account', session.customerId, session.actorId, session.authenticationMode]
  const members = useQuery({ queryKey, retry: false, queryFn: async () => workspaceMembers(await fetchPlatformUserAccessOverview(), session.customerId), enabled: session.canManageUserDirectory })
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [selection, setSelection] = useState<string | null>(null)
  const [editor, setEditor] = useState<{ mode: 'add' | 'edit' | 'password'; user: PlatformUserAccessSummary | null } | null>(null)
  const [form, setForm] = useState(blank)
  const [busy, setBusy] = useState(false)
  const command = useRef(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [disable, setDisable] = useState<PlatformUserAccessSummary | null>(null)
  const [discardOpen, setDiscardOpen] = useState(false)
  const rows = filterMembers(members.data ?? [], search, status)
  const selected = rows.find(user => user.id === selection) ?? (selection === null ? rows.find(user => isCurrentMember(user, session.actorId)) ?? rows[0] : null)
  const dirty = Boolean(editor && (editor.mode === 'add' ? form.email || form.displayName || form.password
    : editor.mode === 'password' ? form.password : form.displayName !== editor.user?.displayName))
  const discard = useCallback(() => { setEditor(null); setForm(blank); setError(null); setDiscardOpen(false) }, [])
  useAccountEditor(dirty, busy, discard)
  const close = () => { if (!busy) { if (dirty) setDiscardOpen(true); else discard() } }
  const open = (mode: 'add' | 'edit' | 'password', user: PlatformUserAccessSummary | null = null) => {
    setError(null); setEditor({ mode, user }); setForm({ ...blank, displayName: user?.displayName ?? '' })
  }
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['platform-user-access-overview'] })
    void queryClient.invalidateQueries({ queryKey: ['platform-users'] })
  }
  const run = async (action: () => Promise<void>) => {
    if (command.current) return
    command.current = true; setBusy(true); setError(null); setNotice(null)
    try { await action() } catch (failure) { if (alive.current) setError(failureText(failure)) }
    finally { command.current = false; if (alive.current) setBusy(false) }
  }
  const save = (event: FormEvent) => {
    event.preventDefault()
    if (!editor || !dirty || !session.canManageUserDirectory) return
    const current = editor
    void run(async () => {
      if (current.mode === 'add') {
        const result = await createPlatformUser(memberCreatePayload(session.customerId, form))
        if (!alive.current) return
        setSelection(result.id); setNotice('Member created. The Console account is active; no invitation email was sent.')
      } else if (current.user) {
        if (current.mode === 'password') {
          await resetPlatformUserPassword(current.user.id, { password: form.password })
          if (!alive.current) return
          setNotice('Console password updated. This does not change a linked Supabase password.')
        } else {
          const fresh = unchangedMember(await fetchPlatformUserAccessOverview(), session.customerId, current.user)
          if (!alive.current) return
          await updatePlatformUser(current.user.id, memberUpdatePayload(session.customerId, form.displayName, fresh.status))
          if (!alive.current) return
          setNotice('Member details updated.')
        }
      }
      if (!alive.current) return
      discard(); invalidate()
      if (current.user && isCurrentMember(current.user, session.actorId) && current.mode === 'edit') void auth.refreshSession()
    })
  }
  const toggleStatus = () => {
    const current = disable
    if (!current || !session.canManageUserDirectory || isCurrentMember(current, session.actorId)) return
    void run(async () => {
      const fresh = unchangedMember(await fetchPlatformUserAccessOverview(), session.customerId, current)
      if (!alive.current) return
      await updatePlatformUser(current.id, memberUpdatePayload(session.customerId, fresh.displayName, fresh.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'))
      if (!alive.current) return
      setDisable(null); setNotice(current.status === 'ACTIVE' ? 'Member disabled.' : 'Member enabled.'); invalidate()
    })
  }
  if (!session.canManageUserDirectory) return <Alert severity="info">Team administration is not enabled for this account. Your own sign-in is unchanged.</Alert>
  const person = (user: PlatformUserAccessSummary) => <Stack direction="row" alignItems="center" gap={1.5} sx={{ minWidth: 0 }}>
    <Avatar sx={{ width: 36, height: 36, fontSize: 12, bgcolor: 'action.selected', color: 'text.secondary' }}>{memberInitials(user.displayName)}</Avatar>
    <Box sx={{ minWidth: 0 }}><Typography fontSize={13} fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>{user.displayName}{isCurrentMember(user, session.actorId) ? ' (you)' : ''}</Typography>
      <Typography fontSize={12} color="text.secondary" noWrap title={user.email}>{user.email}</Typography></Box></Stack>
  return <Stack gap={2} sx={{ flex: 1, minHeight: 0 }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} gap={1.5} alignItems={{ sm: 'center' }}>
      <TextField size="small" label="Search people" value={search} onChange={event => setSearch(event.target.value)} sx={{ width: { xs: '100%', sm: 290 } }}
        InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded fontSize="small" /></InputAdornment> }} />
      <TextField select size="small" label="Member status" value={status} onChange={event => setStatus(event.target.value)} sx={{ minWidth: 175 }} InputLabelProps={{ shrink: true }} SelectProps={{ displayEmpty: true }}>
        <MenuItem value="">Active & disabled</MenuItem><MenuItem value="ACTIVE">Active</MenuItem><MenuItem value="DISABLED">Disabled</MenuItem></TextField>
      <Box sx={{ flex: 1, display: { xs: 'none', sm: 'block' } }} /><Stack direction="row" gap={1} justifyContent="flex-end">
        <Tooltip title="Refresh team"><span><IconButton aria-label="Refresh team" disabled={busy || members.isFetching} onClick={() => void members.refetch()}><RefreshRounded /></IconButton></span></Tooltip>
        <Button variant="contained" startIcon={<PersonAddOutlined />} disabled={busy || members.isError} onClick={() => open('add')}>Add member</Button>
      </Stack>
    </Stack>
    {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
    {error && !editor && !disable && <Alert severity="error">{error}</Alert>}
    {members.isPending && <CircularProgress size={24} aria-label="Loading team" />}
    {members.isError && <Alert severity="error" action={<Button onClick={() => void members.refetch()}>Retry</Button>}>{failureText(members.error)}</Alert>}
    {members.isSuccess && <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: selected ? 'minmax(0,1.6fr) minmax(280px,1fr)' : 'minmax(0,1fr)' }, borderTop: 1, borderColor: 'divider', flex: 1, minHeight: 0 }}>
      <Box sx={{ minWidth: 0, overflow: 'auto', pr: { lg: selected ? 2 : 0 } }}>
        <Table size="small" aria-label="Workspace team" sx={{ tableLayout: 'fixed' }}><TableHead><TableRow>
          <TableCell sx={{ width: { xs: '70%', md: '43%' } }}>Person</TableCell><TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Access</TableCell><TableCell>Status</TableCell>
          <TableCell sx={{ display: { xs: 'none', xl: 'table-cell' } }}>Last sign-in</TableCell>
        </TableRow></TableHead><TableBody>{rows.map(user => <TableRow key={user.id} selected={selected?.id === user.id} hover sx={{ '&.Mui-selected': { bgcolor: 'action.selected' } }}>
          <TableCell sx={{ py: 1.5, borderLeft: 3, borderLeftColor: selected?.id === user.id ? 'primary.main' : 'transparent' }}>
            <Box component="button" aria-label={`View ${user.displayName}`} aria-pressed={selected?.id === user.id} disabled={busy} onClick={() => setSelection(user.id)}
              sx={{ border: 0, p: 0, bgcolor: 'transparent', color: 'inherit', cursor: 'pointer', width: '100%', textAlign: 'left', font: 'inherit', '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 4 } }}>{person(user)}</Box>
          </TableCell><TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, fontSize: 12 }}>Workspace admin</TableCell>
          <TableCell><Chip size="small" variant="outlined" label={user.status === 'ACTIVE' ? 'Active' : user.status === 'DISABLED' ? 'Disabled' : user.status} color={user.status === 'ACTIVE' ? 'success' : 'default'} /></TableCell>
          <TableCell sx={{ display: { xs: 'none', xl: 'table-cell' }, fontSize: 12, color: 'text.secondary' }}>{accountDate(user.lastLoginAt)}</TableCell>
        </TableRow>)}</TableBody></Table>
        {!rows.length && <Typography color="text.secondary" sx={{ py: 4 }}>{members.data.length ? 'No members match these filters.' : 'No team members found.'}</Typography>}
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', py: 1.5 }}>{rows.length} {rows.length === 1 ? 'member' : 'members'}{search || status ? ` of ${members.data.length}` : ''}</Typography>
      </Box>
      {selected && <Box component="aside" aria-label="Member details" sx={{ minWidth: 0, overflow: 'auto', borderLeft: { lg: 1 }, borderTop: { xs: 1, lg: 0 }, borderColor: 'divider', pl: { lg: 3 }, pt: 2 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="overline" color="text.secondary">Member details</Typography><IconButton aria-label="Close member details" onClick={() => setSelection('')}><CloseRounded fontSize="small" /></IconButton></Stack>
        <Stack gap={2.5} sx={{ pt: 2 }}>
          <Stack direction="row" gap={2} alignItems="center"><Avatar sx={{ width: 56, height: 56, bgcolor: 'action.selected', color: 'text.primary' }}>{memberInitials(selected.displayName)}</Avatar>
            <Box sx={{ minWidth: 0 }}><Typography component="h2" fontSize={20} fontWeight={650} sx={{ overflowWrap: 'anywhere' }}>{selected.displayName}</Typography><Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{selected.email}</Typography><Typography variant="caption" color={selected.status === 'ACTIVE' ? 'success.main' : 'text.secondary'}>{selected.status === 'ACTIVE' ? 'Active' : 'Disabled'}</Typography></Box></Stack>
          <Button startIcon={<EditOutlined />} disabled={busy} onClick={() => open('edit', selected)} sx={{ alignSelf: 'flex-start' }}>Edit member</Button><Divider />
          <Box><Typography fontSize={13} fontWeight={600} gutterBottom>Workspace administrator</Typography><Typography variant="body2" color="text.secondary">Can manage all Kits, {deploymentTerm.plural}, Engage apps and team members in this workspace.</Typography></Box>
          <Divider /><Box component="dl" sx={{ m: 0 }}>{[['Created', accountDate(selected.createdAt)], ['Last sign-in', accountDate(selected.lastLoginAt)]].map(([label, value]) => <Stack key={label} direction="row" gap={2} justifyContent="space-between" sx={{ py: 0.5 }}><Typography component="dt" variant="body2" color="text.secondary">{label}</Typography><Typography component="dd" variant="body2" sx={{ m: 0, textAlign: 'right' }}>{value}</Typography></Stack>)}</Box>
          <Divider /><Stack alignItems="flex-start" gap={1}>
            <Button startIcon={<LockResetOutlined />} disabled={busy} onClick={() => open('password', selected)}>Reset Console password</Button>
            <Button color={selected.status === 'ACTIVE' ? 'error' : 'primary'} startIcon={<PersonOffOutlined />} disabled={busy || isCurrentMember(selected, session.actorId)} onClick={() => { setError(null); setDisable(selected) }}>{selected.status === 'ACTIVE' ? 'Disable member' : 'Enable member'}</Button>
            {isCurrentMember(selected, session.actorId) && <Typography variant="caption" color="text.secondary">You cannot disable your own access here.</Typography>}
          </Stack>
        </Stack>
      </Box>}
    </Box>}
    <Drawer anchor="right" open={Boolean(editor)} onClose={close} PaperProps={{ sx: { width: { xs: '100%', sm: 440 }, maxWidth: '100%' } }}>
      <Stack component="form" onSubmit={save} gap={2.5} sx={{ p: 3, minHeight: '100%' }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between"><Typography component="h2" fontSize={22} fontWeight={650}>{editor?.mode === 'add' ? 'Add member' : editor?.mode === 'password' ? 'Reset Console password' : 'Edit member'}</Typography><IconButton aria-label="Close member editor" disabled={busy} onClick={close}><CloseRounded /></IconButton></Stack>
        <Typography variant="body2" color="text.secondary">{editor?.mode === 'password' ? 'Changes only the legacy Console password, not Supabase sign-in or email-link credentials.' : `Workspace administrators can manage every Kit, ${deploymentTerm.singular}, Engage app and team member in this workspace.`}</Typography>
        {editor?.mode === 'add' && <TextField label="Email" type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} required disabled={busy} autoComplete="off" inputProps={{ maxLength: 255 }} />}
        {editor?.mode !== 'password' && <TextField label="Full name" value={form.displayName} onChange={event => setForm({ ...form, displayName: event.target.value })} required disabled={busy} inputProps={{ maxLength: 160 }} />}
        {editor?.mode !== 'edit' && <TextField label={editor?.mode === 'add' ? 'Initial Console password' : 'New Console password'} type="password" value={form.password} onChange={event => setForm({ ...form, password: event.target.value })} required disabled={busy} autoComplete="new-password" />}
        {editor?.mode === 'add' && <Alert severity="info">Creates an active Console account. No invitation email is sent. For customer sign-in, the member connects their existing Console account to Supabase using the current account-linking flow.</Alert>}
        {editor?.mode === 'edit' && <Typography variant="body2" color="text.secondary">Email and workspace ownership are unchanged. Use the status action in member details to enable or disable access.</Typography>}
        {error && <Alert severity="error">{error}</Alert>}<Box sx={{ flex: 1 }} /><Divider />
        <Stack direction="row" gap={1} justifyContent="flex-end"><Button disabled={busy} onClick={close}>Cancel</Button><Button type="submit" variant="contained" disabled={busy || !dirty || (editor?.mode !== 'password' && !form.displayName.trim())}>{busy ? 'Saving...' : editor?.mode === 'add' ? 'Create member' : 'Save changes'}</Button></Stack>
      </Stack>
    </Drawer>
    <Dialog open={discardOpen} onClose={() => setDiscardOpen(false)} maxWidth="xs" fullWidth><DialogTitle>Discard member details?</DialogTitle><DialogContent>Your unsaved input will be lost. No account changes have been made.</DialogContent><DialogActions><Button onClick={() => setDiscardOpen(false)}>Keep editing</Button><Button color="warning" onClick={discard}>Discard input</Button></DialogActions></Dialog>
    <Dialog open={Boolean(disable)} onClose={() => !busy && setDisable(null)} maxWidth="xs" fullWidth><DialogTitle>{disable?.status === 'ACTIVE' ? 'Disable this member?' : 'Enable this member?'}</DialogTitle><DialogContent>
      <Typography sx={{ overflowWrap: 'anywhere' }}>{disable?.displayName} · {disable?.email}</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>{disable?.status === 'ACTIVE' ? 'This removes their ability to use this Console account. The server protects the last active administrator.' : 'This restores their workspace administrator access.'}</Typography>{error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
    </DialogContent><DialogActions><Button disabled={busy} onClick={() => setDisable(null)}>Cancel</Button><Button variant="contained" color={disable?.status === 'ACTIVE' ? 'error' : 'primary'} disabled={busy} onClick={toggleStatus}>{busy ? 'Updating...' : disable?.status === 'ACTIVE' ? 'Disable member' : 'Enable member'}</Button></DialogActions></Dialog>
  </Stack>
}
