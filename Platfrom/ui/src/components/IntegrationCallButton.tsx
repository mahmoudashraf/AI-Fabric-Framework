import { useId, useState } from 'react'
import { Box, Button, CircularProgress, Dialog, IconButton, Stack, Typography, type ButtonProps } from '@mui/material'
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined'
import CloseRounded from '@mui/icons-material/CloseRounded'
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded'
import { integrationTeam } from '../integrationTeam'

type Props = Pick<ButtonProps, 'variant' | 'size' | 'fullWidth' | 'className' | 'sx'> & { children?: string }

export function IntegrationCallButton({ children = 'Book integration call', ...props }: Props) {
  const [open, setOpen] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [embedUrl, setEmbedUrl] = useState('')
  const titleId = useId()
  return <><Button component="a" href={integrationTeam.bookingUrl} target="_blank" rel="noopener noreferrer"
    aria-haspopup="dialog" onClick={event => {
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
      event.preventDefault()
      const url = new URL(integrationTeam.bookingUrl)
      url.searchParams.set('embed_domain', window.location.host)
      url.searchParams.set('embed_type', 'Inline')
      setEmbedUrl(url.toString())
      setLoaded(false)
      setOpen(true)
    }} startIcon={<CalendarMonthOutlined />} {...props}>{children}</Button>
    <Dialog open={open} onClose={() => setOpen(false)} aria-labelledby={titleId} maxWidth={false}
      PaperProps={{ sx: { m: { xs: 1, sm: 3 }, width: '1100px', maxWidth: { xs: 'calc(100% - 16px)', sm: 'calc(100% - 48px)' },
        height: { xs: 'calc(100dvh - 16px)', sm: 'min(860px, calc(100dvh - 48px))' }, maxHeight: 'none', borderRadius: 2 } }}>
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ px: { xs: 1.5, sm: 2.5 }, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <CalendarMonthOutlined color="primary" sx={{ display: { xs: 'none', sm: 'block' } }} />
        <Box sx={{ flex: 1, minWidth: 0 }}><Typography id={titleId} component="h2" sx={{ fontSize: 17, fontWeight: 650 }}>Book an integration call</Typography>
          <Typography color="text.secondary" sx={{ fontSize: 12 }}>LoomAI integration team</Typography></Box>
        <Button component="a" href={integrationTeam.bookingUrl} target="_blank" rel="noopener noreferrer" size="small"
          startIcon={<OpenInNewRounded />} sx={{ fontSize: 12, flexShrink: 0, display: { xs: 'none', sm: 'inline-flex' } }}>Open in new tab</Button>
        <IconButton autoFocus aria-label="Close booking" onClick={() => setOpen(false)}><CloseRounded /></IconButton>
      </Stack>
      <Box sx={{ flex: 1, minHeight: 0, position: 'relative', bgcolor: '#fff' }}>
        {!loaded && <Stack role="status" alignItems="center" spacing={2} sx={{ position: 'absolute', inset: 0, justifyContent: 'center', color: '#394652', pointerEvents: 'none' }}>
          <CircularProgress size={28} /><Typography sx={{ fontSize: 13 }}>Loading available times...</Typography></Stack>}
        {open && <Box component="iframe" src={embedUrl} title="Schedule a LoomAI integration call" referrerPolicy="no-referrer"
          onLoad={() => setLoaded(true)} sx={{ display: 'block', width: '100%', height: '100%', border: 0, position: 'relative', colorScheme: 'light' }} />}
      </Box>
      <Box sx={{ display: { xs: 'block', sm: 'none' }, textAlign: 'center', borderTop: 1, borderColor: 'divider', py: 0.5 }}>
        <Button component="a" href={integrationTeam.bookingUrl} target="_blank" rel="noopener noreferrer" size="small" startIcon={<OpenInNewRounded />}>Open in new tab</Button>
      </Box>
    </Dialog></>
}
