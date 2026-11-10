import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Drawer, FormControlLabel, IconButton, LinearProgress, Stack, Switch, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import CloseOutlined from '@mui/icons-material/CloseOutlined'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import RefreshOutlined from '@mui/icons-material/RefreshOutlined'
import UndoOutlined from '@mui/icons-material/UndoOutlined'
import { ControlledConversation, type ControlledConversationMessage } from '../../../../max-mode-widget/src/controlledConversation'
import { stringifyExactJson } from '../lib/losslessJson'
import { AssistantSources } from './AssistantSources'
import { operation, scopeIdentity } from './scope'
import { useAssistant, type AssistantController } from './useAssistant'
import type { AssistantScope, AssistantTurn } from './types'
import { ChangeSummary, ValidationOutcome } from './TurnDetails'
import { validationSummary } from './turnPresentation'

type Props = {
  scope: AssistantScope | null
  children: ReactNode | ((state: { paused: boolean; revision: number }) => ReactNode)
  onSaved: () => Promise<void>
  onBusyChange?: (busy: boolean) => void
  editorBusy?: boolean
  readOnlyReason?: string
  initiallyOpen?: boolean
  guideOnly?: boolean
  links?: { label: string; href: string }[]
}

export function AssistantWorkspace({ scope, children, onSaved, onBusyChange, editorBusy, readOnlyReason, initiallyOpen = false, guideOnly, links }: Props) {
  const identity = scope ? scopeIdentity(scope) : null
  const [visibility, setVisibility] = useState({ identity, open: initiallyOpen, activated: initiallyOpen })
  const current = visibility.identity === identity ? visibility : { identity, open: initiallyOpen, activated: initiallyOpen }
  const open = Boolean(scope && current.open)
  const [activity, setActivity] = useState({ identity, busy: false })
  const busy = activity.identity === identity && activity.busy
  const [revision, setRevision] = useState(0)
  const editor = useRef<HTMLDivElement>(null), opener = useRef<HTMLButtonElement>(null)
  const savedCallback = useRef(onSaved); savedCallback.current = onSaved
  const refreshEditor = useCallback(async () => { await savedCallback.current(); setRevision(value => value + 1) }, [])
  const reportActivity = useCallback((value: boolean) => setActivity({ identity, busy: value }), [identity])
  const busyCallback = useRef(onBusyChange); busyCallback.current = onBusyChange
  useEffect(() => { busyCallback.current?.(busy); return () => busyCallback.current?.(false) }, [busy])
  useEffect(() => {
    const node = editor.current
    if (!node) return
    node.toggleAttribute('inert', busy)
    if (busy && node.contains(document.activeElement)) (document.activeElement as HTMLElement)?.blur()
    return () => node.removeAttribute('inert')
  }, [busy])
  const close = () => { setVisibility({ ...current, open: false }); requestAnimationFrame(() => opener.current?.focus()) }
  return <Box data-assistant-workspace data-assistant-open={open} sx={{ display: 'flex', flexDirection: 'column', flex: 1, width: '100%', height: '100%', minWidth: 0, minHeight: 0 }}>
    {scope && <Stack direction="row" alignItems="center" justifyContent="flex-end" sx={{ borderBottom: 1, borderColor: 'divider', px: 2, py: .5, flexShrink: 0 }}>
      {busy && <Typography role="status" variant="caption" sx={{ mr: 'auto' }}>AI is updating this editor</Typography>}
      <Button ref={opener} size="small" startIcon={<AutoAwesomeOutlined />} aria-expanded={open} aria-controls="configuration-assistant-panel" onClick={() => setVisibility({ identity, activated: true, open: !open })}>Build with AI</Button>
    </Stack>}
    <Box sx={{ display: 'flex', flex: 1, minHeight: 0, minWidth: 0 }}>
      {/* Scope changes reset the assistant, never an editor's retained manual buffer. */}
      <Box ref={editor} aria-busy={busy} sx={{ flex: 1, minHeight: 0, minWidth: 0, position: 'relative', display: 'flex', flexDirection: 'column' }}>
        <Box component="fieldset" disabled={busy} sx={{ border: 0, p: 0, m: 0, display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, minWidth: 0, overflow: 'auto', opacity: busy ? .6 : 1 }}>
          {typeof children === 'function' ? children({ paused: busy, revision }) : children}
        </Box>
        {busy && <Box sx={{ position: 'absolute', inset: 0, zIndex: 5, cursor: 'wait' }} />}
      </Box>
      {scope && <ScopedAssistant key={identity} scope={scope} enabled={current.activated} open={open} onSaved={refreshEditor} onBusyChange={reportActivity}
        onClose={close} editorBusy={editorBusy} readOnlyReason={readOnlyReason} guideOnly={guideOnly} links={links} />}
    </Box>
  </Box>
}

function ScopedAssistant({ scope, enabled, open, onSaved, onBusyChange, ...panelProps }: Omit<Parameters<typeof AssistantPanel>[0], 'controller'> & {
  enabled: boolean; open: boolean; onSaved: () => Promise<void>; onBusyChange: (busy: boolean) => void
}) {
  const controller = useAssistant(scope, enabled, onSaved)
  const [width, setWidth] = useState(420)
  const theme = useTheme(), narrow = useMediaQuery(theme.breakpoints.down('lg'))
  useLayoutEffect(() => { onBusyChange(controller.busy); return () => onBusyChange(false) }, [controller.busy, onBusyChange])
  const panel = <AssistantPanel controller={controller} scope={scope} {...panelProps} />
  if (narrow) return <Drawer anchor="right" open={open} onClose={panelProps.onClose} ModalProps={{ keepMounted: true }} sx={{ '& .MuiDrawer-paper': { width: 'min(100%, 520px)', height: '100dvh' } }}>{panel}</Drawer>
  return <Box sx={{ display: open ? 'flex' : 'none', flexShrink: 0, width: `min(${width}px, 50%)`, minWidth: 310, borderLeft: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
    <Box role="separator" aria-label="Resize assistant" aria-orientation="vertical" aria-valuenow={width} aria-valuemin={320} aria-valuemax={680} tabIndex={0}
      onKeyDown={event => { if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); setWidth(value => Math.max(320, Math.min(680, value + (event.key === 'ArrowLeft' ? 20 : -20)))) } }}
      onPointerDown={event => { event.preventDefault(); event.currentTarget.setPointerCapture(event.pointerId) }}
      onPointerMove={event => { if (event.currentTarget.hasPointerCapture(event.pointerId)) setWidth(Math.max(320, Math.min(680, event.currentTarget.parentElement!.getBoundingClientRect().right - event.clientX))) }}
      onPointerUp={event => { if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId) }}
      sx={{ width: 6, flexShrink: 0, cursor: 'col-resize', touchAction: 'none', '&:hover, &:focus-visible': { bgcolor: 'primary.main' } }} />
    {panel}
  </Box>
}

type Message = ControlledConversationMessage & { turn?: AssistantTurn }
function JsonDetails({ label, value }: { label: string; value: unknown }) {
  if (value === undefined || value === null) return null
  return <Box component="details" sx={{ mt: 1, fontSize: 12, whiteSpace: 'normal' }}><summary>{label}</summary><Box component="pre" sx={{ m: 0, mt: 1, p: 1, bgcolor: 'action.hover', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontSize: 11, maxHeight: 260, overflow: 'auto' }}>{stringifyExactJson(value, 2)}</Box></Box>
}

export function AssistantPanel({ controller: c, scope, onClose, editorBusy, readOnlyReason, guideOnly, links }: {
  controller: AssistantController; scope: AssistantScope; onClose: () => void; editorBusy?: boolean; readOnlyReason?: string
  guideOnly?: boolean; links?: { label: string; href: string }[]
}) {
  const [query, setQuery] = useState(''), [preview, setPreview] = useState(false), [explain, setExplain] = useState(false), [deleteOpen, setDeleteOpen] = useState(false)
  const [sourceIds, setSourceIds] = useState<string[]>([]), [operationIds, setOperationIds] = useState<string[]>([])
  const sources = c.session?.sources
  useEffect(() => {
    const validSources = new Set(sources?.map(source => source.id))
    const validOperations = new Set(sources?.flatMap(source => source.operations.map(operation).filter(Boolean).map(item => item!.id)))
    setSourceIds(ids => ids.filter(id => validSources.has(id)))
    setOperationIds(ids => ids.filter(id => validOperations.has(id)))
  }, [sources])
  const readOnly = readOnlyReason || (guideOnly ? 'Guide only. Open the selected resource editor to make changes.' : '') || (c.session?.editable === false ? c.session.readOnlyReason || 'This resource is read-only.' : '')
  const unavailable = c.status.data && (!c.status.data.enabled || !c.status.data.ready)
  const disabled = Boolean(!c.session || unavailable || editorBusy)
  const messages: Message[] = (c.session?.turns ?? []).flatMap(turn => [
    { id: `${turn.id}:query`, role: 'user', text: turn.query },
    { id: turn.id, role: 'assistant', text: turn.message ?? '', turn },
  ])
  const submit = async (value: string) => {
    if (await c.submit({ query: explain || guideOnly ? `Explain only; do not change the draft.\n\n${value}` : value, previewOnly: preview || Boolean(readOnly) || explain || Boolean(guideOnly), sourceIds, operationIds })) setQuery('')
  }
  return <Box id="configuration-assistant-panel" component="aside" aria-label="Build with AI" sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, minWidth: 0, p: 2, bgcolor: 'background.paper', color: 'text.primary', '& .MuiTypography-root': { overflowWrap: 'anywhere' } }}>
    <Stack direction="row" alignItems="center" gap={1} sx={{ flexShrink: 0 }}><AutoAwesomeOutlined fontSize="small" color="primary" /><Typography fontSize={15} fontWeight={650} sx={{ flex: 1 }}>Build with AI</Typography>
      <Tooltip title="Refresh session"><span><IconButton size="small" aria-label="Refresh assistant session" disabled={c.pending} onClick={() => { void c.status.refetch(); void c.refresh() }}><RefreshOutlined fontSize="small" /></IconButton></span></Tooltip>
      <Tooltip title="Delete session and its sources"><span><IconButton size="small" aria-label="Delete assistant session" disabled={!c.session || c.busy} onClick={() => setDeleteOpen(true)}><DeleteOutline fontSize="small" /></IconButton></span></Tooltip>
      <Tooltip title="Close assistant"><IconButton size="small" aria-label="Close assistant" onClick={onClose}><CloseOutlined fontSize="small" /></IconButton></Tooltip>
    </Stack>
    <Typography variant="caption" color="text.secondary" sx={{ my: 1 }}>{c.session?.displayName ?? scope.resourceId} / {scope.task.split('_').join(' ')}</Typography>
    {!guideOnly && <Typography variant="caption" color="text.secondary">AI saves requested draft changes, including manual values. Review or refine afterward. Publishing remains a separate explicit step.</Typography>}
    {readOnly && <Alert severity="info" sx={{ mt: 1 }}>{readOnly}{!guideOnly && ' Preview only; no live resource is changed.'}</Alert>}
    {Boolean(links?.length) && <Box component="details" sx={{ mt: 1, fontSize: 12, maxHeight: '24dvh', overflow: 'auto', flexShrink: 0 }}><summary>Open resource editor</summary><Stack alignItems="flex-start">{links?.map(link => <Button key={link.href} size="small" component={RouterLink} to={link.href} onClick={onClose}>{link.label}</Button>)}</Stack></Box>}
    {unavailable && <Alert severity="warning" sx={{ mt: 1 }}>{c.status.data?.unavailableReason || 'The configuration assistant is unavailable. Manual editing is still available.'}</Alert>}
    {editorBusy && <Alert severity="info" sx={{ mt: 1 }}>Wait for the editor's current operation to finish.</Alert>}
    {c.pending && <LinearProgress sx={{ my: 1, flexShrink: 0 }} />}
    {!c.session && !c.pending && c.status.data?.ready && <Button onClick={() => void c.newSession()}>Start new session</Button>}
    <AssistantSources controller={c} sourceIds={sourceIds} setSourceIds={setSourceIds} operationIds={operationIds} setOperationIds={setOperationIds} />
    <ControlledConversation<Message> className="configuration-conversation" messages={messages} value={query} onValueChange={setQuery} onSubmit={value => void submit(value)} busy={c.busy} disabled={disabled || c.retryTurn} onCancel={c.running || c.retryTurn ? c.cancel : undefined} error={c.error}
      submitLabel={explain || guideOnly ? 'Explain' : preview || readOnly ? 'Preview' : 'Send'}
      style={{ fontSize: 13 }}
      composerExtras={<>
        {c.retryTurn && <Alert severity="warning" action={<Button disabled={c.pending} onClick={() => void c.retry()}>Retry same turn</Button>}>The turn response was not confirmed. Retry uses the same request key; no second turn is created.</Alert>}
        {!guideOnly && <Stack direction="row" gap={1} flexWrap="wrap"><FormControlLabel sx={{ m: 0, '& .MuiFormControlLabel-label': { fontSize: 12 } }} control={<Switch size="small" checked={preview || Boolean(readOnly)} disabled={c.busy || Boolean(readOnly)} onChange={event => setPreview(event.target.checked)} />} label="Preview only" />
          <FormControlLabel sx={{ m: 0, '& .MuiFormControlLabel-label': { fontSize: 12 } }} control={<Switch size="small" checked={explain} disabled={c.busy} onChange={event => setExplain(event.target.checked)} />} label="Explain only" /></Stack>
        }
        <Typography variant="caption" color="text.secondary">Your request, selected sources and scoped configuration are shared with LoomAI's AI provider. Do not include credentials or unnecessary personal data.</Typography>
      </>}
      renderMessage={message => message.turn ? <TurnView turn={message.turn} controller={c} readOnly={Boolean(readOnly)} editorBusy={editorBusy} /> : <><Typography variant="caption" color="text.secondary">You</Typography><Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{message.text}</Typography></>} />
    <Typography variant="caption" color="text.secondary" sx={{ pt: 1 }}>{c.session ? `Session expires ${new Date(c.session.expiresAt).toLocaleString()}` : ''}</Typography>
    <Box component="style">{`.configuration-conversation form button{font:inherit;min-height:34px;padding:5px 14px;border-radius:4px;border:1px solid currentColor;background:transparent;color:inherit;cursor:pointer}.configuration-conversation form button:disabled{opacity:.45;cursor:default}.configuration-conversation form button:focus-visible,.configuration-conversation textarea:focus-visible{outline:2px solid currentColor;outline-offset:2px}.configuration-conversation [role=alert]{color:inherit}`}</Box>
    <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)} maxWidth="xs" fullWidth><DialogTitle>Delete this assistant session?</DialogTitle><DialogContent>Its conversation and imported sources will be deleted. Saved resource changes are not undone.</DialogContent><DialogActions><Button onClick={() => setDeleteOpen(false)}>Keep session</Button><Button color="error" disabled={c.busy} onClick={async () => { await c.remove(); setDeleteOpen(false); setSourceIds([]); setOperationIds([]) }}>Delete session</Button></DialogActions></Dialog>
  </Box>
}

function TurnView({ turn, controller: c, readOnly, editorBusy }: { turn: AssistantTurn; controller: AssistantController; readOnly: boolean; editorBusy?: boolean }) {
  const latestSaved = [...(c.session?.turns ?? [])].reverse().find(item => item.status === 'SAVED')?.id
  const disabled = c.busy || editorBusy
  const validation = validationSummary(turn)
  return <>
    <Stack direction="row" gap={1} alignItems="center" sx={{ mb: .75 }}><Typography variant="caption" color="text.secondary">Assistant</Typography><Chip size="small" variant="outlined" color={turn.status === 'SAVED' ? validation.outcome === 'attention' ? 'warning' : 'default' : ['FAILED', 'INVALID'].includes(turn.status) ? 'error' : 'default'} label={turn.status.split('_').join(' ')} /></Stack>
    <ValidationOutcome turn={turn} />
    {turn.message && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{turn.message}</Typography>}
    {turn.notice && <Alert severity="info" sx={{ mt: 1 }}>{turn.notice}</Alert>}
    {turn.questions?.map(question => <Alert severity="info" key={question.id} sx={{ mt: 1, whiteSpace: 'normal' }}>{question.question}{question.field && <Typography display="block" variant="caption">{question.field}</Typography>}</Alert>)}
    <ChangeSummary turn={turn} />
    <Box component="details" sx={{ mt: 1, fontSize: 12, whiteSpace: 'normal' }}><summary>Advanced</summary>
    <JsonDetails label={turn.status === 'SAVED' || turn.status === 'UNDONE' ? 'Saved diff' : 'Proposed diff'} value={turn.diff ?? turn.changes} />
    <JsonDetails label="Validation" value={turn.validation} />
    <JsonDetails label="Findings, uncertainty and source references" value={turn.findings} />
    <JsonDetails label="Assumptions and uncertainty" value={turn.assumptions} />
    <JsonDetails label="Source references" value={turn.sourceReferences} />
    <JsonDetails label="Save receipt" value={turn.receipt} />
    <JsonDetails label="Undo receipt" value={turn.undoReceipt} />
    <JsonDetails label="Usage" value={turn.usage} />
    <JsonDetails label="Runtime, usage and accounting" value={turn.runtime} />
    <JsonDetails label="Diagnostics" value={turn.diagnostics} />
    </Box>
    <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mt: 1 }}>
      {turn.status === 'PREVIEW' && <><Button size="small" disabled={disabled} onClick={() => void c.proposal(turn.id, 'check')}>Check preview</Button><Button size="small" disabled={disabled || readOnly} onClick={() => void c.proposal(turn.id, 'apply')}>Save preview to draft</Button></>}
      {turn.status === 'SAVED' && turn.id === latestSaved && <Button size="small" startIcon={<UndoOutlined />} disabled={disabled || readOnly} onClick={() => void c.proposal(turn.id, 'undo')}>Undo saved change</Button>}
    </Stack>
  </>
}
