import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link as RouterLink, useBlocker, useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Avatar, Box, Breadcrumbs, Button, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, Drawer, FormControl, IconButton, Link, List, ListItemButton, ListItemIcon,
  ListItemText, MenuItem, Select, Stack, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import HomeOutlined from '@mui/icons-material/HomeOutlined'
import LayersOutlined from '@mui/icons-material/LayersOutlined'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded'
import AccountTreeOutlined from '@mui/icons-material/AccountTreeOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import PlayArrowOutlined from '@mui/icons-material/PlayArrowOutlined'
import SystemUpdateAltOutlined from '@mui/icons-material/SystemUpdateAltOutlined'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import HubOutlined from '@mui/icons-material/HubOutlined'
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined'
import LightModeOutlined from '@mui/icons-material/LightModeOutlined'
import MenuRounded from '@mui/icons-material/MenuRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import HelpOutlineRounded from '@mui/icons-material/HelpOutlineRounded'
import { fetchDeploymentReleases, fetchDeploymentTargetOptions, fetchDeploymentTargetProfiles, fetchDeploymentVersions, type PlatformAuthSessionSummary } from '../api/platformApi'
import { CustomerConsoleTheme, useCustomerColorPreference } from '../theme/CustomerConsoleTheme'
import { CustomerSupportProvider } from '../components/customer/CustomerSupportContext'
import { CustomerSupportAssistant } from '../components/customer/CustomerSupportAssistant'
import { SupportResourceDialog } from '../components/customer/SupportResourceDialog'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { BrainEditorContext, BrainWorkspaceContext, type BrainEditorState } from './BrainWorkspaceContext'
import { brainIdFromPath, brainPath, brainSections, currentBrainSection, isUsableRelease, retainEditingRelease } from './brainNavigation'
import { deploymentTerm } from '../deploymentTerminology'

const sectionIcons = [HomeOutlined, LayersOutlined, AutoAwesomeOutlined, ChatBubbleOutlineRounded,
  AccountTreeOutlined, HeadsetMicOutlined, PlayArrowOutlined, SystemUpdateAltOutlined, SettingsOutlined]

type Props = { session: PlatformAuthSessionSummary | null; onSignOut: () => Promise<void>; children: ReactNode }

export function SmartBrainShell(props: Props) {
  return <CustomerConsoleTheme>{props.session?.role === 'CUSTOMER_ADMIN'
    ? <CustomerSupportProvider><SmartBrainFrame {...props} /><SupportResourceDialog /></CustomerSupportProvider>
    : <SmartBrainFrame {...props} />}</CustomerConsoleTheme>
}

function SmartBrainFrame({ session, onSignOut, children }: Props) {
  const location = useLocation()
  const navigate = useNavigate()
  const workspace = useDeploymentWorkspace()
  const theme = useTheme()
  const compact = useMediaQuery(theme.breakpoints.down('md'))
  const color = useCustomerColorPreference()
  const [menuOpen, setMenuOpen] = useState(false)
  const [editor, setEditor] = useState<BrainEditorState | null>(null)
  const footerRef = useRef<HTMLElement>(null)
  const [footerHeight, setFooterHeight] = useState(64)
  const registerEditor = useCallback((value: BrainEditorState | null) => setEditor(value), [])
  const brainId = brainIdFromPath(location.pathname)
  const customer = session?.role === 'CUSTOMER_ADMIN'
  const valid = workspace.selectedDeploymentId === brainId && Boolean(brainId)
  const params = new URLSearchParams(location.search)
  const requestedTarget = params.get('targetProfileId') ?? ''
  const profiles = useQuery({ queryKey: ['brain-target-options', brainId, session?.role],
    queryFn: () => customer ? fetchDeploymentTargetOptions(brainId) : fetchDeploymentTargetProfiles(), enabled: valid, retry: false })
  const releases = useQuery({ queryKey: ['deployment-releases', brainId], queryFn: () => fetchDeploymentReleases(brainId), enabled: valid })
  const versions = useQuery({ queryKey: ['deployment-versions', brainId], queryFn: () => fetchDeploymentVersions(brainId), enabled: valid })
  const options = profiles.data ?? []
  const targetProfile = options.find(item => item.id === requestedTarget) ?? null
  const targetProfileId = targetProfile?.id ?? ''
  const scopedReleases = (releases.data ?? []).filter(item => item.targetProfileId === targetProfileId)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
  const applied = scopedReleases.find(isUsableRelease) ?? null
  const requestedRelease = params.get('releaseId')
  const candidateRelease = requestedRelease ? scopedReleases.find(item => item.id === requestedRelease && isUsableRelease(item)) ?? null : applied
  const latest = scopedReleases[0]
  const section = currentBrainSection(location.pathname)
  const sectionLabel = brainSections.find(item => item.path === section)?.label ?? 'Overview'
  const brain = workspace.selectedDeploymentSummary
  const dirty = Boolean(editor?.dirty)
  const busy = Boolean(editor?.busy)
  const editingRelease = useRef<{ scope: string; release: typeof candidateRelease } | null>(null)
  editingRelease.current = retainEditingRelease(editingRelease.current, `${brainId}:${targetProfileId}`, candidateRelease, dirty || busy)
  const release = editingRelease.current.release
  const releaseHeld = release?.id !== candidateRelease?.id
  const blocker = useBlocker(({ currentLocation, nextLocation }) => (dirty || busy)
    && `${currentLocation.pathname}${currentLocation.search}` !== `${nextLocation.pathname}${nextLocation.search}`)

  useEffect(() => {
    const footer = footerRef.current
    if (!footer) return
    const observer = new ResizeObserver(() => setFooterHeight(Math.ceil(footer.getBoundingClientRect().height)))
    observer.observe(footer)
    return () => observer.disconnect()
  }, [valid])

  useEffect(() => {
    if (requestedTarget || !profiles.isSuccess || !releases.isSuccess) return
    const defaults = options.filter(item => item.active && item.defaultForRuntime)
    const recent = [...(releases.data ?? [])].filter(isUsableRelease).sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .find(item => options.some(option => option.id === item.targetProfileId))
    const initial = recent?.targetProfileId ?? (defaults.length === 1 ? defaults[0].id : options.length === 1 ? options[0].id : '')
    if (!initial) return
    const next = new URLSearchParams(location.search)
    next.set('targetProfileId', initial)
    navigate({ pathname: location.pathname, search: next.toString() }, { replace: true })
  }, [requestedTarget, profiles.isSuccess, releases.isSuccess, options, releases.data, location.pathname, location.search, navigate])

  useEffect(() => {
    if (!dirty && !busy) return
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', prevent)
    return () => window.removeEventListener('beforeunload', prevent)
  }, [dirty, busy])

  const href = useCallback((path = '') => brainPath(brainId, path, requestedTarget), [brainId, requestedTarget])
  const context = useMemo(() => ({ brainId, targetProfileId, targetProfile, release,
    target: release && targetProfileId ? { deploymentId: brainId, targetProfileId, releaseId: release.id } : null,
    href }), [brainId, targetProfileId, targetProfile, release, href])
  const appliedLabel = versions.data?.find(item => item.id === applied?.deploymentVersionId)?.versionLabel
    ?? (applied ? applied.deploymentVersionId : '')
  const changed = Boolean(workspace.workspace && (!workspace.workspace.lifecycle.savedDraftMatchesLatestPublished
    || (workspace.workspace.latestVersion && workspace.workspace.latestVersion.id !== applied?.deploymentVersionId)))
  const targetLabel = targetProfile?.environmentName ?? 'environment'
  const applyFailed = latest && ['FAILED', 'APPLIED_VERIFICATION_FAILED'].includes(latest.status)
  const nav = <Box component="nav" aria-label={`${deploymentTerm.singular} navigation`} sx={{ display: 'flex', flexDirection: 'column', height: '100%', py: 2 }}>
    <Button component={RouterLink} to="/deployments" startIcon={<ArrowBackRounded />} sx={{ alignSelf: 'flex-start', mx: 1.5, color: 'text.primary', mb: 2 }}>All {deploymentTerm.plural}</Button>
    <Typography sx={{ fontSize: 19, fontWeight: 650, px: 2.5, mb: 2.5, overflowWrap: 'anywhere' }}>{brain?.name ?? deploymentTerm.singular}</Typography>
    {['Build', 'Experience', 'Release'].map(group => <Box key={group} sx={{ mb: 1.5 }}>
      <Typography sx={{ px: 2.5, py: 0.75, fontSize: 10, fontWeight: 650, textTransform: 'uppercase', color: 'text.secondary' }}>{group}</Typography>
      <List disablePadding>{brainSections.map((item, index) => {
        if (item.group !== group) return null
        const Icon = sectionIcons[index]
        return <ListItemButton key={item.path} component={RouterLink} to={href(item.path)} selected={section === item.path}
          aria-current={section === item.path ? 'page' : undefined} onClick={() => setMenuOpen(false)}
          sx={{ px: 2.5, minHeight: 41, borderLeft: 3, borderColor: section === item.path ? 'primary.main' : 'transparent',
            '&.Mui-selected': { bgcolor: 'action.selected', color: 'primary.main' } }}>
          <ListItemIcon sx={{ minWidth: 31, color: 'inherit' }}><Icon sx={{ fontSize: 20 }} /></ListItemIcon>
          <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: 13, fontWeight: section === item.path ? 600 : 400 }} />
        </ListItemButton>
      })}</List>
    </Box>)}
    <Box sx={{ flexGrow: 1 }} /><Divider sx={{ mx: 2, mb: 1.25 }} />
    <ListItemButton component={RouterLink} to={href('settings')} selected={section === 'settings'} onClick={() => setMenuOpen(false)} sx={{ px: 2.5 }}>
      <ListItemIcon sx={{ minWidth: 31, color: 'inherit' }}><SettingsOutlined sx={{ fontSize: 20 }} /></ListItemIcon><ListItemText primary="Technical settings" primaryTypographyProps={{ fontSize: 13 }} />
    </ListItemButton>
  </Box>

  return <BrainWorkspaceContext.Provider value={context}><BrainEditorContext.Provider value={registerEditor}>
    <Box data-brain-workspace sx={{ '--customer-navigation-width': compact ? '0px' : '210px', height: { xs: 'auto', md: '100dvh' }, minHeight: '100dvh', display: 'flex', flexDirection: 'column', overflow: { md: 'hidden' }, bgcolor: 'background.default' }}>
      <Stack component="header" direction="row" alignItems="center" sx={{ gap: { xs: 0.75, md: 2 }, height: 56, flexShrink: 0, px: { xs: 1.5, md: 2.5 }, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        {compact && <IconButton aria-label={`Open ${deploymentTerm.singular} navigation`} onClick={() => setMenuOpen(true)}><MenuRounded /></IconButton>}
        <Stack component="a" href="https://loomai.pro/" direction="row" gap={1} alignItems="center" sx={{ color: 'text.primary', textDecoration: 'none', pr: { md: 2 }, borderRight: { md: 1 }, borderColor: 'divider' }}>
          <HubOutlined color="primary" /><Typography fontWeight={700} fontSize={19}>LoomAI</Typography>
        </Stack>
        {compact ? <Typography fontSize={12} noWrap sx={{ flex: 1, minWidth: 0 }}>{sectionLabel}</Typography> : <Breadcrumbs aria-label="Brain breadcrumb" sx={{ flex: 1, minWidth: 0, fontSize: 12, '& ol': { flexWrap: 'nowrap' }, '& li': { minWidth: 0 } }}>
          <Link component={RouterLink} to={customer ? '/home' : '/deployments'} color="inherit" underline="hover">Console</Link>
          <Link component={RouterLink} to="/deployments" color="inherit" underline="hover">{deploymentTerm.plural}</Link>
          <Link component={RouterLink} to={href()} color="inherit" underline="hover" sx={{ whiteSpace: 'nowrap', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}>{brain?.name ?? deploymentTerm.singular}</Link>
          <Typography fontSize={12} noWrap>{sectionLabel}</Typography>
        </Breadcrumbs>}
        {import.meta.env.VITE_SMART_BRAIN_REVIEW === 'true' && !compact && <Typography variant="caption" color="warning.main" sx={{ flexShrink: 0 }}>Local review · read-only</Typography>}
        <Tooltip title={`Switch to ${theme.palette.mode === 'dark' ? 'light' : 'dark'} mode`}><IconButton aria-label="Switch color theme" onClick={() => color.setPreference(theme.palette.mode === 'dark' ? 'light' : 'dark')}>
          {theme.palette.mode === 'dark' ? <LightModeOutlined fontSize="small" /> : <DarkModeOutlined fontSize="small" />}</IconButton></Tooltip>
        {!compact && <Tooltip title="Help and integration support"><IconButton component={RouterLink} to={customer ? '/support' : '/customer-workspaces'} aria-label="Help and integration support"><HelpOutlineRounded fontSize="small" /></IconButton></Tooltip>}
        {!compact && <Tooltip title={session?.displayName ?? 'Account'}><Avatar component={RouterLink} to="/users" sx={{ width: 29, height: 29, fontSize: 12, bgcolor: 'action.selected', color: 'text.primary', textDecoration: 'none' }}>{session?.displayName?.[0] ?? 'A'}</Avatar></Tooltip>}
        <Tooltip title="Sign out"><IconButton aria-label="Sign out" onClick={() => { void onSignOut() }}><LogoutRounded sx={{ fontSize: 18 }} /></IconButton></Tooltip>
      </Stack>
      <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <Drawer variant={compact ? 'temporary' : 'permanent'} open={!compact || menuOpen} onClose={() => setMenuOpen(false)}
          sx={{ width: compact ? 0 : 210, flexShrink: 0, '& .MuiDrawer-paper': { width: 210, position: compact ? 'fixed' : 'relative', height: '100%', bgcolor: 'background.paper', boxSizing: 'border-box' } }}>{nav}</Drawer>
        <Box sx={{ flex: 1, minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <Stack direction="row" gap={2} alignItems="center" flexWrap="wrap" sx={{ px: { xs: 2, md: 3 }, py: 1.5, minHeight: 64, flexShrink: 0, borderBottom: 1, borderColor: 'divider' }}>
            <Tooltip title="This saved configuration draft is shared. Choose where to inspect or apply it."><Typography fontSize={13} fontWeight={600}>Editing shared draft</Typography></Tooltip>
            <FormControl size="small" sx={{ minWidth: 140, maxWidth: 220 }}><Select displayEmpty value={requestedTarget} inputProps={{ 'aria-label': 'Environment' }}
              renderValue={() => targetProfile ? targetProfile.environmentName.charAt(0).toUpperCase() + targetProfile.environmentName.slice(1) : requestedTarget ? 'Unavailable environment' : 'Choose environment'}
              onChange={event => { const next = new URLSearchParams(location.search); next.set('targetProfileId', event.target.value); next.delete('releaseId'); navigate({ pathname: location.pathname, search: next.toString() }) }}>
              <MenuItem value="" disabled>Choose environment</MenuItem>
              {requestedTarget && !targetProfile && <MenuItem value={requestedTarget} disabled>Unavailable environment</MenuItem>}
              {options.map(option => <MenuItem key={option.id} value={option.id} disabled={!option.active}><Box><Typography fontSize={13}>{option.environmentName.charAt(0).toUpperCase() + option.environmentName.slice(1)}</Typography>
                {options.filter(item => item.environmentName === option.environmentName).length > 1 && <Typography fontSize={11} color="text.secondary">{option.name}</Typography>}</Box></MenuItem>)}
            </Select></FormControl>
            <Stack direction="row" gap={0.75} alignItems="center"><Box sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: applyFailed ? 'error.main' : applied ? 'success.main' : 'text.disabled' }} />
              <Typography fontSize={12} color="text.secondary">{profiles.isError || releases.isError ? 'Status unavailable' : profiles.isPending || releases.isPending ? 'Loading environment...' : !targetProfile ? 'Choose an environment' : applyFailed ? 'Update needs attention' : applied ? `Applied ${appliedLabel}` : 'Not deployed'}</Typography></Stack>
            {changed && <Typography fontSize={12} color="warning.main">Changes not applied</Typography>}
            {!customer && <Typography sx={{ ml: 'auto', fontSize: 11, color: 'text.secondary' }}>{brain?.binding?.customerName ? `Assisting ${brain.binding.customerName}` : 'Staff access'}</Typography>}
          </Stack>
          <Box component="main" id="brain-main" sx={{ flex: 1, minHeight: 0, overflow: { md: 'hidden' }, display: 'flex', flexDirection: 'column', p: { xs: 2, md: 3 } }}>
            {releaseHeld && <Alert severity="info" sx={{ mb: 2 }}>A newer release is available. Finish or discard your current task before switching its context. The header shows the latest applied state.</Alert>}
            {workspace.deploymentsLoading ? <CircularProgress size={26} aria-label={`Loading ${deploymentTerm.singular}`} /> : !valid ? <Alert severity="error">This {deploymentTerm.lowercase} is unavailable or you do not have access. <Link component={RouterLink} to="/deployments">Return to your {deploymentTerm.pluralLowercase}</Link></Alert> : <>
              {profiles.isError && <Alert severity="error">{profiles.error.message}</Alert>}
              {requestedTarget && profiles.isSuccess && !targetProfile ? <Alert severity="error">The requested environment is not available. No alternative environment has been selected.</Alert> : requestedRelease && releases.isSuccess && !release ? <Alert severity="error">The requested release is not active in this environment. Choose a current environment before continuing.</Alert> : children}
            </>}
          </Box>
          {valid && <Box component="footer" ref={footerRef} aria-label="Workspace actions" sx={{ borderTop: 1, borderColor: 'divider', flexShrink: 0, px: { xs: 2, md: 3 }, py: 1.5, bgcolor: 'background.paper' }}>
            {editor?.error && <Alert severity="error" sx={{ mb: 1 }}>{editor.error}</Alert>}
            <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} alignItems={{ xs: 'stretch', sm: 'center' }} justifyContent="space-between">
              <Typography variant="body2" color={dirty ? 'warning.main' : 'text.secondary'}>{editor?.message ?? (dirty ? 'Unsaved changes' : `Shared draft${workspace.workspace?.draft ? ` r${workspace.workspace.draft.revisionNumber}` : ''}. Applied configuration is unchanged.`)}</Typography>
              <Stack direction="row" gap={1} sx={{ flexShrink: 0, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                {editor?.discard && <Button disabled={busy} variant="outlined" onClick={() => editor.discard?.()}>{editor.secondaryLabel ?? 'Discard'}</Button>}
                {editor?.save ? <Button variant="contained" disabled={busy || editor.canSave === false} onClick={() => { void editor.save?.() }}>{busy ? 'Working...' : editor.primaryLabel ?? 'Save draft'}</Button>
                  : section !== 'updates' && <Button component={RouterLink} to={href('updates')} variant="outlined">Review & publish</Button>}
              </Stack>
            </Stack>
          </Box>}
        </Box>
      </Box>
      {customer && <CustomerSupportAssistant bottomOffset={footerHeight} initiallyMinimized />}
    </Box>
    <Dialog open={blocker.state === 'blocked'} onClose={() => blocker.state === 'blocked' && blocker.reset()} maxWidth="xs" fullWidth>
      <DialogTitle>{busy ? 'An operation is in progress' : 'Leave unsaved changes?'}</DialogTitle>
      <DialogContent>{busy ? 'Wait for the current operation to finish before changing the workspace.' : 'Unsaved input will be discarded. Saved configuration is unchanged.'}</DialogContent>
      <DialogActions><Button onClick={() => blocker.state === 'blocked' && blocker.reset()}>Keep editing</Button>
        {!busy && <Button color="warning" onClick={() => { editor?.discard?.(); if (blocker.state === 'blocked') blocker.proceed() }}>Discard & leave</Button>}</DialogActions>
    </Dialog>
  </BrainEditorContext.Provider></BrainWorkspaceContext.Provider>
}
