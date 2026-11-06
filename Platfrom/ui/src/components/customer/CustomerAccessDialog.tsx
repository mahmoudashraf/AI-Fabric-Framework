import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Stack, TextField, Tooltip, Typography } from '@mui/material'
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded'
import PersonAddRounded from '@mui/icons-material/PersonAddRounded'
import { prepareStaffCustomerAccess, type StaffCustomerAccess } from '../../api/staffCustomerAccessApi'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'

export type CustomerAccessDialogProps = {
  customerId: string
  customerName: string
  open: boolean
  onClose: () => void
  onPrepared?: (access: StaffCustomerAccess) => void
}

export function customerAccessLinks(origin: string) {
  return [
    { label: 'Create sign-in account', url: new URL('/sign-up', origin).href },
    { label: 'Connect existing workspace', url: new URL('/recover-account', origin).href },
    { label: 'Sign in', url: new URL('/sign-in', origin).href },
  ]
}

export function CustomerAccessDialog({ customerId, customerName, open, onClose, onPrepared }: CustomerAccessDialogProps) {
  const { session } = usePlatformAuth()
  const authority = JSON.stringify([session?.actorId, session?.authenticationMode, session?.role, session?.authenticated])
  const scope = JSON.stringify([customerId, open, authority])
  const currentScope = useRef(scope)
  currentScope.current = scope
  const request = useRef<AbortController | null>(null)
  const generation = useRef(0)
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [prepared, setPrepared] = useState<StaffCustomerAccess | null>(null)
  const [copyNotice, setCopyNotice] = useState<string | null>(null)
  const canPrepare = session?.authenticated && session.role === 'PLATFORM_ADMIN' && Boolean(customerId)

  useEffect(() => {
    generation.current++
    request.current?.abort()
    request.current = null
    setEmail(''); setDisplayName(''); setPrepared(null); setError(null); setCopyNotice(null); setBusy(false)
    return () => { generation.current++; request.current?.abort() }
  }, [scope])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canPrepare || busy || request.current || !email.trim() || displayName.trim().length < 2) return
    const attempt = ++generation.current
    const submittedScope = scope
    const controller = new AbortController()
    request.current = controller
    setBusy(true); setError(null); setCopyNotice(null)
    try {
      const result = await prepareStaffCustomerAccess(customerId, { email: email.trim(), displayName: displayName.trim() }, controller.signal)
      if (attempt !== generation.current || currentScope.current !== submittedScope) return
      if (result.customerId !== customerId || result.email.toLowerCase() !== email.trim().toLowerCase()) {
        throw new Error('The returned account does not match this request. Check the workspace before continuing.')
      }
      setPrepared(result)
      onPrepared?.(result)
    } catch (failure) {
      if (attempt === generation.current && currentScope.current === submittedScope && !controller.signal.aborted) {
        setError(failure instanceof Error ? failure.message : 'Access could not be prepared.')
      }
    } finally {
      if (attempt === generation.current && currentScope.current === submittedScope) { request.current = null; setBusy(false) }
    }
  }

  const copy = async (url: string, label: string) => {
    const copiedScope = scope
    try {
      await navigator.clipboard.writeText(url)
      if (currentScope.current === copiedScope) setCopyNotice(`${label} link copied.`)
    } catch {
      if (currentScope.current === copiedScope) setCopyNotice('Clipboard unavailable. Select the link to copy it.')
    }
  }

  const visiblePrepared = prepared?.customerId === customerId ? prepared : null
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="customer-access-title">
    <DialogTitle id="customer-access-title">Prepare customer access</DialogTitle>
    <Box component="form" onSubmit={submit}>
      <DialogContent sx={{ overflowWrap: 'anywhere' }}><Stack spacing={2.5}>
        <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>{customerName}</Typography>
        {!canPrepare && <Alert severity="warning">An active platform administrator session is required.</Alert>}
        {error && <Alert severity="error">{error}</Alert>}
        {visiblePrepared ? <>
          <Alert severity="success">Access {visiblePrepared.created ? 'prepared' : 'already prepared'} for {visiblePrepared.email}. No email has been sent.</Alert>
          <Typography variant="body2">This account has customer administrator access in this workspace.</Typography>
          <Typography variant="body2">{visiblePrepared.identityLinked
            ? 'A sign-in identity is already connected. The customer can sign in normally with their own account.'
            : 'If the customer has no LoomAI sign-in account, they must create and verify it first using this same email. Then they can connect this existing workspace with a recovery email link. No old Console password is needed.'}</Typography>
          <Stack spacing={1.5}>{customerAccessLinks(window.location.origin).map(link => <Stack key={link.url} direction="row" alignItems="center" spacing={1}>
            <TextField label={link.label} value={link.url} size="small" fullWidth slotProps={{ input: { readOnly: true }, htmlInput: { onFocus: (event: React.FocusEvent<HTMLInputElement>) => event.target.select() } }} />
            <Tooltip title={`Copy ${link.label.toLowerCase()} link`}><IconButton aria-label={`Copy ${link.label.toLowerCase()} link`} onClick={() => { void copy(link.url, link.label) }} sx={{ width: 40, height: 40, flexShrink: 0 }}><ContentCopyRounded /></IconButton></Tooltip>
          </Stack>)}</Stack>
          {copyNotice && <Typography role="status" variant="body2">{copyNotice}</Typography>}
        </> : <>
          <TextField label="Customer email" type="email" required autoComplete="off" value={email} onChange={event => setEmail(event.target.value)}
            disabled={busy || !canPrepare} slotProps={{ htmlInput: { maxLength: 255 } }} />
          <TextField label="Customer name" required autoComplete="off" value={displayName} onChange={event => setDisplayName(event.target.value)}
            disabled={busy || !canPrepare} slotProps={{ htmlInput: { minLength: 2, maxLength: 255 } }} />
          <Typography variant="body2" color="text.secondary">Use the customer's real email. This prepares administrator access only in the selected workspace. No password or email will be issued.</Typography>
        </>}
      </Stack></DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{visiblePrepared ? 'Done' : 'Cancel'}</Button>
        {!visiblePrepared && <Button type="submit" variant="contained" startIcon={<PersonAddRounded />} disabled={busy || !canPrepare || !email.trim() || displayName.trim().length < 2}>
          {busy ? 'Preparing...' : 'Prepare access'}
        </Button>}
      </DialogActions>
    </Box>
  </Dialog>
}
