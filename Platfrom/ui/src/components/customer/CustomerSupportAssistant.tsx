import { Link, useLocation } from 'react-router-dom'
import { Box, Button, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import SupportAgentOutlined from '@mui/icons-material/SupportAgentOutlined'
import OpenInFullRounded from '@mui/icons-material/OpenInFullRounded'
import RemoveRounded from '@mui/icons-material/RemoveRounded'
import SwapHorizRounded from '@mui/icons-material/SwapHorizRounded'
import TuneRounded from '@mui/icons-material/TuneRounded'
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded'
import { useCustomerSupport } from './CustomerSupportContext'
import { SupportWorkspace } from './SupportWorkspace'

export function CustomerSupportAssistant() {
  const support = useCustomerSupport()
  const location = useLocation()
  const inPage = location.pathname === '/support' || location.pathname.startsWith('/support/')
  const hidden = !inPage && support.minimized
  const controlPosition = support.side === 'left'
    ? { left: { xs: 8, md: 'calc(var(--customer-navigation-width, 0px) + 20px)' } }
    : { right: { xs: 8, md: 20 } }
  const openFull = support.requestId ? `/support/${encodeURIComponent(support.requestId)}` : '/support'
  return <>
    {hidden && <Button variant="contained" startIcon={<ChatBubbleOutlineRounded />}
      onClick={() => support.setMinimized(false)} aria-label="Open LoomAI support"
      sx={{ position: 'fixed', bottom: 20, ...controlPosition, zIndex: 1100, borderRadius: 1, boxShadow: 4, py: 1.25, textTransform: 'none' }}>LoomAI Support</Button>}
    <Box data-testid="customer-support-assistant" data-presentation={inPage ? 'page' : 'dock'}
      sx={{ display: hidden ? 'none' : 'flex', flexDirection: 'column', minWidth: 0, bgcolor: 'background.paper',
        ...(inPage ? { mt: 2, height: 'max(480px, calc(100dvh - 200px))', minHeight: 480 }
          : { position: 'fixed', top: 76, bottom: 0,
            left: { xs: support.side === 'left' ? 48 : 0, md: 'calc(var(--customer-navigation-width, 0px) + 52px)' },
            right: { xs: support.side === 'right' ? 48 : 0, md: 52 },
            zIndex: 1100, pointerEvents: 'none', bgcolor: 'transparent' }),
        ...(inPage ? { border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' } : {}) }}>
      {inPage && <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ px: 1.5, py: 0.75, borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}>
        <Stack direction="row" spacing={0.75} alignItems="center"><SupportAgentOutlined fontSize="small" color="secondary" /><Typography variant="subtitle2">LoomAI Support</Typography></Stack>
        <Stack direction="row" spacing={0.25}>
          {support.requestId && <Tooltip title="Configure diagnostics"><IconButton size="small" aria-label="Configure diagnostics" onClick={() => support.setDiagnosticsOpen(true)}><TuneRounded fontSize="small" /></IconButton></Tooltip>}
        </Stack>
      </Stack>}
      {!inPage && <Stack data-testid="support-dock-controls" spacing={0.5} sx={{ position: 'fixed', bottom: 24, ...controlPosition,
        zIndex: 1102, pointerEvents: 'auto', p: 0.5, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 1, boxShadow: 2 }}>
        {support.requestId && <Tooltip title="Configure diagnostics"><IconButton size="small" aria-label="Configure diagnostics" onClick={() => support.setDiagnosticsOpen(true)}><TuneRounded fontSize="small" /></IconButton></Tooltip>}
        <Tooltip title="Support and requests"><IconButton component={Link} to={openFull} size="small" aria-label="Open support page"><OpenInFullRounded fontSize="small" /></IconButton></Tooltip>
        <Tooltip title={`Move support to the ${support.side === 'right' ? 'left' : 'right'}`}><IconButton size="small" aria-label={`Move support to the ${support.side === 'right' ? 'left' : 'right'}`} onClick={support.changeSide}><SwapHorizRounded fontSize="small" /></IconButton></Tooltip>
        <Tooltip title="Minimize support"><IconButton size="small" aria-label="Minimize support" onClick={() => support.setMinimized(true)}><RemoveRounded fontSize="small" /></IconButton></Tooltip>
      </Stack>}
      <SupportWorkspace visible={!hidden && !support.diagnosticsOpen} layer={inPage ? 4 : 1101} presentation={inPage ? 'page' : 'docked'} />
    </Box>
  </>
}
