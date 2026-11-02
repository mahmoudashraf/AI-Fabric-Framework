import { Checkbox, FormControlLabel, IconButton, Switch, TextField, Tooltip } from '@mui/material'
import CheckRounded from '@mui/icons-material/CheckRounded'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import { Link } from 'react-router-dom'
import { kitBrandColors, type KitExperienceForm } from './kitModels'

export function KitExperienceFields({ value, onChange, disabled = false, deploymentId, includeName = false }: {
  value: KitExperienceForm; onChange: (value: KitExperienceForm) => void; disabled?: boolean; deploymentId?: string; includeName?: boolean
}) {
  const set = <K extends keyof KitExperienceForm>(key: K, next: KitExperienceForm[K]) => onChange({ ...value, [key]: next })
  return <div className="ek-experience-fields">
    {includeName && <TextField fullWidth size="small" label="Kit name" value={value.name} disabled={disabled}
      onChange={event => set('name', event.target.value)} required inputProps={{ maxLength: 120 }} />}
    <TextField fullWidth size="small" label="Workspace name" value={value.workspaceName} disabled={disabled}
      onChange={event => set('workspaceName', event.target.value)} required inputProps={{ maxLength: 128 }} />
    <div className="ek-color-field"><span>Brand colors</span><div role="group" aria-label="Brand color">
      {kitBrandColors.map(color => <Tooltip title={color} key={color}><IconButton disabled={disabled} aria-label={`Brand color ${color}`}
        aria-pressed={value.brandColor.toLowerCase() === color.toLowerCase()} className="ek-color-swatch" style={{ background: color }}
        onClick={() => set('brandColor', color)}>{value.brandColor.toLowerCase() === color.toLowerCase() ? <CheckRounded /> : null}</IconButton></Tooltip>)}
      <Tooltip title="Choose a custom brand color"><input aria-label="Custom brand color" type="color" value={/^#[0-9a-f]{6}$/i.test(value.brandColor) ? value.brandColor : '#28bfa6'}
        disabled={disabled} onChange={event => set('brandColor', event.target.value)} /></Tooltip>
    </div></div>
    <fieldset className="ek-delivery-field"><legend>Publish as</legend><div>
      <FormControlLabel control={<Checkbox disabled={disabled} checked={value.embedded} onChange={(_, checked) => set('embedded', checked)} />} label="Embed on my website" />
      <FormControlLabel control={<Checkbox disabled={disabled} checked={value.standalone} onChange={(_, checked) => set('standalone', checked)} />} label="Standalone app" />
    </div><small>Use one or both.</small></fieldset>
    {value.standalone && <TextField size="small" label="App address" value={value.appSlug} disabled={disabled}
      onChange={event => set('appSlug', event.target.value)} placeholder="your-company-ai" fullWidth required
      inputProps={{ maxLength: 63, autoCapitalize: 'none', spellCheck: false }} helperText="Choose the name before .apps.loomai.pro" />}
    {value.embedded && <TextField size="small" label="Website origin" value={value.websiteOrigin} disabled={disabled}
      onChange={event => set('websiteOrigin', event.target.value)} placeholder="https://www.example.com" type="url" fullWidth required
      inputProps={{ autoCapitalize: 'none', maxLength: 2048 }} />}
    <section className="ek-support-option" aria-labelledby="ek-support-option-title"><div className="ek-support-option-heading">
      <HeadsetMicOutlined /><div><strong id="ek-support-option-title">Human support</strong><span className="ek-badge">Optional</span></div>
      <Switch inputProps={{ 'aria-label': 'Include human support setup' }} disabled={disabled} checked={value.humanSupportEnabled}
        onChange={(_, checked) => set('humanSupportEnabled', checked)} /></div>
      <p>Let your own agents continue AI conversations through LoomAI Desk.</p>
      {value.humanSupportEnabled && <div className="ek-support-option-details"><strong>{deploymentId ? 'Your team in Desk' : 'Add your team after setup'}</strong>
        <p>Setup preference only. Connect Desk and assign your agents in Human support.</p>
        {deploymentId && <Link to={`/human-support?${new URLSearchParams({ deploymentId })}`}>Manage human support <ArrowForwardRounded /></Link>}
        <small>Your agents use their own accounts.</small>
      </div>}
    </section>
    <details className="ek-advanced"><summary><SettingsOutlined />Advanced configuration</summary><p>Knowledge, integrations, actions, identity and launch settings stay in the existing product editors.</p>
      {deploymentId && <Link to={`/deployments?${new URLSearchParams({ view: 'manage', deploymentId })}`}>Open deployment configuration <ArrowForwardRounded /></Link>}
    </details>
  </div>
}
