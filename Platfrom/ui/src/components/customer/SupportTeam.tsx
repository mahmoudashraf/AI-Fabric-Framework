import { useEffect, useState } from 'react'
import { Avatar, Box, CircularProgress, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import SupportAgentOutlined from '@mui/icons-material/SupportAgentOutlined'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { useQuery } from '@tanstack/react-query'
import { getSupportAgentPhoto, getSupportTeam, type SupportTeamAgent } from '../../api/supportApi'
import { useCustomerSupport } from './CustomerSupportContext'

function AgentAvatar({ agent }: { agent: SupportTeamAgent }) {
  const [url, setUrl] = useState<string>()
  useEffect(() => {
    setUrl(undefined)
    if (!agent.photoAvailable) return
    const controller = new AbortController()
    let objectUrl: string | undefined
    getSupportAgentPhoto(agent.agentReference, controller.signal).then(blob => {
      if (controller.signal.aborted) return
      objectUrl = URL.createObjectURL(blob); setUrl(objectUrl)
    }).catch(() => undefined)
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [agent.agentReference, agent.photoAvailable])
  const initials = agent.displayName.trim().split(/\s+/).slice(0, 2).map(part => part[0]).join('')
  return <Avatar src={url} alt={agent.displayName} sx={{ width: 42, height: 42, bgcolor: 'action.selected', color: 'text.primary', fontSize: 14, border: 1, borderColor: 'divider' }}>{initials}</Avatar>
}

export function SupportTeam() {
  const { customerId, actorId } = useCustomerSupport()
  const team = useQuery({ queryKey: ['support-team', customerId, actorId], queryFn: getSupportTeam, retry: false, staleTime: 60000 })
  return <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5} alignItems={{ sm: 'center' }}
    sx={{ py: 2.5, borderTop: 1, borderBottom: 1, borderColor: 'divider' }}>
    <Stack direction="row" spacing={1.25} alignItems="center" sx={{ flexShrink: 0 }}>
      <SupportAgentOutlined sx={{ color: 'secondary.main', fontSize: 23 }} />
      <Box><Typography variant="subtitle2">Your integration team</Typography><Typography variant="caption" color="text.secondary">Human help, when you need it</Typography></Box>
    </Stack>
    <Stack direction="row" spacing={3} sx={{ flex: 1, minWidth: 0, overflowX: 'auto', py: 0.5 }}>
      {team.isPending && <CircularProgress size={20} aria-label="Loading support team" />}
      {team.data?.agents.map(agent => <Stack key={agent.agentReference} direction="row" spacing={1.25} alignItems="center" sx={{ flexShrink: 0 }}>
        <AgentAvatar agent={agent} /><Box><Typography variant="body2" fontWeight={600}>{agent.displayName}</Typography>
          <Typography variant="caption" color="text.secondary">Integration support</Typography></Box>
      </Stack>)}
      {team.isError && <Typography color="text.secondary" variant="body2">Team details are temporarily unavailable.</Typography>}
      {team.data?.agents.length === 0 && <Typography color="text.secondary" variant="body2">No agents are assigned yet.</Typography>}
    </Stack>
    <Tooltip title="Refresh team"><IconButton aria-label="Refresh support team" size="small" disabled={team.isFetching} onClick={() => { void team.refetch() }}><RefreshRounded fontSize="small" /></IconButton></Tooltip>
  </Stack>
}
