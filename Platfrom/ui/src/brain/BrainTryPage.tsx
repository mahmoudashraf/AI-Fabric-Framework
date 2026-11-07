import { useRef, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, Chip, CircularProgress, Stack, Tab, Tabs, TextField, Typography } from '@mui/material'
import SendOutlined from '@mui/icons-material/SendOutlined'
import OpenInNewOutlined from '@mui/icons-material/OpenInNewOutlined'
import { fetchDeploymentTargetContext, queryDeploymentPocChat, type DeploymentPocChatQueryResponse } from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { safeCustomerAppUrl } from '../components/customer/customerResourceModels'
import { useBrainWorkspace, useBrainEditor } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'
import { useBrainApps } from './BrainExperiencePages'

export function BrainTryPage() {
  const reviewOnly = import.meta.env.VITE_SMART_BRAIN_REVIEW === 'true'
  const { brainId, target, release, href } = useBrainWorkspace()
  const { workspace } = useDeploymentWorkspace()
  const { apps } = useBrainApps()
  const [tab, setTab] = useState('experience')
  const [question, setQuestion] = useState('')
  const [turns, setTurns] = useState<Array<{ scope: string; question: string; result: DeploymentPocChatQueryResponse }>>([])
  const [loaded, setLoaded] = useState(false)
  const scope = `${brainId}:${target?.targetProfileId}:${target?.releaseId}`
  const activeScope = useRef(scope)
  activeScope.current = scope
  const context = useQuery({ queryKey: ['deployment-target-context', target?.deploymentId, target?.targetProfileId, target?.releaseId],
    queryFn: () => fetchDeploymentTargetContext(target!), enabled: Boolean(target), retry: false })
  const current = Boolean(target && context.data && workspace?.lifecycle.liveVersionId === context.data.versionId
    && workspace.deployment.runtimeBaseUrl?.replace(/\/$/, '') === context.data.runtimeBaseUrl.replace(/\/$/, ''))
  const matching = apps.filter(app => app.status === 'ACTIVE' && app.ready && app.releaseId === release?.id)
  const app = matching.find(item => item.deliveryMode === 'standalone')
  const url = safeCustomerAppUrl(app?.appUrl ?? null)
  const query = useMutation({ mutationFn: (input: { question: string; conversationId?: string; scope: string }) => queryDeploymentPocChat(brainId, {
    query: input.question, conversationId: input.conversationId, authPath: 'PLATFORM_PRIVATE',
  }), onSuccess: (result, input) => { if (activeScope.current === input.scope) { setTurns(previous => [...previous, { scope: input.scope, question: input.question, result }]); setQuestion('') } } })
  useBrainEditor({ dirty: Boolean(question), busy: query.isPending,
    discard: question ? () => setQuestion('') : undefined,
    message: 'Tests use applied configuration. Provider usage and normal action permissions apply.' })
  const currentTurns = turns.filter(turn => turn.scope === scope)
  const last = currentTurns[currentTurns.length - 1]?.result
  return <><BrainPageHeader title="Try your AI before customers do" description="Open the connected customer experience, or inspect a focused test result." />
    <Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}><Tab value="experience" label="Customer experience" /><Tab value="technical" label="Technical test" /></Tabs>
    {tab === 'experience' ? <Box sx={{ ...brainPaneSx, flex: 1, display: 'flex', flexDirection: 'column' }}>
      {!url ? <Alert severity="info">No active standalone Engage app is assigned to this exact release. You can manage website installations and app assignments in Channels. <Button component={RouterLink} to={href('channels')}>Open channels</Button></Alert>
        : <><Stack direction="row" gap={1.5} alignItems="center" sx={{ mb: 2 }}><Typography variant="body2" sx={{ flex: 1 }}>{app!.displayName}</Typography>
          {!loaded && <Button variant="contained" disabled={reviewOnly} onClick={() => setLoaded(true)}>Load workspace</Button>}
          <Button component="a" href={url} target="_blank" rel="noopener noreferrer" endIcon={<OpenInNewOutlined />}>{reviewOnly ? 'Open live app separately' : 'Open app'}</Button></Stack>
          {reviewOnly && <Alert severity="info" sx={{ mb: 2 }}>Embedding is disabled in this read-only review. The external app uses your live service, outside this preview.</Alert>}
          {loaded ? <Box component="iframe" title="Connected Engage workspace" src={url} sx={{ width: '100%', flex: 1, minHeight: 400, border: 1, borderColor: 'divider', borderRadius: 1 }} />
            : <Alert severity="info">Load the actual branded workspace to test its tools, attachments, sign-in and human handover.</Alert>}</>}
    </Box> : <Box sx={{ display: 'flex', gap: 3, flex: 1, minHeight: 0, flexDirection: { xs: 'column', lg: 'row' } }}>
      <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0, minHeight: 0 }}>
        {!current && <Alert severity="info" sx={{ mb: 1.5 }}>The existing test API addresses the Brain's current serving target. This selected environment is not that target; use its assigned Engage experience instead.</Alert>}
        {context.isError && <Alert severity="error">{context.error.message}</Alert>}
        <Box sx={{ ...brainPaneSx, flex: 1 }}>
          {!turns.length && <Typography variant="body2" color="text.secondary" sx={{ py: 3 }}>Ask a question to inspect the applied Brain's response. This is not a simulation of unsaved draft settings.</Typography>}
          {turns.map((turn, index) => <Box key={index} sx={{ py: 1.5 }}><Typography fontSize={13} fontWeight={600} sx={{ color: 'primary.main', mb: 1 }}>{turn.question}</Typography>
            {turn.scope !== scope && <Typography variant="caption" color="warning.main">Previous applied release</Typography>}
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{turn.result.traceSummary?.answer ?? turn.result.message ?? 'Inspect the structured result for response details.'}</Typography>
            {!turn.result.success && <Chip sx={{ mt: 1 }} label="Request did not succeed" color="warning" variant="outlined" />}</Box>)}
        </Box>
        {query.isError && <Alert severity="error" sx={{ my: 1 }}>{query.error.message}</Alert>}
        <Stack component="form" direction="row" gap={1} sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }} onSubmit={event => { event.preventDefault(); if (!reviewOnly && current && question.trim() && workspace?.access.canOperate && !query.isPending) query.mutate({ question, conversationId: last?.conversationId ?? undefined, scope }) }}>
          <TextField label="Ask a test question" size="small" fullWidth multiline maxRows={3} value={question} onChange={event => setQuestion(event.target.value)} disabled={!current || query.isPending || !workspace?.access.canOperate} />
          <Button type="submit" variant="contained" aria-label="Send test question" disabled={reviewOnly || !current || query.isPending || !question.trim() || !workspace?.access.canOperate}>{query.isPending ? <CircularProgress size={18} /> : <SendOutlined />}</Button>
        </Stack>
      </Box>
      <Box component="aside" sx={{ ...brainPaneSx, width: { lg: '40%' }, borderLeft: { lg: 1 }, borderColor: 'divider', pl: { lg: 3 } }}>
        <Typography variant="subtitle1">Result details</Typography>{last ? <Box component="pre" sx={{ fontSize: 12, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', m: 0, mt: 2 }}>{JSON.stringify(last.traceSummary ?? last.result, null, 2)}</Box> : <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Sources, actions and diagnostic information appear after a test.</Typography>}
      </Box>
    </Box>}
  </>
}
