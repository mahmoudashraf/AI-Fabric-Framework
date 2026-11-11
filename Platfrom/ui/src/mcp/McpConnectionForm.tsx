import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link as RouterLink } from 'react-router-dom'
import AddRounded from '@mui/icons-material/AddRounded'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { Alert, Box, Button, FormControl, FormControlLabel, FormLabel, IconButton, Link, MenuItem, Radio, RadioGroup, Stack, TextField, Tooltip, Typography } from '@mui/material'
import { fetchCustomerDeploymentSecrets, type CustomerDeploymentSecret } from '../api/platformApi'
import { connectionError, connectionPayload, httpsEndpointError, transportLabel } from './model'
import type { McpAuthMode, McpConnectionInput, McpOAuthConfig, McpTransport } from './types'

type Props = { brainId: string; initial: McpConnectionInput; secretsHref: string; busy: boolean; onSave: (input: McpConnectionInput) => void; onCancel: () => void }

function SecretReference({ value, label = 'Saved key', secrets, disabled, onChange }: {
  value: string; label?: string; secrets: CustomerDeploymentSecret[]; disabled: boolean; onChange: (value: string) => void
}) {
  const options = secrets.filter(secret => secret.present)
  return <TextField select required fullWidth size="small" label={label} value={value} disabled={disabled}
    helperText="Select a reference from this Brain. Saved values are never displayed." onChange={event => onChange(event.target.value)}>
    <MenuItem value="" disabled>Choose a saved key</MenuItem>
    {value && !options.some(secret => secret.name === value) ? <MenuItem value={value}>{value} (unavailable)</MenuItem> : null}
    {options.map(secret => <MenuItem key={secret.name} value={secret.name} sx={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{secret.label} / {secret.name}</MenuItem>)}
  </TextField>
}

const authLabels: Record<Exclude<McpAuthMode, 'NONE'>, string> = {
  BEARER_TOKEN_SECRET_REF: 'Saved bearer token', API_KEY_HEADER_SECRET: 'API-key header', MULTIPLE_HEADERS: 'Multiple credential headers',
  OAUTH_AUTHORIZATION_CODE: 'Company-owner OAuth sign-in', OAUTH2_CLIENT_CREDENTIALS: 'OAuth service credentials',
}

export function McpConnectionForm({ brainId, initial, secretsHref, busy, onSave, onCancel }: Props) {
  const [input, setInput] = useState(initial)
  const [scopeText, setScopeText] = useState(initial.auth.oauth?.scopes.join(' ') ?? '')
  const [advanced, setAdvanced] = useState(false)
  const [attempted, setAttempted] = useState(false)
  const business = input.connectionModel === 'BUSINESS'
  const auth = input.auth
  const oauth = auth.oauth ?? { registrationMode: 'AUTO', scopes: [] }
  const oauthMode = auth.mode === 'OAUTH_AUTHORIZATION_CODE' || auth.mode === 'OAUTH2_CLIENT_CREDENTIALS'
  const secrets = useQuery({ queryKey: ['customer-deployment-secrets', brainId], queryFn: () => fetchCustomerDeploymentSecrets(brainId), enabled: business, retry: false })
  const keys = secrets.data?.secrets ?? []
  const patch = (change: Partial<McpConnectionInput>) => setInput(current => ({ ...current, ...change }))
  const patchAuth = (change: Partial<McpConnectionInput['auth']>) => setInput(current => ({ ...current, auth: { ...current.auth, ...change } }))
  const patchOAuth = (change: Partial<McpOAuthConfig>) => patchAuth({ oauth: { ...oauth, ...change } })
  const error = connectionError(input)
  const secretPicker = (value: string, onChange: (value: string) => void, label?: string) => <SecretReference value={value} label={label} onChange={onChange} secrets={keys} disabled={busy || !secrets.isSuccess} />

  return <Box component="form" onSubmit={event => { event.preventDefault(); setAttempted(true); if (!error && !busy) onSave(connectionPayload(input)) }}>
    <Stack gap={2.5}>
      <Typography component="h2" fontSize={21} fontWeight={650}>{'id' in initial ? 'Edit connection' : 'Connect a service'}</Typography>
      {'id' in initial ? <Alert severity="info">These are shared connection settings. Enabling another environment is a separate choice; saving here does not move the connection to the environment you are viewing.</Alert> : null}
      <TextField autoFocus fullWidth required size="small" label="Connection name" placeholder="e.g. Company support tools" value={input.name}
        inputProps={{ maxLength: 160 }} disabled={busy} onChange={event => patch({ name: event.target.value })} helperText="A name your team will recognize." />
      <TextField fullWidth required size="small" label="MCP server address" type="url" placeholder="https://service.example.com/mcp" value={input.endpointUrl}
        disabled={busy} autoComplete="off" onChange={event => patch({ endpointUrl: event.target.value })}
        error={Boolean(input.endpointUrl && httpsEndpointError(input.endpointUrl))} helperText={input.endpointUrl && httpsEndpointError(input.endpointUrl) || 'Use the HTTPS endpoint supplied by the service. Never put a key in this address.'} />
      <FormControl disabled={busy}><FormLabel id="mcp-connection-model">How this service is authorized</FormLabel>
        <RadioGroup aria-labelledby="mcp-connection-model" value={input.connectionModel} onChange={event => {
          const connectionModel = event.target.value === 'PUBLIC' ? 'PUBLIC' : 'BUSINESS'
          patch({ connectionModel, auth: { mode: connectionModel === 'PUBLIC' ? 'NONE' : 'BEARER_TOKEN_SECRET_REF' } })
        }}>
          <FormControlLabel value="PUBLIC" control={<Radio />} label={<Box sx={{ py: .75 }}><Typography variant="body2" fontWeight={600}>Public / no authentication</Typography><Typography variant="caption" color="text.secondary">Public information tools. No upstream credentials or sign-in.</Typography></Box>} />
          <FormControlLabel value="BUSINESS" control={<Radio />} label={<Box sx={{ py: .75 }}><Typography variant="body2" fontWeight={600}>Business-authorized</Typography><Typography variant="caption" color="text.secondary">Your company connects once for this Brain's permitted actions.</Typography></Box>} />
        </RadioGroup>
      </FormControl>
      {business ? <Stack gap={2} sx={{ pl: 2, borderLeft: 2, borderColor: 'divider' }}>
        <TextField select fullWidth size="small" required label="Company authentication" value={auth.mode} disabled={busy}
          onChange={event => { setScopeText(''); patch({ auth: { mode: event.target.value as McpAuthMode, ...(event.target.value === 'API_KEY_HEADER_SECRET' ? { headerName: 'X-API-Key' } : {}),
            ...(event.target.value === 'MULTIPLE_HEADERS' ? { headers: [{ name: '', secretRef: '' }] } : {}),
            ...(event.target.value === 'OAUTH_AUTHORIZATION_CODE' || event.target.value === 'OAUTH2_CLIENT_CREDENTIALS' ? { oauth: { registrationMode: event.target.value === 'OAUTH2_CLIENT_CREDENTIALS' ? 'PREREGISTERED' : 'AUTO', scopes: [] } } : {}) } }) }}>
          {Object.entries(authLabels).map(([mode, label]) => <MenuItem key={mode} value={mode} sx={{ whiteSpace: 'normal' }}>{label}</MenuItem>)}
        </TextField>
        {secrets.isError ? <Alert severity="error" action={<Button onClick={() => void secrets.refetch()}>Retry</Button>}>{secrets.error.message}</Alert> : null}
        {!oauthMode || auth.mode === 'OAUTH2_CLIENT_CREDENTIALS' || advanced ? <Stack direction="row" gap={1} alignItems="center" justifyContent="space-between">
          <Link component={RouterLink} to={secretsHref} target="_blank" rel="noopener" fontSize={13}>Manage keys &amp; secrets</Link>
          <Tooltip title="Refresh saved key references"><IconButton aria-label="Refresh saved keys" size="small" disabled={busy || secrets.isFetching} onClick={() => void secrets.refetch()}><RefreshRounded fontSize="small" /></IconButton></Tooltip>
        </Stack> : null}
        {auth.mode === 'BEARER_TOKEN_SECRET_REF' || auth.mode === 'API_KEY_HEADER_SECRET' ? secretPicker(auth.secretRef ?? '', value => patchAuth({ secretRef: value })) : null}
        {auth.mode === 'API_KEY_HEADER_SECRET' ? <TextField size="small" required label="API-key header name" value={auth.headerName ?? ''} disabled={busy}
          onChange={event => patchAuth({ headerName: event.target.value })} helperText="For example X-API-Key. Routing and protocol headers cannot be overridden." /> : null}
        {auth.mode === 'MULTIPLE_HEADERS' ? <>
          {(auth.headers ?? []).map((header, index) => <Stack key={index} gap={1.5} sx={{ pb: 2, borderBottom: 1, borderColor: 'divider' }}>
            <Stack direction="row" alignItems="center" gap={1}>
              <TextField required fullWidth size="small" label={`Header ${index + 1} name`} value={header.name} placeholder="X-API-Key" disabled={busy}
                onChange={event => patchAuth({ headers: auth.headers!.map((item, at) => at === index ? { ...item, name: event.target.value } : item) })} />
              <Tooltip title="Remove header"><IconButton aria-label={`Remove header ${index + 1}`} disabled={busy} onClick={() => patchAuth({ headers: auth.headers!.filter((_, at) => at !== index) })}><DeleteOutlineRounded fontSize="small" /></IconButton></Tooltip>
            </Stack>
            {secretPicker(header.secretRef, value => patchAuth({ headers: auth.headers!.map((item, at) => at === index ? { ...item, secretRef: value } : item) }), `Header ${index + 1} saved key`)}
          </Stack>)}
          <Button startIcon={<AddRounded />} disabled={busy || (auth.headers?.length ?? 0) >= 8} onClick={() => patchAuth({ headers: [...auth.headers ?? [], { name: '', secretRef: '' }] })} sx={{ alignSelf: 'flex-start' }}>Add credential header</Button>
        </> : null}
        {oauthMode ? <>
          <Typography variant="body2" color="text.secondary">{auth.mode === 'OAUTH_AUTHORIZATION_CODE' ? 'After saving, connect your company account with the provider. Credentials stay on the server; visitors do not connect a personal account.' : 'The service uses your company application credentials, not an account for each visitor.'}</Typography>
          {auth.mode === 'OAUTH2_CLIENT_CREDENTIALS' ? <>
            <TextField required size="small" label="Authorization server URL" type="url" value={oauth.authorizationServerUrl ?? ''} disabled={busy}
              onChange={event => patchOAuth({ authorizationServerUrl: event.target.value })} helperText="Required provider issuer address, for example https://login.example.com. The token endpoint is discovered from its metadata." />
            <TextField required size="small" label="Service client ID" value={oauth.clientId ?? ''} disabled={busy} onChange={event => patchOAuth({ clientId: event.target.value })} />
            {secretPicker(oauth.clientSecretRef ?? '', value => patchOAuth({ clientSecretRef: value }), 'Saved client secret')}
          </> : null}
          <TextField size="small" label="Requested scopes" value={scopeText} disabled={busy} onChange={event => { setScopeText(event.target.value); patchOAuth({ scopes: event.target.value.split(/\s+/).filter(Boolean) }) }}
            helperText="Optional space-separated permissions supplied by the provider. OAuth consent remains explicit." />
        </> : null}
      </Stack> : <Typography variant="body2" color="text.secondary">Tool access, customer sign-in and confirmation are separate policies, selected after discovery.</Typography>}
      <Button size="small" sx={{ alignSelf: 'flex-start', px: 0 }} aria-expanded={advanced} onClick={() => setAdvanced(value => !value)}>{advanced ? 'Hide technical connection settings' : 'Technical connection settings'}</Button>
      {advanced ? <Stack gap={2}>
        <TextField select size="small" label="Transport" value={input.transport} disabled={busy} onChange={event => patch({ transport: event.target.value as McpTransport })}>
          {Object.entries(transportLabel).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
        </TextField>
        {business && oauthMode ? <>
          {auth.mode === 'OAUTH_AUTHORIZATION_CODE' ? <TextField size="small" label="Authorization server URL" type="url" value={oauth.authorizationServerUrl ?? ''} disabled={busy} onChange={event => patchOAuth({ authorizationServerUrl: event.target.value })} helperText="Optional provider issuer address; otherwise discovered from the MCP service." /> : null}
          <TextField size="small" label="Resource URL" type="url" value={oauth.resourceUrl ?? ''} disabled={busy} onChange={event => patchOAuth({ resourceUrl: event.target.value })} helperText="Optional protected resource identifier provided by the service." />
          {auth.mode === 'OAUTH_AUTHORIZATION_CODE' ? <>
            <TextField select size="small" label="Client registration" value={oauth.registrationMode} disabled={busy} onChange={event => patchOAuth({ registrationMode: event.target.value as McpOAuthConfig['registrationMode'] })}>
              <MenuItem value="AUTO">Automatic discovery</MenuItem><MenuItem value="PREREGISTERED">Already registered</MenuItem><MenuItem value="CIMD">Client metadata document</MenuItem><MenuItem value="DCR">Dynamic client registration</MenuItem>
            </TextField>
            <TextField size="small" required={oauth.registrationMode === 'PREREGISTERED'} label="Registered client ID" value={oauth.clientId ?? ''} disabled={busy} onChange={event => patchOAuth({ clientId: event.target.value })} helperText="Only if supplied by the provider or required by its registration method." />
            <TextField select size="small" label="Client authentication" value={oauth.clientAuthenticationMethod ?? 'none'} disabled={busy} onChange={event => patchOAuth({ clientAuthenticationMethod: event.target.value as McpOAuthConfig['clientAuthenticationMethod'] })}>
              <MenuItem value="none">Public client / PKCE</MenuItem><MenuItem value="client_secret_basic">Client secret / HTTP Basic</MenuItem><MenuItem value="client_secret_post">Client secret / token request</MenuItem>
            </TextField>
            {oauth.clientAuthenticationMethod && oauth.clientAuthenticationMethod !== 'none' ? secretPicker(oauth.clientSecretRef ?? '', value => patchOAuth({ clientSecretRef: value }), 'Saved OAuth client secret') : null}
          </> : null}
        </> : null}
      </Stack> : null}
      {attempted && error ? <Alert severity="warning">{error}</Alert> : null}
      <Stack direction="row" gap={1} sx={{ pt: 1, borderTop: 1, borderColor: 'divider' }}>
        <Button variant="contained" type="submit" disabled={busy}>{busy ? 'Saving...' : 'Save connection'}</Button>
        <Button disabled={busy} onClick={onCancel}>Cancel</Button>
      </Stack>
      <Typography variant="caption" color="text.secondary">Saving does not call a business tool or change your Brain draft. Discovery and import are separate steps.</Typography>
    </Stack>
  </Box>
}
