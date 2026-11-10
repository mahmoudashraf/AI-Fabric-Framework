import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link as RouterLink, useBlocker, useLocation, useNavigate } from 'react-router-dom'
import { useIsMutating, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Avatar, Box, Breadcrumbs, Button, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, Drawer, IconButton, Link, List, ListItemButton, ListItemIcon,
  ListItemText, Stack, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material'
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
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined'
import PsychologyOutlined from '@mui/icons-material/PsychologyOutlined'
import KeyOutlined from '@mui/icons-material/KeyOutlined'
import { fetchDeploymentReleases, fetchDeploymentTargetOptions, fetchDeploymentTargetProfiles, fetchDeploymentVersions, type PlatformAuthSessionSummary } from '../api/platformApi'
import { CustomerConsoleTheme, useCustomerColorPreference } from '../theme/CustomerConsoleTheme'
import { CustomerSupportProvider } from '../components/customer/CustomerSupportContext'
import { CustomerSupportAssistant } from '../components/customer/CustomerSupportAssistant'
import { SupportResourceDialog } from '../components/customer/SupportResourceDialog'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { BrainEditorContext, BrainWorkspaceContext, type BrainEditorState } from './BrainWorkspaceContext'
import { brainAdvancedSections, brainIdFromPath, brainPath, brainSections, currentBrainSection, isUsableRelease, retainEditingRelease } from './brainNavigation'
import { deploymentTerm } from '../deploymentTerminology'
import { accountPath } from '../account/accountNavigation'
import { BrainEnvironmentSwitch } from './BrainEnvironmentSwitch'
import { environmentAllowed, stagingOnly } from '../developer/deploymentPolicy'
import { AssistantWorkspace } from '../assistant/AssistantWorkspace'
import { brainAssistantScope } from '../assistant/scope'
import { CustomerSpecialistAssistance } from '../components/customer/CustomerSpecialistAssistance'

const sectionIcons = { '': HomeOutlined, knowledge: LayersOutlined, capabilities: AutoAwesomeOutlined,
  responses: ChatBubbleOutlineRounded, models: PsychologyOutlined, channels: AccountTreeOutlined,
  'human-support': HeadsetMicOutlined, 'action-reviews': FactCheckOutlined, try: PlayArrowOutlined,
  updates: SystemUpdateAltOutlined, secrets: KeyOutlined, advanced: SettingsOutlined }

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
  const queryClient = useQueryClient()
  const mutations = useIsMutating()
  const theme = useTheme()
  const compact = useMediaQuery(theme.breakpoints.down('md'))
  const color = useCustomerColorPreference()
  const [menuOpen, setMenuOpen] = useState(false)
  const [editor, setEditor] = useState<BrainEditorState | null>(null)
  const [assistantBusy, setAssistantBusy] = useState(false)
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
  const brain = workspace.selectedDeploymentSummary
  const options = (profiles.data ?? []).filter(profile => environmentAllowed(brain?.deploymentPolicy, profile.environmentName))
  const targetProfile = options.find(item => item.id === requestedTarget) ?? null
  const targetProfileId = targetProfile?.id ?? ''
  const scopedReleases = (releases.data ?? []).filter(item => item.targetProfileId === targetProfileId)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
  const applied = scopedReleases.find(isUsableRelease) ?? null
  const requestedRelease = params.get('releaseId')
  const candidateRelease = requestedRelease ? scopedReleases.find(item => item.id === requestedRelease && isUsableRelease(item)) ?? null : applied
  const section = currentBrainSection(location.pathname)
  const sectionLabel = brainSections.find(item => item.path === section)?.label ?? 'Overview'
  const childLabel = section === 'advanced' ? brainAdvancedSections.find(item => item.path === location.pathname.split('/')[4])?.label
    : section === 'knowledge' && location.pathname.split('/')[4] === 'search' ? 'Search settings'
      : section === 'human-support' && location.pathname.split('/')[4] === 'specialists' ? 'Specialists' : null
  const dirty = Boolean(editor?.dirty)
  const specialistRoute = location.pathname === `/smart-brains/${encodeURIComponent(brainId)}/human-support/specialists`
  const manualBusy = Boolean(editor?.busy) || specialistRoute && mutations > 0
  const busy = manualBusy || assistantBusy
  const assistantScope = valid ? brainAssistantScope(location.pathname, location.search, brainId) : null
  const editingRelease = useRef<{ scope: string; release: typeof candidateRelease } | null>(null)
  editingRelease.current = retainEditingRelease(editingRelease.current, `${brainId}:${targetProfileId}`, candidateRelease, dirty || busy)
  const release = editingRelease.current.release
  const releaseHeld = release?.id !== candidateRelease?.id
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (!dirty && !busy || `${currentLocation.pathname}${currentLocation.search}` === `${nextLocation.pathname}${nextLocation.search}`) return false
    // Only a mounted editor can declare sibling tasks that retain its buffer.
    if (!busy && currentLocation.search === nextLocation.search
      && editor?.retainedNavigationPaths?.includes(currentLocation.pathname)
      && editor.retainedNavigationPaths.includes(nextLocation.pathname)) return false
    return true
  })

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
    const exactRelease = requestedRelease ? (releases.data ?? []).find(item => item.id === requestedRelease
      && options.some(option => option.id === item.targetProfileId)) : null
    const initial = exactRelease?.targetProfileId ?? (requestedRelease ? '' : recent?.targetProfileId)
      ?? (defaults.length === 1 ? defaults[0].id : options.length === 1 ? options[0].id : '')
    if (!initial) return
    const next = new URLSearchParams(location.search)
    next.set('targetProfileId', initial)
    navigate({ pathname: location.pathname, search: next.toString() }, { replace: true })
  }, [requestedTarget, requestedRelease, profiles.isSuccess, releases.isSuccess, options, releases.data, location.pathname, location.search, navigate])

  useEffect(() => {
    if (!dirty && !busy) return
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', prevent)
    return () => window.removeEventListener('beforeunload', prevent)
  }, [dirty, busy])

  const href = useCallback((path = '') => {
    const [pathname, search] = brainPath(brainId, path, requestedTarget).split('?')
    const query = new URLSearchParams(search)
    if (requestedRelease) query.set('releaseId', requestedRelease)
    return `${pathname}${query.size ? `?${query}` : ''}`
  }, [brainId, requestedTarget, requestedRelease])
  const context = useMemo(() => ({ brainId, targetProfileId, targetProfile, release,
    target: release && targetProfileId ? { deploymentId: brainId, targetProfileId, releaseId: release.id } : null,
    href }), [brainId, targetProfileId, targetProfile, release, href])
  const changed = Boolean(workspace.workspace && (!workspace.workspace.lifecycle.savedDraftMatchesLatestPublished
    || (workspace.workspace.latestVersion && workspace.workspace.latestVersion.id !== applied?.deploymentVersionId)))
  const nav = <Box component="nav" aria-label={`${deploymentTerm.singular} navigation`} sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0, overflow: 'hidden', py: 2 }}>
    <Box sx={{ flexShrink: 0 }}>
    <Button component={RouterLink} to="/deployments" startIcon={<ArrowBackRounded />} sx={{ alignSelf: 'flex-start', mx: 1.5, color: 'text.primary', mb: 2 }}>All {deploymentTerm.plural}</Button>
    <Typography sx={{ fontSize: 19, fontWeight: 650, px: 2.5, mb: 2.5, overflowWrap: 'anywhere' }}>{brain?.name ?? deploymentTerm.singular}</Typography>
    {stagingOnly(brain?.deploymentPolicy) && <Typography sx={{ px: 2.5, mb: 2, fontSize: 12, color: 'primary.main' }}>Developer · Staging only</Typography>}
    </Box>
    <Box data-brain-navigation-scroll sx={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
    {['Build', 'Experience', 'Operations', 'Release'].map(group => <Box key={group} sx={{ mb: 1.5 }}>
      <Typography sx={{ px: 2.5, py: 0.75, fontSize: 10, fontWeight: 650, textTransform: 'uppercase', color: 'text.secondary' }}>{group}</Typography>
      <List disablePadding>{brainSections.map(item => {
        if (item.group !== group) return null
        const Icon = sectionIcons[item.path]
        return <ListItemButton key={item.path} component={RouterLink} to={href(item.path)} selected={section === item.path}
          aria-current={section === item.path ? 'page' : undefined} onClick={() => setMenuOpen(false)}
          sx={{ px: 2.5, minHeight: 41, borderLeft: 3, borderColor: section === item.path ? 'primary.main' : 'transparent',
            '&.Mui-selected': { bgcolor: 'action.selected', color: 'primary.main' } }}>
          <ListItemIcon sx={{ minWidth: 31, color: 'inherit' }}><Icon sx={{ fontSize: 20 }} /></ListItemIcon>
          <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: 13, fontWeight: section === item.path ? 600 : 400 }} />
        </ListItemButton>
      })}</List>
    </Box>)}
    </Box>
    <Box sx={{ flexShrink: 0 }}>
      <Divider sx={{ mx: 2, my: 1.25 }} />
      <ListItemButton component={RouterLink} to={href('secrets')} selected={section === 'secrets'}
        aria-current={section === 'secrets' ? 'page' : undefined} onClick={() => setMenuOpen(false)} sx={{ px: 2.5 }}>
        <ListItemIcon sx={{ minWidth: 31, color: 'inherit' }}><KeyOutlined sx={{ fontSize: 20 }} /></ListItemIcon><ListItemText primary="Keys & secrets" primaryTypographyProps={{ fontSize: 13 }} />
      </ListItemButton>
      <ListItemButton component={RouterLink} to={href('advanced/runtime')} selected={section === 'advanced'}
        aria-current={section === 'advanced' && !childLabel ? 'page' : undefined} onClick={() => setMenuOpen(false)} sx={{ px: 2.5 }}>
        <ListItemIcon sx={{ minWidth: 31, color: 'inherit' }}><SettingsOutlined sx={{ fontSize: 20 }} /></ListItemIcon><ListItemText primary="Advanced" primaryTypographyProps={{ fontSize: 13 }} />
      </ListItemButton>
      {section === 'advanced' && <List disablePadding aria-label="Advanced settings" sx={{ maxHeight: '35dvh', overflowY: 'auto' }}>
        {brainAdvancedSections.map(item => <ListItemButton key={item.path} component={RouterLink} to={href(`advanced/${item.path}`)}
          selected={childLabel === item.label} aria-current={childLabel === item.label ? 'page' : undefined}
          onClick={() => setMenuOpen(false)} sx={{ pl: 4.5, pr: 1.5, py: 0.5, minHeight: 32 }}>
          <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: 12 }} />
        </ListItemButton>)}
      </List>}
    </Box>
  </Box>

  return <BrainWorkspaceContext.Provider value={context}><BrainEditorContext.Provider value={registerEditor}>
    <Box data-brain-workspace sx={{ '--customer-navigation-width': compact ? '0px' : '210px', height: { xs: 'auto', md: '100dvh' }, minHeight: '100dvh', display: 'flex', flexDirection: 'column', overflow: { md: 'hidden' }, bgcolor: 'background.default' }}>
      <Stack component="header" direction="row" alignItems="center" sx={{ gap: { xs: 0.75, md: 2 }, height: 56, flexShrink: 0, px: { xs: 1.5, md: 2.5 }, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        {compact && <IconButton aria-label={`Open ${deploymentTerm.singular} navigation`} onClick={() => setMenuOpen(true)}><MenuRounded /></IconButton>}
        <Stack component="a" href="https://loomai.pro/" direction="row" gap={1} alignItems="center" sx={{ color: 'text.primary', textDecoration: 'none', pr: { md: 2 }, borderRight: { md: 1 }, borderColor: 'divider' }}>
          <HubOutlined color="primary" /><Typography fontWeight={700} fontSize={19}>LoomAI</Typography>
        </Stack>
        {compact ? <Typography fontSize={12} noWrap sx={{ flex: 1, minWidth: 0 }}>{childLabel ?? sectionLabel}</Typography> : <Breadcrumbs aria-label="Brain breadcrumb" sx={{ flex: 1, minWidth: 0, fontSize: 12, '& ol': { flexWrap: 'nowrap' }, '& li': { minWidth: 0 } }}>
          <Link component={RouterLink} to={customer ? '/home' : '/deployments'} color="inherit" underline="hover">Console</Link>
          <Link component={RouterLink} to="/deployments" color="inherit" underline="hover">{deploymentTerm.plural}</Link>
          <Link component={RouterLink} to={href()} color="inherit" underline="hover" sx={{ whiteSpace: 'nowrap', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}>{brain?.name ?? deploymentTerm.singular}</Link>
          {childLabel && <Link component={RouterLink} to={href(section)} color="inherit" underline="hover">{sectionLabel}</Link>}
          <Typography fontSize={12} noWrap>{childLabel ?? sectionLabel}</Typography>
        </Breadcrumbs>}
        {import.meta.env.VITE_SMART_BRAIN_REVIEW === 'true' && !compact && <Typography variant="caption" color="warning.main" sx={{ flexShrink: 0 }}>Local review · read-only</Typography>}
        <Tooltip title={`Switch to ${theme.palette.mode === 'dark' ? 'light' : 'dark'} mode`}><IconButton aria-label="Switch color theme" onClick={() => color.setPreference(theme.palette.mode === 'dark' ? 'light' : 'dark')}>
          {theme.palette.mode === 'dark' ? <LightModeOutlined fontSize="small" /> : <DarkModeOutlined fontSize="small" />}</IconButton></Tooltip>
        {!compact && <Tooltip title="Help and integration support"><IconButton component={RouterLink} to={customer ? '/support' : '/customer-workspaces'} aria-label="Help and integration support"><HelpOutlineRounded fontSize="small" /></IconButton></Tooltip>}
        {!compact && <Tooltip title={session?.displayName ?? 'Account'}><Avatar component={RouterLink} to={customer ? accountPath('team', new URLSearchParams({ fromBrain: brainId, targetProfileId: requestedTarget }).toString()) : '/users'} sx={{ width: 29, height: 29, fontSize: 12, bgcolor: 'action.selected', color: 'text.primary', textDecoration: 'none' }}>{session?.displayName?.[0] ?? 'A'}</Avatar></Tooltip>}
        <Tooltip title="Sign out"><IconButton aria-label="Sign out" onClick={() => { void onSignOut() }}><LogoutRounded sx={{ fontSize: 18 }} /></IconButton></Tooltip>
      </Stack>
      <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <Drawer variant={compact ? 'temporary' : 'permanent'} open={!compact || menuOpen} onClose={() => setMenuOpen(false)}
          sx={{ width: compact ? 0 : 210, flexShrink: 0, '& .MuiDrawer-paper': { width: 210, position: compact ? 'fixed' : 'relative', height: '100%', bgcolor: 'background.paper', boxSizing: 'border-box' } }}>{nav}</Drawer>
        <Box sx={{ flex: 1, minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <AssistantWorkspace scope={assistantScope} editorBusy={manualBusy} onBusyChange={setAssistantBusy}
            guideOnly={assistantScope?.task === 'KIT' || section === 'action-reviews'}
            readOnlyReason={workspace.workspace?.access.canEdit === false ? 'You do not have draft editing access.' : undefined}
            onSaved={async () => { await queryClient.invalidateQueries({ predicate: query => query.queryKey.includes(brainId)
              || query.queryKey[0] === 'customer-specialist-assistance' && query.queryKey[1] === JSON.stringify([session?.actorId, session?.customerId, session?.authenticationMode, brainId]) }, { throwOnError: true }) }}>
          {({ revision }) => <>
          <Stack direction="row" gap={2} alignItems="center" flexWrap="wrap" sx={{ px: { xs: 2, md: 3 }, py: 1.5, minHeight: 64, flexShrink: 0, borderBottom: 1, borderColor: 'divider' }}>
            <BrainEnvironmentSwitch profiles={options} releases={releases.data ?? []} versions={versions.data ?? []}
              selectedId={requestedTarget} loading={profiles.isPending || releases.isPending} unavailable={profiles.isError || releases.isError}
              disabled={busy} onChange={id => { const next = new URLSearchParams(location.search); next.set('targetProfileId', id); next.delete('releaseId'); navigate({ pathname: location.pathname, search: next.toString() }) }} />
            <Stack spacing={0.25} sx={{ ml: { sm: 'auto' } }}><Tooltip title={stagingOnly(brain?.deploymentPolicy) ? 'This developer Brain can only run in staging. Publishing saves a version; applying updates its staging environment.' : 'Saved configuration is shared by Staging and Production; published versions are applied separately.'}><Typography fontSize={12} fontWeight={600}>Shared draft{workspace.workspace?.draft ? ` r${workspace.workspace.draft.revisionNumber}` : ''}</Typography></Tooltip>
              <Typography fontSize={11} color={changed ? 'warning.main' : 'text.secondary'}>{changed ? 'Changes not applied' : 'Saved configuration'}</Typography></Stack>
            {!customer && <Typography sx={{ ml: 'auto', fontSize: 11, color: 'text.secondary' }}>{brain?.binding?.customerName ? `Assisting ${brain.binding.customerName}` : 'Staff access'}</Typography>}
          </Stack>
          <Box component="main" id="brain-main" sx={{ flex: 1, minHeight: 0, overflow: { md: 'hidden' }, display: 'flex', flexDirection: 'column', p: { xs: 2, md: 3 } }}>
            {['human-support', 'action-reviews'].includes(section) && <Stack component="nav" aria-label="Human support tasks" direction="row" gap={1} flexWrap="wrap" sx={{ mb: 2 }}>
              <Button component={RouterLink} to={href('human-support')} aria-current={section === 'human-support' && !specialistRoute ? 'page' : undefined}>Handover</Button>
              <Button component={RouterLink} to={href('action-reviews')} aria-current={section === 'action-reviews' ? 'page' : undefined}>Action reviews</Button>
              <Button component={RouterLink} to={href('human-support/specialists')} aria-current={specialistRoute ? 'page' : undefined}>Specialists</Button>
            </Stack>}
            {releaseHeld && <Alert severity="info" sx={{ mb: 2 }}>A newer release is available. Finish or discard your current task before switching its context. The header shows the latest applied state.</Alert>}
            {workspace.deploymentsLoading ? <CircularProgress size={26} aria-label={`Loading ${deploymentTerm.singular}`} /> : !valid ? <Alert severity="error">This {deploymentTerm.lowercase} is unavailable or you do not have access. <Link component={RouterLink} to="/deployments">Return to your {deploymentTerm.pluralLowercase}</Link></Alert> : <>
              {profiles.isError && <Alert severity="error">{profiles.error.message}</Alert>}
              {requestedTarget && profiles.isSuccess && !targetProfile ? <Alert severity="error">The requested environment is not available. No alternative environment has been selected.</Alert> : requestedRelease && releases.isSuccess && !release ? <Alert severity="error">The requested release is not active in this environment. Choose a current environment before continuing.</Alert> : <Fragment key={revision}>{specialistRoute ? <Box sx={{ overflow: 'auto' }}><Typography component="h1" fontSize={20} fontWeight={650} sx={{ mb: 2 }}>Specialists</Typography><CustomerSpecialistAssistance deploymentId={brainId} /></Box> : children}</Fragment>}
            </>}
          </Box>
          {valid && <Box component="footer" ref={footerRef} aria-label="Workspace actions" sx={{ borderTop: 1, borderColor: 'divider', flexShrink: 0, px: { xs: 2, md: 3 }, py: 1.5, bgcolor: 'background.paper' }}>
            {editor?.error && <Alert severity="error" sx={{ mb: 1 }}>{editor.error}</Alert>}
            <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} alignItems={{ xs: 'stretch', sm: 'center' }} justifyContent="space-between">
              <Typography variant="body2" color={dirty ? 'warning.main' : 'text.secondary'}>{editor?.message ?? (dirty ? 'Unsaved changes' : `Shared draft${workspace.workspace?.draft ? ` r${workspace.workspace.draft.revisionNumber}` : ''}. Applied configuration is unchanged.`)}</Typography>
              <Stack direction="row" gap={1} sx={{ flexShrink: 0, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                {editor?.discard && <Button disabled={busy} variant="outlined" onClick={() => editor.discard?.()}>{editor.secondaryLabel ?? 'Discard'}</Button>}
                {editor?.save ? <Button variant="contained" disabled={busy || editor.canSave === false} onClick={() => { void editor.save?.() }}>{busy ? 'Working...' : editor.primaryLabel ?? 'Save draft'}</Button>
                  : !['updates', 'action-reviews', 'human-support', 'knowledge'].includes(section) && <Button component={RouterLink} to={href('updates')} variant="outlined">Review & publish</Button>}
              </Stack>
            </Stack>
          </Box>}
          </>}
          </AssistantWorkspace>
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
