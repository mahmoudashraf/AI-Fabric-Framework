import CableRounded from '@mui/icons-material/CableRounded'
import { Alert, Box, Button, Stack, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { ConfigurationDetails } from '../components/ConfigurationSurface'
import type { JsonObject } from '../actions/actionDefinitionModel'
import { stringifyExactJson } from '../lib/losslessJson'

const exactText = (value: unknown) => typeof value === 'string' && value ? value : 'Not recorded'

export function McpActionDestination({ action, destination, capabilitiesHref }: { action: JsonObject; destination: JsonObject; capabilitiesHref?: string }) {
  const connectionId = typeof destination.connectionId === 'string' ? destination.connectionId : ''
  const [path, query] = (capabilitiesHref ?? '').split('?')
  const params = new URLSearchParams(query)
  params.set('view', 'mcp')
  if (connectionId) params.set('mcp', connectionId)
  return <Stack gap={1.5} component="section" aria-label="MCP destination" sx={{ py: 2, borderTop: 1, borderBottom: 1, borderColor: 'divider', minWidth: 0 }}>
    <Stack direction="row" alignItems="center" gap={1}><CableRounded fontSize="small" color="primary" /><Typography variant="subtitle2">MCP tool destination</Typography></Stack>
    <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: '130px minmax(0, 1fr)' }, columnGap: 2, rowGap: .75, m: 0,
      '& dt': { color: 'text.secondary', fontSize: 13 }, '& dd': { m: 0, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12, overflowWrap: 'anywhere' } }}>
      <dt>Exact tool name</dt><dd>{exactText(destination.toolName)}</dd>
      <dt>Owned connection</dt><dd>{exactText(destination.connectionId)}</dd>
      <dt>Server reference</dt><dd>{exactText(destination.serverRef)}</dd>
      {destination.toolSchemaHash ? <><dt>Schema snapshot</dt><dd>{exactText(destination.toolSchemaHash)}</dd></> : null}
    </Box>
    {capabilitiesHref ? <Button component={RouterLink} to={`${path}?${params}`} startIcon={<CableRounded />} sx={{ alignSelf: 'flex-start' }}>View MCP connection</Button> : null}
    {!connectionId ? <Typography variant="caption" color="text.secondary">This action does not declare an owned connection reference. No connection identity has been inferred from its tool or server name.</Typography> : null}
    <ConfigurationDetails label="Native MCP schemas (read-only)">
      <Stack gap={2}>{(['inputSchema', 'outputSchema'] as const).map(field => action[field] !== undefined && action[field] !== null ? <Box key={field}>
        <Typography variant="subtitle2">{field === 'inputSchema' ? 'Input schema' : 'Output schema'}</Typography>
        <Box component="pre" tabIndex={0} aria-label={`MCP native ${field === 'inputSchema' ? 'input' : 'output'} schema`} sx={{ p: 1.5, mt: 1, mb: 0, bgcolor: 'action.hover', border: 1, borderColor: 'divider', borderRadius: 1, maxHeight: 300, overflow: 'auto', fontSize: 12, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{stringifyExactJson(action[field], 2)}</Box>
      </Box> : null)}
        {action.inputSchema == null ? <Alert severity="info">No native input schema is recorded in this action. Refresh the source tools through MCP connections; no schema has been invented here.</Alert> : null}
      </Stack>
    </ConfigurationDetails>
  </Stack>
}
