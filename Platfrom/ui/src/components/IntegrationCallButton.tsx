import { useId, useRef, useState } from 'react'
import { Avatar, Box, Button, CircularProgress, Dialog, IconButton, Stack, Tooltip, Typography, type ButtonProps } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined'
import CloseRounded from '@mui/icons-material/CloseRounded'
import LanguageOutlined from '@mui/icons-material/LanguageOutlined'
import LocationOnOutlined from '@mui/icons-material/LocationOnOutlined'
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded'
import PhoneOutlined from '@mui/icons-material/PhoneOutlined'
import StorageOutlined from '@mui/icons-material/StorageOutlined'
import WhatsApp from '@mui/icons-material/WhatsApp'
import { integrationTeam } from '../integrationTeam'

type Props = Pick<ButtonProps, 'variant' | 'size' | 'fullWidth' | 'className' | 'sx'> & { children?: string }

export function IntegrationCallButton({ children, sx, ...props }: Props) {
  const [view, setView] = useState<'contact' | 'booking' | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [embedUrl, setEmbedUrl] = useState('')
  const titleId = useId()
  const triggerRef = useRef<HTMLAnchorElement | null>(null)
  const book = () => {
    const url = new URL(integrationTeam.bookingUrl)
    url.searchParams.set('embed_domain', window.location.host)
    url.searchParams.set('embed_type', 'Inline')
    setEmbedUrl(url.toString())
    setLoaded(false)
    setView('booking')
  }
  return <><Button component="a" href={integrationTeam.bookingUrl} target="_blank" rel="noopener noreferrer"
    aria-label={children || 'Talk to Mo'} aria-haspopup="dialog" onClick={event => {
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
      event.preventDefault()
      triggerRef.current = event.currentTarget
      setView('contact')
    }} {...props} sx={[{ textTransform: 'none', gap: 1, px: 1.25, py: 0.75, minHeight: 46, maxWidth: '100%',
      border: '1px solid', borderColor: 'divider', color: 'text.primary', borderRadius: '8px',
      '&:hover': { borderColor: 'secondary.main', bgcolor: 'action.hover' } }, ...(Array.isArray(sx) ? sx : [sx])]}>
    <Avatar src={integrationTeam.portraitUrl} alt="" sx={{ width: 34, height: 34, flexShrink: 0 }} />
    <Box component="span" sx={{ minWidth: 0, textAlign: 'left', lineHeight: 1.3 }}>
      <Box component="span" sx={{ display: 'block', fontSize: 13, fontWeight: 700 }}>{children || 'Talk to Mo'}</Box>
      {!children && <Box component="span" sx={{ display: 'block', fontSize: 10, color: 'text.secondary', fontWeight: 400 }}>AI Enablement Engineer</Box>}
    </Box><ArrowForwardRounded sx={{ fontSize: 17, ml: 'auto', flexShrink: 0 }} />
  </Button>
    <Dialog open={view !== null} onClose={() => setView(null)} aria-labelledby={titleId} maxWidth={false}
      TransitionProps={{ onExited: () => triggerRef.current?.focus({ preventScroll: true }) }}
      PaperProps={{ sx: { m: { xs: 1, sm: 3 }, width: view === 'booking' ? 1100 : 650,
        maxWidth: { xs: 'calc(100% - 16px)', sm: 'calc(100% - 48px)' },
        height: view === 'booking' ? { xs: 'calc(100dvh - 16px)', sm: 'min(860px, calc(100dvh - 48px))' } : 'auto',
        maxHeight: { xs: 'calc(100dvh - 16px)', sm: 'calc(100dvh - 48px)' }, borderRadius: '8px',
        border: 1, borderColor: 'divider' } }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ px: { xs: 2, sm: 3.5 }, pt: 2, pb: 1 }}>
        {view === 'booking' && <Tooltip title="Back to Mo's details"><IconButton aria-label="Back to Mo's details" onClick={() => setView('contact')}><ArrowBackRounded /></IconButton></Tooltip>}
        <Typography sx={{ flex: 1, fontSize: 18, fontWeight: 750 }}>LoomAI</Typography>
        {view === 'booking' && <Button component="a" href={integrationTeam.bookingUrl} target="_blank" rel="noopener noreferrer" size="small"
          startIcon={<OpenInNewRounded />} sx={{ fontSize: 12, display: { xs: 'none', sm: 'inline-flex' } }}>Open in new tab</Button>}
        <Tooltip title="Close"><IconButton autoFocus aria-label="Close integration panel" onClick={() => setView(null)}><CloseRounded /></IconButton></Tooltip>
      </Stack>
      {view === 'contact' ? <Box sx={{ px: { xs: 2, sm: 3.5 }, pb: { xs: 2.5, sm: 3.5 }, overflowY: 'auto' }}>
        <Typography id={titleId} component="h2" sx={{ fontSize: { xs: 24, sm: 28 }, fontWeight: 750, lineHeight: 1.2, mt: 1 }}>Let's connect your AI to your business.</Typography>
        <Typography color="text.secondary" sx={{ fontSize: 15, mt: 1 }}>Meet your AI integration engineer.</Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5} sx={{ my: 3 }} alignItems={{ xs: 'flex-start', sm: 'center' }}>
          <Avatar src={integrationTeam.portraitUrl} alt={integrationTeam.name} sx={{ width: { xs: 86, sm: 112 }, height: { xs: 86, sm: 112 } }} />
          <Box sx={{ minWidth: 0 }}><Typography component="h3" sx={{ fontSize: 23, fontWeight: 750 }}>{integrationTeam.name}</Typography>
            <Typography color="text.secondary" sx={{ fontSize: 14, mt: 0.5 }}>{integrationTeam.role}</Typography>
            <Stack direction="row" spacing={0.5} alignItems="center" sx={{ color: 'text.secondary', mt: 0.5 }}><LocationOnOutlined sx={{ fontSize: 15 }} /><Typography sx={{ fontSize: 12 }}>{integrationTeam.location}</Typography></Stack>
            <Typography sx={{ mt: 1.5, fontSize: 15, lineHeight: 1.6 }}>{integrationTeam.introduction}</Typography>
            <Typography sx={{ mt: 1.5, fontSize: 14, fontWeight: 650, lineHeight: 1.5 }}>{integrationTeam.promise}</Typography>
          </Box>
        </Stack>
        <Stack spacing={1.5} sx={{ py: 2.5, mb: 2.5, borderTop: 1, borderColor: 'divider' }}>
          <Stack direction="row" spacing={1.5} alignItems="center"><LanguageOutlined sx={{ fontSize: 22 }} /><Typography sx={{ fontSize: 15 }}>Website & AI workspace</Typography></Stack>
          <Stack direction="row" spacing={1.5} alignItems="center"><StorageOutlined sx={{ fontSize: 22 }} /><Typography sx={{ fontSize: 15 }}>Data, tools & integrations</Typography></Stack>
        </Stack>
        <Button fullWidth variant="contained" startIcon={<CalendarMonthOutlined />} onClick={book} sx={{ py: 1.5 }}>Book a 30-min call</Button>
        <Typography color="text.secondary" sx={{ textAlign: 'center', fontSize: 12, mt: 1, mb: 2.5 }}>Choose a time that works for you.</Typography>
        <Button fullWidth component="a" href={integrationTeam.whatsappUrl} target="_blank" rel="noopener noreferrer" variant="outlined" startIcon={<WhatsApp />}
          sx={{ py: 1.25, color: 'text.primary', borderColor: 'success.main', '&:hover': { borderColor: 'success.main', bgcolor: 'action.hover' } }}>Message me on WhatsApp</Button>
        <Box sx={{ textAlign: 'center', my: 1 }}><Button component="a" href={integrationTeam.phoneUrl} startIcon={<PhoneOutlined />} sx={{ color: 'text.secondary', fontSize: 13 }}>{integrationTeam.phone}</Button></Box>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', sm: 'center' }} justifyContent="space-between" sx={{ mt: 2.5, pt: 2.5, borderTop: 1, borderColor: 'divider' }}>
          <Typography color="text.secondary" sx={{ fontSize: 13 }}>Prefer to write it down?</Typography>
          <Button component="a" href={integrationTeam.requestUrl} endIcon={<ArrowForwardRounded />} sx={{ fontSize: 13, px: 0 }} onClick={() => setView(null)}>Send an integration request</Button>
        </Stack>
      </Box> : <>
        <Typography id={titleId} component="h2" sx={{ px: { xs: 2, sm: 3.5 }, pb: 1.5, fontSize: 17, fontWeight: 650 }}>Book a call with Mo</Typography>
        <Box sx={{ flex: 1, minHeight: 0, position: 'relative', bgcolor: '#fff' }}>
          {!loaded && <Stack role="status" alignItems="center" spacing={2} sx={{ position: 'absolute', inset: 0, justifyContent: 'center', color: '#394652', pointerEvents: 'none' }}>
            <CircularProgress size={28} /><Typography sx={{ fontSize: 13 }}>Loading available times...</Typography></Stack>}
          {view === 'booking' && <Box component="iframe" src={embedUrl} title="Schedule a LoomAI integration call" referrerPolicy="no-referrer"
            onLoad={() => setLoaded(true)} sx={{ display: 'block', width: '100%', height: '100%', border: 0, position: 'relative', colorScheme: 'light' }} />}
        </Box>
        <Box sx={{ display: { xs: 'block', sm: 'none' }, textAlign: 'center', borderTop: 1, borderColor: 'divider', py: 0.5 }}>
          <Button component="a" href={integrationTeam.bookingUrl} target="_blank" rel="noopener noreferrer" size="small" startIcon={<OpenInNewRounded />}>Open in new tab</Button>
        </Box>
      </>}
    </Dialog></>
}
