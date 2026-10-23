import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useBlocker } from 'react-router-dom'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Drawer, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import CloseRounded from '@mui/icons-material/CloseRounded'
import { CustomerEditorSections, type CustomerEditorSection } from './CustomerDetailSections'

export function CustomerConfigurationPanel({ open, title, scopeKey, onClose, children, initialSection, notices }: {
  open: boolean
  title: string
  scopeKey: string
  onClose: () => void
  children: ReactNode
  initialSection?: CustomerEditorSection
  notices?: ReactNode
}) {
  const [confirmClose, setConfirmClose] = useState(false)
  const discardNavigation = useRef(false)
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (discardNavigation.current) { discardNavigation.current = false; return false }
    const current = new URLSearchParams(currentLocation.search)
    const next = new URLSearchParams(nextLocation.search)
    current.delete('step'); next.delete('step')
    return open && (currentLocation.pathname !== nextLocation.pathname || current.toString() !== next.toString())
  })
  useEffect(() => {
    if (!open) return
    const preventDiscard = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', preventDiscard)
    return () => window.removeEventListener('beforeunload', preventDiscard)
  }, [open])
  const requestClose = () => setConfirmClose(true)
  const keepEditing = () => { if (blocker.state === 'blocked') blocker.reset(); setConfirmClose(false) }
  const discard = () => {
    setConfirmClose(false)
    if (blocker.state === 'blocked') blocker.proceed()
    else { discardNavigation.current = true; onClose() }
  }
  return <>
    <Drawer anchor="right" open={open} onClose={requestClose} PaperProps={{ sx: { width: { xs: '100%', md: 'min(1100px, calc(100vw - 205px))' }, maxWidth: '100%' } }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ px: 3, py: 1.5, borderBottom: 1, borderColor: 'divider', position: 'sticky', top: 0, bgcolor: 'background.paper', zIndex: 2 }}>
        <Typography fontWeight={600}>{title}</Typography><Tooltip title="Close configuration"><IconButton aria-label="Close configuration" onClick={requestClose}><CloseRounded /></IconButton></Tooltip>
      </Stack>
      <Box key={scopeKey} sx={{ p: { xs: 2, md: 3 }, minWidth: 0 }}>
        <CustomerEditorSections scopeKey={scopeKey} initialSection={initialSection} notices={notices}>{children}</CustomerEditorSections>
      </Box>
    </Drawer>
    <Dialog open={open && (confirmClose || blocker.state === 'blocked')} onClose={keepEditing} maxWidth="xs" fullWidth>
      <DialogTitle>Close configuration?</DialogTitle>
      <DialogContent>Any changes not explicitly saved will be discarded. No deployment or app assignment changes when this panel closes.</DialogContent>
      <DialogActions><Button onClick={keepEditing}>Keep editing</Button><Button onClick={discard}>Close panel</Button></DialogActions>
    </Dialog>
  </>
}
