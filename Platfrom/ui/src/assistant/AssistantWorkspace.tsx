import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Drawer, FormControlLabel, IconButton, LinearProgress, MenuItem, Stack, Switch, TextField, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import CloseOutlined from '@mui/icons-material/CloseOutlined'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import RefreshOutlined from '@mui/icons-material/RefreshOutlined'
import UndoOutlined from '@mui/icons-material/UndoOutlined'
import TuneOutlined from '@mui/icons-material/TuneOutlined'
import InfoOutlined from '@mui/icons-material/InfoOutlined'
import ExpandMoreOutlined from '@mui/icons-material/ExpandMoreOutlined'
import SendRounded from '@mui/icons-material/SendRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import ShieldOutlined from '@mui/icons-material/ShieldOutlined'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded'
import { alpha } from '@mui/material/styles'
import { ControlledConversation, type ControlledConversationMessage } from '../../../../max-mode-widget/src/controlledConversation'
import { stringifyExactJson } from '../lib/losslessJson'
import { AssistantSources } from './AssistantSources'
import { BuildWithAIButton } from './BuildWithAIButton'
import { operation, scopeIdentity } from './scope'
import { useAssistant, type AssistantController } from './useAssistant'
import type { AssistantScope, AssistantSession, AssistantTurn, ValidationFeedbackPolicy } from './types'
import { ChangeSummary, ValidationOutcome } from './TurnDetails'
import { turnStatusLabel, validationSummary } from './turnPresentation'
import { feedbackCeiling, feedbackPolicy, feedbackProgress, feedbackReason } from './validationFeedback'
import { assistantPage, editorContext, userTurnText } from './pagePresentation'

type Props = {
  scope: AssistantScope | null
  children: ReactNode | ((state: { revision: number }) => ReactNode)
  onSaved: () => Promise<void>
  editorBusy?: boolean
  readOnlyReason?: string
  initiallyOpen?: boolean
  visibilityKey?: string
  guideOnly?: boolean
  links?: { label: string; href: string }[]
  setupLinks?: { label: string; href: string }[]
  reviewHref?: string
}

type SourceRequest = { notice: string; load: (sessionId: string) => Promise<AssistantSession>; scope?: AssistantScope; prepare?: boolean; cancel?: () => Promise<void> }
type PendingSource = SourceRequest & { id: number; identity: string }
const AssistantSourceContext = createContext<((request: SourceRequest) => void) | null>(null)
export const useAssistantSource = () => useContext(AssistantSourceContext)

export function AssistantWorkspace({ scope: baseScope, children, onSaved, editorBusy, readOnlyReason, initiallyOpen = false, visibilityKey, guideOnly, links, setupLinks, reviewHref }: Props) {
  const baseIdentity = baseScope ? scopeIdentity(baseScope) : null
  const parentIdentity = `${baseIdentity}:${JSON.stringify(baseScope?.editorContext ?? null)}`
  const [override, setOverride] = useState<{ parent: string | null; scope: AssistantScope } | null>(null)
  const scope = override?.parent === parentIdentity ? override.scope : baseScope
  const identity = scope ? scopeIdentity(scope) : null
  const visibilityIdentity = visibilityKey ?? identity
  const [visibility, setVisibility] = useState({ identity: visibilityIdentity, open: initiallyOpen, activated: initiallyOpen })
  const current = visibility.identity === visibilityIdentity ? visibility : { identity: visibilityIdentity, open: initiallyOpen, activated: initiallyOpen }
  if (visibility.identity !== visibilityIdentity) setVisibility(current)
  const open = Boolean(scope && current.open)
  const [sourceRequest, setSourceRequest] = useState<PendingSource | null>(null)
  const sourceSequence = useRef(0)
  const requestSource = useCallback((request: SourceRequest) => {
    if (!identity) return
    if (request.scope) setOverride({ parent: parentIdentity, scope: request.scope })
    setVisibility({ identity: visibilityKey ?? (request.scope ? scopeIdentity(request.scope) : visibilityIdentity), open: true, activated: true })
    setSourceRequest({ ...request, id: ++sourceSequence.current, identity: request.scope ? scopeIdentity(request.scope) : identity })
  }, [identity, parentIdentity, visibilityIdentity, visibilityKey])
  const [activity, setActivity] = useState({ identity, busy: false })
  const busy = activity.identity === identity && activity.busy
  const [revision, setRevision] = useState(0)
  const opener = useRef<HTMLButtonElement>(null)
  const savedCallback = useRef(onSaved); savedCallback.current = onSaved
  const refreshEditor = useCallback(async () => { await savedCallback.current(); setRevision(value => value + 1) }, [])
  const reportActivity = useCallback((value: boolean) => setActivity({ identity, busy: value }), [identity])
  const close = () => { setVisibility({ ...current, open: false }); requestAnimationFrame(() => opener.current?.focus()) }
  return <AssistantSourceContext.Provider value={scope ? requestSource : null}><Box data-assistant-workspace data-assistant-open={open} sx={{ display: 'flex', flexDirection: 'column', flex: 1, width: '100%', height: '100%', minWidth: 0, minHeight: 0 }}>
    {scope && <Stack direction="row" alignItems="center" justifyContent="flex-end" sx={{ borderBottom: 1, borderColor: 'divider', px: 2, py: .5, flexShrink: 0 }}>
      {busy && <Typography role="status" variant="caption" sx={{ mr: 'auto' }}>Assistant working...</Typography>}
      <BuildWithAIButton ref={opener} aria-expanded={open} aria-controls="configuration-assistant-panel" onClick={() => setVisibility({ identity: visibilityIdentity, activated: true, open: !open })} />
    </Stack>}
    <Box sx={{ display: 'flex', flex: 1, minHeight: 0, minWidth: 0 }}>
      {/* Scope changes reset the assistant, never an editor's retained manual buffer. */}
      <Box sx={{ flex: 1, minHeight: 0, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, minWidth: 0, overflow: 'auto' }}>
          {typeof children === 'function' ? children({ revision }) : children}
        </Box>
      </Box>
      {scope && <ScopedAssistant key={identity} scope={scope} enabled={current.activated} open={open} onSaved={refreshEditor} onBusyChange={reportActivity}
        sourceRequest={sourceRequest?.identity === identity ? sourceRequest : null}
        onClose={close} editorBusy={editorBusy} readOnlyReason={readOnlyReason} guideOnly={guideOnly} links={links} setupLinks={setupLinks} reviewHref={reviewHref} />}
    </Box>
  </Box></AssistantSourceContext.Provider>
}

export function useAssistantPanelInputs() {
  const [query, setQuery] = useState(''), [preview, setPreview] = useState(false), [explain, setExplain] = useState(false)
  const [sourceIds, setSourceIds] = useState<string[]>([]), [operationIds, setOperationIds] = useState<string[]>([])
  const [feedbackPreference, setFeedbackPreference] = useState<Partial<ValidationFeedbackPolicy>>({})
  const [optionsOpen, setOptionsOpen] = useState(false), [prepareOpen, setPrepareOpen] = useState(false)
  return { query, setQuery, preview, setPreview, explain, setExplain, sourceIds, setSourceIds, operationIds, setOperationIds, feedbackPreference, setFeedbackPreference, optionsOpen, setOptionsOpen, prepareOpen, setPrepareOpen }
}

function ScopedAssistant({ scope, enabled, open, onSaved, onBusyChange, sourceRequest, ...panelProps }: Omit<Parameters<typeof AssistantPanel>[0], 'controller' | 'inputs'> & {
  enabled: boolean; open: boolean; onSaved: () => Promise<void>; onBusyChange: (busy: boolean) => void; sourceRequest: PendingSource | null
}) {
  const controller = useAssistant(scope, enabled, onSaved)
  // This scope-keyed owner survives the desktop/Drawer panel remount.
  const inputs = useAssistantPanelInputs()
  const [attaching, setAttaching] = useState(false), [sourceError, setSourceError] = useState('')
  const attemptedSource = useRef<number | null>(null), live = useRef(true), sourceStopped = useRef(false)
  useEffect(() => { live.current = true; return () => { live.current = false } }, [])
  const attachSource = async () => {
    if (!sourceRequest || !controller.session || controller.busy || attaching) return
    attemptedSource.current = sourceRequest.id; sourceStopped.current = false; setAttaching(true); setSourceError('')
    try {
      const before = new Set(controller.session.sources.map(source => source.id))
      const session = await sourceRequest.load(controller.session.id)
      if (!live.current) return
      if (sourceStopped.current) throw new Error('Evaluation stopped. No optimization request was sent.')
      if (!await controller.refresh()) throw new Error('The source was attached, but the assistant session could not be refreshed. Retry to review it.')
      if (!live.current) return
      if (sourceStopped.current) throw new Error('Evaluation stopped. No optimization request was sent.')
      const added = session.sources.filter(source => !before.has(source.id))
      inputs.setSourceIds(current => [...new Set([...current, ...added.map(source => source.id)])])
      inputs.setOperationIds(current => [...new Set([...current, ...added.flatMap(source => source.operations.map(operation).filter(Boolean).map(item => item!.id))])])
      if (sourceRequest.prepare) await controller.submit({ query: '', command: 'PREPARE', editorContext: editorContext(scope),
        previewOnly: false, sourceIds: added.map(source => source.id), operationIds: [],
        validationFeedback: feedbackPolicy(controller.status.data?.validationFeedback, {}) })
    } catch (failure) { if (live.current) setSourceError(failure instanceof Error ? failure.message : 'The source could not be attached.') }
    finally { if (live.current) setAttaching(false) }
  }
  const stopSource = async () => {
    sourceStopped.current = true
    try { await sourceRequest?.cancel?.() }
    catch (failure) { if (live.current) setSourceError(failure instanceof Error ? failure.message : 'The evaluation could not be stopped. Inspect its retained status.') }
  }
  useEffect(() => {
    if (sourceRequest && attemptedSource.current !== sourceRequest.id) void attachSource()
  }, [sourceRequest, controller.session, controller.busy, attaching])
  const [width, setWidth] = useState(420)
  const theme = useTheme(), narrow = useMediaQuery(theme.breakpoints.down('lg'))
  useLayoutEffect(() => { onBusyChange(controller.busy || attaching); return () => onBusyChange(false) }, [controller.busy, attaching, onBusyChange])
  const panel = <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, minWidth: 0 }}>
    {sourceRequest && <Box sx={{ p: 2, pb: 0 }}><Alert severity="info">{sourceRequest.notice}</Alert>
      {attaching && <Alert severity="info" sx={{ mt: 1 }}>Preparing the selected context{sourceRequest.prepare ? ' for document optimization.' : '. No model request has started.'}</Alert>}
      {attaching && sourceRequest.cancel && <Button color="error" onClick={() => void stopSource()}>Stop evaluation</Button>}
      {sourceError && <Alert severity="error" sx={{ mt: 1 }} action={<Button disabled={controller.busy || attaching} onClick={() => void attachSource()}>Retry</Button>}>{sourceError}</Alert>}
    </Box>}
    <AssistantPanel controller={{ ...controller, pending: controller.pending || attaching, busy: controller.busy || attaching }} inputs={inputs} scope={scope}
      {...panelProps} editorBusy={panelProps.editorBusy || attaching || Boolean(sourceError)} />
  </Box>
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

export function AssistantPanel({ controller: c, inputs, scope, onClose, editorBusy, readOnlyReason, guideOnly: requestedGuideOnly, links, setupLinks, reviewHref }: {
  controller: AssistantController; scope: AssistantScope; onClose: () => void; editorBusy?: boolean; readOnlyReason?: string
  inputs: ReturnType<typeof useAssistantPanelInputs>
  guideOnly?: boolean; links?: { label: string; href: string }[]
  setupLinks?: { label: string; href: string }[]; reviewHref?: string
}) {
  const { query, setQuery, preview, setPreview, explain, setExplain, sourceIds, setSourceIds, operationIds, setOperationIds, feedbackPreference, setFeedbackPreference } = inputs
  const { optionsOpen, setOptionsOpen, prepareOpen, setPrepareOpen } = inputs
  const context = editorContext(scope), page = assistantPage(scope)
  const guideOnly = requestedGuideOnly || scope.task === 'KIT' || context.section === 'ACTION_REVIEWS'
  const hasTurns = Boolean(c.session?.turns.length), showPreparation = !hasTurns || prepareOpen
  const turns = c.session?.turns
  const needsInput = turns?.[turns.length - 1]?.status === 'NEEDS_INPUT'
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [sessionOptions, setSessionOptions] = useState(false)
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
  const feedbackCapability = c.status.data?.validationFeedback
  const correctionCeiling = feedbackCeiling(feedbackCapability)
  const correctionAllowed = !explain && !guideOnly && scope.task !== 'KIT'
  const validationFeedback = feedbackPolicy(feedbackCapability, feedbackPreference, correctionAllowed)
  const correctionLimit = feedbackPolicy(feedbackCapability, { ...feedbackPreference, enabled: true }).maxCorrections
  const submitLabel = explain || guideOnly ? 'Explain' : preview || readOnly ? 'Preview' : 'Send'
  const messages: Message[] = (c.session?.turns ?? []).flatMap(turn => [
    { id: `${turn.id}:query`, role: 'user', text: userTurnText(turn, scope) },
    { id: turn.id, role: 'assistant', text: turn.message ?? '', turn },
  ])
  const submit = async (value: string, command?: 'PREPARE') => {
    if (command && (value.length > 6000 || explain && !guideOnly)) return
    if (await c.submit({ query: !command && (explain || guideOnly) ? `Explain only; do not change the draft.\n\n${value}` : value,
      ...(command ? { command } : {}), editorContext: context,
      previewOnly: preview || Boolean(readOnly) || explain || Boolean(guideOnly), sourceIds, operationIds, validationFeedback })) {
      setQuery(''); setPrepareOpen(false)
    }
  }
  return <Box id="configuration-assistant-panel" component="aside" aria-label="Build with AI" sx={theme => ({
    flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, minWidth: 0, p: 2,
    borderTop: 3, borderTopColor: 'primary.main', bgcolor: 'background.paper', color: 'text.primary',
    '& .MuiTypography-root': { overflowWrap: 'anywhere' },
    '& .configuration-conversation [role="log"] > article[aria-label="You"]': {
      maxWidth: '92%', ml: 'auto', my: 1, p: '10px 12px !important', borderRadius: '6px',
      bgcolor: alpha(theme.palette.primary.main, .1), whiteSpace: 'normal !important',
    },
    '& .configuration-conversation [role="log"] > article[aria-label="Assistant"]': { py: '14px !important', whiteSpace: 'normal !important' },
    '& .configuration-conversation textarea': {
      borderColor: `${theme.palette.divider} !important`, borderRadius: '6px !important',
      bgcolor: `${theme.palette.background.default} !important`, lineHeight: 1.6,
      '&:focus': { borderColor: `${theme.palette.primary.main} !important` },
      '&::placeholder': { color: theme.palette.text.secondary, opacity: 1 },
    },
    '& .configuration-conversation button[type="submit"]': {
      width: 38, height: 36, display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      bgcolor: `${theme.palette.primary.main} !important`, color: `${theme.palette.primary.contrastText} !important`,
      borderColor: 'transparent !important',
    },
  })}>
    <Stack direction="row" alignItems="center" gap={1} sx={{ flexShrink: 0 }}><AutoAwesomeOutlined color="primary" /><Typography fontSize={20} fontWeight={700} sx={{ flex: 1 }}>Build with AI</Typography>
      <Tooltip title="Session options"><IconButton size="small" aria-label="Assistant session options" aria-expanded={sessionOptions} onClick={() => setSessionOptions(!sessionOptions)}><MoreHorizRounded fontSize="small" /></IconButton></Tooltip>
      <Tooltip title="Close assistant"><IconButton size="small" aria-label="Close assistant" onClick={onClose}><CloseOutlined fontSize="small" /></IconButton></Tooltip>
    </Stack>
    {sessionOptions && <Box component="section" aria-label="Assistant session settings" sx={{ mt: 1, p: 1, bgcolor: 'action.hover', borderRadius: 1, flexShrink: 0, maxHeight: 140, overflow: 'auto' }}>
      <Stack direction="row" gap={1} alignItems="center">
        <Button size="small" startIcon={<RefreshOutlined />} aria-label="Refresh assistant session" disabled={c.pending} onClick={() => { void c.status.refetch(); void c.refresh() }}>Refresh</Button>
        <Button size="small" color="error" startIcon={<DeleteOutline />} aria-label="Delete assistant session" disabled={!c.session || c.busy} onClick={() => setDeleteOpen(true)}>Delete session</Button>
      </Stack>
      <Typography variant="caption" color="text.secondary" display="block" sx={{ overflowWrap: 'anywhere' }}>Resource: {scope.resourceId}</Typography>
      {context.selectedDefinitionIds?.length ? <Typography variant="caption" display="block">Selected: {context.selectedDefinitionIds.join(', ')}</Typography> : null}
      {c.session && <Typography variant="caption" color="text.secondary" display="block">Session expires {new Date(c.session.expiresAt).toLocaleString()}</Typography>}
    </Box>}
    <Box component="section" aria-label="Page guidance" sx={{ my: 1, flexShrink: 0 }}>
      <Typography fontSize={13} color="text.secondary" sx={{ mb: 1.5 }}>{c.session?.displayName ?? (scope.resourceKind === 'DEPLOYMENT_DRAFT' ? 'Your Smart Brain' : 'Your workspace')} · {page.title}</Typography>
      <Stack direction="row" alignItems="center" gap={.5}><Typography fontSize={12} fontWeight={600} color="text.secondary" sx={{ flex: 1 }}>{guideOnly ? 'Plan your next step' : hasTurns ? 'Your current task' : 'Build from your requirements'}</Typography>
        {hasTurns && <Tooltip title={showPreparation ? 'Collapse page guidance' : 'Show page guidance'}><IconButton size="small" aria-label="Page guidance" aria-expanded={showPreparation} onClick={() => setPrepareOpen(!prepareOpen)}><ExpandMoreOutlined fontSize="small" sx={{ transform: showPreparation ? 'rotate(180deg)' : undefined }} /></IconButton></Tooltip>}
      </Stack>
      {showPreparation && <Typography variant="body2" sx={{ mt: .5, fontSize: 13, lineHeight: 1.6 }}>{page.welcome}</Typography>}
    </Box>
    {Boolean(setupLinks?.length) && <Stack component="nav" aria-label="AI setup steps" sx={{ mb: 1, flexShrink: 0 }}>{setupLinks?.map(link => <Button key={link.href} component={RouterLink} to={link.href} disabled={editorBusy} endIcon={<ArrowForwardRounded fontSize="small" />} sx={{ justifyContent: 'space-between', textAlign: 'left', px: 0, py: 1, borderBottom: 1, borderColor: 'divider', fontWeight: 600 }}>{link.label}</Button>)}</Stack>}
    {readOnly && <Alert severity="info" sx={{ mt: 1 }}>{readOnly}{!guideOnly && ' Preview only; no live resource is changed.'}</Alert>}
    {Boolean(links?.length) && <Box component="details" sx={{ mt: 1, fontSize: 12, maxHeight: '24dvh', overflow: 'auto', flexShrink: 0 }}><summary>Open resource editor</summary><Stack alignItems="flex-start">{links?.map(link => <Button key={link.href} size="small" component={RouterLink} to={link.href} onClick={onClose}>{link.label}</Button>)}</Stack></Box>}
    {unavailable && <Alert severity="warning" sx={{ mt: 1 }}>{c.status.data?.unavailableReason || 'The configuration assistant is unavailable. Manual editing is still available.'}</Alert>}
    {editorBusy && <Alert severity="info" sx={{ mt: 1 }}>Wait for the editor's current operation to finish.</Alert>}
    {c.pending && <LinearProgress sx={{ my: 1, flexShrink: 0 }} />}
    {!c.session && !c.pending && c.status.data?.ready && <Button onClick={() => void c.newSession()}>Start new session</Button>}
    <AssistantSources controller={c} scope={scope} sourceIds={sourceIds} setSourceIds={setSourceIds} operationIds={operationIds} setOperationIds={setOperationIds} />
    <ControlledConversation<Message> className="configuration-conversation" messages={messages} value={query} onValueChange={setQuery} onSubmit={value => void submit(value)} busy={c.busy} disabled={disabled || c.retryTurn} onCancel={c.running || c.retryTurn ? c.cancel : undefined} error={c.error}
      submitLabel={submitLabel}
      submitContent={<SendRounded fontSize="small" />}
      placeholder={hasTurns ? 'Add a detail or ask for a change...' : guideOnly ? 'What would you like to set up?' : 'Share what you want your AI to do...'}
      style={{ fontSize: 13, marginTop: 8 }}
      composerExtras={<>
        {c.retryTurn && <Alert severity="warning" action={<Button disabled={c.pending} onClick={() => void c.retry()}>Retry same turn</Button>}>The turn response was not confirmed. Retry uses the same request key; no second turn is created.</Alert>}
        <Stack direction="row" gap={.5} alignItems="center" flexWrap="wrap">
          {showPreparation ? <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeOutlined fontSize="small" />} disabled={disabled || c.busy || c.retryTurn || query.length > 6000 || explain && !guideOnly}
            onClick={() => void submit(query, 'PREPARE')} sx={{ mr: 'auto', minWidth: 0, textAlign: 'left', fontSize: 12 }}>{page.command}{preview || readOnly && !guideOnly ? ' (preview)' : ''}</Button>
            : <Button type="button" size="small" startIcon={<AutoAwesomeOutlined fontSize="small" />} disabled={c.busy || disabled} onClick={() => setPrepareOpen(true)} sx={{ mr: 'auto', fontSize: 12 }}>New preparation</Button>}
          <Tooltip title="Turn options"><IconButton type="button" size="small" aria-label="Turn options" aria-expanded={optionsOpen} onClick={() => setOptionsOpen(!optionsOpen)}><TuneOutlined fontSize="small" /></IconButton></Tooltip>
          <Tooltip title="Your request, selected documents and scoped configuration are shared with LoomAI's AI provider. Do not include credentials or unnecessary personal data."><IconButton type="button" size="small" aria-label="AI data sharing"><InfoOutlined sx={{ fontSize: 16 }} /></IconButton></Tooltip>
        </Stack>
        {showPreparation && query.length > 6000 && <Typography role="alert" variant="caption" color="error.main">Keep the optional preparation hint to 6,000 characters. Typed requests can use up to 8,000.</Typography>}
        {needsInput && !showPreparation && <Typography variant="caption" color="text.secondary">Answer the question, then {submitLabel}.</Typography>}
        {!needsInput && <Typography variant="caption" color="text.secondary">{showPreparation ? 'Optional focus hint, or type a request' : 'Type a follow-up request'}</Typography>}
        {optionsOpen && <Box component="section" aria-label="Turn options" sx={{ maxHeight: 144, overflowY: 'auto', borderTop: 1, borderColor: 'divider', pt: .5 }}>
        {!guideOnly && <Stack direction="row" gap={1} flexWrap="wrap"><FormControlLabel sx={{ m: 0, '& .MuiFormControlLabel-label': { fontSize: 12 } }} control={<Switch size="small" checked={preview || Boolean(readOnly)} disabled={c.busy || Boolean(readOnly)} onChange={event => setPreview(event.target.checked)} />} label="Preview only" />
          <FormControlLabel sx={{ m: 0, '& .MuiFormControlLabel-label': { fontSize: 12 } }} control={<Switch size="small" checked={explain} disabled={c.busy} onChange={event => setExplain(event.target.checked)} />} label="Explain only" /></Stack>
        }
        <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap">
          <FormControlLabel sx={{ m: 0, minWidth: 0, '& .MuiFormControlLabel-label': { fontSize: 12, overflowWrap: 'anywhere' } }}
            control={<Switch size="small" checked={validationFeedback.enabled} disabled={c.busy || disabled || !correctionAllowed || !correctionCeiling}
              onChange={event => setFeedbackPreference(value => ({ ...value, enabled: event.target.checked }))} />} label="Let AI fix validation errors" />
          <TextField select size="small" label="Max corrections" value={correctionLimit || 1} disabled={c.busy || disabled || !validationFeedback.enabled}
            onChange={event => setFeedbackPreference(value => ({ ...value, maxCorrections: Number(event.target.value) }))}
            sx={{ width: 128, flexShrink: 0, mt: .5 }}>
            {Array.from({ length: correctionCeiling || 1 }, (_, index) => <MenuItem key={index + 1} value={index + 1}>{index + 1}</MenuItem>)}
          </TextField>
        </Stack>
        </Box>}
      </>}
      renderMessage={message => message.turn ? <TurnView turn={message.turn} controller={c} readOnly={Boolean(readOnly)} editorBusy={editorBusy} reviewHref={reviewHref} onReview={onClose} /> : <><Typography variant="caption" color="text.secondary">You</Typography><Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{message.text}</Typography></>} />
    <Stack direction="row" alignItems="flex-start" gap={.75} sx={{ pt: 1, flexShrink: 0 }}><ShieldOutlined sx={{ fontSize: 14, color: 'text.secondary', mt: .2 }} /><Typography variant="caption" color="text.secondary">{guideOnly ? 'Guidance only. No draft changes.' : explain ? 'Explanation only. No draft changes.' : preview || readOnly ? 'Preview only. No draft save.' : 'Saves draft only. Review or undo after. Never publishes.'}</Typography></Stack>
    <Box component="style">{`.configuration-conversation form button:not(.MuiButtonBase-root){font:inherit;min-height:36px;padding:6px 12px;border-radius:6px;border:1px solid currentColor;background:transparent;color:inherit;cursor:pointer}.configuration-conversation form button:disabled{opacity:.45;cursor:default}.configuration-conversation form button:focus-visible,.configuration-conversation textarea:focus-visible{outline:2px solid currentColor;outline-offset:2px}.configuration-conversation [role=alert]{color:inherit}`}</Box>
    <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)} maxWidth="xs" fullWidth><DialogTitle>Delete this assistant session?</DialogTitle><DialogContent>Its conversation and imported sources will be deleted. Saved resource changes are not undone.</DialogContent><DialogActions><Button onClick={() => setDeleteOpen(false)}>Keep session</Button><Button color="error" disabled={c.busy} onClick={async () => { await c.remove(); setDeleteOpen(false); setSourceIds([]); setOperationIds([]) }}>Delete session</Button></DialogActions></Dialog>
  </Box>
}

function TurnView({ turn, controller: c, readOnly, editorBusy, reviewHref, onReview }: { turn: AssistantTurn; controller: AssistantController; readOnly: boolean; editorBusy?: boolean; reviewHref?: string; onReview: () => void }) {
  const hasSaved = turn.status === 'SAVED' || Boolean(turn.operations?.length && turn.status !== 'UNDONE')
  const latestSaved = [...(c.session?.turns ?? [])].reverse().find(item => item.status === 'SAVED' || item.operations?.length && item.status !== 'UNDONE')?.id
  const disabled = c.busy || editorBusy
  const validation = validationSummary(turn)
  const progress = feedbackProgress(turn)
  return <>
    <Stack direction="row" gap={1} alignItems="center" sx={{ mb: .75 }}><AutoAwesomeOutlined sx={{ fontSize: 15, color: 'primary.main' }} /><Typography variant="caption" color="text.secondary">Assistant</Typography><Chip data-assistant-turn-status={turn.status} size="small" variant="outlined" color={turn.status === 'SAVED' ? validation.outcome === 'attention' ? 'warning' : 'default' : ['FAILED', 'INVALID'].includes(turn.status) ? 'error' : 'default'} label={turnStatusLabel(turn.status)} /></Stack>
    <ValidationOutcome turn={turn} />
    {turn.workflow?.selection && <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: .75 }}>
      {turn.workflow.selection.name}
    </Typography>}
    {progress && <Typography role="status" variant="caption" sx={{ display: 'block', mb: .75 }}>{progress}</Typography>}
    {turn.message && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{turn.message}</Typography>}
    {hasSaved && <Stack direction="row" gap={.75} alignItems="center" sx={{ mt: 1 }}>
      <CheckCircleOutlineRounded fontSize="small" color={validation.outcome === 'attention' ? 'warning' : validation.outcome === 'passed' ? 'success' : 'action'} />
      <Typography variant="caption" fontWeight={600}>Saved to draft · Not published</Typography>
    </Stack>}
    {turn.notice && <Alert severity="info" sx={{ mt: 1 }}>{turn.notice}</Alert>}
    {turn.operations?.length ? <Box component="section" aria-label="Saved operations" sx={{ mt: 1 }}>
      {turn.operations.map((operation, index) => <Typography variant="caption" display="block" key={operation.operationId}>
        {index + 1}. {operation.action === 'authoring_undo_request' ? 'Draft change undone' : 'Draft change saved'} · Not published
      </Typography>)}
    </Box> : null}
    {turn.workflow?.usage && <Typography variant="caption" display="block" sx={{ mt: 1 }}>
      {turn.workflow.usage.actions}{turn.workflow.limits ? ` / ${turn.workflow.limits.actions}` : ''} tool steps · {turn.workflow.usage.writes}{turn.workflow.limits ? ` / ${turn.workflow.limits.writes}` : ''} {turn.workflow.usage.writes === 1 ? 'write attempt' : 'write attempts'} · {turn.workflow.usage.modelCalls}{turn.workflow.limits ? ` / ${turn.workflow.limits.modelCalls}` : ''} model calls · {turn.workflow.usage.corrections}{turn.workflow.limits ? ` / ${turn.workflow.limits.corrections}` : ''} corrections
    </Typography>}
    {turn.workflow?.stopCode && <Alert severity="warning" sx={{ mt: 1 }}>Work stopped: {turn.workflow.stopCode}. Review any saved operations before continuing.</Alert>}
    {turn.questions?.map(question => <Alert severity="info" key={question.id} sx={{ mt: 1, whiteSpace: 'normal' }}>{question.question}{question.field && <Typography display="block" variant="caption">{question.field}</Typography>}</Alert>)}
    <ChangeSummary turn={turn} compact />
    {turn.feedbackStopReason && <Typography variant="caption" sx={{ display: 'block', mt: 1 }}>{feedbackReason(turn.feedbackStopReason)}</Typography>}
    {(turn.validationFeedback || turn.attempts?.length || turn.feedbackStopReason) && <Box component="details" sx={{ mt: 1, minWidth: 0, fontSize: 12, whiteSpace: 'normal', overflowWrap: 'anywhere' }}>
      <summary>{turn.workflow ? 'Correction preference' : `Validation feedback (${turn.attempts?.length ?? 0} attempts)`}</summary>
      <JsonDetails label="Turn correction policy" value={turn.validationFeedback} />
      <Box component="ol" sx={{ pl: 2, m: 0 }}>{turn.attempts?.map((attempt, index) => <Box component="li" key={`${attempt.number}:${index}`} sx={{ py: 1, borderBottom: 1, borderColor: 'divider', minWidth: 0 }}>
        <Typography variant="body2" fontWeight={600}>Attempt {attempt.number} / {attempt.status}</Typography>
        {attempt.startedAt && <Typography variant="caption" display="block">Started: {attempt.startedAt}</Typography>}
        <Typography variant="caption" display="block">{attempt.completedAt ? `Completed: ${attempt.completedAt}` : 'Not completed'}</Typography>
        {attempt.message && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{attempt.message}</Typography>}
        <JsonDetails label="Attempt findings" value={attempt.findings} />
        <JsonDetails label="Attempt changes" value={attempt.changes} />
        <JsonDetails label="Attempt runtime" value={attempt.runtime} />
      </Box>)}</Box>
    </Box>}
    <Box component="details" sx={{ mt: 1, fontSize: 12, whiteSpace: 'normal' }}><summary>Advanced</summary>
    <Typography variant="caption" display="block">Turn status: {turn.status}</Typography>
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
    <JsonDetails label="Brain workflow" value={turn.workflow} />
    <JsonDetails label="Operation receipts" value={turn.operations} />
    </Box>
    <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mt: 1 }}>
      {hasSaved && turn.id === latestSaved && reviewHref && <Button size="small" variant="outlined" disabled={disabled} component={RouterLink} to={reviewHref} onClick={onReview} endIcon={<ArrowForwardRounded />}>Review draft</Button>}
      {turn.status === 'PREVIEW' && <><Button size="small" disabled={disabled} onClick={() => void c.proposal(turn.id, 'check')}>Check preview</Button><Button size="small" disabled={disabled || readOnly} onClick={() => void c.proposal(turn.id, 'apply')}>Save preview to draft</Button></>}
      {hasSaved && turn.id === latestSaved && <Button size="small" startIcon={<UndoOutlined />} disabled={disabled || readOnly} onClick={() => void c.proposal(turn.id, 'undo')}>Undo saved changes</Button>}
    </Stack>
  </>
}
