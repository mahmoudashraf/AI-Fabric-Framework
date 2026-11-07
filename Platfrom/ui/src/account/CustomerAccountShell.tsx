import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link as RouterLink, useBlocker, useLocation } from 'react-router-dom'
import { Avatar, Box, Breadcrumbs, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider,
  Drawer, IconButton, Link, List, ListItemButton, ListItemIcon, ListItemText, Stack, Tooltip,
  Typography, useMediaQuery, useTheme } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import HubOutlined from '@mui/icons-material/HubOutlined'
import PersonOutline from '@mui/icons-material/PersonOutline'
import PeopleOutline from '@mui/icons-material/PeopleOutline'
import HandshakeOutlined from '@mui/icons-material/HandshakeOutlined'
import LockOutlined from '@mui/icons-material/LockOutlined'
import MenuRounded from '@mui/icons-material/MenuRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import HelpOutlineRounded from '@mui/icons-material/HelpOutlineRounded'
import LightModeOutlined from '@mui/icons-material/LightModeOutlined'
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined'
import VerifiedUserOutlined from '@mui/icons-material/VerifiedUserOutlined'
import type { PlatformAuthSessionSummary } from '../api/platformApi'
import { CustomerConsoleTheme, useCustomerColorPreference } from '../theme/CustomerConsoleTheme'
import { CustomerSupportProvider } from '../components/customer/CustomerSupportContext'
import { CustomerSupportAssistant } from '../components/customer/CustomerSupportAssistant'
import { SupportResourceDialog } from '../components/customer/SupportResourceDialog'
import { brainPath } from '../brain/brainNavigation'
import { deploymentTerm } from '../deploymentTerminology'
import { accountPath, accountSection, accountSections } from './accountNavigation'
import { AccountEditorContext, type AccountEditorState } from './AccountEditorContext'

type Props = { session: PlatformAuthSessionSummary | null; onSignOut: () => Promise<void>; children: ReactNode }
const icons = [PersonOutline, PeopleOutline, HandshakeOutlined, LockOutlined]

export function CustomerAccountShell(props: Props) {
  return <CustomerConsoleTheme><CustomerSupportProvider><AccountFrame {...props} /><SupportResourceDialog /></CustomerSupportProvider></CustomerConsoleTheme>
}

function AccountFrame({ session, children, onSignOut }: Props) {
  const location = useLocation()
  const theme = useTheme()
  const compact = useMediaQuery(theme.breakpoints.down('md'))
  const color = useCustomerColorPreference()
  const [menu, setMenu] = useState(false)
  const [signOutOpen, setSignOutOpen] = useState(false)
  const [editor, setEditor] = useState<AccountEditorState | null>(null)
  const register = useCallback((value: typeof editor) => setEditor(value), [])
  const section = accountSection(location.pathname)
  const label = accountSections.find(item => item.path === section)!.label
  const params = new URLSearchParams(accountPath(section, location.search).split('?')[1])
  const fromBrain = params.get('fromBrain')
  const footer = useRef<HTMLElement>(null)
  const [footerHeight, setFooterHeight] = useState(56)
  const blocker = useBlocker(({ currentLocation, nextLocation }) => Boolean(editor?.dirty || editor?.busy)
    && `${currentLocation.pathname}${currentLocation.search}` !== `${nextLocation.pathname}${nextLocation.search}`)
  useEffect(() => {
    if (!editor?.dirty && !editor?.busy) return
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', prevent)
    return () => window.removeEventListener('beforeunload', prevent)
  }, [editor?.dirty, editor?.busy])
  useEffect(() => {
    if (!footer.current) return
    const observer = new ResizeObserver(() => setFooterHeight(Math.ceil(footer.current!.getBoundingClientRect().height)))
    observer.observe(footer.current)
    return () => observer.disconnect()
  }, [])
  const nav = <Box component="nav" aria-label="Account navigation" sx={{ height: '100%', display: 'flex', flexDirection: 'column', py: 2 }}>
    <Button component={RouterLink} to="/home" startIcon={<ArrowBackRounded />} sx={{ mx: 1.5, mb: 2, alignSelf: 'flex-start', color: 'text.primary' }}>Back to Console</Button>
    <Typography fontSize={19} fontWeight={650} sx={{ px: 2.5, mb: 3, overflowWrap: 'anywhere' }}>{session?.customerName ?? 'Your workspace'}</Typography>
    <Typography variant="overline" color="text.secondary" sx={{ px: 2.5, mb: 1 }}>Account</Typography>
    <List disablePadding>{accountSections.map((item, index) => {
      const Icon = icons[index]
      return <ListItemButton key={item.path} component={RouterLink} to={accountPath(item.path, location.search)} selected={section === item.path}
        aria-current={section === item.path ? 'page' : undefined} onClick={() => setMenu(false)} sx={{ px: 2.5, minHeight: 46,
          borderLeft: 3, borderColor: section === item.path ? 'primary.main' : 'transparent', '&.Mui-selected': { color: 'primary.main', bgcolor: 'action.selected' } }}>
        <ListItemIcon sx={{ minWidth: 32, color: 'inherit' }}><Icon sx={{ fontSize: 20 }} /></ListItemIcon>
        <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: 13, fontWeight: section === item.path ? 600 : 400 }} />
      </ListItemButton>
    })}</List>
    {fromBrain && <Button component={RouterLink} to={brainPath(fromBrain, '', params.get('targetProfileId'))} startIcon={<ArrowBackRounded />} sx={{ m: 2, fontSize: 12 }}>Back to {deploymentTerm.singular}</Button>}
    <Box sx={{ flex: 1 }} /><Divider sx={{ mx: 2 }} />
    <Button component={RouterLink} to="/support" startIcon={<HelpOutlineRounded />} sx={{ m: 2, color: 'text.primary', justifyContent: 'flex-start' }}>Help and support</Button>
  </Box>
  return <AccountEditorContext.Provider value={register}><Box data-account-workspace sx={{ '--customer-navigation-width': compact ? '0px' : '220px',
    height: { md: '100dvh' }, minHeight: '100dvh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default', overflow: { md: 'hidden' } }}>
    <Stack component="header" direction="row" alignItems="center" gap={{ xs: 0.5, md: 2 }} sx={{ height: 56, flexShrink: 0, px: { xs: 1.5, md: 2.5 }, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
      {compact && <IconButton aria-label="Open account navigation" onClick={() => setMenu(true)}><MenuRounded /></IconButton>}
      <Stack component="a" href="https://loomai.pro/" direction="row" alignItems="center" gap={1} sx={{ textDecoration: 'none', color: 'text.primary', pr: 2 }}><HubOutlined color="primary" /><Typography fontSize={19} fontWeight={700}>LoomAI</Typography></Stack>
      {compact ? <Typography noWrap variant="caption" sx={{ flex: 1 }}>{label}</Typography> : <Breadcrumbs aria-label="Account breadcrumb" sx={{ flex: 1, fontSize: 12 }}>
        <Link component={RouterLink} to="/home" color="inherit" underline="hover">Console</Link>
        <Link component={RouterLink} to={accountPath('profile', location.search)} color="inherit" underline="hover">Account</Link>
        <Typography fontSize={12}>{label}</Typography></Breadcrumbs>}
      <Tooltip title={`Switch to ${theme.palette.mode === 'dark' ? 'light' : 'dark'} mode`}><IconButton aria-label="Switch color theme" onClick={() => color.setPreference(theme.palette.mode === 'dark' ? 'light' : 'dark')}>
        {theme.palette.mode === 'dark' ? <LightModeOutlined fontSize="small" /> : <DarkModeOutlined fontSize="small" />}</IconButton></Tooltip>
      {!compact && <><Tooltip title="Help and support"><IconButton component={RouterLink} to="/support" aria-label="Help and support"><HelpOutlineRounded fontSize="small" /></IconButton></Tooltip>
        <Avatar sx={{ width: 29, height: 29, fontSize: 12, bgcolor: 'action.selected', color: 'text.primary' }}>{session?.displayName?.[0] ?? 'A'}</Avatar></>}
      <Tooltip title="Sign out"><IconButton aria-label="Sign out" disabled={editor?.busy} onClick={() => {
        if (editor?.dirty) setSignOutOpen(true); else void onSignOut()
      }}><LogoutRounded sx={{ fontSize: 18 }} /></IconButton></Tooltip>
    </Stack>
    <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
      <Drawer variant={compact ? 'temporary' : 'permanent'} open={!compact || menu} onClose={() => setMenu(false)} sx={{ width: compact ? 0 : 220, flexShrink: 0,
        '& .MuiDrawer-paper': { width: 220, position: compact ? 'fixed' : 'relative', height: '100%', bgcolor: 'background.paper' } }}>{nav}</Drawer>
      <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0, minHeight: 0 }}>
        <Box component="main" sx={{ flex: 1, minHeight: 0, overflow: { md: 'auto' }, p: { xs: 2, md: 3 } }}>{children}</Box>
        <Stack component="footer" ref={footer} direction="row" alignItems="center" gap={1.5} sx={{ borderTop: 1, borderColor: 'divider', px: { xs: 2, md: 3 }, py: 1.75, bgcolor: 'background.paper', flexShrink: 0 }}>
          <VerifiedUserOutlined sx={{ fontSize: 20, color: 'text.secondary' }} /><Typography variant="body2" color="text.secondary">Access changes apply immediately. No {deploymentTerm.lowercase} update is needed.</Typography>
        </Stack>
      </Box>
    </Box><CustomerSupportAssistant bottomOffset={footerHeight} initiallyMinimized />
    <Dialog open={blocker.state === 'blocked'} onClose={() => blocker.state === 'blocked' && blocker.reset()} maxWidth="xs" fullWidth>
      <DialogTitle>{editor?.busy ? 'An update is in progress' : 'Discard unsaved input?'}</DialogTitle>
      <DialogContent>{editor?.busy ? 'Wait for this access update to finish before leaving.' : 'Your unsaved input will be lost. Existing account access is unchanged.'}</DialogContent>
      <DialogActions><Button onClick={() => blocker.state === 'blocked' && blocker.reset()}>Stay here</Button>{!editor?.busy && <Button color="warning" onClick={() => { editor?.discard(); if (blocker.state === 'blocked') blocker.proceed() }}>Discard & leave</Button>}</DialogActions>
    </Dialog>
    <Dialog open={signOutOpen} onClose={() => setSignOutOpen(false)} maxWidth="xs" fullWidth><DialogTitle>Discard input and sign out?</DialogTitle><DialogContent>Unsaved input will be lost. Existing account access is unchanged.</DialogContent><DialogActions><Button onClick={() => setSignOutOpen(false)}>Stay here</Button><Button color="warning" onClick={() => { editor?.discard(); setSignOutOpen(false); void onSignOut() }}>Sign out</Button></DialogActions></Dialog>
  </Box></AccountEditorContext.Provider>
}
