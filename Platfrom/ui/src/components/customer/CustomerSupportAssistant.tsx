import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, CircularProgress, IconButton, Stack, TextField, Tooltip, Typography } from '@mui/material'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import OpenInFullRounded from '@mui/icons-material/OpenInFullRounded'
import RemoveRounded from '@mui/icons-material/RemoveRounded'
import SwapHorizRounded from '@mui/icons-material/SwapHorizRounded'
import SendRounded from '@mui/icons-material/SendRounded'
import AddCommentOutlined from '@mui/icons-material/AddCommentOutlined'
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded'
import { getSupportCase, getSupportWorkspace } from '../../api/supportApi'
import { useCustomerSupport } from './CustomerSupportContext'
import { SupportWorkspace } from './SupportWorkspace'

const controlLabels: Record<string, string> = { AI_ACTIVE: 'AI assisting', HUMAN_PENDING: 'Waiting for integration team',
  HUMAN_ACTIVE: 'Human support active', AI_RESUME_PENDING: 'Returning to AI', CLOSED: 'Conversation closed' }

export function CustomerSupportAssistant() {
  const support = useCustomerSupport()
  const location = useLocation()
  const navigate = useNavigate()
  const cache = useQueryClient()
  const inPage = location.pathname === '/support' || (location.pathname.startsWith('/support/') && location.pathname !== '/support/requests')
  const hidden = !inPage && support.minimized
  const dockPosition = support.side === 'left'
    ? { left: { xs: 8, md: 'calc(var(--customer-navigation-width, 0px) + 20px)' } }
    : { right: { xs: 8, md: 20 } }
  const [question, setQuestion] = useState('')
  const caseKey = ['support-case', support.customerId, support.actorId, support.caseId]
  const request = useQuery({ queryKey: caseKey, queryFn: () => getSupportCase(support.caseId!), enabled: Boolean(support.caseId), retry: false,
    refetchInterval: data => ['UNKNOWN', 'USER_MESSAGE_PERSISTED'].includes(data.state.data?.initialMessageDelivery || '') ? 5000 : false })
  const current = request.data
  const workspace = useQuery({ queryKey: ['support-workspace', support.customerId, support.actorId, support.caseId], queryFn: () => getSupportWorkspace(support.caseId!),
    enabled: Boolean(current && current.initialMessageDelivery !== 'PENDING'), retry: false, refetchOnWindowFocus: false })
  useEffect(() => {
    if (support.workspaceState?.controlState === 'CLOSED') void cache.invalidateQueries({ queryKey: caseKey })
  }, [support.workspaceState?.controlState, support.caseId])
  const ask = async () => {
    const input = support.pendingBody ?? { summary: question.trim().slice(0, 240), details: question.trim().length > 240 ? question.trim() : '', topic: '', resources: [], diagnosticConsent: false }
    if (await support.submitIntake(input, inPage)) setQuestion('')
  }
  const openFull = support.caseId ? `/support/${encodeURIComponent(support.caseId)}` : '/support'
  return <>
    {!inPage && support.minimized && <Button variant="contained" startIcon={<ChatBubbleOutlineRounded />}
      onClick={() => support.setMinimized(false)} aria-label="Open LoomAI support"
      sx={{ position: 'fixed', bottom: 20, ...dockPosition, zIndex: 1100, borderRadius: 1, boxShadow: 4, py: 1.25, textTransform: 'none' }}>LoomAI Support</Button>}
    <Box data-testid="customer-support-assistant" data-presentation={inPage ? 'page' : 'dock'}
      sx={{ display: hidden ? 'none' : 'flex', flexDirection: 'column', minWidth: 0, bgcolor: 'background.paper',
        ...(inPage ? { mt: 2.5, height: `max(460px, calc(100dvh - ${support.caseId ? 410 : 340}px))`, minHeight: 460 }
          : { position: 'fixed', bottom: { xs: 8, md: 20 }, ...dockPosition, width: { xs: 'calc(100vw - 16px)', sm: 420 },
            height: support.caseId ? 'min(660px, calc(100dvh - 100px))' : 'auto', maxHeight: 'calc(100dvh - 100px)', zIndex: 1100, boxShadow: 12 }),
        border: 1, borderColor: 'divider', borderTop: 2, borderTopColor: 'secondary.main', borderRadius: 1, overflow: 'hidden' }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ px: 2, py: 1.25, borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
          <AutoAwesomeOutlined sx={{ color: 'secondary.main', fontSize: 20 }} />
          <Box sx={{ minWidth: 0 }}><Typography variant="subtitle2">LoomAI Support</Typography>
            <Typography variant="caption" color="text.secondary" noWrap component="div">{support.saving ? 'Preparing your conversation...' : current?.reference ? `${current.reference} · ${current.status !== 'OPEN' ? (current.status === 'RESOLVED' ? 'Resolved request' : 'Cancelled request') : controlLabels[support.workspaceState?.controlState || ''] || 'AI assistance'}` : 'Ask a question or request integration help'}</Typography></Box>
        </Stack>
        <Stack direction="row" spacing={0.25}>
          <Tooltip title="New conversation"><span><IconButton size="small" aria-label="New support conversation" disabled={support.saving || support.pendingIntake} onClick={() => { support.selectCase(null); setQuestion(''); if (inPage) navigate('/support') }}><AddCommentOutlined fontSize="small" /></IconButton></span></Tooltip>
          {!inPage && <>
            <Tooltip title="Open support page"><IconButton component={Link} to={openFull} size="small" aria-label="Open support page"><OpenInFullRounded fontSize="small" /></IconButton></Tooltip>
            <Tooltip title={`Move support to the ${support.side === 'right' ? 'left' : 'right'}`}><IconButton size="small" aria-label={`Move support to the ${support.side === 'right' ? 'left' : 'right'}`} onClick={support.changeSide}><SwapHorizRounded fontSize="small" /></IconButton></Tooltip>
            <Tooltip title="Minimize support"><IconButton size="small" aria-label="Minimize support" onClick={() => support.setMinimized(true)}><RemoveRounded fontSize="small" /></IconButton></Tooltip>
          </>}
        </Stack>
      </Stack>
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', p: current ? 1 : 0 }}>
        {support.intakeError && <Alert severity="warning" sx={{ m: 1 }}>{support.intakeError}</Alert>}
        {support.saving ? <Stack spacing={2} alignItems="center" justifyContent="center" sx={{ flex: 1, p: 4, minHeight: 160 }}><CircularProgress size={25} /><Typography variant="body2">{support.savedCase ? 'Your request is saved. Waiting for the AI...' : 'Saving your question...'}</Typography></Stack>
          : support.caseId ? <>
            {request.isPending && <CircularProgress size={24} sx={{ m: 3 }} />}
            {request.isError && <Alert severity="error">{request.error.message} No new conversation was created.</Alert>}
            {current?.initialMessageDelivery === 'PENDING' && <Alert severity="info">Your request is saved. Open its support page to start the conversation explicitly.</Alert>}
            {workspace.isPending && workspace.fetchStatus === 'fetching' && <CircularProgress size={22} sx={{ m: 3 }} />}
            {workspace.isError && <Alert severity="warning" action={<Button onClick={() => { void workspace.refetch() }}>Retry workspace</Button>}>{workspace.error.message} Your saved request is unchanged.</Alert>}
            {workspace.data && current && current.initialMessageDelivery !== 'PENDING'
              && workspace.data.conversationId === current.episodes[current.episodes.length - 1]?.conversationId
              && <SupportWorkspace caseId={current.id} bootstrap={workspace.data} readOnly={current.status !== 'OPEN'} fillHeight onStateChange={support.setWorkspaceState} />}
          </> : <Box component="form" onSubmit={event => { event.preventDefault(); void ask() }} sx={{ flex: 1, display: 'flex', flexDirection: 'column', p: inPage ? { xs: 2.5, md: 4 } : 2, minHeight: 0 }}>
            {inPage && <Stack spacing={2} alignItems="center" justifyContent="center" sx={{ flex: 1, py: 4 }}>
              <Box sx={{ display: 'grid', placeItems: 'center', height: 54, width: 54, border: 1, borderColor: 'divider', borderRadius: 1, color: 'secondary.main' }}><AutoAwesomeOutlined sx={{ fontSize: 28 }} /></Box>
              <Typography variant="h5" textAlign="center">What are you working on?</Typography>
              <Typography color="text.secondary" variant="body2" textAlign="center">Deployments, integrations, knowledge or your AI workspace.</Typography>
              <Stack direction="row" flexWrap="wrap" justifyContent="center" gap={1}>
                {['How do I embed Engage on my website?', 'How do I add my policy documents?', 'How do I connect human support?'].map(prompt => <Button key={prompt} size="small" variant="outlined" disabled={support.pendingIntake} onClick={() => setQuestion(prompt)}>{prompt}</Button>)}
              </Stack>
            </Stack>}
            <Stack direction="row" spacing={1} alignItems="flex-end" sx={{ mt: inPage ? 'auto' : 0 }}>
              <TextField fullWidth label="Ask LoomAI support" multiline minRows={inPage ? 2 : 1} maxRows={4}
                value={support.pendingBody ? support.pendingBody.summary + (support.pendingBody.details ? '\n' + support.pendingBody.details : '') : question}
                disabled={support.pendingIntake} onChange={event => setQuestion(event.target.value)} inputProps={{ maxLength: 12000 }}
                onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void ask() } }} />
              <Tooltip title={support.pendingIntake ? 'Retry the same submission' : 'Send message'}><span><IconButton type="submit" aria-label={support.pendingIntake ? 'Retry same support question' : 'Send support question'} disabled={!question.trim() && !support.pendingBody}
                sx={{ bgcolor: 'secondary.main', color: 'secondary.contrastText', borderRadius: 1, width: 44, height: 44, '&:hover': { bgcolor: 'secondary.dark' } }}><SendRounded fontSize="small" /></IconButton></span></Tooltip>
            </Stack>
          </Box>}
      </Box>
    </Box>
  </>
}
