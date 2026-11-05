import { Checkbox, FormControlLabel, IconButton, MenuItem, Switch, TextField, Tooltip } from '@mui/material'
import ConfirmationNumberOutlined from '@mui/icons-material/ConfirmationNumberOutlined'
import CheckRounded from '@mui/icons-material/CheckRounded'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import LanguageRounded from '@mui/icons-material/LanguageRounded'
import { Link } from 'react-router-dom'
import { deploymentTerm } from '../../deploymentTerminology'
import { kitAppAddressPreview, kitBrandColors, type KitExperienceForm } from './kitModels'
import type { TicketProviderOption } from '../../api/experienceKitsApi'

export function KitExperienceFields({ value, onChange, disabled = false, deploymentId, includeName = false, supportExperience = false, ticketProviders = [] }: {
  value: KitExperienceForm; onChange: (value: KitExperienceForm) => void; disabled?: boolean; deploymentId?: string; includeName?: boolean
  supportExperience?: boolean; ticketProviders?: TicketProviderOption[]
}) {
  const set = <K extends keyof KitExperienceForm>(key: K, next: KitExperienceForm[K]) => onChange({ ...value, [key]: next })
  const appUrl = kitAppAddressPreview(value.appSlug)
  return <div className="ek-experience-fields">
    {includeName && <TextField fullWidth size="small" label="Kit name" value={value.name} disabled={disabled}
      onChange={event => set('name', event.target.value)} required inputProps={{ maxLength: 120 }} />}
    <TextField fullWidth size="small" label="Workspace name" value={value.workspaceName} disabled={disabled}
      onChange={event => set('workspaceName', event.target.value)} required inputProps={{ maxLength: 120 }} />
    <div className="ek-color-field"><span>Brand colors</span><div role="group" aria-label="Brand color">
      {kitBrandColors.map(color => <Tooltip title={color} key={color}><IconButton disabled={disabled} aria-label={`Brand color ${color}`}
        aria-pressed={value.brandColor.toLowerCase() === color.toLowerCase()} className="ek-color-swatch" style={{ background: color }}
        onClick={() => set('brandColor', color)}>{value.brandColor.toLowerCase() === color.toLowerCase() ? <CheckRounded /> : null}</IconButton></Tooltip>)}
      <Tooltip title="Choose a custom brand color"><input aria-label="Custom brand color" type="color" value={/^#[0-9a-f]{6}$/i.test(value.brandColor) ? value.brandColor : '#28bfa6'}
        disabled={disabled} onChange={event => set('brandColor', event.target.value)} /></Tooltip>
    </div></div>
    <fieldset className="ek-delivery-field"><legend>Where customers will find you</legend><div>
      <FormControlLabel control={<Checkbox disabled={disabled} checked={value.embedded} onChange={(_, checked) => set('embedded', checked)} />} label="Embed on my website" />
      <FormControlLabel control={<Checkbox disabled={disabled} checked={value.standalone} onChange={(_, checked) => set('standalone', checked)} />} label="Standalone app" />
    </div><small>Use one or both.</small></fieldset>
    {value.standalone && <div className="ek-app-address-field"><TextField size="small" label="App address name" value={value.appSlug} disabled={disabled}
      onChange={event => set('appSlug', event.target.value)} placeholder="your-company-ai" fullWidth required
      error={Boolean(value.appSlug && !appUrl)} inputProps={{ maxLength: 63, autoCapitalize: 'none', spellCheck: false }}
      helperText={value.appSlug && !appUrl ? 'Name only: lowercase letters, numbers and single hyphens. For example northfield-ai, without https:// or a domain.' : 'Enter the name only, for example northfield-ai.'} />
      <output className="ek-app-link-preview" aria-label="App link preview" aria-live="polite"><LanguageRounded aria-hidden="true" /><span><small>{appUrl ? 'Your app link' : 'Example app link'}</small><span>{appUrl ?? 'https://your-company-ai.apps.loomai.pro'}</span></span></output>
      <small className="ek-app-link-note">Available after setup, address verification and activation.</small>
    </div>}
    {value.embedded && <TextField size="small" label="Website address" value={value.websiteOrigin} disabled={disabled}
      onChange={event => set('websiteOrigin', event.target.value)} placeholder="https://www.example.com" type="url" fullWidth required
      inputProps={{ autoCapitalize: 'none', maxLength: 255 }} helperText="The website where you will add your AI workspace, without a page path." />}
    {supportExperience && <section className="ek-support-option" aria-labelledby="ek-ticket-option-title"><div className="ek-support-option-heading">
      <ConfirmationNumberOutlined /><div><strong id="ek-ticket-option-title">Request tracking</strong></div>
      <Switch inputProps={{ 'aria-label': 'Include request tracking' }} disabled={disabled} checked={value.ticketingEnabled}
        onChange={(_, checked) => set('ticketingEnabled', checked)} /></div>
      <p>Keep requests linked to the conversation, with or without human support.</p>
      {(value.ticketingEnabled || value.humanSupportEnabled) && <TextField label="Ticket provider" size="small" select required fullWidth value={value.ticketProvider ?? ''}
        disabled={disabled} onChange={event => set('ticketProvider', event.target.value)}>
        <MenuItem value="" disabled>Choose a provider</MenuItem>
        {value.ticketProvider && !ticketProviders.some(provider => provider.code === value.ticketProvider) && <MenuItem value={value.ticketProvider} disabled>Unavailable provider</MenuItem>}
        {ticketProviders.map(provider => <MenuItem key={provider.code} value={provider.code}>{provider.name}</MenuItem>)}
      </TextField>}
      {!ticketProviders.length && <p role="status">No ticket provider is available in this catalogue. Chat without request tracking remains available.</p>}
    </section>}
    <section className="ek-support-option" aria-labelledby="ek-support-option-title"><div className="ek-support-option-heading">
      <HeadsetMicOutlined /><div><strong id="ek-support-option-title">Human support</strong><span className="ek-badge">Optional</span></div>
      <Switch inputProps={{ 'aria-label': 'Include human support setup' }} disabled={disabled} checked={value.humanSupportEnabled}
        onChange={(_, checked) => set('humanSupportEnabled', checked)} /></div>
      <p>Let your own agents continue AI conversations through LoomAI Desk.</p>
      {value.humanSupportEnabled && <div className="ek-support-option-details"><strong>{deploymentId ? 'Your team in Desk' : 'Add your team after setup'}</strong>
        <p>Connect Desk and choose which agents can help your customers.</p>
        {deploymentId && <Link to={`/human-support?${new URLSearchParams({ deploymentId })}`}>Manage human support <ArrowForwardRounded /></Link>}
        <small>Your agents use their own accounts.</small>
      </div>}
    </section>
    <details className="ek-advanced"><summary><SettingsOutlined />Advanced configuration</summary><p>Your technical team can manage documents, connected systems, actions, sign-in and releases in the product editors.</p>
      {deploymentId && <Link to={`/deployments?${new URLSearchParams({ view: 'manage', deploymentId })}`}>Open {deploymentTerm.lowercase} configuration <ArrowForwardRounded /></Link>}
    </details>
  </div>
}
