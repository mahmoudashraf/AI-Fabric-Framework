import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Box, Chip, FormControlLabel, IconButton, MenuItem, Paper, Stack, Switch, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material'
import DesktopWindowsOutlined from '@mui/icons-material/DesktopWindowsOutlined'
import SmartphoneOutlined from '@mui/icons-material/SmartphoneOutlined'
import LightModeOutlined from '@mui/icons-material/LightModeOutlined'
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import type { WorkspaceAppearance } from '../../pages/aiWorkspaceAppearance'
import { engagePreviewDocument } from './engagePreviewDocument'
import { buildWorkspacePreviewConfiguration, type PreviewCapabilities, type PreviewInteractionPolicy } from './buildWorkspacePreviewConfiguration'

let bundle: Promise<string> | null = null
function previewBundle() {
  if (!bundle) bundle = fetch('/engage-preview.iife.js', { credentials: 'omit', cache: 'no-store' }).then(async response => {
    if (!response.ok) throw new Error('The visual preview asset is unavailable. Your draft has not changed.')
    const value = await response.text()
    if (value.trimStart().startsWith('<') || value.length < 1000) throw new Error('The visual preview asset is invalid.')
    return value
  }).catch(error => { bundle = null; throw error })
  return bundle
}

export function EngageAppearancePreview({ appearance, appName, deliveryMode, configuration: selectedConfiguration, packCode, interactionPolicy, capabilities, showBrandingError }: {
  appearance: WorkspaceAppearance; appName: string; deliveryMode: string; showBrandingError: boolean
  configuration: Record<string, unknown>; packCode: string; interactionPolicy: PreviewInteractionPolicy; capabilities: PreviewCapabilities
}) {
  const iframe = useRef<HTMLIFrameElement>(null)
  const viewport = useRef<HTMLDivElement>(null)
  const [html, setHtml] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [ready, setReady] = useState(false)
  const [width, setWidth] = useState(560)
  const [device, setDevice] = useState('desktop')
  const [layout, setLayout] = useState(deliveryMode === 'standalone' ? 'standalone' : 'embedded')
  const [sample, setSample] = useState('welcome')
  const [signedIn, setSignedIn] = useState(false)
  const [attachment, setAttachment] = useState(false)
  const [overrideDark, setOverrideDark] = useState<boolean | null>(null)
  const [systemDark, setSystemDark] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches)
  useEffect(() => { setOverrideDark(null) }, [appearance.darkMode])
  useEffect(() => { setLayout(deliveryMode === 'standalone' ? 'standalone' : 'embedded') }, [deliveryMode])
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const change = () => setSystemDark(media.matches)
    media.addEventListener('change', change)
    return () => media.removeEventListener('change', change)
  }, [])
  useEffect(() => {
    let current = true
    setError(null); setReady(false)
    previewBundle().then(script => { if (current) setHtml(engagePreviewDocument(script)) })
      .catch(failure => { if (current) setError(failure.message) })
    return () => { current = false }
  }, [attempt])
  useEffect(() => {
    const onReady = (event: MessageEvent) => {
      if (event.source === iframe.current?.contentWindow && event.data?.type === 'loomai:engage-preview:ready') setReady(true)
    }
    window.addEventListener('message', onReady)
    return () => window.removeEventListener('message', onReady)
  }, [])
  useEffect(() => {
    if (!viewport.current) return
    const observer = new ResizeObserver(entries => setWidth(entries[0].contentRect.width))
    observer.observe(viewport.current)
    return () => observer.disconnect()
  }, [])
  const configuration = useMemo(() => buildWorkspacePreviewConfiguration({ configuration: selectedConfiguration, packCode, interactionPolicy, capabilities,
    appearance: { ...appearance, logoUrl: showBrandingError ? '' : appearance.logoUrl.trim() } }),
  [selectedConfiguration, packCode, interactionPolicy, capabilities, appearance, showBrandingError])
  const dark = overrideDark ?? (appearance.darkMode === 'auto' ? systemDark : appearance.darkMode === 'dark')
  const scenario = { appName, layout, sample: !configuration.capabilities.handover.enabled && ['pending', 'human'].includes(sample) ? 'conversation' : sample,
    signedIn: interactionPolicy !== 'public' && signedIn, attachment: (configuration.capabilities.attachments || configuration.currentPage.enabled) && attachment, dark }
  const message = { type: 'loomai:engage-preview:configure', configuration, scenario }
  const messageKey = JSON.stringify(message)
  const latest = useRef(message)
  latest.current = message
  useEffect(() => {
    if (ready) iframe.current?.contentWindow?.postMessage(latest.current, '*')
  }, [ready, messageKey])
  const logicalWidth = device === 'mobile' ? 390 : 960
  const logicalHeight = device === 'mobile' ? 760 : 700
  const scale = Math.min(1, Math.max(1, width) / logicalWidth)

  return <Paper variant="outlined" sx={{ borderRadius: 1, overflow: 'hidden', position: { lg: 'sticky' }, top: 0 }}>
    <Stack spacing={1.5} sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
        <Typography fontWeight={700}>Customer preview</Typography><Chip size="small" label="Sample content" variant="outlined" />
      </Stack>
      <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap" useFlexGap>
        <ToggleButtonGroup exclusive size="small" aria-label="Preview device" value={device} onChange={(_, value) => { if (value) setDevice(value) }}>
          <ToggleButton value="desktop" aria-label="Desktop preview"><Tooltip title="Desktop"><DesktopWindowsOutlined fontSize="small" /></Tooltip></ToggleButton>
          <ToggleButton value="mobile" aria-label="Phone preview"><Tooltip title="Phone"><SmartphoneOutlined fontSize="small" /></Tooltip></ToggleButton>
        </ToggleButtonGroup>
        <TextField select size="small" label="View" value={layout} onChange={event => setLayout(event.target.value)} sx={{ flex: 1, minWidth: 150 }}>
          <MenuItem value="launcher">Website launcher</MenuItem><MenuItem value="embedded">Website dock</MenuItem><MenuItem value="max">MAX workspace</MenuItem><MenuItem value="standalone">Standalone app</MenuItem>
        </TextField>
        <Tooltip title={dark ? 'Preview light appearance' : 'Preview dark appearance'}>
          <IconButton aria-label={dark ? 'Preview light appearance' : 'Preview dark appearance'} onClick={() => setOverrideDark(!dark)}>{dark ? <LightModeOutlined /> : <DarkModeOutlined />}</IconButton>
        </Tooltip>
        {overrideDark != null ? <Tooltip title="Use configured appearance"><IconButton aria-label="Use configured appearance" onClick={() => setOverrideDark(null)}><RefreshRounded /></IconButton></Tooltip> : null}
      </Stack>
      <TextField select size="small" label="Sample screen" value={scenario.sample} onChange={event => setSample(event.target.value)}>
        <MenuItem value="welcome">Welcome</MenuItem><MenuItem value="conversation">Conversation</MenuItem>
        <MenuItem value="results">Registered results</MenuItem><MenuItem value="fallback">Generic result</MenuItem>
        <MenuItem value="confirmation">Confirmation</MenuItem><MenuItem value="receipt">Receipt</MenuItem>
        {configuration.capabilities.handover.enabled && <MenuItem value="pending">Support pending</MenuItem>}
        {configuration.capabilities.handover.enabled && <MenuItem value="human">Human support</MenuItem>}
      </TextField>
      <Stack direction="row" flexWrap="wrap" useFlexGap>
        {interactionPolicy !== 'public' && <FormControlLabel control={<Switch size="small" checked={signedIn} onChange={(_, checked) => setSignedIn(checked)} disabled={!configuration.capabilities.authentication.signInAvailable} />} label="Signed-in sample" />}
        <FormControlLabel control={<Switch size="small" checked={scenario.attachment} onChange={(_, checked) => setAttachment(checked)} disabled={!configuration.capabilities.attachments && !configuration.currentPage.enabled} />} label="Attached context" />
      </Stack>
      <Typography variant="caption" color="text.secondary">Unsaved configuration. Synthetic content only. No AI calls, sign-in, actions or saved visitor state.</Typography>
    </Stack>
    {error ? <Alert severity="warning" action={<IconButton aria-label="Retry visual preview" onClick={() => setAttempt(value => value + 1)}><RefreshRounded /></IconButton>}>{error}</Alert> : null}
    {!error && !ready ? <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>Loading visual preview...</Typography> : null}
    <Box ref={viewport} sx={{ p: 0, bgcolor: 'action.hover', minWidth: 0, overflow: 'hidden' }}>
      <Box sx={{ width: Math.min(width, logicalWidth), height: logicalHeight * scale, mx: 'auto', position: 'relative' }}>
        {html ? <iframe ref={iframe} key={attempt} title="Engage customer appearance preview" sandbox="allow-scripts"
          referrerPolicy="no-referrer" srcDoc={html}
          style={{ border: 0, display: 'block', width: logicalWidth, height: logicalHeight, transform: `scale(${scale})`, transformOrigin: 'top left', background: '#fff' }} /> : null}
      </Box>
    </Box>
  </Paper>
}
