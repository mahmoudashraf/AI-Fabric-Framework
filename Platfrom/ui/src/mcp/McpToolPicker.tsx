import { useState } from 'react'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import ExpandLessRounded from '@mui/icons-material/ExpandLessRounded'
import SearchRounded from '@mui/icons-material/SearchRounded'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import { Alert, Box, Button, Checkbox, Chip, Collapse, FormControlLabel, InputAdornment, MenuItem, Pagination, Stack, Tab, Tabs, TextField, Typography } from '@mui/material'
import { stringifyExactJson } from '../lib/losslessJson'
import { defaultToolPolicy, updateToolPolicy, type compareMcpTools, type McpToolChange } from './model'
import type { McpDiscovery, McpImportInput, McpTool, McpToolPolicy } from './types'

type Props = { snapshot: McpDiscovery; comparison?: ReturnType<typeof compareMcpTools>; existingPolicies: Record<string, McpToolPolicy>; canImport: boolean; busy: boolean; onImport: (input: McpImportInput) => void; onHelp: (tools: string[]) => void }
const PAGE_SIZE = 8
const changeLabels: Record<McpToolChange, string> = { NEW: 'New tool', SCHEMA_CHANGED: 'Schema changed', UNCHANGED: 'Schema unchanged', NEEDS_REVIEW: 'Hash needs review' }

function ToolDetails({ tool, policy, customized, disabled, onPolicy, onReset }: { tool: McpTool; policy: McpToolPolicy; customized: boolean; disabled: boolean; onPolicy: (update: Partial<McpToolPolicy>) => void; onReset: () => void }) {
  const [tab, setTab] = useState('access')
  return <Box sx={{ pl: { xs: 0, sm: 5 }, py: 2 }}>
    {tool.description ? <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{tool.description}</Typography> : null}
    {tool.findings.length ? <Alert severity={tool.compatible ? 'warning' : 'error'} sx={{ mb: 2 }}>{tool.findings.join(' ')}</Alert> : null}
    <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" allowScrollButtonsMobile aria-label={`${tool.name} details`}>
      <Tab value="access" label="Access & confirmation" /><Tab value="input" label="Input schema" />
      {tool.outputSchema != null ? <Tab value="output" label="Output schema" /> : null}
      {tool.annotations || tool.execution ? <Tab value="metadata" label="Service hints" /> : null}
    </Tabs>
    {tab === 'access' ? <Stack gap={1.5} sx={{ pt: 2 }}>
      <Typography variant="caption" color="text.secondary">{customized ? 'Explicit policy for this tool.' : 'New tools: confirmation required, signed-in access, unverified effect and no answer grounding. Existing tools keep their policy unless you customize it.'}</Typography>
      <TextField select size="small" label="Tool effect" value={policy.accessMode} disabled={disabled || !tool.compatible} onChange={event => onPolicy({ accessMode: event.target.value as McpToolPolicy['accessMode'] })}>
        <MenuItem value="READ_WRITE">Unverified / may read and change data</MenuItem><MenuItem value="READ">Verified read-only</MenuItem><MenuItem value="WRITE_ONLY">Changes data</MenuItem>
      </TextField>
      <FormControlLabel control={<Checkbox checked={policy.requiresConfirmation} disabled={disabled || !tool.compatible} onChange={event => onPolicy({ requiresConfirmation: event.target.checked })} />} label="Ask the customer to confirm before running" />
      <FormControlLabel control={<Checkbox checked={policy.allowAnonymous} disabled={disabled || !tool.compatible} onChange={event => onPolicy({ allowAnonymous: event.target.checked })} />} label="Allow visitors who are not signed in" />
      {policy.accessMode === 'READ' ? <FormControlLabel control={<Checkbox checked={policy.readGrounding} disabled={disabled || !tool.compatible} onChange={event => onPolicy({ readGrounding: event.target.checked })} />} label="Allow the result to support AI answers" /> : null}
      {!policy.requiresConfirmation ? <Alert severity="warning">This tool can run without a customer confirmation step. Your integration must still enforce user and object permissions.</Alert> : null}
      {policy.allowAnonymous ? <Alert severity="warning">Anonymous visitors may use this tool. A public MCP address alone does not make customer data safe to expose.</Alert> : null}
      {customized ? <Button size="small" sx={{ alignSelf: 'flex-start', px: 0 }} disabled={disabled} onClick={onReset}>Keep existing policy / use safe defaults</Button> : null}
    </Stack> : <Box component="pre" tabIndex={0} aria-label={`${tool.name} ${tab} schema`} sx={{ m: 0, mt: 2, p: 1.5, bgcolor: 'action.hover', border: 1, borderColor: 'divider', borderRadius: 1, maxHeight: 300, overflow: 'auto', fontSize: 12, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
      {stringifyExactJson(tab === 'input' ? tool.inputSchema : tab === 'output' ? tool.outputSchema : { annotations: tool.annotations, execution: tool.execution }, 2)}
    </Box>}
    {tab === 'metadata' ? <Typography variant="caption" color="text.secondary">Service hints are untrusted metadata. They do not grant read-only status, public access or permission to run.</Typography> : null}
  </Box>
}

export function McpToolPicker({ snapshot, comparison, existingPolicies, canImport, busy, onImport, onHelp }: Props) {
  const [selected, setSelected] = useState<string[]>([])
  const [policies, setPolicies] = useState<Record<string, Partial<McpToolPolicy>>>(() => Object.create(null))
  const [expanded, setExpanded] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const compatible = snapshot.tools.filter(tool => tool.compatible)
  const needle = search.trim().toLowerCase()
  const filtered = snapshot.tools.filter(tool => `${tool.name} ${tool.title ?? ''} ${tool.description ?? ''}`.toLowerCase().includes(needle))
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, pages)
  return <Stack gap={2} sx={{ minWidth: 0 }}>
    <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center">
      <Typography component="h3" variant="subtitle1" fontWeight={650}>Choose capabilities</Typography>
      <Chip size="small" label={`${snapshot.tools.length} discovered`} variant="outlined" /><Chip size="small" label={`${compatible.length} compatible`} variant="outlined" />
    </Stack>
    <Typography variant="caption" color="text.secondary">Observed {new Date(snapshot.observedAt).toLocaleString()} / Protocol {snapshot.protocolVersion}. Discovery did not run any business tools.</Typography>
    {comparison ? <Box component="section" aria-label="Tool refresh comparison"><Typography variant="caption" color="text.secondary">Compared with this connection's imported draft tools</Typography>
      <Stack direction="row" gap={1} flexWrap="wrap" sx={{ mt: .75 }}>
        <Chip size="small" variant="outlined" label={`${comparison.added.length} new / not in draft`} />
        <Chip size="small" variant="outlined" color={comparison.updated.length ? 'warning' : 'default'} label={`${comparison.updated.length} schema ${comparison.updated.length === 1 ? 'change' : 'changes'}`} />
        <Chip size="small" variant="outlined" label={`${comparison.unchanged.length} unchanged`} />
        <Chip size="small" variant="outlined" color={comparison.notReturned.length ? 'warning' : 'default'} label={`${comparison.notReturned.length} not returned`} />
        {comparison.unverified.length ? <Chip size="small" variant="outlined" color="warning" label={`${comparison.unverified.length} need hash review`} /> : null}
      </Stack>
    </Box> : <Alert severity="info">The saved draft is unavailable for comparison. No new, removed or changed tool state is assumed.</Alert>}
    {comparison?.notReturned.length ? <Alert severity="warning">{comparison.notReturned.length} previously imported {comparison.notReturned.length === 1 ? 'tool was' : 'tools were'} not returned. Existing actions are retained; nothing is deleted or updated automatically.
      {!snapshot.complete ? <Typography variant="caption" display="block">This discovery is incomplete, so absence does not prove removal.</Typography> : null}
      <Box component="details" sx={{ mt: 1 }}><summary>Names not returned</summary><Box component="pre" sx={{ m: 0, mt: 1, maxHeight: 180, overflow: 'auto', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontSize: 12 }}>{comparison.notReturned.join('\n')}</Box></Box>
    </Alert> : null}
    {comparison?.unverified.length ? <Alert severity="warning">Some imported tools have no comparable stored hash, or conflicting stored hashes. Their schema state is not assumed unchanged.</Alert> : null}
    {!snapshot.complete ? <Alert severity="warning">Discovery is incomplete. Only the {snapshot.tools.length} tools returned in this snapshot are shown. Refresh tools to obtain a complete catalogue before importing. {snapshot.message}</Alert> : snapshot.message ? <Typography variant="body2" color="text.secondary">{snapshot.message}</Typography> : null}
    <Alert severity="info">New tools require confirmation by default. Selected tools are added only to your draft; existing policies remain unless explicitly changed.</Alert>
    <TextField size="small" label="Find a tool" value={search} onChange={event => { setSearch(event.target.value); setPage(1) }} InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded fontSize="small" /></InputAdornment> }} />
    <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap">
      <Button size="small" disabled={busy || !canImport || !compatible.length} onClick={() => setSelected(compatible.map(tool => tool.name))}>Select all compatible</Button>
      <Button size="small" disabled={busy || !selected.length} onClick={() => setSelected([])}>Clear selection</Button>
      <Typography variant="caption" color="text.secondary">{selected.length} selected</Typography>
      <Button size="small" startIcon={<AutoAwesomeOutlined />} disabled={busy || !selected.length || !canImport} onClick={() => onHelp(selected)} sx={{ ml: { sm: 'auto' } }}>Help configure</Button>
    </Stack>
    <Box aria-label="Discovered MCP tools">
      {filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE).map(tool => <Box key={tool.name} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Stack direction="row" gap={1} alignItems="center" sx={{ py: 1.25 }}>
          <Checkbox checked={selected.includes(tool.name)} disabled={busy || !canImport || !tool.compatible} inputProps={{ 'aria-label': `Select ${tool.name}` }}
            onChange={(_, checked) => setSelected(current => checked ? [...current, tool.name] : current.filter(name => name !== tool.name))} />
          <Box sx={{ minWidth: 0, flex: 1 }}><Typography variant="body2" fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>{tool.title || tool.name}</Typography>
            {tool.title && tool.title !== tool.name ? <Typography variant="caption" component="code" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{tool.name}</Typography> : null}
            {comparison ? <Typography variant="caption" color={comparison.states.get(tool.name) === 'SCHEMA_CHANGED' || comparison.states.get(tool.name) === 'NEEDS_REVIEW' ? 'warning.main' : 'text.secondary'} display="block">{changeLabels[comparison.states.get(tool.name)!]}</Typography> : null}
            {!tool.compatible ? <Typography variant="caption" color="error" display="block">{tool.findings.join(' ') || 'This tool is not compatible with the current runtime.'}</Typography> : null}
          </Box>
          <Button size="small" aria-expanded={expanded === tool.name} aria-label={`Details for ${tool.name}`} endIcon={expanded === tool.name ? <ExpandLessRounded /> : <ExpandMoreRounded />} onClick={() => setExpanded(current => current === tool.name ? null : tool.name)} sx={{ flexShrink: 0 }}>Details</Button>
        </Stack>
        <Collapse in={expanded === tool.name} unmountOnExit><ToolDetails tool={tool} disabled={busy || !canImport} policy={{ ...existingPolicies[tool.name] ?? defaultToolPolicy(), ...policies[tool.name] }} customized={Object.prototype.hasOwnProperty.call(policies, tool.name)}
          onPolicy={update => setPolicies(current => Object.assign(Object.create(null), current, { [tool.name]: updateToolPolicy(current[tool.name] ?? {}, update) }))}
          onReset={() => setPolicies(current => { const next = Object.assign(Object.create(null), current); delete next[tool.name]; return next })} /></Collapse>
      </Box>)}
      {!filtered.length ? <Typography color="text.secondary" sx={{ py: 3 }}>{snapshot.tools.length ? 'No tools match this search.' : 'This service returned no tools.'}</Typography> : null}
    </Box>
    {pages > 1 ? <Pagination size="small" count={pages} page={currentPage} onChange={(_, value) => setPage(value)} /> : null}
    <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} alignItems={{ sm: 'center' }} justifyContent="space-between" sx={{ position: 'sticky', bottom: 0, bgcolor: 'background.paper', borderTop: 1, borderColor: 'divider', py: 2, zIndex: 1 }}>
      <Typography variant="caption" color="text.secondary">No live changes. Publish and apply separately in Updates.</Typography>
      <Button variant="contained" disabled={busy || !canImport || !selected.length || !snapshot.complete} sx={{ flexShrink: 0 }} onClick={() => onImport({ snapshotId: snapshot.id, connectionRevision: snapshot.connectionRevision,
        tools: snapshot.tools.filter(tool => tool.compatible && selected.includes(tool.name)).map(tool => ({ toolName: tool.name, ...policies[tool.name] })) })}>
        {busy ? 'Adding tools...' : `Add ${selected.length || 'selected'} ${selected.length === 1 ? 'tool' : 'tools'} to draft`}
      </Button>
    </Stack>
  </Stack>
}
